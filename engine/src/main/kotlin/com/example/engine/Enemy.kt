package com.example.engine

import kotlin.math.floor

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
        val width = grid.size
        val height = if (width > 0) grid[0].size else 0
        if (width == 0 || height == 0) return

        // 1. Predict and update X movement
        val nextX = x + vx * dt
        val gridX = floor(nextX).toInt().coerceIn(0, width - 1)
        val currentGridY = floor(y).toInt().coerceIn(0, height - 1)

        if (grid[gridX][currentGridY] == GridCellState.CAPTURED || nextX < 0 || nextX >= width) {
            vx = -vx // Reverse horizontal direction
        } else {
            x = nextX
        }

        // 2. Predict and update Y movement
        val nextY = y + vy * dt
        val currentGridX = floor(x).toInt().coerceIn(0, width - 1)
        val gridY = floor(nextY).toInt().coerceIn(0, height - 1)

        if (grid[currentGridX][gridY] == GridCellState.CAPTURED || nextY < 0 || nextY >= height) {
            vy = -vy // Reverse vertical direction
        } else {
            y = nextY
        }
    }

    override fun copyWith(x: Double, y: Double, vx: Double, vy: Double): Enemy {
        return Bouncer(id, x, y, vx, vy, radius)
    }
}

/**
 * Crawler enemy - moves along the boundary of captured territory (edge-following).
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

    override fun update(grid: Array<Array<GridCellState>>, dt: Double) {
        val width = grid.size
        val height = if (width > 0) grid[0].size else 0
        if (width == 0 || height == 0) return

        // TODO: Implement full advanced Crawler pathfinding logic to follow captured area boundaries.
        // For now, Crawler behaves as a slower Bouncer that hugs the edges when close to them, 
        // serving as a fully functional stub.

        val speedModFactor = 0.6 // Crawlers move slower along walls
        val nextX = x + vx * speedModFactor * dt
        val gridX = floor(nextX).toInt().coerceIn(0, width - 1)
        val currentGridY = floor(y).toInt().coerceIn(0, height - 1)

        if (grid[gridX][currentGridY] == GridCellState.CAPTURED || nextX < 0 || nextX >= width) {
            vx = -vx
            // Pivot on collision to follow wall
            if (Math.random() > 0.5) {
                val temp = vx
                vx = vy
                vy = -temp
            }
        } else {
            x = nextX
        }

        val nextY = y + vy * speedModFactor * dt
        val currentGridX = floor(x).toInt().coerceIn(0, width - 1)
        val gridY = floor(nextY).toInt().coerceIn(0, height - 1)

        if (grid[currentGridX][gridY] == GridCellState.CAPTURED || nextY < 0 || nextY >= height) {
            vy = -vy
            // Pivot on collision to follow wall
            if (Math.random() > 0.5) {
                val temp = vy
                vy = vx
                vx = -temp
            }
        } else {
            y = nextY
        }
    }

    override fun copyWith(x: Double, y: Double, vx: Double, vy: Double): Enemy {
        return Crawler(id, x, y, vx, vy, radius)
    }
}
