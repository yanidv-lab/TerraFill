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
