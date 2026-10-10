package com.kaagazvault.database

/**
 * Lazy provider. Call get() only from the repository's background I/O executor because
 * first access opens SQLCipher and may perform schema creation/migration.
 */
internal class EncryptedMetadataIndexProvider(
    private val databaseProvider: VaultDatabaseProvider
) {
    @Volatile private var attempted = false
    @Volatile private var cached: EncryptedMetadataIndex? = null

    @Synchronized
    fun get(): EncryptedMetadataIndex? {
        if (attempted) return cached
        attempted = true
        cached = runCatching {
            val dao = databaseProvider.get().documentMetadataDao()
            dao.getAll() // Force the first open so a wrong/corrupt key is detected.
            EncryptedMetadataIndex(dao)
        }.getOrNull()
        return cached
    }
}
