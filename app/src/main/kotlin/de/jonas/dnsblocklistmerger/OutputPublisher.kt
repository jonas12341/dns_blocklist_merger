package de.jonas.dnsblocklistmerger

import de.jonas.dnsblocklistmerger.data.BlocklistRepository
import de.jonas.dnsblocklistmerger.data.SettingsRepository
import de.jonas.dnsblocklistmerger.smb.PasswordStore
import de.jonas.dnsblocklistmerger.smb.SmbCredentials
import de.jonas.dnsblocklistmerger.smb.SmbWriteException
import de.jonas.dnsblocklistmerger.smb.SmbWriter

/** Writes the current output to the FRITZ!Box share and records the outcome in the output state. */
class OutputPublisher(
    private val lists: BlocklistRepository,
    private val settings: SettingsRepository,
    private val passwords: PasswordStore,
    private val writer: SmbWriter,
) {
    /** Returns the number of bytes written; throws [SmbWriteException] after recording the error. */
    suspend fun publish(): Int {
        val s = settings.current()
        val content = lists.renderOutput().toByteArray(Charsets.US_ASCII)
        try {
            if (s.smbPath.isBlank()) throw SmbWriteException("Pfad zur FRITZ!Box-Freigabe in den Einstellungen eintragen.")
            writer.write(s.smbPath, SmbCredentials(s.smbUser, passwords.read().orEmpty()), content)
        } catch (e: SmbWriteException) {
            lists.recordWritten(e.message)
            throw e
        }
        lists.recordWritten(null)
        return content.size
    }

    /** [password] null uses the stored password. */
    suspend fun test(path: String, user: String, password: String?) {
        writer.testConnection(path, SmbCredentials(user.trim(), password ?: passwords.read().orEmpty()))
    }
}
