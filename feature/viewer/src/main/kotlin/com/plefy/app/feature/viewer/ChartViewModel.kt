package com.plefy.app.feature.viewer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plefy.app.data.repository.SheetRepository
import com.plefy.app.database.dao.GroupAggregate
import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.domain.query.AggregateSpec
import com.plefy.app.domain.repository.QueryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the [ChartScreen] for a single imported table.
 *
 * The chart shows a grouped calculation over a user-picked category column via [aggregate], backed
 * by the query engine ([QueryRepository.aggregate]). The screen turns the results into a bar or pie.
 *
 * @property tableId the table this chart is bound to, read from [SavedStateHandle] like the viewer.
 */
@HiltViewModel
class ChartViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sheetRepository: SheetRepository,
    private val queryRepository: QueryRepository,
) : ViewModel() {

    /** The table id supplied via the `chart/{tableId}` navigation argument. */
    val tableId: Long = savedStateHandle.get<Long>(ARG_TABLE_ID)
        ?: error("ChartViewModel requires a '$ARG_TABLE_ID' argument")

    private val _columns = MutableStateFlow<List<ColumnDefEntity>>(emptyList())

    /** The table's columns in display order — the pool of pickable category columns. */
    val columns: StateFlow<List<ColumnDefEntity>> = _columns.asStateFlow()

    init {
        viewModelScope.launch { _columns.value = sheetRepository.getColumns(tableId) }
    }

    /**
     * Runs a grouped calculation (COUNT/SUM/AVG/MIN/MAX of a value column per category, over the
     * rows passing [spec]'s filters). Backs the chart's calculation + filter mode.
     */
    suspend fun aggregate(spec: AggregateSpec): List<GroupAggregate> =
        queryRepository.aggregate(spec)

    private companion object {
        const val ARG_TABLE_ID = "tableId"
    }
}
