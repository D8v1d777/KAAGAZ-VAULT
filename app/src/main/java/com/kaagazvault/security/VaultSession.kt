package com.kaagazvault.security

import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * In-memory VEK session gate. It is deliberately independent of Android UI and persistence.
 *
 * Call [installVerifiedKey] only with bytes returned by a successful authenticated unwrap or
 * first-time setup operation, after the wrapped envelope has been durably persisted. This class
 * does not itself authenticate the caller or persist the key.
 *
 * Every protected operation must run entirely inside [withKey]. Lock takes the write lock, so it
 * waits for active operations to finish before zeroing the session key and returning. The callback
 * receives a temporary copy which is cleared when the callback exits. Callbacks must not retain
 * that copy or call lock/unlock recursively.
 */
internal class VaultSession {
    private val lock = ReentrantReadWriteLock()
    private var vek: ByteArray? = null

    fun isUnlocked(): Boolean = lock.read { vek != null }

    /**
     * Installs a verified 256-bit VEK once per locked session.
     * The caller retains ownership of [verifiedKey] and must clear its own buffer when safe.
     */
    fun installVerifiedKey(verifiedKey: ByteArray) {
        require(verifiedKey.size == VEK_BYTES) { "Vault encryption key must be 256 bits" }
        lock.write {
            check(vek == null) { "Vault session is already unlocked" }
            vek = verifiedKey.copyOf()
        }
    }

    /**
     * Executes an operation while the session is unlocked.
     * The temporary key copy is cleared even when [operation] throws.
     */
    fun <T> withKey(operation: (ByteArray) -> T): T = lock.read {
        val sessionKey = vek?.copyOf() ?: throw VaultLockedException()
        try {
            operation(sessionKey)
        } finally {
            sessionKey.fill(0)
        }
    }

    /**
     * Stops new key uses, waits for active uses to exit, then clears the session key.
     * This guarantees no operation using this session key copy remains active after lock returns.
     */
    fun lock() {
        lock.write {
            vek?.fill(0)
            vek = null
        }
    }

    private companion object {
        const val VEK_BYTES = 32
    }
}

internal class VaultLockedException : IllegalStateException("Vault is locked")
