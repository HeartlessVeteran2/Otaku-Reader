package app.otakureader.feature.reader.panel

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.panelDetectionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "panel_detection_prefs"
)

/**
 * Repository for managing panel detection settings
 */
@Singleton
class PanelDetectionRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.panelDetectionDataStore

    /**
     * Enable/disable panel detection
     */
    val panelDetectionEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PANEL_DETECTION_ENABLED] ?: DEFAULT_PANEL_DETECTION_ENABLED
    }

    suspend fun setPanelDetectionEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PANEL_DETECTION_ENABLED] = enabled
        }
    }

    /**
     * Edge detection threshold (0-255)
     */
    val edgeThreshold: Flow<Int> = dataStore.data.map { preferences ->
        preferences[EDGE_THRESHOLD] ?: DEFAULT_EDGE_THRESHOLD
    }

    suspend fun setEdgeThreshold(threshold: Int) {
        dataStore.edit { preferences ->
            preferences[EDGE_THRESHOLD] = threshold.coerceIn(0, 255)
        }
    }

    /**
     * Minimum line length percentage (0.0-1.0)
     */
    val minLineLengthPercent: Flow<Float> = dataStore.data.map { preferences ->
        preferences[MIN_LINE_LENGTH_PERCENT] ?: DEFAULT_MIN_LINE_LENGTH_PERCENT
    }

    suspend fun setMinLineLengthPercent(percent: Float) {
        dataStore.edit { preferences ->
            preferences[MIN_LINE_LENGTH_PERCENT] = percent.coerceIn(0f, 1f)
        }
    }

    /**
     * Minimum panel size percentage (0.0-1.0)
     */
    val minPanelSizePercent: Flow<Float> = dataStore.data.map { preferences ->
        preferences[MIN_PANEL_SIZE_PERCENT] ?: DEFAULT_MIN_PANEL_SIZE_PERCENT
    }

    suspend fun setMinPanelSizePercent(percent: Float) {
        dataStore.edit { preferences ->
            preferences[MIN_PANEL_SIZE_PERCENT] = percent.coerceIn(0f, 1f)
        }
    }

    /**
     * Auto-advance to next panel
     */
    val autoAdvancePanel: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_ADVANCE_PANEL] ?: DEFAULT_AUTO_ADVANCE_PANEL
    }

    suspend fun setAutoAdvancePanel(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_ADVANCE_PANEL] = enabled
        }
    }

    /**
     * Show panel borders overlay
     */
    val showPanelBorders: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_PANEL_BORDERS] ?: DEFAULT_SHOW_PANEL_BORDERS
    }

    suspend fun setShowPanelBorders(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_PANEL_BORDERS] = enabled
        }
    }

    /**
     * Get current panel detection configuration
     */
    suspend fun getPanelDetectionConfig(isRightToLeft: Boolean): PanelDetectionConfig {
        val edge = edgeThreshold.first()
        val minLine = minLineLengthPercent.first()
        val minSize = minPanelSizePercent.first()

        return PanelDetectionConfig(
            edgeThreshold = edge,
            minLineLengthPercent = minLine,
            minPanelWidthPercent = minSize,
            minPanelHeightPercent = minSize,
            isRightToLeft = isRightToLeft
        )
    }

    companion object {
        private val PANEL_DETECTION_ENABLED = booleanPreferencesKey("panel_detection_enabled")
        private val EDGE_THRESHOLD = intPreferencesKey("panel_edge_threshold")
        private val MIN_LINE_LENGTH_PERCENT = floatPreferencesKey("panel_min_line_length_percent")
        private val MIN_PANEL_SIZE_PERCENT = floatPreferencesKey("panel_min_panel_size_percent")
        private val AUTO_ADVANCE_PANEL = booleanPreferencesKey("panel_auto_advance")
        private val SHOW_PANEL_BORDERS = booleanPreferencesKey("panel_show_borders")

        const val DEFAULT_PANEL_DETECTION_ENABLED = true
        const val DEFAULT_EDGE_THRESHOLD = 30
        const val DEFAULT_MIN_LINE_LENGTH_PERCENT = 0.4f
        const val DEFAULT_MIN_PANEL_SIZE_PERCENT = 0.1f
        const val DEFAULT_AUTO_ADVANCE_PANEL = false
        const val DEFAULT_SHOW_PANEL_BORDERS = false
    }
}
