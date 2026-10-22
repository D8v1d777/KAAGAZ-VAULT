package com.kaagazvault.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

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

    @Test
    fun staleMigrationCleanupCannotInterleaveWithNewAuthentication() {
        val gate = VaultAccessEpochGate()
        val migrationEpoch = gate.currentEpoch()
        var sessionUnlocked = true
        val cleanupEntered = CountDownLatch(1)
        val releaseCleanup = CountDownLatch(1)
        val authenticationStarted = CountDownLatch(1)
        val authenticationFinished = CountDownLatch(1)
        val cleanupFailure = AtomicReference<Throwable?>()
        val authenticationFailure = AtomicReference<Throwable?>()

        val staleCleanupThread = Thread {
            try {
                gate.ifCurrent(migrationEpoch) {
                    cleanupEntered.countDown()
                    check(releaseCleanup.await(2, TimeUnit.SECONDS)) {
                        "Timed out waiting to finish stale migration cleanup"
                    }
                    sessionUnlocked = false
                }
            } catch (failure: Throwable) {
                cleanupFailure.set(failure)
            }
        }

        val authenticationThread = Thread {
            authenticationStarted.countDown()
            try {
                // Mirrors lock invalidation followed by a verified-key installation.
                gate.invalidate { sessionUnlocked = false }
                gate.withLock { sessionUnlocked = true }
            } catch (failure: Throwable) {
                authenticationFailure.set(failure)
            } finally {
                authenticationFinished.countDown()
            }
        }

        staleCleanupThread.start()
        try {
            assertTrue(
                "Stale cleanup did not enter its guarded section",
                cleanupEntered.await(2, TimeUnit.SECONDS)
            )
            authenticationThread.start()
            assertTrue(
                "Authentication worker did not start",
                authenticationStarted.await(2, TimeUnit.SECONDS)
            )

            // The stale cleanup holds the gate monitor while its epoch check and action
            // are atomic. The competing authentication must block until that action exits.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (
                authenticationThread.isAlive &&
                authenticationThread.state != Thread.State.BLOCKED &&
                System.nanoTime() < deadline
            ) {
                Thread.yield()
            }
            assertEquals(
                "Authentication must serialize behind the in-flight guarded cleanup",
                Thread.State.BLOCKED,
                authenticationThread.state
            )
        } finally {
            releaseCleanup.countDown()
        }

        staleCleanupThread.join(2_000)
        authenticationThread.join(2_000)

        assertFalse("Stale cleanup worker did not terminate", staleCleanupThread.isAlive)
        assertFalse("Authentication worker did not terminate", authenticationThread.isAlive)
        assertNull("Stale cleanup worker failed", cleanupFailure.get())
        assertNull("Authentication worker failed", authenticationFailure.get())
        assertTrue("New authentication must be the final session state", sessionUnlocked)
        assertEquals(0L, authenticationFinished.count)
    }
}
