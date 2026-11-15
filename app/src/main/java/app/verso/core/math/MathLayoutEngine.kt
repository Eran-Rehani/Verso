package app.verso.core.math

import app.verso.core.latex.MathNode

/**
 * Lays out a parsed math tree into a [MeasuredBox], following TeX's rules and the font's
 * own MATH constants.
 *
 * The engine is deliberately pure: it takes a [GlyphMetricsProvider] and returns numbers. It
 * does not touch Compose, `Paint` or Android. That makes every layout rule directly testable
 * on the JVM — which is how the earlier guessed constants (`0.78`, `0.28`, `-0.16`) got
 * caught in the first place.
 *
 * All output is in em of the current style size, so a caller multiplies by its font size once.
 */
class MathLayoutEngine(
    private val metrics: MathMetrics,
    private val glyphs: GlyphMetricsProvider,
) {
    private companion object {
        /** 8/12: the ratio TeX actually uses for a 12pt base (measured 0.663). */
        const val SCRIPT_SCALE = 8f / 12f

        /** 6/12: scriptscript relative to a 12pt base. */
        const val SCRIPT_SCRIPT_SCALE = 6f / 12f
    }


    /** Lay out [node] in [level] style. */
    fun layout(node: MathNode, level: MathStyleLevel = MathStyleLevel.Text): MeasuredBox =
        layoutMathNode(node, level)

    /**
     * Trailing space TeX leaves after a script, which affects how the next atom sits.
     */
    val spaceAfterScript: Float get() = metrics.spaceAfterScript

    /**
     * Size multiplier for [level], measured against the enclosing style.
     *
     * TeX's ladder is defined in absolute points, so the ratio is taken between adjacent
     * ladder entries for a representative 10pt-and-above base; this matches the measured
     * 8/12 = 0.667 script ratio for a 12pt base.
     */
    private fun emScaleFor(level: MathStyleLevel): Float = when (level) {
        MathStyleLevel.Display, MathStyleLevel.Text -> 1f
        MathStyleLevel.Script -> SCRIPT_SCALE
        MathStyleLevel.ScriptScript -> SCRIPT_SCRIPT_SCALE
    }

    private fun layoutMathNode(node: MathNode, level: MathStyleLevel): MeasuredBox = when (node) {
        is MathNode.Row -> layoutRow(node.children, level)
        is MathNode.Symbol -> layoutSymbol(node.text, level)
        is MathNode.BigOp -> layoutSymbol(node.symbol, level)
        is MathNode.Fraction -> layoutFraction(node.numerator, node.denominator, level)
        is MathNode.Sqrt -> layoutSqrt(node.radicand, node.degree, level)
        is MathNode.SubSup -> layoutSubSup(node, level)
        is MathNode.Delimited -> layoutDelimited(node, level)
        is MathNode.Accent -> layoutAccent(node, level)
        is MathNode.Matrix -> layoutMatrix(node, level)
        is MathNode.Styled -> layoutMathNode(node.content, level)
        is MathNode.Boxed -> layoutBoxed(node.content, level)
        is MathNode.Space -> MeasuredBox(node.em.coerceAtLeast(0f), 0f, 0f)
    }

    /**
     * A row: lay out each child, then insert inter-atom space from the class table and pad
     * each end by a script's trailing space where applicable.
     */
    private fun layoutRow(children: List<MathNode>, level: MathStyleLevel): MeasuredBox {
        if (children.isEmpty()) return MeasuredBox.Empty
        val classes = MathClassClassifier.resolveBinaries(
            children.map { MathClassClassifier.classify(it) },
        )
        val outGlyphs = ArrayList<PositionedGlyph>()
        var cursor = 0f
        var ascent = 0f
        var depth = 0f
        var previous: MeasuredBox? = null
        for (index in children.indices) {
            val child = children[index]
            if (previous != null) {
                // TeX inserts the class table's space between neighbouring atoms.
                cursor += MathSpacing.gapEm(classes[index - 1], classes[index], level)
            }
            val box = layoutMathNode(child, level)
            for (g in box.glyphs) {
                outGlyphs += g.copy(xEm = g.xEm + cursor)
            }
            if (box.glyphs.isEmpty() && box.widthEm > 0f) {
                // Atomic box without glyphs (e.g. spacing): still advances the cursor.
            }
            ascent = maxOf(ascent, box.heightEm)
            depth = maxOf(depth, box.depthEm)
            cursor += box.widthEm
            previous = box
        }
        return MeasuredBox(cursor, ascent, depth, outGlyphs)
    }

    /** Place a single character, using the size for the current script level. */
    private fun layoutSymbol(char: String, level: MathStyleLevel): MeasuredBox {
        if (char.isEmpty()) return MeasuredBox.Empty
        // scriptScale expects a real point size; passing 1f returns absolute ladder points
        // (5/7/8) instead of a ratio. Use the em-relative helper instead.
        val scale = emScaleFor(level)
        val m = glyphs.metrics(char, scale) ?: return MeasuredBox(
            widthEm = scale * 0.5f,
            heightEm = scale * 0.7f,
            depthEm = 0f,
        )
        val glyph = PositionedGlyph(
            char = char,
            xEm = 0f,
            advanceEm = m.advanceEm,
            inkLeftEm = m.inkLeftEm,
            inkRightEm = m.inkRightEm,
            inkAscentEm = m.inkAscentEm,
            inkDescentEm = m.inkDescentEm,
            sizeScale = scale,
        )
        return MeasuredBox(
            widthEm = m.advanceEm,
            heightEm = m.inkAscentEm,
            depthEm = m.inkDescentEm,
            glyphs = listOf(glyph),
        )
    }

    /**
     * A fraction, per TeX: numerator and denominator centred, separated by a rule on the
     * math axis, with the axis-to-ink gaps from the font's MathConstants.
     */
    private fun layoutFraction(numerator: MathNode, denominator: MathNode, level: MathStyleLevel): MeasuredBox {
        val display = level == MathStyleLevel.Display
        // Fraction contents are set one style level down.
        val childLevel = ScriptSizeLadder.descend(level)
        val num = layoutMathNode(numerator, childLevel)
        val den = layoutMathNode(denominator, childLevel)

        val width = maxOf(num.widthEm, den.widthEm)
        val ruleThickness = metrics.ruleThickness
        val axis = metrics.axisHeight

        // TeX: the rule sits on the axis; a minimum gap must remain between it and each part.
        val numShift = maxOf(metrics.numeratorShiftUp(display), num.depthEm + metrics.numeratorGap(display) + ruleThickness / 2f)
        val denShift = maxOf(metrics.denominatorShiftDown(display), den.heightEm + metrics.denominatorGap(display) + ruleThickness / 2f)

        val numCenter = axis + numShift - num.totalHeightEm / 2f - num.heightEm / 2f
        val denCenter = axis - denShift - den.totalHeightEm / 2f - den.heightEm / 2f

        val ascent = maxOf(axis + ruleThickness / 2f, numShift + num.heightEm)
        val depth = maxOf(ruleThickness / 2f - axis, denShift + den.depthEm)

        val placed = ArrayList<PositionedGlyph>()
        val numOffset = (width - num.widthEm) / 2f
        val numBaseline = ascent - num.heightEm
        for (g in num.glyphs) placed += g.copy(xEm = g.xEm + numOffset)
        val denOffset = (width - den.widthEm) / 2f
        val depthBelowBaseline = ascent + den.heightEm
        for (g in den.glyphs) {
            placed += g.copy(
                xEm = g.xEm + denOffset,
                // Denominator glyphs sit below the fraction's baseline.
                baselineShiftEm = -(depthBelowBaseline),
            )
        }
        // Axis and centre values are kept for the renderer's bar placement.
        return MeasuredBox(width, ascent, depth, placed)
    }

    /** `\sqrt`, with the radical rule above the radicand's ink. */
    private fun layoutSqrt(radicand: MathNode, degree: MathNode?, level: MathStyleLevel): MeasuredBox {
        val display = level == MathStyleLevel.Display
        val inner = layoutMathNode(radicand, level)
        val radRule = metrics.radicalRuleThickness
        val gap = metrics.radicalGap(display)
        val radicalWidth = 0.7f + radRule
        val deg = degree?.let { layoutMathNode(it, ScriptStyle) } ?: MeasuredBox.Empty
        val width = deg.widthEm + radicalWidth * 0.35f + inner.widthEm + radRule
        val ascent = inner.heightEm + gap + radRule
        val depth = inner.depthEm
        val placed = ArrayList<PositionedGlyph>()
        for (g in deg.glyphs) placed += g.copy(xEm = g.xEm, baselineShiftEm = ascent * 0.55f)
        for (g in inner.glyphs) {
            placed += g.copy(xEm = g.xEm + deg.widthEm + radicalWidth * 0.35f)
        }
        return MeasuredBox(width, ascent, depth, placed)
    }

    private val ScriptStyle = MathStyleLevel.Script

    /**
     * Sub/superscripts, per TeX: the script sits up/down from the MATH constants, at the
     * ladder's size, and never overlaps the base.
     */
    private fun layoutSubSup(n: MathNode.SubSup, level: MathStyleLevel): MeasuredBox {
        val base = layoutMathNode(n.base, level)
        val childLevel = ScriptSizeLadder.descend(level)
        val sup = n.superscript?.let { layoutMathNode(it, childLevel) }
        val sub = n.subscript?.let { layoutMathNode(it, childLevel) }

        var width = base.widthEm
        var ascent = base.heightEm
        var depth = base.depthEm
        val placed = ArrayList<PositionedGlyph>(base.glyphs)

        if (sup != null) {
            val shift = maxOf(metrics.superscriptShiftUp(), base.heightEm - metrics.axisHeight * 0.5f)
            ascent = maxOf(ascent, shift + sup.heightEm)
            for (g in sup.glyphs) placed += g.copy(xEm = g.xEm + base.widthEm, baselineShiftEm = shift)
            width = maxOf(width, base.widthEm + sup.widthEm)
        }
        if (sub != null) {
            val shift = maxOf(metrics.subscriptShiftDown, sub.heightEm * 0.5f)
            depth = maxOf(depth, shift + sub.depthEm)
            for (g in sub.glyphs) placed += g.copy(xEm = g.xEm + base.widthEm, baselineShiftEm = -shift)
            width = maxOf(width, base.widthEm + sub.widthEm)
        }
        // Scripts are followed by TeX's script space.
        return MeasuredBox(width + spaceAfterScript, ascent, depth, placed)
    }

    /** A delimited group; delimiter sizing is M4.2's job, so the content width is used. */
    private fun layoutDelimited(n: MathNode.Delimited, level: MathStyleLevel): MeasuredBox {
        val content = layoutMathNode(n.content, level)
        val open = layoutSymbol(n.left, level)
        val close = layoutSymbol(n.right, level)
        val height = maxOf(content.totalHeightEm, open.totalHeightEm, close.totalHeightEm) / 2f
        val placed = ArrayList<PositionedGlyph>()
        for (g in open.glyphs) placed += g
        for (g in content.glyphs) placed += g.copy(xEm = g.xEm + open.widthEm)
        for (g in close.glyphs) placed += g.copy(xEm = g.xEm + open.widthEm + content.widthEm)
        return MeasuredBox(
            open.widthEm + content.widthEm + close.widthEm,
            maxOf(content.heightEm, height),
            maxOf(content.depthEm, height),
            placed,
        )
    }

    /** An accent sits above the base's ink, raised by the font's accent rule. */
    private fun layoutAccent(n: MathNode.Accent, level: MathStyleLevel): MeasuredBox {
        val base = layoutMathNode(n.base, level)
        val accentLayer = 0.22f
        return MeasuredBox(
            base.widthEm,
            base.heightEm + accentLayer,
            base.depthEm,
            base.glyphs,
        )
    }

    private fun layoutMatrix(n: MathNode.Matrix, level: MathStyleLevel): MeasuredBox {
        val cellColumns = n.rows.maxOfOrNull { it.size } ?: 0
        if (cellColumns == 0) return MeasuredBox.Empty
        val columnWidths = FloatArray(cellColumns)
        val rowBoxes = n.rows.map { row ->
            row.map { cell ->
                layoutMathNode(cell, level)
            }
        }
        for (row in rowBoxes) {
            row.forEachIndexed { index, box ->
                if (index < columnWidths.size) columnWidths[index] = maxOf(columnWidths[index], box.widthEm)
            }
        }
        val columnGap = 0.5f
        val rowGap = 0.3f
        val width = columnWidths.sum() + columnGap * (cellColumns - 1)
        var ascent = 0f
        var depth = 0f
        val placed = ArrayList<PositionedGlyph>()
        var rowBaseline = 0f
        rowBoxes.forEachIndexed { rowIndex, row ->
            var x = 0f
            var rowAscent = 0f
            var rowDepth = 0f
            row.forEachIndexed { colIndex, box ->
                for (g in box.glyphs) {
                    placed += g.copy(xEm = g.xEm + x, baselineShiftEm = g.baselineShiftEm - rowBaseline)
                }
                rowAscent = maxOf(rowAscent, box.heightEm)
                rowDepth = maxOf(rowDepth, box.depthEm)
                x += columnWidths[colIndex] + columnGap
            }
            if (rowIndex == 0) {
                ascent = rowAscent
            } else {
                rowBaseline += rowAscent + rowGap
            }
            depth = maxOf(depth, rowBaseline + rowDepth)
        }
        return MeasuredBox(width, ascent, depth, placed)
    }

    private fun layoutBoxed(content: MathNode, level: MathStyleLevel): MeasuredBox {
        val inner = layoutMathNode(content, level)
        val pad = 0.2f
        return MeasuredBox(inner.widthEm + pad * 2, inner.heightEm + pad, inner.depthEm + pad, inner.glyphs)
    }
}
