package com.plefy.app.feature.viewer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.plefy.app.database.dao.GroupAggregate
import com.plefy.app.database.entity.ColumnDefEntity
import com.plefy.app.domain.query.AggregateOp
import com.plefy.app.domain.query.AggregateSpec
import com.plefy.app.domain.query.FilterOp
import com.plefy.app.domain.query.FilterSpec
import com.plefy.app.model.CellType
import java.util.Locale

/**
 * Calculation chart for one imported table.
 *
 * The user picks a category column, a [AggregateOp] calculation (count / sum / average / min / max
 * of a value column), optional pre-aggregation [FilterSpec]s, and a chart type. The screen runs the
 * grouped calculation via [ChartViewModel.aggregate] and draws it as a bar or pie chart. Only the
 * [TOP_N] largest categories get their own slice; the rest fold into a gray "Other".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    onBack: () -> Unit,
    viewModel: ChartViewModel = hiltViewModel(),
) {
    val columns by viewModel.columns.collectAsState()

    var category by remember { mutableStateOf<ColumnDefEntity?>(null) }
    var op by remember { mutableStateOf(AggregateOp.COUNT) }
    var valueColumn by remember { mutableStateOf<ColumnDefEntity?>(null) }
    var filters by remember { mutableStateOf<List<FilterSpec>>(emptyList()) }
    var type by remember { mutableStateOf(ChartType.BAR) }
    var showFilters by remember { mutableStateOf(false) }

    if (category == null && columns.isNotEmpty()) category = columns.first()
    val numericColumns = columns.filter { it.cellType() in VALUE_TYPES }
    if (valueColumn == null && numericColumns.isNotEmpty()) valueColumn = numericColumns.first()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chart") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val cat = category
            val useValue = op != AggregateOp.COUNT
            val spec = cat?.let {
                AggregateSpec(
                    tableId = viewModel.tableId,
                    categoryColumnId = it.id,
                    valueColumnId = if (useValue) valueColumn?.id else null,
                    op = op,
                    filters = filters,
                )
            }

            val raw by produceState<List<GroupAggregate>?>(initialValue = null, spec) {
                value = spec?.let { viewModel.aggregate(it) } ?: emptyList()
            }

            ChartHeader(
                calcLabel = calcLabel(op, if (useValue) valueColumn?.name else null),
                categoryName = cat?.name,
                filterCount = filters.size,
                groupCount = raw?.size ?: 0,
                loaded = raw != null,
            )

            ChartDropdown("Category column", columns, category, { it.name }) { category = it }
            ChartDropdown(
                label = "Calculation",
                options = AggregateOp.entries,
                selected = op,
                optionLabel = { it.name.lowercase(Locale.ROOT).replaceFirstChar { c -> c.uppercase() } },
                onSelect = { op = it },
            )
            if (useValue) {
                ChartDropdown(
                    label = "Value column",
                    options = numericColumns,
                    selected = valueColumn,
                    optionLabel = { it.name },
                    onSelect = { valueColumn = it },
                    emptyHint = "No numeric columns",
                )
            }
            FiltersRow(
                filters = filters,
                columns = columns,
                onAdd = { showFilters = true },
                onRemove = { idx -> filters = filters.filterIndexed { i, _ -> i != idx } },
                onClear = { filters = emptyList() },
            )
            TypePicker(type) { type = it }

            when {
                cat == null -> CenteredState("This table has no columns to chart.")
                raw == null -> CenteredState("Loading…")
                raw!!.isEmpty() -> CenteredState("No values to chart for this selection.")
                else -> {
                    val slices = foldSlices(raw!!)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            when (type) {
                                ChartType.BAR -> BarChart(slices)
                                ChartType.PIE -> PieChart(slices)
                            }
                        }
                    }
                }
            }
        }

        if (showFilters) {
            ChartFilterSheet(
                columns = columns,
                onDismiss = { showFilters = false },
                onAdd = { filters = filters + it; showFilters = false },
            )
        }
    }
}

/** The supported chart forms. */
enum class ChartType { BAR, PIE }

/** Column types worth running a numeric calculation over. */
private val VALUE_TYPES = setOf(CellType.INTEGER, CellType.DECIMAL, CellType.CURRENCY)

