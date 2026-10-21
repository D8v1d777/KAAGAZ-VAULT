package com.kaagazvault.reminders

import android.content.Context
import com.kaagazvault.database.ReminderEntity
import com.kaagazvault.database.VaultDatabaseProvider
import com.kaagazvault.security.VaultSession
import java.util.UUID

/**
 * User-confirmed reminders. Reminder text is encrypted at rest in SQLCipher.
 * No OCR-derived reminder is created automatically.
 */
internal class ReminderRepository(
    context: Context,
    private val databaseProvider: VaultDatabaseProvider,
    private val session: VaultSession? = null
) {
    private val appContext = context.applicationContext

    private fun <T> withVaultSession(operation: () -> T): T =
        session?.withKey { operation() } ?: operation()

    fun list(): List<ReminderEntity> = withVaultSession {
        databaseProvider.withDatabase { it.reminderDao().listAll() }
    }

    fun create(title: String, dueAtEpochMillis: Long, linkedDocumentId: String? = null): ReminderEntity =
        withVaultSession {
            val safeTitle = ReminderPolicy.normalizeTitle(title)
            ReminderPolicy.requireFutureDueTime(dueAtEpochMillis, System.currentTimeMillis())
            val reminder = ReminderEntity(
                id = UUID.randomUUID().toString(),
                title = safeTitle,
                dueAtEpochMillis = dueAtEpochMillis,
                linkedDocumentId = linkedDocumentId,
                createdAtEpochMillis = System.currentTimeMillis()
            )
            databaseProvider.withDatabase { it.reminderDao().upsert(reminder) }
            try {
                ReminderScheduler.schedule(appContext, reminder.id, reminder.dueAtEpochMillis)
            } catch (error: Exception) {
                databaseProvider.withDatabase { it.reminderDao().deleteById(reminder.id) }
                throw error
            }
            reminder
        }

    fun delete(id: String) = withVaultSession {
        databaseProvider.withDatabase { it.reminderDao().deleteById(id) }
        ReminderScheduler.cancel(appContext, id)
    }
}
