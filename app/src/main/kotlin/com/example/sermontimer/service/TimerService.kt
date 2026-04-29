package com.example.sermontimer.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.LocusIdCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TileUpdateRequester
import com.example.sermontimer.R
import com.example.sermontimer.complication.TimerComplicationService
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.domain.engine.CoroutineTimerEngine
import com.example.sermontimer.domain.engine.DefaultTimerStateReducer
import com.example.sermontimer.domain.engine.TimerCommand
import com.example.sermontimer.domain.engine.TimerEvent
import com.example.sermontimer.domain.engine.TimerStateReducer
import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.Segment
import com.example.sermontimer.domain.model.SegmentDurations
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.domain.time.MonotonicTimeProvider
import com.example.sermontimer.presentation.MainActivity
import com.example.sermontimer.tile.SermonTileService
import com.example.sermontimer.util.DurationFormatter
import com.example.sermontimer.util.HapticPatterns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class TimerService : Service() {

    private lateinit var engine: CoroutineTimerEngine
    private lateinit var reducer: TimerStateReducer
    private lateinit var timeProvider: MonotonicTimeProvider
    private lateinit var dataRepository: com.example.sermontimer.data.TimerDataRepository
    private lateinit var serviceScope: CoroutineScope
    private lateinit var hapticPatterns: HapticPatterns
    private lateinit var tileUpdateRequester: TileUpdateRequester
    private lateinit var countdownScheduler: CountdownAlarmScheduler

    private var timerJob: Job? = null
    private var chipRefreshJob: Job? = null
    private val notificationManager by lazy { getSystemService(NOTIFICATION_SERVICE) as NotificationManager }

    // Guard for engine initialization race condition
    private var engineReady = false
    private val pendingCommands = mutableListOf<Pair<TimerCommand, (() -> Unit)?>>()
    private var observedNonIdleState = false

    // Track countdown scheduling per upcoming boundary (monotonic ms)
    private var scheduledCountdownForBoundaryMs: Long? = null
    private var immediateCountdownStartedForBoundaryMs: Long? = null
    private var exactAlarmAccessMissing = false
    private var lastKnownState: TimerState? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "timer"
        private const val CHANNEL_NAME = "Timer Service"
        private const val LOCUS_ID = "sermon-timer-active"
        private const val COUNTDOWN_SECONDS = 10
        private const val COUNTDOWN_WINDOW_MS = COUNTDOWN_SECONDS * 1000L
        private const val COUNTDOWN_GRACE_MS = 500L
        private const val REQUEST_EXACT_ALARM_SETTINGS = 1001

        // Intent actions
        const val ACTION_START = "com.example.sermontimer.START"
        const val ACTION_PAUSE = "com.example.sermontimer.PAUSE"
        const val ACTION_RESUME = "com.example.sermontimer.RESUME"
        const val ACTION_SKIP = "com.example.sermontimer.SKIP"
        const val ACTION_STOP = "com.example.sermontimer.STOP"
        private const val ACTION_COUNTDOWN_ALARM = "com.example.sermontimer.COUNTDOWN_ALARM"

        // Intent extras
        const val EXTRA_PRESET_ID = "preset_id"
        private const val EXTRA_COUNTDOWN_BOUNDARY_AT = "countdown_boundary_at"

        fun startService(context: Context, presetId: String) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PRESET_ID, presetId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun skipService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_SKIP
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        internal fun createCountdownIntent(context: Context, boundaryAtElapsedMs: Long): Intent {
            return Intent(context, TimerService::class.java).apply {
                action = ACTION_COUNTDOWN_ALARM
                putExtra(EXTRA_COUNTDOWN_BOUNDARY_AT, boundaryAtElapsedMs)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        dataRepository = TimerDataProvider.getRepository()
        timeProvider = MonotonicTimeProvider { android.os.SystemClock.elapsedRealtime() }
        reducer = DefaultTimerStateReducer()
        hapticPatterns = HapticPatterns(this)
        tileUpdateRequester = TileService.getUpdater(applicationContext)
        countdownScheduler = CountdownAlarmScheduler(
            context = this,
            onTrigger = { boundaryAtMs -> onCountdownAlarmFired(boundaryAtMs) },
            onExactAlarmAccessMissing = { markExactAlarmAccessMissing() },
            onExactAlarmAccessRestored = { clearExactAlarmAccessWarning() },
        )

        // Try to recover state from DataStore
        serviceScope.launch {
            val lastState = dataRepository.lastTimerState.first()
            val initialState = lastState ?: TimerState.idle(SegmentDurations(0, 0, 0))
            engine = CoroutineTimerEngine(reducer, serviceScope, initialState)

            // Mark engine as ready and process any pending commands
            engineReady = true
            processPendingCommands()

            // Start observing state changes
            observeTimerState()
            observeTimerEvents()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground service immediately to prevent timeout
        val initialState = TimerState.idle(SegmentDurations(0, 0, 0))
        val notification = createNotification(initialState)

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)

        when (intent?.action) {
            ACTION_START -> {
                val presetId = intent.getStringExtra(EXTRA_PRESET_ID)
                if (presetId != null) {
                    serviceScope.launch {
                        startTimerWithPreset(presetId)
                    }
                }
            }

            ACTION_PAUSE -> safeSubmit(TimerCommand.Pause(timeProvider.elapsedRealtimeMillis()))
            ACTION_RESUME -> safeSubmit(TimerCommand.Resume(timeProvider.elapsedRealtimeMillis()))
            ACTION_SKIP -> safeSubmit(TimerCommand.SkipSegment(timeProvider.elapsedRealtimeMillis()))
            ACTION_STOP -> stopTimer()
            ACTION_COUNTDOWN_ALARM -> {
                val boundaryAtMs = intent.getLongExtra(EXTRA_COUNTDOWN_BOUNDARY_AT, -1L)
                if (boundaryAtMs > 0) {
                    android.util.Log.d(
                        "TIMER",
                        "COUNTDOWN: pending intent fired for boundary=$boundaryAtMs"
                    )
                    countdownScheduler.handlePendingIntentTrigger(boundaryAtMs)
                } else {
                    android.util.Log.w("TIMER", "COUNTDOWN: missing boundary extra in alarm intent")
                }
            }

            null -> {
                // Service restarted by system, try to restore state
                serviceScope.launch {
                    // Wait for engine to be ready
                    while (!::engine.isInitialized || !engineReady) {
                        delay(10)
                    }

                    val lastState = dataRepository.lastTimerState.first()
                    if (lastState != null && lastState.isActive) {
                        // Find the preset by ID and restore the state
                        val preset = dataRepository.presets.first()
                            .find { it.id == lastState.activePreset?.id }
                        if (preset != null) {
                            val settings = dataRepository.appSettings.first()
                            engine.submit(
                                TimerCommand.Start(
                                    preset = preset,
                                    monotonicStartMs = timeProvider.elapsedRealtimeMillis(),
                                    prerollSec = settings.prerollSec,
                                    overtimeMaxSec = settings.overtimeMaxSec,
                                )
                            )
                        }
                    }
                }
            }
        }

        return START_STICKY
    }

    private fun safeSubmit(command: TimerCommand, onSubmitted: (() -> Unit)? = null) {
        if (::engine.isInitialized && engineReady) {
            engine.submit(command)
            onSubmitted?.invoke()
        } else {
            // Buffer command until engine is ready
            synchronized(pendingCommands) {
                pendingCommands.add(command to onSubmitted)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        chipRefreshJob?.cancel()
        timerJob?.cancel()
        serviceScope.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private suspend fun startTimerWithPreset(presetId: String) {
        // Wait for engine to be ready if it's not initialized yet
        while (!::engine.isInitialized || !engineReady) {
            delay(10)
        }

        val preset = dataRepository.presets.first().find { it.id == presetId }
        if (preset != null) {
            val settings = dataRepository.appSettings.first()
            val startCommand = TimerCommand.Start(
                preset = preset,
                monotonicStartMs = timeProvider.elapsedRealtimeMillis(),
                prerollSec = settings.prerollSec,
                overtimeMaxSec = settings.overtimeMaxSec,
            )
            engine.submit(startCommand)
            // No-preroll path: reducer goes straight to RUNNING with no PrerollStarted event,
            // so play the start cue here. Preroll path's haptic is fired by handlePrerollStarted.
            if (settings.prerollSec == 0) {
                hapticPatterns.playStartPattern()
            }
            // Reflect start on the tile once; avoid per-second updates
            try {
                tileUpdateRequester.requestUpdate(SermonTileService::class.java)
            } catch (_: Exception) {
            }
            TimerComplicationService.requestUpdate(this)
        }
    }

    private fun observeTimerState() {
        serviceScope.launch {
            engine.state.collect { state ->
                val previous = lastKnownState
                lastKnownState = state
                // Wear OS throttles Ongoing Activity updates that arrive too quickly and
                // falls back to icon-only when bombed each second. Push only on meaningful
                // transitions; Status.TimerPart ticks the chip's text on its own.
                if (shouldRepublishNotification(previous, state)) {
                    updateNotification(state)
                }
                saveStateToDataStore(state)

                // Start/stop timer job based on state
                if (state.isActive && timerJob?.isActive != true) {
                    startTimerJob()
                } else if (!state.isActive) {
                    timerJob?.cancel()
                }

                // Start/stop the chip-refresh job based on whether the watch face chip
                // should be visible. Refreshes setContentText every few seconds so the
                // launcher renders live mm:ss on faces that don't honour chronometer.
                if (shouldDisplayOngoingActivity(state) && chipRefreshJob?.isActive != true) {
                    startChipRefreshJob()
                } else if (!shouldDisplayOngoingActivity(state)) {
                    chipRefreshJob?.cancel()
                }

                if (state.status != RunStatus.IDLE) {
                    observedNonIdleState = true
                }

                // Countdown scheduling tied to upcoming boundary via AlarmManager
                scheduleOrRunCountdown(state)

                // Stop service after we've seen an active session return to idle
                if (state.status == RunStatus.IDLE && observedNonIdleState) {
                    observedNonIdleState = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf()
                }
            }
        }
    }

    private fun observeTimerEvents() {
        serviceScope.launch {
            engine.events.collect { event ->
                when (event) {
                    is TimerEvent.BoundaryReached -> handleBoundaryReached(event)
                    is TimerEvent.Completed -> handleTimerCompleted()
                    is TimerEvent.Paused -> handleTimerPaused()
                    is TimerEvent.Resumed -> handleTimerResumed()
                    is TimerEvent.Stopped -> handleTimerStopped()
                    is TimerEvent.PrerollStarted -> handlePrerollStarted()
                    is TimerEvent.PrerollEnded -> handlePrerollEnded()
                    is TimerEvent.OvertimeStarted -> handleOvertimeStarted()
                    is TimerEvent.OvertimeCapped -> handleTimerCompleted()
                    else -> {} // Ignore other events
                }
            }
        }
    }

    private fun scheduleOrRunCountdown(state: TimerState) {
        if (state.status != RunStatus.RUNNING || state.startedAtElapsedRealtime == null) {
            resetCountdownScheduling()
            return
        }

        val boundarySec = state.durations.cumulativeBoundaryFor(state.segment)
        val boundaryAtMs = state.startedAtElapsedRealtime + boundarySec * 1000L
        val triggerAtMs = boundaryAtMs - COUNTDOWN_WINDOW_MS
        val now = timeProvider.elapsedRealtimeMillis()

        if (state.remainingInSegmentSec > COUNTDOWN_SECONDS) {
            if (triggerAtMs <= now) {
                startCountdownForBoundary(boundaryAtMs)
            } else if (scheduledCountdownForBoundaryMs != boundaryAtMs) {
                android.util.Log.d(
                    "TIMER",
                    "COUNTDOWN: scheduling at t=$triggerAtMs for boundary=$boundaryAtMs"
                )
                countdownScheduler.schedule(triggerAtMs, boundaryAtMs)
                scheduledCountdownForBoundaryMs = boundaryAtMs
                immediateCountdownStartedForBoundaryMs = null
            }
        } else if (state.remainingInSegmentSec in 1..COUNTDOWN_SECONDS) {
            startCountdownForBoundary(boundaryAtMs)
        } else {
            resetCountdownScheduling()
        }
    }

    private fun onCountdownAlarmFired(boundaryAtMs: Long) {
        android.util.Log.d("TIMER", "COUNTDOWN: alarm fired for boundary=$boundaryAtMs")
        startCountdownForBoundary(boundaryAtMs)
    }

    private fun startCountdownForBoundary(boundaryAtMs: Long) {
        if (immediateCountdownStartedForBoundaryMs == boundaryAtMs) {
            return
        }
        val now = timeProvider.elapsedRealtimeMillis()
        val secondsLeft = calculateCountdownSeconds(boundaryAtMs, now) ?: run {
            resetCountdownScheduling()
            return
        }
        android.util.Log.d(
            "TIMER",
            "COUNTDOWN: starting countdown with secondsLeft=$secondsLeft (boundary=$boundaryAtMs, now=$now)",
        )
        countdownScheduler.cancel()
        hapticPatterns.startCountdownVibration(secondsLeft)
        immediateCountdownStartedForBoundaryMs = boundaryAtMs
        scheduledCountdownForBoundaryMs = null
    }

    private fun calculateCountdownSeconds(boundaryAtMs: Long, nowMs: Long): Int? {
        val millisLeft = boundaryAtMs - nowMs
        if (millisLeft <= -COUNTDOWN_GRACE_MS) {
            android.util.Log.d(
                "TIMER",
                "COUNTDOWN: boundary already passed (delta=${millisLeft}ms) — skipping countdown"
            )
            return null
        }
        val remainingMs = millisLeft.coerceAtLeast(0L)
        return (((remainingMs + 999L) / 1000L).toInt()).coerceIn(1, COUNTDOWN_SECONDS)
    }

    private fun resetCountdownScheduling() {
        countdownScheduler.cancel()
        scheduledCountdownForBoundaryMs = null
        immediateCountdownStartedForBoundaryMs = null
    }

    private fun startTimerJob() {
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1.seconds)
                val currentTime = timeProvider.elapsedRealtimeMillis()
                if (android.util.Log.isLoggable("TIMER", android.util.Log.DEBUG)) {
                    android.util.Log.d(
                        "TIMER",
                        "TICK: submitting Tick command at time=$currentTime"
                    )
                }
                engine.submit(TimerCommand.Tick(currentTime))
            }
        }
    }

    /**
     * Re-publishes the ongoing notification every few seconds with a freshly formatted
     * remaining-time string. Necessary because some Wear OS watch faces render the
     * chip's text from `setContentText` (a snapshot at notification time) rather than
     * from the live chronometer — without periodic refresh the chip would freeze on
     * the moment the timer started.
     *
     * Cadence: 3 s for the last 30 s of a phase (smooth final sweep) and 5 s otherwise.
     * That stays under the "a few updates per minute" budget Wear OS throttles against.
     */
    private fun startChipRefreshJob() {
        chipRefreshJob = serviceScope.launch {
            while (isActive) {
                val state = lastKnownState
                if (state != null && shouldDisplayOngoingActivity(state)) {
                    updateNotification(state)
                }
                val intervalMs = when {
                    state == null -> 5_000L
                    state.status == RunStatus.PREROLL && state.prerollRemainingSec <= 30 -> 3_000L
                    state.status == RunStatus.RUNNING && state.remainingInSegmentSec <= 30 -> 3_000L
                    state.status == RunStatus.OVERTIME -> 5_000L
                    else -> 5_000L
                }
                delay(intervalMs)
            }
        }
    }

    private fun stopTimer() {
        safeSubmit(TimerCommand.Stop)
    }

    private fun processPendingCommands() {
        synchronized(pendingCommands) {
            val commandsToProcess = pendingCommands.toList()
            pendingCommands.clear()

            commandsToProcess.forEach { (command, callback) ->
                engine.submit(command)
                callback?.invoke()
            }
        }
    }

    private suspend fun saveStateToDataStore(state: TimerState) {
        dataRepository.saveTimerState(state)
    }

    private fun updateNotification(state: TimerState) {
        val notification = createNotification(state)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Returns true if the new state warrants a notification republish. We rebuild the chip
     * only on state transitions (status / segment / preroll-or-overtime base / active preset)
     * — never per-tick. Wear OS's chip ticks the live mm:ss text via Status.TimerPart on
     * its own, and over-issuing causes the launcher to fall back to icon-only.
     */
    private fun shouldRepublishNotification(previous: TimerState?, current: TimerState): Boolean {
        if (previous == null) return true
        if (previous.status != current.status) return true
        if (previous.segment != current.segment) return true
        if (previous.activePreset?.id != current.activePreset?.id) return true
        // Re-anchor when the timer baseline shifts (resume, skip, alarm boundary correction).
        if (previous.startedAtElapsedRealtime != current.startedAtElapsedRealtime) return true
        // Re-anchor preroll / overtime totals when settings change between sessions.
        if (previous.prerollTotalSec != current.prerollTotalSec) return true
        if (previous.overtimeMaxSec != current.overtimeMaxSec) return true
        return false
    }

    private fun createNotification(state: TimerState): Notification {
        val title = buildNotificationTitle(state)
        val text = buildNotificationText(state)

        // Active = anything where the timer should keep visible chip on the watch face.
        val isOngoing = state.status == RunStatus.RUNNING ||
                state.status == RunStatus.PAUSED ||
                state.status == RunStatus.PREROLL ||
                state.status == RunStatus.OVERTIME

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_ongoing)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            // LocusId is what tells Wear OS to render the elongated chip with text on the
            // watch face (Promoted Ongoing). Without it the launcher falls back to icon-only.
            .setLocusId(LocusIdCompat(LOCUS_ID))
            .setContentIntent(createActivityIntent())

        // *** CRITICAL for the live-ticking pill chip ***
        // Same trick Samsung Clock and Google Clock use: built-in chronometer that the
        // notification framework ticks itself, no per-second republish from us.
        applyChronometer(builder, state)

        // Determinate progress drives the colour fill on the chip; accent matches phase.
        applyProgressAndColour(builder, state)

        // Add action buttons
        addNotificationActions(builder, state)
        maybeAddExactAlarmHint(builder)

        maybeApplyOngoingActivity(builder, state)

        return builder.build()
    }

    /**
     * Wires the system chronometer into the notification so the watch-face chip renders
     * a live, ticking mm:ss display — exactly the way Samsung Clock and Google Clock do.
     *
     * Mechanics: setUsesChronometer + setChronometerCountDown + setWhen(absoluteWallMs)
     * tells the platform to display the difference between `now` and `when`, ticking it
     * automatically. We never have to republish to advance the digits.
     *
     *   when = currentTime + remaining → counts down to zero
     *   when = currentTime - elapsed   → counts up from start (overtime / paused)
     */
    private fun applyChronometer(builder: NotificationCompat.Builder, state: TimerState) {
        val now = System.currentTimeMillis()
        when (state.status) {
            RunStatus.PREROLL -> {
                builder.setWhen(now + state.prerollRemainingSec * 1000L)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setShowWhen(true)
            }
            RunStatus.RUNNING -> {
                // Per-segment countdown gives users the most useful number on the chip.
                builder.setWhen(now + state.remainingInSegmentSec * 1000L)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .setShowWhen(true)
            }
            RunStatus.PAUSED -> {
                // Frozen timestamp; chronometer would tick incorrectly when paused.
                builder.setUsesChronometer(false)
                    .setShowWhen(false)
            }
            RunStatus.OVERTIME -> {
                // Count UP from when overtime began.
                val elapsed = state.overtimeElapsedSec * 1000L
                builder.setWhen(now - elapsed)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(false)
                    .setShowWhen(true)
            }
            RunStatus.DONE,
            RunStatus.IDLE -> {
                builder.setUsesChronometer(false).setShowWhen(false)
            }
        }
    }

    /**
     * Applies a determinate progress bar + accent colour to the foreground notification so
     * Wear OS renders an elongated horizontal "live notification" chip on the watch face
     * (the same UI Samsung's stock timer shows). The progress encodes how much time has
     * elapsed within the *current* phase — preroll countdown, total timer, or overtime —
     * and the colour matches the on-screen phase accent.
     */
    private fun applyProgressAndColour(builder: NotificationCompat.Builder, state: TimerState) {
        val total: Int
        val current: Int
        val accent: Int
        val indeterminate: Boolean
        when (state.status) {
            RunStatus.PREROLL -> {
                total = state.prerollTotalSec.coerceAtLeast(1)
                current = (total - state.prerollRemainingSec).coerceIn(0, total)
                accent = 0xFFFFB300.toInt()         // amber
                indeterminate = false
            }
            RunStatus.RUNNING, RunStatus.PAUSED -> {
                total = state.totalSec.coerceAtLeast(1)
                current = state.elapsedTotalSec.coerceIn(0, total)
                accent = when (state.segment) {
                    com.example.sermontimer.domain.model.Segment.INTRO -> 0xFF66BB6A.toInt()
                    com.example.sermontimer.domain.model.Segment.MAIN -> 0xFF42A5F5.toInt()
                    com.example.sermontimer.domain.model.Segment.OUTRO -> 0xFFFFA726.toInt()
                    com.example.sermontimer.domain.model.Segment.DONE -> 0xFFCE93D8.toInt()
                }
                indeterminate = false
            }
            RunStatus.OVERTIME -> {
                total = state.overtimeMaxSec.coerceAtLeast(1)
                current = state.overtimeElapsedSec.coerceIn(0, total)
                accent = 0xFFFF5252.toInt()         // red
                indeterminate = false
            }
            RunStatus.DONE -> {
                total = 100; current = 100
                accent = 0xFFCE93D8.toInt()
                indeterminate = false
            }
            RunStatus.IDLE -> {
                total = 0; current = 0
                accent = 0
                indeterminate = false
            }
        }
        if (state.status != RunStatus.IDLE) {
            builder.setProgress(total, current, indeterminate)
            builder.setColor(accent)
            builder.setColorized(true) // hint Wear OS to fill the chip with the accent
        }
    }

    private fun buildNotificationTitle(state: TimerState): String {
        val presetName = state.activePreset?.id ?: getString(R.string.app_name)
        val phaseText = when (state.status) {
            RunStatus.PREROLL -> getString(R.string.phase_preroll_short)
            RunStatus.OVERTIME -> getString(R.string.phase_overtime_short)
            else -> getPhaseShortLabel(state.segment)
        }
        return "$presetName • $phaseText"
    }

    private fun buildNotificationText(state: TimerState): String {
        return when (state.status) {
            RunStatus.DONE -> {
                val totalMinutes = state.totalSec / 60
                val totalSeconds = state.totalSec % 60
                getString(R.string.timer_completed_with_total, totalMinutes, totalSeconds)
            }
            RunStatus.PREROLL -> {
                getString(
                    R.string.notification_preroll_text,
                    DurationFormatter.formatTimerDisplay(state.prerollRemainingSec),
                )
            }
            RunStatus.OVERTIME -> {
                getString(
                    R.string.notification_overtime_text,
                    DurationFormatter.formatTimerDisplay(state.overtimeElapsedSec),
                )
            }
            else -> {
                val remainingMinutes = state.remainingInSegmentSec / 60
                val remainingSeconds = state.remainingInSegmentSec % 60
                val progressPercent =
                    if (state.totalSec > 0) {
                        ((state.elapsedTotalSec.toFloat() / state.totalSec.toFloat()) * 100).toInt()
                    } else 0
                getString(
                    R.string.remaining_time_with_progress,
                    remainingMinutes,
                    remainingSeconds,
                    progressPercent
                )
            }
        }
    }

    private fun addNotificationActions(builder: NotificationCompat.Builder, state: TimerState) {
        when (state.status) {
            RunStatus.PREROLL -> {
                // Skip jumps straight from preroll → RUNNING (preacher walks faster than expected).
                builder.addAction(createSkipAction())
                builder.addAction(createStopAction())
            }

            RunStatus.RUNNING -> {
                builder.addAction(createPauseAction())
                if (state.activePreset?.allowSkip == true) {
                    builder.addAction(createSkipAction())
                }
                builder.addAction(createStopAction())
            }

            RunStatus.PAUSED -> {
                builder.addAction(createResumeAction())
                builder.addAction(createStopAction())
            }

            RunStatus.OVERTIME -> {
                builder.addAction(createStopAction())
            }

            else -> {} // No actions for idle/done states
        }
    }

    private fun maybeAddExactAlarmHint(builder: NotificationCompat.Builder) {
        if (!exactAlarmAccessMissing) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) return

        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:$packageName")
        }
        val pendingIntent = if (packageManager.resolveActivity(intent, 0) != null) {
            PendingIntent.getActivity(
                this,
                REQUEST_EXACT_ALARM_SETTINGS,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } else {
            null
        }

        builder.setSubText(getString(R.string.notification_exact_alarm_needed))
        if (pendingIntent != null) {
            builder.addAction(
                NotificationCompat.Action.Builder(
                    0,
                    getString(R.string.action_grant_alarm_access),
                    pendingIntent,
                ).build(),
            )
        }
    }

    /**
     * Registers the foreground notification as an OngoingActivity — Wear OS uses this
     * registration to render the watch-face chip and to wire the touchIntent. The actual
     * live-ticking number on the chip comes from the chronometer set via [applyChronometer]
     * (Samsung's stock timer uses the same approach). We deliberately do NOT pass a
     * `Status` template here: when both Status and chronometer are present, the launcher
     * tries to substitute placeholders that the chronometer is already filling and ends up
     * showing icon-only.
     */
    private fun maybeApplyOngoingActivity(builder: NotificationCompat.Builder, state: TimerState) {
        if (!shouldDisplayOngoingActivity(state)) {
            return
        }
        if (!hasNotificationPermission()) {
            return
        }

        val ongoingActivity = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(Icon.createWithResource(this, R.drawable.ic_timer_ongoing))
            .setAnimatedIcon(Icon.createWithResource(this, R.drawable.ic_timer_ongoing))
            .setTouchIntent(createActivityIntent())
            .setTitle(getString(R.string.app_name))
            .setCategory(android.app.Notification.CATEGORY_STOPWATCH)
            .build()

        ongoingActivity.apply(applicationContext)
    }

    private fun shouldDisplayOngoingActivity(state: TimerState): Boolean {
        return state.status == RunStatus.RUNNING ||
                state.status == RunStatus.PAUSED ||
                state.status == RunStatus.PREROLL ||
                state.status == RunStatus.OVERTIME
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun buildOngoingStatusText(state: TimerState): String {
        val phaseLabel = getPhaseLabel(state.segment)
        return if (state.status == RunStatus.PAUSED) {
            getString(R.string.ongoing_status_paused, phaseLabel)
        } else {
            val remaining = DurationFormatter.formatTimerDisplay(state.remainingInSegmentSec)
            getString(R.string.ongoing_status_running, phaseLabel, remaining)
        }
    }

    private fun getPhaseLabel(segment: Segment): String {
        return when (segment) {
            Segment.INTRO -> getString(R.string.segment_intro)
            Segment.MAIN -> getString(R.string.segment_main)
            Segment.OUTRO -> getString(R.string.segment_outro)
            Segment.DONE -> getString(R.string.timer_done)
        }
    }

    private fun getPhaseShortLabel(segment: Segment): String {
        return when (segment) {
            Segment.INTRO -> getString(R.string.segment_intro_short)
            Segment.MAIN -> getString(R.string.segment_main_short)
            Segment.OUTRO -> getString(R.string.segment_outro_short)
            Segment.DONE -> getString(R.string.timer_done)
        }
    }

    private fun markExactAlarmAccessMissing() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
            return
        }
        if (!exactAlarmAccessMissing) {
            exactAlarmAccessMissing = true
            lastKnownState?.let { updateNotification(it) }
        }
    }

    private fun clearExactAlarmAccessWarning() {
        if (exactAlarmAccessMissing) {
            exactAlarmAccessMissing = false
            lastKnownState?.let { updateNotification(it) }
        }
    }

    private fun createPauseAction() = NotificationCompat.Action.Builder(
        0, getString(R.string.action_pause),
        PendingIntent.getService(
            this,
            1,
            Intent(this, TimerService::class.java).apply { action = ACTION_PAUSE },
            PendingIntent.FLAG_IMMUTABLE
        )
    ).build()

    private fun createResumeAction() = NotificationCompat.Action.Builder(
        0, getString(R.string.action_resume),
        PendingIntent.getService(
            this,
            2,
            Intent(this, TimerService::class.java).apply { action = ACTION_RESUME },
            PendingIntent.FLAG_IMMUTABLE
        )
    ).build()

    private fun createSkipAction() = NotificationCompat.Action.Builder(
        0, getString(R.string.action_skip),
        PendingIntent.getService(
            this,
            3,
            Intent(this, TimerService::class.java).apply { action = ACTION_SKIP },
            PendingIntent.FLAG_IMMUTABLE
        )
    ).build()

    private fun createStopAction() = NotificationCompat.Action.Builder(
        0, getString(R.string.action_stop),
        PendingIntent.getService(
            this,
            4,
            Intent(this, TimerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
    ).build()

    private fun createActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // Include current preset ID for preset-aware navigation if engine is ready
            if (::engine.isInitialized) {
                putExtra("preset_id", engine.state.value.activePreset?.id)
            }
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Timer service notifications"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun handleBoundaryReached(event: TimerEvent.BoundaryReached) {
        android.util.Log.d("TIMER", "EVENT: BoundaryReached - nextSegment=${event.nextSegment}")
        // Stop any ongoing countdown vibration before playing boundary pattern
        hapticPatterns.stopCountdownVibration()
        resetCountdownScheduling()
        // Play haptic pattern for segment boundary according to AGENTS.md §10
        hapticPatterns.playBoundaryPattern(event.nextSegment)
        notifySurfacesOfStateChange()
    }

    private fun handleTimerCompleted() {
        android.util.Log.d("TIMER", "EVENT: TimerCompleted")
        hapticPatterns.stopCountdownVibration()
        resetCountdownScheduling()
        hapticPatterns.playCompletionPattern()
        notifySurfacesOfStateChange()
    }

    private fun handleTimerPaused() {
        android.util.Log.d("TIMER", "EVENT: TimerPaused")
        hapticPatterns.stopCountdownVibration()
        resetCountdownScheduling()
        hapticPatterns.playLightTick()
        notifySurfacesOfStateChange()
    }

    private fun handleTimerResumed() {
        android.util.Log.d("TIMER", "EVENT: TimerResumed")
        hapticPatterns.playLightTick()
        notifySurfacesOfStateChange()
    }

    private fun handleTimerStopped() {
        android.util.Log.d("TIMER", "EVENT: TimerStopped")
        hapticPatterns.stopCountdownVibration()
        resetCountdownScheduling()
        hapticPatterns.playLightTick()
        notifySurfacesOfStateChange()
    }

    private fun handlePrerollStarted() {
        android.util.Log.d("TIMER", "EVENT: PrerollStarted")
        hapticPatterns.playStartPattern()
        notifySurfacesOfStateChange()
    }

    private fun handlePrerollEnded() {
        android.util.Log.d("TIMER", "EVENT: PrerollEnded")
        hapticPatterns.playPrerollEndedPattern()
        notifySurfacesOfStateChange()
    }

    private fun handleOvertimeStarted() {
        android.util.Log.d("TIMER", "EVENT: OvertimeStarted")
        hapticPatterns.stopCountdownVibration()
        resetCountdownScheduling()
        hapticPatterns.playOvertimeStartedPattern()
        notifySurfacesOfStateChange()
    }

    private fun notifySurfacesOfStateChange() {
        try {
            tileUpdateRequester.requestUpdate(SermonTileService::class.java)
        } catch (_: Exception) {
        }
        TimerComplicationService.requestUpdate(this)
    }
}
