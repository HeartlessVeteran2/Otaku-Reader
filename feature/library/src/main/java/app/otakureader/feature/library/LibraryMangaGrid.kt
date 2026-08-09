package app.otakureader.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items as staggeredItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.otakureader.core.ui.components.CompletedBadge
import app.otakureader.core.ui.components.DownloadBadge
import app.otakureader.core.ui.components.DroppedBadge
import app.otakureader.core.ui.components.MangaCard
import app.otakureader.core.ui.components.ManhwaCard
import app.otakureader.core.ui.theme.LocalOtakuColors
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.TextButton
import coil3.compose.AsyncImage
import java.util.Calendar
import java.util.Locale

/** Layout constants for the source icon badge. */
private object SourceIconBadgeDefaults {
    val IconSize = 20.dp
    val CornerRadius = 4.dp
}

/** Layout constants for the compact list-mode row. */
private object LibraryListRowDefaults {
    val HorizontalPadding = 12.dp
    val VerticalPadding = 6.dp
    val ItemSpacing = 12.dp
    val ThumbnailWidth = 40.dp
    val ThumbnailCornerRadius = 4.dp
    const val ThumbnailAspectRatio = 3f / 4f
    const val TitleMaxLines = 2
}

/**
 * Returns true when a manga item belongs to the manhwa/webtoon content type.
 *
 * Matched against the source's *name*. It used to read `sourceId.toString()`, which is a decimal
 * number — no number contains the word "webtoon", so the Manhwa tab was always empty and the
 * Manga tab always held everything.
 */
internal fun isManhwa(manga: LibraryMangaItem): Boolean {
    val src = manga.sourceName.lowercase()
    return src.contains("manhwa") || src.contains("webtoon") ||
        src.contains("korean") || src.contains("toon") || src.contains("naver")
}

/** Max title lines for the caption shown below covers in comfortable grid mode. */
private const val COMFORTABLE_TITLE_MAX_LINES = 2

