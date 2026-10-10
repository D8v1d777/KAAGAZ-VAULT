package com.kaagazvault.reminders

import android.content.Context
import com.kaagazvault.database.ReminderEntity
import com.kaagazvault.database.VaultDatabaseProvider
import java.io.Closeable
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
        val safeTitle = title.filterNot(Char::isISOControl).trim().take(MAX_TITLE_LENGTH)
        require(safeTitle.isNotBlank()) { "Enter a reminder title." }
        require(dueAtEpochMillis > System.currentTimeMillis()) { "Choose a future date and time." }
        val reminder = ReminderEntity(
            id = UUID.randomUUID().toString(),
            title = safeTitle,
            dueAtEpochMillis = dueAtEpochMillis,
            linkedDocumentId = linkedDocumentId,
            createdAtEpochMillis = System.currentTimeMillis()
        )
        databaseProvider.get().reminderDao().upsert(reminder)
        ReminderScheduler.schedule(appContext, reminder.id, reminder.dueAtEpochMillis)
        return reminder
    }

    fun delete(id: String) {
        ReminderScheduler.cancel(appContext, id)
        databaseProvider.get().reminderDao().deleteById(id)
    }

    private companion object {
        const val MAX_TITLE_LENGTH = 160
    }
}
