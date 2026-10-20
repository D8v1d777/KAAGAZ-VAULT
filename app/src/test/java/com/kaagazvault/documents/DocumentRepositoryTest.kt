package com.kaagazvault.documents

import com.kaagazvault.security.DocumentKeyProvider
import com.kaagazvault.security.EncryptedDocumentStore
import com.kaagazvault.security.MissingEncryptionKeyException
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

        val searched = repository.search("no-readable-document-matches-this-query")
        assertTrue(
            "A damaged file must remain surfaced even if its former name does not match",
            searched.any { it.id == unreadableId }
        )
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
