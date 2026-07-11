package com.plefy.app.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single cell (the value at the intersection of a [RowEntity] and a [ColumnDefEntity]).
 *
 * To support typed ordering later (Phase 3), the cell carries pre-computed sort keys:
 * exactly one of [numericSort] / [textSort] is populated for sortable types, both are
 * `null` for empty cells. See the mapper for the exact rule. [rawValue] always holds the
 * original display string (or `null`).
 *
 * The composite indices `(columnId, numericSort)` and `(columnId, textSort)` let a query
 * fetch a single column already ordered by its typed sort key.
 *
 * @property id auto-generated primary key.
 * @property rowId owning [RowEntity.id].
 * @property columnId the [ColumnDefEntity.id] this value belongs to.
 * @property rawValue original display string, or `null` when the source cell was empty.
 * @property numericSort numeric sort key for boolean/number/date types; `null` otherwise.
 * @property textSort lower-cased text sort key for text type; `null` otherwise.
 * @property type the [com.plefy.app.model.CellType] name for this cell.
 */
@Entity(
    tableName = "cell_entity",
    foreignKeys = [
        ForeignKey(
            entity = RowEntity::class,
            parentColumns = ["id"],
            childColumns = ["rowId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("columnId", "numericSort"),
        Index("columnId", "textSort"),
        Index("rowId")
    ]
)
data class CellEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rowId: Long,
    val columnId: Long,
    val rawValue: String? = null,
    val numericSort: Double? = null,
    val textSort: String? = null,
    val type: String
)
