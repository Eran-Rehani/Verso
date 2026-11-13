package app.verso.reader.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.verso.core.latex.AccentKind
import app.verso.core.latex.LatexParser
import app.verso.core.latex.MathFontStyle
import app.verso.core.latex.MathNode
import app.verso.core.latex.MatrixEnvType
import app.verso.core.latex.SymbolCategory
import app.verso.core.store.ReaderSettings
import app.verso.core.tts.TtsTextFormatter
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun MathBlockView(
    latex: String,
    settings: ReaderSettings,
    palette: ReaderPalette,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val parsed = remember(latex) { LatexParser.parseEquation(latex) }
    val readableDesc = remember(latex) { LatexParser.toReadablePlainText(latex) }
    val baseMathSizeSp = settings.fontSizeSp * 1.05f
    val hScroll = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.codeBackground.copy(alpha = 0.65f))
            .border(1.dp, palette.rule.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
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
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(hScroll)
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

@Composable
fun LatexEquationInspectorDialog(
    initialLatex: String,
    settings: ReaderSettings,
    palette: ReaderPalette,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var activeLatex by remember(initialLatex) { mutableStateOf(initialLatex) }
    var isEditingLatex by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableFloatStateOf(1.3f) }
    var copiedLatex by remember { mutableStateOf(false) }
    var copiedUnicode by remember { mutableStateOf(false) }

    LaunchedEffect(copiedLatex) {
        if (copiedLatex) {
            delay(1500)
            copiedLatex = false
        }
    }

    LaunchedEffect(copiedUnicode) {
        if (copiedUnicode) {
            delay(1500)
            copiedUnicode = false
        }
    }

    val parsed = remember(activeLatex) { LatexParser.parseEquation(activeLatex) }
    val readableUnicode = remember(activeLatex) { LatexParser.toReadablePlainText(activeLatex) }
    val spokenMath = remember(activeLatex) { TtsTextFormatter.formatLatex(activeLatex) }
    val renderedFontSizeSp = (settings.fontSizeSp * 1.25f * zoomScale).coerceIn(14f, 56f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.background)
                .border(1.dp, palette.rule.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
                .testTag("latex_equation_inspector_dialog")
        ) {
            // Top Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Rendered LaTeX",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.text
                    )
                    if (!parsed.tag.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette.link.copy(alpha = 0.15f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "(${parsed.tag})",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.link
                            )
                        }
                    }
                }

                // Zoom controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.codeBackground)
                        .border(1.dp, palette.rule.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                ) {
                    IconButton(
                        onClick = { zoomScale = (zoomScale - 0.2f).coerceAtLeast(0.8f) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("equation_zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Zoom out equation",
                            tint = palette.text,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "${(zoomScale * 100).roundToInt()}%",
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.link,
                        modifier = Modifier
                            .clickable { zoomScale = 1.3f }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .testTag("equation_zoom_reset_button")
                    )
                    IconButton(
                        onClick = { zoomScale = (zoomScale + 0.2f).coerceAtMost(2.5f) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("equation_zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Zoom in equation",
                            tint = palette.text,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("equation_inspector_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close rendered LaTeX view",
                        tint = palette.muted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary 2D Rendered LaTeX Stage
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 104.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.codeBackground.copy(alpha = 0.8f))
                    .border(1.2.dp, palette.link.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 22.dp)
                    .testTag("equation_inspector_rendered_stage")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    MathNodeView(
                        node = parsed.root,
                        fontSizeSp = renderedFontSizeSp,
                        color = palette.text,
                        palette = palette,
                        isDisplay = true,
                        isTopLevel = true
                    )

                    if (!parsed.tag.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "(${parsed.tag})",
                            style = TextStyle(
                                fontSize = (renderedFontSizeSp * 0.82f).sp,
                                fontFamily = FontFamily.Serif,
                                color = palette.muted
                            )
                        )
                    }
                }
            }

            // Spoken & Unicode Math Summary
            if (readableUnicode.isNotBlank() || spokenMath.isNotBlank()) {
                Spacer(modifier = Modifier.height(14.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.codeBackground.copy(alpha = 0.45f))
                        .border(1.dp, palette.rule.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "READABLE MATH",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.muted
                        )
                        Text(
                            text = if (copiedUnicode) "Copied" else "Copy Math",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.link,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("math", readableUnicode.ifBlank { activeLatex }))
                                    copiedUnicode = true
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("equation_copy_unicode_button")
                        )
                    }

                    if (readableUnicode.isNotBlank()) {
                        Text(
                            text = readableUnicode,
                            fontSize = 14.5.sp,
                            fontFamily = FontFamily.Serif,
                            color = palette.text,
                            modifier = Modifier.testTag("equation_unicode_text")
                        )
                    }

                    if (spokenMath.isNotBlank()) {
                        Text(
                            text = "“$spokenMath”",
                            fontSize = 12.5.sp,
                            fontStyle = FontStyle.Italic,
                            color = palette.muted,
                            modifier = Modifier.testTag("equation_spoken_text")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // LaTeX Source & Interactive Live Editor
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.codeBackground)
                    .border(1.dp, palette.rule.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                    .testTag("equation_latex_source_box")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.rule.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "LATEX SOURCE",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.muted
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (activeLatex != initialLatex) {
                            Text(
                                text = "Reset",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.muted,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { activeLatex = initialLatex }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                    .testTag("equation_reset_latex_button")
                            )
                        }

                        Text(
                            text = if (isEditingLatex) "Done" else "Edit LaTeX",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.link,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { isEditingLatex = !isEditingLatex }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("equation_toggle_edit_button")
                        )

                        Text(
                            text = if (copiedLatex) "Copied" else "Copy LaTeX",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.link,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("latex", activeLatex))
                                    copiedLatex = true
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("equation_copy_latex_button")
                        )
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = palette.rule.copy(alpha = 0.45f))

                if (isEditingLatex) {
                    BasicTextField(
                        value = activeLatex,
                        onValueChange = { activeLatex = it },
                        textStyle = TextStyle(
                            fontSize = 13.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.text
                        ),
                        cursorBrush = SolidColor(palette.link),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("equation_live_edit_input")
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = activeLatex,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.text,
                            modifier = Modifier.testTag("equation_latex_source_text")
                        )
                    }
                }
            }
        }
    }
}

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
    val reusablePath = remember { Path() }

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

                reusablePath.reset()
                reusablePath.moveTo(tickStartX, midY + (bottomY - midY) * 0.15f)
                reusablePath.lineTo(tickPeakX, midY)
                reusablePath.lineTo(valleyX, bottomY)
                reusablePath.lineTo(barStartX, topY)
                reusablePath.lineTo(totalW - strokePx * 0.5f, topY)

                drawPath(
                    path = reusablePath,
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
    val reusablePath = remember { Path() }

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
                        color = color,
                        reusablePath = reusablePath
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
                        color = color,
                        reusablePath = reusablePath
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
    color: Color,
    reusablePath: Path
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
            reusablePath.reset()
            reusablePath.moveTo(edgeX, topY)
            reusablePath.cubicTo(curveX, height * 0.22f, curveX, height * 0.78f, edgeX, botY)
            drawPath(reusablePath, color, style = style)
        }
        "[", "]" -> {
            val openLeft = delim == "["
            val spineX = if (openLeft) leftX else rightX
            val tipX = if (openLeft) rightX else leftX
            reusablePath.reset()
            reusablePath.moveTo(tipX, topY)
            reusablePath.lineTo(spineX, topY)
            reusablePath.lineTo(spineX, botY)
            reusablePath.lineTo(tipX, botY)
            drawPath(reusablePath, color, style = style)
        }
        "⌊", "⌋" -> {
            val openLeft = delim == "⌊"
            val spineX = if (openLeft) leftX else rightX
            val tipX = if (openLeft) rightX else leftX
            reusablePath.reset()
            reusablePath.moveTo(spineX, topY)
            reusablePath.lineTo(spineX, botY)
            reusablePath.lineTo(tipX, botY)
            drawPath(reusablePath, color, style = style)
        }
        "⌈", "⌉" -> {
            val openLeft = delim == "⌈"
            val spineX = if (openLeft) leftX else rightX
            val tipX = if (openLeft) rightX else leftX
            reusablePath.reset()
            reusablePath.moveTo(tipX, topY)
            reusablePath.lineTo(spineX, topY)
            reusablePath.lineTo(spineX, botY)
            drawPath(reusablePath, color, style = style)
        }
        "{", "}" -> {
            val openLeft = delim == "{" || (isLeft && delim != "}")
            val tipX = if (openLeft) rightX else leftX
            val peakX = if (openLeft) leftX else rightX
            val spineX = (leftX + rightX) / 2f
            val r = minOf(width * 0.45f, height * 0.12f)
            reusablePath.reset()
            reusablePath.moveTo(tipX, topY)
            reusablePath.quadraticTo(spineX, topY, spineX, topY + r)
            reusablePath.lineTo(spineX, midY - r)
            reusablePath.quadraticTo(spineX, midY, peakX, midY)
            reusablePath.quadraticTo(spineX, midY, spineX, midY + r)
            reusablePath.lineTo(spineX, botY - r)
            reusablePath.quadraticTo(spineX, botY, tipX, botY)
            drawPath(reusablePath, color, style = style)
        }
        "⟨", "⟩" -> {
            val openLeft = delim == "⟨"
            val edgeX = if (openLeft) rightX else leftX
            val apexX = if (openLeft) leftX else rightX
            reusablePath.reset()
            reusablePath.moveTo(edgeX, topY)
            reusablePath.lineTo(apexX, midY)
            reusablePath.lineTo(edgeX, botY)
            drawPath(reusablePath, color, style = style)
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
    val reusablePath = remember { Path() }

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
                        reusablePath.reset()
                        reusablePath.moveTo(sx, baseY)
                        reusablePath.lineTo(midX, topPeakY)
                        reusablePath.lineTo(ex, baseY)
                        drawPath(reusablePath, color, style = style)
                    }
                    AccentKind.CHECK -> {
                        val span = minOf(rightX - leftX, strokePx * 7f)
                        val sx = midX - span / 2f
                        val ex = midX + span / 2f
                        val topY = strokePx * 1.2f
                        val valleyY = accentSpaceDp.toPx() * 0.85f
                        reusablePath.reset()
                        reusablePath.moveTo(sx, topY)
                        reusablePath.lineTo(midX, valleyY)
                        reusablePath.lineTo(ex, topY)
                        drawPath(reusablePath, color, style = style)
                    }
                    AccentKind.VEC, AccentKind.OVER_RIGHT_ARROW -> {
                        val y = accentSpaceDp.toPx() * 0.55f
                        val headSize = strokePx * 2.4f
                        drawLine(color, Offset(leftX, y), Offset(rightX, y), strokeWidth = strokePx, cap = StrokeCap.Round)
                        reusablePath.reset()
                        reusablePath.moveTo(rightX - headSize, y - headSize * 0.75f)
                        reusablePath.lineTo(rightX, y)
                        reusablePath.lineTo(rightX - headSize, y + headSize * 0.75f)
                        drawPath(reusablePath, color, style = style)
                    }
                    AccentKind.OVER_LEFT_ARROW -> {
                        val y = accentSpaceDp.toPx() * 0.55f
                        val headSize = strokePx * 2.4f
                        drawLine(color, Offset(leftX, y), Offset(rightX, y), strokeWidth = strokePx, cap = StrokeCap.Round)
                        reusablePath.reset()
                        reusablePath.moveTo(leftX + headSize, y - headSize * 0.75f)
                        reusablePath.lineTo(leftX, y)
                        reusablePath.lineTo(leftX + headSize, y + headSize * 0.75f)
                        drawPath(reusablePath, color, style = style)
                    }
                    AccentKind.TILDE, AccentKind.WIDE_TILDE -> {
                        val span = if (kind == AccentKind.WIDE_TILDE) (rightX - leftX) else minOf(rightX - leftX, strokePx * 8f)
                        val sx = midX - span / 2f
                        val ex = midX + span / 2f
                        val cy = accentSpaceDp.toPx() * 0.55f
                        val amp = strokePx * 1.8f
                        reusablePath.reset()
                        reusablePath.moveTo(sx, cy + amp * 0.5f)
                        reusablePath.cubicTo(sx + span * 0.3f, cy - amp, sx + span * 0.7f, cy + amp, ex, cy - amp * 0.5f)
                        drawPath(reusablePath, color, style = style)
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
                        reusablePath.reset()
                        reusablePath.moveTo(sx, topY)
                        reusablePath.quadraticTo(midX, valleyY, ex, topY)
                        drawPath(reusablePath, color, style = style)
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
