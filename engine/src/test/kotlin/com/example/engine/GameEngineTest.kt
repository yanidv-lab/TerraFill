package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Behavioral tests for the core game loop: movement, trail drawing, region
 * closing, capture percentage, crashes, lives, and end-of-game transitions.
 *
 * All tests use enemy-free level configs (and inject stationary enemies by
 * hand where needed) so outcomes are fully deterministic.
 */
class GameEngineTest {

    /** 10x10 grid, no enemies, generous time limit. Player starts at (5, 0) on the top border. */
    private fun newEngine(
        targetPercentage: Double = 75.0,
        timeLimitSeconds: Int = 180,
        initialLives: Int = 3
    ) = GameEngine(
        LevelConfig(
            levelNumber = 1,
            gridWidth = 10,
            gridHeight = 10,
            bouncerCount = 0,
            crawlerCount = 0,
            jumperCount = 0,
            hunterCount = 0,
            enemySpeed = 0.0,
            targetPercentage = targetPercentage,
            timeLimitSeconds = timeLimitSeconds
        ),
        initialLives = initialLives
    )

    /** Advances the engine by exactly one player grid step (one move interval). */
    private fun GameEngine.step() = tick(0.08)

    /** A stationary enemy parked at the given cell. */
    private fun enemyAt(x: Double, y: Double) = Bouncer(id = 99, x = x, y = y, vx = 0.0, vy = 0.0)

    // ---------------------------------------------------------------- initial state

    @Test
    fun `initial state has captured border, empty interior, zero percent`() {
        val engine = newEngine()

        assertEquals(5, engine.playerX)
        assertEquals(0, engine.playerY)
        assertFalse(engine.isDrawing)
        assertEquals(GameStateStatus.RUNNING, engine.status)
        assertEquals(0.0, engine.capturedPercentage, 1e-9)

        for (x in 0 until 10) {
            for (y in 0 until 10) {
                val expected = if (x == 0 || x == 9 || y == 0 || y == 9) {
                    GridCellState.CAPTURED
                } else {
                    GridCellState.EMPTY
                }
                assertEquals("cell ($x,$y)", expected, engine.grid[x][y])
            }
        }
    }

    // ---------------------------------------------------------------- movement & trail

    @Test
    fun `moving into empty territory starts a trail`() {
        val engine = newEngine()
        engine.setDirection(Direction.DOWN)
        engine.step()

        assertEquals(5, engine.playerX)
        assertEquals(1, engine.playerY)
        assertTrue(engine.isDrawing)
        assertEquals(listOf(Pair(5, 1)), engine.trail)
        assertEquals(GridCellState.TRAIL, engine.grid[5][1])
    }

    @Test
    fun `moving along the captured border does not draw a trail`() {
        val engine = newEngine()
        engine.setDirection(Direction.RIGHT)
        engine.step()
        engine.step()

        assertEquals(7, engine.playerX)
        assertEquals(0, engine.playerY)
        assertFalse(engine.isDrawing)
        assertTrue(engine.trail.isEmpty())
    }

    @Test
    fun `player stops at the field boundary instead of leaving it`() {
        val engine = newEngine()
        engine.setDirection(Direction.UP) // would leave the grid at y = -1
        engine.step()

        assertEquals(5, engine.playerX)
        assertEquals(0, engine.playerY)
        assertEquals(Direction.NONE, engine.playerDirection)
    }

    @Test
    fun `reversing direction while drawing is ignored`() {
        val engine = newEngine()
        engine.setDirection(Direction.DOWN)
        engine.step()
        engine.step() // trail is now (5,1), (5,2)

        engine.setDirection(Direction.UP) // directly backwards onto own trail
        assertEquals(Direction.DOWN, engine.playerDirection)
    }

    // ---------------------------------------------------------------- closing a region

    @Test
    fun `closing a trail across the field captures everything when no enemies exist`() {
        val engine = newEngine()
        engine.setDirection(Direction.DOWN)
        // 8 steps through the interior (y = 1..8), 9th step reaches the bottom border
        repeat(9) { engine.step() }

        assertFalse(engine.isDrawing)
        assertTrue(engine.trail.isEmpty())
        // Whole 8x8 interior captured: trail column (8 cells) + both side regions
        assertEquals(100.0, engine.capturedPercentage, 1e-9)
        // 64 captured cells (8 trail + 56 flood-filled) at 15 points each
        assertEquals(64 * 15, engine.score)
        // 100% >= 75% target
        assertEquals(GameStateStatus.LEVEL_COMPLETE, engine.status)
    }

