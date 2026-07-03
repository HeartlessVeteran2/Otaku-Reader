@file:Suppress("MaxLineLength")
package app.otakureader.feature.browse

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.extension.domain.model.InstallStatus
import app.otakureader.core.ui.component.ErrorScreen
import app.otakureader.core.ui.component.LoadingScreen
import app.otakureader.feature.browse.R
import coil3.compose.AsyncImage
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Extensions browser — restructured to match Mihon/Komikku's single sectioned list.
 *
 * Komikku shows one scrolling list with section headers (Updates pending → Installed →
 * Available, grouped by language) and compact rows. Per the parity work, the old inline
 * search bar, NSFW/sort row, repository manager and security banner, and Installed/Available/
 * Updates sub-tabs were removed: repository management lives on its own screen (reachable from a
 * section-header action), and enable/disable + uninstall are reached by long-pressing a row
 * (matching Komikku's onLongClickItem affordance).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsBottomSheet(
    onDismiss: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRepositories: () -> Unit = {},
    onNavigateToExtensionDetail: (packageName: String) -> Unit = {},
    viewModel: ExtensionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = false
    )
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ExtensionsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is ExtensionsEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.fillMaxSize()
    ) {
        ExtensionsContent(
            state = state,
            onEvent = viewModel::onEvent,
            snackbarHostState = snackbarHostState,
            onClose = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            },
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToRepositories = onNavigateToRepositories,
            onNavigateToExtensionDetail = onNavigateToExtensionDetail,
        )
    }
}

/**
 * Scaffold-free body used both by [ExtensionsContent] (inside the bottom sheet / full-screen
 * Scaffold) and by [ExtensionsTabBody] (embedded directly in Browse's Extensions tab).
 */
@Composable
private fun ExtensionsBody(
    state: ExtensionsState,
    onEvent: (ExtensionsEvent) -> Unit,
    onNavigateToRepositories: () -> Unit,
    onNavigateToExtensionDetail: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val allEmpty = state.installedExtensions.isEmpty() &&
        state.availableExtensions.isEmpty() &&
        state.extensionsWithUpdates.isEmpty()

    when {
        state.isLoading && allEmpty -> LoadingScreen(modifier = modifier)
        state.error != null && allEmpty -> ErrorScreen(
            message = state.error,
            onRetry = { onEvent(ExtensionsEvent.Refresh) },
            modifier = modifier,
        )
        allEmpty -> EmptyExtensionsView(
            onManageRepositories = onNavigateToRepositories,
            modifier = modifier,
        )
        else -> ExtensionsList(
            state = state,
            onEvent = onEvent,
            onNavigateToRepositories = onNavigateToRepositories,
            onNavigateToExtensionDetail = onNavigateToExtensionDetail,
            modifier = modifier,
        )
    }

    // Unverified install confirmation dialog (sideloaded APKs with no repo-provided hash).
    if (state.showUnverifiedInstallDialog) {
        UnverifiedInstallDialog(
            extension = state.pendingUnverifiedExtension,
            onDismiss = { onEvent(ExtensionsEvent.DismissUnverifiedDialog) },
            onConfirm = { onEvent(ExtensionsEvent.ConfirmUnverifiedInstall) }
        )
    }
}

