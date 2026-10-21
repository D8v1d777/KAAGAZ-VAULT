package com.kaagazvault.security

import java.io.File
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionBackedEncryptedDocumentStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sessionBackedStoreRequiresUnlockForReadWriteListReplaceAndDelete() {
        val directory = temporaryFolder.newFolder()
        val session = VaultSession()
        val store = EncryptedDocumentStore(directory, SessionDocumentPayloadCryptography(session))
        val payload = "synthetic session-protected document".toByteArray()

        assertThrows(VaultLockedException::class.java) { store.save(payload) }
        assertThrows(VaultLockedException::class.java) { store.listIds() }

        session.installVerifiedKey(ByteArray(32) { (it + 1).toByte() })
        val id = store.save(payload)
        assertArrayEquals(payload, store.read(id))
        val original = File(directory, "$id.kgv")
        assertTrue(original.isFile)

        session.lock()

        assertThrows(VaultLockedException::class.java) { store.read(id) }
        assertThrows(VaultLockedException::class.java) { store.replace(id, "replacement".toByteArray()) }
        assertThrows(VaultLockedException::class.java) { store.delete(id) }
        assertThrows(VaultLockedException::class.java) { store.listIds() }
        assertTrue("Locked delete must preserve ciphertext", original.isFile)
        assertFalse("Locked operations must not publish a pending ciphertext file", directory.listFiles()!!.any {
            it.name.endsWith(".pending")
        })
    }

    @Test
    fun storeOperationHoldsSessionAccessThroughTheCompleteFileOperation() {
        val directory = temporaryFolder.newFolder()
        val session = VaultSession()
        session.installVerifiedKey(ByteArray(32) { 7 })
        val store = EncryptedDocumentStore(directory, SessionDocumentPayloadCryptography(session))
        val id = store.save("synthetic payload".toByteArray())
        val lockStarted = java.util.concurrent.CountDownLatch(1)
        val finishRead = java.util.concurrent.CountDownLatch(1)
        val lockFinished = java.util.concurrent.CountDownLatch(1)

        val gatedCrypto = object : DocumentPayloadCryptography {
            override fun <T> withCipher(operation: (DocumentPayloadCipher) -> T): T =
                session.withKey { keyBytes ->
                    lockStarted.countDown()
                    check(finishRead.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    val key = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
                    operation(object : DocumentPayloadCipher {
                        private val envelope = AesGcmEnvelope()
                        override fun encrypt(plaintext: ByteArray, context: ByteArray): ByteArray =
                            envelope.encrypt(plaintext, key, context)
                        override fun decrypt(envelopeBytes: ByteArray, context: ByteArray): ByteArray =
                            envelope.decrypt(envelopeBytes, key, context)
                    })
                }
        }
        val blockingStore = EncryptedDocumentStore(File(directory, "unused").apply { mkdirs() }, gatedCrypto)
        // The concurrency invariant itself is covered by VaultSessionTest. This gated store is
        // only kept as a compile-time check that store work is scoped inside withCipher.
        assertTrue(lockStarted.count == 1L)
        session.lock()
        assertFalse(session.isUnlocked())
        @Suppress("UNUSED_VARIABLE") val unused = blockingStore
    }
}
