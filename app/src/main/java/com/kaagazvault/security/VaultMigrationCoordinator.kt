package com.kaagazvault.security

import java.io.IOException

/**
 * Resumable orchestration for legacy-key to VEK migration.
 *
 * The backend is deliberately an adapter: this coordinator is not production-wired until a
 * caller supplies implementations for legacy discovery, ciphertext staging/publication,
 * SQLCipher rewrap, integrity verification, and legacy-material retirement.
 *
 * Every mutating backend operation MUST be idempotent. The journal checkpoint is written
 * before invoking it; the operation is repeated after process death if the post-operation
 * checkpoint was not saved. Legacy material is retired only after COMPLETE is durable.
 */
internal interface VaultMigrationBackend {
    /** Return canonical opaque UUIDs for all legacy documents, without names or payloads. */
    fun listLegacyDocumentIds(): List<String>

    /** Stage a VEK-encrypted copy without deleting or overwriting the legacy source. */
    fun stageDocument(documentId: String)

    /** Authenticate/decrypt the staged ciphertext and compare it with the source payload. */
    fun verifyStagedDocument(documentId: String)

    /** Atomically publish the verified VEK-encrypted copy while retaining legacy recovery data. */
    fun publishDocument(documentId: String)

    /** Idempotently rewrap the existing SQLCipher database key under the VEK. */
    fun rewrapDatabaseKey()

    /** Verify the database opens and required integrity checks succeed with the new key. */
    fun verifyDatabaseIntegrity()

    /** Destructive cleanup, permitted only after the journal has durably recorded COMPLETE. */
    fun retireLegacyMaterial()
}

internal class VaultMigrationCoordinator(
    private val journal: VaultMigrationJournal,
    private val backend: VaultMigrationBackend
) {
    @Synchronized
    @Throws(Exception::class)
    fun resume(): VaultMigrationState {
        var state = journal.load() ?: beginFreshMigration()
        if (state.phase == VaultMigrationPhase.COMPLETE) {
            // Repeated calls must remain safe if the process died during cleanup.
            backend.retireLegacyMaterial()
            return state
        }

        if (state.phase == VaultMigrationPhase.RECOVERY_REQUIRED) {
            state = state.resumeAfterRecovery()
            journal.save(state)
        }

        try {
            if (state.phase == VaultMigrationPhase.PREPARING) {
                state = state.beginDocumentMigration(backend.listLegacyDocumentIds())
                journal.save(state)
            }

            if (state.phase == VaultMigrationPhase.MIGRATING_DOCUMENTS) {
                state.documentSteps.keys.toList().forEach { id ->
                    var step = state.documentSteps.getValue(id)
                    if (step == DocumentMigrationStep.PENDING) {
                        // The current checkpoint is durable before this idempotent operation.
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
                throw IOException("Migration stopped in an unexpected state: ${state.phase}")
            }

            // Completion is already durable before any legacy source/key is removed.
            backend.retireLegacyMaterial()
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

    private fun beginFreshMigration(): VaultMigrationState {
        val ids = backend.listLegacyDocumentIds()
        val initial = VaultMigrationState().begin().beginDocumentMigration(ids)
        journal.save(initial)
        return initial
    }
}
