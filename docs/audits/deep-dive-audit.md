# Otaku Reader - Deep Dive Audit Report

**Date:** 2026-04-11  
**Total Kotlin Files:** 437  
**Test Files:** 7 (low coverage)

---

## ✅ COMPLETE MODULES (Ready for Production)

### 1. Core Architecture
| Component | Status | Notes |
|-----------|--------|-------|
| Clean Architecture + MVI | ✅ | Properly separated layers |
| Hilt DI | ✅ | All major components injected |
| Navigation (Type-safe) | ✅ | 17 destinations defined |
| Database (Room) | ✅ | 16 entities with migrations |
| Network (Ktor) | ✅ | OkHttp + Coil configured |
| Preferences (DataStore) | ✅ | All settings persisted |

### 2. Library Feature
| Component | Status | Notes |
|-----------|--------|-------|
| Grid/List display | ✅ | 3 grid sizes + list mode |
| Category management | ✅ | Create, rename, delete |
| Sorting (5 modes) | ✅ | Alpha, last read, date, unread, source |
| Filtering (5 modes) | ✅ | All, downloaded, unread, completed, tracking |
| Search | ✅ | Real-time filtering |
| NSFW toggle | ✅ | Hidden/show based on pref |
| Badges | ✅ | Unread count, download, tracking |
| Selection mode | ✅ | Multi-select for batch actions |
| Pull-to-refresh | ✅ | Implemented |

### 3. Reader Feature
| Component | Status | Notes |
|-----------|--------|-------|
| Paged Reader | ✅ | L2R, R2L, vertical modes |
| Webtoon Reader | ✅ | Infinite scroll |
| Smart Panels | ⚠️ | UI exists, panel detection needs work |
| Page Slider | ✅ | With RTL support |
| Page Thumbnail Strip | ✅ | Bottom scrollable strip |
| Full Page Gallery | ✅ | Grid overlay (2/3/4 cols) |
| Chapter Thumbnails | ✅ | Inline + on-demand loading |
| Volume Keys | ✅ | Navigation implemented |
| Keep Screen On | ✅ | Implemented |
| Fullscreen | ✅ | System bars hidden |
| Background Color | ✅ | Settings exist |
| Scale Type | ✅ | Fit, stretch, original, smart fit |
| Tap Zones | ✅ | 3x3 grid customizable |
| Page Numbers | ✅ | Display toggle |
| Progress Saving | ✅ | Per-chapter resume |

### 4. Downloads
| Component | Status | Notes |
|-----------|--------|-------|
| Chapter Download | ✅ | Queue-based with progress |
| Auto-download | ✅ | New chapters, Wi-Fi only |
| CBZ Export | ✅ | Single + batch |
| Download Location | ✅ | Configurable path |
| Concurrent Limit | ✅ | Default 2 |
| Download Ahead | ✅ | While reading |

### 5. Tracking
| Component | Status | Notes |
|-----------|--------|-------|
| MyAnimeList | ✅ | OAuth + sync |
| AniList | ✅ | OAuth + sync |
| Kitsu | ✅ | OAuth + sync |
| MangaUpdates | ✅ | OAuth + sync |
| Auto-sync | ✅ | On chapter read |
| Manual sync | ✅ | Push/pull |

### 6. Search & Discovery
| Component | Status | Notes |
|-----------|--------|-------|
| Smart Search (AI) | ✅ | Uses Gemini for enhanced search |
| Source Browsing | ✅ | Per-source catalog |
| Global Search | ✅ | Cross-source search |
| Saved Searches | ⚠️ | Interface defined, not fully wired |

### 7. Settings
| Component | Status | Notes |
|-----------|--------|-------|
| Appearance | ✅ | 11 themes, AMOLED, dynamic colors |
| Library | ✅ | Grid, badges, filter defaults |
| Reader | ✅ | Comprehensive options |
| Downloads | ✅ | All options functional |
| Tracking | ✅ | Login, sync settings |
| Backup/Restore | ✅ | Auto + manual, local only |
| Notifications | ✅ | Updates, goals, errors |
| Discord RPC | ✅ | Complete |
| Privacy | ✅ | NSFW, crash reporting |

