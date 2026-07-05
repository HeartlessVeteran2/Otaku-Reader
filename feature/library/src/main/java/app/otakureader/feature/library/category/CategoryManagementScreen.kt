package app.otakureader.feature.library.category

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SyncDisabled
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import app.otakureader.domain.model.CategoryUpdateFrequency
import app.otakureader.domain.model.DynamicCategoryRule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.feature.library.R
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoryManagementViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryUiItem?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf<CategoryUiItem?>(null) }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is CategoryEffect.ShowSnackbar -> {
                    scope.launch { snackbarHostState.showSnackbar(effect.message) }
                }
                CategoryEffect.DismissDialog -> {
                    showCreateDialog = false
                    editingCategory = null
                    showDeleteConfirmation = null
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.category_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.category_create_new))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.categories.isEmpty()) {
                EmptyCategoriesMessage(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                val context = androidx.compose.ui.platform.LocalContext.current
                val visibleCategories = if (state.hiddenRevealed) {
                    state.categories
                } else {
                    state.categories.filter { !it.isHidden }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (state.hasHiddenCategories) {
                        item(key = "hidden_reveal_toggle") {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    if (state.hiddenRevealed) {
                                        viewModel.onEvent(CategoryEvent.SetHiddenRevealed(false))
                                    } else {
                                        HiddenCategoryAuth.authenticate(context) { success ->
                                            if (success) {
                                                viewModel.onEvent(CategoryEvent.SetHiddenRevealed(true))
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 8.dp),
                            ) {
                                androidx.compose.material3.Text(
                                    stringResource(
                                        if (state.hiddenRevealed) {
                                            R.string.category_hide_hidden
                                        } else {
                                            R.string.category_show_hidden
                                        },
                                    ),
                                )
                            }
                        }
                    }
                    items(
                        items = visibleCategories,
                        key = { it.id }
                    ) { category ->
                        CategoryListItem(
                            category = category,
                            onEdit = { editingCategory = category },
                            onDelete = { showDeleteConfirmation = category },
                            onToggleHidden = {
                                viewModel.onEvent(CategoryEvent.ToggleHidden(category.id))
                            },
                            onToggleNsfw = {
                                viewModel.onEvent(CategoryEvent.ToggleNsfw(category.id))
                            },
                            onToggleLocked = {
                                viewModel.onEvent(CategoryEvent.ToggleLocked(category.id))
                            },
                            onToggleSkipUpdates = {
                                viewModel.onEvent(CategoryEvent.ToggleSkipUpdates(category.id))
                            },
                            onEditRules = {
                                viewModel.onEvent(CategoryEvent.OpenRuleEditor(category.id))
                            },
                        )
                    }
                }
            }
        }
    }

    // Create/Edit Dialog
    if (showCreateDialog || editingCategory != null) {
        CategoryDialog(
            category = editingCategory,
            onDismiss = {
                showCreateDialog = false
                editingCategory = null
            },
            onConfirm = { name, frequency ->
                val editing = editingCategory
                if (editing != null) {
                    viewModel.onEvent(CategoryEvent.UpdateCategory(editing.id, name, frequency))
                } else {
                    viewModel.onEvent(CategoryEvent.CreateCategory(name, frequency))
                }
            }
        )
    }

    // Delete Confirmation
    showDeleteConfirmation?.let { category ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = { Text(stringResource(R.string.category_delete_title)) },
            text = { Text(stringResource(R.string.category_delete_message, category.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onEvent(CategoryEvent.DeleteCategory(category.id))
                        showDeleteConfirmation = null
                    }
                ) {
                    Text(stringResource(R.string.category_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = null }) {
                    Text(stringResource(R.string.category_delete_cancel))
                }
            }
        )
    }

    // Smart-rule editor
    state.ruleEditor?.let { editor ->
        RuleEditorDialog(
            editor = editor,
            onAddRule = { viewModel.onEvent(CategoryEvent.AddRule(it)) },
            onRemoveRule = { viewModel.onEvent(CategoryEvent.RemoveRule(it)) },
            onSave = { viewModel.onEvent(CategoryEvent.SaveRules) },
            onDismiss = { viewModel.onEvent(CategoryEvent.CloseRuleEditor) },
        )
    }
}

@Composable
private fun CategoryUpdateFrequency.label(): String = when (this) {
    CategoryUpdateFrequency.NEVER -> stringResource(R.string.category_freq_never)
    CategoryUpdateFrequency.DAILY -> stringResource(R.string.category_freq_daily)
    CategoryUpdateFrequency.EVERY_3_DAYS -> stringResource(R.string.category_freq_3days)
    CategoryUpdateFrequency.WEEKLY -> stringResource(R.string.category_freq_weekly)
}

