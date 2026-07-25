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

    /**
     * Steps the player one cell down into open territory (drawing a trail), where
     * roaming enemies are lethal. Standing on the claimed border is safe ground, so
     * collision tests must leave it first.
     */
    private fun GameEngine.stepIntoOpenGround() {
        setDirection(Direction.DOWN)
        step()
    }

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
        engine.stepIntoOpenGround()               // out in the open at (5,1)
        engine.enemies.add(enemyAt(5.0, 1.0))     // parked on the player
        engine.tick(0.01)

        assertEquals(2, engine.lives)
        assertEquals(GameStateStatus.CRASH_RESET, engine.status)
    }

    @Test
    fun `game is over when the last life is lost`() {
        val engine = newEngine(initialLives = 1)
        engine.stepIntoOpenGround()
        engine.enemies.add(enemyAt(5.0, 1.0))
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
        engine.stepIntoOpenGround()
        engine.enemies.add(enemyAt(5.0, 1.0))
        engine.tick(0.01)
        assertEquals(1, engine.crashCount)
    }

    // ---------------------------------------------------------------- radius-based collision

    @Test
    fun `enemy overlapping the player from a neighboring cell collides`() {
        val engine = newEngine()
        engine.stepIntoOpenGround()
        // Enemy center at (6.1, 1.5); player center at (5.5, 1.5): gap 0.6 < 0.4 + 0.4
        engine.enemies.add(enemyAt(5.6, 1.0))
        engine.tick(0.01)
        assertEquals(GameStateStatus.CRASH_RESET, engine.status)
    }

    @Test
    fun `enemy in a neighboring cell without circle overlap does not collide`() {
        val engine = newEngine()
        engine.stepIntoOpenGround()
        // Enemy center at (6.5, 1.5); player center at (5.5, 1.5): gap 1.0 > 0.8
        engine.enemies.add(enemyAt(6.0, 1.0))
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
    fun `every level up is a felt difficulty step, and nothing ever gets easier`() {
        for (l in 1 until 15) {
            val a = LevelConfig.getConfig(l)
            val b = LevelConfig.getConfig(l + 1)
            // Speed is the smooth per-level pressure; the rest must never regress.
            assertTrue("speed step L$l", b.enemySpeed > a.enemySpeed + 0.3)
            assertTrue("target never drops L$l", b.targetPercentage >= a.targetPercentage)
            assertTrue("time never grows L$l", b.timeLimitSeconds <= a.timeLimitSeconds)
        }
    }

    @Test
    fun `difficulty is staggered - a level up does not raise every dimension at once`() {
        // Across the campaign there must be levels where the target holds steady and
        // levels where the clock holds steady, so pressure arrives one axis at a time.
        var targetHeldSomewhere = false
        var timeHeldSomewhere = false
        for (l in 1 until 20) {
            val a = LevelConfig.getConfig(l)
            val b = LevelConfig.getConfig(l + 1)
            if (b.targetPercentage == a.targetPercentage) targetHeldSomewhere = true
            if (b.timeLimitSeconds == a.timeLimitSeconds) timeHeldSomewhere = true
        }
        assertTrue("capture target should hold steady on some level-ups", targetHeldSomewhere)
        assertTrue("time limit should hold steady on some level-ups", timeHeldSomewhere)
    }

    @Test
    fun `mid and late game capture targets stay approachable`() {
        // Level 12 used to demand ~86%; the eased curve keeps it around 72-75%.
        assertTrue(
            "L12 target too steep: ${LevelConfig.getConfig(12).targetPercentage}",
            LevelConfig.getConfig(12).targetPercentage <= 75.0
        )
        assertTrue(
            "final target should cap at 80%",
            LevelConfig.getConfig(999).targetPercentage <= 80.0
        )
    }

    @Test
    fun `new enemy reveals are announced on the levels that introduce them`() {
        assertEquals("Jumper", LevelConfig.newEnemyAt(3)?.type)
        assertEquals("Eater", LevelConfig.newEnemyAt(4)?.type)
        assertEquals("Hunter", LevelConfig.newEnemyAt(5)?.type)
        assertEquals("Speeder", LevelConfig.newEnemyAt(7)?.type)
        assertEquals("Spitter", LevelConfig.newEnemyAt(15)?.type)
        // Quiet levels announce nothing
        assertEquals(null, LevelConfig.newEnemyAt(6))
        assertEquals(null, LevelConfig.newEnemyAt(12))
    }

    @Test
    fun `every announced enemy actually spawns on its debut level`() {
        for (level in listOf(3, 4, 5, 7, 15)) {
            val intro = LevelConfig.newEnemyAt(level)!!
            val engine = GameEngine(LevelConfig.getConfig(level))
            assertTrue(
                "L$level announces ${intro.type} but none spawned",
                engine.enemies.any { it.type == intro.type }
            )
        }
    }

    @Test
    fun `enemy count is capped even at very high levels`() {
        val cfg = LevelConfig.getConfig(999)
        val total = cfg.bouncerCount + cfg.crawlerCount + cfg.jumperCount +
            cfg.hunterCount + cfg.speederCount + cfg.eaterCount + cfg.spitterCount
        assertTrue(total <= 7)
        assertTrue(cfg.enemySpeed <= 9.8)
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
    fun `default field aspect yields a 28-wide grid`() {
        val config = LevelConfig.getConfig(1)
        assertEquals(28, config.gridWidth)
        assertTrue(config.gridHeight in 36..64)
    }

    @Test
    fun `taller screens get taller grids with time scaled to the extra area`() {
        val base = LevelConfig.getConfig(1)                    // 28 x 36 (default aspect)
        val tall = LevelConfig.getConfig(1, fieldAspect = 0.5) // 28 x 56
        assertEquals(28, tall.gridWidth)
        assertEquals(56, tall.gridHeight)
        // Proportionally more cells to traverse => proportionally more time
        val expected = Math.round(
            base.timeLimitSeconds.toDouble() * (28.0 * 56.0) / (28.0 * base.gridHeight)
        ).toInt()
        assertEquals(expected, tall.timeLimitSeconds)
    }

    @Test
    fun `extreme aspect ratios are clamped to a sane grid`() {
        assertEquals(64, LevelConfig.getConfig(1, fieldAspect = 0.1).gridHeight)
        assertEquals(36, LevelConfig.getConfig(1, fieldAspect = 5.0).gridHeight)
    }

    @Test
    fun `engine runs on an aspect-shaped grid`() {
        val engine = GameEngine(LevelConfig.getConfig(1, fieldAspect = 0.5))
        assertEquals(28, engine.width)
        assertEquals(56, engine.height)
        // Border cells are pre-captured on the taller grid too
        assertEquals(GridCellState.CAPTURED, engine.grid[0][55])
        assertEquals(GridCellState.CAPTURED, engine.grid[27][0])
    }

    @Test
    fun `late levels stay a small squad, not a crowd`() {
        val cfg = LevelConfig.getConfig(20)
        val total = cfg.bouncerCount + cfg.crawlerCount + cfg.jumperCount +
            cfg.hunterCount + cfg.speederCount + cfg.eaterCount + cfg.spitterCount
        assertTrue("level 20 should feature every ability type",
            cfg.bouncerCount > 0 && cfg.crawlerCount > 0 && cfg.jumperCount > 0 &&
                cfg.hunterCount > 0 && cfg.speederCount > 0 &&
                cfg.eaterCount > 0 && cfg.spitterCount > 0)
        assertTrue("late-game squad should stay small (<=7), was $total", total <= 7)
    }

    @Test
    fun `eaters appear from level 4 and spitters from level 15`() {
        assertEquals(0, LevelConfig.getConfig(3).eaterCount)
        assertTrue(LevelConfig.getConfig(4).eaterCount >= 1)
        assertEquals(0, LevelConfig.getConfig(14).spitterCount)
        assertTrue(LevelConfig.getConfig(15).spitterCount >= 1)
    }

    @Test
    fun `resuming frees enemies that would be walled inside claimed land`() {
        // A board where everything except one pocket has been claimed.
        val w = 12
        val h = 12
        val sb = StringBuilder()
        for (x in 0 until w) {
            for (y in 0 until h) {
                val open = (x == 6 && y == 6)
                sb.append(if (open) '0' else '1')
            }
        }
        val engine = GameEngine(
            LevelConfig(
                levelNumber = 1, gridWidth = w, gridHeight = h,
                bouncerCount = 1, crawlerCount = 0, jumperCount = 0,
                hunterCount = 0, speederCount = 0,
                enemySpeed = 4.0, enemyAggression = 0.0,
                targetPercentage = 99.0, timeLimitSeconds = 300
            )
        )
        engine.restoreSnapshot(sb.toString(), savedScore = 10, savedLives = 3, savedTime = 100.0)

        val enemy = engine.enemies.first()
        val ex = kotlin.math.floor(enemy.x).toInt()
        val ey = kotlin.math.floor(enemy.y).toInt()
        assertEquals(
            "a restored enemy must never be left inside claimed land",
            GridCellState.EMPTY, engine.grid[ex][ey]
        )
    }

    @Test
    fun `restored enemies that are already in the open are left where they are`() {
        val engine = newEngine()
        engine.enemies.clear()
        engine.enemies.add(Bouncer(1, 4.0, 4.0, 3.0, 3.0))
        val mask = engine.exportCapturedMask()   // only the border is claimed

        engine.restoreSnapshot(mask, savedScore = 0, savedLives = 3, savedTime = 50.0)

        assertEquals(4.0, engine.enemies.first().x, 1e-9)
        assertEquals(4.0, engine.enemies.first().y, 1e-9)
    }

    // ---------------------------------------------------------------- new enemy roster

    @Test
    fun `the campaign now runs to thirty levels with staged debuts`() {
        assertEquals(30, LevelConfig.TOTAL_LEVELS)
        assertEquals("Weaver", LevelConfig.newEnemyAt(18)?.type)
        assertEquals("Hornet", LevelConfig.newEnemyAt(21)?.type)
        assertEquals("Phantom", LevelConfig.newEnemyAt(24)?.type)
        assertEquals("Broodmother", LevelConfig.newEnemyAt(27)?.type)
    }

    @Test
    fun `every debut enemy actually spawns on its level, even late on`() {
        for (level in listOf(18, 21, 24, 27)) {
            val intro = LevelConfig.newEnemyAt(level)!!
            val engine = GameEngine(LevelConfig.getConfig(level))
            assertTrue(
                "L$level announces ${intro.type} but none spawned",
                engine.enemies.any { it.type == intro.type }
            )
        }
    }

    @Test
    fun `late levels stay within the roster budget`() {
        for (level in 1..30) {
            val engine = GameEngine(LevelConfig.getConfig(level))
            assertTrue(
                "L$level spawned ${engine.enemies.size} enemies",
                engine.enemies.size <= 8
            )
        }
    }

    @Test
    fun `weaver spins a sticky trap onto open ground and it kills on contact`() {
        val config = LevelConfig(
            levelNumber = 18, gridWidth = 12, gridHeight = 12,
            bouncerCount = 0, crawlerCount = 0, jumperCount = 0,
            hunterCount = 0, speederCount = 0, weaverCount = 1,
            enemySpeed = 0.0, enemyAggression = 1.0,
            targetPercentage = 99.0, timeLimitSeconds = 300
        )
        val engine = GameEngine(config)
        // Park the weaver on a known open cell and let it spin.
        val weaver = engine.enemies.first { it.type == "Weaver" }
        weaver.x = 5.0
        weaver.y = 5.0
        weaver.vx = 0.0
        weaver.vy = 0.0
        var guard = 0
        while (engine.webTraps.isEmpty() && guard++ < 400) engine.tick(0.05)

        assertTrue("weaver should have spun a trap", engine.webTraps.isNotEmpty())
        val (tx, ty) = engine.webTraps.first()
        assertEquals("traps only stick to open ground", GridCellState.EMPTY, engine.grid[tx][ty])
    }

    @Test
    fun `hornets and phantoms pass straight through claimed land`() {
        val grid = Array(12) { x ->
            Array(12) { y ->
                if (x == 6) GridCellState.CAPTURED else GridCellState.EMPTY
            }
        }
        val hornet = Hornet(1, 4.0, 5.0, 6.0, 0.0)
        repeat(30) { hornet.update(grid, 0.05) }
        assertTrue("hornet should have crossed the wall at x=6", hornet.x > 6.5)

        val phantom = Phantom(2, 4.0, 5.0, 3.0, 0.0)
        phantom.setTarget(11.0, 5.0)
        repeat(60) { phantom.update(grid, 0.05) }
        assertTrue("phantom should have drifted through the wall", phantom.x > 6.5)
    }

    @Test
    fun `only the phantom and crawler threaten a player on claimed ground`() {
        // Phantom reaches the player standing safely on the border.
        val ghosted = newEngine()
        ghosted.enemies.add(Phantom(1, ghosted.playerX.toDouble(), 0.0, 0.0, 0.0))
        ghosted.tick(0.05)
        assertEquals("a phantom must reach you on claimed land", 2, ghosted.lives)

        // A hornet in the very same spot cannot.
        val buzzed = newEngine()
        buzzed.enemies.add(Hornet(1, buzzed.playerX.toDouble(), 0.0, 0.0, 0.0))
        buzzed.tick(0.05)
        assertEquals("a hornet must not reach you on claimed land", 3, buzzed.lives)
    }

    @Test
    fun `broodmother hatches spiderlings up to a cap`() {
        val config = LevelConfig(
            levelNumber = 27, gridWidth = 16, gridHeight = 16,
            bouncerCount = 0, crawlerCount = 0, jumperCount = 0,
            hunterCount = 0, speederCount = 0, broodmotherCount = 1,
            enemySpeed = 2.0, enemyAggression = 1.0,
            targetPercentage = 99.0, timeLimitSeconds = 900
        )
        val engine = GameEngine(config)
        repeat(2000) { engine.tick(0.05) }   // plenty of time to breed

        val brood = engine.enemies.count { it.type == "Spiderling" }
        assertTrue("the queen should have hatched at least one child", brood >= 1)
        assertTrue("the brood must stay capped, was $brood", brood <= 4)
    }

    // ---------------------------------------------------------------- star currency

    @Test
    fun `star pools grow with the level and jump at 10 and 15`() {
        assertEquals(10, StarEconomy.poolForLevel(1))
        assertEquals(20, StarEconomy.poolForLevel(2))
        assertEquals(90, StarEconomy.poolForLevel(9))
        // Non-linear step ups
        assertTrue(
            "L10 should jump well past the linear 100",
            StarEconomy.poolForLevel(10) > StarEconomy.poolForLevel(9) + 20
        )
        assertTrue(
            "L15 should jump again",
            StarEconomy.poolForLevel(15) > StarEconomy.poolForLevel(14) + 20
        )
        // Monotonic all the way up
        for (l in 1 until 20) {
            assertTrue("pool must not shrink at L$l", StarEconomy.poolForLevel(l + 1) > StarEconomy.poolForLevel(l))
        }
    }

    @Test
    fun `stars paid out scale with how much of the board was claimed`() {
        // 40% of level 1's pool of 10 = 4 stars
        assertEquals(4, StarEconomy.award(level = 1, capturedPercentage = 40.0))
        assertEquals(10, StarEconomy.award(level = 1, capturedPercentage = 100.0))
        assertEquals(20, StarEconomy.award(level = 2, capturedPercentage = 100.0))
        assertEquals(0, StarEconomy.award(level = 5, capturedPercentage = 0.0))
        // A high level pays far better for the same effort
        assertTrue(
            StarEconomy.award(20, 70.0) > StarEconomy.award(2, 70.0) * 5
        )
    }

    @Test
    fun `finishing a level banks star currency proportional to the capture`() {
        val engine = newEngine(targetPercentage = 50.0)
        engine.enemies.add(enemyAt(7.0, 5.0))          // blocks the right half
        engine.setDirection(Direction.DOWN)
        repeat(9) { engine.step() }

        assertEquals(GameStateStatus.LEVEL_COMPLETE, engine.status)
        // 62.5% of level 1's pool of 10
        assertEquals(StarEconomy.award(1, engine.capturedPercentage), engine.starsEarned)
        assertTrue("a completed level must pay something", engine.starsEarned > 0)
    }

    @Test
    fun `an unfinished level pays no star currency`() {
        val engine = newEngine()
        engine.setDirection(Direction.DOWN)
        repeat(3) { engine.step() }     // still drawing, nothing closed
        assertEquals(0, engine.starsEarned)
    }

    // ---------------------------------------------------------------- mid-level save/restore

    @Test
    fun `a mid-level snapshot round-trips the claimed board and stats`() {
        val engine = newEngine()
        engine.setDirection(Direction.DOWN)
        repeat(9) { engine.step() }          // claim the whole interior
        val mask = engine.exportCapturedMask()
        val score = engine.score
        val captured = engine.capturedPercentage

        val resumed = newEngine()
        resumed.restoreSnapshot(mask, savedScore = score, savedLives = 2, savedTime = 42.0)

        assertEquals(score, resumed.score)
        assertEquals(2, resumed.lives)
        assertEquals(42.0, resumed.timeRemainingSeconds, 1e-9)
        assertEquals(captured, resumed.capturedPercentage, 1e-9)
        assertEquals(mask, resumed.exportCapturedMask())
    }

    @Test
    fun `a resumed run starts safely on the border with no trail`() {
        val engine = newEngine()
        engine.setDirection(Direction.DOWN)
        repeat(3) { engine.step() }          // mid-draw, out in the open
        val mask = engine.exportCapturedMask()

        val resumed = newEngine()
        resumed.restoreSnapshot(mask, savedScore = 100, savedLives = 3, savedTime = 60.0)

        assertFalse(resumed.isDrawing)
        assertTrue(resumed.trail.isEmpty())
        assertEquals(0, resumed.playerY)
        assertEquals(GridCellState.CAPTURED, resumed.grid[resumed.playerX][resumed.playerY])
        assertEquals(GameStateStatus.RUNNING, resumed.status)
    }

    @Test
    fun `a snapshot from a different board size is ignored`() {
        val engine = newEngine()
        val before = engine.exportCapturedMask()
        engine.restoreSnapshot("101010", savedScore = 999, savedLives = 1, savedTime = 5.0)

        assertEquals("board must be untouched", before, engine.exportCapturedMask())
        assertEquals("stats must be untouched", 0, engine.score)
    }

    // ---------------------------------------------------------------- movement stability

    @Test
    fun `an enemy sealed in a tiny pocket does not thrash its heading`() {
        // A 1-cell pocket at (5,5): everything around it is claimed land.
        val grid = Array(12) { x ->
            Array(12) { y ->
                if (x == 5 && y == 5) GridCellState.EMPTY else GridCellState.CAPTURED
            }
        }
        val enemy = Bouncer(1, 5.4, 5.4, 9.0, 9.0)
        var signFlips = 0
        var previousSign = if (enemy.vx > 0) 1 else -1
        repeat(60) {
            enemy.update(grid, 1.0 / 60.0)
            val sign = if (enemy.vx > 0) 1 else -1
            if (sign != previousSign) signFlips++
            previousSign = sign
        }
        // One reversal per axis per tick at most - not dozens of sub-step flips.
        assertTrue("heading flipped $signFlips times in 60 ticks", signFlips <= 60)
        // And it must stay inside its pocket rather than tunnelling out.
        assertTrue("enemy escaped its pocket", enemy.x >= 5.0 && enemy.x < 6.0)
    }

    @Test
    fun `rendered facing eases instead of snapping with every bounce`() {
        val enemy = Bouncer(1, 5.0, 5.0, 4.0, 0.0)
        enemy.facing = 1.0
        enemy.vx = -4.0                      // instantaneous reversal
        enemy.advanceFacing(1.0 / 60.0)      // a single frame
        assertTrue("facing should ease, not snap to -1", enemy.facing > 0.5)
        repeat(60) { enemy.advanceFacing(1.0 / 60.0) }
        assertTrue("facing should settle after sustained travel", enemy.facing < -0.5)
    }

    // ---------------------------------------------------------------- safe ground

    @Test
    fun `player standing on claimed land is safe from roaming spiders`() {
        val engine = newEngine()
        // Player is on the captured top border at spawn, not drawing.
        engine.enemies.add(enemyAt(engine.playerX.toDouble(), 1.0)) // right beneath them
        engine.tick(0.05)

        assertEquals("standing on claimed ground must not crash", 3, engine.lives)
        assertEquals(GameStateStatus.RUNNING, engine.status)
    }

    @Test
    fun `border-patrolling crawlers still threaten a player on claimed land`() {
        val engine = newEngine()
        engine.enemies.add(Crawler(50, engine.playerX.toDouble(), 0.2, 0.0, 1.0))
        engine.tick(0.05)

        assertEquals("a crawler on the border must still hit", 2, engine.lives)
    }

    @Test
    fun `player drawing a trail is vulnerable again`() {
        val engine = newEngine()
        engine.setDirection(Direction.DOWN)
        engine.step()   // now at (5,1) in open territory, drawing
        assertTrue(engine.isDrawing)

        engine.enemies.add(enemyAt(engine.playerX.toDouble(), engine.playerY.toDouble()))
        engine.tick(0.05)

        assertEquals("out in the open the spider must hit", 2, engine.lives)
    }

    // ---------------------------------------------------------------- eater (wall-devourer)

    private fun borderedGrid(w: Int, h: Int): Array<Array<GridCellState>> =
        Array(w) { x ->
            Array(h) { y ->
                if (x == 0 || x == w - 1 || y == 0 || y == h - 1) GridCellState.CAPTURED
                else GridCellState.EMPTY
            }
        }

    @Test
    fun `eater devours an interior captured cell it runs into`() {
        val grid = borderedGrid(12, 12)
        grid[6][5] = GridCellState.CAPTURED          // a lone claimed cell
        val eater = Eater(1, 4.0, 5.0, 5.0, 0.0)     // to its left, drifting right
        // Advance until it reaches and bites the cell.
        repeat(20) { eater.update(grid, 0.05) }
        assertEquals(GridCellState.EMPTY, grid[6][5])
        assertTrue("eater should flag that it ate a wall", eater.ateWall)
    }

    @Test
    fun `eater can never devour the outer border`() {
        val grid = borderedGrid(12, 12)
        val eater = Eater(1, 1.5, 5.0, -5.0, 0.0)    // drifting left into the border
        repeat(40) { eater.update(grid, 0.05) }
        assertEquals(GridCellState.CAPTURED, grid[0][5])  // border intact
    }

    // ---------------------------------------------------------------- spitter (web-shooter)

    @Test
    fun `spitter charges then fires a web aimed at the player`() {
        val grid = borderedGrid(20, 20)
        val spitter = Spitter(1, 10.0, 12.0, aggression = 1.0)
        spitter.setTarget(10.5, 0.5)                 // player far above the spitter
        var fired: DoubleArray? = null
        var t = 0.0
        while (t < 8.0 && fired == null) {
            spitter.update(grid, 0.1)
            fired = spitter.consumePendingSpit()
            t += 0.1
        }
        assertTrue("spitter should fire within a few seconds", fired != null)
        assertTrue("web should travel upward toward the player", fired!![1] < 0.0)
    }

    @Test
    fun `spitter never moves from its post`() {
        val grid = borderedGrid(20, 20)
        val spitter = Spitter(1, 10.0, 12.0, aggression = 0.5)
        spitter.setTarget(2.0, 2.0)
        repeat(60) { spitter.update(grid, 0.1) }
        assertEquals(10.0, spitter.x, 1e-9)
        assertEquals(12.0, spitter.y, 1e-9)
    }

    @Test
    fun `engine emits web projectiles when a spitter is present`() {
        val config = LevelConfig(
            levelNumber = 15, gridWidth = 20, gridHeight = 20,
            bouncerCount = 0, crawlerCount = 0, jumperCount = 0,
            hunterCount = 0, speederCount = 0, eaterCount = 0, spitterCount = 1,
            enemySpeed = 4.0, enemyAggression = 1.0,
            targetPercentage = 99.0, timeLimitSeconds = 999
        )
        val engine = GameEngine(config)
        var sawWeb = false
        repeat(80) {
            engine.tick(0.1)
            if (engine.webs.isNotEmpty()) sawWeb = true
        }
        assertTrue("a spitter should have launched at least one web", sawWeb)
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
