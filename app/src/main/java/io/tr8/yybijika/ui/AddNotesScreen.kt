package io.tr8.yybijika.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.notes.NoteParser
import io.tr8.yybijika.ui.theme.HanziInline
import io.tr8.yybijika.ui.theme.PinyinStyle

/** Where the add-notes flow has got to. */
sealed interface AddState {
    data object Editing : AddState
    data object Parsing : AddState
    data class Parsed(val drafts: List<NoteParser.Draft>) : AddState
    data class Filling(val done: Int, val total: Int) : AddState
    data class Ready(val cards: List<PendingCard>) : AddState
    data class Saved(val count: Int) : AddState
    data class Failed(val message: String, val drafts: List<NoteParser.Draft>) : AddState
}

/** A card about to be added, after any blanks were filled. */
data class PendingCard(
    val hanzi: String,
    val pinyin: String,
    val glossId: String?,
    val glossEn: String?,
    val exampleZh: String?,
    val examplePinyin: String?,
    val exampleGloss: String?,
    val notes: String?,
    val filledByModel: Boolean,
    val duplicate: Boolean,
    val warnings: List<String> = emptyList(),
    val include: Boolean = true,
)

@Composable
fun AddNotesScreen(
    text: String,
    state: AddState,
    hasKey: Boolean,
    onTextChange: (String) -> Unit,
    onParse: () -> Unit,
    onFill: () -> Unit,
    onToggleCard: (Int) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        when (state) {
            is AddState.Saved -> {
                Text("Added ${state.count} cards", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "They are in the deck now and will come up in your next session, " +
                        "scheduled like everything else.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onReset) { Text("Add more") }
            }

            else -> {
                Text("Paste your notes", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Same shorthand you already write. One word per line; a sentence " +
                        "on its own line becomes an example for the word above it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("热闹rame (positif）meriah", style = MaterialTheme.typography.bodySmall)
                        Text("旅行tour=旅游", style = MaterialTheme.typography.bodySmall)
                        Text("接menerima：接电话", style = MaterialTheme.typography.bodySmall)
                        Text("努力", style = MaterialTheme.typography.bodySmall)
                    }
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    label = { Text("Notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    enabled = state is AddState.Editing || state is AddState.Failed,
                )

                when (state) {
                    is AddState.Editing, is AddState.Failed -> {
                        if (state is AddState.Failed) {
                            Text(
                                state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Button(
                            onClick = onParse,
                            enabled = text.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Read these notes") }
                    }

                    is AddState.Parsing -> Loading("Reading…")

                    is AddState.Parsed -> ParsedList(
                        drafts = state.drafts,
                        hasKey = hasKey,
                        onFill = onFill,
                        onSave = onSave,
                        onOpenSettings = onOpenSettings,
                    )

                    is AddState.Filling -> Loading(
                        "Filling blanks… ${state.done} of ${state.total}"
                    )

                    is AddState.Ready -> ReadyList(state.cards, onToggleCard, onSave, onReset)

                    is AddState.Saved -> Unit
                }
            }
        }
    }
}

@Composable
private fun Loading(label: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator()
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ParsedList(
    drafts: List<NoteParser.Draft>,
    hasKey: Boolean,
    onFill: () -> Unit,
    onSave: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val incomplete = drafts.count { it.missing.isNotEmpty() }

    Text("${drafts.size} words found", style = MaterialTheme.typography.titleMedium)

    drafts.take(30).forEach { draft ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(draft.hanzi, style = HanziInline)
                draft.pinyin?.let { Text(it, style = PinyinStyle,
                    color = MaterialTheme.colorScheme.primary) }
                draft.glosses.forEach {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                draft.examples.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (draft.missing.isNotEmpty()) {
                    Text(
                        "missing: ${draft.missing.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
    if (drafts.size > 30) {
        Text("… and ${drafts.size - 30} more",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (incomplete > 0) {
        if (hasKey) {
            Button(onClick = onFill, modifier = Modifier.fillMaxWidth()) {
                Text("Fill the blanks in $incomplete of them")
            }
            Text(
                "DeepSeek is asked only about these words, never for new ones.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("$incomplete words are missing something",
                        fontWeight = FontWeight.Medium)
                    Text(
                        "Add a DeepSeek key in Settings and the app can fill in the " +
                            "readings, meanings and examples for you.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                }
            }
        }
    }

    OutlinedButton(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
        Text(if (incomplete > 0) "Add them as they are" else "Add these cards")
    }
}

@Composable
private fun ReadyList(
    cards: List<PendingCard>,
    onToggle: (Int) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    val selected = cards.count { it.include }
    Text("$selected of ${cards.size} will be added",
        style = MaterialTheme.typography.titleMedium)

    cards.forEachIndexed { index, card ->
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (card.include) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant
            ),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(card.hanzi, style = HanziInline)
                    TextButton(onClick = { onToggle(index) }) {
                        Text(if (card.include) "Skip" else "Include")
                    }
                }
                Text(card.pinyin, style = PinyinStyle,
                    color = MaterialTheme.colorScheme.primary)
                card.glossId?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                card.glossEn?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                card.exampleZh?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (card.filledByModel) {
                        AssistChip(onClick = {}, label = { Text("filled in") })
                    }
                    if (card.duplicate) {
                        AssistChip(onClick = {}, label = { Text("already in deck") })
                    }
                }
                card.warnings.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    Button(
        onClick = onSave,
        enabled = selected > 0,
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Add $selected cards") }
    TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Start over") }
}
