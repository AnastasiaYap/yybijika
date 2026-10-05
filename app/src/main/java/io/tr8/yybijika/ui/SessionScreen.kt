package io.tr8.yybijika.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.exercise.Exercise
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import io.tr8.yybijika.learn.FlagReason
import io.tr8.yybijika.learn.Grade
import io.tr8.yybijika.learn.SessionItem
import io.tr8.yybijika.ui.theme.HanziHero
import io.tr8.yybijika.ui.theme.HanziInline
import io.tr8.yybijika.ui.theme.HanziMedium
import io.tr8.yybijika.ui.theme.PinyinStyle

@Composable
fun SessionScreen(
    state: SessionState,
    previews: Map<Grade, Int>,
    onReveal: () -> Unit,
    onChoose: (Int) -> Unit,
    onType: (String) -> Unit,
    onTapTile: (Int) -> Unit,
    onUndoTile: () -> Unit,
    onGrade: (Grade) -> Unit,
    onSpeak: (String) -> Unit,
    onSubmit: () -> Unit,
    onFlag: (FlagReason, String?) -> Unit,
    onKeepGoing: () -> Unit,
    onStopHere: () -> Unit,
    onDone: () -> Unit,
    audio: AudioState,
    onToggleAudio: (Boolean) -> Unit,
) {
    if (state.finished) {
        SessionDone(state, onDone)
        return
    }
    val item = state.current
    if (item == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    // Three failures in a row, and the app says so instead of dealing out a
    // fourth question as though nothing had happened.
    state.breather?.let { message ->
        Breather(message, state.settled, onKeepGoing, onStopHere)
        return
    }

    var reporting by remember { mutableStateOf(false) }
    if (reporting) {
        FlagDialog(
            hanzi = item.exercise.word.hanzi,
            onDismiss = { reporting = false },
            onFlag = { reason, note -> reporting = false; onFlag(reason, note) },
        )
    }

    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${item.skill.labelZh} · ${item.skill.label}" + when {
                    // Meeting the same word twice looks like a bug unless the
                    // app says why — and saying why is also the teaching.
                    item.isRetry -> " · again"
                    item.isNew -> " · new"
                    else -> ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (item.isRetry) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // One tap to say the card itself is wrong. It lives in the
                // header rather than beside the grade buttons because it is
                // not a grade: the deck is being reported, not the learner.
                IconButton(onClick = { reporting = true }) {
                    Icon(
                        Icons.Outlined.Flag,
                        "Something's off with this card",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Muting mid-session is allowed, and takes effect on the next
                // question rather than retroactively: the one on screen has
                // already spoken, and silently re-grading it would be worse.
                AudioToggle(audio, onToggleAudio)
                Text(
                    "${state.index + 1}/${state.total}" +
                        if (state.combo >= 3) "  ×${state.combo}" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            when (val ex = item.exercise) {
                is Exercise.Flashcard -> FlashcardBody(ex, state, onReveal)
                is Exercise.MultipleChoice -> ChoiceBody(ex, state, onChoose, onSpeak)
                is Exercise.Cloze -> ClozeBody(ex, state, onChoose)
                is Exercise.Typing -> TypingBody(ex, state, onType)
                is Exercise.TileBuilder ->
                    BuilderBody(ex, state, onTapTile, onUndoTile, onSpeak)
                is Exercise.Compose -> ComposeBody(ex, state, onType, onSubmit)
            }
        }

        GradeBar(state, item, previews, onReveal, onGrade)
    }
}

// --------------------------------------------------------------------------

@Composable
private fun FlashcardBody(ex: Exercise.Flashcard, state: SessionState, onReveal: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.revealed) { onReveal() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(ex.front, style = HanziHero, textAlign = TextAlign.Center)
        if (state.revealed) {
            Text(ex.word.pinyin, style = PinyinStyle,
                color = MaterialTheme.colorScheme.primary)
            Text(ex.back, style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center)
            ex.word.usageNotes.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center)
            }
        } else {
            Text("Tap to reveal", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceBody(
    ex: Exercise.MultipleChoice,
    state: SessionState,
    onChoose: (Int) -> Unit,
    onSpeak: (String) -> Unit,
) {
    // A listening question plays itself on arrival, so the first tap is an
    // answer rather than a play button.
    LaunchedEffect(ex.prompt) {
        if (ex.speakPrompt) onSpeak(ex.prompt)
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (ex.speakPrompt) {
                if (state.revealed) {
                    Text(ex.prompt, style = HanziMedium)
                    Text(ex.promptPinyin.orEmpty(), style = PinyinStyle,
                        color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("🔊", style = HanziHero)
                }
                TextButton(onClick = { onSpeak(ex.prompt) }) { Text("Play again") }
            } else {
                Text(ex.prompt, style = HanziMedium, textAlign = TextAlign.Center)
                if (state.revealed && ex.promptPinyin != null) {
                    Text(ex.promptPinyin, style = PinyinStyle,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ex.choices.forEachIndexed { i, choice ->
                ChoiceButton(
                    text = choice,
                    state = choiceState(i, ex.answerIndex, state),
                    enabled = !state.revealed,
                    onClick = { onChoose(i) },
                )
            }
        }
    }
}

@Composable
private fun ClozeBody(ex: Exercise.Cloze, state: SessionState, onChoose: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ex.sentenceBefore, style = HanziInline)
                    Text(
                        if (state.revealed) ex.answer else "＿＿",
                        style = HanziInline,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(ex.sentenceAfter, style = HanziInline)
                }
                ex.gloss?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ex.choices.forEachIndexed { i, choice ->
                ChoiceButton(
                    text = choice,
                    state = choiceState(i, ex.answerIndex, state),
                    enabled = !state.revealed,
                    onClick = { onChoose(i) },
                )
            }
        }
    }
}

@Composable
private fun TypingBody(ex: Exercise.Typing, state: SessionState, onType: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(ex.prompt, style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center)
        Text(ex.promptPinyin.orEmpty(), style = PinyinStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(
            value = state.typed,
            onValueChange = onType,
            enabled = !state.revealed,
            singleLine = true,
            textStyle = HanziMedium,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        if (state.revealed) {
            val right = state.typed.trim() == ex.answer
            Text(
                if (right) "Correct" else "${ex.answer} · ${ex.word.pinyin}",
                style = HanziMedium,
                color = if (right) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuilderBody(
    ex: Exercise.TileBuilder,
    state: SessionState,
    onTap: (Int) -> Unit,
    onUndo: () -> Unit,
    onSpeak: (String) -> Unit,
) {
    // Dictation plays itself on arrival, like the listening choice questions.
    LaunchedEffect(ex.speak) { ex.speak?.let(onSpeak) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(ex.prompt, style = MaterialTheme.typography.titleMedium)

        // Replaying is the whole exercise, so the button is not a fallback for
        // a missed autoplay — it is how the question is actually worked.
        ex.speak?.let { sentence ->
            OutlinedButton(onClick = { onSpeak(sentence) }) { Text("Play again") }
        }

        Card(
            Modifier
                .fillMaxWidth()
                .height(90.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
        ) {
            FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.assembled.forEach { i ->
                    Text(ex.tiles[i], style = HanziInline)
                }
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ex.tiles.forEachIndexed { i, tile ->
                if (i !in state.assembled) {
                    OutlinedButton(onClick = { onTap(i) }, enabled = !state.revealed) {
                        Text(tile, style = HanziInline)
                    }
                }
            }
        }

        if (state.assembled.isNotEmpty() && !state.revealed) {
            TextButton(onClick = onUndo) { Text("Undo") }
        }

        // The sentence only; its reading and translation are the explanation,
        // which GradeBar already prints under every question.
        if (state.revealed) {
            Text(ex.solution.joinToString(""), style = HanziInline,
                color = MaterialTheme.colorScheme.primary)
        }
    }
}

// --------------------------------------------------------------------------

private enum class ChoiceVisual { IDLE, CORRECT, WRONG, MISSED }

private fun choiceState(index: Int, answer: Int, state: SessionState): ChoiceVisual = when {
    !state.revealed -> ChoiceVisual.IDLE
    index == answer && state.chosen == answer -> ChoiceVisual.CORRECT
    index == answer -> ChoiceVisual.MISSED
    index == state.chosen -> ChoiceVisual.WRONG
    else -> ChoiceVisual.IDLE
}

@Composable
private fun ChoiceButton(
    text: String,
    state: ChoiceVisual,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val background = when (state) {
        ChoiceVisual.IDLE -> scheme.surfaceVariant
        ChoiceVisual.CORRECT, ChoiceVisual.MISSED -> scheme.primaryContainer
        ChoiceVisual.WRONG -> scheme.errorContainer
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            // A missed answer is marked so a wrong choice still shows the right one.
            if (state == ChoiceVisual.MISSED) Text("←", color = scheme.primary)
        }
    }
}

@Composable
private fun GradeBar(
    state: SessionState,
    item: SessionItem,
    previews: Map<Grade, Int>,
    onReveal: () -> Unit,
    onGrade: (Grade) -> Unit,
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item.exercise.explanation?.takeIf { state.revealed }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Which exercises have a button to submit with at all.
        //
        // A multiple choice does not: tapping the answer *is* the submission,
        // and a Check button beside it is a trap — pressed without choosing, it
        // marks you wrong for a question you never answered. Writing has its
        // own. That leaves the ones where you build something up first and then
        // hand it in, plus the flashcard, which reveals rather than submits.
        val submits = when (item.exercise) {
            is Exercise.MultipleChoice, is Exercise.Cloze, is Exercise.Compose -> false
            else -> true
        }

        if (!state.revealed && submits) {
            Button(
                onClick = onReveal,
                modifier = Modifier.fillMaxWidth(),
                // Nothing built yet is nothing to mark.
                enabled = when (item.exercise) {
                    is Exercise.Typing -> state.typed.isNotBlank()
                    is Exercise.TileBuilder -> state.assembled.isNotEmpty()
                    else -> true
                },
            ) {
                Text(
                    if (item.exercise is Exercise.Flashcard) "Show answer" else "Check"
                )
            }
        } else if (state.revealed && state.selfGraded) {
            // The app already knows. Asking the learner to rate themselves a
            // second after seeing the answer collects a judgement that is
            // mostly noise — and that noise would set their review intervals.
            // A right answer has already moved on by itself; this is the
            // wrong-answer case, which waits as long as it needs to.
            Button(onClick = { onGrade(Grade.AGAIN) }, modifier = Modifier.fillMaxWidth()) {
                Text("Got it")
            }
        } else if (state.revealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradeButton("Again", previews[Grade.AGAIN], Modifier.weight(1f)) {
                    onGrade(Grade.AGAIN)
                }
                GradeButton("Hard", previews[Grade.HARD], Modifier.weight(1f)) {
                    onGrade(Grade.HARD)
                }
                GradeButton("Good", previews[Grade.GOOD], Modifier.weight(1f)) {
                    onGrade(Grade.GOOD)
                }
            }
        }
    }
}

@Composable
private fun GradeButton(
    label: String,
    days: Int?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label)
            // Showing the interval makes the choice informed rather than a guess
            // about what the app will do with it.
            Text(
                when {
                    days == null -> ""
                    days == 0 -> "now"
                    days == 1 -> "1d"
                    else -> "${days}d"
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun SessionDone(state: SessionState, onDone: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.total == 0) {
            Text("Nothing to study", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Every card is scheduled for a later day. Come back tomorrow, " +
                    "or browse the deck to read ahead.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text("Done", style = MaterialTheme.typography.headlineMedium)
            // Against what was actually answered, not against how long the
            // sitting was meant to be — somebody who stops after three
            // questions has not got twelve of them wrong.
            Text(
                if (state.answered == 0) "Nothing answered this time"
                else "${state.correct} of ${state.answered} correct",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("+${state.earned} points",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onDone) { Text("Back") }
    }
}

/**
 * The writing exercise.
 *
 * Deliberately sparse while the box is empty: a scene, an instruction and
 * somewhere to type. The meaning is behind a button rather than on the screen,
 * because a sentence written while looking at the gloss is a translation, and
 * translating is the thing this exercise exists to stop being the only skill.
 */
@Composable
private fun ComposeBody(
    ex: Exercise.Compose,
    state: SessionState,
    onType: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var showHint by remember(ex.word.id) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ex.situation, style = HanziInline)
                Text(ex.instruction, style = MaterialTheme.typography.bodyMedium)
            }
        }

        OutlinedTextField(
            value = state.typed,
            onValueChange = onType,
            enabled = !state.revealed,
            label = { Text("Your sentence") },
            textStyle = HanziInline,
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!state.revealed) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showHint = !showHint }) {
                    Text(if (showHint) "Hide the meaning" else "I need the meaning")
                }
                Button(
                    onClick = onSubmit,
                    enabled = state.typed.isNotBlank() && !state.checking,
                ) { Text(if (state.checking) "Checking…" else "Check it") }
            }
            if (showHint) {
                Text(ex.hint, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        state.critique?.let { critique ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (critique.clean) {
                        Text(
                            "Nothing to fix.",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    critique.findings.forEach { finding ->
                        Text(
                            finding.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (finding.fatal) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    critique.corrected?.let {
                        Text("Corrected", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(it, style = HanziInline,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (critique.source == "offline") {
                        Text(
                            // Saying which checks ran matters: a clean offline
                            // pass is not the same as "your Chinese is right",
                            // and letting it read that way would teach errors.
                            "Checked on the phone only — grammar was not read. " +
                                "Saved under Writing.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * "Something's off with this card."
 *
 * Five reasons rather than a free-text box alone, because a list of typed
 * complaints months old is hard to act on, where "the pinyin is wrong" sorts
 * straight into a job. The note is there for the half of cases where the reason
 * is not enough — and optional, because a flag that takes a paragraph to file
 * is a flag nobody files mid-session.
 */
@Composable
private fun FlagDialog(
    hanzi: String,
    onDismiss: () -> Unit,
    onFlag: (FlagReason, String?) -> Unit,
) {
    var reason by remember { mutableStateOf(FlagReason.MEANING) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What's wrong with $hanzi?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FlagReason.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { reason = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = reason == option, onClick = { reason = option })
                        Text(option.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("What it should say (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                Text(
                    // Said plainly, because both halves are the reason to press
                    // it: nothing is held against the word, and it stops coming
                    // back until the note is fixed.
                    "This question won't be marked, and the word is set aside " +
                        "until you fix the note.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onFlag(reason, note.takeIf { it.isNotBlank() }) }) {
                Text("Report it")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The pause after a bad run.
 *
 * Both doors are real. Stopping keeps everything the sitting earned — answers
 * are written to the schedule one at a time, so leaving early costs only the
 * questions not asked — and carrying on gets an easier question, already moved
 * to the front of what is left. What it must not do is pretend the run of
 * failures did not happen: somebody who has just missed three knows it, and an
 * encouraging line over the top would read as the app not having noticed.
 */
@Composable
private fun Breather(
    message: String,
    done: Int,
    onKeepGoing: () -> Unit,
    onStopHere: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Take a breath", style = MaterialTheme.typography.headlineSmall)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (done > 0) {
            Text(
                // What is already banked, because the decision being offered is
                // whether to stop — and nobody stops gladly without knowing
                // that the work so far is kept.
                if (done == 1) "1 question is already counted, whatever you decide."
                else "$done questions are already counted, whatever you decide.",
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onKeepGoing, modifier = Modifier.fillMaxWidth()) {
            Text("Keep going — the next one is easier")
        }
        OutlinedButton(onClick = onStopHere, modifier = Modifier.fillMaxWidth()) {
            Text("Stop for now")
        }
    }
}
