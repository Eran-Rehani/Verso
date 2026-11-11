package app.verso.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun BlockView(
    block: Block,
    settings: ReaderSettings,
    palette: ReaderPalette,
    modifier: Modifier = Modifier
) {
    when (block) {
        is Paragraph -> {
            Text(
                text = block.text.toAnnotatedString(palette),
                style = settings.bodyStyle(palette),
                modifier = modifier.fillMaxWidth()
            )
        }
        is Heading -> {
            Text(
                text = block.text.toAnnotatedString(palette),
                style = settings.headingStyle(block.level, palette),
                modifier = modifier.fillMaxWidth()
            )
        }
        is ListItem -> {
            val indentDp = (block.depth * 1.25f * settings.fontSizeSp).dp
            val markerWidthDp = (1.5f * settings.fontSizeSp).dp

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
                    text = block.text.toAnnotatedString(palette),
                    style = settings.bodyStyle(palette),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        is Quote -> {
            val indentDp = (12 * (block.depth + 1)).dp
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(start = indentDp)
            ) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .padding(vertical = 2.dp)
                        .background(palette.muted)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = block.text.toAnnotatedString(palette),
                    style = settings.bodyStyle(palette).copy(color = palette.muted),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        is CodeBlock -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.codeBackground)
                    .padding(8.dp)
            ) {
                Text(
                    text = block.code,
                    style = settings.monoStyle(palette),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        is MathBlock -> {
            Box(
                modifier = modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(palette.codeBackground)
                    .padding(8.dp)
            ) {
                Text(
                    text = block.latex,
                    style = settings.monoStyle(palette),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        is TableBlock -> {
            Column(modifier = modifier.fillMaxWidth()) {
                val headerTexts = block.header.map { it.text }
                block.rows.forEachIndexed { rowIndex, row ->
                    val rowAnnotated = buildAnnotatedString {
                        row.forEachIndexed { cellIndex, cell ->
                            val headerLabel = headerTexts.getOrNull(cellIndex) ?: "Column ${cellIndex + 1}"
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append("$headerLabel: ")
                            pop()
                            append(cell.text)
                            if (cellIndex < row.size - 1) {
                                append("\n")
                            }
                        }
                    }

                    Text(
                        text = rowAnnotated,
                        style = settings.bodyStyle(palette),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )

                    if (rowIndex < block.rows.size - 1) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = palette.rule,
                            modifier = Modifier.padding(vertical = 4.dp)
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
