package com.kaagazvault.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultSessionTest {
    @Test
    fun startsLockedAndRejectsKeyAccess() {
        val session = VaultSession()

        assertFalse(session.isUnlocked())
        assertThrows(VaultLockedException::class.java) {
            session.withKey { it.size }
        }
    }

    @Test
    fun acceptsOnlyA256BitVekAndProvidesAnEphemeralCopy() {
        val session = VaultSession()
        val vek = ByteArray(32) { it.toByte() }
        var observedCopy: ByteArray? = null

        session.installVerifiedKey(vek)
        val length = session.withKey {
            assertArrayEquals(vek, it)
            observedCopy = it
            it.size
        }

        assertEquals(32, length)
        assertTrue(observedCopy!!.all { it == 0.toByte() })
        assertTrue(session.isUnlocked())
        session.lock()
        assertFalse(session.isUnlocked())
        assertThrows(VaultLockedException::class.java) {
            session.withKey { it.size }
        }
    }

    @Test
    fun rejectsReplacingAnUnlockedSessionKey() {
        val session = VaultSession()
        val originalKey = ByteArray(32) { 3 }
        val replacementKey = ByteArray(32) { 9 }

        session.installVerifiedKey(originalKey)

        assertThrows(IllegalStateException::class.java) {
            session.installVerifiedKey(replacementKey)
        }
        session.withKey { activeKey ->
            assertArrayEquals(originalKey, activeKey)
        }
        session.lock()
    }

    @Test
    fun installedKeyIsIsolatedFromCallerBufferMutations() {
        val session = VaultSession()
        val callerBuffer = ByteArray(32) { 4 }
        val expectedKey = callerBuffer.copyOf()

        session.installVerifiedKey(callerBuffer)
        callerBuffer.fill(0)

        session.withKey { activeKey ->
            assertArrayEquals(expectedKey, activeKey)
        }
        session.lock()
    }

    @Test
    fun clearsEphemeralKeyCopyWhenOperationThrows() {
        val session = VaultSession()
        session.installVerifiedKey(ByteArray(32) { 5 })
        var observedCopy: ByteArray? = null

        assertThrows(IllegalArgumentException::class.java) {
            session.withKey {
                observedCopy = it
                throw IllegalArgumentException("synthetic operation failure")
            }
        }

        assertTrue(observedCopy!!.all { it == 0.toByte() })
        session.lock()
    }

    @Test
    fun rejectsInvalidKeyLengthWithoutUnlocking() {
        val session = VaultSession()

        assertThrows(IllegalArgumentException::class.java) {
            session.installVerifiedKey(ByteArray(31))
        }
        assertFalse(session.isUnlocked())
    }

    @Test
    fun lockClosesRegisteredResourcesAndStillClearsSessionIfACloserFails() {
        val session = VaultSession()
        session.installVerifiedKey(ByteArray(32) { 6 })
        var closeCalled = false
        session.addLockListener { closeCalled = true }
        session.addLockListener { throw IllegalStateException("synthetic close failure") }

        assertThrows(IllegalStateException::class.java) { session.lock() }

        assertTrue(closeCalled)
        assertFalse(session.isUnlocked())
        assertThrows(VaultLockedException::class.java) {
            session.withKey { it.size }
        }
    }

    @Test
    fun lockWaitsForActiveKeyUseAndRejectsNewUsesAfterCompletion() {
        val session = VaultSession()
        session.installVerifiedKey(ByteArray(32) { 7 })
        val operationEntered = CountDownLatch(1)
        val finishOperation = CountDownLatch(1)
        val lockFinished = CountDownLatch(1)

        val operation = Thread {
            session.withKey {
                operationEntered.countDown()
                check(finishOperation.await(5, TimeUnit.SECONDS))
            }
        }
        operation.start()
        assertTrue(operationEntered.await(5, TimeUnit.SECONDS))

        val locking = Thread {
            session.lock()
            lockFinished.countDown()
        }
        locking.start()

        assertFalse("lock must wait for active key use", lockFinished.await(100, TimeUnit.MILLISECONDS))
        finishOperation.countDown()
        operation.join(5_000)
        locking.join(5_000)

        assertTrue("lock did not finish", lockFinished.await(1, TimeUnit.SECONDS))
        assertFalse(session.isUnlocked())
        assertThrows(VaultLockedException::class.java) {
            session.withKey { it.size }
        }
    }
}
