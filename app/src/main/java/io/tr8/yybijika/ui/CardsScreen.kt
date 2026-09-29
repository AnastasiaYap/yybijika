package io.tr8.yybijika.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.data.CardState
import io.tr8.yybijika.data.CharacterCard
import io.tr8.yybijika.data.CharacterState
import io.tr8.yybijika.exercise.WordBundle
import io.tr8.yybijika.learn.CardDeck
import io.tr8.yybijika.learn.CardFilter
import io.tr8.yybijika.ui.theme.HanziHero
import io.tr8.yybijika.ui.theme.HanziInline
import io.tr8.yybijika.ui.theme.PinyinStyle
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * The deck you groom by hand.
 *
 * One card at a time, three gestures, no buttons to weigh up. Left is "no",
 * right is "yes", down retires the card for good. The point is speed: a hundred
 * cards sorted in two minutes, which is a different activity from the graded
 * review and is why it lives on its own screen.
 */
@Composable
fun CardsScreen(
    state: CardsState,
    characters: CharacterCardsState,
    kind: DeckKind,
    audio: AudioState,
    onSwipe: (CardDeck.Swipe) -> Unit,
    onUndo: () -> Unit,
    onFilter: (CardFilter) -> Unit,
    onRevive: (Long) -> Unit,
    onSpeak: (String) -> Unit,
    onToggleAudio: (Boolean) -> Unit,
    onDeckKind: (DeckKind) -> Unit,
    onSwipeCharacter: (CardDeck.Swipe) -> Unit,
    onUndoCharacter: () -> Unit,
    onReviveCharacter: (String) -> Unit,
) {
    if (kind == DeckKind.CHARACTERS) {
        CharacterDeck(
            state = characters,
            filter = state.filter,
            audio = audio,
            onSwipe = onSwipeCharacter,
            onUndo = onUndoCharacter,
            onFilter = onFilter,
            onRevive = onReviveCharacter,
            onSpeak = onSpeak,
            onToggleAudio = onToggleAudio,
            onDeckKind = onDeckKind,
        )
        return
    }

    var confirmRetire by remember { mutableStateOf(false) }

    // With sound on, each card says itself as it arrives, so a pass through the
    // deck trains the ear as well as the eye. It is also the reason the mute
    // button is on this screen: unattended speech is the kind you want to stop
    // immediately, not after finding Settings.
    val speaking = state.current?.first?.hanzi
    LaunchedEffect(speaking, audio.on) {
        if (audio.on && speaking != null) onSpeak(speaking)
    }

    Column(Modifier.fillMaxSize()) {
        DeckSwitch(DeckKind.WORDS, onDeckKind)
        FilterBar(state, onFilter, audio, onToggleAudio)

        if (state.order.isEmpty()) {
            EmptySlice(state.filter)
            return@Column
        }

        LinearProgressIndicator(
            progress = { (state.index.toFloat() / state.size).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${state.index + 1} / ${state.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.canUndo) {
                TextButton(onClick = onUndo) { Text("Undo") }
            }
        }

        val current = state.current
        if (current == null) {
            // The order is known but this card's word has not arrived yet.
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            Legend()
            return@Column
        }

        Box(Modifier.weight(1f)) {
            SwipeCard(
                word = current.first,
                cardState = current.second,
                retired = state.filter == CardFilter.RETIRED,
                onSpeak = onSpeak.takeIf { audio.on },
                onRevive = { onRevive(current.first.id) },
                onSwipe = { swipe ->
                    if (swipe == CardDeck.Swipe.RETIRE) confirmRetire = true
                    else onSwipe(swipe)
                },
            )
        }

        Legend()
    }

    if (confirmRetire) {
        AlertDialog(
            onDismissRequest = { confirmRetire = false },
            title = { Text("Retire this card?") },
            text = {
                Text(
                    "It leaves the rotation and stops coming up. You can bring it " +
                        "back any time from the Retired filter."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRetire = false
                    onSwipe(CardDeck.Swipe.RETIRE)
                }) { Text("Retire") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRetire = false }) { Text("Keep it") }
            },
        )
    }
}

