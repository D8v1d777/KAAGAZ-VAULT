package com.kaagazvault.database

import android.os.Build
import com.kaagazvault.security.AesGcmEnvelope
import com.kaagazvault.security.MigrationVekKeyAccess
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.security.GeneralSecurityException

/**
 * Create-once SQLCipher database-key envelope under the authenticated VEK.
 *
 * A valid primary wins. If the primary is absent, a complete authenticated pending write may be
 * promoted. Invalid material is preserved and rejected; it is never replaced with a new key.
 */
internal class VekWrappedDatabaseKeyStore(
    private val file: File,
    private val keyAccess: MigrationVekKeyAccess,
    private val envelope: AesGcmEnvelope = AesGcmEnvelope()
) {
    private val pending = File(file.parentFile, ".${file.name}.pending")

    init {
        val parent = file.parentFile ?: throw IOException("Database-key wrapper has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create database-key wrapper directory")
        if (!parent.isDirectory) throw IOException("Database-key wrapper parent is not a directory")
    }

    fun hasMaterial(): Boolean = file.exists() || pending.exists()

    @Synchronized
    @Throws(IOException::class, GeneralSecurityException::class)
    fun loadOrNull(): ByteArray? {
        if (file.exists()) {
            val key = decryptFile(file)
            if (pending.exists() && !pending.delete()) {
                key.fill(0)
                throw IOException("Could not remove stale database-key pending file")
            }
            return key
        }
        if (!pending.exists()) {
            // Even an empty-store probe is an access operation: a locked session must not
            // expose the wrapper store's state through this API.
            return keyAccess.withKey { null }
        }

        val key = decryptFile(pending)
        if (file.exists() || !pending.renameTo(file)) {
            key.fill(0)
            throw IOException("Could not recover pending database-key wrapper")
        }
        syncDirectoryBestEffort()
        return key
    }

    /** Persists once. Repeating the exact same key after an interrupted coordinator call is safe. */
    @Synchronized
    @Throws(IOException::class, GeneralSecurityException::class)
    fun saveIfAbsent(databaseKey: ByteArray) {
        require(databaseKey.size == KEY_BYTES) { "SQLCipher database key must be 256 bits" }
        if (hasMaterial()) {
            val existing = loadOrNull()
                ?: throw IOException("Database-key wrapper artifact could not be recovered")
            try {
                if (!existing.contentEquals(databaseKey)) {
                    throw IOException("Existing VEK-wrapped database key conflicts; replacement is forbidden")
                }
                return
            } finally {
                existing.fill(0)
            }
        }

        val wrapped = keyAccess.withKey { vek -> envelope.encrypt(databaseKey, vek, KEY_CONTEXT) }
        try {
            // Claim the pending path atomically before opening it. Instance-level @Synchronized
            // does not serialize multiple store instances; FileOutputStream(pending) alone would
            // truncate another writer's in-flight recovery evidence.
            if (!pending.createNewFile()) {
                throw IOException("A database-key pending write already exists; refusing to overwrite it")
            }
            FileOutputStream(pending).use { output ->
                output.write(wrapped)
                output.fd.sync()
            }
            if (file.exists()) {
                val existing = decryptFile(file)
                try {
                    if (!existing.contentEquals(databaseKey)) {
                        throw IOException("Database key was concurrently wrapped with different material")
                    }
                    if (pending.exists() && !pending.delete()) {
                        throw IOException("Could not remove duplicate pending database-key wrapper")
                    }
                    return
                } finally {
                    existing.fill(0)
                }
            }
            if (!pending.renameTo(file)) throw IOException("Could not publish VEK-wrapped database key")
            syncDirectoryBestEffort()
        } finally {
            wrapped.fill(0)
        }
    }

    private fun decryptFile(candidate: File): ByteArray {
        if (!candidate.isFile || candidate.length() != ENVELOPE_BYTES.toLong()) {
            throw IOException("VEK-wrapped database key is missing or malformed")
        }
        val wrapped = candidate.readBytes()
        val key = try {
            keyAccess.withKey { vek -> envelope.decrypt(wrapped, vek, KEY_CONTEXT) }
        } finally {
            wrapped.fill(0)
        }
        if (key.size != KEY_BYTES) {
            key.fill(0)
            throw IOException("VEK-wrapped database key has an invalid plaintext length")
        }
        return key
    }

    private fun syncDirectoryBestEffort() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            FileChannel.open(file.parentFile!!.toPath(), StandardOpenOption.READ).use { channel ->
                channel.force(true)
            }
        } catch (_: Exception) {
            // Payload bytes were fsynced. Directory metadata durability is best-effort on Android.
        }
    }

    private companion object {
        const val KEY_BYTES = 32
        const val ENVELOPE_BYTES = 4 + 1 + 12 + KEY_BYTES + 16
        val KEY_CONTEXT = "kaagaz.vault.sqlcipher-key.vek.v2".toByteArray(Charsets.UTF_8)
    }
}
