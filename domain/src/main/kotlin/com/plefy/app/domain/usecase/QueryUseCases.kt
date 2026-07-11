package com.plefy.app.domain.usecase

import androidx.paging.PagingSource
import com.plefy.app.database.entity.CellEntity
import com.plefy.app.database.entity.RowEntity
import com.plefy.app.domain.query.QuerySpec
import com.plefy.app.domain.repository.QueryRepository

/**
 * Thin single-responsibility wrappers around [QueryRepository]. They exist so the presentation
 * layer depends on small, intention-revealing operations rather than the whole repository, and so
 * cross-cutting concerns can be added later without touching callers.
 */

/** Returns a Paging source of rows matching a [QuerySpec]. */
class QueryRowsUseCase(private val repository: QueryRepository) {
    operator fun invoke(spec: QuerySpec): PagingSource<Int, RowEntity> = repository.rows(spec)
}

/** Returns every cell of a single row. */
class GetRowCellsUseCase(private val repository: QueryRepository) {
    suspend operator fun invoke(rowId: Long): List<CellEntity> = repository.cells(rowId)
}
