package com.kaagazvault.security

/** A small adapter boundary for resumable migration work. */
internal interface VaultMigrationBackend {
    fun listLegacyDocumentIds(): List<String>
    fun stageDocument(documentId: String)
    fun verifyStagedDocument(documentId: String)
    fun publishDocument(documentId: String)
    fun rewrapDatabaseKey()
    fun verifyDatabaseIntegrity()
}
