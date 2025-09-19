package com.example.sermontimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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

            item {
                Spacer(modifier = Modifier.height(48.dp))
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
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
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
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = preset.formatDuration(),
                        style = MaterialTheme.typography.caption1,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDefault) {
                    DefaultBadge()
                } else {
                    CompactChip(
                        onClick = { onSetDefault(true) },
                        label = { Text(stringResource(R.string.set_default)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultBadge() {
    Text(
        text = stringResource(R.string.default_label),
        style = MaterialTheme.typography.caption2,
        color = MaterialTheme.colors.primary,
        modifier = Modifier
            .background(
                color = MaterialTheme.colors.primary.copy(alpha = 0.2f),
                shape = RoundedCornerShape(50)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
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
