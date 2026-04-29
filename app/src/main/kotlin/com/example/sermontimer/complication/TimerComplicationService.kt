package com.example.sermontimer.complication

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.example.sermontimer.R
import com.example.sermontimer.data.TimerDataProvider
import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.Segment
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.presentation.MainActivity
import com.example.sermontimer.util.DurationFormatter
import kotlinx.coroutines.flow.first
import java.time.Instant

/**
 * Publishes timer status as a watch-face complication.
 *
 * - SHORT_TEXT / LONG_TEXT: live mm:ss countdown via TimeDifferenceComplicationText
 *   — the watch face auto-updates every second without waking our process.
 * - RANGED_VALUE: arc fill = total progress; pushed via update requester on state changes.
 * - MONOCHROMATIC_IMAGE / SMALL_IMAGE: branded icon with tap-to-open.
 *
 * Tap on any complication opens the timer activity.
 */
class TimerComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val tap = openAppPendingIntent()
        val title = PlainComplicationText.Builder(getString(R.string.app_name)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = PlainComplicationText.Builder("12:34").build(),
                contentDescription = PlainComplicationText.Builder(
                    getString(R.string.complication_preview_description)
                ).build(),
            ).setTitle(title)
                .setMonochromaticImage(monochromaticImage())
                .setTapAction(tap)
                .build()

            ComplicationType.LONG_TEXT -> androidx.wear.watchface.complications.data.LongTextComplicationData.Builder(
                text = PlainComplicationText.Builder("12:34 • Main").build(),
                contentDescription = PlainComplicationText.Builder(
                    getString(R.string.complication_preview_description)
                ).build(),
            ).setTitle(title)
                .setMonochromaticImage(monochromaticImage())
                .setTapAction(tap)
                .build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 0.4f,
                min = 0f,
                max = 1f,
                contentDescription = PlainComplicationText.Builder(
                    getString(R.string.complication_preview_description)
                ).build(),
            ).setText(PlainComplicationText.Builder("12:34").build())
                .setTitle(title)
                .setMonochromaticImage(monochromaticImage())
                .setTapAction(tap)
                .build()

            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                monochromaticImage = monochromaticImage(),
                contentDescription = PlainComplicationText.Builder(
                    getString(R.string.complication_preview_description)
                ).build(),
            ).setTapAction(tap).build()

            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val state = readState()
        return buildData(state, request.complicationType)
    }

    private suspend fun readState(): TimerState? {
        return runCatching {
            TimerDataProvider.getRepository().lastTimerState.first()
        }.getOrNull()
    }

    private fun buildData(state: TimerState?, type: ComplicationType): ComplicationData? {
        val tap = openAppPendingIntent()
        val description = PlainComplicationText.Builder(getString(R.string.app_name)).build()

        // Idle/null state — show ready-to-start hint.
        if (state == null || state.status == RunStatus.IDLE) {
            return idleData(type, tap, description)
        }

        return when (type) {
            ComplicationType.SHORT_TEXT -> shortText(state, tap, description)
            ComplicationType.LONG_TEXT -> longText(state, tap, description)
            ComplicationType.RANGED_VALUE -> rangedValue(state, tap, description)
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                monochromaticImage = monochromaticImage(),
                contentDescription = description,
            ).setTapAction(tap).build()
            else -> null
        }
    }

    private fun idleData(
        type: ComplicationType,
        tap: PendingIntent,
        description: PlainComplicationText,
    ): ComplicationData? {
        val ready = PlainComplicationText.Builder(getString(R.string.complication_idle_text)).build()
        val title = PlainComplicationText.Builder(getString(R.string.app_name)).build()
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                text = ready,
                contentDescription = description,
            ).setTitle(title)
                .setMonochromaticImage(monochromaticImage())
                .setTapAction(tap)
                .build()

            ComplicationType.LONG_TEXT -> androidx.wear.watchface.complications.data.LongTextComplicationData.Builder(
                text = ready,
                contentDescription = description,
            ).setTitle(title)
                .setMonochromaticImage(monochromaticImage())
                .setTapAction(tap)
                .build()

            ComplicationType.RANGED_VALUE -> RangedValueComplicationData.Builder(
                value = 0f,
                min = 0f,
                max = 1f,
                contentDescription = description,
            ).setText(ready)
                .setTitle(title)
                .setMonochromaticImage(monochromaticImage())
                .setTapAction(tap)
                .build()

            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(
                monochromaticImage = monochromaticImage(),
                contentDescription = description,
            ).setTapAction(tap).build()

            else -> null
        }
    }

    private fun shortText(
        state: TimerState,
        tap: PendingIntent,
        description: PlainComplicationText,
    ): ComplicationData {
        val title = PlainComplicationText.Builder(phaseShortLabel(state.segment)).build()
        return ShortTextComplicationData.Builder(
            text = remainingText(state),
            contentDescription = description,
        ).setTitle(title)
            .setMonochromaticImage(monochromaticImage())
            .setTapAction(tap)
            .build()
    }

    private fun longText(
        state: TimerState,
        tap: PendingIntent,
        description: PlainComplicationText,
    ): ComplicationData {
        val title = PlainComplicationText.Builder(phaseLongLabel(state.segment)).build()
        return androidx.wear.watchface.complications.data.LongTextComplicationData.Builder(
            text = remainingText(state),
            contentDescription = description,
        ).setTitle(title)
            .setMonochromaticImage(monochromaticImage())
            .setTapAction(tap)
            .build()
    }

    private fun rangedValue(
        state: TimerState,
        tap: PendingIntent,
        description: PlainComplicationText,
    ): ComplicationData {
        val total = state.totalSec.coerceAtLeast(1)
        val progress = (state.elapsedTotalSec.toFloat() / total.toFloat()).coerceIn(0f, 1f)
        val title = PlainComplicationText.Builder(phaseShortLabel(state.segment)).build()
        return RangedValueComplicationData.Builder(
            value = progress,
            min = 0f,
            max = 1f,
            contentDescription = description,
        ).setText(remainingText(state))
            .setTitle(title)
            .setMonochromaticImage(monochromaticImage())
            .setTapAction(tap)
            .build()
    }

    /**
     * For RUNNING state, return a TimeDifferenceComplicationText counting down to
     * the moment when the current segment ends. The watch face will tick this
     * automatically every second — no need for us to push updates during the run.
     *
     * For PAUSED / DONE, we return a plain static text.
     */
    private fun remainingText(state: TimerState): androidx.wear.watchface.complications.data.ComplicationText {
        if (state.status == RunStatus.RUNNING && state.startedAtElapsedRealtime != null) {
            val targetWallMs = System.currentTimeMillis() + state.remainingInSegmentSec * 1000L
            val instant = Instant.ofEpochMilli(targetWallMs)
            return TimeDifferenceComplicationText.Builder(
                style = TimeDifferenceStyle.SHORT_DUAL_UNIT,
                countDownTimeReference = CountDownTimeReference(instant),
            ).setMinimumTimeUnit(java.util.concurrent.TimeUnit.SECONDS)
                .build()
        }

        val text = when (state.status) {
            RunStatus.PAUSED -> getString(
                R.string.complication_paused,
                DurationFormatter.formatTimerDisplay(state.remainingInSegmentSec),
            )
            RunStatus.DONE -> getString(R.string.timer_done)
            else -> DurationFormatter.formatTimerDisplay(state.remainingInSegmentSec)
        }
        return PlainComplicationText.Builder(text).build()
    }

    private fun phaseShortLabel(segment: Segment): String = when (segment) {
        Segment.INTRO -> getString(R.string.segment_intro_short)
        Segment.MAIN -> getString(R.string.segment_main_short)
        Segment.OUTRO -> getString(R.string.segment_outro_short)
        Segment.DONE -> getString(R.string.timer_done)
    }

    private fun phaseLongLabel(segment: Segment): String = when (segment) {
        Segment.INTRO -> getString(R.string.segment_intro)
        Segment.MAIN -> getString(R.string.segment_main)
        Segment.OUTRO -> getString(R.string.segment_outro)
        Segment.DONE -> getString(R.string.timer_done)
    }

    private fun monochromaticImage(): MonochromaticImage =
        MonochromaticImage.Builder(
            Icon.createWithResource(this, R.drawable.ic_timer_ongoing)
        ).build()

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        /**
         * Trigger a refresh of all instances of this complication. Cheap; safe to call
         * on every timer state event (start, pause, resume, skip, boundary, stop, done).
         */
        fun requestUpdate(context: Context) {
            try {
                ComplicationDataSourceUpdateRequester.create(
                    context.applicationContext,
                    ComponentName(context.applicationContext, TimerComplicationService::class.java),
                ).requestUpdateAll()
            } catch (_: Exception) {
                // Best-effort: complication system may be unavailable on some configurations.
            }
        }
    }
}
