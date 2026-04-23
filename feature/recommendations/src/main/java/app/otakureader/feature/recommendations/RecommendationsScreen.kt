package app.otakureader.feature.recommendations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.MangaRecommendation
import app.otakureader.domain.model.RecommendationType
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSearch: (String) -> Unit,
    viewModel: RecommendationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RecommendationsEffect.NavigateToSearch -> onNavigateToSearch(effect.query)
                is RecommendationsEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recommendations_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.onEvent(RecommendationsEvent.ForceRefresh) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.recommendations_force_refresh))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.recommendations.isNotEmpty(),
            onRefresh = { viewModel.onEvent(RecommendationsEvent.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !state.isAiEnabled -> AiUnavailableContent()
                state.isLoading && state.recommendations.isEmpty() -> LoadingContent()
                state.error != null && state.recommendations.isEmpty() -> ErrorContent(
                    message = state.error!!,
                    onRetry = { viewModel.onEvent(RecommendationsEvent.Refresh) }
                )
                state.recommendations.isEmpty() -> EmptyContent()
                else -> RecommendationsList(
                    recommendations = state.recommendations,
                    isCacheExpired = state.isCacheExpired,
                    onRefresh = { viewModel.onEvent(RecommendationsEvent.Refresh) },
                    onDismiss = { id -> viewModel.onEvent(RecommendationsEvent.DismissRecommendation(id)) },
                    onSearch = { rec -> viewModel.onEvent(RecommendationsEvent.OnRecommendationClick(rec)) },
                )
            }
        }
    }
}

@Composable
private fun RecommendationsList(
    recommendations: List<MangaRecommendation>,
    isCacheExpired: Boolean,
    onRefresh: () -> Unit,
    onDismiss: (String) -> Unit,
    onSearch: (MangaRecommendation) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isCacheExpired) {
            item {
                StaleCacheBanner(onRefresh = onRefresh)
            }
        }
        items(recommendations, key = { it.title + it.sourceId }) { rec ->
            RecommendationCard(
                recommendation = rec,
                onDismiss = { onDismiss(rec.title) },
                onSearch = { onSearch(rec) },
            )
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: MangaRecommendation,
    onDismiss: () -> Unit,
    onSearch: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Cover thumbnail
                AsyncImage(
                    model = recommendation.thumbnailUrl,
                    contentDescription = recommendation.title,
                    modifier = Modifier
                        .width(64.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = recommendation.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    recommendation.author?.let { author ->
                        Text(
                            text = author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    RecommendationTypeBadge(type = recommendation.recommendationType)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.recommendations_dismiss))
                }
            }

            recommendation.description?.let { desc ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = recommendation.reasonExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.recommendations_confidence),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                LinearProgressIndicator(
                    progress = { recommendation.confidenceScore },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(
                    onClick = onSearch,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.recommendations_search_hint), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun RecommendationTypeBadge(type: RecommendationType) {
    val label = stringResource(
        when (type) {
            RecommendationType.SIMILAR -> R.string.recommendations_type_similar
            RecommendationType.DISCOVERY -> R.string.recommendations_type_discovery
            RecommendationType.TRENDING -> R.string.recommendations_type_trending
            RecommendationType.THEME_BASED -> R.string.recommendations_type_theme_based
            RecommendationType.AUTHOR_BASED -> R.string.recommendations_type_author_based
            RecommendationType.HIDDEN_GEM -> R.string.recommendations_type_hidden_gem
        }
    )
    SuggestionChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        modifier = Modifier.height(24.dp),
    )
}

@Composable
private fun StaleCacheBanner(onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.recommendations_stale_cache),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onRefresh) {
                Text(stringResource(R.string.recommendations_refresh))
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.recommendations_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onRetry) {
            Text(stringResource(R.string.recommendations_refresh))
        }
    }
}

@Composable
private fun AiUnavailableContent() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.recommendations_ai_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
