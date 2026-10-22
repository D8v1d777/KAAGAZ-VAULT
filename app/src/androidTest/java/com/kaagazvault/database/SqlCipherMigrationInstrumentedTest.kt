package com.kaagazvault.database

import android.content.Context
import android.os.Build
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kaagazvault.security.AesGcmEnvelope
import com.kaagazvault.security.MigrationVekKeyAccess
import com.kaagazvault.security.VaultMigrationBackend
import com.kaagazvault.security.VaultMigrationCoordinator
import com.kaagazvault.security.VaultMigrationJournal
import com.kaagazvault.security.VaultMigrationPhase
import com.kaagazvault.security.VaultSession
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKey
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uses only disposable cache files and synthetic in-memory wrapping keys. It deliberately does not
 * invoke DatabaseKeyManager or touch the application's Android Keystore aliases/default DB paths.
 * The production VEK-wrapped store, migration journal/coordinator, Room schema, and SQLCipher engine
 * are exercised together; the legacy wrapper collaborator itself is a synthetic test fixture.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherMigrationInstrumentedTest {
    @Test
    fun sqlCipherRowsSurviveKeyRewrapAndIntegrityBeforeDurableComplete() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hardware = Build.HARDWARE.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        assumeTrue(
            "Run this SQLCipher instrumentation test on an Android emulator",
            hardware in EMULATOR_HARDWARE || fingerprint.contains("generic")
        )

        val fixtureRoot = File(context.cacheDir, "sqlcipher-migration-test-${UUID.randomUUID()}")
        assertTrue("Could not create unique synthetic SQLCipher fixture", fixtureRoot.mkdirs())
        val databaseFile = File(fixtureRoot, DATABASE_NAME)
        val legacyWrapper = File(fixtureRoot, "legacy-db-key.synthetic-wrap")
        val vekWrapper = File(fixtureRoot, "vault-database-key.vek.wrap")
        val journalDirectory = File(fixtureRoot, "journal")

        val databaseKey = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val syntheticLegacyWrappingKey = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val vek = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val session = VaultSession()
        var legacyWrapperBefore: ByteArray? = null

        try {
            val envelope = AesGcmEnvelope()
            val syntheticLegacyEnvelope = envelope.encrypt(
                databaseKey,
                ByteArraySecretKey(syntheticLegacyWrappingKey),
                LEGACY_KEY_CONTEXT
            )
            try {
                legacyWrapper.writeBytes(syntheticLegacyEnvelope)
                legacyWrapperBefore = legacyWrapper.readBytes()
            } finally {
                syntheticLegacyEnvelope.fill(0)
            }

            val expectedRow = DocumentMetadataEntity(
                id = "550e8400-e29b-41d4-a716-446655440000",
                displayName = "synthetic-migration-fixture.pdf",
                mimeType = "application/pdf",
                byteSize = 1234L,
                updatedAtEpochMillis = 1_791_234_567_890L,
                normalizedSearchText = "synthetic migration fixture",
                ocrConfidence = 98,
                ocrReviewed = true,
                ocrTruncated = false
            )
            val initialDatabase = openSqlCipherDatabase(context, databaseFile, databaseKey)
            try {
                initialDatabase.documentMetadataDao().upsert(expectedRow)
                assertEquals(listOf(expectedRow), initialDatabase.documentMetadataDao().getAll())
            } finally {
                initialDatabase.close()
            }

            session.installVerifiedKey(vek)
            val vekKeyAccess = SessionVekKeyAccess(session)
            val vekKeyStore = VekWrappedDatabaseKeyStore(vekWrapper, vekKeyAccess)
            var failOnceAfterDatabaseRewrap = true
            val backend = object : VaultMigrationBackend {
                override fun listLegacyDocumentIds(): List<String> = emptyList()

                override fun stageDocument(documentId: String) {
                    error("This isolated SQLCipher fixture contains no documents")
                }

                override fun verifyStagedDocument(documentId: String) {
                    error("This isolated SQLCipher fixture contains no documents")
                }

                override fun publishDocument(documentId: String) {
                    error("This isolated SQLCipher fixture contains no documents")
                }

                override fun rewrapDatabaseKey() {
                    val encodedLegacyWrapper = legacyWrapper.readBytes()
                    val unwrappedDatabaseKey = try {
                        envelope.decrypt(
                            encodedLegacyWrapper,
                            ByteArraySecretKey(syntheticLegacyWrappingKey),
                            LEGACY_KEY_CONTEXT
                        )
                    } finally {
                        encodedLegacyWrapper.fill(0)
                    }
                    try {
                        vekKeyStore.saveIfAbsent(unwrappedDatabaseKey)
                    } finally {
                        unwrappedDatabaseKey.fill(0)
                    }
                }

                override fun verifyDatabaseIntegrity() {
                    if (failOnceAfterDatabaseRewrap) {
                        failOnceAfterDatabaseRewrap = false
                        throw IOException("synthetic interruption after VEK database-key rewrap")
                    }
                    val unwrappedDatabaseKey = vekKeyStore.loadOrNull()
                        ?: throw IOException("Synthetic VEK database-key wrapper could not be reopened")
                    try {
                        val reopenedDatabase = openSqlCipherDatabase(context, databaseFile, unwrappedDatabaseKey)
                        try {
                            assertEquals(
                                "Existing SQLCipher rows must survive close, rewrap, and reopen",
                                listOf(expectedRow),
                                reopenedDatabase.documentMetadataDao().getAll()
                            )
                            reopenedDatabase.openHelper.writableDatabase
                                .query("PRAGMA integrity_check").use { cursor ->
                                    assertTrue("PRAGMA integrity_check returned no row", cursor.moveToFirst())
                                    assertEquals("SQLCipher integrity check failed", "ok", cursor.getString(0))
                                    assertFalse(
                                        "PRAGMA integrity_check returned unexpected extra rows",
                                        cursor.moveToNext()
                                    )
                                }
                        } finally {
                            reopenedDatabase.close()
                        }
                    } finally {
                        unwrappedDatabaseKey.fill(0)
                    }
                }
            }

            val journal = VaultMigrationJournal(journalDirectory)
            org.junit.Assert.assertThrows(IOException::class.java) {
                VaultMigrationCoordinator(journal, backend).resume()
            }

            val interruptedState = VaultMigrationJournal(journalDirectory).load()
            assertEquals(
                "Failed integrity verification must durably record RECOVERY_REQUIRED",
                VaultMigrationPhase.RECOVERY_REQUIRED,
                interruptedState?.phase
            )
            assertTrue("VEK-wrapped database key must survive the interruption", vekWrapper.isFile)
            assertArrayEquals(
                "The legacy wrapper must remain byte-identical during recovery",
                legacyWrapperBefore!!,
                legacyWrapper.readBytes()
            )

            // A fresh journal/coordinator instance models process restart from the durable snapshot.
            val completed = VaultMigrationCoordinator(
                VaultMigrationJournal(journalDirectory),
                backend
            ).resume()

            assertEquals(
                "Journal must reach COMPLETE only after resumed real SQLCipher integrity succeeds",
                VaultMigrationPhase.COMPLETE,
                completed.phase
            )
            assertEquals(
                "The COMPLETE checkpoint must remain readable from disk",
                completed,
                VaultMigrationJournal(journalDirectory).load()
            )
            assertTrue("The VEK-wrapped database-key envelope should exist", vekWrapper.isFile)
            assertArrayEquals(
                "Legacy wrapper bytes must be preserved through durable COMPLETE",
                legacyWrapperBefore!!,
                legacyWrapper.readBytes()
            )
        } finally {
            try {
                session.lock()
            } finally {
                databaseKey.fill(0)
                syntheticLegacyWrappingKey.fill(0)
                vek.fill(0)
                legacyWrapperBefore?.fill(0)
                check(!fixtureRoot.exists() || fixtureRoot.deleteRecursively()) {
                    "Synthetic SQLCipher fixture cleanup failed"
                }
            }
        }
    }

    @Test
    fun sqlCipherRewrapSideEffectBeforeJournalCheckpointIsIdempotentOnResume() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hardware = Build.HARDWARE.lowercase()
        val fingerprint = Build.FINGERPRINT.lowercase()
        assumeTrue(
            "Run this SQLCipher instrumentation test on an Android emulator",
            hardware in EMULATOR_HARDWARE || fingerprint.contains("generic")
        )

        val fixtureRoot = File(context.cacheDir, "sqlcipher-rewrap-crash-${UUID.randomUUID()}")
        assertTrue("Could not create unique synthetic SQLCipher fixture", fixtureRoot.mkdirs())
        val databaseFile = File(fixtureRoot, DATABASE_NAME)
        val legacyWrapper = File(fixtureRoot, "legacy-db-key.synthetic-wrap")
        val vekWrapper = File(fixtureRoot, "vault-database-key.vek.wrap")
        val journalDirectory = File(fixtureRoot, "journal")
        val databaseKey = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val legacyWrappingKey = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val vek = ByteArray(KEY_BYTES).also(SecureRandom()::nextBytes)
        val session = VaultSession()
        var legacyWrapperBefore: ByteArray? = null

        try {
            val envelope = AesGcmEnvelope()
            val legacyEnvelope = envelope.encrypt(
                databaseKey,
                ByteArraySecretKey(legacyWrappingKey),
                LEGACY_KEY_CONTEXT
            )
            try {
                legacyWrapper.writeBytes(legacyEnvelope)
                legacyWrapperBefore = legacyWrapper.readBytes()
            } finally {
                legacyEnvelope.fill(0)
            }

            val expectedRow = DocumentMetadataEntity(
                id = "550e8400-e29b-41d4-a716-446655440001",
                displayName = "synthetic-rewrap-crash.pdf",
                mimeType = "application/pdf",
                byteSize = 567L,
                updatedAtEpochMillis = 1_791_234_567_891L,
                normalizedSearchText = "synthetic rewrap crash",
                ocrConfidence = 87,
                ocrReviewed = false,
                ocrTruncated = false
            )
            val initialDatabase = openSqlCipherDatabase(context, databaseFile, databaseKey)
            try {
                initialDatabase.documentMetadataDao().upsert(expectedRow)
                assertEquals(listOf(expectedRow), initialDatabase.documentMetadataDao().getAll())
            } finally {
                initialDatabase.close()
            }

            session.installVerifiedKey(vek)
            val vekKeyStore = VekWrappedDatabaseKeyStore(
                vekWrapper,
                SessionVekKeyAccess(session)
            )
            var failOnceAfterRewrap = true
            var rewrapCalls = 0
            val backend = object : VaultMigrationBackend {
                override fun listLegacyDocumentIds(): List<String> = emptyList()
                override fun stageDocument(documentId: String) {
                    error("This isolated SQLCipher fixture contains no documents")
                }
                override fun verifyStagedDocument(documentId: String) {
                    error("This isolated SQLCipher fixture contains no documents")
                }
                override fun publishDocument(documentId: String) {
                    error("This isolated SQLCipher fixture contains no documents")
                }
                override fun rewrapDatabaseKey() {
                    rewrapCalls++
                    val encodedLegacyWrapper = legacyWrapper.readBytes()
                    val unwrapped = try {
                        envelope.decrypt(
                            encodedLegacyWrapper,
                            ByteArraySecretKey(legacyWrappingKey),
                            LEGACY_KEY_CONTEXT
                        )
                    } finally {
                        encodedLegacyWrapper.fill(0)
                    }
                    try {
                        vekKeyStore.saveIfAbsent(unwrapped)
                    } finally {
                        unwrapped.fill(0)
                    }
                    if (failOnceAfterRewrap) {
                        failOnceAfterRewrap = false
                        throw IOException("synthetic interruption after DB-key rewrap before journal checkpoint")
                    }
                }
                override fun verifyDatabaseIntegrity() {
                    val unwrapped = vekKeyStore.loadOrNull()
                        ?: throw IOException("Synthetic VEK database-key wrapper could not be reopened")
                    try {
                        val reopened = openSqlCipherDatabase(context, databaseFile, unwrapped)
                        try {
                            assertEquals(
                                "SQLCipher rows must survive an interrupted and repeated rewrap",
                                listOf(expectedRow),
                                reopened.documentMetadataDao().getAll()
                            )
                            reopened.openHelper.writableDatabase.query("PRAGMA integrity_check").use { cursor ->
                                assertTrue("PRAGMA integrity_check returned no row", cursor.moveToFirst())
                                assertEquals("SQLCipher integrity check failed", "ok", cursor.getString(0))
                                assertFalse("PRAGMA integrity_check returned extra rows", cursor.moveToNext())
                            }
                        } finally {
                            reopened.close()
                        }
                    } finally {
                        unwrapped.fill(0)
                    }
                }
            }

            org.junit.Assert.assertThrows(IOException::class.java) {
                VaultMigrationCoordinator(
                    VaultMigrationJournal(journalDirectory),
                    backend
                ).resume()
            }
            val interrupted = VaultMigrationJournal(journalDirectory).load()
            assertEquals(VaultMigrationPhase.RECOVERY_REQUIRED, interrupted?.phase)
            assertEquals(
                "Recovery must remember that the DB-key rewrap checkpoint was not committed",
                VaultMigrationPhase.MIGRATING_DATABASE_KEY,
                interrupted?.recoveryPhase
            )
            assertFalse(interrupted?.databaseKeyRewrapped == true)
            assertTrue("The create-once VEK wrapper must survive the interruption", vekWrapper.isFile)
            assertArrayEquals(
                "The legacy wrapper must remain unchanged before recovery",
                legacyWrapperBefore!!,
                legacyWrapper.readBytes()
            )

            val completed = VaultMigrationCoordinator(
                VaultMigrationJournal(journalDirectory),
                backend
            ).resume()
            assertEquals(VaultMigrationPhase.COMPLETE, completed.phase)
            assertEquals(
                "The durable COMPLETE checkpoint must survive a fresh journal read",
                completed,
                VaultMigrationJournal(journalDirectory).load()
            )
            assertEquals(
                "Recovery should retry the idempotent DB-key rewrap exactly once",
                2,
                rewrapCalls
            )
            assertArrayEquals(
                "The legacy wrapper must remain unchanged through COMPLETE",
                legacyWrapperBefore!!,
                legacyWrapper.readBytes()
            )
        } finally {
            try {
                session.lock()
            } finally {
                databaseKey.fill(0)
                legacyWrappingKey.fill(0)
                vek.fill(0)
                legacyWrapperBefore?.fill(0)
                check(!fixtureRoot.exists() || fixtureRoot.deleteRecursively()) {
                    "Synthetic SQLCipher fixture cleanup failed"
                }
            }
        }
    }

    private fun openSqlCipherDatabase(context: Context, path: File, key: ByteArray): VaultDatabase {
        check(path.isAbsolute && path.path.startsWith(context.cacheDir.absolutePath + File.separator)) {
            "SQLCipher instrumentation must stay under the disposable cache fixture"
        }
        System.loadLibrary("sqlcipher")
        Logger.setTarget(NoopTarget())
        return Room.databaseBuilder(context, VaultDatabase::class.java, path.absolutePath)
            .openHelperFactory(SupportOpenHelperFactory(key))
            .build()
    }

    private class SessionVekKeyAccess(private val session: VaultSession) : MigrationVekKeyAccess {
        override fun <T> withKey(block: (SecretKey) -> T): T = session.withKey { bytes ->
            block(ByteArraySecretKey(bytes))
        }
    }

    private class ByteArraySecretKey(private val bytes: ByteArray) : SecretKey {
        override fun getAlgorithm(): String = "AES"
        override fun getFormat(): String = "RAW"
        override fun getEncoded(): ByteArray = bytes.copyOf()
    }

    private companion object {
        const val DATABASE_NAME = "vault-metadata.db"
        const val KEY_BYTES = 32
        val LEGACY_KEY_CONTEXT = "kaagaz.vault.sqlcipher-key.v1".toByteArray(Charsets.UTF_8)
        val EMULATOR_HARDWARE = setOf("ranchu", "goldfish", "vbox86", "emu64x")
    }
}
