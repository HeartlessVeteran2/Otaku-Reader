# Otaku-Reader Code Optimization Audit
## Fat Trimming Targets — 2026-04-13

### 🔴 CRITICAL — Memory Leaks

**1. DetailsViewModel.kt — Unbounded Thumbnail Cache**
```kotlin
// Line ~65: Memory leak - grows indefinitely
private val thumbnailCache = mutableMapOf<Long, Pair<String?, Int>>()
```
**Fix:** Replace with LRU cache (max 50 entries)

---

### 🟠 HIGH — Performance

**2. DetailsViewModel.kt — 7 Separate Flow Collections**
```kotlin
init {
    loadMangaDetails()      // Flow 1
    loadChapters()          // Flow 2
    observeFavoriteStatus() // Flow 3
    loadNextUnreadChapter() // Flow 4
    observeDownloads()      // Flow 5
    observeDeleteAfterReadSetting() // Flow 6
    observeAiSettings()     // Flow 7
}
```
**Fix:** Combine related flows to reduce coroutine overhead

**3. SettingsScreen.kt — 2154 Lines**
Massive Composable doing too much. Split into separate screen files:
- `AppearanceSettingsScreen.kt`
- `ReaderSettingsScreen.kt`
- `DownloadSettingsScreen.kt`
- etc.

**4. Unnecessary List Copies**
```kotlin
// Found 42 instances of .toList() / .toMutableList()
// Many are redundant - creating new collections when not needed
```

---

### 🟡 MEDIUM — Compose Efficiency

**5. Missing LazyList Keys**
```kotlin
// Some LazyColumns use items() without key parameter
// Causes unnecessary recompositions during scroll
```

**6. Lambda Captures in Lazy Lists**
```kotlin
items(list) { item ->
    MyItem(
        onClick = { onEvent(ItemClick(item.id)) } // Captures loop variable
    )
}
```
**Fix:** Use `remember` or key-based lambda caching

---

### 🟢 LOW — Code Bloat

**7. Verbose Comments**
- 617KB of comments in Kotlin files
- Many are redundant KDoc

**8. Debug Code in Release**
```kotlin
// 6 println() statements in production code
System.err.println("[SyncRoutes] storeSnapshot failed...")
```

**9. Suppression Annotations**
- 41 @Suppress annotations (some may be outdated)
- 12 "unused" suppressions

---

## Recommended Fixes Priority

| Priority | File | Fix | Estimated Impact |
|----------|------|-----|------------------|
| P0 | DetailsViewModel.kt | Bounded LRU cache for thumbnails | Fixes memory leak |
| P1 | DetailsViewModel.kt | Combine observeX() flows | Reduces coroutine count |
| P1 | SettingsScreen.kt | Split into separate screens | Better maintainability |
| P2 | Multiple | Remove redundant .toList() calls | Fewer allocations |
| P2 | Multiple | Add missing LazyList keys | Better scroll perf |
| P3 | Multiple | Replace println with Timber | Cleaner logs |
