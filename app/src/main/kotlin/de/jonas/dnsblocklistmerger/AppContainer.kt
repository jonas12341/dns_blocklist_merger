package de.jonas.dnsblocklistmerger

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import de.jonas.dnsblocklistmerger.data.AppDatabase
import de.jonas.dnsblocklistmerger.data.BlocklistRepository
import de.jonas.dnsblocklistmerger.data.DomainFileStore
import de.jonas.dnsblocklistmerger.data.SettingsRepository
import de.jonas.dnsblocklistmerger.download.BlocklistDownloader
import de.jonas.dnsblocklistmerger.download.Refresher
import de.jonas.dnsblocklistmerger.smb.AesGcmCipher
import de.jonas.dnsblocklistmerger.smb.PasswordStore
import de.jonas.dnsblocklistmerger.smb.SmbWriter
import java.io.File

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Manual dependency wiring; one instance per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: AppDatabase = AppDatabase.create(appContext)
    val settings = SettingsRepository(appContext.settingsStore)
    val blocklists = BlocklistRepository(database, DomainFileStore(File(appContext.filesDir, "lists")))
    val refresher = Refresher(blocklists, settings, BlocklistDownloader())
    val passwords = PasswordStore(appContext.settingsStore, AesGcmCipher(AesGcmCipher::androidKeystoreKey))
    val publisher = OutputPublisher(blocklists, settings, passwords, SmbWriter())
    val controller = AppController(blocklists, settings, refresher, publisher)
}

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as App).container
