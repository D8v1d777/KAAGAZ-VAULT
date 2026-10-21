package com.kaagazvault.security

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultMigrationJournalTest {
    private val documentId = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun migrationCodesAreExplicitAndStable() {
        assertEquals(10, VaultMigrationPhase.NOT_STARTED.code)
        assertEquals(60, VaultMigrationPhase.COMPLETE.code)
        assertEquals(10, DocumentMigrationStep.PENDING.code)
        assertEquals(40, DocumentMigrationStep.PUBLISHED.code)
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

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("kgv-migration-journal-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
