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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.data.CharacterCard
import io.tr8.yybijika.ui.theme.HanziInline
import io.tr8.yybijika.ui.theme.HanziMedium
import io.tr8.yybijika.ui.theme.PinyinStyle

/**
 * One character, and every word in the deck built from it.
 *
 * The list underneath is not decoration — it is the argument. A meaning like
 * "large institution" is a claim, and 医院 · 学院 · 商学院 · 工学院 · 住院 · 出院
 * sitting under it is the evidence, which is also the moment the four words stop
 * being four things to remember.
 */
@Composable
fun CharacterScreen(
    character: CharacterCard,
    words: List<Triple<Long, String, String?>>,
    onSpeak: (String) -> Unit,
    onOpenWord: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onSpeak(character.hanzi) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(character.hanzi, style = HanziMedium)
            // Two readings where the deck genuinely uses two: 长 is cháng in
            // 长城 and zhǎng in 长大, and showing only one makes the other wrong.
            Text(character.pinyin, style = PinyinStyle,
                color = MaterialTheme.colorScheme.primary)
        }

        character.gloss?.let { gloss ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(gloss, style = MaterialTheme.typography.bodyLarge)
                    character.glossEn?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (character.gloss == null) {
            Text(
                "This character appears in only one word in your deck, so there " +
                    "is nothing it explains on its own yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (words.isNotEmpty()) {
            Text(
                if (words.size == 1) "In one word of yours"
                else "In ${words.size} words of yours",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                words.forEach { (_, hanzi, gloss) ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenWord(hanzi) },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(hanzi, style = HanziInline, fontWeight = FontWeight.Medium)
                            Text(
                                gloss.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
