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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Bundle
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
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
        private const val COUNTDOWN_SECONDS = 10
        private const val COUNTDOWN_WINDOW_MS = COUNTDOWN_SECONDS * 1000L
        private const val COUNTDOWN_GRACE_MS = 500L
        private const val REQUEST_EXACT_ALARM_SETTINGS = 1001

        // Px size of the runtime-generated chip icon.
        private const val ICON_BITMAP_PX = 96

        // Phase accent colours. Used for the chip icon and the Now bar gradient.
        private const val ACCENT_PREROLL = 0xFFFFB300.toInt()   // amber
        private const val ACCENT_INTRO = 0xFF66BB6A.toInt()     // green
        private const val ACCENT_MAIN = 0xFF42A5F5.toInt()      // blue
        private const val ACCENT_OUTRO = 0xFFFFA726.toInt()     // orange
        private const val ACCENT_OVERTIME = 0xFFFF5252.toInt()  // red
        private const val ACCENT_DONE = 0xFFCE93D8.toInt()      // purple

        // Galaxy Watch One UI 8 Now bar extras — reverse-engineered byte-for-byte from the
        // stock Samsung Timer (TimerWatch.apk). Stock app does NOT use OngoingActivity /
        // Status.TimerPart / NotificationCompat.ProgressStyle / setRequestPromotedOngoing —
        // it ships only `customDisplayBundle.nowBarData` plus `forceAutoResume` /
        // `ambientImmediateExpire` on the outer extras. Adding ANY androidx.core.ongoing.*
        // extras (i.e. OngoingActivity.Builder) routes the notification through legacy
        // OANowBarController which ignores cardColorStart/End → result: grey chip.
        private const val NOWBAR_KEY_CUSTOM_DISPLAY_BUNDLE = "customDisplayBundle"
        private const val NOWBAR_KEY_ENABLE = "enableNowBar"
        private const val NOWBAR_KEY_DATA = "nowBarData"
        private const val NOWBAR_KEY_TYPE = "type"
        private const val NOWBAR_KEY_CARD_ICON_LEFT = "cardIconLeft"
        private const val NOWBAR_KEY_QUE_ICON = "queIcon"
        private const val NOWBAR_KEY_CARD_CONTENTS = "cardContents"
        private const val NOWBAR_KEY_CARD_CHRONO_RV = "cardChronometerRemoteView"
        private const val NOWBAR_KEY_EXPAND_VIEW_ICON = "expandViewIcon"
        private const val NOWBAR_KEY_EXPAND_CHRONO_RV = "expandChronometerRemoteView"
        private const val NOWBAR_KEY_EXPAND_CHRONO_POS = "expandChronometerPosition"
        private const val NOWBAR_KEY_CARD_COLOR_START = "cardColorStart"
        private const val NOWBAR_KEY_CARD_COLOR_END = "cardColorEnd"
        private const val NOWBAR_KEY_EXPAND_VIEW_COLOR_START = "expandViewColorStart"
        private const val NOWBAR_KEY_EXPAND_VIEW_COLOR_END = "expandViewColorEnd"
        private const val NOWBAR_KEY_FORCE_AUTO_RESUME = "forceAutoResume"
        private const val NOWBAR_KEY_AMBIENT_EXPIRE = "ambientImmediateExpire"
        private const val NOWBAR_TYPE_STANDARD = 1

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
                // The Now bar Chronometer view ticks itself once placed in sysui's
                // host window — we only need to republish on phase / status / preset
                // transitions. See shouldRepublishNotification for the full predicate.
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
        // notify() updates the existing foreground-service notification in place. We avoid
        // notificationManager.cancel(NOTIFICATION_ID) here even though the stock Samsung
        // Timer does it: on Android 14+ cancelling the FGS notification tears down the
        // foreground state entirely (the next notify() comes in as a regular notification,
        // and Wear sysui drops it from the Now bar surface).
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Returns true if the new state warrants a notification republish. We rebuild the chip
     * only on state transitions (status / segment / preroll-or-overtime base / active preset)
     * — never per-tick. The Chronometer inside our nowBarData RemoteView ticks itself, and
     * over-issuing notify() forces a full sysui re-parse on every second.
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

    /**
     * Builds the foreground-service notification in the exact shape the stock Samsung
     * Timer (`com.samsung.android.watch.timer` / TimerWatch.apk) ships. The shape is
     * what determines whether Watch sysui routes the notification through the rich
     * `ConvertingNowBarData` path (gradient background honoured) or the legacy
     * `OANowBarController` path (icon-only, grey gradient).
     *
     * Stock shape — verified by `dumpsys notification --noredact` and dexdump:
     *   - title/text/subText all null
     *   - no `androidx.core.ongoing.*` extras (NO OngoingActivity.Builder)
     *   - no MessagingStyle, ProgressStyle, chronometer, setRequestPromotedOngoing
     *   - category = alarm, foreground service, ongoing, no-clear
     *   - extras = customDisplayBundle{ enableNowBar=true, nowBarData{...} }
     *              + forceAutoResume=true + ambientImmediateExpire=true
     */
    private fun createNotification(state: TimerState): Notification {
        val isOngoing = shouldDisplayOngoingActivity(state)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_ongoing)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(isOngoing)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setShowWhen(false)
            .setContentIntent(createActivityIntent())

        applySamsungNowBarExtras(builder, state)
        addNotificationActions(builder, state)
        maybeAddExactAlarmHint(builder)

        return builder.build()
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

    private fun phaseColouredIcon(state: TimerState): Icon {
        return buildPhaseIconBitmap(phaseAccent(state))
    }

    @ColorInt
    private fun phaseAccent(state: TimerState): Int = when (state.status) {
        RunStatus.PREROLL -> ACCENT_PREROLL
        RunStatus.OVERTIME -> ACCENT_OVERTIME
        RunStatus.RUNNING, RunStatus.PAUSED -> when (state.segment) {
            Segment.INTRO -> ACCENT_INTRO
            Segment.MAIN -> ACCENT_MAIN
            Segment.OUTRO -> ACCENT_OUTRO
            Segment.DONE -> ACCENT_DONE
        }
        // Idle / Done — fall back to the brand amber so the chip has a colour even before
        // the engine assigns a phase.
        else -> ACCENT_PREROLL
    }

    /**
     * Galaxy Watch One UI 8 Now bar extras — byte-for-byte mirror of stock Samsung Timer
     * (TimerWatch.apk). See companion-object NOWBAR_KEY_* docs for routing background.
     *
     * Critical pieces (verified by dexdump of the stock APK):
     *   1. `cardChronometerRemoteView` / `expandChronometerRemoteView` — RemoteViews
     *      with android.widget.Chronometer. Without this, sysui silently treats the
     *      notification as non-rich and drops the gradient.
     *   2. `expandViewColorStart` / `expandViewColorEnd` — note the `View` infix; we
     *      previously shipped `expandColorStart/End` and they were ignored.
     *   3. `queIcon` mirrors `cardIconLeft`. Sysui pulls one or the other depending on
     *      surface; we provide both.
     *   4. `forceAutoResume` / `ambientImmediateExpire` live on the OUTER extras Bundle,
     *      siblings of `customDisplayBundle` — not nested.
     */
    private fun applySamsungNowBarExtras(builder: NotificationCompat.Builder, state: TimerState) {
        if (!shouldDisplayOngoingActivity(state)) return
        val accent = phaseAccent(state)
        val accentEnd = darkenForGradient(accent)
        val phaseIcon = phaseColouredIcon(state)
        val cardContents = buildOngoingShortText(state)
        val cardChrono = buildChronometerRemoteView(state)
        val expandChrono = buildChronometerRemoteView(state)

        val nowBarData = Bundle().apply {
            putInt(NOWBAR_KEY_TYPE, NOWBAR_TYPE_STANDARD)
            putParcelable(NOWBAR_KEY_CARD_ICON_LEFT, phaseIcon)
            putParcelable(NOWBAR_KEY_QUE_ICON, phaseIcon)
            putString(NOWBAR_KEY_CARD_CONTENTS, cardContents)
            putParcelable(NOWBAR_KEY_CARD_CHRONO_RV, cardChrono)
            putParcelable(NOWBAR_KEY_EXPAND_VIEW_ICON, phaseIcon)
            putParcelable(NOWBAR_KEY_EXPAND_CHRONO_RV, expandChrono)
            putInt(NOWBAR_KEY_EXPAND_CHRONO_POS, 1)
            putInt(NOWBAR_KEY_CARD_COLOR_START, accent)
            putInt(NOWBAR_KEY_CARD_COLOR_END, accentEnd)
            putInt(NOWBAR_KEY_EXPAND_VIEW_COLOR_START, accent)
            putInt(NOWBAR_KEY_EXPAND_VIEW_COLOR_END, accentEnd)
        }
        val customDisplay = Bundle().apply {
            putBoolean(NOWBAR_KEY_ENABLE, true)
            putBundle(NOWBAR_KEY_DATA, nowBarData)
        }
        builder.addExtras(Bundle().apply {
            putBundle(NOWBAR_KEY_CUSTOM_DISPLAY_BUNDLE, customDisplay)
            putBoolean(NOWBAR_KEY_FORCE_AUTO_RESUME, true)
            putBoolean(NOWBAR_KEY_AMBIENT_EXPIRE, true)
        })
    }

    /**
     * Build a RemoteViews wrapping a system Chronometer. The Chronometer ticks every
     * second on its own once placed in a host window — sysui hosts it inside the Now bar
     * card. `base` is in SystemClock.elapsedRealtime() units:
     *   - countdown: base = now + remainingMs → display goes from remainingMs down to 0
     *   - count up:  base = now - elapsedMs   → display goes from elapsedMs upward
     */
    private fun buildChronometerRemoteView(state: TimerState): RemoteViews {
        val rv = RemoteViews(packageName, R.layout.nowbar_chronometer)
        val now = SystemClock.elapsedRealtime()
        val (base, countDown) = when (state.status) {
            RunStatus.PREROLL -> (now + state.prerollRemainingSec * 1000L) to true
            RunStatus.RUNNING -> (now + state.remainingInSegmentSec * 1000L) to true
            RunStatus.OVERTIME -> (now - state.overtimeElapsedSec * 1000L) to false
            // Paused / Done / Idle — render frozen mm:ss by setting base in the past
            // with countDown=false then immediately not started; simplest path is to
            // still anchor a chronometer that won't visibly drift if shown briefly.
            else -> (now - 0L) to false
        }
        rv.setChronometer(R.id.nowbar_chronometer, base, null, true)
        rv.setChronometerCountDown(R.id.nowbar_chronometer, countDown)
        return rv
    }

    /**
     * Slightly darken the accent for the gradient end-stop. Stock Samsung Timer uses
     * different colour resources for start/end — we approximate with an HSV value drop
     * so we don't have to ship a full design palette.
     */
    @ColorInt
    private fun darkenForGradient(@ColorInt argb: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        hsv[2] = (hsv[2] * 0.78f).coerceIn(0f, 1f)
        return Color.HSVToColor(Color.alpha(argb), hsv)
    }

    /**
     * Short snapshot string written into `cardContents`. Used by sysui as the fallback
     * label when the Chronometer RemoteView can't be inflated for a given surface.
     */
    private fun buildOngoingShortText(state: TimerState): String {
        return when (state.status) {
            RunStatus.PREROLL -> DurationFormatter.formatTimerDisplay(state.prerollRemainingSec)
            RunStatus.OVERTIME -> "+" + DurationFormatter.formatTimerDisplay(state.overtimeElapsedSec)
            RunStatus.PAUSED, RunStatus.RUNNING ->
                DurationFormatter.formatTimerDisplay(state.remainingInSegmentSec)
            RunStatus.DONE -> getString(R.string.timer_done)
            RunStatus.IDLE -> ""
        }
    }

    private fun buildPhaseIconBitmap(@ColorInt accent: Int): Icon {
        val size = ICON_BITMAP_PX
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f

        Paint().apply {
            isAntiAlias = true
            color = accent
        }.also { canvas.drawCircle(cx, cy, cx, it) }

        val glyph = ContextCompat.getDrawable(this, R.drawable.ic_timer_ongoing)?.mutate()
        if (glyph != null) {
            DrawableCompat.setTint(glyph, Color.WHITE)
            val pad = (size * 0.18f).toInt()
            glyph.setBounds(pad, pad, size - pad, size - pad)
            glyph.draw(canvas)
        }
        return Icon.createWithBitmap(bmp)
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
            // IMPORTANCE_DEFAULT (3) is required for the Wear launcher / Samsung Now bar to
            // honour setColor + setColorized on the chip. With IMPORTANCE_LOW the chip renders
            // in a neutral system colour regardless of the phase accent. See Google's
            // android/codelab-ongoing-activity reference, which uses IMPORTANCE_DEFAULT for the
            // same reason. Sound is muted at the channel level so we keep the silent UX.
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Timer service notifications"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(true)
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