@Composable
private fun FilterBar(
    state: CardsState,
    onFilter: (CardFilter) -> Unit,
    audio: AudioState,
    onToggleAudio: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The chips scroll; the mute button does not. Sound is the one control
        // here you may need in a hurry, so it keeps a fixed corner rather than
        // sliding off the end of a list of six filters.
        LazyRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(CardFilter.entries.toList()) { filter ->
                val n = state.counts[filter]
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilter(filter) },
                    label = { Text(if (n != null) "${filter.label}  $n" else filter.label) },
                )
            }
        }
        AudioToggle(audio, onToggleAudio)
    }
}

@Composable
private fun EmptySlice(filter: CardFilter) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            when (filter) {
                CardFilter.DUE -> "Nothing due. Everything you have swiped is resting."
                CardFilter.RETIRED -> "No retired cards. Swipe a card down to retire it."
                CardFilter.STRUGGLING -> "Nothing here — no card has been swiped left."
                CardFilter.KNOWN -> "No card has reached known yet. Swipe right a few times."
                else -> "Nothing in this slice."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * A card that follows the finger and commits when thrown far enough.
 *
 * The threshold is a quarter of the screen: far enough that a scroll or a
 * mis-touch never commits, close enough that the gesture stays cheap. Below it
 * the card springs back, which is the feedback that tells you nothing happened.
 */
@Composable
private fun SwipeCard(
    word: WordBundle,
    cardState: CardState,
    retired: Boolean,
    // Null when the app is muted, so the play button is absent rather than
    // present and dead.
    onSpeak: ((String) -> Unit)?,
    onRevive: () -> Unit,
    onSwipe: (CardDeck.Swipe) -> Unit,
) {
    SwipeShell(
        key = word.id,
        retired = retired,
        onSwipe = onSwipe,
        front = { CardFace(word, cardState, retired, onRevive) },
        back = { CardDetail(word, onSpeak) },
    )
}

/**
 * A character card in the same deck, with the same three gestures.
 *
 * Characters are groomed exactly as words are — the thing being judged is still
 * "do I know this" — so they share the gesture shell rather than getting a
 * second, slightly different one that would drift.
 */
@Composable
private fun SwipeCharacterCard(
    character: CharacterCard,
    cardState: CharacterState,
    retired: Boolean,
    onSpeak: ((String) -> Unit)?,
    onRevive: () -> Unit,
    onSwipe: (CardDeck.Swipe) -> Unit,
) {
    SwipeShell(
        key = character.hanzi,
        retired = retired,
        onSwipe = onSwipe,
        front = { CharacterFace(character, cardState, retired, onRevive) },
        back = { CharacterBack(character, onSpeak) },
    )
}

/**
 * The gesture, the tint and the flip, with nothing in it about what is on the
 * card. Extracted so that adding characters to the deck could not quietly
 * change how the destructive gesture behaves for words.
 */
@Composable
private fun SwipeShell(
    key: Any,
    retired: Boolean,
    onSwipe: (CardDeck.Swipe) -> Unit,
    front: @Composable () -> Unit,
    back: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val threshold = widthPx * 0.25f

    var flipped by remember(key) { mutableStateOf(false) }
    // Two float animatables rather than one Offset animatable: the Offset vector
    // converter is an extension that has to be imported separately, and two
    // floats read more plainly at the call sites below.
    val dragX = remember(key) { Animatable(0f) }
    val dragY = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(360),
        label = "flip",
    )

    // The card tints towards the meaning of the gesture as it moves, so the
    // outcome is legible before you let go.
    val scheme = MaterialTheme.colorScheme
    val tint = when {
        dragX.value < -threshold * 0.5f -> scheme.errorContainer
        dragX.value > threshold * 0.5f -> scheme.primaryContainer
        dragY.value > threshold * 0.5f -> scheme.surfaceVariant
        rotation > 90f -> scheme.surfaceVariant
        else -> scheme.primaryContainer
    }

    Box(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp)) {
        // The gesture lives outside the flip, and this is not a style choice.
        // Compose reports pointer deltas in the layer's own coordinate space,
        // so a card rotated 180° about Y hands back a negated x: on a flipped
        // card, swiping right read as "I don't know this". Reading the drag on
        // an untransformed parent keeps the deltas in screen space, and the
        // rotation below stays a purely visual thing.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dragX.value
                    translationY = dragY.value
                    rotationZ = dragX.value / 40f
                }
                .pointerInput(key) {
                    detectDragGestures(
                        onDrag = { change, delta ->
                            change.consume()
                            scope.launch {
                                dragX.snapTo(dragX.value + delta.x)
                                dragY.snapTo(dragY.value + delta.y)
                            }
                        },
                        onDragEnd = {
                            val x = dragX.value
                            val y = dragY.value
                            // Down wins over sideways only when it is clearly the
                            // dominant direction — retiring is destructive, so a
                            // diagonal throw must never trigger it by accident.
                            val swipe = when {
                                y > threshold && y > abs(x) -> CardDeck.Swipe.RETIRE
                                x < -threshold -> CardDeck.Swipe.UNKNOWN
                                x > threshold -> CardDeck.Swipe.KNOWN
                                else -> null
                            }
                            scope.launch { dragX.animateTo(0f, tween(220)) }
                            scope.launch { dragY.animateTo(0f, tween(220)) }
                            if (swipe != null && !retired) onSwipe(swipe)
                        },
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { flipped = !flipped }
        ) {
            Card(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density.density
                    },
                colors = CardDefaults.cardColors(containerColor = tint),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { if (rotation > 90f) rotationY = 180f },
                    contentAlignment = Alignment.Center,
                ) {
                    if (rotation <= 90f) front() else back()
                }
            }
        }
    }
}

