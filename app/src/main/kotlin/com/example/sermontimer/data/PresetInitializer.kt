package com.example.sermontimer.data

import com.example.sermontimer.domain.model.Preset
import kotlinx.coroutines.flow.first

/**
 * Initializes default presets if none exist.
 */
class PresetInitializer(private val repository: TimerDataRepository) {

    suspend fun initializeDefaults() {
        val existingPresets = repository.presets.first()
        if (existingPresets.isEmpty()) {
            val defaultPresets = createDefaultPresets()
            repository.savePresets(defaultPresets)

            // Set first preset as default
            repository.setDefaultPresetId(defaultPresets.first().id)
        }
    }

    private fun createDefaultPresets(): List<Preset> = listOf(
        Preset(
            id = "sermon-5-20-5",
            title = "Sermon 5-20-5",
            introSec = 300, // 5 minutes
            mainSec = 1200, // 20 minutes
            outroSec = 300, // 5 minutes
            allowSkip = true,
            soundEnabled = false
        ),
        Preset(
            id = "meeting-3-15-3",
            title = "Meeting 3-15-3",
            introSec = 180, // 3 minutes
            mainSec = 900, // 15 minutes
            outroSec = 180, // 3 minutes
            allowSkip = true,
            soundEnabled = false
        ),
        Preset(
            id = "quick-2-10-2",
            title = "Quick 2-10-2",
            introSec = 120, // 2 minutes
            mainSec = 600, // 10 minutes
            outroSec = 120, // 2 minutes
            allowSkip = true,
            soundEnabled = false
        )
    )
}
