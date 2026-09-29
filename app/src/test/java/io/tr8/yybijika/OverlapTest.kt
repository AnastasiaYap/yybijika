package io.tr8.yybijika

import io.tr8.yybijika.exercise.Overlap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A wrong answer has to be distinguishable, not merely different.
 *
 * The case this exists for: 逛街 was offered with 旅行 "bepergian, jalan-jalan"
 * as a wrong answer while its own gloss read "jalan-jalan belanja". Both were
 * defensible, one was marked wrong, and the lesson taught was false.
 */
class OverlapTest {

    @Test
    fun `glosses sharing a content word collide`() {
        assertTrue(Overlap.collide("jalan-jalan belanja", "bepergian, jalan-jalan"))
        assertTrue(Overlap.collide("to window-shop, to stroll the shops", "to shop for food"))
    }

    @Test
    fun `unrelated glosses do not collide`() {
        assertFalse(Overlap.collide("cuci mata, lihat-lihat di pertokoan",
            "pekerjaan rumah, tugas"))
        assertFalse(Overlap.collide("ramai, meriah", "bepergian, jalan-jalan"))
    }

    /**
     * Grammar words are shared by half the deck's glosses. Counting them would
     * reject almost every distractor and leave the questions unbuildable.
     */
    @Test
    fun `ordinary words are not enough to collide`() {
        assertFalse(Overlap.collide("pergi ke kota", "naik ke atas"))
        assertFalse(Overlap.collide("to be at home", "to go to work"))
        // Different activities, no shared content word: a fair pair of choices.
        assertFalse(Overlap.collide("to go shopping", "to go travelling"))
    }

    @Test
    fun `an empty gloss collides with nothing`() {
        assertFalse(Overlap.collide("", "jalan-jalan"))
        assertFalse(Overlap.collide("di ke dari", "di ke dari"))
    }
}
