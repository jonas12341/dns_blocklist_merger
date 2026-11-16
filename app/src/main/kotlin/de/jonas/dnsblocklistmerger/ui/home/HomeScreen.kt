package de.jonas.dnsblocklistmerger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.jonas.dnsblocklistmerger.core.InputText
import de.jonas.dnsblocklistmerger.data.BlocklistEntity
import de.jonas.dnsblocklistmerger.data.ListStatus
import de.jonas.dnsblocklistmerger.data.OutputStateEntity
import de.jonas.dnsblocklistmerger.ui.formatDateTime
import de.jonas.dnsblocklistmerger.ui.formatKb
import de.jonas.dnsblocklistmerger.ui.formatNumber
import de.jonas.dnsblocklistmerger.ui.formatTimeOfDay
import de.jonas.dnsblocklistmerger.ui.theme.AppTheme
import de.jonas.dnsblocklistmerger.ui.theme.Pill
import de.jonas.dnsblocklistmerger.ui.theme.PillKind

data class HomeActions(
    val onRefresh: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onOpenDomains: () -> Unit = {},
    val onWriteToFritzBox: () -> Unit = {},
    val onSaveAs: () -> Unit = {},
    val onAddList: (String, () -> Unit) -> Unit = { _, _ -> },
    val onClearAddError: () -> Unit = {},
    val onRemoveList: (Long) -> Unit = {},
    /** Reports null when the URL was saved, otherwise the error text. */
    val onEditUrl: (Long, String, (String?) -> Unit) -> Unit = { _, _, _ -> },
    val onChangePriority: (BlocklistEntity, Int) -> Unit = { _, _ -> },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, snackbar: SnackbarHostState = remember { SnackbarHostState() }) {
    var confirmRemove by remember { mutableStateOf<BlocklistEntity?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocklist Merger") },
                actions = {
                    IconButton(onClick = actions.onRefresh, enabled = state.busy == null) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                    IconButton(onClick = actions.onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.busy != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(state.busy, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item { OutputCard(state, actions) }
            item { DomainsLinkCard(state, actions.onOpenDomains) }
            item {
                Text(
                    "Blocklisten nach Priorität",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (state.lists.isEmpty()) {
                item { Text("Noch keine Blockliste. Füge unten eine Adresse hinzu.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(state.lists, key = { it.id }) { list ->
                ListCard(
                    list,
                    onPriority = { actions.onChangePriority(list, it) },
                    onEditUrl = { url, done -> actions.onEditUrl(list.id, url, done) },
                    onRemove = { confirmRemove = list },
                )
            }
            item { AddListCard(state.addError, actions.onAddList, actions.onClearAddError) }
        }
    }
    confirmRemove?.let { list ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Liste entfernen?") },
            text = { Text(list.name) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onRemoveList(list.id)
                    confirmRemove = null
                }) { Text("Entfernen") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun OutputCard(state: HomeUiState, actions: HomeActions) {
    val out = state.output
    val limit = state.settings.maxBytes
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ausgabedatei", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                when {
                    out?.computedAt == null -> Pill("noch nicht berechnet", PillKind.NEUTRAL)
                    out.requiredBytes > limit -> Pill("P${out.topPriority} gekürzt", PillKind.WARN)
                    out.cutByLimit > 0 -> Pill("gekürzt", PillKind.WARN)
                    else -> Pill("vollständig", PillKind.OK)
                }
            }
            val bytes = out?.bytes ?: 0
            UsageMeter(required = minOf(out?.requiredBytes ?: 0, bytes), total = bytes, limit = limit)
            Text(
                "${formatKb(bytes)} von ${formatNumber(state.settings.maxKb)} KB · dunkel: Pflicht" +
                    (out?.topPriority?.let { " (P$it)" } ?: "") + ", hell: nach Rang aufgefüllt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (out?.computedAt != null && out.requiredBytes > limit) {
                Text(
                    "P${out.topPriority} allein hat ${formatKb(out.requiredBytes)} und passt nicht ins Limit. " +
                        "Auch P${out.topPriority} wird darum nach Rang bis zur maximalen Größe aufgefüllt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(formatNumber(out?.entries ?: 0), "Einträge", Modifier.weight(1f))
                Stat(formatNumber(out?.cutByLimit ?: 0), "wegen Limit raus", Modifier.weight(1f))
                Stat(formatNumber(out?.exceptions ?: 0), "Ausnahmen", Modifier.weight(1f))
            }
            Text(
                "Zuletzt geschrieben: " + (out?.writtenAt?.let(::formatDateTime) ?: "noch nie") +
                    " · nächster Lauf täglich " + formatTimeOfDay(state.settings.dailyRunMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            out?.writeError?.let { ErrorBox("Schreiben fehlgeschlagen: $it") }
            Button(
                onClick = actions.onWriteToFritzBox,
                enabled = state.busy == null && out?.computedAt != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Auf FRITZ!Box schreiben") }
            OutlinedButton(
                onClick = actions.onSaveAs,
                enabled = out?.computedAt != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Speichern unter…") }
        }
    }
}

@Composable
private fun UsageMeter(required: Long, total: Long, limit: Long) {
    val requiredShare = if (limit > 0) (required.toFloat() / limit).coerceIn(0f, 1f) else 0f
    val totalShare = if (limit > 0) (total.toFloat() / limit).coerceIn(0f, 1f) else 0f
    val primary = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .fillMaxWidth()
            .height(12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .semantics { contentDescription = "Belegung ${formatKb(total)} von ${formatKb(limit)}" },
    ) {
        Box(Modifier.fillMaxWidth(totalShare).height(12.dp).background(primary.copy(alpha = 0.4f), RoundedCornerShape(6.dp)))
        Box(Modifier.fillMaxWidth(requiredShare).height(12.dp).background(primary, RoundedCornerShape(6.dp)))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DomainsLinkCard(state: HomeUiState, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Domains & Ausnahmen", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${formatNumber(state.output?.totalDomains ?: 0)} Domains · ${state.exceptionCount} Ausnahmen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun ListCard(
    list: BlocklistEntity,
    onPriority: (Int) -> Unit,
    onEditUrl: (String, (String?) -> Unit) -> Unit,
    onRemove: () -> Unit,
) {
    // Collapsed the URL is cut to one line; a tap on name or URL shows it in full.
    var expanded by rememberSaveable(list.id) { mutableStateOf(false) }
    var editing by rememberSaveable(list.id) { mutableStateOf(false) }
    val lines = if (expanded) Int.MAX_VALUE else 1
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable(onClickLabel = if (expanded) "URL kürzen" else "Ganze URL zeigen") { expanded = !expanded }) {
                    Text(list.name, style = MaterialTheme.typography.titleSmall, maxLines = lines, overflow = TextOverflow.Ellipsis)
                    Text(
                        list.url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = lines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                PriorityStepper(list.priority, onPriority)
            }
            if (editing) EditUrl(list, onEditUrl, onClose = { editing = false })
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (list.status) {
                    ListStatus.OK -> Pill("aktuell", PillKind.OK)
                    ListStatus.ERROR -> Pill("Fehler", PillKind.ERROR)
                    ListStatus.PENDING -> Pill("noch nicht geladen", PillKind.NEUTRAL)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    list.lastSuccessAt?.let {
                        "Stand ${formatDateTime(it)} · ${formatNumber(list.domainCount)} Domains" +
                            (list.format?.let { f -> " · ${formatName(f)}" } ?: "")
                    } ?: "wird beim nächsten Lauf geladen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { editing = true }, enabled = !editing) { Icon(Icons.Filled.Edit, contentDescription = "Bearbeiten") }
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "Entfernen") }
            }
            list.errorMessage?.let { ErrorBox(it + if (list.lastSuccessAt != null) ". Der letzte gute Stand wird genutzt." else "") }
        }
    }
}

private fun formatName(format: String) = when (format) {
    "ADBLOCK" -> "Adblock"
    "HOSTS" -> "Hosts"
    else -> "Domain-Liste"
}

@Composable
private fun PriorityStepper(priority: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Lower number means higher priority, so "−" raises it.
        TextButton(onClick = { onChange(-1) }, enabled = priority > 1) { Text("−") }
        Text("P$priority", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { contentDescription = "Priorität $priority, 1 ist die höchste" })
        TextButton(onClick = { onChange(1) }) { Text("+") }
    }
}

@Composable
fun ErrorBox(text: String) {
    Text(
        text,
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
            .padding(10.dp),
    )
}

@Composable
private fun EditUrl(list: BlocklistEntity, onSave: (String, (String?) -> Unit) -> Unit, onClose: () -> Unit) {
    var url by rememberSaveable(list.id) { mutableStateOf(list.url) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = InputText.withoutWhitespace(it)
                error = null
            },
            label = { Text("URL") },
            isError = error != null,
            supportingText = { Text(error ?: "Nach dem Speichern wird die Liste neu geladen.") },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(url) { e -> if (e == null) onClose() else error = e } }, enabled = url.isNotBlank()) {
                Text("Speichern")
            }
            OutlinedButton(onClick = onClose) { Text("Abbrechen") }
        }
    }
}

@Composable
private fun AddListCard(error: String?, onAdd: (String, () -> Unit) -> Unit, onClearError: () -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Blockliste hinzufügen", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = url,
                onValueChange = {
                    url = InputText.withoutWhitespace(it)
                    if (error != null) onClearError()
                },
                placeholder = { Text("https://…/blocklist.txt") },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onAdd(url) { url = "" } }, enabled = url.isNotBlank()) { Text("Hinzufügen") }
        }
    }
}

