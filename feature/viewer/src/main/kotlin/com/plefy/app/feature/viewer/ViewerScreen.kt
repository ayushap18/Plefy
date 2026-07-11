package com.plefy.app.feature.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.domain.query.FilterOp
import com.plefy.app.model.CellType
import com.plefy.app.model.SortDirection

/** Fixed width of every table cell; keeps header and body columns aligned under horizontal scroll. */
private val CellWidth = 148.dp

/** Cell types rendered right-aligned (numbers read better trailing-aligned). */
private val NumericTypes = setOf(CellType.INTEGER, CellType.DECIMAL, CellType.CURRENCY)

/**
 * The table viewer for one imported spreadsheet.
 *
 * Renders a horizontally scrollable, paged table (header row + [LazyColumn] of data rows) whose
 * contents are driven by the [ViewerViewModel]'s spec. A control-chip row exposes search, sort,
 * filter, and group controls that mutate that spec, plus a coral link into the chart screen;
 * tapping a row opens a [ModalBottomSheet] listing every column's value for the row.
 *
 * @param onBack invoked when the user taps the back control.
 * @param onOpenChart invoked with this table's id when the user taps the chart action.
 * @param viewModel supplied by Hilt; keyed to the `tableId` navigation argument.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    onBack: () -> Unit,
    onOpenChart: (Long) -> Unit,
    viewModel: ViewerViewModel = hiltViewModel(),
) {
    val columns by viewModel.columns.collectAsState()
    val spec by viewModel.currentSpec.collectAsState()
    val rows = viewModel.rows.collectAsLazyPagingItems()

    // Which control sheet (if any) is open, and which row's detail sheet is showing.
    var activeSheet by remember { mutableStateOf(ViewerSheet.NONE) }
    var detailRow by remember { mutableStateOf<RowUiModel?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Table", style = MaterialTheme.typography.headlineSmall) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                )
                SearchField(
                    term = spec.search?.term.orEmpty(),
                    onTermChange = viewModel::setSearch,
                )
                ControlChipRow(
                    filterCount = spec.filters.size,
                    grouped = spec.group != null,
                    onSort = { activeSheet = ViewerSheet.SORT },
                    onFilter = { activeSheet = ViewerSheet.FILTER },
                    onGroup = { activeSheet = ViewerSheet.GROUP },
                    onViewChart = { onOpenChart(viewModel.tableId) },
                )
            }
        },
    ) { padding ->
        ViewerTable(
            columns = columns,
            rows = rows,
            contentPadding = padding,
            onRowClick = { detailRow = it },
        )
    }

    when (activeSheet) {
        ViewerSheet.SORT -> SortSheet(
            columns = columns,
            onDismiss = { activeSheet = ViewerSheet.NONE },
            onConfirm = { column, direction ->
                viewModel.setSort(column.id, column.cellType(), direction)
                activeSheet = ViewerSheet.NONE
            },
        )

        ViewerSheet.FILTER -> FilterSheet(
            columns = columns,
            hasActiveFilters = spec.filters.isNotEmpty(),
            onDismiss = { activeSheet = ViewerSheet.NONE },
            onClear = {
                viewModel.clearFilters()
                activeSheet = ViewerSheet.NONE
            },
            onConfirm = { column, op, value ->
                viewModel.addFilter(column.id, column.cellType(), op, value.takeIf { it.isNotEmpty() })
                activeSheet = ViewerSheet.NONE
            },
        )

        ViewerSheet.GROUP -> GroupSheet(
            columns = columns,
            selectedColumnId = spec.group?.columnId,
            onDismiss = { activeSheet = ViewerSheet.NONE },
            onConfirm = { columnId ->
                viewModel.setGroup(columnId)
                activeSheet = ViewerSheet.NONE
            },
        )

        ViewerSheet.NONE -> Unit
    }

    detailRow?.let { row ->
        RowDetailSheet(
            row = row,
            columns = columns,
            sheetState = rememberModalBottomSheetState(),
            onDismiss = { detailRow = null },
        )
    }
}

/** Which control sheet is currently presented over the viewer. */
private enum class ViewerSheet { NONE, SORT, FILTER, GROUP }

/** Resolves a column's stored type string to its [CellType], defaulting to [CellType.TEXT]. */
private fun ColumnDefEntity.cellType(): CellType =
    runCatching { CellType.valueOf(type) }.getOrDefault(CellType.TEXT)

