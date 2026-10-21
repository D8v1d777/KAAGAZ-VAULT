package com.kaagazvault.documents

import com.kaagazvault.security.DocumentKeyProvider
import com.kaagazvault.security.EncryptedDocumentStore
import com.kaagazvault.security.MissingEncryptionKeyException
import com.kaagazvault.security.SessionDocumentPayloadCryptography
import com.kaagazvault.security.VaultLockedException
import android.net.Uri
import java.io.File
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun newKey() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun saveSyntheticDocument(store: EncryptedDocumentStore, name: String): String {
        val encoded = DocumentPayloadCodec.encode(
            ImportedPayload(name, "image/png", byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))
        )
        return try {
            store.save(encoded)
        } finally {
            encoded.fill(0)
        }
    }

    @Test
    fun importConsumesAndClearsPlaintextBuffersAfterSaving() {
        val directory = temporaryFolder.newFolder()
        val key = newKey()
        val store = EncryptedDocumentStore(directory, DocumentKeyProvider { key })
        val repository = DocumentRepository(null, store)
        val content = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x11, 0x22, 0x33, 0x44
        )
        val expected = content.copyOf()

        val imported = repository.importBytes("private.png", "image/png", content)

        assertTrue("Import buffers must be cleared after storage", content.all { it == 0.toByte() })
        val encoded = store.read(imported.id)
        try {
            val payload = DocumentPayloadCodec.decode(encoded)
            try {
                assertEquals("private.png", payload.displayName)
                assertEquals(expected.toList(), payload.content.toList())
            } finally {
                payload.content.fill(0)
            }
        } finally {
            encoded.fill(0)
            expected.fill(0)
        }
    }

    @Test
    fun invalidImportAlsoClearsTheSuppliedPlaintextBuffer() {
        val directory = temporaryFolder.newFolder()
        val key = newKey()
        val store = EncryptedDocumentStore(directory, DocumentKeyProvider { key })
        val repository = DocumentRepository(null, store)
        val invalid = "not a real image".toByteArray()

        assertThrows(UnsupportedDocumentTypeException::class.java) {
            repository.importBytes("private.png", "image/png", invalid)
        }

        assertTrue("Rejected import buffers must also be cleared", invalid.all { it == 0.toByte() })
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun listRetainsUnreadableDocumentAndSearchSurfacesItEvenWhenItDoesNotMatch() {
        val directory = temporaryFolder.newFolder()
        val key = newKey()
        val store = EncryptedDocumentStore(directory, DocumentKeyProvider { key })
        val readableOne = saveSyntheticDocument(store, "alpha.png")
        val readableTwo = saveSyntheticDocument(store, "beta.png")
        val unreadableId = saveSyntheticDocument(store, "gamma.png")

        val ciphertextFile = File(directory, "$unreadableId.kgv")
        val ciphertext = ciphertextFile.readBytes()
        try {
            ciphertext[ciphertext.lastIndex] = (ciphertext.last().toInt() xor 1).toByte()
            ciphertextFile.writeBytes(ciphertext)
        } finally {
            ciphertext.fill(0)
        }

        val repository = DocumentRepository(null, store)
        val listed = repository.list()
        assertEquals(
            "Unreadable encrypted files must remain represented in the vault listing",
            setOf(readableOne, readableTwo, unreadableId),
            listed.map { it.id }.toSet()
        )
        assertEquals(2, listed.count { it.isReadable })
        val unreadable = listed.single { it.id == unreadableId }
        assertTrue(!unreadable.isReadable)
        assertEquals("Unreadable encrypted document", unreadable.displayName)
        assertEquals("application/octet-stream", unreadable.mimeType)
        assertEquals(0, unreadable.byteSize)
        assertEquals(null, unreadable.ocrText)

        val searched = repository.search("no-readable-document-matches-this-query")
        assertEquals(
            "A damaged file must remain surfaced even if its former name does not match",
            setOf(unreadableId),
            searched.filterNot { it.isReadable }.map { it.id }.toSet()
        )
    }

    @Test
    fun sessionBoundRepositoryRejectsAllKeyBearingOperationsWhileLocked() {
        val directory = temporaryFolder.newFolder()
        val session = com.kaagazvault.security.VaultSession()
        val store = EncryptedDocumentStore(directory, SessionDocumentPayloadCryptography(session))
        val repository = DocumentRepository(null, store, session = session)

        assertThrows(VaultLockedException::class.java) { repository.list() }
        assertThrows(VaultLockedException::class.java) { repository.search("private") }
        assertThrows(VaultLockedException::class.java) { repository.delete("123e4567-e89b-12d3-a456-426614174000") }
        assertThrows(VaultLockedException::class.java) {
            repository.importBytes("synthetic.png", "image/png", byteArrayOf(1, 2, 3))
        }
        assertThrows(VaultLockedException::class.java) {
            repository.saveReviewedOcr("123e4567-e89b-12d3-a456-426614174000", "sensitive text")
        }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test
    fun listPropagatesMissingEncryptionKeyInsteadOfDroppingEveryDocument() {
        val directory = temporaryFolder.newFolder()
        val originalKey = newKey()
        val originalStore = EncryptedDocumentStore(directory, DocumentKeyProvider { originalKey })
        saveSyntheticDocument(originalStore, "private.png")

        val missingKeyStore = EncryptedDocumentStore(
            directory,
            DocumentKeyProvider { throw MissingEncryptionKeyException() }
        )
        val repository = DocumentRepository(null, missingKeyStore)
        assertThrows(MissingEncryptionKeyException::class.java) { repository.list() }
    }
}
