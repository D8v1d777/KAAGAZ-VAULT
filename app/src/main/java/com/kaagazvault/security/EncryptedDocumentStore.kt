package com.kaagazvault.security

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.UUID

/**
 * Stores opaque, encrypted document payloads under app-private internal storage.
 *
 * Key access is supplied by [DocumentPayloadCryptography]. The session-backed implementation
 * holds the session read lock over every store operation, including atomic publication and delete.
 * Legacy callers can continue to use [DocumentKeyProvider] during the reviewed migration window.
 */
internal class EncryptedDocumentStore(
    private val directory: File,
    private val cryptography: DocumentPayloadCryptography
) {
    constructor(
        directory: File,
        keyProvider: DocumentKeyProvider,
        envelope: AesGcmEnvelope = AesGcmEnvelope()
    ) : this(directory, LegacyDocumentPayloadCryptography(keyProvider, envelope))

    private val recoveryLock = Any()
    @Volatile private var temporaryFilesRecovered = false

    init {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create encrypted document directory")
        }
        if (!directory.isDirectory) throw IOException("Encrypted document path is not a directory")
        // Recovery can rename/delete ciphertext files, so defer it until the first authorized
        // store operation rather than mutating protected storage merely by constructing a store.
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun save(payload: ByteArray): String = withCipher { cipher ->
        require(payload.size <= MAX_PAYLOAD_BYTES) { "Document payload exceeds the supported size limit" }
        val id = UUID.randomUUID().toString()
        val encrypted = cipher.encrypt(payload, id.toByteArray(Charsets.UTF_8))
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
            id
        } finally {
            encrypted.fill(0)
            // Only ciphertext is ever written to the pending file.
            if (temporary.exists()) temporary.delete()
        }
    }

    @Throws(IOException::class, GeneralSecurityException::class)
    fun read(id: String): ByteArray = withCipher { cipher ->
        val file = fileFor(id)
        if (!file.isFile) throw IOException("Encrypted document not found")
        if (file.length() > MAX_ENVELOPE_BYTES || file.length() < MIN_ENVELOPE_BYTES) {
            throw IOException("Encrypted document has an invalid size")
        }
        val encrypted = file.readBytes()
        try {
            cipher.decrypt(encrypted, id.toByteArray(Charsets.UTF_8))
        } finally {
            encrypted.fill(0)
        }
    }

    /**
     * Replaces a stored ciphertext under the same opaque ID. Both pending and backup files
     * contain ciphertext only; rollback preserves the previous document if publication fails.
     */
    @Throws(IOException::class, GeneralSecurityException::class)
    fun replace(id: String, payload: ByteArray) = withCipher { cipher ->
        require(payload.size <= MAX_PAYLOAD_BYTES) { "Document payload exceeds the supported size limit" }
        val destination = fileFor(id)
        if (!destination.isFile) throw IOException("Encrypted document not found")
        val encrypted = cipher.encrypt(payload, id.toByteArray(Charsets.UTF_8))
        val temporary = File(directory, ".$id.pending")
        val backup = File(directory, ".$id.backup")

        try {
            FileOutputStream(temporary).use { output ->
                output.write(encrypted)
                output.fd.sync()
            }
            if (backup.exists() && !backup.delete()) throw IOException("Could not prepare encrypted document update")
            if (!destination.renameTo(backup)) throw IOException("Could not stage encrypted document update")
            if (!temporary.renameTo(destination)) {
                backup.renameTo(destination)
                throw IOException("Could not publish encrypted document update")
            }
            // A stale backup is ciphertext, not plaintext. Startup recovery restores it if
            // publication was interrupted before the new destination became visible.
            backup.delete()
        } finally {
            encrypted.fill(0)
            if (temporary.exists()) temporary.delete()
        }
        Unit
    }

    /** Returns opaque IDs only, but remains inside the same authorization boundary. */
    fun listIds(): List<String> = withCipher {
        directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(FILE_EXTENSION) }
            ?.map { it.name.removeSuffix(FILE_EXTENSION) }
            ?.filter { id -> runCatching { fileFor(id) }.isSuccess }
            ?.toList()
            .orEmpty()
    }

    @Throws(IOException::class)
    fun delete(id: String) = withCipher {
        val file = fileFor(id)
        if (file.exists() && !file.delete()) throw IOException("Could not delete encrypted document")
    }

    private fun <T> withCipher(block: (DocumentPayloadCipher) -> T): T =
        cryptography.withCipher { cipher ->
            ensureTemporaryFilesRecovered()
            block(cipher)
        }

    private fun ensureTemporaryFilesRecovered() {
        if (temporaryFilesRecovered) return
        synchronized(recoveryLock) {
            if (temporaryFilesRecovered) return
            recoverTemporaryFiles()
            temporaryFilesRecovered = true
        }
    }

    private fun recoverTemporaryFiles() {
        directory.listFiles()?.forEach { file ->
            when {
                file.name.startsWith(".") && file.name.endsWith(".backup") -> {
                    val id = file.name.removePrefix(".").removeSuffix(".backup")
                    val destination = runCatching { fileFor(id) }.getOrNull()
                    if (destination == null) {
                        file.delete()
                    } else if (!destination.exists()) {
                        file.renameTo(destination)
                    } else {
                        file.delete()
                    }
                }
                file.name.startsWith(".") && file.name.endsWith(".pending") -> file.delete()
            }
        }
    }

    private fun fileFor(id: String): File {
        val parsed = try {
            UUID.fromString(id)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid document identifier", error)
        }
        require(parsed.toString() == id) { "Invalid document identifier" }
        return File(directory, "$id$FILE_EXTENSION")
    }

    private companion object {
        const val MAX_PAYLOAD_BYTES = 32 * 1024 * 1024
        const val FILE_EXTENSION = ".kgv"
        const val HEADER_LENGTH = 4 + 1 + 12
        const val TAG_LENGTH_BYTES = 16
        const val MIN_ENVELOPE_BYTES = HEADER_LENGTH + TAG_LENGTH_BYTES
        const val MAX_ENVELOPE_BYTES = MAX_PAYLOAD_BYTES + HEADER_LENGTH + TAG_LENGTH_BYTES
    }
}
