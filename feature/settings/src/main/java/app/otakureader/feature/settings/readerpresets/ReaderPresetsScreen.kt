package app.otakureader.feature.settings.readerpresets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.core.preferences.ReaderPreset
import app.otakureader.feature.settings.R
import kotlinx.coroutines.flow.collectLatest

fun NavGraphBuilder.readerPresetsScreen(onNavigateBack: () -> Unit) {
    composable<Route.ReaderPresets> {
        ReaderPresetsScreen(onNavigateBack = onNavigateBack)
    }
}

@Composable
private fun readerModeName(mode: Int): String = when (mode) {
    0 -> stringResource(R.string.reader_mode_single_page)
    1 -> stringResource(R.string.reader_mode_dual_page)
    2 -> stringResource(R.string.reader_mode_webtoon)
    3 -> stringResource(R.string.reader_mode_smart_panels)
    else -> stringResource(R.string.reader_presets_mode_label, mode)
}

@Composable
private fun readerScaleName(scale: Int): String = when (scale) {
    0 -> stringResource(R.string.reader_scale_fit_screen)
    1 -> stringResource(R.string.reader_scale_fit_width)
    2 -> stringResource(R.string.reader_scale_fit_height)
    3 -> stringResource(R.string.reader_scale_original)
    4 -> stringResource(R.string.reader_scale_smart_fit)
    else -> stringResource(R.string.reader_presets_mode_label, scale)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPresetsScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReaderPresetsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is ReaderPresetsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reader_presets_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.onEvent(ReaderPresetsEvent.ShowSaveDialog) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.reader_presets_save_current))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.presets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.reader_presets_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(state.presets, key = { it.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        onApply = { viewModel.onEvent(ReaderPresetsEvent.Apply(preset)) },
                        onDelete = { viewModel.onEvent(ReaderPresetsEvent.Delete(preset.id)) },
                    )
                }
            }
        }

        if (state.showSaveDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(ReaderPresetsEvent.HideSaveDialog) },
                title = { Text(stringResource(R.string.reader_presets_save_dialog_title)) },
                text = {
                    OutlinedTextField(
                        value = state.saveDialogName,
                        onValueChange = { viewModel.onEvent(ReaderPresetsEvent.UpdateSaveName(it)) },
                        placeholder = { Text(stringResource(R.string.reader_presets_save_dialog_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.onEvent(ReaderPresetsEvent.ConfirmSave) },
                        enabled = state.saveDialogName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.reader_presets_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onEvent(ReaderPresetsEvent.HideSaveDialog) }) {
                        Text(stringResource(R.string.reader_presets_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun PresetCard(
    preset: ReaderPreset,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = preset.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${readerModeName(preset.readerMode)} · ${readerScaleName(preset.readerScale)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val details = buildList {
                    if (preset.volumeKeysEnabled) add(stringResource(R.string.reader_presets_detail_volume_keys))
                    if (preset.skipReadChapters) add(stringResource(R.string.reader_presets_detail_skip_read))
                    if (preset.invertTapZones) add(stringResource(R.string.reader_presets_detail_inverted_taps))
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onApply) {
                Text(stringResource(R.string.reader_presets_apply))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.reader_presets_delete))
            }
        }
    }
}
