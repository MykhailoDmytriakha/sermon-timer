# TODO

## Done
- [x] DataStore persistence for presets, default preset id, and last timer snapshot (`app/src/main/kotlin/com/example/sermontimer/data/DataStoreTimerRepository.kt`, `TimerDataProvider.kt`, `PresetInitializer.kt`).
- [x] Deterministic timer engine with reducer, commands, and coroutine wrapper (`domain/engine/*`).
- [x] Foreground service skeleton with notification actions and DataStore syncing (`app/src/main/kotlin/com/example/sermontimer/service/TimerService.kt`).
- [x] Compose UI scaffolds for preset list and timer plus application bootstrap (`presentation/*`, `ui/*`, `SermonTimerApplication.kt`).
- [x] Haptic patterns for segment boundaries and completion hooked up via `HapticPatterns` and `TimerService.handleBoundaryReached/handleTimerCompleted`.
- [x] Foreground service start/command race mitigated with pending command queue and readiness guard in `TimerService` (UI state persisted through DataStore).
- [x] Notification copy updated to `<Preset> • <Phase>`, richer progress text, and preset-aware `PendingIntent` in `TimerService`.
- [x] Duration formatter extracted to `util/DurationFormatter.kt` and applied on preset list rows.
- [x] Default timer reducer unit tests covering start, ticks, pause/resume, skip, stop, and zero-length segments (`app/src/test/.../DefaultTimerStateReducerTest.kt`).

## Not Done
- [ ] Implement TileService with Dynamic Time progress, TileUpdateRequester callbacks, and tile actions per AGENTS.md (§11).
- [ ] Implement preset editor navigation/actions (currently buttons in `PresetListScreen` are placeholders).
- [ ] Add instrumentation smoke tests on Wear AVD per §9 (unit tests exist, instrumentation missing).
- [ ] Document and verify exact-alarm vs tick evaluation with reproducible measurements per §6 (§13); ensure findings are captured alongside the manifest decision.
