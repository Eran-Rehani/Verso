package app.verso.reader.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.em
import app.verso.core.latex.AccentKind
import app.verso.core.latex.LatexParser
import app.verso.core.latex.MathFontStyle
import app.verso.core.latex.MathNode
import app.verso.core.latex.SymbolCategory
import app.verso.core.model.InlineText
import app.verso.core.model.Span
import app.verso.core.model.SpanKind
import kotlin.math.max

data class RichInlineRenderResult(
    val annotatedString: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>
)

@Composable
fun rememberRichInlineText(
    inlineText: InlineText,
    fontSizeSp: Float,
    palette: ReaderPalette,
    searchQuery: String? = null,
    activeMatchRawStart: Int? = null
): RichInlineRenderResult {
    return remember(inlineText, fontSizeSp, palette, searchQuery, activeMatchRawStart) {
        buildRichInlineResult(
            inlineText = inlineText,
            fontSizeSp = fontSizeSp,
            palette = palette,
            searchQuery = searchQuery,
            activeMatchRawStart = activeMatchRawStart
        )
    }
}

fun InlineText.toAnnotatedString(palette: ReaderPalette): AnnotatedString {
    return buildRichInlineResult(this, fontSizeSp = 18f, palette = palette, allow2DInlineWidgets = false).annotatedString
}

private fun buildRichInlineResult(
    inlineText: InlineText,
    fontSizeSp: Float,
    palette: ReaderPalette,
    allow2DInlineWidgets: Boolean = true,
    searchQuery: String? = null,
    activeMatchRawStart: Int? = null
): RichInlineRenderResult {
    val raw = inlineText.text
    val spans = inlineText.spans

    val mathSpans = spans.filter { it.kind == SpanKind.Math }
        .filter { it.start >= 0 && it.end <= raw.length && it.end > it.start }
        .sortedBy { it.start }

    // Fast path when there are no math spans
    if (mathSpans.isEmpty()) {
        val annotated = buildAnnotatedString {
            append(raw)
            applyNonMathSpans(raw.length, spans, palette)
            applySearchHighlights(
                displayText = raw,
                rawText = raw,
                indexMap = null,
                searchQuery = searchQuery,
                activeMatchRawStart = activeMatchRawStart,
                palette = palette
            )
        }
        return RichInlineRenderResult(annotated, emptyMap())
    }

    // Build remapped string where Math spans are replaced by formatted math or inline 2D placeholders
    val inlineContentMap = mutableMapOf<String, InlineTextContent>()
    val indexMap = IntArray(raw.length + 1)

    val annotated = buildAnnotatedString {
        var cursor = 0
        var widgetCounter = 0

        for (mathSpan in mathSpans) {
            val s = mathSpan.start.coerceIn(0, raw.length)
            val e = mathSpan.end.coerceIn(0, raw.length)
            if (s < cursor) continue

            // Append plain text before this math span
            while (cursor < s) {
                indexMap[cursor] = this.length
                append(raw[cursor])
                cursor++
            }

            val mathStartInOut = this.length
            val latex = raw.substring(s, e)
            val parsedNode = LatexParser.parseEquation(latex).root

            val use2DWidget = allow2DInlineWidgets &&
                    contains2DConstruct(parsedNode) &&
                    estimateWidthEm(parsedNode) <= 13.5f

            if (use2DWidget) {
                val widgetId = "verso_inline_math_${widgetCounter++}_$s"
                val widthEm = (estimateWidthEm(parsedNode) + 0.25f).coerceIn(1.1f, 14f)
                val heightEm = estimateHeightEm(parsedNode).coerceIn(1.3f, 3.0f)

                appendInlineContent(id = widgetId, alternateText = LatexParser.toReadablePlainText(latex).ifBlank { latex })
                inlineContentMap[widgetId] = InlineTextContent(
                    placeholder = Placeholder(
                        width = widthEm.em,
                        height = heightEm.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        MathNodeView(
                            node = parsedNode,
                            fontSizeSp = fontSizeSp * 0.96f,
                            color = palette.text,
                            palette = palette,
                            isDisplay = false,
                            isTopLevel = false
                        )
                    }
                }
            } else {
                appendMathNodeInline(parsedNode, palette, scale = 1f, baselineShift = null, styleOverride = null)
            }

            val mathEndInOut = this.length
            for (k in s..e) {
                indexMap[k] = if (k == e) mathEndInOut else mathStartInOut
            }
            cursor = e
        }

        while (cursor < raw.length) {
            indexMap[cursor] = this.length
            append(raw[cursor])
            cursor++
        }
        indexMap[raw.length] = this.length

        // Remap and apply non-math spans
        val nonMathSpans = spans.filter { it.kind != SpanKind.Math }.mapNotNull { span ->
            val rs = indexMap[span.start.coerceIn(0, raw.length)]
            val re = indexMap[span.end.coerceIn(0, raw.length)]
            if (re > rs) Span(rs, re, span.kind, span.href) else null
        }
        applyNonMathSpans(this.length, nonMathSpans, palette)
        applySearchHighlights(
            displayText = this.toString(),
            rawText = raw,
            indexMap = indexMap,
            searchQuery = searchQuery,
            activeMatchRawStart = activeMatchRawStart,
            palette = palette
        )
    }

    return RichInlineRenderResult(annotated, inlineContentMap)
}

