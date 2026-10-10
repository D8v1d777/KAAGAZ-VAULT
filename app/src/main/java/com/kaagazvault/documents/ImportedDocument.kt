package com.kaagazvault.documents

data class ImportedDocument(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val byteSize: Int,
    val ocrText: String? = null,
    val ocrConfidence: Int? = null,
    val ocrReviewed: Boolean = false,
    val ocrTruncated: Boolean = false,
    val ocrSource: String? = null,
    val ocrLanguages: String? = null
)

internal data class ImportedPayload(
    val displayName: String,
    val mimeType: String,
    val content: ByteArray,
    val ocrText: String? = null,
    val ocrConfidence: Int? = null,
    val ocrReviewed: Boolean = false,
    val ocrTruncated: Boolean = false,
    val ocrSource: String? = null,
    val ocrLanguages: String? = null
)
