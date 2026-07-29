package com.example.engine

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.random.Random

/**
 * An in-flight web projectile fired by a Spitter. Coordinates use the same cell
 * space as enemies (the visual centre sits at +0.5). Read-only snapshot for the UI.
 */
data class WebShot(val x: Double, val y: Double, val vx: Double, val vy: Double)

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

    private var nextEnemyId = 1
    // Seeded from the clock, not the level number: a level-derived seed made every
    // attempt at a given level - including an immediate retry - spawn enemies and
    // power-ups in exactly the same places doing exactly the same thing, so a retry
    // never felt different from the run that just failed.
    private val broodRandom = Random(System.nanoTime())
    private var powerUpSpawnTimer = POWERUP_FIRST_SPAWN
    private var powerUpIdCounter = 1
    private val powerUpRandom = Random(System.nanoTime() xor 0x5DEECE66DL)

    /** Star rating (0-3) for the level, valid once status is LEVEL_COMPLETE. */
    var stars = 0
        private set

    /**
     * Star CURRENCY paid out for this completion (scaled by how much of the board was
     * claimed and by the level number). Valid once status is LEVEL_COMPLETE. Distinct
     * from [stars], which is the 0-3 performance rating shown on the level badge.
     */
    var starsEarned = 0
        private set

    private var startingLives = initialLives

    // Enemy state
    val enemies = mutableListOf<Enemy>()

    // Web projectiles fired by Spitters. Internal mutable list + read-only snapshot.
    private class WebProjectile(var x: Double, var y: Double, val vx: Double, val vy: Double, var life: Double)
    private val activeWebs = mutableListOf<WebProjectile>()
    /** Read-only snapshot of in-flight webs for the UI, refreshed every tick. */
    var webs: List<WebShot> = emptyList()
        private set

    /**
     * Permanent sticky web patches spun by Weavers. Touching one is fatal. They only
     * sit on open ground, and claiming a region wipes any patch inside it - so
     * capturing territory is also how the board gets cleaned up.
     */
    var webTraps: List<Pair<Int, Int>> = emptyList()
        private set
    private val activeTraps = LinkedHashSet<Pair<Int, Int>>()

    /** Monotonic counter of spun traps, so the UI can animate each new one. */
    var webTrapCount = 0
        private set

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

        /** Web projectile tuning. */
        const val WEB_RADIUS = 0.34             // collision radius of a web glob (cells)
        const val WEB_LIFE = 4.0                // seconds before a web dissipates

        /** Weaver / Broodmother limits that keep late levels winnable. */
        const val MAX_WEB_TRAPS = 10
        const val MAX_SPIDERLINGS = 4

        /** 4-way steps used when searching for a free cell. */
        val NEIGHBOURS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
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
        // Seeded from the clock: a level-derived seed made every attempt at a level -
        // fresh start or immediate retry alike - place every enemy at the exact same
        // spot doing the exact same thing, so a retry never felt like a new attempt.
        val random = Random(System.nanoTime() xor 0x2545F4914F6CDD1DL)
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

        // Spawn Eaters (wall-devouring spiders): slow drift, seeded away from the border.
        for (i in 0 until levelConfig.eaterCount) {
            val rx = random.nextDouble(5.0, (width - 6).toDouble())
            val ry = random.nextDouble(5.0, (height - 6).toDouble())
            val angle = random.nextDouble(0.0, 2.0 * Math.PI)
            val speed = levelConfig.enemySpeed * 0.5   // deliberately sluggish
            val vx = speed * kotlin.math.cos(angle)
            val vy = speed * kotlin.math.sin(angle)

            enemies.add(Eater(idCounter++, rx, ry, vx, vy))
        }

        // Spawn Spitters (stationary web-shooters) in the lower half, so the player has
        // a moment before they're in range, spread across the width.
        for (i in 0 until levelConfig.spitterCount) {
            val rx = random.nextDouble(4.0, (width - 5).toDouble())
            val ry = random.nextDouble((height * 0.45), (height - 5).toDouble())
            enemies.add(Spitter(idCounter++, rx, ry, aggression = levelConfig.enemyAggression))
        }

        // Spawn Weavers (web-trap spinners): slow drifters.
        for (i in 0 until levelConfig.weaverCount) {
            val rx = random.nextDouble(5.0, (width - 6).toDouble())
            val ry = random.nextDouble(5.0, (height - 6).toDouble())
            val angle = random.nextDouble(0.0, 2.0 * Math.PI)
            val speed = levelConfig.enemySpeed * 0.55
            enemies.add(
                Weaver(
                    idCounter++, rx, ry,
                    speed * kotlin.math.cos(angle), speed * kotlin.math.sin(angle),
                    aggression = levelConfig.enemyAggression
                )
            )
        }

        // Spawn Hornets (fast fliers that ignore walls entirely).
        for (i in 0 until levelConfig.hornetCount) {
            val rx = random.nextDouble(3.0, (width - 4).toDouble())
            val ry = random.nextDouble(3.0, (height - 4).toDouble())
            val angle = random.nextDouble(0.0, 2.0 * Math.PI)
            val speed = levelConfig.enemySpeed * 1.5
            enemies.add(
                Hornet(
                    idCounter++, rx, ry,
                    speed * kotlin.math.cos(angle), speed * kotlin.math.sin(angle)
                )
            )
        }

        // Spawn Phantoms (slow wall-passing ghosts) far from the player's spawn.
        for (i in 0 until levelConfig.phantomCount) {
            val rx = random.nextDouble(4.0, (width - 5).toDouble())
            val ry = random.nextDouble((height * 0.6), (height - 4).toDouble())
            val angle = random.nextDouble(0.0, 2.0 * Math.PI)
            val speed = levelConfig.enemySpeed * 0.42
            enemies.add(
                Phantom(
                    idCounter++, rx, ry,
                    speed * kotlin.math.cos(angle), speed * kotlin.math.sin(angle)
                )
            )
        }

        // Spawn Broodmothers (slow queens that hatch spiderlings).
        for (i in 0 until levelConfig.broodmotherCount) {
            val rx = random.nextDouble(6.0, (width - 7).toDouble())
            val ry = random.nextDouble((height * 0.5), (height - 6).toDouble())
            val angle = random.nextDouble(0.0, 2.0 * Math.PI)
            val speed = levelConfig.enemySpeed * 0.4
            enemies.add(
                Broodmother(
                    idCounter++, rx, ry,
                    speed * kotlin.math.cos(angle), speed * kotlin.math.sin(angle),
                    aggression = levelConfig.enemyAggression
                )
            )
        }

        nextEnemyId = idCounter
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
                enemy.advanceFacing(enemyDt)
            }
            // Eaters mutate the grid directly; refresh the version + percentage once.
            if (enemies.any { it.ateWall }) {
                enemies.forEach { it.ateWall = false }
                gridVersion++
                recalculateCapturedPercentage()
            }
            // Spitters may have launched webs this tick; spawn them from the enemy centre.
            for (enemy in enemies) {
                val spit = enemy.consumePendingSpit() ?: continue
                activeWebs.add(WebProjectile(enemy.x, enemy.y, spit[0], spit[1], WEB_LIFE))
            }

            // Weavers leave sticky patches; Broodmothers hatch spiderlings.
            handleWeaverTraps()
            handleBroodHatching()
        }

        // 2b. Advance web projectiles (frozen/slowed with the rest of the enemies).
        updateWebs(enemyDt)

        // 3. Check enemy-player direct collision, trail collision, or a web hit
        if (checkEnemyCollisions() || checkWebCollisions() || checkWebTrapCollision()) {
            handleCrash()
            return
        }

        // 4. Update Player movement on grid (tick-rate independent accumulator)
        playerMoveTimer += dt
        while (playerMoveTimer >= playerMoveInterval) {
            playerMoveTimer -= playerMoveInterval
            performPlayerGridStep()

            // Re-check collisions after the player moves
            if (status == GameStateStatus.RUNNING &&
                (checkEnemyCollisions() || checkWebCollisions() || checkWebTrapCollision())
            ) {
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

        // Standing on reclaimed land with no trail out is SAFE ground: the roaming
        // spiders live in the open territory and cannot reach the player there, even
        // when their sprite brushes the boundary. Only the wall-hugging Crawler
        // patrols the claimed edge, so it alone stays lethal on the surface. Once the
        // player steps out (or is drawing a trail), everything is dangerous again.
        val onSafeGround = !isDrawing &&
            playerX in 0 until width && playerY in 0 until height &&
            grid[playerX][playerY] == GridCellState.CAPTURED

        for (enemy in enemies) {
            // Enemy positions are cell coordinates; the visual center sits at +0.5.
            val ecx = enemy.x + 0.5
            val ecy = enemy.y + 0.5
            val r = enemy.radius

            // Direct collision with the player square (approximated as a circle)
            val threatensPlayer = !onSafeGround || enemy.threatensSafeGround
            if (threatensPlayer) {
                val dx = ecx - playerCx
                val dy = ecy - playerCy
                val hitDistance = r + PLAYER_HALF_SIZE
                if (dx * dx + dy * dy < hitDistance * hitDistance) {
                    return true
                }
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
     * Moves every web projectile, sub-stepped so fast webs can't tunnel through thin
     * walls. A web dies when it leaves the field, strikes CAPTURED land (absorbed by
     * the wall - so claimed territory is cover), or its lifetime expires. Refreshes the
     * public [webs] snapshot.
     */
    private fun updateWebs(dt: Double) {
        if (activeWebs.isNotEmpty() && dt > 0.0) {
            val iter = activeWebs.iterator()
            while (iter.hasNext()) {
                val w = iter.next()
                val distance = hypot(w.vx, w.vy) * dt
                val steps = ceil(distance / 0.5).toInt().coerceAtLeast(1)
                val sdt = dt / steps
                var dead = false
                var s = 0
                while (s < steps && !dead) {
                    w.x += w.vx * sdt
                    w.y += w.vy * sdt
                    val gx = floor(w.x + 0.5).toInt()
                    val gy = floor(w.y + 0.5).toInt()
                    if (gx !in 0 until width || gy !in 0 until height ||
                        grid[gx][gy] == GridCellState.CAPTURED
                    ) {
                        dead = true
                    }
                    s++
                }
                w.life -= dt
                if (dead || w.life <= 0.0) iter.remove()
            }
        }
        webs = if (activeWebs.isEmpty()) emptyList()
        else activeWebs.map { WebShot(it.x, it.y, it.vx, it.vy) }
    }

    /**
     * Places a sticky patch under any Weaver that finished spinning. Patches only
     * stick to open ground (never claimed land, never the player's own cell) and are
     * capped so the board can always be finished - the oldest patch decays away when
     * a new one would exceed the cap.
     */
    private fun handleWeaverTraps() {
        for (enemy in enemies) {
            if (!enemy.consumePendingWebTrap()) continue
            val tx = floor(enemy.x).toInt().coerceIn(0, width - 1)
            val ty = floor(enemy.y).toInt().coerceIn(0, height - 1)
            if (grid[tx][ty] != GridCellState.EMPTY) continue
            if (tx == playerX && ty == playerY) continue
            if (!activeTraps.add(Pair(tx, ty))) continue
            if (activeTraps.size > MAX_WEB_TRAPS) {
                activeTraps.iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
            }
            webTraps = activeTraps.toList()
            webTrapCount++
        }
    }

    /**
     * Hatches a spiderling beside any Broodmother that finished brooding, as long as
     * the brood has not already reached its cap (a queen left alone would otherwise
     * fill the board and make the level unwinnable).
     */
    private fun handleBroodHatching() {
        for (enemy in enemies.toList()) {
            if (!enemy.consumePendingSpawn()) continue
            if (enemies.count { it.type == "Spiderling" } >= MAX_SPIDERLINGS) continue
            val angle = broodRandom.nextDouble(0.0, 2.0 * Math.PI)
            val speed = levelConfig.enemySpeed * 1.15
            val sx = (enemy.x + kotlin.math.cos(angle)).coerceIn(1.0, (width - 2).toDouble())
            val sy = (enemy.y + kotlin.math.sin(angle)).coerceIn(1.0, (height - 2).toDouble())
            enemies.add(
                Spiderling(
                    id = nextEnemyId++,
                    x = sx, y = sy,
                    vx = speed * kotlin.math.cos(angle),
                    vy = speed * kotlin.math.sin(angle)
                )
            )
        }
    }

    /** True if the player is standing on a sticky web patch left by a Weaver. */
    private fun checkWebTrapCollision(): Boolean {
        if (activeTraps.isEmpty()) return false
        return activeTraps.contains(Pair(playerX, playerY))
    }

    /** True if any web projectile currently overlaps the player. */
    private fun checkWebCollisions(): Boolean {
        if (activeWebs.isEmpty()) return false
        // Same safe-ground rule as spiders: webs are stopped by claimed land, so a
        // player standing on it cannot be hit.
        if (!isDrawing && playerX in 0 until width && playerY in 0 until height &&
            grid[playerX][playerY] == GridCellState.CAPTURED
        ) return false
        val pcx = playerX + 0.5
        val pcy = playerY + 0.5
        val hitDistance = WEB_RADIUS + PLAYER_HALF_SIZE
        for (w in activeWebs) {
            val dx = (w.x + 0.5) - pcx
            val dy = (w.y + 0.5) - pcy
            if (dx * dx + dy * dy < hitDistance * hitDistance) return true
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

                    // Claiming land also sweeps away any web traps inside it.
                    if (activeTraps.isNotEmpty()) {
                        val cleaned = activeTraps.removeAll { (tx, ty) ->
                            grid[tx][ty] == GridCellState.CAPTURED
                        }
                        if (cleaned) webTraps = activeTraps.toList()
                    }

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
                        starsEarned = StarEconomy.award(
                            level = levelConfig.levelNumber,
                            capturedPercentage = capturedPercentage
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

        // Clear in-flight webs so the player isn't instantly re-hit on reset.
        activeWebs.clear()
        webs = emptyList()
        // A fresh life also clears the sticky patches and any hatched brood, so the
        // player is never handed an already-poisoned board.
        activeTraps.clear()
        webTraps = emptyList()
        enemies.removeAll { it.type == "Spiderling" }

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
     * Serializes the claimed territory as a compact '1'/'0' mask in column-major
     * order, for mid-level saves. Trail cells are written as open ground: a resumed
     * run always restarts the player safely on the border with no trail.
     */
    fun exportCapturedMask(): String {
        val sb = StringBuilder(width * height)
        for (x in 0 until width) {
            for (y in 0 until height) {
                sb.append(if (grid[x][y] == GridCellState.CAPTURED) '1' else '0')
            }
        }
        return sb.toString()
    }

    /**
     * Serializes each live enemy's type and kinematic state as "Type,x,y,vx,vy"
     * records joined by ';', for mid-level saves - so a resumed run finds the exact
     * spiders it left behind (same positions, same count), not a freshly regenerated
     * roster. Per-type timers (a Weaver's spin charge, a Spitter's aim, a
     * Broodmother's brood clock) are not carried over; they simply restart their
     * cycle from scratch, which is unnoticeable on resume and keeps the format simple.
     */
    fun exportEnemies(): String =
        enemies.joinToString(";") { "${it.type},${it.x},${it.y},${it.vx},${it.vy}" }

    /**
     * Rebuilds one enemy of [type] with the given kinematic state, using this level's
     * aggression for the types that read it - the same value a fresh spawn of that
     * type would have received. Returns null for an unrecognised type, so a corrupt
     * or future-format record is simply dropped rather than crashing the restore.
     */
    private fun buildEnemy(type: String, id: Int, x: Double, y: Double, vx: Double, vy: Double): Enemy? {
        val aggression = levelConfig.enemyAggression
        return when (type) {
            "Bouncer" -> Bouncer(id, x, y, vx, vy)
            "Crawler" -> Crawler(id, x, y, vx, vy)
            "Jumper" -> Jumper(id, x, y, vx, vy, aggression = aggression)
            "Hunter" -> Hunter(id, x, y, vx, vy, aggression = aggression)
            "Eater" -> Eater(id, x, y, vx, vy)
            "Spitter" -> Spitter(id, x, y, vx, vy, aggression = aggression)
            "Speeder" -> Speeder(id, x, y, vx, vy)
            "Weaver" -> Weaver(id, x, y, vx, vy, aggression = aggression)
            "Hornet" -> Hornet(id, x, y, vx, vy)
            "Phantom" -> Phantom(id, x, y, vx, vy)
            "Broodmother" -> Broodmother(id, x, y, vx, vy, aggression = aggression)
            "Spiderling" -> Spiderling(id, x, y, vx, vy)
            else -> null
        }
    }

    /**
     * Adds shop-bought spare lives to a run that has already begun.
     *
     * The bank of spare lives lives in storage and is read asynchronously, so it can
     * only be applied a moment after the level starts. Granting here (rather than
     * through the constructor) means the level never has to wait on a disk read, and
     * a slow read can never silently drop a life the player paid for.
     *
     * The starting-lives baseline moves with the grant, so the flawless-run star is
     * still only awarded for finishing without losing any life.
     */
    fun grantExtraLives(count: Int) {
        if (count <= 0) return
        lives += count
        startingLives += count
    }

    /**
     * Restores a mid-level save produced by [exportCapturedMask]: the claimed board,
     * score, lives and remaining time. The player returns to the safe spawn with no
     * trail. Returns false and changes nothing if the mask does not match this
     * board's size - the caller must not treat the save as consumed in that case,
     * since nothing was actually restored.
     *
     * [savedEnemies], from [exportEnemies], replaces the freshly-spawned roster with
     * the actual spiders the player left behind - same positions, same count. Left
     * blank (the default), the fresh roster is kept as-is, only relocated if the
     * board changed underneath it; this is what lets an older save without an enemy
     * field still restore cleanly.
     */
    fun restoreSnapshot(
        capturedMask: String,
        savedScore: Int,
        savedLives: Int,
        savedTime: Double,
        savedEnemies: String = ""
    ): Boolean {
        if (capturedMask.length != width * height) return false

        var i = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                grid[x][y] = if (capturedMask[i] == '1') GridCellState.CAPTURED else GridCellState.EMPTY
                i++
            }
        }
        // The border is always claimed, whatever the save said.
        for (x in 0 until width) {
            grid[x][0] = GridCellState.CAPTURED
            grid[x][height - 1] = GridCellState.CAPTURED
        }
        for (y in 0 until height) {
            grid[0][y] = GridCellState.CAPTURED
            grid[width - 1][y] = GridCellState.CAPTURED
        }

        score = savedScore
        lives = savedLives.coerceAtLeast(1)
        timeRemainingSeconds = savedTime.coerceIn(1.0, levelConfig.timeLimitSeconds.toDouble())

        trail.clear()
        isDrawing = false
        playerDirection = Direction.NONE
        playerX = width / 2
        playerY = 0
        pathHistory.clear()
        pathHistory.addFirst(Pair(playerX, playerY))
        activeWebs.clear()
        webs = emptyList()

        // Rebuild the exact roster the player left behind, if the save carries one.
        if (savedEnemies.isNotEmpty()) {
            val restored = mutableListOf<Enemy>()
            for (record in savedEnemies.split(";")) {
                if (record.isBlank()) continue
                val parts = record.split(",")
                if (parts.size != 5) continue
                val x = parts[1].toDoubleOrNull()
                val y = parts[2].toDoubleOrNull()
                val vx = parts[3].toDoubleOrNull()
                val vy = parts[4].toDoubleOrNull()
                if (x == null || y == null || vx == null || vy == null) continue
                restored.add(buildEnemy(parts[0], nextEnemyId++, x, y, vx, vy) ?: continue)
            }
            if (restored.isNotEmpty()) {
                enemies.clear()
                enemies.addAll(restored)
            }
        }

        // Whichever roster is now in play - restored or the fresh one - may have a
        // spawn point sitting inside reclaimed land, where it would be walled in
        // forever, since the board can have changed shape or grown since that spawn.
        relocateTrappedEnemies()
        gridVersion++
        recalculateCapturedPercentage()
        return true
    }

    /**
     * Moves any enemy that is standing inside CAPTURED territory out to the nearest
     * open cell. An enemy embedded in claimed land is blocked on every side, so it
     * bounces on the spot and never moves again - a soft-lock that can only arise
     * when the board changes underneath the enemies (i.e. restoring a saved run).
     */
    private fun relocateTrappedEnemies() {
        for (enemy in enemies) {
            val ex = floor(enemy.x).toInt().coerceIn(0, width - 1)
            val ey = floor(enemy.y).toInt().coerceIn(0, height - 1)
            if (grid[ex][ey] != GridCellState.CAPTURED) continue

            val spot = findNearestOpenCell(ex, ey) ?: continue
            enemy.x = spot.first.toDouble()
            enemy.y = spot.second.toDouble()
        }
    }

    /** Breadth-first search for the closest non-CAPTURED cell, or null if none exists. */
    private fun findNearestOpenCell(startX: Int, startY: Int): Pair<Int, Int>? {
        val visited = Array(width) { BooleanArray(height) }
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.addLast(Pair(startX, startY))
        visited[startX][startY] = true

        while (queue.isNotEmpty()) {
            val (cx, cy) = queue.removeFirst()
            if (grid[cx][cy] != GridCellState.CAPTURED) return Pair(cx, cy)
            for ((dx, dy) in NEIGHBOURS) {
                val nx = cx + dx
                val ny = cy + dy
                if (nx in 0 until width && ny in 0 until height && !visited[nx][ny]) {
                    visited[nx][ny] = true
                    queue.addLast(Pair(nx, ny))
                }
            }
        }
        return null
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
