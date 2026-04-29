package com.example.sermontimer.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/**
 * Sermon Timer palette — calibrated to read well on a black AMOLED Wear screen
 * and to match the in-timer phase colours (green / blue / orange).
 */
private val SermonColors = Colors(
    primary = Color(0xFF42A5F5),           // sermon "main" blue — used for primary buttons
    primaryVariant = Color(0xFF1E88E5),
    secondary = Color(0xFFFFB300),         // amber — preroll / call-to-action accent
    secondaryVariant = Color(0xFFFFA000),
    background = Color(0xFF000000),
    surface = Color(0xFF101418),           // dark slate — slightly above black for cards
    error = Color(0xFFFF5252),
    onPrimary = Color(0xFF001022),
    onSecondary = Color(0xFF1F1300),
    onBackground = Color(0xFFE8EAED),
    onSurface = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFFB0BEC5),
    onError = Color(0xFF000000),
)

@Composable
fun SermonTimerTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colors = SermonColors,
        content = content,
    )
}
