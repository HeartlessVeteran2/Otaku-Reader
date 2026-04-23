package app.otakureader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.preferences.LibraryPreferences
import app.otakureader.core.ui.theme.OtakuReaderTheme
import app.otakureader.crash.CrashHandler
import app.otakureader.data.worker.LibraryUpdateScheduler
import app.otakureader.data.worker.LibraryUpdateWorker
import app.otakureader.util.DeepLinkHandler
import app.otakureader.util.DeepLinkResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        // I-2: Named constants for theme mode integers stored in GeneralPreferences.
        const val THEME_MODE_SYSTEM = 0
        const val THEME_MODE_LIGHT = 1
        const val THEME_MODE_DARK = 2
    }


    @Inject lateinit var generalPreferences: GeneralPreferences
    @Inject lateinit var libraryPreferences: LibraryPreferences
    @Inject lateinit var libraryUpdateScheduler: LibraryUpdateScheduler

    // Hold deep link result across recompositions for the current Activity instance
    private var pendingDeepLinkResult by mutableStateOf<DeepLinkResult?>(null)

    // Crash report from the previous run – shown once, then cleared from SharedPreferences
    private var pendingCrashReport by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyLocaleFromPreferences()

        // Read the crash report saved by the previous run. Only done on a fresh
        // launch (savedInstanceState == null) to avoid a redundant read on rotation;
        // the report is already gone from SharedPreferences after the first read.
        if (savedInstanceState == null) {
            pendingCrashReport = CrashHandler.getAndClearCrashReport(this)
        }

        // Trigger auto-refresh on app start if enabled (only on fresh launch, not recreation)
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val (updateIntervalHours, updateOnlyOnWifi) = combine(
                    generalPreferences.updateCheckInterval,
                    libraryPreferences.updateOnlyOnWifi
                ) { interval, wifiOnly ->
                    interval to wifiOnly
                }.first()
                libraryUpdateScheduler.schedule(updateIntervalHours, updateOnlyOnWifi)

                val autoRefresh = libraryPreferences.autoRefreshOnStart.first()
                if (autoRefresh) {
                    LibraryUpdateWorker.enqueue(applicationContext)
                }
            }
        }

        // Handle deep link or share intent only on initial launch
        if (savedInstanceState == null) {
            val result = DeepLinkHandler.parseIntent(intent)
            if (result !is DeepLinkResult.Invalid) {
                pendingDeepLinkResult = result
            }
        }

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
            // Observe onboarding status - defaults to false (show onboarding) for new users
            val onboardingCompleted by generalPreferences.onboardingCompleted
                .collectAsStateWithLifecycle(initialValue = false)

            // I-2: Use named constants instead of magic numbers for theme mode.
            val darkTheme = when (themeMode) {
                THEME_MODE_LIGHT -> false
                THEME_MODE_DARK -> true
                else -> isSystemInDarkTheme() // THEME_MODE_SYSTEM (0) = follow system
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
                        generalPreferences = generalPreferences,
                        libraryPreferences = libraryPreferences,
                        onboardingCompleted = onboardingCompleted,
                        deepLinkResult = pendingDeepLinkResult,
                        onDeepLinkConsumed = { pendingDeepLinkResult = null }
                    )

                    // Show crash report from previous run as an overlay dialog.
                    // The report is already removed from SharedPreferences at this point;
                    // dismissing just hides the dialog for this session.
                    pendingCrashReport?.let { report ->
                        CrashReportDialog(
                            report = report,
                            onDismiss = { pendingCrashReport = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
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
    generalPreferences: GeneralPreferences,
    libraryPreferences: LibraryPreferences,
    onboardingCompleted: Boolean,
    deepLinkResult: DeepLinkResult? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    
    // Observe new updates count for badge
    val newUpdatesCount by libraryPreferences.newUpdatesCount
        .collectAsStateWithLifecycle(initialValue = 0)

    Scaffold(
        bottomBar = {
            OtakuReaderBottomBar(
                navController = navController,
                newUpdatesCount = newUpdatesCount
            )
        }
    ) { padding ->
        OtakuReaderNavHost(
            navController = navController,
            onboardingCompleted = onboardingCompleted,
            deepLinkResult = deepLinkResult,
            onDeepLinkConsumed = onDeepLinkConsumed,
            // Set onboarding as complete when user finishes the flow
            onOnboardingComplete = {
                coroutineScope.launch {
                    generalPreferences.setOnboardingCompleted(true)
                }
            },
            modifier = Modifier.padding(padding)
        )
    }
}

/**
 * Modal dialog shown when [MainActivity] detects a crash report saved by the
 * previous run's [app.otakureader.crash.CrashHandler].
 *
 * - The stack trace is wrapped in a [SelectionContainer] so you can long-press
 *   any line on your phone to select and copy a specific portion.
 * - The **Copy** button copies the full report to the clipboard in one tap.
 * - The **Dismiss** button closes the dialog. Because [CrashHandler.getAndClearCrashReport]
 *   already removed the report from SharedPreferences when it was first read,
 *   the dialog will not reappear on the next launch — no separate "clear" step needed.
 */
@Composable
private fun CrashReportDialog(
    report: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.crash_report_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.crash_report_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                // SelectionContainer lets the user long-press to highlight specific
                // lines and copy them — useful when you cannot use Logcat.
                SelectionContainer {
                    Text(
                        text = report,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText("Crash Report", report)
                    )
                    // Android 13+ shows its own "Copied" system notification; show a
                    // Toast only on older versions to avoid double feedback.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.crash_report_copied),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            ) {
                Text(stringResource(R.string.crash_report_copy))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.crash_report_dismiss))
            }
        },
    )
}
