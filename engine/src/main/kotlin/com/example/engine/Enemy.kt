package com.example.engine

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Moves an enemy along its velocity, bouncing off the field edges and CAPTURED
 * cells. Movement is broken into sub-steps no larger than half a cell so fast
 * enemies (e.g. a jumping spider mid-leap) can't tunnel through thin walls.
 * Mutates the enemy's x/y/vx/vy in place.
 */
private fun Enemy.stepBounce(grid: Array<Array<GridCellState>>, dt: Double) {
    val width = grid.size
    val height = if (width > 0) grid[0].size else 0
    if (width == 0 || height == 0) return

    val distance = hypot(vx, vy) * dt
    val steps = ceil(distance / 0.5).toInt().coerceAtLeast(1)
    val sdt = dt / steps

    repeat(steps) {
        val nextX = x + vx * sdt
        val gridX = floor(nextX).toInt().coerceIn(0, width - 1)
        val currentGridY = floor(y).toInt().coerceIn(0, height - 1)
        if (nextX < 0 || nextX >= width || grid[gridX][currentGridY] == GridCellState.CAPTURED) {
            vx = -vx
        } else {
            x = nextX
        }

        val nextY = y + vy * sdt
        val currentGridX = floor(x).toInt().coerceIn(0, width - 1)
        val gridY = floor(nextY).toInt().coerceIn(0, height - 1)
        if (nextY < 0 || nextY >= height || grid[currentGridX][gridY] == GridCellState.CAPTURED) {
            vy = -vy
        } else {
            y = nextY
        }
    }
}

/**
 * Base representation of enemies in TerraFill.
 * Positions are floating point for smooth rendering, but are mapped to grid coordinates.
 */
sealed class Enemy {
    abstract val id: Int
    abstract var x: Double
    abstract var y: Double
    abstract var vx: Double
    abstract var vy: Double
    abstract val radius: Double
    abstract val type: String

    /**
     * Updates the enemy position, handles collision/bouncing off borders and CAPTURED cells.
     */
    abstract fun update(grid: Array<Array<GridCellState>>, dt: Double)

    /**
     * Helper to clone the enemy instance.
     */
    abstract fun copyWith(x: Double = this.x, y: Double = this.y, vx: Double = this.vx, vy: Double = this.vy): Enemy
}

/**
 * Bouncer enemy - moves in straight lines and bounces off captured territory edges.
 */
class Bouncer(
    override val id: Int,
    override var x: Double,
    override var y: Double,
    override var vx: Double,
    override var vy: Double,
    override val radius: Double = 0.4
) : Enemy() {
    override val type: String = "Bouncer"

    override fun update(grid: Array<Array<GridCellState>>, dt: Double) {
        stepBounce(grid, dt)
    }

    override fun copyWith(x: Double, y: Double, vx: Double, vy: Double): Enemy {
        return Bouncer(id, x, y, vx, vy, radius)
    }
}

/**
 * Jumper enemy - a jumping spider. It drifts slowly like a bouncer, then
 * periodically coils and launches a fast leap in a new direction before settling
 * again. The unpredictable bursts make it much harder to read than a bouncer.
 *
 * Behaviour is deterministic per enemy (seeded by [id]) so it is fully testable
 * and identical every run of a given level.
 */
class Jumper(
    override val id: Int,
    override var x: Double,
    override var y: Double,
    override var vx: Double,
    override var vy: Double,
    override val radius: Double = 0.42
) : Enemy() {
    override val type: String = "Jumper"

    private val rng = Random(id.toLong() * 2654435761L)
    private val cruiseSpeed = hypot(vx, vy).coerceAtLeast(1.0)

    /** Seconds until the next leap while cruising. */
    private var cooldown = 1.2 + rng.nextDouble() * 1.6

    /** Seconds of leap remaining; > 0 means mid-jump (fast). */
    private var leapRemaining = 0.0

    /** Fraction in [0,1] of the current leap, exposed so the UI can animate the pounce. */
    var leapProgress = 0.0
        private set

    override fun update(grid: Array<Array<GridCellState>>, dt: Double) {
        if (leapRemaining > 0.0) {
            leapRemaining -= dt
            leapProgress = (1.0 - (leapRemaining / LEAP_DURATION)).coerceIn(0.0, 1.0)
            if (leapRemaining <= 0.0) {
                // Land: settle back to a gentle drift in a fresh direction.
                aim(cruiseSpeed)
                cooldown = 1.4 + rng.nextDouble() * 1.8
                leapProgress = 0.0
            }
        } else {
            cooldown -= dt
            if (cooldown <= 0.0) {
                // Coil and pounce: launch a fast leap in a new random direction.
                aim(cruiseSpeed * LEAP_SPEED_FACTOR)
                leapRemaining = LEAP_DURATION
                leapProgress = 0.0
            }
        }
        stepBounce(grid, dt)
    }

    private fun aim(speed: Double) {
        val angle = rng.nextDouble(0.0, 2.0 * Math.PI)
        vx = cos(angle) * speed
        vy = sin(angle) * speed
    }

    override fun copyWith(x: Double, y: Double, vx: Double, vy: Double): Enemy {
        return Jumper(id, x, y, vx, vy, radius)
    }

    private companion object {
        const val LEAP_DURATION = 0.32          // seconds of fast flight
        const val LEAP_SPEED_FACTOR = 3.2       // leap speed relative to cruise
    }
}

