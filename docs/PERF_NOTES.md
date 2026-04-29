# Performance & Reliability Notes

## 2026-04-29 — v1.22 — long sermon support (WAKE_LOCK reintroduced)

- **Re-introduced PARTIAL_WAKE_LOCK for the duration of an active session** (PREROLL / RUNNING / OVERTIME). FGS exempts us from Doze background limits but does NOT keep the CPU from suspending between scheduled events. For 1-2 h sermons that meant the 1 s tick coroutine and the Now bar Chronometer would lag visibly when the watch sat on the wrist with the screen off.
  - Tag: `SermonTimer:active-session`
  - Acquired in `TimerService.observeTimerState` when `state.isActive == true`, released the moment the engine returns to IDLE / DONE. `onDestroy` is the belt-and-braces release path if the service is killed mid-session.
  - Idempotent: subsequent acquires are no-ops while the lock is already held; `setReferenceCounted(false)` keeps it that way.
  - Battery cost: marginal compared to the foreground notification + active vibrator + accelerometer/gyro typical during a sermon.
- **Removed `POST_PROMOTED_NOTIFICATIONS` permission** and `com.samsung.android.support.ongoing_activity` manifest meta-data — both were artefacts of the v1.20 Live Updates / OngoingActivity attempts and are no-ops on the new `customDisplayBundle` rendering path.

## 2025-09-26 — initial perf pass

Changes focused on battery, responsiveness, and policy compliance.

- Removed manual wakelock usage from countdown haptics. ForegroundService + `VibrationAttributes.USAGE_ALARM` suffice for the *haptic* path; less battery impact and aligns with Doze rules.
- Throttled Tile updates: request refresh only on boundary/pause/resume/stop/start, not every tick.
- Quieted hot-path logs (1 s tick) behind `Log.isLoggable()`.
- Enabled R8 + resource shrinking for release.
- Use `ServiceCompat.startForeground(...)` and declare `specialUse` FGS subtype property for Android 14.

Note: the September 2025 wakelock removal was specifically for the **countdown haptic** (the brief T-10 vibration burst). The April 2026 reintroduction is a **session-level** wakelock that brackets the entire active timer state, addressing a different problem (1-2 h sermon CPU drift). Both changes can coexist.

## Key references

1) Foreground services types & specialUse (Android 14 / Wear OS 5):
   - developer.android.com → Foreground services and types; Special use and property declaration.
2) Exact alarms: permissions and `setExact` fallback:
   - developer.android.com → Exact alarms on Android 13+; using `OnAlarmListener` `setExact()` without `SCHEDULE_EXACT_ALARM`.
3) Tiles best practices (performance):
   - developer.android.com → Show dynamic updates in tiles; use Dynamic Expressions/Time and avoid frequent `requestUpdate`.
4) Wake locks on Wear OS:
   - developer.android.com → `PowerManager.WakeLock`, partial wake locks, and battery considerations on Wear.
5) Compose performance tips:
   - developer.android.com → Jetpack Compose performance best practices.

See AGENTS.md §11 and §13 for behavior clarifications.
