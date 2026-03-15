package app.otakureader.feature.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.otakureader.feature.browse.R
import app.otakureader.sourceapi.Filter
import app.otakureader.sourceapi.FilterList
import app.otakureader.sourceapi.Filters

/**
 * Bottom sheet that renders source-provided filters dynamically.
 * Supports Select, CheckBox, TriState, Text, Sort, and Group filter types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceFilterSheet(
    filters: FilterList,
    onFilterUpdate: (index: Int, filter: Filter<*>) -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.browse_filters),
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.browse_filters_reset))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filter list
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                filters.filters.forEachIndexed { index, filter ->
                    FilterItem(
                        filter = filter,
                        onUpdate = { updatedFilter -> onFilterUpdate(index, updatedFilter) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.browse_filters_cancel))
                }
                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.browse_filters_apply))
                }
            }
        }
    }
}

@Composable
private fun FilterItem(
    filter: Filter<*>,
    onUpdate: (Filter<*>) -> Unit
) {
    when (filter) {
        is Filter.Header -> HeaderFilter(filter)
        is Filter.Separator -> HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        is Filter.Select<*> -> SelectFilter(filter, onUpdate)
        is Filter.Text -> TextFilter(filter, onUpdate)
        is Filter.CheckBox -> CheckBoxFilter(filter, onUpdate)
        is Filter.TriState -> TriStateFilter(filter, onUpdate)
        is Filter.Sort -> SortFilter(filter, onUpdate)
        is Filter.Group<*> -> GroupFilter(filter, onUpdate)
    }
}

@Composable
private fun HeaderFilter(filter: Filter.Header) {
    Text(
        text = filter.name,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SelectFilter(filter: Filter.Select<*>, onUpdate: (Filter<*>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val currentIndex = filter.state
    val options = remember(filter.name, filter.values.size) {
        filter.values.map { it.toString() }
    }
    val optionsArray = remember(filter.name, filter.values.size) {
        filter.values.map { it.toString() }.toTypedArray()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = filter.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = options.getOrElse(currentIndex) { "" },
                style = MaterialTheme.typography.bodyMedium
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onUpdate(
                            Filters.SelectFilter(filter.name, optionsArray, index)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TextFilter(filter: Filter.Text, onUpdate: (Filter<*>) -> Unit) {
    OutlinedTextField(
        value = filter.state,
        onValueChange = { newValue ->
            onUpdate(Filters.TextFilter(filter.name, newValue))
        },
        label = { Text(filter.name) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
private fun CheckBoxFilter(filter: Filter.CheckBox, onUpdate: (Filter<*>) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onUpdate(Filters.CheckBoxFilter(filter.name, !filter.state)) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = filter.state,
            onCheckedChange = { checked ->
                onUpdate(Filters.CheckBoxFilter(filter.name, checked))
            }
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = filter.name,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TriStateFilter(filter: Filter.TriState, onUpdate: (Filter<*>) -> Unit) {
    val nextState = when (filter.state) {
        Filter.TriState.STATE_IGNORE -> Filter.TriState.STATE_INCLUDE
        Filter.TriState.STATE_INCLUDE -> Filter.TriState.STATE_EXCLUDE
        else -> Filter.TriState.STATE_IGNORE
    }

    FilterChip(
        selected = filter.state != Filter.TriState.STATE_IGNORE,
        onClick = { onUpdate(Filters.TriStateFilter(filter.name, nextState)) },
        label = {
            Text(
                text = when (filter.state) {
                    Filter.TriState.STATE_INCLUDE -> "✓ ${filter.name}"
                    Filter.TriState.STATE_EXCLUDE -> "✕ ${filter.name}"
                    else -> filter.name
                }
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortFilter(filter: Filter.Sort, onUpdate: (Filter<*>) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = filter.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            filter.values.forEachIndexed { index, name ->
                val isSelected = filter.state?.index == index
                val ascending = filter.state?.ascending ?: true

                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newAscending = if (isSelected) !ascending else true
                        onUpdate(
                            Filters.SortFilter(
                                filter.name,
                                filter.values,
                                Filter.Sort.Selection(index, newAscending)
                            )
                        )
                    },
                    label = { Text(name) },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                if (ascending) Icons.Default.KeyboardArrowUp
                                else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (ascending) stringResource(R.string.browse_sort_ascending) else stringResource(R.string.browse_sort_descending)
                            )
                        }
                    } else null
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupFilter(filter: Filter.Group<*>, onUpdate: (Filter<*>) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = filter.name,
                style = MaterialTheme.typography.titleSmall
            )
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) stringResource(R.string.browse_group_collapse) else stringResource(R.string.browse_group_expand)
            )
        }

        if (expanded) {
            @Suppress("UNCHECKED_CAST")
            val childFilters = filter.state as? List<Filter<*>> ?: emptyList()

            // For groups of TriState filters, use a compact FlowRow layout
            if (childFilters.all { it is Filter.TriState }) {
                FlowRow(
                    modifier = Modifier.padding(start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    childFilters.forEachIndexed { childIndex, childFilter ->
                        FilterItem(
                            filter = childFilter,
                            onUpdate = { updatedChild ->
                                val newChildren = childFilters.toMutableList()
                                newChildren[childIndex] = updatedChild
                                onUpdate(Filters.GroupFilter(filter.name, newChildren))
                            }
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    childFilters.forEachIndexed { childIndex, childFilter ->
                        FilterItem(
                            filter = childFilter,
                            onUpdate = { updatedChild ->
                                val newChildren = childFilters.toMutableList()
                                newChildren[childIndex] = updatedChild
                                onUpdate(Filters.GroupFilter(filter.name, newChildren))
                            }
                        )
                    }
                }
            }
        }
    }
}
