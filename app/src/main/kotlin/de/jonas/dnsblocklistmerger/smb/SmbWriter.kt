package de.jonas.dnsblocklistmerger.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.io.ArrayByteChunkProvider
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** Thrown for every failure; [message] is ready to show to the user. */
class SmbWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class SmbCredentials(val user: String, val password: String)

/** What the writer needs from SMB; the real one talks to the FRITZ!Box, tests use a fake. */
interface SmbFiles {
    /** Writes [content] to [tempName] in the target directory, then renames it onto the target file. */
    fun replaceFile(target: SmbTarget, credentials: SmbCredentials, tempName: String, content: ByteArray)

    /** Logs in, opens the share and creates and deletes a small file in the target directory. */
    fun probe(target: SmbTarget, credentials: SmbCredentials, probeName: String)
}

class SmbWriter(private val files: SmbFiles = SmbjFiles()) {

    suspend fun write(path: String, credentials: SmbCredentials, content: ByteArray) = withContext(Dispatchers.IO) {
        val target = parse(path)
        checkCredentials(credentials)
        translating { files.replaceFile(target, credentials, "${target.fileName}.tmp", content) }
    }

    suspend fun testConnection(path: String, credentials: SmbCredentials) = withContext(Dispatchers.IO) {
        val target = parse(path)
        checkCredentials(credentials)
        translating { files.probe(target, credentials, ".dns_blocklist_merger_test") }
    }

    private fun parse(path: String) = try {
        SmbTarget.parse(path)
    } catch (e: IllegalArgumentException) {
        throw SmbWriteException(e.message ?: "Ungültiger Pfad", e)
    }

    private fun checkCredentials(c: SmbCredentials) {
        if (c.user.isBlank() || c.password.isEmpty()) throw SmbWriteException("Benutzer und Passwort in den Einstellungen eintragen.")
    }

    private inline fun translating(block: () -> Unit) {
        try {
            block()
        } catch (e: SmbWriteException) {
            throw e
        } catch (e: Exception) {
            throw SmbWriteException(messageFor(e), e)
        }
    }

    companion object {

        /** Maps SMB and network errors to a German message; looks through the cause chain. */
        fun messageFor(error: Throwable): String {
            var e: Throwable? = error
            while (e != null) {
                when (e) {
                    is SMBApiException -> return when (e.status) {
                        NtStatus.STATUS_LOGON_FAILURE, NtStatus.STATUS_ACCOUNT_DISABLED, NtStatus.STATUS_PASSWORD_EXPIRED,
                        -> "Anmeldung fehlgeschlagen: Benutzer oder Passwort falsch"
                        NtStatus.STATUS_ACCESS_DENIED -> "Kein Schreibrecht im Verzeichnis"
                        NtStatus.STATUS_BAD_NETWORK_NAME -> "Freigabe nicht gefunden"
                        NtStatus.STATUS_OBJECT_PATH_NOT_FOUND, NtStatus.STATUS_OBJECT_NAME_NOT_FOUND -> "Verzeichnis nicht gefunden"
                        NtStatus.STATUS_DISK_FULL -> "Speicher der FRITZ!Box ist voll"
                        NtStatus.STATUS_SHARING_VIOLATION -> "Datei ist gerade gesperrt, später erneut versuchen"
                        else -> "SMB-Fehler: ${e.status}"
                    }
                    is UnknownHostException -> return "FRITZ!Box nicht erreichbar: Name nicht gefunden"
                    is ConnectException, is NoRouteToHostException, is SocketTimeoutException ->
                        return "FRITZ!Box nicht erreichbar"
                }
                e = e.cause
            }
            return if (error is IOException) "Verbindungsfehler: ${error.message ?: error.javaClass.simpleName}"
            else "Fehler: ${error.message ?: error.javaClass.simpleName}"
        }
    }
}

/** smbj implementation; SMB2/3 only, as smbj does not speak SMB1. */
class SmbjFiles : SmbFiles {
    private fun client() = SMBClient(
        SmbConfig.builder()
            .withSecurityProvider(BCSecurityProvider())
            .withTimeout(20, TimeUnit.SECONDS)
            .withSoTimeout(20, TimeUnit.SECONDS)
            .build(),
    )

    private fun <T> withShare(target: SmbTarget, credentials: SmbCredentials, block: (DiskShare) -> T): T =
        client().use { client ->
            client.connect(target.host).use { connection ->
                val auth = AuthenticationContext(credentials.user, credentials.password.toCharArray(), null)
                connection.authenticate(auth).use { session ->
                    (session.connectShare(target.share) as DiskShare).use(block)
                }
            }
        }

    override fun replaceFile(target: SmbTarget, credentials: SmbCredentials, tempName: String, content: ByteArray) =
        withShare(target, credentials) { share ->
            if (target.directory.isNotEmpty() && !share.folderExists(target.directory)) {
                throw SmbWriteException("Verzeichnis nicht gefunden: ${target.directory.replace('\\', '/')}")
            }
            val temp = target.sibling(tempName)
            share.openFile(
                temp,
                setOf(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null,
            ).use { file ->
                file.write(ArrayByteChunkProvider(content, 0))
                file.flush()
                // Only the finished file gets the target name, so the FRITZ!Box never reads half a file.
                file.rename(target.file, true)
            }
        }

    override fun probe(target: SmbTarget, credentials: SmbCredentials, probeName: String) =
        withShare(target, credentials) { share ->
            if (target.directory.isNotEmpty() && !share.folderExists(target.directory)) {
                throw SmbWriteException("Verzeichnis nicht gefunden: ${target.directory.replace('\\', '/')}")
            }
            val probe = target.sibling(probeName)
            share.openFile(
                probe,
                setOf(AccessMask.GENERIC_WRITE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                null,
            ).use { it.write("test".toByteArray(), 0) }
            share.rm(probe)
        }
}
