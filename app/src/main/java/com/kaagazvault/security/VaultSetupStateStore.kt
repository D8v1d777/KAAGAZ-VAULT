package com.kaagazvault.security

import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Durable acknowledgement that the owner confirmed storing the one-time recovery key.
 * A missing marker means setup/recovery-key confirmation is not complete. A malformed marker
 * fails closed and must never be silently rewritten as "not confirmed".
 */
internal class VaultSetupStateStore(private val file: File) {
    private val pending = File(file.parentFile, ".${file.name}.pending")
    private val backup = File(file.parentFile, ".${file.name}.backup")

    init {
        val parent = file.parentFile ?: throw IOException("Setup-state marker has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create setup-state directory")
        if (!parent.isDirectory) throw IOException("Setup-state parent is not a directory")
    }

    @Synchronized
    fun hasArtifacts(): Boolean = file.exists() || pending.exists() || backup.exists()

    @Synchronized
    @Throws(IOException::class)
    fun isRecoveryConfirmed(): Boolean {
        if (!file.exists()) {
            if (pending.exists()) {
                validate(pending.readBytes())
                if (!pending.renameTo(file)) throw IOException("Could not recover setup-state marker")
                syncDirectoryBestEffort()
            } else if (backup.exists()) {
                validate(backup.readBytes())
                if (!backup.renameTo(file)) throw IOException("Could not restore setup-state marker")
                syncDirectoryBestEffort()
            } else {
                return false
            }
        }
        validate(file.readBytes())
        if (pending.exists()) pending.delete()
        if (backup.exists()) backup.delete()
        return true
    }

    @Synchronized
    @Throws(IOException::class)
    fun markRecoveryConfirmed() {
        if (isRecoveryConfirmed()) return
        val bytes = MARKER.copyOf()
        try {
            FileOutputStream(pending).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (!pending.renameTo(file)) throw IOException("Could not commit recovery-key confirmation")
            syncDirectoryBestEffort()
        } finally {
            bytes.fill(0)
        }
    }

    private fun validate(bytes: ByteArray) {
        try {
            if (!bytes.contentEquals(MARKER)) throw IOException("Setup-state marker is malformed")
        } finally {
            bytes.fill(0)
        }
    }

    private fun syncDirectoryBestEffort() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            FileChannel.open(file.parentFile!!.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (_: Exception) {
            // Marker bytes are fsynced; directory metadata sync is best-effort.
        }
    }

    private companion object {
        val MARKER = byteArrayOf(0x4b, 0x47, 0x56, 0x43, 0x01) // KGVC v1
    }
}
