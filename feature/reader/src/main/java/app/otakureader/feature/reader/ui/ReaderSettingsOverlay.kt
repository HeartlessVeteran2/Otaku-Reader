package app.otakureader.feature.reader.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.otakureader.domain.model.ColorFilterMode
import app.otakureader.domain.model.ReaderMode
import app.otakureader.domain.model.ReaderOrientation
import app.otakureader.domain.model.ReadingDirection
import app.otakureader.domain.model.TapInvertMode
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.ReaderEvent
import app.otakureader.feature.reader.ReaderSetting
import app.otakureader.feature.reader.ReaderState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderSettingsOverlay(
    isVisible: Boolean,
    state: ReaderState,
    onEvent: (ReaderEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val tabs = listOf(
        stringResource(R.string.reader_settings_tab_reading_mode),
        stringResource(R.string.reader_settings_tab_general),
        stringResource(R.string.reader_settings_tab_color_filter),
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        SecondaryTabRow(selectedTabIndex = pagerState.currentPage) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(title) },
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            when (page) {
                0 -> ReadingModeTab(state = state, onEvent = onEvent)
                1 -> GeneralTab(state = state, onEvent = onEvent)
                2 -> ColorFilterTab(state = state, onEvent = onEvent)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ReadingModeTab(state: ReaderState, onEvent: (ReaderEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SettingsSectionLabel(stringResource(R.string.reader_mode_title))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            ReaderMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { onEvent(ReaderEvent.OnModeChange(mode)) },
                    label = { Text(mode.toLabel()) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }

        SettingsDivider()

        SettingsSectionLabel(stringResource(R.string.reader_direction_title))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            ReadingDirection.entries.forEach { direction ->
                FilterChip(
                    selected = state.readingDirection == direction,
                    onClick = { onEvent(ReaderEvent.OnDirectionChange(direction)) },
                    label = { Text(direction.toLabel()) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }

        SettingsDivider()

        SettingsSectionLabel(stringResource(R.string.reader_orientation_title))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            ReaderOrientation.entries.forEach { orientation ->
                FilterChip(
                    selected = state.readerOrientation == orientation,
                    onClick = { onEvent(ReaderEvent.OnOrientationChange(orientation)) },
                    label = { Text(orientation.toLabel()) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }

        SettingsDivider()

        if (state.mode == ReaderMode.WEBTOON) {
            WebtoonViewerSettings(state = state, onEvent = onEvent)
        } else {
            PagerViewerSettings(state = state, onEvent = onEvent)
        }
    }
}

@Composable
private fun PagerViewerSettings(state: ReaderState, onEvent: (ReaderEvent) -> Unit) {
    SettingsSectionLabel(stringResource(R.string.reader_scale_title))
    val scaleLabels = listOf(
        stringResource(R.string.reader_scale_fit_screen),
        stringResource(R.string.reader_scale_fit_width),
        stringResource(R.string.reader_scale_fit_height),
        stringResource(R.string.reader_scale_original),
        stringResource(R.string.reader_scale_smart_fit),
    )
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        scaleLabels.forEachIndexed { index, label ->
            FilterChip(
                selected = state.readerScale == index,
                onClick = { onEvent(ReaderEvent.SetReaderScale(index)) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }

    SettingsDivider()

    SettingsToggleRow(
        label = stringResource(R.string.reader_crop_borders),
        checked = state.cropBordersEnabled,
        onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.CROP_BORDERS)) },
    )
    SettingsToggleRow(
        label = stringResource(R.string.reader_auto_zoom_wide),
        checked = state.autoZoomWideImages,
        onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.AUTO_ZOOM_WIDE_IMAGES)) },
    )
    SettingsToggleRow(
        label = stringResource(R.string.reader_animate_transitions),
        checked = state.animatePageTransitions,
        onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.ANIMATE_PAGE_TRANSITIONS)) },
    )

    SettingsDivider()

    SettingsSectionLabel(stringResource(R.string.reader_page_layout_title))
    val pageLayoutLabels = listOf(
        stringResource(R.string.reader_page_layout_single),
        stringResource(R.string.reader_page_layout_double),
        stringResource(R.string.reader_page_layout_automatic),
    )
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        pageLayoutLabels.forEachIndexed { index, label ->
            FilterChip(
                selected = state.pageLayout == index,
                onClick = { onEvent(ReaderEvent.SetPageLayout(index)) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }

    SettingsDivider()

    SettingsSectionLabel(stringResource(R.string.reader_landscape_zoom_title))
    val landscapeZoomLabels = listOf(
        stringResource(R.string.reader_landscape_zoom_fit),
        stringResource(R.string.reader_landscape_zoom_in),
    )
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        landscapeZoomLabels.forEachIndexed { index, label ->
            FilterChip(
                selected = state.landscapeZoomScaleType == index,
                onClick = { onEvent(ReaderEvent.SetLandscapeZoomScaleType(index)) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }

    SettingsDivider()

    NavigationModeSection(
        sectionLabel = stringResource(R.string.reader_nav_mode_title),
        selectedMode = state.navigationModePager,
        onModeSelect = { onEvent(ReaderEvent.SetNavigationModePager(it)) },
        selectedInvert = state.tapInvertModePager,
        onInvertSelect = { onEvent(ReaderEvent.SetTapInvertModePager(it)) },
        smallerTapZone = state.smallerTapZone,
        onSmallerTapZoneToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.SMALLER_TAP_ZONE)) },
    )
}

@Composable
private fun WebtoonViewerSettings(state: ReaderState, onEvent: (ReaderEvent) -> Unit) {
    SettingsSectionLabel(stringResource(R.string.reader_webtoon_side_padding))
    val paddingLabels = listOf(
        stringResource(R.string.reader_webtoon_side_padding_none),
        stringResource(R.string.reader_webtoon_side_padding_small),
        stringResource(R.string.reader_webtoon_side_padding_medium),
        stringResource(R.string.reader_webtoon_side_padding_large),
    )
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        paddingLabels.forEachIndexed { index, label ->
            FilterChip(
                selected = state.webtoonSidePadding == index,
                onClick = { onEvent(ReaderEvent.SetWebtoonSidePadding(index)) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }

    SettingsDivider()

    SettingsToggleRow(
        label = stringResource(R.string.reader_crop_borders),
        checked = state.cropBordersEnabled,
        onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.CROP_BORDERS)) },
    )
    SettingsToggleRow(
        label = stringResource(R.string.reader_webtoon_double_tap_zoom),
        checked = state.webtoonDoubleTapZoom,
        onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.WEBTOON_DOUBLE_TAP_ZOOM)) },
    )
    SettingsToggleRow(
        label = stringResource(R.string.reader_webtoon_disable_zoom_out),
        checked = state.webtoonDisableZoomOut,
        onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.WEBTOON_DISABLE_ZOOM_OUT)) },
    )
    SettingsToggleRow(
        label = stringResource(R.string.reader_webtoon_pinch_to_zoom),
        checked = state.webtoonPinchToZoomEnabled,
        onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.WEBTOON_PINCH_TO_ZOOM)) },
    )

    SettingsDivider()

    SettingsSectionLabel(stringResource(R.string.reader_webtoon_scale_type))
    val webtoonScaleLabels = listOf(
        stringResource(R.string.reader_webtoon_scale_fit_width),
        stringResource(R.string.reader_webtoon_scale_stretch),
        stringResource(R.string.reader_webtoon_scale_fit_page),
    )
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        webtoonScaleLabels.forEachIndexed { index, label ->
            FilterChip(
                selected = state.webtoonScaleType == index,
                onClick = { onEvent(ReaderEvent.SetWebtoonScaleType(index)) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }

    SettingsDivider()

    NavigationModeSection(
        sectionLabel = stringResource(R.string.reader_nav_mode_title),
        selectedMode = state.navigationModeWebtoon,
        onModeSelect = { onEvent(ReaderEvent.SetNavigationModeWebtoon(it)) },
        selectedInvert = state.tapInvertModeWebtoon,
        onInvertSelect = { onEvent(ReaderEvent.SetTapInvertModeWebtoon(it)) },
        smallerTapZone = state.smallerTapZone,
        onSmallerTapZoneToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.SMALLER_TAP_ZONE)) },
    )
}

