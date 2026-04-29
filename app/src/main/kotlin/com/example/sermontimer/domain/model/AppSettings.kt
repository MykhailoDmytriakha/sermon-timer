package com.example.sermontimer.domain.model

import kotlinx.serialization.Serializable

/**
 * Global timer behaviour settings, applied to every preset. Stored in DataStore and surfaced
 * to the user through the in-app settings screen.
 *
 * @property prerollSec Length of preparation countdown before the timer actually starts.
 *                     0 disables preroll. Defaults to 60s — gives the preacher a minute to
 *                     walk to the podium after pressing Start.
 * @property overtimeMaxSec Maximum time the timer keeps counting upward after the configured
 *                         total has elapsed. 0 disables overtime. Defaults to 300s (5 min).
 */
@Serializable
data class AppSettings(
    val prerollSec: Int = DEFAULT_PREROLL_SEC,
    val overtimeMaxSec: Int = DEFAULT_OVERTIME_MAX_SEC,
) {
    init {
        require(prerollSec in 0..MAX_PREROLL_SEC) { "prerollSec out of range: $prerollSec" }
        require(overtimeMaxSec in 0..MAX_OVERTIME_SEC) { "overtimeMaxSec out of range: $overtimeMaxSec" }
    }

    companion object {
        const val DEFAULT_PREROLL_SEC = 60
        const val DEFAULT_OVERTIME_MAX_SEC = 300
        const val MAX_PREROLL_SEC = 600    // 10 minutes
        const val MAX_OVERTIME_SEC = 1800  // 30 minutes
    }
}
