package io.tr8.yybijika.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.data.GrammarPattern
import io.tr8.yybijika.data.Passage
import io.tr8.yybijika.ui.theme.HanziInline
import io.tr8.yybijika.ui.theme.PinyinStyle

@Composable
fun ReadScreen(
    passages: List<Passage>,
    patterns: List<GrammarPattern>,
    onSpeak: (String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    var openPassage by remember { mutableStateOf<Passage?>(null) }

    val passage = openPassage
    if (passage != null) {
        PassageReader(passage, onSpeak) { openPassage = null }
        return
    }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 },
                text = { Text("Essays") })
            Tab(selected = tab == 1, onClick = { tab = 1 },
                text = { Text("Grammar") })
        }
        when (tab) {
            0 -> LazyColumn(Modifier.fillMaxSize()) {
                items(passages, key = { it.id }) { p ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable { openPassage = p },
                    ) {
                        Column(Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.title, style = HanziInline)
                            Text(p.titleId, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "Level ${p.level} · ${p.lines.size} lines · " +
                                    "${p.questions.size} questions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            1 -> LazyColumn(Modifier.fillMaxSize()) {
                items(patterns, key = { it.id }) { pattern ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Column(Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(pattern.formula, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium)
                            pattern.note?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            pattern.examples.forEach { ex ->
                                Column(Modifier.clickable { onSpeak(ex.first) }) {
                                    Text(ex.first, style = HanziInline)
                                    ex.second?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            if (pattern.examples.isEmpty()) {
                                Text(
                                    "No example written down for this one yet.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A passage read a line at a time, translation underneath.
 *
 * Deliberately not a wall of Chinese with a separate translation below it: the
 * notes gloss line by line, and keeping that alignment is what lets you check
 * yourself one sentence at a time instead of after the fact.
 */
@Composable
private fun PassageReader(
    passage: Passage,
    onSpeak: (String) -> Unit,
    onBack: () -> Unit,
) {
    var showTranslation by remember { mutableStateOf(false) }
    var quizzing by remember { mutableStateOf(false) }
    var answers by remember { mutableStateOf(mapOf<Int, Int>()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(passage.title, style = HanziInline)
                Text(passage.titleId, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onBack) { Text("Close") }
        }

        if (!quizzing) {
            passage.lines.forEach { line ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSpeak(line.zh) },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(line.zh, style = HanziInline)
                    Text(line.pinyin, style = PinyinStyle,
                        color = MaterialTheme.colorScheme.primary)
                    if (showTranslation) {
                        Text(line.gloss, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            OutlinedButton(
                onClick = { showTranslation = !showTranslation },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (showTranslation) "Hide translation" else "Show translation")
            }

            if (passage.questions.isNotEmpty()) {
                Button(
                    onClick = { quizzing = true; answers = emptyMap() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Answer ${passage.questions.size} questions") }
            }
        } else {
            passage.questions.forEachIndexed { qi, question ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(question.q, style = MaterialTheme.typography.titleSmall)
                        question.choices.forEachIndexed { ci, choice ->
                            val chosen = answers[qi]
                            val revealed = chosen != null
                            val correct = ci == question.answer
                            OutlinedButton(
                                onClick = {
                                    if (!revealed) answers = answers + (qi to ci)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = if (!revealed) androidx.compose.material3
                                    .ButtonDefaults.outlinedButtonColors()
                                else androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        correct -> MaterialTheme.colorScheme.primaryContainer
                                        ci == chosen -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.surface
                                    },
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                ),
                            ) { Text(choice) }
                        }
                        if (answers[qi] != null) {
                            question.explain?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            val done = answers.size == passage.questions.size
            if (done) {
                val right = passage.questions.indices.count {
                    answers[it] == passage.questions[it].answer
                }
                Text("$right of ${passage.questions.size} correct",
                    style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { quizzing = false }, modifier = Modifier.fillMaxWidth()) {
                Text("Back to the passage")
            }
        }
    }
}
