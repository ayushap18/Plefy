package com.plefy.app.data.repository

import androidx.paging.PagingSource
import com.plefy.app.common.AppResult
import com.plefy.app.database.entity.CellEntity
import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.database.entity.RowEntity
import com.plefy.app.database.entity.SheetTableEntity
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * On-device persistence facade for imported spreadsheets.
 *
 * All I/O-bound members are `suspend` and run on the repository's injected dispatcher; the
 * observation and paging members return cold streams that Room drives on its own executors.
 */
interface SheetRepository {

    /**
     * Reads, infers, and persists a spreadsheet in a single pipeline.
     *
     * @param input       the raw workbook bytes (CSV / XLS / XLSX); consumed but not closed here
     * @param fileName     the original file name, used to pick the right reader
     * @param displayName the human-facing name to store for the sheet
     * @param onProgress optional callback invoked with a `0f..1f` fraction as body rows are
     *   persisted, at most once per committed chunk; `null` to skip progress reporting
     * @return [AppResult.Success] with the new sheet's table id, or [AppResult.Failure] describing
     *   why the import failed (unsupported format, empty sheet, parse error, or I/O error)
     */
    suspend fun importSpreadsheet(
        input: InputStream,
        fileName: String,
        displayName: String,
        onProgress: (suspend (Float) -> Unit)? = null,
    ): AppResult<Long>

    /** Observes every imported sheet, newest state emitted on any change. */
    fun observeSheets(): Flow<List<SheetTableEntity>>

    /** Returns the ordered column definitions for [tableId]. */
    suspend fun getColumns(tableId: Long): List<ColumnDefEntity>

    /** A [PagingSource] over the rows of [tableId], ordered by their sheet position. */
    fun rowPagingSource(tableId: Long): PagingSource<Int, RowEntity>

    /** Returns all cells belonging to [rowId]. */
    suspend fun getCells(rowId: Long): List<CellEntity>

    /** Deletes a sheet and, by cascade, its columns, rows, and cells. */
    suspend fun deleteSheet(tableId: Long)
}
