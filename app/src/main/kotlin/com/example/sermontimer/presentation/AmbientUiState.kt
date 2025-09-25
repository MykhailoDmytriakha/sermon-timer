package com.example.sermontimer.presentation

data class AmbientUiState(
    val isAmbient: Boolean = false,
    val isLowBit: Boolean = false,
    val requiresBurnInProtection: Boolean = false,
)
