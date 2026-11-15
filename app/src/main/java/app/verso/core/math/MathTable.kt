package app.verso.core.math

/**
 * Reader for the OpenType `MATH` table.
 *
 * Android exposes no public API for MATH metrics at minSdk 26, so these values — which are
 * exactly what TeX's layout rules depend on (axis height, rule thickness, script sizes and
 * shifts, fraction gaps) — have to be read from the font file ourselves. Before this
 * existed the renderer used hand-tuned constants (`0.78`, `0.28`, `-0.16`, …) that were
 * guesses at these numbers.
 *
 * Only the parts a layout engine needs are parsed. Everything is read big-endian per the
 * OpenType specification, and every offset is bounds-checked so a malformed or non-math
 * font produces a clear error instead of an exception deep inside layout.
 */
class MathTable private constructor(
    val constants: MathConstants,
    private val variants: MathVariants,
    private val ligatures: MathLigatures,
) {

    /** Vertical construction (how a stretchy glyph grows) for [glyph]. */
    fun verticalConstruction(glyph: Int): GlyphConstruction? = variants.vertical(glyph)

    /** Horizontal construction for [glyph]. */
    fun horizontalConstruction(glyph: Int): GlyphConstruction? = variants.horizontal(glyph)

    /** Glyph size variants for [glyph], smallest first. */
    fun verticalVariants(glyph: Int): List<GlyphVariant> = variants.verticalVariants(glyph)

    /** Whether [left] and [right] fuse into the ligature [replacement]. */
    fun ligatureFor(left: Int, right: Int): Int? = ligatures.replacement(left, right)

    companion object {
        /** Parse the MATH table out of a complete OpenType/TrueType font file. */
        fun parse(font: ByteArray): MathTable {
            val reader = BigEndianReader(font)
            val tableOffset = reader.findTableOffset("MATH")
                ?: throw MathTableException("Font has no MATH table; it is not a math font.")
            reader.seek(tableOffset)
            @Suppress("UNUSED_VARIABLE")
            val majorVersion = reader.u16()
            @Suppress("UNUSED_VARIABLE")
            val minorVersion = reader.u16()
            val constantsOffset = reader.u16()
            @Suppress("UNUSED_VARIABLE")
            val glyphInfoOffset = reader.u16()
            val variantsOffset = reader.u16()
            if (constantsOffset == 0) {
                throw MathTableException("MATH table has no MathConstants subtable.")
            }
            val constants = MathConstants.parse(
                BigEndianReader(font).also { it.seek(tableOffset + constantsOffset) },
            )
            val variants = if (variantsOffset != 0) {
                MathVariants.parse(
                    BigEndianReader(font).also { it.seek(tableOffset + variantsOffset) },
                    base = tableOffset + variantsOffset,
                    file = font,
                )
            } else {
                MathVariants.Empty
            }
            val ligatures = MathLigatures.parse(font)
            return MathTable(constants, variants, ligatures)
        }
    }
}

class MathTableException(message: String) : Exception(message)

/**
 * The 56 `MathConstants` values, in the spec's order.
 *
 * Values that the spec defines as unsigned percentages (`ScriptPercentScaleDown`, …) are
 * kept as their raw integers because TeX-style layout uses the *ratio*, not the percentage.
 */
