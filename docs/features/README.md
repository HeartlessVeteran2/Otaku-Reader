# Otaku Reader - Feature Documentation

Complete documentation of all features available in Otaku Reader.

## Table of Contents

- [Feature Overview](#feature-overview)
- [Library Management](#library-management)
- [Browse & Search](#browse--search)
- [Reader](#reader)
- [Smart Panels](#smart-panels)
- [Smart Prefetch](#smart-prefetch)
- [Updates](#updates)
- [Downloads](#downloads)
- [Cloud Sync](#cloud-sync)
- [OPDS Catalog](#opds-catalog)
- [AI Recommendations](#ai-recommendations)
- [Discord Rich Presence](#discord-rich-presence)
- [Tracking](#tracking)
- [Statistics](#statistics)
- [Migration](#migration)
- [Settings](#settings)
- [Future Suggestions & Enhancements](#future-suggestions--enhancements)
- [Usage Instructions](#usage-instructions)

## Feature Overview

Otaku Reader offers a comprehensive set of features for manga enthusiasts:

| Category | Features |
|----------|----------|
| **Library** | Categories, favorites, reading progress, filters |
| **Browse** | Multiple sources, filters, latest/popular |
| **Search** | Global search, source-specific search, history |
| **Reader** | Multiple modes, zoom, brightness, gestures |
| **Smart Panels** | Auto panel detection, guided panel-by-panel navigation |
| **Smart Prefetch** | Adaptive prefetch based on reading speed and behavior |
| **Updates** | Auto-check, notifications, batch updates |
| **Downloads** | Background queue, CBZ export, pause/resume |
| **Cloud Sync** | Google Drive sync, conflict resolution, periodic background sync |
| **OPDS** | Self-hosted catalog support (Komga, Kavita), add/browse/search |
| **AI** | Gemini-powered manga recommendations |
| **Discord** | Rich Presence showing currently reading manga |
| **Tracking** | MyAnimeList, AniList, Kitsu, MangaUpdates, Shikimori |
| **Statistics** | Reading analytics, charts, insights |
| **Migration** | Migrate manga between sources |
| **Settings** | Appearance, reader, downloads, backup |

## Library Management

### Overview

The Library is your personal manga collection, organized with categories and tracking.

### Features

#### Categories

- Create unlimited custom categories
- Set default category for new manga
- Reorder categories with drag & drop
- Bulk move manga between categories

#### Library Grid

- **Grid Layout**: 2-4 columns (configurable)
- **Compact Mode**: Smaller cards for more content
- **Badges**: Unread count, download status, update indicator
- **Swipe Actions**: Quick mark as read, delete, share

#### Filters

| Filter | Description |
|--------|-------------|
| **Unread** | Show only manga with unread chapters |
| **Completed** | Show completed manga |
| **Downloaded** | Show manga with downloaded chapters |
| **Started** | Show manga you've started reading |
| **Bookmarked** | Show bookmarked manga |

#### Sort Options

- By Title (A-Z, Z-A)
- By Last Read
- By Last Update
- By Unread Count
- By Date Added
- By Total Chapters

### Screenshots

<p align="center">
  <img src="docs/screenshots/library.png" width="200" alt="Library">
  <img src="docs/screenshots/library_categories.png" width="200" alt="Categories">
  <img src="docs/screenshots/library_filters.png" width="200" alt="Filters">
</p>

## Browse & Search

### Browse

Browse manga from multiple sources with rich filtering options.

#### Source Selection

- View all available sources
- Enable/disable sources
- Source status indicators (online/offline/error)
- Quick source switching

#### Browse Filters

| Filter | Options |
|--------|---------|
| **Status** | Ongoing, Completed, Licensed, Publishing Finished, Cancelled, On Hiatus |
| **Type** | Manga, Manhwa, Manhua, Comic, Webtoon |
| **Sort** | Popular, Latest, Alphabetical, Date Added |
| **Genre** | Multi-select genre filter |

#### Browse Modes

- **Popular**: Most popular manga from source
- **Latest**: Recently updated manga
- **Search**: Search within source

### Search

#### Global Search

- Search across all enabled sources
- Real-time results as you type
- Search history with suggestions
- Saved searches for quick access

#### Search Filters

- Filter by source
- Filter by status
- Filter by genre
- Sort results

### Screenshots

<p align="center">
  <img src="docs/screenshots/browse.png" width="200" alt="Browse">
  <img src="docs/screenshots/search.png" width="200" alt="Search">
  <img src="docs/screenshots/source_selection.png" width="200" alt="Sources">
</p>

## Reader

### Overview

The Reader provides an immersive reading experience with multiple viewing modes and customization options.

### Viewing Modes

| Mode | Description | Best For |
|------|-------------|----------|
| **Left-to-Right** | Standard left-to-right paging | Western comics |
| **Right-to-Left** | Manga-style right-to-left paging | Japanese manga |
| **Vertical (Webtoon)** | Continuous vertical scrolling | Korean webtoons |

### Reader Settings

#### General Settings

| Setting | Options |
|---------|---------|
| **Reading Mode** | LTR, RTL, Vertical |
| **Scale Type** | Fit Screen, Fit Width, Fit Height, Original Size, Smart Fit |
| **Zoom Start** | Automatic, Left, Right, Center |
| **Crop Borders** | On/Off |

#### Navigation Settings

| Setting | Description |
|---------|-------------|
| **Tapping Zones** | Customizable tap zones for navigation |
| **Volume Keys** | Use volume keys for page navigation |
| **Invert Volume** | Invert volume key direction |
| **Page Animation** | Enable/disable page transition animations |

#### Display Settings

| Setting | Options |
|---------|---------|
| **Brightness** | In-app brightness control (separate from system) |
| **Screen Rotation** | Free, Portrait, Landscape, Locked |
| **Show Page Number** | Display current page number |
| **Show Reading Progress** | Display chapter progress |

#### Advanced Settings

| Setting | Description |
|---------|-------------|
| **Keep Screen On** | Prevent screen timeout |
| **Hide Status Bar** | Fullscreen reading mode |
| **Hide Navigation Bar** | Immersive mode |
| **Double Tap Zoom** | Enable double-tap to zoom |
| **Long Tap Menu** | Show menu on long press |

### Reader Overlay

The reader overlay provides quick access to:

- **Chapter List**: Navigate between chapters
- **Settings**: Quick access to reader settings
- **Manga Info**: View manga details
- **Share**: Share current page
- **Save**: Save current page to device

### Screenshots

<p align="center">
  <img src="docs/screenshots/reader.png" width="200" alt="Reader">
  <img src="docs/screenshots/reader_settings.png" width="200" alt="Reader Settings">
  <img src="docs/screenshots/reader_overlay.png" width="200" alt="Reader Overlay">
</p>

## Smart Panels

### Overview

Smart Panels provides guided panel-by-panel navigation similar to ComiXology's Guided View, automatically detecting manga panels and animating through them.

### How It Works

1. When Smart Panels mode is active, `PanelDetectionService` loads the page bitmap via Coil 3.
2. `PanelDetector` runs an edge detection pipeline (grayscale → Sobel-like gradient → horizontal/vertical line detection → region extraction).
3. Detected panels are stored in `ReaderPage.panels` and navigated via `PanelNavigationView`.
4. Smooth spring-eased zoom/pan animations center each panel on screen.

### Detection Settings

| Setting | Default | Description |
|---------|---------|-------------|
| **Panel Detection** | Enabled | Toggle panel auto-detection |
| **Edge Threshold** | 30 | Gradient strength required to detect an edge (0–255) |
| **Min Line Length** | 40% | Minimum separator line length as fraction of dimension |
| **Min Panel Size** | 10% | Minimum panel area as fraction of page |
| **Auto Advance** | Off | Automatically advance to next panel/page |
| **Show Borders** | Off | Overlay detected panel borders |

### Tap Zones

| Tap Area | Action |
|----------|--------|
| Left | Previous panel |
| Right | Next panel |
| Center | Show menu |

### Fallback Behavior

If panel detection fails or returns no panels, the reader falls back to the standard `ZoomableImage` full-page view transparently.

## Smart Prefetch

### Overview

Smart Prefetch proactively loads pages and chapters before the user reaches them, minimizing loading delays for a seamless reading experience.

### Prefetch Strategies

| Strategy | Pages Ahead | Cross-Chapter | Best For |
|----------|-------------|---------------|----------|
| **Conservative** | 1–2 | No | Mobile data, battery saving |
| **Balanced** | 3 (1 back) | Next only (last 3 pages) | Wi-Fi, mid-range devices |
| **Aggressive** | 7–10 (2–3 back) | Next + prev | Fast Wi-Fi, high-end devices |
| **Adaptive** | Dynamic | Based on completion rate | All users (default) |

### Adaptive Strategy

`ReadingBehaviorTracker` records `PageNavigationEvent`s and computes:

- `forwardNavigationRatio` — how often the user reads forward
- `averagePageDurationMs` — average time per page
- `completionRate` — probability of finishing a chapter
- `sequentialNavigationRatio` — sequential vs. jump navigation

The `AdaptiveChapterPrefetcher` uses these metrics to tune prefetch depth at runtime.

### Telemetry

`PrefetchTelemetry` tracks hit rate and efficiency so the adaptive strategy can continuously improve without any user configuration.

## Updates

### Overview

The Updates feature keeps track of new chapters and notifies you of updates.

### Features

#### Update Checking

- **Manual Check**: Pull to refresh for updates
- **Automatic Check**: Background updates at scheduled intervals
- **Smart Updates**: Skip checking for completed manga

#### Update Notifications

- Push notifications for new chapters
- Grouped notifications by manga
- Quick actions from notification (Mark read, Download)

#### Update History

- View update history
- See which chapters are new
- Mark all as read

### Update Settings

| Setting | Description |
|---------|-------------|
| **Auto Update** | Enable automatic chapter checking |
| **Update Interval** | How often to check for updates |
| **Only on Wi-Fi** | Only check updates on Wi-Fi |
| **Skip Completed** | Don't check updates for completed manga |
| **Notify on Update** | Show notification for new chapters |

### Screenshots

<p align="center">
  <img src="docs/screenshots/updates.png" width="200" alt="Updates">
  <img src="docs/screenshots/update_notification.png" width="200" alt="Notification">
</p>

## Downloads

### Overview

The download system allows chapters to be saved locally for offline reading, with a managed queue and CBZ archive support.

### Features

- **Background Queue**: Download multiple chapters simultaneously in the background
- **Pause / Resume**: Suspend and resume individual downloads at any time
- **Cancel**: Remove downloads from the queue at any point
- **CBZ Export**: Save chapters as CBZ archives for use with other readers
- **Auto-Download**: Automatically download new chapters when they are detected
- **Wi-Fi Only**: Restrict downloads to unmetered (Wi-Fi) connections

### Storage Layout

```
{app external files dir}/OtakuReader/
  {sanitized sourceName}/
    {sanitized mangaTitle}/
      {sanitized chapterName}/
        0.jpg, 1.jpg, …   ← loose image files
        chapter.cbz        ← optional CBZ archive
        .pages/            ← CBZ extraction cache
```

### Status Values

| Status | Description |
|--------|-------------|
| `QUEUED` | Waiting to start |
| `DOWNLOADING` | Currently downloading |
| `PAUSED` | Manually paused |
| `COMPLETED` | Download finished |
| `FAILED` | Download error |

## Cloud Sync

### Overview

Cloud Sync keeps library data (favorites, categories, reading progress) synchronized across devices using a pluggable provider architecture.

### Providers

| Provider | Status |
|----------|--------|
| **Google Drive** | Prototype (OAuth 2.0, Drive REST API v3) |
| **Dropbox** | Stub (future) |
| **WebDAV** | Stub (future) |

### Conflict Resolution Strategies

| Strategy | Behavior |
|----------|----------|
| `PREFER_NEWER` | Keep the snapshot with the most recent timestamp (default) |
| `PREFER_LOCAL` | Always use local data |
| `PREFER_REMOTE` | Always use remote data |
| `MERGE` | Intelligent merge: OR for booleans (favorites/read), max for progress |

### Background Sync

`SyncWorker` runs via WorkManager and supports:

- Configurable sync interval (1–168 hours)
- Wi-Fi-only constraint
- In-progress, success, and failure notifications via `SyncNotifier`

### What Is Synced

- ✅ Library manga (favorites, categories)
- ✅ Read progress (chapter read status, page progress)
- ✅ Categories
- ❌ Preferences (device-specific, not synced)
- ❌ Full reading history (too large; may sync separately in future)
- ❌ Theme/display settings (device-specific)

## OPDS Catalog

### Overview

OPDS (Open Publication Distribution System) support allows browsing and downloading manga from self-hosted servers such as Komga or Kavita.

### Features

- **Add Servers**: Save multiple OPDS server URLs with optional credentials
- **Browse Hierarchy**: Navigate catalog folders recursively
- **Search**: Use server-side search feeds when available
- **Direct Download**: Download CBZ/CBR archives directly into the reader
- **Edit / Delete**: Manage saved servers at any time

### Compatible Software

- [Komga](https://komga.org/) — Self-hosted manga/comics library server
- [Kavita](https://www.kavitareader.com/) — Self-hosted reading server
- Any OPDS-compliant catalog

## AI Recommendations

### Overview

`GeminiClient` integrates Google Gemini to generate personalized manga recommendations based on the user's reading history and preferences.

### Key Design

- **Config Fingerprinting**: Uses SHA-256 (via `MessageDigest`) on the `(apiKey, modelName)` pair (null-byte delimited) to detect configuration changes. The resulting hex string is stored in memory instead of the raw API key to reduce secret exposure.
- **Timeout Handling**: `TimeoutCancellationException` is caught before the generic `CancellationException` in `AiRepositoryImpl` to correctly map request timeouts to `Result.failure`.
- **Privacy**: All AI calls are opt-in; no data is sent without user consent.

## Discord Rich Presence

### Overview

`DiscordRpcService` (in `core/discord`) tracks the currently reading manga and updates Discord Rich Presence activity.

### Behavior

- Shows manga title, chapter, and elapsed reading time in the Discord status.
- Gracefully disconnects when Discord is not installed or not running.
- No network/IPC communication is performed while Discord is unavailable.

### Connection States

| State | Description |
|-------|-------------|
| `Connected` | Rich Presence active |
| `Disconnected` | No active session |
| `Error` | Discord unavailable or rejected |

## Tracking

### Overview

Otaku Reader integrates with major manga tracking services to automatically sync reading progress.

### Supported Services

| Service | Features |
|---------|---------|
| **MyAnimeList** | Reading status, score, progress sync |
| **AniList** | Reading status, score, progress sync |
| **Kitsu** | Reading status, score, progress sync |
| **MangaUpdates** | Reading list sync |
| **Shikimori** | Reading status and progress |

### Features

- **Auto Sync**: Update trackers automatically when a chapter is marked as read
- **Manual Sync**: Trigger sync from the manga details screen
- **Multi-Tracker**: Track the same manga on multiple services simultaneously

## Migration

### Overview

The Migration feature allows moving manga from one source to another while preserving reading progress and chapter history.

### Features

- **Source Selection**: Choose the target source to migrate to
- **Chapter Mapping**: Automatically map chapters from old source to new source
- **Progress Transfer**: Carry over read/unread status and page progress
- **Batch Migration**: Migrate multiple manga in a single operation
- **Status Tracking**: Monitor migration progress with per-manga status indicators

## Settings

### Overview

Comprehensive settings to customize your Otaku Reader experience.

### Categories

#### Appearance

| Setting | Options |
|---------|---------|
| **Theme** | Light, Dark, System Default |
| **Pure Black Dark Mode** | OLED-friendly dark mode |
| **Dynamic Colors** | Material You theming (Android 12+) |
| **App Language** | Per-app language selection |

#### Library

| Setting | Description |
|---------|-------------|
| **Grid Size** | Number of columns in library grid |
| **Show Unread Badges** | Display unread count on manga covers |
| **Show Download Badges** | Display download status on covers |
| **Show Language Flags** | Show source language flags |
| **Update Covers** | Auto-update manga covers |

#### Reader

See [Reader Settings](#reader-settings) above.

#### Downloads

| Setting | Description |
|---------|-------------|
| **Download Location** | Choose download directory |
| **Auto Download** | Auto-download new chapters |
| **Download Only on Wi-Fi** | Restrict downloads to Wi-Fi |
| **Remove After Read** | Auto-delete after reading |
| **Save as CBZ** | Save chapters as CBZ archives |

#### Tracking

| Setting | Description |
|---------|-------------|
| **Enable Tracking** | Sync with tracking services |
| **Auto Sync** | Auto-update tracking on chapter read |
| **Services** | MyAnimeList, AniList, Kitsu |

#### Backup & Restore

| Setting | Description |
|---------|-------------|
| **Create Backup** | Manual backup creation |
| **Auto Backup** | Scheduled automatic backups |
| **Backup Location** | Local or cloud storage |
| **Restore** | Restore from backup file |

#### Advanced

| Setting | Description |
|---------|-------------|
| **Clear Cache** | Clear image and data cache |
| **Clear Database** | Reset app database |
| **Network Settings** | Proxy, DNS, etc. |
| **Developer Options** | Debug settings |

### Screenshots

<p align="center">
  <img src="docs/screenshots/settings.png" width="200" alt="Settings">
  <img src="docs/screenshots/settings_appearance.png" width="200" alt="Appearance">
  <img src="docs/screenshots/settings_backup.png" width="200" alt="Backup">
</p>

## Statistics

### Overview

Track your reading habits with detailed statistics and insights.

### Features

#### Reading Stats

| Stat | Description |
|------|-------------|
| **Total Manga** | Number of manga in library |
| **Total Chapters** | Total chapters read |
| **Total Pages** | Total pages read |
| **Reading Time** | Total time spent reading |
| **Daily Average** | Average chapters per day |

#### Reading History

- Calendar view of reading activity
- Daily/weekly/monthly reading stats
- Reading streak tracking

#### Charts

| Chart | Description |
|-------|-------------|
| **Reading Progress** | Line chart of reading over time |
| **Genre Distribution** | Pie chart of genre preferences |
| **Status Distribution** | Bar chart of manga status |
| **Source Usage** | Chart of source usage |

#### Insights

- Most read manga
- Favorite genres
- Reading trends
- Recommendations based on history

### Screenshots

<p align="center">
  <img src="docs/screenshots/stats_overview.png" width="200" alt="Stats Overview">
  <img src="docs/screenshots/stats_charts.png" width="200" alt="Charts">
  <img src="docs/screenshots/stats_history.png" width="200" alt="History">
</p>

## Future Suggestions & Enhancements

### Kotlin Multiplatform (KMP) Expansion

Otaku Reader introduces smart manga recommendations based on your reading history.

#### Key Benefits
- **Shared Business Logic**: Share domain, data, and repository layers across platforms.
- **Unified Testing**: Write unit tests once for the core logic.
- **Native UI**: Retain Jetpack Compose on Android while using Compose Multiplatform for iOS and Desktop, providing a seamless multiplatform experience.

### Seamless OPDS Catalog Integration

Broaden content discovery by supporting the OPDS (Open Publication Distribution System) standard.

#### Features
- **Library Integration**: Allow users to add OPDS feeds from self-hosted solutions like Komga or Kavita.
- **Rich Browsing**: Browse catalogs with cover art, summaries, and metadata.
- **Direct Downloads**: Support seamless downloading of archives (CBZ/CBR) directly into the reader.

### Advanced Background Syncing with WorkManager

Provide reliable and efficient background operations for library updates and chapter downloads.

#### Enhancements
- **Scheduled Updates**: Utilize WorkManager for robust, battery-friendly scheduled library updates.
- **Resilient Downloads**: Ensure chapter downloads automatically resume after network interruptions or app restarts.
- **Constraints Management**: Allow granular control over when sync occurs (e.g., Unmetered Wi-Fi only, Device Charging).

### Enhanced List Performance using Paging 3

Improve the user experience in Browse and Library screens with infinite scrolling and robust caching.

#### Improvements
- **Infinite Scrolling**: Implement Paging 3 to smoothly handle vast extension catalogs without memory overhead.
- **Network & Database Unification**: Combine network requests and Room database caching into a single source of truth for seamless offline/online experiences.
- **UI State Management**: Simplify error handling and loading state representation using PagingData flows.

### Comprehensive UI Testing via Macrobenchmark

Guarantee a buttery-smooth reading experience on a wide range of devices.

#### Objectives
- **Startup Metrics**: Track and optimize Time to Initial Display (TTID) and Time to Full Display (TTFD).
- **Jank Detection**: Ensure scroll performance in reader and library screens maintains 60/120fps.
- **Baseline Profiles Generation**: Automate the generation and distribution of Baseline Profiles to pre-compile critical code paths upon app installation.

## Usage Instructions

### Getting Started

1. **Install Otaku Reader**
   - Download from GitHub releases or build from source

2. **Add Sources**
   - Go to Browse → Sources
   - Enable your preferred manga sources

3. **Search for Manga**
   - Use the Search tab or Browse sources
   - Find manga you want to read

4. **Add to Library**
   - Tap the heart icon to add to library
   - Organize into categories

5. **Start Reading**
   - Tap on manga cover
   - Select a chapter to read
   - Customize reader settings as needed

### Tips & Tricks

#### Library Management

- **Long press** manga for multi-select
- **Swipe** manga for quick actions
- **Pull down** to refresh library
- **Pinch** to change grid columns

#### Reading

- **Double tap** to zoom in/out
- **Long press** for quick menu
- **Volume keys** for page navigation
- **Tap edges** for next/previous page

#### Downloads

- **Download all** chapters from manga details
- **Auto-download** new chapters in settings
- **Manage downloads** from Settings → Downloads

#### Updates

- **Pull to refresh** for manual update check
- **Enable auto-update** in settings
- **Group notifications** for cleaner updates

### Keyboard Shortcuts

| Key | Action |
|-----|--------|
| **Space** | Next page |
| **Shift + Space** | Previous page |
| **Arrow Keys** | Navigate pages |
| **Home** | First page |
| **End** | Last page |
| **F11** | Fullscreen |
| **Esc** | Exit reader |

---

For more information, see:
- [Architecture Documentation](ARCHITECTURE.md)
- [Extension API](API.md)
- [Contributing Guidelines](CONTRIBUTING.md)
