package de.jonas.dnsblocklistmerger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.jonas.dnsblocklistmerger.AppController
import de.jonas.dnsblocklistmerger.data.BlocklistEntity
import de.jonas.dnsblocklistmerger.data.BlocklistRepository
import de.jonas.dnsblocklistmerger.data.OutputStateEntity
import de.jonas.dnsblocklistmerger.data.Settings
import de.jonas.dnsblocklistmerger.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val lists: List<BlocklistEntity> = emptyList(),
    val output: OutputStateEntity? = null,
    val settings: Settings = Settings(),
    val exceptionCount: Int = 0,
    val busy: String? = null,
    val addError: String? = null,
)

class HomeViewModel(
    private val lists: BlocklistRepository,
    settings: SettingsRepository,
    private val controller: AppController,
) : ViewModel() {
    private val addError = MutableStateFlow<String?>(null)

    val state: StateFlow<HomeUiState> = combine(
        lists.observeLists(),
        lists.observeOutputState(),
        settings.settings,
        lists.observeExceptions(),
        combine(controller.busy, addError) { busy, error -> busy to error },
    ) { l, output, s, exceptions, (busy, error) ->
        HomeUiState(l, output, s, exceptions.size, busy, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun refresh() = controller.refresh()

    /** Returns true when the list was added, so the input can be cleared. */
    fun addList(url: String, onAdded: () -> Unit) {
        viewModelScope.launch {
            try {
                lists.addList(url)
                addError.value = null
                onAdded()
                controller.message("Liste hinzugefügt. Sie wird beim nächsten Aktualisieren geladen.")
            } catch (e: IllegalArgumentException) {
                addError.value = e.message
            }
        }
    }

    fun clearAddError() {
        addError.value = null
    }

    fun editUrl(id: Long, url: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                lists.updateUrl(id, url)
            } catch (e: IllegalArgumentException) {
                onResult(e.message ?: "Ungültige Adresse.")
                return@launch
            }
            onResult(null)
            controller.refresh()
        }
    }

    fun removeList(id: Long) {
        viewModelScope.launch {
            lists.removeList(id)
            controller.recompute()
        }
    }

    fun changePriority(list: BlocklistEntity, delta: Int) {
        viewModelScope.launch {
            lists.setPriority(list.id, list.priority + delta)
            controller.recompute()
        }
    }
}
