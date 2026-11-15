package app.verso.mathgolden

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import app.verso.core.latex.MathFontStyle
import app.verso.core.latex.MathNode
import app.verso.core.latex.SymbolCategory
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Rasterizes a [MathNode] tree to a [Bitmap] and segments it into glyph boxes, so the
 * harness can compare our output against a TeX golden.
 *
 * **This is a stopgap for M1–M2.** It mirrors the geometry rules currently hard-coded in
 * `LatexRenderer` (fraction child scale, big-operator scale, operator padding) so the
 * measurement reflects what the app actually draws. M3 replaces it with the engine's own
 * measurement tree, at which point those constants are driven by the font's MATH table.
 *
 * Glyph boxes are produced by finding contiguous column runs of non-background pixels.
 * Because the TeX goldens are per-word (`pdftotext -bbox`), the same segmentation is used
 * on both sides at comparison time where integers are involved.
 *
 * Coordinates: returns [GoldenGlyph] values in TeX points, using the same convention as
 * the golden loader, so [MathComparator] compares like with like.
 */
object OursRaster {

    /** Raster scale: pixels per TeX point. */
    private const val PX_PER_PT = 8f

    /**
     * Render [node] at [baseFontPt] and return per-glyph boxes in TeX points.
     *
     * [typeface] defaults to the platform serif, matching the pre-M2 renderer.
     */
    fun measure(
        node: MathNode,
        baseFontPt: Double,
        isDisplay: Boolean,
        typeface: Typeface? = null,
    ): List<GoldenGlyph> {
        val linePt = baseFontPt * 1.15
        val heightPt = linePt * 4.0
        val widthPt = 240.0
        val bmp = Bitmap.createBitmap(
            (widthPt * PX_PER_PT).roundToInt(),
            (heightPt * PX_PER_PT).roundToInt(),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val baselineYpt = heightPt / 2.0
        val cursor = floatArrayOf(4f)
        val drawn = mutableListOf<DrawnGlyph>()

        drawNode(canvas, node, cursor, baselineYpt.toFloat(), baseFontPt.toFloat(), isDisplay, typeface, drawn)

        return segmentAndLabel(bmp, drawn)
    }

    /** A glyph we asked the canvas to draw, with the horizontal span it occupies. */
    private data class DrawnGlyph(
        val char: String,
        val xMinPt: Float,
        val xMaxPt: Float,
    )


    /** Mirror of `MathNodeView`'s layout rules for the subset needed by the harness. */
    private fun drawNode(
        canvas: Canvas,
        node: MathNode,
        cursor: FloatArray,
        baselinePt: Float,
        fontPt: Float,
        isDisplay: Boolean,
        typeface: Typeface?,
        drawn: MutableList<DrawnGlyph>,
    ) {
        when (node) {
            is MathNode.Row -> {
                val classes = app.verso.core.math.MathClassClassifier.resolveBinaries(
                    node.children.map { app.verso.core.math.MathClassClassifier.classify(it) }
                )
                val level = if (isDisplay) {
                    app.verso.core.math.MathStyleLevel.Display
                } else {
                    app.verso.core.math.MathStyleLevel.Text
                }
                node.children.forEachIndexed { index, child ->
                    if (index > 0) {
                        val gap = app.verso.core.math.MathSpacing.gapEm(
                            classes[index - 1], classes[index], level,
                        )
                        if (gap > 0f) cursor[0] += fontPt * gap
                    }
                    drawNode(canvas, child, cursor, baselinePt, fontPt, isDisplay, typeface, drawn)
                }
            }
            is MathNode.Symbol -> drawSymbol(canvas, node, cursor, baselinePt, fontPt, typeface, drawn)
            is MathNode.BigOp -> drawBigOp(canvas, node, cursor, baselinePt, fontPt, isDisplay, typeface, drawn)
            is MathNode.Fraction -> {
                // Renderer: childScale 0.88 display / 0.76 otherwise; bar under numerator.
                val childScale = if (isDisplay && !node.compact) 0.88f else 0.76f
                val childPt = (fontPt * childScale).coerceAtLeast(10f)
                val startX = cursor[0]
                drawNode(canvas, node.numerator, cursor, baselinePt, childPt, false, typeface, drawn)
                val numEndX = cursor[0]
                var denCursor = floatArrayOf(startX)
                val denBaseline = baselinePt + fontPt * 0.95f
                drawNode(canvas, node.denominator, denCursor, denBaseline, childPt, false, typeface, drawn)
                val barY = baselinePt - fontPt * 0.28f
                val barStart = startX - fontPt * 0.02f
                val barEnd = maxOf(numEndX, denCursor[0]) + fontPt * 0.02f
                val paint = Paint().apply { color = Color.BLACK; strokeWidth = (fontPt * 0.05f) * PX_PER_PT }
                canvas.drawLine(barStart * PX_PER_PT, barY * PX_PER_PT, barEnd * PX_PER_PT, barY * PX_PER_PT, paint)
                cursor[0] = maxOf(numEndX, denCursor[0]) + fontPt * 0.1f
            }
            is MathNode.Sqrt -> {
                drawSymbolText(canvas, "√", cursor, baselinePt, fontPt, false, false, typeface, drawn)
                drawNode(canvas, node.radicand, cursor, baselinePt, fontPt, isDisplay, typeface, drawn)
            }
            is MathNode.SubSup -> {
                drawNode(canvas, node.base, cursor, baselinePt, fontPt, isDisplay, typeface, drawn)
                val scriptScale = if (isDisplay) 0.7f else 0.7f
                val scriptPt = (fontPt * scriptScale).coerceAtLeast(6f)
                if (node.superscript != null) {
                    drawNode(canvas, node.superscript, cursor, baselinePt - fontPt * 0.45f, scriptPt, false, typeface, drawn)
                }
                if (node.subscript != null) {
                    drawNode(canvas, node.subscript, cursor, baselinePt + fontPt * 0.25f, scriptPt, false, typeface, drawn)
                }
            }
            is MathNode.Delimited -> {
                drawSymbolText(canvas, node.left, cursor, baselinePt, fontPt, false, false, typeface, drawn)
                drawNode(canvas, node.content, cursor, baselinePt, fontPt, isDisplay, typeface, drawn)
                drawSymbolText(canvas, node.right, cursor, baselinePt, fontPt, false, false, typeface, drawn)
            }
            is MathNode.Accent -> drawNode(canvas, node.base, cursor, baselinePt, fontPt, isDisplay, typeface, drawn)
            is MathNode.Matrix -> {
                for (row in node.rows) for (cell in row) {
                    drawNode(canvas, cell, cursor, baselinePt, fontPt, isDisplay, typeface, drawn)
                }
            }
            is MathNode.Styled -> drawNode(canvas, node.content, cursor, baselinePt, fontPt, isDisplay, typeface, drawn)
            is MathNode.Boxed -> drawNode(canvas, node.content, cursor, baselinePt, fontPt, isDisplay, typeface, drawn)
            is MathNode.Space -> cursor[0] += node.em.coerceAtLeast(0f) * fontPt
        }
    }

    private fun drawSymbol(
        canvas: Canvas,
        node: MathNode.Symbol,
        cursor: FloatArray,
        baselinePt: Float,
        fontPt: Float,
        typeface: Typeface?,
        drawn: MutableList<DrawnGlyph>,
    ) {
        // Inter-atom spacing is applied by the enclosing Row from the TeX spacing table,
        // mirroring what the renderer does.
        val italic = node.category == SymbolCategory.VARIABLE
        drawSymbolText(canvas, node.text, cursor, baselinePt, fontPt, italic, false, typeface, drawn)
    }

    private fun drawBigOp(
        canvas: Canvas,
        node: MathNode.BigOp,
        cursor: FloatArray,
        baselinePt: Float,
        fontPt: Float,
        isDisplay: Boolean,
        typeface: Typeface?,
        drawn: MutableList<DrawnGlyph>,
    ) {
        val scale = when {
            node.isLimitWord -> 1.0f
            node.isIntegral && isDisplay -> 1.65f
            node.isIntegral -> 1.25f
            isDisplay -> 1.45f
            else -> 1.15f
        }
        cursor[0] += fontPt * 0.11f
        drawSymbolText(canvas, node.symbol, cursor, baselinePt, fontPt * scale, node.isIntegral, false, typeface, drawn)
        cursor[0] += fontPt * 0.11f
    }

    private fun drawSymbolText(
        canvas: Canvas,
        text: String,
        cursor: FloatArray,
        baselinePt: Float,
        fontPt: Float,
        italic: Boolean,
        typefaceIsFallback: Boolean,
        typeface: Typeface?,
        drawn: MutableList<DrawnGlyph>,
    ) {
        val paint = Paint().apply {
            this.color = Color.BLACK
            textSize = fontPt * PX_PER_PT
            isAntiAlias = true
            this.typeface = typeface ?: Typeface.SERIF
            isFakeBoldText = false
            textSkewX = if (italic) -0.20f else 0f
        }
        val advancePt = paint.measureText(text) / PX_PER_PT
        if (text.isNotEmpty()) {
            drawn += DrawnGlyph(text, cursor[0], cursor[0] + advancePt)
        }
        canvas.drawText(text, cursor[0] * PX_PER_PT, baselinePt * PX_PER_PT, paint)
        cursor[0] += advancePt
    }

    /**
     * Segment the bitmap into ink runs and label each with the glyph that produced it.
     *
     * Labelling matters: a box whose character we cannot name cannot be compared fairly
     * against a golden that knows its characters, and an unlabelled comparison reports
     * "text differs" instead of the real spacing defect.
     */
    private fun segmentAndLabel(bmp: Bitmap, drawn: List<DrawnGlyph>): List<GoldenGlyph> {
        val runs = segment(bmp)
        if (runs.isEmpty()) return emptyList()
        return runs.map { run ->
            // Attribute the run to the drawn glyph whose horizontal span overlaps it most.
            val centre = (run.xMin + run.xMax) / 2.0
            val owner = drawn.maxByOrNull { g ->
                val overlap = minOf(run.xMax, g.xMaxPt.toDouble()) - maxOf(run.xMin, g.xMinPt.toDouble())
                if (overlap > 0) overlap else -kotlin.math.abs(centre - (g.xMinPt + g.xMaxPt) / 2.0)
            }
            run.copy(char = owner?.char ?: "")
        }
    }

    /** Find vertical column runs of ink and report each as a box in TeX points. */
    private fun segment(bmp: Bitmap): List<GoldenGlyph> {
        val width = bmp.width
        val height = bmp.height
        val inked = BooleanArray(width)
        for (x in 0 until width) {
            for (y in 0 until height) {
                if (bmp.getPixel(x, y) != Color.WHITE) {
                    inked[x] = true
                    break
                }
            }
        }
        val boxes = mutableListOf<GoldenGlyph>()
        var x = 0
        while (x < width) {
            if (!inked[x]) { x++; continue }
            val start = x
            while (x < width && inked[x]) x++
            val end = x - 1
            var top = height
            var bottom = -1
            for (cx in start..end) {
                for (cy in 0 until height) {
                    if (bmp.getPixel(cx, cy) != Color.WHITE) {
                        if (cy < top) top = cy
                        if (cy > bottom) bottom = cy
                        break
                    }
                }
                for (cy in height - 1 downTo 0) {
                    if (bmp.getPixel(cx, cy) != Color.WHITE) {
                        if (cy < top) top = cy
                        if (cy > bottom) bottom = cy
                        break
                    }
                }
            }
            if (bottom >= top) {
                boxes += GoldenGlyph(
                    char = "",
                    xMin = start / PX_PER_PT.toDouble(),
                    yMin = top / PX_PER_PT.toDouble(),
                    xMax = (end + 1) / PX_PER_PT.toDouble(),
                    yMax = (bottom + 1) / PX_PER_PT.toDouble(),
                )
            }
        }
        return boxes
    }

    /** Total ink width in TeX points, useful for quick sanity assertions. */
    fun inkWidthPt(node: MathNode, baseFontPt: Double, isDisplay: Boolean): Double {
        val boxes = measure(node, baseFontPt, isDisplay)
        if (boxes.isEmpty()) return 0.0
        return boxes.maxOf { it.xMax } - boxes.minOf { it.xMin }
    }

    internal fun approxEqual(a: Double, b: Double, tol: Double) = abs(a - b) <= tol
}
