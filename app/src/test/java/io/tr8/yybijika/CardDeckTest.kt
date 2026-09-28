package io.tr8.yybijika

import io.tr8.yybijika.data.CardState
import io.tr8.yybijika.data.CharacterState
import io.tr8.yybijika.learn.CardDeck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grooming ladder, which words and characters now share.
 *
 * Sharing it is the point of these tests: the two decks are stored in different
 * tables and rendered by different composables, so nothing but a test stops one
 * of them quietly acquiring different intervals from the other.
 */
class CardDeckTest {

    private val today = 20_000L

    @Test
    fun `unknown drops to the bottom and comes back the same day`() {
        val card = CardState(wordId = 1, box = 5, state = CardState.STATE_KNOWN)
        val after = CardDeck.apply(card, CardDeck.Swipe.UNKNOWN, today)
        assertEquals(CardState.STATE_STRUGGLING, after.state)
        assertEquals(0, after.box)
        // Same day on purpose: the value of the gesture is that the word you
        // just failed reappears while the failure is still fresh.
        assertEquals(today, after.dueAt)
        assertEquals(1, after.seenCount)
    }

    /**
     * One confident glance is weak evidence, so "known" is a status the deck
     * withholds until the claim has been made a few times.
     */
    @Test
    fun `known advances one box and only promotes at box three`() {
        var card = CardState(wordId = 1)
        repeat(2) { card = CardDeck.apply(card, CardDeck.Swipe.KNOWN, today) }
        assertEquals(2, card.box)
        assertEquals(CardState.STATE_LEARNING, card.state)

        card = CardDeck.apply(card, CardDeck.Swipe.KNOWN, today)
        assertEquals(3, card.box)
        assertEquals(CardState.STATE_KNOWN, card.state)
    }

    @Test
    fun `intervals only ever grow and stop at the top box`() {
        var card = CardState(wordId = 1)
        var previous = -1
        repeat(CardDeck.MAX_BOX + 4) {
            card = CardDeck.apply(card, CardDeck.Swipe.KNOWN, today)
            val interval = (card.dueAt - today).toInt()
            assertTrue("interval went backwards at box ${card.box}", interval >= previous)
            previous = interval
        }
        assertEquals(CardDeck.MAX_BOX, card.box)
    }

    @Test
    fun `retiring keeps the box so a revived card remembers where it was`() {
        val card = CardState(wordId = 1, box = 4, state = CardState.STATE_KNOWN)
        val retired = CardDeck.apply(card, CardDeck.Swipe.RETIRE, today)
        assertEquals(CardState.STATE_RETIRED, retired.state)
        assertEquals(4, retired.box)
        // Retiring is not a review, so it does not count as having seen it.
        assertEquals(card.seenCount, retired.seenCount)

        val revived = CardDeck.revive(retired, today)
        // One box down and due now: you revived it because you had forgotten it.
        assertEquals(3, revived.box)
        assertEquals(today, revived.dueAt)
        assertEquals(CardState.STATE_KNOWN, revived.state)
    }

    /**
     * Characters climb exactly the same ladder. Written against the two public
     * entry points rather than against [CardDeck.step] directly, because the
     * risk being guarded is that one of the wrappers diverges.
     */
    @Test
    fun `characters and words move identically`() {
        for (swipe in CardDeck.Swipe.entries) {
            val word = CardDeck.apply(
                CardState(wordId = 7, box = 2, state = CardState.STATE_LEARNING),
                swipe, today,
            )
            val character = CardDeck.apply(
                CharacterState(hanzi = "院", box = 2, state = CardState.STATE_LEARNING),
                swipe, today,
            )
            assertEquals("$swipe state", word.state, character.state)
            assertEquals("$swipe box", word.box, character.box)
            assertEquals("$swipe dueAt", word.dueAt, character.dueAt)
            assertEquals("$swipe seenCount", word.seenCount, character.seenCount)
        }
    }

    @Test
    fun `reviving a character behaves like reviving a word`() {
        val word = CardDeck.revive(
            CardState(wordId = 7, box = 5, state = CardState.STATE_RETIRED), today,
        )
        val character = CardDeck.revive(
            CharacterState(hanzi = "院", box = 5, state = CardState.STATE_RETIRED), today,
        )
        assertEquals(word.state, character.state)
        assertEquals(word.box, character.box)
        assertEquals(word.dueAt, character.dueAt)
    }

    @Test
    fun `a card at the bottom box cannot be revived below it`() {
        val revived = CardDeck.revive(
            CardState(wordId = 1, box = 0, state = CardState.STATE_RETIRED), today,
        )
        assertEquals(0, revived.box)
    }
}
