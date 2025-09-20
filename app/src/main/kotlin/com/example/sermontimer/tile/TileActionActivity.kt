package com.example.sermontimer.tile

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.wear.tiles.TileService
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.data.TimerDataRepository
import com.example.sermontimer.presentation.MainActivity
import com.example.sermontimer.service.TimerService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Headless activity invoked from the Tile CompactChip to relay user actions to [TimerService].
 * It immediately performs the requested action and finishes without presenting UI.
 */
class TileActionActivity : Activity() {

    private val repository: TimerDataRepository by lazy { TimerDataProvider.getRepository() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAction(intent)
        finishWithoutAnimation()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleAction(intent)
        finishWithoutAnimation()
    }

    private fun handleAction(intent: Intent?) {
        val action = intent?.getStringExtra(EXTRA_TILE_ACTION) ?: return
        when (action) {
            ACTION_START -> {
                val presetId = resolvePresetId(intent.getStringExtra(EXTRA_PRESET_ID))
                if (presetId != null) {
                    TimerService.startService(applicationContext, presetId)
                }
                openMainActivity()
            }
            ACTION_VIEW_PROGRESS -> openMainActivity()
            ACTION_PAUSE -> TimerService.pauseService(applicationContext)
            ACTION_RESUME -> {
                TimerService.resumeService(applicationContext)
                openMainActivity()
            }
            else -> Unit
        }
        TileService.getUpdater(applicationContext).requestUpdate(SermonTileService::class.java)
    }

    private fun resolvePresetId(preferredPresetId: String?): String? = runBlocking {
        if (!preferredPresetId.isNullOrBlank()) {
            return@runBlocking preferredPresetId
        }
        val defaultId = repository.defaultPresetId.first()
        if (!defaultId.isNullOrBlank()) {
            return@runBlocking defaultId
        }
        repository.presets.first().firstOrNull()?.id
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    companion object {
        const val EXTRA_TILE_ACTION = "com.example.sermontimer.tile.EXTRA_TILE_ACTION"
        const val EXTRA_PRESET_ID = "com.example.sermontimer.tile.EXTRA_PRESET_ID"

        const val ACTION_START = "start"
        const val ACTION_VIEW_PROGRESS = "view_progress"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
    }
}
