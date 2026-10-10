package com.kaagazvault.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReminderPolicyTest {
    @Test
    fun titleIsTrimmedAndControlCharactersAreRemoved() {
        assertEquals("Renew passport", ReminderPolicy.normalizeTitle("\u0000  Renew\n passport\t "))
    }

    @Test
    fun titleIsBoundedToMaximumLength() {
        assertEquals(ReminderPolicy.MAX_TITLE_LENGTH, ReminderPolicy.normalizeTitle("x".repeat(300)).length)
    }

    @Test
    fun blankOrControlOnlyTitleIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { ReminderPolicy.normalizeTitle(" \n\t ") }
        assertThrows(IllegalArgumentException::class.java) { ReminderPolicy.normalizeTitle("\u0000\u0001") }
    }

    @Test
    fun dueTimeMustBeStrictlyInTheFuture() {
        val now = 1_800_000_000_000L
        ReminderPolicy.requireFutureDueTime(now + 1, now)
        assertThrows(IllegalArgumentException::class.java) {
            ReminderPolicy.requireFutureDueTime(now, now)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReminderPolicy.requireFutureDueTime(now - 1, now)
        }
    }
}
