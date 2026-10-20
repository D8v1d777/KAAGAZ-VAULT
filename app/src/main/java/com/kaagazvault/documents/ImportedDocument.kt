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
    val ocrLanguages: String? = null,
    /** False means the ciphertext exists, but its name and contents could not be authenticated/decoded. */
    val isReadable: Boolean = true
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
