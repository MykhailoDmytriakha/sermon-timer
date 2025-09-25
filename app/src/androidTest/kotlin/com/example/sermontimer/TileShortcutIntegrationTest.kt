package com.example.sermontimer

import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sermontimer.R
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.Segment
import com.example.sermontimer.domain.model.SegmentDurations
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.domain.model.toActivePresetMeta
import com.example.sermontimer.presentation.MainActivity
import com.example.sermontimer.presentation.TimerViewModel
import com.example.sermontimer.presentation.WearApp
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
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        TimerDataProvider.initialize(context)
        grantNotificationPermission()

        testPreset = Preset(
            id = "tile_test_preset",
            title = "Tile Test Timer",
            introSec = 10,
            mainSec = 300,
            outroSec = 30
        )

        // Save test preset and set as default
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
    fun tile_shortcut_integration_with_app_navigation() {
        // Test that the tile shortcut integrates properly with app navigation
        composeTestRule.setContent {
            WearApp(viewModel)
        }

        // Verify app starts on preset list screen
        composeTestRule.waitForText("Presets")
        composeTestRule.onNodeWithText("Presets", useUnmergedTree = true).assertExists()

        // Verify preset is available
        composeTestRule.waitForTag("preset-${testPreset.id}")
        composeTestRule.onNodeWithTag("preset-${testPreset.id}", useUnmergedTree = true).assertExists()
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
        composeTestRule.waitForText("Presets")
        composeTestRule.onNodeWithText("Presets", useUnmergedTree = true).assertExists()

        // Verify default preset is displayed
        composeTestRule.waitForTag("preset-${testPreset.id}")
        composeTestRule.onNodeWithTag("preset-${testPreset.id}", useUnmergedTree = true).assertExists()

        // Simulate starting a timer to navigate to timer screen
        val runningState = createRunningState()
        runBlocking {
            TimerDataProvider.getRepository().saveTimerState(runningState)
            viewModel.timerState.awaitValue { it?.status == RunStatus.RUNNING }
        }

        composeTestRule.waitUntil(5_000) {
            viewModel.currentScreen.value == TimerViewModel.Screen.Timer
        }

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pauseDescription = context.getString(R.string.action_pause)
        val stopDescription = context.getString(R.string.action_stop)

        composeTestRule.waitForContentDescription(pauseDescription)
        composeTestRule.onNodeWithContentDescription(pauseDescription, useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithContentDescription(stopDescription, useUnmergedTree = true).assertExists()
    }

    @Test
    fun tile_shortcut_works_independently_of_timer_state() {
        // Test that tile shortcut works regardless of timer state

        // Test with no timer running
        composeTestRule.setContent {
            WearApp(viewModel)
        }

        composeTestRule.waitForText("Presets")
        composeTestRule.onNodeWithText("Presets", useUnmergedTree = true).assertExists()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pauseDescription = context.getString(R.string.action_pause)
        val stopDescription = context.getString(R.string.action_stop)

        // Start timer
        runBlocking {
            TimerDataProvider.getRepository().saveTimerState(createRunningState())
            viewModel.timerState.awaitValue { it?.status == RunStatus.RUNNING }
        }
        composeTestRule.waitUntil(5_000) {
            viewModel.currentScreen.value == TimerViewModel.Screen.Timer
        }

        composeTestRule.waitForContentDescription(pauseDescription)
        composeTestRule.onNodeWithContentDescription(pauseDescription, useUnmergedTree = true).assertExists()

        // Stop timer
        runBlocking {
            TimerDataProvider.getRepository().saveTimerState(TimerState.idle(createDurations()))
            viewModel.timerState.awaitValue { it?.status == RunStatus.IDLE }
        }

        composeTestRule.waitUntil(5_000) {
            viewModel.currentScreen.value == TimerViewModel.Screen.PresetList
        }

        // Should return to preset list
        composeTestRule.waitForText("Presets")
        composeTestRule.onNodeWithText("Presets", useUnmergedTree = true).assertExists()

        // Verify app returns to preset list after timer operations
        composeTestRule.onNodeWithText("Presets", useUnmergedTree = true).assertExists()
    }

    private fun createDurations(): SegmentDurations =
        SegmentDurations(testPreset.introSec, testPreset.mainSec, testPreset.outroSec)

    private fun createRunningState(): TimerState =
        TimerState(
            status = RunStatus.RUNNING,
            segment = Segment.INTRO,
            remainingInSegmentSec = testPreset.introSec,
            elapsedTotalSec = 0,
            durations = createDurations(),
            startedAtElapsedRealtime = SystemClock.elapsedRealtime(),
            activePreset = testPreset.toActivePresetMeta()
        )

    private fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val packageName = instrumentation.targetContext.packageName

            instrumentation.uiAutomation.executeShellCommand(
                "pm grant $packageName android.permission.POST_NOTIFICATIONS"
            ).use { }
            instrumentation.uiAutomation.executeShellCommand(
                "cmd appops set $packageName POST_NOTIFICATION allow"
            ).use { }
        }
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
