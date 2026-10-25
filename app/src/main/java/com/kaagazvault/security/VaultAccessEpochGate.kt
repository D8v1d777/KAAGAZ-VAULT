package com.kaagazvault.security

/**
 * Serializes access-epoch checks with session-key installation and lock/dispose cleanup.
 *
 * The action passed to [ifCurrent] must remain small and must not perform blocking I/O.
 */
internal class VaultAccessEpochGate {
    private val monitor = Any()
    private var epoch: Long = 0L

    fun currentEpoch(): Long = synchronized(monitor) { epoch }

    fun <T> withLock(action: (Long) -> T): T = synchronized(monitor) { action(epoch) }

    fun <T> invalidate(action: (Long) -> T): T = synchronized(monitor) {
        epoch += 1
        action(epoch)
    }

    fun ifCurrent(expectedEpoch: Long, action: () -> Unit): Boolean = synchronized(monitor) {
        if (epoch != expectedEpoch) {
            false
        } else {
            action()
            true
        }
    }
}
