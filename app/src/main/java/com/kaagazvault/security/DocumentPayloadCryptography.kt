package com.kaagazvault.security

import java.io.IOException
import java.security.GeneralSecurityException
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Cryptographic operations available during one protected document-store operation.
 * Implementations must not let the callback or its key object escape beyond the call.
 */
internal interface DocumentPayloadCipher {
    @Throws(GeneralSecurityException::class)
    fun encrypt(plaintext: ByteArray, context: ByteArray): ByteArray

    @Throws(IOException::class, GeneralSecurityException::class)
    fun decrypt(envelopeBytes: ByteArray, context: ByteArray): ByteArray
}

/**
 * Holds an access boundary around the complete store operation, not just Cipher.doFinal().
 * This lets lock wait for file publication/deletion as well as cryptography.
 */
internal interface DocumentPayloadCryptography {
    fun <T> withCipher(operation: (DocumentPayloadCipher) -> T): T
}

internal class LegacyDocumentPayloadCryptography(
    private val keyProvider: DocumentKeyProvider,
    private val envelope: AesGcmEnvelope = AesGcmEnvelope()
) : DocumentPayloadCryptography {
    override fun <T> withCipher(operation: (DocumentPayloadCipher) -> T): T {
        val key: SecretKey = keyProvider.getOrCreateKey()
        val cipher = object : DocumentPayloadCipher {
            override fun encrypt(plaintext: ByteArray, context: ByteArray): ByteArray =
                envelope.encrypt(plaintext, key, context)

            override fun decrypt(envelopeBytes: ByteArray, context: ByteArray): ByteArray =
                envelope.decrypt(envelopeBytes, key, context)
        }
        return operation(cipher)
    }
}

/**
 * Uses the unlocked VEK only within VaultSession.withKey. The session's read lock remains held
 * for the *entire* operation callback, including file writes/renames/deletion. After lock returns,
 * every new call fails with VaultLockedException.
 */
internal class SessionDocumentPayloadCryptography(
    private val session: VaultSession,
    private val envelope: AesGcmEnvelope = AesGcmEnvelope()
) : DocumentPayloadCryptography {
    override fun <T> withCipher(operation: (DocumentPayloadCipher) -> T): T =
        session.withKey { vekBytes ->
            val key = SecretKeySpec(vekBytes, "AES")
            val cipher = object : DocumentPayloadCipher {
                override fun encrypt(plaintext: ByteArray, context: ByteArray): ByteArray =
                    envelope.encrypt(plaintext, key, context)

                override fun decrypt(envelopeBytes: ByteArray, context: ByteArray): ByteArray =
                    envelope.decrypt(envelopeBytes, key, context)
            }
            operation(cipher)
        }
}

/** Adapts the active in-memory VEK session to migration/database key operations. */
internal class SessionMigrationVekKeyAccess(private val session: VaultSession) : MigrationVekKeyAccess {
    override fun <T> withKey(block: (SecretKey) -> T): T =
        session.withKey { vekBytes -> block(SecretKeySpec(vekBytes, "AES")) }
}
