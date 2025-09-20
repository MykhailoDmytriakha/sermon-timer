package com.example.sermontimer.presentation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.service.TimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class TimerViewModel(application: Application) : AndroidViewModel(application) {

    private val dataRepository = TimerDataProvider.getRepository()

    // UI State
    private val _currentScreen = MutableStateFlow<Screen>(Screen.PresetList)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _timerState = MutableStateFlow<TimerState?>(null)
    val timerState: StateFlow<TimerState?> = _timerState.asStateFlow()

    private val _presets = MutableStateFlow<List<Preset>>(emptyList())
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    private val _defaultPresetId = MutableStateFlow<String?>(null)
    val defaultPresetId: StateFlow<String?> = _defaultPresetId.asStateFlow()

    private val _editorTargetPreset = MutableStateFlow<Preset?>(null)
    val editorTargetPreset: StateFlow<Preset?> = _editorTargetPreset.asStateFlow()

    init {
        loadData()
        observeTimerState()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load presets
            dataRepository.presets.collect { presets ->
                _presets.value = presets
            }
        }

        viewModelScope.launch {
            // Load default preset
            dataRepository.defaultPresetId.collect { defaultId ->
                _defaultPresetId.value = defaultId
            }
        }
    }

    private fun observeTimerState() {
        viewModelScope.launch {
            // For now, we'll load from DataStore. In a full implementation,
            // this would observe the service state via a bound connection
            dataRepository.lastTimerState.collect { state ->
                _timerState.value = state

                when (state?.status) {
                    RunStatus.RUNNING, RunStatus.PAUSED, RunStatus.DONE -> {
                        if (_currentScreen.value != Screen.PresetEditor) {
                            _currentScreen.value = Screen.Timer
                        }
                    }

                    RunStatus.IDLE -> {
                        if (_currentScreen.value == Screen.Timer) {
                            _currentScreen.value = Screen.PresetList
                        }
                    }

                    null -> Unit
                }
            }
        }
    }

    // Navigation
    fun navigateToTimer() {
        _currentScreen.value = Screen.Timer
    }

    fun navigateToPresetList() {
        _currentScreen.value = Screen.PresetList
    }

    fun openPresetEditor(preset: Preset?) {
        _editorTargetPreset.value = preset
        _currentScreen.value = Screen.PresetEditor
    }

    fun closePresetEditor() {
        _editorTargetPreset.value = null
        _currentScreen.value = Screen.PresetList
    }

    // Timer actions
    fun startTimer(preset: Preset) {
        TimerService.startService(getApplication(), preset.id)
        navigateToTimer()
    }

    fun pauseTimer() {
        TimerService.pauseService(getApplication())
    }

    fun resumeTimer() {
        TimerService.resumeService(getApplication())
    }

    fun skipSegment() {
        TimerService.skipService(getApplication())
    }

    fun stopTimer() {
        TimerService.stopService(getApplication())
        navigateToPresetList()
    }

    // Preset management
    fun addPreset(preset: Preset) {
        viewModelScope.launch {
            dataRepository.savePreset(preset)
        }
    }

    fun updatePreset(preset: Preset) {
        viewModelScope.launch {
            dataRepository.savePreset(preset)
        }
    }

    fun startAddPresetFlow() {
        openPresetEditor(null)
    }

    fun startEditPresetFlow(preset: Preset) {
        openPresetEditor(preset)
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            dataRepository.deletePreset(presetId)
        }
    }

    fun setDefaultPreset(presetId: String?) {
        viewModelScope.launch {
            dataRepository.setDefaultPresetId(presetId)
        }
    }

    enum class Screen {
        PresetList,
        Timer,
        PresetEditor
    }
}
