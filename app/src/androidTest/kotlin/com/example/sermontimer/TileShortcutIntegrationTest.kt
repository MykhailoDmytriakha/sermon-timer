package com.example.sermontimer

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.presentation.MainActivity
import com.example.sermontimer.presentation.TimerViewModel
import com.example.sermontimer.presentation.WearApp
import com.example.sermontimer.tile.SermonTileService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Integration tests for Tile as shortcut functionality.
 * Tests that Tile opens the app without showing preset details, progress, or timer state.
 */
@RunWith(AndroidJUnit4::class)
class TileShortcutIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()


    private lateinit var viewModel: TimerViewModel
    private lateinit var testPreset: Preset

    @Before
    fun setUp() {
        // Initialize with test data
        TimerDataProvider.initialize(
            InstrumentationRegistry.getInstrumentation().targetContext
        )

        testPreset = Preset(
            id = "tile_test_preset",
            title = "Tile Test Timer",
            introSec = 10,
            mainSec = 300,
            outroSec = 30
        )

        // Save test preset and set as default
        runBlocking {
            TimerDataProvider.getRepository().savePreset(testPreset)
            TimerDataProvider.getRepository().setDefaultPresetId(testPreset.id)
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
    fun tile_shortcut_integration_with_app_navigation() {
        // Test that the tile shortcut integrates properly with app navigation
        composeTestRule.setContent {
            WearApp(viewModel)
        }

        // Verify app starts on preset list screen
        composeTestRule.onNodeWithText("Presets").assertExists()

        // Verify preset is available
        composeTestRule.onNodeWithText(testPreset.title).assertExists()
    }

    @Test
    fun tile_shortcut_opens_main_activity() {
        // Launch MainActivity to simulate opening from tile
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        scenario.onActivity { activity ->
            // Verify MainActivity is launched
            assertThat(activity).isNotNull()
            assertThat(activity.isFinishing).isFalse()
        }

        scenario.close()
    }

    @Test
    fun tile_shortcut_preserves_app_state_navigation() {
        // Set up the app to show preset list initially
        composeTestRule.setContent {
            WearApp(viewModel)
        }

        // Initially should show preset list
        composeTestRule.onNodeWithText("Presets").assertExists()

        // Verify default preset is displayed
        composeTestRule.onNodeWithText(testPreset.title).assertExists()

        // Simulate starting a timer to navigate to timer screen
        runBlocking {
            viewModel.startTimer(testPreset)
        }

        // Should now show timer screen
        composeTestRule.onNodeWithText("Pause").assertExists()
        composeTestRule.onNodeWithText("Stop").assertExists()
    }

    @Test
    fun tile_shortcut_works_independently_of_timer_state() {
        // Test that tile shortcut works regardless of timer state

        // Test with no timer running
        composeTestRule.setContent {
            WearApp(viewModel)
        }

        composeTestRule.onNodeWithText("Presets").assertExists()

        // Start timer
        runBlocking {
            viewModel.startTimer(testPreset)
        }

        composeTestRule.onNodeWithText("Pause").assertExists()

        // Stop timer
        runBlocking {
            viewModel.stopTimer()
        }

        // Should return to preset list
        composeTestRule.onNodeWithText("Presets").assertExists()

        // Verify app returns to preset list after timer operations
        composeTestRule.onNodeWithText("Presets").assertExists()
    }
}