@Composable
private fun ExtensionsList(
    state: ExtensionsState,
    onEvent: (ExtensionsEvent) -> Unit,
    onNavigateToRepositories: () -> Unit,
    onNavigateToExtensionDetail: (packageName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Place the "manage repositories" header action on the Installed section when present,
    // otherwise on the first Available-language section, so repos are always reachable.
    val repoActionOnInstalled = state.installedExtensions.isNotEmpty()
    val availableByLang = state.availableExtensions.groupBy { it.lang }.toSortedMap(compareBy { it })

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        // ── Updates pending ──────────────────────────────────────────────────────────
        if (state.extensionsWithUpdates.isNotEmpty()) {
            item(key = "hdr_updates") {
                ExtensionSectionHeader(
                    title = stringResource(R.string.extensions_tab_updates),
                    actionText = stringResource(
                        if (state.isUpdatingAll) {
                            R.string.extensions_updating_all
                        } else {
                            R.string.extensions_update_all
                        },
                    ),
                    onAction = { onEvent(ExtensionsEvent.UpdateAllExtensions) },
                    actionEnabled = !state.isUpdatingAll,
                )
            }
            items(state.extensionsWithUpdates, key = { "upd_${it.id}" }) { extension ->
                ExtensionRow(
                    extension = extension,
                    signerMismatch = extension.pkgName in state.signerMismatchedPackages,
                    blockedReason = state.blockedPackages[extension.pkgName],
                    onEvent = onEvent,
                    onViewDetails = { onNavigateToExtensionDetail(extension.pkgName) },
                )
            }
        }

        // ── Installed ────────────────────────────────────────────────────────────────
        if (state.installedExtensions.isNotEmpty()) {
            item(key = "hdr_installed") {
                ExtensionSectionHeader(
                    title = stringResource(R.string.extensions_tab_installed),
                    actionText = stringResource(R.string.extensions_manage_repositories),
                    onAction = onNavigateToRepositories,
                )
            }
            items(state.installedExtensions, key = { "ins_${it.id}" }) { extension ->
                ExtensionRow(
                    extension = extension,
                    signerMismatch = extension.pkgName in state.signerMismatchedPackages,
                    blockedReason = state.blockedPackages[extension.pkgName],
                    onEvent = onEvent,
                    onViewDetails = { onNavigateToExtensionDetail(extension.pkgName) },
                )
            }
        }

        // ── Available, grouped by language ───────────────────────────────────────────
        availableByLang.entries.forEachIndexed { index, (lang, extensions) ->
            val showRepoAction = !repoActionOnInstalled && index == 0
            item(key = "hdr_avail_$lang") {
                ExtensionSectionHeader(
                    title = if (lang.isBlank()) {
                        stringResource(R.string.extensions_tab_available)
                    } else {
                        lang.uppercase(Locale.ROOT)
                    },
                    actionText = if (showRepoAction) {
                        stringResource(R.string.extensions_manage_repositories)
                    } else {
                        null
                    },
                    onAction = onNavigateToRepositories,
                )
            }
            items(extensions, key = { "avl_${it.id}" }) { extension ->
                ExtensionRow(
                    extension = extension,
                    signerMismatch = false,
                    blockedReason = state.blockedPackages[extension.pkgName],
                    onEvent = onEvent,
                    onViewDetails = { onNavigateToExtensionDetail(extension.pkgName) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtensionsContent(
    state: ExtensionsState,
    onEvent: (ExtensionsEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRepositories: () -> Unit = {},
    onNavigateToExtensionDetail: (packageName: String) -> Unit = {},
    onNavigateToExtensionInstall: () -> Unit = {},
) {
    var searchActive by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            focusRequester.requestFocus()
        }
    }

    BackHandler(enabled = searchActive) {
        searchActive = false
        onEvent(ExtensionsEvent.OnSearchQueryChange(""))
    }

    Scaffold(
        topBar = {
            var overflowExpanded by remember { mutableStateOf(false) }
            TopAppBar(
                title = {
                    if (searchActive) {
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { onEvent(ExtensionsEvent.OnSearchQueryChange(it)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.extensions_search_placeholder)) },
                            singleLine = true,
                        )
                    } else {
                        Text(stringResource(R.string.extensions_sheet_title))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (searchActive) {
                            searchActive = false
                            onEvent(ExtensionsEvent.OnSearchQueryChange(""))
                        } else {
                            onClose()
                        }
                    }) {
                        Icon(
                            if (searchActive) {
                                Icons.AutoMirrored.Filled.ArrowBack
                            } else {
                                Icons.Default.Close
                            },
                            contentDescription = stringResource(R.string.extensions_close),
                        )
                    }
                },
                actions = {
                    if (!searchActive) {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.extensions_search_cd))
                        }
                        IconButton(onClick = { onEvent(ExtensionsEvent.Refresh) }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.extensions_refresh))
                        }
                        Box {
                            IconButton(onClick = { overflowExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.browse_more_options))
                            }
                            DropdownMenu(
                                expanded = overflowExpanded,
                                onDismissRequest = { overflowExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.extensions_manage_repositories)) },
                                    onClick = {
                                        overflowExpanded = false
                                        onNavigateToRepositories()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.extensions_menu_install_from_url)) },
                                    onClick = {
                                        overflowExpanded = false
                                        onNavigateToExtensionInstall()
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (state.showNsfw) {
                                                stringResource(R.string.browse_hide_nsfw)
                                            } else {
                                                stringResource(R.string.browse_show_nsfw)
                                            },
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (state.showNsfw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        onEvent(ExtensionsEvent.ToggleNsfw(!state.showNsfw))
                                        overflowExpanded = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.extensions_settings)) },
                                    onClick = {
                                        overflowExpanded = false
                                        onNavigateToSettings()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        ExtensionsBody(
            state = state,
            onEvent = onEvent,
            onNavigateToRepositories = onNavigateToRepositories,
            onNavigateToExtensionDetail = onNavigateToExtensionDetail,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        )
    }
}

