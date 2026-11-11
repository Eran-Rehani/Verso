package app.verso.core.store

import kotlinx.serialization.Serializable
import java.io.File
import kotlin.math.roundToInt

@Serializable
enum class ThemeChoice {
    System,
    Light,
    Sepia,
    Dark,
    Black
}

@Serializable
enum class FontChoice {
    Sans,
    Serif,
    Mono
}

@Serializable
enum class ReadAlign {
    Start,
    Justify
}

@Serializable
data class ReaderSettings(
    val theme: ThemeChoice = ThemeChoice.System,
    val font: FontChoice = FontChoice.Serif,
    val fontSizeSp: Float = 20f,
    val lineHeight: Float = 1.5f,
    val paragraphSpacingEm: Float = 0.8f,
    val marginDp: Int = 20,
    val align: ReadAlign = ReadAlign.Start,
    val keepScreenOn: Boolean = false
)

object SettingsLimits {
    val FONT_SIZE = 12f..48f
    val LINE_HEIGHT = 1.0f..2.4f
    val PARAGRAPH_SPACING = 0f..2f
    val MARGIN = 0..48
}

fun ReaderSettings.coerced(): ReaderSettings {
    // Snap fontSizeSp: step 1, 12..48
    val clampedFontSize = fontSizeSp.coerceIn(SettingsLimits.FONT_SIZE)
    val snappedFontSize = clampedFontSize.roundToInt().toFloat()

    // Snap lineHeight: step 0.05, 1.0..2.4
    val clampedLineHeight = lineHeight.coerceIn(SettingsLimits.LINE_HEIGHT)
    val snappedLineHeight = (clampedLineHeight * 20f).roundToInt() / 20f

    // Snap paragraphSpacingEm: step 0.1, 0.0..2.0
    val clampedParaSpacing = paragraphSpacingEm.coerceIn(SettingsLimits.PARAGRAPH_SPACING)
    val snappedParaSpacing = (clampedParaSpacing * 10f).roundToInt() / 10f

    // Snap marginDp: step 4, 0..48
    val clampedMargin = marginDp.coerceIn(SettingsLimits.MARGIN)
    val snappedMargin = ((clampedMargin / 4.0).roundToInt() * 4).coerceIn(0, 48)

    return copy(
        fontSizeSp = snappedFontSize,
        lineHeight = snappedLineHeight,
        paragraphSpacingEm = snappedParaSpacing,
        marginDp = snappedMargin
    )
}

class SettingsStore(private val file: File) {
    private val jsonFile = JsonFile(file, ReaderSettings.serializer()) { ReaderSettings() }

    @Synchronized
    fun load(): ReaderSettings {
        return jsonFile.read().coerced()
    }

    @Synchronized
    fun save(settings: ReaderSettings) {
        jsonFile.write(settings.coerced())
    }
}
