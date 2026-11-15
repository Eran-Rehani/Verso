package app.verso.reader.ui.reader

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.verso.core.store.ReaderSettings
import app.verso.reader.ui.settings.SettingsPanel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsSwitchTest {
    @get:Rule val compose = createComposeRule()

    @Test fun labeledRowIsOneSwitchTarget() {
        compose.setContent {
            var settings by remember { mutableStateOf(ReaderSettings()) }
            SettingsPanel(settings, { settings = it })
        }
        val row = compose.onNodeWithText("Keep screen on")
        row.performScrollTo().assertHasClickAction().assertIsOff().performClick().assertIsOn()
    }
}
