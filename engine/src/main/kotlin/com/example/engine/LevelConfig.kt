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
                targetPercentage = 70.0
            ),
            LevelConfig(
                levelNumber = 2,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 2,
                crawlerCount = 1,
                enemySpeed = 3.8,
                targetPercentage = 72.0
            ),
            LevelConfig(
                levelNumber = 3,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 3,
                crawlerCount = 2,
                enemySpeed = 4.2,
                targetPercentage = 75.0
            ),
            LevelConfig(
                levelNumber = 4,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 4,
                crawlerCount = 2,
                enemySpeed = 4.6,
                targetPercentage = 75.0
            ),
            LevelConfig(
                levelNumber = 5,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 5,
                crawlerCount = 3,
                enemySpeed = 5.0,
                targetPercentage = 75.0
            ),
            LevelConfig(
                levelNumber = 6,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 5,
                crawlerCount = 4,
                enemySpeed = 5.4,
                targetPercentage = 75.0
            ),
            LevelConfig(
                levelNumber = 7,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 6,
                crawlerCount = 4,
                enemySpeed = 5.8,
                targetPercentage = 78.0
            ),
            LevelConfig(
                levelNumber = 8,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 6,
                crawlerCount = 5,
                enemySpeed = 6.2,
                targetPercentage = 80.0
            ),
            LevelConfig(
                levelNumber = 9,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 7,
                crawlerCount = 5,
                enemySpeed = 6.6,
                targetPercentage = 80.0
            ),
            LevelConfig(
                levelNumber = 10,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = 8,
                crawlerCount = 6,
                enemySpeed = 7.0,
                targetPercentage = 85.0
            )
        )

        fun getConfig(level: Int): LevelConfig {
            val index = (level - 1).coerceIn(0, LEVELS.size - 1)
            return LEVELS[index].copy(levelNumber = level) // scale dynamically if higher
        }
    }
}
