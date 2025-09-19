package com.example.sermontimer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.wear.tiles.TileUpdateRequester
import com.example.sermontimer.R
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.domain.engine.*
import com.example.sermontimer.domain.model.*
import com.example.sermontimer.domain.time.MonotonicTimeProvider
import com.example.sermontimer.presentation.MainActivity
import com.example.sermontimer.tile.SermonTileService
import com.example.sermontimer.util.HapticPatterns
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class TimerService : Service() {

    private lateinit var engine: CoroutineTimerEngine
    private lateinit var reducer: TimerStateReducer
    private lateinit var timeProvider: MonotonicTimeProvider
    private lateinit var dataRepository: com.example.sermontimer.data.TimerDataRepository
    private lateinit var serviceScope: CoroutineScope
    private lateinit var hapticPatterns: HapticPatterns
    private lateinit var tileUpdateRequester: TileUpdateRequester

    private var timerJob: Job? = null
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    // Guard for engine initialization race condition
    private var engineReady = false
    private val pendingCommands = mutableListOf<Pair<TimerCommand, (() -> Unit)?>>()
    private var observedNonIdleState = false

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "timer"
        private const val CHANNEL_NAME = "Timer Service"

        // Intent actions
        const val ACTION_START = "com.example.sermontimer.START"
        const val ACTION_PAUSE = "com.example.sermontimer.PAUSE"
        const val ACTION_RESUME = "com.example.sermontimer.RESUME"
        const val ACTION_SKIP = "com.example.sermontimer.SKIP"
        const val ACTION_STOP = "com.example.sermontimer.STOP"

        // Intent extras
        const val EXTRA_PRESET_ID = "preset_id"

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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        dataRepository = TimerDataProvider.getRepository()
        timeProvider = MonotonicTimeProvider { android.os.SystemClock.elapsedRealtime() }
        reducer = DefaultTimerStateReducer()
        hapticPatterns = HapticPatterns(this)
        tileUpdateRequester = TileUpdateRequester { clazz ->
            if (clazz == SermonTileService::class.java) {
                // Tile update request
            }
        }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

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
            null -> {
                // Service restarted by system, try to restore state
                serviceScope.launch {
                    // Wait for engine to be ready
                    while (!::engine.isInitialized || !engineReady) {
                        kotlinx.coroutines.delay(10)
                    }

                    val lastState = dataRepository.lastTimerState.first()
                    if (lastState != null && (lastState.status == RunStatus.RUNNING || lastState.status == RunStatus.PAUSED)) {
                        // Find the preset by ID and restore the state
                        val preset = dataRepository.presets.first().find { it.id == lastState.activePreset?.id }
                        if (preset != null) {
                            engine.submit(TimerCommand.Start(preset, timeProvider.elapsedRealtimeMillis()))
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
            kotlinx.coroutines.delay(10)
        }

        val preset = dataRepository.presets.first().find { it.id == presetId }
        if (preset != null) {
            val startCommand = TimerCommand.Start(preset, timeProvider.elapsedRealtimeMillis())
            engine.submit(startCommand)
        }
    }

    private fun observeTimerState() {
        serviceScope.launch {
            engine.state.collect { state ->
                updateNotification(state)
                saveStateToDataStore(state)
                try {
                    tileUpdateRequester.requestUpdate(SermonTileService::class.java)
                } catch (e: Exception) {
                    // Tile update failed, but don't crash the service
                    android.util.Log.w("TIMER", "Tile update failed", e)
                }

                // Start/stop timer job based on state
                if (state.isActive && timerJob?.isActive != true) {
                    startTimerJob()
                } else if (!state.isActive) {
                    timerJob?.cancel()
                }

                if (state.status != RunStatus.IDLE) {
                    observedNonIdleState = true
                }

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
                    else -> {} // Ignore other events
                }
            }
        }
    }

    private fun startTimerJob() {
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1.seconds)
                engine.submit(TimerCommand.Tick(timeProvider.elapsedRealtimeMillis()))
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

    private fun createNotification(state: TimerState): Notification {
        val title = buildNotificationTitle(state)
        val text = buildNotificationText(state)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(state.isActive)
            .setContentIntent(createActivityIntent())

        // Add action buttons
        addNotificationActions(builder, state)

        return builder.build()
    }

    private fun buildNotificationTitle(state: TimerState): String {
        val presetName = state.activePreset?.id ?: getString(R.string.app_name)
        val phaseText = when (state.segment) {
            Segment.INTRO -> getString(R.string.segment_intro_short)
            Segment.MAIN -> getString(R.string.segment_main_short)
            Segment.OUTRO -> getString(R.string.segment_outro_short)
            Segment.DONE -> getString(R.string.timer_done)
        }
        return "$presetName • $phaseText"
    }

    private fun buildNotificationText(state: TimerState): String {
        return when {
            state.status == RunStatus.DONE -> {
                val totalMinutes = state.totalSec / 60
                val totalSeconds = state.totalSec % 60
                getString(R.string.timer_completed_with_total, totalMinutes, totalSeconds)
            }
            else -> {
                val remainingMinutes = state.remainingInSegmentSec / 60
                val remainingSeconds = state.remainingInSegmentSec % 60
                val progressPercent = ((state.elapsedTotalSec.toFloat() / state.totalSec.toFloat()) * 100).toInt()
                getString(R.string.remaining_time_with_progress, remainingMinutes, remainingSeconds, progressPercent)
            }
        }
    }

    private fun addNotificationActions(builder: NotificationCompat.Builder, state: TimerState) {
        when (state.status) {
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
            else -> {} // No actions for idle/done states
        }
    }

    private fun createPauseAction() = NotificationCompat.Action.Builder(
        0, getString(R.string.action_pause),
        PendingIntent.getService(this, 1, Intent(this, TimerService::class.java).apply { action = ACTION_PAUSE }, PendingIntent.FLAG_IMMUTABLE)
    ).build()

    private fun createResumeAction() = NotificationCompat.Action.Builder(
        0, getString(R.string.action_resume),
        PendingIntent.getService(this, 2, Intent(this, TimerService::class.java).apply { action = ACTION_RESUME }, PendingIntent.FLAG_IMMUTABLE)
    ).build()

    private fun createSkipAction() = NotificationCompat.Action.Builder(
        0, getString(R.string.action_skip),
        PendingIntent.getService(this, 3, Intent(this, TimerService::class.java).apply { action = ACTION_SKIP }, PendingIntent.FLAG_IMMUTABLE)
    ).build()

    private fun createStopAction() = NotificationCompat.Action.Builder(
        0, getString(R.string.action_stop),
        PendingIntent.getService(this, 4, Intent(this, TimerService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_IMMUTABLE)
    ).build()

    private fun createActivityIntent() = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).apply {
            // Include current preset ID for preset-aware navigation if engine is ready
            if (::engine.isInitialized) {
                putExtra("preset_id", engine.state.value.activePreset?.id)
            }
        },
        PendingIntent.FLAG_IMMUTABLE
    )

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
        // Play haptic pattern for segment boundary according to AGENTS.md §10
        hapticPatterns.playBoundaryPattern(event.nextSegment)
    }

    private fun handleTimerCompleted() {
        // Play completion haptic pattern according to AGENTS.md §10
        hapticPatterns.playCompletionPattern()
    }

    private fun handleTimerPaused() {
        // TODO: Add pause feedback if needed
    }

    private fun handleTimerResumed() {
        // TODO: Add resume feedback if needed
    }

    private fun handleTimerStopped() {
        // TODO: Add stop feedback if needed
    }
}
