package de.jonas.dnsblocklistmerger.data

import de.jonas.dnsblocklistmerger.core.InputText
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val maxKb: Int = MAX_KB,
    val a: Int = 10,
    val b: Int = 1,
    val omitSubdomains: Boolean = true,
    /** Time of the daily run in minutes after midnight. */
    val dailyRunMinutes: Int = 5 * 60,
    val smbPath: String = "",
    val smbUser: String = "",
) {
    val maxBytes: Long get() = maxKb * 1024L

    companion object {
        const val MIN_KB = 5
        const val MAX_KB = 4096
        const val MIN_WEIGHT = 0
        const val MAX_WEIGHT = 1000
    }
}

/** Settings in DataStore. The SMB password is not stored here, see the SMB credential store. */
class SettingsRepository(private val store: DataStore<Preferences>) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val maxKb = intPreferencesKey("max_kb")
        val a = intPreferencesKey("weight_a")
        val b = intPreferencesKey("weight_b")
        val omitSubdomains = booleanPreferencesKey("omit_subdomains")
        val dailyRunMinutes = intPreferencesKey("daily_run_minutes")
        val smbPath = stringPreferencesKey("smb_path")
        val smbUser = stringPreferencesKey("smb_user")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        val d = Settings()
        Settings(
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            maxKb = (p[Keys.maxKb] ?: d.maxKb).coerceIn(Settings.MIN_KB, Settings.MAX_KB),
            a = (p[Keys.a] ?: d.a).coerceIn(Settings.MIN_WEIGHT, Settings.MAX_WEIGHT),
            b = (p[Keys.b] ?: d.b).coerceIn(Settings.MIN_WEIGHT, Settings.MAX_WEIGHT),
            omitSubdomains = p[Keys.omitSubdomains] ?: d.omitSubdomains,
            dailyRunMinutes = (p[Keys.dailyRunMinutes] ?: d.dailyRunMinutes).coerceIn(0, 24 * 60 - 1),
            smbPath = p[Keys.smbPath] ?: d.smbPath,
            smbUser = p[Keys.smbUser] ?: d.smbUser,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setTheme(value: ThemeMode) = store.edit { it[Keys.theme] = value.name }

    suspend fun setMaxKb(value: Int) {
        require(value in Settings.MIN_KB..Settings.MAX_KB) { "max KB out of range: $value" }
        store.edit { it[Keys.maxKb] = value }
    }

    suspend fun setWeights(a: Int, b: Int) {
        require(a in Settings.MIN_WEIGHT..Settings.MAX_WEIGHT && b in Settings.MIN_WEIGHT..Settings.MAX_WEIGHT) {
            "weights out of range: a=$a b=$b"
        }
        store.edit {
            it[Keys.a] = a
            it[Keys.b] = b
        }
    }

    suspend fun setOmitSubdomains(value: Boolean) = store.edit { it[Keys.omitSubdomains] = value }

    suspend fun setDailyRunMinutes(value: Int) {
        require(value in 0 until 24 * 60) { "time out of range: $value" }
        store.edit { it[Keys.dailyRunMinutes] = value }
    }

    suspend fun setSmbTarget(path: String, user: String) = store.edit {
        it[Keys.smbPath] = InputText.withoutSpacesAfterDots(path)
        it[Keys.smbUser] = user.trim()
    }
}
