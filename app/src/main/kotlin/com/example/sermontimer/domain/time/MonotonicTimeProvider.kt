package com.example.sermontimer.domain.time

/** Provider of elapsed real-time in milliseconds (SystemClock.elapsedRealtime). */
fun interface MonotonicTimeProvider {
    fun elapsedRealtimeMillis(): Long
}

