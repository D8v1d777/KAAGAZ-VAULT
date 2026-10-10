package com.kaagazvault.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [DocumentMetadataEntity::class, ReminderEntity::class], version = 2, exportSchema = false)
internal abstract class VaultDatabase : RoomDatabase() {
    abstract fun documentMetadataDao(): DocumentMetadataDao
    abstract fun reminderDao(): ReminderDao
}

internal class VaultDatabaseProvider(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var instance: VaultDatabase? = null
    private var databaseKey: ByteArray? = null

    @Synchronized
    fun get(): VaultDatabase {
        instance?.let { return it }
        System.loadLibrary("sqlcipher")
        Logger.setTarget(NoopTarget())
        val key = DatabaseKeyManager(appContext).getOrCreateKey()
        try {
            val created = Room.databaseBuilder(
                appContext, VaultDatabase::class.java,
                appContext.getDatabasePath(DATABASE_NAME).absolutePath
            )
                .openHelperFactory(SupportOpenHelperFactory(key))
                .addMigrations(MIGRATION_1_2)
                .build()
            databaseKey = key
            instance = created
            return created
        } catch (error: Throwable) {
            key.fill(0)
            throw error
        }
    }

    @Synchronized
    fun close() {
        try {
            instance?.close()
        } finally {
            instance = null
            databaseKey?.fill(0)
            databaseKey = null
        }
    }

    private companion object {
        const val DATABASE_NAME = "vault-metadata.db"
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS vault_reminders (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        dueAtEpochMillis INTEGER NOT NULL,
                        linkedDocumentId TEXT,
                        createdAtEpochMillis INTEGER NOT NULL
                    )""".trimIndent()
                )
            }
        }
    }
}
