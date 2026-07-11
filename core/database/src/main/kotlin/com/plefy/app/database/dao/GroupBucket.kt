package com.plefy.app.database.dao

import androidx.room.ColumnInfo

/**
 * Room projection POJO for a group-by summary bucket (Phase 3).
 *
 * Produced by `SELECT rawValue AS value, COUNT(*) AS count ...` queries that summarise how many
 * cells share each distinct value within a column. [value] is nullable because [com.plefy.app
 * .database.entity.CellEntity.rawValue] is nullable (an empty/absent cell groups under `null`).
 */
data class GroupBucket(
    @ColumnInfo(name = "value") val value: String?,
    @ColumnInfo(name = "count") val count: Int
)
