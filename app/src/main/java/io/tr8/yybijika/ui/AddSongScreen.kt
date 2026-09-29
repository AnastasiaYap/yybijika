package io.tr8.yybijika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.data.MAX_SONG_LINES
import io.tr8.yybijika.ui.theme.HanziInline

/**
 * Paste a song, get it back annotated.
 *
 * The app has no way to look a song up, and that is deliberate rather than an
 * omission: the lyrics are yours, they arrive by paste from wherever you are
 * listening, and they stay on the phone. What is added here is the reading, the
 * meaning and the word boundaries — which is what a dictionary adds to a page
 * you are already holding.
 */
@Composable
fun AddSongScreen(
    title: String,
    lyrics: String,
    working: Boolean,
    error: String?,
    hasKey: Boolean,
    onTitle: (String) -> Unit,
    onLyrics: (String) -> Unit,
    onAdd: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val lines = lyrics.lines().count { it.isNotBlank() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Add a song", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Paste the lines you want to study. Each one comes back with its " +
                "reading, what it means, and every word tappable — song lines " +
                "bend their word order for the tune, so they are worth having " +
                "explained rather than guessed at.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = title,
            onValueChange = onTitle,
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = lyrics,
            onValueChange = onLyrics,
            label = { Text("Lines, one per line") },
            textStyle = HanziInline,
            minLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            if (lines == 0) "Nothing pasted yet."
            else "$lines lines" + if (lines > MAX_SONG_LINES) {
                " — that is more than $MAX_SONG_LINES, so add it in parts."
            } else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (lines > MAX_SONG_LINES) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )

        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }

        if (!hasKey) {
            Text(
                "This one needs the DeepSeek key — it is the reading and the " +
                    "meaning that have to be worked out.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenSettings) { Text("Add the key") }
        }

        Button(
            onClick = onAdd,
            enabled = !working && hasKey && title.isNotBlank() &&
                lines in 1..MAX_SONG_LINES,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (working) "Reading it…" else "Add it")
        }
    }
}
