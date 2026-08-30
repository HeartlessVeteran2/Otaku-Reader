package app.otakureader

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import app.otakureader.core.navigation.Route
import app.otakureader.feature.about.navigation.aboutScreen
import app.otakureader.feature.about.navigation.privacyPolicyScreen
import app.otakureader.feature.browse.developer.navigation.developerScreen
import app.otakureader.feature.browse.navigation.browseScreen
import app.otakureader.feature.browse.navigation.browseExtensionDetailScreen
import app.otakureader.feature.browse.navigation.extensionInstallScreen
import app.otakureader.feature.browse.navigation.extensionsBottomSheet
import app.otakureader.feature.browse.repos.navigation.extensionRepositoriesScreen
import app.otakureader.feature.browse.navigation.globalSearchScreen
import app.otakureader.feature.browse.navigation.sourceMangaDetailScreen
import app.otakureader.feature.browse.navigation.sourceDetailScreen
import app.otakureader.feature.details.navigation.detailsScreen
import app.otakureader.feature.feed.navigation.feedManagementScreen
import app.otakureader.feature.feed.navigation.feedScreen
import app.otakureader.feature.history.navigation.historyScreen
import app.otakureader.feature.library.category.navigation.categoryManagementScreen
import app.otakureader.feature.library.navigation.libraryScreen
import app.otakureader.feature.library.navigation.libraryMaintenanceScreen
import app.otakureader.feature.library.navigation.mergeDuplicatesScreen
import app.otakureader.feature.library.readinglist.navigation.readingListDetailScreen
import app.otakureader.feature.library.readinglist.navigation.readingListsScreen
import app.otakureader.feature.migration.navigation.migrationEntryScreen
import app.otakureader.feature.migration.navigation.migrationScreen
import app.otakureader.feature.more.bookmarks.bookmarksScreen
import app.otakureader.feature.more.navigation.moreScreen
import app.otakureader.feature.more.navigation.scanLibraryScreen
import app.otakureader.feature.more.navigation.shareLibraryScreen
import app.otakureader.feature.onboarding.navigation.onboardingScreen
import app.otakureader.feature.opds.navigation.opdsScreen
import app.otakureader.feature.reader.navigation.readerScreen
import app.otakureader.feature.settings.navigation.settingsScreen
import app.otakureader.feature.statistics.navigation.statisticsScreen
import app.otakureader.feature.tracking.navigation.trackerOAuthScreen
import app.otakureader.feature.tracking.navigation.trackingScreen
import app.otakureader.core.webview.webViewScreen
import app.otakureader.feature.webview.navigation.webViewFallbackScreen
import app.otakureader.feature.updates.navigation.downloadsScreen
import app.otakureader.feature.updates.navigation.updateErrorsScreen
import app.otakureader.feature.updates.navigation.updatesScreen
import app.otakureader.util.DeepLinkResult
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import app.otakureader.core.webview.WebViewPurpose
import app.otakureader.core.webview.WebViewChallengeManager
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.toRoute

