package com.example.engine

import java.util.ArrayDeque
import kotlin.math.floor

/**
 * Flood Fill and Region Evaluation utilities for TerraFill.
 *
 * This algorithm runs after the player connects their trail back to safe captured territory.
 * It identifies all disconnected EMPTY regions of the playfield, checks if any enemies
 * reside in each region, and captures any region containing NO enemies.
 */
object FloodFill {

    /**
     * Evaluates the entire playfield grid to find disconnected EMPTY regions and capture
     * those that contain no enemies.
     *
     * @param grid The 2D playfield grid array of size [width][height].
     * @param enemies The list of active floating-point enemies.
     * @return The newly captured cells (empty list if nothing was captured), so callers
     *         can score them and drive capture animations.
     */
    fun evaluateAndCaptureRegions(
        grid: Array<Array<GridCellState>>,
        enemies: List<Enemy>
    ): List<Pair<Int, Int>> {
        val width = grid.size
        val height = if (width > 0) grid[0].size else 0
        if (width == 0 || height == 0) return emptyList()

        val visited = Array(width) { BooleanArray(height) { false } }
        val newlyCaptured = mutableListOf<Pair<Int, Int>>()

        // Find enemy grid positions for fast lookup
        val enemyPositions = enemies.map { enemy ->
            val ex = floor(enemy.x).toInt().coerceIn(0, width - 1)
            val ey = floor(enemy.y).toInt().coerceIn(0, height - 1)
            Pair(ex, ey)
        }.toSet()

        for (x in 0 until width) {
            for (y in 0 until height) {
                // If we find an unvisited EMPTY cell, start a BFS to gather all cells in this region
                if (grid[x][y] == GridCellState.EMPTY && !visited[x][y]) {
                    val regionCells = performBFS(grid, x, y, visited, width, height)
                    
                    // Check if this region contains any enemy
                    val containsEnemy = regionCells.any { cell ->
                        enemyPositions.contains(cell)
                    }

                    // If the region contains no enemies, capture all its cells
                    if (!containsEnemy) {
                        for (cell in regionCells) {
                            grid[cell.first][cell.second] = GridCellState.CAPTURED
                            newlyCaptured.add(cell)
                        }
                    }
                }
            }
        }

        return newlyCaptured
    }

    /**
     * Performs a Breadth-First Search (BFS) to gather all contiguous EMPTY cells forming a region.
     */
    private fun performBFS(
        grid: Array<Array<GridCellState>>,
        startX: Int,
        startY: Int,
        visited: Array<BooleanArray>,
        width: Int,
        height: Int
    ): List<Pair<Int, Int>> {
        val cells = mutableListOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(Pair(startX, startY))
        visited[startX][startY] = true

        val dx = intArrayOf(0, 0, -1, 1)
        val dy = intArrayOf(-1, 1, 0, 0)

        while (!queue.isEmpty()) {
            val curr = queue.poll() ?: break
            cells.add(curr)

            for (i in 0 until 4) {
                val nx = curr.first + dx[i]
                val ny = curr.second + dy[i]

                if (nx in 0 until width && ny in 0 until height) {
                    if (grid[nx][ny] == GridCellState.EMPTY && !visited[nx][ny]) {
                        visited[nx][ny] = true
                        queue.add(Pair(nx, ny))
                    }
                }
            }
        }

        return cells
    }
}
