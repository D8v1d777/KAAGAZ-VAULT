package com.kaagazvault.security

import java.io.File
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

}
