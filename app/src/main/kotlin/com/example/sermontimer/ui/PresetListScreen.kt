package com.example.sermontimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.wear.compose.material.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import com.example.sermontimer.R
import com.example.sermontimer.domain.model.Preset
import com.example.sermontimer.domain.model.SegmentDurations
import com.example.sermontimer.util.DurationFormatter

@Composable
fun PresetListScreen(
    presets: List<Preset>,
    defaultPresetId: String?,
    onPresetSelected: (Preset) -> Unit,
    onStartTimer: (Preset) -> Unit,
    onAddPreset: () -> Unit,
    onEditPreset: (Preset) -> Unit,
    onSetDefault: (String?) -> Unit,
) {
    // Set default confirmation state
    var showSetDefaultConfirmation by remember { mutableStateOf<Preset?>(null) }

    // Show either the main screen or set default confirmation
    if (showSetDefaultConfirmation != null) {
        // Set default confirmation dialog
        Scaffold(
            timeText = { TimeText() }
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.set_default_preset_title),
                        style = MaterialTheme.typography.title3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.set_default_preset_message, showSetDefaultConfirmation?.title ?: ""),
                        style = MaterialTheme.typography.body2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showSetDefaultConfirmation = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.secondaryButtonColors()
                        ) {
                            Text(
                                text = stringResource(R.string.set_default_preset_cancel),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Button(
                            onClick = {
                                showSetDefaultConfirmation?.let { preset ->
                                    onSetDefault(preset.id)
                                }
                                showSetDefaultConfirmation = null
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = stringResource(R.string.set_default_preset_confirm),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Main preset list screen
        Scaffold(
            timeText = { TimeText() }
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("preset-list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.presets_title),
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            items(presets) { preset ->
                PresetListItem(
                    preset = preset,
                    isDefault = preset.id == defaultPresetId,
                    onClick = { onPresetSelected(preset) },
                    onEdit = { onEditPreset(preset) },
                    onStartTimer = { onStartTimer(preset) },
                    onSetDefault = { onSetDefault(if (it) preset.id else null) },
                    onShowSetDefaultDialog = { showSetDefaultConfirmation = it }
                )
            }

            item {
                Button(
                    onClick = onAddPreset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add-preset")
                ) {
                    Text(text = stringResource(R.string.add_preset))
                }
            }

            item {
                Spacer(modifier = Modifier.height(48.dp))
            }
        }
        }
    }
}

@Composable
fun PresetListItem(
    preset: Preset,
    isDefault: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onStartTimer: () -> Unit,
    onSetDefault: (Boolean) -> Unit,
    onShowSetDefaultDialog: (Preset) -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("preset-${preset.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 5.dp, vertical = 1.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = preset.title,
                        style = MaterialTheme.typography.title3,
                        color = MaterialTheme.colors.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
//                    Spacer(modifier = Modifier.height(1.dp))
                }
            }
            Spacer(modifier = Modifier.height(5.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Edit button - round icon button (following best practices)
                Button(
                    onClick = onEdit,
                    modifier = Modifier.size(44.dp), // 44dp for optimal touch target per guidelines
                    shape = RoundedCornerShape(22.dp), // Perfect circle for familiarity
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colors.secondary ,
                        disabledBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.2f),
                        disabledContentColor = MaterialTheme.colors.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.action_edit)
                    )
                }

                // Play button
                Button(
                    onClick = onStartTimer,
                    modifier = Modifier.size(44.dp), // 44dp for optimal touch target per guidelines
                    shape = RoundedCornerShape(22.dp), // Perfect circle for familiarity
                    colors = ButtonDefaults.primaryButtonColors()
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.action_play)
                    )
                }

                // Default/ Set Default button
                if (isDefault) {
                    DefaultBadge()
                } else {
                    Button(
                        onClick = { onShowSetDefaultDialog(preset) },
                        modifier = Modifier.size(44.dp), // 44dp for optimal touch target per guidelines
                        shape = RoundedCornerShape(22.dp), // Perfect circle for familiarity
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.2f),
                            contentColor = MaterialTheme.colors.secondary,
                            disabledBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.2f),
                            disabledContentColor = MaterialTheme.colors.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.set_default),
                            modifier = Modifier.rotate(15f) // Tilt the pin icon for dynamic appearance
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultBadge() {
    Button(
        onClick = {}, // No action needed for display
        enabled = false,
        modifier = Modifier.size(44.dp), // 44dp for optimal touch target per guidelines
        shape = RoundedCornerShape(22.dp), // Perfect circle for familiarity
        colors = ButtonDefaults.buttonColors(
            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.2f),
            contentColor = MaterialTheme.colors.primary,
            disabledBackgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.2f),
            disabledContentColor = MaterialTheme.colors.primary
        )
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = stringResource(R.string.default_label)
        )
    }
}

private fun Preset.formatDuration(): String {
    return DurationFormatter.formatPresetDurations(introSec, mainSec, outroSec)
}

@Preview(device = "id:wear_os_large_round", showSystemUi = true)
@Composable
fun PresetListScreenPreview() {
    // Mock presets in unsorted order to demonstrate sorting
    val mockPresets = listOf(
        Preset("2", "Meeting 3-15-3", 180, 900, 180), // Would be second alphabetically
        Preset("1", "Sermon 5-20-5", 300, 1200, 300),  // Default preset - should be first
        Preset("3", "Quick 2-10-2", 120, 600, 120)     // Would be first alphabetically
    )

    PresetListScreen(
        presets = mockPresets,
        defaultPresetId = "1", // Preset "1" should appear first despite alphabetical order
        onPresetSelected = {},
        onStartTimer = {},
        onAddPreset = {},
        onEditPreset = {},
        onSetDefault = {}
    )
}

@Preview(device = "id:wear_os_large_round", showSystemUi = true)
@Composable
fun SetDefaultPresetDialogPreview() {
    // Preview of the set default confirmation dialog
    val mockPreset = Preset("1", "Sermon 5-20-5", 300, 1200, 300)

    Scaffold(
        timeText = { TimeText() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "Set Default Preset", // Using hardcoded string for preview
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(
                    text = "Set \"${mockPreset.title}\" as the default preset? It will appear at the top of the list.", // Using hardcoded string for preview
                    style = MaterialTheme.typography.body2,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) {
                        Text(
                            text = "Cancel",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = {},
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Set Default",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
