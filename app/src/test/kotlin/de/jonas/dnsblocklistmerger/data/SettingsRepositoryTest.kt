package de.jonas.dnsblocklistmerger.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun <T> withRepo(file: File, block: suspend (SettingsRepository) -> T): T {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            return runBlocking { block(SettingsRepository(store)) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun defaultsMatchTheSpec() {
        val s = withRepo(File(tmp.root, "a.preferences_pb")) { it.current() }
        assertEquals(ThemeMode.SYSTEM, s.theme)
        assertEquals(4096, s.maxKb)
        assertEquals(4096L * 1024, s.maxBytes)
        assertEquals(10, s.a)
        assertEquals(1, s.b)
        assertTrue(s.omitSubdomains)
    }

    @Test
    fun valuesSurviveARestart() {
        val file = File(tmp.root, "b.preferences_pb")
        withRepo(file) { repo ->
            repo.setTheme(ThemeMode.DARK)
            repo.setMaxKb(512)
            repo.setWeights(5, 3)
            repo.setOmitSubdomains(false)
            repo.setDailyRunMinutes(4 * 60 + 30)
            repo.setSmbTarget(" smb://fritz.box/FRITZ.NAS/dnsfilter/blocklist.txt ", "jonas")
        }
        // Wait for the first store's scope to release the file before reopening it.
        Thread.sleep(200)
        val s = withRepo(file) { it.current() }
        assertEquals(Settings(ThemeMode.DARK, 512, 5, 3, false, 270, "smb://fritz.box/FRITZ.NAS/dnsfilter/blocklist.txt", "jonas"), s)
    }

    @Test
    fun invalidValuesAreRejected() {
        withRepo(File(tmp.root, "c.preferences_pb")) { repo ->
            assertTrue(runCatching { repo.setMaxKb(4) }.isFailure)
            assertTrue(runCatching { repo.setMaxKb(4097) }.isFailure)
            assertTrue(runCatching { repo.setWeights(-1, 1) }.isFailure)
            assertTrue(runCatching { repo.setDailyRunMinutes(24 * 60) }.isFailure)
            assertEquals(4096, repo.current().maxKb)
        }
    }
}
