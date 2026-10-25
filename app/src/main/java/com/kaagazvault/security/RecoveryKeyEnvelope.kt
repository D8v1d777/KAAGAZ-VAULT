package com.kaagazvault.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Versioned AES-GCM recovery envelope for a 256-bit VEK.
 *
 * The recovery key is a separate random 256-bit secret. The caller must show its canonical
 * encoding only during setup, require the owner to confirm safe storage, and clear its mutable
 * key buffer after use. This class never persists the recovery key and has no network behavior.
 *
 * The envelope is exactly: 4-byte magic, 1-byte version, 12-byte IV, and 32-byte VEK ciphertext
 * plus a 16-byte GCM tag. Header and protocol context are authenticated as AAD.
 */
internal class RecoveryKeyEnvelope(
    private val random: SecureRandom = SecureRandom()
) {
    fun generateRecoveryKey(): ByteArray = ByteArray(KEY_BYTES).also(random::nextBytes)

    /** Canonical lowercase hexadecimal representation; 64 characters encode 256 random bits. */
    fun encodeRecoveryKey(recoveryKey: ByteArray): String {
        require(recoveryKey.size == KEY_BYTES) { "Recovery key must be 256 bits" }
        return buildString(KEY_BYTES * 2) {
            recoveryKey.forEach { byte ->
                val value = byte.toInt() and 0xff
                append(HEX[value ushr 4])
                append(HEX[value and 0x0f])
            }
        }
    }

    /** Accepts only the exact canonical encoding to avoid ambiguous transcription formats. */
    fun decodeRecoveryKey(encoded: String): ByteArray {
        require(encoded.length == KEY_BYTES * 2 && encoded.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Recovery key must be 64 lowercase hexadecimal characters"
        }
        return ByteArray(KEY_BYTES) { index ->
            val high = nibble(encoded[index * 2])
            val low = nibble(encoded[index * 2 + 1])
            ((high shl 4) or low).toByte()
        }
    }

    @Throws(GeneralSecurityException::class)
    fun wrap(vek: ByteArray, recoveryKey: ByteArray): ByteArray {
        require(vek.size == KEY_BYTES) { "Vault encryption key must be 256 bits" }
        require(recoveryKey.size == KEY_BYTES) { "Recovery key must be 256 bits" }

        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val header = HEADER.copyOf()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(recoveryKey, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(authenticatedContext(header))
        val ciphertext = cipher.doFinal(vek)

        return try {
            ByteArray(HEADER_BYTES + IV_BYTES + ciphertext.size).also { envelope ->
                System.arraycopy(header, 0, envelope, 0, HEADER_BYTES)
                System.arraycopy(iv, 0, envelope, HEADER_BYTES, IV_BYTES)
                System.arraycopy(ciphertext, 0, envelope, HEADER_BYTES + IV_BYTES, ciphertext.size)
            }
        } finally {
            ciphertext.fill(0)
        }
    }

    @Throws(GeneralSecurityException::class)
    fun unwrap(envelope: ByteArray, recoveryKey: ByteArray): ByteArray {
        require(recoveryKey.size == KEY_BYTES) { "Recovery key must be 256 bits" }
        require(envelope.size == ENVELOPE_BYTES) { "Recovery envelope has an invalid size" }
        require(envelope.copyOfRange(0, HEADER_BYTES).contentEquals(HEADER)) {
            "Recovery envelope has an unsupported format"
        }

        val header = envelope.copyOfRange(0, HEADER_BYTES)
        val iv = envelope.copyOfRange(HEADER_BYTES, HEADER_BYTES + IV_BYTES)
        val ciphertext = envelope.copyOfRange(HEADER_BYTES + IV_BYTES, envelope.size)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(recoveryKey, "AES"), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(authenticatedContext(header))
            val vek = try {
                cipher.doFinal(ciphertext)
            } catch (error: AEADBadTagException) {
                throw GeneralSecurityException("Recovery key is invalid or envelope is damaged", error)
            }
            if (vek.size != KEY_BYTES) {
                vek.fill(0)
                throw GeneralSecurityException("Recovered vault key has an invalid size")
            }
            vek
        } finally {
            iv.fill(0)
            ciphertext.fill(0)
            header.fill(0)
        }
    }

    private fun authenticatedContext(header: ByteArray): ByteArray = header + CONTEXT

    private fun nibble(char: Char): Int = when (char) {
        in '0'..'9' -> char.code - '0'.code
        in 'a'..'f' -> char.code - 'a'.code + 10
        else -> throw IllegalArgumentException("Invalid recovery key character")
    }

    private companion object {
        const val KEY_BYTES = 32
        const val HEADER_BYTES = 5
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val ENVELOPE_BYTES = HEADER_BYTES + IV_BYTES + KEY_BYTES + 16
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val HEX = "0123456789abcdef"
        val HEADER = byteArrayOf(0x4b, 0x56, 0x52, 0x4b, 0x01)
        val CONTEXT = "kaagaz-vault/recovery-vek/v1".toByteArray(Charsets.UTF_8)
    }
}
