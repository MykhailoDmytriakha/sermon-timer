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
- [x] Preset list wired to navigation: Add/Edit buttons launch the editor flow and return via Back (`TimerViewModel`, `WearApp`, `PresetEditorScreen`).
- [x] TileService upgraded with Dynamic Time progress ring, contextual actions, and TileUpdateRequester integration (`app/src/main/kotlin/com/example/sermontimer/tile/SermonTileService.kt`, `TileActionActivity.kt`).
- [x] Preset editor rebuilt with wearable TextField input, validation, and delete flow parity (`app/src/main/kotlin/com/example/sermontimer/ui/PresetEditorScreen.kt`).
- [x] Instrumentation smoke tests added for service + UI (`app/src/androidTest/...`).
- [x] Timing strategy evaluation documented and referenced from manifest (`TIMING_STRATEGY_EVALUATION.md`, `AndroidManifest.xml`).

## Not Done
- [ ] Pending
