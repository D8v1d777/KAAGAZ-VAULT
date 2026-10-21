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

    @Test
    fun cancelledReminderDoesNotDeliverNotification() {
        val gate = ReminderDeliveryGate()
        val id = UUID.randomUUID().toString()
        var deliveries = 0
        gate.cancel(id)

        val delivered = gate.deliverIfAllowed(id) { deliveries += 1 }

        assertEquals(false, delivered)
        assertEquals(0, deliveries)
    }

    @Test
    fun cancellationAfterSchedulingSuppressesDeliveryAndRescheduleReenablesIt() {
        val gate = ReminderDeliveryGate()
        val id = UUID.randomUUID().toString()
        var deliveries = 0
        gate.allow(id)
        gate.cancel(id)
        assertEquals(false, gate.deliverIfAllowed(id) { deliveries += 1 })

        gate.allow(id)
        assertEquals(true, gate.deliverIfAllowed(id) { deliveries += 1 })
        assertEquals(1, deliveries)
    }
}
