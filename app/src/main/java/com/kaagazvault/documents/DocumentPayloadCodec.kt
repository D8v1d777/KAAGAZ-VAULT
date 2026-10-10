package com.kaagazvault.documents

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Binary payload format lives inside EncryptedDocumentStore's authenticated ciphertext.
 * Version 1 remains readable so early prototype documents can be upgraded in place.
 */
internal object DocumentPayloadCodec {
    const val MAX_CONTENT_BYTES = 31 * 1024 * 1024
    private const val MAX_NAME_LENGTH = 180
    private const val MAX_OCR_CHARACTERS = 150_000
    private const val MAX_OCR_TEXT_BYTES = 600_000
    private const val PAYLOAD_MAGIC = 0x4b475044 // KGPD
    private const val LEGACY_PAYLOAD_VERSION = 1
    private const val VERSION_WITH_OCR_SOURCE = 3
    private const val PAYLOAD_VERSION = VERSION_WITH_OCR_SOURCE

    fun encode(payload: ImportedPayload): ByteArray {
        require(payload.content.size in 1..MAX_CONTENT_BYTES) {
            "Document payload content size is outside the supported limit"
        }
        val name = payload.displayName.filterNot { it.isISOControl() }.take(MAX_NAME_LENGTH)
            .ifBlank { "Imported document" }
        val text = payload.ocrText?.take(MAX_OCR_CHARACTERS)?.toByteArray(Charsets.UTF_8)
        if (text != null && text.size > MAX_OCR_TEXT_BYTES) {
            throw IOException("OCR text exceeds the supported size limit")
        }
        val output = ByteArrayOutputStream(payload.content.size + (text?.size ?: 0) + 256)
        DataOutputStream(output).use { data ->
            data.writeInt(PAYLOAD_MAGIC)
            data.writeInt(PAYLOAD_VERSION)
            data.writeUTF(name)
            data.writeUTF(payload.mimeType)
            data.writeInt(payload.content.size)
            data.write(payload.content)
            data.writeInt(payload.ocrConfidence?.coerceIn(0, 100) ?: -1)
            data.writeBoolean(payload.ocrReviewed)
            data.writeBoolean(payload.ocrTruncated || (payload.ocrText?.length ?: 0) > MAX_OCR_CHARACTERS)
            data.writeUTF(payload.ocrSource.orEmpty().take(64))
            data.writeUTF(payload.ocrLanguages.orEmpty().take(64))
            data.writeInt(text?.size ?: -1)
            if (text != null) data.write(text)
        }
        return output.toByteArray()
    }

    fun decode(encoded: ByteArray): ImportedPayload {
        DataInputStream(ByteArrayInputStream(encoded)).use { data ->
            if (data.readInt() != PAYLOAD_MAGIC) throw IOException("Unsupported imported document payload")
            val version = data.readInt()
            if (version !in LEGACY_PAYLOAD_VERSION..VERSION_WITH_OCR_SOURCE) {
                throw IOException("Unsupported imported document payload version")
            }
            val name = data.readUTF()
            val mime = data.readUTF()
            val length = data.readInt()
            if (length <= 0 || length > MAX_CONTENT_BYTES || length > data.available()) {
                throw IOException("Invalid imported document payload length")
            }
            val bytes = ByteArray(length)
            data.readFully(bytes)

            if (version == LEGACY_PAYLOAD_VERSION) {
                if (data.available() != 0) throw IOException("Unexpected legacy payload data")
                return ImportedPayload(name, mime, bytes)
            }

            val confidence = data.readInt().takeIf { it in 0..100 }
            val reviewed = data.readBoolean()
            val truncated = data.readBoolean()
            val source = if (version >= VERSION_WITH_OCR_SOURCE) data.readUTF().takeIf { it.isNotBlank() } else null
            val languages = if (version >= VERSION_WITH_OCR_SOURCE) data.readUTF().takeIf { it.isNotBlank() } else null
            val textLength = data.readInt()
            if (textLength < -1 || textLength > MAX_OCR_TEXT_BYTES ||
                (textLength == -1 && data.available() != 0) ||
                (textLength >= 0 && textLength != data.available())
            ) throw IOException("Invalid encrypted OCR payload length")
            val text = if (textLength >= 0) {
                ByteArray(textLength).also(data::readFully).toString(Charsets.UTF_8)
            } else null
            return ImportedPayload(
                displayName = name,
                mimeType = mime,
                content = bytes,
                ocrText = text,
                ocrConfidence = confidence,
                ocrReviewed = reviewed,
                ocrTruncated = truncated,
                ocrSource = source,
                ocrLanguages = languages
            )
        }
    }
}
