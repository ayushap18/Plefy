package com.plefy.app.database.dao

import androidx.room.ColumnInfo

/**
 * One row of a grouped calculation: the category [label] and the computed [amount]
 * (SUM/AVG/MIN/MAX/COUNT). [amount] is nullable because AVG/MIN/MAX over an all-null value column
 * yields SQL NULL.
 */
data class GroupAggregate(
    @ColumnInfo(name = "label") val label: String?,
    @ColumnInfo(name = "amount") val amount: Double?
)