/**
 * Full-screen Extensions destination ([Route.ExtensionCatalog] in the nav graph). Renders
 * [ExtensionsContent] (Scaffold + TopAppBar) as a regular slide-in screen (issue #1117).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRepositories: () -> Unit = {},
    onNavigateToExtensionDetail: (packageName: String) -> Unit = {},
    onNavigateToExtensionInstall: () -> Unit = {},
    viewModel: ExtensionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ExtensionsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is ExtensionsEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    ExtensionsContent(
        state = state,
        onEvent = viewModel::onEvent,
        snackbarHostState = snackbarHostState,
        onClose = onNavigateBack,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToRepositories = onNavigateToRepositories,
        onNavigateToExtensionDetail = onNavigateToExtensionDetail,
        onNavigateToExtensionInstall = onNavigateToExtensionInstall,
    )
}

/**
 * Scaffold-free Extensions content for embedding in Browse's Extensions tab.
 * Manages its own [ExtensionsViewModel] and snackbar; no TopAppBar (Browse provides it).
 */
@Composable
internal fun ExtensionsTabBody(
    onNavigateToRepositories: () -> Unit = {},
    onNavigateToExtensionDetail: (packageName: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ExtensionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ExtensionsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is ExtensionsEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Box(modifier = modifier) {
        ExtensionsBody(
            state = state,
            onEvent = viewModel::onEvent,
            onNavigateToRepositories = onNavigateToRepositories,
            onNavigateToExtensionDetail = onNavigateToExtensionDetail,
            modifier = Modifier.fillMaxSize(),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ExtensionSectionHeader(
    title: String,
    actionText: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionEnabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (actionText != null) {
            TextButton(onClick = onAction, enabled = actionEnabled) {
                Text(actionText)
            }
        }
    }
}

@Composable
private fun EmptyExtensionsView(
    onManageRepositories: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.browse_no_sources_title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onManageRepositories) {
                Text(stringResource(R.string.extensions_manage_repositories))
            }
        }
    }
}

