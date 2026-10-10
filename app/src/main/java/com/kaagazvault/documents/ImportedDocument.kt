package com.kaagazvault.documents

data class ImportedDocument(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val byteSize: Int
)

internal data class ImportedPayload(
    val displayName: String,
    val mimeType: String,
    val content: ByteArray
)
