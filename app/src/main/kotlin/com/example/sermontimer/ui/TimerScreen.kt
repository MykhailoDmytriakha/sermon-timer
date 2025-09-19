package com.example.sermontimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.example.sermontimer.R
import com.example.sermontimer.domain.model.RunStatus
import com.example.sermontimer.domain.model.Segment
import com.example.sermontimer.domain.model.TimerState
import com.example.sermontimer.domain.model.SegmentDurations
import com.example.sermontimer.domain.model.ActivePresetMeta
import com.example.sermontimer.service.TimerService
import com.example.sermontimer.util.DurationFormatter

@Composable
fun TimerScreen(
    timerState: TimerState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onSkip: () -> Unit,
    onStop: () -> Unit,
) {
    val backgroundColor = when (timerState.segment) {
        Segment.INTRO -> Color(0xFF4CAF50) // Green
        Segment.MAIN -> Color(0xFF2196F3)  // Blue
        Segment.OUTRO -> Color(0xFFFF9800) // Orange
        Segment.DONE -> Color(0xFF9C27B0)  // Purple
    }

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
                        Text(
                            text = stringResource(R.string.action_pause),
                            color = Color.White
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
                        Text(
                            text = stringResource(R.string.action_stop),
                            color = Color.White
                        )
                    }
                }

                RunStatus.PAUSED -> {
                    Button(
                        onClick = onResume,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = stringResource(R.string.action_resume),
                            color = Color.White
                        )
                    }

                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.Red.copy(alpha = 0.8f))
                    ) {
                        Text(
                            text = stringResource(R.string.action_stop),
                            color = Color.White
                        )
                    }
                }

                RunStatus.DONE -> {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(
                            text = stringResource(R.string.action_stop),
                            color = Color.White
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
        onPause = {},
        onResume = {},
        onSkip = {},
        onStop = {}
    )
}
