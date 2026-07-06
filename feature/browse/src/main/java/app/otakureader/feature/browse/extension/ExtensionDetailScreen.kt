package app.otakureader.feature.browse.extension

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.otakureader.core.extension.domain.model.Extension
import app.otakureader.core.navigation.Route
import app.otakureader.core.ui.component.ErrorScreen
import app.otakureader.core.ui.component.LoadingScreen
import app.otakureader.feature.browse.R
import coil3.compose.AsyncImage
import java.util.Locale

// ─── Nav-graph extension ─────────────────────────────────────────────────────

/**
 * Registers [ExtensionDetailScreen] as a composable destination in the calling
 * [NavGraphBuilder] for the [Route.ExtensionDetail] route.
 */
fun NavGraphBuilder.extensionDetailScreen(
    onNavigateBack: () -> Unit,
) {
    composable<Route.ExtensionDetail> {
        ExtensionDetailScreen(
            onNavigateBack = onNavigateBack,
            viewModel = hiltViewModel(),
        )
    }
}

// ─── Screen ──────────────────────────────────────────────────────────────────

/**
 * Full-screen detail view for a single extension.
 *
 * Displays:
 * - Icon, name, version, package name
 * - Trust status badge
 * - Signer hash (monospace, truncated)
 * - Repository URL (clickable)
 * - Capability badges (Cloudflare, Readme, Changelog)
 * - Expandable sources list
 * - Trust / Untrust action button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExtensionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ExtensionDetailEffect.ShowSnackbar ->
                    snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.extension?.name
                            ?: stringResource(R.string.extension_detail_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.browse_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        when {
            state.isLoading -> LoadingScreen(modifier = Modifier.padding(padding))
            state.error != null -> ErrorScreen(
                message = state.error!!,
                onRetry = { viewModel.onEvent(ExtensionDetailEvent.Retry) },
                modifier = Modifier.padding(padding),
            )
            state.extension != null -> ExtensionDetailContent(
                extension = state.extension!!,
                onToggleTrust = { viewModel.onEvent(ExtensionDetailEvent.ToggleTrust) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

// ─── Content ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtensionDetailContent(
    extension: Extension,
    onToggleTrust: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ── Header row ───────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model = extension.iconUrl,
                contentDescription = extension.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.name,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.extension_detail_version, extension.versionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = extension.pkgName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ── Trust status badge ────────────────────────────────────────────────
        TrustStatusBadge(isTrusted = extension.isTrusted)

        HorizontalDivider()

        // ── Signer hash ───────────────────────────────────────────────────────
        extension.signatureHash?.let { hash ->
            LabeledSection(label = stringResource(R.string.extension_detail_signer)) {
                Text(
                    text = hash.take(SIGNER_DISPLAY_LENGTH) +
                        if (hash.length > SIGNER_DISPLAY_LENGTH) "…" else "",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── Repository URL ────────────────────────────────────────────────────
        extension.repoUrl?.takeIf { it.isNotBlank() }?.let { url ->
            LabeledSection(label = stringResource(R.string.extension_detail_repo)) {
                TextButton(
                    onClick = {
                        val uri = Uri.parse(url)
                        if (uri.scheme == "https" || uri.scheme == "http") {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        }
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Text(
                        text = url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // ── Capability badges ─────────────────────────────────────────────────
        CapabilityBadges(extension = extension)

        HorizontalDivider()

        // ── Sources list ──────────────────────────────────────────────────────
        SourcesList(extension = extension)

        HorizontalDivider()

        // ── Trust / Untrust button ────────────────────────────────────────────
        if (extension.isTrusted) {
            OutlinedButton(
                onClick = onToggleTrust,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.extension_detail_untrust))
            }
        } else {
            Button(
                onClick = onToggleTrust,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.extension_detail_trust))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

// ─── Sub-components ───────────────────────────────────────────────────────────

@Composable
private fun TrustStatusBadge(isTrusted: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (isTrusted) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.extension_trust_status_trusted),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.extension_trust_status_unverified),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CapabilityBadges(extension: Extension, modifier: Modifier = Modifier) {
    // Extension model doesn't expose individual capability flags yet.
    // We derive them from the available data on the Extension and its sources.
    val hasCloudflare = false  // Placeholder — future field in Extension model
    val hasReadme = !extension.repoUrl.isNullOrBlank()
    val hasChangelog = false   // Placeholder — future field in Extension model

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (extension.isNsfw) {
            CapabilityChip(label = "18+")
        }
        if (hasCloudflare) {
            CapabilityChip(label = "Cloudflare")
        }
        if (hasReadme) {
            CapabilityChip(label = "Readme")
        }
        if (hasChangelog) {
            CapabilityChip(label = "Changelog")
        }
        // Show language badge
        CapabilityChip(label = extension.lang.uppercase(Locale.ROOT))
    }
}

@Composable
private fun CapabilityChip(label: String) {
    SuggestionChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = SuggestionChipDefaults.suggestionChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun SourcesList(extension: Extension, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(true) }

    Column(modifier = modifier.animateContentSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.extension_detail_sources),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.extension_detail_sources_collapse
                        else R.string.extension_detail_sources_expand
                    ),
                )
            }
        }

        if (expanded) {
            if (extension.sources.isEmpty()) {
                Text(
                    text = stringResource(R.string.extension_detail_no_sources),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else {
                extension.sources.forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = source.name,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = source.lang.uppercase(Locale.ROOT),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

// ─── Constants ────────────────────────────────────────────────────────────────

/** Maximum characters of the signer hash shown before truncation. */
private const val SIGNER_DISPLAY_LENGTH = 40