@Composable
private fun CategoryListItem(
    category: CategoryUiItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleNsfw: () -> Unit,
    onToggleLocked: () -> Unit,
    onToggleSkipUpdates: () -> Unit,
    onEditRules: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        ListItem(
            headlineContent = { Text(category.name) },
            supportingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.category_manga_count, category.mangaCount),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = stringResource(R.string.category_update_freq_label, category.updateFrequency.label()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (category.isHidden) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = stringResource(R.string.category_hidden),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (category.isLocked) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = stringResource(R.string.category_locked),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (category.isDynamic) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = stringResource(R.string.category_dynamic),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (category.skipUpdates) {
                        Icon(
                            imageVector = Icons.Default.SyncDisabled,
                            contentDescription = stringResource(R.string.category_updates_skipped),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            trailingContent = {
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.category_edit))
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.category_more))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (category.isHidden)
                                        stringResource(R.string.category_show)
                                    else
                                        stringResource(R.string.category_hide)
                                )
                            },
                            onClick = {
                                onToggleHidden()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (category.isHidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (category.isNsfw)
                                        stringResource(R.string.category_remove_nsfw)
                                    else
                                        stringResource(R.string.category_mark_nsfw)
                                )
                            },
                            onClick = {
                                onToggleNsfw()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (category.isNsfw) Icons.Default.VisibilityOff else Icons.Default.Warning,
                                    contentDescription = null,
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (category.isLocked)
                                        stringResource(R.string.category_unlock_label)
                                    else
                                        stringResource(R.string.category_lock_label)
                                )
                            },
                            onClick = {
                                onToggleLocked()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (category.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (category.skipUpdates)
                                        stringResource(R.string.category_include_in_updates)
                                    else
                                        stringResource(R.string.category_skip_updates)
                                )
                            },
                            onClick = {
                                onToggleSkipUpdates()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (category.skipUpdates) Icons.Default.Sync else Icons.Default.SyncDisabled,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.category_edit_smart_rules)) },
                            onClick = {
                                onEditRules()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.category_delete)) },
                            onClick = {
                                onDelete()
                                showMenu = false
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            }
                        )
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDialog(
    category: CategoryUiItem?,
    onDismiss: () -> Unit,
    onConfirm: (String, CategoryUpdateFrequency) -> Unit,
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var frequency by remember { mutableStateOf(category?.updateFrequency ?: CategoryUpdateFrequency.DAILY) }
    val freqOptions = CategoryUpdateFrequency.entries

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (category == null)
                    stringResource(R.string.category_create_title)
                else
                    stringResource(R.string.category_edit_title)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.category_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.category_update_frequency_label),
                    style = MaterialTheme.typography.labelMedium,
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    freqOptions.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = frequency == option,
                            onClick = { frequency = option },
                            shape = SegmentedButtonDefaults.itemShape(index, freqOptions.size),
                            label = { Text(option.label(), style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, frequency) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.category_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.category_cancel))
            }
        },
    )
}

/** Human-readable label for a rule, used in the editor's current-rules list. */
@Composable
private fun DynamicCategoryRule.label(): String = when (this) {
    is DynamicCategoryRule.UnreadAtLeast -> stringResource(R.string.rule_label_unread_at_least, count)
    is DynamicCategoryRule.RecentlyUpdated -> stringResource(R.string.rule_label_recently_updated, withinDays)
    is DynamicCategoryRule.GenreContains -> stringResource(R.string.rule_label_genre_contains, genre)
    DynamicCategoryRule.Completed -> stringResource(R.string.rule_label_completed)
    DynamicCategoryRule.Ongoing -> stringResource(R.string.rule_label_ongoing)
    is DynamicCategoryRule.RecentlyAdded -> stringResource(R.string.rule_label_recently_added, withinDays)
    DynamicCategoryRule.NeverStarted -> stringResource(R.string.rule_label_never_started)
    is DynamicCategoryRule.ReadWithinDays -> stringResource(R.string.rule_label_read_within_days, withinDays)
    is DynamicCategoryRule.NotReadInDays -> stringResource(R.string.rule_label_not_read_in_days, withinDays)
    DynamicCategoryRule.MarkedCompleted -> stringResource(R.string.rule_label_marked_completed)
    DynamicCategoryRule.MarkedDropped -> stringResource(R.string.rule_label_marked_dropped)
}

