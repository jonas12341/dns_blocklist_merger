package de.jonas.dnsblocklistmerger.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import de.jonas.dnsblocklistmerger.core.DomainStatus
import de.jonas.dnsblocklistmerger.data.ExceptionEntity
import de.jonas.dnsblocklistmerger.data.OutputDomainEntity
import de.jonas.dnsblocklistmerger.ui.domains.DomainsActions
import de.jonas.dnsblocklistmerger.ui.domains.DomainsScreen
import de.jonas.dnsblocklistmerger.ui.theme.AppTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = android.app.Application::class, qualifiers = "w411dp-h1000dp-xxhdpi")
class DomainsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val rows = listOf(
        OutputDomainEntity("ads.example.com", 3, 1, 33, DomainStatus.REQUIRED),
        OutputDomainEntity("tracker.example.net", 2, 2, 22, DomainStatus.INCLUDED),
        OutputDomainEntity("x.example.com", 1, 3, 11, DomainStatus.SUBDOMAIN),
        OutputDomainEntity("rare.example.org", 1, 3, 11, DomainStatus.CUT_BY_LIMIT),
        OutputDomainEntity("login.example.com", 1, 2, 21, DomainStatus.EXCEPTION),
    )

    @Test
    fun longDomainsAreShownInFull() {
        val long = "very-long-subdomain-name.tracking-and-telemetry.some-advertising-network.example.com"
        compose.setContent {
            AppTheme(dynamic = false) {
                DomainsScreen(
                    query = "",
                    totalDomains = 1,
                    exceptions = listOf(ExceptionEntity("another-$long", 0)),
                    exceptionDomains = setOf("another-$long"),
                    domains = flowOf(PagingData.from(listOf(OutputDomainEntity(long, 1, 1, 11, DomainStatus.REQUIRED)))).collectAsLazyPagingItems(),
                    busy = null,
                    actions = DomainsActions(),
                )
            }
        }
        // #28: one line, never wrapped; the full name is there and scrolls sideways.
        assertEquals(1, compose.onNodeWithText(long, useUnmergedTree = true).lineCount())
        assertEquals(1, compose.onNodeWithText("another-$long", useUnmergedTree = true).lineCount())
        compose.saveScreenshot("domains-long")
    }

    @Test
    fun showsDomainsWithStatusAndExcludeReportsDomain() {
        val excluded = mutableListOf<String>()
        compose.setContent {
            AppTheme(dynamic = false) {
                DomainsScreen(
                    query = "",
                    totalDomains = rows.size,
                    exceptions = listOf(ExceptionEntity("login.example.com", 0)),
                    exceptionDomains = setOf("login.example.com"),
                    domains = flowOf(PagingData.from(rows)).collectAsLazyPagingItems(),
                    busy = null,
                    actions = DomainsActions(onExclude = { excluded += it }),
                )
            }
        }
        compose.onNodeWithText("Rang 33 · 3× · beste P1").assertIsDisplayed()
        compose.onNodeWithText("Pflicht").assertIsDisplayed()
        compose.onNodeWithText("Zulassen").assertIsDisplayed()
        compose.saveScreenshot("domains")
        compose.onAllNodesWithText("Ausschließen")[0].performClick()
        assertEquals(listOf("ads.example.com"), excluded)
    }
}
