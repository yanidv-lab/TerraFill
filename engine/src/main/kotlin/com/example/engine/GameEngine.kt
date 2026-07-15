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

    /**
     * Recent cells the player's head has passed through, most recent first
     * (index 0 is always the current cell). Used by the UI to render a segmented
     * caterpillar body trailing behind the head. Cleared on crash reset.
     */
    val pathHistory = ArrayDeque<Pair<Int, Int>>()

    /**
     * Interpolation fraction in [0, 1] of the head's glide from pathHistory[1]
     * toward pathHistory[0]. Pinned to 1.0 while the player is stationary so
     * the body rests instead of jittering.
     */
    var moveProgress = 1.0
        private set
    private var advancing = false

    /** Monotonic counter incremented every time a region capture completes. */
    var captureCount = 0
        private set

    /** The cells captured by the most recent trail closure (trail + flood-filled). */
    var lastCapturedCells: List<Pair<Int, Int>> = emptyList()
        private set

    /** Monotonic counter incremented every time the player crashes. */
    var crashCount = 0
        private set

    /**
     * Monotonic counter bumped whenever the grid contents change (trail drawn,
     * region captured, trail cleared on crash). The UI layer uses this to avoid
     * deep-copying the whole grid on frames where nothing changed - a big win for
     * smoothness, since most frames only move enemies.
     */
    var gridVersion = 0
        private set

    // --- Combo / score multiplier ---
    /** Current score multiplier from chaining captures quickly (1..MAX_MULTIPLIER). */
    var scoreMultiplier = 1
        private set
    /** Seconds left in the combo window; capture again before it hits 0 to keep chaining. */
    var comboTimeRemaining = 0.0
        private set

    // --- Power-ups ---
    /** Collectibles currently on the field. */
    val powerUps = mutableListOf<PowerUp>()
    /** True while a shield is armed (will absorb the next crash). */
    var shieldActive = false
        private set
    /** Seconds enemies remain frozen (0 = not frozen). */
    var freezeRemaining = 0.0
        private set
    /** Seconds enemies remain slowed (0 = normal speed). */
    var slowRemaining = 0.0
        private set
    /** Monotonic counter incremented each time a power-up is collected. */
    var powerUpCollectedCount = 0
        private set
    /** The type collected in the most recent pickup (for UI/sfx). */
    var lastCollectedPowerUp: PowerUpType? = null
        private set

    private var powerUpSpawnTimer = POWERUP_FIRST_SPAWN
    private var powerUpIdCounter = 1
    private val powerUpRandom = Random(levelConfig.levelNumber * 97 + 7)

    /** Star rating (0-3) for the level, valid once status is LEVEL_COMPLETE. */
    var stars = 0
        private set

    private val startingLives = initialLives

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

    private companion object {
        /** Half the player's collision box in cell units (the sprite is ~0.8 cells wide). */
        const val PLAYER_HALF_SIZE = 0.4

        /** How many recent head positions to keep for caterpillar body rendering. */
        const val MAX_PATH_HISTORY = 24

        /** Combo window: capture again within this many seconds to raise the multiplier. */
        const val COMBO_DURATION = 5.0
        const val MAX_MULTIPLIER = 8

        /** Power-up tuning. */
        const val POWERUP_FIRST_SPAWN = 6.0     // seconds before the first one appears
        const val POWERUP_INTERVAL = 12.0       // seconds between spawns
        const val MAX_POWERUPS = 3              // max simultaneously on the field
        const val FREEZE_SECONDS = 3.5
        const val SLOW_SECONDS = 5.0
        const val SLOW_FACTOR = 0.4
    }

    init {
        pathHistory.addFirst(Pair(playerX, playerY))
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

        // Spawn Crawlers (border-following enemies) directly against the left or
        // right wall so they start hugging the boundary immediately. Heading runs
        // down along the left wall / up along the right wall, which puts the wall
        // on the crawler's right hand.
        for (i in 0 until levelConfig.crawlerCount) {
            val onLeftWall = random.nextBoolean()
            val rx = if (onLeftWall) 1.0 else (width - 2).toDouble()
            val ry = random.nextInt(3, height - 3).toDouble()
            val speed = levelConfig.enemySpeed
            val vy = if (onLeftWall) speed else -speed

            enemies.add(Crawler(idCounter++, rx, ry, 0.0, vy))
        }

        // Spawn Jumpers (jumping spiders) in open space, cruising slower than bouncers.
        for (i in 0 until levelConfig.jumperCount) {
            val rx = random.nextDouble(5.0, (width - 6).toDouble())
            val ry = random.nextDouble(5.0, (height - 6).toDouble())
            val angle = random.nextDouble(0.0, 2.0 * Math.PI)
            val speed = levelConfig.enemySpeed * 0.7
            val vx = speed * kotlin.math.cos(angle)
            val vy = speed * kotlin.math.sin(angle)

            enemies.add(Jumper(idCounter++, rx, ry, vx, vy, aggression = levelConfig.enemyAggression))
        }

        // Spawn Hunters (chasers) in the lower half so they start away from the
        // player's top-border spawn, giving the player a moment before the chase.
        for (i in 0 until levelConfig.hunterCount) {
            val rx = random.nextDouble(5.0, (width - 6).toDouble())
            val ry = random.nextDouble((height * 0.5), (height - 6).toDouble())
            val angle = random.nextDouble(0.0, 2.0 * Math.PI)
            val speed = levelConfig.enemySpeed * 0.85
            val vx = speed * kotlin.math.cos(angle)
            val vy = speed * kotlin.math.sin(angle)

            enemies.add(Hunter(idCounter++, rx, ry, vx, vy, aggression = levelConfig.enemyAggression))
        }

        // Spawn Speeders (fast spiders) in open space, moving much faster than bouncers.
        for (i in 0 until levelConfig.speederCount) {
            val rx = random.nextDouble(5.0, (width - 6).toDouble())
            val ry = random.nextDouble(5.0, (height - 6).toDouble())
            val angle = random.nextDouble(0.0, 2.0 * Math.PI)
            val speed = levelConfig.enemySpeed * 1.8
            val vx = speed * kotlin.math.cos(angle)
            val vy = speed * kotlin.math.sin(angle)

            enemies.add(Speeder(idCounter++, rx, ry, vx, vy))
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

        // 1b. Tick down combo window and power-up effect timers
        if (comboTimeRemaining > 0.0) {
            comboTimeRemaining = (comboTimeRemaining - dt).coerceAtLeast(0.0)
            if (comboTimeRemaining == 0.0) scoreMultiplier = 1
        }
        if (freezeRemaining > 0.0) freezeRemaining = (freezeRemaining - dt).coerceAtLeast(0.0)
        if (slowRemaining > 0.0) slowRemaining = (slowRemaining - dt).coerceAtLeast(0.0)

        // 1c. Periodically spawn power-ups
        powerUpSpawnTimer -= dt
        if (powerUpSpawnTimer <= 0.0) {
            spawnPowerUp()
            powerUpSpawnTimer = POWERUP_INTERVAL
        }

        // 2. Update Enemies (tell chasers where the player is, then move everyone).
        //    Frozen -> no movement; slowed -> reduced dt.
        val targetX = playerX + 0.5
        val targetY = playerY + 0.5
        val enemyDt = when {
            freezeRemaining > 0.0 -> 0.0
            slowRemaining > 0.0 -> dt * SLOW_FACTOR
            else -> dt
        }
        if (enemyDt > 0.0) {
            for (enemy in enemies) {
                enemy.setTarget(targetX, targetY)
                enemy.update(grid, enemyDt)
            }
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

        // 5. Expose the head's interpolation fraction for smooth rendering
        moveProgress = if (advancing) {
            (playerMoveTimer / playerMoveInterval).coerceIn(0.0, 1.0)
        } else {
            1.0
        }
    }

    /**
     * Checks if any enemy circle overlaps the player cursor or any cell of the active
     * drawing trail. Uses real distances (enemy radius vs. player half-size) rather
     * than whole-cell overlap, so near misses feel fair.
     */
    private fun checkEnemyCollisions(): Boolean {
        val playerCx = playerX + 0.5
        val playerCy = playerY + 0.5

        for (enemy in enemies) {
            // Enemy positions are cell coordinates; the visual center sits at +0.5.
            val ecx = enemy.x + 0.5
            val ecy = enemy.y + 0.5
            val r = enemy.radius

            // Direct collision with the player square (approximated as a circle)
            val dx = ecx - playerCx
            val dy = ecy - playerCy
            val hitDistance = r + PLAYER_HALF_SIZE
            if (dx * dx + dy * dy < hitDistance * hitDistance) {
                return true
            }

            // Collision with any trail cell the enemy circle overlaps
            val minX = floor(ecx - r).toInt().coerceIn(0, width - 1)
            val maxX = floor(ecx + r).toInt().coerceIn(0, width - 1)
            val minY = floor(ecy - r).toInt().coerceIn(0, height - 1)
            val maxY = floor(ecy + r).toInt().coerceIn(0, height - 1)
            for (cx in minX..maxX) {
                for (cy in minY..maxY) {
                    if (grid[cx][cy] != GridCellState.TRAIL) continue
                    // Circle vs. cell rectangle: clamp the center into the cell and compare
                    val nearestX = ecx.coerceIn(cx.toDouble(), (cx + 1).toDouble())
                    val nearestY = ecy.coerceIn(cy.toDouble(), (cy + 1).toDouble())
                    val ddx = ecx - nearestX
                    val ddy = ecy - nearestY
                    if (ddx * ddx + ddy * ddy < r * r) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Performs a single discrete step of player cursor movement in the grid.
     */
    private fun performPlayerGridStep() {
        advancing = false
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
                    recordStep()
                    isDrawing = false
                    playerDirection = Direction.NONE

                    // Convert the trail to captured
                    val trailCells = trail.toList()
                    for (cell in trailCells) {
                        grid[cell.first][cell.second] = GridCellState.CAPTURED
                    }
                    trail.clear()

                    // Run the Flood Fill algorithm to evaluate and capture regions with no enemies
                    val filledCells = FloodFill.evaluateAndCaptureRegions(grid, enemies)

                    // Record the capture event so the UI can animate the claimed area
                    lastCapturedCells = trailCells + filledCells
                    captureCount++
                    gridVersion++

                    // Combo: chaining captures within the window raises the multiplier
                    scoreMultiplier = if (comboTimeRemaining > 0.0) {
                        (scoreMultiplier + 1).coerceAtMost(MAX_MULTIPLIER)
                    } else {
                        1
                    }
                    comboTimeRemaining = COMBO_DURATION

                    // Scoring (scaled by the current multiplier)
                    score += lastCapturedCells.size * 15 * scoreMultiplier

                    // Any power-up swallowed by the newly captured area is removed
                    powerUps.removeAll { grid[it.x][it.y] == GridCellState.CAPTURED }

                    recalculateCapturedPercentage()

                    if (capturedPercentage >= levelConfig.targetPercentage) {
                        stars = computeStars(
                            capturedPercentage = capturedPercentage,
                            targetPercentage = levelConfig.targetPercentage,
                            timeRemainingSeconds = timeRemainingSeconds,
                            timeLimitSeconds = levelConfig.timeLimitSeconds,
                            livesRemaining = lives,
                            initialLives = startingLives
                        )
                        status = GameStateStatus.LEVEL_COMPLETE
                    }
                } else {
                    // Just moving along the safe border
                    playerX = nextX
                    playerY = nextY
                    recordStep()
                }
            }
            GridCellState.EMPTY -> {
                // Enter or continue drawing a trail
                playerX = nextX
                playerY = nextY
                recordStep()
                isDrawing = true
                grid[playerX][playerY] = GridCellState.TRAIL
                trail.add(Pair(playerX, playerY))
                gridVersion++
            }
            GridCellState.TRAIL -> {
                // Player intersected their own trail -> Crash!
                handleCrash()
            }
        }
    }

    /**
     * Records a successful head movement into the path history ring used for
     * caterpillar body rendering.
     */
    private fun recordStep() {
        pathHistory.addFirst(Pair(playerX, playerY))
        while (pathHistory.size > MAX_PATH_HISTORY) {
            pathHistory.removeLast()
        }
        advancing = true

        // Collect any power-up on the cell the head just entered
        val idx = powerUps.indexOfFirst { it.x == playerX && it.y == playerY }
        if (idx >= 0) applyPowerUp(powerUps.removeAt(idx))
    }

    /** Applies a collected power-up's effect and records the pickup event. */
    private fun applyPowerUp(powerUp: PowerUp) {
        when (powerUp.type) {
            PowerUpType.SHIELD -> shieldActive = true
            PowerUpType.FREEZE -> freezeRemaining = FREEZE_SECONDS
            PowerUpType.SLOW -> slowRemaining = SLOW_SECONDS
        }
        lastCollectedPowerUp = powerUp.type
        powerUpCollectedCount++
    }

    /**
     * Spawns one power-up on a random EMPTY interior cell that is clear of the
     * player, the trail, and any enemy. Does nothing if the field is full.
     */
    private fun spawnPowerUp() {
        if (powerUps.size >= MAX_POWERUPS) return

        val enemyCells = enemies.map {
            Pair(floor(it.x).toInt().coerceIn(0, width - 1), floor(it.y).toInt().coerceIn(0, height - 1))
        }.toHashSet()

        repeat(24) { // a handful of attempts to find a free cell
            val px = powerUpRandom.nextInt(2, width - 2)
            val py = powerUpRandom.nextInt(2, height - 2)
            val free = grid[px][py] == GridCellState.EMPTY &&
                !(px == playerX && py == playerY) &&
                enemyCells.none { it.first == px && it.second == py } &&
                powerUps.none { it.x == px && it.y == py }
            if (free) {
                val type = PowerUpType.entries[powerUpRandom.nextInt(PowerUpType.entries.size)]
                powerUps.add(PowerUp(powerUpIdCounter++, type, px, py))
                return
            }
        }
    }

    /**
     * Handles the crash event when the player hits an enemy, their trail, or crosses their own trail.
     */
    private fun handleCrash() {
        // A shield absorbs the hit: lose the trail and reset, but keep the life.
        val shielded = shieldActive
        if (shielded) shieldActive = false else lives--
        crashCount++
        playerDirection = Direction.NONE
        isDrawing = false
        advancing = false
        moveProgress = 1.0
        // A crash breaks the combo chain
        scoreMultiplier = 1
        comboTimeRemaining = 0.0

        // Clear the unfinished trail and restore those cells back to EMPTY
        if (trail.isNotEmpty()) {
            for (cell in trail) {
                grid[cell.first][cell.second] = GridCellState.EMPTY
            }
            gridVersion++
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

        // The head teleports on reset, so the body must not lag across the field
        pathHistory.clear()
        pathHistory.addFirst(Pair(playerX, playerY))
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
