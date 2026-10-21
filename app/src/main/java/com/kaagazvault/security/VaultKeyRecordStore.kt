package com.kaagazvault.security

import android.os.Build
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.nio.charset.StandardCharsets

/** Persistent mapping between the active Keystore alias and its auth-bound VEK envelope. */
internal data class VaultKeyRecord(val alias: String, val wrappedVek: ByteArray)

internal class VaultKeyRecordStore(private val file: File) {
    private val pending = File(file.parentFile, ".${file.name}.pending")
    private val backup = File(file.parentFile, ".${file.name}.backup")

    init {
        val parent = file.parentFile ?: throw IOException("Vault-key record has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create vault-key record directory")
        if (!parent.isDirectory) throw IOException("Vault-key record parent is not a directory")
    }

    @Synchronized
    fun hasArtifacts(): Boolean = file.exists() || pending.exists() || backup.exists()

    @Synchronized
    @Throws(IOException::class)
    fun loadOrNull(): VaultKeyRecord? {
        if (file.exists()) {
            val record = decode(readBounded(file))
            // Valid primary is authoritative. Any pending/backup artifact is from an interrupted
            // transition and must not override the known-good committed record.
            if (pending.exists() && !pending.delete()) throw IOException("Could not clear stale vault-key pending record")
            if (backup.exists() && !backup.delete()) throw IOException("Could not clear stale vault-key backup record")
            return record
        }
        if (pending.exists()) {
            val record = decode(readBounded(pending))
            if (!pending.renameTo(file)) throw IOException("Could not recover pending vault-key record")
            syncDirectoryBestEffort()
            if (backup.exists() && !backup.delete()) throw IOException("Could not clear superseded vault-key record")
            return record
        }
        if (backup.exists()) {
            val record = decode(readBounded(backup))
            if (!backup.renameTo(file)) throw IOException("Could not restore vault-key backup record")
            syncDirectoryBestEffort()
            return record
        }
        return null
    }

    @Synchronized
    @Throws(IOException::class)
    fun saveIfAbsent(alias: String, wrappedVek: ByteArray) {
        validate(alias, wrappedVek)
        if (hasArtifacts()) throw IOException("Vault-key record already exists; replacement is forbidden")
        writePending(alias, wrappedVek)
        if (!pending.renameTo(file)) throw IOException("Could not publish vault-key record")
        syncDirectoryBestEffort()
    }

    /** Commits a recovered VEK record whether the prior record was missing or still present. */
    @Synchronized
    @Throws(IOException::class)
    fun persistRecovered(alias: String, wrappedVek: ByteArray) {
        if (hasArtifacts()) replaceAfterRecovery(alias, wrappedVek)
        else saveIfAbsent(alias, wrappedVek)
    }

    /**
     * Used only after successful recovery-key authentication has produced the existing VEK and the
     * new alias has authenticated that same VEK. Crash ordering preserves either the old record or
     * a complete new record; no VEK is generated here.
     */
    @Synchronized
    @Throws(IOException::class)
    fun replaceAfterRecovery(alias: String, wrappedVek: ByteArray) {
        validate(alias, wrappedVek)
        if (!hasArtifacts()) throw IOException("No existing vault-key record to recover")
        loadOrNull() ?: throw IOException("Vault-key record cannot be recovered")
        writePending(alias, wrappedVek)
        if (backup.exists() && !backup.delete()) throw IOException("Could not prepare vault-key record replacement")
        if (file.exists() && !file.renameTo(backup)) throw IOException("Could not stage previous vault-key record")
        if (!pending.renameTo(file)) {
            if (backup.exists() && !file.exists()) backup.renameTo(file)
            throw IOException("Could not publish recovered vault-key record")
        }
        syncDirectoryBestEffort()
        if (backup.exists() && !backup.delete()) {
            // The new record is already committed. A leftover backup is handled on next load.
        }
    }

    private fun writePending(alias: String, wrappedVek: ByteArray) {
        val encoded = encode(alias, wrappedVek)
        try {
            FileOutputStream(pending).use { output ->
                output.write(encoded)
                output.fd.sync()
            }
        } finally {
            encoded.fill(0)
        }
    }

    private fun encode(alias: String, wrappedVek: ByteArray): ByteArray {
        val aliasBytes = alias.toByteArray(StandardCharsets.UTF_8)
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(FORMAT_VERSION)
                out.writeInt(aliasBytes.size)
                out.write(aliasBytes)
                out.writeInt(wrappedVek.size)
                out.write(wrappedVek)
            }
        }.toByteArray()
    }

    private fun decode(bytes: ByteArray): VaultKeyRecord {
        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
                    throw IOException("Unsupported vault-key record format")
                }
                val aliasLength = input.readInt()
                if (aliasLength !in 1..MAX_ALIAS_BYTES || aliasLength > input.available()) {
                    throw IOException("Invalid auth-bound alias length")
                }
                val aliasBytes = ByteArray(aliasLength)
                input.readFully(aliasBytes)
                val alias = String(aliasBytes, StandardCharsets.UTF_8)
                aliasBytes.fill(0)
                val wrappedLength = input.readInt()
                if (wrappedLength != WRAPPED_VEK_BYTES || input.available() != wrappedLength) {
                    throw IOException("Invalid wrapped VEK length")
                }
                val wrapped = ByteArray(wrappedLength)
                input.readFully(wrapped)
                validate(alias, wrapped)
                return VaultKeyRecord(alias, wrapped)
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun readBounded(candidate: File): ByteArray {
        val length = candidate.length()
        if (!candidate.isFile || length !in (MIN_RECORD_BYTES.toLong())..MAX_RECORD_BYTES.toLong()) {
            throw IOException("Invalid vault-key record size")
        }
        return candidate.readBytes()
    }

    private fun validate(alias: String, wrappedVek: ByteArray) {
        val validAlias = alias == ALIAS_PREFIX.removeSuffix("_") || alias.startsWith(ALIAS_PREFIX)
        if (!validAlias || alias.length > MAX_ALIAS_BYTES) throw IOException("Invalid auth-bound key alias")
        if (wrappedVek.size != WRAPPED_VEK_BYTES) throw IOException("Invalid wrapped VEK length")
    }

    private fun syncDirectoryBestEffort() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            FileChannel.open(file.parentFile!!.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (_: Exception) {
            // File contents are fsynced; directory metadata sync is best effort on Android.
        }
    }

    private companion object {
        const val MAGIC = 0x4B475652 // KGVR
        const val FORMAT_VERSION = 1
        const val MAX_ALIAS_BYTES = 128
        const val WRAPPED_VEK_BYTES = 12 + 32 + 16
        const val MIN_RECORD_BYTES = 4 + 4 + 4 + 1 + 4 + WRAPPED_VEK_BYTES
        const val MAX_RECORD_BYTES = 512
        const val ALIAS_PREFIX = "kaagaz_vault_auth_bound_vek_wrap_v1_"
    }
}
