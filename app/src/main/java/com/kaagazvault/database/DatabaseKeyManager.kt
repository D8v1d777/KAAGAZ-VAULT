package com.kaagazvault.database

import android.content.Context
import com.kaagazvault.security.AesGcmEnvelope
import com.kaagazvault.security.AndroidKeystoreDocumentKeyProvider
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom

/**
 * Creates a random SQLCipher key and stores only its AES-GCM-wrapped form in no-backup storage.
 * A missing/corrupt wrapper is not silently replaced when one already exists.
 */
internal class DatabaseKeyManager(context: Context) {
    private val keyFile = File(context.noBackupFilesDir, WRAPPED_KEY_FILE)
    private val wrapperKeyProvider = AndroidKeystoreDocumentKeyProvider(WRAPPING_KEY_ALIAS)
    private val envelope = AesGcmEnvelope()

    @Synchronized
    @Throws(IOException::class, GeneralSecurityException::class)
    fun getOrCreateKey(): ByteArray {
        if (keyFile.exists()) {
            val unwrapped = envelope.decrypt(
                keyFile.readBytes(),
                wrapperKeyProvider.getOrCreateKey(),
                KEY_CONTEXT
            )
            if (unwrapped.size != KEY_SIZE_BYTES) {
                unwrapped.fill(0)
                throw IOException("Wrapped database key has an invalid size")
            }
            return unwrapped
        }

        val key = ByteArray(KEY_SIZE_BYTES).also(SecureRandom()::nextBytes)
        val temporary = File(keyFile.parentFile, ".$WRAPPED_KEY_FILE.pending")
        try {
            val wrapped = envelope.encrypt(key, wrapperKeyProvider.getOrCreateKey(), KEY_CONTEXT)
            FileOutputStream(temporary).use { output ->
                output.write(wrapped)
                output.fd.sync()
            }
            if (keyFile.exists()) throw IOException("Database key was created concurrently")
            if (!temporary.renameTo(keyFile)) throw IOException("Could not persist wrapped database key")
            return key
        } catch (error: Exception) {
            key.fill(0)
            throw error
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    companion object {
        private const val KEY_SIZE_BYTES = 32
        private const val WRAPPED_KEY_FILE = "vault-database-key.wrap"
        private const val WRAPPING_KEY_ALIAS = "kaagaz.vault.database-key-wrap.v1"
        private val KEY_CONTEXT = "kaagaz.vault.sqlcipher-key.v1".toByteArray(Charsets.UTF_8)
    }
}
