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
import java.util.UUID
import java.util.zip.CRC32

/**
 * Durable write-ahead snapshot store for [VaultMigrationState].
 *
 * The journal contains opaque document UUIDs and migration progress only: never keys,
 * document names, OCR text, plaintext, or ciphertext. Callers must serialize access and
 * must persist a transition before performing its corresponding idempotent side effect.
 *
 * Recovery is deliberately conservative. A valid primary wins. If primary is absent or
 * invalid, a valid backup is restored. A pending file is accepted only for the first write
 * (when neither primary nor backup exists); otherwise ambiguity fails closed.
 *
 * This is not an Android Keystore, database, or filesystem durability guarantee. Directory
 * metadata sync is best-effort because portable JVM APIs do not expose it on every platform.
 */
internal class VaultMigrationJournal(private val directory: File) {
    private val primary = File(directory, JOURNAL_NAME)
    private val pending = File(directory, ".$JOURNAL_NAME.new")
    private val backup = File(directory, ".$JOURNAL_NAME.bak")

    init {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Could not create migration journal directory")
        }
        if (!directory.isDirectory) throw IOException("Migration journal path is not a directory")
    }

    @Synchronized
    @Throws(IOException::class)
    fun load(): VaultMigrationState? {
        val primaryExists = primary.exists()
        val backupExists = backup.exists()
        val pendingExists = pending.exists()

        if (primaryExists) {
            val state = runCatching { decode(readBounded(primary)) }.getOrNull()
            if (state != null) {
                // A published primary is authoritative; stale transaction artifacts can go.
                deleteArtifact(pending)
                deleteArtifact(backup)
                return state
            }
        }

        if (backupExists) {
            val recovered = runCatching { decode(readBounded(backup)) }.getOrNull()
                ?: throw IOException("Migration journal and backup are invalid; recovery is required")
            if (primary.exists() && !primary.delete()) {
                throw IOException("Could not quarantine invalid migration journal")
            }
            if (!backup.renameTo(primary)) {
                throw IOException("Could not restore migration journal backup")
            }
            deleteArtifact(pending)
            syncDirectoryBestEffort()
            return recovered
        }

        if (primaryExists) {
            // A bad primary without a known-good backup is ambiguous; do not guess.
            throw IOException("Migration journal is invalid and no valid backup exists")
        }

        if (pendingExists) {
            val firstWrite = runCatching { decode(readBounded(pending)) }.getOrNull()
                ?: throw IOException("Pending migration journal is invalid; recovery is required")
            if (!pending.renameTo(primary)) {
                throw IOException("Could not recover initial migration journal write")
            }
            syncDirectoryBestEffort()
            return firstWrite
        }

        return null
    }

    @Synchronized
    @Throws(IOException::class)
    fun save(state: VaultMigrationState) {
        validate(state)
        val bytes = encode(state)
        if (bytes.size > MAX_JOURNAL_BYTES) throw IOException("Migration journal exceeds size limit")

        // Resolve an interrupted prior transaction before starting another. Never overwrite a
        // known-good backup merely because the primary path exists but contains invalid bytes.
        if (primary.exists() || backup.exists() || pending.exists()) load()
        if (pending.exists() && !pending.delete()) throw IOException("Could not clear stale pending journal")

        FileOutputStream(pending).use { output ->
            output.write(bytes)
            output.fd.sync()
        }

        // Keep a last-known-good snapshot until the new snapshot is published.
        if (primary.exists()) {
            if (backup.exists() && !backup.delete()) throw IOException("Could not prepare journal backup")
            if (!primary.renameTo(backup)) throw IOException("Could not stage migration journal backup")
            syncDirectoryBestEffort()
        }

        if (!pending.renameTo(primary)) {
            if (!primary.exists() && backup.exists()) backup.renameTo(primary)
            throw IOException("Could not publish migration journal")
        }
        syncDirectoryBestEffort()
        deleteArtifact(backup)
    }

    private fun encode(state: VaultMigrationState): ByteArray {
        val payloadBuffer = ByteArrayOutputStream()
        DataOutputStream(payloadBuffer).use { out ->
            out.writeInt(state.phase.ordinal)
            out.writeBoolean(state.databaseKeyRewrapped)
            out.writeBoolean(state.databaseIntegrityVerified)
            out.writeInt(state.recoveryPhase?.ordinal ?: NO_RECOVERY_PHASE)
            val entries = state.documentSteps.toSortedMap()
            out.writeInt(entries.size)
            entries.forEach { (id, step) ->
                writeString(out, id)
                out.writeInt(step.ordinal)
            }
        }
        val payload = payloadBuffer.toByteArray()
        val checksum = CRC32().apply { update(payload) }.value
        val result = ByteArrayOutputStream()
        DataOutputStream(result).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(FORMAT_VERSION)
            out.writeInt(payload.size)
            out.write(payload)
            out.writeLong(checksum)
        }
        return result.toByteArray()
    }

    private fun decode(bytes: ByteArray): VaultMigrationState {
        if (bytes.size < HEADER_BYTES + CHECKSUM_BYTES || bytes.size > MAX_JOURNAL_BYTES) {
            throw IOException("Invalid migration journal size")
        }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        if (input.readInt() != MAGIC) throw IOException("Invalid migration journal magic")
        if (input.readInt() != FORMAT_VERSION) throw IOException("Unsupported migration journal version")
        val payloadSize = input.readInt()
        if (payloadSize < MIN_PAYLOAD_BYTES || payloadSize != bytes.size - HEADER_BYTES - CHECKSUM_BYTES) {
            throw IOException("Invalid migration journal payload length")
        }
        val payload = ByteArray(payloadSize)
        input.readFully(payload)
        val expectedChecksum = input.readLong()
        val actualChecksum = CRC32().apply { update(payload) }.value
        if (expectedChecksum != actualChecksum || input.available() != 0) {
            throw IOException("Migration journal checksum mismatch")
        }

        val data = DataInputStream(ByteArrayInputStream(payload))
        val phase = enumValue<VaultMigrationPhase>(data.readInt(), "phase")
        val dbRewrapped = data.readBoolean()
        val dbVerified = data.readBoolean()
        val recoveryOrdinal = data.readInt()
        val recoveryPhase = if (recoveryOrdinal == NO_RECOVERY_PHASE) null
            else enumValue<VaultMigrationPhase>(recoveryOrdinal, "recovery phase")
        val count = data.readInt()
        if (count < 0 || count > MAX_DOCUMENTS) throw IOException("Invalid migration document count")
        val steps = LinkedHashMap<String, DocumentMigrationStep>(count)
        repeat(count) {
            val id = readString(data)
            val parsed = runCatching { UUID.fromString(id) }.getOrNull()
            if (parsed == null || parsed.toString() != id || steps.containsKey(id)) {
                throw IOException("Invalid or duplicate migration document identifier")
            }
            steps[id] = enumValue(data.readInt(), "document step")
        }
        if (data.available() != 0) throw IOException("Unexpected migration journal payload data")
        val state = VaultMigrationState(phase, steps, dbRewrapped, dbVerified, recoveryPhase)
        validate(state)
        return state
    }

    private fun validate(state: VaultMigrationState) {
        if (state.documentSteps.size > MAX_DOCUMENTS) throw IOException("Too many migration documents")
        state.documentSteps.forEach { (id, step) ->
            val parsed = runCatching { UUID.fromString(id) }.getOrNull()
            if (parsed == null || parsed.toString() != id || step !in DocumentMigrationStep.entries) {
                throw IOException("Invalid migration document entry")
            }
        }
        if (state.phase == VaultMigrationPhase.RECOVERY_REQUIRED) {
            if (state.recoveryPhase == null || state.recoveryPhase == VaultMigrationPhase.RECOVERY_REQUIRED ||
                state.recoveryPhase == VaultMigrationPhase.COMPLETE ||
                state.recoveryPhase == VaultMigrationPhase.NOT_STARTED) {
                throw IOException("Invalid migration recovery phase")
            }
        } else if (state.recoveryPhase != null) {
            throw IOException("Recovery phase set while migration is not paused")
        }
        val effectivePhase = if (state.phase == VaultMigrationPhase.RECOVERY_REQUIRED) {
            state.recoveryPhase ?: throw IOException("Missing recovery phase")
        } else state.phase

        if (state.databaseIntegrityVerified && (!state.databaseKeyRewrapped ||
                effectivePhase !in setOf(VaultMigrationPhase.VERIFYING, VaultMigrationPhase.COMPLETE))) {
            throw IOException("Database verification flags are inconsistent")
        }
        if (state.databaseKeyRewrapped && effectivePhase !in setOf(
                VaultMigrationPhase.MIGRATING_DATABASE_KEY,
                VaultMigrationPhase.VERIFYING,
                VaultMigrationPhase.COMPLETE
            )) {
            throw IOException("Database-key migration flag is inconsistent")
        }
        if (effectivePhase == VaultMigrationPhase.COMPLETE &&
            (state.documentSteps.values.any { it != DocumentMigrationStep.PUBLISHED } ||
                !state.databaseKeyRewrapped || !state.databaseIntegrityVerified)) {
            throw IOException("Completed migration is missing verification evidence")
        }
        if (effectivePhase in setOf(VaultMigrationPhase.MIGRATING_DATABASE_KEY, VaultMigrationPhase.VERIFYING,
                VaultMigrationPhase.COMPLETE) &&
            state.documentSteps.values.any { it != DocumentMigrationStep.PUBLISHED }) {
            throw IOException("Database migration started before documents were published")
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(ordinal: Int, label: String): T =
        enumValues<T>().getOrNull(ordinal) ?: throw IOException("Invalid migration $label")

    private fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > MAX_ID_BYTES) throw IOException("Migration identifier is too long")
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readString(input: DataInputStream): String {
        val size = input.readInt()
        if (size !in 1..MAX_ID_BYTES || size > input.available()) {
            throw IOException("Invalid migration identifier length")
        }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun readBounded(file: File): ByteArray {
        if (!file.isFile || file.length() !in (HEADER_BYTES + CHECKSUM_BYTES).toLong()..MAX_JOURNAL_BYTES.toLong()) {
            throw IOException("Invalid migration journal file")
        }
        return file.readBytes()
    }

    private fun deleteArtifact(file: File) {
        if (file.exists() && !file.delete()) throw IOException("Could not clean migration journal artifact")
    }

    private fun syncDirectoryBestEffort() {
        // Directory channels are not available on every supported Android API/filesystem.
        // Try on API 26+ and retain the documented fallback when the filesystem refuses it.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            FileChannel.open(directory.toPath(), StandardOpenOption.READ).use { channel ->
                channel.force(true)
            }
        } catch (_: Exception) {
            // File contents were already synced. Directory metadata durability remains best-effort.
        }
    }

    private companion object {
        const val JOURNAL_NAME = "vault-migration.journal"
        const val MAGIC = 0x4B47564D // KGVM
        const val FORMAT_VERSION = 1
        const val HEADER_BYTES = 12
        const val CHECKSUM_BYTES = 8
        const val MIN_PAYLOAD_BYTES = 4 + 1 + 1 + 4 + 4
        const val MAX_ID_BYTES = 64
        const val MAX_DOCUMENTS = 10_000
        const val MAX_JOURNAL_BYTES = 1_000_000
        const val NO_RECOVERY_PHASE = -1
    }
}
