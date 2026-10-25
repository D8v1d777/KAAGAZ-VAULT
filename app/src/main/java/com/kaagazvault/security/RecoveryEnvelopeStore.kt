package com.kaagazvault.security

import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

/**
 * Persists only the authenticated recovery envelope, never the plaintext recovery key.
 * Replacement is allowed only before the owner confirms storing the displayed key (for example,
 * after process death while the one-time key display was still unconfirmed).
 */
internal class RecoveryEnvelopeStore(
    private val file: File,
    private val setupState: VaultSetupStateStore
) {
    private val pending = File(file.parentFile, ".${file.name}.pending")
    private val backup = File(file.parentFile, ".${file.name}.backup")

    init {
        val parent = file.parentFile ?: throw IOException("Recovery envelope has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Could not create recovery-envelope directory")
        if (!parent.isDirectory) throw IOException("Recovery-envelope parent is not a directory")
    }

    @Synchronized
    fun hasArtifacts(): Boolean = file.exists() || pending.exists() || backup.exists()

    @Synchronized
    @Throws(IOException::class)
    fun loadOrNull(): ByteArray? {
        if (file.exists()) {
            val current = readEnvelope(file)
            // A valid primary is authoritative. Remove stale artifacts only after validating it.
            if (pending.exists()) pending.delete()
            if (backup.exists()) backup.delete()
            return current
        }
        if (pending.exists()) {
            val staged = readEnvelope(pending)
            if (!pending.renameTo(file)) {
                staged.fill(0)
                throw IOException("Could not recover pending recovery envelope")
            }
            syncDirectoryBestEffort()
            if (backup.exists()) backup.delete()
            return staged
        }
        if (backup.exists()) {
            val previous = readEnvelope(backup)
            if (!backup.renameTo(file)) {
                previous.fill(0)
                throw IOException("Could not restore recovery-envelope backup")
            }
            syncDirectoryBestEffort()
            return previous
        }
        return null
    }

    @Synchronized
    @Throws(IOException::class)
    fun saveBeforeConfirmation(envelope: ByteArray) {
        if (setupState.isRecoveryConfirmed()) {
            throw IOException("Recovery envelope is immutable after owner confirmation")
        }
        validateEnvelope(envelope)
        writePending(envelope)
        if (backup.exists() && !backup.delete()) throw IOException("Could not prepare recovery-envelope replacement")
        if (file.exists() && !file.renameTo(backup)) throw IOException("Could not stage previous recovery envelope")
        if (!pending.renameTo(file)) {
            if (backup.exists() && !file.exists()) backup.renameTo(file)
            throw IOException("Could not publish recovery envelope")
        }
        syncDirectoryBestEffort()
        if (backup.exists()) backup.delete()
    }

    private fun writePending(envelope: ByteArray) {
        try {
            FileOutputStream(pending).use { output ->
                output.write(envelope)
                output.fd.sync()
            }
        } catch (failure: Exception) {
            if (pending.exists()) pending.delete()
            throw IOException("Could not persist recovery envelope", failure)
        }
    }

    private fun readEnvelope(candidate: File): ByteArray {
        if (!candidate.isFile || candidate.length() != ENVELOPE_BYTES.toLong()) {
            throw IOException("Recovery envelope is missing or malformed")
        }
        return candidate.readBytes()
    }

    private fun validateEnvelope(envelope: ByteArray) {
        if (envelope.size != ENVELOPE_BYTES) throw IOException("Recovery envelope has an invalid size")
    }

    private fun syncDirectoryBestEffort() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            FileChannel.open(file.parentFile!!.toPath(), StandardOpenOption.READ).use { it.force(true) }
        } catch (_: Exception) {
            // Envelope contents are fsynced; directory metadata is best-effort on Android.
        }
    }

    private companion object {
        const val ENVELOPE_BYTES = 5 + 12 + 32 + 16
    }
}
