package com.example.sermontimer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.domain.model.TimerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "timer_prefs")

private object PreferencesKeys {
    val PRESETS_JSON = stringPreferencesKey("presets_json")
    val DEFAULT_PRESET_ID = stringPreferencesKey("default_preset_id")
    val LAST_TIMER_STATE_JSON = stringPreferencesKey("last_timer_state_json")
}

class DataStoreTimerRepository(
    private val context: Context,
    private val json: Json = Json { encodeDefaults = false }
) : TimerDataRepository {

    override val presets: Flow<List<Preset>> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val presetsJson = preferences[PreferencesKeys.PRESETS_JSON]
            if (presetsJson.isNullOrBlank()) {
                emptyList()
            } else {
                try {
                    json.decodeFromString<List<Preset>>(presetsJson)
                } catch (e: Exception) {
                    // Corrupted data, return empty list
                    emptyList()
                }
            }
        }

    override val defaultPresetId: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.DEFAULT_PRESET_ID]
        }

    override val lastTimerState: Flow<TimerState?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val stateJson = preferences[PreferencesKeys.LAST_TIMER_STATE_JSON]
            if (stateJson.isNullOrBlank()) {
                null
            } else {
                try {
                    json.decodeFromString<TimerState>(stateJson)
                } catch (e: Exception) {
                    // Corrupted data, return null
                    null
                }
            }
        }

    override suspend fun savePresets(presets: List<Preset>) {
        val presetsJson = json.encodeToString(presets)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRESETS_JSON] = presetsJson
        }
    }

    override suspend fun savePreset(preset: Preset) {
        val currentPresets = presets.first().toMutableList()
        val existingIndex = currentPresets.indexOfFirst { it.id == preset.id }
        if (existingIndex >= 0) {
            currentPresets[existingIndex] = preset
        } else {
            currentPresets.add(preset)
        }
        savePresets(currentPresets)
    }

    override suspend fun deletePreset(presetId: String) {
        val currentPresets = presets.first().filter { it.id != presetId }
        savePresets(currentPresets)

        // Clear default preset if it was deleted
        val currentDefaultId = defaultPresetId.first()
        if (currentDefaultId == presetId) {
            setDefaultPresetId(null)
        }
    }

    override suspend fun setDefaultPresetId(presetId: String?) {
        context.dataStore.edit { preferences ->
            if (presetId != null) {
                preferences[PreferencesKeys.DEFAULT_PRESET_ID] = presetId
            } else {
                preferences.remove(PreferencesKeys.DEFAULT_PRESET_ID)
            }
        }
    }

    override suspend fun saveTimerState(state: TimerState?) {
        context.dataStore.edit { preferences ->
            if (state != null) {
                val stateJson = json.encodeToString(state)
                preferences[PreferencesKeys.LAST_TIMER_STATE_JSON] = stateJson
            } else {
                preferences.remove(PreferencesKeys.LAST_TIMER_STATE_JSON)
            }
        }
    }

    override suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
