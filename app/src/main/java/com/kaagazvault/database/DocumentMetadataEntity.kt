package com.kaagazvault.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * All fields in this entity live only inside the SQLCipher-encrypted database.
 * normalizedSearchText is sensitive OCR-derived data, not a harmless index.
 */
@Entity(tableName = "document_metadata")
internal data class DocumentMetadataEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val mimeType: String,
    val byteSize: Long,
    val updatedAtEpochMillis: Long,
    val normalizedSearchText: String,
    val ocrConfidence: Int?,
    val ocrReviewed: Boolean,
    val ocrTruncated: Boolean
)