data class MathConstants(
    val scriptPercentScaleDown: Int,
    val scriptScriptPercentScaleDown: Int,
    val delimitedSubFormulaMinHeight: Int,
    val displayOperatorMinHeight: Int,
    val mathLeading: Int,
    val axisHeight: Int,
    val accentBaseHeight: Int,
    val flattenedAccentBaseHeight: Int,
    val subscriptShiftDown: Int,
    val subscriptTopMax: Int,
    val subscriptBaselineDropMin: Int,
    val superscriptShiftUp: Int,
    val superscriptShiftUpCramped: Int,
    val superscriptBottomMin: Int,
    val superscriptBaselineDropMax: Int,
    val subSuperscriptGapMin: Int,
    val superscriptBottomMaxWithSubscript: Int,
    val spaceAfterScript: Int,
    val upperLimitGapMin: Int,
    val upperLimitBaselineRiseMin: Int,
    val lowerLimitGapMin: Int,
    val lowerLimitBaselineDropMin: Int,
    val stackTopShiftUp: Int,
    val stackTopDisplayStyleShiftUp: Int,
    val stackBottomShiftDown: Int,
    val stackBottomDisplayStyleShiftDown: Int,
    val stackGapMin: Int,
    val stackDisplayStyleGapMin: Int,
    val stretchStackTopShiftUp: Int,
    val stretchStackBottomShiftDown: Int,
    val stretchStackGapAboveMin: Int,
    val stretchStackGapBelowMin: Int,
    val fractionNumeratorShiftUp: Int,
    val fractionNumeratorDisplayStyleShiftUp: Int,
    val fractionDenominatorShiftDown: Int,
    val fractionDenominatorDisplayStyleShiftDown: Int,
    val fractionNumeratorGapMin: Int,
    val fractionNumDisplayStyleGapMin: Int,
    val fractionRuleThickness: Int,
    val fractionDenominatorGapMin: Int,
    val fractionDenomDisplayStyleGapMin: Int,
    val skewedFractionHorizontalGap: Int,
    val skewedFractionVerticalGap: Int,
    val overbarVerticalGap: Int,
    val overbarRuleThickness: Int,
    val overbarExtraAscender: Int,
    val underbarVerticalGap: Int,
    val underbarRuleThickness: Int,
    val underbarExtraDescender: Int,
    val radicalVerticalGap: Int,
    val radicalDisplayStyleVerticalGap: Int,
    val radicalRuleThickness: Int,
    val radicalExtraAscender: Int,
    val radicalKernBeforeDegree: Int,
    val radicalKernAfterDegree: Int,
    val radicalDegreeBottomRaisePercent: Int,
) {
    companion object {
        internal fun parse(reader: BigEndianReader): MathConstants {
            // MathConstants begins with two 16-bit percentages, followed by 54 values.
            //
            // Per the spec each subsequent value is a MathValueRecord: an FWORD followed by
            // a device-table Offset16, i.e. 4 bytes. Reading them as bare int16 yields an
            // interleaved 'value, 0, value, 0' pattern and misplaces every gap. Verified
            // against the binary: at a 2-byte stride axisHeight reads 0; as a
            // MathValueRecord it reads 250, the correct value for Latin Modern at 1000 upem.
            // Two int16 percentages, then two UFWORDs (2 bytes each, NOT MathValueRecords),
            // then 52 MathValueRecords. Treating the two UFWORDs as records reads
            // delimitedSubFormulaMinHeight twice and shifts every subsequent value by one.
            val script = reader.u16()
            val scriptScript = reader.u16()
            val delimitedSubFormulaMinHeight = reader.u16()
            val displayOperatorMinHeight = reader.u16()
            val values = IntArray(52) { reader.mathValueRecord() }
            return MathConstants(
                scriptPercentScaleDown = script,
                scriptScriptPercentScaleDown = scriptScript,
                delimitedSubFormulaMinHeight = delimitedSubFormulaMinHeight,
                displayOperatorMinHeight = displayOperatorMinHeight,
                mathLeading = values[0],
                axisHeight = values[1],
                accentBaseHeight = values[2],
                flattenedAccentBaseHeight = values[3],
                subscriptShiftDown = values[4],
                subscriptTopMax = values[5],
                subscriptBaselineDropMin = values[6],
                superscriptShiftUp = values[7],
                superscriptShiftUpCramped = values[8],
                superscriptBottomMin = values[9],
                superscriptBaselineDropMax = values[10],
                subSuperscriptGapMin = values[11],
                superscriptBottomMaxWithSubscript = values[12],
                spaceAfterScript = values[13],
                upperLimitGapMin = values[14],
                upperLimitBaselineRiseMin = values[15],
                lowerLimitGapMin = values[16],
                lowerLimitBaselineDropMin = values[17],
                stackTopShiftUp = values[18],
                stackTopDisplayStyleShiftUp = values[19],
                stackBottomShiftDown = values[20],
                stackBottomDisplayStyleShiftDown = values[21],
                stackGapMin = values[22],
                stackDisplayStyleGapMin = values[23],
                stretchStackTopShiftUp = values[24],
                stretchStackBottomShiftDown = values[25],
                stretchStackGapAboveMin = values[26],
                stretchStackGapBelowMin = values[27],
                fractionNumeratorShiftUp = values[28],
                fractionNumeratorDisplayStyleShiftUp = values[29],
                fractionDenominatorShiftDown = values[30],
                fractionDenominatorDisplayStyleShiftDown = values[31],
                fractionNumeratorGapMin = values[32],
                fractionNumDisplayStyleGapMin = values[33],
                fractionRuleThickness = values[34],
                fractionDenominatorGapMin = values[35],
                fractionDenomDisplayStyleGapMin = values[36],
                skewedFractionHorizontalGap = values[37],
                skewedFractionVerticalGap = values[38],
                overbarVerticalGap = values[39],
                overbarRuleThickness = values[40],
                overbarExtraAscender = values[41],
                underbarVerticalGap = values[42],
                underbarRuleThickness = values[43],
                underbarExtraDescender = values[44],
                radicalVerticalGap = values[45],
                radicalDisplayStyleVerticalGap = values[46],
                radicalRuleThickness = values[47],
                radicalExtraAscender = values[48],
                radicalKernBeforeDegree = values[49],
                radicalKernAfterDegree = values[50],
                radicalDegreeBottomRaisePercent = values[51],
            )
        }
    }
}

