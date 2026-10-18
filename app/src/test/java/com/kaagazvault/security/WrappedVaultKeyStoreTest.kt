package com.kaagazvault.security

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WrappedVaultKeyStoreTest {
    private val wrapped = ByteArray(60) { it.toByte() }

    @Test
    fun persistsAndReloadsWrappedKeyBytes() {
        withDirectory { directory ->
            val store = WrappedVaultKeyStore(File(directory, "wrapped-vek.bin"))
            assertNull(store.loadOrNull())

            store.saveIfAbsent(wrapped)

            assertArrayEquals(wrapped, store.loadOrNull())
        }
    }

    @Test
    fun refusesToReplaceExistingWrappedKey() {
        withDirectory { directory ->
            val store = WrappedVaultKeyStore(File(directory, "wrapped-vek.bin"))
            store.saveIfAbsent(wrapped)

            assertThrows(IOException::class.java) { store.saveIfAbsent(ByteArray(60) { 7 }) }
            assertArrayEquals(wrapped, store.loadOrNull())
        }
    }

    @Test
    fun rejectsMalformedStoredEnvelope() {
        withDirectory { directory ->
            val file = File(directory, "wrapped-vek.bin")
            file.writeBytes(ByteArray(8))

            assertThrows(IOException::class.java) {
                WrappedVaultKeyStore(file).loadOrNull()
            }
        }
    }

    @Test
    fun recoversValidPendingInitialWrite() {
        withDirectory { directory ->
            val file = File(directory, "wrapped-vek.bin")
            val store = WrappedVaultKeyStore(file)
            store.saveIfAbsent(wrapped)
            assertTrue(file.renameTo(File(directory, ".wrapped-vek.bin.new")))

            assertArrayEquals(wrapped, store.loadOrNull())
            assertTrue(file.isFile)
        }
    }

    @Test
    fun rejectsInvalidNewEnvelopeBeforeWriting() {
        withDirectory { directory ->
            val store = WrappedVaultKeyStore(File(directory, "wrapped-vek.bin"))

            assertThrows(IOException::class.java) { store.saveIfAbsent(ByteArray(11)) }
            assertEquals(false, File(directory, "wrapped-vek.bin").exists())
        }
    }

    private fun withDirectory(block: (File) -> Unit) {
        val directory = Files.createTempDirectory("kgv-wrapped-key-test").toFile()
        try {
            block(directory)
        } finally {
            directory.deleteRecursively()
        }
    }
}
