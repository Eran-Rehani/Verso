package app.verso.reader.ui.reader

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.performTextInput
import app.verso.core.model.Document
import app.verso.core.model.InlineText
import app.verso.core.model.Paragraph
import app.verso.core.store.ReaderSettings
import app.verso.core.store.ThemeChoice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReaderSearchUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun typingDuringSearchKeepsNavigationDisabled() {
        val doc = Document("test", listOf(Paragraph(InlineText.plain("alpha beta"))))
        compose.setContent {
            ReaderScreen(doc, ReaderSettings(), paletteFor(ThemeChoice.Light, false), 0, 0,
                true, {}, { _, _ -> }, {}, {}, searchOpen = true)
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("reader_search_input").performTextInput("a")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("reader_search_input").performTextInput("l")
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("reader_search_input").assertTextContains("al")
        compose.onNodeWithTag("search_match_count").assertTextContains("Searching…")
        compose.onNodeWithTag("search_next_button").assertIsNotEnabled()
        compose.onNodeWithTag("search_prev_button").assertIsNotEnabled()
    }
    @Test fun pendingQueryDoesNotHighlightLargeVisibleBlock() {
        val text = "z".repeat(10000) + "needle"
        val doc = Document("test", listOf(Paragraph(InlineText.plain(text))))
        compose.setContent {
            ReaderScreen(doc, ReaderSettings(), paletteFor(ThemeChoice.Light, false), 0, 0,
                true, {}, { _, _ -> }, {}, {}, searchOpen = true)
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("reader_search_input").performTextInput("needle")
        compose.mainClock.advanceTimeByFrame()
        val rendered = compose.onNodeWithText(text).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue(rendered.spanStyles.isEmpty())
        compose.onNodeWithTag("search_match_count").assertTextContains("Searching…")
    }

}
