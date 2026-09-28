package io.tr8.yybijika.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.tr8.yybijika.exercise.WordBundle
import io.tr8.yybijika.ui.theme.HanziHero
import io.tr8.yybijika.ui.theme.HanziInline
import io.tr8.yybijika.ui.theme.PinyinStyle

/**
 * The browsing deck: one card at a time, swipe sideways, tap to flip.
 *
 * Separate from the study session on purpose. This is for leafing through
 * vocabulary without being graded — nothing here touches a schedule, so you can
 * flick through two hundred cards on the bus without wrecking tomorrow's
 * reviews. Grading lives in Review.
 */
@Composable
fun FlashcardsScreen(
    words: List<WordBundle>,
    onSpeak: (String) -> Unit,
    onStudyThis: (Long) -> Unit,
) {
    if (words.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val pager = rememberPagerState(pageCount = { words.size })
    var flippedPage by remember { mutableIntStateOf(-1) }

    // Swiping to a new card should present its front, not inherit the last
    // card's flipped state — otherwise you see answers you did not ask for.
    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.collect { flippedPage = -1 }
    }

    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (pager.currentPage + 1f) / words.size },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "${pager.currentPage + 1} / ${words.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "swipe ← →   ·   tap to flip",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
            pageSpacing = 12.dp,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
        ) { page ->
            FlipCard(
                word = words[page],
                flipped = flippedPage == page,
                onFlip = { flippedPage = if (flippedPage == page) -1 else page },
                onSpeak = onSpeak,
                onStudyThis = { onStudyThis(words[page].id) },
            )
        }
    }
}

/**
 * A card that turns over rather than cross-fading.
 *
 * The rotation is the whole point: a flip you can see keeps the front and back
 * felt as two sides of one object, which is what a paper card does and what a
 * fade does not. The back is drawn pre-rotated so its text is not mirrored.
 */
@Composable
private fun FlipCard(
    word: WordBundle,
    flipped: Boolean,
    onFlip: () -> Unit,
    onSpeak: (String) -> Unit,
    onStudyThis: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "flip",
    )
    val density = LocalConfiguration.current.densityDpi / 160f

    Card(
        Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
            .graphicsLayer {
                rotationY = rotation
                // Without this the card looks like it is shearing rather than
                // turning; the far edge has to recede.
                cameraDistance = 12f * density
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onFlip,
            ),
        // The page behind is the same paper colour, so a flat surface card has no
        // visible edge. Elevation plus a tinted face is what makes it read as an
        // object you can turn over.
        colors = CardDefaults.cardColors(
            containerColor = if (rotation > 90f) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // Past the halfway point the back becomes the visible face, so
                // it is counter-rotated to read the right way round.
                .graphicsLayer { if (rotation > 90f) rotationY = 180f },
            contentAlignment = Alignment.Center,
        ) {
            if (rotation <= 90f) CardFront(word) else CardBack(word, onSpeak, onStudyThis)
        }
    }
}

@Composable
private fun CardFront(word: WordBundle) {
    Column(
        Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(word.hanzi, style = HanziHero, textAlign = TextAlign.Center)
        if (!word.pinyinVerified) {
            Text(
                "reading not yet checked",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CardBack(word: WordBundle, onSpeak: (String) -> Unit, onStudyThis: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Text(word.hanzi, style = HanziInline)
        Text(
            word.pinyin,
            style = PinyinStyle,
            color = MaterialTheme.colorScheme.primary,
        )
        word.glosses.forEachIndexed { i, gloss ->
            Text(
                gloss,
                style = if (i == 0) MaterialTheme.typography.titleMedium
                else MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = if (i == 0) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        word.usageNotes.forEach {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        word.examples.firstOrNull()?.let { example ->
            Column(
                Modifier.padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(example.zh, style = HanziInline)
                example.pinyin?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
                example.gloss?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onSpeak(word.hanzi) }) { Text("🔊 Listen") }
        }
    }
}
