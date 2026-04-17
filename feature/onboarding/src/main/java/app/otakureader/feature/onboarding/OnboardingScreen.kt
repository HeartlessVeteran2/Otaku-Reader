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
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Type of each onboarding page — drives which permission UI (if any) is shown. */
enum class OnboardingPageType {
    WELCOME,
    NOTIFICATIONS,  // Android 13+ only
    BATTERY,        // Battery optimisation exclusion
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
 *  2. [Android 13+] Notifications permission
 *  3. Battery-optimisation exclusion
 *  4. Install extensions
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
) {
    val context = LocalContext.current

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
    onRequestNotifications: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPermissionGranted = when (page.type) {
        OnboardingPageType.NOTIFICATIONS -> notificationsGranted
        OnboardingPageType.BATTERY -> batteryOptimizationIgnored
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

