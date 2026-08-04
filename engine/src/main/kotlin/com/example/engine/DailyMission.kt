package com.example.engine

import kotlin.math.roundToInt

/** The kind of objective a daily mission asks for. */
enum class DailyMissionType {
    /** Capture at least [DailyMission.target] percent of the field in one single move. */
    CAPTURE_BURST,
    /** Finish a level without a single crash. [DailyMission.target] is unused (0). */
    FLAWLESS_LEVEL,
    /** Reach at least a [DailyMission.target]x combo multiplier during a level. */
    COMBO_STREAK
}

/** One objective: a type plus the number that makes it concrete. */
data class DailyMission(val type: DailyMissionType, val target: Int)

/** A mission plus whether the player has satisfied it yet today. */
data class DailyMissionProgress(val mission: DailyMission, val completed: Boolean)

/**
 * Picks and prices the day's missions shown on the main menu.
 *
 * A pure function - callers own deciding when a new day has started and
 * persisting the result, so this stays trivial to test.
 */
object DailyMissions {

    /**
     * Today's three missions - one of every [DailyMissionType], all of which must be
     * completed (across as many level attempts as it takes) before the day's reward
     * can be claimed. Each target scales with [playerLevel] so a level-2 player and
     * a level-40 player see equally *reachable* goals rather than the same absolute
     * bar - gentle at the start, only modestly harder by the late game.
     */
    fun forLevel(playerLevel: Int): List<DailyMission> {
        // 0.0 at level 1, 1.0 by level 40 and beyond.
        val progress = ((playerLevel.coerceIn(1, 40) - 1) / 39.0)
        return DailyMissionType.entries.map { type ->
            when (type) {
                DailyMissionType.CAPTURE_BURST -> {
                    val target = (35 + progress * 20).roundToInt().coerceIn(35, 55)
                    DailyMission(type, target)
                }
                DailyMissionType.FLAWLESS_LEVEL -> DailyMission(type, 0)
                DailyMissionType.COMBO_STREAK -> {
                    val target = (3 + progress * 2).roundToInt().coerceIn(3, 5)
                    DailyMission(type, target)
                }
            }
        }
    }

    /** Star reward for claiming on the [streakDay]-th consecutive day (1..7). */
    fun streakReward(streakDay: Int): Int = 100 + (streakDay.coerceIn(1, 7) - 1) * 50

    /**
     * The streak day a claim happening the day after [currentStreakDay] advances to -
     * wrapping from 7 back to 1 so the reward cycles 100..400 rather than growing
     * forever. Any gap (a day missed) drops the streak back to day 1.
     */
    fun nextStreakDay(currentStreakDay: Int, consecutive: Boolean): Int =
        if (consecutive) (currentStreakDay.coerceIn(1, 7) % 7) + 1 else 1
}
