package com.plefy.app.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.plefy.app.database.entity.CellEntity
import com.plefy.app.database.entity.RowEntity

/**
 * Data-access object for [RowEntity] rows.
 */
@Dao
interface RowDao {

    /** Inserts all rows and returns their generated primary keys, in order. */
    @Insert
    suspend fun insertAll(rows: List<RowEntity>): List<Long>

    /**
     * A Paging 3 source over a table's rows ordered by [RowEntity.rowIndex]. Room generates
     * the paging implementation; callers wrap this in a `Pager`.
     */
    @Query("SELECT * FROM row_entity WHERE tableId = :tableId ORDER BY rowIndex ASC")
    fun pagingSource(tableId: Long): PagingSource<Int, RowEntity>

    /** Returns the number of rows persisted for a table. */
    @Query("SELECT COUNT(*) FROM row_entity WHERE tableId = :tableId")
    suspend fun countForTable(tableId: Long): Int

    /**
     * Escape hatch for the dynamically-built sort/filter/search engine (Phase 3), paged.
     *
     * The caller (`:domain`) supplies a fully-formed [SupportSQLiteQuery] projecting `row_entity`
     * columns; Room generates the [PagingSource]. [observedEntities] lists both [RowEntity] and
     * [CellEntity] so Room invalidation fires when either the rows or the cells they filter/sort
     * on change, keeping observed paging results fresh.
     */
    @RawQuery(observedEntities = [RowEntity::class, CellEntity::class])
    fun queryRowsPaging(query: SupportSQLiteQuery): PagingSource<Int, RowEntity>

    /**
     * One-shot (non-paged) variant of [queryRowsPaging] for callers that need the full result
     * set at once. The query must project `row_entity` columns.
     */
    @RawQuery(observedEntities = [RowEntity::class, CellEntity::class])
    suspend fun queryRowsList(query: SupportSQLiteQuery): List<RowEntity>

    /**
     * Group-by summary over cell values (Phase 3). The caller supplies a query projecting
     * `value` and `count` columns (see [GroupBucket]); only [CellEntity] is observed because the
     * summary reads solely from the cell table.
     */
    @RawQuery(observedEntities = [CellEntity::class])
    suspend fun groupSummary(query: SupportSQLiteQuery): List<GroupBucket>

    /**
     * Grouped calculation (SUM/AVG/MIN/MAX/COUNT) — the caller supplies a query projecting
     * `label` and `amount` (see [GroupAggregate]). Reads only the cell table.
     */
    @RawQuery(observedEntities = [CellEntity::class])
    suspend fun aggregate(query: SupportSQLiteQuery): List<GroupAggregate>
}
