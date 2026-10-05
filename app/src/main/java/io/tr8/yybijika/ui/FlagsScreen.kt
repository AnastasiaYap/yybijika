package io.tr8.yybijika.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.learn.FlaggedCard
import io.tr8.yybijika.learn.Flags
import io.tr8.yybijika.ui.theme.HanziInline
import io.tr8.yybijika.ui.theme.PinyinStyle

/**
 * The cards you have said are wrong.
 *
 * The fix does not happen here — it happens in notes.txt, or in a question to
 * DeepSeek, and then in the next release's deck. So the one thing this screen
 * must do well is hand the whole list over in a form that can be pasted
 * somewhere else, which is what Copy is for.
 *
 * Each card stays out of sessions until it is marked fixed. That is the reason
 * "Fixed" is a deliberate tap rather than something that happens on its own: the
 * app cannot tell whether the note behind the card was ever corrected, and
 * guessing would quietly put a wrong card back into rotation.
 */
@Composable
fun FlagsScreen(
    flags: List<FlaggedCard>,
    onFixed: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    onOpenWord: (String) -> Unit,
) {
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (flags.isEmpty()) {
            Text("Nothing flagged", style = MaterialTheme.typography.titleMedium)
            Text(
                "When a card is wrong — a meaning that does not fit, a reading " +
                    "that is off, an example nobody would say — press the flag in " +
                    "the corner while you are studying. It lands here, and the " +
                    "word stops coming round until you have fixed the note.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        Text(
            "These words are set aside until you mark them fixed.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = { copy(context, Flags.report(flags)) }) {
            Text("Copy the list")
        }

        flags.forEach { flag ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(flag.hanzi, style = HanziInline)
                        Text(
                            flag.pinyin,
                            style = PinyinStyle,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(flag.reason.label, style = MaterialTheme.typography.titleSmall)
                    flag.shown?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            "The card said: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    flag.note?.takeIf { it.isNotBlank() }?.let {
                        Text("You said: $it", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onOpenWord(flag.hanzi) }) { Text("Open") }
                        TextButton(onClick = { onFixed(flag.id) }) { Text("Fixed") }
                        // Separate from Fixed because they mean opposite things
                        // about the deck: one says the note was wrong and has
                        // been corrected, the other says it was right all along.
                        TextButton(onClick = { onRemove(flag.id) }) { Text("My mistake") }
                    }
                }
            }
        }
    }
}

private fun copy(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Flagged cards", text))
}
