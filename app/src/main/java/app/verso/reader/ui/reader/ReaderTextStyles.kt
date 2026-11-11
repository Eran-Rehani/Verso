package app.verso.reader.ui.reader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp
import app.verso.core.store.FontChoice
import app.verso.core.store.ReadAlign
import app.verso.core.store.ReaderSettings

fun ReaderSettings.bodyStyle(palette: ReaderPalette): TextStyle {
    val family = when (font) {
        FontChoice.Sans -> FontFamily.SansSerif
        FontChoice.Serif -> FontFamily.Serif
        FontChoice.Mono -> FontFamily.Monospace
    }
    val alignStyle = when (align) {
        ReadAlign.Start -> TextAlign.Start
        ReadAlign.Justify -> TextAlign.Justify
    }
    val hyphenStyle = if (align == ReadAlign.Justify) Hyphens.Auto else Hyphens.None

    return TextStyle(
        fontSize = fontSizeSp.sp,
        lineHeight = (fontSizeSp * lineHeight).sp,
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        color = palette.text,
        textAlign = alignStyle,
        textDirection = TextDirection.Content,
        lineBreak = LineBreak.Paragraph,
        hyphens = hyphenStyle
    )
}

fun ReaderSettings.headingStyle(level: Int, palette: ReaderPalette): TextStyle {
    val scale = when (level) {
        1 -> 1.6f
        2 -> 1.4f
        3 -> 1.2f
        else -> 1.0f
    }
    val family = when (font) {
        FontChoice.Sans -> FontFamily.SansSerif
        FontChoice.Serif -> FontFamily.Serif
        FontChoice.Mono -> FontFamily.Monospace
    }
    val alignStyle = when (align) {
        ReadAlign.Start -> TextAlign.Start
        ReadAlign.Justify -> TextAlign.Justify
    }

    return TextStyle(
        fontSize = (fontSizeSp * scale).sp,
        lineHeight = (fontSizeSp * scale * lineHeight).sp,
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        color = palette.text,
        textAlign = alignStyle,
        textDirection = TextDirection.Content,
        lineBreak = LineBreak.Paragraph
    )
}

fun ReaderSettings.monoStyle(palette: ReaderPalette): TextStyle {
    val monoSize = fontSizeSp * 0.92f
    return TextStyle(
        fontSize = monoSize.sp,
        lineHeight = (monoSize * lineHeight).sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        color = palette.text,
        textAlign = TextAlign.Start,
        textDirection = TextDirection.Content,
        lineBreak = LineBreak.Paragraph
    )
}
