package com.example.engine

/**
 * Directional movement vectors for the grid.
 */
enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    NONE(0, 0)
}

/**
 * Player entity representing the cursor position, direction, and drawing state.
 */
data class Player(
    val x: Int = 20,
    val y: Int = 0,
    val currentDirection: Direction = Direction.NONE,
    val isDrawing: Boolean = false
)
