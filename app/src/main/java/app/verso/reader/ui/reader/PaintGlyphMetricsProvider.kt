package app.verso.reader.ui.reader

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import app.verso.core.math.GlyphMetrics
import app.verso.core.math.GlyphMetricsProvider

/**
 * Supplies real glyph metrics from the bundled OpenType font via [Paint].
 *
 * `Paint.getTextWidths` / `getTextBounds` return pixel values rounded to integers at small
 * sizes, which is too coarse for sub-pixel fidelity, so a large reference size is used and
 * the result divided down. That keeps the em values stable regardless of display density.
 */
class PaintGlyphMetricsProvider(
    typeface: Typeface,
    private val referencePx: Float = 1000f,
) : GlyphMetricsProvider {

    private val italic = Paint().apply {
        this.typeface = typeface
        isAntiAlias = true
        textSize = referencePx
        textSkewX = -0.20f
    }

    private val upright = Paint().apply {
        this.typeface = typeface
        isAntiAlias = true
        textSize = referencePx
    }

    private val bounds = Rect()

    override fun metrics(char: String, sizeScale: Float): GlyphMetrics? {
        if (char.isEmpty()) return null
        val paint = if (char.first().isLetter()) italic else upright
        val px = referencePx * sizeScale
        paint.textSize = px
        val widths = FloatArray(char.length)
        val count = paint.getTextWidths(char, widths)
        if (count <= 0) return null
        val advance = widths.take(count).sum()
        if (advance <= 0f) return null
        paint.getTextBounds(char, 0, char.length, bounds)
        val em = px
        return GlyphMetrics(
            advanceEm = advance / em,
            inkLeftEm = bounds.left / em,
            inkRightEm = bounds.right / em,
            inkAscentEm = -bounds.top / em,
            inkDescentEm = bounds.bottom / em,
        )
    }
}
