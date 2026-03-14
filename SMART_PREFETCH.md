# Smart Prefetch Feature for Manga Reader

This document describes the smart prefetch implementation for the Otaku Reader manga reader.

## Overview

The smart prefetch system proactively loads manga pages and chapters based on user reading behavior to minimize loading delays and provide a seamless reading experience. It analyzes navigation patterns, adapts to reading speed, and intelligently prefetches content using configurable strategies.

## Architecture

### Domain Models (`domain/src/main/java/app/otakureader/domain/model/`)

#### ReadingBehavior.kt
- **ReadingBehavior**: Captures user reading patterns
  - `forwardNavigationRatio`: Ratio of forward page navigations (0.0-1.0)
  - `averagePageDurationMs`: Average time spent per page
  - `completionRate`: Likelihood of finishing chapters
  - `sequentialNavigationRatio`: Ratio of sequential vs jump navigation
  - `preferredReaderMode`: Most common reader mode (single/dual/webtoon/panels)
  - `sampleSize`: Number of navigation events recorded

- **PageNavigationEvent**: Records individual page navigation events
  - `mangaId`, `chapterId`: Context
  - `fromPage`, `toPage`: Navigation direction
  - `pageDurationMs`: Time spent on previous page
  - `readerMode`: Active reader mode
  - `timestamp`: Event time

#### PrefetchStrategy.kt
Defines four prefetch strategies with different memory/bandwidth tradeoffs:

- **Conservative**: Minimal prefetching (1-2 pages ahead)
  - Best for: Mobile data, low-end devices, battery preservation
  - No backward prefetch, no cross-chapter prefetch

- **Balanced**: Good tradeoff for most users (1 before, 3 after)
  - Best for: WiFi connections, mid-range devices
  - Prefetches next chapter when on last 3 pages

- **Aggressive**: Maximum preloading (2-3 before, 7-10 after)
  - Best for: Fast WiFi, high-end devices, downloaded manga
  - Prefetches next/previous chapters proactively

- **Adaptive**: Learns from user behavior
  - Adjusts based on reading speed, completion rate, navigation patterns
  - Fast readers get more prefetch, slow readers get less
  - Only prefetches next chapter if user typically completes chapters

#### PrefetchTelemetry.kt
Tracks prefetch effectiveness:
- `pagesPrefetched`: Total pages prefetched
- `cacheHits`: Prefetched pages that were viewed
- `cacheMisses`: Prefetched pages never viewed
- `onDemandLoads`: Pages loaded without prefetch
- `hitRate`: Cache hit percentage
- `efficiency`: Ratio of useful prefetches

### Core Components (`feature/reader/src/main/java/app/otakureader/feature/reader/prefetch/`)

#### ReadingBehaviorTracker
Analyzes navigation patterns to build user behavior profiles.

**Key Methods:**
- `recordNavigation(event)`: Records a page navigation event
- `getBehaviorForManga(mangaId)`: Returns behavior profile for a manga
- `currentBehavior`: Current aggregated behavior profile
- `reset()`: Clears navigation history

**Features:**
- Maintains rolling window of 500 navigation events
- Excludes outlier durations (< 100ms or > 60s)
- Requires minimum 50 samples for meaningful statistics
- Thread-safe with synchronized access

#### SmartPrefetchManager
Manages intelligent page prefetching based on strategy and behavior.

**Key Methods:**
- `prefetchPages(pages, currentPage, strategy, behavior, onlyOnWiFi, scope)`: Prefetches pages
- `recordPageView(page)`: Tracks page views for telemetry
- `getCacheHitRate()`: Returns cache hit rate
- `getPrefetchEfficiency()`: Returns prefetch efficiency
- `resetTelemetry()`: Resets telemetry counters

**Features:**
- Network-aware (WiFi vs mobile data detection)
- Avoids redundant prefetching (5-minute cooldown)
- Integrates with Coil ImageLoader
- Tracks telemetry for optimization

#### AdaptiveChapterPrefetcher
Handles cross-chapter prefetching for seamless chapter transitions.

**Key Methods:**
- `prefetchAdjacentChapters(...)`: Orchestrates next/previous chapter prefetch; currently queries adjacent chapters and marks them as prefetched, but does **not** yet prefetch their pages
- `prefetchPages(pages, pageCount, fromEnd)`: Prefetches specific pages within a chapter (where implemented)
- `clearPrefetchedChapters()`: Resets any in-memory prefetch cache and prefetch metadata

**Current Status & Planned Features:**
- Current implementation: Adjacent chapters are queried and recorded as prefetched; `prefetchChapterPages()` (page-level prefetch for those chapters) is still TODO
- Planned: Prefetch first 5 pages of the next chapter when nearing the end of the current chapter
- Planned: Prefetch last 5 pages of the previous chapter when starting a new chapter
- Planned: Respect active `PrefetchStrategy` and `ReadingBehavior` patterns to decide when/what to prefetch
- Planned: More robust avoidance of duplicate prefetching and unnecessary network/disk usage (beyond current basic checks/flags)

### ViewModel Integration (`feature/reader/src/main/java/app/otakureader/feature/reader/viewmodel/`)

#### UltimateReaderViewModel
Integrates smart prefetch into the reader lifecycle.

