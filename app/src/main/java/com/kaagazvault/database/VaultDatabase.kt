package com.kaagazvault.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.kaagazvault.security.SessionMigrationVekKeyAccess
import com.kaagazvault.security.VaultSession
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(entities = [DocumentMetadataEntity::class, ReminderEntity::class], version = 2, exportSchema = false)
internal abstract class VaultDatabase : RoomDatabase() {
    abstract fun documentMetadataDao(): DocumentMetadataDao
    abstract fun reminderDao(): ReminderDao
}

internal class VaultDatabaseProvider(context: Context, private val session: VaultSession? = null) {
    private val vekKeyAccess = session?.let(::SessionMigrationVekKeyAccess)
    private val lockListener: () -> Unit = { close() }
    @Volatile private var lockListenerRegistered = false
    private val appContext = context.applicationContext
    @Volatile private var instance: VaultDatabase? = null
    private var databaseKey: ByteArray? = null

    init {
        ensureLockListenerRegistered()
    }

    /** Raw Room handles are restricted to legacy providers. Session providers use withDatabase. */
    fun get(): VaultDatabase {
        check(session == null) {
            "Session-bound database provider requires withDatabase so lock can drain active DAO work"
        }
        return getUnchecked()
    }

    /** Holds session access across database open and the complete DAO/query callback. */
    fun <T> withDatabase(operation: (VaultDatabase) -> T): T {
        val activeSession = session
        return if (activeSession == null) {
            operation(getUnchecked())
        } else {
            activeSession.withKey {
                ensureLockListenerRegistered()
                operation(getUnchecked())
            }
        }
    }

    @Synchronized
    private fun getUnchecked(): VaultDatabase {
        ensureLockListenerRegistered()
        instance?.let { return it }
        System.loadLibrary("sqlcipher")
        Logger.setTarget(NoopTarget())
        val key = DatabaseKeyManager(appContext, vekKeyAccess).getOrCreateKey()
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
            if (lockListenerRegistered) {
                session?.removeLockListener(lockListener)
                lockListenerRegistered = false
            }
        }
    }

    @Synchronized
    private fun ensureLockListenerRegistered() {
        if (session != null && !lockListenerRegistered) {
            session.addLockListener(lockListener)
            lockListenerRegistered = true
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
