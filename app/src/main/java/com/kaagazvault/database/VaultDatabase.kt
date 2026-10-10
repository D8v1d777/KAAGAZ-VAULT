package com.kaagazvault.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [DocumentMetadataEntity::class],
    version = 1,
    exportSchema = false
)
internal abstract class VaultDatabase : RoomDatabase() {
    abstract fun documentMetadataDao(): DocumentMetadataDao
}

/**
 * Owns the database passphrase for exactly as long as the Room database is open.
 * The passphrase is generated randomly, wrapped by Android Keystore, and never hardcoded.
 */
internal class VaultDatabaseProvider(context: Context) {
    private val appContext = context.applicationContext
    @Volatile private var instance: VaultDatabase? = null
    private var databaseKey: ByteArray? = null

    @Synchronized
    fun get(): VaultDatabase {
        instance?.let { return it }
        System.loadLibrary("sqlcipher")
        // Disable SQLCipher's default Logcat target to avoid accidental query/data leakage.
        Logger.setTarget(NoopTarget())

        val key = DatabaseKeyManager(appContext).getOrCreateKey()
        try {
            val created = Room.databaseBuilder(
                appContext,
                VaultDatabase::class.java,
                appContext.getDatabasePath(DATABASE_NAME).absolutePath
            )
                .openHelperFactory(SupportOpenHelperFactory(key))
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
    }
}
