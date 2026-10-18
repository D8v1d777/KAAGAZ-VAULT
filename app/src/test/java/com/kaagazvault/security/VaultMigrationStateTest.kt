package com.kaagazvault.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultMigrationStateTest {
    private val id = "123e4567-e89b-12d3-a456-426614174000"

    private fun readyState(ids: List<String> = listOf(id)): VaultMigrationState =
        VaultMigrationState()
            .begin()
            .beginDocumentMigration(ids)

    @Test
    fun migrationCannotSkipDocumentVerificationSteps() {
        val state = readyState()
        assertThrows(IllegalArgumentException::class.java) {
            state.advanceDocument(id, DocumentMigrationStep.VERIFIED)
        }
    }

    @Test
    fun databaseKeyCannotMoveBeforeAllDocumentsArePublished() {
        val state = readyState().advanceDocument(id, DocumentMigrationStep.STAGED)
        assertThrows(IllegalArgumentException::class.java) { state.beginDatabaseKeyMigration() }
    }

    @Test
    fun legacyDataCannotBeRetiredBeforeCompleteVerification() {
        assertFalse(readyState().mayRetireLegacyData())
    }

    @Test
    fun completeRequiresDatabaseKeyAndIntegrityVerification() {
        val state = readyState()
            .advanceDocument(id, DocumentMigrationStep.STAGED)
            .advanceDocument(id, DocumentMigrationStep.VERIFIED)
            .advanceDocument(id, DocumentMigrationStep.PUBLISHED)
            .beginDatabaseKeyMigration()
            .markDatabaseKeyRewrapped()
            .beginVerification()
        assertThrows(IllegalArgumentException::class.java) { state.complete() }
        assertTrue(state.markDatabaseIntegrityVerified().complete().mayRetireLegacyData())
    }

    @Test
    fun recoveryRemembersAndRestoresInterruptedPhase() {
        val state = readyState()
        val paused = state.requireRecovery()
        assertEquals(VaultMigrationPhase.RECOVERY_REQUIRED, paused.phase)
        assertEquals(VaultMigrationPhase.MIGRATING_DOCUMENTS, paused.resumeAfterRecovery().phase)
    }

    @Test
    fun rejectsDuplicateAndNonCanonicalDocumentIds() {
        assertThrows(IllegalArgumentException::class.java) {
            VaultMigrationState().begin().beginDocumentMigration(listOf(id, id))
        }
        assertThrows(IllegalArgumentException::class.java) {
            readyState(listOf("123E4567-E89B-12D3-A456-426614174000"))
        }
    }
}
