package com.plefy.app.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import com.plefy.app.data.repository.SheetRepository
import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.domain.query.FilterOp
import com.plefy.app.domain.query.FilterSpec
import com.plefy.app.domain.query.GroupSpec
import com.plefy.app.domain.query.QuerySort
import com.plefy.app.domain.query.QuerySpec
import com.plefy.app.domain.query.SearchSpec
import com.plefy.app.domain.usecase.BuildDefaultViewUseCase
import com.plefy.app.domain.usecase.GetRowCellsUseCase
import com.plefy.app.domain.usecase.QueryRowsUseCase
import com.plefy.app.model.CellType
import com.plefy.app.model.SortDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the [ViewerScreen] for a single imported table.
 *
 * ## Data flow
 * The single source of truth for *what* is shown is [currentSpec], a [QuerySpec] the user mutates
 * through the intent methods ([setSort], [addFilter], [clearFilters], [setSearch], [setGroup]).
 * [rows] reacts to every spec change: [flatMapLatest] cancels the previous [Pager] and starts a
 * fresh one whenever the spec changes, so applying a sort/filter/search rebuilds the paged result
 * transparently. Each [com.plefy.app.database.entity.RowEntity] is hydrated into a
 * [RowUiModel] by fetching its cells and keying them by `columnId`.
 *
 * The initial spec is produced by [BuildDefaultViewUseCase] once the table's columns have loaded,
 * giving the user a sensible first view (typed default sort + optional grouping) they can refine.
 *
 * @property tableId the persisted table this viewer is bound to, read from [SavedStateHandle].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sheetRepository: SheetRepository,
    private val queryRowsUseCase: QueryRowsUseCase,
    private val getRowCellsUseCase: GetRowCellsUseCase,
    private val buildDefaultViewUseCase: BuildDefaultViewUseCase,
) : ViewModel() {

    /** The table id supplied via the `viewer/{tableId}` navigation argument. */
    val tableId: Long = savedStateHandle.get<Long>(ARG_TABLE_ID)
        ?: error("ViewerViewModel requires a '$ARG_TABLE_ID' argument")

    private val _columns = MutableStateFlow<List<ColumnDefEntity>>(emptyList())

    /** The table's column definitions in display order — used for headers and control pickers. */
    val columns: StateFlow<List<ColumnDefEntity>> = _columns.asStateFlow()

    // Seeded with a bare spec so [rows] can start emitting immediately; replaced with the
    // use-case-built default view as soon as columns resolve (see [init]).
    private val _currentSpec = MutableStateFlow(QuerySpec(tableId = tableId))

    /** The active query. Every mutation re-drives [rows]. */
    val currentSpec: StateFlow<QuerySpec> = _currentSpec.asStateFlow()

    /**
     * The paged, spec-driven rows for the table.
     *
     * Rebuilt on every [currentSpec] change and cached in [viewModelScope] so it survives
     * configuration changes and is shared across collectors.
     */
    val rows: Flow<PagingData<RowUiModel>> =
        currentSpec.flatMapLatest { spec ->
            Pager(PagingConfig(pageSize = PAGE_SIZE)) {
                queryRowsUseCase(spec)
            }.flow.map { paging ->
                paging.map { row ->
                    RowUiModel(
                        rowId = row.id,
                        cells = getRowCellsUseCase(row.id).associate { it.columnId to it.rawValue },
                    )
                }
            }
        }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            val loaded = sheetRepository.getColumns(tableId)
            _columns.value = loaded
            // Promote the placeholder spec to the inferred default view.
            _currentSpec.value = buildDefaultViewUseCase.build(tableId, loaded)
        }
    }

    /** Replaces the sort with a single key over [columnId], keeping every other facet. */
    fun setSort(columnId: Long, type: CellType, direction: SortDirection) {
        _currentSpec.update { it.copy(sorts = listOf(QuerySort(columnId, type, direction))) }
    }

    /** Appends a conjunctive [FilterSpec] to the active query. */
    fun addFilter(columnId: Long, type: CellType, op: FilterOp, value: String?, value2: String? = null) {
        val filter = FilterSpec(columnId = columnId, type = type, op = op, value = value, value2 = value2)
        _currentSpec.update { it.copy(filters = it.filters + filter) }
    }

    /** Removes every active filter. */
    fun clearFilters() {
        _currentSpec.update { it.copy(filters = emptyList()) }
    }

    /** Sets (or, on a blank [term], clears) the cross-column text search. */
    fun setSearch(term: String) {
        val search = term.trim().takeIf { it.isNotEmpty() }?.let { SearchSpec(it) }
        _currentSpec.update { it.copy(search = search) }
    }

    /** Groups by [columnId], or clears grouping when [columnId] is `null`. */
    fun setGroup(columnId: Long?) {
        _currentSpec.update { it.copy(group = columnId?.let { id -> GroupSpec(id) }) }
    }

    private companion object {
        const val ARG_TABLE_ID = "tableId"
        const val PAGE_SIZE = 50
    }
}
