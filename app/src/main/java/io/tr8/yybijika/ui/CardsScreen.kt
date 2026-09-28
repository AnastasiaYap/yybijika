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
    onSwipe: (CardDeck.Swipe) -> Unit,
    onUndo: () -> Unit,
    onFilter: (CardFilter) -> Unit,
    onRevive: (Long) -> Unit,
    onSpeak: (String) -> Unit,
) {
    var confirmRetire by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        FilterBar(state, onFilter)

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
                onSpeak = onSpeak,
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
private fun FilterBar(state: CardsState, onFilter: (CardFilter) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
    onSpeak: (String) -> Unit,
    onRevive: () -> Unit,
    onSwipe: (CardDeck.Swipe) -> Unit,
) {
    val density = LocalDensity.current
    val widthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val threshold = widthPx * 0.25f

    var flipped by remember(word.id) { mutableStateOf(false) }
    // Two float animatables rather than one Offset animatable: the Offset vector
    // converter is an extension that has to be imported separately, and two
    // floats read more plainly at the call sites below.
    val dragX = remember(word.id) { Animatable(0f) }
    val dragY = remember(word.id) { Animatable(0f) }
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
        Card(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = dragX.value
                    translationY = dragY.value
                    rotationZ = dragX.value / 40f
                    rotationY = rotation
                    cameraDistance = 12f * density.density
                }
                .pointerInput(word.id) {
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
                ) { flipped = !flipped },
            colors = CardDefaults.cardColors(containerColor = tint),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { if (rotation > 90f) rotationY = 180f },
                contentAlignment = Alignment.Center,
            ) {
                if (rotation <= 90f) {
                    CardFace(word, cardState, retired, onRevive)
                } else {
                    CardDetail(word, onSpeak)
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
private fun CardDetail(word: WordBundle, onSpeak: (String) -> Unit) {
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
        TextButton(onClick = { onSpeak(word.hanzi) }) { Text("🔊") }
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
