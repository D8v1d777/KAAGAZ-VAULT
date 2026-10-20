package com.kaagazvault.security

import java.util.concurrent.atomic.AtomicReference

/**
 * In-memory session that holds the unwrapped Vault Encryption Key (VEK)
 * after successful biometric (or chosen authenticator) unlock.
 *
 * Design goals:
 * - Never persist the raw VEK
 * - Explicit zeroization on lock / process death risk
 * - Single place that repositories must ask before touching ciphertext
 * - Does not own UI or BiometricPrompt — that stays in the presentation layer
 *
 * Status: Skeleton. Not yet wired into DocumentRepository or MainActivity.
 * Wiring is blocked on owner decision for CO-01 / CO-02 (authenticator + recovery).
 *
 * Usage sketch (after policy decision):
 *   1. prepareUnlockCipher / BiometricPrompt
 *   2. finishUnlock → obtain VEK
 *   3. VaultSession.unlock(vek)
 *   4. Repositories call VaultSession.requireUnlockedVek()
 *   5. On background / timeout / explicit lock → VaultSession.lock()
 */
class VaultSession {

    private val currentVek = AtomicReference<ByteArray?>(null)

    val isUnlocked: Boolean
        get() = currentVek.get() != null

    /**
     * Store the freshly unwrapped VEK in memory.
     * Caller must zero any temporary copies after this call.
     */
    fun unlock(vek: ByteArray) {
        require(vek.size == 32) { "VEK must be 32 bytes" }
        // Replace any previous key and zero it
        val previous = currentVek.getAndSet(vek.copyOf())
        previous?.fill(0)
    }

    /**
     * Zero the in-memory VEK. Safe to call multiple times.
     */
    fun lock() {
        val key = currentVek.getAndSet(null)
        key?.fill(0)
    }

    /**
     * Returns the current VEK or throws if the session is locked.
     * Callers must not retain the returned array longer than necessary.
     */
    @Throws(MissingEncryptionKeyException::class)
    fun requireUnlockedVek(): ByteArray {
        return currentVek.get()
            ?: throw MissingEncryptionKeyException("Vault is locked; authentication required")
    }

    /**
     * Convenience for short-lived operations that should not retain the key.
     */
    inline fun <T> withVek(block: (ByteArray) -> T): T {
        val vek = requireUnlockedVek()
        return try {
            block(vek)
        } finally {
            // Note: we do not zero the session key here — only the caller's temporary use.
        }
    }
}
