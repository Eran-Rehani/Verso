package app.verso.reader

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import app.verso.core.store.ReaderSettings
import app.verso.core.store.ThemeChoice
import app.verso.reader.ui.reader.LatexEquationInspectorDialog
import app.verso.reader.ui.reader.paletteFor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LatexInspectorEditTest {
    @get:Rule val compose = createComposeRule()

    @Test fun initiallyRejectedSourceShowsBoundedPreviewAndCopy() {
        val source = "x".repeat(16_385)
        compose.setContent {
            LatexEquationInspectorDialog(source, ReaderSettings(), paletteFor(ThemeChoice.Light, false), {})
        }
        compose.onNodeWithTag("equation_live_edit_input").assertDoesNotExist()
        compose.onNodeWithTag("equation_toggle_edit_button").assertDoesNotExist()
        compose.onNodeWithTag("equation_latex_source_text").assertTextEquals(source.take(160) + "…")
        compose.onNodeWithTag("equation_copy_latex_button").assertExists()
    }

    @Test fun editCanBeCorrectedAfterCrossingParserLimit() {
        compose.setContent {
            LatexEquationInspectorDialog("x", ReaderSettings(), paletteFor(ThemeChoice.Light, false), {})
        }
        compose.onNodeWithTag("equation_toggle_edit_button").performClick()
        val rejected = "{".repeat(65) + "x" + "}".repeat(65)
        compose.onNodeWithTag("equation_live_edit_input").performTextReplacement(rejected)
        compose.onNodeWithTag("equation_live_edit_input").assertExists().assertTextEquals(rejected)
        compose.onNodeWithTag("equation_toggle_edit_button").assertExists()
        compose.onNodeWithTag("equation_live_edit_input").performTextReplacement("x+1")
        compose.onNodeWithTag("equation_live_edit_input").assertTextEquals("x+1")
        compose.onNodeWithTag("equation_toggle_edit_button").performClick()
        compose.onNodeWithTag("equation_latex_source_text").assertTextEquals("x+1")
    }
}
