package com.kaagazvault.documents

import android.content.ContentResolver
import android.net.Uri
import com.kaagazvault.security.AndroidKeystoreDocumentKeyProvider
import com.kaagazvault.security.EncryptedDocumentStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Imports user-selected PDFs and images without broad storage permissions.
 * Names and MIME metadata are stored inside the encrypted payload, never in filenames.
 */
internal class DocumentRepository(
    private val resolver: ContentResolver,
    private val store: EncryptedDocumentStore
) {
    @Throws(IOException::class, GeneralSecurityException::class)
    fun import(uri: Uri): ImportedDocument {
        val mimeType = resolver.getType(uri)?.lowercase()
            ?: throw IOException("Could not determine the selected file type")
        if (mimeType != PDF_MIME && !mimeType.startsWith("image/")) {
            throw UnsupportedDocumentTypeException()
        }

        val displayName = queryDisplayName(uri)
        val content = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_CONTENT_BYTES) throw DocumentTooLargeException()
                output.write(buffer, 0, count)
            }
            if (total == 0) throw IOException("The selected document is empty")
            output.toByteArray()
        } ?: throw IOException("Could not open the selected document")

        val payload = encode(ImportedPayload(displayName, mimeType, content))
        val id = store.save(payload)
        return ImportedDocument(id, displayName, mimeType, content.size)
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun list(): List<ImportedDocument> =
        store.listIds().mapNotNull { id ->
            runCatching { decode(store.read(id), id) }.getOrNull()
        }.sortedByDescending { it.displayName.lowercase() }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun delete(id: String) = store.delete(id)

    private fun queryDisplayName(uri: Uri): String {
        val candidate = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME))
                    } else null
                }
        }.getOrNull().orEmpty()
        val cleaned = candidate
            .substringAfterLast('/')
            .filterNot { it.isISOControl() }
            .trim()
            .take(MAX_NAME_LENGTH)
        return cleaned.ifBlank { "Imported document" }
    }

    private fun encode(payload: ImportedPayload): ByteArray {
        val output = ByteArrayOutputStream(payload.content.size + 256)
        DataOutputStream(output).use { data ->
            data.writeInt(PAYLOAD_MAGIC)
            data.writeInt(PAYLOAD_VERSION)
            data.writeUTF(payload.displayName)
            data.writeUTF(payload.mimeType)
            data.writeInt(payload.content.size)
            data.write(payload.content)
        }
        return output.toByteArray()
    }

    private fun decode(encoded: ByteArray, id: String): ImportedDocument {
        DataInputStream(ByteArrayInputStream(encoded)).use { data ->
            if (data.readInt() != PAYLOAD_MAGIC || data.readInt() != PAYLOAD_VERSION) {
                throw IOException("Unsupported imported document payload")
            }
            val name = data.readUTF()
            val mime = data.readUTF()
            val length = data.readInt()
            if (length <= 0 || length > MAX_CONTENT_BYTES || length != data.available()) {
                throw IOException("Invalid imported document payload length")
            }
            return ImportedDocument(id, name, mime, length)
        }
    }

    companion object {
        const val MAX_CONTENT_BYTES = 31 * 1024 * 1024
        private const val MAX_NAME_LENGTH = 180
        private const val PDF_MIME = "application/pdf"
        private const val PAYLOAD_MAGIC = 0x4b475044 // KGPD
        private const val PAYLOAD_VERSION = 1
    }
}

internal class DocumentTooLargeException : IOException("File exceeds the 31 MiB import limit")
internal class UnsupportedDocumentTypeException : IOException("Choose a PDF or image document")
