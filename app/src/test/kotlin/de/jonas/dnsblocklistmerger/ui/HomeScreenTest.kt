package de.jonas.dnsblocklistmerger.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import de.jonas.dnsblocklistmerger.data.BlocklistEntity
import de.jonas.dnsblocklistmerger.data.ListStatus
import de.jonas.dnsblocklistmerger.data.OutputStateEntity
import de.jonas.dnsblocklistmerger.ui.home.HomeActions
import de.jonas.dnsblocklistmerger.ui.home.HomeScreen
import de.jonas.dnsblocklistmerger.ui.home.HomeUiState
import de.jonas.dnsblocklistmerger.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1400dp-xxhdpi")
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    private val lists = listOf(
        BlocklistEntity(1, "https://big.oisd.nl", "big.oisd.nl", 1, "ADBLOCK", ListStatus.OK, null, 1_763_272_800_000, 1_763_272_800_000, domainCount = 228_394),
        BlocklistEntity(2, "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", "StevenBlack/hosts hosts", 2, "HOSTS", ListStatus.ERROR, "Serverfehler: HTTP 503", 1_763_186_400_000, 1_763_272_800_000, domainCount = 76_104),
    )
    private val output = OutputStateEntity(
        computedAt = 1L, entries = 182_000, bytes = 4_100_000, requiredBytes = 3_100_000, maxBytes = 4096L * 1024,
        topPriority = 1, cutByLimit = 64_000, exceptions = 3, totalDomains = 410_000,
    )

    @Test
    fun showsErrorOnlyAtTheAffectedList() {
        compose.setContent { AppTheme(dynamic = false) { HomeScreen(HomeUiState(lists, output, exceptionCount = 3), HomeActions()) } }
        compose.onNodeWithText("Serverfehler: HTTP 503. Der letzte gute Stand wird genutzt.").assertIsDisplayed()
        compose.onNodeWithText("gekürzt").assertIsDisplayed()
        compose.onAllNodesWithText("Fehler").assertCountEquals(1)
        compose.saveScreenshot("home-error")
    }

    @Test
    fun emptyAppShowsHint() {
        compose.setContent { AppTheme(dynamic = false) { HomeScreen(HomeUiState(), HomeActions()) } }
        compose.onNodeWithText("Noch keine Blockliste. Füge unten eine Adresse hinzu.").assertIsDisplayed()
        compose.onNodeWithText("noch nicht berechnet").assertIsDisplayed()
        compose.saveScreenshot("home-empty")
    }

    @Test
    fun darkThemeRenders() {
        compose.setContent { AppTheme(dark = true, dynamic = false) { HomeScreen(HomeUiState(lists, output), HomeActions()) } }
        compose.saveScreenshot("home-dark")
    }

    @Test
    fun priorityButtonsReportDirection() {
        val changes = mutableListOf<Int>()
        compose.setContent {
            AppTheme(dynamic = false) {
                HomeScreen(HomeUiState(lists, output), HomeActions(onChangePriority = { _, d -> changes += d }))
            }
        }
        compose.onAllNodesWithText("+")[1].performClick()
        compose.onAllNodesWithText("−")[1].performClick()
        assertEquals(listOf(1, -1), changes)
    }
}
