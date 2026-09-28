package io.tr8.yybijika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Mute the app, from wherever it would otherwise start talking.
 *
 * Sits in the header of both Cards and the session player rather than only in
 * Settings, because the moment you want silence is the moment a word has just
 * said itself out loud on a quiet train — and by then Settings is four taps and
 * a lost place in the deck away.
 *
 * The same stored preference behind both, so muting in one is muting in the app.
 */
@Composable
fun AudioToggle(
    state: AudioState,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Without a Chinese voice there is nothing to mute, and a control that
    // changes nothing is worse than none: it reads as the reason for the
    // silence. The Home screen explains the real reason and how to fix it.
    if (!state.voiceInstalled) return

    TextButton(onClick = { onToggle(!state.enabled) }, modifier = modifier) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (state.enabled) Icons.AutoMirrored.Filled.VolumeUp
                else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (state.enabled) "Sound on, tap to mute"
                else "Muted, tap to unmute",
            )
            Text(
                if (state.enabled) "Sound" else "Muted",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