private val previewLists = listOf(
    BlocklistEntity(1, "https://big.oisd.nl", "big.oisd.nl", 1, "ADBLOCK", ListStatus.OK, null, 1_763_272_800_000, 1_763_272_800_000, domainCount = 228_394),
    BlocklistEntity(
        2, "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.txt", "hagezi/dns-blocklists pro.txt", 2,
        "ADBLOCK", ListStatus.OK, null, 1_763_272_800_000, 1_763_272_800_000, domainCount = 238_185,
    ),
    BlocklistEntity(
        3, "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts", "StevenBlack/hosts hosts", 2,
        "HOSTS", ListStatus.OK, null, 1_763_186_400_000, 1_763_186_400_000, domainCount = 76_104,
    ),
)

private val previewOutput = OutputStateEntity(
    computedAt = 1_763_272_800_000, entries = 182_000, bytes = 4_100_000, requiredBytes = 3_100_000, maxBytes = 4096L * 1024,
    topPriority = 1, cutByLimit = 64_000, exceptions = 3, droppedSubdomains = 12_000, totalDomains = 410_000, writtenAt = 1_763_272_900_000,
)

@Preview(name = "Normal", showBackground = true, heightDp = 1100)
@Composable
private fun HomePreviewNormal() = AppTheme(dynamic = false) {
    HomeScreen(HomeUiState(previewLists, previewOutput, exceptionCount = 3), HomeActions())
}

@Preview(name = "Fehler", showBackground = true, heightDp = 1100)
@Composable
private fun HomePreviewError() = AppTheme(dynamic = false) {
    val lists = previewLists.mapIndexed { i, l ->
        if (i == 2) l.copy(status = ListStatus.ERROR, errorMessage = "Serverfehler: HTTP 503") else l
    }
    HomeScreen(HomeUiState(lists, previewOutput, exceptionCount = 3), HomeActions())
}

@Preview(name = "Leer", showBackground = true)
@Composable
private fun HomePreviewEmpty() = AppTheme(dynamic = false) {
    HomeScreen(HomeUiState(), HomeActions())
}

@Preview(name = "Dunkel", showBackground = true, heightDp = 1100, backgroundColor = 0xFF000000)
@Composable
private fun HomePreviewDark() = AppTheme(dark = true, dynamic = false) {
    HomeScreen(HomeUiState(previewLists, previewOutput.copy(writeError = "Anmeldung fehlgeschlagen")), HomeActions())
}
