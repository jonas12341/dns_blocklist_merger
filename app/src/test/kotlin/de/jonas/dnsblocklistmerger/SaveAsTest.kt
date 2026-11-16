package de.jonas.dnsblocklistmerger

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.jonas.dnsblocklistmerger.core.BlocklistParser
import de.jonas.dnsblocklistmerger.data.AppDatabase
import de.jonas.dnsblocklistmerger.data.BlocklistRepository
import de.jonas.dnsblocklistmerger.data.DomainFileStore
import de.jonas.dnsblocklistmerger.data.Settings
import de.jonas.dnsblocklistmerger.data.SettingsRepository
import de.jonas.dnsblocklistmerger.download.BlocklistDownloader
import de.jonas.dnsblocklistmerger.download.Refresher
import de.jonas.dnsblocklistmerger.smb.AesGcmCipher
import de.jonas.dnsblocklistmerger.smb.PasswordStore
import de.jonas.dnsblocklistmerger.smb.SmbWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class SaveAsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun saveAsWritesTheRenderedOutput() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(tmp.root, "s.preferences_pb") })
            val settings = SettingsRepository(store)
            val repo = BlocklistRepository(db, DomainFileStore(tmp.newFolder("lists")))
            val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
            val publisher = OutputPublisher(repo, settings, PasswordStore(store, AesGcmCipher { key }), SmbWriter())
            val controller = AppController(repo, settings, Refresher(repo, settings, BlocklistDownloader()), publisher, scope)

            val id = repo.addList("https://one.example/list")
            repo.recordSuccess(id, BlocklistParser.parse("||b.example^\n||a.example^"), null, null)
            repo.recompute(Settings())

            val out = File(tmp.root, "blocklist.txt")
            val message = async(start = CoroutineStart.UNDISPATCHED) { controller.messages.first() }
            controller.saveAs("file") { out.outputStream() }
            assertEquals("Gespeichert (0 KB)", withTimeout(5_000) { message.await() })
            assertEquals("||a.example^\n||b.example^\n", out.readText())
        } finally {
            scope.cancel()
            db.close()
        }
    }
}
