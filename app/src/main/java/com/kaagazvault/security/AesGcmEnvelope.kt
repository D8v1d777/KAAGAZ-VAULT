package com.kaagazvault.security

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Versioned authenticated-encryption envelope for one bounded document payload.
 *
 * Format: 4-byte magic, 1-byte version, 12-byte random IV, then AES-GCM ciphertext
 * including its 128-bit authentication tag. The caller-supplied context is authenticated
 * as additional data and is not stored in the envelope.
 */
internal class AesGcmEnvelope(
    private val random: SecureRandom = SecureRandom()
) {
    @Throws(GeneralSecurityException::class)
    fun encrypt(plaintext: ByteArray, key: SecretKey, context: ByteArray): ByteArray {
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) { "Document payload exceeds the supported size limit" }
        val iv = ByteArray(IV_LENGTH).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(associatedData(context))
        val ciphertext = cipher.doFinal(plaintext)

        return ByteBuffer.allocate(HEADER_LENGTH + ciphertext.size)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MAGIC)
            .put(FORMAT_VERSION)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun decrypt(envelope: ByteArray, key: SecretKey, context: ByteArray): ByteArray {
        if (envelope.size < HEADER_LENGTH + TAG_LENGTH_BYTES) {
            throw IOException("Encrypted document is truncated")
        }
        if (envelope.size > MAX_ENVELOPE_BYTES) {
            throw IOException("Encrypted document exceeds the supported size limit")
        }

        val buffer = ByteBuffer.wrap(envelope).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(MAGIC_LENGTH).also(buffer::get)
        if (!magic.contentEquals(MAGIC)) throw IOException("Unrecognized encrypted document format")
        val version = buffer.get()
        if (version != FORMAT_VERSION) throw IOException("Unsupported encrypted document version")

        val iv = ByteArray(IV_LENGTH).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        return try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                updateAAD(associatedData(context))
                doFinal(ciphertext)
            }
        } catch (error: GeneralSecurityException) {
            // Do not return unauthenticated plaintext or include document content in errors.
            throw EncryptedDocumentAuthenticationException(error)
        }
    }

    private fun associatedData(context: ByteArray): ByteArray =
        ByteBuffer.allocate(MAGIC.size + 1 + context.size)
            .put(MAGIC)
            .put(FORMAT_VERSION)
            .put(context)
            .array()

    companion object {
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val MAGIC_LENGTH = 4
        private const val IV_LENGTH = 12
        private const val TAG_BITS = 128
        private const val TAG_LENGTH_BYTES = TAG_BITS / 8
        private const val HEADER_LENGTH = MAGIC_LENGTH + 1 + IV_LENGTH
        private const val MAX_PLAINTEXT_BYTES = 32 * 1024 * 1024
        private const val MAX_ENVELOPE_BYTES = MAX_PLAINTEXT_BYTES + HEADER_LENGTH + TAG_LENGTH_BYTES
        private val MAGIC = byteArrayOf(0x4b, 0x47, 0x56, 0x46) // "KGVF"
        private const val FORMAT_VERSION: Byte = 1
    }
}

internal class EncryptedDocumentAuthenticationException(
    cause: Throwable
) : IOException("Encrypted document authentication failed", cause)
