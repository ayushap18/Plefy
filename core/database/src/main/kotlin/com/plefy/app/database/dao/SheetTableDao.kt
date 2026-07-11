package com.plefy.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.plefy.app.database.entity.SheetTableEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for [SheetTableEntity] (the root of each imported table).
 */
@Dao
interface SheetTableDao {

    /** Inserts a table row and returns its generated primary key. */
    @Insert
    suspend fun insert(table: SheetTableEntity): Long

    /** Observes all persisted tables, most recently imported first. */
    @Query("SELECT * FROM sheet_table ORDER BY importedAt DESC")
    fun getAll(): Flow<List<SheetTableEntity>>

    /** Returns the table with the given id, or `null` if none exists. */
    @Query("SELECT * FROM sheet_table WHERE id = :id")
    suspend fun getById(id: Long): SheetTableEntity?

    /** Updates the cached [SheetTableEntity.rowCount] for a table. */
    @Query("UPDATE sheet_table SET rowCount = :count WHERE id = :id")
    suspend fun updateRowCount(id: Long, count: Int)

    /**
     * Deletes a table by id. Owned columns, rows and cells are removed automatically via
     * `ON DELETE CASCADE` foreign keys.
     */
    @Query("DELETE FROM sheet_table WHERE id = :id")
    suspend fun deleteById(id: Long)
}
