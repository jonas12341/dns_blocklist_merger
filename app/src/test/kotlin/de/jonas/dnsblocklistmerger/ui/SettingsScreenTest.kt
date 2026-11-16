package de.jonas.dnsblocklistmerger.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import de.jonas.dnsblocklistmerger.data.Settings
import de.jonas.dnsblocklistmerger.ui.settings.SettingsActions
import de.jonas.dnsblocklistmerger.ui.settings.SettingsScreen
import de.jonas.dnsblocklistmerger.ui.settings.parseInRange
import de.jonas.dnsblocklistmerger.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1600dp-xxhdpi")
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun parseInRangeRejectsOutOfRange() {
        assertNull(parseInRange("4", 5..4096))
        assertNull(parseInRange("4097", 5..4096))
        assertNull(parseInRange("", 5..4096))
        assertEquals(5, parseInRange(" 5 ", 5..4096))
    }

    @Test
    fun invalidSizeBlocksSavingValidSizeSaves() {
        var saved: Triple<Int, Int, Int>? = null
        compose.setContent {
            AppTheme(dynamic = false) {
                SettingsScreen(Settings(), null, SettingsActions(onSaveOutput = { kb, a, b -> saved = Triple(kb, a, b) }))
            }
        }
        compose.saveScreenshot("settings")
        val kbField = hasSetTextAction() and hasText("4096")
        compose.onNode(kbField).performTextReplacement("9999")
        compose.onNodeWithText("Übernehmen").assertIsNotEnabled()
        compose.onNodeWithText("5 bis 4096 KB").assertExists()
        compose.onNode(hasSetTextAction() and hasText("9999")).performTextReplacement("512")
        compose.onNodeWithText("Übernehmen").assertIsEnabled().performClick()
        assertEquals(Triple(512, 10, 1), saved)
    }
}
