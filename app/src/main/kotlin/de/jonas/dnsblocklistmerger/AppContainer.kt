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
import androidx.work.Configuration
import androidx.work.WorkManager
import de.jonas.dnsblocklistmerger.work.AppWorkerFactory
import de.jonas.dnsblocklistmerger.work.DailyScheduler
import de.jonas.dnsblocklistmerger.work.UpdateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    val updateFlow = UpdateFlow(refresher, settings, publisher)
    val controller = AppController(blocklists, settings, publisher, updateFlow::run)

    fun scheduleDaily(replace: Boolean) {
        appScope.launch {
            DailyScheduler.schedule(WorkManager.getInstance(appContext), settings.current().dailyRunMinutes, replace)
        }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class App : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.scheduleDaily(replace = false)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(AppWorkerFactory { container.updateFlow.run() }).build()
}

val Context.container: AppContainer get() = (applicationContext as App).container
