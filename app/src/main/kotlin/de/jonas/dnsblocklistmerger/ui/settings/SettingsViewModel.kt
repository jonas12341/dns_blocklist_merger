package de.jonas.dnsblocklistmerger.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.jonas.dnsblocklistmerger.AppController
import de.jonas.dnsblocklistmerger.data.Settings
import de.jonas.dnsblocklistmerger.data.SettingsRepository
import de.jonas.dnsblocklistmerger.data.ThemeMode
import de.jonas.dnsblocklistmerger.smb.PasswordStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val passwords: PasswordStore,
    private val controller: AppController,
    private val onDailyTimeChanged: () -> Unit = {},
) : ViewModel() {
    val passwordSet: StateFlow<Boolean> = passwords.isSet.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Null until DataStore has answered, so the form never starts from defaults by mistake. */
    val settings: StateFlow<Settings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val busy: StateFlow<String?> = controller.busy

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { repo.setTheme(mode) }
    }

    /** Saves size and weights together and recomputes the output once. */
    fun saveOutput(maxKb: Int, a: Int, b: Int) {
        viewModelScope.launch {
            repo.setOutput(maxKb, a, b)
            controller.recompute()
            controller.message("Gespeichert, Ausgabe wird neu berechnet")
        }
    }

    fun setOmitSubdomains(value: Boolean) {
        viewModelScope.launch {
            repo.setOmitSubdomains(value)
            controller.recompute()
        }
    }

    fun setDailyRunMinutes(minutes: Int) {
        viewModelScope.launch {
            repo.setDailyRunMinutes(minutes)
            onDailyTimeChanged()
        }
    }

    fun refreshNow() = controller.refresh()

    fun saveSmbTarget(path: String, user: String, password: String?) {
        viewModelScope.launch {
            repo.setSmbTarget(path, user)
            if (password != null) passwords.save(password)
            controller.message("FRITZ!Box-Freigabe gespeichert")
        }
    }

    fun testSmb(path: String, user: String, password: String?) = controller.testConnection(path, user, password)
}
