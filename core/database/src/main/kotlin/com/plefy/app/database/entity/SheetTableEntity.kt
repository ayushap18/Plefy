package com.plefy.app.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Root entity describing an imported spreadsheet ("table").
 *
 * A [SheetTableEntity] owns a set of [ColumnDefEntity] column definitions and a set of
 * [RowEntity] rows; deleting a table cascades to both (and, transitively, to their cells).
 *
 * @property id auto-generated primary key.
 * @property name human-readable display name of the imported table.
 * @property sourceUri the original content/file URI the data was imported from, if known.
 * @property sheetName the sheet/tab name within the source workbook, if applicable.
 * @property rowCount number of data rows persisted for this table.
 * @property importedAt import timestamp in epoch milliseconds.
 */
@Entity(tableName = "sheet_table")
data class SheetTableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sourceUri: String? = null,
    val sheetName: String? = null,
    val rowCount: Int = 0,
    val importedAt: Long = 0
)
