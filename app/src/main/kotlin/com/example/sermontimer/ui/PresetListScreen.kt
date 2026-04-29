package com.example.sermontimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.sermontimer.R
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.util.DurationFormatter

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun PresetListScreen(
    presets: List<Preset>,
    defaultPresetId: String?,
    onPresetSelected: (Preset) -> Unit,
    onStartTimer: (Preset) -> Unit,
    onAddPreset: () -> Unit,
    onEditPreset: (Preset) -> Unit,
    onSetDefault: (String?) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    var showSetDefaultConfirmation by remember { mutableStateOf<Preset?>(null) }

    if (showSetDefaultConfirmation != null) {
        SetDefaultConfirmation(
            presetTitle = showSetDefaultConfirmation?.title.orEmpty(),
            onConfirm = {
                showSetDefaultConfirmation?.let { preset -> onSetDefault(preset.id) }
                showSetDefaultConfirmation = null
            },
            onCancel = { showSetDefaultConfirmation = null },
        )
        return
    }

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
                .testTag("preset-list")
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
                    focusRequester = focusRequester,
                ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.presets_title),
                        style = MaterialTheme.typography.title3,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val context = LocalContext.current
                    val versionName = remember(context) {
                        runCatching {
                            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                        }.getOrNull().orEmpty()
                    }
                    if (versionName.isNotBlank()) {
                        Text(
                            text = "v$versionName",
                            style = MaterialTheme.typography.caption3,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.55f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            items(presets, key = { it.id }) { preset ->
                PresetListItem(
                    preset = preset,
                    isDefault = preset.id == defaultPresetId,
                    onClick = { onPresetSelected(preset) },
                    onEdit = { onEditPreset(preset) },
                    onStartTimer = { onStartTimer(preset) },
                    onShowSetDefaultDialog = { showSetDefaultConfirmation = it },
                )
            }

            item {
                Button(
                    onClick = onAddPreset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add-preset"),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(R.string.add_preset))
                    }
                }
            }

            item {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = stringResource(R.string.settings_button))
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetListItem(
    preset: Preset,
    isDefault: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onStartTimer: () -> Unit,
    onShowSetDefaultDialog: (Preset) -> Unit,
) {
    // Skip showing the title when it's the auto-generated "<intro>-<main>-<outro>" pattern —
    // the duration breakdown below already conveys the same information.
    val autoTitle = "${preset.introSec / 60}-${preset.mainSec / 60}-${preset.outroSec / 60}"
    val showTitle = preset.title.isNotBlank() && preset.title != autoTitle

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preset-${preset.id}"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showTitle) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (isDefault) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.default_label),
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(15f),
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                    Text(
                        text = preset.title,
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }

            // Big total time — primary identifier when there's no custom title.
            Text(
                text = DurationFormatter.formatDurationCompact(preset.totalSec),
                style = MaterialTheme.typography.title2,
                color = MaterialTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            // Segments breakdown.
            Text(
                text = stringResource(
                    R.string.preset_summary_breakdown_only,
                    DurationFormatter.formatDurationCompact(preset.introSec),
                    DurationFormatter.formatDurationCompact(preset.mainSec),
                    DurationFormatter.formatDurationCompact(preset.outroSec),
                ),
                style = MaterialTheme.typography.caption2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 1.dp),
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Action row: Edit · Play (big) · Pin — finger-friendly sizes.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onEdit,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.18f),
                        contentColor = MaterialTheme.colors.onSurface,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.action_edit),
                        modifier = Modifier.size(22.dp),
                    )
                }

                // Big play button — the primary action.
                Button(
                    onClick = onStartTimer,
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.primaryButtonColors(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.action_play),
                        modifier = Modifier.size(32.dp),
                    )
                }

                if (isDefault) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = stringResource(R.string.default_label),
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(34.dp),
                    )
                } else {
                    Button(
                        onClick = { onShowSetDefaultDialog(preset) },
                        modifier = Modifier.size(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.18f),
                            contentColor = MaterialTheme.colors.onSurface,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.set_default),
                            modifier = Modifier
                                .size(22.dp)
                                .rotate(15f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
private fun SetDefaultConfirmation(
    presetTitle: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val state = rememberScalingLazyListState()
    val focusRequester: FocusRequester = rememberActiveFocusRequester()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = state) },
    ) {
        ScalingLazyColumn(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(scrollableState = state),
                    focusRequester = focusRequester,
                ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.set_default_preset_title),
                    style = MaterialTheme.typography.title3,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.set_default_preset_message, presetTitle),
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.secondaryButtonColors(),
                    ) {
                        Text(
                            text = stringResource(R.string.set_default_preset_cancel),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.set_default_preset_confirm),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Preview(device = "id:wear_os_large_round", showSystemUi = true)
@Composable
fun PresetListScreenPreview() {
    val mockPresets = listOf(
        Preset("2", "Meeting 3-15-3", 180, 900, 180),
        Preset("1", "Sermon 5-20-5", 300, 1200, 300),
        Preset("3", "Quick 2-10-2", 120, 600, 120),
    )
    PresetListScreen(
        presets = mockPresets,
        defaultPresetId = "1",
        onPresetSelected = {},
        onStartTimer = {},
        onAddPreset = {},
        onEditPreset = {},
        onSetDefault = {},
    )
}
