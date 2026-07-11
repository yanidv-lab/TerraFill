package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Declare DataStore extension on Context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "terrafill_settings")

/**
 * Manages local state persistence for level unlock progress and high scores in TerraFill.
 */
class GamePreferences(private val context: Context) {

    companion object {
        private val HIGHEST_UNLOCKED_LEVEL = intPreferencesKey("highest_unlocked_level")

        private fun percentageKey(level: Int) = doublePreferencesKey("best_percentage_level_$level")
        private fun timeKey(level: Int) = intPreferencesKey("best_time_level_$level")
    }

    /**
     * Flow of the highest unlocked level. Defaults to level 1.
     */
    val highestUnlockedLevel: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[HIGHEST_UNLOCKED_LEVEL] ?: 1
    }

    /**
     * Retrieves the high score percentage achieved for a specific level.
     */
    fun getBestPercentage(level: Int): Flow<Double> = context.contextDataStore().map { preferences ->
        preferences[percentageKey(level)] ?: 0.0
    }

    /**
     * Retrieves the best completion time (seconds remaining) for a specific level.
     */
    fun getBestTimeRemaining(level: Int): Flow<Int> = context.contextDataStore().map { preferences ->
        preferences[timeKey(level)] ?: 0
    }

    /**
     * Saves progress of a level completed by the player. Unlocks the next level.
     */
    suspend fun saveLevelCompletion(level: Int, percentage: Double, timeRemaining: Int) {
        context.dataStore.edit { preferences ->
            // Save level scores if they are better than the previous high
            val currentBestPerc = preferences[percentageKey(level)] ?: 0.0
            if (percentage > currentBestPerc) {
                preferences[percentageKey(level)] = percentage
            }

            val currentBestTime = preferences[timeKey(level)] ?: 0
            if (timeRemaining > currentBestTime) {
                preferences[timeKey(level)] = timeRemaining
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
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    // Helper to get raw data flow safely
    private fun Context.contextDataStore() = dataStore.data
}
