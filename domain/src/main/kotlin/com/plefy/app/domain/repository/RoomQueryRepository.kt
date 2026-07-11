package com.plefy.app.domain.repository

import androidx.paging.PagingSource
import com.plefy.app.database.AppDatabase
import com.plefy.app.database.dao.GroupBucket
import com.plefy.app.database.entity.CellEntity
import com.plefy.app.database.entity.RowEntity
import com.plefy.app.domain.query.QuerySpec
import com.plefy.app.domain.query.RawQueryFactory
import com.plefy.app.domain.query.SqlQueryBuilder

/**
 * Room-backed [QueryRepository]. Builds SQL with [SqlQueryBuilder], wraps it with [RawQueryFactory],
 * and dispatches to the `@RawQuery` methods on [AppDatabase.rowDao] and the plain query on
 * [AppDatabase.cellDao].
 *
 * @param db the application database.
 */
class RoomQueryRepository(
    private val db: AppDatabase,
) : QueryRepository {

    private val builder = SqlQueryBuilder()

    override fun rows(spec: QuerySpec): PagingSource<Int, RowEntity> {
        val (sql, args) = builder.build(spec)
        return db.rowDao().queryRowsPaging(RawQueryFactory.create(sql, args))
    }

    override suspend fun rowsList(spec: QuerySpec): List<RowEntity> {
        val (sql, args) = builder.build(spec)
        return db.rowDao().queryRowsList(RawQueryFactory.create(sql, args))
    }

    override suspend fun groupSummary(tableId: Long, columnId: Long): List<GroupBucket> {
        val (sql, args) = builder.buildGroupSummary(tableId, columnId)
        return db.rowDao().groupSummary(RawQueryFactory.create(sql, args))
    }

    override suspend fun aggregate(spec: com.plefy.app.domain.query.AggregateSpec): List<com.plefy.app.database.dao.GroupAggregate> {
        val (sql, args) = builder.buildAggregate(spec)
        return db.rowDao().aggregate(RawQueryFactory.create(sql, args))
    }

    override suspend fun cells(rowId: Long): List<CellEntity> =
        db.cellDao().getForRow(rowId)
}
