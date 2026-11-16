package de.jonas.dnsblocklistmerger

import de.jonas.dnsblocklistmerger.data.BlocklistRepository
import de.jonas.dnsblocklistmerger.data.SettingsRepository
import de.jonas.dnsblocklistmerger.download.Refresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs the long jobs (refresh, recompute, writing the file) one at a time in an
 * app-wide scope, so they survive leaving a screen. UI reports go to [messages].
 */
class AppController(
    private val lists: BlocklistRepository,
    private val settings: SettingsRepository,
    private val refresher: Refresher,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val mutex = Mutex()
    private val _busy = MutableStateFlow<String?>(null)

    /** Short text of the running job, null when idle. */
    val busy: StateFlow<String?> = _busy

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    /** Runs [block] after any job that is already running. */
    fun launchJob(label: String, block: suspend () -> Unit) {
        scope.launch {
            mutex.withLock {
                _busy.value = label
                try {
                    block()
                } catch (e: Exception) {
                    message("Fehler: ${e.message ?: e.javaClass.simpleName}")
                } finally {
                    _busy.value = null
                }
            }
        }
    }

    fun refresh() = launchJob("Listen werden geladen …") {
        val summary = refresher.refreshAll()
        message(
            if (summary.failed == 0) {
                "Aktualisiert: ${summary.result.entries.size} Einträge"
            } else {
                "${summary.failed} Liste(n) konnten nicht geladen werden"
            },
        )
    }

    fun recompute() = launchJob("Ausgabe wird berechnet …") {
        lists.recompute(settings.current())
    }
}
