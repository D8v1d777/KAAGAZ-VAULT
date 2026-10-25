package com.kaagazvault.security

import java.io.IOException

/**
 * Resumes migration from the last durable checkpoint.
 *
 * Backend operations must be idempotent: if the process stops after a side effect but before
 * its checkpoint is saved, the same operation will be repeated on the next invocation.
 * Legacy-data retirement is deliberately not automated until device-level recovery testing.
 */
internal class VaultMigrationCoordinator(
    private val journal: VaultMigrationJournal,
    private val backend: VaultMigrationBackend
) {
    @Synchronized
    @Throws(Exception::class)
    fun resume(): VaultMigrationState {
        var state = journal.load() ?: VaultMigrationState().begin()
            .beginDocumentMigration(backend.listLegacyDocumentIds())
            .also(journal::save)

        if (state.phase == VaultMigrationPhase.COMPLETE) return state
        if (state.phase == VaultMigrationPhase.RECOVERY_REQUIRED) {
            state = state.resumeAfterRecovery()
            journal.save(state)
        }

        try {
            if (state.phase == VaultMigrationPhase.MIGRATING_DOCUMENTS) {
                for (id in state.documentSteps.keys.toList()) {
                    var step = state.documentSteps.getValue(id)
                    if (step == DocumentMigrationStep.PENDING) {
                        journal.save(state)
                        backend.stageDocument(id)
                        backend.verifyStagedDocument(id)
                        state = state.advanceDocument(id, DocumentMigrationStep.STAGED)
                        journal.save(state)
                        step = DocumentMigrationStep.STAGED
                    }
                    if (step == DocumentMigrationStep.STAGED) {
                        journal.save(state)
                        backend.verifyStagedDocument(id)
                        state = state.advanceDocument(id, DocumentMigrationStep.VERIFIED)
                        journal.save(state)
                        step = DocumentMigrationStep.VERIFIED
                    }
                    if (step == DocumentMigrationStep.VERIFIED) {
                        journal.save(state)
                        backend.publishDocument(id)
                        state = state.advanceDocument(id, DocumentMigrationStep.PUBLISHED)
                        journal.save(state)
                    }
                }
                state = state.beginDatabaseKeyMigration()
                journal.save(state)
            }

            if (state.phase == VaultMigrationPhase.MIGRATING_DATABASE_KEY) {
                journal.save(state)
                backend.rewrapDatabaseKey()
                state = state.markDatabaseKeyRewrapped()
                journal.save(state)
                state = state.beginVerification()
                journal.save(state)
            }

            if (state.phase == VaultMigrationPhase.VERIFYING) {
                journal.save(state)
                backend.verifyDatabaseIntegrity()
                state = state.markDatabaseIntegrityVerified()
                journal.save(state)
                state = state.complete()
                journal.save(state)
            }

            if (state.phase != VaultMigrationPhase.COMPLETE) {
                throw IOException("Migration stopped in unexpected phase: ${state.phase}")
            }
            return state
        } catch (failure: Exception) {
            if (state.phase != VaultMigrationPhase.COMPLETE &&
                state.phase != VaultMigrationPhase.RECOVERY_REQUIRED
            ) {
                try {
                    state = state.requireRecovery()
                    journal.save(state)
                } catch (journalFailure: Exception) {
                    failure.addSuppressed(journalFailure)
                }
            }
            throw failure
        }
    }
}
