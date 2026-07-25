package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
 * Enemies are not serialized: they respawn at their deterministic level positions,
 * which keeps the save tiny and the resumed run fair.
 */
data class SavedGame(
    val level: Int,
    val score: Int,
    val lives: Int,
    val timeRemaining: Double,
    val gridWidth: Int,
    val gridHeight: Int,
    val capturedMask: String
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
                capturedMask = mask
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
