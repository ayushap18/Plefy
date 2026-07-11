package com.plefy.app.feature.importer

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.plefy.app.common.AppResult
import com.plefy.app.data.repository.SheetRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker that runs the spreadsheet import pipeline off the main thread.
 *
 * The heavy work (reading the workbook stream, inferring the schema, and persisting rows) is
 * delegated to [SheetRepository.importSpreadsheet]. Running inside WorkManager keeps the import
 * alive across configuration changes and process death, and lets the UI observe its progress via
 * [WorkInfo][androidx.work.WorkInfo].
 *
 * Constructed by Hilt's [androidx.hilt.work.HiltWorkerFactory]; the [SheetRepository] is injected
 * while the [Context] and [WorkerParameters] are supplied by WorkManager at runtime.
 */
@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val sheetRepository: SheetRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_URI)
            ?: return Result.failure(workDataOf(KEY_ERROR to "No file URI was provided."))
        val uri = Uri.parse(uriString)

        // Prefer the caller-supplied names, falling back to the URI's last path segment.
        val displayName = inputData.getString(KEY_DISPLAY_NAME)
            ?: uri.lastPathSegment
            ?: "Spreadsheet"
        val fileName = inputData.getString(KEY_FILE_NAME) ?: displayName

        return try {
            val stream = appContext.contentResolver.openInputStream(uri)
                ?: return Result.failure(workDataOf(KEY_ERROR to "Could not open the selected file."))

            // Publish progress via WorkInfo, but only when the whole-percent changes — WorkManager
            // persists every setProgress, so throttling avoids a write per 500-row chunk.
            var lastPercent = -1
            val onProgress: suspend (Float) -> Unit = { fraction ->
                val percent = (fraction * 100).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    setProgress(workDataOf(KEY_FRACTION to fraction))
                }
            }

            // `use` guarantees the stream is closed even if the pipeline throws.
            stream.use { input ->
                when (val result = sheetRepository.importSpreadsheet(input, fileName, displayName, onProgress)) {
                    is AppResult.Success ->
                        Result.success(workDataOf(KEY_TABLE_ID to result.value))
                    is AppResult.Failure ->
                        Result.failure(workDataOf(KEY_ERROR to result.error.message))
                }
            }
        } catch (t: Throwable) {
            Result.failure(workDataOf(KEY_ERROR to (t.message ?: "Import failed.")))
        }
    }

    companion object {
        /** Input: the content URI (as a string) of the spreadsheet to import. */
        const val KEY_URI = "uri"

        /** Input: the human-facing display name to store for the sheet. */
        const val KEY_DISPLAY_NAME = "displayName"

        /** Input: the original file name, used by the repository to pick the right reader. */
        const val KEY_FILE_NAME = "fileName"

        /** Progress: the import completion fraction in `0f..1f`. */
        const val KEY_FRACTION = "fraction"

        /** Output (success): the newly created table id. */
        const val KEY_TABLE_ID = "tableId"

        /** Output (failure): a human-readable failure message. */
        const val KEY_ERROR = "error"
    }
}
