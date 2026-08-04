package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.engine.DailyMission
import com.example.engine.DailyMissionType
import com.example.engine.DailyMissions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Declare DataStore extension on Context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "terrafill_settings")

/**
 * A mid-level save so a player who is interrupted (call, battery, app killed) can
 * pick the run back up instead of losing it.
 *
 * The claimed territory is stored as a compact '1'/'0' mask in column-major order.
 * Enemies are stored too (see [com.example.engine.GameEngine.exportEnemies]), so a
 * resumed run finds the exact spiders it left behind - same positions, same count -
 * rather than a freshly regenerated roster. [enemies] defaults to blank so a save
 * written before this field existed still deserializes and restores cleanly.
 */
data class SavedGame(
    val level: Int,
    val score: Int,
    val lives: Int,
    val timeRemaining: Double,
    val gridWidth: Int,
    val gridHeight: Int,
    val capturedMask: String,
    val enemies: String = ""
)

/**
 * Today's mission plus where the player stands on it - what [GamePreferences]
 * exposes to the rest of the app, so nothing outside this file needs to know how
 * completion, claiming, or the streak are actually stored.
 */
data class DailyMissionState(
    val mission: DailyMission,
    val completed: Boolean,
    val claimed: Boolean,
    /**
     * Before claiming: the streak day *claiming right now* would land on - a
     * preview. After claiming: the streak day that claim actually landed on, so
     * the UI can keep showing "day 3 of 7" rather than a preview of a hypothetical
     * second claim that can never happen today.
     */
    val streakDay: Int
) {
    val reward: Int get() = DailyMissions.streakReward(streakDay)
}

/**
 * Manages local state persistence for level unlock progress and high scores in TerraFill.
 */
class GamePreferences(context: Context) {

