package com.kaagazvault.database

import android.content.Context
import com.kaagazvault.security.AesGcmEnvelope
import com.kaagazvault.security.AndroidKeystoreDocumentKeyProvider
import com.kaagazvault.security.MigrationVekKeyAccess
import com.kaagazvault.security.VaultLockedException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom

/**
 * Manages the SQLCipher database key's wrapping lifecycle.
 *
 * Before migration, the database key is wrapped by the legacy Android Keystore alias.
 * After migration, a create-once VEK-wrapped envelope takes precedence. If that envelope or its
 * pending first-write artifact exists, callers without the unlocked VEK must fail closed instead
 * of falling back to the legacy wrapper.
 */
internal class DatabaseKeyManager(
    context: Context,
    private val vekKeyAccess: MigrationVekKeyAccess? = null,
    keyStorageDirectoryOverride: File? = null,
    databaseFileOverride: File? = null,
    wrappingKeyAlias: String = WRAPPING_KEY_ALIAS
) {
    private val appContext = context.applicationContext
    // Internal injection seams keep integration tests isolated from the user's vault and fixed alias.
    // Production callers omit these overrides and continue to use the app's no-backup directory,
    // default database path, and stable wrapping-key alias.
    private val keyStorageDirectory = keyStorageDirectoryOverride ?: appContext.noBackupFilesDir
    private val keyFile = File(keyStorageDirectory, WRAPPED_KEY_FILE)
    private val vekKeyFile = File(keyStorageDirectory, VEK_WRAPPED_KEY_FILE)
    private val vekPendingFile = File(keyStorageDirectory, ".${VEK_WRAPPED_KEY_FILE}.pending")
    private val databaseFile = databaseFileOverride ?: appContext.getDatabasePath(DATABASE_NAME)
    private val wrapperKeyProvider = AndroidKeystoreDocumentKeyProvider(wrappingKeyAlias) {
        keyFile.exists() || databaseArtifactsExist() || vekKeyFile.exists() || vekPendingFile.exists()
    }
    private val envelope = AesGcmEnvelope()

    @Synchronized
    @Throws(IOException::class, GeneralSecurityException::class)
    fun getOrCreateKey(): ByteArray {
        if (vekKeyFile.exists() || vekPendingFile.exists()) {
            val access = vekKeyAccess ?: throw VaultLockedException()
            return VekWrappedDatabaseKeyStore(vekKeyFile, access).loadOrNull()
                ?: throw IOException("VEK-wrapped database-key artifact could not be recovered")
        }

        // A session-bound provider is used after authenticated migration. If its VEK wrapper
        // has disappeared, never fall back to the legacy Keystore wrapper: that would bypass the
        // post-migration VEK boundary. The migration collaborator uses rewrapLegacyKeyUnderVek()
        // explicitly and does not depend on this ordinary-open fallback.
        if (vekKeyAccess != null && keyFile.exists()) {
            throw IOException("VEK-wrapped database key is missing; refusing legacy-key fallback in an authenticated session")
        }

        if (keyFile.exists()) return readLegacyKey()
        if (databaseArtifactsExist()) {
            throw IOException("Database exists but its wrapped key is missing; refusing to replace the key")
        }

        val vekAccess = vekKeyAccess
        if (vekAccess != null) {
            val key = ByteArray(KEY_SIZE_BYTES).also(SecureRandom()::nextBytes)
            try {
                VekWrappedDatabaseKeyStore(vekKeyFile, vekAccess).saveIfAbsent(key)
                return key
            } catch (failure: Exception) {
                key.fill(0)
                throw failure
            }
        }
        return createLegacyKey()
    }

    /**
     * Idempotently re-wraps the existing SQLCipher key under the authenticated VEK.
     * Legacy wrapper bytes and the database file are never deleted here; retirement requires the
     * durable migration journal COMPLETE gate and separate recovery verification.
     */
    @Synchronized
    @Throws(IOException::class, GeneralSecurityException::class)
    fun rewrapLegacyKeyUnderVek() {
        val access = vekKeyAccess ?: throw VaultLockedException()
        val vekStore = VekWrappedDatabaseKeyStore(vekKeyFile, access)

        if (vekStore.hasMaterial()) {
            val vekWrappedKey = vekStore.loadOrNull()
                ?: throw IOException("VEK-wrapped database key could not be recovered")
            try {
                if (keyFile.exists()) {
                    val legacyKey = readLegacyKey()
                    try {
                        if (!legacyKey.contentEquals(vekWrappedKey)) {
                            throw IOException("Legacy and VEK-wrapped database keys conflict; recovery is required")
                        }
                    } finally {
                        legacyKey.fill(0)
                    }
                }
                return
            } finally {
                vekWrappedKey.fill(0)
            }
        }

        val databaseKey = if (keyFile.exists()) {
            readLegacyKey()
        } else {
            if (databaseArtifactsExist()) {
                throw IOException("Database exists but its legacy wrapped key is missing; refusing key replacement")
            }
            ByteArray(KEY_SIZE_BYTES).also(SecureRandom()::nextBytes)
        }
        try {
            vekStore.saveIfAbsent(databaseKey)
        } finally {
            databaseKey.fill(0)
        }
    }

    private fun readLegacyKey(): ByteArray {
        val wrapped = keyFile.readBytes()
        val unwrapped = try {
            envelope.decrypt(wrapped, wrapperKeyProvider.getOrCreateKey(), LEGACY_KEY_CONTEXT)
        } finally {
            wrapped.fill(0)
        }
        if (unwrapped.size != KEY_SIZE_BYTES) {
            unwrapped.fill(0)
            throw IOException("Wrapped database key has an invalid size")
        }
        return unwrapped
    }

    private fun createLegacyKey(): ByteArray {
        val key = ByteArray(KEY_SIZE_BYTES).also(SecureRandom()::nextBytes)
        val temporary = File(keyFile.parentFile, ".$WRAPPED_KEY_FILE.pending")
        try {
            val wrapped = envelope.encrypt(key, wrapperKeyProvider.getOrCreateKey(), LEGACY_KEY_CONTEXT)
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(wrapped)
                    output.fd.sync()
                }
                if (keyFile.exists()) throw IOException("Database key was created concurrently")
                if (!temporary.renameTo(keyFile)) throw IOException("Could not persist wrapped database key")
                return key
            } finally {
                wrapped.fill(0)
            }
        } catch (error: Exception) {
            key.fill(0)
            throw error
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun databaseArtifactsExist(): Boolean =
        databaseFile.exists() ||
            File(databaseFile.path + "-wal").exists() ||
            File(databaseFile.path + "-journal").exists() ||
            File(databaseFile.path + "-shm").exists()

    companion object {
        private const val KEY_SIZE_BYTES = 32
        private const val WRAPPED_KEY_FILE = "vault-database-key.wrap"
        private const val VEK_WRAPPED_KEY_FILE = "vault-database-key.vek.wrap"
        private const val DATABASE_NAME = "vault-metadata.db"
        private const val WRAPPING_KEY_ALIAS = "kaagaz.vault.database-key-wrap.v1"
        private val LEGACY_KEY_CONTEXT = "kaagaz.vault.sqlcipher-key.v1".toByteArray(Charsets.UTF_8)
    }
}
