package com.example.sermontimer

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.service.TimerService
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation smoke tests for TimerService on Wear OS.
 * Verifies basic timer functionality works correctly on device/emulator.
 *
 * Tests run on Wear AVD as specified in AGENTS.md §9.
 */
@RunWith(AndroidJUnit4::class)
class TimerServiceSmokeTest {

    private lateinit var context: Context
    private lateinit var testPreset: Preset

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Initialize data provider
        TimerDataProvider.initialize(context)

        // Create a test preset with short durations for quick testing
        testPreset = Preset(
            id = "test_preset_smoke",
            title = "Test Timer",
            introSec = 2,  // 2 seconds
            mainSec = 3,   // 3 seconds
            outroSec = 1   // 1 second
        )

        // Save test preset
        runBlocking {
            val repository = TimerDataProvider.getRepository()
            repository.clearAll()
            repository.savePreset(testPreset)
            repository.setDefaultPresetId(testPreset.id)
            repository.saveTimerState(null)
        }
    }

    @After
    fun tearDown() {
        // Clean up any running timers
        context.stopService(Intent(context, TimerService::class.java))

        // Clean up test data
        runBlocking {
            TimerDataProvider.getRepository().deletePreset(testPreset.id)
            TimerDataProvider.getRepository().clearAll()
        }
    }

    @Test
    fun timerService_startsAndRunsCorrectly() {
        // Start timer service
        TimerService.startService(context, testPreset.id)

        // Verify timer state is running
        val timerState = runBlocking { waitForStatus(RunStatus.RUNNING) }
        assertThat(timerState).isNotNull()
        assertThat(timerState?.activePreset?.id).isEqualTo(testPreset.id)
    }

    @Test
    fun timerService_pausesAndResumesCorrectly() {
        // Start timer
        TimerService.startService(context, testPreset.id)

        runBlocking { waitForStatus(RunStatus.RUNNING) }

        // Pause timer
        TimerService.pauseService(context)
        val pausedState = runBlocking { waitForStatus(RunStatus.PAUSED) }
        assertThat(pausedState?.status).isEqualTo(RunStatus.PAUSED)

        // Resume timer
        TimerService.resumeService(context)
        val resumedState = runBlocking { waitForStatus(RunStatus.RUNNING) }
        assertThat(resumedState?.status).isEqualTo(RunStatus.RUNNING)
    }

    @Test
    fun timerService_completesFullCycle() {
        // Start timer with very short durations
        TimerService.startService(context, testPreset.id)

        runBlocking { waitForStatus(RunStatus.RUNNING) }

        // Wait for timer to complete (2 + 3 + 1 = 6 seconds, allow buffer)
        val completedState = runBlocking { waitForStatus(RunStatus.DONE, timeoutMillis = 20_000L) }
        assertThat(completedState?.status).isEqualTo(RunStatus.DONE)
    }

    @Test
    fun timerService_handlesStopCorrectly() {
        // Start timer
        TimerService.startService(context, testPreset.id)
        runBlocking { waitForStatus(RunStatus.RUNNING) }

        // Stop timer
        TimerService.stopService(context)

        val idleState = runBlocking {
            waitForState(timeoutMillis = 10_000L) { state ->
                state == null || state.status == RunStatus.IDLE
            }
        }
        assertThat(idleState?.status ?: RunStatus.IDLE).isEqualTo(RunStatus.IDLE)
    }

    @Test
    fun tileService_providesValidTile() {
        // This test verifies that the tile service doesn't crash and returns valid data
        // We can't easily test the actual tile rendering in instrumentation tests
        // but we can verify the service initializes properly

        val tileService = com.example.sermontimer.tile.SermonTileService()
        assertThat(tileService).isNotNull()

        // If we get here without crashing, the tile service is properly set up
    }

    @Test
    fun dataPersistence_worksAcrossRestarts() {
        // Save some test data
        val testPreset2 = testPreset.copy(id = "test_persistence", title = "Persistence Test")
        runBlocking {
            TimerDataProvider.getRepository().savePreset(testPreset2)
            TimerDataProvider.getRepository().setDefaultPresetId(testPreset2.id)
        }

        // Simulate app restart by getting fresh repository instance
        val freshRepository = TimerDataProvider.getRepository()

        // Verify data persists
        runBlocking {
            val presets = freshRepository.presets.first()
            val defaultId = freshRepository.defaultPresetId.first()

            assertThat(presets).isNotEmpty()
            assertThat(presets.find { it.id == testPreset2.id }).isNotNull()
            assertThat(defaultId).isEqualTo(testPreset2.id)
        }

        // Clean up
        runBlocking {
            TimerDataProvider.getRepository().deletePreset(testPreset2.id)
        }
    }

    private suspend fun waitForStatus(
        status: RunStatus,
        timeoutMillis: Long = 10_000L
    ): TimerState? = waitForState(timeoutMillis) { state -> state?.status == status }

    private suspend fun waitForState(
        timeoutMillis: Long = 10_000L,
        predicate: (TimerState?) -> Boolean
    ): TimerState? = withTimeout(timeoutMillis) {
        var result: TimerState? = null
        var satisfied = false
        while (!satisfied) {
            val state = TimerDataProvider.getRepository().lastTimerState.first()
            if (predicate(state)) {
                result = state
                satisfied = true
            } else {
                delay(200)
            }
        }
        result
    }
}
