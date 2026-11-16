package de.jonas.dnsblocklistmerger.ui.domains

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import de.jonas.dnsblocklistmerger.core.DomainStatus
import de.jonas.dnsblocklistmerger.data.ExceptionEntity
import de.jonas.dnsblocklistmerger.data.OutputDomainEntity
import de.jonas.dnsblocklistmerger.ui.SubScreen
import de.jonas.dnsblocklistmerger.ui.formatNumber
import de.jonas.dnsblocklistmerger.ui.theme.Pill
import de.jonas.dnsblocklistmerger.ui.theme.PillKind

data class DomainsActions(
    val onBack: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onExclude: (String) -> Unit = {},
    val onAllow: (String) -> Unit = {},
    val onAddException: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
)

@Composable
fun DomainsScreen(
    query: String,
    totalDomains: Int,
    exceptions: List<ExceptionEntity>,
    exceptionDomains: Set<String>,
    domains: LazyPagingItems<OutputDomainEntity>,
    busy: String?,
    actions: DomainsActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    SubScreen("Domains & Ausnahmen", actions.onBack, snackbar) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (busy != null) {
                item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            }
            item { ExceptionsCard(exceptions, actions) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text("Domains (${formatNumber(totalDomains)})", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = query,
                        onValueChange = actions.onQuery,
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text("Domain suchen…") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Search,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            items(count = domains.itemCount, key = domains.itemKey { it.domain }) { index ->
                val item = domains[index]
                if (item != null) {
                    DomainRow(item, excluded = item.domain in exceptionDomains, actions)
                    HorizontalDivider()
                }
            }
            if (domains.itemCount == 0) {
                item {
                    Text(
                        if (query.isBlank()) "Noch keine Domains. Erst die Listen aktualisieren." else "Keine Domain gefunden.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExceptionsCard(exceptions: List<ExceptionEntity>, actions: DomainsActions) {
    var input by rememberSaveable { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dauerhafte Ausnahmen", style = MaterialTheme.typography.titleMedium)
            Text(
                "Werden nie geschrieben, auch wenn eine Liste sie enthält.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            for (e in exceptions) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.domain, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { actions.onAllow(e.domain) }) { Text("Entfernen") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it
                        invalid = false
                    },
                    placeholder = { Text("z. B. login.example.com") },
                    singleLine = true,
                    isError = invalid,
                    supportingText = if (invalid) ({ Text("Keine gültige Domain") }) else null,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        actions.onAddException(input) { ok -> if (ok) input = "" else invalid = true }
                    },
                    enabled = input.isNotBlank(),
                ) { Text("Hinzufügen") }
            }
        }
    }
}

@Composable
private fun DomainRow(item: OutputDomainEntity, excluded: Boolean, actions: DomainsActions) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.domain, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Rang ${item.rank} · ${item.frequency}× · beste P${item.bestPriority}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusPill(if (excluded) DomainStatus.EXCEPTION else item.status)
        if (excluded) {
            TextButton(onClick = { actions.onAllow(item.domain) }) { Text("Zulassen") }
        } else {
            TextButton(onClick = { actions.onExclude(item.domain) }) { Text("Ausschließen") }
        }
    }
}

@Composable
private fun StatusPill(status: DomainStatus) = when (status) {
    DomainStatus.REQUIRED -> Pill("Pflicht", PillKind.OK)
    DomainStatus.INCLUDED -> Pill("drin", PillKind.OK)
    DomainStatus.CUT_BY_LIMIT -> Pill("Limit", PillKind.WARN)
    DomainStatus.SUBDOMAIN -> Pill("Subdomain", PillKind.NEUTRAL)
    DomainStatus.EXCEPTION -> Pill("Ausnahme", PillKind.NEUTRAL)
}
