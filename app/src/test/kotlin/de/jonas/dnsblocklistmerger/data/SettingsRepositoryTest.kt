package de.jonas.dnsblocklistmerger.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            repo.setOutput(512, 5, 3)
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
    fun outputValuesAreStoredInOneStep() {
        // #21: size and weights in one DataStore edit, so no observer sees the new size with the old weights.
        val seen = withRepo(File(tmp.root, "d.preferences_pb")) { repo ->
            val edits = mutableListOf<Settings>()
            coroutineScope {
                val job = launch(Dispatchers.Unconfined) { repo.settings.collect { edits += it } }
                repo.setOutput(2048, 7, 3)
                delay(200)
                job.cancel()
            }
            edits.map { Triple(it.maxKb, it.a, it.b) }
        }
        assertEquals(listOf(Triple(4096, 10, 1), Triple(2048, 7, 3)), seen)
    }

    @Test
    fun invalidValuesAreRejected() {
        withRepo(File(tmp.root, "c.preferences_pb")) { repo ->
            assertTrue(runCatching { repo.setOutput(4, 10, 1) }.isFailure)
            assertTrue(runCatching { repo.setOutput(Settings.MAX_KB + 1, 10, 1) }.isFailure)
            repo.setOutput(20_000, 10, 1)
            assertEquals(20_000, repo.current().maxKb)
            repo.setOutput(4096, 10, 1)
            assertTrue(runCatching { repo.setOutput(4096, -1, 1) }.isFailure)
            assertTrue(runCatching { repo.setOutput(4096, 10, 1001) }.isFailure)
            assertTrue(runCatching { repo.setDailyRunMinutes(24 * 60) }.isFailure)
            assertEquals(4096, repo.current().maxKb)
        }
    }
}
