package app.otakureader.feature.onboarding

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Data class representing an onboarding page.
 */
data class OnboardingPage(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val showExtensionInstall: Boolean = false
)

/**
 * Onboarding screen for first-time users.
 * Multi-step flow: Welcome → Browse & Discover → Download & Read Offline → Organize Library → Install Extensions
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit = onComplete,
    onNavigateToExtensions: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pages = listOf(
        OnboardingPage(
            title = "Welcome to Otaku Reader",
            description = "Your personal manga library. Track your reading, discover new series, and enjoy your favorite manga offline.",
            icon = Icons.Default.MenuBook
        ),
        OnboardingPage(
            title = "Browse & Discover",
            description = "Explore thousands of manga from various sources. Search by title, genre, or popularity to find your next read.",
            icon = Icons.Default.Search
        ),
        OnboardingPage(
            title = "Download & Read Offline",
            description = "Download chapters to read anywhere, even without internet. Your library stays with you.",
            icon = Icons.Default.Download
        ),
        OnboardingPage(
            title = "Organize Your Library",
            description = "Create categories, track reading progress, and get notified when new chapters are available.",
            icon = Icons.Default.Collections
        ),
        OnboardingPage(
            title = "Install Extensions",
            description = "Otaku Reader uses extensions to access manga sources. Install your first extension to get started!",
            icon = Icons.Default.Settings,
            showExtensionInstall = true
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            OnboardingBottomBar(
                currentPage = pagerState.currentPage,
                totalPages = pages.size,
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
                showExtensionInstall = pages[pagerState.currentPage].showExtensionInstall,
                onNavigateToExtensions = onNavigateToExtensions
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) { page ->
            OnboardingPageContent(
                page = pages[page],
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = page.title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = page.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OnboardingBottomBar(
    currentPage: Int,
    totalPages: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    showExtensionInstall: Boolean,
    onNavigateToExtensions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        // Page indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
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
                            }
                        )
                )
                if (index < totalPages - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buttons
        if (showExtensionInstall) {
            Button(
                onClick = onNavigateToExtensions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Install Extensions")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Skip for Now")
            }
        } else {
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (currentPage == totalPages - 1) "Get Started" else "Next")
                if (currentPage < totalPages - 1) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (currentPage < totalPages - 1) {
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Skip")
                }
            }
        }
    }
}