private fun AnnotatedString.Builder.applySearchHighlights(
    displayText: String,
    rawText: String,
    indexMap: IntArray?,
    searchQuery: String?,
    activeMatchRawStart: Int?,
    palette: ReaderPalette
) {
    if (searchQuery.isNullOrBlank()) return
    val q = searchQuery.trim()
    if (q.isEmpty()) return

    var searchPos = 0
    val qLen = q.length
    while (searchPos < displayText.length) {
        val found = displayText.indexOf(q, searchPos, ignoreCase = true)
        if (found == -1) break
        val end = found + qLen

        val isActive = if (activeMatchRawStart != null) {
            val mappedActiveStart = if (indexMap != null) {
                indexMap[activeMatchRawStart.coerceIn(0, rawText.length)]
            } else {
                activeMatchRawStart
            }
            found == mappedActiveStart || (found in mappedActiveStart..(mappedActiveStart + 1))
        } else false

        val bg = if (isActive) {
            if (palette.isDark) Color(0xFFD97706) else Color(0xFFFF9800)
        } else {
            if (palette.isDark) Color(0xFF6B4F1D) else Color(0xFFFFE082)
        }
        val fg = if (isActive) Color.White else if (palette.isDark) Color(0xFFFFF8E1) else Color(0xFF1A1A1A)

        addStyle(
            SpanStyle(
                background = bg,
                color = fg,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            ),
            found,
            end
        )
        searchPos = end
    }
}

