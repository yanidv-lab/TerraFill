package com.example.engine

/**
 * Data-driven configuration for each level in TerraFill.
 */
data class LevelConfig(
    val levelNumber: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val bouncerCount: Int,
    val crawlerCount: Int,
    val enemySpeed: Double, // scaled speed multiplier
    val targetPercentage: Double = 75.0,
    val timeLimitSeconds: Int = 180
) {
    companion object {
        val LEVELS = listOf(
            LevelConfig(
                levelNumber = 1,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 1,
                crawlerCount = 1,
                enemySpeed = 3.5,
                targetPercentage = 75.0
            ),
            LevelConfig(
                levelNumber = 2,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 2,
                crawlerCount = 1,
                enemySpeed = 4.0,
                targetPercentage = 75.0
            ),
            LevelConfig(
                levelNumber = 3,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 3,
                crawlerCount = 2,
                enemySpeed = 4.5,
                targetPercentage = 75.0
            ),
            LevelConfig(
                levelNumber = 4,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 4,
                crawlerCount = 2,
                enemySpeed = 5.0,
                targetPercentage = 75.0
            ),
            LevelConfig(
                levelNumber = 5,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 5,
                crawlerCount = 3,
                enemySpeed = 5.5,
                targetPercentage = 75.0
            )
        )

        fun getConfig(level: Int): LevelConfig {
            val index = (level - 1).coerceIn(0, LEVELS.size - 1)
            return LEVELS[index].copy(levelNumber = level) // scale dynamically if higher
        }
    }
}