/** Parameterized rule kinds offered in the editor's advanced adder. */
private enum class ParamRuleType(@StringRes val labelRes: Int, val isGenre: Boolean) {
    UNREAD_AT_LEAST(R.string.rule_type_unread_at_least, false),
    READ_WITHIN_DAYS(R.string.rule_type_read_within_days, false),
    NOT_READ_IN_DAYS(R.string.rule_type_not_read_in_days, false),
    RECENTLY_ADDED(R.string.rule_type_recently_added, false),
    RECENTLY_UPDATED(R.string.rule_type_recently_updated, false),
    GENRE_CONTAINS(R.string.rule_type_genre_contains, true),
}

private fun buildParamRule(type: ParamRuleType, value: String): DynamicCategoryRule? = when (type) {
    ParamRuleType.GENRE_CONTAINS ->
        value.trim().takeIf { it.isNotEmpty() }?.let { DynamicCategoryRule.GenreContains(it) }
    else -> value.toIntOrNull()?.takeIf { it >= 0 }?.let { n ->
        when (type) {
            ParamRuleType.UNREAD_AT_LEAST -> DynamicCategoryRule.UnreadAtLeast(n)
            ParamRuleType.READ_WITHIN_DAYS -> DynamicCategoryRule.ReadWithinDays(n)
            ParamRuleType.NOT_READ_IN_DAYS -> DynamicCategoryRule.NotReadInDays(n)
            ParamRuleType.RECENTLY_ADDED -> DynamicCategoryRule.RecentlyAdded(n)
            ParamRuleType.RECENTLY_UPDATED -> DynamicCategoryRule.RecentlyUpdated(n)
            ParamRuleType.GENRE_CONTAINS -> null
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RuleEditorDialog(
    editor: RuleEditorUiState,
    onAddRule: (DynamicCategoryRule) -> Unit,
    onRemoveRule: (Int) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.category_smart_rules_title, editor.categoryName)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.category_smart_rules_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Text(
                    text = stringResource(R.string.category_smart_rules_current),
                    style = MaterialTheme.typography.labelMedium,
                )
                if (editor.rules.isEmpty()) {
                    Text(
                        text = stringResource(R.string.category_smart_rules_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    editor.rules.forEachIndexed { index, rule ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rule.label(),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            IconButton(onClick = { onRemoveRule(index) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.category_rule_remove),
                                )
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.category_smart_rules_add),
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onAddRule(DynamicCategoryRule.Completed) },
                        label = { Text(stringResource(R.string.rule_label_completed)) },
                    )
                    AssistChip(
                        onClick = { onAddRule(DynamicCategoryRule.Ongoing) },
                        label = { Text(stringResource(R.string.rule_label_ongoing)) },
                    )
                    AssistChip(
                        onClick = { onAddRule(DynamicCategoryRule.NeverStarted) },
                        label = { Text(stringResource(R.string.rule_label_never_started)) },
                    )
                    AssistChip(
                        onClick = { onAddRule(DynamicCategoryRule.MarkedCompleted) },
                        label = { Text(stringResource(R.string.rule_label_marked_completed)) },
                    )
                    AssistChip(
                        onClick = { onAddRule(DynamicCategoryRule.MarkedDropped) },
                        label = { Text(stringResource(R.string.rule_label_marked_dropped)) },
                    )
                }

                ParameterizedRuleAdder(onAddRule = onAddRule)
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text(stringResource(R.string.category_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.category_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParameterizedRuleAdder(onAddRule: (DynamicCategoryRule) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(ParamRuleType.UNREAD_AT_LEAST) }
    var value by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            OutlinedTextField(
                value = stringResource(selected.labelRes),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth(),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ParamRuleType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(stringResource(type.labelRes)) },
                        onClick = {
                            selected = type
                            value = ""
                            expanded = false
                        },
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { input ->
                    val filtered = if (selected.isGenre) input else input.filter { it.isDigit() }
                    value = if (selected.isGenre) filtered else filtered.take(9)
                },
                label = { Text(stringResource(R.string.category_rule_value_hint)) },
                singleLine = true,
                keyboardOptions = if (selected.isGenre) {
                    KeyboardOptions.Default
                } else {
                    KeyboardOptions(keyboardType = KeyboardType.Number)
                },
                modifier = Modifier.weight(1f),
            )
            val rule = buildParamRule(selected, value)
            OutlinedButton(
                onClick = { rule?.let { onAddRule(it); value = "" } },
                enabled = rule != null,
            ) {
                Text(stringResource(R.string.category_rule_add_button))
            }
        }
    }
}

@Composable
private fun EmptyCategoriesMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.category_empty_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.padding(8.dp))
        Text(
            text = stringResource(R.string.category_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
