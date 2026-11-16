package de.jonas.dnsblocklistmerger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.jonas.dnsblocklistmerger.core.BlocklistParser
import de.jonas.dnsblocklistmerger.core.DomainStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class BlocklistRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var repo: BlocklistRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = BlocklistRepository(db, DomainFileStore(tmp.newFolder("lists")), clock = { now })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun addListGivesNextPriorityAndRejectsDuplicatesAndBadUrls() = runTest {
        val first = repo.addList("https://big.oisd.nl")
        val second = repo.addList("https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.txt")
        val lists = repo.observeLists().first()
        assertEquals(listOf(first, second), lists.map { it.id })
        assertEquals(listOf(1, 2), lists.map { it.priority })
        assertEquals("big.oisd.nl", lists[0].name)
        assertEquals("hagezi/dns-blocklists pro.txt", lists[1].name)
        assertEquals(ListStatus.PENDING, lists[0].status)

        assertTrue(runCatching { repo.addList("https://big.oisd.nl") }.isFailure)
        assertTrue(runCatching { repo.addList("ftp://example.com/list") }.isFailure)
        assertTrue(runCatching { repo.addList("not a url") }.isFailure)
    }

    @Test
    fun successErrorAndNotModifiedUpdateStatusAndKeepLastGoodDomains() = runTest {
        val id = repo.addList("https://example.com/list.txt")
        repo.recordSuccess(id, BlocklistParser.parse("||a.example^\n||b.example^"), etag = "\"v1\"", lastModified = "Mon")
        var list = repo.getList(id)!!
        assertEquals(ListStatus.OK, list.status)
        assertEquals(2, list.domainCount)
        assertEquals("\"v1\"", list.etag)
        assertEquals(1_000L, list.lastSuccessAt)

        now = 2_000L
        repo.recordError(id, "HTTP 503")
        list = repo.getList(id)!!
        assertEquals(ListStatus.ERROR, list.status)
        assertEquals("HTTP 503", list.errorMessage)
        assertEquals(1_000L, list.lastSuccessAt)
        assertEquals(2_000L, list.lastAttemptAt)

        val result = repo.recompute(Settings())
        assertEquals(listOf("a.example", "b.example"), result.entries)

        now = 3_000L
        repo.recordNotModified(id)
        list = repo.getList(id)!!
        assertEquals(ListStatus.OK, list.status)
        assertNull(list.errorMessage)
        assertEquals(3_000L, list.lastSuccessAt)
    }

    @Test
    fun recomputeStoresDomainsStateAndRenderedOutput() = runTest {
        val top = repo.addList("https://one.example/list")
        val other = repo.addList("https://two.example/list")
        repo.recordSuccess(top, BlocklistParser.parse("||a.example^\n||ads.a.example^"), null, null)
        repo.recordSuccess(other, BlocklistParser.parse("||b.example^\n||c.example^"), null, null)
        assertTrue(repo.addException("C.Example"))
        assertFalse(repo.addException("not valid"))

        repo.recompute(Settings())
        val state = repo.observeOutputState().first()!!
        assertEquals(2, state.entries)
        assertEquals(1, state.exceptions)
        assertEquals(1, state.droppedSubdomains)
        assertEquals(4, state.totalDomains)
        assertEquals(1, state.topPriority)
        assertEquals("||a.example^\n||b.example^\n", repo.renderOutput())
        assertEquals(1, db.outputDao().countByStatus(DomainStatus.REQUIRED))

        repo.recordWritten(error = null)
        assertEquals(now, repo.observeOutputState().first()!!.writtenAt)
        repo.recordWritten(error = "Anmeldung fehlgeschlagen")
        val failed = repo.observeOutputState().first()!!
        assertEquals("Anmeldung fehlgeschlagen", failed.writeError)
        assertEquals(now, failed.writtenAt)
    }

    @Test
    fun removingAListDropsItsDomains() = runTest {
        val id = repo.addList("https://one.example/list")
        repo.recordSuccess(id, BlocklistParser.parse("||a.example^"), null, null)
        repo.removeList(id)
        assertTrue(repo.allLists().isEmpty())
        assertTrue(repo.recompute(Settings()).entries.isEmpty())
    }

    @Test
    fun priorityIsClamped() = runTest {
        val id = repo.addList("https://one.example/list")
        repo.setPriority(id, 0)
        assertEquals(1, repo.getList(id)!!.priority)
        repo.setPriority(id, 500)
        assertEquals(BlocklistRepository.MAX_PRIORITY, repo.getList(id)!!.priority)
    }
}