    @Test
    fun `closing a trail captures only regions without enemies`() {
        val engine = newEngine()
        // Park a stationary enemy in the right half (x=7), so only the left half may be captured
        engine.enemies.add(enemyAt(7.0, 5.0))

        engine.setDirection(Direction.DOWN)
        repeat(9) { engine.step() }

        assertFalse(engine.isDrawing)
        // Left region: x=1..4 (4 cols x 8 rows = 32) + trail column x=5 (8 cells) = 40 of 64
        assertEquals(40.0 / 64.0 * 100.0, engine.capturedPercentage, 1e-9)
        assertEquals(GameStateStatus.RUNNING, engine.status) // 62.5% < 75% target
        // Right region must still be open territory
        assertEquals(GridCellState.EMPTY, engine.grid[7][5])
    }

    @Test
    fun `level completes when captured percentage reaches the target`() {
        val engine = newEngine(targetPercentage = 50.0)
        engine.enemies.add(enemyAt(7.0, 5.0))

        engine.setDirection(Direction.DOWN)
        repeat(9) { engine.step() }

        // 62.5% >= 50% target
        assertEquals(GameStateStatus.LEVEL_COMPLETE, engine.status)
    }

    // ---------------------------------------------------------------- crashing

    @Test
    fun `crossing your own trail crashes, clears the trail, and costs a life`() {
        val engine = newEngine()
        // Draw a small hook that loops back onto its own trail:
        // (5,1) (5,2) -> (6,2) -> (6,1) -> attempts (5,1) which is TRAIL
        engine.setDirection(Direction.DOWN)
        engine.step()
        engine.step()
        engine.setDirection(Direction.RIGHT)
        engine.step()
        engine.setDirection(Direction.UP)
        engine.step()
        engine.setDirection(Direction.LEFT)
        engine.step()

        assertEquals(2, engine.lives)
        assertEquals(GameStateStatus.CRASH_RESET, engine.status)
        assertFalse(engine.isDrawing)
        assertTrue(engine.trail.isEmpty())
        // Trail cells restored to EMPTY, nothing was captured
        assertEquals(GridCellState.EMPTY, engine.grid[5][1])
        assertEquals(GridCellState.EMPTY, engine.grid[6][2])
        assertEquals(0.0, engine.capturedPercentage, 1e-9)
        // Player reset to spawn
        assertEquals(5, engine.playerX)
        assertEquals(0, engine.playerY)

        // clearReset resumes play
        engine.clearReset()
        assertEquals(GameStateStatus.RUNNING, engine.status)
    }

    @Test
    fun `enemy touching the trail crashes the player`() {
        val engine = newEngine()
        engine.setDirection(Direction.DOWN)
        engine.step()
        engine.step() // trail at (5,1), (5,2)

        engine.enemies.add(enemyAt(5.0, 2.0)) // sits on a trail cell
        engine.step()

        assertEquals(2, engine.lives)
        assertEquals(GameStateStatus.CRASH_RESET, engine.status)
        assertEquals(GridCellState.EMPTY, engine.grid[5][1])
    }

    @Test
    fun `enemy touching the player cursor crashes the player`() {
        val engine = newEngine()
        engine.enemies.add(enemyAt(5.0, 0.0)) // player spawn cell
        engine.tick(0.01)

        assertEquals(2, engine.lives)
        assertEquals(GameStateStatus.CRASH_RESET, engine.status)
    }

    @Test
    fun `game is over when the last life is lost`() {
        val engine = newEngine(initialLives = 1)
        engine.enemies.add(enemyAt(5.0, 0.0))
        engine.tick(0.01)

        assertEquals(0, engine.lives)
        assertEquals(GameStateStatus.GAME_OVER, engine.status)
    }

    // ---------------------------------------------------------------- timer & pause

