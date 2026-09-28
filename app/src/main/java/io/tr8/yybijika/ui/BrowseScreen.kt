package io.tr8.yybijika.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.data.Mastery
import io.tr8.yybijika.exercise.WordBundle
import io.tr8.yybijika.learn.Scheduler
import io.tr8.yybijika.learn.Skill
import io.tr8.yybijika.ui.theme.HanziInline
import io.tr8.yybijika.ui.theme.HanziMedium
import io.tr8.yybijika.ui.theme.PinyinStyle

@Composable
fun BrowseScreen(
    words: List<WordBundle>,
    onSearch: (String) -> Unit,
    onOpen: (Long) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(query) { onSearch(query) }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search hanzi, pinyin or meaning") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(words, key = { it.id }) { word ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(word.id) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(word.hanzi, style = HanziInline)
                        Text(word.pinyin, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                        word.primaryGloss?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // An unchecked reading is worth surfacing here: it is the one
                    // piece of the card the deck is not yet sure about.
                    if (!word.pinyinVerified) {
                        Text("?", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun WordDetailScreen(word: WordBundle, mastery: Map<Skill, Mastery>, onSpeak: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onSpeak(word.hanzi) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(word.hanzi, style = HanziMedium)
            Text(word.pinyin, style = PinyinStyle, color = MaterialTheme.colorScheme.primary)
            word.hanziTrad?.let {
                Text("traditional: $it", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!word.pinyinVerified) {
                Text(
                    "reading not yet checked",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (word.glosses.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    word.glosses.forEachIndexed { i, g ->
                        Text("${i + 1}. $g", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        if (word.usageNotes.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Notes", fontWeight = FontWeight.SemiBold)
                    word.usageNotes.forEach {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (word.examples.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Examples", fontWeight = FontWeight.SemiBold)
                    word.examples.forEach { ex ->
                        Column(Modifier.clickable { onSpeak(ex.zh) }) {
                            Text(ex.zh, style = HanziInline)
                            ex.pinyin?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mastery", fontWeight = FontWeight.SemiBold)
                Skill.entries.forEach { skill ->
                    val box = mastery[skill]?.box ?: 0
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("${skill.labelZh}  ${skill.label}",
                                style = MaterialTheme.typography.bodySmall)
                            Text(
                                if (box == 0) "not started" else "box $box",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { Scheduler.mastery(box) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp),
                        )
                    }
                }
            }
        }
    }
}