private fun AnnotatedString.Builder.applyNonMathSpans(
    textLength: Int,
    spans: List<Span>,
    palette: ReaderPalette
) {
    for (span in spans) {
        val s = span.start.coerceIn(0, textLength)
        val e = span.end.coerceIn(0, textLength)
        if (e <= s) continue

        when (span.kind) {
            SpanKind.Bold -> addStyle(
                SpanStyle(fontWeight = FontWeight.Bold), s, e
            )
            SpanKind.Italic -> addStyle(
                SpanStyle(fontStyle = FontStyle.Italic), s, e
            )
            SpanKind.Code -> addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = palette.codeBackground
                ), s, e
            )
            SpanKind.Math -> {
                // Already styled by appendMathNodeInline
            }
            SpanKind.Strike -> addStyle(
                SpanStyle(textDecoration = TextDecoration.LineThrough), s, e
            )
            SpanKind.Link -> {
                addStyle(
                    SpanStyle(
                        color = palette.link,
                        textDecoration = TextDecoration.Underline
                    ), s, e
                )
                val href = span.href
                if (!href.isNullOrBlank()) {
                    addLink(
                        LinkAnnotation.Url(
                            url = href,
                            styles = TextLinkStyles(
                                style = SpanStyle(
                                    color = palette.link,
                                    textDecoration = TextDecoration.Underline
                                )
                            )
                        ), s, e
                    )
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendMathNodeInline(
    node: MathNode,
    palette: ReaderPalette,
    scale: Float,
    baselineShift: BaselineShift?,
    styleOverride: MathFontStyle?
) {
    when (node) {
        is MathNode.Row -> {
            for (child in node.children) {
                appendMathNodeInline(child, palette, scale, baselineShift, styleOverride)
            }
        }
        is MathNode.Symbol -> {
            val isItalic = when (styleOverride) {
                MathFontStyle.ROMAN, MathFontStyle.MONOSPACE, MathFontStyle.SANS_SERIF -> false
                MathFontStyle.ITALIC -> true
                else -> node.category == SymbolCategory.VARIABLE
            }
            val family = when (styleOverride) {
                MathFontStyle.MONOSPACE -> FontFamily.Monospace
                MathFontStyle.SANS_SERIF -> FontFamily.SansSerif
                else -> FontFamily.Serif
            }
            val weight = if (styleOverride == MathFontStyle.BOLD) FontWeight.Bold else FontWeight.Normal

            val spanStyle = SpanStyle(
                fontFamily = family,
                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                fontWeight = weight,
                fontSize = if (scale != 1f) scale.em else androidx.compose.ui.unit.TextUnit.Unspecified,
                baselineShift = baselineShift
            )

            pushStyle(spanStyle)
            when (node.category) {
                SymbolCategory.OPERATOR -> append("\u2009${node.text}\u2009")
                SymbolCategory.FUNCTION -> append("${node.text}\u2009")
                else -> append(node.text)
            }
            pop()
        }
        is MathNode.BigOp -> {
            pushStyle(
                SpanStyle(
                    fontFamily = FontFamily.Serif,
                    fontStyle = if (node.isIntegral) FontStyle.Italic else FontStyle.Normal,
                    fontSize = if (node.isLimitWord) scale.em else (scale * 1.15f).em,
                    baselineShift = baselineShift
                )
            )
            append(node.symbol)
            pop()
        }
        is MathNode.Fraction -> {
            val subScale = (scale * 0.78f).coerceAtLeast(0.6f)
            if (node.hasBar) {
                appendMathNodeInline(
                    node.numerator,
                    palette,
                    scale = subScale,
                    baselineShift = BaselineShift(0.28f),
                    styleOverride = styleOverride
                )
                pushStyle(SpanStyle(fontFamily = FontFamily.Serif))
                append("⁄")
                pop()
                appendMathNodeInline(
                    node.denominator,
                    palette,
                    scale = subScale,
                    baselineShift = BaselineShift(-0.16f),
                    styleOverride = styleOverride
                )
            } else {
                append("(")
                appendMathNodeInline(node.numerator, palette, subScale, BaselineShift(0.22f), styleOverride)
                append(" ")
                appendMathNodeInline(node.denominator, palette, subScale, BaselineShift(-0.14f), styleOverride)
                append(")")
            }
        }
        is MathNode.Sqrt -> {
            if (node.degree != null) {
                appendMathNodeInline(
                    node.degree,
                    palette,
                    scale = (scale * 0.65f).coerceAtLeast(0.55f),
                    baselineShift = BaselineShift(0.35f),
                    styleOverride = styleOverride
                )
            }
            pushStyle(
                SpanStyle(
                    fontFamily = FontFamily.Serif,
                    fontSize = if (scale != 1f) scale.em else androidx.compose.ui.unit.TextUnit.Unspecified,
                    baselineShift = baselineShift
                )
            )
            append("√")
            pop()
            val needsParens = node.radicand is MathNode.Row && node.radicand.children.size > 1
            if (needsParens) append("(")
            appendMathNodeInline(node.radicand, palette, scale, baselineShift, styleOverride)
            if (needsParens) append(")")
        }
        is MathNode.SubSup -> {
            appendMathNodeInline(node.base, palette, scale, baselineShift, styleOverride)
            val scriptScale = (scale * 0.74f).coerceAtLeast(0.58f)
            if (node.subscript != null) {
                val subShift = BaselineShift((baselineShift?.multiplier ?: 0f) - 0.26f)
                appendMathNodeInline(node.subscript, palette, scriptScale, subShift, styleOverride)
            }
            if (node.superscript != null) {
                val supShift = BaselineShift((baselineShift?.multiplier ?: 0f) + 0.36f)
                appendMathNodeInline(node.superscript, palette, scriptScale, supShift, styleOverride)
            }
        }
        is MathNode.Delimited -> {
            pushStyle(SpanStyle(fontFamily = FontFamily.Serif, baselineShift = baselineShift))
            if (node.left.isNotEmpty()) append(node.left)
            pop()
            appendMathNodeInline(node.content, palette, scale, baselineShift, styleOverride)
            pushStyle(SpanStyle(fontFamily = FontFamily.Serif, baselineShift = baselineShift))
            if (node.right.isNotEmpty()) append(node.right)
            pop()
        }
        is MathNode.Accent -> {
            appendMathNodeInline(node.base, palette, scale, baselineShift, styleOverride)
            val combining = when (node.kind) {
                AccentKind.HAT, AccentKind.WIDE_HAT -> "\u0302"
                AccentKind.BAR, AccentKind.OVERLINE -> "\u0304"
                AccentKind.UNDERLINE -> "\u0332"
                AccentKind.VEC, AccentKind.OVER_RIGHT_ARROW -> "\u20D7"
                AccentKind.OVER_LEFT_ARROW -> "\u20D6"
                AccentKind.TILDE, AccentKind.WIDE_TILDE -> "\u0303"
                AccentKind.DOT -> "\u0307"
                AccentKind.DDOT -> "\u0308"
                AccentKind.CHECK -> "\u030C"
                AccentKind.BREVE -> "\u0306"
                AccentKind.OVERBRACE, AccentKind.UNDERBRACE -> ""
            }
            if (combining.isNotEmpty()) {
                append(combining)
            }
        }
        is MathNode.Matrix -> {
            node.rows.forEachIndexed { rIdx, row ->
                row.forEachIndexed { cIdx, cell ->
                    appendMathNodeInline(cell, palette, scale, baselineShift, styleOverride)
                    if (cIdx < row.size - 1) append(", ")
                }
                if (rIdx < node.rows.size - 1) append("; ")
            }
        }
        is MathNode.Styled -> {
            appendMathNodeInline(node.content, palette, scale, baselineShift, node.style)
        }
        is MathNode.Boxed -> {
            append("[")
            appendMathNodeInline(node.content, palette, scale, baselineShift, styleOverride)
            append("]")
        }
        is MathNode.Space -> {
            if (node.em >= 0.15f) append("\u2009")
        }
    }
}

private fun contains2DConstruct(node: MathNode): Boolean = when (node) {
    is MathNode.Fraction -> true
    is MathNode.Sqrt -> true
    is MathNode.Matrix -> true
    is MathNode.Boxed -> true
    is MathNode.Accent -> node.kind in setOf(
        AccentKind.OVERLINE,
        AccentKind.UNDERLINE,
        AccentKind.WIDE_HAT,
        AccentKind.WIDE_TILDE,
        AccentKind.OVER_RIGHT_ARROW,
        AccentKind.OVER_LEFT_ARROW
    )
    is MathNode.Delimited -> contains2DConstruct(node.content)
    is MathNode.SubSup -> node.limitsUnderOver ||
            (node.subscript != null && node.superscript != null) ||
            contains2DConstruct(node.base) ||
            (node.subscript != null && contains2DConstruct(node.subscript)) ||
            (node.superscript != null && contains2DConstruct(node.superscript))
    is MathNode.Row -> node.children.any { contains2DConstruct(it) }
    is MathNode.Styled -> contains2DConstruct(node.content)
    is MathNode.Symbol, is MathNode.BigOp, is MathNode.Space -> false
}

private fun estimateWidthEm(node: MathNode): Float = when (node) {
    is MathNode.Row -> node.children.sumOf { estimateWidthEm(it).toDouble() }.toFloat()
    is MathNode.Symbol -> when (node.category) {
        SymbolCategory.OPERATOR -> node.text.length * 0.58f + 0.36f
        SymbolCategory.FUNCTION -> node.text.length * 0.54f + 0.15f
        else -> node.text.length * 0.56f
    }
    is MathNode.BigOp -> if (node.isLimitWord) node.symbol.length * 0.55f else 1.15f
    is MathNode.Fraction -> {
        val numW = estimateWidthEm(node.numerator)
        val denW = estimateWidthEm(node.denominator)
        max(numW, denW) * 0.82f + 0.48f
    }
    is MathNode.Sqrt -> {
        val degW = node.degree?.let { estimateWidthEm(it) * 0.6f } ?: 0f
        degW + 0.68f + estimateWidthEm(node.radicand)
    }
    is MathNode.SubSup -> {
        val baseW = estimateWidthEm(node.base)
        val subW = node.subscript?.let { estimateWidthEm(it) * 0.7f } ?: 0f
        val supW = node.superscript?.let { estimateWidthEm(it) * 0.7f } ?: 0f
        if (node.limitsUnderOver) {
            maxOf(baseW, subW, supW) + 0.2f
        } else {
            baseW + max(subW, supW) + 0.12f
        }
    }
    is MathNode.Delimited -> {
        val lW = if (node.left.isNotEmpty()) 0.42f else 0f
        val rW = if (node.right.isNotEmpty()) 0.42f else 0f
        lW + estimateWidthEm(node.content) + rW
    }
    is MathNode.Accent -> estimateWidthEm(node.base) + 0.15f
    is MathNode.Matrix -> {
        val numCols = node.rows.maxOfOrNull { it.size } ?: 0
        var total = 0f
        for (c in 0 until numCols) {
            val colMax = node.rows.maxOfOrNull { row ->
                row.getOrNull(c)?.let { estimateWidthEm(it) } ?: 0f
            } ?: 0f
            total += colMax + 0.6f
        }
        total + 0.8f
    }
    is MathNode.Styled -> estimateWidthEm(node.content)
    is MathNode.Boxed -> estimateWidthEm(node.content) + 0.6f
    is MathNode.Space -> node.em.coerceAtLeast(0f)
}

private fun estimateHeightEm(node: MathNode): Float = when (node) {
    is MathNode.Row -> node.children.maxOfOrNull { estimateHeightEm(it) } ?: 1.2f
    is MathNode.Symbol -> 1.2f
    is MathNode.BigOp -> 1.55f
    is MathNode.Fraction -> {
        val numH = estimateHeightEm(node.numerator) * 0.78f
        val denH = estimateHeightEm(node.denominator) * 0.78f
        (numH + denH + 0.35f).coerceIn(1.95f, 3.0f)
    }
    is MathNode.Sqrt -> (estimateHeightEm(node.radicand) + 0.3f).coerceIn(1.45f, 2.8f)
    is MathNode.SubSup -> {
        if (node.limitsUnderOver) 2.2f
        else if (node.subscript != null && node.superscript != null) 1.55f
        else 1.35f
    }
    is MathNode.Delimited -> (estimateHeightEm(node.content) + 0.15f).coerceAtMost(3.0f)
    is MathNode.Accent -> (estimateHeightEm(node.base) + 0.32f).coerceAtMost(2.5f)
    is MathNode.Matrix -> (node.rows.size * 1.25f + 0.3f).coerceAtMost(3.2f)
    is MathNode.Styled -> estimateHeightEm(node.content)
    is MathNode.Boxed -> estimateHeightEm(node.content) + 0.35f
    is MathNode.Space -> 1.0f
}
