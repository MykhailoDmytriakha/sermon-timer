package com.example.sermontimer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.sermontimer.R
import com.example.sermontimer.domain.model.ActivePresetMeta
import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.Segment
import com.example.sermontimer.domain.model.SegmentDurations
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.presentation.AmbientUiState
import com.example.sermontimer.util.DurationFormatter

@Composable
fun TimerScreen(
    timerState: TimerState,
    ambientState: AmbientUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
) {
    if (ambientState.isAmbient) {
        AmbientTimerLayout(timerState = timerState)
        return
    }

    val accent = phaseAccentColor(timerState)
    val animatedAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(durationMillis = 320),
        label = "phaseAccent",
    )

    // Progress is updated in lockstep with the seconds text — no tween, otherwise the
    // arc visibly lags behind the digits ticking down each second.
    val progress = computeProgress(timerState)

    Scaffold(timeText = { TimeText() }) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            // Outer dimmed track + bright animated arc.
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize().padding(6.dp),
                indicatorColor = animatedAccent,
                trackColor = Color.White.copy(alpha = 0.10f),
                strokeWidth = 7.dp,
            )

            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PhaseHeader(timerState = timerState, accent = animatedAccent)

                Spacer(modifier = Modifier.height(2.dp))

                BigTimerText(timerState = timerState)

                Spacer(modifier = Modifier.height(2.dp))

                SecondaryLine(timerState = timerState)

                Spacer(modifier = Modifier.height(14.dp))

                ActionRow(
                    timerState = timerState,
                    onPause = onPause,
                    onResume = onResume,
                    onSkip = onSkip,
                    onStop = onStop,
                )
            }
        }
    }
}

@Composable
private fun PhaseHeader(timerState: TimerState, accent: Color) {
    val label = when (timerState.status) {
        RunStatus.PREROLL -> stringResource(R.string.phase_preroll)
        RunStatus.OVERTIME -> stringResource(R.string.phase_overtime)
        RunStatus.PAUSED -> "${stringResource(R.string.action_pause).uppercase()} · ${segmentLabel(timerState.segment)}"
        RunStatus.DONE -> stringResource(R.string.timer_done)
        else -> segmentLabel(timerState.segment)
    }
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.caption1,
        color = accent,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        letterSpacing = 1.5.sp,
    )
}

@Composable
private fun BigTimerText(timerState: TimerState) {
    val text = when (timerState.status) {
        RunStatus.PREROLL -> DurationFormatter.formatTimerDisplay(timerState.prerollRemainingSec)
        RunStatus.OVERTIME -> "+${DurationFormatter.formatTimerDisplay(timerState.overtimeElapsedSec)}"
        RunStatus.DONE -> DurationFormatter.formatTimerDisplay(0)
        else -> DurationFormatter.formatTimerDisplay(timerState.remainingInSegmentSec)
    }
    val color = when (timerState.status) {
        RunStatus.OVERTIME -> Color(0xFFFF5252)
        RunStatus.DONE -> Color(0xFFE1BEE7)
        else -> Color.White
    }
    Text(
        text = text,
        color = color,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.display1.copy(fontSize = 52.sp),
    )
}

@Composable
private fun SecondaryLine(timerState: TimerState) {
    val text = when (timerState.status) {
        RunStatus.PREROLL -> {
            val total = DurationFormatter.formatTimerDisplay(timerState.totalSec)
            stringResource(R.string.timer_secondary_preroll_with_total, total)
        }
        RunStatus.OVERTIME -> stringResource(
            R.string.timer_secondary_overtime,
            DurationFormatter.formatTimerDisplay(timerState.overtimeMaxSec),
        )
        RunStatus.DONE -> stringResource(R.string.timer_completed)
        else -> {
            val elapsed = DurationFormatter.formatTimerDisplay(timerState.elapsedTotalSec)
            val total = DurationFormatter.formatTimerDisplay(timerState.totalSec)
            "$elapsed / $total"
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = Color.White.copy(alpha = 0.65f),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ActionRow(
    timerState: TimerState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
) {
    val pauseBtn: @Composable () -> Unit = {
        CircleAction(
            icon = Icons.Filled.Pause,
            contentDescription = stringResource(R.string.action_pause),
            onClick = onPause,
            bgColor = Color(0xFF455A64),
        )
    }
    val skipBtn: @Composable () -> Unit = {
        CircleAction(
            icon = Icons.Filled.SkipNext,
            contentDescription = stringResource(R.string.action_skip),
            onClick = onSkip,
            bgColor = Color(0xFF43A047),
        )
    }
    val resumeBtn: @Composable () -> Unit = {
        CircleAction(
            icon = Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.action_resume),
            onClick = onResume,
            bgColor = Color(0xFF43A047),
        )
    }
    val stopBtn: @Composable () -> Unit = {
        CircleAction(
            icon = Icons.Filled.Stop,
            contentDescription = stringResource(R.string.action_stop),
            onClick = onStop,
            bgColor = Color(0xFFD32F2F),
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (timerState.status) {
            RunStatus.PREROLL -> {
                // Single row: skip + stop fits comfortably (2 buttons)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) { skipBtn(); stopBtn() }
            }

            RunStatus.RUNNING -> {
                if (timerState.activePreset?.allowSkip == true) {
                    // 3 actions → top row primary (Pause + Skip), Stop separate below.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { pauseBtn(); skipBtn() }
                    stopBtn()
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { pauseBtn(); stopBtn() }
                }
            }

            RunStatus.PAUSED -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) { resumeBtn(); stopBtn() }
            }

            RunStatus.OVERTIME, RunStatus.DONE -> {
                stopBtn()
            }

            RunStatus.IDLE -> {
                Text(
                    text = stringResource(R.string.timer_ready),
                    color = Color.White,
                    style = MaterialTheme.typography.body2,
                )
            }
        }
    }
}

/**
 * Uniform 64dp circular action button — matched icon size keeps the row
 * visually consistent regardless of which icon (Pause/Skip/Stop) is rendered.
 */
@Composable
private fun CircleAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    bgColor: Color,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(backgroundColor = bgColor),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(30.dp),
        )
    }
}

