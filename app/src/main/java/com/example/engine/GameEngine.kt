package com.example.engine

import kotlin.math.floor
import kotlin.random.Random

/**
 * Game state enum to track the current phase of the match.
 */
enum class GameStateStatus {
    MENU,
    RUNNING,
    PAUSED,
    CRASH_RESET,
    LEVEL_COMPLETE,
    GAME_OVER
}

/**
 * The core game engine containing the state and physics of TerraFill.
 * This class is written in pure Kotlin and is fully testable.
 */
class GameEngine(
    val levelConfig: LevelConfig,
    initialLives: Int = 3
) {
    val width = levelConfig.gridWidth
    val height = levelConfig.gridHeight

    // 2D grid representing the playfield: grid[x][y]
    val grid: Array<Array<GridCellState>> = Array(width) { x ->
        Array(height) { y ->
            if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                GridCellState.CAPTURED
            } else {
                GridCellState.EMPTY
            }
        }
    }

    // Player cursor state
    var playerX = width / 2
        private set
    var playerY = 0
        private set
    var playerDirection = Direction.NONE
        private set
    var isDrawing = false
        private set
    val trail = mutableListOf<Pair<Int, Int>>()

    // Enemy state
    val enemies = mutableListOf<Enemy>()

    // Stats
    var lives = initialLives
        private set
    var score = 0
        private set
    var timeRemainingSeconds = levelConfig.timeLimitSeconds.toDouble()
        private set
    var capturedPercentage = 0.0
        private set
    var status = GameStateStatus.RUNNING
        private set

    // Speed limits & accumulators
    private val playerMoveInterval = 0.08 // seconds per grid step (approx 12.5 steps/sec)
    private var playerMoveTimer = 0.0

    init {
        initializeEnemies()
        recalculateCapturedPercentage()
    }

    /**
     * Spawns enemies safely away from the borders in empty spaces.
     */
    private fun initializeEnemies() {
        enemies.clear()
        val random = Random(levelConfig.levelNumber * 31)
        var idCounter = 1

        // Spawn Bouncers
        for (i in 0 until levelConfig.bouncerCount) {
            val rx = random.nextDouble(5.0, (width - 6).toDouble())
            val ry = random.nextDouble(5.0, (height - 6).toDouble())
            
            // Random direction angles
            val angle = random.nextDouble(0.0, 2.0 * Math.PI)
            val speed = levelConfig.enemySpeed
            val vx = speed * kotlin.math.cos(angle)
            val vy = speed * kotlin.math.sin(angle)

            enemies.add(Bouncer(idCounter++, rx, ry, vx, vy))
        }

        // Spawn Crawlers (border-following enemies)
        for (i in 0 until levelConfig.crawlerCount) {
            // Crawlers start near the perimeter but inside empty cells
            val rx = if (random.nextBoolean()) 2.0 else (width - 3).toDouble()
            val ry = random.nextDouble(5.0, (height - 6).toDouble())
            
            val angle = random.nextDouble(0.0, 2.0 * Math.PI)
            val speed = levelConfig.enemySpeed
            val vx = speed * kotlin.math.cos(angle)
            val vy = speed * kotlin.math.sin(angle)

            enemies.add(Crawler(idCounter++, rx, ry, vx, vy))
        }
    }

    /**
     * Changes the current movement direction of the player cursor.
     */
    fun setDirection(direction: Direction) {
        if (status != GameStateStatus.RUNNING) return

        // Prevent moving immediately backwards into your own trail
        if (isDrawing && trail.size > 1) {
            val prevCell = trail[trail.size - 2]
            val nextX = playerX + direction.dx
            val nextY = playerY + direction.dy
            if (nextX == prevCell.first && nextY == prevCell.second) {
                // Ignore input that is directly opposite
                return
            }
        }
        playerDirection = direction
    }

    /**
     * Game engine tick update - updates enemies, grid-aligned player movement,
     * timers, and collision status.
     *
     * @param dt Elapsed time in seconds.
     */
    fun tick(dt: Double) {
        if (status != GameStateStatus.RUNNING) return

        // 1. Update Timer
        timeRemainingSeconds = (timeRemainingSeconds - dt).coerceAtLeast(0.0)
        if (timeRemainingSeconds <= 0) {
            triggerGameOver()
            return
        }

        // 2. Update Enemies
        for (enemy in enemies) {
            enemy.update(grid, dt)
        }

        // 3. Check enemy-player direct collision or trail collision before moving player
        if (checkEnemyCollisions()) {
            handleCrash()
            return
        }

        // 4. Update Player movement on grid (tick-rate independent accumulator)
        playerMoveTimer += dt
        while (playerMoveTimer >= playerMoveInterval) {
            playerMoveTimer -= playerMoveInterval
            performPlayerGridStep()
            
            // Re-check collisions after the player moves
            if (status == GameStateStatus.RUNNING && checkEnemyCollisions()) {
                handleCrash()
                break
            }
        }
    }

    /**
     * Checks if any enemy touches the player cursor or any cell of the active drawing trail.
     */
    private fun checkEnemyCollisions(): Boolean {
        for (enemy in enemies) {
            val ex = floor(enemy.x).toInt().coerceIn(0, width - 1)
            val ey = floor(enemy.y).toInt().coerceIn(0, height - 1)

            // Direct collision with player
            if (ex == playerX && ey == playerY) {
                return true
            }

            // Collision with the temporary drawing trail
            if (grid[ex][ey] == GridCellState.TRAIL) {
                return true
            }
        }
        return false
    }

    /**
     * Performs a single discrete step of player cursor movement in the grid.
     */
    private fun performPlayerGridStep() {
        if (playerDirection == Direction.NONE) return

        val nextX = playerX + playerDirection.dx
        val nextY = playerY + playerDirection.dy

        // Out of field boundaries check
        if (nextX !in 0 until width || nextY !in 0 until height) {
            playerDirection = Direction.NONE
            return
        }

        val targetState = grid[nextX][nextY]

        when (targetState) {
            GridCellState.CAPTURED -> {
                if (isDrawing) {
                    // Success! Player re-entered safe captured territory and closed a region
                    playerX = nextX
                    playerY = nextY
                    isDrawing = false
                    playerDirection = Direction.NONE

                    // Convert the trail to captured
                    for (cell in trail) {
                        grid[cell.first][cell.second] = GridCellState.CAPTURED
                    }
                    val capturedInTrail = trail.size
                    trail.clear()

                    // Run the Flood Fill algorithm to evaluate and capture regions with no enemies
                    val capturedInFill = FloodFill.evaluateAndCaptureRegions(grid, enemies)

                    // Scoring
                    val totalCaptured = capturedInTrail + capturedInFill
                    score += totalCaptured * 15

                    recalculateCapturedPercentage()

                    // TODO: Trigger nice sound effect and visual particle effects here on region capture.

                    if (capturedPercentage >= levelConfig.targetPercentage) {
                        status = GameStateStatus.LEVEL_COMPLETE
                    }
                } else {
                    // Just moving along the safe border
                    playerX = nextX
                    playerY = nextY
                }
            }
            GridCellState.EMPTY -> {
                // Enter or continue drawing a trail
                playerX = nextX
                playerY = nextY
                isDrawing = true
                grid[playerX][playerY] = GridCellState.TRAIL
                trail.add(Pair(playerX, playerY))
            }
            GridCellState.TRAIL -> {
                // Player intersected their own trail -> Crash!
                handleCrash()
            }
        }
    }

    /**
     * Handles the crash event when the player hits an enemy, their trail, or crosses their own trail.
     */
    private fun handleCrash() {
        lives--
        playerDirection = Direction.NONE
        isDrawing = false

        // Clear the unfinished trail and restore those cells back to EMPTY
        for (cell in trail) {
            grid[cell.first][cell.second] = GridCellState.EMPTY
        }
        trail.clear()

        if (lives <= 0) {
            status = GameStateStatus.GAME_OVER
        } else {
            // Reset player to a safe, guaranteed captured starting location
            playerX = width / 2
            playerY = 0
            status = GameStateStatus.CRASH_RESET
        }
    }

    /**
     * Clears the crash reset state, resuming game loop operations.
     */
    fun clearReset() {
        if (status == GameStateStatus.CRASH_RESET) {
            status = GameStateStatus.RUNNING
        }
    }

    private fun triggerGameOver() {
        status = GameStateStatus.GAME_OVER
    }

    /**
     * Recalculates the exact percentage of playable (inner) area captured,
     * excluding the initial boundary/border cells, so that level progress starts at 0.0%.
     */
    private fun recalculateCapturedPercentage() {
        var capturedCount = 0
        // Exclude the 1-cell thick border to calculate percentage of the playable area
        for (x in 1 until width - 1) {
            for (y in 1 until height - 1) {
                if (grid[x][y] == GridCellState.CAPTURED) {
                    capturedCount++
                }
            }
        }
        val totalPlayableCells = (width - 2) * (height - 2)
        capturedPercentage = if (totalPlayableCells > 0) {
            (capturedCount.toDouble() / totalPlayableCells) * 100.0
        } else {
            0.0
        }
    }

    /**
     * Toggles pause status.
     */
    fun togglePause() {
        if (status == GameStateStatus.RUNNING) {
            status = GameStateStatus.PAUSED
        } else if (status == GameStateStatus.PAUSED) {
            status = GameStateStatus.RUNNING
        }
    }
}
