package app.verso.core.math

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The MATH-table parser is the foundation of TeX-correct layout: every gap and script size
 * M3 applies comes from these numbers. These tests read the **real bundled fonts**, so a
 * green run means the parser agrees with the actual files the app ships.
 */
class MathTableTest {

    private fun latinModern(): MathTable {
        val file = File("src/main/assets/fonts/latinmodern-math.otf")
        assumeFont(file)
        return MathTable.parse(file.readBytes())
    }

    private fun stix(): MathTable {
        val file = File("src/main/assets/fonts/stix-two-math.otf")
        assumeFont(file)
        return MathTable.parse(file.readBytes())
    }

    private fun assumeFont(file: File) {
        org.junit.Assume.assumeTrue("font asset missing: ${file.path}", file.isFile)
    }

    @Test
    fun `latin modern declares its script scaling`() {
        val constants = latinModern().constants
        // Read earlier with an independent script straight from the binary.
        assertEquals(70, constants.scriptPercentScaleDown)
        assertEquals(50, constants.scriptScriptPercentScaleDown)
    }

    @Test
    fun `latin modern axis height and rule thickness are positive and plausible`() {
        val c = latinModern().constants
        assertTrue("axisHeight should be positive, was ${c.axisHeight}", c.axisHeight > 0)
        assertTrue("fractionRuleThickness should be positive, was ${c.fractionRuleThickness}",
            c.fractionRuleThickness > 0)
        // At a 1000 upem a fraction rule is roughly 40-80 units; guard against misreads.
        assertTrue("fractionRuleThickness suspicious: ${c.fractionRuleThickness}",
            c.fractionRuleThickness in 10..200)
        assertTrue("axisHeight suspicious: ${c.axisHeight}", c.axisHeight in 100..600)
    }

    @Test
    fun `fraction gaps are non-negative`() {
        val c = latinModern().constants
        assertTrue(c.fractionNumeratorGapMin >= 0)
        assertTrue(c.fractionDenominatorGapMin >= 0)
        assertTrue(c.radicalVerticalGap >= 0)
    }

    @Test
    fun `stix two parses and agrees on direction of magnitudes`() {
        val c = stix().constants
        assertEquals(70, c.scriptPercentScaleDown)
        // STIX deliberately differs from Latin Modern here (55 vs 50). Asserting the real
        // value keeps the test honest rather than encoding an assumption.
        assertEquals(55, c.scriptScriptPercentScaleDown)
        assertTrue(c.axisHeight > 0)
        assertTrue(c.fractionRuleThickness > 0)
    }

    @Test
    fun `stretchy delimiters expose size variants`() {
        // Coverage tables are keyed by GLYPH ID, not by Unicode codepoint, so the glyph for
        // U+0028 has to be resolved through the font's cmap first. Looking it up as 0x28
        // was a bug in this test, not in the parser.
        val file = File("src/main/assets/fonts/latinmodern-math.otf")
        assumeFont(file)
        val bytes = file.readBytes()
        val table = MathTable.parse(bytes)
        val parenGlyph = GlyphCmap.lookup(bytes, 0x28)
        assertNotNull("left paren must be in the cmap", parenGlyph)
        val construction = table.verticalConstruction(parenGlyph!!)
        assertNotNull(
            "left paren should have size variants in the MATH table",
            construction,
        )
        val variants = construction!!.variants
        assertTrue("left paren should offer growing variants, got ${variants.size}",
            variants.size >= 1)
        // Variants are ordered smallest first.
        val advances = variants.map { it.advanceMeasurement }
        assertEquals("variants should be ordered by size", advances.sorted(), advances)
    }

    @Test
    fun `non-math font is rejected clearly`() {
        // A minimal file with no table directory at all must not throw something opaque.
        val failure = runCatching { MathTable.parse(ByteArray(8)) }.exceptionOrNull()
        assertTrue("expected MathTableException, got $failure", failure is MathTableException)
    }

    @Test
    fun `garbage table directory does not crash the parser`() {
        val bytes = ByteArray(1024)
        // numTables = 4 but the directory claims entries beyond the buffer.
        bytes[4] = 0
        bytes[5] = 4
        val failure = runCatching { MathTable.parse(bytes) }.exceptionOrNull()
        assertTrue("expected a MathTableException, got $failure", failure is MathTableException)
    }
}
