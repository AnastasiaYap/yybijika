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
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.learn.Diagnosis
import io.tr8.yybijika.ui.theme.HanziInline
import io.tr8.yybijika.ui.theme.PinyinStyle
import kotlin.math.roundToInt

/**
 * What is going wrong, and where.
 *
 * Deliberately not a dashboard. Charts of a rising streak tell a learner she has
 * been diligent, which she already knows; what she cannot work out on her own is
 * which of five skills has fallen behind, which words are being lost and to
 * what, and whether a run of failures has a character in common.
 *
 * Every number here is read back out of reviews that were already recorded. The
 * screen adds no data — it asks the first question of it.
 */
@Composable
fun DiagnosisScreen(
    diagnosis: Diagnosis?,
    onOpenWord: (String) -> Unit,
    onOpenCharacter: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (diagnosis == null) {
            Text("Reading your reviews…", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        if (diagnosis.thin) {
            // Below thirty reviews any pattern is noise, and a confident wrong
            // diagnosis is worse than none: it sends the learner after a word
            // that happened to go wrong twice.
            Text("Not enough to go on yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "After about thirty reviews there is enough here to tell a " +
                    "pattern from a bad afternoon. Until then anything this " +
                    "screen said would be guesswork dressed up as a finding.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        // ---- skills --------------------------------------------------------
        Text("Where you are strongest and weakest",
            style = MaterialTheme.typography.titleMedium)
        Text(
            "Every word carries a separate schedule for each skill, so a " +
                "listening weakness cannot hide behind reading. This is the " +
                "comparison those schedules were kept apart for.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        diagnosis.skills.forEach { gap ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${gap.skill.labelZh}  ${gap.skill.label}",
                        style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${gap.words} words · ${gap.lapses} lost",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { gap.progress.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // ---- words ---------------------------------------------------------
        if (diagnosis.words.isNotEmpty()) {
            Text("The words you keep losing",
                style = MaterialTheme.typography.titleMedium)
            diagnosis.words.forEach { word ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenWord(word.hanzi) },
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(word.hanzi, style = HanziInline)
                            Text(
                                "lost ${word.lapses}×",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(word.pinyin, style = PinyinStyle,
                            color = MaterialTheme.colorScheme.primary)
                        Text(word.gloss, style = MaterialTheme.typography.bodySmall)

                        // What was actually answered beats a lapse count: the
                        // first names the confusion, the second only counts it.
                        if (word.answeredInstead.isNotEmpty()) {
                            Text(
                                "you answered ${word.answeredInstead.joinToString("、")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else if (word.confusableWith.isNotEmpty()) {
                            Text(
                                "easily lost against ${word.confusableWith.joinToString("、")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ---- characters ----------------------------------------------------
        if (diagnosis.characters.isNotEmpty()) {
            Text("A character may be behind it",
                style = MaterialTheme.typography.titleMedium)
            Text(
                "These turn up in words you lose more often than the rest. " +
                    "Worth reading the character itself rather than the words " +
                    "again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            diagnosis.characters.forEach { ch ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCharacter(ch.hanzi) },
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(ch.hanzi, style = HanziInline)
                            Text(ch.pinyin, style = PinyinStyle,
                                color = MaterialTheme.colorScheme.primary)
                            Text(
                                "${((ch.lift - 1) * 100).roundToInt()}% more often",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        ch.gloss?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium)
                        }
                        Text(
                            ch.words.joinToString("、"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (diagnosis.words.isEmpty() && diagnosis.characters.isEmpty()) {
            Text(
                "Nothing is going badly enough to name. Keep going.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
