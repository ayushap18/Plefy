package com.plefy.app.domain.repository

import androidx.paging.PagingSource
import com.plefy.app.database.dao.GroupAggregate
import com.plefy.app.database.dao.GroupBucket
import com.plefy.app.database.entity.CellEntity
import com.plefy.app.database.entity.RowEntity
import com.plefy.app.domain.query.AggregateSpec
import com.plefy.app.domain.query.QuerySpec

/**
 * The read side of the sort / filter / search / group engine. Implementations translate a
 * [QuerySpec] into SQLite over the EAV schema and return rows (as Paging results or a materialised
 * list), group summaries, and the cells of a single row.
 */
interface QueryRepository {

    /**
     * A Paging 3 source of rows matching [spec]. Backed by Room's `@RawQuery` paging so the UI can
     * scroll arbitrarily large, sorted/filtered result sets without materialising them.
     */
    fun rows(spec: QuerySpec): PagingSource<Int, RowEntity>

    /** Materialises every row matching [spec] into a list (tests / small result sets / export). */
    suspend fun rowsList(spec: QuerySpec): List<RowEntity>

    /**
     * Distinct values in a column with their row counts, most frequent first — powering group
     * headers and category pickers.
     */
    suspend fun groupSummary(tableId: Long, columnId: Long): List<GroupBucket>

    /**
     * A grouped calculation (COUNT/SUM/AVG/MIN/MAX of a value column per category value, over the
     * rows passing the spec's filters), most significant first — powering the chart's calculation
     * mode.
     */
    suspend fun aggregate(spec: AggregateSpec): List<GroupAggregate>

    /** All cell values belonging to a single row. */
    suspend fun cells(rowId: Long): List<CellEntity>
}