    @Test
    fun `running out of time ends the game`() {
        val engine = newEngine(timeLimitSeconds = 1)
        engine.tick(2.0)

        assertEquals(0.0, engine.timeRemainingSeconds, 1e-9)
        assertEquals(GameStateStatus.GAME_OVER, engine.status)
    }

    @Test
    fun `pause freezes the simulation and unpause resumes it`() {
        val engine = newEngine()
        engine.togglePause()
        assertEquals(GameStateStatus.PAUSED, engine.status)

        val timeBefore = engine.timeRemainingSeconds
        engine.setDirection(Direction.DOWN) // ignored while paused
        engine.step()
        assertEquals(timeBefore, engine.timeRemainingSeconds, 1e-9)
        assertEquals(0, engine.playerY)

        engine.togglePause()
        assertEquals(GameStateStatus.RUNNING, engine.status)
        engine.setDirection(Direction.DOWN)
        engine.step()
        assertEquals(1, engine.playerY)
    }

    // ---------------------------------------------------------------- path history & interpolation

    @Test
    fun `path history tracks recent head cells, most recent first`() {
        val engine = newEngine()
        assertEquals(listOf(Pair(5, 0)), engine.pathHistory.toList())

        engine.setDirection(Direction.DOWN)
        engine.step()
        engine.step()
        engine.setDirection(Direction.RIGHT)
        engine.step()

        assertEquals(
            listOf(Pair(6, 2), Pair(5, 2), Pair(5, 1), Pair(5, 0)),
            engine.pathHistory.toList()
        )
    }

    @Test
    fun `move progress is pinned to 1 before the player ever moves`() {
        val engine = newEngine()
        engine.tick(0.04)
        assertEquals(1.0, engine.moveProgress, 1e-9)
    }

    @Test
    fun `move progress interpolates between steps and pins to 1 when stationary`() {
        val engine = newEngine()

        // A full step just landed: progress restarts at 0 and grows with elapsed time
        engine.setDirection(Direction.DOWN)
        engine.step()
        assertEquals(0.0, engine.moveProgress, 1e-9)
        engine.tick(0.04)
        assertEquals(0.5, engine.moveProgress, 1e-6)

        // Stop: once the next scheduled step does not move, progress pins to 1
        engine.setDirection(Direction.NONE)
        engine.tick(0.04) // completes the pending interval; the scheduled step is a no-move
        assertEquals(1.0, engine.moveProgress, 1e-9)
    }

    @Test
    fun `path history resets to spawn after a crash`() {
        val engine = newEngine()
        engine.setDirection(Direction.DOWN)
        engine.step()
        engine.enemies.add(enemyAt(5.0, 1.0)) // on top of the player
        engine.step()

        assertEquals(GameStateStatus.CRASH_RESET, engine.status)
        assertEquals(listOf(Pair(5, 0)), engine.pathHistory.toList())
    }

    // ---------------------------------------------------------------- capture & crash events

    @Test
    fun `capture events expose the claimed cells for animations`() {
        val engine = newEngine()
        assertEquals(0, engine.captureCount)

        engine.setDirection(Direction.DOWN)
        repeat(9) { engine.step() }

        assertEquals(1, engine.captureCount)
        assertEquals(64, engine.lastCapturedCells.size) // 8 trail + 56 flood-filled
        assertTrue(engine.lastCapturedCells.contains(Pair(5, 4))) // a trail cell
        assertTrue(engine.lastCapturedCells.contains(Pair(2, 4))) // a flood-filled cell
    }

    @Test
    fun `crash count increments on every crash`() {
        val engine = newEngine()
        assertEquals(0, engine.crashCount)
        engine.enemies.add(enemyAt(5.0, 0.0))
        engine.tick(0.01)
        assertEquals(1, engine.crashCount)
    }

    // ---------------------------------------------------------------- radius-based collision

    @Test
    fun `enemy overlapping the player from a neighboring cell collides`() {
        val engine = newEngine()
        // Enemy center at (6.1, 0.5); player center at (5.5, 0.5): gap 0.6 < 0.4 + 0.4
        engine.enemies.add(enemyAt(5.6, 0.0))
        engine.tick(0.01)
        assertEquals(GameStateStatus.CRASH_RESET, engine.status)
    }

