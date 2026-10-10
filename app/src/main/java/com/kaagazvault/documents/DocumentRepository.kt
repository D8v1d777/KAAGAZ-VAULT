package com.kaagazvault.documents

import android.content.ContentResolver
import android.net.Uri
import com.kaagazvault.ocr.OfflineOcrEngine
import com.kaagazvault.security.EncryptedDocumentStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Imports user-selected PDFs and images without broad storage permissions.
 * Names, MIME metadata, and OCR output are stored inside the encrypted payload.
 */
internal class DocumentRepository(
    private val resolver: ContentResolver,
    private val store: EncryptedDocumentStore
) {
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
                if (total > MAX_CONTENT_BYTES) throw DocumentTooLargeException()
                output.write(buffer, 0, count)
            }
            if (total == 0) throw IOException("The selected document is empty")
            output.toByteArray()
        } ?: throw IOException("Could not open the selected document")

        if (!DocumentSignatureValidator.isSupported(mimeType, content)) throw UnsupportedDocumentTypeException()
        val id = store.save(encode(ImportedPayload(displayName, mimeType, content)))
        return ImportedDocument(id, displayName, mimeType, content.size)
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun recognizeImage(id: String, engine: OfflineOcrEngine): ImportedDocument {
        val payload = decodePayload(store.read(id))
        if (!payload.mimeType.startsWith("image/")) throw UnsupportedDocumentTypeException()
        val result = engine.recognizeImage(payload.content)
        store.replace(
            id,
            encode(payload.copy(
                ocrText = result.text.take(MAX_OCR_CHARACTERS),
                ocrConfidence = result.meanConfidence,
                ocrReviewed = false,
                ocrTruncated = result.text.length > MAX_OCR_CHARACTERS
            ))
        )
        return decode(store.read(id), id)
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun saveReviewedOcr(id: String, correctedText: String): ImportedDocument {
        val payload = decodePayload(store.read(id))
        if (payload.ocrText == null) throw IOException("No OCR result exists for this document")
        store.replace(
            id,
            encode(payload.copy(
                ocrText = correctedText.take(MAX_OCR_CHARACTERS),
                ocrReviewed = true,
                ocrTruncated = correctedText.length > MAX_OCR_CHARACTERS
            ))
        )
        return decode(store.read(id), id)
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun list(): List<ImportedDocument> =
        store.listIds().mapNotNull { id -> runCatching { decode(store.read(id), id) }.getOrNull() }
            .sortedByDescending { it.displayName.lowercase() }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun delete(id: String) = store.delete(id)

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

    private fun encode(payload: ImportedPayload): ByteArray {
        val text = payload.ocrText?.take(MAX_OCR_CHARACTERS)?.toByteArray(Charsets.UTF_8)
        if (text != null && text.size > MAX_OCR_TEXT_BYTES) throw IOException("OCR text exceeds the supported size limit")
        val output = ByteArrayOutputStream(payload.content.size + (text?.size ?: 0) + 256)
        DataOutputStream(output).use { data ->
            data.writeInt(PAYLOAD_MAGIC)
            data.writeInt(PAYLOAD_VERSION)
            data.writeUTF(payload.displayName)
            data.writeUTF(payload.mimeType)
            data.writeInt(payload.content.size)
            data.write(payload.content)
            data.writeInt(payload.ocrConfidence ?: -1)
            data.writeBoolean(payload.ocrReviewed)
            data.writeBoolean(payload.ocrTruncated)
            data.writeInt(text?.size ?: -1)
            if (text != null) data.write(text)
        }
        return output.toByteArray()
    }

    private fun decode(encoded: ByteArray, id: String): ImportedDocument {
        val payload = decodePayload(encoded)
        return ImportedDocument(
            id = id,
            displayName = payload.displayName,
            mimeType = payload.mimeType,
            byteSize = payload.content.size,
            ocrText = payload.ocrText,
            ocrConfidence = payload.ocrConfidence,
            ocrReviewed = payload.ocrReviewed,
            ocrTruncated = payload.ocrTruncated
        )
    }

    private fun decodePayload(encoded: ByteArray): ImportedPayload {
        DataInputStream(ByteArrayInputStream(encoded)).use { data ->
            if (data.readInt() != PAYLOAD_MAGIC) throw IOException("Unsupported imported document payload")
            val version = data.readInt()
            if (version != LEGACY_PAYLOAD_VERSION && version != PAYLOAD_VERSION) {
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
            val textLength = data.readInt()
            if (textLength < -1 || textLength > MAX_OCR_TEXT_BYTES ||
                (textLength == -1 && data.available() != 0) ||
                (textLength >= 0 && textLength != data.available())
            ) throw IOException("Invalid encrypted OCR payload length")
            val text = if (textLength >= 0) {
                ByteArray(textLength).also(data::readFully).toString(Charsets.UTF_8)
            } else null
            return ImportedPayload(name, mime, bytes, text, confidence, reviewed, truncated)
        }
    }

    companion object {
        const val MAX_CONTENT_BYTES = 31 * 1024 * 1024
        private const val MAX_NAME_LENGTH = 180
        private const val MAX_OCR_CHARACTERS = 150_000
        private const val MAX_OCR_TEXT_BYTES = 600_000
        private const val PDF_MIME = "application/pdf"
        private const val PAYLOAD_MAGIC = 0x4b475044
        private const val LEGACY_PAYLOAD_VERSION = 1
        private const val PAYLOAD_VERSION = 2
    }
}

internal class DocumentTooLargeException : IOException("File exceeds the 31 MiB import limit")
internal class UnsupportedDocumentTypeException : IOException("Choose a supported PDF or image document")
