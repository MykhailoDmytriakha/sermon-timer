package com.example.sermontimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Alarm-trigger broadcast receiver that re-foregrounds the [TimerService] at a
 * segment boundary.
 *
 * Why a Receiver instead of a direct `PendingIntent.getService`:
 * Android 12+ FGS-from-background restrictions allow an exact alarm
 * (`setExactAndAllowWhileIdle` / `setExact`, < 24 h) to start a foreground
 * service ONLY when the alarm trigger is delivered via a [BroadcastReceiver].
 * Direct `getService` PendingIntents do NOT inherit the alarm-fire exemption —
 * `Service.startForeground()` from `onStartCommand` throws
 * `ForegroundServiceStartNotAllowedException` and the chip vanishes from
 * Galaxy Watch's Now bar after Samsung Freecess kills the previous FGS.
 *
 * Stock Samsung Timer (`com.samsung.android.watch.timer`) uses exactly this
 * pattern — its `NotificationInternalReceiver` is a BroadcastReceiver that
 * starts `TimerService` in response to alarm fires.
 *
 * Verified by reading Android 12+ FGS docs:
 *   developer.android.com/about/versions/12/foreground-services#exemptions
 */
class BoundaryTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val boundaryAtMs = intent.getLongExtra(EXTRA_BOUNDARY_AT_MS, -1L)
        Log.d(
            "TIMER",
            "BOUNDARY_TICK receiver fired (boundary=$boundaryAtMs); promoting service to FGS",
        )
        val serviceIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_BOUNDARY_TICK
            putExtra(EXTRA_BOUNDARY_AT_MS, boundaryAtMs)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w("TIMER", "BOUNDARY_TICK receiver: startForegroundService threw", e)
        }
    }

    companion object {
        // Same key TimerService uses on its own service intents — keeping it
        // identical lets the service's existing handler logic stay unchanged.
        const val EXTRA_BOUNDARY_AT_MS = "countdown_boundary_at"
    }
}
