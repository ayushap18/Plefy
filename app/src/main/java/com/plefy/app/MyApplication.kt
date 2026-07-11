package com.plefy.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point and Hilt root.
 *
 * [@HiltAndroidApp][HiltAndroidApp] generates the application-level Dagger component that every
 * `@AndroidEntryPoint` / `@HiltViewModel` / `@HiltWorker` in the app hangs off of.
 *
 * It also implements [Configuration.Provider] so that WorkManager is initialised **on demand** with
 * a [HiltWorkerFactory]. That factory lets Hilt construct `@HiltWorker` workers (such as the
 * spreadsheet `ImportWorker` in `:feature:import`) with their `@AssistedInject` dependencies. The
 * default `androidx.startup` WorkManager initializer is removed in the manifest so this custom
 * configuration is the one that takes effect.
 */
@HiltAndroidApp
class MyApplication : Application(), Configuration.Provider {

    /** Hilt-provided factory that knows how to build every `@HiltWorker` in the graph. */
    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()
}
