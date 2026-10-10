package com.kaagazvault.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal interface DocumentMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(document: DocumentMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAll(documents: List<DocumentMetadataEntity>)

    @Query("DELETE FROM document_metadata WHERE id = :id")
    fun deleteById(id: String)

    @Query("DELETE FROM document_metadata")
    fun clear()

    @Query("SELECT * FROM document_metadata WHERE :normalizedQuery = '' OR instr(normalizedSearchText, :normalizedQuery) > 0 ORDER BY updatedAtEpochMillis DESC")
    fun search(normalizedQuery: String): List<DocumentMetadataEntity>

    @Query("SELECT * FROM document_metadata")
    fun getAll(): List<DocumentMetadataEntity>

    @Transaction
    fun replaceAll(documents: List<DocumentMetadataEntity>) {
        clear()
        if (documents.isNotEmpty()) upsertAll(documents)
    }
}
