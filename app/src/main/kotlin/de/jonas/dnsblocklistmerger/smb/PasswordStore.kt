package de.jonas.dnsblocklistmerger.smb

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-GCM with a key from [keyProvider]; output is IV (12 bytes) followed by ciphertext and tag. */
class AesGcmCipher(private val keyProvider: () -> SecretKey) {
    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        val iv = cipher.iv
        require(iv.size == IV_BYTES)
        return iv + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray): ByteArray {
        require(data.size > IV_BYTES) { "ciphertext too short" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(128, data, 0, IV_BYTES))
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val KEY_ALIAS = "smb_password_key"

        /** Key that never leaves the Android Keystore; created on first use. */
        fun androidKeystoreKey(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return gen.generateKey()
        }
    }
}

/** Stores the SMB password only encrypted. Never log the value returned by [read]. */
class PasswordStore(private val store: DataStore<Preferences>, private val cipher: AesGcmCipher) {
    private val key = stringPreferencesKey("smb_password_encrypted")

    val isSet = store.data.map { it[key] != null }

    suspend fun save(password: String) {
        if (password.isEmpty()) {
            clear()
            return
        }
        val encrypted = Base64.getEncoder().encodeToString(cipher.encrypt(password.toByteArray(Charsets.UTF_8)))
        store.edit { it[key] = encrypted }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }

    /** Null when no password is stored or it cannot be decrypted any more (e.g. Keystore key lost). */
    suspend fun read(): String? {
        val encoded = store.data.first()[key] ?: return null
        return runCatching { String(cipher.decrypt(Base64.getDecoder().decode(encoded)), Charsets.UTF_8) }.getOrNull()
    }
}
