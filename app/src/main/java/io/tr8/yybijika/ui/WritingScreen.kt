package io.tr8.yybijika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.data.Composition
import io.tr8.yybijika.ui.theme.HanziInline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Everything she has written, newest first.
 *
 * The only place in the app that holds Chinese she produced rather than
 * recognised. Worth keeping for its own sake: a year of these is the honest
 * record of what she can say, which no number of review streaks can stand in
 * for. It is also where sentences wait when there was no network to mark them.
 */
@Composable
fun WritingScreen(compositions: List<Composition>) {
    if (compositions.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Nothing written yet.",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Quiz → Write a sentence. It is the only exercise here that asks " +
                    "what you would actually say, which is why it is the one that " +
                    "tells you what you can and cannot do yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val when_ = SimpleDateFormat("d MMM", Locale.getDefault())

    LazyColumn(Modifier.fillMaxSize()) {
        items(compositions, key = { it.id }) { c ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Column(
                    Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            c.hanzi,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            when_.format(Date(c.writtenAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(c.prompt, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(c.text, style = HanziInline)

                    c.corrected?.let {
                        Text("Corrected", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it, style = HanziInline,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    c.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (c.correct == null) {
                        // An unmarked sentence is not a passed one, and the list
                        // has to say which it is or it becomes a wall of
                        // apparent successes.
                        Text(
                            "Not marked — written without a connection.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