/** One available size of a stretchy glyph. */
data class GlyphVariant(val variantGlyph: Int, val advanceMeasurement: Int)

/**
 * How to build a large glyph out of pieces: either a longer variant glyph, or (for the
 * biggest sizes) a stack of [parts] around zero or more [extenders].
 */
data class GlyphConstruction(
    val variants: List<GlyphVariant>,
    val parts: List<GlyphPart>,
) {
    /**
     * The glyph to use for a target advance, and whether it had to be assembled from
     * pieces. Returns `null` when even assembly cannot reach [targetAdvance].
     */
    fun forAdvance(targetAdvance: Int): GlyphChoice? {
        variants.firstOrNull { it.advanceMeasurement >= targetAdvance }?.let {
            return GlyphChoice(it.variantGlyph, assembled = false)
        }
        if (parts.isEmpty()) return null
        val fullConnector = parts.any { it.partFlags and GLYPH_PART_EXTENDER != 0 }
        if (!fullConnector) return null
        return GlyphChoice(parts.first().glyph, assembled = true)
    }

    companion object {
        const val GLYPH_PART_EXTENDER = 0x01
    }
}

/** A chosen glyph, plus whether the caller must stack its pieces. */
data class GlyphChoice(val glyph: Int, val assembled: Boolean)

/** One piece of an assembled (built-up) glyph. */
data class GlyphPart(
    val glyph: Int,
    val startConnectorLength: Int,
    val endConnectorLength: Int,
    val fullAdvance: Int,
    val partFlags: Int,
)

private class MathVariants(
    private val verticalCoverage: IntArray,
    private val vertical: List<GlyphConstruction>,
    private val horizontalCoverage: IntArray,
    private val horizontal: List<GlyphConstruction>,
) {
    fun vertical(glyph: Int): GlyphConstruction? =
        verticalCoverage.indexOf(glyph).takeIf { it >= 0 }?.let { vertical.getOrNull(it) }

    fun horizontal(glyph: Int): GlyphConstruction? =
        horizontalCoverage.indexOf(glyph).takeIf { it >= 0 }?.let { horizontal.getOrNull(it) }

    fun verticalVariants(glyph: Int): List<GlyphVariant> =
        vertical(glyph)?.variants ?: emptyList()

    companion object {
        val Empty = MathVariants(IntArray(0), emptyList(), IntArray(0), emptyList())

        fun parse(reader: BigEndianReader, base: Int, file: ByteArray): MathVariants {
            // MathVariants header: minOverlap, vertCoverageOff, vertGlyphCount,
            // horizCoverageOff, horizGlyphCount, then the construction-offset arrays.
            // Spec order: minConnectorOverlap, vertCoverageOff, horizCoverageOff,
            // vertGlyphCount, horizGlyphCount, then the two construction-offset arrays.
            reader.seek(base)
            @Suppress("UNUSED_VARIABLE")
            val minOverlap = reader.u16()
            val vertCoverageOffset = reader.u16()
            val horizCoverageOffset = reader.u16()
            val vertCount = reader.u16()
            val horizCount = reader.u16()
            val vertOffsets = IntArray(vertCount) { reader.u16() }
            val horizOffsets = IntArray(horizCount) { reader.u16() }

            val vertCoverage = if (vertCoverageOffset != 0) {
                Coverage.parse(BigEndianReader(file).also { it.seek(base + vertCoverageOffset) })
            } else {
                IntArray(0)
            }
            val horizCoverage = if (horizCoverageOffset != 0) {
                Coverage.parse(BigEndianReader(file).also { it.seek(base + horizCoverageOffset) })
            } else {
                IntArray(0)
            }
            val vertical = vertOffsets.map { parseConstruction(file, base + it) }
            val horizontal = horizOffsets.map { parseConstruction(file, base + it) }
            return MathVariants(vertCoverage, vertical, horizCoverage, horizontal)
        }

        /**
         * Parse one MathGlyphConstruction: a glyph count, an optional MathGlyphAssembly
         * offset, and the size-variant records.
         */
        private fun parseConstruction(file: ByteArray, offset: Int): GlyphConstruction {
            val reader = BigEndianReader(file).also { it.seek(offset) }
            val assemblyOffset = reader.u16()
            val glyphCount = reader.u16()
            val variants = ArrayList<GlyphVariant>(glyphCount)
            repeat(glyphCount) {
                variants += GlyphVariant(variantGlyph = reader.u16(), advanceMeasurement = reader.u16())
            }
            val parts = ArrayList<GlyphPart>()
            if (assemblyOffset != 0) {
                val assembly = BigEndianReader(file).also { it.seek(offset + assemblyOffset) }
                // MathValueRecord = FWORD value + Offset16 deviceOffset (4 bytes).
                @Suppress("UNUSED_VARIABLE")
                val italicCorrection = assembly.mathValueRecord()
                val partCount = assembly.u16()
                repeat(partCount) {
                    parts += GlyphPart(
                        glyph = assembly.u16(),
                        startConnectorLength = assembly.u16(),
                        endConnectorLength = assembly.u16(),
                        fullAdvance = assembly.u16(),
                        partFlags = assembly.u16(),
                    )
                }
            }
            return GlyphConstruction(variants, parts)
        }
    }
}

