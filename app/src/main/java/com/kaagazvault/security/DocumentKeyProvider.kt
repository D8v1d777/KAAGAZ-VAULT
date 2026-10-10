package com.kaagazvault.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
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

internal class AndroidKeystoreDocumentKeyProvider(
    private val alias: String = DEFAULT_ALIAS
) : DocumentKeyProvider {

    override fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(alias, null)
        if (existing != null) {
            return existing as? SecretKey
                ?: throw IllegalStateException("Stored document key has an unexpected type")
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_SIZE_BITS = 256
        const val DEFAULT_ALIAS = "kaagaz.vault.document-payload.v1"
    }
}