@Composable
private fun GeneralTab(state: ReaderState, onEvent: (ReaderEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SettingsSectionLabel(stringResource(R.string.reader_background))
        val bgLabels = listOf(
            stringResource(R.string.reader_bg_black),
            stringResource(R.string.reader_bg_white),
            stringResource(R.string.reader_bg_grey),
            stringResource(R.string.reader_bg_auto),
        )
        val bgValues = listOf(0, 1, 2, 3)
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            bgLabels.forEachIndexed { index, label ->
                FilterChip(
                    selected = state.backgroundColor == bgValues[index],
                    onClick = { onEvent(ReaderEvent.SetBackgroundColor(bgValues[index])) },
                    label = { Text(label) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }

        SettingsDivider()

        SettingsToggleRow(
            label = stringResource(R.string.reader_show_reading_timer),
            checked = state.showReadingTimer,
            onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.SHOW_READING_TIMER)) },
        )
        SettingsToggleRow(
            label = stringResource(R.string.reader_show_battery_time),
            checked = state.showBatteryTime,
            onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.SHOW_BATTERY_TIME)) },
        )
        SettingsToggleRow(
            label = stringResource(R.string.reader_show_page_number),
            checked = state.showPageNumber,
            onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.SHOW_PAGE_NUMBER)) },
        )
        SettingsToggleRow(
            label = stringResource(R.string.reader_fullscreen),
            checked = state.isFullscreen,
            onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.FULLSCREEN)) },
        )
        SettingsToggleRow(
            label = stringResource(R.string.reader_show_content_in_cutout),
            checked = state.showContentInCutout,
            onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.SHOW_CONTENT_IN_CUTOUT)) },
        )
        SettingsToggleRow(
            label = stringResource(R.string.reader_keep_screen_on),
            checked = state.keepScreenOn,
            onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.KEEP_SCREEN_ON)) },
        )
        SettingsToggleRow(
            label = stringResource(R.string.reader_long_tap_actions),
            checked = state.showActionsOnLongTap,
            onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.SHOW_ACTIONS_ON_LONG_TAP)) },
        )
        SettingsToggleRow(
            label = stringResource(R.string.reader_always_show_chapter_transition),
            checked = state.alwaysShowChapterTransition,
            onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.ALWAYS_SHOW_CHAPTER_TRANSITION)) },
        )
        SettingsToggleRow(
            label = stringResource(R.string.reader_eink_flash),
            checked = state.einkFlashOnPageChange,
            onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.EINK_FLASH_ON_PAGE_CHANGE)) },
        )
        SettingsToggleRow(
            label = stringResource(R.string.reader_incognito_mode),
            checked = state.incognitoMode,
            onToggle = { onEvent(ReaderEvent.ToggleSetting(ReaderSetting.INCOGNITO_MODE)) },
        )
    }
}