// --------------------------------------------------------------------------------------------
// Search + controls
// --------------------------------------------------------------------------------------------

@Composable
private fun SearchField(term: String, onTermChange: (String) -> Unit) {
    OutlinedTextField(
        value = term,
        onValueChange = onTermChange,
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        label = { Text("Search all columns") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

/**
 * The horizontal row of spec controls that replaces the old text-button top-bar actions.
 * "Filter" carries a count badge when active; "View chart" is the single coral link/action.
 */
@Composable
private fun ControlChipRow(
    filterCount: Int,
    grouped: Boolean,
    onSort: () -> Unit,
    onFilter: () -> Unit,
    onGroup: () -> Unit,
    onViewChart: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(onClick = onSort, label = { Text("Sort") })

        // Filter: amber/tertiary tonal when active, with the active count.
        AssistChip(
            onClick = onFilter,
            label = { Text(if (filterCount > 0) "Filter ($filterCount)" else "Filter") },
            colors = if (filterCount > 0) {
                AssistChipDefaults.assistChipColors(
                    containerColor = scheme.tertiaryContainer,
                    labelColor = scheme.onTertiaryContainer,
                )
            } else {
                AssistChipDefaults.assistChipColors()
            },
        )

        AssistChip(
            onClick = onGroup,
            label = { Text("Group") },
            colors = if (grouped) {
                AssistChipDefaults.assistChipColors(
                    containerColor = scheme.secondaryContainer,
                    labelColor = scheme.onSecondaryContainer,
                )
            } else {
                AssistChipDefaults.assistChipColors()
            },
        )

        // The one coral voltage action on this screen.
        AssistChip(
            onClick = onViewChart,
            label = { Text("View chart") },
            colors = AssistChipDefaults.assistChipColors(labelColor = scheme.primary),
            border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = scheme.primary),
        )
    }
}

// --------------------------------------------------------------------------------------------
// Table
// --------------------------------------------------------------------------------------------

/**
 * The scrollable, paged table body. The header row and every data row share one horizontal
 * [rememberScrollState] so their columns stay aligned as the user scrolls sideways.
 */
@Composable
private fun ViewerTable(
    columns: List<ColumnDefEntity>,
    rows: LazyPagingItems<RowUiModel>,
    contentPadding: PaddingValues,
    onRowClick: (RowUiModel) -> Unit,
) {
    val horizontalScroll = rememberScrollState()
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        // Sticky header: column name (bold) + a tiny type caption, on surfaceVariant.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.surfaceContainerLow)
                .horizontalScroll(horizontalScroll),
        ) {
            columns.forEach { column ->
                HeaderCell(name = column.name, type = column.cellType())
            }
        }
        HorizontalDivider(color = scheme.outlineVariant)

        // Body.
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                count = rows.itemCount,
                key = rows.itemKey { it.rowId },
            ) { index ->
                val row = rows[index]
                if (row != null) {
                    DataRow(
                        row = row,
                        columns = columns,
                        // Zebra-stripe for readability.
                        background = if (index % 2 == 0) scheme.surface else scheme.surfaceContainerLowest,
                        horizontalScroll = horizontalScroll,
                        onClick = { onRowClick(row) },
                    )
                    HorizontalDivider(color = scheme.outlineVariant)
                }
            }

            // Trailing progress indicator only while the next page is actively loading.
            if (rows.loadState.append is androidx.paging.LoadState.Loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(name: String, type: CellType) {
    Column(
        modifier = Modifier
            .width(CellWidth)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = type.name.lowercase(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DataRow(
    row: RowUiModel,
    columns: List<ColumnDefEntity>,
    background: androidx.compose.ui.graphics.Color,
    horizontalScroll: androidx.compose.foundation.ScrollState,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .horizontalScroll(horizontalScroll),
    ) {
        columns.forEach { column ->
            TableCell(
                text = row.cells[column.id].orEmpty(),
                numeric = column.cellType() in NumericTypes,
            )
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    numeric: Boolean = false,
) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (numeric) TextAlign.End else TextAlign.Start,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .width(CellWidth)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    )
}

// --------------------------------------------------------------------------------------------
// Row detail
// --------------------------------------------------------------------------------------------

/** A bottom sheet listing `columnName -> value` for every column of the tapped row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowDetailSheet(
    row: RowUiModel,
    columns: List<ColumnDefEntity>,
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Row details",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            columns.forEach { column ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(modifier = Modifier.width(CellWidth)) {
                        Text(
                            text = column.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TypePill(column.cellType())
                    }
                    Text(
                        text = row.cells[column.id] ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** Small pill showing a column's cell type (e.g. "text", "integer") in the row-detail sheet. */
@Composable
private fun TypePill(type: CellType) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.padding(top = 4.dp),
    ) {
        Text(
            text = type.name.lowercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// --------------------------------------------------------------------------------------------
// Sort control
// --------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortSheet(
    columns: List<ColumnDefEntity>,
    onDismiss: () -> Unit,
    onConfirm: (ColumnDefEntity, SortDirection) -> Unit,
) {
    if (columns.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var selected by remember { mutableStateOf(columns.first()) }
    var direction by remember { mutableStateOf(SortDirection.ASC) }

    ControlSheet(title = "Sort", onDismiss = onDismiss) {
        LabeledDropdown(
            label = "Column",
            options = columns,
            selected = selected,
            optionLabel = { it.name },
            onSelect = { selected = it },
        )
        Text(
            "Direction",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = direction == SortDirection.ASC,
                onClick = { direction = SortDirection.ASC },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
            ) { Text("Ascending") }
            SegmentedButton(
                selected = direction == SortDirection.DESC,
                onClick = { direction = SortDirection.DESC },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
            ) { Text("Descending") }
        }
        ConfirmRow(
            confirmLabel = "Apply",
            onConfirm = { onConfirm(selected, direction) },
        )
    }
}

// --------------------------------------------------------------------------------------------
// Filter control
// --------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    columns: List<ColumnDefEntity>,
    hasActiveFilters: Boolean,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onConfirm: (ColumnDefEntity, FilterOp, String) -> Unit,
) {
    if (columns.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var column by remember { mutableStateOf(columns.first()) }
    var op by remember { mutableStateOf(FilterOp.CONTAINS) }
    var value by remember { mutableStateOf("") }

    ControlSheet(title = "Filter", onDismiss = onDismiss) {
        LabeledDropdown(
            label = "Column",
            options = columns,
            selected = column,
            optionLabel = { it.name },
            onSelect = { column = it },
        )
        LabeledDropdown(
            label = "Condition",
            options = FilterOp.entries,
            selected = op,
            optionLabel = { it.name.lowercase().replace('_', ' ') },
            onSelect = { op = it },
        )
        if (op != FilterOp.IS_EMPTY && op != FilterOp.IS_NOT_EMPTY) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Value") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
        ConfirmRow(
            confirmLabel = "Add filter",
            onConfirm = { onConfirm(column, op, value) },
            secondaryLabel = "Clear all".takeIf { hasActiveFilters },
            onSecondary = onClear,
        )
    }
}

// --------------------------------------------------------------------------------------------
// Group control
// --------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupSheet(
    columns: List<ColumnDefEntity>,
    selectedColumnId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit,
) {
    // "None" is modelled as a null-id sentinel column so the dropdown can carry it.
    val noneOption = remember { null }
    var selected by remember { mutableStateOf(selectedColumnId) }

    ControlSheet(title = "Group by", onDismiss = onDismiss) {
        LabeledDropdown(
            label = "Column",
            options = listOf(noneOption) + columns.map { it.id },
            selected = selected,
            optionLabel = { id -> if (id == null) "None" else columns.first { it.id == id }.name },
            onSelect = { selected = it },
        )
        ConfirmRow(
            confirmLabel = "Apply",
            onConfirm = { onConfirm(selected) },
        )
    }
}

// --------------------------------------------------------------------------------------------
// Shared bottom-sheet scaffolding + controls
// --------------------------------------------------------------------------------------------

/** Common [ModalBottomSheet] shell with a serif title and 16dp padding for the control sheets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

/** Confirm (coral) + optional secondary text action, laid out as the sheet's action row. */
@Composable
private fun ConfirmRow(
    confirmLabel: String,
    onConfirm: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondaryLabel != null) {
            TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
        Box(modifier = Modifier.weight(1f))
        androidx.compose.material3.Button(onClick = onConfirm) { Text(confirmLabel) }
    }
}

/** A labeled, single-select dropdown backed by [ExposedDropdownMenuBox]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> LabeledDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(top = 12.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = optionLabel(selected),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(label) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
