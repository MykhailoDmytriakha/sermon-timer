package com.example.sermontimer

import android.app.Application
import com.example.sermontimer.data.TimerDataProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SermonTimerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize TimerDataProvider synchronously to ensure it's ready when ViewModel is created
        TimerDataProvider.initialize(this)

        // Initialize default presets asynchronously - this can happen later
        // Commenting out to avoid blocking startup
        // CoroutineScope(Dispatchers.Default).launch {
        //     TimerDataProvider.initializeDefaultsIfNeeded()
        // }
    }
}
