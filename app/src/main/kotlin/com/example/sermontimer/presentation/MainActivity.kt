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
            timerViewModel.updateAmbientState(
                isAmbient = false,
                isLowBit = false,
                requiresBurnInProtection = false
            )
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
        timerViewModel.updateAmbientState(
            isAmbient = false,
            isLowBit = false,
            requiresBurnInProtection = false
        )
        maybeRequestNotificationPermission()
        // NOTE: NOT auto-prompting for IGNORE_BATTERY_OPTIMIZATIONS in onCreate.
        // The system intent opens a Settings activity that immediately pauses
        // our MainActivity → "Activity pause timeout" + the timer UI never
        // renders. We declare the permission in the manifest so user can grant
        // it manually via Settings → Apps → Sermon Timer → Battery, but we
        // don't force the dialog. The BroadcastReceiver-mediated alarm path is
        // the primary persistence mechanism and works without this exemption.

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

    override fun onResume() {
        super.onResume()
        // If the engine is still mid-session in DataStore but the foreground service
        // got killed by the platform (Samsung Freecess on Galaxy Watch is aggressive
        // during long sermons), the Now bar chip vanishes because the notification
        // loses the FOREGROUND_SERVICE flag. We can't re-foreground from background
        // on Android 14+, but a user-driven Activity launch IS allowed to promote.
        // So whenever the user opens the app and we observe an active state, kick
        // the service via startForegroundService(ACTION_REATTACH_FGS) — this gets
        // chip back onto the watch face without resetting the timer baseline.
        activityScope.launch {
            try {
                val lastState = TimerDataProvider.getRepository().lastTimerState.first()
                if (lastState != null && lastState.isActive) {
                    TimerService.reattachForeground(this@MainActivity)
                }
            } catch (_: Exception) {
                // No-op: reattach is best-effort, never block UI on it.
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
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

}
