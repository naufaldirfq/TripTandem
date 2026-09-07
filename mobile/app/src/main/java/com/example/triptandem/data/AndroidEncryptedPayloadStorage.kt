package com.triptandem.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.triptandem.shared.SecurePayloadStorage
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android encrypted payload storage backed by the AndroidKeyStore and AES-256-GCM.
 * Payload encryption keys are hardware-backed on supported devices and never leave
 * secure hardware.
 *
 * Implements corruption recovery per PRD 11: malformed or tampered entries fail closed
 * and are pruned from the store without crashing the application.
 */
class AndroidEncryptedPayloadStorage(context: Context) : SecurePayloadStorage {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override suspend fun read(key: String): String? {
        val encryptedBase64 = preferences.getString(sanitizeKey(key), null) ?: return null
        return try {
            decrypt(encryptedBase64)
        } catch (error: Throwable) {
            // Self-heal corrupted payload entry
            delete(key)
            null
        }
    }

    override suspend fun write(key: String, payload: String) {
        val encryptedBase64 = encrypt(payload)
        preferences.edit().putString(sanitizeKey(key), encryptedBase64).apply()
    }

    override suspend fun delete(key: String) {
        preferences.edit().remove(sanitizeKey(key)).apply()
    }

    override suspend fun listKeys(): List<String> {
        return preferences.all.keys.toList()
    }

    override suspend fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER,
            )
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(parameterSpec)
            keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            ?: error("KeyStore entry missing or not SecretKeyEntry")
        return entry.secretKey
    }

    private fun encrypt(plaintext: String): String {
        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv ?: error("Cipher initialization produced null IV")
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Combine IV (typically 12 bytes) and ciphertext
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encryptedBase64: String): String {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        if (combined.size < GCM_IV_LENGTH_BYTES + 1) {
            throw IllegalArgumentException("Encrypted payload too short")
        }

        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val ciphertext = ByteArray(combined.size - GCM_IV_LENGTH_BYTES)
        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES)
        System.arraycopy(combined, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertext.size)

        val key = getOrCreateSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }

    private fun sanitizeKey(key: String): String = key.replace("/", "_")

    private companion object {
        const val PREFERENCES_NAME = "triptandem_encrypted_payloads"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "triptandem_cache_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
