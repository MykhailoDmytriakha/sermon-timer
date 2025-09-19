package com.example.sermontimer.presentation

import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import com.example.sermontimer.ui.PresetEditorPlaceholder
import com.example.sermontimer.ui.PresetListScreen
import com.example.sermontimer.ui.TimerScreen

@Composable
fun WearApp(viewModel: TimerViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val timerState by viewModel.timerState.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val defaultPresetId by viewModel.defaultPresetId.collectAsState()
    val editorTarget by viewModel.editorTargetPreset.collectAsState()

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
            PresetEditorPlaceholder(
                preset = editorTarget,
                onClose = { viewModel.closePresetEditor() }
            )
        }
    }
}
