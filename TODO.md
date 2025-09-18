# TODO

## Done
- [x] DataStore persistence for presets, default preset id, and last timer snapshot (`app/src/main/kotlin/com/example/sermontimer/data/DataStoreTimerRepository.kt`, `TimerDataProvider.kt`, `PresetInitializer.kt`).
- [x] Deterministic timer engine with reducer, commands, and coroutine wrapper (`domain/engine/*`).
- [x] Foreground service skeleton with notification actions and DataStore syncing (`app/src/main/kotlin/com/example/sermontimer/service/TimerService.kt`).
- [x] Compose UI scaffolds for preset list and timer plus application bootstrap (`presentation/*`, `ui/*`, `SermonTimerApplication.kt`).

## Not Done
- [ ] Implement TileService with Dynamic Time progress, TileUpdateRequester callbacks, and tile actions per AGENTS.md (§11).
- [ ] Wire haptic patterns for segment boundaries and completion (`TimerService.handleBoundaryReached`/`handleTimerCompleted`).
- [ ] Fix service initialization race: commands can arrive before `engine` is created in `TimerService`, add binding/guarding for safe start and UI state streaming.
- [ ] Update notification copy to include `<Preset> • <Phase>` and richer progress info as specified, plus preset-aware content intent.
- [ ] Replace the integer-only duration formatter in `PresetListScreen` and implement preset editor navigation/actions.
- [ ] Add unit tests for the reducer/engine and instrumentation smoke tests per §9.
- [ ] Evaluate exact alarm vs tick strategy per §6; implement chosen path and drop unused `WAKE_LOCK` permission if alarms not required.
