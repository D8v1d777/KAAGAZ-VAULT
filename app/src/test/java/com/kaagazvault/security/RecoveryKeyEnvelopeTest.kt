package com.kaagazvault.security

import java.security.GeneralSecurityException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryKeyEnvelopeTest {
    private val subject = RecoveryKeyEnvelope()

    @Test
    fun recoveryKeyEncodingIsCanonicalAndRoundTrips() {
        val key = ByteArray(32) { it.toByte() }
        val encoded = subject.encodeRecoveryKey(key)

        assertEquals(64, encoded.length)
        assertEquals(encoded, encoded.lowercase())
        assertArrayEquals(key, subject.decodeRecoveryKey(encoded))
        assertThrows(IllegalArgumentException::class.java) {
            subject.decodeRecoveryKey(encoded.uppercase())
        }
    }

    @Test
    fun generatedRecoveryKeyWrapsAndUnwrapsTheVek() {
        val recoveryKey = subject.generateRecoveryKey()
        val vek = ByteArray(32) { (it * 3).toByte() }

        val envelope = subject.wrap(vek, recoveryKey)
        val recovered = subject.unwrap(envelope, recoveryKey)

        assertArrayEquals(vek, recovered)
        assertEquals(65, envelope.size)
        assertTrue(!envelope.contentEquals(vek))
    }

    @Test
    fun wrongRecoveryKeyFailsClosed() {
        val envelope = subject.wrap(ByteArray(32) { 9 }, ByteArray(32) { 1 })

        assertThrows(GeneralSecurityException::class.java) {
            subject.unwrap(envelope, ByteArray(32) { 2 })
        }
    }

    @Test
    fun modifiedCiphertextFailsAuthentication() {
        val envelope = subject.wrap(ByteArray(32) { 9 }, ByteArray(32) { 1 })
        envelope[envelope.lastIndex] = (envelope.last().toInt() xor 0x01).toByte()

        assertThrows(GeneralSecurityException::class.java) {
            subject.unwrap(envelope, ByteArray(32) { 1 })
        }
    }

    @Test
    fun malformedOrUnsupportedEnvelopeIsRejectedBeforeDecrypt() {
        val key = ByteArray(32) { 1 }
        val envelope = subject.wrap(ByteArray(32) { 9 }, key)

        assertThrows(IllegalArgumentException::class.java) {
            subject.unwrap(envelope.copyOf(envelope.size - 1), key)
        }
        val unsupported = envelope.copyOf().apply { this[4] = 2 }
        assertThrows(IllegalArgumentException::class.java) {
            subject.unwrap(unsupported, key)
        }
    }
}
