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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.verso.core.latex.LatexParser
import app.verso.core.model.Block
import app.verso.core.model.CodeBlock
import app.verso.core.model.Divider
import app.verso.core.model.Heading
import app.verso.core.model.ImageBlock
import app.verso.core.model.ListItem
import app.verso.core.model.MathBlock
import app.verso.core.model.Paragraph
import app.verso.core.model.Quote
import app.verso.core.model.TableBlock
import app.verso.core.store.ReaderSettings
import kotlinx.coroutines.delay

@Composable
fun BlockView(
    block: Block,
    settings: ReaderSettings,
    palette: ReaderPalette,
    searchQuery: String? = null,
    activeMatchRawStart: Int? = null,
    modifier: Modifier = Modifier
) {
    when (block) {
        is Paragraph -> {
            val rich = rememberRichInlineText(
                inlineText = block.text,
                fontSizeSp = settings.fontSizeSp,
                palette = palette,
                searchQuery = searchQuery,
                activeMatchRawStart = activeMatchRawStart
            )
            Text(
                text = rich.annotatedString,
                inlineContent = rich.inlineContent,
                style = settings.bodyStyle(palette),
                modifier = modifier.fillMaxWidth()
            )
        }
        is Heading -> {
            val headingStyle = settings.headingStyle(block.level, palette)
            val rich = rememberRichInlineText(
                inlineText = block.text,
                fontSizeSp = headingStyle.fontSize.value,
                palette = palette,
                searchQuery = searchQuery,
                activeMatchRawStart = activeMatchRawStart
            )
            Text(
                text = rich.annotatedString,
                inlineContent = rich.inlineContent,
                style = headingStyle,
                modifier = modifier.fillMaxWidth()
            )
        }
        is ListItem -> {
            val indentDp = (block.depth * 1.25f * settings.fontSizeSp).dp
            val markerWidthDp = (1.5f * settings.fontSizeSp).dp
            val rich = rememberRichInlineText(
                inlineText = block.text,
                fontSizeSp = settings.fontSizeSp,
                palette = palette,
                searchQuery = searchQuery,
                activeMatchRawStart = activeMatchRawStart
            )

            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = indentDp)
            ) {
                if (block.marker.isNotEmpty()) {
                    Text(
                        text = block.marker,
                        style = settings.bodyStyle(palette),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.width(markerWidthDp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(markerWidthDp))
                }
                Text(
                    text = rich.annotatedString,
                    inlineContent = rich.inlineContent,
                    style = settings.bodyStyle(palette),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        is Quote -> {
            val indentDp = (12 * block.depth).dp
            val rich = rememberRichInlineText(
                inlineText = block.text,
                fontSizeSp = settings.fontSizeSp,
                palette = palette,
                searchQuery = searchQuery,
                activeMatchRawStart = activeMatchRawStart
            )
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = indentDp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.codeBackground.copy(alpha = 0.45f))
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.link.copy(alpha = 0.7f))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = rich.annotatedString,
                    inlineContent = rich.inlineContent,
                    style = settings.bodyStyle(palette).copy(
                        color = palette.text.copy(alpha = 0.88f),
                        fontStyle = FontStyle.Italic
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        is CodeBlock -> {
            val context = LocalContext.current
            var copied by remember { mutableStateOf(false) }

            LaunchedEffect(copied) {
                if (copied) {
                    delay(1600)
                    copied = false
                }
            }

            val codeAnnotated = remember(block.code, searchQuery, activeMatchRawStart, palette) {
                buildAnnotatedString {
                    append(block.code)
                    if (!searchQuery.isNullOrBlank()) {
                        val q = searchQuery.trim()
                        var pos = 0
                        while (pos < block.code.length) {
                            val found = block.code.indexOf(q, pos, ignoreCase = true)
                            if (found == -1) break
                            val end = found + q.length
                            val isActive = activeMatchRawStart != null && found == activeMatchRawStart
                            val bg = if (isActive) {
                                if (palette.isDark) Color(0xFFD97706) else Color(0xFFFF9800)
                            } else {
                                if (palette.isDark) Color(0xFF6B4F1D) else Color(0xFFFFE082)
                            }
                            val fg = if (isActive) Color.White else if (palette.isDark) Color(0xFFFFF8E1) else Color(0xFF1A1A1A)
                            addStyle(
                                SpanStyle(background = bg, color = fg, fontWeight = FontWeight.Bold),
                                found,
                                end
                            )
                            pos = end
                        }
                    }
                }
            }

            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.codeBackground)
                    .border(1.dp, palette.rule.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(palette.rule.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = block.language?.uppercase()?.takeIf { it.isNotBlank() } ?: "CODE",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.muted
                    )
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.link,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("code", block.code))
                                copied = true
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("copy_code_button")
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = codeAnnotated,
                        style = settings.monoStyle(palette)
                    )
                }
            }
        }
        is MathBlock -> {
            val isMathMatch = !searchQuery.isNullOrBlank() && (
                block.latex.contains(searchQuery.trim(), ignoreCase = true) ||
                LatexParser.toReadablePlainText(block.latex).contains(searchQuery.trim(), ignoreCase = true)
            )
            val mathBorderModifier = if (isMathMatch) {
                val isActive = activeMatchRawStart != null
                modifier.border(
                    width = if (isActive) 2.dp else 1.5.dp,
                    color = if (isActive) (if (palette.isDark) Color(0xFFF59E0B) else Color(0xFFFF9800)) else palette.link,
                    shape = RoundedCornerShape(10.dp)
                )
            } else modifier

            MathBlockView(
                latex = block.latex,
                settings = settings,
                palette = palette,
                modifier = mathBorderModifier
            )
        }
        is TableBlock -> {
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, palette.rule.copy(alpha = 0.8f), RoundedCornerShape(10.dp))
            ) {
                block.rows.forEachIndexed { rowIndex, row ->
                    val rowBg = if (rowIndex % 2 == 1) {
                        palette.codeBackground.copy(alpha = 0.4f)
                    } else {
                        Color.Transparent
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        row.forEachIndexed { cellIndex, cell ->
                            val headerInline = block.header.getOrNull(cellIndex)
                            val headerLabel = headerInline?.text?.trim().takeUnless { it.isNullOrEmpty() }
                                ?: "Column ${cellIndex + 1}"

                            val cellRich = rememberRichInlineText(
                                inlineText = cell,
                                fontSizeSp = (settings.fontSizeSp * 0.92f).coerceAtLeast(13f),
                                palette = palette,
                                searchQuery = searchQuery,
                                activeMatchRawStart = null
                            )

                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "$headerLabel:",
                                    style = settings.bodyStyle(palette).copy(
                                        fontSize = (settings.fontSizeSp * 0.88f).coerceAtLeast(12f).sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.muted
                                    ),
                                    modifier = Modifier.width((settings.fontSizeSp * 5.2f).coerceIn(88f, 150f).dp)
                                )
                                Text(
                                    text = cellRich.annotatedString,
                                    inlineContent = cellRich.inlineContent,
                                    style = settings.bodyStyle(palette).copy(
                                        fontSize = (settings.fontSizeSp * 0.92f).coerceAtLeast(13f).sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (rowIndex < block.rows.size - 1) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = palette.rule.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
        is ImageBlock -> {
            val placeholder = if (block.alt.isBlank()) "[Image]" else "[Image: ${block.alt}]"
            Text(
                text = placeholder,
                style = settings.bodyStyle(palette).copy(
                    color = palette.muted,
                    fontStyle = FontStyle.Italic
                ),
                modifier = modifier.fillMaxWidth()
            )
        }
        is Divider -> {
            HorizontalDivider(
                thickness = 1.dp,
                color = palette.rule,
                modifier = modifier.padding(vertical = 8.dp)
            )
        }
    }
}