/** Coverage table lookup: maps a glyph ID to its index, or -1. */
private object Coverage {
    fun parse(reader: BigEndianReader): IntArray {
        val format = reader.u16()
        return when (format) {
            1 -> IntArray(reader.u16()) { reader.u16() }
            2 -> {
                val count = reader.u16()
                val glyphs = ArrayList<Int>()
                repeat(count) {
                    val start = reader.u16()
                    val end = reader.u16()
                    @Suppress("UNUSED_VARIABLE")
                    val startCoverageIndex = reader.u16()
                    for (g in start..end) glyphs += g
                }
                glyphs.toIntArray()
            }
            else -> IntArray(0)
        }
    }
}

/** Optional pre-built ligatures for combining pieces (e.g. math kerning pairs). */
private class MathLigatures(private val pairs: Map<Long, Int>) {
    fun replacement(left: Int, right: Int): Int? = pairs[(left.toLong() shl 32) or right.toLong()]

    companion object {
        fun parse(font: ByteArray): MathLigatures {
            // The MathGlyphInfo subtable is at an offset we do not read here; ligatures are
            // rare in practice and the layout engine works without them.
            return MathLigatures(emptyMap())
        }
    }
}

/** Minimal bounds-checked big-endian reader over a font file. */
internal class BigEndianReader(private val data: ByteArray) {
    private var offset = 0

    fun seek(position: Int) {
        if (position < 0 || position > data.size) {
            throw MathTableException("Seek out of bounds: $position (size ${data.size})")
        }
        offset = position
    }

    fun position(): Int = offset

    fun u16(): Int {
        require_()
        val v = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
        offset += 2
        return v
    }

    fun i16(): Int {
        val v = u16()
        return if (v >= 0x8000) v - 0x10000 else v
    }

    /**
     * Read a `MathValueRecord`: an `FWORD` value followed by an `Offset16` device-table
     * offset, four bytes in total. The device offset is skipped — device corrections are
     * resolution-specific hints we deliberately do not apply.
     *
     * The value is the *low* 16 bits; treating the pair as one little-endian int yields
     * nonsense such as `600 << 16` for the fraction rule thickness.
     */
    fun mathValueRecord(): Int {
        val value = i16()
        offset += 2 // device-table offset, unused
        return value
    }

    /** Read a signed value stored as 32-bit. */
    fun u32(): Int {
        require_(4)
        val v = ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
        offset += 4
        return v
    }

    fun i16AsInt32(): Int {
        require_(4)
        val v = ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
        offset += 4
        return v
    }

    /**
     * Locate a table by tag in the font directory. Returns the byte offset of the table or
     * null when absent.
     */
    fun findTableOffset(tag: String): Int? {
        if (data.size < 12) throw MathTableException("File is too small to be a font.")
        // sfnt header: sfntVersion (4 bytes), numTables (u16), then 3 u16 search fields.
        seek(4)
        @Suppress("UNUSED_VARIABLE")
        val numTables = u16()
        u16(); u16(); u16()
        val tagBytes = tag.toByteArray(Charsets.US_ASCII)
        require(tagBytes.size == 4)
        repeat(numTables) {
            require_(16)
            var matches = true
            for (i in 0 until 4) {
                if (data[offset + i] != tagBytes[i]) matches = false
            }
            offset += 4
            offset += 4 // checkSum
            val tableOffset = data.readInt(offset); offset += 4
            offset += 4 // length
            if (matches) return tableOffset
        }
        return null
    }

