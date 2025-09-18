package com.example.sermontimer.data

import android.content.Context

/**
 * Provides timer data repository instance.
 * Simple singleton provider for dependency injection.
 */
object TimerDataProvider {
    private lateinit var repository: TimerDataRepository
    private lateinit var initializer: PresetInitializer

    fun initialize(context: Context) {
        repository = DataStoreTimerRepository(context)
        initializer = PresetInitializer(repository)
    }

    fun getRepository(): TimerDataRepository {
        check(::repository.isInitialized) { "TimerDataProvider must be initialized first" }
        return repository
    }

    suspend fun initializeDefaultsIfNeeded() {
        check(::initializer.isInitialized) { "TimerDataProvider must be initialized first" }
        initializer.initializeDefaults()
    }
}
