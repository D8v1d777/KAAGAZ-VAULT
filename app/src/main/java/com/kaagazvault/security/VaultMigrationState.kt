package com.kaagazvault.security

import java.util.UUID

/**
 * Pure, immutable migration policy model for the legacy-key to VEK migration.
 *
 * This class does not perform I/O or cryptography. Persist snapshots atomically before
 * acting on each transition; do not treat this model alone as a durable migration journal.
 */
internal enum class VaultMigrationPhase(val code: Int) {
    NOT_STARTED(10),
    PREPARING(20),
    MIGRATING_DOCUMENTS(30),
    MIGRATING_DATABASE_KEY(40),
    VERIFYING(50),
    COMPLETE(60),
    RECOVERY_REQUIRED(70);

    companion object {
        fun fromCode(code: Int): VaultMigrationPhase =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown migration phase code: $code")
    }
}

internal enum class DocumentMigrationStep(val code: Int) {
    PENDING(10),
    STAGED(20),
    VERIFIED(30),
    PUBLISHED(40);

    companion object {
        fun fromCode(code: Int): DocumentMigrationStep =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown document migration step code: $code")
    }
}

internal data class VaultMigrationState(
    val phase: VaultMigrationPhase = VaultMigrationPhase.NOT_STARTED,
    val documentSteps: Map<String, DocumentMigrationStep> = emptyMap(),
    val databaseKeyRewrapped: Boolean = false,
    val databaseIntegrityVerified: Boolean = false,
    val recoveryPhase: VaultMigrationPhase? = null
) {
    fun begin(): VaultMigrationState {
        require(phase == VaultMigrationPhase.NOT_STARTED) { "Migration has already started" }
        return copy(phase = VaultMigrationPhase.PREPARING)
    }

    fun beginDocumentMigration(documentIds: Collection<String>): VaultMigrationState {
        require(phase == VaultMigrationPhase.PREPARING) { "Migration is not prepared" }
        val canonicalIds = documentIds.map(::canonicalDocumentId)
        require(canonicalIds.size == canonicalIds.toSet().size) { "Duplicate document identifier" }
        return copy(
            phase = VaultMigrationPhase.MIGRATING_DOCUMENTS,
            documentSteps = canonicalIds.associateWith { DocumentMigrationStep.PENDING }
        )
    }

    /**
     * Steps are strictly monotonic. VERIFIED means staged ciphertext was decrypted and
     * authenticated successfully. PUBLISHED must not imply legacy data has been deleted.
     */
    fun advanceDocument(documentId: String, next: DocumentMigrationStep): VaultMigrationState {
        require(phase == VaultMigrationPhase.MIGRATING_DOCUMENTS) {
            "Documents can only advance during document migration"
        }
        val id = canonicalDocumentId(documentId)
        val current = documentSteps[id] ?: throw IllegalArgumentException("Unknown document identifier")
        require(next.ordinal == current.ordinal || next.ordinal == current.ordinal + 1) {
            "Document migration step cannot be skipped or reversed"
        }
        return copy(documentSteps = documentSteps + (id to next))
    }

    fun beginDatabaseKeyMigration(): VaultMigrationState {
        require(phase == VaultMigrationPhase.MIGRATING_DOCUMENTS) { "Document migration is not active" }
        require(documentSteps.values.all { it == DocumentMigrationStep.PUBLISHED }) {
            "Every document must be published before database-key migration"
        }
        return copy(phase = VaultMigrationPhase.MIGRATING_DATABASE_KEY)
    }

    fun markDatabaseKeyRewrapped(): VaultMigrationState {
        require(phase == VaultMigrationPhase.MIGRATING_DATABASE_KEY) {
            "Database key can only be rewrapped during its migration phase"
        }
        return copy(databaseKeyRewrapped = true)
    }

    fun beginVerification(): VaultMigrationState {
        require(phase == VaultMigrationPhase.MIGRATING_DATABASE_KEY) {
            "Database-key migration is not active"
        }
        require(databaseKeyRewrapped) { "Database key has not been rewrapped" }
        return copy(phase = VaultMigrationPhase.VERIFYING)
    }

    fun markDatabaseIntegrityVerified(): VaultMigrationState {
        require(phase == VaultMigrationPhase.VERIFYING) { "Migration is not in verification phase" }
        return copy(databaseIntegrityVerified = true)
    }

    fun complete(): VaultMigrationState {
        require(phase == VaultMigrationPhase.VERIFYING) { "Migration is not in verification phase" }
        require(documentSteps.values.all { it == DocumentMigrationStep.PUBLISHED }) {
            "Not all documents have verified published ciphertext"
        }
        require(databaseKeyRewrapped && databaseIntegrityVerified) {
            "Database key and database integrity must be verified"
        }
        return copy(phase = VaultMigrationPhase.COMPLETE)
    }

    fun requireRecovery(): VaultMigrationState {
        require(phase != VaultMigrationPhase.COMPLETE && phase != VaultMigrationPhase.RECOVERY_REQUIRED) {
            "Completed or already-paused migration cannot enter recovery again"
        }
        return copy(phase = VaultMigrationPhase.RECOVERY_REQUIRED, recoveryPhase = phase)
    }

    fun resumeAfterRecovery(): VaultMigrationState {
        require(phase == VaultMigrationPhase.RECOVERY_REQUIRED && recoveryPhase != null) {
            "No recoverable migration phase is recorded"
        }
        return copy(phase = recoveryPhase, recoveryPhase = null)
    }

    /** Legacy keys and legacy ciphertext may only be retired after durable completion. */
    fun mayRetireLegacyData(): Boolean = phase == VaultMigrationPhase.COMPLETE

    private fun canonicalDocumentId(value: String): String {
        val parsed = try {
            UUID.fromString(value)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid document identifier", error)
        }
        require(parsed.toString() == value) { "Non-canonical document identifier" }
        return parsed.toString()
    }
}
