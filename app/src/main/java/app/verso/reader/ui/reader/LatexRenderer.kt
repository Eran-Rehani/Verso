package app.verso.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.verso.core.latex.AccentKind
import app.verso.core.latex.LatexParser
import app.verso.core.latex.MathFontStyle
import app.verso.core.latex.MathNode
import app.verso.core.latex.MatrixEnvType
import app.verso.core.latex.SymbolCategory
import app.verso.core.store.ReaderSettings
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun MathBlockView(
    latex: String,
    settings: ReaderSettings,
    palette: ReaderPalette,
    modifier: Modifier = Modifier
) {
    val parsed = remember(latex) { LatexParser.parseEquation(latex) }
    val readableDesc = remember(latex) { LatexParser.toReadablePlainText(latex) }
    val baseMathSizeSp = settings.fontSizeSp * 1.05f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.codeBackground.copy(alpha = 0.65f))
            .border(1.dp, palette.rule.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics { contentDescription = readableDesc.ifBlank { latex } }
            .testTag("math_block_view")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.weight(1f)
            ) {
                MathNodeView(
                    node = parsed.root,
                    fontSizeSp = baseMathSizeSp,
                    color = palette.text,
                    palette = palette,
                    isDisplay = true,
                    isTopLevel = true
                )
            }

            if (!parsed.tag.isNullOrBlank()) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "(${parsed.tag})",
                    style = TextStyle(
                        fontSize = (settings.fontSizeSp * 0.9f).sp,
                        fontFamily = FontFamily.Serif,
                        color = palette.muted
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MathNodeView(
    node: MathNode,
    fontSizeSp: Float,
    color: Color,
    palette: ReaderPalette,
    isDisplay: Boolean,
    isTopLevel: Boolean = false,
    fontStyleOverride: MathFontStyle? = null
) {
    val clampedSize = fontSizeSp.coerceAtLeast(10f)

    when (node) {
        is MathNode.Row -> {
            if (node.children.isEmpty()) return
            if (isTopLevel && node.children.size > 4) {
                FlowRow(
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (child in node.children) {
                        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                            MathNodeView(
                                node = child,
                                fontSizeSp = clampedSize,
                                color = color,
                                palette = palette,
                                isDisplay = isDisplay,
                                isTopLevel = false,
                                fontStyleOverride = fontStyleOverride
                            )
                        }
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (child in node.children) {
                        MathNodeView(
                            node = child,
                            fontSizeSp = clampedSize,
                            color = color,
                            palette = palette,
                            isDisplay = isDisplay,
                            isTopLevel = false,
                            fontStyleOverride = fontStyleOverride
                        )
                    }
                }
            }
        }

        is MathNode.Symbol -> {
            val family = when (fontStyleOverride) {
                MathFontStyle.MONOSPACE -> FontFamily.Monospace
                MathFontStyle.SANS_SERIF -> FontFamily.SansSerif
                else -> FontFamily.Serif
            }
            val weight = if (fontStyleOverride == MathFontStyle.BOLD) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            }
            val italic = when (fontStyleOverride) {
                MathFontStyle.ROMAN, MathFontStyle.MONOSPACE, MathFontStyle.SANS_SERIF -> FontStyle.Normal
                MathFontStyle.ITALIC -> FontStyle.Italic
                else -> if (node.category == SymbolCategory.VARIABLE) FontStyle.Italic else FontStyle.Normal
            }

            val horizontalPad = when (node.category) {
                SymbolCategory.OPERATOR -> (clampedSize * 0.16f).dp
                else -> 0.dp
            }
            val endPad = if (node.category == SymbolCategory.FUNCTION) (clampedSize * 0.10f).dp else 0.dp

            Text(
                text = node.text,
                style = TextStyle(
                    fontSize = clampedSize.sp,
                    lineHeight = (clampedSize * 1.15f).sp,
                    fontFamily = family,
                    fontWeight = weight,
                    fontStyle = italic,
                    color = color
                ),
                modifier = Modifier.padding(
                    start = horizontalPad,
                    end = horizontalPad + endPad
                )
            )
        }

        is MathNode.BigOp -> {
            if (node.isLimitWord) {
                Text(
                    text = node.symbol,
                    style = TextStyle(
                        fontSize = clampedSize.sp,
                        lineHeight = (clampedSize * 1.1f).sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Normal,
                        color = color
                    ),
                    modifier = Modifier.padding(horizontal = 1.dp)
                )
            } else {
                val scale = when {
                    node.isIntegral && isDisplay -> 1.65f
                    node.isIntegral -> 1.25f
                    isDisplay -> 1.45f
                    else -> 1.15f
                }
                Text(
                    text = node.symbol,
                    style = TextStyle(
                        fontSize = (clampedSize * scale).sp,
                        lineHeight = (clampedSize * scale * 1.05f).sp,
                        fontFamily = FontFamily.Serif,
                        fontStyle = if (node.isIntegral) FontStyle.Italic else FontStyle.Normal,
                        color = color
                    ),
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
        }

        is MathNode.Fraction -> {
            val childScale = if (isDisplay && !node.compact) 0.88f else 0.76f
            val childFontSize = (clampedSize * childScale).coerceAtLeast(10f)
            val barThickness = (clampedSize * 0.065f).coerceIn(1.0f, 2.2f).dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(horizontal = 3.dp, vertical = 1.dp)
                    .width(IntrinsicSize.Max)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 3.dp)
                ) {
                    MathNodeView(
                        node = node.numerator,
                        fontSizeSp = childFontSize,
                        color = color,
                        palette = palette,
                        isDisplay = false,
                        fontStyleOverride = fontStyleOverride
                    )
                }

                if (node.hasBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 1.5.dp)
                            .height(barThickness)
                            .background(color)
                    )
                } else {
                    Spacer(modifier = Modifier.height(2.dp))
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 3.dp)
                ) {
                    MathNodeView(
                        node = node.denominator,
                        fontSizeSp = childFontSize,
                        color = color,
                        palette = palette,
                        isDisplay = false,
                        fontStyleOverride = fontStyleOverride
                    )
                }
            }
        }

        is MathNode.Sqrt -> {
            SqrtLayout(
                radicand = node.radicand,
                degree = node.degree,
                fontSizeSp = clampedSize,
                color = color,
                palette = palette,
                isDisplay = isDisplay,
                fontStyleOverride = fontStyleOverride
            )
        }

        is MathNode.SubSup -> {
            val scriptSize = (clampedSize * 0.68f).coerceAtLeast(9.5f)
            if (node.limitsUnderOver && isDisplay) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    if (node.superscript != null) {
                        MathNodeView(
                            node = node.superscript,
                            fontSizeSp = scriptSize,
                            color = color,
                            palette = palette,
                            isDisplay = false,
                            fontStyleOverride = fontStyleOverride
                        )
                    }
                    MathNodeView(
                        node = node.base,
                        fontSizeSp = clampedSize,
                        color = color,
                        palette = palette,
                        isDisplay = isDisplay,
                        fontStyleOverride = fontStyleOverride
                    )
                    if (node.subscript != null) {
                        MathNodeView(
                            node = node.subscript,
                            fontSizeSp = scriptSize,
                            color = color,
                            palette = palette,
                            isDisplay = false,
                            fontStyleOverride = fontStyleOverride
                        )
                    }
                }
            } else {
                SubSupLayout(
                    base = node.base,
                    subscript = node.subscript,
                    superscript = node.superscript,
                    baseFontSizeSp = clampedSize,
                    scriptFontSizeSp = scriptSize,
                    color = color,
                    palette = palette,
                    isDisplay = isDisplay,
                    fontStyleOverride = fontStyleOverride
                )
            }
        }

        is MathNode.Delimited -> {
            ScalableDelimitedLayout(
                left = node.left,
                content = node.content,
                right = node.right,
                fontSizeSp = clampedSize,
                color = color,
                palette = palette,
                isDisplay = isDisplay,
                fontStyleOverride = fontStyleOverride
            )
        }

        is MathNode.Accent -> {
            AccentLayout(
                base = node.base,
                kind = node.kind,
                fontSizeSp = clampedSize,
                color = color,
                palette = palette,
                isDisplay = isDisplay,
                fontStyleOverride = fontStyleOverride
            )
        }

        is MathNode.Matrix -> {
            val (leftDelim, rightDelim) = when (node.envType) {
                MatrixEnvType.PAREN -> "(" to ")"
                MatrixEnvType.BRACKET -> "[" to "]"
                MatrixEnvType.BRACE -> "{" to "}"
                MatrixEnvType.VBAR -> "|" to "|"
                MatrixEnvType.DOUBLE_VBAR -> "‖" to "‖"
                MatrixEnvType.CASES -> "{" to ""
                MatrixEnvType.PLAIN, MatrixEnvType.ALIGNED -> "" to ""
            }

            if (leftDelim.isNotEmpty() || rightDelim.isNotEmpty()) {
                ScalableDelimitedLayout(
                    left = leftDelim,
                    content = MathNode.Matrix(node.rows, MatrixEnvType.PLAIN),
                    right = rightDelim,
                    fontSizeSp = clampedSize,
                    color = color,
                    palette = palette,
                    isDisplay = isDisplay,
                    fontStyleOverride = fontStyleOverride,
                    isAlignStyle = node.envType == MatrixEnvType.CASES
                )
            } else {
                MatrixGridLayout(
                    rows = node.rows,
                    isAlignedEnv = node.envType == MatrixEnvType.ALIGNED,
                    fontSizeSp = clampedSize,
                    color = color,
                    palette = palette,
                    isDisplay = isDisplay,
                    fontStyleOverride = fontStyleOverride
                )
            }
        }

        is MathNode.Styled -> {
            MathNodeView(
                node = node.content,
                fontSizeSp = clampedSize,
                color = color,
                palette = palette,
                isDisplay = isDisplay,
                fontStyleOverride = node.style
            )
        }

        is MathNode.Boxed -> {
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .border(1.2.dp, color, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                MathNodeView(
                    node = node.content,
                    fontSizeSp = clampedSize,
                    color = color,
                    palette = palette,
                    isDisplay = isDisplay,
                    fontStyleOverride = fontStyleOverride
                )
            }
        }

        is MathNode.Space -> {
            val widthDp = (node.em * clampedSize).coerceAtLeast(0f).dp
            if (widthDp > 0.dp) {
                Spacer(modifier = Modifier.width(widthDp))
            }
        }
    }
}

