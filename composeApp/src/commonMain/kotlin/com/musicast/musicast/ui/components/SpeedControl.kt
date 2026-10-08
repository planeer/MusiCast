package com.musicast.musicast.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.musicast.musicast.ui.theme.AppIcons
import com.musicast.musicast.ui.theme.musicContainer
import com.musicast.musicast.ui.theme.onMusicContainer
import kotlin.math.roundToInt

/**
 * Speed picker. The big label always shows the user's chosen speed; when the
 * music override kicks in, a badge underneath explains the actual playback speed.
 */
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrement) {
                Icon(AppIcons.Remove, contentDescription = "Slower")
            }

            Text(
                text = "${formatSpeed(userSpeed)}x",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 56.dp),
            )

            Spacer(Modifier.width(8.dp))

            Slider(
                value = userSpeed,
                // Snap to 0.1x without drawing 24 tick marks on the track
                onValueChange = { onSpeedChanged((it * 10).roundToInt() / 10f) },
                valueRange = 0.5f..3.0f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            )

            IconButton(onClick = onIncrement) {
                Icon(AppIcons.Add, contentDescription = "Faster")
            }
        }

        AnimatedVisibility(visible = isMusicDetected && currentSpeed != userSpeed) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.musicContainer,
                contentColor = MaterialTheme.colorScheme.onMusicContainer,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(AppIcons.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Music detected · playing at ${formatSpeed(currentSpeed)}x",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

fun formatSpeed(speed: Float): String {
    val tenths = (speed * 10).roundToInt()
    return if (tenths % 10 == 0) (tenths / 10).toString() else "${tenths / 10}.${tenths % 10}"
}
