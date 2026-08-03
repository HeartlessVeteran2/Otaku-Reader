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
import androidx.navigation.NavDestination.Companion.hasRoute

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
    // Exactly one challenge is shown at a time. Two hosts can be blocked at once (a chapter's
    // pages and its cover, say), and navigating for each would push a stack of WebViews the user
    // has to dismiss one by one — and a challenge arriving late would yank them off whatever
    // screen they had moved on to. `showingChallengeHost` gates that: the next pending host is
    // only shown once the current one closes.
    // Whether a challenge screen is up is read from the back stack, not tracked in a flag of
    // our own. A remembered flag goes stale the moment the destination is removed by anything
    // other than its close button — a deep link that clears the back stack, for instance — and
    // a stale "still showing" would suppress every later challenge for the life of this nav
    // host, silently disabling the whole bypass. The NavController already knows the answer.
    // The WHOLE back stack, not just the current entry. If the user navigates away from an open
    // CAPTCHA without closing it — a notification tap, a deep link — the current destination is
    // no longer the WebView, and gating on that alone would push a second CAPTCHA while the
    // first sat stranded beneath it.
    val backStack by navController.currentBackStack.collectAsStateWithLifecycle()
    val challengeShowing = backStack.any { it.destination.hasRoute(Route.WebView::class) }

    val pendingChallenges by challengeManager.pendingChallenges.collectAsStateWithLifecycle()
    val nextChallenge = pendingChallenges.firstOrNull()

    LaunchedEffect(nextChallenge?.id, challengeShowing) {
        if (nextChallenge != null && !challengeShowing) {
            navController.navigate(
                Route.WebView(
                    url = nextChallenge.url,
                    purpose = WebViewPurpose.CAPTCHA.name,
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
            onOpenBookmark = { mangaId, chapterId ->
                navController.navigate(Route.Reader(mangaId, chapterId))
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
