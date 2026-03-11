package app.otakureader.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.otakureader.feature.reader.R
import app.otakureader.feature.reader.model.ColorFilterMode
import app.otakureader.feature.reader.model.ReaderMode

/** Default custom tint color (semi-transparent blue) used when no custom color is set. */
private const val DEFAULT_CUSTOM_TINT_COLOR = 0x4000AAFFL

/**
 * Main reader menu overlay with controls and settings.
 * Appears when user taps the center of the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderMenuOverlay(
    isVisible: Boolean,
    chapterTitle: String,
    currentPage: Int,
    totalPages: Int,
    currentMode: ReaderMode,
    zoomLevel: Float,
    brightness: Float,
    colorFilterMode: ColorFilterMode = ColorFilterMode.NONE,
    customTintColor: Long = DEFAULT_CUSTOM_TINT_COLOR,
    readerBackgroundColor: Long? = null,
    onBrightnessChange: (Float) -> Unit,
    onModeChange: (ReaderMode) -> Unit,
    onColorFilterChange: (ColorFilterMode) -> Unit = {},
    onCustomTintColorChange: (Long) -> Unit = {},
    onReaderBackgroundColorChange: (Long?) -> Unit = {},
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    onToggleGallery: () -> Unit,
    onNavigateBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInVertically { -it },
        exit = fadeOut() + slideOutVertically { -it },
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = chapterTitle,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.reader_page_indicator, currentPage, totalPages),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.reader_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleFullscreen) {
                            Icon(Icons.Default.Fullscreen, contentDescription = stringResource(R.string.reader_fullscreen))
                        }
                        IconButton(onClick = onToggleGallery) {
                            Icon(Icons.Default.GridView, contentDescription = stringResource(R.string.reader_gallery))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
                
                HorizontalDivider()
                
                // Mode selector
                ReaderModeSelector(
                    currentMode = currentMode,
                    onModeChange = onModeChange,
                    modifier = Modifier.padding(16.dp)
                )
                
                HorizontalDivider()
                
                // Zoom controls
                ZoomControls(
                    zoomLevel = zoomLevel,
                    onZoomIn = onZoomIn,
                    onZoomOut = onZoomOut,
                    onResetZoom = onResetZoom,
                    modifier = Modifier.padding(16.dp)
                )
                
                HorizontalDivider()
                
                // Brightness slider
                BrightnessControl(
                    brightness = brightness,
                    onBrightnessChange = onBrightnessChange,
                    modifier = Modifier.padding(16.dp)
                )

                HorizontalDivider()

                // Color filter selector
                ColorFilterControl(
                    currentMode = colorFilterMode,
                    customTintColor = customTintColor,
                    onModeChange = onColorFilterChange,
                    onCustomTintColorChange = onCustomTintColorChange,
                    modifier = Modifier.padding(16.dp)
                )

                HorizontalDivider()

                // Per-manga reader background color
                ReaderBackgroundColorControl(
                    currentColor = readerBackgroundColor,
                    onColorChange = onReaderBackgroundColorChange,
                    modifier = Modifier.padding(16.dp)
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Hint text
                Text(
                    text = stringResource(R.string.reader_tap_to_hide),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
fun ReaderModeSelector(
    currentMode: ReaderMode,
    onModeChange: (ReaderMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.reader_mode_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ReaderMode.entries.forEach { mode ->
                ModeButton(
                    mode = mode,
                    isSelected = mode == currentMode,
                    onClick = { onModeChange(mode) }
                )
            }
        }
    }
}

@Composable
private fun ModeButton(
    mode: ReaderMode,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, label) = when (mode) {
        ReaderMode.SINGLE_PAGE -> Icons.Default.MenuBook to stringResource(R.string.reader_mode_single)
        ReaderMode.DUAL_PAGE -> Icons.AutoMirrored.Filled.NavigateNext to stringResource(R.string.reader_mode_dual)
        ReaderMode.WEBTOON -> Icons.Default.FitScreen to stringResource(R.string.reader_mode_webtoon)
        ReaderMode.SMART_PANELS -> Icons.Default.Settings to stringResource(R.string.reader_mode_smart)
    }
    
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

@Composable
fun ZoomControls(
    zoomLevel: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetZoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.reader_zoom),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(60.dp)
        )
        
        IconButton(onClick = onZoomOut) {
            Icon(Icons.Default.ZoomOut, contentDescription = stringResource(R.string.reader_zoom_out))
        }
        
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onResetZoom)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.reader_zoom_percentage, (zoomLevel * 100).toInt()),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        IconButton(onClick = onZoomIn) {
            Icon(Icons.Default.ZoomIn, contentDescription = stringResource(R.string.reader_zoom_in))
        }
    }
}

@Composable
fun BrightnessControl(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Brightness6,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.reader_brightness),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                valueRange = 0.1f..1.5f,
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = stringResource(R.string.reader_brightness_percentage, (brightness * 100).toInt()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(48.dp)
        )
    }
}

/**
 * Compact row of chips for selecting the active color-filter mode.
 * When [ColorFilterMode.CUSTOM_TINT] is selected, an additional row of preset
 * color swatches and an opacity slider are shown so users can pick their tint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorFilterControl(
    currentMode: ColorFilterMode,
    customTintColor: Long = DEFAULT_CUSTOM_TINT_COLOR,
    onModeChange: (ColorFilterMode) -> Unit,
    onCustomTintColorChange: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.reader_color_filter),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ColorFilterMode.entries) { mode ->
                FilterChip(
                    selected = currentMode == mode,
                    onClick = { onModeChange(mode) },
                    label = {
                        Text(
                            text = stringResource(ColorFilterMode.displayNameResId(mode)),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }

        // Custom tint picker — visible only when CUSTOM_TINT is the active mode
        if (currentMode == ColorFilterMode.CUSTOM_TINT) {
            Spacer(modifier = Modifier.height(12.dp))
            CustomTintPicker(
                currentColor = customTintColor,
                onColorChange = onCustomTintColorChange
            )
        }
    }
}

/**
 * Preset tint colors displayed as small swatches the user can tap.
 * Includes an opacity slider so the user can control how strong the tint is.
 */
