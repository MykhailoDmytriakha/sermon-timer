package com.example.sermontimer.data

import android.content.Context
import com.example.sermontimer.tile.TileUpdateDispatcher
import com.example.sermontimer.tile.WearTileUpdateDispatcher

/**
 * Provides timer data repository instance.
 * Simple singleton provider for dependency injection.
 */
object TimerDataProvider {
    private lateinit var repository: TimerDataRepository
    private lateinit var initializer: PresetInitializer

    fun initialize(
        context: Context,
        tileUpdateDispatcher: TileUpdateDispatcher = WearTileUpdateDispatcher(context)
    ) {
        repository = DataStoreTimerRepository(context, tileUpdateDispatcher)
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
