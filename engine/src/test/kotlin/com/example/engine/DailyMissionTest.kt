package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyMissionTest {

    @Test
    fun `the same day and level always yields the same mission`() {
        val a = DailyMissions.forDay(dayEpoch = 12345L, playerLevel = 7)
        val b = DailyMissions.forDay(dayEpoch = 12345L, playerLevel = 7)
        assertEquals(a, b)
    }

    @Test
    fun `different days can yield different mission types`() {
        val types = (0L until 30L).map { DailyMissions.forDay(dayEpoch = it, playerLevel = 10).type }.toSet()
        assertTrue("expected some variety across 30 days, got only $types", types.size > 1)
    }

    @Test
    fun `capture burst target is gentle at level 1 and stays capped at high levels`() {
        // Fix a day whose type happens to be CAPTURE_BURST at both ends of the range.
        val day = firstDayOfType(DailyMissionType.CAPTURE_BURST)
        val early = DailyMissions.forDay(day, playerLevel = 1)
        val late = DailyMissions.forDay(day, playerLevel = 60)

        assertEquals(35, early.target)
        assertEquals(55, late.target)
        assertTrue(late.target > early.target)
    }

    @Test
    fun `combo streak target is gentle at level 1 and stays capped at high levels`() {
        val day = firstDayOfType(DailyMissionType.COMBO_STREAK)
        val early = DailyMissions.forDay(day, playerLevel = 1)
        val late = DailyMissions.forDay(day, playerLevel = 60)

        assertEquals(3, early.target)
        assertEquals(5, late.target)
    }

    @Test
    fun `flawless level mission has no numeric target`() {
        val day = firstDayOfType(DailyMissionType.FLAWLESS_LEVEL)
        val mission = DailyMissions.forDay(day, playerLevel = 25)
        assertEquals(0, mission.target)
    }

    @Test
    fun `streak reward climbs by 50 stars a day and tops out at day 7`() {
        assertEquals(100, DailyMissions.streakReward(1))
        assertEquals(150, DailyMissions.streakReward(2))
        assertEquals(200, DailyMissions.streakReward(3))
        assertEquals(400, DailyMissions.streakReward(7))
    }

    @Test
    fun `streak reward clamps out-of-range days instead of misbehaving`() {
        assertEquals(100, DailyMissions.streakReward(0))
        assertEquals(400, DailyMissions.streakReward(9))
    }

    @Test
    fun `a consecutive claim advances the streak day and wraps after day 7`() {
        assertEquals(2, DailyMissions.nextStreakDay(currentStreakDay = 1, consecutive = true))
        assertEquals(7, DailyMissions.nextStreakDay(currentStreakDay = 6, consecutive = true))
        assertEquals(1, DailyMissions.nextStreakDay(currentStreakDay = 7, consecutive = true))
    }

    @Test
    fun `a gap in claims resets the streak to day 1`() {
        assertEquals(1, DailyMissions.nextStreakDay(currentStreakDay = 5, consecutive = false))
    }

    /** Smallest day epoch (from a small deterministic search window) whose mission is [type]. */
    private fun firstDayOfType(type: DailyMissionType): Long =
        (0L until 50L).first { DailyMissions.forDay(it, playerLevel = 1).type == type }
}
