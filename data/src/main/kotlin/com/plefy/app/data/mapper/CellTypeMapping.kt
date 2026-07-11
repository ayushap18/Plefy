package com.plefy.app.data.mapper

import com.plefy.app.model.CellType

/**
 * Pure, Android-free conversions between [CellType] and its persisted [String] form.
 *
 * Enums are stored in Room as their [Enum.name] so no Room `TypeConverter` is required.
 * Reading is defensive: an unknown or corrupt token degrades to [CellType.TEXT] rather than
 * throwing, so a schema/database drift can never crash a query.
 */
object CellTypeMapping {

    /** Serialises a [CellType] to the exact token stored in the database. */
    fun toStorage(type: CellType): String = type.name

    /**
     * Parses a stored token back into a [CellType].
     *
     * @param token the persisted value (may be `null` or unrecognised)
     * @return the matching [CellType], or [CellType.TEXT] when the token is null/unknown
     */
    fun fromStorage(token: String?): CellType {
        if (token.isNullOrEmpty()) return CellType.TEXT
        return try {
            enumValueOf<CellType>(token)
        } catch (_: IllegalArgumentException) {
            CellType.TEXT
        }
    }
}

/** Convenience extension mirroring [CellTypeMapping.toStorage]. */
fun CellType.toStorage(): String = CellTypeMapping.toStorage(this)

/** Convenience extension mirroring [CellTypeMapping.fromStorage]. */
fun String?.toCellType(): CellType = CellTypeMapping.fromStorage(this)