    private val appContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        context.applicationContext.createAttributionContext("default")
    } else {
        context.applicationContext
    }

    companion object {
        private val HIGHEST_UNLOCKED_LEVEL = intPreferencesKey("highest_unlocked_level")
        private val LAST_PLAYED_LEVEL = intPreferencesKey("last_played_level")

        private fun percentageKey(level: Int) = doublePreferencesKey("best_percentage_level_$level")
        private fun timeKey(level: Int) = intPreferencesKey("best_time_level_$level")
        private fun scoreKey(level: Int) = intPreferencesKey("best_score_level_$level")
        private fun starsKey(level: Int) = intPreferencesKey("best_stars_level_$level")

        // Star currency: cumulative across every level completion (levels can be
        // replayed for more), minus whatever skins have cost.
        private val TOTAL_STARS_EARNED = intPreferencesKey("total_stars_earned")

        // Stars spent on consumables (extra lives). Skin spend is derived from the
        // owned set, so only consumable spend needs its own running total.
        private val STARS_SPENT_CONSUMABLES = intPreferencesKey("stars_spent_consumables")

        /** Spare lives banked from the shop, added on top of the standard three. */
        private val EXTRA_LIVES = intPreferencesKey("extra_lives")

        // Caterpillar skins bought with stars
        private val OWNED_SKINS = stringSetPreferencesKey("owned_skins")
        private val SELECTED_SKIN = stringPreferencesKey("selected_skin")

        // Mid-level save (resume after an interruption)
        private val SAVE_LEVEL = intPreferencesKey("save_level")
        private val SAVE_SCORE = intPreferencesKey("save_score")
        private val SAVE_LIVES = intPreferencesKey("save_lives")
        private val SAVE_TIME = doublePreferencesKey("save_time")
        private val SAVE_WIDTH = intPreferencesKey("save_width")
        private val SAVE_HEIGHT = intPreferencesKey("save_height")
        private val SAVE_MASK = stringPreferencesKey("save_mask")
        private val SAVE_ENEMIES = stringPreferencesKey("save_enemies")

        // Rewarded-ad daily cap: which day the count is for, and how many so far.
        private val REWARDED_AD_WATCH_DAY = longPreferencesKey("rewarded_ad_watch_day")
        private val REWARDED_AD_WATCH_COUNT = intPreferencesKey("rewarded_ad_watch_count")

        // Share-for-stars: which week the reward was last claimed in. Sharing itself
        // is never rate-limited - only this stamp ever changes, so repeat shares
        // within the same week are free of any bookkeeping.
        private val SHARE_REWARD_WEEK = longPreferencesKey("share_reward_week")

        // Daily mission: which day it was rolled for, what it is, and where the
        // player stands on it. STREAK_DAY/LAST_CLAIM_DAY persist across the mission
        // itself resetting daily - they are what makes the reward climb on
        // consecutive days instead of paying out 100 stars forever.
        private val DAILY_MISSION_DAY = longPreferencesKey("daily_mission_day")
        private val DAILY_MISSION_TYPE = stringPreferencesKey("daily_mission_type")
        private val DAILY_MISSION_TARGET = intPreferencesKey("daily_mission_target")
        private val DAILY_MISSION_COMPLETED = booleanPreferencesKey("daily_mission_completed")
        private val DAILY_MISSION_CLAIMED = booleanPreferencesKey("daily_mission_claimed")
        private val DAILY_MISSION_STREAK_DAY = intPreferencesKey("daily_mission_streak_day")
        private val DAILY_MISSION_LAST_CLAIM_DAY = longPreferencesKey("daily_mission_last_claim_day")

        /** Days since the epoch, in the device's clock - all that matters is that it rolls over once a day. */
        private fun currentDayEpoch(): Long = System.currentTimeMillis() / 86_400_000L

        /** Weeks since the epoch, in the device's clock - rolls over once every 7 days. */
        private fun currentWeekEpoch(): Long = System.currentTimeMillis() / (7 * 86_400_000L)
    }

    /** How many rewarded ads the player has already watched today. Rolls over to 0 on a new day. */
    val rewardedAdWatchesToday: Flow<Int> = appContext.dataStore.data.map { preferences ->
        val storedDay = preferences[REWARDED_AD_WATCH_DAY] ?: -1L
        if (storedDay == currentDayEpoch()) preferences[REWARDED_AD_WATCH_COUNT] ?: 0 else 0
    }

    /** Records a completed rewarded-ad watch, resetting the count first if it's a new day. */
    suspend fun recordRewardedAdWatch() {
        appContext.dataStore.edit { preferences ->
            val today = currentDayEpoch()
            val storedDay = preferences[REWARDED_AD_WATCH_DAY] ?: -1L
            val soFar = if (storedDay == today) preferences[REWARDED_AD_WATCH_COUNT] ?: 0 else 0
            preferences[REWARDED_AD_WATCH_DAY] = today
            preferences[REWARDED_AD_WATCH_COUNT] = soFar + 1
        }
    }

    /** Every star ever earned from completing levels (replays keep adding). */
    val totalStarsEarned: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[TOTAL_STARS_EARNED] ?: 0
    }

    /** Whether the share reward has already been claimed in the current week. */
    val hasClaimedShareRewardThisWeek: Flow<Boolean> = appContext.dataStore.data.map { preferences ->
        (preferences[SHARE_REWARD_WEEK] ?: -1L) == currentWeekEpoch()
    }

    /**
     * Pays out [amount] stars for sharing the game, but only once per week - checked
     * and set inside the same DataStore transaction so it can't double-grant even if
     * invoked more than once in quick succession (e.g. a fast double-tap before the
     * UI re-collects the claimed flag). Returns whether this call was the one that
     * actually claimed it.
     */
    suspend fun claimShareRewardIfEligible(amount: Int): Boolean {
        var claimed = false
        appContext.dataStore.edit { preferences ->
            val week = currentWeekEpoch()
            if ((preferences[SHARE_REWARD_WEEK] ?: -1L) != week) {
                preferences[SHARE_REWARD_WEEK] = week
                preferences[TOTAL_STARS_EARNED] = (preferences[TOTAL_STARS_EARNED] ?: 0) + amount
                claimed = true
            }
        }
        return claimed
    }

    /** Today's mission and the player's progress on it, or null before it's ever been rolled. */
    val dailyMissionState: Flow<DailyMissionState?> = appContext.dataStore.data.map { preferences ->
        val storedDay = preferences[DAILY_MISSION_DAY] ?: return@map null
        if (storedDay != currentDayEpoch()) return@map null // stale - ensureTodayMission hasn't run yet today
        val type = preferences[DAILY_MISSION_TYPE]?.let {
            runCatching { DailyMissionType.valueOf(it) }.getOrNull()
        } ?: return@map null
        val target = preferences[DAILY_MISSION_TARGET] ?: 0
        val completed = preferences[DAILY_MISSION_COMPLETED] == true
        val claimed = preferences[DAILY_MISSION_CLAIMED] == true
        val storedStreakDay = preferences[DAILY_MISSION_STREAK_DAY] ?: 0
        val lastClaimDay = preferences[DAILY_MISSION_LAST_CLAIM_DAY] ?: -1L
        val streakDay = if (claimed) {
            // Already claimed today: show what was actually claimed, not a preview.
            storedStreakDay.coerceAtLeast(1)
        } else {
            val consecutive = lastClaimDay == storedDay - 1
            DailyMissions.nextStreakDay(storedStreakDay, consecutive)
        }
        DailyMissionState(DailyMission(type, target), completed, claimed, streakDay)
    }

    /**
     * Makes sure today has a mission rolled, generating a fresh one (scaled to
     * [playerLevel]) the first time this is called on a new day. Cheap to call every
     * time the main menu appears - it's a no-op once today's mission already exists.
     */
    suspend fun ensureTodayMission(playerLevel: Int) {
        appContext.dataStore.edit { preferences ->
            val today = currentDayEpoch()
            if (preferences[DAILY_MISSION_DAY] == today) return@edit
            val mission = DailyMissions.forDay(today, playerLevel)
            preferences[DAILY_MISSION_DAY] = today
            preferences[DAILY_MISSION_TYPE] = mission.type.name
            preferences[DAILY_MISSION_TARGET] = mission.target
            preferences[DAILY_MISSION_COMPLETED] = false
            preferences[DAILY_MISSION_CLAIMED] = false
        }
    }

    /** Marks today's mission as completed (ready to claim), if it isn't already. */
    suspend fun markTodayMissionCompleted() {
        appContext.dataStore.edit { preferences ->
            if (preferences[DAILY_MISSION_DAY] != currentDayEpoch()) return@edit
            preferences[DAILY_MISSION_COMPLETED] = true
        }
    }

    /**
     * Pays out today's mission reward, once - checked and set atomically so a stray
     * double-tap on the claim button can't double-grant it. The reward climbs with
     * the streak (see [DailyMissions.streakReward]), which advances only when the
     * previous claim was yesterday; any gap resets it to day 1. Returns the amount
     * claimed, or null if there was nothing eligible to claim.
     */
    suspend fun claimDailyMission(): Int? {
        var reward: Int? = null
        appContext.dataStore.edit { preferences ->
            val today = currentDayEpoch()
            if (preferences[DAILY_MISSION_DAY] != today) return@edit
            if (preferences[DAILY_MISSION_COMPLETED] != true) return@edit
            if (preferences[DAILY_MISSION_CLAIMED] == true) return@edit

            val lastClaimDay = preferences[DAILY_MISSION_LAST_CLAIM_DAY] ?: -1L
            val consecutive = lastClaimDay == today - 1
            val streakDay = DailyMissions.nextStreakDay(preferences[DAILY_MISSION_STREAK_DAY] ?: 0, consecutive)
            val amount = DailyMissions.streakReward(streakDay)

            preferences[DAILY_MISSION_CLAIMED] = true
            preferences[DAILY_MISSION_STREAK_DAY] = streakDay
            preferences[DAILY_MISSION_LAST_CLAIM_DAY] = today
            preferences[TOTAL_STARS_EARNED] = (preferences[TOTAL_STARS_EARNED] ?: 0) + amount
            reward = amount
        }
        return reward
    }

    /** Banks the stars paid out by a completed level. */
    suspend fun addStars(amount: Int) {
        if (amount <= 0) return
        appContext.dataStore.edit { preferences ->
            preferences[TOTAL_STARS_EARNED] = (preferences[TOTAL_STARS_EARNED] ?: 0) + amount
        }
    }

    /** Stars spent on consumables so far. */
    val starsSpentOnConsumables: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[STARS_SPENT_CONSUMABLES] ?: 0
    }

    /** Spare lives currently banked (0 when none are held). */
    val extraLives: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[EXTRA_LIVES] ?: 0
    }

    /** Buys one spare life, recording the star spend in the same transaction. */
    suspend fun purchaseExtraLife(cost: Int, cap: Int) {
        appContext.dataStore.edit { preferences ->
            val held = preferences[EXTRA_LIVES] ?: 0
            if (held >= cap) return@edit
            preferences[EXTRA_LIVES] = held + 1
            preferences[STARS_SPENT_CONSUMABLES] = (preferences[STARS_SPENT_CONSUMABLES] ?: 0) + cost
        }
    }

    /**
     * Empties the bank of spare lives and returns what was in it, in one atomic
     * read-modify-write. Called when a level starts: reading and clearing as separate
     * steps could drop a purchase made in between, or hand the same life to two
     * levels.
     */
    suspend fun takeAllExtraLives(): Int {
        var taken = 0
        appContext.dataStore.edit { preferences ->
            taken = preferences[EXTRA_LIVES] ?: 0
            preferences[EXTRA_LIVES] = 0
        }
        return taken
    }

    /**
     * Updates the banked spare lives. The bank is emptied as soon as a level starts:
     * a purchased life boosts exactly one level and never carries past it.
     */
    suspend fun setExtraLives(count: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[EXTRA_LIVES] = count.coerceAtLeast(0)
        }
    }

    /** Skin ids the player owns. The default skin is always available. */
    val ownedSkins: Flow<Set<String>> = appContext.dataStore.data.map { preferences ->
        preferences[OWNED_SKINS] ?: emptySet()
    }

    /** The skin currently equipped on the caterpillar. */
    val selectedSkin: Flow<String> = appContext.dataStore.data.map { preferences ->
        preferences[SELECTED_SKIN] ?: "classic"
    }

    /** Records a skin purchase and equips it immediately. */
    suspend fun purchaseSkin(skinId: String) {
        appContext.dataStore.edit { preferences ->
            val owned = preferences[OWNED_SKINS] ?: emptySet()
            preferences[OWNED_SKINS] = owned + skinId
            preferences[SELECTED_SKIN] = skinId
        }
    }

    /** Equips an already-owned skin. */
    suspend fun selectSkin(skinId: String) {
        appContext.dataStore.edit { preferences ->
            preferences[SELECTED_SKIN] = skinId
        }
    }

    /**
     * The interrupted run, if any. Returns null when nothing is stored or the saved
     * board no longer matches (e.g. the device was rotated onto a different grid).
     */
    val savedGame: Flow<SavedGame?> = appContext.dataStore.data.map { preferences ->
        val level = preferences[SAVE_LEVEL]
        val mask = preferences[SAVE_MASK]
        val w = preferences[SAVE_WIDTH]
        val h = preferences[SAVE_HEIGHT]
        if (level == null || mask == null || w == null || h == null || mask.length != w * h) {
            null
        } else {
            SavedGame(
                level = level,
                score = preferences[SAVE_SCORE] ?: 0,
                lives = preferences[SAVE_LIVES] ?: 3,
                timeRemaining = preferences[SAVE_TIME] ?: 0.0,
                gridWidth = w,
                gridHeight = h,
                capturedMask = mask,
                enemies = preferences[SAVE_ENEMIES] ?: ""
            )
        }
    }

    /** Stores the current run so it can be resumed after an interruption. */
    suspend fun saveGame(save: SavedGame) {
        appContext.dataStore.edit { preferences ->
            preferences[SAVE_LEVEL] = save.level
            preferences[SAVE_SCORE] = save.score
            preferences[SAVE_LIVES] = save.lives
            preferences[SAVE_TIME] = save.timeRemaining
            preferences[SAVE_WIDTH] = save.gridWidth
            preferences[SAVE_HEIGHT] = save.gridHeight
            preferences[SAVE_MASK] = save.capturedMask
            preferences[SAVE_ENEMIES] = save.enemies
        }
    }

    /** Drops the mid-level save (level finished, run ended, or resumed). */
    suspend fun clearSavedGame() {
        appContext.dataStore.edit { preferences ->
            preferences.remove(SAVE_LEVEL)
            preferences.remove(SAVE_SCORE)
            preferences.remove(SAVE_LIVES)
            preferences.remove(SAVE_TIME)
            preferences.remove(SAVE_WIDTH)
            preferences.remove(SAVE_ENEMIES)
            preferences.remove(SAVE_HEIGHT)
            preferences.remove(SAVE_MASK)
        }
    }

    /**
     * Flow of the highest unlocked level. Defaults to level 1.
     */
    val highestUnlockedLevel: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[HIGHEST_UNLOCKED_LEVEL] ?: 1
    }

    /**
     * Flow of the level the player most recently played, so the game can offer a
     * one-tap "continue where you left off". Defaults to level 1.
     */
    val lastPlayedLevel: Flow<Int> = appContext.dataStore.data.map { preferences ->
        preferences[LAST_PLAYED_LEVEL] ?: 1
    }

    /** Remembers the level the player just started, for the continue button. */
    suspend fun saveLastPlayedLevel(level: Int) {
        appContext.dataStore.edit { preferences ->
            preferences[LAST_PLAYED_LEVEL] = level
        }
    }

    /**
     * Retrieves the high score percentage achieved for a specific level.
     */
    fun getBestPercentage(level: Int): Flow<Double> = appContext.contextDataStore().map { preferences ->
        preferences[percentageKey(level)] ?: 0.0
    }

    /**
     * Retrieves the best completion time (seconds remaining) for a specific level.
     */
    fun getBestTimeRemaining(level: Int): Flow<Int> = appContext.contextDataStore().map { preferences ->
        preferences[timeKey(level)] ?: 0
    }

    /**
     * Retrieves the high score achieved for a specific level.
     */
    fun getBestScore(level: Int): Flow<Int> = appContext.contextDataStore().map { preferences ->
        preferences[scoreKey(level)] ?: 0
    }

    /**
     * Retrieves the best star rating (0-3) earned for a specific level.
     */
    fun getBestStars(level: Int): Flow<Int> = appContext.contextDataStore().map { preferences ->
        preferences[starsKey(level)] ?: 0
    }

    /**
     * Saves progress of a level completed by the player. Unlocks the next level.
     */
    suspend fun saveLevelCompletion(level: Int, percentage: Double, timeRemaining: Int, score: Int, stars: Int) {
        appContext.dataStore.edit { preferences ->
            // Save level scores if they are better than the previous high
            val currentBestPerc = preferences[percentageKey(level)] ?: 0.0
            if (percentage > currentBestPerc) {
                preferences[percentageKey(level)] = percentage
            }

            val currentBestTime = preferences[timeKey(level)] ?: 0
            if (timeRemaining > currentBestTime) {
                preferences[timeKey(level)] = timeRemaining
            }

            val currentBestScore = preferences[scoreKey(level)] ?: 0
            if (score > currentBestScore) {
                preferences[scoreKey(level)] = score
            }

            val currentBestStars = preferences[starsKey(level)] ?: 0
            if (stars > currentBestStars) {
                preferences[starsKey(level)] = stars
            }

            // Unlock next level if relevant
            val currentHighest = preferences[HIGHEST_UNLOCKED_LEVEL] ?: 1
            if (level + 1 > currentHighest) {
                preferences[HIGHEST_UNLOCKED_LEVEL] = level + 1
            }
        }
    }

    /**
     * Reset all saved game progress back to level 1.
     */
    suspend fun resetProgress() {
        appContext.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    // Helper to get raw data flow safely
    private fun Context.contextDataStore() = dataStore.data
}
