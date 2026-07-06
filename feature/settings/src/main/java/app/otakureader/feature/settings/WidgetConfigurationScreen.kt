package app.otakureader.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.domain.model.Category
import app.otakureader.domain.model.WidgetSettings
import app.otakureader.domain.model.WidgetTapAction
import app.otakureader.domain.model.WidgetType
import app.otakureader.feature.settings.viewmodel.WidgetConfigViewModel

/**
 * Widget Configuration Studio — lets the user configure each home-screen widget individually.
 *
 * Each widget type gets its own [WidgetConfigCard] with:
 *  - A count-limit [Slider] (1–10 items).
 *  - A tap-action dropdown.
 *  - A category-filter dropdown (populated from the live category list).
 *  - A thumbnail toggle.
 *  - A static live-preview showing what the widget will approximately look like.
 *
 * All changes are persisted immediately via [WidgetConfigViewModel] → [WidgetPreferences].
 * The screen is stateless — all state comes from the ViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigurationScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WidgetConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect one-shot effects (currently just the save confirmation snackbar).
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                WidgetConfigEffect.SettingsSaved -> { /* silent save — no snackbar noise on every slider tick */ }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_widgets)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader(title = stringResource(R.string.settings_widgets_available))

            if (!state.isLoading) {
                state.widgetSettingsList.forEach { settings ->
                    WidgetConfigCard(
                        settings = settings,
                        categories = state.categories,
                        onIntent = viewModel::onIntent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_widgets_how_to_add)) },
                supportingContent = {
                    Text(
                        text = stringResource(R.string.settings_widgets_how_to_add_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

// ─── Widget config card ───────────────────────────────────────────────────────

/**
 * A Material 3 card that groups all configurable options for a single widget type.
 *
 * The card is collapsed/expanded by tapping the header so the user isn't overwhelmed
 * if they only care about one or two widgets.
 */
@Composable
private fun WidgetConfigCard(
    settings: WidgetSettings,
    categories: List<Category>,
    onIntent: (WidgetConfigIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Card header (tap to expand/collapse) ─────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settings.widgetType.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = settings.widgetType.description(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }

            // ── Expandable settings body ──────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider()

                    // Count-limit slider
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.widget_config_count_limit, settings.countLimit),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Slider(
                            value = settings.countLimit.toFloat(),
                            onValueChange = { raw ->
                                onIntent(
                                    WidgetConfigIntent.SetCountLimit(
                                        widgetType = settings.widgetType,
                                        limit = raw.toInt().coerceIn(1, 10),
                                    )
                                )
                            },
                            valueRange = 1f..10f,
                            steps = 8, // steps between 1 and 10 exclusive = 8
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    HorizontalDivider()

                    // Tap-action dropdown
                    TapActionRow(
                        current = settings.tapAction,
                        onSelected = { action ->
                            onIntent(WidgetConfigIntent.SetTapAction(settings.widgetType, action))
                        },
                    )

                    HorizontalDivider()

                    // Category-filter dropdown
                    CategoryFilterRow(
                        categories = categories,
                        selected = settings.categoryFilter,
                        onSelected = { categoryId ->
                            onIntent(WidgetConfigIntent.SetCategoryFilter(settings.widgetType, categoryId))
                        },
                    )

                    HorizontalDivider()

                    // Thumbnail toggle
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.widget_config_show_thumbnails)) },
                        trailingContent = {
                            Switch(
                                checked = settings.showThumbnails,
                                onCheckedChange = { show ->
                                    onIntent(WidgetConfigIntent.SetShowThumbnails(settings.widgetType, show))
                                },
                            )
                        },
                    )

                    HorizontalDivider()

                    // Static widget preview
                    WidgetPreview(
                        settings = settings,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

// ─── Setting rows ─────────────────────────────────────────────────────────────

@Composable
private fun TapActionRow(
    current: WidgetTapAction,
    onSelected: (WidgetTapAction) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.widget_config_tap_action)) },
        trailingContent = {
            Box {
                OutlinedButton(onClick = { menuOpen = true }) {
                    Text(current.label())
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    WidgetTapAction.entries.forEach { action ->
                        DropdownMenuItem(
                            text = { Text(action.label()) },
                            onClick = {
                                onSelected(action)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun CategoryFilterRow(
    categories: List<Category>,
    selected: Long?,
    onSelected: (Long?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selected }?.name
        ?: stringResource(R.string.widget_config_all_categories)

    ListItem(
        headlineContent = { Text(stringResource(R.string.widget_config_category_filter)) },
        trailingContent = {
            Box {
                OutlinedButton(onClick = { menuOpen = true }) {
                    Text(
                        text = selectedName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    // "All categories" option
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.widget_config_all_categories)) },
                        onClick = {
                            onSelected(null)
                            menuOpen = false
                        },
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                onSelected(category.id)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        },
    )
}

// ─── Static widget preview ────────────────────────────────────────────────────

/**
 * A purely decorative Compose mockup of what the widget looks like with the current settings.
 *
 * This is NOT a real Glance preview — Glance widgets can't be rendered inside a Compose
 * hierarchy. Instead we replicate the widget's visual language (surface background, title,
 * item rows with optional thumbnail placeholder) using standard Compose components. It gives
 * the user a quick visual sanity-check without any runtime overhead.
 */
@Composable
private fun WidgetPreview(
    settings: WidgetSettings,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.widget_config_preview_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = settings.widgetType.displayName(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Show `countLimit` placeholder rows (capped at 4 in the preview for brevity)
                val previewRows = minOf(settings.countLimit, 4)
                repeat(previewRows) { index ->
                    PreviewRow(
                        showThumbnail = settings.showThumbnails,
                        alpha = 1f - (index * 0.15f).coerceIn(0f, 0.6f),
                    )
                    if (index < previewRows - 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                if (settings.countLimit > 4) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "… +${settings.countLimit - 4} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewRow(showThumbnail: Boolean, alpha: Float) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (showThumbnail) {
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 38.dp)
                    .background(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = alpha * 0.25f),
                        shape = RoundedCornerShape(4.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Book,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            // Placeholder "title" bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(10.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.35f),
                        shape = RoundedCornerShape(4.dp),
                    ),
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Placeholder "subtitle" bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(8.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha * 0.2f),
                        shape = RoundedCornerShape(4.dp),
                    ),
            )
        }
    }
}

// ─── NavGraph extension ───────────────────────────────────────────────────────

fun NavGraphBuilder.widgetConfigurationScreen(onNavigateBack: () -> Unit) {
    composable<Route.WidgetConfiguration> {
        WidgetConfigurationScreen(onNavigateBack = onNavigateBack)
    }
}

// ─── Display-name helpers ─────────────────────────────────────────────────────

/**
 * Human-readable name for a [WidgetType].
 * These map to existing string resources to avoid adding duplicate strings.
 */
@Composable
private fun WidgetType.displayName(): String = when (this) {
    WidgetType.CONTINUE_READING -> stringResource(R.string.settings_widget_continue_reading)
    WidgetType.NEW_UPDATES -> stringResource(R.string.settings_widget_recent_updates)
    WidgetType.LIBRARY -> stringResource(R.string.settings_widget_library)
    WidgetType.NOW_READING -> stringResource(R.string.settings_widget_now_reading)
}

@Composable
private fun WidgetType.description(): String = when (this) {
    WidgetType.CONTINUE_READING -> stringResource(R.string.settings_widget_continue_reading_description)
    WidgetType.NEW_UPDATES -> stringResource(R.string.settings_widget_recent_updates_description)
    WidgetType.LIBRARY -> stringResource(R.string.settings_widget_library_description)
    WidgetType.NOW_READING -> stringResource(R.string.settings_widget_now_reading_description)
}

@Composable
private fun WidgetTapAction.label(): String = when (this) {
    WidgetTapAction.OPEN_MANGA -> stringResource(R.string.widget_config_tap_open_manga)
    WidgetTapAction.OPEN_READER -> stringResource(R.string.widget_config_tap_open_reader)
    WidgetTapAction.OPEN_APP -> stringResource(R.string.widget_config_tap_open_app)
}
