package com.example.sermontimer.tile

import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService

private const val TILE_LOG_TAG = "TILE"

/**
 * Dispatches requests for Wear Tile updates so callers don't have to interact with [TileService]
 * directly. Helps centralize error handling and keeps the logic testable.
 */
fun interface TileUpdateDispatcher {
    fun requestTileUpdate()
}

class WearTileUpdateDispatcher(context: Context) : TileUpdateDispatcher {
    private val appContext = context.applicationContext

    override fun requestTileUpdate() {
        try {
            TileService.getUpdater(appContext).requestUpdate(SermonTileService::class.java)
        } catch (t: Throwable) {
            Log.w(TILE_LOG_TAG, "Failed to request tile update", t)
        }
    }
}
