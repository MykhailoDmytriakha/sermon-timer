package com.example.sermontimer.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.presentation.theme.SermonTimerTheme
import com.example.sermontimer.service.TimerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setTheme(android.R.style.Theme_DeviceDefault)

        // Handle tile actions from intent
        handleIntent(intent)

        setContent {
            SermonTimerTheme {
                val viewModel: TimerViewModel = viewModel()
                WearApp(viewModel)
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
        super.onDestroy()
        activityScope.cancel()
    }
}