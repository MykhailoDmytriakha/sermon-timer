package com.example.sermontimer.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.wear.ambient.AmbientLifecycleObserver
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.presentation.theme.SermonTimerTheme
import com.example.sermontimer.service.TimerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private val timerViewModel: TimerViewModel by viewModels()
    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val ambientCallbacks = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            val isLowBit = ambientDetails.deviceHasLowBitAmbient
            val requiresBurnIn = ambientDetails.burnInProtectionRequired
            timerViewModel.updateAmbientState(
                isAmbient = true,
                isLowBit = isLowBit,
                requiresBurnInProtection = requiresBurnIn
            )
        }

        override fun onExitAmbient() {
            timerViewModel.updateAmbientState(isAmbient = false, isLowBit = false, requiresBurnInProtection = false)
        }

        override fun onUpdateAmbient() {
            // No periodic updates needed for static timer UI.
        }
    }

    private val ambientObserver by lazy {
        AmbientLifecycleObserver(this, mainExecutor, ambientCallbacks)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No-op: service gracefully degrades when permission denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition {
            // Keep splash screen until data is loaded
            !timerViewModel.isDataLoaded.value
        }
        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        lifecycle.addObserver(ambientObserver)
        timerViewModel.updateAmbientState(isAmbient = false, isLowBit = false, requiresBurnInProtection = false)
        maybeRequestNotificationPermission()

        // Handle tile actions from intent
        handleIntent(intent)

        setContent {
            SermonTimerTheme {
                WearApp(timerViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.getStringExtra("action")
        when (action) {
            TimerService.ACTION_PAUSE -> {
                TimerService.pauseService(this)
            }
            TimerService.ACTION_RESUME -> {
                TimerService.resumeService(this)
            }
            "start_default" -> {
                activityScope.launch {
                    try {
                        val dataRepository = TimerDataProvider.getRepository()
                        val defaultPresetId = dataRepository.defaultPresetId.first()
                        if (!defaultPresetId.isNullOrBlank()) {
                            TimerService.startService(this@MainActivity, defaultPresetId)
                        }
                        // If no default preset, just open the activity normally
                    } catch (e: Exception) {
                        // Handle error silently - just open the activity
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        lifecycle.removeObserver(ambientObserver)
        super.onDestroy()
        activityScope.cancel()
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
