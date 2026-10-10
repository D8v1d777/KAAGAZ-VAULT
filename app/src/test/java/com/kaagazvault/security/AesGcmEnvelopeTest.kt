package com.kaagazvault.security

import java.io.IOException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class AesGcmEnvelopeTest {
    private lateinit var key: javax.crypto.SecretKey
    private lateinit var envelope: AesGcmEnvelope
    private val context = "synthetic-test-document-id".toByteArray()

    @Before
    fun setUp() {
        key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        envelope = AesGcmEnvelope()
    }

    @Test
    fun roundTripPreservesBytes() {
        val input = ByteArray(4096) { (it % 251).toByte() }
        assertArrayEquals(input, envelope.decrypt(envelope.encrypt(input, key, context), key, context))
    }

    @Test
    fun eachEncryptionUsesFreshIv() {
        val first = envelope.encrypt(byteArrayOf(1, 2, 3), key, context)
        val second = envelope.encrypt(byteArrayOf(1, 2, 3), key, context)
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun modifiedCiphertextFailsAuthentication() {
        val encoded = envelope.encrypt("private synthetic fixture".toByteArray(), key, context)
        encoded[encoded.lastIndex] = (encoded.last().toInt() xor 1).toByte()
        assertThrows(EncryptedDocumentAuthenticationException::class.java) {
            envelope.decrypt(encoded, key, context)
        }
    }

    @Test
    fun wrongContextFailsAuthentication() {
        val encoded = envelope.encrypt(byteArrayOf(9, 8, 7), key, context)
        assertThrows(EncryptedDocumentAuthenticationException::class.java) {
            envelope.decrypt(encoded, key, "different-id".toByteArray())
        }
    }

    @Test
    fun truncatedEnvelopeIsRejected() {
        assertThrows(IOException::class.java) {
            envelope.decrypt(byteArrayOf(0x4b, 0x47, 0x56), key, context)
        }
    }

    @Test
    fun unsupportedVersionIsRejectedBeforeDecryption() {
        val encoded = envelope.encrypt(byteArrayOf(1), key, context)
        encoded[4] = 2
        assertThrows(IOException::class.java) {
            envelope.decrypt(encoded, key, context)
        }
    }
}
