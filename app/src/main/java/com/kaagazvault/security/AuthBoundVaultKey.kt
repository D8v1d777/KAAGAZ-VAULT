package com.kaagazvault.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Auth-per-use Android Keystore wrapping primitive for a random 256-bit Vault Encryption Key.
 *
 * The returned Cipher must be passed to BiometricPrompt.CryptoObject. Android Keystore enforces
 * authentication at doFinal(); this class does not implement UI, session lifetime, persistence
 * policy, migration, or repository gating. Callers must persist wrapped bytes before exposing the
 * new VEK to the rest of the application.
 *
 * Never silently regenerate this alias when wrapped vault-key material or protected vault data
 * already exists. Alias loss is a recovery condition, not a request to create a replacement key.
 */
internal class AuthBoundVaultKey(
    private val alias: String = DEFAULT_ALIAS,
    private val keyStoreProvider: () -> KeyStore = { loadAndroidKeyStore() },
    private val random: SecureRandom = SecureRandom()
) {
    @Throws(GeneralSecurityException::class, IOException::class)
    fun prepareSetupCipher(): Cipher {
        val keyStore = keyStoreProvider()
        if (keyStore.containsAlias(alias)) {
            throw GeneralSecurityException("Vault wrapping key already exists; setup must not replace it")
        }
        val key = generateWrappingKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    /**
     * Call only with the same Cipher instance supplied to CryptoObject and after prompt success.
     * The callback must durably persist wrapped bytes (without logging them) before this returns
     * the VEK. If persistence fails, the in-memory VEK is zeroed and the error is propagated.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun finishSetup(
        authenticatedCipher: Cipher,
        persistWrappedKey: (ByteArray) -> Unit
    ): ByteArray {
        require(authenticatedCipher.opmode == Cipher.ENCRYPT_MODE) { "Setup cipher is not in encrypt mode" }
        val vek = ByteArray(VEK_BYTES).also(random::nextBytes)
        try {
            val wrapped = authenticatedCipher.doFinal(vek)
            try {
                persistWrappedKey(wrapped)
            } finally {
                wrapped.fill(0)
            }
            return vek
        } catch (error: Exception) {
            vek.fill(0)
            when (error) {
                is GeneralSecurityException -> throw error
                is IOException -> throw error
                else -> throw IOException("Could not persist wrapped vault key", error)
            }
        }
    }

    /** Initialize this cipher before constructing BiometricPrompt.CryptoObject(cipher). */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun prepareUnlockCipher(wrappedKey: ByteArray): Cipher {
        if (wrappedKey.size !in MIN_WRAPPED_KEY_BYTES..MAX_WRAPPED_KEY_BYTES) {
            throw IOException("Wrapped vault key has an invalid size")
        }
        val keyStore = keyStoreProvider()
        if (!keyStore.containsAlias(alias)) {
            throw MissingEncryptionKeyException("Vault wrapping key is missing; recovery is required")
        }
        val key = keyStore.getKey(alias, null) as? SecretKey
            ?: throw MissingEncryptionKeyException("Vault wrapping key is unavailable; recovery is required")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, wrappedKey.copyOfRange(0, IV_BYTES)))
        return cipher
    }

    /**
     * Call with the Cipher returned by prepareUnlockCipher, after the successful prompt result.
     * A failed tag check or an unauthenticated operation must leave the caller locked.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun finishUnlock(authenticatedCipher: Cipher, wrappedKey: ByteArray): ByteArray {
        require(authenticatedCipher.opmode == Cipher.DECRYPT_MODE) { "Unlock cipher is not in decrypt mode" }
        val ciphertext = wrappedKey.copyOfRange(IV_BYTES, wrappedKey.size)
        val vek = try {
            authenticatedCipher.doFinal(ciphertext)
        } catch (error: GeneralSecurityException) {
            throw error
        } finally {
            ciphertext.fill(0)
        }
        if (vek.size != VEK_BYTES) {
            vek.fill(0)
            throw GeneralSecurityException("Unwrapped vault key has an invalid size")
        }
        return vek
    }

    /**
     * Explicit recovery-only cleanup for an abandoned initial setup. The caller must first prove
     * no wrapped key, document ciphertext, database, WAL, journal, or other protected data exists.
     * Never call this as an automatic response to an unlock failure.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun discardAbandonedSetup() {
        val keyStore = keyStoreProvider()
        if (keyStore.containsAlias(alias) && !keyStore.deleteEntry(alias)) {
            throw IOException("Could not discard abandoned vault setup key")
        }
    }

    private fun generateWrappingKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(VEK_BYTES * 8)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun loadAndroidKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val DEFAULT_ALIAS = "kaagaz_vault_auth_bound_vek_wrap_v1"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VEK_BYTES = 32
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val MIN_WRAPPED_KEY_BYTES = IV_BYTES + 16
        const val MAX_WRAPPED_KEY_BYTES = IV_BYTES + VEK_BYTES + 16
    }
}