**Changes:**
1. **Dependency Injection**: Injects `ReadingBehaviorTracker`, `SmartPrefetchManager`, `AdaptiveChapterPrefetcher`
2. **Settings Cache**: Caches smart prefetch settings to avoid DataStore reads on every page change
3. **Page Navigation**: Records navigation events and updates telemetry
4. **Prefetch Trigger**: Uses smart prefetch if enabled, falls back to manual settings

**Behavior Tracking:**
- Records page duration on each navigation
- Tracks navigation direction (forward/backward)
- Monitors reader mode usage
- Updates behavior profile incrementally

### Settings Repository (`feature/reader/src/main/java/app/otakureader/feature/reader/repository/`)

#### ReaderSettingsRepository
Manages smart prefetch preferences via DataStore.

**New Settings:**
- `smartPrefetchEnabled`: Enable/disable smart prefetch
- `prefetchStrategyOrdinal`: Selected strategy (0-3)
- `adaptiveLearningEnabled`: Enable behavior learning
- `prefetchAdjacentChapters`: Enable cross-chapter prefetch
- `prefetchOnlyOnWiFi`: Restrict prefetch to WiFi

## Configuration

### Default Settings

```kotlin
smartPrefetchEnabled = true
prefetchStrategy = Balanced (ordinal 1)
adaptiveLearningEnabled = true
prefetchAdjacentChapters = false
prefetchOnlyOnWiFi = false
```

### Strategy Selection Guide

| Strategy | Pages Before | Pages After | Chapter Prefetch | Best For |
|----------|--------------|-------------|------------------|----------|
| Conservative | 0 | 1-2 | No | Mobile data, low-end devices |
| Balanced | 1 | 3 | Last 3 pages | WiFi, mid-range devices |
| Aggressive | 2-3 | 7-10 | Last 5 pages | Fast WiFi, high-end devices |
| Adaptive | Varies | Varies | Conditional | Users with consistent patterns |

### Adaptive Strategy Behavior

**Fast Readers** (< 3s per page):
- Prefetch 6-8 pages ahead
- Prefetch next chapter when on last 5 pages

**Normal Readers** (3-8s per page):
- Prefetch 3-5 pages ahead
- Prefetch next chapter when on last 3 pages

**Slow Readers** (> 8s per page):
- Prefetch 2-4 pages ahead
- Prefetch next chapter when on last 3 pages

## Telemetry & Optimization

### Metrics Tracked

1. **Pages Prefetched**: Total pages loaded in background
2. **Cache Hits**: Pages viewed after being prefetched
3. **On-Demand Loads**: Pages loaded without prefetch
4. **Cache Hit Rate**: `cacheHits / (cacheHits + onDemandLoads)`
5. **Prefetch Efficiency**: `viewedPrefetchedPages / pagesPrefetched`

### Optimization Tips

**High Hit Rate (> 80%)**: Prefetch strategy is effective
**Low Hit Rate (< 50%)**: Consider more conservative strategy
**Low Efficiency (< 60%)**: Too much wasted prefetching
**High Efficiency (> 85%)**: Can increase prefetch aggressiveness

### Accessing Telemetry

```kotlin
val hitRate = smartPrefetchManager.getCacheHitRate()
val efficiency = smartPrefetchManager.getPrefetchEfficiency()
val stats = smartPrefetchManager.getTelemetryStats()
println(stats) // Formatted telemetry report
```

## Performance Considerations

### Memory Usage

- **Conservative**: ~2-5 pages in memory (~2-10 MB)
- **Balanced**: ~4-6 pages in memory (~4-12 MB)
- **Aggressive**: ~9-13 pages in memory (~9-26 MB)
- **Adaptive**: Varies based on behavior

### Network Usage

Prefetching respects:
- WiFi/mobile data detection (via `prefetchOnlyOnWiFi`)
- 5-minute cooldown to avoid redundant downloads
- Coil's HTTP cache (reduces duplicate requests)

### Battery Impact

Smart prefetch minimizes battery impact by:
- Avoiding excessive network requests
- Canceling prefetch jobs on navigation
- Using coroutines for background work
- Respecting device connectivity state

## Testing

### Unit Tests

**ReadingBehaviorTrackerTest** (`feature/reader/src/test/.../ReadingBehaviorTrackerTest.kt`):
- Tests forward/backward navigation tracking
- Tests sequential vs jump navigation detection
- Tests average page duration calculation
- Tests outlier exclusion
- Tests rolling window behavior
- Tests behavior profile computation

**SmartPrefetchManagerTest** (`feature/reader/src/test/.../SmartPrefetchManagerTest.kt`):
- Tests strategy-specific prefetch behavior
- Tests WiFi/mobile data detection
- Tests telemetry tracking
- Tests cache hit/miss recording
- Tests prefetch job cancellation

### Running Tests

```bash
./gradlew :feature:reader:testDebugUnitTest
```

## Future Enhancements

1. **Per-Manga Behavior Tracking**: Track reading patterns per manga
2. **ML-Based Prediction**: Use machine learning for better prefetch predictions
3. **Bandwidth Throttling**: Limit prefetch bandwidth on metered connections
4. **Progressive Image Loading**: Prefetch low-quality first, then high-quality
5. **Prefetch Scheduling**: Schedule prefetch during idle times
6. **Integration with SourceManager**: Full cross-chapter prefetching when sources are available

## API Stability

All public APIs are subject to change as this is a new feature. Breaking changes will be documented in release notes.

## License

Part of Otaku Reader, licensed under the project license.
