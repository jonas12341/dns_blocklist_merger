package de.jonas.dnsblocklistmerger.data

import androidx.paging.PagingSource
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.jonas.dnsblocklistmerger.core.DomainStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class DomainPagingTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun firstPage(query: String): List<OutputDomainEntity> {
        val result = db.outputDao().pageDomains(query).load(PagingSource.LoadParams.Refresh(null, 60, false))
        return (result as PagingSource.LoadResult.Page).data
    }

    @Test
    fun searchIn300kDomainsIsFast() = runBlocking {
        val rows = (0 until 300_000).map {
            OutputDomainEntity("host$it.tracker${it % 997}.example", 1 + it % 3, 1 + it % 4, (it % 50).toLong(), DomainStatus.INCLUDED)
        }
        db.outputDao().replaceDomains(rows)

        val start = System.nanoTime()
        val all = firstPage("")
        val search = firstPage("tracker42.")
        val none = firstPage("does-not-exist")
        val millis = (System.nanoTime() - start) / 1_000_000

        assertEquals(60, all.size)
        assertEquals(49L, all.first().rank)
        assertTrue(search.all { "tracker42." in it.domain })
        assertTrue(search.isNotEmpty())
        assertTrue(none.isEmpty())
        // Three page loads, each with its own count query; generous bound for slow CI machines.
        assertTrue("took $millis ms", millis < 3_000)
    }
}