@Composable
private fun CardFace(
    word: WordBundle,
    cardState: CardState,
    retired: Boolean,
    onRevive: () -> Unit,
) {
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(word.hanzi, style = HanziHero, textAlign = TextAlign.Center)
        StateChip(cardState)
        if (retired) {
            TextButton(onClick = onRevive) { Text("Bring it back") }
        } else {
            Text(
                "tap to flip",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CardDetail(word: WordBundle, onSpeak: ((String) -> Unit)?) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(word.hanzi, style = HanziInline)
        Text(word.pinyin, style = PinyinStyle, color = MaterialTheme.colorScheme.primary)
        word.glosses.forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        word.usageNotes.firstOrNull()?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        word.examples.firstOrNull()?.let { ex ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ex.zh, style = HanziInline, textAlign = TextAlign.Center)
                ex.gloss?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        onSpeak?.let { speak ->
            TextButton(onClick = { speak(word.hanzi) }) { Text("🔊") }
        }
    }
}

@Composable
private fun StateChip(state: CardState) {
    val (label, colour) = when (state.state) {
        CardState.STATE_STRUGGLING -> "struggling" to MaterialTheme.colorScheme.error
        CardState.STATE_KNOWN -> "known" to MaterialTheme.colorScheme.primary
        CardState.STATE_RETIRED -> "retired" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> if (state.seenCount == 0) "new" to MaterialTheme.colorScheme.onSurfaceVariant
        else "learning" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = colour)
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LegendItem("←  don't know", MaterialTheme.colorScheme.error)
        LegendItem("↓  retire", MaterialTheme.colorScheme.onSurfaceVariant)
        LegendItem("know  →", MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LegendItem(text: String, colour: Color) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = colour)
}

@Composable
private fun CharacterFace(
    character: CharacterCard,
    cardState: CharacterState,
    retired: Boolean,
    onRevive: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(character.hanzi, style = HanziHero, textAlign = TextAlign.Center)
        // The count is the reason this character is in the deck at all, so it
        // sits on the front: 院 in eleven words is worth more attention than a
        // character you will meet twice.
        Text(
            "in ${character.wordCount} of your words",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (cardState.seenCount > 0) {
            Text(
                "${cardState.state} · box ${cardState.box}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (retired) {
            TextButton(onClick = onRevive) { Text("Bring back") }
        } else {
            Text(
                "tap to flip",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CharacterBack(character: CharacterCard, onSpeak: ((String) -> Unit)?) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(character.hanzi, style = HanziInline)
        Text(character.pinyin, style = PinyinStyle, color = MaterialTheme.colorScheme.primary)
        character.gloss?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        character.glossEn?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        onSpeak?.let { speak ->
            TextButton(onClick = { speak(character.hanzi) }) { Text("🔊") }
        }
    }
}

/**
 * Words or characters.
 *
 * Two chips rather than a tab row: this is a choice of what you are looking at,
 * not a place you navigate to, and the filters below it apply to either.
 */
@Composable
private fun DeckSwitch(current: DeckKind, onSelect: (DeckKind) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DeckKind.entries.forEach { kind ->
            FilterChip(
                selected = current == kind,
                onClick = { onSelect(kind) },
                label = { Text(kind.label) },
            )
        }
    }
}

/**
 * The character deck: the same screen, grooming the parts instead of the words.
 *
 * 529 characters against 1,173 words, and the ones at the front appear in a
 * dozen words each — which is why this deck is worth a pass of its own rather
 * than being folded in among the words as extra cards.
 */
@Composable
private fun CharacterDeck(
    state: CharacterCardsState,
    filter: CardFilter,
    audio: AudioState,
    onSwipe: (CardDeck.Swipe) -> Unit,
    onUndo: () -> Unit,
    onFilter: (CardFilter) -> Unit,
    onRevive: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onToggleAudio: (Boolean) -> Unit,
    onDeckKind: (DeckKind) -> Unit,
) {
    var confirmRetire by remember { mutableStateOf(false) }

    val speaking = state.current?.first?.hanzi
    LaunchedEffect(speaking, audio.on) {
        if (audio.on && speaking != null) onSpeak(speaking)
    }

    Column(Modifier.fillMaxSize()) {
        DeckSwitch(DeckKind.CHARACTERS, onDeckKind)
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CardFilter.entries.toList()) { entry ->
                    val n = state.counts[entry]
                    FilterChip(
                        selected = filter == entry,
                        onClick = { onFilter(entry) },
                        label = { Text(if (n != null) "${entry.label}  $n" else entry.label) },
                    )
                }
            }
            AudioToggle(audio, onToggleAudio)
        }

        if (state.order.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing in this slice of the character deck.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }

        LinearProgressIndicator(
            progress = { (state.index.toFloat() / state.size).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${state.index + 1} / ${state.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.canUndo) TextButton(onClick = onUndo) { Text("Undo") }
        }

        val current = state.current
        if (current == null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            Legend()
            return@Column
        }

        Box(Modifier.weight(1f)) {
            SwipeCharacterCard(
                character = current.first,
                cardState = current.second,
                retired = filter == CardFilter.RETIRED,
                onSpeak = onSpeak.takeIf { audio.on },
                onRevive = { onRevive(current.first.hanzi) },
                onSwipe = { swipe ->
                    if (swipe == CardDeck.Swipe.RETIRE) confirmRetire = true
                    else onSwipe(swipe)
                },
            )
        }

        Legend()
    }

    if (confirmRetire) {
        AlertDialog(
            onDismissRequest = { confirmRetire = false },
            title = { Text("Retire this character?") },
            text = {
                Text(
                    "It leaves the rotation and stops coming up. The words built " +
                        "from it are not affected, and you can bring it back any " +
                        "time from the Retired filter."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRetire = false
                    onSwipe(CardDeck.Swipe.RETIRE)
                }) { Text("Retire") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRetire = false }) { Text("Keep it") }
            },
        )
    }
}