private fun ColumnDefEntity.cellType(): CellType =
    runCatching { CellType.valueOf(type) }.getOrDefault(CellType.TEXT)

private fun calcLabel(op: AggregateOp, valueName: String?): String = when (op) {
    AggregateOp.COUNT -> "COUNT"
    AggregateOp.SUM -> "SUM OF ${valueName ?: "—"}"
    AggregateOp.AVERAGE -> "AVERAGE OF ${valueName ?: "—"}"
    AggregateOp.MIN -> "MIN OF ${valueName ?: "—"}"
    AggregateOp.MAX -> "MAX OF ${valueName ?: "—"}"
}.uppercase(Locale.ROOT)

/** Formats an amount: whole numbers without decimals, else two places; thousands-grouped. */
private fun formatAmount(v: Double): String =
    if (v == Math.floor(v) && !v.isInfinite()) String.format(Locale.US, "%,d", v.toLong())
    else String.format(Locale.US, "%,.2f", v)

private fun FilterOp.symbol(): String = when (this) {
    FilterOp.EQUALS -> "="
    FilterOp.NOT_EQUALS -> "≠"
    FilterOp.CONTAINS -> "contains"
    FilterOp.STARTS_WITH -> "starts with"
    FilterOp.GREATER_THAN -> ">"
    FilterOp.LESS_THAN -> "<"
    FilterOp.BETWEEN -> "between"
    FilterOp.IS_EMPTY -> "is empty"
    FilterOp.IS_NOT_EMPTY -> "is not empty"
}

// --------------------------------------------------------------------------------------------
// Header
// --------------------------------------------------------------------------------------------

