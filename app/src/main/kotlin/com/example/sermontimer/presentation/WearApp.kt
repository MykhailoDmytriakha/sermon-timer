package com.example.sermontimer.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.sermontimer.ui.PresetEditorScreen
import com.example.sermontimer.ui.PresetListScreen
import com.example.sermontimer.ui.SettingsScreen
import com.example.sermontimer.ui.TimerScreen

@Composable
fun WearApp(viewModel: TimerViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val timerState by viewModel.timerState.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val defaultPresetId by viewModel.defaultPresetId.collectAsState()
    val editorTarget by viewModel.editorTargetPreset.collectAsState()
    val ambientUiState by viewModel.ambientState.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()

    // Hardware Back button (Galaxy Watch bottom button) — navigate within app stack
    // before letting the system close the activity. Timer screen lets back pass through
    // (= close activity, service keeps running) so the watch face shows the chip.
    when (currentScreen) {
        TimerViewModel.Screen.Settings,
        TimerViewModel.Screen.PresetEditor -> BackHandler { viewModel.navigateToPresetList() }
        TimerViewModel.Screen.Timer -> {
            // Don't intercept — system back closes the activity, the foreground service
            // keeps running and the Ongoing Activity chip stays on the watch face.
        }
        TimerViewModel.Screen.PresetList -> {
            // Don't intercept — system back closes the activity (back to launcher).
        }
    }

    when (currentScreen) {
        TimerViewModel.Screen.PresetList -> {
            PresetListScreen(
                presets = presets,
                defaultPresetId = defaultPresetId,
                onPresetSelected = { preset ->
                    viewModel.startTimer(preset)
                },
                onStartTimer = { preset ->
                    viewModel.startTimer(preset)
                },
                onAddPreset = { viewModel.startAddPresetFlow() },
                onEditPreset = { preset -> viewModel.startEditPresetFlow(preset) },
                onSetDefault = { presetId ->
                    viewModel.setDefaultPreset(presetId)
                },
                onOpenSettings = { viewModel.openSettings() },
            )
        }

        TimerViewModel.Screen.Settings -> {
            SettingsScreen(
                settings = appSettings,
                onSettingsChange = { viewModel.saveAppSettings(it) },
                onBack = { viewModel.closeSettings() },
            )
        }

        TimerViewModel.Screen.Timer -> {
            timerState?.let { state ->
                TimerScreen(
                    timerState = state,
                    ambientState = ambientUiState,
                    onPause = { viewModel.pauseTimer() },
                    onResume = { viewModel.resumeTimer() },
                    onSkip = { viewModel.skipSegment() },
                    onStop = { viewModel.stopTimer() }
                )
            } ?: run {
                // Show loading or error state
                PresetListScreen(
                    presets = presets,
                    defaultPresetId = defaultPresetId,
                    onPresetSelected = { preset -> viewModel.startTimer(preset) },
                    onStartTimer = { preset -> viewModel.startTimer(preset) },
                    onAddPreset = {},
                    onEditPreset = {},
                    onSetDefault = { viewModel.setDefaultPreset(it) },
                    onOpenSettings = { viewModel.openSettings() },
                )
            }
        }

        TimerViewModel.Screen.PresetEditor -> {
            PresetEditorScreen(
                preset = editorTarget,
                onSave = { preset ->
                    if (editorTarget == null) {
                        // Adding new preset
                        viewModel.addPreset(preset)
                    } else {
                        // Updating existing preset
                        viewModel.updatePreset(preset)
                    }
                    viewModel.closePresetEditor()
                },
                onCancel = { viewModel.closePresetEditor() },
                onDelete = { preset ->
                    viewModel.deletePreset(preset.id)
                    viewModel.closePresetEditor()
                }
            )
        }
    }
}
