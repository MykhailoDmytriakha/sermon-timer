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
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.example.sermontimer.R
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.data.TimerDataRepository
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.Segment
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
    ): PrimaryLayout {
        val progressPropBuilder = TypeBuilders.FloatProp.Builder(snapshot.progressFraction.coerceIn(0f, 1f))
        val dynamicProgress = snapshot.dynamicProgress
        if (dynamicProgress != null) {
            progressPropBuilder.setDynamicValue(dynamicProgress)
        }

        val progressIndicator = CircularProgressIndicator.Builder()
            .setProgress(progressPropBuilder.build())
            .setContentDescription(snapshot.progressContentDescription)
            .setOuterMarginApplied(true)
            .build()

        val centerText = Text.Builder(this, snapshot.centerText)
            .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
            .setColor(ColorBuilders.argb(snapshot.centerTextColor))
            .build()

        val progressContainer = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .addContent(progressIndicator)
            .addContent(centerText)
            .build()

        val primaryLabel = Text.Builder(this, snapshot.primaryLabel)
            .setTypography(Typography.TYPOGRAPHY_TITLE1)
            .setColor(ColorBuilders.argb(snapshot.primaryLabelColor))
            .build()

        val secondaryLabel = snapshot.secondaryLabel?.let {
            Text.Builder(this, it)
                .setTypography(Typography.TYPOGRAPHY_BODY1)
                .setColor(ColorBuilders.argb(snapshot.secondaryLabelColor))
                .build()
        }

        val clickable = createActionClickable(snapshot.buttonAction, snapshot.targetPresetId)

        val chip = CompactChip.Builder(this, snapshot.buttonText, clickable, deviceParameters)
            .setChipColors(snapshot.compactChipColors)
            .setContentDescription(snapshot.buttonContentDescription)
            .build()

        return PrimaryLayout.Builder(deviceParameters)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(primaryLabel)
            .setContent(progressContainer)
            .setPrimaryChipContent(chip)
            .apply {
                if (secondaryLabel != null) {
                    setSecondaryLabelTextContent(secondaryLabel)
                }
            }
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
        val fallbackPreset = presets.find { it.id == defaultPresetId } ?: presets.firstOrNull()
        if (timerState == null || timerState.status == RunStatus.IDLE) {
            val targetPreset = fallbackPreset
            val presetTitle = targetPreset?.title ?: getString(R.string.tile_ready_to_start)
            val durationLabel = targetPreset?.let {
                DurationFormatter.formatPresetDurations(it.introSec, it.mainSec, it.outroSec)
            }
            val totalSeconds = targetPreset?.let { it.introSec + it.mainSec + it.outroSec } ?: 0
            return TileSnapshot(
                status = RunStatus.IDLE,
                primaryLabel = presetTitle,
                secondaryLabel = durationLabel,
                centerText = if (totalSeconds > 0) DurationFormatter.formatDurationCompact(totalSeconds) else "--:--",
                buttonText = getString(R.string.action_start),
                buttonContentDescription = getString(R.string.tile_ready_to_start),
                buttonAction = TileButtonAction.START,
                targetPresetId = targetPreset?.id,
                progressFraction = 0f,
                dynamicProgress = null,
                progressContentDescription = getString(R.string.tile_ready_to_start),
                centerTextColor = snapshotColors.center,
                primaryLabelColor = snapshotColors.primary,
                secondaryLabelColor = snapshotColors.secondary,
                compactChipColors = snapshotColors.primaryChip,
                freshnessIntervalMillis = 0L
            )
        }

        val activePresetId = timerState.activePreset?.id
        val activePreset = presets.find { it.id == activePresetId } ?: fallbackPreset
        val totalSeconds = timerState.totalSec.takeIf { it > 0 } ?: activePreset?.let { it.introSec + it.mainSec + it.outroSec } ?: 0
        val elapsedSeconds = min(timerState.elapsedTotalSec, totalSeconds)
        val remainingSeconds = max(0, totalSeconds - elapsedSeconds)
        val fraction = if (totalSeconds > 0) elapsedSeconds.toFloat() / totalSeconds else 0f
        val phaseLabel = phaseLabel(timerState.segment)
        val remainingFormatted = DurationFormatter.formatTimerDisplay(timerState.remainingInSegmentSec)
        val totalFormatted = if (totalSeconds > 0) DurationFormatter.formatDurationCompact(totalSeconds) else "--:--"
        val presetTitle = activePreset?.title ?: getString(R.string.app_name)

        val (buttonText, buttonAction) = when (timerState.status) {
            RunStatus.RUNNING -> getString(R.string.action_view_progress) to TileButtonAction.VIEW_PROGRESS
            RunStatus.PAUSED -> getString(R.string.action_view_progress) to TileButtonAction.VIEW_PROGRESS
            RunStatus.DONE -> getString(R.string.action_start) to TileButtonAction.START
            else -> getString(R.string.action_start) to TileButtonAction.START
        }

        val primaryLabel = when (timerState.status) {
            RunStatus.RUNNING -> getString(R.string.tile_timer_running)
            RunStatus.PAUSED -> getString(R.string.tile_timer_paused)
            RunStatus.DONE -> getString(R.string.tile_label_done, presetTitle)
            else -> presetTitle
        }

        val secondaryLabel = when (timerState.status) {
            RunStatus.RUNNING, RunStatus.PAUSED -> getString(R.string.tile_tap_to_view)
            RunStatus.DONE -> getString(R.string.tile_label_done_total, totalFormatted)
            else -> presetTitle
        }

        val dynamicProgress = null // No progress animation for tile shortcut mode

        val progressDescription = when (timerState.status) {
            RunStatus.RUNNING, RunStatus.PAUSED -> getString(R.string.tile_timer_active)
            RunStatus.DONE -> getString(R.string.tile_progress_done, totalFormatted)
            else -> getString(R.string.tile_ready_to_start)
        }

        val centerText = when (timerState.status) {
            RunStatus.RUNNING, RunStatus.PAUSED -> "--:--"
            RunStatus.DONE -> totalFormatted
            else -> remainingFormatted
        }

        val targetPresetId = when (timerState.status) {
            RunStatus.RUNNING, RunStatus.PAUSED, RunStatus.DONE -> activePreset?.id
            else -> fallbackPreset?.id
        }

        val chipColors = when (timerState.status) {
            RunStatus.PAUSED -> snapshotColors.secondaryChip
            RunStatus.RUNNING -> snapshotColors.primaryChip
            RunStatus.DONE -> snapshotColors.primaryChip
            else -> snapshotColors.primaryChip
        }

        return TileSnapshot(
            status = timerState.status,
            primaryLabel = primaryLabel,
            secondaryLabel = secondaryLabel,
            centerText = centerText,
            buttonText = buttonText,
            buttonContentDescription = progressDescription,
            buttonAction = buttonAction,
            targetPresetId = targetPresetId,
            progressFraction = if (timerState.status == RunStatus.RUNNING || timerState.status == RunStatus.PAUSED) 0f else fraction.coerceIn(0f, 1f),
            dynamicProgress = dynamicProgress,
            progressContentDescription = progressDescription,
            centerTextColor = snapshotColors.center,
            primaryLabelColor = snapshotColors.primary,
            secondaryLabelColor = snapshotColors.secondary,
            compactChipColors = chipColors,
            freshnessIntervalMillis = if (timerState.status == RunStatus.RUNNING) 0L else 30_000L
        )
    }

    private fun phaseLabel(segment: Segment): String = when (segment) {
        Segment.INTRO -> getString(R.string.segment_intro)
        Segment.MAIN -> getString(R.string.segment_main)
        Segment.OUTRO -> getString(R.string.segment_outro)
        Segment.DONE -> getString(R.string.timer_done)
    }

    private fun logSnapshot(snapshot: TileSnapshot) {
        val progress = String.format(Locale.US, "%.2f", snapshot.progressFraction)
        Log.i(
            TILE_LOG_TAG,
            "Tile snapshot → status=${snapshot.status}, button='${snapshot.buttonText}', primary='${snapshot.primaryLabel}', secondary='${snapshot.secondaryLabel ?: ""}', targetPreset=${snapshot.targetPresetId ?: "none"}, progressFraction=$progress"
        )
    }

    private val snapshotColors = TileColors()

    private data class TileSnapshot(
        val status: RunStatus,
        val primaryLabel: String,
        val secondaryLabel: String?,
        val centerText: String,
        val buttonText: String,
        val buttonContentDescription: String,
        val buttonAction: TileButtonAction,
        val targetPresetId: String?,
        val progressFraction: Float,
        val dynamicProgress: DynamicBuilders.DynamicFloat?,
        val progressContentDescription: String,
        val centerTextColor: Int,
        val primaryLabelColor: Int,
        val secondaryLabelColor: Int,
        val compactChipColors: ChipColors,
        val freshnessIntervalMillis: Long,
    )

    private enum class TileButtonAction(val intentAction: String) {
        START(TileActionActivity.ACTION_START),
        VIEW_PROGRESS(TileActionActivity.ACTION_VIEW_PROGRESS),
        PAUSE(TileActionActivity.ACTION_PAUSE),
        RESUME(TileActionActivity.ACTION_RESUME),
    }

    private class TileColors {
        private val colors = Colors.DEFAULT
        val primary: Int = colors.onSurface
        val secondary: Int = colors.onSurface
        val center: Int = colors.onPrimary
        val primaryChip: ChipColors = ChipDefaults.COMPACT_PRIMARY_COLORS
        val secondaryChip: ChipColors = ChipDefaults.COMPACT_SECONDARY_COLORS
    }
}
