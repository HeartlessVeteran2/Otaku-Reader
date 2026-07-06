package app.otakureader.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.domain.model.ImageQuality
import app.otakureader.domain.model.VolumeKeyBehavior
import app.otakureader.feature.settings.viewmodel.ReaderSettingsViewModel
import kotlin.math.roundToInt

/**
 * Reader sub-screen: reading mode, display, scale, tap zones, volume keys, Webtoon, e-ink,
 * and reading behaviour toggles.
 * Serviced by [ReaderSettingsViewModel] backed by [ReaderSettingsDelegate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsReaderScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReaderPresets: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ReaderSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_reader)) },
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
            // Presets shortcut
            ListItem(
                headlineContent = { Text(stringResource(R.string.reader_presets_title)) },
                supportingContent = { Text(stringResource(R.string.reader_presets_subtitle)) },
                modifier = Modifier.clickable(onClick = onNavigateToReaderPresets),
            )
            HorizontalDivider()
            ReaderContent(state = state, onEvent = viewModel::onEvent)
        }
    }
}

fun NavGraphBuilder.settingsReaderScreen(
    onNavigateBack: () -> Unit,
    onNavigateToReaderPresets: () -> Unit = {},
) {
    composable<Route.SettingsReader> {
        SettingsReaderScreen(
            onNavigateBack = onNavigateBack,
            onNavigateToReaderPresets = onNavigateToReaderPresets,
        )
    }
}

// ─── Section composable ───────────────────────────────────────────────────────

@Suppress("LongMethod")
@Composable
private fun ReaderContent(state: SettingsState, onEvent: (SettingsEvent) -> Unit) {
    SectionHeader(title = stringResource(R.string.settings_reader))

    // Reading mode
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_reading_mode)) },
        supportingContent = {
            Column(modifier = Modifier.selectableGroup()) {
                val modes = listOf(
                    stringResource(R.string.settings_reading_mode_single_page) to 0,
                    stringResource(R.string.settings_reading_mode_dual_page) to 1,
                    stringResource(R.string.settings_reading_mode_webtoon) to 2,
                    stringResource(R.string.settings_reading_mode_smart_panels) to 3,
                )
                modes.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.readerMode == value,
                                onClick = { onEvent(SettingsEvent.SetReaderMode(value)) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = state.readerMode == value, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_keep_screen_on)) },
        supportingContent = { Text(stringResource(R.string.settings_keep_screen_on_description)) },
        trailingContent = {
            Switch(
                checked = state.keepScreenOn,
                onCheckedChange = { onEvent(SettingsEvent.SetKeepScreenOn(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_secure_screen)) },
        supportingContent = { Text(stringResource(R.string.settings_secure_screen_description)) },
        trailingContent = {
            Switch(
                checked = state.secureScreen,
                onCheckedChange = { onEvent(SettingsEvent.SetSecureScreen(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_incognito_mode)) },
        supportingContent = { Text(stringResource(R.string.settings_incognito_mode_description)) },
        trailingContent = {
            Switch(
                checked = state.incognitoMode,
                onCheckedChange = { onEvent(SettingsEvent.SetIncognitoMode(it)) },
            )
        },
    )

    // Preloading
    var preloadBeforeSlider by remember(state.preloadPagesBefore) {
        mutableFloatStateOf(state.preloadPagesBefore.toFloat())
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_preload_before, preloadBeforeSlider.roundToInt())) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_preload_before_description))
                Slider(
                    value = preloadBeforeSlider,
                    onValueChange = { preloadBeforeSlider = it },
                    onValueChangeFinished = {
                        onEvent(SettingsEvent.SetPreloadPagesBefore(preloadBeforeSlider.roundToInt()))
                    },
                    valueRange = 0f..10f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )

    var preloadAfterSlider by remember(state.preloadPagesAfter) {
        mutableFloatStateOf(state.preloadPagesAfter.toFloat())
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_preload_after, preloadAfterSlider.roundToInt())) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_preload_after_description))
                Slider(
                    value = preloadAfterSlider,
                    onValueChange = { preloadAfterSlider = it },
                    onValueChangeFinished = {
                        onEvent(SettingsEvent.SetPreloadPagesAfter(preloadAfterSlider.roundToInt()))
                    },
                    valueRange = 0f..10f,
                    steps = 9,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_crop_borders)) },
        supportingContent = { Text(stringResource(R.string.settings_crop_borders_description)) },
        trailingContent = {
            Switch(
                checked = state.cropBordersEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetCropBordersEnabled(it)) },
            )
        },
    )

    // Image quality
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_image_quality)) },
        supportingContent = {
            Column(modifier = Modifier.selectableGroup()) {
                ImageQuality.entries.map { it.displayName to it.name }.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.imageQuality == value,
                                onClick = { onEvent(SettingsEvent.SetImageQuality(value)) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = state.imageQuality == value, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_data_saver)) },
        supportingContent = { Text(stringResource(R.string.settings_data_saver_description)) },
        trailingContent = {
            Switch(
                checked = state.dataSaverEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetDataSaverEnabled(it)) },
            )
        },
    )

    // ── Display ───────────────────────────────────────────────────────
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_reader_display))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_fullscreen)) },
        supportingContent = { Text(stringResource(R.string.settings_fullscreen_description)) },
        trailingContent = {
            Switch(
                checked = state.fullscreen,
                onCheckedChange = { onEvent(SettingsEvent.SetFullscreen(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_cutout)) },
        supportingContent = { Text(stringResource(R.string.settings_show_cutout_description)) },
        trailingContent = {
            Switch(
                checked = state.showContentInCutout,
                onCheckedChange = { onEvent(SettingsEvent.SetShowContentInCutout(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_page_number)) },
        supportingContent = { Text(stringResource(R.string.settings_show_page_number_description)) },
        trailingContent = {
            Switch(
                checked = state.showPageNumber,
                onCheckedChange = { onEvent(SettingsEvent.SetShowPageNumber(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_page_thumbnail_strip)) },
        supportingContent = { Text(stringResource(R.string.settings_show_page_thumbnail_strip_description)) },
        trailingContent = {
            Switch(
                checked = state.showPageThumbnailStrip,
                onCheckedChange = { onEvent(SettingsEvent.SetShowPageThumbnailStrip(it)) },
            )
        },
    )

    // Background color
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_background_color)) },
        supportingContent = {
            Column(modifier = Modifier.selectableGroup()) {
                val colors = listOf(
                    stringResource(R.string.settings_bg_black) to 0,
                    stringResource(R.string.settings_bg_white) to 1,
                    stringResource(R.string.settings_bg_gray) to 2,
                    stringResource(R.string.settings_bg_auto) to 3,
                )
                colors.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.backgroundColor == value,
                                onClick = { onEvent(SettingsEvent.SetBackgroundColor(value)) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = state.backgroundColor == value, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_animate_transitions)) },
        supportingContent = { Text(stringResource(R.string.settings_animate_transitions_description)) },
        trailingContent = {
            Switch(
                checked = state.animatePageTransitions,
                onCheckedChange = { onEvent(SettingsEvent.SetAnimatePageTransitions(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_mode_overlay)) },
        supportingContent = { Text(stringResource(R.string.settings_show_mode_overlay_description)) },
        trailingContent = {
            Switch(
                checked = state.showReadingModeOverlay,
                onCheckedChange = { onEvent(SettingsEvent.SetShowReadingModeOverlay(it)) },
            )
        },
    )

    // ── Scale ─────────────────────────────────────────────────────────
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_reader_scale))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_scale_type)) },
        supportingContent = {
            Column(modifier = Modifier.selectableGroup()) {
                val scales = listOf(
                    stringResource(R.string.settings_scale_fit_screen) to 0,
                    stringResource(R.string.settings_scale_fit_width) to 1,
                    stringResource(R.string.settings_scale_fit_height) to 2,
                    stringResource(R.string.settings_scale_original) to 3,
                    stringResource(R.string.settings_scale_smart_fit) to 4,
                )
                scales.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.readerScale == value,
                                onClick = { onEvent(SettingsEvent.SetReaderScale(value)) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = state.readerScale == value, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_auto_zoom_wide)) },
        supportingContent = { Text(stringResource(R.string.settings_auto_zoom_wide_description)) },
        trailingContent = {
            Switch(
                checked = state.autoZoomWideImages,
                onCheckedChange = { onEvent(SettingsEvent.SetAutoZoomWideImages(it)) },
            )
        },
    )

    // ── Tap zones ─────────────────────────────────────────────────────
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_tap_zones))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_tap_zone_config)) },
        supportingContent = {
            Column(modifier = Modifier.selectableGroup()) {
                val configs = listOf(
                    stringResource(R.string.settings_tap_default) to 0,
                    stringResource(R.string.settings_tap_left_handed) to 1,
                    stringResource(R.string.settings_tap_kindle) to 2,
                    stringResource(R.string.settings_tap_edge) to 3,
                )
                configs.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.tapZoneConfig == value,
                                onClick = { onEvent(SettingsEvent.SetTapZoneConfig(value)) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = state.tapZoneConfig == value, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_invert_tap_zones)) },
        supportingContent = { Text(stringResource(R.string.settings_invert_tap_zones_description)) },
        trailingContent = {
            Switch(
                checked = state.invertTapZones,
                onCheckedChange = { onEvent(SettingsEvent.SetInvertTapZones(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_tap_zones)) },
        supportingContent = { Text(stringResource(R.string.settings_show_tap_zones_description)) },
        trailingContent = {
            Switch(
                checked = state.showTapZonesOverlay,
                onCheckedChange = { onEvent(SettingsEvent.SetShowTapZonesOverlay(it)) },
            )
        },
    )

    // ── Volume keys ───────────────────────────────────────────────────
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_volume_keys))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_volume_keys_paging)) },
        supportingContent = { Text(stringResource(R.string.settings_volume_keys_paging_description)) },
        trailingContent = {
            Switch(
                checked = state.volumeKeysEnabled,
                onCheckedChange = { onEvent(SettingsEvent.SetVolumeKeysEnabled(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_volume_keys_inverted)) },
        supportingContent = { Text(stringResource(R.string.settings_volume_keys_inverted_description)) },
        trailingContent = {
            Switch(
                checked = state.volumeKeysInverted,
                onCheckedChange = { onEvent(SettingsEvent.SetVolumeKeysInverted(it)) },
                enabled = state.volumeKeysEnabled,
            )
        },
    )

    if (state.volumeKeysEnabled) {
        val perModeRows = listOf(
            Triple(0, stringResource(R.string.settings_vol_key_single_page), state.volumeKeyBehaviorSinglePage),
            Triple(1, stringResource(R.string.settings_vol_key_dual_page),   state.volumeKeyBehaviorDualPage),
            Triple(2, stringResource(R.string.settings_vol_key_webtoon),     state.volumeKeyBehaviorWebtoon),
            Triple(3, stringResource(R.string.settings_vol_key_smart_panels),state.volumeKeyBehaviorSmartPanels),
        )
        perModeRows.forEach { (modeIndex, modeName, behavior) ->
            val behaviorLabel = when (behavior) {
                VolumeKeyBehavior.DISABLED -> stringResource(R.string.settings_vol_key_behavior_disabled)
                VolumeKeyBehavior.NORMAL   -> stringResource(R.string.settings_vol_key_behavior_normal)
                VolumeKeyBehavior.INVERTED -> stringResource(R.string.settings_vol_key_behavior_inverted)
                else                       -> stringResource(R.string.settings_vol_key_behavior_inherit)
            }
            ListItem(
                headlineContent = { Text(modeName) },
                trailingContent = {
                    TextButton(onClick = {
                        onEvent(SettingsEvent.SetVolumeKeyBehaviorForMode(modeIndex, (behavior + 1) % 4))
                    }) {
                        Text(behaviorLabel)
                    }
                },
            )
        }
    }

    // ── Interaction ───────────────────────────────────────────────────
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_reader_interaction))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_long_tap_actions)) },
        supportingContent = { Text(stringResource(R.string.settings_long_tap_actions_description)) },
        trailingContent = {
            Switch(
                checked = state.showActionsOnLongTap,
                onCheckedChange = { onEvent(SettingsEvent.SetShowActionsOnLongTap(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_save_separate_folders)) },
        supportingContent = { Text(stringResource(R.string.settings_save_separate_folders_description)) },
        trailingContent = {
            Switch(
                checked = state.savePagesToSeparateFolders,
                onCheckedChange = { onEvent(SettingsEvent.SetSavePagesToSeparateFolders(it)) },
            )
        },
    )

    // ── Webtoon ───────────────────────────────────────────────────────
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_webtoon))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_webtoon_padding)) },
        supportingContent = {
            Column(modifier = Modifier.selectableGroup()) {
                val paddings = listOf(
                    stringResource(R.string.settings_padding_none) to 0,
                    stringResource(R.string.settings_padding_small) to 1,
                    stringResource(R.string.settings_padding_medium) to 2,
                    stringResource(R.string.settings_padding_large) to 3,
                )
                paddings.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.webtoonSidePadding == value,
                                onClick = { onEvent(SettingsEvent.SetWebtoonSidePadding(value)) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = state.webtoonSidePadding == value, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_menu_sensitivity)) },
        supportingContent = {
            Column(modifier = Modifier.selectableGroup()) {
                val sensitivities = listOf(
                    stringResource(R.string.settings_sensitivity_low) to 0,
                    stringResource(R.string.settings_sensitivity_medium) to 1,
                    stringResource(R.string.settings_sensitivity_high) to 2,
                )
                sensitivities.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = state.webtoonMenuHideSensitivity == value,
                                onClick = { onEvent(SettingsEvent.SetWebtoonMenuHideSensitivity(value)) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = state.webtoonMenuHideSensitivity == value, onClick = null)
                        Text(text = label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_webtoon_double_tap)) },
        supportingContent = { Text(stringResource(R.string.settings_webtoon_double_tap_description)) },
        trailingContent = {
            Switch(
                checked = state.webtoonDoubleTapZoom,
                onCheckedChange = { onEvent(SettingsEvent.SetWebtoonDoubleTapZoom(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_disable_zoom_out)) },
        supportingContent = { Text(stringResource(R.string.settings_disable_zoom_out_description)) },
        trailingContent = {
            Switch(
                checked = state.webtoonDisableZoomOut,
                onCheckedChange = { onEvent(SettingsEvent.SetWebtoonDisableZoomOut(it)) },
            )
        },
    )

    // ── E-ink ─────────────────────────────────────────────────────────
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_eink))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_eink_flash)) },
        supportingContent = { Text(stringResource(R.string.settings_eink_flash_description)) },
        trailingContent = {
            Switch(
                checked = state.einkFlashOnPageChange,
                onCheckedChange = { onEvent(SettingsEvent.SetEinkFlashOnPageChange(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_eink_bw)) },
        supportingContent = { Text(stringResource(R.string.settings_eink_bw_description)) },
        trailingContent = {
            Switch(
                checked = state.einkBlackAndWhite,
                onCheckedChange = { onEvent(SettingsEvent.SetEinkBlackAndWhite(it)) },
            )
        },
    )

    // ── Reading behaviour ─────────────────────────────────────────────
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    SectionHeader(title = stringResource(R.string.settings_reader_behavior))

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_skip_read)) },
        supportingContent = { Text(stringResource(R.string.settings_skip_read_description)) },
        trailingContent = {
            Switch(
                checked = state.skipReadChapters,
                onCheckedChange = { onEvent(SettingsEvent.SetSkipReadChapters(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_skip_filtered)) },
        supportingContent = { Text(stringResource(R.string.settings_skip_filtered_description)) },
        trailingContent = {
            Switch(
                checked = state.skipFilteredChapters,
                onCheckedChange = { onEvent(SettingsEvent.SetSkipFilteredChapters(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_skip_duplicates)) },
        supportingContent = { Text(stringResource(R.string.settings_skip_duplicates_description)) },
        trailingContent = {
            Switch(
                checked = state.skipDuplicateChapters,
                onCheckedChange = { onEvent(SettingsEvent.SetSkipDuplicateChapters(it)) },
            )
        },
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_show_chapter_transition)) },
        supportingContent = { Text(stringResource(R.string.settings_show_chapter_transition_description)) },
        trailingContent = {
            Switch(
                checked = state.alwaysShowChapterTransition,
                onCheckedChange = { onEvent(SettingsEvent.SetAlwaysShowChapterTransition(it)) },
            )
        },
    )
}
