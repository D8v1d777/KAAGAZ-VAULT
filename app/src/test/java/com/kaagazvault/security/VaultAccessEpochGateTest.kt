package com.kaagazvault.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultAccessEpochGateTest {
    @Test
    fun staleMigrationFailureCannotInvalidateNewerAuthenticatedSession() {
        val gate = VaultAccessEpochGate()
        val migrationEpoch = gate.currentEpoch()
        var sessionUnlocked = true

        // Simulate foreground lock invalidating the migration, then a newer unlock.
        gate.invalidate { sessionUnlocked = false }
        gate.withLock { sessionUnlocked = true }

        val staleCleanupRan = gate.ifCurrent(migrationEpoch) {
            sessionUnlocked = false
        }

        assertFalse(staleCleanupRan)
        assertTrue(sessionUnlocked)
    }

    @Test
    fun cleanupForCurrentEpochStillRuns() {
        val gate = VaultAccessEpochGate()
        val epoch = gate.currentEpoch()
        var sessionLocked = false

        val cleanupRan = gate.ifCurrent(epoch) {
            sessionLocked = true
        }

        assertTrue(cleanupRan)
        assertTrue(sessionLocked)
    }

    @Test
    fun invalidationAdvancesEpochBeforeRunningLockAction() {
        val gate = VaultAccessEpochGate()
        val before = gate.currentEpoch()
        var observedEpoch = before

        gate.invalidate { newEpoch -> observedEpoch = newEpoch }

        assertTrue(observedEpoch > before)
        assertTrue(gate.currentEpoch() > before)
    }
}