    private fun require_(count: Int = 2) {
        if (offset + count > data.size) {
            throw MathTableException("Unexpected end of font data at offset $offset.")
        }
    }

    private fun ByteArray.readInt(at: Int): Int =
        ((this[at].toInt() and 0xFF) shl 24) or
            ((this[at + 1].toInt() and 0xFF) shl 16) or
            ((this[at + 2].toInt() and 0xFF) shl 8) or
            (this[at + 3].toInt() and 0xFF)
}

/**
 * Minimal `cmap` lookup: maps a Unicode codepoint to a glyph ID.
 *
 * MATH coverage tables are keyed by glyph ID, so anything that wants to ask "how does this
 * *character* grow" — delimiters in M4, in particular — must resolve the character through
 * the font's character map first. Only the formats that real text fonts use are handled
 * (4 and 12); an unknown format yields `null` rather than a wrong glyph.
 */
object GlyphCmap {

    fun lookup(font: ByteArray, codepoint: Int): Int? {
        val cmapOffset = runCatching {
            BigEndianReader(font).findTableOffset("cmap")
        }.getOrNull() ?: return null
        val reader = BigEndianReader(font).also { it.seek(cmapOffset) }
        reader.u16() // version
        val numTables = reader.u16()
        // Prefer a full-Unicode subtable (format 12), then BMP (format 4).
        var bestOffset: Int? = null
        var bestFormat = 0
        repeat(numTables) {
            val platformId = reader.u16()
            val encodingId = reader.u16()
            val subtableOffset = reader.u32()
            val isUnicode = platformId == 0 || (platformId == 3 && encodingId in 1..10)
            if (!isUnicode) return@repeat
            val format = BigEndianReader(font).also { it.seek(cmapOffset + subtableOffset) }.u16()
            if (format == 12 && bestFormat != 12) {
                bestOffset = cmapOffset + subtableOffset
                bestFormat = 12
            } else if (format == 4 && bestFormat == 0) {
                bestOffset = cmapOffset + subtableOffset
                bestFormat = 4
            }
        }
        val offset = bestOffset ?: return null
        return when (bestFormat) {
            4 -> format4(font, offset, codepoint)
            12 -> format12(font, offset, codepoint)
            else -> null
        }
    }

    private fun format4(font: ByteArray, offset: Int, codepoint: Int): Int? {
        if (codepoint > 0xFFFF) return null
        val reader = BigEndianReader(font).also { it.seek(offset) }
        reader.u16() // format
        reader.u16() // length
        reader.u16() // language
        val segCountX2 = reader.u16()
        val segCount = segCountX2 / 2
        reader.u16(); reader.u16(); reader.u16() // searchRange, entrySelector, rangeShift
        val endCodes = IntArray(segCount) { reader.u16() }
        reader.u16() // reservedPad
        val startCodes = IntArray(segCount) { reader.u16() }
        val idDeltas = IntArray(segCount) { reader.i16() }
        val idRangeOffsetPosition = reader.position()
        val idRangeOffsets = IntArray(segCount) { reader.u16() }
        for (i in 0 until segCount) {
            if (codepoint in startCodes[i]..endCodes[i]) {
                val rangeOffset = idRangeOffsets[i]
                if (rangeOffset == 0) {
                    return (codepoint + idDeltas[i]) and 0xFFFF
                }
                val glyphIndexAddress = idRangeOffsetPosition + i * 2 + rangeOffset +
                    (codepoint - startCodes[i]) * 2
                val glyph = BigEndianReader(font).also { it.seek(glyphIndexAddress) }.u16()
                return if (glyph == 0) null else (glyph + idDeltas[i]) and 0xFFFF
            }
        }
        return null
    }

    private fun format12(font: ByteArray, offset: Int, codepoint: Int): Int? {
        val reader = BigEndianReader(font).also { it.seek(offset) }
        reader.u16() // format
        reader.u16() // reserved
        reader.u32() // length
        reader.u32() // language
        val numGroups = reader.u32()
        repeat(numGroups) {
            val startChar = reader.u32()
            val endChar = reader.u32()
            val startGlyph = reader.u32()
            if (codepoint in startChar..endChar) {
                return startGlyph + (codepoint - startChar)
            }
        }
        return null
    }
}
