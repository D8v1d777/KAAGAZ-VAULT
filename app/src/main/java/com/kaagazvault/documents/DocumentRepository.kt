package com.kaagazvault.documents

import android.content.ContentResolver
import android.net.Uri
import com.kaagazvault.database.EncryptedMetadataIndex
import com.kaagazvault.database.EncryptedMetadataIndexProvider
import com.kaagazvault.ocr.OfflineOcrEngine
import com.kaagazvault.security.EncryptedDocumentStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Imports user-selected PDFs and images without broad storage permissions.
 * Names, MIME metadata, and OCR output are stored inside the encrypted payload.
 */
internal class DocumentRepository(
    private val resolver: ContentResolver,
    private val store: EncryptedDocumentStore,
    private val metadataIndexProvider: EncryptedMetadataIndexProvider? = null
) {
    @Volatile
    var encryptedSearchAvailable: Boolean = false
        private set

    private fun currentIndex(): EncryptedMetadataIndex? =
        metadataIndexProvider?.get().also { encryptedSearchAvailable = it != null }

    private inline fun <T> updateIndex(block: (EncryptedMetadataIndex) -> T): T? {
        val index = currentIndex() ?: return null
        return runCatching { block(index) }
            .onFailure { encryptedSearchAvailable = false }
            .getOrNull()
    }
    @Throws(IOException::class, GeneralSecurityException::class)
    fun import(uri: Uri): ImportedDocument {
        val mimeType = resolver.getType(uri)?.lowercase()
            ?: throw IOException("Could not determine the selected file type")
        if (mimeType != PDF_MIME && !mimeType.startsWith("image/")) throw UnsupportedDocumentTypeException()

        val displayName = queryDisplayName(uri)
        val content = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > DocumentPayloadCodec.MAX_CONTENT_BYTES) throw DocumentTooLargeException()
                output.write(buffer, 0, count)
            }
            if (total == 0) throw IOException("The selected document is empty")
            output.toByteArray()
        } ?: throw IOException("Could not open the selected document")

        return saveContent(displayName, mimeType, content)
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun importBytes(displayName: String, mimeType: String, content: ByteArray): ImportedDocument =
        saveContent(displayName, mimeType.lowercase(), content)

    @Throws(IOException::class, GeneralSecurityException::class)
    private fun saveContent(displayName: String, mimeType: String, content: ByteArray): ImportedDocument {
        if (content.isEmpty()) throw IOException("The selected document is empty")
        if (content.size > DocumentPayloadCodec.MAX_CONTENT_BYTES) throw DocumentTooLargeException()
        if (mimeType != PDF_MIME && !mimeType.startsWith("image/")) throw UnsupportedDocumentTypeException()
        if (!DocumentSignatureValidator.isSupported(mimeType, content)) throw UnsupportedDocumentTypeException()

        val safeName = displayName.filterNot { it.isISOControl() }
            .substringAfterLast('/')
            .trim()
            .take(MAX_NAME_LENGTH)
            .ifBlank { "Imported document" }
        val id = store.save(DocumentPayloadCodec.encode(ImportedPayload(safeName, mimeType, content)))
        val imported = ImportedDocument(id, safeName, mimeType, content.size)
        updateIndex { it.upsert(imported) }
        return imported
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun recognizeDocument(id: String, engine: OfflineOcrEngine): ImportedDocument {
        val payload = DocumentPayloadCodec.decode(store.read(id))
        val result = when {
            payload.mimeType.startsWith("image/") -> engine.recognizeImage(payload.content)
            payload.mimeType == PDF_MIME -> engine.recognizePdf(payload.content)
            else -> throw UnsupportedDocumentTypeException()
        }
        store.replace(
            id,
            DocumentPayloadCodec.encode(payload.copy(
                ocrText = result.text.take(MAX_OCR_CHARACTERS),
                ocrConfidence = result.meanConfidence,
                ocrReviewed = false,
                ocrTruncated = result.truncated || result.text.length > MAX_OCR_CHARACTERS,
                ocrSource = result.source,
                ocrLanguages = result.languages
            ))
        )
        val updated = toDocument(DocumentPayloadCodec.decode(store.read(id)), id)
        updateIndex { it.upsert(updated) }
        return updated
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun saveReviewedOcr(id: String, correctedText: String): ImportedDocument {
        val payload = DocumentPayloadCodec.decode(store.read(id))
        if (payload.ocrText == null) throw IOException("No OCR result exists for this document")
        store.replace(
            id,
            DocumentPayloadCodec.encode(payload.copy(
                ocrText = correctedText.take(MAX_OCR_CHARACTERS),
                ocrReviewed = true,
                ocrTruncated = correctedText.length > MAX_OCR_CHARACTERS
            ))
        )
        val updated = toDocument(DocumentPayloadCodec.decode(store.read(id)), id)
        updateIndex { it.upsert(updated) }
        return updated
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun list(): List<ImportedDocument> {
        val documents = store.listIds().mapNotNull { id ->
            runCatching { toDocument(DocumentPayloadCodec.decode(store.read(id)), id) }.getOrNull()
        }.sortedByDescending { it.displayName.lowercase() }
        // The encrypted files remain the source of truth; rebuilding repairs stale index rows.
        updateIndex { it.rebuild(documents) }
        return documents
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun search(query: String): List<ImportedDocument> {
        if (query.isBlank()) return list()
        val ids = updateIndex { it.search(query) }
        if (ids != null) {
            val matched = ids.mapNotNull { id ->
                runCatching { toDocument(DocumentPayloadCodec.decode(store.read(id)), id) }.getOrNull()
            }
            return matched
        }
        val normalized = EncryptedMetadataIndex.normalize(query)
        return list().filter { document ->
            EncryptedMetadataIndex.normalize(document.displayName + " " + document.ocrText.orEmpty())
                .contains(normalized)
        }
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun delete(id: String) {
        store.delete(id)
        updateIndex { it.delete(id) }
    }

    private fun toDocument(payload: ImportedPayload, id: String) = ImportedDocument(
        id = id,
        displayName = payload.displayName,
        mimeType = payload.mimeType,
        byteSize = payload.content.size,
        ocrText = payload.ocrText,
        ocrConfidence = payload.ocrConfidence,
        ocrReviewed = payload.ocrReviewed,
        ocrTruncated = payload.ocrTruncated,
        ocrSource = payload.ocrSource,
        ocrLanguages = payload.ocrLanguages
    )

    private fun queryDisplayName(uri: Uri): String {
        val candidate = runCatching {
            resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
                }
        }.getOrNull().orEmpty()
        return candidate.substringAfterLast('/')
            .filterNot { it.isISOControl() }
            .trim()
            .take(MAX_NAME_LENGTH)
            .ifBlank { "Imported document" }
    }

    companion object {
        private const val MAX_NAME_LENGTH = 180
        private const val MAX_OCR_CHARACTERS = 150_000
        private const val PDF_MIME = "application/pdf"
    }
}

internal class DocumentTooLargeException : IOException("File exceeds the 31 MiB import limit")
internal class UnsupportedDocumentTypeException : IOException("Choose a supported PDF or image document")
