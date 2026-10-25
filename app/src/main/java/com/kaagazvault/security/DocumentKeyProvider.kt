package com.kaagazvault.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.kaagazvault.security.hardening.StrongBoxEnforcer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Supplies the non-exportable key used to encrypt document payloads at rest.
 *
 * This key is not an app-lock mechanism: authentication gating is a separate feature.
 * If the key becomes unavailable or invalidated, callers must fail closed rather than
 * silently generate a replacement that cannot decrypt existing files.
 */
internal fun interface DocumentKeyProvider {
    fun getOrCreateKey(): SecretKey
}

/** Raised when encrypted data exists but its Keystore key is missing. */
internal class MissingEncryptionKeyException(
    message: String = "Encryption key is missing; existing encrypted data was preserved"
) : GeneralSecurityException(message)

internal fun ensureNewKeyAllowed(hasExistingProtectedData: Boolean) {
    if (hasExistingProtectedData) throw MissingEncryptionKeyException()
}

internal class AndroidKeystoreDocumentKeyProvider(
    private val alias: String = DEFAULT_ALIAS,
    private val hasExistingProtectedData: () -> Boolean = { false }
) : DocumentKeyProvider {

    override fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(alias, null)
        if (existing != null) {
            // Verify existing key meets hardware-backed requirements
            if (!verifyKeyHardwareBacked(keyStore, alias)) {
                throw GeneralSecurityException("Existing document key does not meet hardware-backed requirements")
            }
            return existing as? SecretKey
                ?: throw IllegalStateException("Stored document key has an unexpected type")
        }

        // Never create a replacement key over ciphertext encrypted with a missing key.
        // The caller supplies a conservative check for data protected by this alias.
        ensureNewKeyAllowed(hasExistingProtectedData())

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        // Build spec with hardware enforcement (StrongBox/TEE)
        val specBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
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

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 256
        const val DEFAULT_ALIAS = "kaagaz.vault.document-payload.v1"
    }
}
