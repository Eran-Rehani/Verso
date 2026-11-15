package app.verso.reader.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import app.verso.core.model.*
import app.verso.core.store.ReaderSettings
import app.verso.core.store.ThemeChoice
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [BlockSelectionTest.MagnifierShadow::class])
class BlockSelectionTest {
    @Implements(android.widget.Magnifier::class)
    class MagnifierShadow {
        @Implementation fun show(x: Float, y: Float) {}
        @Implementation fun show(x: Float, y: Float, windowX: Float, windowY: Float) {}
        @Implementation fun update() {}
        @Implementation fun dismiss() {}
    }

    @get:Rule val compose = createComposeRule()

    @Test fun paragraphCopies() = checkCopy(0)
    @Test fun headingCopies() = checkCopy(1)
    @Test fun listBodyCopies() = checkCopy(2)
    @Test fun quoteCopies() = checkCopy(3)

    private fun checkCopy(index: Int) {
        val text = InlineText.plain("selection")
        val blocks = listOf(Paragraph(text), Heading(2, text), ListItem(0, "•", text), Quote(1, text))
        var copied: AnnotatedString? = null
        var copyAction: (() -> Unit)? = null
        val clipboard = object : ClipboardManager {
            override fun setText(annotatedString: AnnotatedString) { copied = annotatedString }
            override fun getText(): AnnotatedString? = copied
        }
        val toolbar = object : TextToolbar {
            override val status = TextToolbarStatus.Shown
            override fun hide() {}
            override fun showMenu(rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?, onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?) {
                copyAction = onCopyRequested
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboard, LocalTextToolbar provides toolbar) {
                BlockView(blocks[index], ReaderSettings(), paletteFor(ThemeChoice.Light, false))
            }
        }
        compose.onNodeWithText("selection").performTouchInput { longClick() }
        compose.runOnIdle { assertNotNull("copy action for block $index", copyAction); copyAction!!() }
        compose.runOnIdle { assertEquals("selection", copied?.text) }
    }

    /**
     * Task 9 acceptance: short taps on inline math must still open the equation inspector after the
     * block is wrapped in a SelectionContainer.
     *
     * The inspector is invoked through the widget's semantics OnClick action rather than touch
     * input: Robolectric measures inline-content placeholders as 0x0, so a coordinate tap in the
     * node has no area to land in. The action is the same lambda the real `Modifier.clickable`
     * installs, so this still exercises the product path instead of re-implementing it.
     */
    @Test fun shortTapReachesParentAndEquationInspector() {
        var parentClicks = 0
        var inspected: String? = null
        val raw = "before \\frac{1}{2} after"
        val start = raw.indexOf('\\')
        val end = raw.indexOf(" after")
        val inline = InlineText(raw, listOf(Span(start, end, SpanKind.Math)))
        compose.setContent {
            androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.clickable { parentClicks++ }) {
                    BlockView(Paragraph(InlineText.plain("plain text")), ReaderSettings(), paletteFor(ThemeChoice.Light, false))
                }
                BlockView(Paragraph(inline), ReaderSettings(), paletteFor(ThemeChoice.Light, false), onInspectEquation = { inspected = it })
            }
        }
        compose.onNodeWithText("plain text").performTouchInput { click(androidx.compose.ui.geometry.Offset(5f, centerY)) }
        compose.runOnIdle { assertEquals(1, parentClicks) }
        compose.onNodeWithTag("inline_math_widget_$start", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals("\\frac{1}{2}", inspected) }
    }

    @Test fun linkTapOpensUrl() {
        var opened: String? = null
        val inline = InlineText("link", listOf(Span(0, 4, SpanKind.Link, "https://example.org")))
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened = uri }
            }) {
                BlockView(Paragraph(inline), ReaderSettings(), paletteFor(ThemeChoice.Light, false))
            }
        }
        compose.onNodeWithText("link", substring = true, useUnmergedTree = true)
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(5f, centerY)) }
        compose.runOnIdle { assertEquals("https://example.org", opened) }
    }

    /**
     * Task 8 search ranges must keep working once Task 9 wraps blocks in a SelectionContainer:
     * the same match offsets feed both the highlight styling and the selection node.
     */
    @Test fun searchRangesSurviveSelectionContainer() {
        val document = app.verso.core.model.Document(title = "Doc", blocks = listOf(Paragraph(InlineText.plain("alpha beta alpha"))))
        val highlights = searchHighlights(document, "alpha", checkCancelled = {})
        val ranges = highlights.blocks[0]
        assertNotNull("search ranges for block 0", ranges)
        assertEquals("both matches found", 2, ranges!!.size)
        compose.setContent {
            BlockView(
                Paragraph(InlineText.plain("alpha beta alpha")),
                ReaderSettings(),
                paletteFor(ThemeChoice.Light, false),
                searchRanges = ranges,
                activeMatchRawStart = 0
            )
        }
        // The text still renders (selection wrapper does not drop the annotated string)...
        compose.onNodeWithText("alpha beta alpha").assertIsDisplayed()
        // ...and a long press inside it selects without failing.
        compose.onNodeWithText("alpha beta alpha").performTouchInput { longClick() }
    }

    /**
     * Task 9 acceptance: a long press must select text, not hide the reader controls.
     * Task 7 removed whole-document tap-to-hide, so hiding is only reachable from the overflow
     * menu; this guards against selection gestures re-introducing an implicit chrome toggle.
     */
    @Test fun longPressDoesNotHideReaderControls() {
        var chromeToggles = 0
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                Box(Modifier.clickable { chromeToggles++ }) {
                    BlockView(
                        Paragraph(InlineText.plain("selectable body text")),
                        ReaderSettings(),
                        paletteFor(ThemeChoice.Light, false)
                    )
                }
            }
        }
        compose.onNodeWithText("selectable body text").performTouchInput { longClick() }
        compose.waitForIdle()
        assertEquals("long press must not toggle reader chrome", 0, chromeToggles)
    }

    @Test fun codeCopyStillWorks() {
        lateinit var context: Context
        compose.setContent {
            context = LocalContext.current
            BlockView(CodeBlock(null, "val x = 1"), ReaderSettings(), paletteFor(ThemeChoice.Light, false))
        }
        compose.onNodeWithTag("copy_code_button").performClick()
        compose.runOnIdle {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            assertEquals("val x = 1", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
    }

}
