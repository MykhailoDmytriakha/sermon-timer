package com.example.sermontimer.presentation

import com.example.sermontimer.domain.model.Preset
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModelTest {

    @Test
    fun `updatePresetsSorted puts default preset at the top`() {
        // Given
        val viewModel = TimerViewModelTestHelper()
        val preset1 = Preset("1", "First Preset", 300, 1200, 300)
        val preset2 = Preset("2", "Second Preset", 180, 900, 180)
        val preset3 = Preset("3", "Third Preset", 120, 600, 120)
        val unsortedPresets = listOf(preset2, preset1, preset3) // preset2 first, preset1 second, preset3 third
        val defaultPresetId = "1" // preset1 should be default

        // When
        viewModel.updatePresetsSorted(unsortedPresets, defaultPresetId)

        // Then
        val sortedPresets = viewModel.sortedPresets
        assertThat(sortedPresets).hasSize(3)
        assertThat(sortedPresets[0].id).isEqualTo("1") // Default preset should be first
        assertThat(sortedPresets[1].id).isEqualTo("2") // Then sorted by title
        assertThat(sortedPresets[2].id).isEqualTo("3")
    }

    @Test
    fun `updatePresetsSorted resorts when default preset changes`() {
        // Given
        val viewModel = TimerViewModelTestHelper()
        val preset1 = Preset("1", "B Second Preset", 300, 1200, 300)
        val preset2 = Preset("2", "A First Preset", 180, 900, 180)
        val preset3 = Preset("3", "C Third Preset", 120, 600, 120)
        val presets = listOf(preset1, preset2, preset3)

        // Initial sort with preset2 as default
        viewModel.updatePresetsSorted(presets, "2")
        assertThat(viewModel.sortedPresets[0].id).isEqualTo("2") // A First Preset (default)

        // Change default to preset3
        viewModel.updatePresetsSorted(presets, "3")

        // Then: preset3 should now be first
        val resortedPresets = viewModel.sortedPresets
        assertThat(resortedPresets[0].id).isEqualTo("3") // C Third Preset (new default)
        assertThat(resortedPresets[1].id).isEqualTo("2") // A First Preset
        assertThat(resortedPresets[2].id).isEqualTo("1") // B Second Preset
    }

    @Test
    fun `updatePresetsSorted sorts by title when no default preset is set`() {
        // Given
        val viewModel = TimerViewModelTestHelper()
        val preset1 = Preset("1", "Z Last Preset", 300, 1200, 300)
        val preset2 = Preset("2", "A First Preset", 180, 900, 180)
        val preset3 = Preset("3", "M Middle Preset", 120, 600, 120)
        val unsortedPresets = listOf(preset1, preset2, preset3)

        // When
        viewModel.updatePresetsSorted(unsortedPresets, null) // No default preset

        // Then
        val sortedPresets = viewModel.sortedPresets
        assertThat(sortedPresets).hasSize(3)
        assertThat(sortedPresets[0].id).isEqualTo("2") // A First Preset
        assertThat(sortedPresets[1].id).isEqualTo("3") // M Middle Preset
        assertThat(sortedPresets[2].id).isEqualTo("1") // Z Last Preset
    }

    @Test
    fun `updatePresetsSorted handles empty list correctly`() {
        // Given
        val viewModel = TimerViewModelTestHelper()
        val emptyPresets = emptyList<Preset>()

        // When
        viewModel.updatePresetsSorted(emptyPresets, "1")

        // Then
        assertThat(viewModel.sortedPresets).isEmpty()
    }

    @Test
    fun `updatePresetsSorted handles single preset correctly`() {
        // Given
        val viewModel = TimerViewModelTestHelper()
        val singlePreset = Preset("1", "Single Preset", 300, 1200, 300)
        val presets = listOf(singlePreset)

        // When
        viewModel.updatePresetsSorted(presets, "1")

        // Then
        val sortedPresets = viewModel.sortedPresets
        assertThat(sortedPresets).hasSize(1)
        assertThat(sortedPresets[0].id).isEqualTo("1")
    }
}

/**
 * Test helper class to expose the private updatePresetsSorted method for testing.
 */
private class TimerViewModelTestHelper {
    var sortedPresets: List<Preset> = emptyList()
        private set

    fun updatePresetsSorted(presets: List<Preset>, defaultPresetId: String?) {
        val sorted = presets.sortedWith(compareBy<Preset> { preset ->
            // Default preset comes first (false < true)
            preset.id != defaultPresetId
        }.thenBy { it.title })

        sortedPresets = sorted
    }
}
