package com.plefy.app.feature.importer

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.plefy.app.database.entity.SheetTableEntity

/**
 * The Library screen: lists every imported spreadsheet and lets the user import a new one via the
 * system file picker (SAF). Each row opens its viewer; swiping a row (or tapping its trailing
 * delete icon) removes it. Import progress and failures are surfaced through a top progress bar and
 * a snackbar respectively.
 *
 * @param onOpenSheet invoked with a table id when the user taps a sheet.
 * @param viewModel supplied by Hilt; overridable in previews/tests.
 */
@Composable
fun LibraryScreen(
    onOpenSheet: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LibraryScreen(
        state = state,
        onOpenSheet = onOpenSheet,
        onFilePicked = viewModel::onFilePicked,
        onDeleteSheet = viewModel::deleteSheet,
        onErrorShown = viewModel::onErrorShown,
    )
}

/**
 * Stateless Library UI. Separated from the ViewModel-bound overload so it can be previewed and
 * unit-tested without Hilt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    state: LibraryUiState,
    onOpenSheet: (Long) -> Unit,
    onFilePicked: (android.net.Uri) -> Unit,
    onDeleteSheet: (Long) -> Unit,
    onErrorShown: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // Launches the Storage Access Framework document picker for spreadsheet MIME types.
    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onFilePicked) }

    // Single import entry point, reused by the FAB and the empty-state button.
    val onImport = { pickFile.launch(SPREADSHEET_MIME_TYPES) }

    // Surface the latest import error as a snackbar, then let the ViewModel clear it.
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onErrorShown()
    }

    Scaffold(
        topBar = { TopAppBar(title = {}) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onImport() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Import") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Determinate/indeterminate import progress, tucked cleanly under the app bar.
            AnimatedVisibility(visible = state.importing) {
                val fraction = state.progress
                if (fraction != null) {
                    // Animate toward the reported fraction so the bar glides instead of jumping
                    // between the throttled whole-percent updates from the worker.
                    val animated by animateFloatAsState(
                        targetValue = fraction,
                        label = "importProgress",
                    )
                    LinearProgressIndicator(
                        progress = { animated },
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.sheets.isEmpty() && !state.importing) {
                EmptyState(onImport = { onImport() })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Serif editorial hero, carrying the section title + a muted summary line.
                    item {
                        LibraryHeader(sheets = state.sheets)
                    }
                    items(items = state.sheets, key = { it.id }) { sheet ->
                        SwipeableSheetRow(
                            sheet = sheet,
                            onOpen = { onOpenSheet(sheet.id) },
                            onDelete = { onDeleteSheet(sheet.id) },
                        )
                    }
                }
            }
        }
    }
}

/** Serif hero title + a muted summary of how many sheets/rows are on file. */
@Composable
private fun LibraryHeader(sheets: List<SheetTableEntity>) {
    val totalRows = sheets.sumOf { it.rowCount }
    Column(modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)) {
        Text(
            text = "Spreadsheets",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${sheets.size} ${if (sheets.size == 1) "sheet" else "sheets"} · $totalRows total rows",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A sheet row that can be swiped away to delete, or opened/deleted via taps. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSheetRow(
    sheet: SheetTableEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            if (target != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else {
                false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        SheetRow(sheet = sheet, onOpen = onOpen, onDelete = onDelete)
    }
}

/** A single sheet feature-card: leading tonal glyph, name, a badge-pill row, and trailing actions. */
@Composable
private fun SheetRow(
    sheet: SheetTableEntity,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Leading tonal circle with a table/list glyph.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sheet.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary),
                    )
                    RowCountPill(count = sheet.rowCount)
                    Text(
                        text = relativeImportTime(sheet.importedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = "Open",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpen),
            )

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete ${sheet.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A pill badge showing the row count. */
@Composable
private fun RowCountPill(count: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = "$count rows",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Shown when there are no imported sheets yet. */
@Composable
private fun EmptyState(onImport: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.size(96.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
            Text(
                text = "No spreadsheets yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Import a CSV or Excel file to explore, sort, and chart your data.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onImport,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import spreadsheet")
            }
        }
    }
}

/** Muted, human-readable "x ago" caption for an epoch-millis import timestamp. */
private fun relativeImportTime(importedAt: Long): String =
    DateUtils.getRelativeTimeSpanString(
        importedAt,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()

/**
 * MIME types offered to the SAF picker. The wildcard type is included last so that providers which
 * mis-report a spreadsheet's type (common for cloud/CSV files) are still selectable; the actual
 * format is validated downstream by the import pipeline.
 */
private val SPREADSHEET_MIME_TYPES = arrayOf(
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/vnd.ms-excel",
    "text/csv",
    "text/comma-separated-values",
    "*/*",
)
