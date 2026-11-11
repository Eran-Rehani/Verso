package app.verso.reader.ui.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import app.verso.core.model.InlineText
import app.verso.core.model.SpanKind

fun InlineText.toAnnotatedString(palette: ReaderPalette): AnnotatedString {
    val raw = this.text
    return buildAnnotatedString {
        append(raw)
        for (span in spans) {
            val s = span.start.coerceIn(0, raw.length)
            val e = span.end.coerceIn(0, raw.length)
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
                SpanKind.Math -> addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = palette.codeBackground
                    ), s, e
                )
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
}
