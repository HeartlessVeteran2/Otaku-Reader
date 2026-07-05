package app.otakureader.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.otakureader.core.preferences.DownloadPreferences
import app.otakureader.core.preferences.GeneralPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the onboarding wizard's Appearance and Storage steps.
 *
 * Exposes the persisted theme mode reactively so the selection survives process death
 * and the app re-themes live as the user taps an option.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val generalPreferences: GeneralPreferences,
    private val downloadPreferences: DownloadPreferences,
) : ViewModel() {

    /** Theme mode: 0 = system default, 1 = light, 2 = dark. */
    val themeMode: StateFlow<Int> = generalPreferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { generalPreferences.setThemeMode(mode) }
    }

    /** Display name entered by the user on the Name onboarding page. */
    val displayName: StateFlow<String> = generalPreferences.displayName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setDisplayName(name: String) {
        viewModelScope.launch { generalPreferences.setDisplayName(name) }
    }

    /**
     * Persisted download-location URI (as a string), or null when the user hasn't picked one —
     * in which case downloads use the app's default internal storage. Surfaced on the Storage
     * onboarding page purely so users know the choice exists; picking a folder here is optional
     * (onboarding never blocks on it, unlike Komikku).
     */
    val downloadLocation: StateFlow<String?> = downloadPreferences.downloadLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setDownloadLocation(location: String) {
        viewModelScope.launch { downloadPreferences.setDownloadLocation(location) }
    }
}
