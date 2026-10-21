package com.kaagazvault.security

import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultKeyRecordStoreTest {
    @Test
    fun savesCreateOnceAndRejectsReplacement() = withDirectory { directory ->
        val store = VaultKeyRecordStore(File(directory, "vault-key.record"))
        val wrapped = ByteArray(60) { it.toByte() }
        store.saveIfAbsent("kaagaz_vault_auth_bound_vek_wrap_v1_1234567890abcdef", wrapped)
        val original = store.loadOrNull()!!
        assertArrayEquals(wrapped, original.wrappedVek)
        assertThrows(IOException::class.java) {
            store.saveIfAbsent("kaagaz_vault_auth_bound_vek_wrap_v1_abcdef1234567890", ByteArray(60) { 2 })
        }
        assertArrayEquals(wrapped, store.loadOrNull()!!.wrappedVek)
    }

    @Test
    fun malformedPrimaryIsPreservedAndFailsClosed() = withDirectory { directory ->
        val file = File(directory, "vault-key.record")
        val corrupted = byteArrayOf(1, 2, 3)
        file.writeBytes(corrupted)
        val store = VaultKeyRecordStore(file)
        assertThrows(IOException::class.java) { store.loadOrNull() }
        assertArrayEquals(corrupted, file.readBytes())
    }

    @Test
    fun replacementAfterRecoveryPublishesNewAliasAndEnvelope() = withDirectory { directory ->
        val store = VaultKeyRecordStore(File(directory, "vault-key.record"))
        val first = ByteArray(60) { 4 }
        val second = ByteArray(60) { 9 }
        store.saveIfAbsent("kaagaz_vault_auth_bound_vek_wrap_v1_first", first)
        store.replaceAfterRecovery("kaagaz_vault_auth_bound_vek_wrap_v1_second", second)
        val record = store.loadOrNull()!!
        assertTrue(record.alias.endsWith("second"))
        assertArrayEquals(second, record.wrappedVek)
        assertFalse(File(directory, ".vault-key.record.backup").exists())
    }

    private fun withDirectory(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("vault-key-record-test").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
}

class VaultSetupStateStoreTest {
    @Test
    fun markerIsFalseUntilDurablyConfirmedThenRemainsTrue() = withDirectory { directory ->
        val store = VaultSetupStateStore(File(directory, "recovery-confirmed.marker"))
        assertFalse(store.isRecoveryConfirmed())
        store.markRecoveryConfirmed()
        assertTrue(store.isRecoveryConfirmed())
        store.markRecoveryConfirmed()
        assertTrue(store.isRecoveryConfirmed())
    }

    @Test
    fun malformedConfirmationMarkerFailsClosed() = withDirectory { directory ->
        val file = File(directory, "recovery-confirmed.marker")
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(IOException::class.java) { VaultSetupStateStore(file).isRecoveryConfirmed() }
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }

    private fun withDirectory(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("vault-setup-state-test").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
}

class RecoveryEnvelopeStoreTest {
    @Test
    fun envelopeCanBeReplacedOnlyBeforeRecoveryKeyConfirmation() = withDirectory { directory ->
        val setup = VaultSetupStateStore(File(directory, "confirmed.marker"))
        val store = RecoveryEnvelopeStore(File(directory, "recovery.wrap"), setup)
        val first = ByteArray(65) { 1 }
        val second = ByteArray(65) { 2 }
        store.saveBeforeConfirmation(first)
        assertArrayEquals(first, store.loadOrNull())
        store.saveBeforeConfirmation(second)
        assertArrayEquals(second, store.loadOrNull())
        setup.markRecoveryConfirmed()
        assertThrows(IOException::class.java) { store.saveBeforeConfirmation(ByteArray(65) { 3 }) }
        assertArrayEquals(second, store.loadOrNull())
    }

    @Test
    fun malformedEnvelopeFailsClosedAndIsNotReplaced() = withDirectory { directory ->
        val setup = VaultSetupStateStore(File(directory, "confirmed.marker"))
        val file = File(directory, "recovery.wrap")
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(IOException::class.java) { RecoveryEnvelopeStore(file, setup).loadOrNull() }
        assertArrayEquals(byteArrayOf(1, 2, 3), file.readBytes())
    }

    private fun withDirectory(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("recovery-envelope-store-test").toFile()
        try { block(dir) } finally { dir.deleteRecursively() }
    }
}