/**
 * Crawler enemy - deterministically patrols the open side of captured territory,
 * hugging walls with a right-hand rule. When it isn't touching a wall it moves in
 * straight lines until it reaches one, then follows the boundary - wrapping around
 * outer corners and turning at inner corners. Because capture reshapes the walls,
 * crawlers naturally adapt their patrol route as the player claims land.
 *
 * The crawler treats TRAIL cells as open ground, so it can run straight over the
 * player's unfinished trail - which triggers a crash via the engine's collision check.
 */
class Crawler(
    override val id: Int,
    override var x: Double,
    override var y: Double,
    override var vx: Double,
    override var vy: Double,
    override val radius: Double = 0.4
) : Enemy() {
    override val type: String = "Crawler"

    // Crawlers move slower than bouncers; speed is derived from the initial velocity vector.
    private val speed: Double = (hypot(vx, vy) * 0.6).coerceAtLeast(1.0)
    private var heading: Direction = dominantDirection(vx, vy)
    private var hasTarget = false
    private var targetX = 0
    private var targetY = 0

    override fun update(grid: Array<Array<GridCellState>>, dt: Double) {
        val width = grid.size
        val height = if (width > 0) grid[0].size else 0
        if (width == 0 || height == 0) return

        var remaining = speed * dt
        // Guard bounds the number of cell decisions in one tick (huge dt safety).
        var guard = 0
        while (remaining > 1e-9 && guard++ < 16) {
            if (!hasTarget && !chooseNextCell(grid, width, height)) {
                return // fully boxed in; stay put until the layout changes
            }
            val dx = targetX - x
            val dy = targetY - y
            val dist = abs(dx) + abs(dy) // axis-aligned movement
            if (dist <= remaining) {
                x = targetX.toDouble()
                y = targetY.toDouble()
                remaining -= dist
                hasTarget = false
            } else {
                x += dx / dist * remaining
                y += dy / dist * remaining
                remaining = 0.0
            }
        }

        // Keep the velocity vector in sync with the heading so the UI can orient the sprite.
        vx = heading.dx * speed
        vy = heading.dy * speed
    }

    private fun isOpen(grid: Array<Array<GridCellState>>, width: Int, height: Int, cx: Int, cy: Int): Boolean {
        return cx in 0 until width && cy in 0 until height && grid[cx][cy] != GridCellState.CAPTURED
    }

    /**
     * Picks the next cell using a right-hand wall-following rule. Turning toward the
     * right hand is only allowed when we were actually hugging a wall there (the cell
     * diagonally behind-right is captured); otherwise open-field movement stays straight.
     */
    private fun chooseNextCell(grid: Array<Array<GridCellState>>, width: Int, height: Int): Boolean {
        val cx = x.roundToInt()
        val cy = y.roundToInt()
        if (heading == Direction.NONE) heading = Direction.DOWN

        val right = heading.clockwise()
        val rightOpen = isOpen(grid, width, height, cx + right.dx, cy + right.dy)
        val rightRearBlocked = !isOpen(
            grid, width, height,
            cx + right.dx - heading.dx,
            cy + right.dy - heading.dy
        )

        val candidates = if (rightOpen && rightRearBlocked) {
            // The wall we were hugging just ended: wrap around the outer corner.
            listOf(right, heading, heading.counterClockwise(), heading.opposite())
        } else {
            listOf(heading, heading.counterClockwise(), right, heading.opposite())
        }

        for (dir in candidates) {
            if (dir == Direction.NONE) continue
            if (isOpen(grid, width, height, cx + dir.dx, cy + dir.dy)) {
                heading = dir
                targetX = cx + dir.dx
                targetY = cy + dir.dy
                hasTarget = true
                return true
            }
        }
        return false
    }

    override fun copyWith(x: Double, y: Double, vx: Double, vy: Double): Enemy {
        return Crawler(id, x, y, vx, vy, radius)
    }

    private companion object {
        fun dominantDirection(vx: Double, vy: Double): Direction = when {
            vx == 0.0 && vy == 0.0 -> Direction.DOWN
            abs(vx) >= abs(vy) -> if (vx > 0) Direction.RIGHT else Direction.LEFT
            else -> if (vy > 0) Direction.DOWN else Direction.UP
        }
    }
}
