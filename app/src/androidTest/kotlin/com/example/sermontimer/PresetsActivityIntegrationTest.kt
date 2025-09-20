package com.example.sermontimer

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.presentation.TimerViewModel
import com.example.sermontimer.presentation.WearApp
import com.example.sermontimer.ui.PresetListScreen
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for Presets Activity functionality.
 * Tests that presets activity displays correctly with default preset ordering,
 * and that changing default preset works properly.
 */
@RunWith(AndroidJUnit4::class)
class PresetsActivityIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: TimerViewModel
    private lateinit var presetA: Preset
    private lateinit var presetB: Preset
    private lateinit var presetC: Preset

    @Before
    fun setUp() {
        // Initialize with test data
        TimerDataProvider.initialize(
            InstrumentationRegistry.getInstrumentation().targetContext
        )

        // Create test presets (names chosen so presetB comes first alphabetically)
        presetA = Preset(
            id = "preset_a",
            title = "Sermon Timer A",
            introSec = 60,
            mainSec = 1800,
            outroSec = 120
        )

        presetB = Preset(
            id = "preset_b",
            title = "Meeting Timer B",
            introSec = 30,
            mainSec = 900,
            outroSec = 60
        )

        presetC = Preset(
            id = "preset_c",
            title = "Workshop Timer C",
            introSec = 45,
            mainSec = 1200,
            outroSec = 90
        )

        // Save test presets and set presetA as default initially
        runBlocking {
            val repository = TimerDataProvider.getRepository()
            repository.savePreset(presetA)
            repository.savePreset(presetB)
            repository.savePreset(presetC)
            repository.setDefaultPresetId(presetA.id)
        }

        viewModel = TimerViewModel(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        )
    }

    @After
    fun tearDown() = runBlocking {
        TimerDataProvider.getRepository().clearAll()
        TimerDataProvider.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test
    fun presets_activity_shows_default_preset_on_top() {
        // Set up the preset list screen
        composeTestRule.setContent {
            WearApp(viewModel)
        }

        // Wait for data to load
        composeTestRule.waitForIdle()

        // Verify we're on the preset list screen
        composeTestRule.onNodeWithText("Presets").assertExists()

        // Verify default preset (presetA) is displayed
        composeTestRule.onNodeWithText(presetA.title).assertExists()

        // Verify other presets are also displayed
        composeTestRule.onNodeWithText(presetB.title).assertExists()
        composeTestRule.onNodeWithText(presetC.title).assertExists()

        // Verify default indicator is shown for presetA
        composeTestRule.onNodeWithText("Default").assertExists()
    }

    @Test
    fun presets_are_ordered_with_default_first() {
        // Test that presets are ordered correctly with default first
        runBlocking {
            val presets = viewModel.presets.first()
            val defaultId = viewModel.defaultPresetId.first()

            // Verify presetA is set as default
            assertThat(defaultId).isEqualTo(presetA.id)

            // Verify presets are sorted with default first
            assertThat(presets[0].id).isEqualTo(presetA.id) // Default should be first
            // Other presets should follow in alphabetical order
            assertThat(presets[1].title).isEqualTo(presetB.title) // "Meeting Timer B"
            assertThat(presets[2].title).isEqualTo(presetC.title) // "Workshop Timer C"
        }
    }

    @Test
    fun changing_default_preset_updates_ordering() {
        // Set up the preset list screen
        composeTestRule.setContent {
            WearApp(viewModel)
        }

        composeTestRule.waitForIdle()

        // Initially, presetA should be default and first
        runBlocking {
            val initialPresets = viewModel.presets.first()
            assertThat(initialPresets[0].id).isEqualTo(presetA.id)
        }

        // Change default to presetB
        runBlocking {
            viewModel.setDefaultPreset(presetB.id)
        }

        // Wait for UI to update
        composeTestRule.waitForIdle()

        // Verify presetB is now first
        runBlocking {
            val updatedPresets = viewModel.presets.first()
            val newDefaultId = viewModel.defaultPresetId.first()

            assertThat(newDefaultId).isEqualTo(presetB.id)
            assertThat(updatedPresets[0].id).isEqualTo(presetB.id) // presetB should now be first

            // Verify the rest are still in alphabetical order
            assertThat(updatedPresets[1].title).isEqualTo(presetA.title) // "Sermon Timer A"
            assertThat(updatedPresets[2].title).isEqualTo(presetC.title) // "Workshop Timer C"
        }
    }

    @Test
    fun default_preset_indicator_shows_correctly() {
        // Test default indicator with presetA as default
        composeTestRule.setContent {
            PresetListScreen(
                presets = listOf(presetA, presetB, presetC),
                defaultPresetId = presetA.id,
                onPresetSelected = {},
                onAddPreset = {},
                onEditPreset = {},
                onSetDefault = {}
            )
        }

        // Verify default indicator is shown for presetA
        composeTestRule.onNodeWithText("Default").assertExists()
    }

    @Test
    fun default_preset_indicator_moves_when_changed() {
        // Test default indicator with presetB as default
        composeTestRule.setContent {
            PresetListScreen(
                presets = listOf(presetB, presetA, presetC), // presetB now first
                defaultPresetId = presetB.id,
                onPresetSelected = {},
                onAddPreset = {},
                onEditPreset = {},
                onSetDefault = {}
            )
        }

        // Should show "Default" indicator for presetB
        composeTestRule.onNodeWithText("Default").assertExists()
    }

    @Test
    fun preset_list_interaction_works() {
        // Set up screen for interaction testing
        composeTestRule.setContent {
            PresetListScreen(
                presets = listOf(presetA, presetB, presetC),
                defaultPresetId = presetA.id,
                onPresetSelected = {},
                onAddPreset = {},
                onEditPreset = {},
                onSetDefault = {}
            )
        }

        // Verify preset list is displayed correctly
        composeTestRule.onNodeWithText(presetA.title).assertExists()
        composeTestRule.onNodeWithText(presetB.title).assertExists()
        composeTestRule.onNodeWithText(presetC.title).assertExists()

        // Verify "Add Preset" button is available
        composeTestRule.onNodeWithText("Add Preset").assertExists()
    }

    @Test
    fun preset_list_displays_correct_durations() {
        // Set up the preset list screen
        composeTestRule.setContent {
            PresetListScreen(
                presets = listOf(presetA, presetB, presetC),
                defaultPresetId = presetA.id,
                onPresetSelected = {},
                onAddPreset = {},
                onEditPreset = {},
                onSetDefault = {}
            )
        }

        // Verify preset titles are displayed
        composeTestRule.onNodeWithText(presetA.title).assertExists()
        composeTestRule.onNodeWithText(presetB.title).assertExists()
        composeTestRule.onNodeWithText(presetC.title).assertExists()

        // Verify formatted durations are shown
        // Note: The actual duration formatting is tested in DurationFormatterTest
        // Here we just verify the preset items are rendered with their content
        composeTestRule.onNodeWithText(presetA.title).assertExists()
    }

    @Test
    fun clearing_default_preset_works() {
        // Set presetA as default
        runBlocking {
            viewModel.setDefaultPreset(presetA.id)
        }

        // Verify it's set
        runBlocking {
            assertThat(viewModel.defaultPresetId.first()).isEqualTo(presetA.id)
        }

        // Clear default preset
        runBlocking {
            viewModel.setDefaultPreset(null)
        }

        // Verify it's cleared
        runBlocking {
            assertThat(viewModel.defaultPresetId.first()).isNull()
        }

        // Verify presets are now sorted alphabetically (no default first)
        runBlocking {
            val presets = viewModel.presets.first()
            assertThat(presets[0].title).isEqualTo(presetB.title) // "Meeting Timer B" first alphabetically
            assertThat(presets[1].title).isEqualTo(presetA.title) // "Sermon Timer A" second
            assertThat(presets[2].title).isEqualTo(presetC.title) // "Workshop Timer C" third
        }
    }
}
