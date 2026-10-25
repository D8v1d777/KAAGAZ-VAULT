package com.kaagazvault.security

import com.kaagazvault.security.hardening.SecureDeletion
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Persists only the AES-GCM-wrapped VEK envelope in app-private no-backup storage.
 *
 * This is create-once storage, not a rotation API. It never replaces existing wrapped key
 * material. A missing or malformed envelope is a recovery condition; callers must not
 * generate a replacement VEK for a vault that may already contain protected data.
 */
internal class WrappedVaultKeyStore(private val file: File) {
    private val pending = File(file.parentFile, ".${file.name}.new")
    private val backup = File(file.parentFile, ".${file.name}.bak")

    init {
        val parent = file.parentFile ?: throw IOException("Wrapped-key path has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create wrapped-key directory")
        if (!parent.isDirectory) throw IOException("Wrapped-key parent is not a directory")
    }

    @Synchronized
    @Throws(IOException::class)
    fun loadOrNull(): ByteArray? {
        if (file.exists()) {
            validateFile(file)
            deleteArtifact(pending)
            deleteArtifact(backup)
            return file.readBytes()
        }
        if (backup.exists()) {
            validateFile(backup)
            if (!backup.renameTo(file)) throw IOException("Could not recover wrapped vault key")
            deleteArtifact(pending)
            return file.readBytes()
        }
        if (pending.exists()) {
            validateFile(pending)
            if (!pending.renameTo(file)) throw IOException("Could not recover initial wrapped-key write")
            return file.readBytes()
        }
        return null
    }

    /** Create once; existing material is never overwritten or silently rotated. */
    @Synchronized
    @Throws(IOException::class)
    fun saveIfAbsent(wrappedKey: ByteArray) {
        validateSize(wrappedKey.size)
        if (file.exists() || backup.exists() || pending.exists()) {
            // Resolve a previous interrupted first write before deciding whether setup is new.
            // Never discard a pending wrapped key: it may be the only surviving copy.
            loadOrNull()
            if (file.exists() || backup.exists()) {
                throw IOException("Wrapped vault key already exists; replacement is forbidden")
            }
        }
        FileOutputStream(pending).use { output ->
            output.write(wrappedKey)
            output.fd.sync()
        }
        if (file.exists()) {
            pending.delete()
            throw IOException("Wrapped vault key appeared during setup")
        }
        if (!pending.renameTo(file)) throw IOException("Could not publish wrapped vault key")
    }

    private fun validateFile(candidate: File) {
        if (!candidate.isFile || candidate.length() !in MIN_BYTES.toLong()..MAX_BYTES.toLong()) {
            throw IOException("Wrapped vault key is missing or malformed; recovery is required")
        }
    }

    private fun validateSize(size: Int) {
        if (size !in MIN_BYTES..MAX_BYTES) throw IOException("Wrapped vault key has an invalid size")
    }

    private fun deleteArtifact(candidate: File) {
        if (candidate.exists()) {
            // Use secure deletion for key artifacts
            if (!SecureDeletion.wipe(candidate)) {
                throw IOException("Could not securely clean wrapped-key artifact")
            }
        }
    }

    private companion object {
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        const val VEK_BYTES = 32
        const val MIN_BYTES = IV_BYTES + TAG_BYTES
        const val MAX_BYTES = IV_BYTES + TAG_BYTES + VEK_BYTES
    }
}
