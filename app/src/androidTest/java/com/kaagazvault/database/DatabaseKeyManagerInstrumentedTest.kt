package com.kaagazvault.database

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaagazvault.security.SessionMigrationVekKeyAccess
import com.kaagazvault.security.VaultLockedException
import com.kaagazvault.security.VaultSession
import java.io.File
import java.io.IOException
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the production DatabaseKeyManager implementation using disposable cache paths and a
 * unique ephemeral Android Keystore alias. It never opens the default vault database or touches
 * the production wrapping-key alias.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseKeyManagerInstrumentedTest {
    @Test
    fun explicitRewrapPreservesDatabaseKeyAndLegacyWrapperAndSessionLockFailsClosed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "database-key-manager-${UUID.randomUUID()}")
        val storage = File(fixture, "key-storage")
        assertTrue("Could not create isolated key-manager fixture", storage.mkdirs())
        val databaseFile = File(fixture, "synthetic-vault-metadata.db")
        val alias = "kaagaz.vault.database-key-wrap.test.${UUID.randomUUID().toString().replace("-", "")}"
        val vek = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val session = VaultSession()
        var originalDatabaseKey: ByteArray? = null
        var migratedDatabaseKey: ByteArray? = null
        var legacyWrapperBefore: ByteArray? = null
        var legacyWrapperAfter: ByteArray? = null

        try {
            val legacyManager = DatabaseKeyManager(
                context = context,
                keyStorageDirectoryOverride = storage,
                databaseFileOverride = databaseFile,
                wrappingKeyAlias = alias
            )
            originalDatabaseKey = legacyManager.getOrCreateKey()
            val legacyWrapper = File(storage, LEGACY_WRAPPER_FILE)
            assertTrue("Legacy wrapper should be created only inside the disposable fixture", legacyWrapper.isFile)
            legacyWrapperBefore = legacyWrapper.readBytes()

            session.installVerifiedKey(vek)
            val sessionManager = DatabaseKeyManager(
                context = context,
                vekKeyAccess = SessionMigrationVekKeyAccess(session),
                keyStorageDirectoryOverride = storage,
                databaseFileOverride = databaseFile,
                wrappingKeyAlias = alias
            )
            sessionManager.rewrapLegacyKeyUnderVek()

            assertTrue("Explicit rewrap must create the VEK wrapper", File(storage, VEK_WRAPPER_FILE).isFile)
            legacyWrapperAfter = legacyWrapper.readBytes()
            assertArrayEquals("Rewrap must preserve legacy wrapper bytes", legacyWrapperBefore, legacyWrapperAfter)

            migratedDatabaseKey = sessionManager.getOrCreateKey()
            assertArrayEquals("VEK-wrapped key must be the same SQLCipher key", originalDatabaseKey, migratedDatabaseKey)

            session.lock()
            assertThrows("A locked session must not expose the VEK-wrapped database key", VaultLockedException::class.java) {
                sessionManager.getOrCreateKey()
            }

            val restartedSession = VaultSession()
            restartedSession.installVerifiedKey(vek)
            try {
                val restartedManager = DatabaseKeyManager(
                    context = context,
                    vekKeyAccess = SessionMigrationVekKeyAccess(restartedSession),
                    keyStorageDirectoryOverride = storage,
                    databaseFileOverride = databaseFile,
                    wrappingKeyAlias = alias
                )
                val reopenedKey = restartedManager.getOrCreateKey()
                try {
                    assertArrayEquals("A fresh authenticated session must recover the same key", originalDatabaseKey, reopenedKey)
                } finally {
                    reopenedKey.fill(0)
                }
            } finally {
                restartedSession.lock()
            }
        } finally {
            originalDatabaseKey?.fill(0)
            migratedDatabaseKey?.fill(0)
            legacyWrapperBefore?.fill(0)
            legacyWrapperAfter?.fill(0)
            vek.fill(0)
            try { session.lock() } catch (_: Exception) { }
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            } finally {
                fixture.deleteRecursively()
            }
        }
    }

    @Test
    fun productionKeyManagerRewrapReopensRealSqlCipherDatabaseWithoutChangingRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "database-key-manager-sqlcipher-" + UUID.randomUUID())
        val storage = File(fixture, "key-storage")
        assertTrue("Could not create isolated SQLCipher key-manager fixture", storage.mkdirs())
        val databaseFile = File(fixture, "synthetic-vault-metadata.db")
        val alias = "kaagaz.vault.database-key-wrap.test." + UUID.randomUUID().toString().replace("-", "")
        val vek = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val session = VaultSession()
        var originalKey: ByteArray? = null
        var migratedKey: ByteArray? = null
        var legacyWrapperBefore: ByteArray? = null
        var legacyWrapperAfter: ByteArray? = null

        try {
            System.loadLibrary("sqlcipher")
            Logger.setTarget(NoopTarget())
            val legacyManager = DatabaseKeyManager(
                context = context,
                keyStorageDirectoryOverride = storage,
                databaseFileOverride = databaseFile,
                wrappingKeyAlias = alias
            )
            originalKey = legacyManager.getOrCreateKey()
            val legacyWrapper = File(storage, LEGACY_WRAPPER_FILE)
            legacyWrapperBefore = legacyWrapper.readBytes()

            val expectedRow = DocumentMetadataEntity(
                id = "550e8400-e29b-41d4-a716-446655440099",
                displayName = "synthetic-key-manager-integration.pdf",
                mimeType = "application/pdf",
                byteSize = 4321L,
                updatedAtEpochMillis = 1_791_234_567_899L,
                normalizedSearchText = "synthetic key manager integration",
                ocrConfidence = 91,
                ocrReviewed = true,
                ocrTruncated = false
            )
            val initial = Room.databaseBuilder(
                context,
                VaultDatabase::class.java,
                databaseFile.absolutePath
            ).openHelperFactory(SupportOpenHelperFactory(originalKey)).build()
            try {
                initial.documentMetadataDao().upsert(expectedRow)
                assertEquals(listOf(expectedRow), initial.documentMetadataDao().getAll())
            } finally {
                initial.close()
            }

            session.installVerifiedKey(vek)
            val migratedManager = DatabaseKeyManager(
                context = context,
                vekKeyAccess = SessionMigrationVekKeyAccess(session),
                keyStorageDirectoryOverride = storage,
                databaseFileOverride = databaseFile,
                wrappingKeyAlias = alias
            )
            migratedManager.rewrapLegacyKeyUnderVek()
            migratedKey = migratedManager.getOrCreateKey()
            assertArrayEquals("Production key manager must preserve the SQLCipher database key", originalKey, migratedKey)

            legacyWrapperAfter = legacyWrapper.readBytes()
            assertArrayEquals("Rewrap must not modify or delete legacy wrapper bytes", legacyWrapperBefore, legacyWrapperAfter)

            val reopened = Room.databaseBuilder(
                context,
                VaultDatabase::class.java,
                databaseFile.absolutePath
            ).openHelperFactory(SupportOpenHelperFactory(migratedKey)).build()
            try {
                assertEquals(
                    "Real SQLCipher rows must survive production key-manager rewrap and reopen",
                    listOf(expectedRow),
                    reopened.documentMetadataDao().getAll()
                )
                reopened.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                    assertTrue("SQLCipher integrity_check returned no row", cursor.moveToFirst())
                    assertEquals("SQLCipher integrity_check failed", "ok", cursor.getString(0))
                    assertFalse("SQLCipher integrity_check returned extra rows", cursor.moveToNext())
                }
            } finally {
                reopened.close()
            }

            session.lock()
            assertThrows("Locked session must fail closed after rewrap", VaultLockedException::class.java) {
                migratedManager.getOrCreateKey()
            }
            assertTrue("Legacy wrapper must remain available for recovery", legacyWrapper.isFile)
        } finally {
            originalKey?.fill(0)
            migratedKey?.fill(0)
            legacyWrapperBefore?.fill(0)
            legacyWrapperAfter?.fill(0)
            vek.fill(0)
            try { session.lock() } catch (_: Exception) { }
            try {
                val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            } finally {
                check(!fixture.exists() || fixture.deleteRecursively()) {
                    "Disposable SQLCipher key-manager fixture cleanup failed"
                }
            }
        }
    }

    @Test
    fun missingVekWrapperRejectsLegacyFallbackWithoutChangingLegacyBytes() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "database-key-manager-fail-closed-${UUID.randomUUID()}")
        val storage = File(fixture, "key-storage")
        assertTrue("Could not create isolated key-manager fixture", storage.mkdirs())
        val databaseFile = File(fixture, "synthetic-vault-metadata.db")
        val alias = "kaagaz.vault.database-key-wrap.test.${UUID.randomUUID().toString().replace("-", "")}"
        val vek = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val session = VaultSession()
        var legacyWrapperBefore: ByteArray? = null
        var legacyWrapperAfter: ByteArray? = null

        try {
            val legacyManager = DatabaseKeyManager(
                context = context,
                keyStorageDirectoryOverride = storage,
                databaseFileOverride = databaseFile,
                wrappingKeyAlias = alias
            )
            val key = legacyManager.getOrCreateKey()
            key.fill(0)
            val legacyWrapper = File(storage, LEGACY_WRAPPER_FILE)
            legacyWrapperBefore = legacyWrapper.readBytes()

            session.installVerifiedKey(vek)
            val authenticatedManager = DatabaseKeyManager(
                context = context,
                vekKeyAccess = SessionMigrationVekKeyAccess(session),
                keyStorageDirectoryOverride = storage,
                databaseFileOverride = databaseFile,
                wrappingKeyAlias = alias
            )
            val failure = assertThrows(IOException::class.java) {
                authenticatedManager.getOrCreateKey()
            }
            assertTrue(
                "Missing VEK wrapper must be reported as a fail-closed condition",
                failure.message.orEmpty().contains("VEK-wrapped database key is missing", ignoreCase = true)
            )
            legacyWrapperAfter = legacyWrapper.readBytes()
            assertArrayEquals("Failed fallback must not alter legacy wrapper bytes", legacyWrapperBefore, legacyWrapperAfter)
            assertTrue("No VEK wrapper should be silently generated during ordinary access", !File(storage, VEK_WRAPPER_FILE).exists())
        } finally {
            legacyWrapperBefore?.fill(0)
            legacyWrapperAfter?.fill(0)
            vek.fill(0)
            try { session.lock() } catch (_: Exception) { }
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            } finally {
                fixture.deleteRecursively()
            }
        }
    }

    @Test
    fun productionKeystoreWrappingRoundTripRejectsTamperedEnvelope() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File(context.cacheDir, "database-key-manager-tamper-${UUID.randomUUID()}")
        val storage = File(fixture, "key-storage")
        assertTrue("Could not create isolated key-manager fixture", storage.mkdirs())
        val databaseFile = File(fixture, "synthetic-vault-metadata.db")
        val alias = "kaagaz.vault.database-key-wrap.test.${UUID.randomUUID().toString().replace("-", "")}"

        try {
            val manager = DatabaseKeyManager(
                context = context,
                keyStorageDirectoryOverride = storage,
                databaseFileOverride = databaseFile,
                wrappingKeyAlias = alias
            )
            val original = manager.getOrCreateKey()
            original.fill(0)

            val wrapper = File(storage, LEGACY_WRAPPER_FILE)
            val tampered = wrapper.readBytes()
            tampered[tampered.lastIndex] = (tampered.last().toInt() xor 1).toByte()
            wrapper.writeBytes(tampered)
            tampered.fill(0)

            assertThrows(IOException::class.java) { manager.getOrCreateKey() }
        } finally {
            try {
                val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
            } finally {
                fixture.deleteRecursively()
            }
        }
    }

    private companion object {
        const val KEY_BYTES = 32
        const val LEGACY_WRAPPER_FILE = "vault-database-key.wrap"
        const val VEK_WRAPPER_FILE = "vault-database-key.vek.wrap"
    }
}
