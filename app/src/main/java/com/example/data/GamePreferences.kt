package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

        /** Days since the epoch, in the device's clock - all that matters is that it rolls over once a day. */
        private fun currentDayEpoch(): Long = System.currentTimeMillis() / 86_400_000L
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
