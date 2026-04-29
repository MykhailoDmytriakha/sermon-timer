package com.example.sermontimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.sermontimer.R
import com.example.sermontimer.domain.model.AppSettings
import com.example.sermontimer.util.DurationFormatter

private const val MINUTE = 60
private const val PREROLL_STEP_SEC = 5
private const val OVERTIME_STEP_SEC = 30

/**
 * Settings — adjusts preroll countdown and overtime overrun.
 *
 * UX: each setting card shows a big mm:ss readout with two full-width
 * horizontal "−1m" / "+1m" pill buttons below — the same comfortable button
 * style as the preset editor's Cancel/Save row.
 */
@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val focusRequester = rememberActiveFocusRequester()

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
                    focusRequester = focusRequester,
                ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                StepperCard(
                    label = stringResource(R.string.settings_preroll_label),
                    description = stringResource(R.string.settings_preroll_description),
                    valueSec = settings.prerollSec,
                    minSec = 0,
                    maxSec = AppSettings.MAX_PREROLL_SEC,
                    stepSec = PREROLL_STEP_SEC,
                    onChange = { newValue -> onSettingsChange(settings.copy(prerollSec = newValue)) },
                    accent = Color(0xFFFFB300),
                )
            }

            item {
                StepperCard(
                    label = stringResource(R.string.settings_overtime_label),
                    description = stringResource(R.string.settings_overtime_description),
                    valueSec = settings.overtimeMaxSec,
                    minSec = 0,
                    maxSec = AppSettings.MAX_OVERTIME_SEC,
                    stepSec = OVERTIME_STEP_SEC,
                    onChange = { newValue -> onSettingsChange(settings.copy(overtimeMaxSec = newValue)) },
                    accent = Color(0xFFFF5252),
                )
            }

            item {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(R.string.action_back))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperCard(
    label: String,
    description: String,
    valueSec: Int,
    minSec: Int,
    maxSec: Int,
    stepSec: Int,
    accent: Color,
    onChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.title3,
            color = accent,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.size(6.dp))

        // Big readout: mm:ss, or "Off" when 0.
        Text(
            text = if (valueSec == 0) {
                stringResource(R.string.settings_off)
            } else {
                DurationFormatter.formatTimerDisplay(valueSec)
            },
            style = MaterialTheme.typography.display3,
            color = MaterialTheme.colors.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.size(6.dp))

        // Two full-width pill buttons matching Cancel/Save style.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { onChange((valueSec - stepSec).coerceAtLeast(minSec)) },
                enabled = valueSec > minSec,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.secondaryButtonColors(),
            ) {
                Text(
                    text = stepLabel(stepSec, sign = -1),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = { onChange((valueSec + stepSec).coerceAtMost(maxSec)) },
                enabled = valueSec < maxSec,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stepLabel(stepSec, sign = 1),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun stepLabel(stepSec: Int, sign: Int): String {
    return if (stepSec % MINUTE == 0) {
        if (sign < 0) stringResource(R.string.settings_minus_minute)
        else stringResource(R.string.settings_plus_minute)
    } else {
        if (sign < 0) stringResource(R.string.settings_minus_seconds, stepSec)
        else stringResource(R.string.settings_plus_seconds, stepSec)
    }
}

@Preview(device = "id:wear_os_large_round", showSystemUi = true)
@Composable
fun SettingsScreenPreview() {
    SettingsScreen(
        settings = AppSettings(prerollSec = 60, overtimeMaxSec = 300),
        onSettingsChange = {},
        onBack = {},
    )
}
