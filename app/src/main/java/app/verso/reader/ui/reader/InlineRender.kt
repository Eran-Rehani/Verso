package app.verso.reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
import app.verso.core.store.ThemeChoice
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
    searchRanges: List<IntRange> = emptyList(),
    activeMatchRawStart: Int? = null,
    onInspectEquation: ((String) -> Unit)? = null,
    engine: app.verso.core.math.MathLayoutEngine? = null
): RichInlineRenderResult {
    return remember(inlineText, fontSizeSp, palette, searchRanges, activeMatchRawStart, onInspectEquation, engine) {
        buildRichInlineResult(
            inlineText = inlineText,
            fontSizeSp = fontSizeSp,
            palette = palette,
            searchRanges = searchRanges,
            activeMatchRawStart = activeMatchRawStart,
            onInspectEquation = onInspectEquation,
            engine = engine
        )
    }
}

/**
 * Build the inline layout engine once per composition, reading the bundled font's MATH
 * table. Returns null when the font is unavailable, in which case inline math falls back to
 * estimated sizes rather than throwing mid-composition.
 */
@Composable
fun rememberMathLayoutEngine(): app.verso.core.math.MathLayoutEngine? {
    val context = LocalContext.current
    val choice = LocalMathFontChoice.current
    return remember(context, choice) {
        runCatching {
            app.verso.core.math.MathLayoutEngine(
                metrics = app.verso.core.math.MathMetrics.forFont(
                    MathTypeface.readFontBytes(context, choice),
                ),
                glyphs = PaintGlyphMetricsProvider(MathTypeface.typeface(context, choice)),
            )
        }.getOrNull()
    }
}

internal fun renderedInlineTextForSearch(inlineText: InlineText): String =
    buildRichInlineResult(inlineText, 18f, paletteFor(ThemeChoice.Light, false)).annotatedString.text

fun InlineText.toAnnotatedString(palette: ReaderPalette): AnnotatedString {
    return buildRichInlineResult(this, fontSizeSp = 18f, palette = palette, allow2DInlineWidgets = false).annotatedString
}

private fun buildRichInlineResult(
    inlineText: InlineText,
    fontSizeSp: Float,
    palette: ReaderPalette,
    allow2DInlineWidgets: Boolean = true,
    searchRanges: List<IntRange> = emptyList(),
    activeMatchRawStart: Int? = null,
    onInspectEquation: ((String) -> Unit)? = null,
    engine: app.verso.core.math.MathLayoutEngine? = null
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
                rawText = raw,
                indexMap = null,
                searchRanges = searchRanges,
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

            // M4.3: EVERY math span is laid out by the engine-backed widget.
            //
            // The previous code sent most inline math down `appendMathNodeInline`, which
            // flattened scripts into a baseline shift and injected U+2009 around operators.
            // That produced text like "ax2 + bx" instead of typeset math, and it meant the
            // same expression looked different depending on whether its paragraph happened to
            // contain a fraction. Both paths existed; only the widget path was correct.
            val widgetId = "verso_inline_math_${widgetCounter++}_$s"
            val measured = measureInlineMath(parsedNode, fontSizeSp, engine)
            val widthEm = measured.widthEm.coerceAtLeast(0.35f)
            val heightEm = measured.totalHeightEm.coerceAtLeast(1.0f)

            appendInlineContent(id = widgetId, alternateText = LatexParser.toReadablePlainText(latex).ifBlank { latex })
            inlineContentMap[widgetId] = InlineTextContent(
                placeholder = Placeholder(
                    width = widthEm.em,
                    height = heightEm.em,
                    // Centring keeps the math's optical centre on the line's centre, which is
                    // closer to TeX's baseline alignment than TOX-aligning to the text top.
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (onInspectEquation != null) {
                                Modifier.clickable { onInspectEquation(latex) }
                            } else {
                                Modifier
                            }
                        )
                        .testTag("inline_math_widget_$s")
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

            val mathEndInOut = this.length
            if (onInspectEquation != null && mathEndInOut > mathStartInOut) {
                addLink(
                    LinkAnnotation.Clickable(
                        tag = "inline_math_$s",
                        // No background tint: inline math should read as typeset math, not as
                        // a highlighted interactive element. The tap-to-inspect behaviour is
                        // retained through the clickable annotation itself.
                        styles = TextLinkStyles(),
                        linkInteractionListener = {
                            onInspectEquation(latex)
                        }
                    ),
                    mathStartInOut,
                    mathEndInOut
                )
            }

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
            rawText = raw,
            indexMap = indexMap,
            searchRanges = searchRanges,
            activeMatchRawStart = activeMatchRawStart,
            palette = palette
        )
    }

    return RichInlineRenderResult(annotated, inlineContentMap)
}

private fun AnnotatedString.Builder.applySearchHighlights(
    rawText: String,
    indexMap: IntArray?,
    searchRanges: List<IntRange>,
    activeMatchRawStart: Int?,
    palette: ReaderPalette
) {
    for (range in searchRanges) {
        val found = range.first
        val end = range.last + 1
        if (found < 0 || end > length || end <= found) continue

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

/**
 * Measure inline math with the real layout engine, so the inline placeholder is sized from
 * font advances and MATH-table constants rather than from the old `estimateWidthEm` guess.
 *
 * Returns a fallback box when the bundled font or its MATH table cannot be read, so a
 * measurement failure degrades to a plausible size instead of throwing during composition.
 */
/**
 * Measure inline math with the real layout engine, so the inline placeholder is sized from
 * font advances and MATH-table constants rather than from the old `estimateWidthEm` guess.
 *
 * Falls back to the estimate when no engine is available (font missing, tests without an
 * asset context) so composition never throws.
 */
private fun measureInlineMath(
    node: MathNode,
    fontSizeSp: Float,
    engine: app.verso.core.math.MathLayoutEngine?,
): app.verso.core.math.MeasuredBox {
    val real = engine?.layout(node, app.verso.core.math.MathStyleLevel.Text)
    if (real != null && real.widthEm > 0f) return real
    return app.verso.core.math.MeasuredBox(
        widthEm = estimateWidthEm(node),
        heightEm = estimateHeightEm(node),
        depthEm = 0f,
    )
}
