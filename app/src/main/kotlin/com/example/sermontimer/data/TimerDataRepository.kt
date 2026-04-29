package com.example.sermontimer.data

import com.example.sermontimer.domain.model.AppSettings
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.domain.model.TimerState
import kotlinx.coroutines.flow.Flow

/**
 * Repository for timer data persistence using DataStore.
 * Stores presets list, default preset ID, last timer state for recovery, and global app settings.
 */
interface TimerDataRepository {

    /**
     * Flow of all available presets.
     * Emits empty list if no presets exist.
     */
    val presets: Flow<List<Preset>>

    /**
     * Flow of the default preset ID.
     * Emits null if no default preset is set.
     */
    val defaultPresetId: Flow<String?>

    /**
     * Flow of the last timer state for recovery.
     * Emits null if no state was saved.
     */
    val lastTimerState: Flow<TimerState?>

    /**
     * Flow of the global app settings (preroll, overtime). Emits defaults when nothing is stored.
     */
    val appSettings: Flow<AppSettings>

    /**
     * Saves a list of presets, replacing any existing ones.
     */
    suspend fun savePresets(presets: List<Preset>)

    /**
     * Adds or updates a single preset.
     */
    suspend fun savePreset(preset: Preset)

    /**
     * Deletes a preset by ID.
     * If it was the default preset, clears the default.
     */
    suspend fun deletePreset(presetId: String)

    /**
     * Sets the default preset ID.
     * Pass null to clear the default.
     */
    suspend fun setDefaultPresetId(presetId: String?)

    /**
     * Saves the current timer state for recovery after restart.
     * Pass null to clear saved state.
     */
    suspend fun saveTimerState(state: TimerState?)

    /**
     * Persists app-wide settings (preroll, overtime).
     */
    suspend fun saveAppSettings(settings: AppSettings)

    /**
     * Clears all data (for testing or reset).
     */
    suspend fun clearAll()
}