@Composable
private fun CustomTintPicker(
    currentColor: Long,
    onColorChange: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    // Extract current alpha from the stored ARGB value
    val currentAlpha = ((currentColor shr 24) and 0xFF).toFloat() / 255f
    val currentRgb = currentColor and 0x00FFFFFFL

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.reader_tint_color),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Preset color swatches
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(TintPresets) { preset ->
                val isSelected = (currentRgb == preset.rgb)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF000000L or preset.rgb))
                        .then(
                            if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            else Modifier
                        )
                        .clickable {
                            val alpha = ((currentAlpha * 255).toInt().coerceIn(0, 255)).toLong()
                            onColorChange((alpha shl 24) or preset.rgb)
                        }
                        .semantics {
                            contentDescription = stringResource(preset.nameResId)
                            role = androidx.compose.ui.semantics.Role.RadioButton
                            selected = isSelected
                        }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Opacity slider
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(R.string.reader_opacity),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(60.dp)
            )

            Slider(
                value = currentAlpha,
                onValueChange = { newAlpha ->
                    val alpha = ((newAlpha * 255).toInt().coerceIn(0, 255)).toLong()
                    onColorChange((alpha shl 24) or currentRgb)
                },
                valueRange = 0.05f..0.9f,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = stringResource(R.string.reader_opacity_percentage, (currentAlpha * 100).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(40.dp)
            )
        }
    }
}

/**
 * Named preset tint colors available in the custom tint picker.
 * Each entry holds the RGB portion (no alpha) – alpha is controlled separately.
 * 
 * TODO: Consider making these themable or user-configurable in future versions.
 */
private data class TintPreset(val rgb: Long, val nameResId: Int)

/** Preset tint colors. These provide sensible defaults for common reading preferences. */
private val TintPresets = listOf(
    TintPreset(0x00AAFFL, R.string.reader_tint_blue),
    TintPreset(0xFF6B6BL, R.string.reader_tint_red),
    TintPreset(0xFFA726L, R.string.reader_tint_orange),
    TintPreset(0xFFEE58L, R.string.reader_tint_yellow),
    TintPreset(0x66BB6AL, R.string.reader_tint_green),
    TintPreset(0xAB47BCL, R.string.reader_tint_purple),
    TintPreset(0x8D6E63L, R.string.reader_tint_brown),
    TintPreset(0x78909CL, R.string.reader_tint_grey)
)

/**
 * Preset background colors for the per-manga reader background.
 * null means "default" (black).
 */
private data class BackgroundPreset(val nameResId: Int, val color: Long?)

private val ReaderBackgroundPresets = listOf(
    BackgroundPreset(R.string.reader_bg_default, null),
    BackgroundPreset(R.string.reader_bg_black, 0xFF000000L),
    BackgroundPreset(R.string.reader_bg_dark_grey, 0xFF1A1A1AL),
    BackgroundPreset(R.string.reader_bg_grey, 0xFF333333L),
    BackgroundPreset(R.string.reader_bg_warm_grey, 0xFF3E3832L),
    BackgroundPreset(R.string.reader_bg_dark_brown, 0xFF2B1D0EL),
    BackgroundPreset(R.string.reader_bg_sepia, 0xFFF5E6CCL),
    BackgroundPreset(R.string.reader_bg_white, 0xFFFFFFFFL),
    BackgroundPreset(R.string.reader_bg_dark_blue, 0xFF0D1B2AL)
)

/**
 * Control for selecting a per-manga reader background color.
 * Shows a row of color swatches with the currently selected one highlighted.
 * Each swatch exposes its preset name for screen readers via [contentDescription]
 * and announces its selection state via [selected] with a [Role.RadioButton] role.
 */
@Composable
fun ReaderBackgroundColorControl(
    currentColor: Long?,
    onColorChange: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.reader_background),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(ReaderBackgroundPresets.size) { index ->
                val (nameResId, colorValue) = ReaderBackgroundPresets[index]
                val isSelected = currentColor == colorValue
                val displayColor = if (colorValue != null) Color(colorValue.toInt()) else Color.Black
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(displayColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                            } else {
                                Modifier.border(
                                    width = 1.dp,
                                    color = Color.Gray.copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                            }
                        )
                        .clickable { onColorChange(colorValue) }
                        .semantics {
                            contentDescription = stringResource(nameResId)
                            role = androidx.compose.ui.semantics.Role.RadioButton
                            selected = isSelected
                        }
                )
            }
        }
    }
}
