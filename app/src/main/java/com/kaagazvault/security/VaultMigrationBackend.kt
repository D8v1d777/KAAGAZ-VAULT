package com.kaagazvault.security

/**
 * SPIKE: not wired into application runtime.
 *
 * A small adapter boundary for resumable migration work.
 */
internal interface VaultMigrationBackend {
    fun listLegacyDocumentIds(): List<String>
    fun stageDocument(documentId: String)
    fun verifyStagedDocument(documentId: String)
    fun publishDocument(documentId: String)
    fun rewrapDatabaseKey()
    fun verifyDatabaseIntegrity()
}
