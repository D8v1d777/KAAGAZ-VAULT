package com.kaagazvault.database

import com.kaagazvault.documents.ImportedDocument
import java.text.Normalizer
import java.util.Locale

/**
 * Searchable names and OCR text are duplicated only inside the SQLCipher database.
 * This v1 index supports normalized substring/phrase search, not ranked token search.
 */
internal class EncryptedMetadataIndex(
    private val dao: DocumentMetadataDao,
    private val provider: VaultDatabaseProvider? = null
) {
    private fun <T> withDao(operation: (DocumentMetadataDao) -> T): T =
        provider?.withDatabase { database -> operation(database.documentMetadataDao()) } ?: operation(dao)
    fun upsert(document: ImportedDocument) {
        withDao { it.upsert(document.toEntity()) }
    }

    fun delete(id: String) {
        withDao { it.deleteById(id) }
    }

    fun rebuild(documents: List<ImportedDocument>) {
        withDao { it.replaceAll(documents.map { document -> document.toEntity() }) }
    }

    fun search(query: String): List<String> {
        val normalized = normalize(query)
        return withDao { dao -> dao.search(normalized).map { it.id } }
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
