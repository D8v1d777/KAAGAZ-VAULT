package com.kaagazvault.reminders

import android.content.Context
import com.kaagazvault.database.ReminderEntity
import com.kaagazvault.database.VaultDatabaseProvider
import java.util.UUID

/**
 * User-confirmed reminders. Reminder text is encrypted at rest in SQLCipher.
 * No OCR-derived reminder is created automatically.
 */
internal class ReminderRepository(
    context: Context,
    private val databaseProvider: VaultDatabaseProvider
) {
    private val appContext = context.applicationContext

    fun list(): List<ReminderEntity> = databaseProvider.get().reminderDao().listAll()

    fun create(title: String, dueAtEpochMillis: Long, linkedDocumentId: String? = null): ReminderEntity {
        val safeTitle = ReminderPolicy.normalizeTitle(title)
        ReminderPolicy.requireFutureDueTime(dueAtEpochMillis, System.currentTimeMillis())
        val reminder = ReminderEntity(
            id = UUID.randomUUID().toString(),
            title = safeTitle,
            dueAtEpochMillis = dueAtEpochMillis,
            linkedDocumentId = linkedDocumentId,
            createdAtEpochMillis = System.currentTimeMillis()
        )
        val dao = databaseProvider.get().reminderDao()
        dao.upsert(reminder)
        try {
            ReminderScheduler.schedule(appContext, reminder.id, reminder.dueAtEpochMillis)
        } catch (error: Exception) {
            // Do not leave a reminder that the UI reported as failed to schedule.
            dao.deleteById(reminder.id)
            throw error
        }
        return reminder
    }

    fun delete(id: String) {
        // Remove the encrypted source-of-truth row first so a racing worker can no-op.
        databaseProvider.get().reminderDao().deleteById(id)
        ReminderScheduler.cancel(appContext, id)
    }

}
