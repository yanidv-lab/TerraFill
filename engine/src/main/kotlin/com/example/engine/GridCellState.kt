package com.example.engine

/**
 * Represents the three possible states of each playfield grid cell in TerraFill.
 */
enum class GridCellState {
    /** Uncaptured territory. Enemies float around freely in this area. */
    EMPTY,

    /** Captured territory. Safe for player cursor, impassable for standard bouncer enemies. */
    CAPTURED,

    /** Temporary trail drawn by the player cursor while traversing empty territory. */
    TRAIL
}