@Composable
private fun ColorFilterTab(state: ReaderState, onEvent: (ReaderEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SettingsSectionLabel(stringResource(R.string.reader_brightness))
        Slider(
            value = state.brightness,
            onValueChange = { onEvent(ReaderEvent.OnBrightnessChange(it)) },
            valueRange = 0.1f..1.5f,
            modifier = Modifier.fillMaxWidth(),
        )

        SettingsDivider()

        SettingsSectionLabel(stringResource(R.string.reader_color_filter))
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            ColorFilterMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.colorFilterMode == mode,
                    onClick = { onEvent(ReaderEvent.SetColorFilterMode(mode)) },
                    label = { Text(mode.toLabel()) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
        }

        if (state.colorFilterMode == ColorFilterMode.CUSTOM_TINT) {
            SettingsDivider()
            CustomTintSection(
                tintColor = state.customTintColor,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun CustomTintSection(tintColor: Long, onEvent: (ReaderEvent) -> Unit) {
    val alpha = ((tintColor shr 24) and 0xFFL).toInt()
    val red = ((tintColor shr 16) and 0xFFL).toInt()
    val green = ((tintColor shr 8) and 0xFFL).toInt()
    val blue = (tintColor and 0xFFL).toInt()

    SettingsSectionLabel(stringResource(R.string.reader_opacity))
    Slider(
        value = alpha / 255f,
        onValueChange = { fraction ->
            val newAlpha = (fraction * 255).toLong()
            val newColor = (newAlpha shl 24) or (red.toLong() shl 16) or (green.toLong() shl 8) or blue.toLong()
            onEvent(ReaderEvent.SetCustomTintColor(newColor))
        },
        valueRange = 0f..1f,
        modifier = Modifier.fillMaxWidth(),
    )

    SettingsSectionLabel(stringResource(R.string.reader_tint_color))
    val tintPresets: List<Pair<String, Long>> = listOf(
        stringResource(R.string.reader_tint_blue) to 0xFF1E90FFL,
        stringResource(R.string.reader_tint_red) to 0xFFDC143CL,
        stringResource(R.string.reader_tint_orange) to 0xFFFF8C00L,
        stringResource(R.string.reader_tint_yellow) to 0xFFFFD700L,
        stringResource(R.string.reader_tint_green) to 0xFF228B22L,
        stringResource(R.string.reader_tint_purple) to 0xFF8B008BL,
        stringResource(R.string.reader_tint_brown) to 0xFF8B4513L,
        stringResource(R.string.reader_tint_grey) to 0xFF808080L,
    )
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        tintPresets.forEach { (label, baseColor) ->
            val currentRgb = tintColor and 0x00FFFFFFL
            val presetRgb = baseColor and 0x00FFFFFFL
            FilterChip(
                selected = currentRgb == presetRgb,
                onClick = {
                    val newColor = (alpha.toLong() shl 24) or presetRgb
                    onEvent(ReaderEvent.SetCustomTintColor(newColor))
                },
                label = { Text(label) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SettingsDivider() {
    Spacer(modifier = Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SettingsToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ReaderMode.toLabel(): String = when (this) {
    ReaderMode.SINGLE_PAGE -> stringResource(R.string.reader_mode_single)
    ReaderMode.DUAL_PAGE -> stringResource(R.string.reader_mode_dual)
    ReaderMode.WEBTOON -> stringResource(R.string.reader_mode_webtoon)
    ReaderMode.SMART_PANELS -> stringResource(R.string.reader_mode_smart)
}

@Composable
private fun ReadingDirection.toLabel(): String = when (this) {
    ReadingDirection.LTR -> stringResource(R.string.reader_direction_ltr)
    ReadingDirection.RTL -> stringResource(R.string.reader_direction_rtl)
    ReadingDirection.VERTICAL -> stringResource(R.string.reader_direction_vertical)
}

@Composable
private fun ReaderOrientation.toLabel(): String = when (this) {
    ReaderOrientation.DEFAULT -> stringResource(R.string.reader_orientation_default)
    ReaderOrientation.FREE -> stringResource(R.string.reader_orientation_free)
    ReaderOrientation.PORTRAIT -> stringResource(R.string.reader_orientation_portrait)
    ReaderOrientation.LANDSCAPE -> stringResource(R.string.reader_orientation_landscape)
    ReaderOrientation.LOCKED_PORTRAIT -> stringResource(R.string.reader_orientation_locked_portrait)
    ReaderOrientation.LOCKED_LANDSCAPE -> stringResource(R.string.reader_orientation_locked_landscape)
    ReaderOrientation.REVERSE_PORTRAIT -> stringResource(R.string.reader_orientation_reverse_portrait)
}

@Composable
private fun ColorFilterMode.toLabel(): String = when (this) {
    ColorFilterMode.NONE -> stringResource(R.string.reader_color_filter_none)
    ColorFilterMode.SEPIA -> stringResource(R.string.reader_color_filter_sepia)
    ColorFilterMode.GRAYSCALE -> stringResource(R.string.reader_color_filter_greyscale)
    ColorFilterMode.INVERT -> stringResource(R.string.reader_color_filter_invert)
    ColorFilterMode.CUSTOM_TINT -> stringResource(R.string.reader_color_filter_custom_tint)
}

@Composable
private fun TapInvertMode.toLabel(): String = when (this) {
    TapInvertMode.NONE -> stringResource(R.string.reader_tap_invert_none)
    TapInvertMode.HORIZONTAL -> stringResource(R.string.reader_tap_invert_horizontal)
    TapInvertMode.VERTICAL -> stringResource(R.string.reader_tap_invert_vertical)
    TapInvertMode.BOTH -> stringResource(R.string.reader_tap_invert_both)
}

@Composable
private fun NavigationModeSection(
    sectionLabel: String,
    selectedMode: Int,
    onModeSelect: (Int) -> Unit,
    selectedInvert: TapInvertMode,
    onInvertSelect: (TapInvertMode) -> Unit,
    smallerTapZone: Boolean,
    onSmallerTapZoneToggle: () -> Unit,
) {
    val navLabels = listOf(
        stringResource(R.string.reader_nav_mode_default),
        stringResource(R.string.reader_nav_mode_l),
        stringResource(R.string.reader_nav_mode_kindlish),
        stringResource(R.string.reader_nav_mode_edge),
        stringResource(R.string.reader_nav_mode_right_and_left),
        stringResource(R.string.reader_nav_mode_disabled),
    )

    SettingsSectionLabel(sectionLabel)
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        navLabels.forEachIndexed { index, label ->
            FilterChip(
                selected = selectedMode == index,
                onClick = { onModeSelect(index) },
                label = { Text(label) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    SettingsSectionLabel(stringResource(R.string.reader_tap_invert_title))
    FlowRow(modifier = Modifier.fillMaxWidth()) {
        TapInvertMode.entries.forEach { mode ->
            FilterChip(
                selected = selectedInvert == mode,
                onClick = { onInvertSelect(mode) },
                label = { Text(mode.toLabel()) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }

    SettingsToggleRow(
        label = stringResource(R.string.reader_smaller_tap_zone),
        checked = smallerTapZone,
        onToggle = onSmallerTapZoneToggle,
    )
}