@Composable
private fun segmentLabel(segment: Segment): String = when (segment) {
    Segment.INTRO -> stringResource(R.string.segment_intro)
    Segment.MAIN -> stringResource(R.string.segment_main)
    Segment.OUTRO -> stringResource(R.string.segment_outro)
    Segment.DONE -> stringResource(R.string.timer_done)
}

private fun phaseAccentColor(state: TimerState): Color = when (state.status) {
    RunStatus.PREROLL -> Color(0xFFFFB300)
    RunStatus.OVERTIME -> Color(0xFFFF5252)
    RunStatus.PAUSED -> Color(0xFFB0BEC5)
    RunStatus.DONE -> Color(0xFFCE93D8)
    else -> when (state.segment) {
        Segment.INTRO -> Color(0xFF66BB6A)
        Segment.MAIN -> Color(0xFF42A5F5)
        Segment.OUTRO -> Color(0xFFFFA726)
        Segment.DONE -> Color(0xFFCE93D8)
    }
}

private fun computeProgress(state: TimerState): Float = when (state.status) {
    RunStatus.PREROLL -> {
        val total = state.prerollTotalSec.coerceAtLeast(1)
        val done = (total - state.prerollRemainingSec).coerceIn(0, total)
        done / total.toFloat()
    }

    RunStatus.OVERTIME -> {
        val cap = state.overtimeMaxSec.coerceAtLeast(1)
        (state.overtimeElapsedSec / cap.toFloat()).coerceIn(0f, 1f)
    }

    RunStatus.DONE -> 1f

    else -> {
        val total = state.totalSec.coerceAtLeast(1)
        (state.elapsedTotalSec / total.toFloat()).coerceIn(0f, 1f)
    }
}

@Composable
private fun AmbientTimerLayout(timerState: TimerState) {
    Scaffold(timeText = { TimeText() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val phaseLabel = when (timerState.status) {
                RunStatus.PREROLL -> stringResource(R.string.phase_preroll)
                RunStatus.OVERTIME -> stringResource(R.string.phase_overtime)
                RunStatus.PAUSED -> stringResource(R.string.action_pause)
                RunStatus.DONE -> stringResource(R.string.timer_done)
                else -> segmentLabel(timerState.segment)
            }
            Text(
                text = phaseLabel.uppercase(),
                style = MaterialTheme.typography.caption2,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val text = when (timerState.status) {
                RunStatus.PREROLL -> DurationFormatter.formatTimerDisplay(timerState.prerollRemainingSec)
                RunStatus.OVERTIME -> "+${DurationFormatter.formatTimerDisplay(timerState.overtimeElapsedSec)}"
                else -> DurationFormatter.formatTimerDisplay(timerState.remainingInSegmentSec)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.display1,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ---------------------- previews ----------------------

@Preview(device = "id:wear_os_large_round", showSystemUi = true)
@Composable
fun TimerScreenRunningPreview() {
    val mockState = TimerState(
        status = RunStatus.RUNNING,
        segment = Segment.MAIN,
        remainingInSegmentSec = 1234,
        elapsedTotalSec = 366,
        durations = SegmentDurations(300, 1200, 300),
        startedAtElapsedRealtime = 1000L,
        activePreset = ActivePresetMeta(
            id = "test",
            durations = SegmentDurations(300, 1200, 300),
            allowSkip = true,
            soundEnabled = false,
        ),
    )
    TimerScreen(
        timerState = mockState,
        ambientState = AmbientUiState(),
        onPause = {}, onResume = {}, onSkip = {}, onStop = {},
    )
}

@Preview(device = "id:wear_os_large_round", showSystemUi = true)
@Composable
fun TimerScreenPrerollPreview() {
    val mockState = TimerState(
        status = RunStatus.PREROLL,
        segment = Segment.INTRO,
        remainingInSegmentSec = 300,
        elapsedTotalSec = 0,
        durations = SegmentDurations(300, 1200, 300),
        startedAtElapsedRealtime = 1000L,
        activePreset = ActivePresetMeta(
            id = "test",
            durations = SegmentDurations(300, 1200, 300),
            allowSkip = true,
            soundEnabled = false,
        ),
        prerollRemainingSec = 38,
        prerollTotalSec = 60,
        overtimeMaxSec = 300,
    )
    TimerScreen(
        timerState = mockState,
        ambientState = AmbientUiState(),
        onPause = {}, onResume = {}, onSkip = {}, onStop = {},
    )
}

@Preview(device = "id:wear_os_large_round", showSystemUi = true)
@Composable
fun TimerScreenOvertimePreview() {
    val mockState = TimerState(
        status = RunStatus.OVERTIME,
        segment = Segment.DONE,
        remainingInSegmentSec = 0,
        elapsedTotalSec = 1800,
        durations = SegmentDurations(300, 1200, 300),
        startedAtElapsedRealtime = 1000L,
        activePreset = ActivePresetMeta(
            id = "test",
            durations = SegmentDurations(300, 1200, 300),
            allowSkip = true,
            soundEnabled = false,
        ),
        overtimeElapsedSec = 73,
        overtimeMaxSec = 300,
    )
    TimerScreen(
        timerState = mockState,
        ambientState = AmbientUiState(),
        onPause = {}, onResume = {}, onSkip = {}, onStop = {},
    )
}
