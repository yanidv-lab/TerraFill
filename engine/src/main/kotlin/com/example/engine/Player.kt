package com.example.engine

/**
 * Directional movement vectors for the grid.
 */
enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    DOWN(0, 1),
    LEFT(-1, 0),
    RIGHT(1, 0),
    NONE(0, 0);

    /** The opposite direction (UP <-> DOWN, LEFT <-> RIGHT). */
    fun opposite(): Direction = when (this) {
        UP -> DOWN
        DOWN -> UP
        LEFT -> RIGHT
        RIGHT -> LEFT
        NONE -> NONE
    }

    /** 90 degrees clockwise in screen coordinates (y grows downward): UP -> RIGHT -> DOWN -> LEFT. */
    fun clockwise(): Direction = when (this) {
        UP -> RIGHT
        RIGHT -> DOWN
        DOWN -> LEFT
        LEFT -> UP
        NONE -> NONE
    }

    /** 90 degrees counter-clockwise in screen coordinates: UP -> LEFT -> DOWN -> RIGHT. */
    fun counterClockwise(): Direction = when (this) {
        UP -> LEFT
        LEFT -> DOWN
        DOWN -> RIGHT
        RIGHT -> UP
        NONE -> NONE
    }
}
