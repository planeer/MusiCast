package com.musicast.musicast.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SpeedControl(
    currentSpeed: Float,
    userSpeed: Float,
    isMusicDetected: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onSpeedChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val speedColor = if (isMusicDetected) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        IconButton(onClick = onDecrement) {
            Text(
                text = "\u2212",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Text(
            text = "${formatSpeed(currentSpeed)}x",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = speedColor,
        )

        Spacer(Modifier.width(8.dp))

        Slider(
            value = userSpeed,
            onValueChange = { onSpeedChanged(it) },
            valueRange = 0.5f..3.0f,
            steps = 24,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )

        IconButton(onClick = onIncrement) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun formatSpeed(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) {
        speed.toInt().toString()
    } else {
        ((speed * 10).toInt().toFloat() / 10).toString()
    }
}
