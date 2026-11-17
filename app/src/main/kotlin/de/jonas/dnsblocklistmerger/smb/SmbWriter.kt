package de.jonas.dnsblocklistmerger.smb

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2Dialect
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
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
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
                    is SocketException, is EOFException -> return CONNECTION_DROPPED + " (${rootMessage(e)})"
                }
                e = e.cause
            }
            val prefix = if (error is IOException || error.cause is IOException) "Verbindungsfehler" else "Fehler"
            return "$prefix: ${rootMessage(error)}"
        }

        const val CONNECTION_DROPPED = "Die FRITZ!Box hat die Verbindung abgebrochen. In der FRITZ!Box prüfen: " +
            "Heimnetz > USB / Speicher > Heimnetzfreigabe: Freigabe als Netzlaufwerk (SMB) eingeschaltet; " +
            "System > FRITZ!Box-Benutzer: Benutzer hat Zugang zu NAS-Inhalten mit Schreibrecht. " +
            "Nötig ist FRITZ!OS 7 oder neuer (SMB2/3)"

        /** Message of the innermost cause, so wrapper exceptions do not end up in the UI. */
        private fun rootMessage(error: Throwable): String {
            var e = error
            while (e.cause != null && e.cause !== e) e = e.cause!!
            return e.message ?: e.javaClass.simpleName
        }

        /**
         * True when the server dropped the connection, e.g. a reset during the SMB negotiation.
         * Then another dialect set may work; login, path or network errors are final.
         */
        fun isDroppedConnection(error: Throwable): Boolean {
            var e: Throwable? = error
            var dropped = false
            while (e != null) {
                when (e) {
                    is SMBApiException, is SmbWriteException, is UnknownHostException, is ConnectException,
                    is NoRouteToHostException, is SocketTimeoutException,
                    -> return false
                    is SocketException, is EOFException -> dropped = true
                }
                e = e.cause
            }
            return dropped
        }

        /** Runs [block] with each option in turn while the connection gets dropped; other errors stop at once. */
        fun <O, T> withFallback(options: List<O>, block: (O) -> T): T {
            require(options.isNotEmpty())
            var last: Exception? = null
            for (option in options) {
                try {
                    return block(option)
                } catch (e: Exception) {
                    if (!isDroppedConnection(e)) throw e
                    last = e
                }
            }
            throw last!!
        }
    }
}

/**
 * smbj implementation; SMB2/3 only, as smbj does not speak SMB1.
 * Some SMB servers drop the connection when offered SMB 3.1.1 (#20), so a dropped
 * connection is retried with older dialect sets.
 */
class SmbjFiles(private val configs: List<SmbConfig> = negotiationFallbacks()) : SmbFiles {

    /** A path that ends on an existing folder means "write blocklist.txt into it" (#27). */
    private fun resolved(share: DiskShare, target: SmbTarget) =
        if (share.folderExists(target.file)) target.intoFolder() else target

    private fun <T> withShare(target: SmbTarget, credentials: SmbCredentials, block: (DiskShare) -> T): T =
        SmbWriter.withFallback(configs) { config -> withShare(config, target, credentials, block) }

    private fun <T> withShare(config: SmbConfig, target: SmbTarget, credentials: SmbCredentials, block: (DiskShare) -> T): T =
        SMBClient(config).use { client ->
            client.connect(target.host).use { connection ->
                val auth = AuthenticationContext(credentials.user, credentials.password.toCharArray(), null)
                connection.authenticate(auth).use { session ->
                    (session.connectShare(target.share) as DiskShare).use(block)
                }
            }
        }

    override fun replaceFile(target: SmbTarget, credentials: SmbCredentials, tempName: String, content: ByteArray) =
        withShare(target, credentials) { share -> replace(share, resolved(share, target), content) }

    private fun replace(share: DiskShare, target: SmbTarget, content: ByteArray) {
        val tempName = "${target.fileName}.tmp"
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
            val dest = resolved(share, target)
            if (dest.directory.isNotEmpty() && !share.folderExists(dest.directory)) {
                throw SmbWriteException("Verzeichnis nicht gefunden: ${dest.directory.replace('\\', '/')}")
            }
            val probe = dest.sibling(probeName)
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

/** smbj default (SMB 3.1.1 down to 2.0.2), then without 3.1.1, then SMB 2.x with an SMB1-style negotiate. */
fun negotiationFallbacks(): List<SmbConfig> {
    fun base() = SmbConfig.builder()
        .withSecurityProvider(BCSecurityProvider())
        .withTimeout(20, TimeUnit.SECONDS)
        .withSoTimeout(20, TimeUnit.SECONDS)
    return listOf(
        base().build(),
        base().withDialects(SMB2Dialect.SMB_3_0_2, SMB2Dialect.SMB_3_0, SMB2Dialect.SMB_2_1, SMB2Dialect.SMB_2_0_2).build(),
        base().withDialects(SMB2Dialect.SMB_2_1, SMB2Dialect.SMB_2_0_2).withMultiProtocolNegotiate(true).build(),
    )
}
