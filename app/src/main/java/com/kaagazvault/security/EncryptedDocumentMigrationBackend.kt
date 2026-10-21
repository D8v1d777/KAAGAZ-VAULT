package com.kaagazvault.security

import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.nio.channels.OverlappingFileLockException
import java.security.GeneralSecurityException
import java.util.UUID
import javax.crypto.SecretKey

/**
 * A short-lived, callback-scoped view of the VEK for one cryptographic operation.
 *
 * Implementations must not retain the key object or return it from [withKey]. A production
 * implementation should delegate to VaultSession so the session clears its temporary key copy
 * immediately after the operation finishes.
 */
internal interface MigrationVekKeyAccess {
    fun <T> withKey(block: (SecretKey) -> T): T
}

/**
 * Database-key work is deliberately a separate boundary. A production implementation must make
 * rewrapping idempotent across process death and verify the reopened SQLCipher database plus
 * integrity result. This interface alone is not evidence that either operation is implemented.
 */
internal interface VaultDatabaseMigrationActions {
    fun rewrapDatabaseKey()
    fun verifyDatabaseIntegrity()
}

/**
 * File-backed document migration adapter.
 *
 * Legacy ciphertext is never overwritten or deleted. A document is decrypted using the existing
 * legacy Keystore key, re-encrypted with the existing AesGcmEnvelope format under the VEK, staged
 * in one root, authenticated and byte-compared with the legacy plaintext, then copied verbatim
 * into a distinct published root and authenticated again. Staging and legacy copies remain after
 * publication so journal replay does not depend on a destructive rename.
 *
 * This class is not wired to the app's production paths. The caller must exclude concurrent
 * repository writes and supply a MigrationVekKeyAccess backed by VaultSession before integration.
 */
