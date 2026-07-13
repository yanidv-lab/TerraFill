package com.example.engine

/**
 * Data-driven configuration for a single level in TerraFill.
 *
 * Levels are generated procedurally by [getConfig] so difficulty keeps ramping
 * for as many levels as the player can reach, rather than stopping at a fixed list.
 *
 * Enemy roster by level band:
 *  - from level 1: bouncers (drift + bounce) and crawlers (wall-followers)
 *  - from level 4: jumpers (spiders that leap in fast bursts)
 *  - from level 6: hunters (spiders that actively chase the player)
 * Movement speed, capture target and time pressure all scale every level too.
 */
data class LevelConfig(
    val levelNumber: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val bouncerCount: Int,
    val crawlerCount: Int,
    val jumperCount: Int,
    val hunterCount: Int,
    val enemySpeed: Double,       // cells per second for drifting enemies
    val targetPercentage: Double = 75.0,
    val timeLimitSeconds: Int = 180
) {
    companion object {
        /** Number of levels shown in the campaign / level-select menu. */
        const val TOTAL_LEVELS = 20

        /** Upper bound on total enemies so the field never gets impossibly crowded. */
        private const val MAX_ENEMIES = 12

        /**
         * Builds the configuration for any level number, scaling difficulty smoothly:
         * more (and smarter) enemies, faster movement, a higher capture target, and
         * less time as the level rises.
         */
        fun getConfig(level: Int): LevelConfig {
            val l = level.coerceAtLeast(1)

            var bouncers = 1 + (l - 1) / 3                       // 1,1,1,2,2,2,3,...
            var crawlers = 1 + (l - 1) / 4                       // 1,1,1,1,2,...
            var jumpers = if (l < 4) 0 else 1 + (l - 4) / 4      // from L4: 1,1,1,1,2,...
            var hunters = if (l < 6) 0 else 1 + (l - 6) / 4      // from L6: 1,1,1,1,2,...

            // Keep the total manageable: while over budget, trim the largest group.
            fun total() = bouncers + crawlers + jumpers + hunters
            while (total() > MAX_ENEMIES) {
                val maxOf4 = maxOf(bouncers, crawlers, maxOf(jumpers, hunters))
                when (maxOf4) {
                    bouncers -> bouncers--
                    crawlers -> crawlers--
                    jumpers -> jumpers--
                    else -> hunters--
                }
            }

            val speed = (3.2 + (l - 1) * 0.32).coerceAtMost(9.5)
            val target = (66.0 + (l - 1) * 1.4).coerceAtMost(86.0)
            val time = (220 - (l - 1) * 7).coerceAtLeast(85)

            return LevelConfig(
                levelNumber = l,
                gridWidth = 40,
                gridHeight = 50,
                bouncerCount = bouncers,
                crawlerCount = crawlers,
                jumperCount = jumpers,
                hunterCount = hunters,
                enemySpeed = speed,
                targetPercentage = target,
                timeLimitSeconds = time
            )
        }
    }
}
