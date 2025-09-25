package com.example.sermontimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
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
        AmbientTimerLayout(timerState = timerState, ambientState = ambientState)
        return
    }

    val backgroundColor = when (timerState.segment) {
        Segment.INTRO -> Color(0xFF4CAF50) // Green
        Segment.MAIN -> Color(0xFF2196F3)  // Blue
        Segment.OUTRO -> Color(0xFFFF9800) // Orange
        Segment.DONE -> Color(0xFF9C27B0)  // Purple
    }

    Scaffold(
        timeText = { TimeText() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        // Segment indicator
        Text(
            text = when (timerState.segment) {
                Segment.INTRO -> stringResource(R.string.segment_intro)
                Segment.MAIN -> stringResource(R.string.segment_main)
                Segment.OUTRO -> stringResource(R.string.segment_outro)
                Segment.DONE -> stringResource(R.string.timer_done)
            },
            style = MaterialTheme.typography.caption1,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Large timer display
        Text(
            text = DurationFormatter.formatTimerDisplay(timerState.remainingInSegmentSec),
            style = MaterialTheme.typography.display1,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (timerState.status) {
                RunStatus.RUNNING -> {
                    Button(
                        onClick = onPause,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = stringResource(R.string.action_pause),
                            tint = Color.White
                        )
                    }

                    if (timerState.activePreset?.allowSkip == true) {
                        Button(
                            onClick = onSkip,
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color.White.copy(alpha = 0.2f))
                        ) {
                            Text(
                                text = stringResource(R.string.action_skip),
                                color = Color.White
                            )
                        }
                    }

                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.action_stop),
                            tint = Color.White
                        )
                    }
                }

                RunStatus.PAUSED -> {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.action_resume),
                            tint = Color.White
                        )
                    }

                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.action_stop),
                            tint = Color.White
                        )
                    }
                }

                RunStatus.DONE -> {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.action_stop),
                            tint = Color.White
                        )
                    }
                }

                else -> {
                    Text(
                        text = stringResource(R.string.timer_ready),
                        style = MaterialTheme.typography.body1,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun AmbientTimerLayout(timerState: TimerState, ambientState: AmbientUiState) {
    val textColor = Color.White
    val supportingColor = if (ambientState.isLowBit) Color.White else Color.White.copy(alpha = 0.7f)
    Scaffold(
        timeText = { TimeText() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
        val phaseLabel = when (timerState.segment) {
            Segment.INTRO -> stringResource(R.string.segment_intro)
            Segment.MAIN -> stringResource(R.string.segment_main)
            Segment.OUTRO -> stringResource(R.string.segment_outro)
            Segment.DONE -> stringResource(R.string.timer_done)
        }

        Text(
            text = phaseLabel,
            style = MaterialTheme.typography.caption1,
            color = textColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = DurationFormatter.formatTimerDisplay(timerState.remainingInSegmentSec),
            style = MaterialTheme.typography.display1,
            color = textColor,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        val statusLabel = when (timerState.status) {
            RunStatus.PAUSED -> stringResource(R.string.action_pause)
            RunStatus.DONE -> stringResource(R.string.timer_done)
            RunStatus.RUNNING -> stringResource(R.string.tile_timer_running)
            else -> stringResource(R.string.timer_ready)
        }

        Text(
            text = statusLabel,
            style = MaterialTheme.typography.caption2,
            color = supportingColor,
            textAlign = TextAlign.Center
        )
    }
    }
}

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
            soundEnabled = false
        )
    )

    TimerScreen(
        timerState = mockState,
        ambientState = AmbientUiState(),
        onPause = {},
        onResume = {},
        onSkip = {},
        onStop = {}
    )
}

@Preview(device = "id:wear_os_large_round", showSystemUi = true)
@Composable
fun TimerScreenPausedPreview() {
    val mockState = TimerState(
        status = RunStatus.PAUSED,
        segment = Segment.MAIN,
        remainingInSegmentSec = 567,
        elapsedTotalSec = 933,
        durations = SegmentDurations(300, 1200, 300),
        startedAtElapsedRealtime = null,
        activePreset = ActivePresetMeta(
            id = "test",
            durations = SegmentDurations(300, 1200, 300),
            allowSkip = true,
            soundEnabled = false
        )
    )

    TimerScreen(
        timerState = mockState,
        ambientState = AmbientUiState(),
        onPause = {},
        onResume = {},
        onSkip = {},
        onStop = {}
    )
}

@Preview(device = "id:wear_os_large_round", showSystemUi = true)
@Composable
fun TimerScreenAmbientPreview() {
    val mockState = TimerState(
        status = RunStatus.RUNNING,
        segment = Segment.INTRO,
        remainingInSegmentSec = 45,
        elapsedTotalSec = 120,
        durations = SegmentDurations(300, 1200, 300),
        startedAtElapsedRealtime = 1000L,
        activePreset = ActivePresetMeta(
            id = "test",
            durations = SegmentDurations(300, 1200, 300),
            allowSkip = true,
            soundEnabled = false
        )
    )

    TimerScreen(
        timerState = mockState,
        ambientState = AmbientUiState(isAmbient = true),
        onPause = {},
        onResume = {},
        onSkip = {},
        onStop = {}
    )
}
