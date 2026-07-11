package com.plefy.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.plefy.app.database.entity.ColumnDefEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data-access object for [ColumnDefEntity] column definitions.
 */
@Dao
interface ColumnDao {

    /** Inserts all column definitions and returns their generated primary keys, in order. */
    @Insert
    suspend fun insertAll(columns: List<ColumnDefEntity>): List<Long>

    /** Observes the columns of a table, ordered by [ColumnDefEntity.colIndex]. */
    @Query("SELECT * FROM column_def WHERE tableId = :tableId ORDER BY colIndex ASC")
    fun getForTable(tableId: Long): Flow<List<ColumnDefEntity>>

    /** One-shot fetch of a table's columns, ordered by [ColumnDefEntity.colIndex]. */
    @Query("SELECT * FROM column_def WHERE tableId = :tableId ORDER BY colIndex ASC")
    suspend fun getForTableOnce(tableId: Long): List<ColumnDefEntity>
}
