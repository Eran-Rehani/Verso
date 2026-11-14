package app.verso.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.verso.core.store.ThemeChoice
import app.verso.reader.ui.reader.paletteFor
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImportingScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun phaseAndCancelAreDisplayedAndCancelInvokesCallback() {
        var cancelled = false
        compose.setContent {
            ImportingScreen(paletteFor(ThemeChoice.Light, false), phase = "Opening book") {
                cancelled = true
            }
        }
        compose.onNodeWithText("Opening book").assertIsDisplayed()
        compose.onNodeWithText("Cancel import").assertIsDisplayed().performClick()
        assertTrue(cancelled)
    }
}
