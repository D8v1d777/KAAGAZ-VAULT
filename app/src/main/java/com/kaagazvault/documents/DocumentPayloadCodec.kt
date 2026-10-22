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
    private const val VERSION_WITH_STRUCTURED_FIELDS = 4
    private const val PAYLOAD_VERSION = VERSION_WITH_STRUCTURED_FIELDS

    fun encode(payload: ImportedPayload): ByteArray {
        require(payload.content.size in 1..MAX_CONTENT_BYTES) {
            "Document payload content size is outside the supported limit"
        }
        val name = payload.displayName.filterNot { it.isISOControl() }.take(MAX_NAME_LENGTH)
            .ifBlank { "Imported document" }
        val text = payload.ocrText?.take(MAX_OCR_CHARACTERS)?.toByteArray(Charsets.UTF_8)
        try {
            if (text != null && text.size > MAX_OCR_TEXT_BYTES) {
                throw IOException("OCR text exceeds the supported size limit")
            }
            val output = WipingByteArrayOutputStream(payload.content.size + (text?.size ?: 0) + 256)
            try {
                val data = DataOutputStream(output)
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
                data.writeInt(payload.structuredFields.size.coerceAtMost(8))
                payload.structuredFields.take(8).forEach { field ->
                    data.writeUTF(field.type.take(24))
                    data.writeUTF(field.value.take(120))
                    data.writeUTF(field.sourceExcerpt.take(180))
                    data.writeUTF(field.method.take(64))
                    data.writeBoolean(field.uncertain)
                    data.writeBoolean(field.accepted)
                }
                return output.copyAndClear()
            } finally {
                output.clear()
            }
        } finally {
            text?.fill(0)
        }
    }

    fun decode(encoded: ByteArray): ImportedPayload {
        DataInputStream(ByteArrayInputStream(encoded)).use { data ->
            if (data.readInt() != PAYLOAD_MAGIC) throw IOException("Unsupported imported document payload")
            val version = data.readInt()
            if (version !in LEGACY_PAYLOAD_VERSION..VERSION_WITH_STRUCTURED_FIELDS) {
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
                val textBytes = ByteArray(textLength)
                try {
                    data.readFully(textBytes)
                    textBytes.toString(Charsets.UTF_8)
                } finally {
                    textBytes.fill(0)
                }
            } else null
            val fields = if (version >= VERSION_WITH_STRUCTURED_FIELDS) {
                val count = data.readInt()
                if (count !in 0..8) throw IOException("Invalid structured field count")
                List(count) {
                    StructuredField(
                        type = data.readUTF().take(24),
                        value = data.readUTF().take(120),
                        sourceExcerpt = data.readUTF().take(180),
                        method = data.readUTF().take(64),
                        uncertain = data.readBoolean(),
                        accepted = data.readBoolean()
                    )
                }
            } else emptyList()
            if (data.available() != 0) throw IOException("Unexpected structured payload data")
            return ImportedPayload(
                displayName = name,
                mimeType = mime,
                content = bytes,
                ocrText = text,
                ocrConfidence = confidence,
                ocrReviewed = reviewed,
                ocrTruncated = truncated,
                ocrSource = source,
                ocrLanguages = languages,
                structuredFields = fields
            )
        }
    }
}

/** ByteArrayOutputStream variant that wipes its internal plaintext buffer after copying. */
internal class WipingByteArrayOutputStream(initialSize: Int) : ByteArrayOutputStream(initialSize) {
    fun copyAndClear(): ByteArray = toByteArray().also { clear() }

    fun clear() {
        buf.fill(0)
        reset()
    }
}
