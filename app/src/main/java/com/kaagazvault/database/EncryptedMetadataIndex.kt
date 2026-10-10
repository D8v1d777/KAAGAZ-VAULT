package com.kaagazvault.database

import com.kaagazvault.documents.ImportedDocument
import java.text.Normalizer
import java.util.Locale

/**
 * Searchable names and OCR text are duplicated only inside the SQLCipher database.
 * This v1 index supports normalized substring/phrase search, not ranked token search.
 */
internal class EncryptedMetadataIndex(private val dao: DocumentMetadataDao) {
    fun upsert(document: ImportedDocument) {
        dao.upsert(document.toEntity())
    }

    fun delete(id: String) {
        dao.deleteById(id)
    }

    fun rebuild(documents: List<ImportedDocument>) {
        dao.replaceAll(documents.map { it.toEntity() })
    }

    fun search(query: String): List<String> {
        val normalized = normalize(query)
        return dao.search(normalized).map { it.id }
    }

    private fun ImportedDocument.toEntity() = DocumentMetadataEntity(
        id = id,
        displayName = displayName,
        mimeType = mimeType,
        byteSize = byteSize.toLong(),
        updatedAtEpochMillis = System.currentTimeMillis(),
        normalizedSearchText = normalize(displayName + "\n" + ocrText.orEmpty()),
        ocrConfidence = ocrConfidence,
        ocrReviewed = ocrReviewed,
        ocrTruncated = ocrTruncated
    )

    companion object {
        fun normalize(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFC)
                .lowercase(Locale.ROOT)
                .replace(Regex("\\s+"), " ")
                .trim()
    }
}
