package com.kaagazvault.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.UUID

class ReminderWorkDataTest {
    @Test
    fun workInputContainsOnlyOpaqueReminderUuid() {
        val id = UUID.randomUUID().toString()

        val data = ReminderScheduler.buildInputData(id)

        assertEquals(setOf(ReminderScheduler.INPUT_REMINDER_ID), data.keyValueMap.keys)
        assertEquals(id, data.getString(ReminderScheduler.INPUT_REMINDER_ID))
    }

    @Test
    fun workInputRejectsNonUuidValues() {
        assertThrows(IllegalArgumentException::class.java) {
            ReminderScheduler.buildInputData("Passport renewal — medical records")
        }
    }
}
