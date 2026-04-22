package com.musicast.musicast.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun BackGestureHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS handles back navigation via native gesture recognizers
}
