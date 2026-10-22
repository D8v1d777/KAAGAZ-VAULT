package com.kaagazvault.database

import android.content.Context
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
import org.junit.Assert.assertArrayEquals
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

    private companion object {
        const val KEY_BYTES = 32
        const val LEGACY_WRAPPER_FILE = "vault-database-key.wrap"
        const val VEK_WRAPPER_FILE = "vault-database-key.vek.wrap"
    }
}
