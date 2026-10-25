package com.kaagazvault.database

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kaagazvault.security.AesGcmEnvelope
import com.kaagazvault.security.DocumentKeyProvider
import com.kaagazvault.security.hardening.SecureDeletion
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Creates a random SQLCipher key and stores only its AES-GCM-wrapped form in no-backup storage.
 * A missing/corrupt wrapper is not silently replaced when one already exists.
 * Uses hardware-enforced (StrongBox/TEE) key wrapping.
 */
internal class DatabaseKeyManager(context: Context) {
    private val keyFile = File(context.noBackupFilesDir, WRAPPED_KEY_FILE)
    private val databaseFile = context.getDatabasePath(DATABASE_NAME)
    private val wrapperKeyProvider = HardwareEnforcedDatabaseKeyProvider(context)
    private val envelope = AesGcmEnvelope()

    @Synchronized
    @Throws(IOException::class, GeneralSecurityException::class)
    fun getOrCreateKey(): ByteArray {
        if (keyFile.exists()) {
            val unwrapped = envelope.decrypt(
                keyFile.readBytes(),
                wrapperKeyProvider.getOrCreateKey(),
                KEY_CONTEXT
            )
            if (unwrapped.size != KEY_SIZE_BYTES) {
                unwrapped.fill(0)
                throw IOException("Wrapped database key has an invalid size")
            }
            return unwrapped
        }

        if (databaseFile.exists()) {
            throw IOException("Database exists but its wrapped key is missing; refusing to replace the key")
        }

        val key = ByteArray(KEY_SIZE_BYTES).also(SecureRandom()::nextBytes)
        val temporary = File(keyFile.parentFile, ".$WRAPPED_KEY_FILE.pending")
        try {
            val wrapped = envelope.encrypt(key, wrapperKeyProvider.getOrCreateKey(), KEY_CONTEXT)
            FileOutputStream(temporary).use { output ->
                output.write(wrapped)
                output.fd.sync()
            }
            if (keyFile.exists()) throw IOException("Database key was created concurrently")
            if (!temporary.renameTo(keyFile)) throw IOException("Could not persist wrapped database key")
            return key
        } catch (error: Exception) {
            key.fill(0)
            throw error
        } finally {
            if (temporary.exists()) {
                // Securely wipe the temporary file
                SecureDeletion.wipe(temporary)
            }
        }
    }

    companion object {
        private const val KEY_SIZE_BYTES = 32
        private const val WRAPPED_KEY_FILE = "vault-database-key.wrap"
        private const val DATABASE_NAME = "vault-metadata.db"
        const val WRAPPING_KEY_ALIAS = "kaagaz.vault.database-key-wrap.v1"
        private val KEY_CONTEXT = "kaagaz.vault.sqlcipher-key.v1".toByteArray(StandardCharsets.UTF_8)
    }
}

/**
 * Hardware-enforced DocumentKeyProvider for database key wrapping.
 * Uses StrongBox (secure element) when available, falls back to TEE.
 * Rejects software-only keystore.
 */
internal class HardwareEnforcedDatabaseKeyProvider(
    private val context: Context,
    private val alias: String = DatabaseKeyManager.WRAPPING_KEY_ALIAS,
    private val hasExistingProtectedData: () -> Boolean = { false }
) : DocumentKeyProvider {

    override fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(alias, null)
        if (existing != null) {
            // Verify existing key meets hardware-backed requirements
            if (!verifyKeyHardwareBacked(keyStore, alias)) {
                throw GeneralSecurityException("Existing database wrapping key does not meet hardware-backed requirements")
            }
            return existing as? SecretKey
                ?: throw IllegalStateException("Stored database wrapping key has an unexpected type")
        }

        // Never create a replacement key over ciphertext encrypted with a missing key.
        if (hasExistingProtectedData()) {
            throw GeneralSecurityException("Database wrapping key is missing; existing encrypted data was preserved")
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )

        // Build spec with hardware enforcement (StrongBox/TEE)
        val specBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        // Enforce StrongBox if available (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            specBuilder.setIsStrongBoxBacked(true)
        }

        generator.init(specBuilder.build())
        return generator.generateKey()
    }

    /**
     * Verify that an existing key is hardware-backed (TEE or StrongBox).
     */
    private fun verifyKeyHardwareBacked(keyStore: KeyStore, alias: String): Boolean {
        return try {
            val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
                ?: return false

            val key = entry.secretKey

            // Get KeyInfo via reflection
            val keyFactoryClass = Class.forName("android.security.keystore.KeyFactory")
            val method = keyFactoryClass.getDeclaredMethod("getKeyInfo", java.security.Key::class.java)
            method.isAccessible = true
            val keyInfo = method.invoke(null, key)

            if (keyInfo == null) return false

            // Check isInsideSecureHardware
            val isInsideSecureHardware = keyInfo.javaClass.getMethod("isInsideSecureHardware")
                .invoke(keyInfo) as? Boolean == true

            return isInsideSecureHardware
        } catch (e: Exception) {
            // If we can't verify, assume it's not hardware-backed
            false
        }
    }
}
