package com.kaagazvault.security

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultMigrationCoordinatorTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun completesAllStagesAndPersistsCompletion() = withDirectory { directory ->
        val backend = FakeBackend(listOf(id))
        val journal = VaultMigrationJournal(directory)

        val result = VaultMigrationCoordinator(journal, backend).resume()

        assertEquals(VaultMigrationPhase.COMPLETE, result.phase)
        assertEquals(listOf("stage:$id", "verify:$id", "publish:$id",
            "rewrap", "integrity"), backend.events)
        assertEquals(VaultMigrationPhase.COMPLETE, journal.load()?.phase)
    }

    @Test
    fun interruptionPersistsRecoveryAndCanResume() = withDirectory { directory ->
        val backend = FakeBackend(listOf(id)).apply { failOnceAt = "publish:$id" }
        val journal = VaultMigrationJournal(directory)

        assertThrows(IOException::class.java) {
            VaultMigrationCoordinator(journal, backend).resume()
        }
        assertEquals(VaultMigrationPhase.RECOVERY_REQUIRED, journal.load()?.phase)

        val recovered = VaultMigrationCoordinator(journal, backend).resume()
        assertEquals(VaultMigrationPhase.COMPLETE, recovered.phase)
        assertEquals(1, backend.events.count { it == "rewrap" })
    }

    @Test
    fun emptyVaultStillVerifiesDatabase() = withDirectory { directory ->
        val backend = FakeBackend(emptyList())
        val result = VaultMigrationCoordinator(
            VaultMigrationJournal(directory), backend
        ).resume()

        assertEquals(VaultMigrationPhase.COMPLETE, result.phase)
        assertEquals(listOf("rewrap", "integrity"), backend.events)
    }

    private class FakeBackend(private val ids: List<String>) : VaultMigrationBackend {
        val events = mutableListOf<String>()
        var failOnceAt: String? = null

        private fun record(event: String) {
            events += event
            if (failOnceAt == event) {
                failOnceAt = null
                throw IOException("Injected interruption at $event")
            }
        }

        override fun listLegacyDocumentIds() = ids
        override fun stageDocument(documentId: String) = record("stage:$documentId")
        override fun verifyStagedDocument(documentId: String) = record("verify:$documentId")
        override fun publishDocument(documentId: String) = record("publish:$documentId")
        override fun rewrapDatabaseKey() = record("rewrap")
        override fun verifyDatabaseIntegrity() = record("integrity")
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("kgv-migration-coordinator-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
