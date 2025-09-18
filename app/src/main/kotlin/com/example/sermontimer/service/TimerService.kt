package com.example.sermontimer.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.sermontimer.R
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.domain.engine.*
import com.example.sermontimer.domain.model.*
import com.example.sermontimer.domain.time.MonotonicTimeProvider
import com.example.sermontimer.presentation.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.seconds

class TimerService : Service() {

    private lateinit var engine: CoroutineTimerEngine
    private lateinit var reducer: TimerStateReducer
    private lateinit var timeProvider: MonotonicTimeProvider
    private lateinit var dataRepository: com.example.sermontimer.data.TimerDataRepository
    private lateinit var serviceScope: CoroutineScope

    private var timerJob: Job? = null
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

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

        // Try to recover state from DataStore
        serviceScope.launch {
            val lastState = dataRepository.lastTimerState.first()
            val initialState = lastState ?: TimerState.idle(SegmentDurations(0, 0, 0))
            engine = CoroutineTimerEngine(reducer, serviceScope, initialState)

            // Start observing state changes
            observeTimerState()
            observeTimerEvents()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val presetId = intent.getStringExtra(EXTRA_PRESET_ID)
                if (presetId != null) {
                    serviceScope.launch {
                        startTimerWithPreset(presetId)
                    }
                }
            }
            ACTION_PAUSE -> engine.submit(TimerCommand.Pause(timeProvider.elapsedRealtimeMillis()))
            ACTION_RESUME -> engine.submit(TimerCommand.Resume(timeProvider.elapsedRealtimeMillis()))
            ACTION_SKIP -> engine.submit(TimerCommand.SkipSegment(timeProvider.elapsedRealtimeMillis()))
            ACTION_STOP -> stopTimer()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopForeground(true)
    }

    private suspend fun startTimerWithPreset(presetId: String) {
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

                // Start/stop timer job based on state
                if (state.isActive && timerJob?.isActive != true) {
                    startTimerJob()
                } else if (!state.isActive) {
                    timerJob?.cancel()
                }

                // Stop service if timer is idle
                if (state.status == RunStatus.IDLE) {
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
        engine.submit(TimerCommand.Stop)
    }

    private suspend fun saveStateToDataStore(state: TimerState) {
        dataRepository.saveTimerState(state)
    }

    private fun updateNotification(state: TimerState) {
        val notification = createNotification(state)
        if (state.status == RunStatus.RUNNING || state.status == RunStatus.PAUSED) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
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
        return when (state.segment) {
            Segment.INTRO -> getString(R.string.segment_intro)
            Segment.MAIN -> getString(R.string.segment_main)
            Segment.OUTRO -> getString(R.string.segment_outro)
            Segment.DONE -> getString(R.string.timer_done)
        }
    }

    private fun buildNotificationText(state: TimerState): String {
        return when {
            state.status == RunStatus.DONE -> getString(R.string.timer_completed)
            else -> {
                val minutes = state.remainingInSegmentSec / 60
                val seconds = state.remainingInSegmentSec % 60
                getString(R.string.remaining_time, minutes, seconds)
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
        this, 0, Intent(this, MainActivity::class.java),
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
        // TODO: Add haptics here
    }

    private fun handleTimerCompleted() {
        // TODO: Add final haptics here
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