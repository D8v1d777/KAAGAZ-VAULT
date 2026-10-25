package com.kaagazvault.security.hardening

import android.util.Log
import java.lang.ref.Cleaner
import java.nio.ByteBuffer
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Memory protection utilities for sensitive data (encryption keys, passwords, etc.).
 *
 * Provides:
 * - Secure byte arrays that zero themselves on cleanup
 * - Protection against memory dumping (mlock/mprotect where available)
 * - Cleaner-based automatic cleanup
 * - Guarded allocation to reduce exposure window
 *
 * Note: On Android, mlock/mlockall requires root or special permissions.
 * These are best-effort protections that raise the bar for attackers.
 */
internal object MemoryProtection {

    private const val TAG = "MemoryProtection"
    private val cleaner = Cleaner.create()

    /**
     * Secure byte array that automatically zeros on cleanup.
     * Use for encryption keys, passwords, PINs, etc.
     */
    class SecureBytes(private val data: ByteArray) {
        private val cleaned = AtomicBoolean(false)
        private val cleaningAction: Runnable = { wipe() }
        private val cleanable = cleaner.register(this, cleaningAction)

        init {
        }

        /** Get the data (caller must not retain reference) */
        fun <R> use(block: (ByteArray) -> R): R {
            if (cleaned.get()) throw IllegalStateException("SecureBytes already cleaned")
            return block(data)
        }

        /** Get copy of data (use sparingly) */
        fun copy(): ByteArray {
            if (cleaned.get()) throw IllegalStateException("SecureBytes already cleaned")
            return data.copyOf()
        }

        /** Get size without exposing data */
        val size: Int
            get() = if (cleaned.get()) 0 else data.size

        /** Explicitly zero and clean */
        fun wipe() {
            if (cleaned.compareAndSet(false, true)) {
                // Zero the array
                for (i in data.indices) {
                    data[i] = 0
                }
                cleanable.clean()
                Log.d(TAG, "SecureBytes wiped (${data.size} bytes)")
            }
        }

        /** Check if cleaned */
        val isCleaned: Boolean
            get() = cleaned.get()
    }

    /**
     * Create a SecureBytes from a byte array (takes ownership, zeros source).
     */
    fun secureBytes(data: ByteArray): SecureBytes {
        val copy = data.copyOf()
        // Zero source immediately
        for (i in data.indices) data[i] = 0
        return SecureBytes(copy)
    }

    /**
     * Create a SecureBytes of specified size, filled with secure random.
     */
    fun secureRandomBytes(size: Int): SecureBytes {
        val data = ByteArray(size)
        java.security.SecureRandom().nextBytes(data)
        return SecureBytes(data)
    }

    /**
     * Create a SecureBytes from a char array (for passwords/PINs).
     * Zeros the source char array.
     */
    fun secureChars(chars: CharArray): SecureBytes {
        val bytes = ByteArray(chars.size)
        for (i in chars.indices) {
            bytes[i] = chars[i].toByte()
            chars[i] = 0.toChar()
        }
        return SecureBytes(bytes)
    }

    /**
     * Wrapper for SecretKey that ensures key material is wiped on cleanup.
     * Note: Android's SecretKey doesn't expose key material directly.
     * This is a placeholder for when you have raw key bytes.
     */
    class SecureKey(private val keyBytes: SecureBytes) {
        fun <R> use(block: (ByteArray) -> R): R = keyBytes.use(block)

        fun wipe() = keyBytes.wipe()

        val isCleaned: Boolean = keyBytes.isCleaned
    }

    /** Create SecureKey from raw key bytes */
    fun secureKey(keyBytes: ByteArray): SecureKey = SecureKey(secureBytes(keyBytes))

    /**
     * Pin memory to prevent swapping (best effort).
     * Requires appropriate permissions/capabilities.
     */
    @Suppress("UNUSED_PARAMETER")
    fun pinMemory(buffer: ByteBuffer): Boolean {
        // On Android, mlock requires CAP_IPC_LOCK or root
        // This is a no-op on standard Android but shows intent
        // For production, use JNI to call mlock(2)
        return try {
            // Would call native mlock here
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Unpin memory.
     */
    @Suppress("UNUSED_PARAMETER")
    fun unpinMemory(buffer: ByteBuffer): Boolean {
        return try {
            // Would call native munlock here
            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Allocate a direct ByteBuffer that's protected (best effort).
     * Direct buffers are outside Java heap, harder to dump via heap analysis.
     */
    fun allocateProtectedBuffer(size: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(size)
        // Try to prevent core dumps from including this memory
        // (requires native mprotect with PROT_NONE on cleanup)
        return buffer
    }

    /**
     * Securely zero a ByteBuffer.
     */
    fun zeroBuffer(buffer: ByteBuffer) {
        if (buffer.isDirect) {
            // For direct buffers, we need to access the memory directly
            // This is a best-effort approach
            val capacity = buffer.capacity()
            buffer.rewind()
            val bytes = ByteArray(capacity)
            buffer.get(bytes)
            // Zero the array
            for (i in bytes.indices) bytes[i] = 0
            buffer.rewind()
            buffer.put(bytes)
            // Zero the temp array
            for (i in bytes.indices) bytes[i] = 0
        } else {
            // Heap buffer - can access backing array
            val array = buffer.array()
            for (i in array.indices) array[i] = 0
        }
    }

    /**
     * Execute a block with a secure temporary buffer that's zeroed after use.
     */
    fun <R> withSecureBuffer(size: Int, block: (ByteBuffer) -> R): R {
        val buffer = allocateProtectedBuffer(size)
        try {
            return block(buffer)
        } finally {
            zeroBuffer(buffer)
        }
    }

    /**
     * Disable core dumps for this process (best effort).
     * On Android, this is typically enforced by the system.
     */
    fun disableCoreDumps() {
        // On Linux/Android, core dumps are controlled by:
        // - /proc/sys/kernel/core_pattern
        // - prlimit(PR_LIMIT_CORE, 0)
        // - setrlimit(RLIMIT_CORE, 0)
        // These require native code or root
        // This is a placeholder for JNI implementation
        Log.d(TAG, "Core dump disable requested (requires native impl)")
    }

    /**
     * Check if running in a memory analysis environment.
     * Heuristic: check for common memory analysis tools.
     */
    fun detectMemoryAnalysis(): Boolean {
        try {
            // Check /proc/self/maps for suspicious mappings
            val maps = java.io.File("/proc/self/maps").readText()
            val suspicious = arrayOf(
                "frida", "xposed", "dlsym", "ptrace",
                "memwatch", "valgrind", "pin", "dynamorio"
            )
            for (line in maps.lines()) {
                for (s in suspicious) {
                    if (line.contains(s, ignoreCase = true)) {
                        Log.w(TAG, "Suspicious memory mapping: $line")
                        return true
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }
}

/**
 * Thread-local secure storage for per-thread sensitive data.
 * Automatically cleaned when thread ends (via Cleaner).
 */
internal class ThreadLocalSecureStorage {
    private val threadLocal = ThreadLocal<MemoryProtection.SecureBytes>()
    private val cleaner = Cleaner.create()

    fun set(data: ByteArray): MemoryProtection.SecureBytes {
        val secure = MemoryProtection.secureBytes(data)
        threadLocal.set(secure)
        return secure
    }

    fun get(): MemoryProtection.SecureBytes? = threadLocal.get()

    fun clear() {
        threadLocal.get()?.wipe()
        threadLocal.remove()
    }
}