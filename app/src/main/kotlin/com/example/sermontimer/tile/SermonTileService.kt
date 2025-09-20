package com.example.sermontimer.tile

import android.util.Log
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.TypeBuilders
import androidx.wear.protolayout.expression.AnimationParameterBuilders
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.ChipDefaults
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.TitleChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.sermontimer.R
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.data.TimerDataRepository
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.util.DurationFormatter
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private const val RESOURCES_VERSION = "2"
private const val TILE_CLICK_ID = "tile-primary"
private const val TILE_LOG_TAG = "TILE"

/**
 * Wear OS Tile service that surfaces the Sermon Timer state with Dynamic Time progress and
 * contextual controls.
 */
class SermonTileService : TileService() {

    private lateinit var dataRepository: TimerDataRepository

    override fun onCreate() {
        super.onCreate()
        dataRepository = TimerDataProvider.getRepository()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> {
        return Futures.immediateFuture(buildTile(requestParams))
    }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> {
        val resources: ResourceBuilders.Resources = ResourceBuilders.Resources.Builder()
            .setVersion(RESOURCES_VERSION)
            .build()
        return Futures.immediateFuture<ResourceBuilders.Resources>(resources)
    }

    private fun buildTile(request: RequestBuilders.TileRequest): TileBuilders.Tile {
        val deviceParameters = request.deviceConfiguration
        val snapshot = runBlocking { loadSnapshot() }
        logSnapshot(snapshot)
        val layout = createLayout(deviceParameters, snapshot)
        val timeline = TimelineBuilders.Timeline.fromLayoutElement(layout)

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(snapshot.freshnessIntervalMillis)
            .build()
    }

    private suspend fun loadSnapshot(): TileSnapshot {
        val presets = dataRepository.presets.first()
        val defaultPresetId = dataRepository.defaultPresetId.first()
        val timerState = dataRepository.lastTimerState.first()
        return mapToSnapshot(timerState, presets, defaultPresetId)
    }

    private fun createLayout(
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        snapshot: TileSnapshot,
    ): LayoutElementBuilders.LayoutElement {
        val primaryLabel = Text.Builder(this, snapshot.primaryLabel)
            .setTypography(Typography.TYPOGRAPHY_TITLE1)
            .setColor(ColorBuilders.argb(snapshot.primaryLabelColor))
            .build()

        val clickable = createActionClickable(snapshot.buttonAction, snapshot.targetPresetId)

        val chip = TitleChip.Builder(this, snapshot.buttonText, clickable, deviceParameters)
            .setChipColors(snapshot.primaryChipColors)
            .setContentDescription(snapshot.buttonContentDescription)
            .build()


        // Create a centered layout with the app label and button
        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(primaryLabel)
                    .addContent(
                        LayoutElementBuilders.Spacer.Builder()
                            .setHeight(DimensionBuilders.dp(16f))
                            .build()
                    )
                    .addContent(chip)
                    .build()
            )
            .build()
    }

    private fun createActionClickable(action: TileButtonAction, presetId: String?): ModifiersBuilders.Clickable {
        val androidActivity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(TileActionActivity::class.java.name)
            .addKeyToExtraMapping(TileActionActivity.EXTRA_TILE_ACTION, ActionBuilders.AndroidStringExtra.Builder().setValue(action.intentAction).build())
            .apply {
                if (!presetId.isNullOrBlank()) {
                    addKeyToExtraMapping(
                        TileActionActivity.EXTRA_PRESET_ID,
                        ActionBuilders.AndroidStringExtra.Builder().setValue(presetId).build()
                    )
                }
            }
            .build()

        val launchAction = ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(androidActivity)
            .build()

        return ModifiersBuilders.Clickable.Builder()
            .setId(TILE_CLICK_ID)
            .setOnClick(launchAction)
            .build()
    }

    private fun mapToSnapshot(
        timerState: TimerState?,
        presets: List<Preset>,
        defaultPresetId: String?,
    ): TileSnapshot {
        // Always show simple app shortcut regardless of timer state
        val appLabel = getString(R.string.tile_app_label)

        return TileSnapshot(
            primaryLabel = appLabel,
            buttonText = getString(R.string.tile_open_app),
            buttonContentDescription = getString(R.string.tile_description),
            buttonAction = TileButtonAction.OPEN_APP,
            targetPresetId = null,
            primaryLabelColor = snapshotColors.primary,
            primaryChipColors = snapshotColors.primaryChip,
            freshnessIntervalMillis = 0L
        )
    }


    private fun logSnapshot(snapshot: TileSnapshot) {
        Log.i(
            TILE_LOG_TAG,
            "Tile snapshot → button='${snapshot.buttonText}', primary='${snapshot.primaryLabel}'"
        )
    }

    private val snapshotColors = TileColors()

    private data class TileSnapshot(
        val primaryLabel: String,
        val buttonText: String,
        val buttonContentDescription: String,
        val buttonAction: TileButtonAction,
        val targetPresetId: String?,
        val primaryLabelColor: Int,
        val primaryChipColors: ChipColors,
        val freshnessIntervalMillis: Long,
    )

    private enum class TileButtonAction(val intentAction: String) {
        START(TileActionActivity.ACTION_START),
        VIEW_PROGRESS(TileActionActivity.ACTION_VIEW_PROGRESS),
        PAUSE(TileActionActivity.ACTION_PAUSE),
        RESUME(TileActionActivity.ACTION_RESUME),
        OPEN_APP(TileActionActivity.ACTION_OPEN_APP),
    }

    private class TileColors {
        private val colors = Colors.DEFAULT
        val primary: Int = colors.onSurface
        val primaryChip: ChipColors = ChipDefaults.PRIMARY_COLORS
    }
}
