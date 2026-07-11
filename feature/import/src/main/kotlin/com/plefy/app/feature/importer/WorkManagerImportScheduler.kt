package com.plefy.app.feature.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.withContext

/**
 * [ImportScheduler] backed by WorkManager.
 *
 * Owns everything framework-shaped about starting an import: persisting the SAF read grant,
 * resolving a friendly display name off the main thread, enqueuing the [ImportWorker], and mapping
 * the resulting [WorkInfo] stream into [ImportStatus]. Provided as a singleton from `:app`'s Hilt
 * module.
 */
class WorkManagerImportScheduler(
    private val context: Context,
    private val workManager: WorkManager,
) : ImportScheduler {

    override fun enqueue(uri: Uri): Flow<ImportStatus> = flow {
        // Signal in-flight immediately for a snappy UI, before the (blocking) content-resolver I/O.
        emit(ImportStatus.Running())

        val request = withContext(Dispatchers.IO) {
            // The picker grants read access for this process; persist it so the worker (and any
            // later re-open) can still read the document. Not every provider supports this — ignore
            // refusals rather than failing the import.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val displayName = resolveDisplayName(uri)
            OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(
                    workDataOf(
                        ImportWorker.KEY_URI to uri.toString(),
                        ImportWorker.KEY_DISPLAY_NAME to displayName,
                        ImportWorker.KEY_FILE_NAME to displayName,
                    ),
                )
                .build()
        }

        workManager.enqueue(request)

        // Mirror the worker's terminal state into a single status, then complete the flow.
        emitAll(
            workManager.getWorkInfoByIdFlow(request.id).transformWhile { info ->
                when (info?.state) {
                    WorkInfo.State.SUCCEEDED -> {
                        emit(ImportStatus.Succeeded)
                        false
                    }
                    WorkInfo.State.FAILED -> {
                        emit(
                            ImportStatus.Failed(
                                info.outputData.getString(ImportWorker.KEY_ERROR) ?: "Import failed.",
                            ),
                        )
                        false
                    }
                    WorkInfo.State.CANCELLED -> {
                        emit(ImportStatus.Failed("Import cancelled."))
                        false
                    }
                    else -> {
                        // null / ENQUEUED / RUNNING / BLOCKED: still in flight. Surface the worker's
                        // reported fraction when present, else stay indeterminate (null).
                        val fraction = info?.progress?.getFloat(ImportWorker.KEY_FRACTION, -1f) ?: -1f
                        emit(ImportStatus.Running(fraction = fraction.takeIf { it >= 0f }))
                        true
                    }
                }
            },
        )
    }

    /**
     * Resolves a friendly display name for [uri], preferring the provider's
     * [OpenableColumns.DISPLAY_NAME] and falling back to the URI's last path segment.
     */
    private fun resolveDisplayName(uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else {
                        null
                    }
                }
        }.getOrNull()

        return fromProvider
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Spreadsheet"
    }
}
