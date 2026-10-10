package com.kaagazvault.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Reminder content is stored only in the SQLCipher database. WorkManager receives
 * only the opaque UUID; never place titles, notes, or document names in WorkData.
 */
@Entity(tableName = "vault_reminders")
internal data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val dueAtEpochMillis: Long,
    val linkedDocumentId: String?,
    val createdAtEpochMillis: Long
)

@Dao
internal interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(reminder: ReminderEntity)

    @Query("SELECT * FROM vault_reminders WHERE id = :id LIMIT 1")
    fun findById(id: String): ReminderEntity?

    @Query("SELECT * FROM vault_reminders ORDER BY dueAtEpochMillis ASC")
    fun listAll(): List<ReminderEntity>

    @Query("DELETE FROM vault_reminders WHERE id = :id")
    fun deleteById(id: String): Int
}
