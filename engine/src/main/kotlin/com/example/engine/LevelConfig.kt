package com.example.engine

/**
 * Data-driven configuration for a single level in TerraFill.
 *
 * Levels are generated procedurally by [getConfig] so difficulty keeps ramping
 * for as many levels as the player can reach, rather than stopping at a fixed list.
 *
 * Enemy roster by level band:
 *  - from level 1: bouncers (drift + bounce) and crawlers (wall-followers)
 *  - from level 3: jumpers (spiders that leap in fast bursts)
 *  - from level 5: hunters (spiders that actively chase the player)
 *  - from level 7: speeders (very fast straight-line spiders)
 * On top of the counts, movement speed, the capture target, time pressure, and
 * enemy "aggression" (how strong the jumper/hunter abilities are) all scale up
 * every level.
 */
data class LevelConfig(
    val levelNumber: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val bouncerCount: Int,
    val crawlerCount: Int,
    val jumperCount: Int,
    val hunterCount: Int,
    val speederCount: Int,
    val enemySpeed: Double,       // cells per second for drifting enemies
    val enemyAggression: Double,  // 0..1 - strength of jumper/hunter special abilities
    val targetPercentage: Double = 75.0,
    val timeLimitSeconds: Int = 180
) {
    companion object {
        /** Number of levels shown in the campaign / level-select menu. */
        const val TOTAL_LEVELS = 20

        /** Upper bound on total enemies so the field never gets impossibly crowded. */
        private const val MAX_ENEMIES = 14

        /**
         * Builds the configuration for any level number, scaling difficulty smoothly:
         * more (and smarter, stronger, faster) enemies, higher capture target, and
         * less time as the level rises.
         *
         * [fieldAspect] is the width/height ratio of the on-screen play area. The grid
         * keeps a fixed width and grows/shrinks in height to match, so the field fills
         * the device screen edge-to-edge with square cells. The time limit scales with
         * the resulting cell count so bigger fields don't get harder for free.
         */
        fun getConfig(level: Int, fieldAspect: Double = 0.8): LevelConfig {
            val l = level.coerceAtLeast(1)

            var bouncers = 1 + (l - 1) / 3                       // 1,1,1,2,2,2,3,...
            var crawlers = 1 + (l - 1) / 4                       // 1,1,1,1,2,...
            var jumpers = if (l < 3) 0 else 1 + (l - 3) / 4      // from L3
            var hunters = if (l < 5) 0 else 1 + (l - 5) / 4      // from L5
            var speeders = if (l < 7) 0 else 1 + (l - 7) / 5     // from L7

            // Keep the total manageable: while over budget, trim the largest group.
            fun total() = bouncers + crawlers + jumpers + hunters + speeders
            while (total() > MAX_ENEMIES) {
                val biggest = maxOf(bouncers, crawlers, jumpers, hunters, speeders)
                when (biggest) {
                    bouncers -> bouncers--
                    crawlers -> crawlers--
                    jumpers -> jumpers--
                    hunters -> hunters--
                    else -> speeders--
                }
            }

            // Difficulty curve: level 1 stays welcoming, but each level up is a
            // clearly felt step - faster spiders (steeper 0.42/level ramp), stronger
            // abilities, a higher capture target and a tighter clock.
            val speed = (3.4 + (l - 1) * 0.42).coerceAtMost(10.5)
            // Ability aggression ramps from 0 at level 1 to 1.0 around level 14.
            val aggression = ((l - 1) * 0.075).coerceIn(0.0, 1.0)
            val target = (68.0 + (l - 1) * 1.6).coerceAtMost(88.0)

            // 32 cells across (was 40): ~1.25x bigger cells, so the caterpillar and
            // spiders render noticeably larger and more readable on phone screens.
            val width = 32
            val height = (width / fieldAspect.coerceIn(0.35, 1.2))
                .let { Math.round(it).toInt() }
                .coerceIn(40, 72)
            // Base time is tuned for the default 32x40 grid; scale with actual area.
            val areaFactor = (width * height) / (32.0 * 40.0)
            val time = Math.round((200 - (l - 1) * 8).coerceAtLeast(80) * areaFactor).toInt()

            return LevelConfig(
                levelNumber = l,
                gridWidth = width,
                gridHeight = height,
                bouncerCount = bouncers,
                crawlerCount = crawlers,
                jumperCount = jumpers,
                hunterCount = hunters,
                speederCount = speeders,
                enemySpeed = speed,
                enemyAggression = aggression,
                targetPercentage = target,
                timeLimitSeconds = time
            )
        }
    }
}
