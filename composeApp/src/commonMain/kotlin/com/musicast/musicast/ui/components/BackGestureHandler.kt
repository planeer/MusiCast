package com.musicast.musicast.ui.components

import androidx.compose.runtime.Composable

@Composable
expect fun BackGestureHandler(enabled: Boolean, onBack: () -> Unit)
