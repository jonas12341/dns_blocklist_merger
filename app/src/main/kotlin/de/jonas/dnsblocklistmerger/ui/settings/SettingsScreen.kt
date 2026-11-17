package de.jonas.dnsblocklistmerger.ui.settings

import de.jonas.dnsblocklistmerger.core.InputText
import de.jonas.dnsblocklistmerger.smb.SmbTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import de.jonas.dnsblocklistmerger.data.Settings
import de.jonas.dnsblocklistmerger.data.ThemeMode
import de.jonas.dnsblocklistmerger.ui.SubScreen
import de.jonas.dnsblocklistmerger.ui.formatNumber
import de.jonas.dnsblocklistmerger.ui.formatTimeOfDay
import kotlin.math.roundToInt

data class SettingsActions(
    val onBack: () -> Unit = {},
    val onTheme: (ThemeMode) -> Unit = {},
    val onSaveOutput: (maxKb: Int, a: Int, b: Int) -> Unit = { _, _, _ -> },
    val onOmitSubdomains: (Boolean) -> Unit = {},
    val onDailyRunMinutes: (Int) -> Unit = {},
    val onRefreshNow: () -> Unit = {},
    /** Password null keeps the stored one. */
    val onSaveSmb: (path: String, user: String, password: String?) -> Unit = { _, _, _ -> },
    val onTestSmb: (path: String, user: String, password: String?) -> Unit = { _, _, _ -> },
)

/** Parses a whole number in [range], or null for anything else. */
/** Slider end for [kb]: at least 4096 KB, otherwise [kb] rounded up to whole MB. */
fun sliderEndFor(kb: Int): Int = maxOf(Settings.FRITZBOX_KB, (kb + 1023) / 1024 * 1024)

fun parseInRange(text: String, range: IntRange): Int? = text.trim().toIntOrNull()?.takeIf { it in range }

