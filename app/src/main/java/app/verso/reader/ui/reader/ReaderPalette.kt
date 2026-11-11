package app.verso.reader.ui.reader

import androidx.compose.ui.graphics.Color
import app.verso.core.store.ThemeChoice

data class ReaderPalette(
    val background: Color,
    val text: Color,
    val muted: Color,
    val link: Color,
    val codeBackground: Color,
    val rule: Color,
    val isDark: Boolean
)

fun paletteFor(theme: ThemeChoice, systemDark: Boolean): ReaderPalette {
    val resolved = when (theme) {
        ThemeChoice.System -> if (systemDark) ThemeChoice.Dark else ThemeChoice.Light
        else -> theme
    }
    return when (resolved) {
        ThemeChoice.Light -> ReaderPalette(
            background = Color(0xFFFAFAF7),
            text = Color(0xFF1A1A1A),
            muted = Color(0xFF6B6B6B),
            link = Color(0xFF1A5FB4),
            codeBackground = Color(0xFFEFEFEA),
            rule = Color(0xFFD9D9D4),
            isDark = false
        )
        ThemeChoice.Sepia -> ReaderPalette(
            background = Color(0xFFF4ECD8),
            text = Color(0xFF5B4636),
            muted = Color(0xFF6F5E4D),
            link = Color(0xFF8A4B08),
            codeBackground = Color(0xFFEADFC4),
            rule = Color(0xFFD8CBAA),
            isDark = false
        )
        ThemeChoice.Dark -> ReaderPalette(
            background = Color(0xFF1B1B1D),
            text = Color(0xFFD6D6D6),
            muted = Color(0xFF9A9A9A),
            link = Color(0xFF8AB4F8),
            codeBackground = Color(0xFF2A2B2E),
            rule = Color(0xFF3A3B3F),
            isDark = true
        )
        ThemeChoice.Black -> ReaderPalette(
            background = Color(0xFF000000),
            text = Color(0xFFC8C8C8),
            muted = Color(0xFF8A8A8A),
            link = Color(0xFF7AA2F7),
            codeBackground = Color(0xFF141414),
            rule = Color(0xFF2A2A2A),
            isDark = true
        )
        ThemeChoice.System -> error("Unreachable")
    }
}
