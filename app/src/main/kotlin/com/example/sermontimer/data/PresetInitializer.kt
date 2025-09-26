package com.example.sermontimer.data

import com.example.sermontimer.domain.model.Preset
import kotlinx.coroutines.flow.first

/**
 * Initializes default presets if none exist.
 */
class PresetInitializer(private val repository: TimerDataRepository) {

    suspend fun initializeDefaults() {
        // Check if presets exist without blocking on first() if possible
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
            id = "sermon-7-20-8",
            title = "Sermon 7-20-8",
            introSec = 420, // 7 minutes
            mainSec = 1200, // 20 minutes
            outroSec = 480, // 8 minutes
            allowSkip = true,
            soundEnabled = false
        ),
        Preset(
            id = "small-group-15-20-15",
            title = "Small Group 15-20-15",
            introSec = 900, // 15 minutes
            mainSec = 1200, // 20 minutes
            outroSec = 900, // 15 minutes
            allowSkip = true,
            soundEnabled = false
        )
//        Preset(
//            id = "test-30s-30s-30s",
//            title = "Test 30s-30s-30s",
//            introSec = 30, // 30 seconds
//            mainSec = 30, // 30 seconds
//            outroSec = 30, // 30 seconds
//            allowSkip = true,
//            soundEnabled = false
//        )
    )
}
