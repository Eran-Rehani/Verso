package app.verso.reader

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import app.verso.core.store.RecentEntry
import app.verso.core.store.ThemeChoice
import app.verso.reader.data.db.BookEntity
import app.verso.reader.ui.home.HomeScreen
import app.verso.reader.ui.library.LibraryScreen
import app.verso.reader.ui.reader.paletteFor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemovalUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeRemovalCallsBackWithBookId() {
        var removed: String? = null
        compose.setContent {
            HomeScreen(
                recents = listOf(RecentEntry("book", "Book", "Markdown", 1, 1, 1)),
                palette = paletteFor(ThemeChoice.Light, false),
                onOpen = {}, onDelete = { removed = it }, onOpenFile = {}, onPaste = {}
            )
        }
        compose.onNodeWithContentDescription("Remove from library").performClick()
        assertEquals("book", removed)
    }

    @Test fun libraryRemovalCallsBackWithBookId() {
        var removed: String? = null
        compose.setContent {
            LibraryScreen(
                books = listOf(BookEntity("book", "Book", "Markdown")),
                palette = paletteFor(ThemeChoice.Light, false),
                onOpenBook = {}, onDeleteBook = { removed = it }, onImportFile = {}
            )
        }
        compose.onNodeWithContentDescription("Remove from library").performClick()
        assertEquals("book", removed)
    }
}