    @Test
    fun `enemy in a neighboring cell without circle overlap does not collide`() {
        val engine = newEngine()
        // Enemy center at (6.5, 0.5); player center at (5.5, 0.5): gap 1.0 > 0.8
        engine.enemies.add(enemyAt(6.0, 0.0))
        engine.tick(0.01)
        assertEquals(GameStateStatus.RUNNING, engine.status)
        assertEquals(3, engine.lives)
    }

    // ---------------------------------------------------------------- crawler wall-following

    @Test
    fun `crawler hugs the captured border and never enters captured cells`() {
        val engine = newEngine()
        // Start against the left wall heading down (wall on its right hand)
        val crawler = Crawler(id = 50, x = 1.0, y = 5.0, vx = 0.0, vy = 3.0)
        engine.enemies.add(crawler)

        // Walk it around for a while in small ticks; it should trace the inner ring
        val visited = mutableSetOf<Pair<Int, Int>>()
        repeat(2000) {
            crawler.update(engine.grid, 0.02)
            val cx = Math.round(crawler.x).toInt()
            val cy = Math.round(crawler.y).toInt()
            assertTrue("crawler left the field at ($cx,$cy)", cx in 0..9 && cy in 0..9)
            assertTrue(
                "crawler entered captured cell ($cx,$cy)",
                engine.grid[cx][cy] != GridCellState.CAPTURED
            )
            visited.add(Pair(cx, cy))
        }

        // The inner ring of a 10x10 field has 28 cells; a wall-hugger must cover all of them
        assertEquals(28, visited.size)
    }

    @Test
    fun `crawler movement is deterministic`() {
        fun runOnce(): List<Pair<Double, Double>> {
            val engine = newEngine()
            val crawler = Crawler(id = 50, x = 1.0, y = 5.0, vx = 0.0, vy = 3.0)
            val positions = mutableListOf<Pair<Double, Double>>()
            repeat(500) {
                crawler.update(engine.grid, 0.016)
                positions.add(Pair(crawler.x, crawler.y))
            }
            return positions
        }
        assertEquals(runOnce(), runOnce())
    }

    // ---------------------------------------------------------------- grid versioning

    @Test
    fun `grid version bumps only when the grid actually changes`() {
        val engine = newEngine()
        val v0 = engine.gridVersion

        // Moving along the border does not change the grid
        engine.setDirection(Direction.RIGHT)
        engine.step()
        assertEquals(v0, engine.gridVersion)

        // Drawing a trail cell changes the grid
        engine.setDirection(Direction.DOWN)
        engine.step()
        val v1 = engine.gridVersion
        assertTrue(v1 > v0)

        // Standing still (no direction) does not change the grid
        engine.setDirection(Direction.NONE)
        engine.step()
        assertEquals(v1, engine.gridVersion)
    }

    // ---------------------------------------------------------------- level scaling

    @Test
    fun `difficulty scales up with level number`() {
        val early = LevelConfig.getConfig(1)
        val late = LevelConfig.getConfig(10)

        assertEquals(2, early.bouncerCount + early.crawlerCount + early.jumperCount) // gentle start
        assertEquals(0, early.jumperCount)                                            // no jumpers yet
        assertTrue(late.enemySpeed > early.enemySpeed)
        assertTrue(late.jumperCount > 0)                                              // jumpers appear later
        assertTrue(late.targetPercentage >= early.targetPercentage)
        assertTrue(late.timeLimitSeconds <= early.timeLimitSeconds)
        assertTrue(
            late.bouncerCount + late.crawlerCount + late.jumperCount >
                early.bouncerCount + early.crawlerCount + early.jumperCount
        )
    }

    @Test
    fun `enemy count is capped even at very high levels`() {
        val cfg = LevelConfig.getConfig(999)
        assertTrue(cfg.bouncerCount + cfg.crawlerCount + cfg.jumperCount + cfg.hunterCount <= 12)
        assertTrue(cfg.enemySpeed <= 9.5)
    }

