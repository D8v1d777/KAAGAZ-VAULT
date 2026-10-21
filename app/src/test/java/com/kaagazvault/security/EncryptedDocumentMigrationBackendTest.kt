package com.kaagazvault.security

import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedDocumentMigrationBackendTest {
    @Test
    fun stagesUnderSameIdAndPreservesLegacyCiphertext() = withFixture { fixture ->
        val originalLegacyCiphertext = fixture.legacyFile.readBytes()

        assertEquals(listOf(fixture.id), fixture.backend.listLegacyDocumentIds())
        fixture.backend.stageDocument(fixture.id)
        fixture.backend.verifyStagedDocument(fixture.id)

        assertArrayEquals(originalLegacyCiphertext, fixture.legacyFile.readBytes())
        assertTrue(fixture.stagedFile.isFile)
        assertFalse(originalLegacyCiphertext.contentEquals(fixture.stagedFile.readBytes()))
        val stagedPlaintext = fixture.decryptVekFile(fixture.stagedFile)
        try {
            assertArrayEquals(fixture.payload, stagedPlaintext)
        } finally {
            stagedPlaintext.fill(0)
        }
    }

    @Test
    fun repeatedStageDoesNotReplaceAnAlreadyAuthenticatedCopy() = withFixture { fixture ->
        fixture.backend.stageDocument(fixture.id)
        val stagedBeforeRetry = fixture.stagedFile.readBytes()

        fixture.backend.stageDocument(fixture.id)

        assertArrayEquals(stagedBeforeRetry, fixture.stagedFile.readBytes())
        fixture.backend.verifyStagedDocument(fixture.id)
    }

    @Test
    fun alteredStagedCiphertextFailsVerificationAndLegacyCopySurvives() = withFixture { fixture ->
        val legacyBefore = fixture.legacyFile.readBytes()
        fixture.backend.stageDocument(fixture.id)
        val altered = fixture.stagedFile.readBytes()
        altered[altered.lastIndex] = (altered.last().toInt() xor 1).toByte()
        fixture.stagedFile.writeBytes(altered)

        assertThrows(EncryptedDocumentAuthenticationException::class.java) {
            fixture.backend.verifyStagedDocument(fixture.id)
        }
        assertArrayEquals(legacyBefore, fixture.legacyFile.readBytes())
    }

    @Test
    fun publicationKeepsBothLegacyAndStagedCopies() = withFixture { fixture ->
        val legacyBefore = fixture.legacyFile.readBytes()
        fixture.backend.stageDocument(fixture.id)
        fixture.backend.verifyStagedDocument(fixture.id)

        fixture.backend.publishDocument(fixture.id)

        assertTrue(fixture.publishedFile.isFile)
        assertTrue(fixture.stagedFile.isFile)
        assertArrayEquals(legacyBefore, fixture.legacyFile.readBytes())
        assertArrayEquals(fixture.stagedFile.readBytes(), fixture.publishedFile.readBytes())
        val publishedPlaintext = fixture.decryptVekFile(fixture.publishedFile)
        try {
            assertArrayEquals(fixture.payload, publishedPlaintext)
        } finally {
            publishedPlaintext.fill(0)
        }
    }

    @Test
    fun existingPublishedCiphertextIsNeverSilentlyReplaced() = withFixture { fixture ->
        fixture.backend.stageDocument(fixture.id)
        val corruptedExisting = byteArrayOf(0x4b, 0x47, 0x56)
        fixture.publishedFile.writeBytes(corruptedExisting)

        assertThrows(IOException::class.java) {
            fixture.backend.publishDocument(fixture.id)
        }
        assertArrayEquals(corruptedExisting, fixture.publishedFile.readBytes())
        assertTrue(fixture.legacyFile.isFile)
        assertTrue(fixture.stagedFile.isFile)
    }

    @Test
    fun coordinatorResumesAfterEachBackendSideEffectCrashPoint() = withFixture { fixture ->
        val crashPoints = listOf(
            "stage:${fixture.id}#1",
            // CO-08 leaves one coordinator-owned verification transition after staging.
            "verify:${fixture.id}#1",
            "publish:${fixture.id}#1",
            "rewrap#1",
            "integrity#1"
        )

        for (crashPoint in crashPoints) {
            val caseRoot = File(fixture.root, crashPoint.replace(':', '_').replace('#', '_'))
            assertTrue(caseRoot.mkdirs())
            val caseStage = File(caseRoot, "stage")
            val casePublished = File(caseRoot, "published")
            val caseJournalDirectory = File(caseRoot, "journal")
            val caseDatabase = FakeDatabaseMigrationActions()
            val caseBackend = EncryptedDocumentMigrationBackend(
                legacyDirectory = fixture.legacyDirectory,
                legacyKey = fixture.legacyKey,
                stagingDirectory = caseStage,
                publishedDirectory = casePublished,
                vekKeyAccess = FixedVekKeyAccess(fixture.vekKey),
                databaseActions = caseDatabase
            )
            val journal = VaultMigrationJournal(caseJournalDirectory)
            val failingBackend = CrashAfterSideEffectBackend(caseBackend, crashPoint)

            assertThrows(IOException::class.java) {
                VaultMigrationCoordinator(journal, failingBackend).resume()
            }
            assertEquals(VaultMigrationPhase.RECOVERY_REQUIRED, journal.load()?.phase)

            val resumed = VaultMigrationCoordinator(journal, caseBackend).resume()

            assertEquals("failed recovery case: $crashPoint", VaultMigrationPhase.COMPLETE, resumed.phase)
            assertArrayEquals(fixture.payload, fixture.legacyStore.read(fixture.id))
            assertTrue(File(caseStage, "${fixture.id}.kgv").isFile)
            assertTrue(File(casePublished, "${fixture.id}.kgv").isFile)
            assertTrue(caseDatabase.wasRewrapped)
            assertTrue(caseDatabase.integrityWasVerified)
        }
    }

    @Test
    fun refusesOverlappingStorageRoots() = withFixture { fixture ->
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedDocumentMigrationBackend(
                legacyDirectory = fixture.legacyDirectory,
                legacyKey = fixture.legacyKey,
                stagingDirectory = fixture.stagingDirectory,
                publishedDirectory = fixture.legacyDirectory,
                vekKeyAccess = FixedVekKeyAccess(fixture.vekKey),
                databaseActions = FakeDatabaseMigrationActions()
            )
        }
    }

    private class Fixture(val root: File) {
        val legacyDirectory = File(root, "legacy").apply { mkdirs() }
        val stagingDirectory = File(root, "staging")
        val publishedDirectory = File(root, "published")
        val legacyKey = newKey()
        val vekKey = newKey()
        val payload = "synthetic migration payload".toByteArray()
        val legacyStore = EncryptedDocumentStore(legacyDirectory, DocumentKeyProvider { legacyKey })
        val id: String = legacyStore.save(payload)
        val legacyFile = File(legacyDirectory, "$id.kgv")
        val stagedFile = File(stagingDirectory, "$id.kgv")
        val publishedFile = File(publishedDirectory, "$id.kgv")
        val backend = EncryptedDocumentMigrationBackend(
            legacyDirectory = legacyDirectory,
            legacyKey = legacyKey,
            stagingDirectory = stagingDirectory,
            publishedDirectory = publishedDirectory,
            vekKeyAccess = FixedVekKeyAccess(vekKey),
            databaseActions = FakeDatabaseMigrationActions()
        )

        fun decryptVekFile(file: File): ByteArray {
            return AesGcmEnvelope().decrypt(file.readBytes(), vekKey, id.toByteArray(Charsets.UTF_8))
        }
    }

    private class FixedVekKeyAccess(private val key: SecretKey) : MigrationVekKeyAccess {
        override fun <T> withKey(block: (SecretKey) -> T): T = block(key)
    }

    private class FakeDatabaseMigrationActions : VaultDatabaseMigrationActions {
        var wasRewrapped = false
        var integrityWasVerified = false

        override fun rewrapDatabaseKey() {
            wasRewrapped = true
        }

        override fun verifyDatabaseIntegrity() {
            check(wasRewrapped) { "Database key must be rewrapped before integrity verification" }
            integrityWasVerified = true
        }
    }

    private class CrashAfterSideEffectBackend(
        private val delegate: VaultMigrationBackend,
        private val crashPoint: String
    ) : VaultMigrationBackend {
        private val invocationCounts = mutableMapOf<String, Int>()
        private var injected = false

        private fun failAfter(operation: String) {
            val count = (invocationCounts[operation] ?: 0) + 1
            invocationCounts[operation] = count
            if (!injected && "$operation#$count" == crashPoint) {
                injected = true
                throw IOException("Injected interruption after $operation")
            }
        }

        override fun listLegacyDocumentIds(): List<String> = delegate.listLegacyDocumentIds()

        override fun stageDocument(documentId: String) {
            delegate.stageDocument(documentId)
            failAfter("stage:$documentId")
        }

        override fun verifyStagedDocument(documentId: String) {
            delegate.verifyStagedDocument(documentId)
            failAfter("verify:$documentId")
        }

        override fun publishDocument(documentId: String) {
            delegate.publishDocument(documentId)
            failAfter("publish:$documentId")
        }

        override fun rewrapDatabaseKey() {
            delegate.rewrapDatabaseKey()
            failAfter("rewrap")
        }

        override fun verifyDatabaseIntegrity() {
            delegate.verifyDatabaseIntegrity()
            failAfter("integrity")
        }
    }

    private fun withFixture(block: (Fixture) -> Unit) {
        val root = Files.createTempDirectory("kgv-document-migration-test").toFile()
        try {
            block(Fixture(root))
        } finally {
            root.deleteRecursively()
        }
    }

    private companion object {
        fun newKey(): SecretKey =
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }
}
