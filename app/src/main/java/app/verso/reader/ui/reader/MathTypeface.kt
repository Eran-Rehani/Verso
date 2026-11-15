package app.verso.reader.ui.reader

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.verso.core.store.MathFontChoice
import java.io.File

/**
 * Supplies the bundled OpenType math typefaces.
 *
 * The fonts ship in `assets/fonts/` rather than `res/font/` because they are OpenType
 * (`.otf`) files with a `MATH` table, and `res/font/` derives resources from the file name
 * and does not distinguish the two families cleanly. Loading from assets also lets the
 * M3 MATH-table parser read the same bytes the renderer draws with, so metrics and glyphs
 * can never disagree.
 *
 * A missing or unreadable font file is a hard failure rather than a silent fallback: a
 * silent fallback to the system serif is exactly the defect this work exists to remove,
 * and it would make every golden comparison quietly meaningless.
 */
object MathTypeface {

    const val LATIN_MODERN_PATH = "fonts/latinmodern-math.otf"
    const val STIX_TWO_PATH = "fonts/stix-two-math.otf"

    fun assetPath(choice: MathFontChoice): String = when (choice) {
        MathFontChoice.LatinModern -> LATIN_MODERN_PATH
        MathFontChoice.StixTwo -> STIX_TWO_PATH
    }

    /** Cached per (context, choice) to avoid re-parsing the OTF on every composition. */
    private val typefaceCache = HashMap<MathFontChoice, Typeface>()

    /**
     * Load the requested face as an `android.graphics.Typeface`.
     *
     * Uses `Typeface.Builder` on a file descriptor so the MATH table bytes are the same
     * ones available to the metrics parser.
     */
    fun typeface(context: Context, choice: MathFontChoice): Typeface {
        typefaceCache[choice]?.let { return it }
        val path = assetPath(choice)
        val loaded: Typeface = try {
            Typeface.Builder(context.assets, path).build()
        } catch (error: Exception) {
            throw IllegalStateException(
                "Bundled math font '$path' could not be loaded. Math rendering must not " +
                    "silently fall back to a system font.",
                error,
            )
        }
        typefaceCache[choice] = loaded
        return loaded
    }

    /**
     * Compose [FontFamily] for the requested face, for use with `Text`.
     *
     * Only one weight and style is registered because OpenType math fonts carry a single
     * face; bold and italic are expressed through `\mathbf`/`\mathit` glyph choices and
     * Unicode codepoints, not synthetic emboldening.
     */
    fun fontFamily(context: Context, choice: MathFontChoice): FontFamily {
        return FontFamily(
            androidx.compose.ui.text.font.Font(
                path = assetPath(choice),
                assetManager = context.assets,
                weight = FontWeight.Normal,
                style = FontStyle.Normal,
            )
        )
    }

    /** Read the raw font bytes, for the M3 MATH-table parser. */
    fun readFontBytes(context: Context, choice: MathFontChoice): ByteArray =
        context.assets.open(assetPath(choice)).use { it.readBytes() }

    /** A raw `Typeface.createFromFile` variant used by the golden harness on the JVM. */
    fun typefaceFromFile(file: File): Typeface = Typeface.createFromFile(file)

    /** Clears the cache; used by tests that swap the underlying asset. */
    internal fun clearCache() = typefaceCache.clear()
}

/**
 * Ambient math face for the current composition.
 *
 * Defaults to the platform serif so previews and tests that do not provide a font still
 * compose. Real reader screens always provide the bundled face via
 * [MathTypeface.fontFamily], so the default is never what a user sees.
 */
val LocalMathFontFamily = androidx.compose.runtime.compositionLocalOf<FontFamily?> { null }

/** Ambient choice, used where a glyph lookup needs to know the active font. */
val LocalMathFontChoice = androidx.compose.runtime.compositionLocalOf<MathFontChoice> {
    MathFontChoice.LatinModern
}