    @Test
    fun `hunters appear only from level 6 and scale up`() {
        assertEquals(0, LevelConfig.getConfig(5).hunterCount)
        assertTrue(LevelConfig.getConfig(6).hunterCount >= 1)
        assertTrue(LevelConfig.getConfig(20).hunterCount >= LevelConfig.getConfig(6).hunterCount)
    }

    // ---------------------------------------------------------------- hunting spider

    @Test
    fun `hunter steers toward the player over time`() {
        // Hunter starts to the right of the player, moving away (left target pull should win)
        val hunter = Hunter(id = 1, x = 30.0, y = 25.0, vx = 4.0, vy = 0.0)
        val grid = Array(40) { x ->
            Array(50) { y ->
                if (x == 0 || x == 39 || y == 0 || y == 49) GridCellState.CAPTURED else GridCellState.EMPTY
            }
        }
        val playerX = 8.0
        val playerY = 25.0
        val startDist = kotlin.math.hypot(hunter.x - playerX, hunter.y - playerY)
        repeat(120) {
            hunter.setTarget(playerX, playerY)
            hunter.update(grid, 0.05)
            assertTrue(hunter.x in 0.0..40.0 && hunter.y in 0.0..50.0)
        }
        val endDist = kotlin.math.hypot(hunter.x - playerX, hunter.y - playerY)
        assertTrue("hunter should close in: start=$startDist end=$endDist", endDist < startDist)
    }

    @Test
    fun `hunter keeps a constant chase speed`() {
        val hunter = Hunter(id = 2, x = 20.0, y = 20.0, vx = 5.0, vy = 0.0)
        val grid = Array(40) { x ->
            Array(50) { y ->
                if (x == 0 || x == 39 || y == 0 || y == 49) GridCellState.CAPTURED else GridCellState.EMPTY
            }
        }
        repeat(50) {
            hunter.setTarget(30.0, 30.0)
            hunter.update(grid, 0.05)
        }
        val speed = kotlin.math.hypot(hunter.vx, hunter.vy)
        assertEquals(5.0, speed, 0.5)
    }

    // ---------------------------------------------------------------- jumping spider

    @Test
    fun `jumper drifts then leaps to a much faster speed`() {
        val jumper = Jumper(id = 1, x = 20.0, y = 25.0, vx = 2.0, vy = 0.0)
        val grid = Array(40) { x ->
            Array(50) { y ->
                if (x == 0 || x == 39 || y == 0 || y == 49) GridCellState.CAPTURED else GridCellState.EMPTY
            }
        }
        var maxSpeed = 0.0
        repeat(600) {
            jumper.update(grid, 0.016)
            maxSpeed = maxOf(maxSpeed, kotlin.math.hypot(jumper.vx, jumper.vy))
            // Must never leave the field or enter captured cells
            assertTrue(jumper.x in 0.0..40.0 && jumper.y in 0.0..50.0)
        }
        // Cruise speed is ~2.0; a leap must reach clearly beyond it
        assertTrue("expected a fast leap, saw max $maxSpeed", maxSpeed > 4.0)
    }

    @Test
    fun `jumper behaviour is deterministic for a given id`() {
        fun run(): List<Pair<Double, Double>> {
            val j = Jumper(id = 7, x = 20.0, y = 25.0, vx = 2.0, vy = 0.0)
            val grid = Array(40) { x ->
                Array(50) { y ->
                    if (x == 0 || x == 39 || y == 0 || y == 49) GridCellState.CAPTURED else GridCellState.EMPTY
                }
            }
            val path = mutableListOf<Pair<Double, Double>>()
            repeat(300) { j.update(grid, 0.016); path.add(j.x to j.y) }
            return path
        }
        assertEquals(run(), run())
    }

    // ---------------------------------------------------------------- enemy spawning

    @Test
    fun `configured enemies spawn inside open territory`() {
        val engine = GameEngine(LevelConfig.getConfig(1))
        assertEquals(2, engine.enemies.size) // level 1: 1 bouncer + 1 crawler

        for (enemy in engine.enemies) {
            val ex = enemy.x.toInt()
            val ey = enemy.y.toInt()
            assertEquals(
                "enemy ${enemy.id} (${enemy.type}) at ($ex,$ey)",
                GridCellState.EMPTY,
                engine.grid[ex][ey]
            )
        }
    }
}
