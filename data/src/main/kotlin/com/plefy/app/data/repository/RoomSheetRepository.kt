package com.plefy.app.data.repository

import androidx.paging.PagingSource
import androidx.room.withTransaction
import com.plefy.app.backend.inference.CellNormalizer
import com.plefy.app.backend.inference.SchemaInferenceEngine
import com.plefy.app.backend.parser.NamedSheet
import com.plefy.app.backend.parser.WorkbookReaderFactory
import com.plefy.app.common.AppError
import com.plefy.app.common.AppResult
import com.plefy.app.data.mapper.CellNormalizeFn
import com.plefy.app.data.mapper.ColumnMapper
import com.plefy.app.data.mapper.RowMapper
import com.plefy.app.database.AppDatabase
import com.plefy.app.database.entity.CellEntity
import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.database.entity.RowEntity
import com.plefy.app.database.entity.SheetTableEntity
import com.plefy.app.model.RawRow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream

/**
 * Room-backed [SheetRepository] implementing the full import pipeline over the EAV schema.
 *
 * The import path runs entirely on [ioDispatcher]:
 * 1. [WorkbookReaderFactory] picks a `SheetReader` from the file name and content magic bytes.
 * 2. The reader produces `List<RawRow>`.
 * 3. [SchemaInferenceEngine] detects the header and infers typed columns.
 * 4. A [SheetTableEntity] is inserted to obtain the table id.
 * 5. Columns are mapped and inserted; their generated ids are captured.
 * 6. Body rows and their cells are mapped and inserted in chunked transactions, keeping memory
 *    bounded and each commit small.
 * 7. The sheet's `rowCount` is finalised.
 *
 * Any failure is translated to the most specific [AppError] and returned as [AppResult.Failure];
 * the pipeline never throws to the caller.
 *
 * @param db           the Room database exposing the sheet/column/row/cell DAOs
 * @param ioDispatcher the dispatcher for all blocking work (defaults to [Dispatchers.IO])
 */
