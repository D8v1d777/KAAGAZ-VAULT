package com.kaagazvault.database

import com.kaagazvault.security.VaultLockedException

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
        return try {
            val index = databaseProvider.withDatabase { database ->
                val dao = database.documentMetadataDao()
                dao.getAll() // Force the first open so a wrong/corrupt key is detected.
                EncryptedMetadataIndex(dao, databaseProvider)
            }
            cached = index
            attempted = true
            index
        } catch (locked: VaultLockedException) {
            // Locked state is transient; do not cache "index unavailable" across a later unlock.
            throw locked
        } catch (_: Exception) {
            attempted = true
            cached = null
            null
        }
    }
}
