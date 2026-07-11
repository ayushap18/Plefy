package com.plefy.app.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Column definition for a persisted table, mirroring
 * [com.plefy.app.model.InferredColumn].
 *
 * @property id auto-generated primary key.
 * @property tableId owning [SheetTableEntity.id].
 * @property colIndex zero-based column position (named `colIndex` because `index` is a SQL
 *   reserved keyword).
 * @property name human-readable column name.
 * @property type the inferred [com.plefy.app.model.CellType], stored as its `.name` string.
 * @property format optional rendering/format hint (e.g. a date pattern or currency symbol).
 * @property confidence inference confidence in `0.0`..`1.0`.
 */
@Entity(
    tableName = "column_def",
    foreignKeys = [
        ForeignKey(
            entity = SheetTableEntity::class,
            parentColumns = ["id"],
            childColumns = ["tableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tableId")]
)
data class ColumnDefEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableId: Long,
    val colIndex: Int,
    val name: String,
    val type: String,
    val format: String? = null,
    val confidence: Double = 0.0
)
