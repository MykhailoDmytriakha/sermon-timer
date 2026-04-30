package com.example.sermontimer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Alarm-trigger broadcast receiver for the T-10 countdown haptic. Same
 * BroadcastReceiver-mediated FGS-exemption pattern as [BoundaryTickReceiver];
 * see that file for rationale and references.
 */
class CountdownAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val boundaryAtMs = intent.getLongExtra(BoundaryTickReceiver.EXTRA_BOUNDARY_AT_MS, -1L)
        Log.d(
            "TIMER",
            "COUNTDOWN_ALARM receiver fired (boundary=$boundaryAtMs); promoting service to FGS",
        )
        val serviceIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_COUNTDOWN_ALARM
            putExtra(BoundaryTickReceiver.EXTRA_BOUNDARY_AT_MS, boundaryAtMs)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w("TIMER", "COUNTDOWN_ALARM receiver: startForegroundService threw", e)
        }
    }
}
