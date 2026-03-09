# Otaku Reader - Feature Documentation

Complete documentation of all features available in Otaku Reader.

## Table of Contents

- [Feature Overview](#feature-overview)
- [Library Management](#library-management)
- [Browse & Search](#browse--search)
- [Reader](#reader)
- [Updates](#updates)
- [Settings](#settings)
- [Statistics](#statistics)
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
| **Updates** | Auto-check, notifications, batch updates |
| **Settings** | Appearance, reader, downloads, backup |
| **Stats** | Reading analytics, charts, insights |
| **Sync** | Cloud backup, cross-device sync |

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
