package app.verso.reader.ui.reader

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.verso.core.store.ThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h1200dp")
class ReaderChromeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun actionsDismissMenuAndHiddenControlsCanBeRestored() {
        var action = ""
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            ReaderChrome(visible, "A title", "Page 1 of 2", false, false,
                paletteFor(ThemeChoice.Light, false),
                onBack = { action = "back" }, onBookmark = { action = "bookmark" },
                onSearch = { action = "search" }, onToc = { action = "toc" },
                onBookmarks = { action = "bookmarks" }, onReadAloud = { action = "tts" },
                onSettings = { action = "settings" }, onToggleChrome = { visible = !visible })
        }
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals("back", action)
        compose.onNodeWithContentDescription("Bookmark current page").performClick()
        assertEquals("bookmark", action)
        compose.onNodeWithContentDescription("Search document").performClick()
        assertEquals("search", action)
        for ((label, expected) in listOf("Table of contents" to "toc", "Bookmarks" to "bookmarks", "Read aloud" to "tts", "Reading settings" to "settings")) {
            compose.onNodeWithContentDescription("More").performClick()
            compose.onNodeWithText(label).performClick()
            assertEquals(expected, action)
            compose.onNodeWithText(label).assertDoesNotExist()
        }
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Hide reading controls").performClick()
        compose.onNodeWithTag("reader_show_controls").assertIsDisplayed().assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp).performClick()
        compose.onNodeWithContentDescription("More").assertIsDisplayed()
    }
    private fun checkLayout(width: Int, scale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale)) {
                Box(Modifier.requiredWidth(width.dp)) {
                    ReaderChrome(true, "Long document title", "Page 1 of 2", false, false,
                        paletteFor(ThemeChoice.Light, false), {}, {}, {}, {}, {}, {}, {}, {})
                }
            }
        }
        val names = listOf("Back", "Bookmark current page", "Search document", "More")
        val bounds = names.map { compose.onNodeWithContentDescription(it).assertIsDisplayed().getUnclippedBoundsInRoot() }
        val title = compose.onNodeWithText("Long document title").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue(bounds[0].right <= title.left)
        assertTrue(title.right <= bounds[1].left)
        assertTrue(bounds.zipWithNext().all { (a, b) -> a.right <= b.left })
    }
    @Test fun layout320Scale10() = checkLayout(320, 1.0f)
    @Test fun layout320Scale13() = checkLayout(320, 1.3f)
    @Test fun layout320Scale20() = checkLayout(320, 2.0f)
    @Test fun layout360Scale10() = checkLayout(360, 1.0f)
    @Test fun layout360Scale13() = checkLayout(360, 1.3f)
    @Test fun layout360Scale20() = checkLayout(360, 2.0f)
    @Test fun layout600Scale10() = checkLayout(600, 1.0f)
    @Test fun layout600Scale13() = checkLayout(600, 1.3f)
    @Test fun layout600Scale20() = checkLayout(600, 2.0f)

}
