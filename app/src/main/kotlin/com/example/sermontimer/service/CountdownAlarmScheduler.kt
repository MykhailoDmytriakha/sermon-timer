package com.example.sermontimer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Schedules countdown alarms anchored to segment boundaries.
 * Prefers `setExactAndAllowWhileIdle` with a PendingIntent when permitted, falling back to
 * the listener-based `setExact` if exact-alarm access is unavailable.
 */
class CountdownAlarmScheduler(
    private val context: Context,
    private val onTrigger: (Long) -> Unit,
    private val onExactAlarmAccessMissing: (() -> Unit)? = null,
    private val onExactAlarmAccessRestored: (() -> Unit)? = null,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var listener: AlarmManager.OnAlarmListener? = null
    private var pendingIntent: PendingIntent? = null
    private var scheduledBoundaryAtMs: Long? = null

    fun schedule(triggerAtElapsedMs: Long, boundaryAtElapsedMs: Long) {
        cancel()
        val now = SystemClock.elapsedRealtime()
        if (triggerAtElapsedMs <= now) {
            onTrigger(boundaryAtElapsedMs)
            return
        }

        if (attemptExactWhileIdle(triggerAtElapsedMs, boundaryAtElapsedMs)) {
            scheduledBoundaryAtMs = boundaryAtElapsedMs
            return
        }

        val alarmListener = AlarmManager.OnAlarmListener {
            listener = null
            scheduledBoundaryAtMs = null
            onTrigger(boundaryAtElapsedMs)
        }
        listener = alarmListener
        alarmManager.setExact(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            triggerAtElapsedMs,
            "sermon-timer:countdown",
            alarmListener,
            handler,
        )
        Log.d(
            "TIMER",
            "COUNTDOWN: scheduled fallback setExact alarm (trigger=$triggerAtElapsedMs, boundary=$boundaryAtElapsedMs)",
        )
        onExactAlarmAccessMissing?.invoke()
        scheduledBoundaryAtMs = boundaryAtElapsedMs
    }

    fun cancel() {
        listener?.let { alarmManager.cancel(it) }
        pendingIntent?.let { alarmManager.cancel(it) }
        listener = null
        pendingIntent = null
        scheduledBoundaryAtMs = null
    }

    fun handlePendingIntentTrigger(boundaryAtElapsedMs: Long) {
        pendingIntent = null
        scheduledBoundaryAtMs = null
        onTrigger(boundaryAtElapsedMs)
    }

    private fun attemptExactWhileIdle(triggerAtElapsedMs: Long, boundaryAtElapsedMs: Long): Boolean {
        if (!shouldUsePendingIntent()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w("TIMER", "COUNTDOWN: exact alarm permission not granted; falling back to setExact")
            onExactAlarmAccessMissing?.invoke()
            return false
        }

        val intent = TimerService.createCountdownIntent(context, boundaryAtElapsedMs)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getService(context, REQUEST_CODE, intent, flags)
        pendingIntent = pi
        return try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtElapsedMs,
                pi,
            )
            Log.d(
                "TIMER",
                "COUNTDOWN: scheduled exact allowWhileIdle alarm (trigger=$triggerAtElapsedMs, boundary=$boundaryAtElapsedMs)",
            )
            onExactAlarmAccessRestored?.invoke()
            true
        } catch (security: SecurityException) {
            Log.w("TIMER", "COUNTDOWN: SecurityException scheduling exact alarm; falling back", security)
            pendingIntent = null
            onExactAlarmAccessMissing?.invoke()
            false
        }
    }

    private fun shouldUsePendingIntent(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    companion object {
        private const val REQUEST_CODE = 42
    }
}