/**
 * Compact extension row matching Komikku's [ExtensionItem]: icon, name, a single metadata line
 * (language · version · @repo · NSFW), and a trailing action (install / update / settings /
 * progress). Long-press opens enable-disable / uninstall / details for installed extensions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExtensionRow(
    extension: Extension,
    signerMismatch: Boolean,
    blockedReason: String?,
    onEvent: (ExtensionsEvent) -> Unit,
    onViewDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isInstalledKind = extension.status == InstallStatus.INSTALLED ||
        extension.status == InstallStatus.HAS_UPDATE

    Box {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        when (extension.status) {
                            InstallStatus.AVAILABLE, InstallStatus.ERROR ->
                                onEvent(ExtensionsEvent.InstallExtension(extension))
                            // No-op while a transient operation is in flight.
                            InstallStatus.INSTALLING, InstallStatus.UPDATING, InstallStatus.UNINSTALLING -> Unit
                            InstallStatus.INSTALLED, InstallStatus.HAS_UPDATE -> onViewDetails()
                        }
                    },
                    onLongClick = { if (isInstalledKind) menuExpanded = true },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = extension.iconUrl,
                contentDescription = extension.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = extension.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (signerMismatch) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = stringResource(R.string.extension_signer_mismatch_warning),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                ExtensionMetadataLine(extension = extension)
                if (blockedReason != null) {
                    Text(
                        text = stringResource(R.string.extension_blocked_warning, blockedReason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ExtensionRowAction(
                status = extension.status,
                onInstall = { onEvent(ExtensionsEvent.InstallExtension(extension)) },
                onUpdate = { onEvent(ExtensionsEvent.UpdateExtension(extension)) },
                onSettings = onViewDetails,
            )
        }

        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        if (extension.isEnabled) {
                            stringResource(R.string.extensions_disable)
                        } else {
                            stringResource(R.string.extensions_enable)
                        },
                    )
                },
                onClick = {
                    menuExpanded = false
                    onEvent(ExtensionsEvent.ToggleExtensionEnabled(extension, !extension.isEnabled))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.extension_detail_view_details)) },
                onClick = {
                    menuExpanded = false
                    onViewDetails()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.extensions_uninstall)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onEvent(ExtensionsEvent.UninstallExtension(extension))
                },
            )
        }
    }
}

@Composable
private fun ExtensionMetadataLine(
    extension: Extension,
    modifier: Modifier = Modifier,
) {
    val separator = stringResource(R.string.extension_meta_separator)
    val parts = buildList {
        if (extension.lang.isNotBlank()) add(extension.lang.uppercase(Locale.ROOT))
        if (extension.versionName.isNotBlank()) add(stringResource(R.string.extension_meta_version, extension.versionName))
        repoOwner(extension.repoUrl)?.let { add(stringResource(R.string.extension_meta_repo, it)) }
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = parts.joinToString(separator),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (extension.isNsfw) {
            Text(
                text = separator,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.browse_source_nsfw_badge),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ExtensionRowAction(
    status: InstallStatus,
    onInstall: () -> Unit,
    onUpdate: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (status) {
            InstallStatus.INSTALLED -> IconButton(onClick = onSettings) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.extensions_settings))
            }
            InstallStatus.HAS_UPDATE -> IconButton(onClick = onUpdate) {
                Icon(
                    Icons.Default.Update,
                    contentDescription = stringResource(R.string.extensions_update),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            InstallStatus.AVAILABLE, InstallStatus.ERROR -> IconButton(onClick = onInstall) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = stringResource(R.string.extensions_install),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            InstallStatus.INSTALLING, InstallStatus.UPDATING, InstallStatus.UNINSTALLING ->
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
        }
    }
}

@Composable
private fun UnverifiedInstallDialog(
    extension: Extension?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    if (extension == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.extension_unverified_title)) },
        text = {
            Column {
                Text(stringResource(R.string.extension_unverified_body))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.extension_install_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.extension_cancel))
            }
        }
    )
}

/**
 * Best-effort extraction of the repository owner from an extension's repo URL, e.g.
 * `https://raw.githubusercontent.com/keiyoushi/extensions/repo` → `keiyoushi`. Falls back to the
 * host when the path has no owner segment. Returns null when no URL is known.
 */
private fun repoOwner(repoUrl: String?): String? {
    if (repoUrl.isNullOrBlank()) return null
    val segments = repoUrl
        .substringAfter("://", repoUrl)
        .split("/")
        .filter { it.isNotBlank() }
    val host = segments.firstOrNull() ?: return null
    return when {
        // GitHub Pages: the owner is the subdomain, e.g. keiyoushi.github.io/extensions
        host.endsWith(".github.io", ignoreCase = true) -> host.substringBefore(".github.io")
        // raw.githubusercontent.com/<owner>/<repo>/... or github.com/<owner>/<repo>
        host.contains("githubusercontent") || host.contains("github") -> segments.getOrNull(1) ?: host
        else -> host
    }
}
