package app.otakureader.feature.about

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.otakureader.core.ui.R as CoreUiR
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.feature.about.R
import app.otakureader.feature.about.developer.AboutDeveloperEffect
import app.otakureader.feature.about.developer.AboutDeveloperEvent
import app.otakureader.feature.about.developer.AboutDeveloperViewModel
import app.otakureader.feature.about.developer.DeveloperPromptDialog
import kotlinx.coroutines.flow.collectLatest

/**
 * About screen showing app information, help, FAQ, licenses, and credits.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit = {},
    onNavigateToDeveloper: () -> Unit = {},
    modifier: Modifier = Modifier,
    developerViewModel: AboutDeveloperViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val developerState by developerViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(developerViewModel.effect) {
        developerViewModel.effect.collectLatest { effect ->
            when (effect) {
                is AboutDeveloperEffect.NavigateToDeveloper -> onNavigateToDeveloper()
            }
        }
    }

    DeveloperPromptDialog(
        state = developerState,
        onSubmit = { developerViewModel.onEvent(AboutDeveloperEvent.SubmitPassphrase(it)) },
        onDismiss = { developerViewModel.onEvent(AboutDeveloperEvent.DismissPrompt) },
    )
    val versionName = remember {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: ""
        } catch (_: PackageManager.NameNotFoundException) {
            ""
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.about_navigate_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // App Info Header
            AppInfoHeader(
                versionName = versionName,
                onVersionClick = { developerViewModel.onEvent(AboutDeveloperEvent.VersionTapped) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Help & Support Section
            AboutSectionTitle(stringResource(R.string.about_help_support_section))

            AboutListItem(
                icon = Icons.AutoMirrored.Filled.Help,
                title = stringResource(R.string.about_how_to_add_sources_title),
                subtitle = stringResource(R.string.about_how_to_add_sources_subtitle),
                onClick = {
                    context.openUrl("https://github.com/Heartless-Veteran/Otaku-Reader/wiki/Adding-Sources")
                }
            )

            AboutListItem(
                icon = Icons.Default.QuestionAnswer,
                title = stringResource(R.string.about_faq_title),
                subtitle = stringResource(R.string.about_faq_subtitle),
                onClick = {
                    context.openUrl("https://github.com/Heartless-Veteran/Otaku-Reader/wiki/FAQ")
                }
            )

            AboutListItem(
                icon = Icons.Default.LocalLibrary,
                title = stringResource(R.string.about_getting_started_title),
                subtitle = stringResource(R.string.about_getting_started_subtitle),
                onClick = {
                    context.openUrl("https://github.com/Heartless-Veteran/Otaku-Reader/wiki/Getting-Started")
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // App Information Section
            AboutSectionTitle(stringResource(R.string.about_app_information_section))

            AboutListItem(
                icon = Icons.Default.History,
                title = stringResource(R.string.about_changelog_title),
                subtitle = stringResource(R.string.about_changelog_subtitle),
                onClick = {
                    context.openUrl("https://heartless-veteran.github.io/Otaku-Reader/changelog")
                }
            )

            AboutListItem(
                icon = Icons.Default.Gavel,
                title = stringResource(R.string.about_open_source_licenses_title),
                subtitle = stringResource(R.string.about_open_source_licenses_subtitle),
                onClick = {
                    context.openUrl(
                        "https://github.com/Heartless-Veteran/Otaku-Reader/blob/main/docs/DEPENDENCY_LICENSES.md"
                    )
                }
            )

            AboutListItem(
                icon = Icons.Default.Policy,
                title = stringResource(R.string.about_privacy_policy_title),
                subtitle = stringResource(R.string.about_privacy_policy_subtitle),
                onClick = onNavigateToPrivacyPolicy
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Connect Section
            AboutSectionTitle(stringResource(R.string.about_connect_section))

            AboutListItem(
                icon = Icons.Default.Code,
                title = stringResource(R.string.about_github_title),
                subtitle = stringResource(R.string.about_github_subtitle),
                onClick = {
                    context.openUrl("https://github.com/Heartless-Veteran/Otaku-Reader")
                }
            )

            AboutListItem(
                icon = Icons.Default.Description,
                title = stringResource(R.string.about_documentation_title),
                subtitle = stringResource(R.string.about_documentation_subtitle),
                onClick = {
                    context.openUrl("https://github.com/Heartless-Veteran/Otaku-Reader/wiki")
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AppInfoHeader(
    versionName: String,
    onVersionClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = 16.dp)
                .size(100.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(CoreUiR.drawable.ic_otaku_logo),
                contentDescription = stringResource(R.string.about_app_icon),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(100.dp)
            )
        }

        Text(
            text = stringResource(R.string.about_app_name),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.about_version_format, versionName),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            // Deliberately no ripple and no content description: this is the reveal gesture for
            // the developer screen, and an accessible affordance would advertise a control that
            // is meant to stay unremarkable. It changes nothing for anyone not counting taps.
            modifier = Modifier
                .padding(top = 4.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onVersionClick,
                )
        )

        Text(
            text = stringResource(R.string.about_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.about_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.about_card_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun AboutSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

private fun Context.openUrl(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: ActivityNotFoundException) {
        // No browser installed — silently drop. The URL is visible in open-source
        // code so users can copy it manually if needed.
    }
}

@Composable
private fun AboutListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = "App logo",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = stringResource(R.string.about_opens_in_browser),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick)
    )
}
