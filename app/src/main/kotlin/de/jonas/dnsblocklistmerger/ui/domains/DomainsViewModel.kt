package de.jonas.dnsblocklistmerger.ui.domains

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import de.jonas.dnsblocklistmerger.AppController
import de.jonas.dnsblocklistmerger.core.InputText
import de.jonas.dnsblocklistmerger.data.BlocklistRepository
import de.jonas.dnsblocklistmerger.data.ExceptionEntity
import de.jonas.dnsblocklistmerger.data.OutputDomainEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class DomainsViewModel(
    private val lists: BlocklistRepository,
    private val controller: AppController,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    val exceptions: StateFlow<List<ExceptionEntity>> =
        lists.observeExceptions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exceptionDomains: StateFlow<Set<String>> =
        lists.observeExceptions().map { l -> l.mapTo(HashSet()) { it.domain } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val totalDomains: StateFlow<Int> =
        lists.observeOutputState().map { it?.totalDomains ?: 0 }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val busy: StateFlow<String?> = controller.busy

    val domains: Flow<PagingData<OutputDomainEntity>> = _query
        .debounce(250)
        .map { InputText.withoutWhitespace(it).lowercase() }
        .distinctUntilChanged()
        .flatMapLatest { q -> Pager(PagingConfig(pageSize = 60, enablePlaceholders = false)) { lists.pageDomains(q) }.flow }
        .cachedIn(viewModelScope)

    fun setQuery(value: String) {
        _query.value = value
    }

    fun exclude(domain: String) = changeExceptions { lists.addException(domain) }

    fun allow(domain: String) = changeExceptions { lists.removeException(domain) }

    /** Returns false when the input is not a valid domain. */
    fun addException(raw: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = lists.addException(raw)
            onResult(ok)
            if (ok) controller.recompute()
        }
    }

    private fun changeExceptions(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            controller.recompute()
        }
    }
}