/** Padding around the title caption shown below covers in comfortable grid mode. */
private val COMFORTABLE_TITLE_PADDING = 4.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MangaGrid(
    state: LibraryState,
    onEvent: (LibraryEvent) -> Unit,
    adaptiveColumns: Int = state.gridSize,
    onMangaSelect: ((LibraryMangaItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedContentFilter by remember { mutableIntStateOf(0) }
    val contentTabs = listOf(
        stringResource(R.string.library_content_tab_all),
        stringResource(R.string.library_content_tab_manga),
        stringResource(R.string.library_content_tab_manhwa),
    )

    // Long-press enters selection mode directly (Komikku parity) — no context-menu indirection.
    val haptic = LocalHapticFeedback.current
    val onMangaLongClick: (Long) -> Unit = remember(haptic, onEvent) {
        { mangaId ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onEvent(LibraryEvent.OnMangaLongClick(mangaId))
        }
    }
    // Taps open the detail pane only in two-pane mode with no active selection; otherwise route
    // through OnMangaClick so taps toggle selection while selection mode is active (and navigate
    // in single-pane). Without the selection guard, two-pane taps could never toggle items.
    val onMangaTap: (LibraryMangaItem) -> Unit = remember(haptic, onMangaSelect, onEvent, state.selectedManga.isEmpty()) {
        { manga ->
            if (onMangaSelect != null && state.selectedManga.isEmpty()) {
                onMangaSelect(manga)
            } else {
                if (state.selectedManga.isNotEmpty()) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onEvent(LibraryEvent.OnMangaClick(manga.id))
            }
        }
    }

    val displayedManga by remember(state.mangaList, selectedContentFilter) {
        derivedStateOf {
            when (selectedContentFilter) {
                1 -> state.mangaList.filter { !isManhwa(it) }
                2 -> state.mangaList.filter { isManhwa(it) }
                else -> state.mangaList
            }
        }
    }

    // GAP 1: page 0 = "All" (null), pages 1..n = each category id
    val categoryPages: List<Long?> = remember(state.categories) {
        listOf(null) + state.categories.map { it.id }
    }
    val pagerState = rememberPagerState(
        initialPage = categoryPages.indexOf(state.selectedCategory).coerceAtLeast(0),
        pageCount = { categoryPages.size },
    )

    // Swipe → update selected category in ViewModel
    LaunchedEffect(pagerState.currentPage) {
        val id = categoryPages.getOrNull(pagerState.currentPage)
        onEvent(LibraryEvent.OnCategorySelected(id))
    }
    // Chip / tab tap → scroll pager to matching page
    // Guard against fighting an in-progress user swipe gesture
    LaunchedEffect(state.selectedCategory) {
        if (pagerState.isScrollInProgress) return@LaunchedEffect
        val target = categoryPages.indexOf(state.selectedCategory).coerceAtLeast(0)
        if (pagerState.currentPage != target) {
            pagerState.animateScrollToPage(target)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Fixed header: greeting, goal banner, continue reading, category controls
        LibraryGreetingHeader(
            mangaCount = state.mangaList.size,
            unreadCount = state.mangaList.sumOf { it.unreadCount },
            userName = state.displayName,
        )

        if (state.readingGoal.dailyGoal > 0) {
            DailyGoalBanner(readingGoal = state.readingGoal)
        }

        if (state.continueReadingItems.isNotEmpty()) {
            ContinueReadingSection(
                items = state.continueReadingItems,
                onItemClick = { mangaId, _ ->
                    onEvent(LibraryEvent.ContinueReadingClick(mangaId))
                }
            )
        }

        if (state.showRecommendations && state.recommendations.isNotEmpty()) {
            RecommendationsCarousel(
                items = state.recommendations,
                onMangaClick = { mangaId -> onEvent(LibraryEvent.OnMangaClick(mangaId)) },
                onDismiss = { mangaId -> onEvent(LibraryEvent.DismissRecommendation(mangaId)) },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_section_categories),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onEvent(LibraryEvent.ToggleBottomSheet) }) {
                Text(
                    text = stringResource(R.string.library_section_categories_manage),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        // Category selector — Mihon/Komikku-style swipeable tabs.
        if (state.showCategoryTabs) {
            CategoryTabRow(
                categories = state.categories,
                selectedCategory = state.selectedCategory,
                showItemCount = state.showCategoryItemCount,
                onCategorySelected = { onEvent(LibraryEvent.OnCategorySelected(it)) },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (state.readingLists.isNotEmpty()) {
            ReadingListFilterChips(
                readingLists = state.readingLists,
                selectedListId = state.filterReadingListId,
                onListSelected = { onEvent(LibraryEvent.SetFilterReadingList(it)) },
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        // Content-type filter tabs (All / Manga / Manhwa)
        // Kept on the deprecated TabRow: the custom per-index indicator color below uses
        // TabRow's (tabPositions: List<TabPosition>) -> Unit indicator signature, which
        // SecondaryTabRow's TabIndicatorScope-based indicator does not support.
        @Suppress("DEPRECATION")
        TabRow(
            selectedTabIndex = selectedContentFilter,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            indicator = { tabPositions ->
                // getOrNull: the indicator lambda runs during layout, after which
                // selectedContentFilter may have changed — never index unchecked.
                tabPositions.getOrNull(selectedContentFilter)?.let { position ->
                    val otakuColors = LocalOtakuColors.current
                    val indicatorColor = when (selectedContentFilter) {
                        1 -> otakuColors.contentFilterManga
                        2 -> otakuColors.contentFilterManhwa
                        else -> MaterialTheme.colorScheme.primary
                    }
                    Box(
                        modifier = Modifier
                            .offset(x = position.left)
                            .width(position.width)
                            .height(3.dp)
                            .background(indicatorColor, RoundedCornerShape(2.dp))
                    )
                }
            }
        ) {
            contentTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedContentFilter == index,
                    onClick = { selectedContentFilter = index },
                    text = { Text(title) }
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.library_section_all_titles),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        // Swipeable pager — each page shows the active category's manga list.
        // Content is extracted to a separate composable to prevent ColumnScope from leaking
        // into AnimatedVisibility resolution (Kotlin 2.x context receiver change).
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { _ ->
            LibraryMangaPageContent(
                state = state,
                adaptiveColumns = adaptiveColumns,
                displayedManga = displayedManga,
                onMangaTap = onMangaTap,
                onMangaLongClick = onMangaLongClick,
                onContinueReadingClick = { mangaId ->
                    onEvent(LibraryEvent.ContinueReadingClick(mangaId))
                },
            )
        }
    }
}

@Composable
private fun LibraryMangaPageContent(
    state: LibraryState,
    adaptiveColumns: Int,
    displayedManga: List<LibraryMangaItem>,
    onMangaTap: (LibraryMangaItem) -> Unit,
    onMangaLongClick: (Long) -> Unit,
    onContinueReadingClick: (mangaId: Long) -> Unit,
) {
    if (state.displayMode == LibraryDisplayMode.LIST) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            columnItems(
                items = displayedManga,
                key = { it.id },
                contentType = { "manga_row" },
            ) { manga ->
                val downloadCount = state.downloadCountByManga[manga.id] ?: 0
                LibraryListRow(
                    manga = manga,
                    isSelected = manga.id in state.selectedManga,
                    unreadCount = if (state.showBadges) manga.unreadCount else 0,
                    downloadCount = if (state.showDownloadBadge) downloadCount else 0,
                    showBadges = state.showBadges,
                    onClick = { onMangaTap(manga) },
                    onLongClick = { onMangaLongClick(manga.id) },
                )
            }
        }
    } else if (state.isStaggeredGrid && state.displayMode != LibraryDisplayMode.COMFORTABLE_GRID &&
        state.displayMode != LibraryDisplayMode.COVER_ONLY) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(130.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalItemSpacing = 8.dp,
            modifier = Modifier.fillMaxSize(),
        ) {
            staggeredItems(
                items = displayedManga,
                key = { it.id },
                contentType = { "manga_card" }
            ) { manga ->
                val cardContent: @Composable () -> Unit = {
                    if (isManhwa(manga)) {
                        ManhwaCard(
                            coverUrl = manga.thumbnailUrl ?: "",
                            title = manga.title,
                            unreadCount = if (state.showBadges) manga.unreadCount else 0,
                            onClick = { onMangaTap(manga) },
                            onLongClick = { onMangaLongClick(manga.id) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        val readProgress = if (manga.totalChapterCount > 0) {
                            (manga.totalChapterCount - manga.unreadCount)
                                .coerceAtLeast(0)
                                .toFloat() / manga.totalChapterCount
                        } else null
                        val downloadCount = state.downloadCountByManga[manga.id] ?: 0
                        val onClickContinueReading: (() -> Unit)? = if (
                            state.showContinueReadingButton &&
                            manga.lastRead != null &&
                            manga.unreadCount > 0
                        ) {
                            { onContinueReadingClick(manga.id) }
                        } else null
                        MangaCard(
                            title = manga.title,
                            coverUrl = manga.thumbnailUrl,
                            onClick = { onMangaTap(manga) },
                            onLongClick = { onMangaLongClick(manga.id) },
                            isSelected = manga.id in state.selectedManga,
                            readProgress = readProgress,
                            onClickContinueReading = onClickContinueReading,
                            isNew = manga.unreadCount > 0,
                            showTitle = state.showTitle,
                            badge = when {
                                state.showBadges && manga.unreadCount > 0 &&
                                    state.showDownloadBadge && downloadCount > 0 -> {
                                    {
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            UnreadBadge(count = manga.unreadCount)
                                            DownloadBadge(count = downloadCount)
                                        }
                                    }
                                }
                                state.showBadges && manga.unreadCount > 0 -> {
                                    { UnreadBadge(count = manga.unreadCount) }
                                }
                                state.showDownloadBadge && downloadCount > 0 -> {
                                    { DownloadBadge(count = downloadCount) }
                                }
                                else -> null
                            },
                            statusBadge = when {
                                manga.userCompleted -> { { CompletedBadge() } }
                                manga.userDropped -> { { DroppedBadge() } }
                                else -> null
                            },
                            languageBadge = if (state.showBadges && manga.sourceLanguage.isNotBlank()) {
                                { LanguageBadge(manga.sourceLanguage) }
                            } else null,
                            sourceIcon = if (state.showBadges && manga.sourceIconUrl != null) {
                                { SourceIconBadge(manga.sourceIconUrl) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Box {
                    if (state.visualEffectsEnabled) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(300)) + scaleIn(
                                initialScale = 0.92f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        ) {
                            cardContent()
                        }
                    } else {
                        cardContent()
                    }
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(adaptiveColumns),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            gridItems(
                items = displayedManga,
                key = { it.id },
                contentType = { "manga_card" }
            ) { manga ->
                val readProgress = if (manga.totalChapterCount > 0) {
                    (manga.totalChapterCount - manga.unreadCount)
                        .coerceAtLeast(0)
                        .toFloat() / manga.totalChapterCount
                } else null
                val downloadCount = state.downloadCountByManga[manga.id] ?: 0
                val onClickContinueReading: (() -> Unit)? = if (
                    state.showContinueReadingButton &&
                    manga.lastRead != null &&
                    manga.unreadCount > 0
                ) {
                    { onContinueReadingClick(manga.id) }
                } else null
                // Comfortable grid: cover with title caption below; Cover-only: no title at all.
                val comfortable = state.displayMode == LibraryDisplayMode.COMFORTABLE_GRID
                val coverOnly = state.displayMode == LibraryDisplayMode.COVER_ONLY
                val card: @Composable () -> Unit = {
                    MangaCard(
                        title = manga.title,
                        coverUrl = manga.thumbnailUrl,
                        onClick = { onMangaTap(manga) },
                        onLongClick = { onMangaLongClick(manga.id) },
                        isSelected = manga.id in state.selectedManga,
                        readProgress = readProgress,
                        onClickContinueReading = onClickContinueReading,
                        isNew = manga.unreadCount > 0,
                        showTitle = if (comfortable || coverOnly) false else state.showTitle,
                        badge = when {
                            state.showBadges && manga.unreadCount > 0 &&
                                state.showDownloadBadge && downloadCount > 0 -> {
                                {
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        UnreadBadge(count = manga.unreadCount)
                                        DownloadBadge(count = downloadCount)
                                    }
                                }
                            }
                            state.showBadges && manga.unreadCount > 0 -> {
                                { UnreadBadge(count = manga.unreadCount) }
                            }
                            state.showDownloadBadge && downloadCount > 0 -> {
                                { DownloadBadge(count = downloadCount) }
                            }
                            else -> null
                        },
                        statusBadge = when {
                            manga.userCompleted -> { { CompletedBadge() } }
                            manga.userDropped -> { { DroppedBadge() } }
                            else -> null
                        },
                        languageBadge = if (state.showBadges && manga.sourceLanguage.isNotBlank()) {
                            { LanguageBadge(manga.sourceLanguage) }
                        } else null,
                        sourceIcon = if (state.showBadges && manga.sourceIconUrl != null) {
                            { SourceIconBadge(manga.sourceIconUrl) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val cardContent: @Composable () -> Unit = {
                    if (comfortable) {
                        // Whole item (cover + caption + padding) is tappable. Null indication
                        // avoids a second ripple layering over the card's own ripple.
                        Column(
                            modifier = Modifier.combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onMangaTap(manga) },
                                onLongClick = { onMangaLongClick(manga.id) },
                            ),
                        ) {
                            card()
                            Text(
                                text = manga.title,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = COMFORTABLE_TITLE_MAX_LINES,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        top = COMFORTABLE_TITLE_PADDING,
                                        start = COMFORTABLE_TITLE_PADDING,
                                        end = COMFORTABLE_TITLE_PADDING,
                                    ),
                            )
                        }
                    } else {
                        // Cover-only and default grid modes — just the card, no extra caption.
                        card()
                    }
                }
                Box {
                    if (state.visualEffectsEnabled) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(300)) + scaleIn(
                                initialScale = 0.92f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        ) {
                            cardContent()
                        }
                    } else {
                        cardContent()
                    }
                }
            }
        }
    }
}

/**
 * Compact single-row representation of a library entry, used when
 * [LibraryState.displayMode] is [LibraryDisplayMode.LIST].
 *
 * Mirrors the grid card's interaction contract: tap opens (or toggles selection when a
 * selection is active), long-press enters selection mode. The whole row tints with the
 * selection container colour while selected.
 */
@Composable
private fun LibraryListRow(
    manga: LibraryMangaItem,
    isSelected: Boolean,
    unreadCount: Int,
    downloadCount: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBadges: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(
                horizontal = LibraryListRowDefaults.HorizontalPadding,
                vertical = LibraryListRowDefaults.VerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LibraryListRowDefaults.ItemSpacing),
    ) {
        AsyncImage(
            // Decorative: the title is shown as text right beside it, so a description
            // would make screen readers announce the title twice.
            model = manga.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(LibraryListRowDefaults.ThumbnailWidth)
                .aspectRatio(LibraryListRowDefaults.ThumbnailAspectRatio)
                .clip(RoundedCornerShape(LibraryListRowDefaults.ThumbnailCornerRadius)),
        )
        Text(
            text = manga.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = LibraryListRowDefaults.TitleMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (downloadCount > 0) {
            DownloadBadge(count = downloadCount)
        }
        if (unreadCount > 0) {
            UnreadBadge(count = unreadCount)
        }
        if (showBadges && manga.sourceLanguage.isNotBlank()) {
            LanguageBadge(manga.sourceLanguage)
        }
        if (showBadges && manga.sourceIconUrl != null) {
            SourceIconBadge(manga.sourceIconUrl, contentDescription = manga.sourceName.ifBlank { null })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryTabRow(
    categories: List<CategoryItem>,
    selectedCategory: Long?,
    onCategorySelected: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    showItemCount: Boolean = true,
) {
    val totalCount = categories.sumOf { it.count }
    val categoryTabs = listOf(
        CategoryItem(id = -1, name = stringResource(R.string.library_category_all), count = totalCount)
    ) + categories

    val selectedIndex = when (selectedCategory) {
        null -> 0
        else -> categoryTabs.indexOfFirst { it.id == selectedCategory }.coerceAtLeast(0)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex,
            edgePadding = 0.dp,
            divider = {},
        ) {
            categoryTabs.forEachIndexed { index, category ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = {
                        if (category.id == -1L) onCategorySelected(null)
                        else onCategorySelected(category.id)
                    },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            if (showItemCount && category.count > 0) {
                                val pillAlpha = if (selectedIndex == index) 0.08f else 0.12f
                                Surface(
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = pillAlpha),
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = category.count.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
        HorizontalDivider()
    }
}

@Composable
internal fun LibraryGreetingHeader(
    mangaCount: Int,
    unreadCount: Int,
    userName: String,
    modifier: Modifier = Modifier,
) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> R.string.library_greeting_morning
            hour < 18 -> R.string.library_greeting_afternoon
            else -> R.string.library_greeting_evening
        }
    }
    val greetingText = if (userName.isBlank()) {
        "${stringResource(greeting)} 👋"
    } else {
        "${stringResource(greeting)}, $userName 👋"
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = greetingText,
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.library_greeting_stats, mangaCount, unreadCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun LanguageBadge(
    language: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.tertiary,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            text = language.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiary,
        )
    }
}

@Composable
internal fun SourceIconBadge(
    iconUrl: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    AsyncImage(
        model = iconUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(SourceIconBadgeDefaults.IconSize)
            .clip(RoundedCornerShape(SourceIconBadgeDefaults.CornerRadius)),
    )
}

@Composable
internal fun UnreadBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .padding(4.dp)
            .size(24.dp)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) stringResource(R.string.library_badge_overflow) else count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
