package com.kaagazvault.security

import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedDocumentStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun newStore(key: SecretKey, directory: File = temporaryFolder.newFolder()): EncryptedDocumentStore =
        EncryptedDocumentStore(directory, DocumentKeyProvider { key })

    @Test
    fun savesOnlyCiphertextAndReadsOriginalPayload() {
        val directory = temporaryFolder.newFolder()
        val payload = "synthetic document payload - not real personal data".toByteArray()
        val store = newStore(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(), directory)

        val id = store.save(payload)
        val files = directory.listFiles()!!.toList()
        assertEquals(1, files.size)
        assertTrue(files.single().name == "$id.kgv")
        assertFalse(files.single().readBytes().toString(Charsets.ISO_8859_1).contains("synthetic document payload"))
        assertArrayEquals(payload, store.read(id))
    }

    @Test
    fun deleteRemovesStoredCiphertext() {
        val directory = temporaryFolder.newFolder()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = newStore(key, directory)
        val id = store.save(byteArrayOf(1, 2, 3))
        store.delete(id)
        assertFalse(File(directory, "$id.kgv").exists())
        assertThrows(java.io.IOException::class.java) { store.read(id) }
    }

    @Test
    fun identifierCannotEscapeStorageDirectory() {
        val store = newStore(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
        assertThrows(IllegalArgumentException::class.java) { store.read("../../private") }
    }

    @Test
    fun tamperedFileFailsClosed() {
        val directory = temporaryFolder.newFolder()
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val store = newStore(key, directory)
        val id = store.save("synthetic".toByteArray())
        val file = File(directory, "$id.kgv")
        val data = file.readBytes()
        data[data.lastIndex] = (data.last().toInt() xor 1).toByte()
        file.writeBytes(data)
        assertThrows(EncryptedDocumentAuthenticationException::class.java) { store.read(id) }
    }

    @Test
    fun invalidIdentifierIsRejected() {
        val store = newStore(KeyGenerator.getInstance("AES").apply { init(256) }.generateKey())
        assertThrows(IllegalArgumentException::class.java) { store.delete("not-a-uuid") }
    }
}
