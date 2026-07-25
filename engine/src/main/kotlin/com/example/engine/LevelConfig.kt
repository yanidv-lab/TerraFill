package com.example.engine

/**
 * A spider ability making its first appearance on a level, so the UI can introduce
 * it to the player before play starts.
 *
 * @param type matches [Enemy.type] so the UI can pick the right sprite and colour.
 */
data class EnemyIntro(val type: String, val name: String, val description: String)

/**
 * Data-driven configuration for a single level in TerraFill.
 *
 * Levels are generated procedurally by [getConfig] so difficulty keeps ramping
 * for as many levels as the player can reach, rather than stopping at a fixed list.
 *
 * Enemy roster by level band:
 *  - from level 1: bouncers (drift + bounce) and crawlers (wall-followers)
 *  - from level 3: jumpers (spiders that leap in fast bursts)
 *  - from level 4: eaters (slow spiders that devour captured territory)
 *  - from level 5: hunters (spiders that actively chase the player)
 *  - from level 7: speeders (very fast straight-line spiders)
 *  - from level 15: spitters (stationary spiders that fire web projectiles)
 *
 * Design intent: difficulty comes from smarter, faster, more aggressive enemies -
 * NOT from swarming the screen. Counts grow slowly and are capped low, so later
 * levels present a small squad of distinct, dangerous abilities rather than a
 * crowd of identical dots. Movement speed, capture target, time pressure and
 * enemy "aggression" (leap frequency/strength, chase turn-rate) all ramp up.
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
    val eaterCount: Int = 0,
    val spitterCount: Int = 0,
    val enemySpeed: Double,       // cells per second for drifting enemies
    val enemyAggression: Double,  // 0..1 - strength of jumper/hunter special abilities
    val targetPercentage: Double = 75.0,
    val timeLimitSeconds: Int = 180
) {
    companion object {
        /** Number of levels shown in the campaign / level-select menu. */
        const val TOTAL_LEVELS = 20

        /**
         * The enemy type that debuts at [level], or null if the roster is unchanged.
         * Keep in sync with the count formulas in [getConfig].
         */
        fun newEnemyAt(level: Int): EnemyIntro? = when (level) {
            1 -> EnemyIntro("Bouncer", "BOUNCER & CRAWLER", "They drift and hug the walls. Never let one touch you or your trail.")
            3 -> EnemyIntro("Jumper", "JUMPING SPIDER", "Drifts calmly, then pounces in sudden fast leaps.")
            4 -> EnemyIntro("Eater", "WALL EATER", "Slow, but it devours the land you have claimed. Keep re-taking ground.")
            5 -> EnemyIntro("Hunter", "HUNTER", "Actively chases you. Juke it with sharp turns.")
            7 -> EnemyIntro("Speeder", "SPEEDER", "Crosses the whole field in a blink. Watch before you draw.")
            15 -> EnemyIntro("Spitter", "WEB SPITTER", "Stands still and shoots webs at you. Captured land blocks them.")
            else -> null
        }

        /** Upper bound on total enemies. Kept low on purpose: a tight squad of
         *  distinct abilities reads far better than a crowded screen of objects.
         *  With seven ability types this yields ~one of each at the top levels. */
        private const val MAX_ENEMIES = 7

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

            // Slow-growing counts: at most one new spider every several levels, so
            // the roster stays a readable squad even at level 20 (trimmed to 7).
            var bouncers = 1 + (l - 1) / 9                       // 1..~3
            var crawlers = 1 + (l - 1) / 9                       // from L1
            var jumpers = if (l < 3) 0 else 1 + (l - 3) / 10     // from L3
            var eaters = if (l < 4) 0 else 1 + (l - 4) / 12      // from L4
            var hunters = if (l < 5) 0 else 1 + (l - 5) / 11     // from L5
            var speeders = if (l < 7) 0 else 1 + (l - 7) / 12    // from L7
            var spitters = if (l < 15) 0 else 1 + (l - 15) / 8   // from L15

            // Keep the total manageable: while over budget, trim the largest group.
            // Because every ability starts at 1, this preserves variety - it thins
            // duplicates before it ever removes a type entirely.
            fun total() = bouncers + crawlers + jumpers + hunters + speeders + eaters + spitters
            while (total() > MAX_ENEMIES) {
                val biggest = maxOf(bouncers, crawlers, jumpers, hunters, speeders, eaters, spitters)
                when (biggest) {
                    bouncers -> bouncers--
                    crawlers -> crawlers--
                    jumpers -> jumpers--
                    hunters -> hunters--
                    speeders -> speeders--
                    eaters -> eaters--
                    else -> spitters--
                }
            }

            // STAGGERED difficulty: a level-up never raises everything at once.
            // Speed/aggression ramp smoothly every level (the core pressure), while
            // the capture target only steps every 2 levels and the clock only
            // tightens every 3 - so most levels change just one or two dimensions.
            // Overall the curve is deliberately gentler than a straight ramp: the
            // target tops out at 80% instead of 88%, and time stays generous.
            val speed = (3.4 + (l - 1) * 0.36).coerceAtMost(9.8)
            val aggression = ((l - 1) * 0.065).coerceIn(0.0, 1.0)
            val target = (64.0 + ((l - 1) / 2) * 1.6).coerceAtMost(80.0)

            // 28 cells across (was 32): larger cells, so the caterpillar and spiders
            // render clearly at arm's length on a phone. Height follows the screen
            // aspect so the board still fills the display with square cells.
            val width = 28
            val height = (width / fieldAspect.coerceIn(0.35, 1.2))
                .let { Math.round(it).toInt() }
                .coerceIn(36, 64)
            // Base time is tuned for a default ~28x44 board; scale with actual area.
            // The clock only tightens every 3rd level and keeps a generous floor.
            val areaFactor = (width * height) / (28.0 * 44.0)
            val baseTime = (200 - ((l - 1) / 3) * 8).coerceAtLeast(110)
            val time = Math.round(baseTime * areaFactor).toInt()

            return LevelConfig(
                levelNumber = l,
                gridWidth = width,
                gridHeight = height,
                bouncerCount = bouncers,
                crawlerCount = crawlers,
                jumperCount = jumpers,
                hunterCount = hunters,
                speederCount = speeders,
                eaterCount = eaters,
                spitterCount = spitters,
                enemySpeed = speed,
                enemyAggression = aggression,
                targetPercentage = target,
                timeLimitSeconds = time
            )
        }
    }
}
