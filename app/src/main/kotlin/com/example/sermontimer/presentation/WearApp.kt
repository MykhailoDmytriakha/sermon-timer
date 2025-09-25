package com.example.sermontimer.presentation

import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import com.example.sermontimer.ui.PresetEditorScreen
import com.example.sermontimer.ui.PresetListScreen
import com.example.sermontimer.ui.TimerScreen

@Composable
fun WearApp(viewModel: TimerViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val timerState by viewModel.timerState.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val defaultPresetId by viewModel.defaultPresetId.collectAsState()
    val editorTarget by viewModel.editorTargetPreset.collectAsState()
    val ambientUiState by viewModel.ambientState.collectAsState()

    when (currentScreen) {
        TimerViewModel.Screen.PresetList -> {
            PresetListScreen(
                presets = presets,
                defaultPresetId = defaultPresetId,
                onPresetSelected = { preset ->
                    viewModel.startTimer(preset)
                },
                onAddPreset = { viewModel.startAddPresetFlow() },
                onEditPreset = { preset -> viewModel.startEditPresetFlow(preset) },
                onSetDefault = { presetId ->
                    viewModel.setDefaultPreset(presetId)
                }
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
                    onAddPreset = {},
                    onEditPreset = {},
                    onSetDefault = { viewModel.setDefaultPreset(it) }
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