class RoomSheetRepository(
    private val db: AppDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SheetRepository {

    private val sheetDao = db.sheetTableDao()
    private val columnDao = db.columnDao()
    private val rowDao = db.rowDao()
    private val cellDao = db.cellDao()

    private val inferenceEngine = SchemaInferenceEngine()

    /** Adapts the `:backend:inference` normaliser to the mapper's pure function type. */
    private val normalize: CellNormalizeFn =
        { raw, type -> CellNormalizer.normalize(raw, type) }

    override suspend fun importSpreadsheet(
        input: InputStream,
        fileName: String,
        displayName: String,
        onProgress: (suspend (Float) -> Unit)?,
    ): AppResult<Long> = withContext(ioDispatcher) {
        try {
            // The factory peeks magic bytes, so the stream must support mark/reset.
            val stream: InputStream =
                if (input.markSupported()) input else BufferedInputStream(input)

            // Every sheet of the workbook is imported as its own table, so multi-sheet files no
            // longer silently drop all but the first tab.
            val sheets = readAllSheets(stream, fileName).filter { it.rows().isNotEmpty() }
            if (sheets.isEmpty()) {
                return@withContext AppResult.Failure(AppError.EmptySheet("The file contains no rows."))
            }
            val multiSheet = sheets.size > 1

            // Aggregate progress across every sheet: cumulative rows persisted / total rows.
            // ponytail: denominator is raw rows (incl. header rows), so the fraction tops out a
            // hair under 1.0 on header-bearing sheets — invisible next to the terminal Succeeded.
            val totalRows = sheets.sumOf { it.rows().size }.coerceAtLeast(1)
            var completedRows = 0

            var firstTableId: Long? = null
            for (sheet in sheets) {
                val tableName =
                    if (multiSheet && sheet.name() != null) "$displayName — ${sheet.name()}"
                    else displayName
                val base = completedRows
                val id = persistSheet(tableName, sheet.name(), sheet.rows()) { rowsInSheet ->
                    onProgress?.invoke(((base + rowsInSheet).toFloat() / totalRows).coerceIn(0f, 1f))
                }
                completedRows += sheet.rows().size
                if (id != null && firstTableId == null) firstTableId = id
            }

            firstTableId?.let { AppResult.Success(it) }
                ?: AppResult.Failure(AppError.EmptySheet("No columns found in any sheet."))
        } catch (io: IOException) {
            AppResult.Failure(AppError.IoError(io.message ?: "I/O error while importing the sheet."))
        } catch (t: Throwable) {
            AppResult.Failure(classify(t))
        }
    }

    override fun observeSheets(): Flow<List<SheetTableEntity>> = sheetDao.getAll()

    override suspend fun getColumns(tableId: Long): List<ColumnDefEntity> =
        withContext(ioDispatcher) { columnDao.getForTableOnce(tableId) }

    override fun rowPagingSource(tableId: Long): PagingSource<Int, RowEntity> =
        rowDao.pagingSource(tableId)

    override suspend fun getCells(rowId: Long): List<CellEntity> =
        withContext(ioDispatcher) { cellDao.getForRow(rowId) }

    override suspend fun deleteSheet(tableId: Long) =
        withContext(ioDispatcher) { sheetDao.deleteById(tableId) }

    /** Selects a reader by name + content and reads the default sheet's raw rows. */
    /** Reads every sheet of the source, choosing the reader by file name + content magic bytes. */
    private fun readAllSheets(stream: InputStream, fileName: String): List<NamedSheet> =
        WorkbookReaderFactory.forName(fileName, stream).readAll(stream)

    /**
     * Infers types, then persists one sheet as its own table (columns + batched rows/cells).
     * Returns the new table id, or `null` when the sheet has no inferable columns.
     */
    private suspend fun persistSheet(
        tableName: String,
        sheetName: String?,
        rawRows: List<RawRow>,
        onChunk: (suspend (rowsPersisted: Int) -> Unit)? = null,
    ): Long? {
        val outcome = inferenceEngine.infer(rawRows)
        val columns = outcome.columns
        if (columns.isEmpty()) return null

        val bodyRows = if (outcome.hasHeader()) rawRows.drop(1) else rawRows
        val tableId = sheetDao.insert(
            SheetTableEntity(
                name = tableName,
                sourceUri = null,
                sheetName = sheetName,
                rowCount = 0,
                importedAt = System.currentTimeMillis(),
            ),
        )
        val columnIds = columnDao.insertAll(ColumnMapper.toEntities(columns, tableId))

        var rowIndex = 0
        bodyRows.chunked(ROW_CHUNK).forEach { chunk ->
            db.withTransaction {
                // Batch the whole chunk: 2 suspend inserts instead of one round-trip per row.
                val mapped = chunk.map { rawRow ->
                    RowMapper.mapRow(rawRow, tableId, rowIndex++, columns, columnIds, normalize)
                }
                val rowIds = rowDao.insertAll(mapped.map { it.row })
                cellDao.insertAll(mapped.flatMapIndexed { i, m -> m.withRowId(rowIds[i]).cells })
            }
            // After the commit, rowIndex == cumulative body rows persisted for this sheet.
            onChunk?.invoke(rowIndex)
        }
        sheetDao.updateRowCount(tableId, bodyRows.size)
        return tableId
    }

    /**
     * Maps a non-I/O throwable to the most specific [AppError].
     *
     * `:backend:parser` surfaces unsupported-container problems via message text (POI's
     * "Your InputStream was neither…", "unsupported", "not a valid…"); anything else that reaches
     * here is treated as a parse error.
     */
    private fun classify(t: Throwable): AppError {
        val message = t.message ?: t.cause?.message ?: t.javaClass.simpleName
        val lower = message.lowercase()
        return when {
            t is IOException -> AppError.IoError(message)
            lower.contains("unsupported") ||
                lower.contains("neither") ||
                lower.contains("not a valid") ||
                lower.contains("invalid header") ||
                lower.contains("unknown format") -> AppError.UnsupportedFormat(message)
            else -> AppError.ParseError(message)
        }
    }

    private companion object {
        /** Rows (with their cells) committed per transaction; bounds memory and commit size. */
        // Larger chunks = far fewer WAL commits/fsyncs (the dominant DB-insert cost). 2000 rows x
        // ~12 cells stays well within memory even for huge sheets. ponytail: bigger only helps up to
        // the fsync-amortisation point; measure before pushing higher.
        const val ROW_CHUNK = 2000
    }
}