### 8. AI Features
| Component | Status | Notes |
|-----------|--------|-------|
| Gemini Integration | ✅ | API key, client, rate limiting |
| Smart Search | ✅ | Caching, fallback |
| Recommendations | ✅ | RepositoryImpl just added |
| SFX Translation | ⚠️ | Toggle exists, not hooked up |
| Summary Translation | ⚠️ | Toggle exists, not hooked up |
| Source Intelligence | ⚠️ | Toggle exists, not hooked up |
| Auto-Categorization | ⚠️ | Toggle exists, repo interface only |

---

## ❌ MISSING IMPLEMENTATIONS

### Critical (App Won't Function Without)

#### 1. FeedRepositoryImpl
**Priority:** HIGH  
**Impact:** Feed feature completely broken

```kotlin
// Domain interface exists:
interface FeedRepository {
    fun getFeedSources(): Flow<List<FeedSource>>
    suspend fun addFeedSource(sourceId: Long, sourceName: String)
    suspend fun removeFeedSource(sourceId: Long)
    fun getFeedItems(limit: Int = 100): Flow<List<FeedItem>>
    suspend fun refreshFeed()
    ...
}

// Implementation: MISSING
// Location: /tmp/otaku-reader/domain/.../FeedRepository.kt
```

**What's needed:**
- Room entity for FeedSource
- Room entity for FeedItem
- DAO with queries
- RepositoryImpl with source scraping logic

#### 2. CategorizationRepositoryImpl
**Priority:** MEDIUM  
**Impact:** AI auto-categorization non-functional

```kotlin
// Domain interface exists:
interface CategorizationRepository {
    suspend fun saveCategorizationResult(result: CategorizationResult)
    suspend fun getCategorizationResult(mangaId: Long): CategorizationResult?
    ...
}

// Implementation: MISSING
```

**What's needed:**
- Room entity for CategorizationResult
- AI prompt to categorize manga
- Background worker to process library

#### 3. TrackerSyncRepositoryImpl
**Priority:** MEDIUM  
**Impact:** 2-way sync not available (TrackRepositoryImpl does 1-way)

```kotlin
// Domain interface exists with bidirectional sync
// But TrackRepositoryImpl only handles 1-way sync

// Implementation: MISSING
```

**What's needed:**
- Conflict resolution logic
- Bi-directional sync algorithms
- Sync state tracking

### Important (Feature Complete but Broken)

#### 4. Library "For You" Recommendations UI
**Priority:** HIGH  
**Status:** RepositoryImpl added, UI not connected

**What's missing:**
- LibraryViewModel doesn't inject RecommendationRepository
- LibraryViewModel doesn't handle recommendation events
- LibraryScreen doesn't show "For You" carousel
- No "Not enough manga" state

**Files to modify:**
- `/feature/library/LibraryViewModel.kt` - Add recommendation loading
- `/feature/library/LibraryScreen.kt` - Add recommendation UI
- `/feature/library/LibraryMvi.kt` - Events already added

#### 5. Smart Panels Panel Detection
**Priority:** MEDIUM  
**Status:** UI component exists, algorithm missing

```kotlin
// File: /feature/reader/modes/SmartPanelsReader.kt
// Has: Pager setup, navigation
// Missing: Actual panel detection algorithm
```

**Options:**
- **ML approach:** TensorFlow Lite model trained on manga panels
- **Heuristic approach:** Image processing to detect white/black gutters between panels
- **Manual approach:** Let users mark panels (not recommended)

**Estimated effort:** 3-5 days (ML) or 1-2 days (heuristic)

#### 6. Feed Feature UI
**Priority:** MEDIUM  
**Status:** Repository interface defined, no UI

**What's needed:**
- FeedScreen composable
- FeedViewModel
- FeedMvi (State, Event, Effect)
- Navigation wiring

#### 7. Extension/Plugin System
**Priority:** HIGH  
**Status:** SourceRepository exists but extension management unclear

**Questions:**
- Can users install 3rd party extensions?
- Is there an extension store UI?
- How are extensions sideloaded?

---

## ⚠️ PARTIAL IMPLEMENTATIONS

### 1. Recommendation Feature
| Component | Status |
|-----------|--------|
| RepositoryImpl | ✅ Just added |
| Library Mvi | ✅ Events added |
| LibraryViewModel | ❌ Not connected |
| LibraryScreen | ❌ No UI |
| Cache/DB | ✅ Entity exists |

### 2. AI Features (Toggle exists, not hooked up)
| Feature | Toggle | Implementation |
|---------|--------|----------------|
| SFX Translation | ✅ | ❌ |
| Summary Translation | ✅ | ❌ |
| Source Intelligence | ✅ | ❌ |
| Auto-Categorization | ✅ | ❌ Repository only |

