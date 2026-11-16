package de.jonas.dnsblocklistmerger.smb

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2MessageCommandCode
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.common.SMBRuntimeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException
import javax.crypto.KeyGenerator

class SmbTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun api(status: NtStatus) = SMBApiException(status.value, SMB2MessageCommandCode.SMB2_CREATE, null)

    @Test
    fun parsesFritzBoxPath() {
        val t = SmbTarget.parse(" smb://fritz.box/FRITZ.NAS/dnsfilter/blocklist.txt ")
        assertEquals(SmbTarget("fritz.box", "FRITZ.NAS", "dnsfilter", "blocklist.txt"), t)
        assertEquals("dnsfilter\\blocklist.txt", t.file)
        assertEquals("dnsfilter\\blocklist.txt.tmp", t.sibling("blocklist.txt.tmp"))
        assertEquals("a\\b\\c.txt", SmbTarget.parse("smb://h/s/a/b/c.txt").file)
        assertEquals("c.txt", SmbTarget.parse("SMB://h/s/c.txt").file)
    }

    @Test
    fun rejectsUnusablePaths() {
        for (bad in listOf("", "fritz.box/FRITZ.NAS/x.txt", "smb://fritz.box/FRITZ.NAS", "smb://fritz.box", "smb://h/s/../x.txt")) {
            assertTrue(bad, runCatching { SmbTarget.parse(bad) }.isFailure)
        }
    }

    @Test
    fun cipherRoundTripUsesFreshIv() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = AesGcmCipher { key }
        val plain = "geheim-ä".toByteArray()
        val a = cipher.encrypt(plain)
        val b = cipher.encrypt(plain)
        assertFalse(a.contentEquals(b))
        assertArrayEquals(plain, cipher.decrypt(a))
        a[a.size - 1] = (a[a.size - 1] + 1).toByte()
        assertTrue(runCatching { cipher.decrypt(a) }.isFailure)
    }

    @Test
    fun passwordStoreKeepsNoPlaintext() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(tmp.root, "p.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val passwords = PasswordStore(store, AesGcmCipher { key })
        try {
            runBlocking {
                assertNull(passwords.read())
                passwords.save("Fritz!Pass123")
                assertEquals("Fritz!Pass123", passwords.read())
                assertTrue(passwords.isSet.first())
                val raw = store.data.first()[stringPreferencesKey("smb_password_encrypted")]!!
                assertFalse(raw.contains("Fritz"))
                assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("Fritz!Pass123"))

                // A different key (e.g. Keystore wiped) reads as "no password", not as a crash.
                val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
                assertNull(PasswordStore(store, AesGcmCipher { other }).read())

                passwords.save("")
                assertFalse(passwords.isSet.first())
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun errorsMapToGermanMessages() {
        assertEquals("Anmeldung fehlgeschlagen: Benutzer oder Passwort falsch", SmbWriter.messageFor(api(NtStatus.STATUS_LOGON_FAILURE)))
        assertEquals("Kein Schreibrecht im Verzeichnis", SmbWriter.messageFor(api(NtStatus.STATUS_ACCESS_DENIED)))
        assertEquals("Freigabe nicht gefunden", SmbWriter.messageFor(api(NtStatus.STATUS_BAD_NETWORK_NAME)))
        assertEquals("Verzeichnis nicht gefunden", SmbWriter.messageFor(api(NtStatus.STATUS_OBJECT_PATH_NOT_FOUND)))
        assertEquals("FRITZ!Box nicht erreichbar: Name nicht gefunden", SmbWriter.messageFor(UnknownHostException("fritz.box")))
        assertEquals("FRITZ!Box nicht erreichbar", SmbWriter.messageFor(IOException("wrapped", ConnectException("refused"))))
        assertEquals("Verbindungsfehler: kaputt", SmbWriter.messageFor(IOException("wrapper", IOException("kaputt"))))
    }

    /** The chain smbj threw on a tester's FRITZ!Box (#20). */
    private fun connectionReset(): Exception = ExecutionException(
        SMBRuntimeException(TransportException(SocketException("Connection reset"))),
    )

    @Test
    fun droppedConnectionGetsAReadableMessage() {
        val message = SmbWriter.messageFor(connectionReset())
        assertTrue(message, message.startsWith(SmbWriter.CONNECTION_DROPPED))
        assertTrue(message, message.endsWith("(Connection reset)"))
        assertFalse(message, message.contains("java."))
    }

    @Test
    fun onlyDroppedConnectionsAreRetried() {
        assertTrue(SmbWriter.isDroppedConnection(connectionReset()))
        assertTrue(SmbWriter.isDroppedConnection(TransportException(EOFException("closed"))))
        assertFalse(SmbWriter.isDroppedConnection(api(NtStatus.STATUS_LOGON_FAILURE)))
        assertFalse(SmbWriter.isDroppedConnection(IOException("x", ConnectException("refused"))))
        assertFalse(SmbWriter.isDroppedConnection(UnknownHostException("fritz.box")))
        assertFalse(SmbWriter.isDroppedConnection(SmbWriteException("Verzeichnis nicht gefunden")))
        assertFalse(SmbWriter.isDroppedConnection(IOException("other")))
    }

    @Test
    fun fallbackTriesTheNextDialectSetOnlyAfterADroppedConnection() {
        val tried = mutableListOf<String>()
        val result = SmbWriter.withFallback(listOf("3.1.1", "3.0", "2.x")) {
            tried += it
            if (it == "3.1.1") throw connectionReset()
            "ok with $it"
        }
        assertEquals("ok with 3.0", result)
        assertEquals(listOf("3.1.1", "3.0"), tried)

        tried.clear()
        val login = runCatching { SmbWriter.withFallback(listOf("a", "b")) { tried += it; throw api(NtStatus.STATUS_LOGON_FAILURE) } }
        assertTrue(login.exceptionOrNull() is SMBApiException)
        assertEquals(listOf("a"), tried)

        tried.clear()
        val all = runCatching { SmbWriter.withFallback(listOf("a", "b")) { tried += it; throw connectionReset() } }
        assertTrue(SmbWriter.isDroppedConnection(all.exceptionOrNull()!!))
        assertEquals(listOf("a", "b"), tried)
    }

    @Test
    fun negotiationFallbacksEndWithoutSmb311() {
        val configs = negotiationFallbacks()
        assertEquals(3, configs.size)
        assertTrue(SMB2Dialect.SMB_3_1_1 in configs[0].supportedDialects)
        assertFalse(SMB2Dialect.SMB_3_1_1 in configs[1].supportedDialects)
        assertEquals(setOf(SMB2Dialect.SMB_2_1, SMB2Dialect.SMB_2_0_2), configs[2].supportedDialects.toSet())
        assertTrue(configs[2].isUseMultiProtocolNegotiate)
    }

    private class FakeFiles(val failWith: Exception? = null) : SmbFiles {
        val writes = mutableListOf<Triple<SmbTarget, String, ByteArray>>()
        var lastCredentials: SmbCredentials? = null
        override fun replaceFile(target: SmbTarget, credentials: SmbCredentials, tempName: String, content: ByteArray) {
            failWith?.let { throw it }
            lastCredentials = credentials
            writes += Triple(target, tempName, content)
        }
        override fun probe(target: SmbTarget, credentials: SmbCredentials, probeName: String) {
            failWith?.let { throw it }
        }
    }

    private val creds = SmbCredentials("jonas", "pw")
    private val path = "smb://fritz.box/FRITZ.NAS/dnsfilter/blocklist.txt"

    @Test
    fun writerWritesViaTempFileName() = runBlocking {
        val files = FakeFiles()
        SmbWriter(files).write(path, creds, "||a.example^\n".toByteArray())
        val (target, temp, content) = files.writes.single()
        assertEquals("blocklist.txt", target.fileName)
        assertEquals("blocklist.txt.tmp", temp)
        assertEquals("||a.example^\n", String(content))
    }

    @Test
    fun writerTakesFilesOver4MbButRefusesMissingCredentials() = runBlocking {
        val files = FakeFiles()
        val writer = SmbWriter(files)
        // The size limit is the user's setting (Selection), not the writer's.
        writer.write(path, creds, ByteArray(5 * 1024 * 1024))
        val noPassword = runCatching { writer.write(path, SmbCredentials("jonas", ""), ByteArray(1)) }.exceptionOrNull()
        assertTrue(noPassword is SmbWriteException)
        assertEquals(1, files.writes.size)
    }

    @Test
    fun writerTranslatesSmbFailures() = runBlocking {
        val writer = SmbWriter(FakeFiles(failWith = api(NtStatus.STATUS_LOGON_FAILURE)))
        val e = runCatching { writer.testConnection(path, creds) }.exceptionOrNull()
        assertTrue(e is SmbWriteException)
        assertEquals("Anmeldung fehlgeschlagen: Benutzer oder Passwort falsch", e!!.message)
    }
}
