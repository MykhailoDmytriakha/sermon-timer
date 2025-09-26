package com.example.sermontimer.data

import android.content.Context
import com.example.sermontimer.tile.TileUpdateDispatcher
import com.example.sermontimer.tile.WearTileUpdateDispatcher

/**
 * Provides timer data repository instance.
 * Simple singleton provider for dependency injection.
 */
object TimerDataProvider {
    private var repository: TimerDataRepository? = null
    private var initializer: PresetInitializer? = null

    fun initialize(
        context: Context,
        tileUpdateDispatcher: TileUpdateDispatcher = WearTileUpdateDispatcher(context)
    ) {
        repository = DataStoreTimerRepository(context, tileUpdateDispatcher)
        initializer = PresetInitializer(repository!!)
    }

    fun getRepository(): TimerDataRepository {
        if (repository == null) {
            throw IllegalStateException("TimerDataProvider must be initialized first")
        }
        return repository!!
    }

    suspend fun initializeDefaultsIfNeeded() {
        if (initializer == null) {
            throw IllegalStateException("TimerDataProvider must be initialized first")
        }
        initializer!!.initializeDefaults()
    }
}
