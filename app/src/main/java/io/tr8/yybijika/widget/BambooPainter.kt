package io.tr8.yybijika.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import io.tr8.yybijika.learn.Vitality
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The plant, drawn rather than drawn from.
 *
 * A widget is RemoteViews underneath, so there is no Compose canvas to paint
 * on: the plant is rendered to a bitmap here and handed over as an image. That
 * turns out to be the better arrangement anyway — it scales to whatever cell
 * size the launcher gives us without a folder of assets at five densities.
 *
 * Bamboo because the app is already ink-on-paper and 盈盈笔记卡, and because it
 * is the one plant that grows in visible segments: a new joint for every
 * [Vitality.WORDS_PER_JOINT] words held is a progress bar that does not look
 * like one. It is also one of the Four Gentlemen of brush painting, where it
 * stands for bending without breaking, which is the right note for a learning
 * app to strike at somebody who has been away a fortnight.
 */
object BambooPainter {

    // A single hue family, lightened and desaturated towards the floor rather
    // than shifted towards brown. Wilting that goes yellow reads as dying; this
    // reads as waiting.
    private val FRESH_STALK = Color.rgb(0x4F, 0x6B, 0x45)
    private val FRESH_LEAF = Color.rgb(0x6E, 0x96, 0x5C)
    private val FADED = Color.rgb(0xB6, 0xB2, 0xA4)

    fun draw(
        width: Int,
        height: Int,
        state: Vitality.State,
        onPaper: Int,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(max(width, 1), max(height, 1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val v = state.vigour.toFloat()

        val stalkColour = blend(FADED, FRESH_STALK, v)
        val leafColour = blend(FADED, FRESH_LEAF, v)

        val pad = height * 0.08f
        val groundY = height - pad
        // A faded plant also stands a little shorter, so the change reads at a
        // glance from the home screen rather than needing to be studied.
        val topY = pad + (height - 2 * pad) * (1f - v) * 0.12f
        val stalkH = groundY - topY
        val stalkW = max(3f, width * 0.055f)
        val x = width * 0.42f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // ---- stalk -------------------------------------------------------
        paint.color = stalkColour
        paint.style = Paint.Style.FILL
        val joints = state.joints.coerceAtLeast(1)
        val segment = stalkH / joints

        // A gentle lean, stronger when faded: an upright plant looks tended,
        // a leaning one looks like it has been left alone for a few days.
        val lean = (1f - v) * width * 0.045f

        for (i in 0 until joints) {
            val bottom = groundY - segment * i
            val top = bottom - segment * 0.88f
            val tilt = lean * (i.toFloat() / joints)
            val halfW = stalkW / 2f * (1f - i * 0.03f)
            canvas.drawRoundRect(
                x + tilt - halfW, top, x + tilt + halfW, bottom,
                halfW, halfW, paint,
            )
        }

        // ---- nodes -------------------------------------------------------
        paint.color = darken(stalkColour)
        paint.strokeWidth = max(1.5f, stalkW * 0.22f)
        paint.style = Paint.Style.STROKE
        for (i in 1 until joints) {
            val y = groundY - segment * i
            val tilt = lean * (i.toFloat() / joints)
            canvas.drawLine(x + tilt - stalkW * 0.62f, y, x + tilt + stalkW * 0.62f, y, paint)
        }

        // ---- leaves ------------------------------------------------------
        // Only the upper joints carry leaves, and droop grows with absence.
        paint.style = Paint.Style.FILL
        paint.color = leafColour
        val leafy = min(joints, 4)
        for (i in 0 until leafy) {
            val jointIndex = joints - 1 - i
            val y = groundY - segment * jointIndex - segment * 0.5f
            val tilt = lean * (jointIndex.toFloat() / joints)
            val side = if (i % 2 == 0) 1f else -1f
            val len = width * (0.30f - i * 0.035f) * (0.72f + 0.28f * v)
            // Upright when fresh, hanging when not.
            val droop = (1f - v) * 0.85f
            canvas.drawPath(leaf(x + tilt, y, side * len, droop, i), paint)
            if (i < 2) {
                canvas.drawPath(leaf(x + tilt, y - segment * 0.18f, -side * len * 0.8f, droop, i + 1), paint)
            }
        }

        // ---- ground ------------------------------------------------------
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = max(1.5f, height * 0.012f)
        paint.color = withAlpha(onPaper, 48)
        canvas.drawLine(width * 0.16f, groundY, width * 0.84f, groundY, paint)

        return bmp
    }

    /**
     * One leaf: a pointed blade swept away from the stalk and bent downwards.
     *
     * [droop] runs 0 (held up) to 1 (hanging), which is the single clearest
     * signal on the whole widget that the deck has been left alone.
     */
    private fun leaf(x: Float, y: Float, length: Float, droop: Float, index: Int): Path {
        val rise = -length * 0.42f * (1f - droop * 1.9f)
        // A little variation per leaf so the plant does not look stamped.
        val wobble = sin(index * 1.7f) * 0.08f
        val tipX = x + length
        val tipY = y + rise + length * wobble
        val width = kotlin.math.abs(length) * 0.21f

        return Path().apply {
            moveTo(x, y)
            quadTo(x + length * 0.42f, y + rise * 0.55f - width, tipX, tipY)
            quadTo(x + length * 0.40f, y + rise * 0.55f + width, x, y)
            close()
        }
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun mix(a: Int, b: Int) = (a + (b - a) * k).toInt().coerceIn(0, 255)
        return Color.rgb(
            mix(Color.red(from), Color.red(to)),
            mix(Color.green(from), Color.green(to)),
            mix(Color.blue(from), Color.blue(to)),
        )
    }

    private fun darken(c: Int) = Color.rgb(
        (Color.red(c) * 0.72f).toInt(),
        (Color.green(c) * 0.72f).toInt(),
        (Color.blue(c) * 0.72f).toInt(),
    )

    private fun withAlpha(c: Int, a: Int) =
        Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
}