@Composable
private fun SubSupLayout(
    base: MathNode,
    subscript: MathNode?,
    superscript: MathNode?,
    baseFontSizeSp: Float,
    scriptFontSizeSp: Float,
    color: Color,
    palette: ReaderPalette,
    isDisplay: Boolean,
    fontStyleOverride: MathFontStyle?
) {
    Layout(
        content = {
            Box {
                MathNodeView(
                    node = base,
                    fontSizeSp = baseFontSizeSp,
                    color = color,
                    palette = palette,
                    isDisplay = isDisplay,
                    fontStyleOverride = fontStyleOverride
                )
            }
            if (superscript != null) {
                Box {
                    MathNodeView(
                        node = superscript,
                        fontSizeSp = scriptFontSizeSp,
                        color = color,
                        palette = palette,
                        isDisplay = false,
                        fontStyleOverride = fontStyleOverride
                    )
                }
            }
            if (subscript != null) {
                Box {
                    MathNodeView(
                        node = subscript,
                        fontSizeSp = scriptFontSizeSp,
                        color = color,
                        palette = palette,
                        isDisplay = false,
                        fontStyleOverride = fontStyleOverride
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val looseConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        val basePlaceable = measurables[0].measure(looseConstraints)
        var idx = 1
        val supPlaceable = if (superscript != null) measurables[idx++].measure(looseConstraints) else null
        val subPlaceable = if (subscript != null) measurables[idx].measure(looseConstraints) else null

        val scriptWidth = max(supPlaceable?.width ?: 0, subPlaceable?.width ?: 0)
        val gapPx = (1.dp).roundToPx()
        val totalWidth = basePlaceable.width + (if (scriptWidth > 0) gapPx + scriptWidth else 0)

        // Vertical positioning relative to base center
        val supOverlap = (basePlaceable.height * 0.45f).roundToInt()
        val subOverlap = (basePlaceable.height * 0.45f).roundToInt()

        val topExtend = if (supPlaceable != null) {
            max(0, supPlaceable.height - supOverlap)
        } else 0

        val bottomExtend = if (subPlaceable != null) {
            max(0, subPlaceable.height - subOverlap)
        } else 0

        // Ensure sup and sub don't collide when both are present
        val baseTopY = topExtend
        var supY = if (supPlaceable != null) baseTopY + supOverlap - supPlaceable.height else 0
        var subY = if (subPlaceable != null) baseTopY + basePlaceable.height - subOverlap else 0

        if (supPlaceable != null && subPlaceable != null) {
            val minGap = (1.dp).roundToPx()
            val overlap = (supY + supPlaceable.height + minGap) - subY
            if (overlap > 0) {
                supY -= overlap / 2
                subY += (overlap - overlap / 2)
            }
        }

        val minY = minOf(0, supY)
        val shiftDown = -minY
        val finalBaseY = baseTopY + shiftDown
        val finalSupY = supY + shiftDown
        val finalSubY = subY + shiftDown

        val totalHeight = maxOf(
            finalBaseY + basePlaceable.height,
            if (supPlaceable != null) finalSupY + supPlaceable.height else 0,
            if (subPlaceable != null) finalSubY + subPlaceable.height else 0
        ) + bottomExtend.coerceAtMost(2)

        layout(totalWidth, totalHeight) {
            basePlaceable.placeRelative(0, finalBaseY)
            val scriptX = basePlaceable.width + gapPx
            supPlaceable?.placeRelative(scriptX, finalSupY)
            subPlaceable?.placeRelative(scriptX, finalSubY)
        }
    }
}

@Composable
private fun SqrtLayout(
    radicand: MathNode,
    degree: MathNode?,
    fontSizeSp: Float,
    color: Color,
    palette: ReaderPalette,
    isDisplay: Boolean,
    fontStyleOverride: MathFontStyle?
) {
    val strokeWidthDp = (fontSizeSp * 0.068f).coerceIn(1.1f, 2.2f).dp
    val hookWidthDp = (fontSizeSp * 0.55f).dp
    val topBarClearanceDp = 4.dp
    val degreeSizeSp = (fontSizeSp * 0.58f).coerceAtLeast(9f)

    Layout(
        content = {
            Box(
                modifier = Modifier.padding(
                    top = topBarClearanceDp,
                    start = 2.dp,
                    end = 2.dp,
                    bottom = 1.dp
                )
            ) {
                MathNodeView(
                    node = radicand,
                    fontSizeSp = fontSizeSp,
                    color = color,
                    palette = palette,
                    isDisplay = isDisplay,
                    fontStyleOverride = fontStyleOverride
                )
            }
            if (degree != null) {
                Box {
                    MathNodeView(
                        node = degree,
                        fontSizeSp = degreeSizeSp,
                        color = color,
                        palette = palette,
                        isDisplay = false,
                        fontStyleOverride = fontStyleOverride
                    )
                }
            }
        },
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .drawWithContent {
                drawContent()
                val strokePx = strokeWidthDp.toPx()
                val hookW = hookWidthDp.toPx()
                val totalW = size.width
                val totalH = size.height
                val barStartX = totalW - (size.width - hookW).coerceAtLeast(0f)

                val topY = strokePx
                val bottomY = totalH - strokePx * 1.2f
                val midY = totalH * 0.56f
                val tickStartX = (barStartX - hookW).coerceAtLeast(strokePx)
                val tickPeakX = tickStartX + hookW * 0.25f
                val valleyX = tickStartX + hookW * 0.55f

                val path = Path().apply {
                    moveTo(tickStartX, midY + (bottomY - midY) * 0.15f)
                    lineTo(tickPeakX, midY)
                    lineTo(valleyX, bottomY)
                    lineTo(barStartX, topY)
                    lineTo(totalW - strokePx * 0.5f, topY)
                }
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val radPlaceable = measurables[0].measure(loose)
        val degPlaceable = if (degree != null) measurables[1].measure(loose) else null

        val hookPx = hookWidthDp.roundToPx()
        val degExtraLeft = if (degPlaceable != null) {
            max(0, degPlaceable.width - (hookPx * 0.65f).roundToInt())
        } else 0

        val radX = degExtraLeft + hookPx
        val totalWidth = radX + radPlaceable.width
        val totalHeight = max(radPlaceable.height, (degPlaceable?.height ?: 0) + (4.dp).roundToPx())

        layout(totalWidth, totalHeight) {
            radPlaceable.placeRelative(radX, totalHeight - radPlaceable.height)
            if (degPlaceable != null) {
                val degY = ((totalHeight * 0.45f).roundToInt() - degPlaceable.height).coerceAtLeast(0)
                degPlaceable.placeRelative(0, degY)
            }
        }
    }
}

@Composable
private fun ScalableDelimitedLayout(
    left: String,
    content: MathNode,
    right: String,
    fontSizeSp: Float,
    color: Color,
    palette: ReaderPalette,
    isDisplay: Boolean,
    fontStyleOverride: MathFontStyle?,
    isAlignStyle: Boolean = false
) {
    val delimWidthDp = (fontSizeSp * 0.36f).coerceIn(5f, 14f).dp
    val strokeWidthDp = (fontSizeSp * 0.068f).coerceIn(1.1f, 2.2f).dp
    val leftWidthDp = if (left.isNotEmpty()) delimWidthDp else 0.dp
    val rightWidthDp = if (right.isNotEmpty()) delimWidthDp else 0.dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 1.dp)
            .drawWithContent {
                drawContent()
                val strokePx = strokeWidthDp.toPx()
                val h = size.height
                val w = size.width
                if (left.isNotEmpty()) {
                    drawScalableDelimiter(
                        delim = left,
                        isLeft = true,
                        xStart = 0f,
                        width = leftWidthDp.toPx(),
                        height = h,
                        strokePx = strokePx,
                        color = color
                    )
                }
                if (right.isNotEmpty()) {
                    val rw = rightWidthDp.toPx()
                    drawScalableDelimiter(
                        delim = right,
                        isLeft = false,
                        xStart = w - rw,
                        width = rw,
                        height = h,
                        strokePx = strokePx,
                        color = color
                    )
                }
            }
            .padding(
                start = leftWidthDp + if (left.isNotEmpty()) 2.dp else 0.dp,
                end = rightWidthDp + if (right.isNotEmpty()) 2.dp else 0.dp,
                top = 2.dp,
                bottom = 2.dp
            )
    ) {
        if (isAlignStyle && content is MathNode.Matrix) {
            MatrixGridLayout(
                rows = content.rows,
                isAlignedEnv = true,
                fontSizeSp = fontSizeSp,
                color = color,
                palette = palette,
                isDisplay = isDisplay,
                fontStyleOverride = fontStyleOverride
            )
        } else {
            MathNodeView(
                node = content,
                fontSizeSp = fontSizeSp,
                color = color,
                palette = palette,
                isDisplay = isDisplay,
                fontStyleOverride = fontStyleOverride
            )
        }
    }
}

private fun DrawScope.drawScalableDelimiter(
    delim: String,
    isLeft: Boolean,
    xStart: Float,
    width: Float,
    height: Float,
    strokePx: Float,
    color: Color
) {
    val topY = strokePx
    val botY = height - strokePx
    val midY = height / 2f
    val leftX = xStart + strokePx
    val rightX = xStart + width - strokePx
    val style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (delim) {
        "(", ")" -> {
            val openLeft = delim == "("
            val edgeX = if (openLeft) rightX else leftX
            val curveX = if (openLeft) leftX else rightX
            val path = Path().apply {
                moveTo(edgeX, topY)
                cubicTo(curveX, height * 0.22f, curveX, height * 0.78f, edgeX, botY)
            }
            drawPath(path, color, style = style)
        }
        "[", "]" -> {
            val openLeft = delim == "["
            val spineX = if (openLeft) leftX else rightX
            val tipX = if (openLeft) rightX else leftX
            val path = Path().apply {
                moveTo(tipX, topY)
                lineTo(spineX, topY)
                lineTo(spineX, botY)
                lineTo(tipX, botY)
            }
            drawPath(path, color, style = style)
        }
        "⌊", "⌋" -> {
            val openLeft = delim == "⌊"
            val spineX = if (openLeft) leftX else rightX
            val tipX = if (openLeft) rightX else leftX
            val path = Path().apply {
                moveTo(spineX, topY)
                lineTo(spineX, botY)
                lineTo(tipX, botY)
            }
            drawPath(path, color, style = style)
        }
        "⌈", "⌉" -> {
            val openLeft = delim == "⌈"
            val spineX = if (openLeft) leftX else rightX
            val tipX = if (openLeft) rightX else leftX
            val path = Path().apply {
                moveTo(tipX, topY)
                lineTo(spineX, topY)
                lineTo(spineX, botY)
            }
            drawPath(path, color, style = style)
        }
        "{", "}" -> {
            val openLeft = delim == "{" || (isLeft && delim != "}")
            val tipX = if (openLeft) rightX else leftX
            val peakX = if (openLeft) leftX else rightX
            val spineX = (leftX + rightX) / 2f
            val r = minOf(width * 0.45f, height * 0.12f)
            val path = Path().apply {
                moveTo(tipX, topY)
                quadraticTo(spineX, topY, spineX, topY + r)
                lineTo(spineX, midY - r)
                quadraticTo(spineX, midY, peakX, midY)
                quadraticTo(spineX, midY, spineX, midY + r)
                lineTo(spineX, botY - r)
                quadraticTo(spineX, botY, tipX, botY)
            }
            drawPath(path, color, style = style)
        }
        "⟨", "⟩" -> {
            val openLeft = delim == "⟨"
            val edgeX = if (openLeft) rightX else leftX
            val apexX = if (openLeft) leftX else rightX
            val path = Path().apply {
                moveTo(edgeX, topY)
                lineTo(apexX, midY)
                lineTo(edgeX, botY)
            }
            drawPath(path, color, style = style)
        }
        "|" -> {
            val cx = (leftX + rightX) / 2f
            drawLine(color, Offset(cx, topY), Offset(cx, botY), strokeWidth = strokePx, cap = StrokeCap.Round)
        }
        "‖" -> {
            val gap = (width * 0.25f).coerceAtLeast(strokePx * 1.4f)
            val cx = (leftX + rightX) / 2f
            drawLine(color, Offset(cx - gap / 2f, topY), Offset(cx - gap / 2f, botY), strokeWidth = strokePx, cap = StrokeCap.Round)
            drawLine(color, Offset(cx + gap / 2f, topY), Offset(cx + gap / 2f, botY), strokeWidth = strokePx, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun AccentLayout(
    base: MathNode,
    kind: AccentKind,
    fontSizeSp: Float,
    color: Color,
    palette: ReaderPalette,
    isDisplay: Boolean,
    fontStyleOverride: MathFontStyle?
) {
    val isUnder = kind == AccentKind.UNDERLINE || kind == AccentKind.UNDERBRACE
    val accentSpaceDp = (fontSizeSp * 0.26f).coerceIn(4f, 9f).dp
    val strokeWidthDp = (fontSizeSp * 0.065f).coerceIn(1.0f, 2.0f).dp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .drawWithContent {
                drawContent()
                val strokePx = strokeWidthDp.toPx()
                val w = size.width
                val h = size.height
                val leftX = strokePx * 1.5f
                val rightX = (w - strokePx * 1.5f).coerceAtLeast(leftX + 2f)
                val midX = w / 2f
                val style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)

                when (kind) {
                    AccentKind.BAR, AccentKind.OVERLINE -> {
                        val y = strokePx * 2f
                        drawLine(color, Offset(leftX, y), Offset(rightX, y), strokeWidth = strokePx, cap = StrokeCap.Round)
                    }
                    AccentKind.UNDERLINE -> {
                        val y = h - strokePx * 1.5f
                        drawLine(color, Offset(leftX, y), Offset(rightX, y), strokeWidth = strokePx, cap = StrokeCap.Round)
                    }
                    AccentKind.HAT, AccentKind.WIDE_HAT -> {
                        val span = if (kind == AccentKind.WIDE_HAT) (rightX - leftX) else minOf(rightX - leftX, strokePx * 7f)
                        val sx = midX - span / 2f
                        val ex = midX + span / 2f
                        val topPeakY = strokePx * 1.2f
                        val baseY = accentSpaceDp.toPx() * 0.85f
                        val path = Path().apply {
                            moveTo(sx, baseY)
                            lineTo(midX, topPeakY)
                            lineTo(ex, baseY)
                        }
                        drawPath(path, color, style = style)
                    }
                    AccentKind.CHECK -> {
                        val span = minOf(rightX - leftX, strokePx * 7f)
                        val sx = midX - span / 2f
                        val ex = midX + span / 2f
                        val topY = strokePx * 1.2f
                        val valleyY = accentSpaceDp.toPx() * 0.85f
                        val path = Path().apply {
                            moveTo(sx, topY)
                            lineTo(midX, valleyY)
                            lineTo(ex, topY)
                        }
                        drawPath(path, color, style = style)
                    }
                    AccentKind.VEC, AccentKind.OVER_RIGHT_ARROW -> {
                        val y = accentSpaceDp.toPx() * 0.55f
                        val headSize = strokePx * 2.4f
                        drawLine(color, Offset(leftX, y), Offset(rightX, y), strokeWidth = strokePx, cap = StrokeCap.Round)
                        val head = Path().apply {
                            moveTo(rightX - headSize, y - headSize * 0.75f)
                            lineTo(rightX, y)
                            lineTo(rightX - headSize, y + headSize * 0.75f)
                        }
                        drawPath(head, color, style = style)
                    }
                    AccentKind.OVER_LEFT_ARROW -> {
                        val y = accentSpaceDp.toPx() * 0.55f
                        val headSize = strokePx * 2.4f
                        drawLine(color, Offset(leftX, y), Offset(rightX, y), strokeWidth = strokePx, cap = StrokeCap.Round)
                        val head = Path().apply {
                            moveTo(leftX + headSize, y - headSize * 0.75f)
                            lineTo(leftX, y)
                            lineTo(leftX + headSize, y + headSize * 0.75f)
                        }
                        drawPath(head, color, style = style)
                    }
                    AccentKind.TILDE, AccentKind.WIDE_TILDE -> {
                        val span = if (kind == AccentKind.WIDE_TILDE) (rightX - leftX) else minOf(rightX - leftX, strokePx * 8f)
                        val sx = midX - span / 2f
                        val ex = midX + span / 2f
                        val cy = accentSpaceDp.toPx() * 0.55f
                        val amp = strokePx * 1.8f
                        val path = Path().apply {
                            moveTo(sx, cy + amp * 0.5f)
                            cubicTo(sx + span * 0.3f, cy - amp, sx + span * 0.7f, cy + amp, ex, cy - amp * 0.5f)
                        }
                        drawPath(path, color, style = style)
                    }
                    AccentKind.DOT -> {
                        val cy = accentSpaceDp.toPx() * 0.5f
                        drawCircle(color, radius = strokePx * 1.15f, center = Offset(midX, cy))
                    }
                    AccentKind.DDOT -> {
                        val cy = accentSpaceDp.toPx() * 0.5f
                        val offset = strokePx * 2.0f
                        drawCircle(color, radius = strokePx * 1.1f, center = Offset(midX - offset, cy))
                        drawCircle(color, radius = strokePx * 1.1f, center = Offset(midX + offset, cy))
                    }
                    AccentKind.BREVE, AccentKind.OVERBRACE -> {
                        val span = minOf(rightX - leftX, strokePx * 8f)
                        val sx = midX - span / 2f
                        val ex = midX + span / 2f
                        val topY = strokePx * 1.5f
                        val valleyY = accentSpaceDp.toPx() * 0.85f
                        val path = Path().apply {
                            moveTo(sx, topY)
                            quadraticTo(midX, valleyY, ex, topY)
                        }
                        drawPath(path, color, style = style)
                    }
                    AccentKind.UNDERBRACE -> {
                        val y = h - strokePx * 1.5f
                        drawLine(color, Offset(leftX, y), Offset(rightX, y), strokeWidth = strokePx, cap = StrokeCap.Round)
                    }
                }
            }
            .padding(
                top = if (!isUnder) accentSpaceDp else 0.dp,
                bottom = if (isUnder) accentSpaceDp else 0.dp
            )
    ) {
        MathNodeView(
            node = base,
            fontSizeSp = fontSizeSp,
            color = color,
            palette = palette,
            isDisplay = isDisplay,
            fontStyleOverride = fontStyleOverride
        )
    }
}

@Composable
private fun MatrixGridLayout(
    rows: List<List<MathNode>>,
    isAlignedEnv: Boolean,
    fontSizeSp: Float,
    color: Color,
    palette: ReaderPalette,
    isDisplay: Boolean,
    fontStyleOverride: MathFontStyle?
) {
    if (rows.isEmpty()) return
    val numCols = rows.maxOfOrNull { it.size } ?: 0
    if (numCols == 0) return

    val cellSizeSp = if (isAlignedEnv) fontSizeSp else (fontSizeSp * 0.90f).coerceAtLeast(10f)
    val colGapDp = if (isAlignedEnv) 4.dp else 12.dp
    val rowGapDp = if (isAlignedEnv) 6.dp else 4.dp

    Layout(
        content = {
            for (row in rows) {
                for (colIdx in 0 until numCols) {
                    val cellNode = row.getOrNull(colIdx) ?: MathNode.Row(emptyList())
                    Box {
                        MathNodeView(
                            node = cellNode,
                            fontSizeSp = cellSizeSp,
                            color = color,
                            palette = palette,
                            isDisplay = isDisplay && isAlignedEnv,
                            fontStyleOverride = fontStyleOverride
                        )
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val loose = Constraints(minWidth = 0, maxWidth = constraints.maxWidth, minHeight = 0, maxHeight = constraints.maxHeight)
        val numRows = rows.size
        val placeables = Array(numRows) { r ->
            Array(numCols) { c ->
                measurables[r * numCols + c].measure(loose)
            }
        }

        val colWidths = IntArray(numCols)
        val rowHeights = IntArray(numRows)

        for (r in 0 until numRows) {
            for (c in 0 until numCols) {
                val p = placeables[r][c]
                if (p.width > colWidths[c]) colWidths[c] = p.width
                if (p.height > rowHeights[r]) rowHeights[r] = p.height
            }
        }

        val colGapPx = colGapDp.roundToPx()
        val rowGapPx = rowGapDp.roundToPx()

        val totalWidth = colWidths.sum() + colGapPx * (numCols - 1).coerceAtLeast(0)
        val totalHeight = rowHeights.sum() + rowGapPx * (numRows - 1).coerceAtLeast(0)

        layout(totalWidth, totalHeight) {
            var y = 0
            for (r in 0 until numRows) {
                var x = 0
                val rh = rowHeights[r]
                for (c in 0 until numCols) {
                    val p = placeables[r][c]
                    val cw = colWidths[c]
                    val cellX = when {
                        // In align/cases environments: col 0 is right-aligned (or left in cases), col 1 is left-aligned
                        isAlignedEnv && numCols >= 2 && c % 2 == 0 -> x + (cw - p.width)
                        isAlignedEnv -> x
                        else -> x + (cw - p.width) / 2
                    }
                    val cellY = y + (rh - p.height) / 2
                    p.placeRelative(cellX, cellY)
                    x += cw + colGapPx
                }
                y += rh + rowGapPx
            }
        }
    }
}
