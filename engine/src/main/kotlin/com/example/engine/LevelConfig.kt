package com.example.engine

/**
 * Data-driven configuration for a single level in TerraFill.
 *
 * Levels are generated procedurally by [getConfig] so difficulty keeps ramping
 * for as many levels as the player can reach, rather than stopping at a fixed list.
 */
data class LevelConfig(
    val levelNumber: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val bouncerCount: Int,
    val crawlerCount: Int,
    val jumperCount: Int,
    val enemySpeed: Double,       // cells per second for drifting enemies
    val targetPercentage: Double = 75.0,
    val timeLimitSeconds: Int = 180
) {
    companion object {
        /** Number of levels shown in the campaign / level-select menu. */
        const val TOTAL_LEVELS = 12

        /** Upper bound on total enemies so the field never gets impossibly crowded. */
        private const val MAX_ENEMIES = 11

        /**
         * Builds the configuration for any level number, scaling difficulty smoothly:
         * more enemies, faster movement, a higher capture target, and less time as the
         * level rises. Jumping spiders are introduced from level 4 onward.
         */
        fun getConfig(level: Int): LevelConfig {
            val l = level.coerceAtLeast(1)

            var bouncers = 1 + (l - 1) / 2          // 1,1,2,2,3,3,...
            var crawlers = 1 + (l - 1) / 3          // 1,1,1,2,2,2,3,...
            var jumpers = maxOf(0, (l - 2) / 2)     // 0,0,0,1,1,2,2,...  (starts at level 4)

            // Keep the total manageable, trimming the fancier enemy types first.
            while (bouncers + crawlers + jumpers > MAX_ENEMIES) {
                when {
                    jumpers > 2 -> jumpers--
                    bouncers > crawlers -> bouncers--
                    else -> crawlers--
                }
            }

            val speed = (3.3 + (l - 1) * 0.35).coerceAtMost(9.0)
            val target = (68.0 + (l - 1) * 1.5).coerceAtMost(85.0)
            val time = (210 - (l - 1) * 8).coerceAtLeast(90)

            return LevelConfig(
                levelNumber = l,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = bouncers,
                crawlerCount = crawlers,
                jumperCount = jumpers,
                enemySpeed = speed,
                targetPercentage = target,
                timeLimitSeconds = time
            )
        }
    }
}
