package io.tr8.yybijika.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.exercise.ExerciseType
import io.tr8.yybijika.learn.Skill

/**
 * Choosing what kind of quiz to sit.
 *
 * Its own screen because a quiz and a flashcard are different activities. The
 * old study session shuffled flip-cards in with multiple choice, which meant
 * switching mental mode every few seconds — you were never quite reviewing and
 * never quite being tested. Cards is now one thing, this is the other.
 */
data class QuizSetup(
    val types: List<ExerciseType> = emptyList(),
    val selected: String? = null,          // null means Mixed
    val length: Int = 15,
    val available: Map<String, Int> = emptyMap(),
)

@Composable
fun QuizScreen(
    setup: QuizSetup,
    onSelectType: (String?) -> Unit,
    onLength: (Int) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Quiz", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Pick one kind of question, or mix them. Answers are graded and " +
                "count towards the same schedule as Review.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (setup.selected == null)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { onSelectType(null) },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Mixed", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
                Text(
                    "Every question type, chosen by whichever skill is most overdue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Skill.entries.forEach { skill ->
            val ofSkill = setup.types.filter { it.skill == skill }
            if (ofSkill.isEmpty()) return@forEach
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${skill.labelZh}  ${skill.label}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ofSkill.forEach { type ->
                    val n = setup.available[type.id] ?: 0
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (setup.selected == type.id)
                                MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                                .clickable { if (n > 0) onSelectType(type.id) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(type.label, fontWeight = FontWeight.Medium)
                                Text(
                                    describe(type.id),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // A type with nothing to draw on is shown greyed with
                            // its count rather than hidden, so the reason it is
                            // unavailable is visible instead of mysterious.
                            Text(
                                "$n",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (n > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("How many questions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 25, 40).forEach { n ->
                    FilterChip(
                        selected = setup.length == n,
                        onClick = { onLength(n) },
                        label = { Text("$n") },
                    )
                }
            }
        }

        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Start quiz")
        }
    }
}

private fun describe(id: String): String = when (id) {
    "recall_zh2gloss" -> "See the word, recall the meaning"
    "mcq_meaning" -> "Pick the meaning from four"
    "recall_gloss2zh" -> "See the meaning, pick the word"
    "type_hanzi" -> "See the meaning, type the characters"
    "listen_choose" -> "Hear a word, pick what it was"
    "cloze_example" -> "Fill the gap in a sentence"
    "build_sentence" -> "Rebuild a sentence from tiles"
    "tell_apart" -> "Two words you confuse — which fits?"
    "tone_id" -> "Hear a word, name its tones"
    "dictation" -> "Hear a sentence, rebuild it"
    "measure_word" -> "一 __ 裤子 — which one?"
    "semantic_choice" -> "Find the synonym or the opposite"
    "pinyin_to_hanzi" -> "Read the pinyin, write the characters"
    "translate_sentence" -> "Indonesian in, Chinese out, with decoy tiles"
    "character_meaning" -> "What one character contributes to a word"
    "word_building" -> "Assemble the word from its characters"
    "write_sentence" -> "Write a sentence of your own, and have it marked"
    else -> ""
}

