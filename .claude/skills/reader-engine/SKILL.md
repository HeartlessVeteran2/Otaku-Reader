# Reader Engine Patterns

## Architecture
The reader is the most performance-critical part of the app. It supports four modes via a shared state machine but with mode-specific composables.

## State Machine
```kotlin
data class ReaderState(
    val manga: Manga? = null,
    val chapters: List<Chapter> = emptyList(),
    val currentChapter: Chapter? = null,
    val currentPage: Int = 0,
    val pages: List<ReaderPage> = emptyList(),
    val isLoading: Boolean = false,
    val viewer: ViewerType = ViewerType.DEFAULT,
    val zoom: Float = 1f,
    val overlayVisible: Boolean = true,
    val settings: ReaderSettings = ReaderSettings(),
)
```

## Event Grouping (Post-Refactor)
Events are grouped by domain via sealed interfaces:
```kotlin
sealed interface ReaderEvent {
    sealed interface PageNavigation : ReaderEvent {
        data object NextPage : PageNavigation
        data object PrevPage : PageNavigation
        data class GoToPage(val page: Int) : PageNavigation
    }
    sealed interface ZoomControl : ReaderEvent {
        data class SetZoom(val zoom: Float) : ZoomControl
        data object ResetZoom : ZoomControl
    }
    // ... etc
}
```

This allows filtering entire domains: `if (event is ReaderEvent.PageNavigation)`

## Reader Modes

### Single Page
- `HorizontalPager` with `beyondBoundsPageCount = 1` for preloading
- Standard manga reading experience
- Tap zones for navigation (3x3 grid, user-configurable)

### Dual Page
- Displays two pages side-by-side
- Auto-detects landscape images as solo spreads via aspect ratio threshold (1.2)
- Respects source `isSpread` flag AND detected aspect ratio
- Uses `mutableStateMapOf` to track image sizes as they load

### Webtoon
- `LazyColumn` with `contentType = { "manga_page" }` for slot reuse
- Configurable page gap (`webtoonGapDp` — 0-16dp, default 4)
- Mouse scroll support via `onPointerEvent`
- Infinite scroll feel with chapter transitions

### Smart Panels
- ML Kit panel detection for tap-to-navigate
- Results cached in `LruCache<String, 50>`
- `allowHardware(false)` for ML Kit pixel access
- Falls back to single page if detection fails

## Image Loading (Coil 3)
```kotlin
AsyncImage(
    model = ImageRequest.Builder(context)
        .data(page.imageUrl)
        .crossfade(true)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .build(),
    contentDescription = null,
    onSuccess = { state ->
        // Report image size for dual page spread detection
        onImageSizeKnown(page.number, state.painter.intrinsicSize)
    }
)
```

## Zoom & Pan
- Custom `ZoomableImage` composable
- Pinch-to-zoom with configurable min/max bounds
- Pan when zoomed > 1.0
- Double-tap to zoom toggle

## Performance Rules
- Preload adjacent pages (via `beyondBoundsPageCount` or manual prefetch)
- Clear reader cache in `ViewModel.onCleared()`
- Don't decode images larger than screen size
- Use `RGB_565` for opaque pages
- Cache panel detection results
- Debounce rapid page navigation events

## Adding New Reader Features
1. Add event to appropriate sealed interface group
2. Handle in ViewModel's `onEvent()`
3. Update state representation
4. Implement in relevant reader composable(s)
5. Update settings if user-configurable
6. Consider all four modes — feature should work (or gracefully degrade) in each
