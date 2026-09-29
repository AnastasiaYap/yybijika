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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ButtonDefaults
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
import io.tr8.yybijika.data.PassageLine
import io.tr8.yybijika.ui.theme.HanziInline
import io.tr8.yybijika.ui.theme.PinyinStyle

@Composable
fun ReadScreen(
    passages: List<Passage>,
    patterns: List<GrammarPattern>,
    openPassageId: Long?,
    showPinyin: Boolean,
    onSpeak: (String) -> Unit,
    onOpenWord: (String) -> Unit,
    onOpenPassage: (Long?) -> Unit,
    onShowPinyin: (Boolean) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }

    val passage = passages.firstOrNull { it.id == openPassageId }
    if (passage != null) {
        PassageReader(passage, showPinyin, onSpeak, onOpenWord, onShowPinyin) {
            onOpenPassage(null)
        }
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
                            .clickable { onOpenPassage(p.id) },
                    ) {
                        Column(Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.title, style = HanziInline)
                            Text(p.titleId, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${p.charCount} characters · ${p.minutes} min · " +
                                    "${p.questions.size} questions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            // The number that says why this one is worth
                            // reading rather than any other text in Chinese.
                            Text(
                                "practises ${p.deckWords} of your words" +
                                    if (p.tags.isEmpty()) ""
                                    else "  ·  ${p.tags.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
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
    showPinyin: Boolean,
    onSpeak: (String) -> Unit,
    onOpenWord: (String) -> Unit,
    onShowPinyin: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    var showTranslation by remember(passage.id) { mutableStateOf(false) }
    var showProse by remember(passage.id) { mutableStateOf(false) }
    var quizzing by remember(passage.id) { mutableStateOf(false) }
    var vocabRound by remember(passage.id) { mutableStateOf(false) }
    var answers by remember(passage.id) { mutableStateOf(mapOf<Int, Int>()) }

    val questions = if (vocabRound) passage.vocabulary else passage.comprehension

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
            Column(Modifier.weight(1f)) {
                Text(passage.title, style = HanziInline)
                Text(passage.titleId, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onBack) { Text("Close") }
        }

        if (!quizzing) {
            // What the passage is worth, before reading it. The word count is
            // the honest form of "built from your notes": a number the build
            // computed, not a claim in a description.
            Text(
                "${passage.charCount} characters · about ${passage.minutes} min · " +
                    "practises ${passage.deckWords} words from your notes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            passage.lines.forEach { line ->
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (line.pieces.isEmpty()) {
                        // The older short passages predate the segmenter; they
                        // are shown whole rather than not at all.
                        Text(
                            line.zh,
                            style = HanziInline,
                            modifier = Modifier.clickable { onSpeak(line.zh) },
                        )
                    } else {
                        TappableLine(line, onSpeak, onOpenWord)
                    }
                    if (showPinyin) {
                        Text(
                            line.pinyin,
                            style = PinyinStyle,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onSpeak(line.zh) },
                        )
                    }
                    if (showTranslation) {
                        Text(line.gloss, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onShowPinyin(!showPinyin) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (showPinyin) "Hide pinyin" else "Show pinyin")
                }
                OutlinedButton(
                    onClick = { showTranslation = !showTranslation },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (showTranslation) "Hide meaning" else "Show meaning")
                }
            }

            passage.summary?.let { prose ->
                OutlinedButton(
                    onClick = { showProse = !showProse },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (showProse) "Hide the whole story" else "The whole story")
                }
                if (showProse) {
                    Card(Modifier.fillMaxWidth()) {
                        Text(prose, Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (passage.footnotes.isNotEmpty()) {
                Text("Notes", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                passage.footnotes.forEach { note ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(note.phrase, style = HanziInline)
                                Text(note.pinyin, style = PinyinStyle,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Text(note.note, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (passage.comprehension.isNotEmpty()) {
                Button(
                    onClick = { quizzing = true; vocabRound = false; answers = emptyMap() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Answer ${passage.comprehension.size} questions") }
            }
            if (passage.vocabulary.isNotEmpty()) {
                OutlinedButton(
                    onClick = { quizzing = true; vocabRound = true; answers = emptyMap() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("${passage.vocabulary.size} vocabulary questions") }
            }
        } else {
            Text(
                if (vocabRound) "What the words meant here"
                else "What the passage said",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            questions.forEachIndexed { qi, question ->
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
                                colors = if (!revealed)
                                    ButtonDefaults.outlinedButtonColors()
                                else ButtonDefaults.buttonColors(
                                    containerColor = when {
                                        correct -> MaterialTheme.colorScheme.primaryContainer
                                        ci == chosen -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
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

            if (answers.size == questions.size && questions.isNotEmpty()) {
                val right = questions.indices.count { answers[it] == questions[it].answer }
                Text("$right / ${questions.size}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
            OutlinedButton(
                onClick = { quizzing = false },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Back to the passage") }
        }
    }
}

/**
 * A line of the passage with every word separately tappable.
 *
 * This is why a reading section belongs in the app rather than on paper: meeting
 * 索赔 inside a sentence and being one tap from your own card for it is the
 * moment the word stops being a list entry and starts being a word.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TappableLine(
    line: PassageLine,
    onSpeak: (String) -> Unit,
    onOpenWord: (String) -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
    ) {
        line.pieces.forEach { piece ->
            Text(
                piece.text,
                style = HanziInline,
                // Only words the deck actually has a card for are links.
                // Everything else — punctuation, and the ordinary words the
                // notes never listed — is shown but inert, because a tap that
                // opens nothing is worse than no tap at all.
                modifier = if (piece.openable) {
                    Modifier.clickable { onOpenWord(piece.text) }
                } else {
                    Modifier
                },
            )
        }
        // With the pinyin hidden there is nothing else on the row to tap, so
        // playing the line needs a control of its own.
        TextButton(
            onClick = { onSpeak(line.zh) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) { Text("🔊", style = MaterialTheme.typography.labelMedium) }
    }
}
