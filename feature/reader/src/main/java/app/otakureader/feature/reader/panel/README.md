# Smart Panels Panel Detection

This package contains the implementation of ML-powered panel detection for the Smart Panels reader mode.

## Overview

The Smart Panels feature automatically detects individual comic panels in manga/comic pages and provides guided panel-by-panel navigation, similar to ComiXology's Guided View.

## Architecture

### Components

1. **PanelDetector** - Core detection algorithm
   - Uses edge detection to identify panel boundaries
   - Detects horizontal and vertical separator lines
   - Creates rectangular panel regions
   - Sorts panels by reading order (RTL for manga, LTR for comics)

2. **PanelDetectionRepository** - Settings persistence
   - Manages panel detection configuration via DataStore
   - Configurable sensitivity and thresholds
   - Per-user preferences for panel detection behavior

3. **PanelDetectionService** - High-level service
   - Loads images from URLs using Coil
   - Orchestrates detection process
   - Handles errors gracefully with fallbacks

4. **PanelNavigationView** - UI component
   - Displays detected panels with smooth animations
   - Zooms and centers on current panel
   - Provides tap zones for panel navigation
   - Shows panel indicator (e.g., "2 / 5")

## Algorithm

The panel detection algorithm works in several stages:

### 1. Grayscale Conversion
```kotlin
// Convert RGB to grayscale using standard formula
gray = 0.299 * R + 0.587 * G + 0.114 * B
```

### 2. Edge Detection
- Uses gradient-based edge detection (similar to Sobel)
- Detects significant intensity changes
- Configurable threshold (default: 30/255)

### 3. Line Detection
- Scans for horizontal lines (panel separators)
- Scans for vertical lines (panel separators)
- Filters lines by minimum length (default: 40% of dimension)
- Merges nearby lines (within threshold)

### 4. Panel Region Creation
- Creates grid from detected lines
- Generates rectangular panel regions
- Filters by size constraints (10%-95% of page)

### 5. Panel Sorting
- Top to bottom ordering
- Right to left for manga (RTL)
- Left to right for Western comics (LTR)

### 6. Confidence Scoring
- Based on aspect ratio (0.2-5.0 is ideal)
- Based on panel size (5%-90% is ideal)
- Returns confidence score 0.0-1.0

## Configuration

### PanelDetectionConfig Parameters

```kotlin
data class PanelDetectionConfig(
    // Edge detection threshold (0-255)
    // Lower = more sensitive, higher = less sensitive
    val edgeThreshold: Int = 30,

    // Minimum line length as percentage (0.0-1.0)
    // Lines shorter than this are ignored
    val minLineLengthPercent: Float = 0.4f,

    // Distance for merging nearby lines (pixels)
    // Lines within this distance are merged
    val lineMergeThreshold: Int = 10,

    // Minimum panel dimensions as percentage (0.0-1.0)
    // Panels smaller than this are filtered out
    val minPanelWidthPercent: Float = 0.1f,
    val minPanelHeightPercent: Float = 0.1f,

    // Maximum panel dimensions as percentage (0.0-1.0)
    // Prevents detecting full page as single panel
    val maxPanelWidthPercent: Float = 0.95f,
    val maxPanelHeightPercent: Float = 0.95f,

    // Reading direction
    // true = manga (RTL), false = Western comics (LTR)
    val isRightToLeft: Boolean = true
)
```

## Usage

### Detecting Panels

```kotlin
// Inject the service
@Inject
lateinit var panelDetectionService: PanelDetectionService

// Detect panels from image URL
val panels = panelDetectionService.detectPanelsFromUrl(
    imageUrl = page.imageUrl,
    readingDirection = ReadingDirection.RTL
)

// Use extension function for batch detection
val pagesWithPanels = pages.withPanelDetection(
    panelDetectionService = panelDetectionService,
    readingDirection = readingDirection
)
```

### Panel Navigation

```kotlin
// In SmartPanelsReader
SmartPanelsReader(
    pages = pagesWithPanels,
    currentPage = currentPage,
    currentPanel = currentPanel,
    onPageChange = { page -> /* handle page change */ },
    onPanelChange = { panel -> /* handle panel change */ }
)
```

### Configuring Detection

```kotlin
// Inject the repository
@Inject
lateinit var panelDetectionRepository: PanelDetectionRepository

// Enable/disable panel detection
panelDetectionRepository.setPanelDetectionEnabled(true)

// Adjust sensitivity
panelDetectionRepository.setEdgeThreshold(40) // Less sensitive
panelDetectionRepository.setMinLineLengthPercent(0.5f) // Stricter

// Get current config
val config = panelDetectionRepository.getPanelDetectionConfig(
    isRightToLeft = true
)
```

## Performance Considerations

1. **Lazy Detection** - Panels are only detected when Smart Panels mode is active
2. **Caching** - Detected panels are stored in `ReaderPage.panels`
3. **Background Processing** - Detection runs on `Dispatchers.Default`
4. **Graceful Fallback** - Falls back to full-page view if detection fails
5. **Bitmap Cleanup** - Bitmaps are recycled after detection

## Limitations

1. **Complex Layouts** - May struggle with irregular panel layouts
2. **No Borders** - Pages without clear panel borders may not detect properly
3. **Text Bubbles** - Speech bubbles crossing panel borders can confuse detection
4. **Spreads** - Two-page spreads should be handled by Dual Page mode
5. **Color Pages** - Works best with black/white manga

## Future Improvements

- [ ] Machine learning model for better accuracy
- [ ] Support for irregular panel shapes (non-rectangular)
- [ ] Detection of speech bubble reading order
- [ ] Adaptive thresholds based on page characteristics
- [ ] User feedback system to improve detection
- [ ] Panel detection caching to disk
- [ ] Background pre-detection for upcoming pages

## Testing

To test panel detection:

1. Enable Smart Panels mode in reader settings
2. Open a manga chapter
3. Panels should be detected and highlighted
4. Tap right side to advance to next panel
5. Tap left side to go to previous panel
6. Tap center to show menu

Adjust sensitivity in Settings > Reader > Panel Detection if panels are not detected correctly.
