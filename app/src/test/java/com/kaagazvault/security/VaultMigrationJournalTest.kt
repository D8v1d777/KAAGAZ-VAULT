package com.kaagazvault.security

import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultMigrationJournalTest {
    private val documentId = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun migrationCodesAreExplicitAndStable() {
        assertEquals(listOf(10, 20, 30, 40, 50, 60, 70), VaultMigrationPhase.entries.map { it.code })
        assertEquals(listOf(10, 20, 30, 40), DocumentMigrationStep.entries.map { it.code })
    }

    @Test
    fun readsLegacyV1OrdinalSnapshot() {
        withDirectory { directory ->
            val expected = VaultMigrationState().begin()
            File(directory, "vault-migration.journal").writeBytes(encodeLegacyV1(expected))

            assertEquals(expected, VaultMigrationJournal(directory).load())
        }
    }

    @Test
    fun readsEveryLegacyV1PhaseAndDocumentStepOrdinal() {
        withDirectory { directory ->
            val base = VaultMigrationState()
            val preparing = base.begin()
            val migrating = preparing.beginDocumentMigration(listOf(documentId))
            val staged = migrating.advanceDocument(documentId, DocumentMigrationStep.STAGED)
            val verified = staged.advanceDocument(documentId, DocumentMigrationStep.VERIFIED)
            val published = verified.advanceDocument(documentId, DocumentMigrationStep.PUBLISHED)
            val databaseMigration = published.beginDatabaseKeyMigration()
            val rewrapped = databaseMigration.markDatabaseKeyRewrapped()
            val verifying = rewrapped.beginVerification()
            val integrityVerified = verifying.markDatabaseIntegrityVerified()
            val completed = integrityVerified.complete()
            val recoveryRequired = verifying.requireRecovery()

            val expectedStates = listOf(
                base,
                preparing,
                migrating,
                staged,
                verified,
                published,
                databaseMigration,
                rewrapped,
                verifying,
                completed,
                recoveryRequired
            )
            val journal = VaultMigrationJournal(directory)
            val primary = File(directory, "vault-migration.journal")

            expectedStates.forEachIndexed { index, expected ->
                primary.writeBytes(encodeLegacyV1(expected))
                assertEquals("Legacy v1 state #" + index + " (" + expected.phase + ")", expected, journal.load())
            }
        }
    }

    @Test
    fun rejectsUnknownV2PhaseAndDocumentStepCodesEvenWithValidChecksum() {
        withDirectory { directory ->
            val journal = VaultMigrationJournal(directory)
            val primary = File(directory, "vault-migration.journal")

            journal.save(VaultMigrationState().begin())
            val unknownPhase = primary.readBytes()
            ByteBuffer.wrap(unknownPhase).order(ByteOrder.BIG_ENDIAN).putInt(12, 9999)
            primary.writeBytes(withValidChecksum(unknownPhase))
            assertThrows(IOException::class.java) { journal.load() }

            primary.delete()
            journal.save(VaultMigrationState().begin().beginDocumentMigration(listOf(documentId)))
            val unknownStep = primary.readBytes()
            val payloadSize = ByteBuffer.wrap(unknownStep).order(ByteOrder.BIG_ENDIAN).getInt(8)
            ByteBuffer.wrap(unknownStep).order(ByteOrder.BIG_ENDIAN)
                .putInt(12 + payloadSize - 4, 9999)
            primary.writeBytes(withValidChecksum(unknownStep))
            assertThrows(IOException::class.java) { journal.load() }
        }
    }

    @Test
    fun roundTripsStateAndOpaqueDocumentProgress() {
        withDirectory { directory ->
            val journal = VaultMigrationJournal(directory)
            val expected = VaultMigrationState()
                .begin()
                .beginDocumentMigration(listOf(documentId))
                .advanceDocument(documentId, DocumentMigrationStep.STAGED)

            journal.save(expected)

            assertEquals(expected, journal.load())
        }
    }

    @Test
    fun firstWritePendingFileCanBeRecoveredAfterInterruptedPublish() {
        withDirectory { directory ->
            val journal = VaultMigrationJournal(directory)
            val expected = VaultMigrationState().begin()
            journal.save(expected)
            val primary = File(directory, "vault-migration.journal")
            val pending = File(directory, ".vault-migration.journal.new")
            assertEquals(true, primary.renameTo(pending))

            assertEquals(expected, journal.load())
            assertEquals(true, primary.isFile)
            assertEquals(false, pending.exists())
        }
    }

    @Test
    fun validBackupIsRestoredWhenPrimaryIsCorrupt() {
        withDirectory { directory ->
            val journal = VaultMigrationJournal(directory)
            val expected = VaultMigrationState().begin()
            journal.save(expected)
            val primary = File(directory, "vault-migration.journal")
            val backup = File(directory, ".vault-migration.journal.bak")
            assertEquals(true, primary.renameTo(backup))
            primary.writeBytes(byteArrayOf(1, 2, 3))

            assertEquals(expected, journal.load())
            assertEquals(true, primary.isFile)
            assertEquals(false, backup.exists())
        }
    }

    @Test
    fun invalidPrimaryWithoutBackupFailsClosed() {
        withDirectory { directory ->
            val primary = File(directory, "vault-migration.journal")
            primary.writeBytes(byteArrayOf(1, 2, 3))

            assertThrows(IOException::class.java) {
                VaultMigrationJournal(directory).load()
            }
        }
    }

    @Test
    fun emptyDirectoryHasNoJournal() {
        withDirectory { directory ->
            assertNull(VaultMigrationJournal(directory).load())
        }
    }

    @Test
    fun refusesImpossibleCompletedState() {
        withDirectory { directory ->
            val impossible = VaultMigrationState(phase = VaultMigrationPhase.COMPLETE)

            assertThrows(IOException::class.java) {
                VaultMigrationJournal(directory).save(impossible)
            }
        }
    }


    @Test
    fun rejectsChecksumMismatch() {
        withDirectory { directory ->
            val journal = VaultMigrationJournal(directory)
            journal.save(VaultMigrationState().begin())
            val primary = File(directory, "vault-migration.journal")
            val bytes = primary.readBytes()
            bytes[bytes.lastIndex - 1] = (bytes[bytes.lastIndex - 1].toInt() xor 0x01).toByte()
            primary.writeBytes(bytes)

            assertThrows(IOException::class.java) { journal.load() }
        }
    }

    @Test
    fun rejectsUnsupportedVersionAndTruncatedSnapshots() {
        withDirectory { directory ->
            val journal = VaultMigrationJournal(directory)
            journal.save(VaultMigrationState().begin())
            val primary = File(directory, "vault-migration.journal")
            val valid = primary.readBytes()

            val unsupported = valid.copyOf()
            unsupported[7] = (unsupported[7].toInt() + 1).toByte()
            primary.writeBytes(unsupported)
            assertThrows(IOException::class.java) { journal.load() }

            primary.writeBytes(valid.copyOf(valid.size - 2))
            assertThrows(IOException::class.java) { journal.load() }
        }
    }

    @Test
    fun rejectsOversizedJournalBeforeParsing() {
        withDirectory { directory ->
            val primary = File(directory, "vault-migration.journal")
            primary.writeBytes(ByteArray(1_000_001))

            assertThrows(IOException::class.java) { VaultMigrationJournal(directory).load() }
        }
    }


    @Test
    fun roundTripsRecoverySnapshotFromVerificationPhase() {
        withDirectory { directory ->
            val completeDocument = VaultMigrationState()
                .begin()
                .beginDocumentMigration(listOf(documentId))
                .advanceDocument(documentId, DocumentMigrationStep.STAGED)
                .advanceDocument(documentId, DocumentMigrationStep.VERIFIED)
                .advanceDocument(documentId, DocumentMigrationStep.PUBLISHED)
                .beginDatabaseKeyMigration()
                .markDatabaseKeyRewrapped()
                .beginVerification()
                .markDatabaseIntegrityVerified()
                .requireRecovery()

            val journal = VaultMigrationJournal(directory)
            journal.save(completeDocument)

            assertEquals(completeDocument, journal.load())
        }
    }

    private fun encodeLegacyV1(state: VaultMigrationState): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use { out ->
            out.writeInt(state.phase.ordinal)
            out.writeBoolean(state.databaseKeyRewrapped)
            out.writeBoolean(state.databaseIntegrityVerified)
            out.writeInt(state.recoveryPhase?.ordinal ?: -1)
            val entries = state.documentSteps.toSortedMap()
            out.writeInt(entries.size)
            entries.forEach { (id, step) ->
                val encodedId = id.toByteArray(StandardCharsets.UTF_8)
                out.writeInt(encodedId.size)
                out.write(encodedId)
                out.writeInt(step.ordinal)
            }
        }
        val payloadBytes = payload.toByteArray()
        return ByteArrayOutputStream().also { snapshot ->
            DataOutputStream(snapshot).use { out ->
                out.writeInt(0x4B47564D)
                out.writeInt(1)
                out.writeInt(payloadBytes.size)
                out.write(payloadBytes)
                out.writeLong(CRC32().apply { update(payloadBytes) }.value)
            }
        }.toByteArray()
    }

    private fun withValidChecksum(snapshot: ByteArray): ByteArray {
        val header = ByteBuffer.wrap(snapshot).order(ByteOrder.BIG_ENDIAN)
        val payloadSize = header.getInt(8)
        val checksum = CRC32().apply { update(snapshot, 12, payloadSize) }.value
        ByteBuffer.wrap(snapshot).order(ByteOrder.BIG_ENDIAN).putLong(12 + payloadSize, checksum)
        return snapshot
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("kgv-migration-journal-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
