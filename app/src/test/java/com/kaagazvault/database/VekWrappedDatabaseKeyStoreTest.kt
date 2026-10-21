package com.kaagazvault.database

import com.kaagazvault.security.AesGcmEnvelope
import com.kaagazvault.security.MigrationVekKeyAccess
import com.kaagazvault.security.VaultLockedException
import java.io.File
import java.io.IOException
import java.nio.file.Files
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VekWrappedDatabaseKeyStoreTest {
    @Test
    fun storesOnlyAuthenticatedEnvelopeAndIsIdempotentForSameDatabaseKey() = withDirectory { directory ->
        val file = File(directory, "vault-database-key.vek.wrap")
        val databaseKey = ByteArray(32) { (it * 7).toByte() }
        val store = VekWrappedDatabaseKeyStore(file, FixedAccess(ByteArray(32) { 9 }))

        store.saveIfAbsent(databaseKey)
        val originalEnvelope = file.readBytes()
        assertFalse(originalEnvelope.contentEquals(databaseKey))
        assertArrayEquals(databaseKey, store.loadOrNull())

        store.saveIfAbsent(databaseKey)
        assertArrayEquals("Idempotent retry must not rewrite the primary envelope", originalEnvelope, file.readBytes())
        assertThrows(IOException::class.java) {
            store.saveIfAbsent(ByteArray(32) { 1 })
        }
        assertArrayEquals("Conflicting material must not replace the envelope", originalEnvelope, file.readBytes())
    }

    @Test
    fun recoversAValidPendingFirstWriteWithoutReplacingTheKey() = withDirectory { directory ->
        val file = File(directory, "vault-database-key.vek.wrap")
        val pending = File(directory, ".${file.name}.pending")
        val databaseKey = ByteArray(32) { 4 }
        val store = VekWrappedDatabaseKeyStore(file, FixedAccess(ByteArray(32) { 8 }))
        store.saveIfAbsent(databaseKey)
        assertTrue(file.renameTo(pending))

        val recovered = store.loadOrNull()
        assertArrayEquals(databaseKey, recovered)
        assertTrue(file.isFile)
        assertFalse(pending.exists())
    }

    @Test
    fun malformedPrimaryFailsClosedAndIsNotSilentlyReplaced() = withDirectory { directory ->
        val file = File(directory, "vault-database-key.vek.wrap")
        val malformed = byteArrayOf(1, 2, 3)
        file.writeBytes(malformed)
        val store = VekWrappedDatabaseKeyStore(file, FixedAccess(ByteArray(32) { 2 }))

        assertThrows(IOException::class.java) { store.loadOrNull() }
        assertArrayEquals(malformed, file.readBytes())
    }

    @Test
    fun lockedSessionCannotReadOrCreateTheDatabaseKeyWrapper() = withDirectory { directory ->
        val file = File(directory, "vault-database-key.vek.wrap")
        val locked = object : MigrationVekKeyAccess {
            override fun <T> withKey(block: (SecretKey) -> T): T = throw VaultLockedException()
        }
        val store = VekWrappedDatabaseKeyStore(file, locked)

        assertThrows(VaultLockedException::class.java) { store.loadOrNull() }
        assertThrows(VaultLockedException::class.java) { store.saveIfAbsent(ByteArray(32) { 3 }) }
        assertFalse(file.exists())
        assertFalse(File(directory, ".${file.name}.pending").exists())
    }

    private class FixedAccess(bytes: ByteArray) : MigrationVekKeyAccess {
        private val key: SecretKey = SecretKeySpec(bytes.copyOf(), "AES")
        override fun <T> withKey(block: (SecretKey) -> T): T = block(key)
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("kgv-db-key-wrap-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