@Composable
fun SettingsScreen(
    settings: Settings?,
    busy: String?,
    actions: SettingsActions,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    passwordSet: Boolean = false,
) {
    SubScreen("Einstellungen", actions.onBack, snackbar) { padding ->
        if (settings == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@SubScreen
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThemeCard(settings.theme, actions.onTheme)
            OutputCard(settings, actions)
            UpdateCard(settings.dailyRunMinutes, busy, actions)
            SmbCard(settings, passwordSet, busy, actions)
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeCard(current: ThemeMode, onTheme: (ThemeMode) -> Unit) = SectionCard("Darstellung") {
    val options = listOf(ThemeMode.SYSTEM to "Wie System", ThemeMode.DARK to "Dunkel")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (mode, label) ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onTheme(mode) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
    Hint("„Wie System“ folgt der Android-Einstellung hell/dunkel.")
}

@Composable
private fun OutputCard(settings: Settings, actions: SettingsActions) = SectionCard("Ausgabe") {
    var maxKb by rememberSaveable(settings.maxKb) { mutableStateOf(settings.maxKb.toString()) }
    var a by rememberSaveable(settings.a) { mutableStateOf(settings.a.toString()) }
    var b by rememberSaveable(settings.b) { mutableStateOf(settings.b.toString()) }
    val kbRange = Settings.MIN_KB..Settings.MAX_KB
    val weightRange = Settings.MIN_WEIGHT..Settings.MAX_WEIGHT
    val kbValue = parseInRange(maxKb, kbRange)
    val aValue = parseInRange(a, weightRange)
    val bValue = parseInRange(b, weightRange)

    // The slider ends at 4096 KB or at the largest size typed so far; typing a larger value widens it.
    var sliderEnd by rememberSaveable(settings.maxKb) { mutableStateOf(sliderEndFor(settings.maxKb)) }

    Text("Maximale Größe: ${kbValue?.let { formatNumber(it) + " KB" } ?: "–"}", fontWeight = FontWeight.Medium)
    Slider(
        value = (kbValue ?: settings.maxKb).coerceAtMost(sliderEnd).toFloat(),
        onValueChange = { maxKb = it.roundToInt().toString() },
        valueRange = Settings.MIN_KB.toFloat()..sliderEnd.toFloat(),
    )
    OutlinedTextField(
        value = maxKb,
        onValueChange = {
            maxKb = it.filter(Char::isDigit).take(7)
            parseInRange(maxKb, kbRange)?.let { kb -> sliderEnd = maxOf(sliderEnd, sliderEndFor(kb)) }
        },
        label = { Text("KB") },
        isError = kbValue == null,
        supportingText = {
            Text(
                when {
                    kbValue == null -> "${formatNumber(Settings.MIN_KB)} bis ${formatNumber(Settings.MAX_KB)} KB"
                    kbValue > Settings.FRITZBOX_KB -> "Über ${formatNumber(Settings.FRITZBOX_KB)} KB nimmt die FRITZ!Box die Datei evtl. nicht an."
                    else -> "Größer? Wert hier eintippen, der Regler passt sich an."
                },
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WeightField("Gewicht Priorität (a)", a, aValue == null, Modifier.weight(1f)) { a = it }
        WeightField("Gewicht Häufigkeit (b)", b, bValue == null, Modifier.weight(1f)) { b = it }
    }
    Hint("Rang = a × Prioritätswert + b × Häufigkeit")
    val changed = kbValue != settings.maxKb || aValue != settings.a || bValue != settings.b
    Button(
        onClick = { actions.onSaveOutput(kbValue!!, aValue!!, bValue!!) },
        enabled = changed && kbValue != null && aValue != null && bValue != null,
    ) { Text("Übernehmen") }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Subdomains weglassen, wenn die Hauptdomain drin ist", modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = settings.omitSubdomains, onCheckedChange = actions.onOmitSubdomains)
    }
}

@Composable
private fun WeightField(label: String, value: String, error: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(4)) },
        label = { Text(label) },
        isError = error,
        supportingText = if (error) ({ Text("0 bis 1000") }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UpdateCard(minutes: Int, busy: String?, actions: SettingsActions) = SectionCard("Aktualisierung") {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Täglich um ${formatTimeOfDay(minutes)}", modifier = Modifier.weight(1f))
        TextButton(onClick = { picking = true }) { Text("Ändern") }
    }
    OutlinedButton(onClick = actions.onRefreshNow, enabled = busy == null) { Text("Jetzt aktualisieren") }
    if (picking) {
        val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Uhrzeit des täglichen Laufs") },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDailyRunMinutes(state.hour * 60 + state.minute)
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SmbCard(settings: Settings, passwordSet: Boolean, busy: String?, actions: SettingsActions) =
    SectionCard("FRITZ!Box-Freigabe") {
        var path by rememberSaveable(settings.smbPath) { mutableStateOf(settings.smbPath) }
        var user by rememberSaveable(settings.smbUser) { mutableStateOf(settings.smbUser) }
        // Never prefilled: the stored password is not shown again.
        var password by remember { mutableStateOf("") }
        val pathOk = path.isBlank() || SmbTarget.hasKnownPrefix(path)
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("Pfad") },
            placeholder = { Text("smb://fritz.box/FRITZ.NAS/dnsfilter/blocklist.txt") },
            isError = !pathOk,
            supportingText = {
                Text(
                    if (pathOk) "smb://Server/Freigabe/Ordner/Datei.txt; endet der Pfad auf einen Ordner, " +
                        "schreibt die App ${SmbTarget.DEFAULT_FILE_NAME} hinein."
                    else "Muss mit smb:// oder // beginnen",
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Benutzer") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Passwort") },
            placeholder = { if (passwordSet) Text("gespeichert, leer lassen zum Behalten") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Hint("Das Passwort wird verschlüsselt im Android-Keystore-Bereich gespeichert.")
        val newPassword = password.ifEmpty { null }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    actions.onSaveSmb(path, user, newPassword)
                    password = ""
                },
                enabled = pathOk && (InputText.withoutSpacesAfterDots(path) != settings.smbPath || user.trim() != settings.smbUser || newPassword != null),
            ) { Text("Speichern") }
            OutlinedButton(
                onClick = { actions.onTestSmb(path, user, newPassword) },
                enabled = busy == null && pathOk && path.isNotBlank() && user.isNotBlank() && (passwordSet || newPassword != null),
            ) { Text("Verbindung testen") }
        }
    }
