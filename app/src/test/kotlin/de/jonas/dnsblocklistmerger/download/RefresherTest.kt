package de.jonas.dnsblocklistmerger.download

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.jonas.dnsblocklistmerger.data.AppDatabase
import de.jonas.dnsblocklistmerger.data.BlocklistRepository
import de.jonas.dnsblocklistmerger.data.DomainFileStore
import de.jonas.dnsblocklistmerger.data.ListStatus
import de.jonas.dnsblocklistmerger.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class RefresherTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var repo: BlocklistRepository
    private lateinit var scope: CoroutineScope
    private lateinit var refresher: Refresher

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = BlocklistRepository(db, DomainFileStore(tmp.newFolder("lists")))
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "s.preferences_pb") }),
        )
        refresher = Refresher(repo, settings, BlocklistDownloader())
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
        server.shutdown()
    }

    @Test
    fun failedListKeepsLastGoodDomainsAndShowsError() = runBlocking {
        val good = repo.addList(server.url("/good").toString())
        val flaky = repo.addList(server.url("/flaky").toString())

        server.enqueue(MockResponse().setBody("||a.example^\n").setHeader("ETag", "\"g1\""))
        server.enqueue(MockResponse().setBody("0.0.0.0 b.example\n"))
        val first = refresher.refreshAll()
        assertEquals(2, first.updated)
        assertEquals(listOf("a.example", "b.example"), first.result.entries)

        server.enqueue(MockResponse().setResponseCode(304))
        server.enqueue(MockResponse().setResponseCode(503))
        val second = refresher.refreshAll()
        assertEquals(1, second.unchanged)
        assertEquals(1, second.failed)
        assertEquals(listOf("a.example", "b.example"), second.result.entries)

        assertEquals(ListStatus.OK, repo.getList(good)!!.status)
        val failed = repo.getList(flaky)!!
        assertEquals(ListStatus.ERROR, failed.status)
        assertEquals("Serverfehler: HTTP 503", failed.errorMessage)
        assertEquals(1, failed.domainCount)
    }
}
