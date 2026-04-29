package com.example.sermontimer.tile

import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import com.example.sermontimer.complication.TimerComplicationService

private const val TILE_LOG_TAG = "TILE"

/**
 * Dispatches refresh requests to the watch surfaces (Tile + Complication). Centralized so
 * data layer callers don't have to know which surfaces exist.
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
        TimerComplicationService.requestUpdate(appContext)
    }
}
