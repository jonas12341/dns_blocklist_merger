package de.jonas.dnsblocklistmerger.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import de.jonas.dnsblocklistmerger.ui.settings.sliderEndFor
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
    fun sizesAbove4096AreAllowedWithAWarning() {
        var saved: Int? = null
        compose.setContent {
            AppTheme(dynamic = false) {
                SettingsScreen(Settings(), null, SettingsActions(onSaveOutput = { kb, _, _ -> saved = kb }))
            }
        }
        compose.onNode(hasSetTextAction() and hasText("4096")).performTextReplacement("20000")
        compose.onNodeWithText("Über 4.096 KB nimmt die FRITZ!Box die Datei evtl. nicht an.").assertExists()
        compose.onNodeWithText("Maximale Größe: 20.000 KB").assertExists()
        compose.saveScreenshot("settings-large")
        compose.onNodeWithText("Übernehmen").assertIsEnabled().performClick()
        assertEquals(20_000, saved)
    }

    @Test
    fun sliderEndGrowsInWholeMb() {
        assertEquals(4096, sliderEndFor(512))
        assertEquals(4096, sliderEndFor(4096))
        assertEquals(5120, sliderEndFor(4097))
        assertEquals(20480, sliderEndFor(20_000))
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
        compose.onNode(kbField).performTextReplacement("4")
        compose.onNodeWithText("Übernehmen").assertIsNotEnabled()
        compose.onNodeWithText("5 bis 1.048.576 KB").assertExists()
        compose.onNode(hasSetTextAction() and hasText("4")).performTextReplacement("512")
        compose.onNodeWithText("Übernehmen").assertIsEnabled().performClick()
        assertEquals(Triple(512, 10, 1), saved)
    }

    @Test
    fun applyingSizeAndWeightsKeepsTheNewValues() {
        // #21: the repository stores size and weights in two steps, like SettingsViewModel.saveOutput.
        var settings by mutableStateOf(Settings())
        compose.setContent {
            AppTheme(dynamic = false) {
                SettingsScreen(settings, null, SettingsActions(onSaveOutput = { kb, a, b ->
                    settings = settings.copy(maxKb = kb)
                    settings = settings.copy(a = a, b = b)
                }))
            }
        }
        compose.onNode(hasSetTextAction() and hasText("4096")).performTextReplacement("2048")
        compose.onNode(hasSetTextAction() and hasText("10")).performTextReplacement("7")
        compose.onNode(hasSetTextAction() and hasText("1")).performTextReplacement("3")
        compose.onNodeWithText("Übernehmen").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(Triple(2048, 7, 3), Triple(settings.maxKb, settings.a, settings.b))
        compose.onNode(hasSetTextAction() and hasText("2048")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("7")).assertExists()
        compose.onNode(hasSetTextAction() and hasText("3")).assertExists()
        compose.onNodeWithText("Übernehmen").assertIsNotEnabled()
    }
}
