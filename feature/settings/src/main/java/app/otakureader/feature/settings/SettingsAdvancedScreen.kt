package app.otakureader.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.otakureader.core.navigation.Route
import app.otakureader.core.preferences.DohProvider
import app.otakureader.feature.settings.viewmodel.AdvancedSettingsViewModel

/** Where "Don't kill my app" points — per-manufacturer instructions for background restrictions. */
private const val DONT_KILL_MY_APP_URL = "https://dontkillmyapp.com/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAdvancedScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdvancedSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val cookiesClearedMessage = stringResource(R.string.settings_advanced_cookies_cleared)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                AdvancedSettingsEffect.CookiesCleared ->
                    snackbarHostState.showSnackbar(cookiesClearedMessage)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_advanced)) },
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
            AdvancedSettingsContent(
                state = state,
                onEvent = viewModel::onEvent,
                onOpenUrl = { context.openUrl(it) },
                onRequestBatteryExemption = { context.openBatteryOptimizationSettings() },
            )
        }
    }
}

fun NavGraphBuilder.settingsAdvancedScreen(onNavigateBack: () -> Unit) {
    composable<Route.SettingsAdvanced> {
        SettingsAdvancedScreen(onNavigateBack = onNavigateBack)
    }
}

@Composable
private fun AdvancedSettingsContent(
    state: AdvancedSettingsState,
    onEvent: (AdvancedSettingsEvent) -> Unit,
    onOpenUrl: (String) -> Unit,
    onRequestBatteryExemption: () -> Unit,
) {
    SectionHeader(title = stringResource(R.string.settings_advanced_network))

    UserAgentField(state = state, onEvent = onEvent)
    DohProviderPicker(selected = state.dohProvider, onEvent = onEvent)

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_advanced_verbose_logging)) },
        supportingContent = {
            Text(stringResource(R.string.settings_advanced_verbose_logging_summary))
        },
        trailingContent = {
            Switch(
                checked = state.verboseLogging,
                onCheckedChange = { onEvent(AdvancedSettingsEvent.SetVerboseLogging(it)) },
            )
        },
    )

    HorizontalDivider()
    SectionHeader(title = stringResource(R.string.settings_advanced_data))

    ClearCookiesRow(onEvent = onEvent)

    HorizontalDivider()
    SectionHeader(title = stringResource(R.string.settings_advanced_background))

    val context = LocalContext.current
    // Re-read on every composition rather than held in state: the user grants this in the system
    // settings app and returns here, and there is no callback to tell us they did. Recomposition
    // on resume is what makes the row correct again.
    val exempt = remember(context) { context.isIgnoringBatteryOptimizations() }
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_advanced_battery)) },
        supportingContent = {
            Text(
                stringResource(
                    if (exempt) {
                        R.string.settings_advanced_battery_granted
                    } else {
                        R.string.settings_advanced_battery_summary
                    },
                ),
            )
        },
        modifier = Modifier.clickable(onClick = onRequestBatteryExemption),
    )

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_advanced_dont_kill_my_app)) },
        supportingContent = {
            Text(stringResource(R.string.settings_advanced_dont_kill_my_app_summary))
        },
        modifier = Modifier.clickable { onOpenUrl(DONT_KILL_MY_APP_URL) },
    )
}

/**
 * The User-Agent override, committed on a button rather than on each keystroke.
 *
 * Writing per keystroke would send requests under every prefix of what is being typed, and a
 * half-typed identity is exactly the kind of thing a site blocks — so the field holds its own text
 * and only [AdvancedSettingsEvent.SetUserAgent] reaches the preference.
 */
@Composable
private fun UserAgentField(
    state: AdvancedSettingsState,
    onEvent: (AdvancedSettingsEvent) -> Unit,
) {
    // Keyed on the saved value so an external change (a reset) refreshes the field, while typing
    // — which does not change that key — is left alone.
    var text by remember(state.userAgent) { mutableStateOf(state.userAgent) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_advanced_user_agent)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_advanced_user_agent_summary))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(state.defaultUserAgent) },
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                Column {
                    TextButton(
                        onClick = { onEvent(AdvancedSettingsEvent.SetUserAgent(text)) },
                        enabled = text.trim() != state.userAgent,
                    ) {
                        Text(stringResource(R.string.settings_advanced_user_agent_save))
                    }
                    TextButton(
                        onClick = { onEvent(AdvancedSettingsEvent.ResetUserAgent) },
                        enabled = state.userAgent.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.settings_advanced_user_agent_reset))
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DohProviderPicker(
    selected: DohProvider,
    onEvent: (AdvancedSettingsEvent) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_advanced_doh)) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.settings_advanced_doh_summary))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    OutlinedTextField(
                        value = stringResource(selected.labelRes()),
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DohProvider.entries.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(stringResource(provider.labelRes())) },
                                onClick = {
                                    expanded = false
                                    onEvent(AdvancedSettingsEvent.SetDohProvider(provider))
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/**
 * Clearing cookies signs the user out of anything reached through a source's WebView, so it asks
 * first. There is no undo — the cookies are gone from the shared store — which is precisely why
 * the confirmation exists rather than a snackbar afterwards.
 */
@Composable
private fun ClearCookiesRow(onEvent: (AdvancedSettingsEvent) -> Unit) {
    var confirming by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_advanced_clear_cookies)) },
        supportingContent = {
            Text(stringResource(R.string.settings_advanced_clear_cookies_summary))
        },
        modifier = Modifier.clickable { confirming = true },
    )

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.settings_advanced_clear_cookies)) },
            text = { Text(stringResource(R.string.settings_advanced_clear_cookies_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onEvent(AdvancedSettingsEvent.ClearCookies)
                    },
                ) {
                    Text(stringResource(R.string.settings_advanced_clear_cookies_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.settings_advanced_cancel))
                }
            },
        )
    }
}

private fun DohProvider.labelRes(): Int = when (this) {
    DohProvider.OFF -> R.string.settings_advanced_doh_off
    DohProvider.CLOUDFLARE -> R.string.settings_advanced_doh_cloudflare
    DohProvider.GOOGLE -> R.string.settings_advanced_doh_google
    DohProvider.ADGUARD -> R.string.settings_advanced_doh_adguard
    DohProvider.QUAD9 -> R.string.settings_advanced_doh_quad9
}

/**
 * True when the app is exempt from Doze. Below API 23 there is no such thing, so the answer is
 * "yes" — nothing is restricting it.
 */
private fun Context.isIgnoringBatteryOptimizations(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val power = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
    return runCatching { power.isIgnoringBatteryOptimizations(packageName) }.getOrDefault(true)
}

/**
 * Opens the *list* of battery-optimised apps rather than asking for the exemption directly.
 *
 * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` needs the `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
 * permission, which Play policy allows only for a narrow set of app categories a manga reader is
 * not in. The settings list needs no permission and gets the user to the same switch.
 */
private fun Context.openBatteryOptimizationSettings() {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    // A device with no such settings activity throws; there is nothing useful to do about it and
    // crashing over a convenience shortcut would be worse than the row appearing to do nothing.
    runCatching { startActivity(intent) }
}

private fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
