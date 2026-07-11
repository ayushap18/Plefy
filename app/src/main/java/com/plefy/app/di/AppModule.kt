package com.plefy.app.di

import android.content.Context
import androidx.work.WorkManager
import com.plefy.app.data.repository.RoomSheetRepository
import com.plefy.app.data.repository.SheetRepository
import com.plefy.app.database.AppDatabase
import com.plefy.app.domain.repository.QueryRepository
import com.plefy.app.domain.repository.RoomQueryRepository
import com.plefy.app.domain.usecase.BuildDefaultViewUseCase
import com.plefy.app.domain.usecase.GetRowCellsUseCase
import com.plefy.app.domain.usecase.QueryRowsUseCase
import com.plefy.app.feature.importer.ImportScheduler
import com.plefy.app.feature.importer.WorkManagerImportScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The single, centralized Hilt module for the whole app.
 *
 * Per the Phase-4 cross-module contract, dependency injection lives entirely in `:app` — the
 * feature and library modules stay Hilt-provider-free and only declare `@HiltViewModel` /
 * `@HiltWorker` consumers plus `@Inject` constructors. This module supplies everything those
 * consumers need, all scoped to the [SingletonComponent] (process-wide) so the database,
 * repositories, and WorkManager are shared singletons.
 *
 * The four query use cases and [BuildDefaultViewUseCase] are intentionally left unscoped: they are
 * cheap, stateless wrappers, so a fresh instance per injection is fine and avoids pinning them in
 * the singleton component.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** The process-wide Room database, file-backed via [AppDatabase.build]. */
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        AppDatabase.build(context)

    /** The data-layer facade for importing, observing, and deleting sheets. */
    @Provides
    @Singleton
    fun provideSheetRepository(db: AppDatabase): SheetRepository =
        RoomSheetRepository(db)

    /** The domain-layer read side backing the sort/filter/search/group engine. */
    @Provides
    @Singleton
    fun provideQueryRepository(db: AppDatabase): QueryRepository =
        RoomQueryRepository(db)

    /** Paged rows for a `QuerySpec`. */
    @Provides
    fun provideQueryRowsUseCase(repository: QueryRepository): QueryRowsUseCase =
        QueryRowsUseCase(repository)

    /** All cells of a single row. */
    @Provides
    fun provideGetRowCellsUseCase(repository: QueryRepository): GetRowCellsUseCase =
        GetRowCellsUseCase(repository)

    /** Pure builder of the smart default view for a freshly imported table. */
    @Provides
    fun provideBuildDefaultViewUseCase(): BuildDefaultViewUseCase =
        BuildDefaultViewUseCase()

    /** The app's [WorkManager], used to enqueue and observe background import work. */
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    /** WorkManager-backed scheduler that the Library screen uses to run background imports. */
    @Provides
    @Singleton
    fun provideImportScheduler(
        @ApplicationContext context: Context,
        workManager: WorkManager,
    ): ImportScheduler =
        WorkManagerImportScheduler(context, workManager)
}
