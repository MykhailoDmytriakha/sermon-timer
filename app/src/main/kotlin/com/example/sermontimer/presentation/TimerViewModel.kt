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
import kotlinx.coroutines.flow.distinctUntilChanged
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

    private val _ambientState = MutableStateFlow(AmbientUiState())
    val ambientState: StateFlow<AmbientUiState> = _ambientState.asStateFlow()

    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded: StateFlow<Boolean> = _isDataLoaded.asStateFlow()

    private var lastObservedRunStatus: RunStatus = RunStatus.IDLE

    init {
        // Delay data loading to allow splash screen to show and UI to initialize
        viewModelScope.launch {
            kotlinx.coroutines.delay(100) // Small delay to allow splash screen
            loadData()
            observeTimerState()
            // Initialize defaults if needed after data is loaded
            initializeDefaultsIfNeeded()
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            // Load presets
            dataRepository.presets
                .distinctUntilChanged()
                .collect { presets ->
                    updatePresetsSorted(presets, _defaultPresetId.value)
                }
        }

        viewModelScope.launch {
            // Load default preset
            dataRepository.defaultPresetId
                .distinctUntilChanged()
                .collect { defaultId ->
                    _defaultPresetId.value = defaultId
                    updatePresetsSorted(_presets.value, defaultId)
                    _isDataLoaded.value = true // Mark data as loaded
                }
        }
    }

    /**
     * Updates the presets list with proper sorting: default preset first, then others by title.
     */
    private fun updatePresetsSorted(presets: List<Preset>, defaultPresetId: String?) {
        val sortedPresets = presets.sortedWith(compareBy<Preset> { preset ->
            // Default preset comes first (false < true)
            preset.id != defaultPresetId
        }.thenBy { it.title })

        _presets.value = sortedPresets
    }

    private fun observeTimerState() {
        viewModelScope.launch {
            // For now, we'll load from DataStore. In a full implementation,
            // this would observe the service state via a bound connection
            dataRepository.lastTimerState
                .distinctUntilChanged()
                .collect { state ->
                    _timerState.value = state

                    val currentStatus = state?.status ?: RunStatus.IDLE
                    if (currentStatus != lastObservedRunStatus) {
                        when (currentStatus) {
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
                        }
                        lastObservedRunStatus = currentStatus
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

    fun updateAmbientState(isAmbient: Boolean, isLowBit: Boolean, requiresBurnInProtection: Boolean) {
        _ambientState.value = AmbientUiState(
            isAmbient = isAmbient,
            isLowBit = isLowBit,
            requiresBurnInProtection = requiresBurnInProtection
        )
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

    private suspend fun initializeDefaultsIfNeeded() {
        // Import the provider function
        com.example.sermontimer.data.TimerDataProvider.initializeDefaultsIfNeeded()
    }

    enum class Screen {
        PresetList,
        Timer,
        PresetEditor
    }
}
