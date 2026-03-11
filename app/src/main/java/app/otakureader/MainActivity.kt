package app.otakureader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.ui.theme.OtakuReaderTheme
import app.otakureader.util.DeepLinkHandler
import app.otakureader.util.DeepLinkResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var generalPreferences: GeneralPreferences
    
    // Store deep link result in a way that survives config changes
    private var pendingDeepLinkResult by mutableStateOf<DeepLinkResult?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLocaleFromPreferences()
        
        // Handle deep link or share intent from initial launch
        pendingDeepLinkResult = DeepLinkHandler.parseIntent(intent)
        
        setContent {
            val themeMode by generalPreferences.themeMode
                .collectAsStateWithLifecycle(initialValue = 0)
            val colorScheme by generalPreferences.colorScheme
                .collectAsStateWithLifecycle(initialValue = 0)
            val usePureBlackDarkMode by generalPreferences.usePureBlackDarkMode
                .collectAsStateWithLifecycle(initialValue = false)
            val useHighContrast by generalPreferences.useHighContrast
                .collectAsStateWithLifecycle(initialValue = false)
            val customAccentColor by generalPreferences.customAccentColor
                .collectAsStateWithLifecycle(initialValue = 0xFF1976D2L)

            val darkTheme = when (themeMode) {
                1 -> false              // light
                2 -> true               // dark
                else -> isSystemInDarkTheme() // system default
            }

            OtakuReaderTheme(
                darkTheme = darkTheme,
                colorScheme = colorScheme,
                usePureBlack = usePureBlackDarkMode,
                useHighContrast = useHighContrast,
                customAccentColor = customAccentColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OtakuReaderApp(
                        deepLinkResult = pendingDeepLinkResult,
                        onDeepLinkConsumed = { pendingDeepLinkResult = null }
                    )
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Handle new intents when activity is already running
        val result = DeepLinkHandler.parseIntent(intent)
        if (result !is DeepLinkResult.Invalid) {
            pendingDeepLinkResult = result
        }
    }

    private fun applyLocaleFromPreferences() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                generalPreferences.locale
                    .distinctUntilChanged()
                    .collect { locale ->
                        // On API 33+, the system manages per-app language via LocaleManager.
                        // Calling setApplicationLocales with an empty list would override
                        // any language the user selected in the system per-app language
                        // picker. Skip the call and let the system be the source of truth.
                        if (locale.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            return@collect
                        }
                        val localeList = if (locale.isEmpty()) {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            LocaleListCompat.forLanguageTags(locale)
                        }
                        AppCompatDelegate.setApplicationLocales(localeList)
                    }
            }
        }
    }
}

@Composable
fun OtakuReaderApp(
    deepLinkResult: DeepLinkResult? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    Scaffold { padding ->
        OtakuReaderNavHost(
            navController = navController,
            deepLinkResult = deepLinkResult,
            onDeepLinkConsumed = onDeepLinkConsumed,
            modifier = Modifier.padding(padding)
        )
    }
}