package app.otakureader.feature.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/** Type of each onboarding page — drives which permission UI (if any) is shown. */
enum class OnboardingPageType {
    WELCOME,
    STORAGE,        // Optional download-location picker (non-blocking, unlike Komikku)
    NOTIFICATIONS,  // Android 13+ only
    BATTERY,        // Battery optimisation exclusion
    APPEARANCE,     // Theme selection (applies live)
    EXTENSIONS,     // "Install extensions" action
}

data class OnboardingPage(
    val type: OnboardingPageType,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
)

/**
 * Onboarding screen that mirrors the setup-focused flow used by Mihon and Komikku:
 *
 *  1. Welcome
 *  2. Storage location (optional — never blocks completion)
 *  3. [Android 13+] Notifications permission
 *  4. Battery-optimisation exclusion
 *  5. Appearance (theme — applies live)
 *  6. Install extensions (with quick-start hints)
 *
 * Each permission page shows a live status icon and an action button that is
 * disabled once the permission has been granted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit = onComplete,
    onNavigateToExtensions: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val downloadLocation by viewModel.downloadLocation.collectAsStateWithLifecycle()

    // Build page list dynamically; notifications page is Android 13+ only
    val pages = remember {
        buildList {
            add(
                OnboardingPage(
                    type = OnboardingPageType.WELCOME,
                    titleRes = R.string.onboarding_title_welcome,
                    descriptionRes = R.string.onboarding_desc_welcome,
                    icon = Icons.Default.MenuBook,
                ),
            )
            add(
                OnboardingPage(
                    type = OnboardingPageType.STORAGE,
                    titleRes = R.string.onboarding_title_storage,
                    descriptionRes = R.string.onboarding_desc_storage,
                    icon = Icons.Default.Folder,
                ),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(
                    OnboardingPage(
                        type = OnboardingPageType.NOTIFICATIONS,
                        titleRes = R.string.onboarding_title_notifications,
                        descriptionRes = R.string.onboarding_desc_notifications,
                        icon = Icons.Default.Notifications,
                    ),
                )
            }
            add(
                OnboardingPage(
                    type = OnboardingPageType.BATTERY,
                    titleRes = R.string.onboarding_title_battery,
                    descriptionRes = R.string.onboarding_desc_battery,
                    icon = Icons.Default.PowerSettingsNew,
                ),
            )
            add(
                OnboardingPage(
                    type = OnboardingPageType.APPEARANCE,
                    titleRes = R.string.onboarding_title_appearance,
                    descriptionRes = R.string.onboarding_desc_appearance,
                    icon = Icons.Default.Palette,
                ),
            )
            add(
                OnboardingPage(
                    type = OnboardingPageType.EXTENSIONS,
                    titleRes = R.string.onboarding_title_extensions,
                    descriptionRes = R.string.onboarding_desc_extensions,
                    icon = Icons.Default.Extension,
                ),
            )
        }
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    // ── Notifications permission ──────────────────────────────────────────────
    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                true // Pre-API 33 — permission not needed at runtime
            },
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    // ── Battery optimisation ──────────────────────────────────────────────────
    fun isBatteryOptimizationIgnored(): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    var batteryOptimizationIgnored by remember { mutableStateOf(isBatteryOptimizationIgnored()) }
    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { batteryOptimizationIgnored = isBatteryOptimizationIgnored() }

    // ── Storage location (optional — see OnboardingPageType.STORAGE) ─────────
    val storageLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Without this the grant is lost on reboot.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setDownloadLocation(uri.toString())
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            OnboardingBottomBar(
                currentPage = pagerState.currentPage,
                totalPages = pages.size,
                currentPageType = pages[pagerState.currentPage].type,
                onNext = {
                    if (pagerState.currentPage < pages.size - 1) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onComplete()
                    }
                },
                onSkip = onSkip,
                onNavigateToExtensions = onNavigateToExtensions,
            )
        },
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) { pageIndex ->
            OnboardingPageContent(
                page = pages[pageIndex],
                notificationsGranted = notificationsGranted,
                batteryOptimizationIgnored = batteryOptimizationIgnored,
                themeMode = themeMode,
                downloadLocation = downloadLocation,
                onThemeModeSelected = viewModel::setThemeMode,
                onRequestNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onRequestBatteryOptimization = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    batteryOptimizationLauncher.launch(intent)
                },
                onRequestStorageLocation = { storageLocationLauncher.launch(null) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page content
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    notificationsGranted: Boolean,
    batteryOptimizationIgnored: Boolean,
    themeMode: Int,
    downloadLocation: String?,
    onThemeModeSelected: (Int) -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onRequestStorageLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPermissionGranted = when (page.type) {
        OnboardingPageType.NOTIFICATIONS -> notificationsGranted
        OnboardingPageType.BATTERY -> batteryOptimizationIgnored
        OnboardingPageType.STORAGE -> downloadLocation != null
        else -> false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Icon with "granted" feedback
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    if (isPermissionGranted) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPermissionGranted) Icons.Default.Check else page.icon,
                contentDescription = stringResource(page.titleRes),
                modifier = Modifier.size(64.dp),
                tint = if (isPermissionGranted) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onPrimaryContainer
                },
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(page.titleRes),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(page.descriptionRes),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ── Per-page action buttons ───────────────────────────────────────────

        if (page.type == OnboardingPageType.STORAGE) {
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestStorageLocation,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (downloadLocation != null) Icons.Default.Check else Icons.Default.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (downloadLocation != null) {
                            R.string.onboarding_storage_selected
                        } else {
                            R.string.onboarding_btn_select_folder
                        },
                    ),
                )
            }
        }

        if (page.type == OnboardingPageType.NOTIFICATIONS) {
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestNotifications,
                enabled = !notificationsGranted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (notificationsGranted) Icons.Default.Check else Icons.Default.Notifications,
                    contentDescription = stringResource(
                        if (notificationsGranted) R.string.onboarding_notifications_granted
                        else R.string.onboarding_btn_grant_permission,
                    ),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (notificationsGranted) {
                            R.string.onboarding_notifications_granted
                        } else {
                            R.string.onboarding_btn_grant_permission
                        },
                    ),
                )
            }
        }

        if (page.type == OnboardingPageType.BATTERY) {
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRequestBatteryOptimization,
                enabled = !batteryOptimizationIgnored,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = if (batteryOptimizationIgnored) Icons.Default.Check else Icons.Default.BatteryFull,
                    contentDescription = stringResource(
                        if (batteryOptimizationIgnored) R.string.onboarding_battery_unrestricted
                        else R.string.onboarding_btn_disable_battery,
                    ),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (batteryOptimizationIgnored) {
                            R.string.onboarding_battery_unrestricted
                        } else {
                            R.string.onboarding_btn_disable_battery
                        },
                    ),
                )
            }
        }

        if (page.type == OnboardingPageType.APPEARANCE) {
            Spacer(modifier = Modifier.height(32.dp))
            ThemeOptionCard(
                label = stringResource(R.string.onboarding_theme_system),
                icon = Icons.Default.PhoneAndroid,
                selected = themeMode == 0,
                onClick = { onThemeModeSelected(0) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeOptionCard(
                label = stringResource(R.string.onboarding_theme_light),
                icon = Icons.Default.LightMode,
                selected = themeMode == 1,
                onClick = { onThemeModeSelected(1) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            ThemeOptionCard(
                label = stringResource(R.string.onboarding_theme_dark),
                icon = Icons.Default.DarkMode,
                selected = themeMode == 2,
                onClick = { onThemeModeSelected(2) },
            )
        }

        if (page.type == OnboardingPageType.EXTENSIONS) {
            Spacer(modifier = Modifier.height(24.dp))
            QuickStartHint(stringResource(R.string.onboarding_hint_browse))
            Spacer(modifier = Modifier.height(8.dp))
            QuickStartHint(stringResource(R.string.onboarding_hint_library))
            Spacer(modifier = Modifier.height(8.dp))
            QuickStartHint(stringResource(R.string.onboarding_hint_trackers))
        }
    }
}

/** A selectable card for one theme choice on the Appearance page. */
@Composable
private fun ThemeOptionCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            // Clip before clickable so the ripple follows the card's rounded corners.
            .clip(CardDefaults.shape)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

/** A single quick-start bullet on the Extensions page. */
@Composable
private fun QuickStartHint(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    totalPages: Int,
    currentPageType: OnboardingPageType,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onNavigateToExtensions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        // Page indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(totalPages) { index ->
                val isSelected = index == currentPage
                Box(
                    modifier = Modifier
                        .size(if (isSelected) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            },
                        ),
                )
                if (index < totalPages - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Extensions page: "Install Extensions" primary + "Get Started" outline
        if (currentPageType == OnboardingPageType.EXTENSIONS) {
            Button(
                onClick = onNavigateToExtensions,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.Extension,
                    contentDescription = stringResource(R.string.onboarding_btn_install_extensions),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_btn_install_extensions))
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_btn_get_started))
            }
        } else {
            // All other pages: "Next / Get Started" + optional "Skip"
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (currentPage == totalPages - 1) {
                            R.string.onboarding_btn_get_started
                        } else {
                            R.string.onboarding_btn_next
                        },
                    ),
                )
                if (currentPage < totalPages - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.onboarding_btn_next),
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.onboarding_btn_get_started),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (currentPage < totalPages - 1) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_btn_skip))
                }
            }
        }
    }
}

