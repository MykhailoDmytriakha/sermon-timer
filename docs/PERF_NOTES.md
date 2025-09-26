# Performance & Reliability Notes (2025‑09‑26)

Changes focused on battery, responsiveness, and policy compliance.

- Removed manual wakelock usage from countdown haptics. ForegroundService + `VibrationAttributes.USAGE_ALARM` suffice; less battery impact and aligns with Doze rules.
- Throttled Tile updates: request refresh only on boundary/pause/resume/stop/start, not every tick.
- Quieted hot‑path logs (1s tick) behind `Log.isLoggable()`.
- Enabled R8 + resource shrinking for release.
- Use `ServiceCompat.startForeground(...)` and declare `specialUse` FGS subtype property for Android 14.

Key references

1) Foreground services types & specialUse (Android 14 / Wear OS 5):
   - developer.android.com → Foreground services and types; Special use and property declaration.
2) Exact alarms: permissions and `setExact` fallback:
   - developer.android.com → Exact alarms on Android 13+; using `OnAlarmListener` `setExact()` without `SCHEDULE_EXACT_ALARM`.
3) Tiles best practices (performance):
   - developer.android.com → Show dynamic updates in tiles; use Dynamic Expressions/Time and avoid frequent `requestUpdate`.
4) Ongoing Activity on Wear + POST_NOTIFICATIONS:
   - developer.android.com → Ongoing Activity guidance; permission requirement on Wear OS 4+.
5) Compose performance tips:
   - developer.android.com → Jetpack Compose performance best practices.

See AGENTS.md §11 and §13 for behavior clarifications.

