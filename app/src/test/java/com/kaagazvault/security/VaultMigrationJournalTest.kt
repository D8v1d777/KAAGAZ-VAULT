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

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("kgv-migration-journal-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