internal class EncryptedDocumentMigrationBackend(
    private val legacyDirectory: File,
    private val legacyKey: SecretKey,
    private val stagingDirectory: File,
    private val publishedDirectory: File,
    private val vekKeyAccess: MigrationVekKeyAccess,
    private val databaseActions: VaultDatabaseMigrationActions,
    private val envelope: AesGcmEnvelope = AesGcmEnvelope()
) : VaultMigrationBackend {

    init {
        validateDistinctRoots(legacyDirectory, stagingDirectory, publishedDirectory)
        if (!legacyDirectory.isDirectory) {
            throw IOException("Legacy encrypted-document directory is unavailable")
        }
        ensureDirectory(stagingDirectory)
        ensureDirectory(publishedDirectory)
    }

    @Synchronized
    override fun listLegacyDocumentIds(): List<String> {
        val files = legacyDirectory.listFiles()
            ?: throw IOException("Could not list legacy encrypted documents")
        val result = ArrayList<String>()
        for (file in files) {
            if (!file.isFile || !file.name.endsWith(FILE_EXTENSION)) continue
            val id = file.name.removeSuffix(FILE_EXTENSION)
            try {
                canonicalId(id)
            } catch (invalid: IllegalArgumentException) {
                throw IOException("Legacy vault contains an invalid encrypted-document filename", invalid)
            }
            result += id
        }
        return result.sorted()
    }

    @Synchronized
    @Throws(IOException::class, GeneralSecurityException::class)
    override fun stageDocument(documentId: String) {
        val id = canonicalId(documentId)
        val source = fileFor(legacyDirectory, id)
        if (!source.isFile) throw IOException("Legacy encrypted document is missing")
        val staged = fileFor(stagingDirectory, id)

        if (staged.exists()) {
            verifyStagedDocument(id)
            return
        }
        if (fileFor(publishedDirectory, id).exists()) {
            throw IOException("Published document exists without its staged source; recovery is required")
        }

        val plaintext = decryptFile(source) { ciphertext ->
            envelope.decrypt(ciphertext, legacyKey, contextFor(id))
        }
        try {
            val encrypted = vekKeyAccess.withKey { key ->
                envelope.encrypt(plaintext, key, contextFor(id))
            }
            try {
                // A concurrent worker may have created the destination first; either way,
                // authenticate and compare the durable result before advancing the journal.
                writeCiphertextIfAbsent(stagingDirectory, id, encrypted, STAGING_SUFFIX)
                verifyStagedDocument(id)
            } finally {
                encrypted.fill(0)
            }
        } finally {
            plaintext.fill(0)
        }
    }

    @Synchronized
    @Throws(IOException::class, GeneralSecurityException::class)
    override fun verifyStagedDocument(documentId: String) {
        val id = canonicalId(documentId)
        val sourcePayload = decryptFile(fileFor(legacyDirectory, id)) { ciphertext ->
            envelope.decrypt(ciphertext, legacyKey, contextFor(id))
        }
        try {
            val stagedPayload = decryptVekFile(fileFor(stagingDirectory, id), id)
            try {
                if (!sourcePayload.contentEquals(stagedPayload)) {
                    throw IOException("Staged encrypted document does not match its legacy source")
                }
            } finally {
                stagedPayload.fill(0)
            }
        } finally {
            sourcePayload.fill(0)
        }
    }

    @Synchronized
    @Throws(IOException::class, GeneralSecurityException::class)
    override fun publishDocument(documentId: String) {
        val id = canonicalId(documentId)
        // Re-check at the publication boundary; a previous verification is not a lock.
        verifyStagedDocument(id)
        val stagedFile = fileFor(stagingDirectory, id)
        val stagedCiphertext = readBoundedCiphertext(stagedFile)
        try {
            val stagedPayload = decryptVek(stagedCiphertext, id)
            try {
                withDirectoryLock(publishedDirectory) {
                    val destination = fileFor(publishedDirectory, id)
                    if (destination.exists()) {
                        val existingPayload = decryptVekFile(destination, id)
                        try {
                            if (!stagedPayload.contentEquals(existingPayload)) {
                                throw IOException("Published encrypted document conflicts with the staged source")
                            }
                        } finally {
                            existingPayload.fill(0)
                        }
                        return@withDirectoryLock
                    }

                    writeCiphertextIfAbsentLocked(
                        publishedDirectory,
                        destination,
                        id,
                        stagedCiphertext,
                        PUBLISH_SUFFIX
                    )
                    val publishedPayload = decryptVekFile(destination, id)
                    try {
                        if (!stagedPayload.contentEquals(publishedPayload)) {
                            throw IOException("Published encrypted document failed read-back verification")
                        }
                    } finally {
                        publishedPayload.fill(0)
                    }
                }
            } finally {
                stagedPayload.fill(0)
            }
        } finally {
            stagedCiphertext.fill(0)
        }
    }

    override fun rewrapDatabaseKey() = databaseActions.rewrapDatabaseKey()

    override fun verifyDatabaseIntegrity() = databaseActions.verifyDatabaseIntegrity()

    private fun decryptVekFile(file: File, id: String): ByteArray =
        decryptFile(file) { ciphertext -> decryptVek(ciphertext, id) }

    private fun decryptVek(ciphertext: ByteArray, id: String): ByteArray =
        vekKeyAccess.withKey { key -> envelope.decrypt(ciphertext, key, contextFor(id)) }

    private fun decryptFile(file: File, decrypt: (ByteArray) -> ByteArray): ByteArray {
        if (!file.isFile) throw IOException("Encrypted document is missing from migration storage")
        val ciphertext = readBoundedCiphertext(file)
        return try {
            decrypt(ciphertext)
        } finally {
            ciphertext.fill(0)
        }
    }

    private fun readBoundedCiphertext(file: File): ByteArray {
        if (!file.isFile) throw IOException("Encrypted document is missing from migration storage")
        val length = file.length()
        if (length < MIN_ENVELOPE_BYTES || length > MAX_ENVELOPE_BYTES) {
            throw IOException("Encrypted document has an invalid size")
        }
        return file.readBytes()
    }

    /**
     * Returns false if a valid destination already won a concurrent create. Never replaces it.
     * The caller must authenticate an existing destination before treating false as success.
     */
    private fun writeCiphertextIfAbsent(
        directory: File,
        id: String,
        ciphertext: ByteArray,
        suffix: String
    ): Boolean = withDirectoryLock(directory) {
        val destination = fileFor(directory, id)
        if (destination.exists()) return@withDirectoryLock false
        writeCiphertextIfAbsentLocked(directory, destination, id, ciphertext, suffix)
        true
    }

    private fun writeCiphertextIfAbsentLocked(
        directory: File,
        destination: File,
        id: String,
        ciphertext: ByteArray,
        suffix: String
    ) {
        val pending = File(directory, ".$id$suffix")
        if (pending.exists() && !pending.delete()) {
            throw IOException("Could not clear interrupted encrypted migration write")
        }
        try {
            FileOutputStream(pending).use { output ->
                output.write(ciphertext)
                output.fd.sync()
            }
            if (destination.exists()) {
                throw IOException("Migration destination appeared during publication")
            }
            if (!pending.renameTo(destination)) {
                throw IOException("Could not atomically publish migrated ciphertext")
            }
            syncDirectoryBestEffort(directory)
        } finally {
            if (pending.exists() && !pending.delete()) {
                // Keep the primary result if already published; a stale file contains ciphertext only.
            }
        }
    }

    private fun <T> withDirectoryLock(directory: File, block: () -> T): T {
        val lockFile = File(directory, LOCK_FILE)
        FileOutputStream(lockFile, true).use { output ->
            val lock = try {
                output.channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            } ?: throw IOException("Another migration operation holds the directory lock")
            try {
                return block()
            } finally {
                lock.release()
            }
        }
    }

    private fun syncDirectoryBestEffort(directory: File) {
        // Directory channels are not available on every supported Android API/filesystem.
        // Match the migration journal's documented fallback on API 24–25 and unsupported filesystems.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { channel ->
                channel.force(true)
            }
        } catch (_: Exception) {
            // The encrypted file contents were synced before rename; directory metadata is best-effort.
        }
    }

    private fun ensureDirectory(directory: File) {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create migration storage directory")
        }
        if (!directory.isDirectory) throw IOException("Migration storage path is not a directory")
    }

    private fun validateDistinctRoots(vararg roots: File) {
        val canonical = roots.map { it.canonicalFile.path }
        for (left in canonical.indices) {
            for (right in left + 1 until canonical.size) {
                if (pathsOverlap(canonical[left], canonical[right])) {
                    throw IllegalArgumentException(
                        "Legacy, staging, and published storage roots must be separate"
                    )
                }
            }
        }
    }

    private fun pathsOverlap(first: String, second: String): Boolean {
        val firstPrefix = if (first.endsWith(File.separator)) first else "$first${File.separator}"
        val secondPrefix = if (second.endsWith(File.separator)) second else "$second${File.separator}"
        return first == second || first.startsWith(secondPrefix) || second.startsWith(firstPrefix)
    }

    private fun fileFor(directory: File, id: String): File =
        File(directory, "${canonicalId(id)}$FILE_EXTENSION")

    private fun canonicalId(value: String): String {
        val parsed = try {
            UUID.fromString(value)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid document identifier", error)
        }
        require(parsed.toString() == value) { "Non-canonical document identifier" }
        return parsed.toString()
    }

    private fun contextFor(id: String) = id.toByteArray(Charsets.UTF_8)

    private companion object {
        const val FILE_EXTENSION = ".kgv"
        const val LOCK_FILE = ".kgv-migration.lock"
        const val STAGING_SUFFIX = ".migration-pending"
        const val PUBLISH_SUFFIX = ".publish-pending"
        const val MIN_ENVELOPE_BYTES = 4 + 1 + 12 + 16
        const val MAX_ENVELOPE_BYTES = 32 * 1024 * 1024 + MIN_ENVELOPE_BYTES
    }
}
