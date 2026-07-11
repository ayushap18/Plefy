package com.plefy.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.plefy.app.database.dao.CellDao
import com.plefy.app.database.dao.ColumnDao
import com.plefy.app.database.dao.RowDao
import com.plefy.app.database.dao.SheetTableDao
import com.plefy.app.database.entity.CellEntity
import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.database.entity.RowEntity
import com.plefy.app.database.entity.SheetTableEntity

/**
 * The Room database for the on-device persistence layer.
 *
 * Stores imported sheets in an EAV layout: [SheetTableEntity] (table) owns [ColumnDefEntity]
 * definitions and [RowEntity] rows, and each row owns [CellEntity] values carrying pre-computed
 * typed sort keys. Foreign keys cascade on delete, so removing a table removes everything it owns.
 *
 * Schema is not exported and destructive migration is used while the schema is pre-1.0; bump
 * [Room database version][Database.version] and add real migrations before shipping data users
 * cannot afford to lose.
 */
@Database(
    entities = [
        SheetTableEntity::class,
        ColumnDefEntity::class,
        RowEntity::class,
        CellEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    /** DAO for imported tables. */
    abstract fun sheetTableDao(): SheetTableDao

    /** DAO for column definitions. */
    abstract fun columnDao(): ColumnDao

    /** DAO for rows. */
    abstract fun rowDao(): RowDao

    /** DAO for cell values. */
    abstract fun cellDao(): CellDao

    companion object {
        private const val DATABASE_NAME = "excel.db"

        /**
         * Builds the persistent, file-backed database. Foreign-key enforcement is enabled by
         * Room automatically for entities declaring foreign keys. Uses destructive migration as
         * a fallback while the schema is unstable.
         */
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration(true)
                .build()

        /**
         * Builds an in-memory database that is cleared when the process dies. Intended for tests
         * and ephemeral use; queries are allowed to run on the main thread for convenience.
         */
        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
    }
}
