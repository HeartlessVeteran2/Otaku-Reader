package app.otakureader.feature.settings.datausage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otakureader.feature.settings.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.common.network.NetworkType
import app.otakureader.core.navigation.Route
import app.otakureader.domain.model.DataUsageRecord

// ---------------------------------------------------------------------------
// Navigation registration
// ---------------------------------------------------------------------------

fun NavGraphBuilder.dataUsageScreen(onNavigateBack: () -> Unit) {
    composable<Route.DataUsage> {
        DataUsageScreen(onNavigateBack = onNavigateBack)
    }
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

private const val BYTES_PER_GB = 1_073_741_824L
private const val BYTES_PER_MB = 1_048_576L
private const val BYTES_PER_KB = 1_024L

/** Maximum MB value the budget slider can represent. */
private const val BUDGET_MAX_MB = 10_000

/** Number of discrete steps in the slider (100 positions → each step ≈ 100 MB). */
private const val BUDGET_SLIDER_STEPS = 99

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataUsageScreen(
    onNavigateBack: () -> Unit,
    viewModel: DataUsageViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_data_usage)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_data_usage_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // --- Period tabs ---
            val tabs = DataUsageTab.entries
            SecondaryTabRow(selectedTabIndex = state.selectedTab.ordinal) {
                tabs.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.onEvent(DataUsageEvent.SelectTab(tab)) },
                        text = { Text(tab.label()) },
                    )
                }
            }

            val entries = when (state.selectedTab) {
                DataUsageTab.TODAY -> state.todayEntries
                DataUsageTab.WEEK -> state.weekEntries
                DataUsageTab.MONTH -> state.monthEntries
            }

            NetworkSummaryChips(entries = entries)

            LazyColumn(
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {

                // --- Per-period category breakdown ---
                val grouped = entries.groupBy { it.category }
                items(grouped.entries.toList(), key = { it.key }) { (category, rows) ->
                    DataUsageRow(category = category, bytes = rows.sumOf { it.bytes })
                }

                // --- Monthly budget section ---
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    MonthlyBudgetSection(
                        budgetMb = state.monthlyDataBudgetMb,
                        monthBytesUsed = state.monthEntries.sumOf { it.bytes },
                        onBudgetChanged = { mb ->
                            viewModel.onEvent(DataUsageEvent.SetMonthlyDataBudgetMb(mb))
                        },
                    )
                }

                // --- Per-source breakdown ---
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = stringResource(R.string.data_usage_by_source),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                if (state.sourceBreakdown.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.data_usage_no_source_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                } else {
                    items(state.sourceBreakdown, key = { it.sourceName }) { entry ->
                        DataUsageRow(
                            category = entry.sourceName,
                            bytes = entry.bytesThisMonth,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Monthly budget section
// ---------------------------------------------------------------------------

@Composable
private fun MonthlyBudgetSection(
    budgetMb: Int,
    monthBytesUsed: Long,
    onBudgetChanged: (Int) -> Unit,
) {
    val budgetLabel = if (budgetMb == 0) {
        stringResource(R.string.data_usage_budget_unlimited)
    } else {
        formatBytes(budgetMb.toLong() * BYTES_PER_MB)
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.data_usage_monthly_budget)) },
        supportingContent = {
            Column {
                Text(
                    text = budgetLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = budgetMb.toFloat(),
                    onValueChange = { onBudgetChanged(it.toInt()) },
                    valueRange = 0f..BUDGET_MAX_MB.toFloat(),
                    steps = BUDGET_SLIDER_STEPS,
                )
                // Only show the progress bar when a budget has been set.
                if (budgetMb > 0) {
                    val used = monthBytesUsed / BYTES_PER_MB.toFloat()
                    val progress = (used / budgetMb).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Text(
                        text = "${formatBytes(monthBytesUsed)} / $budgetLabel",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        },
    )
}

// ---------------------------------------------------------------------------
// Shared composables
// ---------------------------------------------------------------------------

@Composable
private fun NetworkSummaryChips(entries: List<DataUsageRecord>) {
    val wifiBytes = entries.filter { it.network == NetworkType.WIFI.name }.sumOf { it.bytes }
    val mobileBytes = entries.filter { it.network == NetworkType.MOBILE.name }.sumOf { it.bytes }
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SuggestionChip(
            onClick = {},
            label = { Text(stringResource(R.string.settings_data_usage_wifi, formatBytes(wifiBytes))) },
        )
        SuggestionChip(
            onClick = {},
            label = { Text(stringResource(R.string.settings_data_usage_mobile, formatBytes(mobileBytes))) },
        )
    }
}

@Composable
private fun DataUsageRow(category: String, bytes: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = formatBytes(bytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatBytes(bytes: Long): String = when {
    bytes >= BYTES_PER_GB -> "%.1f GB".format(bytes / BYTES_PER_GB.toDouble())
    bytes >= BYTES_PER_MB -> "%.1f MB".format(bytes / BYTES_PER_MB.toDouble())
    bytes >= BYTES_PER_KB -> "%.1f KB".format(bytes / BYTES_PER_KB.toDouble())
    else -> "$bytes B"
}

@Composable
private fun DataUsageTab.label(): String = when (this) {
    DataUsageTab.TODAY -> stringResource(R.string.settings_data_usage_tab_today)
    DataUsageTab.WEEK -> stringResource(R.string.settings_data_usage_tab_week)
    DataUsageTab.MONTH -> stringResource(R.string.settings_data_usage_tab_month)
}
