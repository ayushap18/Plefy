package com.plefy.app.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single data row within a persisted table.
 *
 * Cell values are stored separately as [CellEntity] rows (an EAV layout) so that columns
 * can be sorted/filtered by typed sort keys without a wide, schema-rigid table.
 *
 * @property id auto-generated primary key.
 * @property tableId owning [SheetTableEntity.id].
 * @property rowIndex zero-based position of the row within the table (drives paging order).
 */
@Entity(
    tableName = "row_entity",
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
data class RowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tableId: Long,
    val rowIndex: Int
)
