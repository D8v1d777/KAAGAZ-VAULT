package com.kaagazvault.database

import android.content.Context
import com.kaagazvault.security.VaultMigrationBackend
import com.kaagazvault.security.VaultSession
import java.io.IOException

/**
 * Real SQLCipher migration collaborator: closes the currently open Room handle, create-once
 * re-wraps the same database key under the VEK, then opens SQLCipher again through the VEK wrapper
 * and executes PRAGMA integrity_check. It never deletes the legacy wrapper or rotates the DB key.
 *
 * This must run only inside the approved authenticated migration flow, with an unlocked session.
 */
internal class SqlCipherDatabaseMigrationActions(
    context: Context,
    private val activeProvider: VaultDatabaseProvider,
    private val session: VaultSession
) : VaultMigrationBackendDatabaseActions {
    private val appContext = context.applicationContext
    private val vekKeyAccess = SessionMigrationVekKeyAccess(session)

    override fun rewrapDatabaseKey() {
        activeProvider.close()
        session.withKey {
            DatabaseKeyManager(appContext, vekKeyAccess).rewrapLegacyKeyUnderVek()
        }
    }

    override fun verifyDatabaseIntegrity() {
        activeProvider.close()
        val verificationProvider = VaultDatabaseProvider(appContext, vekKeyAccess)
        try {
            session.withKey {
                val database = verificationProvider.get()
                database.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                    if (!cursor.moveToFirst() || cursor.getString(0) != "ok" || cursor.moveToNext()) {
                        throw IOException("SQLCipher integrity_check did not return exactly one ok result")
                    }
                }
            }
        } catch (failure: Exception) {
            throw IOException("Could not reopen and integrity-check SQLCipher after key rewrap", failure)
        } finally {
            verificationProvider.close()
        }
    }
}

/** Narrow collaborator interface reused by the migration adapter. */
internal interface VaultDatabaseMigrationActions : VaultMigrationBackend {
    // The document-specific methods are supplied by EncryptedDocumentMigrationBackend.
}
