package com.example.sermontimer

import android.app.Application
import com.example.sermontimer.data.TimerDataProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SermonTimerApplication : Application() {

    private val applicationScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        TimerDataProvider.initialize(this)

        // Initialize default presets if needed
        applicationScope.launch {
            TimerDataProvider.initializeDefaultsIfNeeded()
        }
    }
}
