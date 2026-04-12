# Otaku Reader UI Audit Report

**Date:** March 23, 2026  
**Auditor:** Aura (AI Assistant)  
**Scope:** Jetpack Compose UI components, themes, and user experience patterns

---

## Executive Summary

The Otaku Reader UI codebase demonstrates solid architecture with Clean + MVI patterns, comprehensive theming, and feature-rich reader modes. However, several opportunities exist for performance optimization, visual refinement, and code consolidation.

**Overall Grade:** B+ (Good with improvement opportunities)

---

## 1. Performance Optimizations

### 1.1 Stability Annotations Missing ⚠️ HIGH PRIORITY

**Issue:** Domain models are passed directly to composables without stability annotations.

**Current Code:**
```kotlin
// In MangaCard.kt - Manga class is likely unstable
@Composable
fun MangaCard(title: String, coverUrl: String, ...) {
    // If Manga class changes frequently, this recomposes unnecessarily
}
```

**Recommendation:**
```kotlin
// In domain layer
@Immutable
data class Manga(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    // ...
)

// Or create stable wrapper for lists
@Immutable
data class MangaList(val items: List<Manga> = emptyList())
```

**Impact:** Reduces unnecessary recompositions in LazyColumn/LazyGrid.

### 1.2 Lazy List Keys Missing in Some Places ⚠️ MEDIUM PRIORITY

**Current Code:**
```kotlin
// LibraryScreen.kt - some lists don't provide keys
LazyVerticalGrid(
    columns = GridCells.Adaptive(minSize = 100.dp)
) {
    items(libraryItems) { item ->  // No key provided
        MangaCard(...)
    }
}
```

**Recommendation:**
```kotlin
items(
    items = libraryItems,
    key = { it.manga.id },  // Unique stable key
    contentType = { it::class.simpleName }  // Optimize recycling
) { item ->
    MangaCard(...)
}
```

### 1.3 State Read Optimization ✅ GOOD

**Positive Finding:** `WebtoonReader.kt` correctly uses `derivedStateOf` for tracking visible pages:

```kotlin
val currentVisiblePage by remember {
    derivedStateOf {
        // Expensive calculation only when layout changes
        calculateMostVisibleItem()
    }
}
```

---

## 2. UI/UX Improvements

### 2.1 MangaCard Component Too Basic ⚠️ MEDIUM PRIORITY

**Current Issues:**
- No loading placeholder for images
- No error state handling
- No elevation/shadow for depth
- Text contrast issues on certain backgrounds
- No badge support (unread count, download status)

**Recommended Enhancement:**
```kotlin
@Composable
fun MangaCard(
    title: String,
    coverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: @Composable (() -> Unit)? = null,  // Unread count, etc.
    onError: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                placeholder = painterResource(R.drawable.placeholder_cover),
                error = painterResource(R.drawable.error_cover),
                onError = { onError?.invoke() }
            )
            
            // Gradient scrim for text readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )
            
            // Title with better contrast
            Text(
                text = title,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
            
            // Optional badge
            badge?.let {
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    it()
                }
            }
        }
    }
}
```

### 2.2 Empty/Error States Too Generic ⚠️ MEDIUM PRIORITY

**Current:** Basic text-only screens with minimal visual feedback.

**Recommendation:** Add illustrations, actionable buttons, and brand personality:

```kotlin
@Composable
fun EmptyLibraryScreen(onBrowseClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Illustrated empty state
        Image(
            painter = painterResource(R.drawable.illustration_empty_library),
            contentDescription = null,
            modifier = Modifier.size(200.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Your library is empty",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Text(
            text = "Discover your next favorite manga",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onBrowseClick) {
            Icon(Icons.Default.Explore, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Browse Sources")
        }
    }
}
```

### 2.3 Theme System is Excellent ✅ HIGHLIGHT

**Positive Findings:**
- 11 custom color schemes
- Pure Black AMOLED mode
- Dynamic color (Material You) support
- Custom accent color generation
- High contrast accessibility mode

The `OtakuReaderTheme.kt` is well-architected with proper color scheme mapping.

---

## 3. Animation & Motion

### 3.1 Missing Transitions Between Screens ⚠️ LOW PRIORITY

**Issue:** No shared element transitions when navigating from library to manga details.

**Recommendation:** Implement Material Motion shared element transitions:

```kotlin
// Using AnimatedContent or navigation-compose experimental APIs
AnimatedContent(
    targetState = selectedManga,
    transitionSpec = {
        (fadeIn(animationSpec = tween(220, delayMillis = 90)) +
         scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
        .togetherWith(
            fadeOut(animationSpec = tween(90))
        )
    }
) { manga ->
    if (manga != null) {
        DetailsScreen(manga)
    } else {
        LibraryScreen()
    }
}
```

### 3.2 Reader Mode Switching Could Be Smoother ⚠️ LOW PRIORITY

**Current:** Instant switch between Single/Dual/Webtoon/Smart Panels modes.

**Recommendation:** Add crossfade animation:

```kotlin
AnimatedContent(
    targetState = readerMode,
    transitionSpec = { fadeIn() togetherWith fadeOut() }
) { mode ->
    when (mode) {
        ReaderMode.SINGLE -> SinglePageReader(...)
        ReaderMode.DUAL -> DualPageReader(...)
        // ...
    }
}
```

---

## 4. Code Organization

### 4.1 Component Consolidation Opportunity ⚠️ LOW PRIORITY

**Finding:** Loading indicators implemented in multiple places:
- `core/ui/components/LoadingIndicator.kt`
- `core/ui/component/StateScreens.kt` (LoadingScreen)
- Inline `CircularProgressIndicator` usages

**Recommendation:** Standardize on one loading component with variants:

```kotlin
// Single unified component
@Composable
fun OtakuLoadingIndicator(
    modifier: Modifier = Modifier,
    size: LoadingSize = LoadingSize.MEDIUM,
    style: LoadingStyle = LoadingStyle.CIRCULAR
) { ... }

enum class LoadingSize { SMALL, MEDIUM, LARGE, FULLSCREEN }
enum class LoadingStyle { CIRCULAR, LINEAR, SHIMMER }
```

### 4.2 Modifier Extensions Could Be Extracted ⚠️ LOW PRIORITY

**Finding:** Common modifier chains repeated across screens (padding, clickable, etc.)

**Recommendation:** Create reusable modifier extensions:

```kotlin
// In core/ui/modifiers/
fun Modifier.mangaCardClickable(onClick: () -> Unit) = this
    .clickable(onClick = onClick)
    .padding(4.dp)

fun Modifier.screenPadding() = this.padding(16.dp)
```

---

## 5. Accessibility

### 5.1 Content Descriptions Missing ⚠️ HIGH PRIORITY

**Issue:** Many images lack content descriptions:

```kotlin
// Current - no content description
AsyncImage(
    model = coverUrl,
    contentDescription = null,  // Screen reader can't describe this
    ...
)
```

**Recommendation:**
```kotlin
AsyncImage(
    model = coverUrl,
    contentDescription = stringResource(
        R.string.cover_of_manga_named, 
        manga.title
    ),
    ...
)
```

### 5.2 Touch Targets May Be Too Small ⚠️ MEDIUM PRIORITY

**Finding:** Some inline buttons and icons may not meet 48dp minimum touch target.

**Recommendation:** Audit all clickable elements:

```kotlin
IconButton(
    onClick = onClick,
    modifier = Modifier.minimumInteractiveComponentSize() // Ensures 48dp
) { ... }
```

---

## 6. Prioritized Action Items

### Immediate (High Impact, Low Effort)
1. **Add content descriptions** to all images for accessibility
2. **Add stability annotations** (`@Immutable`) to domain models
3. **Add keys to LazyColumn/LazyGrid items** for performance

### Short Term (1-2 sprints)
4. **Enhance MangaCard** with loading states, error handling, and badges
5. **Improve empty/error states** with illustrations and actions
6. **Standardize loading indicators** across the app

### Long Term (Backlog)
7. **Add shared element transitions** for navigation
8. **Extract reusable modifier extensions**
9. **Implement shimmer loading** for better perceived performance

---

## Appendix: Code Samples

### A1. Stability Configuration File

Create `compose_compiler_config.conf`:

```
// Mark external classes as stable
eu.kanade.tachiyomi.source.model.SManga
app.otakureader.domain.model.Manga
app.otakureader.domain.model.Chapter
```

### A2. Shimmer Loading Effect

```kotlin
@Composable
fun ShimmerMangaCard() {
    val shimmer = rememberShimmer(shimmerBounds = ShimmerBounds.View)
    Card(
        modifier = Modifier.shimmer(shimmer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.LightGray.copy(alpha = 0.3f))
        )
    }
}
```

---

*End of Audit Report*
