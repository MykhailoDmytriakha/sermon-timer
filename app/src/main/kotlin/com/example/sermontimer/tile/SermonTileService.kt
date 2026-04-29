package com.example.sermontimer.tile

import android.graphics.Color
import android.util.Log
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.ChipDefaults
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

private const val TILE_RESOURCES_VERSION = "4"
private const val TILE_CLICKABLE_ID = "tile-primary"
private const val LOG_TAG_TILE = "SermonTile"

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
            .setVersion(TILE_RESOURCES_VERSION)
            .build()
        return Futures.immediateFuture(resources)
    }

    private fun buildTile(request: RequestBuilders.TileRequest): TileBuilders.Tile {
        val deviceParameters = request.deviceConfiguration
        val snapshot = runBlocking { loadSnapshot() }
        logSnapshot(snapshot)
        val layout = createLayout(deviceParameters, snapshot)
        val timeline = TimelineBuilders.Timeline.fromLayoutElement(layout)

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(TILE_RESOURCES_VERSION)
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
        snapshot.defaultPreset?.let { defaultPreset ->
            return createDefaultPresetLayout(deviceParameters, snapshot, defaultPreset)
        }

        val primaryContent = Text.Builder(this, snapshot.titleText)
            .setTypography(Typography.TYPOGRAPHY_TITLE1)
            .setColor(ColorBuilders.argb(snapshot.primaryLabelColor))
            .setMaxLines(2)
            .setMultilineAlignment(LayoutElementBuilders.TEXT_ALIGN_CENTER)
            .build()

        val columnBuilder = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(primaryContent)

        return PrimaryLayout.Builder(deviceParameters)
            .setResponsiveContentInsetEnabled(true)
            .setContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .addContent(columnBuilder.build())
                    .build()
            )
            .setPrimaryChipContent(buildPrimaryChip(deviceParameters, snapshot))
            .build()
    }

    private fun createDefaultPresetLayout(
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        snapshot: TileSnapshot,
        preset: DefaultPresetSnapshot,
    ): LayoutElementBuilders.LayoutElement {
        return createStatusIndicatorWithButton(deviceParameters, snapshot, preset)
    }

    private fun buildPrimaryChip(
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        snapshot: TileSnapshot,
    ): LayoutElementBuilders.LayoutElement {
        val clickable = createActionClickable(snapshot.buttonAction, snapshot.targetPresetId)
        return CompactChip.Builder(this, snapshot.buttonText, clickable, deviceParameters)
            .setContentDescription(snapshot.buttonContentDescription)
            .setChipColors(snapshot.primaryChipColors)
            .build()
    }

    private fun createStatusIndicatorWithButton(
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        snapshot: TileSnapshot,
        preset: DefaultPresetSnapshot
    ): LayoutElementBuilders.LayoutElement {
        // Create a linear progress bar showing preset structure with colored segments
        val segmentSpecs =
            computeLinearSegmentSpecs(listOf(preset.introSec, preset.mainSec, preset.outroSec))

        return LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.wrap())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setTop(DimensionBuilders.dp(34f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                // Preset title
                Text.Builder(this@SermonTileService, preset.presetName)
                    .setTypography(Typography.TYPOGRAPHY_TITLE3)
                    .setColor(ColorBuilders.argb(snapshotColors.primary))
                    .setMaxLines(1)
                    .build()
            )
            .addContent(
                // Spacer between title and progress bar
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(8f))
                    .build()
            )
            .addContent(
                // Progress bar container
                LayoutElementBuilders.Box.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.dp(24f))
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(ColorBuilders.argb(Color.parseColor("#1A1A1A"))) // Dark background
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        // Use Row to properly distribute segments
                        LayoutElementBuilders.Row.Builder()
                            .setWidth(DimensionBuilders.expand())
                            .setHeight(DimensionBuilders.expand())
                            .apply {
                                // Add each segment as a colored box
                                segmentSpecs.forEach { spec ->
                                    addContent(
                                        LayoutElementBuilders.Box.Builder()
                                            .setWidth(DimensionBuilders.weight(spec.widthFraction))
                                            .setHeight(DimensionBuilders.expand())
                                            .setModifiers(
                                                ModifiersBuilders.Modifiers.Builder()
                                                    .setBackground(
                                                        ModifiersBuilders.Background.Builder()
                                                            .setColor(
                                                                ColorBuilders.argb(
                                                                    snapshotColors.colorForSegment(
                                                                        spec.segmentIndex
                                                                    )
                                                                )
                                                            )
                                                            .build()
                                                    )
                                                    .build()
                                            )
                                            .build()
                                    )
                                }
                            }
                            .build()
                    )
                    .build()
            )
            .addContent(
                // Time labels row
                LayoutElementBuilders.Row.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHeight(DimensionBuilders.wrap())
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .apply {
                        // Add time labels for each active segment in the same order as segmentSpecs
                        segmentSpecs.forEach { spec ->
                            val durations = listOf(preset.introSec, preset.mainSec, preset.outroSec)
                            val durationSec = durations[spec.segmentIndex]
                            val timeText = DurationFormatter.formatTimerDisplay(durationSec)
                            addContent(
                                LayoutElementBuilders.Box.Builder()
                                    .setWidth(DimensionBuilders.weight(spec.widthFraction))
                                    .setHeight(DimensionBuilders.wrap())
                                    .addContent(
                                        Text.Builder(this@SermonTileService, timeText)
                                            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                                            .setColor(ColorBuilders.argb(snapshotColors.primary))
                                            .setMaxLines(1)
                                            .build()
                                    )
                                    .build()
                            )
                        }
                    }
                    .build()
            )
            .addContent(
                // Small spacer before button
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(2f))
                    .build()
            )
            .addContent(
                // Button
                buildPrimaryChip(deviceParameters, snapshot)
            )
            .build()
    }


    private fun createActionClickable(
        action: TileButtonAction,
        presetId: String?
    ): ModifiersBuilders.Clickable {
        val androidActivity = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
            .setClassName(TileActionActivity::class.java.name)
            .addKeyToExtraMapping(
                TileActionActivity.EXTRA_TILE_ACTION,
                ActionBuilders.AndroidStringExtra.Builder().setValue(action.intentAction).build()
            )
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
            .setId(TILE_CLICKABLE_ID)
            .setOnClick(launchAction)
            .build()
    }

    private fun mapToSnapshot(
        _timerState: TimerState?,
        presets: List<Preset>,
        defaultPresetId: String?,
    ): TileSnapshot {
        val defaultPreset = resolveDefaultPreset(presets, defaultPresetId)
        val defaultPresetSnapshot = defaultPreset?.let { preset ->
            DefaultPresetSnapshot(
                presetName = preset.title.ifBlank { getString(R.string.tile_app_label) },
                introSec = preset.introSec,
                mainSec = preset.mainSec,
                outroSec = preset.outroSec,
            )
        }

        val status = _timerState?.status ?: RunStatus.IDLE
        val activePresetId = _timerState?.activePreset?.id
        val activePresetName = activePresetId?.let { id ->
            presets.firstOrNull { it.id == id }?.title?.takeIf { it.isNotBlank() }
        }
        val baseTitle = activePresetName
            ?: defaultPresetSnapshot?.presetName
            ?: getString(R.string.tile_app_label)

        val restartPresetId = activePresetId ?: defaultPreset?.id
        val buttonAction: TileButtonAction
        val buttonText: String
        val buttonDescription: String
        val targetPresetId: String?
        val chipColors: ChipColors

        when (status) {
            RunStatus.PREROLL -> {
                buttonAction = TileButtonAction.VIEW_PROGRESS
                buttonText = getString(R.string.action_view_progress)
                buttonDescription = getString(R.string.phase_preroll)
                targetPresetId = null
                chipColors = snapshotColors.secondaryChip
            }

            RunStatus.RUNNING -> {
                buttonAction = TileButtonAction.VIEW_PROGRESS
                buttonText = getString(R.string.action_view_progress)
                buttonDescription = getString(R.string.tile_timer_running)
                targetPresetId = null
                chipColors = snapshotColors.secondaryChip
            }

            RunStatus.PAUSED -> {
                buttonAction = TileButtonAction.RESUME
                buttonText = getString(R.string.action_resume)
                buttonDescription = getString(R.string.tile_timer_paused)
                targetPresetId = null
                chipColors = snapshotColors.primaryChip
            }

            RunStatus.OVERTIME -> {
                buttonAction = TileButtonAction.VIEW_PROGRESS
                buttonText = getString(R.string.action_view_progress)
                buttonDescription = getString(R.string.phase_overtime)
                targetPresetId = null
                chipColors = snapshotColors.secondaryChip
            }

            RunStatus.DONE -> {
                if (!restartPresetId.isNullOrBlank()) {
                    buttonAction = TileButtonAction.START
                    buttonText = getString(R.string.tile_start_timer)
                    buttonDescription = getString(R.string.tile_ready_to_start)
                    targetPresetId = restartPresetId
                    chipColors = snapshotColors.primaryChip
                } else {
                    buttonAction = TileButtonAction.VIEW_PROGRESS
                    buttonText = getString(R.string.tile_tap_to_view)
                    buttonDescription = getString(R.string.tile_description)
                    targetPresetId = null
                    chipColors = snapshotColors.secondaryChip
                }
            }

            RunStatus.IDLE -> {
                if (!restartPresetId.isNullOrBlank()) {
                    buttonAction = TileButtonAction.START
                    buttonText = getString(R.string.tile_start_timer)
                    buttonDescription = getString(R.string.tile_description)
                    targetPresetId = restartPresetId
                    chipColors = snapshotColors.primaryChip
                } else {
                    buttonAction = TileButtonAction.VIEW_PROGRESS
                    buttonText = getString(R.string.tile_tap_to_view)
                    buttonDescription = getString(R.string.tile_description)
                    targetPresetId = null
                    chipColors = snapshotColors.secondaryChip
                }
            }
        }

        return TileSnapshot(
            titleText = baseTitle,
            buttonText = buttonText,
            buttonContentDescription = buttonDescription,
            buttonAction = buttonAction,
            targetPresetId = targetPresetId,
            primaryLabelColor = snapshotColors.primary,
            statusTextColor = snapshotColors.secondaryText,
            primaryChipColors = chipColors,
            freshnessIntervalMillis = 0L,
            defaultPreset = defaultPresetSnapshot,
            status = status,
        )
    }

    private fun resolveDefaultPreset(
        presets: List<Preset>,
        defaultPresetId: String?,
    ): Preset? {
        if (presets.isEmpty()) return null
        if (!defaultPresetId.isNullOrBlank()) {
            presets.firstOrNull { it.id == defaultPresetId }?.let { return it }
        }
        return presets.firstOrNull()
    }

    private fun segmentDisplayLabel(segment: Segment): String = when (segment) {
        Segment.INTRO -> getString(R.string.segment_intro)
        Segment.MAIN -> getString(R.string.segment_main)
        Segment.OUTRO -> getString(R.string.segment_outro)
        Segment.DONE -> getString(R.string.timer_done)
    }


    private fun logSnapshot(snapshot: TileSnapshot) {
        Log.i(
            LOG_TAG_TILE,
            "Tile snapshot → status='${snapshot.status}', title='${snapshot.titleText}', button='${snapshot.buttonText}', default='${snapshot.defaultPreset?.presetName ?: "none"}'"
        )
    }

    private val snapshotColors = TileColors()

    private data class TileSnapshot(
        val titleText: String,
        val buttonText: String,
        val buttonContentDescription: String,
        val buttonAction: TileButtonAction,
        val targetPresetId: String?,
        val primaryLabelColor: Int,
        val statusTextColor: Int,
        val primaryChipColors: ChipColors,
        val freshnessIntervalMillis: Long,
        val defaultPreset: DefaultPresetSnapshot?,
        val status: RunStatus,
    )

    private data class DefaultPresetSnapshot(
        val presetName: String,
        val introSec: Int,
        val mainSec: Int,
        val outroSec: Int,
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
        val primary: Int = Color.WHITE
        val secondaryText: Int = Color.parseColor("#B3FFFFFF")
        val primaryChip: ChipColors = ChipDefaults.COMPACT_PRIMARY_COLORS
        val secondaryChip: ChipColors = ChipDefaults.COMPACT_SECONDARY_COLORS

        fun colorForSegment(index: Int): Int = when (index) {
            0 -> Color.parseColor("#4CAF50") // Intro - same as Activity green
            1 -> Color.parseColor("#2196F3") // Main - same as Activity blue
            2 -> Color.parseColor("#FF9800") // Outro - same as Activity orange
            else -> colors.onSurface
        }
    }
}
