package com.kaagazvault.security

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.UUID

/**
 * Stores opaque, encrypted document payloads under app-private internal storage.
 *
 * This first vertical slice protects payload bytes only. It does not yet persist document
 * metadata/OCR, implement vault locking, or provide secure deletion or backup recovery.
 */
internal class EncryptedDocumentStore(
    private val directory: File,
    private val keyProvider: DocumentKeyProvider,
    private val envelope: AesGcmEnvelope = AesGcmEnvelope()
) {
    init {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create encrypted document directory")
        }
        if (!directory.isDirectory) throw IOException("Encrypted document path is not a directory")
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun save(payload: ByteArray): String {
        require(payload.size <= MAX_PAYLOAD_BYTES) { "Document payload exceeds the supported size limit" }
        val id = UUID.randomUUID().toString()
        val encrypted = envelope.encrypt(payload, keyProvider.getOrCreateKey(), id.toByteArray(Charsets.UTF_8))
        val destination = fileFor(id)
        val temporary = File(directory, ".$id.pending")

        try {
            FileOutputStream(temporary).use { output ->
                output.write(encrypted)
                output.fd.sync()
            }
            if (!temporary.renameTo(destination)) {
                throw IOException("Could not atomically publish encrypted document")
            }
            return id
        } finally {
            // Only ciphertext is ever written to the pending file.
            if (temporary.exists()) temporary.delete()
        }
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun read(id: String): ByteArray {
        val file = fileFor(id)
        if (!file.isFile) throw IOException("Encrypted document not found")
        if (file.length() > MAX_ENVELOPE_BYTES || file.length() < MIN_ENVELOPE_BYTES) {
            throw IOException("Encrypted document has an invalid size")
        }
        val encrypted = file.readBytes()
        return envelope.decrypt(encrypted, keyProvider.getOrCreateKey(), id.toByteArray(Charsets.UTF_8))
    }

    @Throws(IOException::class)
    fun delete(id: String) {
        val file = fileFor(id)
        if (file.exists() && !file.delete()) throw IOException("Could not delete encrypted document")
    }

    private fun fileFor(id: String): File {
        val parsed = try {
            UUID.fromString(id)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid document identifier", error)
        }
        require(parsed.toString() == id.lowercase()) { "Invalid document identifier" }
        return File(directory, "$id.kgv")
    }

    private companion object {
        const val MAX_PAYLOAD_BYTES = 32 * 1024 * 1024
        const val HEADER_LENGTH = 4 + 1 + 12
        const val TAG_LENGTH_BYTES = 16
        const val MIN_ENVELOPE_BYTES = HEADER_LENGTH + TAG_LENGTH_BYTES
        const val MAX_ENVELOPE_BYTES = MAX_PAYLOAD_BYTES + HEADER_LENGTH + TAG_LENGTH_BYTES
    }
}