/** Dark-chrome editorial header: the calculation (caption) + "by <category>" (serif) + summary. */
@Composable
private fun ChartHeader(
    calcLabel: String,
    categoryName: String?,
    filterCount: Int,
    groupCount: Int,
    loaded: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = calcLabel,
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
            )
            Text(
                text = "by ${categoryName ?: "—"}",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val summary = buildString {
                append(if (loaded) "$groupCount categories" else "Loading…")
                if (filterCount > 0) append(" · $filterCount filter${if (filterCount == 1) "" else "s"}")
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun CenteredState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --------------------------------------------------------------------------------------------
// Pickers & filters
// --------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChartDropdown(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    emptyHint: String = "—",
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (label.isNotEmpty()) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (options.isNotEmpty()) expanded = it },
        ) {
            OutlinedTextField(
                value = selected?.let(optionLabel) ?: emptyHint,
                onValueChange = {},
                readOnly = true,
                enabled = options.isNotEmpty(),
                shape = MaterialTheme.shapes.small,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionLabel(option)) },
                        onClick = { onSelect(option); expanded = false },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypePicker(selected: ChartType, onSelect: (ChartType) -> Unit) {
    val types = ChartType.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, t ->
            SegmentedButton(
                selected = t == selected,
                onClick = { onSelect(t) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
            ) {
                Text(if (t == ChartType.BAR) "Bar" else "Pie")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FiltersRow(
    filters: List<FilterSpec>,
    columns: List<ColumnDefEntity>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onClear: () -> Unit,
) {
    val nameOf = remember(columns) { columns.associate { it.id to it.name } }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Filters",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (filters.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEachIndexed { i, f ->
                InputChip(
                    selected = false,
                    onClick = { onRemove(i) },
                    label = {
                        Text("${nameOf[f.columnId] ?: "?"} ${f.op.symbol()} ${f.value ?: ""}".trim())
                    },
                    trailingIcon = {
                        Icon(Icons.Filled.Close, contentDescription = "Remove filter", modifier = Modifier.size(16.dp))
                    },
                )
            }
            AssistChip(
                onClick = onAdd,
                label = { Text("Add filter") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartFilterSheet(
    columns: List<ColumnDefEntity>,
    onDismiss: () -> Unit,
    onAdd: (FilterSpec) -> Unit,
) {
    if (columns.isEmpty()) {
        onDismiss()
        return
    }
    val sheetState = rememberModalBottomSheetState()
    var column by remember { mutableStateOf(columns.first()) }
    var op by remember { mutableStateOf(FilterOp.CONTAINS) }
    var value by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Filter data", style = MaterialTheme.typography.headlineSmall)
            ChartDropdown("Column", columns, column, { it.name }) { column = it }
            ChartDropdown(
                "Condition",
                FilterOp.entries,
                op,
                { it.name.lowercase(Locale.ROOT).replace('_', ' ') },
            ) { op = it }
            if (op != FilterOp.IS_EMPTY && op != FilterOp.IS_NOT_EMPTY) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Value") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = { onAdd(FilterSpec(column.id, column.cellType(), op, value.takeIf { it.isNotEmpty() })) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add filter")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// --------------------------------------------------------------------------------------------
// Slices
// --------------------------------------------------------------------------------------------

/** One drawable category: a display label, its computed amount, and its stable color. */
private data class Slice(val label: String, val amount: Double, val color: Color)

/** Folds aggregate rows into at most [TOP_N] colored slices plus a single gray "Other". */
private fun foldSlices(rows: List<GroupAggregate>): List<Slice> {
    val cleaned = rows
        .map { (it.label?.takeIf { l -> l.isNotBlank() } ?: "(blank)") to (it.amount ?: 0.0) }
        .sortedByDescending { it.second }
    val top = cleaned.take(TOP_N).mapIndexed { i, (label, amt) -> Slice(label, amt, categoricalColor(i)) }
    val rest = cleaned.drop(TOP_N).sumOf { it.second }
    return if (rest > 0.0) top + Slice("Other", rest, ChartOther) else top
}

// --------------------------------------------------------------------------------------------
// Bar chart
// --------------------------------------------------------------------------------------------

@Composable
private fun BarChart(slices: List<Slice>) {
    val maxAmount = slices.maxOf { it.amount }.coerceAtLeast(1e-9)
    val track = MaterialTheme.colorScheme.surfaceContainerLow
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        slices.forEach { slice ->
            Column {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = slice.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = formatAmount(slice.amount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .padding(top = 4.dp),
                ) {
                    val h = size.height
                    drawRoundRect(color = track, size = Size(size.width, h), cornerRadius = corner(h))
                    val frac = (slice.amount / maxAmount).coerceIn(0.0, 1.0).toFloat()
                    val w = size.width * frac
                    if (w > 0f) {
                        drawRoundRect(color = slice.color, size = Size(w, h), cornerRadius = corner(h))
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------
// Pie chart
// --------------------------------------------------------------------------------------------

@Composable
private fun PieChart(slices: List<Slice>) {
    val total = slices.sumOf { it.amount }.coerceAtLeast(1e-9)
    val stroke = MaterialTheme.colorScheme.surface
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp),
        ) {
            var start = -90f
            slices.forEach { slice ->
                val sweep = (360.0 * slice.amount / total).toFloat()
                drawArc(color = slice.color, startAngle = start, sweepAngle = sweep, useCenter = true)
                drawArc(color = stroke, startAngle = start, sweepAngle = sweep, useCenter = true, style = Stroke(width = 2f))
                start += sweep
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { slice ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(slice.color),
                    )
                    Text(
                        text = slice.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = formatAmount(slice.amount),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------------------
// Palette (warm Anthropic brand; fixed so slice colors stay stable across themes)
// --------------------------------------------------------------------------------------------

private val ChartColors = listOf(
    Color(0xFFCC785C), Color(0xFF5DB8A6), Color(0xFFE8A55A), Color(0xFF3D3D3A),
    Color(0xFFA9583E), Color(0xFF8E8B82), Color(0xFF7BA7C4), Color(0xFFB58AB0),
)

/** Neutral fill for the folded "Other" bucket. */
private val ChartOther = Color(0xFFC9C2B8)

/** Palette color for the [index]-th slice; cycles and darkens 10% per cycle past the first pass. */
private fun categoricalColor(index: Int): Color {
    val base = ChartColors[index % ChartColors.size]
    val cycles = index / ChartColors.size
    if (cycles == 0) return base
    val f = 1f - 0.10f * cycles
    return Color(red = base.red * f, green = base.green * f, blue = base.blue * f, alpha = base.alpha)
}

/** Corner radius (px) for a bar of the given pixel height — fully rounded ends. */
private fun corner(heightPx: Float) =
    androidx.compose.ui.geometry.CornerRadius(heightPx / 2f, heightPx / 2f)

private const val TOP_N = 12
