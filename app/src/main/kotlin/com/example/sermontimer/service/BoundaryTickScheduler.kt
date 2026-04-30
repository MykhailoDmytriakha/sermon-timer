package com.example.sermontimer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log

/**
 * Schedules an exact alarm AT each segment boundary so the engine reliably advances
 * state even if the in-process 1 s tick coroutine has been frozen by the platform
 * (Samsung Freecess, Doze, low memory).
 *
 * Why this exists in addition to [CountdownAlarmScheduler]:
 *  - CountdownAlarmScheduler fires at boundary − 10 s to start the haptic countdown.
 *    It does NOT advance engine state.
 *  - On a long sermon (1–2 h) the in-process tick coroutine may pause when the watch
 *    sits on the wrist with the screen off; without an external trigger the engine
 *    sees no Tick commands and the chip's chronometer (which lives in sysui's
 *    process) keeps ticking past zero into negative territory while our segment
 *    stays at INTRO.
 *  - This scheduler fires a [PendingIntent] at boundary + 1 s using
 *    `setExactAndAllowWhileIdle` via [BoundaryTickReceiver]. The receiver
 *    inherits the alarm-fire FGS-from-background exemption (only granted to
 *    BroadcastReceiver-mediated alarm triggers, NOT to direct
 *    `PendingIntent.getService`) and re-promotes [TimerService] to FGS, then
 *    submits a Tick(now) command which forces the reducer to emit
 *    `BoundaryReached`.
 */
class BoundaryTickScheduler(
    private val context: Context,
    private val onTrigger: (Long) -> Unit,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private var pendingIntent: PendingIntent? = null
    private var scheduledBoundaryAtMs: Long? = null

    fun schedule(boundaryAtElapsedMs: Long) {
        val now = SystemClock.elapsedRealtime()
        // Fire 1 s after the boundary so we never race the reducer's own boundary detection
        // when ticks happen to land exactly at the moment.
        val triggerAtMs = boundaryAtElapsedMs + BOUNDARY_GRACE_MS

        if (scheduledBoundaryAtMs == boundaryAtElapsedMs) {
            return
        }
        cancel()

        if (triggerAtMs <= now) {
            // We're already past the boundary — fire synchronously.
            onTrigger(boundaryAtElapsedMs)
            return
        }

        val intent = Intent(context, BoundaryTickReceiver::class.java).apply {
            putExtra(BoundaryTickReceiver.EXTRA_BOUNDARY_AT_MS, boundaryAtElapsedMs)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        pendingIntent = pi

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                // No exact alarm permission — degrade to setAndAllowWhileIdle (windowed,
                // ±2 min in deep doze). Still better than nothing for advancing the engine.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMs,
                    pi,
                )
                Log.d("TIMER", "BOUNDARY_TICK: scheduled inexact alarm for $boundaryAtElapsedMs (trigger=$triggerAtMs)")
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerAtMs,
                    pi,
                )
                Log.d("TIMER", "BOUNDARY_TICK: scheduled exact alarm for $boundaryAtElapsedMs (trigger=$triggerAtMs)")
            }
            scheduledBoundaryAtMs = boundaryAtElapsedMs
        } catch (security: SecurityException) {
            Log.w("TIMER", "BOUNDARY_TICK: SecurityException scheduling alarm", security)
            pendingIntent = null
            scheduledBoundaryAtMs = null
        }
    }

    fun cancel() {
        pendingIntent?.let { alarmManager.cancel(it) }
        pendingIntent = null
        scheduledBoundaryAtMs = null
    }

    fun handlePendingIntentTrigger(boundaryAtElapsedMs: Long) {
        pendingIntent = null
        scheduledBoundaryAtMs = null
        onTrigger(boundaryAtElapsedMs)
    }

    companion object {
        // Different request code from CountdownAlarmScheduler so both alarms coexist.
        private const val REQUEST_CODE = 43
        private const val BOUNDARY_GRACE_MS = 1_000L
    }
}
