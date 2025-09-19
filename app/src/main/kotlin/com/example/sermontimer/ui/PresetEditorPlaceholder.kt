package com.example.sermontimer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.example.sermontimer.R
import com.example.sermontimer.domain.model.Preset

/**
 * Temporary placeholder screen until full preset editor is implemented.
 * Provides navigation feedback that the Add/Edit actions are wired.
 */
@Composable
fun PresetEditorPlaceholder(
    preset: Preset?,
    onClose: () -> Unit,
) {
    Scaffold(
        timeText = { TimeText() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (preset == null) {
                    stringResource(R.string.preset_editor_title_new)
                } else {
                    stringResource(R.string.preset_editor_title_edit, preset.title)
                },
                style = MaterialTheme.typography.title2,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.preset_editor_placeholder_description),
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onClose) {
                Text(text = stringResource(R.string.action_back))
            }
        }
    }
}