### 3. Test Coverage
| Module | Test Files | Coverage |
|--------|------------|----------|
| Data | 7 | Low |
| Domain | 0 | None |
| Feature | 0 | None |
| Core | 0 | None |

**Total:** 7 test files for 437 Kotlin files = ~1.6% coverage

---

## 🔧 BUILD/CONFIGURATION ISSUES

### 1. API Keys (Security)
```kotlin
// File: /core/ai/src/.../SecureApiKeyDataStore.kt
// TODO: Security Review Required - Evaluate token storage mechanism (C-5)
// Issue: H-5 - Security Vulnerabilities in API Key Storage
```

**Recommendation:** Use Android Keystore for API key encryption

### 2. Flavor Dependencies
- `full` flavor: Has AI features (Gemini)
- `foss` flavor: No AI (privacy-friendly)

**Status:** ✅ Properly configured

### 3. Missing Baseline Profile
**File:** `/baselineprofile/src/.../BaselineProfileGenerator.kt`  
**Status:** Exists but may need updating

---

## 📊 COMPLETION MATRIX

| Module | Completion | Critical Issues |
|--------|------------|-----------------|
| Core/Architecture | 95% | 0 |
| Library | 90% | Feed missing |
| Reader | 85% | Smart Panels algorithm |
| Downloads | 95% | 0 |
| Tracking | 90% | 2-way sync missing |
| Browse/Search | 80% | Saved searches not wired |
| Settings | 95% | 0 |
| AI Features | 60% | 4 toggles not hooked up |
| Recommendations | 70% | UI not connected |
| Tests | 5% | Need comprehensive suite |

**Overall: ~78% Complete**

---

## 🎯 PRIORITY FIX LIST

### Sprint 1 (Critical Path)
1. **FeedRepositoryImpl** - Feed feature completely broken
2. **Connect Recommendations to LibraryViewModel** - Just wired RepositoryImpl
3. **Library "For You" UI** - Add recommendation carousel

### Sprint 2 (Important)
4. **Smart Panels Detection** - Either ML or heuristic approach
5. **Feed UI** - FeedScreen, ViewModel, navigation
6. **CategorizationRepositoryImpl** - Auto-categorization

### Sprint 3 (Polish)
7. **TrackerSyncRepositoryImpl** - 2-way sync
8. **Hook up AI toggles** - SFX, Summary, Source Intelligence
9. **Extension management UI** - If not complete

### Sprint 4 (Quality)
10. **Unit tests** - Critical paths
11. **UI tests** - Main flows
12. **Security review** - API key storage

---

## 📝 SPECIFIC CODE GAPS

### Missing Database Entity (FeedSource)
```kotlin
// Need in /core/database/entity/
@Entity(tableName = "feed_sources")
data class FeedSourceEntity(
    @PrimaryKey val id: Long,
    val sourceId: Long,
    val sourceName: String,
    val isEnabled: Boolean,
    val sortOrder: Int,
    val itemCount: Int
)
```

### Missing Database Entity (FeedItem)
```kotlin
@Entity(tableName = "feed_items")
data class FeedItemEntity(
    @PrimaryKey val id: Long = 0,
    val mangaId: Long,
    val chapterId: Long,
    val sourceId: Long,
    val title: String,
    val chapterName: String,
    val isRead: Boolean,
    val fetchedAt: Long
)
```

### Missing ViewModel Connection (Recommendations)
```kotlin
// In LibraryViewModel, need to add:
private val recommendationRepository: RecommendationRepository,

// In onEvent, handle:
is LibraryEvent.LoadRecommendations -> loadRecommendations()
is LibraryEvent.RefreshRecommendations -> refreshRecommendations()
```

---

## CONCLUSION

Otaku Reader is a **mature, well-architected codebase** at ~78% completion. The foundation is solid:

✅ Clean architecture, proper DI, comprehensive settings  
✅ Reader experience with thumbnails, galleries, navigation  
✅ Download system, tracking, theming  
⚠️ Feed feature completely missing implementation  
⚠️ Recommendations repository done, UI not connected  
⚠️ Several AI toggles exist but aren't functional  

**Estimated time to production-ready:** 3-4 weeks of focused work

**Biggest risks:**
1. Feed feature is substantial (entity, DAO, repository, UI, background sync)
2. Smart Panels needs computer vision expertise
3. Low test coverage could lead to regressions