@Composable
fun OtakuReaderNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    onboardingCompleted: Boolean = false,
    deepLinkResult: DeepLinkResult? = null,
    onDeepLinkConsumed: () -> Unit = {},
    onOnboardingComplete: () -> Unit = {},
    challengeManager: WebViewChallengeManager = hiltViewModel<ChallengeHostViewModel>().manager,
) {
    // Determine start destination based on onboarding status
    val startDestination: Route = if (onboardingCompleted) Route.Library else Route.Onboarding

    // Open the WebView when a source hits a Cloudflare wall.
    //
    // This observer is the step that was missing entirely: the manager published pending
    // challenges and the KDoc said "the app's navigation layer observes" them, but nothing did —
    // so a blocked source simply failed with no way for the user to intervene.
    //
    // Show a challenge that has no screen of its own yet — matched by challenge **id**, not by
    // "is some WebView open". Whether a screen exists is read from the back stack rather than
    // tracked in a flag of our own, because the NavController already knows the answer and a
    // flag of ours can disagree with it.
    //
    // That distinction is the whole correctness of this block, and three narrower versions were
    // each wrong in their own way: a remembered flag went stale when a deep link cleared the
    // back stack and suppressed every later challenge; gating on the current destination missed
    // a CAPTCHA the user had navigated away from and pushed a duplicate on top of it; gating on
    // the bare route would let an unrelated general-purpose WebView mask pending challenges, and
    // left a stranded CAPTCHA blocking the bypass permanently, since only its own close button
    // can pop it.
    //
    // The predicate has to satisfy FOUR properties at once, and every previous version got some
    // subset. Listed so the next change can be checked against all of them rather than the one
    // that happens to be under discussion:
    //
    //  1. One at a time. Two hosts can be blocked together; stacking their screens means the
    //     user dismisses a pile of CAPTCHAs to get back to reading.
    //  2. No duplicate for a live challenge, even after navigating away from its screen.
    //  3. A stranded screen must not wedge the bypass. Only its own close button pops it, so a
    //     CAPTCHA left behind by a deep link would otherwise block every later challenge for the
    //     lifetime of the nav host.
    //  4. An unrelated WebView — OAuth, the fallback viewer — must not mask a challenge.
    //
    // Matching on challenge id gives 2, 3 and 4: a stranded screen whose challenge completed
    // matches nothing pending, and a WebView opened for another purpose carries no id. Property
    // 1 needs the extra check below, because once a screen exists for A its id counts as shown
    // and B would immediately become eligible — which is how an id-only filter reintroduced the
    // stacking it was not trying to fix.
    val backStack by navController.currentBackStack.collectAsStateWithLifecycle()
    val shownChallengeIds = backStack.mapNotNull { entry ->
        runCatching { entry.toRoute<Route.WebView>() }.getOrNull()?.challengeId
    }.toSet()

    val pendingChallenges by challengeManager.pendingChallenges.collectAsStateWithLifecycle()

    // A screen already exists for a challenge that is STILL pending — so the user is looking at
    // (or has left open) live work, and nothing new should be pushed on top of it.
    val liveChallengeOnStack = pendingChallenges.any { it.id in shownChallengeIds }

    val nextChallenge = when {
        liveChallengeOnStack -> null
        else -> pendingChallenges.firstOrNull { it.id !in shownChallengeIds }
    }

    LaunchedEffect(nextChallenge?.id) {
        if (nextChallenge != null) {
            navController.navigate(
                Route.WebView(
                    url = nextChallenge.url,
                    purpose = WebViewPurpose.CAPTCHA.name,
                    challengeId = nextChallenge.id,
                )
            )
        }
    }

    // Handle deep link navigation - only trigger once when deepLinkResult changes
    LaunchedEffect(deepLinkResult) {
        when (deepLinkResult) {
            is DeepLinkResult.MangaUrl -> {
                navController.navigate(Route.Search(query = deepLinkResult.mangaUrl))
                onDeepLinkConsumed()
            }
            is DeepLinkResult.SearchQuery -> {
                navController.navigate(Route.Search(query = deepLinkResult.query))
                onDeepLinkConsumed()
            }
            is DeepLinkResult.NavigateToLibrary -> {
                navController.navigate(Route.Library) {
                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            is DeepLinkResult.NavigateToUpdates -> {
                navController.navigate(Route.Updates) {
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            is DeepLinkResult.ContinueReading -> {
                navController.navigate(
                    Route.Reader(deepLinkResult.mangaId, deepLinkResult.chapterId)
                ) {
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            is DeepLinkResult.NavigateToManga -> {
                navController.navigate(Route.MangaDetails(deepLinkResult.mangaId)) {
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            is DeepLinkResult.TrackerOAuth -> {
                navController.navigate(
                    Route.TrackerOAuth(deepLinkResult.tracker, deepLinkResult.code, deepLinkResult.state)
                ) {
                    launchSingleTop = true
                }
                onDeepLinkConsumed()
            }
            is DeepLinkResult.Invalid, null -> {
                // No deep link to handle
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 3 },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 3 },
                animationSpec = tween(300)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300)
            ) + fadeOut(animationSpec = tween(300))
        }
    ) {
        // Library screen - main entry point
        libraryScreen(
            onMangaClick = { mangaId ->
                navController.navigate(Route.MangaDetails(mangaId))
            },
            onNavigateToSettings = {
                navController.navigate(Route.Settings)
            },
            onNavigateToDownloads = {
                navController.navigate(Route.Downloads)
            },
            onNavigateToMigration = {
                navController.navigate(Route.MigrationEntry)
            },
            onNavigateToCategoryManagement = {
                navController.navigate(Route.CategoryManagement)
            },
            onNavigateToReader = { mangaId, chapterId ->
                navController.navigate(Route.Reader(mangaId, chapterId))
            },
            onNavigateToMergeDuplicates = {
                navController.navigate(Route.MergeDuplicates)
            },
            onNavigateToShareLibrary = {
                navController.navigate(Route.ShareLibrary)
            },
            onNavigateToScanLibrary = {
                navController.navigate(Route.ScanLibrary)
            },
            onNavigateToMaintenance = {
                navController.navigate(Route.LibraryMaintenance)
            },
            onBrowseClick = {
                navController.navigate(Route.Browse) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
        )

        mergeDuplicatesScreen(
            onNavigateBack = { navController.popBackStack() }
        )

        // Library maintenance center (refresh covers/metadata, reindex downloads, orphan cleanup)
        libraryMaintenanceScreen(
            onNavigateBack = { navController.popBackStack() }
        )

        // Updates screen - new chapters
        updatesScreen(
            onMangaClick = { mangaId ->
                navController.navigate(Route.MangaDetails(mangaId))
            },
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToDownloads = {
                navController.navigate(Route.Downloads)
            },
            onNavigateToUpdateErrors = {
                navController.navigate(Route.UpdateErrors)
            },
        )

        // Dedicated Update Errors screen (replaces the old in-dialog error list)
        updateErrorsScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToManga = { mangaId -> navController.navigate(Route.MangaDetails(mangaId)) },
            onNavigateToMigration = { mangaIds -> navController.navigate(Route.Migration(mangaIds)) },
        )

        // Browse - sources catalog
        browseScreen(
            onMangaClick = { sourceId, mangaUrl, mangaTitle ->
                navController.navigate(Route.SourceMangaDetail(sourceId, mangaUrl, mangaTitle))
            },
            onNavigateToSource = { sourceId ->
                navController.navigate(Route.SourceListing(sourceId))
            },
            onNavigateToExtensions = {
                navController.navigate(Route.ExtensionCatalog)
            },
            onNavigateToGlobalSearch = {
                navController.navigate(Route.Search(query = ""))
            },
            onNavigateToOpds = {
                navController.navigate(Route.OpdsCatalog())
            },
            onNavigateToMigration = {
                navController.navigate(Route.MigrationEntry)
            },
            onNavigateToReader = { mangaId, chapterId ->
                navController.navigate(Route.Reader(mangaId, chapterId))
            },
            onNavigateToFeedManagement = {
                navController.navigate(Route.FeedManagement)
            },
            onNavigateToExtensionSettings = {
                navController.navigate(Route.Settings)
            },
            onNavigateToExtensionRepositories = {
                navController.navigate(Route.ExtensionRepositories)
            },
            onNavigateToExtensionDetail = { packageName ->
                navController.navigate(Route.ExtensionDetail(packageName))
            },
            onStartMigration = { selectedMangaIds ->
                navController.navigate(Route.Migration(selectedMangaIds))
            },
        )

        // OPDS catalog
        opdsScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToMangaDetail = { mangaUrl, mangaTitle ->
                navController.navigate(Route.Search(query = mangaUrl))
            }
        )

        // Global search across all sources
        globalSearchScreen(
            onMangaClick = { sourceId, mangaUrl ->
                navController.navigate(Route.SourceMangaDetail(sourceId, mangaUrl))
            },
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToSource = { sourceId, query ->
                navController.navigate(Route.SourceListing(sourceId, query))
            },
        )

        // Source detail — manga listing from a specific source
        sourceDetailScreen(
            onMangaClick = { sourceId, mangaUrl, mangaTitle ->
                navController.navigate(Route.SourceMangaDetail(sourceId, mangaUrl, mangaTitle))
            },
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Source manga detail — resolves URL to DB id then forwards to MangaDetails
        sourceMangaDetailScreen(
            onNavigateToMangaDetail = { mangaId ->
                navController.navigate(Route.MangaDetails(mangaId)) {
                    popUpTo<Route.SourceMangaDetail> { inclusive = true }
                }
            },
            onNavigateBack = {
                navController.popBackStack()
            },
        )

        // Extensions bottom sheet
        extensionsBottomSheet(
            onDismiss = {
                navController.popBackStack()
            },
            onNavigateToSettings = {
                navController.navigate(Route.Settings)
            },
            onNavigateToRepositories = {
                navController.navigate(Route.ExtensionRepositories)
            },
            onNavigateToExtensionDetail = { packageName ->
                navController.navigate(Route.ExtensionDetail(packageName))
            },
            onNavigateToExtensionInstall = {
                navController.navigate(Route.ExtensionInstall)
            },
        )

        // Extension detail screen (#1047)
        browseExtensionDetailScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Extension repositories management screen (#953)
        extensionRepositoriesScreen(
            onNavigateBack = { navController.popBackStack() },
        )

        // Extension install screen
        extensionInstallScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // History screen
        historyScreen(
            onChapterClick = { mangaId, chapterId ->
                navController.navigate(Route.Reader(mangaId, chapterId))
            },
            onNavigateBack = {
                navController.popBackStack()
            },
            onMangaClick = { mangaId ->
                navController.navigate(Route.MangaDetails(mangaId))
            },
        )

        // Manga details
        detailsScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToReader = { mangaId, chapterId ->
                navController.navigate(Route.Reader(mangaId, chapterId))
            },
            onNavigateToTracking = { mangaId, mangaTitle ->
                navController.navigate(Route.Tracking(mangaId, mangaTitle))
            },
            onNavigateToGlobalSearch = { query ->
                navController.navigate(Route.Search(query = query))
            },
            onNavigateToSourceSearch = { sourceId, query ->
                navController.navigate(Route.SourceListing(sourceId, query))
            },
            onNavigateToMigration = { mangaId ->
                navController.navigate(Route.Migration(selectedMangaIds = listOf(mangaId)))
            },
            onNavigateToWebViewFallback = { url, title ->
                navController.navigate(Route.WebViewFallback(url, title))
            }
        )

        // Reader
        readerScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Settings
        settingsScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToAbout = {
                navController.navigate(Route.About)
            },
            onNavigateToMigrationEntry = {
                navController.navigate(Route.MigrationEntry)
            },
            onNavigateToAppearance = {
                navController.navigate(Route.SettingsAppearance)
            },
            onNavigateToLibrary = {
                navController.navigate(Route.SettingsLibrary)
            },
            onNavigateToReader = {
                navController.navigate(Route.SettingsReader)
            },
            onNavigateToDownloads = {
                navController.navigate(Route.SettingsDownloads)
            },
            onNavigateToTracking = {
                navController.navigate(Route.SettingsTracking)
            },
            onNavigateToBrowse = {
                navController.navigate(Route.SettingsBrowse)
            },
            onNavigateToBackup = {
                navController.navigate(Route.SettingsBackup)
            },
            onNavigateToDiscord = {
                navController.navigate(Route.SettingsDiscord)
            },
            onNavigateToSecurity = {
                navController.navigate(Route.SettingsSecurity)
            },
            onNavigateToNotifications = {
                navController.navigate(Route.SettingsNotifications)
            },
            onNavigateToWidgetConfiguration = {
                navController.navigate(Route.WidgetConfiguration)
            },
            onNavigateToLocalSourceBrowser = {
                navController.navigate(Route.LocalSourceBrowser)
            },
            onNavigateToCloudBackup = {
                navController.navigate(Route.SettingsCloudBackup)
            },
            onNavigateToDataUsage = {
                navController.navigate(Route.DataUsage)
            },
            onNavigateToSync = {
                navController.navigate(Route.SettingsSync)
            },
            onNavigateToNavOrder = {
                navController.navigate(Route.SettingsNavOrder)
            },
            onNavigateToReaderPresets = {
                navController.navigate(Route.ReaderPresets)
            },
            onNavigateToStorageAnalytics = {
                navController.navigate(Route.StorageAnalytics)
            },
            onNavigateToExtensionRepos = {
                navController.navigate(Route.ExtensionRepositories)
            },
            onNavigateToAdvanced = {
                navController.navigate(Route.SettingsAdvanced)
            },
        )

        downloadsScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Statistics
        statisticsScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Migration
        migrationScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Migration entry
        migrationEntryScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToMigration = { selectedMangaIds ->
                navController.navigate(Route.Migration(selectedMangaIds))
            }
        )

        // Tracking
        trackingScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Tracker OAuth callback
        trackerOAuthScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Feed
        feedScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToReader = { mangaId, chapterId ->
                navController.navigate(Route.Reader(mangaId, chapterId))
            },
            onNavigateToFeedManagement = {
                navController.navigate(Route.FeedManagement)
            }
        )

        feedManagementScreen(
            onNavigateBack = { navController.popBackStack() }
        )

        // About
        aboutScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToPrivacyPolicy = {
                navController.navigate(Route.PrivacyPolicy)
            },
            onNavigateToDeveloper = {
                navController.navigate(Route.Developer)
            }
        )

        developerScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        privacyPolicyScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // More screen - access to settings, downloads, statistics, about, extensions, feed
        moreScreen(
            onNavigateToSettings = {
                navController.navigate(Route.Settings)
            },
            onNavigateToDownloads = {
                navController.navigate(Route.Downloads)
            },
            onNavigateToStatistics = {
                navController.navigate(Route.Statistics)
            },
            onNavigateToAbout = {
                navController.navigate(Route.About)
            },
            onNavigateToExtensions = {
                navController.navigate(Route.ExtensionCatalog)
            },
            onNavigateToFeed = {
                navController.navigate(Route.Feed)
            },
            onNavigateToShareLibrary = {
                navController.navigate(Route.ShareLibrary)
            },
            onNavigateToScanLibrary = {
                navController.navigate(Route.ScanLibrary)
            },
            onNavigateToUpdateErrors = {
                navController.navigate(Route.UpdateErrors)
            },
            onNavigateToBackup = {
                navController.navigate(Route.SettingsBackup)
            },
            onNavigateToReadingLists = {
                navController.navigate(Route.ReadingLists)
            },
            onNavigateToBookmarks = {
                navController.navigate(Route.Bookmarks)
            },
            onNavigateToCategories = {
                navController.navigate(Route.CategoryManagement)
            },
        )

        readingListsScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToList = { listId -> navController.navigate(Route.ReadingListDetail(listId)) },
        )
        readingListDetailScreen(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToManga = { mangaId -> navController.navigate(Route.MangaDetails(mangaId)) },
        )

        bookmarksScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onOpenBookmark = { mangaId, chapterId, pageIndex ->
                navController.navigate(Route.Reader(mangaId, chapterId, pageIndex))
            },
        )

        // QR Library Sharing
        shareLibraryScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onNavigateToScanLibrary = {
                navController.navigate(Route.ScanLibrary)
            },
        )

        scanLibraryScreen(
            onNavigateBack = {
                navController.popBackStack()
            },
            onLibraryScanned = { _ ->
                navController.popBackStack()
            },
        )

        // Category management
        categoryManagementScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )

        // Onboarding — shows first for new users, navigates to Library on completion
        onboardingScreen(
            onComplete = {
                onOnboardingComplete()
                navController.navigate(Route.Library) {
                    popUpTo<Route.Onboarding> { inclusive = true }
                }
            },
            onNavigateToExtensions = {
                navController.navigate(Route.ExtensionCatalog)
            }
        )

        // WebView — embedded browser for CAPTCHA solving, OAuth, etc.
        webViewScreen(
            onClose = { url, cookieString, userAgent ->
                // Completing the challenge is what resumes the blocked request. The previous
                // version discarded all three values and only popped the back stack, so the
                // caller waiting on the challenge was never released — the WebView "worked"
                // and nothing downstream ever knew.
                url.toHttpUrlOrNull()?.host?.let { host ->
                    challengeManager.completeChallenge(host, cookieString, userAgent)
                }
                navController.popBackStack()
            }
        )

        // WebView fallback viewer for source pages that fail API extraction
        webViewFallbackScreen(
            onNavigateBack = {
                navController.popBackStack()
            }
        )
    }
}
