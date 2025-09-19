package com.example.sermontimer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    onAddPreset: () -> Unit,
    onEditPreset: (Preset) -> Unit,
    onSetDefault: (String?) -> Unit,
) {
    Scaffold(
        timeText = { TimeText() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                    onSetDefault = { onSetDefault(if (it) preset.id else null) }
                )
            }

            item {
                Button(
                    onClick = onAddPreset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.add_preset))
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
    onSetDefault: (Boolean) -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = preset.title,
                    style = MaterialTheme.typography.title3
                )

                if (isDefault) {
                    Text(
                        text = stringResource(R.string.default_label),
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.primary
                    )
                }
            }

            Text(
                text = preset.formatDuration(),
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurfaceVariant
            )

            if (!isDefault) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CompactChip(
                        onClick = { onSetDefault(true) },
                        label = { Text(stringResource(R.string.set_default)) },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

private fun Preset.formatDuration(): String {
    return DurationFormatter.formatPresetDurations(introSec, mainSec, outroSec)
}

@Preview(device = "id:wear_os_large_round", showSystemUi = true)
@Composable
fun PresetListScreenPreview() {
    val mockPresets = listOf(
        Preset("1", "Sermon 5-20-5", 300, 1200, 300),
        Preset("2", "Meeting 3-15-3", 180, 900, 180),
        Preset("3", "Quick 2-10-2", 120, 600, 120)
    )

    PresetListScreen(
        presets = mockPresets,
        defaultPresetId = "1",
        onPresetSelected = {},
        onAddPreset = {},
        onEditPreset = {},
        onSetDefault = {}
    )
}
