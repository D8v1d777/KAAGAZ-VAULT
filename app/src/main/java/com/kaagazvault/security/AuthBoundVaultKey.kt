package com.kaagazvault.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
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
    fun hasWrappingKeyAlias(): Boolean = keyStoreProvider().containsAlias(alias)

    /** Detect orphaned auth-bound aliases so a lost wrapper record never triggers silent setup. */
    fun hasAnyWrappingKeyAlias(): Boolean {
        val aliases = keyStoreProvider().aliases()
        while (aliases.hasMoreElements()) {
            val candidate = aliases.nextElement()
            if (candidate == DEFAULT_ALIAS || candidate.startsWith(DEFAULT_ALIAS_PREFIX)) return true
        }
        return false
    }

    /** Match the key's per-use authorization policy to the prompt. API 28-29 use biometric-only. */
    fun promptAuthenticators(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        }

    @Throws(GeneralSecurityException::class, IOException::class)
    fun prepareSetupCipher(): Cipher {
        val keyStore = keyStoreProvider()
        if (keyStore.containsAlias(alias)) {
            throw GeneralSecurityException("Vault wrapping key already exists; setup must not replace it")
        }
        val key = generateWrappingKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /**
     * Call only with the same Cipher instance supplied to CryptoObject and after prompt success.
     * Wrapped bytes must be durably persisted before this returns the VEK.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun finishSetup(
        authenticatedCipher: Cipher,
        persistWrappedKey: (ByteArray) -> Unit
    ): ByteArray {
        val vek = ByteArray(VEK_BYTES).also(random::nextBytes)
        try {
            finishSetupWithKey(authenticatedCipher, vek, persistWrappedKey)
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

    /**
     * Recovery-only re-wrap of the existing VEK under a fresh auth-bound alias. The caller must
     * persist a new alias+envelope record atomically; this method never generates a replacement VEK.
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun finishSetupWithKey(
        authenticatedCipher: Cipher,
        vek: ByteArray,
        persistWrappedKey: (ByteArray) -> Unit
    ) {
        require(authenticatedCipher.algorithm == TRANSFORMATION) { "Unexpected setup cipher" }
        require(vek.size == VEK_BYTES) { "Vault encryption key must be 256 bits" }
        val iv = authenticatedCipher.iv
        if (iv == null || iv.size != IV_BYTES) throw GeneralSecurityException("Setup cipher has an invalid IV")
        val ciphertext = authenticatedCipher.doFinal(vek)
        val wrapped = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, wrapped, 0, iv.size)
        System.arraycopy(ciphertext, 0, wrapped, iv.size, ciphertext.size)
        try {
            persistWrappedKey(wrapped)
        } finally {
            ciphertext.fill(0)
            wrapped.fill(0)
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
        val iv = wrappedKey.copyOfRange(0, IV_BYTES)
        return try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
        } finally {
            iv.fill(0)
        }
    }

    @Throws(GeneralSecurityException::class, IOException::class)
    fun finishUnlock(authenticatedCipher: Cipher, wrappedKey: ByteArray): ByteArray {
        require(authenticatedCipher.algorithm == TRANSFORMATION) { "Unexpected unlock cipher" }
        if (wrappedKey.size !in MIN_WRAPPED_KEY_BYTES..MAX_WRAPPED_KEY_BYTES) {
            throw IOException("Wrapped vault key has an invalid size")
        }
        val ciphertext = wrappedKey.copyOfRange(IV_BYTES, wrappedKey.size)
        val vek = try {
            authenticatedCipher.doFinal(ciphertext)
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
     */
    @Throws(GeneralSecurityException::class, IOException::class)
    fun discardAbandonedSetup() {
        val keyStore = keyStoreProvider()
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
            if (keyStore.containsAlias(alias)) {
                throw IOException("Could not discard abandoned vault setup key")
            }
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
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else {
                    setInvalidatedByBiometricEnrollment(true)
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
            }
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        fun loadAndroidKeyStore(): KeyStore =
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        const val DEFAULT_ALIAS = "kaagaz_vault_auth_bound_vek_wrap_v1"
        const val DEFAULT_ALIAS_PREFIX = "kaagaz_vault_auth_bound_vek_wrap_v1_"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val VEK_BYTES = 32
        const val IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val MIN_WRAPPED_KEY_BYTES = IV_BYTES + 16
        const val MAX_WRAPPED_KEY_BYTES = IV_BYTES + VEK_BYTES + 16

        fun newAlias(): String = DEFAULT_ALIAS_PREFIX + UUID.randomUUID().toString().replace("-", "")
    }

    internal fun newAliasForRecoveryOrSetup(): String = newAlias()
}
