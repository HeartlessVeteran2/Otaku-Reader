# Komikku 2026 - Extension API Documentation

Complete guide for developing manga source extensions for Komikku 2026.

## Table of Contents

- [Overview](#overview)
- [Getting Started](#getting-started)
- [Source API](#source-api)
- [Models](#models)
- [Filter System](#filter-system)
- [Example Extension](#example-extension)
- [Best Practices](#best-practices)
- [Advanced Topics](#advanced-topics)

## Overview

Komikku 2026 uses a plugin-based source system that allows developers to create extensions for manga providers. Extensions are distributed as separate APKs that can be installed and managed within the app.

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Komikku App                            │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                   Source Manager                       │  │
│  │  - Load extensions                                    │  │
│  │  - Manage sources                                     │  │
│  │  - Handle updates                                     │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    Extension APKs                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │  Source A    │  │  Source B    │  │  Source C    │      │
│  │  Extension   │  │  Extension   │  │  Extension   │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

## Getting Started

### Prerequisites

- Android Studio Koala or newer
- JDK 21 or newer
- Android SDK with API 35
- Basic knowledge of Kotlin and Android development

### Project Setup

1. **Create a new Android project**
   - Minimum SDK: 26
   - Target SDK: 35
   - Language: Kotlin

2. **Add the source-api dependency**

```kotlin
// build.gradle.kts (Module: app)
dependencies {
    implementation("app.komikku:source-api:1.0.0")
    implementation("org.jsoup:jsoup:1.17.2")
}
```

3. **Configure the extension manifest**

```xml
<!-- AndroidManifest.xml -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:label="My Source Extension">
        
        <meta-data
            android:name="komikku.extension"
            android:value="true" />
        
        <meta-data
            android:name="komikku.extension.name"
            android:value="MyMangaSource" />
        
        <meta-data
            android:name="komikku.extension.lang"
            android:value="en" />
        
        <meta-data
            android:name="komikku.extension.version"
            android:value="1.0.0" />
        
    </application>
</manifest>
```

## Source API

### Source Interface

The `Source` interface is the main entry point for your extension:

```kotlin
interface Source {
    /** Unique identifier for the source */
    val id: Long
    
    /** Human-readable name */
    val name: String
    
    /** Base URL of the source */
    val baseUrl: String
    
    /** Language code (e.g., "en", "ja", "ko") */
    val lang: String
    
    /** Whether the source supports the latest updates endpoint */
    val supportsLatest: Boolean
    
    /** Available filters for this source */
    val filters: List<Filter<*>>
    
    /**
     * Fetch popular manga
     * @param page Page number (1-indexed)
     * @return Page of manga
     */
    suspend fun getPopularManga(page: Int): MangasPage
    
    /**
     * Fetch latest updates
     * @param page Page number (1-indexed)
     * @return Page of manga
     */
    suspend fun getLatestUpdates(page: Int): MangasPage
    
    /**
     * Search manga
     * @param query Search query
     * @param page Page number (1-indexed)
     * @param filters Applied filters
     * @return Page of manga
     */
    suspend fun searchManga(
        query: String,
        page: Int,
        filters: List<Filter<*>>
    ): MangasPage
    
    /**
     * Get manga details
     * @param manga Manga to fetch details for
     * @return Manga with full details
     */
    suspend fun getMangaDetails(manga: SManga): SManga
    
    /**
     * Get chapter list
     * @param manga Manga to fetch chapters for
     * @return List of chapters
     */
    suspend fun getChapterList(manga: SManga): List<SChapter>
    
    /**
     * Get page list for a chapter
     * @param chapter Chapter to fetch pages for
     * @return List of pages
     */
    suspend fun getPageList(chapter: SChapter): List<SPage>
    
    /**
     * Get image URL for a page
     * @param page Page to get image URL for
     * @return Image URL
     */
    suspend fun getImageUrl(page: SPage): String
}
```

### HttpSource

For HTTP-based sources, extend `HttpSource` which provides common functionality:

```kotlin
abstract class HttpSource : Source {
    
    /** OkHttp client for network requests */
    protected val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /** Request headers */
    protected open fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", DEFAULT_USER_AGENT)
    
    /** Make a GET request */
    protected suspend fun GET(
        url: String,
        headers: Headers = headersBuilder().build()
    ): Response
    
    /** Make a POST request */
    protected suspend fun POST(
        url: String,
        headers: Headers = headersBuilder().build(),
        body: RequestBody
    ): Response
    
    /** Parse HTML response with Jsoup */
    protected fun Response.asJsoup(): Document
}
```

## Models

### SManga

Represents a manga in the source:

```kotlin
data class SManga(
    /** Unique identifier from source */
    val url: String,
    
    /** Manga title */
    val title: String,
    
    /** Cover image URL */
    val thumbnailUrl: String? = null,
    
    /** Manga description */
    val description: String? = null,
    
    /** Author name */
    val author: String? = null,
    
    /** Artist name */
    val artist: String? = null,
    
    /** Genre tags */
    val genre: String? = null,
    
    /** Publication status */
    val status: Int = UNKNOWN,
    
    /** Initialized flag */
    val initialized: Boolean = false
) {
    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6
    }
}
```

### SChapter

Represents a chapter:

```kotlin
data class SChapter(
    /** Unique identifier from source */
    val url: String,
    
    /** Chapter name */
    val name: String,
    
    /** Chapter number */
    val chapterNumber: Float = -1f,
    
    /** Volume number */
    val volumeNumber: Float = -1f,
    
    /** Upload date (timestamp) */
    val dateUpload: Long = 0,
    
    /** Scanlator group */
    val scanlator: String? = null
)
```

### SPage

Represents a page in a chapter:

```kotlin
data class SPage(
    /** Page index */
    val index: Int,
    
    /** Image URL (may be null if requires additional fetch) */
    val imageUrl: String? = null,
    
    /** Page URL for additional processing */
    val url: String? = null
)
```

### MangasPage

Represents a page of manga results:

```kotlin
data class MangasPage(
    /** List of manga */
    val manga: List<SManga>,
    
    /** Whether there are more pages */
    val hasNextPage: Boolean
)
```

## Filter System

### Available Filters

```kotlin
// Header filter (for grouping)
class Header(name: String) : Filter<String>(name)

// Separator filter
class Separator() : Filter<Unit>("")

// Text filter
class Text(name: String, state: String = "") : Filter<String>(name, state)

// Checkbox filter
class CheckBox(name: String, state: Boolean = false) : Filter<Boolean>(name, state)

// Select filter (dropdown)
class Select(
    name: String,
    val options: List<String>,
    state: Int = 0
) : Filter<Int>(name, state)

// Tri-state filter (include/exclude/ignore)
class TriState(
    name: String,
    state: Int = STATE_IGNORE
) : Filter<Int>(name, state) {
    companion object {
        const val STATE_IGNORE = 0
        const val STATE_INCLUDE = 1
        const val STATE_EXCLUDE = 2
    }
}

// Group filter (for complex filters)
class Group<T>(
    name: String,
    val filters: List<Filter<T>>
) : Filter<List<T>>(name, filters.map { it.state })

// Sort filter
class Sort(
    name: String,
    val options: List<Pair<String, String>>,
    state: Selection? = null
) : Filter<Sort.Selection?>(name, state) {
    data class Selection(val index: Int, val ascending: Boolean)
}
```

### Using Filters

```kotlin
class MyMangaSource : HttpSource() {
    
    override val filters: List<Filter<*>> = listOf(
        // Status filter
        Select(
            "Status",
            listOf("All", "Ongoing", "Completed", "Hiatus"),
            0
        ),
        
        // Genre filter (tri-state)
        Group(
            "Genres",
            listOf(
                TriState("Action"),
                TriState("Adventure"),
                TriState("Comedy"),
                TriState("Drama"),
                TriState("Fantasy")
            )
        ),
        
        // Sort filter
        Sort(
            "Sort by",
            listOf(
                "Popular" to "popular",
                "Latest" to "latest",
                "Alphabetical" to "alphabetical"
            )
        )
    )
    
    override suspend fun searchManga(
        query: String,
        page: Int,
        filters: List<Filter<*>>
    ): MangasPage {
        // Process filters
        val statusFilter = filters.find { it.name == "Status" } as? Select
        val genreFilter = filters.find { it.name == "Genres" } as? Group<*>
        val sortFilter = filters.find { it.name == "Sort by" } as? Sort
        
        // Build search URL with filter parameters
        // ...
    }
}
```

## Example Extension

### Complete Example

```kotlin
package app.komikku.extension.en.mymangasource

import app.komikku.source.model.*
import app.komikku.source.online.HttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class MyMangaSource : HttpSource() {
    
    override val id: Long = 123456789L
    override val name: String = "MyMangaSource"
    override val baseUrl: String = "https://mymangasource.com"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true
    
    override val filters: List<Filter<*>> = listOf(
        Select(
            "Status",
            listOf("All", "Ongoing", "Completed", "Hiatus"),
            0
        ),
        Group(
            "Genres",
            listOf(
                TriState("Action"),
                TriState("Adventure"),
                TriState("Comedy"),
                TriState("Drama"),
                TriState("Fantasy"),
                TriState("Romance")
            )
        )
    )
    
    // ============== Popular Manga ==============
    
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/popular?page=$page"
        val response = GET(url)
        val document = response.asJsoup()
        
        val manga = document.select("div.manga-item").map { element ->
            element.toSManga()
        }
        
        val hasNextPage = document.select("a.next").isNotEmpty()
        
        return MangasPage(manga, hasNextPage)
    }
    
    // ============== Latest Updates ==============
    
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/latest?page=$page"
        val response = GET(url)
        val document = response.asJsoup()
        
        val manga = document.select("div.manga-item").map { element ->
            element.toSManga()
        }
        
        val hasNextPage = document.select("a.next").isNotEmpty()
        
        return MangasPage(manga, hasNextPage)
    }
    
    // ============== Search ==============
    
    override suspend fun searchManga(
        query: String,
        page: Int,
        filters: List<Filter<*>>
    ): MangasPage {
        val urlBuilder = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
        
        // Apply filters
        filters.forEach { filter ->
            when (filter) {
                is Select -> {
                    if (filter.name == "Status" && filter.state > 0) {
                        urlBuilder.addQueryParameter(
                            "status",
                            filter.options[filter.state].lowercase()
                        )
                    }
                }
                is Group<*> -> {
                    if (filter.name == "Genres") {
                        val included = mutableListOf<String>()
                        val excluded = mutableListOf<String>()
                        
                        filter.filters.forEachIndexed { index, genreFilter ->
                            when ((genreFilter as TriState).state) {
                                TriState.STATE_INCLUDE -> 
                                    included.add(genreFilter.name)
                                TriState.STATE_EXCLUDE -> 
                                    excluded.add(genreFilter.name)
                            }
                        }
                        
                        if (included.isNotEmpty()) {
                            urlBuilder.addQueryParameter(
                                "genres",
                                included.joinToString(",")
                            )
                        }
                        if (excluded.isNotEmpty()) {
                            urlBuilder.addQueryParameter(
                                "exclude_genres",
                                excluded.joinToString(",")
                            )
                        }
                    }
                }
            }
        }
        
        val response = GET(urlBuilder.build().toString())
        val document = response.asJsoup()
        
        val manga = document.select("div.manga-item").map { element ->
            element.toSManga()
        }
        
        val hasNextPage = document.select("a.next").isNotEmpty()
        
        return MangasPage(manga, hasNextPage)
    }
    
    // ============== Manga Details ==============
    
    override suspend fun getMangaDetails(manga: SManga): SManga {
        val response = GET(baseUrl + manga.url)
        val document = response.asJsoup()
        
        return manga.copy(
            title = document.select("h1.manga-title").text(),
            thumbnailUrl = document.select("img.manga-cover")
                .attr("src"),
            description = document.select("div.description")
                .text(),
            author = document.select("div.author").text()
                .removePrefix("Author: "),
            artist = document.select("div.artist").text()
                .removePrefix("Artist: "),
            genre = document.select("div.genres a")
                .joinToString { it.text() },
            status = parseStatus(document.select("div.status").text()),
            initialized = true
        )
    }
    
    private fun parseStatus(status: String): Int {
        return when (status.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            "hiatus" -> SManga.ON_HIATUS
            "cancelled" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
    
    // ============== Chapter List ==============
    
    override suspend fun getChapterList(manga: SManga): List<SChapter> {
        val response = GET(baseUrl + manga.url)
        val document = response.asJsoup()
        
        return document.select("div.chapter-item").map { element ->
            SChapter(
                url = element.select("a").attr("href"),
                name = element.select("span.chapter-name").text(),
                chapterNumber = element.select("span.chapter-number")
                    .text()
                    .toFloatOrNull() ?: -1f,
                dateUpload = parseDate(
                    element.select("span.chapter-date").text()
                ),
                scanlator = element.select("span.scanlator").text()
            )
        }.reversed() // Oldest first
    }
    
    private fun parseDate(date: String): Long {
        // Parse date string to timestamp
        // Example: "2 hours ago", "2024-01-15"
        return try {
            when {
                date.contains("hour") -> {
                    val hours = date.filter { it.isDigit() }.toInt()
                    System.currentTimeMillis() - hours * 60 * 60 * 1000
                }
                date.contains("day") -> {
                    val days = date.filter { it.isDigit() }.toInt()
                    System.currentTimeMillis() - days * 24 * 60 * 60 * 1000
                }
                else -> {
                    // Parse standard date format
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        .parse(date)?.time ?: 0L
                }
            }
        } catch (e: Exception) {
            0L
        }
    }
    
    // ============== Page List ==============
    
    override suspend fun getPageList(chapter: SChapter): List<SPage> {
        val response = GET(baseUrl + chapter.url)
        val document = response.asJsoup()
        
        return document.select("div.page img").mapIndexed { index, element ->
            SPage(
                index = index,
                imageUrl = element.attr("data-src")
                    .ifEmpty { element.attr("src") }
            )
        }
    }
    
    override suspend fun getImageUrl(page: SPage): String {
        // If imageUrl is already set, return it
        return page.imageUrl ?: throw Exception("Image URL not found")
    }
    
    // ============== Helper Functions ==============
    
    private fun Element.toSManga(): SManga {
        return SManga(
            url = select("a").attr("href"),
            title = select("h3.manga-title").text(),
            thumbnailUrl = select("img").attr("data-src")
                .ifEmpty { select("img").attr("src") }
        )
    }
}
```

## Best Practices

### 1. Error Handling

```kotlin
override suspend fun getPopularManga(page: Int): MangasPage {
    return try {
        val response = GET("$baseUrl/popular?page=$page")
        
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}")
        }
        
        val document = response.asJsoup()
        // Parse manga...
        
    } catch (e: Exception) {
        throw Exception("Failed to load popular manga: ${e.message}")
    }
}
```

### 2. Rate Limiting

```kotlin
class MyMangaSource : HttpSource() {
    
    private val rateLimitInterceptor = RateLimitInterceptor(
        permitsPerPeriod = 2,
        period = 1,
        unit = TimeUnit.SECONDS
    )
    
    override val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(rateLimitInterceptor)
        .build()
}
```

### 3. Cloudflare Bypass

```kotlin
override val client: OkHttpClient = OkHttpClient.Builder()
    .addInterceptor(CloudflareInterceptor())
    .build()
```

### 4. Image Processing

```kotlin
override suspend fun getImageUrl(page: SPage): String {
    // Some sources require additional processing
    if (page.imageUrl == null && page.url != null) {
        val response = GET(baseUrl + page.url)
        val document = response.asJsoup()
        
        return document.select("img#reader-image")
            .attr("src")
    }
    
    return page.imageUrl ?: throw Exception("Image URL not found")
}
```

### 5. URL Handling

```kotlin
// Always use absolute URLs
private fun String.toAbsoluteUrl(): String {
    return when {
        startsWith("http") -> this
        startsWith("/") -> baseUrl + this
        else -> "$baseUrl/$this"
    }
}
```

## Advanced Topics

### WebView Support

For sources that require JavaScript:

```kotlin
class JavaScriptSource : HttpSource() {
    
    override suspend fun getPageList(chapter: SChapter): List<SPage> {
        // Use WebView to render page and extract image URLs
        return webViewIntercept(
            url = baseUrl + chapter.url,
            script = """
                // JavaScript to extract image URLs
                Array.from(document.querySelectorAll('img.page'))
                    .map(img => img.src)
            """
        )
    }
}
```

### Login Support

For sources that require authentication:

```kotlin
class LoginSource : HttpSource(), LoginSource {
    
    override val requiresLogin: Boolean = true
    
    override suspend fun login(username: String, password: String): Boolean {
        val response = POST(
            "$baseUrl/login",
            body = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build()
        )
        
        return response.isSuccessful
    }
    
    override suspend fun isLoggedIn(): Boolean {
        val response = GET("$baseUrl/profile")
        return response.asJsoup()
            .select(".user-profile")
            .isNotEmpty()
    }
}
```

### Dynamic Content

For sources with dynamic content loading:

```kotlin
override suspend fun getPopularManga(page: Int): MangasPage {
    // Some sources use AJAX to load content
    val ajaxUrl = "$baseUrl/ajax/popular?page=$page"
    
    val response = GET(
        ajaxUrl,
        headers = headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .build()
    )
    
    // Parse JSON response
    val json = Json.parseToJsonElement(response.body.string())
    // Extract manga from JSON...
}
```

---

For more information, see:
- [Architecture Documentation](ARCHITECTURE.md)
- [Feature Documentation](FEATURES.md)
- [Example Extensions](https://github.com/yourusername/komikku-extensions)
