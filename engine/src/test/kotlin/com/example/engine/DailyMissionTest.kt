package com.example.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyMissionTest {

    @Test
    fun `forLevel returns exactly one mission of every type`() {
        val missions = DailyMissions.forLevel(playerLevel = 10)
        assertEquals(DailyMissionType.entries.toSet(), missions.map { it.type }.toSet())
        assertEquals(DailyMissionType.entries.size, missions.size)
    }

    @Test
    fun `the same level always yields the same missions`() {
        val a = DailyMissions.forLevel(playerLevel = 7)
        val b = DailyMissions.forLevel(playerLevel = 7)
        assertEquals(a, b)
    }

    @Test
    fun `capture burst target is gentle at level 1 and stays capped at high levels`() {
        val early = missionOf(DailyMissions.forLevel(playerLevel = 1), DailyMissionType.CAPTURE_BURST)
        val late = missionOf(DailyMissions.forLevel(playerLevel = 60), DailyMissionType.CAPTURE_BURST)

        assertEquals(35, early.target)
        assertEquals(55, late.target)
        assertTrue(late.target > early.target)
    }

    @Test
    fun `combo streak target is gentle at level 1 and stays capped at high levels`() {
        val early = missionOf(DailyMissions.forLevel(playerLevel = 1), DailyMissionType.COMBO_STREAK)
        val late = missionOf(DailyMissions.forLevel(playerLevel = 60), DailyMissionType.COMBO_STREAK)

        assertEquals(3, early.target)
        assertEquals(5, late.target)
    }

    @Test
    fun `flawless level mission has no numeric target`() {
        val mission = missionOf(DailyMissions.forLevel(playerLevel = 25), DailyMissionType.FLAWLESS_LEVEL)
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

    private fun missionOf(missions: List<DailyMission>, type: DailyMissionType): DailyMission =
        missions.first { it.type == type }
}
