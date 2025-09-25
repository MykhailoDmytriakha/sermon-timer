package com.example.sermontimer

import android.app.Application
import android.os.SystemClock
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.Segment
import com.example.sermontimer.domain.model.SegmentDurations
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.domain.model.toActivePresetMeta
import com.example.sermontimer.presentation.TimerViewModel
import com.example.sermontimer.presentation.WearApp
import com.example.sermontimer.ui.PresetListScreen
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        TimerDataProvider.initialize(context)

        testPreset = Preset(
            id = "ui_test_preset",
            title = "UI Test Timer",
            introSec = 10,
            mainSec = 300,
            outroSec = 30
        )

        // Save test preset
        runBlocking {
            val repository = TimerDataProvider.getRepository()
            repository.clearAll()
            repository.savePreset(testPreset)
            repository.setDefaultPresetId(testPreset.id)
            repository.saveTimerState(null)
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
        composeTestRule.onNodeWithTag("preset-${testPreset.id}", useUnmergedTree = true).assertExists()

        // Verify default indicator is shown (icon with content description)
        // TODO: Test for default indicator icon presence
    }

    @Test
    fun wearApp_navigatesBetweenScreens() {
        composeTestRule.setContent {
            WearApp(viewModel)
        }

        // Initially should show preset list
        composeTestRule.waitForText("Presets")
        composeTestRule.onNodeWithText("Presets", useUnmergedTree = true).assertExists()
        composeTestRule.waitForTag("preset-${testPreset.id}")
        composeTestRule.onNodeWithTag("preset-${testPreset.id}", useUnmergedTree = true).assertExists()

        // Simulate running state to navigate to timer screen
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.INTRO,
            remainingInSegmentSec = testPreset.introSec,
            elapsedTotalSec = 0,
            durations = SegmentDurations(testPreset.introSec, testPreset.mainSec, testPreset.outroSec),
            startedAtElapsedRealtime = SystemClock.elapsedRealtime(),
            activePreset = testPreset.toActivePresetMeta()
        )

        runBlocking {
            TimerDataProvider.getRepository().saveTimerState(runningState)
            viewModel.timerState.awaitValue { it?.status == RunStatus.RUNNING }
        }

        // Should now show timer screen
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pauseDescription = context.getString(R.string.action_pause)
        val stopDescription = context.getString(R.string.action_stop)

        composeTestRule.waitForContentDescription(pauseDescription)
        composeTestRule.onNodeWithContentDescription(pauseDescription, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithContentDescription(stopDescription, useUnmergedTree = true).assertExists()
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
            activePreset = testPreset.toActivePresetMeta() // Provide active preset for RUNNING status
        )

        composeTestRule.setContent {
            com.example.sermontimer.ui.TimerScreen(
                timerState = testState,
                ambientState = com.example.sermontimer.presentation.AmbientUiState(),
                onPause = {},
                onResume = {},
                onSkip = {},
                onStop = {}
            )
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val segmentLabel = context.getString(R.string.segment_main)
        val pauseDescription = context.getString(R.string.action_pause)
        val skipLabel = context.getString(R.string.action_skip)
        val stopDescription = context.getString(R.string.action_stop)

        // Verify timer shows remaining time
        composeTestRule.onNodeWithText("04:05", useUnmergedTree = true).assertExists()

        // Verify segment indicator
        composeTestRule.onNodeWithText(segmentLabel, useUnmergedTree = true).assertExists()

        // Verify control buttons
        composeTestRule.onNodeWithContentDescription(pauseDescription, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithText(skipLabel, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithContentDescription(stopDescription, useUnmergedTree = true).assertExists()
    }

    @Test
    fun timerViewModel_stateManagementWorks() {
        // Test that view model properly manages timer state
        assertThat(viewModel).isNotNull()

        val repository = TimerDataProvider.getRepository()
        val durations = SegmentDurations(testPreset.introSec, testPreset.mainSec, testPreset.outroSec)

        // Simulate running state emitted by service
        val runningState = TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.MAIN,
            remainingInSegmentSec = durations.mainSec,
            elapsedTotalSec = durations.introSec,
            durations = durations,
            startedAtElapsedRealtime = SystemClock.elapsedRealtime(),
            activePreset = testPreset.toActivePresetMeta()
        )

        runBlocking {
            repository.saveTimerState(runningState)
        }

        val observedRunningState = runBlocking {
            viewModel.timerState.awaitValue { it?.status == RunStatus.RUNNING }
        }
        assertThat(observedRunningState?.status).isEqualTo(RunStatus.RUNNING)
        assertThat(observedRunningState?.activePreset?.id).isEqualTo(testPreset.id)

        // Simulate stop/idle
        runBlocking {
            repository.saveTimerState(TimerState.idle(durations))
        }

        val observedIdleState = runBlocking {
            viewModel.timerState.awaitValue { it?.status == RunStatus.IDLE }
        }
        assertThat(observedIdleState?.status).isEqualTo(RunStatus.IDLE)
    }
}

private fun ComposeTestRule.waitForText(text: String, timeoutMillis: Long = 5_000L) {
    waitUntil(timeoutMillis) {
        onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun ComposeTestRule.waitForTag(tag: String, timeoutMillis: Long = 5_000L) {
    waitUntil(timeoutMillis) {
        onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
}

private fun ComposeTestRule.waitForContentDescription(description: String, timeoutMillis: Long = 5_000L) {
    waitUntil(timeoutMillis) {
        onAllNodesWithContentDescription(description, useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()
    }
}

private suspend fun <T> Flow<T>.awaitValue(
    timeoutMillis: Long = 5_000L,
    predicate: (T) -> Boolean
): T = withTimeout(timeoutMillis) { first { predicate(it) } }
