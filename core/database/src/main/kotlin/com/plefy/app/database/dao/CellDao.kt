package com.plefy.app.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.plefy.app.database.entity.CellEntity

/**
 * Data-access object for [CellEntity] cell values (the EAV leaf table).
 */
@Dao
interface CellDao {

    /** Inserts all cells and returns their generated primary keys, in order. */
    @Insert
    suspend fun insertAll(cells: List<CellEntity>): List<Long>

    /** Returns all cells belonging to a single row. */
    @Query("SELECT * FROM cell_entity WHERE rowId = :rowId")
    suspend fun getForRow(rowId: Long): List<CellEntity>
}
