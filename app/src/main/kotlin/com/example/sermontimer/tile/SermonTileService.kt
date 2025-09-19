package com.example.sermontimer.tile

import android.util.Log
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.*
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures

private const val TAG = "SermonTileService"

/**
 * Wear OS Tile service for the Sermon Timer.
 * Provides quick access to timer controls.
 *
 * This is a basic implementation that shows timer status.
 * TODO: Enhance with full ProtoLayout features and actions.
 */
class SermonTileService : TileService() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "TileService created")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "TileService destroyed")
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        Log.d(TAG, "Tile request received")

        // Create a simple tile layout
        val text = LayoutElementBuilders.Text.Builder()
            .setText("Sermon Timer")
            .build()

        val layout = LayoutElementBuilders.Column.Builder()
            .addContent(text)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion("1")
            .setTileTimeline(
                TimelineBuilders.Timeline.fromLayoutElement(layout)
            )
            .build()

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        Log.d(TAG, "Tile resources request received")

        val resources = ResourceBuilders.Resources.Builder()
            .setVersion("1")
            .build()

        return Futures.immediateFuture(resources)
    }
}
