package com.example.sermontimer

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.Segment
import com.example.sermontimer.domain.model.SegmentDurations
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.presentation.TimerViewModel
import com.example.sermontimer.presentation.WearApp
import com.example.sermontimer.ui.PresetListScreen
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI smoke tests to verify screens display correctly on Wear OS.
 * Tests basic navigation and UI functionality.
 */
@RunWith(AndroidJUnit4::class)
class TimerUISmokeTest {

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
            id = "ui_test_preset",
            title = "UI Test Timer",
            introSec = 10,
            mainSec = 300,
            outroSec = 30
        )

        // Save test preset
        runBlocking {
            TimerDataProvider.getRepository().savePreset(testPreset)
        }

        viewModel = TimerViewModel(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        )
    }

    @Test
    fun presetListScreen_displaysCorrectly() {
        composeTestRule.setContent {
            PresetListScreen(
                presets = listOf(testPreset),
                defaultPresetId = testPreset.id,
                onPresetSelected = {},
                onAddPreset = {},
                onEditPreset = {},
                onSetDefault = {}
            )
        }

        // Verify preset is displayed
        composeTestRule.onNodeWithText(testPreset.title).assertExists()

        // Verify default indicator is shown
        composeTestRule.onNodeWithText("Default").assertExists()
    }

    @Test
    fun wearApp_navigatesBetweenScreens() {
        composeTestRule.setContent {
            WearApp(viewModel)
        }

        // Initially should show preset list
        composeTestRule.onNodeWithText("Presets").assertExists()

        // Start timer to navigate to timer screen
        runBlocking {
            viewModel.startTimer(testPreset)
        }

        // Should now show timer screen
        composeTestRule.onNodeWithText("Pause").assertExists()
        composeTestRule.onNodeWithText("Stop").assertExists()
    }

    @Test
    fun timerScreen_showsCorrectState() {
        val testState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.MAIN,
            remainingInSegmentSec = 245, // 4:05 remaining
            elapsedTotalSec = 55,
            durations = SegmentDurations(testPreset.introSec, testPreset.mainSec, testPreset.outroSec),
            startedAtElapsedRealtime = System.currentTimeMillis(),
            activePreset = null // Simplified for test
        )

        composeTestRule.setContent {
            com.example.sermontimer.ui.TimerScreen(
                timerState = testState,
                onPause = {},
                onResume = {},
                onSkip = {},
                onStop = {}
            )
        }

        // Verify timer shows remaining time
        composeTestRule.onNodeWithText("4:05").assertExists()

        // Verify segment indicator
        composeTestRule.onNodeWithText("M").assertExists()

        // Verify control buttons
        composeTestRule.onNodeWithText("Pause").assertExists()
        composeTestRule.onNodeWithText("Skip").assertExists()
        composeTestRule.onNodeWithText("Stop").assertExists()
    }

    @Test
    fun timerViewModel_stateManagementWorks() {
        // Test that view model properly manages timer state
        assertThat(viewModel).isNotNull()

        // Start timer
        runBlocking {
            viewModel.startTimer(testPreset)
        }

        // Verify state is updated
        runBlocking {
            val state = viewModel.timerState.value
            assertThat(state).isNotNull()
            assertThat(state?.status).isEqualTo(RunStatus.RUNNING)
            assertThat(state?.activePreset?.id).isEqualTo(testPreset.id)
        }

        // Stop timer
        runBlocking {
            viewModel.stopTimer()
        }

        // Verify stopped
        runBlocking {
            val state = viewModel.timerState.value
            assertThat(state?.status).isEqualTo(RunStatus.IDLE)
        }
    }
}
