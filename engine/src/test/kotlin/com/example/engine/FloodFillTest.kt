package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloodFillTest {

    @Test
    fun testNoCapturedRegionsWhenEnemyIsPresent() {
        // Create a 10x10 grid with outer boundaries as CAPTURED and the inside as EMPTY
        val width = 10
        val height = 10
        val grid = Array(width) { x ->
            Array(height) { y ->
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    GridCellState.CAPTURED
                } else {
                    GridCellState.EMPTY
                }
            }
        }

        // Add 1 enemy right in the center (5, 5)
        val enemies = listOf(
            Bouncer(id = 1, x = 5.0, y = 5.0, vx = 1.0, vy = 1.0)
        )

        // Run evaluateAndCaptureRegions
        val captured = FloodFill.evaluateAndCaptureRegions(grid, enemies)

        // Since there is only one big empty region and it contains an enemy, no cells should be captured
        assertEquals(0, captured.size)

        // Ensure all inner cells are still EMPTY
        for (x in 1 until width - 1) {
            for (y in 1 until height - 1) {
                assertEquals(GridCellState.EMPTY, grid[x][y])
            }
        }
    }

    @Test
    fun testRegionCapturedWhenNoEnemiesArePresent() {
        // Create a 10x10 grid where the left half is EMPTY and the right half is divided by a CAPTURED line
        val width = 10
        val height = 10
        val grid = Array(width) { x ->
            Array(height) { y ->
                // Border
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    GridCellState.CAPTURED
                } else if (x == 5) {
                    // Wall splitting the grid into left (1 to 4) and right (6 to 8) regions
                    GridCellState.CAPTURED
                } else {
                    GridCellState.EMPTY
                }
            }
        }

        // Put an enemy in the right region (7, 5)
        val enemies = listOf(
            Bouncer(id = 1, x = 7.2, y = 5.1, vx = 1.0, vy = 1.0)
        )

        // Left region is empty of enemies. Width of left region is 4 (from x=1 to 4) and height is 8 (from y=1 to 8)
        // Total empty cells in left region: 4 * 8 = 32
        // Total empty cells in right region: 3 * 8 = 24

        val captured = FloodFill.evaluateAndCaptureRegions(grid, enemies)

        // The left region should be fully captured because it has no enemies!
        assertEquals(32, captured.size)

        // Verify left region is indeed captured
        for (x in 1..4) {
            for (y in 1..8) {
                assertEquals(GridCellState.CAPTURED, grid[x][y])
            }
        }

        // Verify right region is still empty
        for (x in 6..8) {
            for (y in 1..8) {
                assertEquals(GridCellState.EMPTY, grid[x][y])
            }
        }
    }

    @Test
    fun testAllRegionsCapturedIfNoEnemiesExist() {
        val width = 10
        val height = 10
        val grid = Array(width) { x ->
            Array(height) { y ->
                if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    GridCellState.CAPTURED
                } else if (x == 5) {
                    GridCellState.CAPTURED
                } else {
                    GridCellState.EMPTY
                }
            }
        }

        // No enemies at all
        val enemies = emptyList<Enemy>()

        val captured = FloodFill.evaluateAndCaptureRegions(grid, enemies)

        // Both regions should be captured: 32 + 24 = 56 cells
        assertEquals(56, captured.size)

        // Verify whole grid (except the separator wall and border which are already CAPTURED) is CAPTURED
        for (x in 0 until width) {
            for (y in 0 until height) {
                assertEquals(GridCellState.CAPTURED, grid[x][y])
            }
        }
    }
}
