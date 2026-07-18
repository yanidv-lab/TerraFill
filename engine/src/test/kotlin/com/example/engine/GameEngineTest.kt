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
            speederCount = 0,
            enemySpeed = 0.0,
            enemyAggression = 0.0,
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
    fun `every level up is a felt difficulty step`() {
        for (l in 1 until 15) {
            val a = LevelConfig.getConfig(l)
            val b = LevelConfig.getConfig(l + 1)
            assertTrue("speed step L$l", b.enemySpeed > a.enemySpeed + 0.3)
            assertTrue("target step L$l", b.targetPercentage >= a.targetPercentage)
            assertTrue("time step L$l", b.timeLimitSeconds <= a.timeLimitSeconds)
        }
    }

    @Test
    fun `enemy count is capped even at very high levels`() {
        val cfg = LevelConfig.getConfig(999)
        val total = cfg.bouncerCount + cfg.crawlerCount + cfg.jumperCount +
            cfg.hunterCount + cfg.speederCount
        assertTrue(total <= 14)
        assertTrue(cfg.enemySpeed <= 10.5)
    }

    @Test
    fun `hunters appear only from level 5 and scale up`() {
        assertEquals(0, LevelConfig.getConfig(4).hunterCount)
        assertTrue(LevelConfig.getConfig(5).hunterCount >= 1)
        assertTrue(LevelConfig.getConfig(20).hunterCount >= LevelConfig.getConfig(5).hunterCount)
    }

    @Test
    fun `speeders appear from level 7 and aggression ramps with level`() {
        assertEquals(0, LevelConfig.getConfig(6).speederCount)
        assertTrue(LevelConfig.getConfig(7).speederCount >= 1)
        // Ability aggression climbs as levels rise
        assertEquals(0.0, LevelConfig.getConfig(1).enemyAggression, 1e-9)
        assertTrue(LevelConfig.getConfig(20).enemyAggression > LevelConfig.getConfig(5).enemyAggression)
        assertTrue(LevelConfig.getConfig(20).enemyAggression <= 1.0)
    }

    // ---------------------------------------------------------------- fast spider + ability scaling

    @Test
    fun `speeder crosses the field much faster than a bouncer`() {
        fun distanceAfter(enemy: Enemy): Double {
            val grid = Array(40) { x ->
                Array(50) { y ->
                    if (x == 0 || x == 39 || y == 0 || y == 49) GridCellState.CAPTURED else GridCellState.EMPTY
                }
            }
            val sx = enemy.x; val sy = enemy.y
            repeat(30) { enemy.update(grid, 0.02) }
            return kotlin.math.hypot(enemy.x - sx, enemy.y - sy)
        }
        val bouncer = Bouncer(id = 1, x = 20.0, y = 25.0, vx = 4.0, vy = 0.0)
        val speeder = Speeder(id = 2, x = 20.0, y = 25.0, vx = 4.0 * 1.8, vy = 0.0)
        assertTrue(distanceAfter(speeder) > distanceAfter(bouncer) * 1.5)
    }

    @Test
    fun `a more aggressive jumper leaps more often`() {
        fun leapCount(aggression: Double): Int {
            val j = Jumper(id = 3, x = 20.0, y = 25.0, vx = 2.0, vy = 0.0, aggression = aggression)
            val grid = Array(40) { x ->
                Array(50) { y ->
                    if (x == 0 || x == 39 || y == 0 || y == 49) GridCellState.CAPTURED else GridCellState.EMPTY
                }
            }
            var leaps = 0
            var wasLeaping = false
            repeat(600) {
                j.update(grid, 0.02)
                val leaping = j.leapProgress > 0.0
                if (leaping && !wasLeaping) leaps++
                wasLeaping = leaping
            }
            return leaps
        }
        assertTrue(leapCount(1.0) > leapCount(0.0))
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

    // ---------------------------------------------------------------- power-ups

    /** Small helper: an engine with one stationary enemy so captures stay partial. */
    private fun engineWithEnemy(target: Double = 99.0) = newEngine(targetPercentage = target).also {
        it.enemies.add(enemyAt(5.0, 5.0))
    }

    @Test
    fun `collecting a shield arms it and absorbs the next crash without losing a life`() {
        val engine = newEngine()
        engine.powerUps.add(PowerUp(1, PowerUpType.SHIELD, x = 5, y = 1))

        engine.setDirection(Direction.DOWN)
        engine.step() // move onto (5,1) -> collect shield
        assertTrue(engine.shieldActive)
        assertTrue(engine.powerUps.isEmpty())
        assertEquals(1, engine.powerUpCollectedCount)

        // Now crash into an enemy: shield should absorb it
        engine.enemies.add(enemyAt(5.0, 1.0))
        engine.tick(0.01)
        assertEquals(3, engine.lives)          // life preserved
        assertFalse(engine.shieldActive)       // shield consumed
        assertEquals(GameStateStatus.CRASH_RESET, engine.status)
    }

    @Test
    fun `freeze power-up stops enemies from moving`() {
        val engine = newEngine()
        engine.powerUps.add(PowerUp(1, PowerUpType.FREEZE, x = 5, y = 1))
        engine.setDirection(Direction.DOWN)
        engine.step() // collect freeze
        assertTrue(engine.freezeRemaining > 0.0)

        val enemy = Bouncer(id = 3, x = 3.0, y = 3.0, vx = 6.0, vy = 0.0)
        engine.enemies.add(enemy)
        engine.tick(0.1)
        assertEquals(3.0, enemy.x, 1e-9)       // did not move while frozen
    }

    @Test
    fun `slow power-up reduces enemy movement`() {
        val engine = newEngine()
        engine.powerUps.add(PowerUp(1, PowerUpType.SLOW, x = 5, y = 1))
        engine.setDirection(Direction.DOWN)
        engine.step()
        assertTrue(engine.slowRemaining > 0.0)

        val enemy = Bouncer(id = 3, x = 3.0, y = 3.0, vx = 10.0, vy = 0.0)
        engine.enemies.add(enemy)
        engine.tick(0.1)
        // Full speed would reach ~4.0; slowed (0.4x) reaches ~3.4
        assertTrue("slowed enemy moved too far: ${enemy.x}", enemy.x < 3.6)
        assertTrue(enemy.x > 3.0)
    }

    // ---------------------------------------------------------------- combo multiplier

    @Test
    fun `chained captures raise the multiplier and it resets after the window`() {
        val engine = engineWithEnemy()

        // Capture 1: cut off the top-left corner
        engine.setDirection(Direction.LEFT); engine.step(); engine.step() // border to (3,0)
        engine.setDirection(Direction.DOWN); engine.step(); engine.step() // draw to (3,2)
        engine.setDirection(Direction.LEFT); engine.step(); engine.step(); engine.step() // -> (0,2) close
        assertEquals(1, engine.captureCount)
        assertEquals(1, engine.scoreMultiplier)
        assertTrue(engine.comboTimeRemaining > 0.0)

        // Capture 2 shortly after: another small pocket on the left wall
        engine.setDirection(Direction.DOWN); engine.step(); engine.step() // border to (0,4)
        engine.setDirection(Direction.RIGHT); engine.step(); engine.step() // draw to (2,4)
        engine.setDirection(Direction.UP); engine.step()                   // (2,3)
        engine.setDirection(Direction.LEFT); engine.step(); engine.step()  // -> (0,3) close
        assertEquals(2, engine.captureCount)
        assertEquals(2, engine.scoreMultiplier)

        // Let the combo window lapse (> COMBO_DURATION of 5s) -> multiplier resets
        engine.tick(6.0)
        assertEquals(1, engine.scoreMultiplier)
        assertEquals(0.0, engine.comboTimeRemaining, 1e-9)
    }

    // ---------------------------------------------------------------- star rating

    @Test
    fun `star rating rewards clean, comfortable clears`() {
        // Not cleared
        assertEquals(0, computeStars(60.0, 75.0, 100.0, 180, 3, 3))
        // Cleared but scrappy: lost lives, thin margin, little time
        assertEquals(1, computeStars(75.5, 75.0, 20.0, 180, 1, 3))
        // Cleared with no damage
        assertTrue(computeStars(76.0, 75.0, 40.0, 180, 3, 3) >= 2)
        // Flawless: no damage + big capture margin
        assertEquals(3, computeStars(90.0, 75.0, 120.0, 180, 3, 3))
    }

    // ---------------------------------------------------------------- adaptive field shape

    @Test
    fun `default field aspect yields the standard 32x40 grid`() {
        val config = LevelConfig.getConfig(1)
        assertEquals(32, config.gridWidth)
        assertEquals(40, config.gridHeight)
    }

    @Test
    fun `taller screens get taller grids with time scaled to the extra area`() {
        val base = LevelConfig.getConfig(1)                    // 32x40
        val tall = LevelConfig.getConfig(1, fieldAspect = 0.5) // 32x64
        assertEquals(32, tall.gridWidth)
        assertEquals(64, tall.gridHeight)
        // 60% more cells to capture => proportionally more time
        val expected = Math.round(base.timeLimitSeconds * (32.0 * 64.0) / (32.0 * 40.0)).toInt()
        assertEquals(expected, tall.timeLimitSeconds)
    }

    @Test
    fun `extreme aspect ratios are clamped to a sane grid`() {
        assertEquals(72, LevelConfig.getConfig(1, fieldAspect = 0.1).gridHeight)
        assertEquals(40, LevelConfig.getConfig(1, fieldAspect = 5.0).gridHeight)
    }

    @Test
    fun `engine runs on an aspect-shaped grid`() {
        val engine = GameEngine(LevelConfig.getConfig(1, fieldAspect = 0.5))
        assertEquals(32, engine.width)
        assertEquals(64, engine.height)
        // Border cells are pre-captured on the taller grid too
        assertEquals(GridCellState.CAPTURED, engine.grid[0][63])
        assertEquals(GridCellState.CAPTURED, engine.grid[31][0])
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
