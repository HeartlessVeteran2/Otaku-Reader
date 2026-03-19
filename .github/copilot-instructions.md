# GitHub Copilot Instructions for Otaku Reader

This file provides comprehensive instructions to optimize GitHub Copilot's code suggestions for the Otaku Reader project.

## Project Overview

Otaku Reader is a modern Android manga reader application built with Kotlin and Jetpack Compose, following Clean Architecture principles with MVI (Model-View-Intent) pattern.

- **Language**: Kotlin 2.3.20
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)
- **UI Framework**: Jetpack Compose + Material 3
- **Architecture**: Clean Architecture (Presentation → Domain → Data)
- **State Management**: MVI pattern with StateFlow
- **Dependency Injection**: Hilt (Dagger)

## Architecture & Design Patterns

### Clean Architecture Layers

The project is strictly organized into three layers:

1. **Presentation Layer** (`/feature/*`, `/app`)
   - Jetpack Compose UI screens
   - ViewModels with StateFlow for state management
   - MVI contracts (State, Event, Effect)
   - Navigation components

2. **Domain Layer** (`/domain`)
   - Pure Kotlin (no Android dependencies)
   - Use cases for business logic
   - Repository interfaces
   - Domain models
   - Business rules and validation

3. **Data Layer** (`/data`, `/core/*`)
   - Repository implementations
   - Database access (Room)
   - Network calls (Retrofit + OkHttp)
   - Preferences (DataStore)
   - Download management
   - Backup/restore functionality

### Core Modules (`/core/*`)

- `core/common` - Shared utilities, extensions, helpers
- `core/database` - Room database v10 with migrations, entities, DAOs
- `core/network` - Retrofit + OkHttp configuration
- `core/preferences` - DataStore for type-safe preferences
- `core/ui` - Shared Jetpack Compose components (Material 3)
- `core/navigation` - Navigation routing
- `core/extension` - Extension loading infrastructure
- `core/tachiyomi-compat` - Tachiyomi extension compatibility

### Module Dependency Rules

**IMPORTANT**: Modules must follow dependency hierarchy:
- Presentation depends on → Domain
- Domain depends on → Nothing (pure Kotlin)
- Data depends on → Domain
- Feature modules depend on → Core modules + Domain
- **NEVER** create circular dependencies

## Code Patterns & Conventions

### MVI Pattern Implementation

Every feature module should follow this exact structure:

```kotlin
// 1. State - Immutable data class representing UI state
data class FeatureState(
    val isLoading: Boolean = false,
    val data: List<Item> = emptyList(),
    val error: String? = null,
    val selectedItems: Set<Long> = emptySet()
)

// 2. Event - Sealed class for user actions
sealed class FeatureEvent {
    data object Refresh : FeatureEvent()
    data class OnItemClick(val itemId: Long) : FeatureEvent()
    data class OnItemLongClick(val itemId: Long) : FeatureEvent()
    data class OnSearchQuery(val query: String) : FeatureEvent()
}

// 3. Effect - Sealed class for one-time side effects
sealed class FeatureEffect {
    data class NavigateToDetail(val itemId: Long) : FeatureEffect()
    data class ShowError(val message: String) : FeatureEffect()
    data class ShowSuccess(val message: String) : FeatureEffect()
}

// 4. ViewModel - State management with Hilt
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val useCase: FeatureUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(FeatureState())
    val state: StateFlow<FeatureState> = _state.asStateFlow()

    private val _effect = Channel<FeatureEffect>()
    val effect: Flow<FeatureEffect> = _effect.receiveAsFlow()

    fun onEvent(event: FeatureEvent) {
        when (event) {
            is FeatureEvent.Refresh -> refresh()
            is FeatureEvent.OnItemClick -> handleItemClick(event.itemId)
            // Handle other events
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            useCase()
                .onSuccess { data ->
                    _state.update { it.copy(isLoading = false, data = data) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
        }
    }
}

// 5. Screen - Composable with lifecycle-aware state collection
@Composable
fun FeatureScreen(
    viewModel: FeatureViewModel = hiltViewModel(),
    onNavigateToDetail: (Long) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Collect one-time effects
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is FeatureEffect.NavigateToDetail -> onNavigateToDetail(effect.itemId)
                is FeatureEffect.ShowError -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    FeatureContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun FeatureContent(
    state: FeatureState,
    onEvent: (FeatureEvent) -> Unit
) {
    // UI implementation
}
```

### Use Case Pattern

Use cases encapsulate single business operations:

```kotlin
class GetFeatureDataUseCase @Inject constructor(
    private val repository: FeatureRepository
) {
    operator fun invoke(): Flow<List<FeatureData>> {
        return repository.getData()
    }
}

// For suspend functions
class PerformFeatureActionUseCase @Inject constructor(
    private val repository: FeatureRepository
) {
    suspend operator fun invoke(param: String): Result<Unit> {
        return try {
            repository.performAction(param)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### Repository Pattern

**Domain layer** (interface):
```kotlin
// domain/src/main/java/app/otakureader/domain/repository/FeatureRepository.kt
interface FeatureRepository {
    fun getData(): Flow<List<FeatureData>>
    suspend fun performAction(param: String)
}
```

**Data layer** (implementation):
```kotlin
// data/src/main/java/app/otakureader/data/repository/FeatureRepositoryImpl.kt
class FeatureRepositoryImpl @Inject constructor(
    private val dao: FeatureDao,
    private val api: FeatureApi,
    @ApplicationContext private val context: Context
) : FeatureRepository {

    override fun getData(): Flow<List<FeatureData>> {
        return dao.getAll().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun performAction(param: String) {
        withContext(Dispatchers.IO) {
            dao.insert(FeatureEntity(param))
        }
    }
}
```

### Hilt Dependency Injection

**Application class**:
```kotlin
@HiltAndroidApp
class OtakuReaderApplication : Application()
```

**ViewModels**:
```kotlin
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val useCase: GetDataUseCase
) : ViewModel()
```

**Module for repository binding**:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFeatureRepository(
        impl: FeatureRepositoryImpl
    ): FeatureRepository
}
```

**Module for providing instances**:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.example.com")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
```

### Room Database

**Current version**: 10 (with migrations from v2 → v3 → v4 → v5 → v6 → v7 → v8 → v9 → v10)

**Entity example**:
```kotlin
@Entity(
    tableName = "manga",
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["title"]),
        Index(value = ["favorite"]),
        Index(value = ["sourceId", "url"], unique = true)
    ]
)
data class MangaEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceId: Long,
    val url: String,
    val title: String,
    val author: String?,
    val artist: String?,
    val description: String?,
    val genres: List<String>,
    val status: Int,
    val thumbnailUrl: String?,
    val favorite: Boolean,
    val lastUpdate: Long,
    val initialized: Boolean
)
```

**DAO example**:
```kotlin
@Dao
interface MangaDao {
    @Query("SELECT * FROM manga WHERE favorite = 1 ORDER BY title ASC")
    fun getFavorites(): Flow<List<MangaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(manga: MangaEntity): Long

    @Update
    suspend fun update(manga: MangaEntity)

    @Delete
    suspend fun delete(manga: MangaEntity)

    @Transaction
    @Query("SELECT * FROM manga WHERE id = :mangaId")
    suspend fun getMangaWithChapters(mangaId: Long): MangaWithChapters?
}
```

**Important**: When batching `UPDATE ... WHERE id IN (:ids)` queries, chunk IDs to ≤997 to stay under SQLite's 999 bind-parameter limit.

### DataStore Preferences

Use DataStore for type-safe preferences:

```kotlin
class FeaturePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    val isFeatureEnabled: Flow<Boolean> = dataStore.data
        .map { it[IS_FEATURE_ENABLED] ?: false }

    suspend fun setFeatureEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[IS_FEATURE_ENABLED] = enabled
        }
    }

    companion object {
        private val IS_FEATURE_ENABLED = booleanPreferencesKey("is_feature_enabled")
    }
}
```

### Jetpack Compose UI

**Material 3 theming**:
```kotlin
@Composable
fun OtakuReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

**Reusable components** (use from `core/ui` module):
- Use `OtakuReaderTheme` for theming
- Use Material 3 components (`Button`, `Card`, `TextField`, etc.)
- Follow Material Design 3 guidelines
- Use `Modifier` for styling
- Prefer `LazyColumn`/`LazyRow` for lists
- Use `collectAsStateWithLifecycle()` for Flow collection in Composables

### Navigation

**Define routes**:
```kotlin
// core/navigation or feature/navigation
sealed class FeatureRoute(val route: String) {
    data object List : FeatureRoute("feature_list")
    data object Detail : FeatureRoute("feature_detail/{itemId}") {
        fun createRoute(itemId: Long) = "feature_detail/$itemId"
    }
}
```

**Navigation graph**:
```kotlin
fun NavGraphBuilder.featureNavigation(
    onNavigateToDetail: (Long) -> Unit
) {
    composable(FeatureRoute.List.route) {
        FeatureListScreen(
            onNavigateToDetail = onNavigateToDetail
        )
    }

    composable(
        route = FeatureRoute.Detail.route,
        arguments = listOf(
            navArgument("itemId") { type = NavType.LongType }
        )
    ) { backStackEntry ->
        val itemId = backStackEntry.arguments?.getLong("itemId") ?: return@composable
        FeatureDetailScreen(itemId = itemId)
    }
}
```

## Naming Conventions

### Kotlin Style

- **Classes**: PascalCase (e.g., `MangaRepository`, `ChapterEntity`)
- **Functions**: camelCase (e.g., `getManga()`, `updateChapter()`)
- **Properties**: camelCase (e.g., `isLoading`, `mangaList`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_RETRY_COUNT`)
- **Packages**: lowercase (e.g., `app.otakureader.feature.library`)

### File Naming

- **Screens**: `FeatureScreen.kt` (e.g., `LibraryScreen.kt`)
- **ViewModels**: `FeatureViewModel.kt` (e.g., `LibraryViewModel.kt`)
- **MVI Contracts**: `FeatureMvi.kt` (e.g., `LibraryMvi.kt`)
- **Use Cases**: `VerbNounUseCase.kt` (e.g., `GetLibraryMangaUseCase.kt`)
- **Repositories**: `FeatureRepository.kt` (interface), `FeatureRepositoryImpl.kt` (implementation)
- **Entities**: `FeatureEntity.kt` (e.g., `MangaEntity.kt`)
- **DAOs**: `FeatureDao.kt` (e.g., `MangaDao.kt`)
- **Navigation**: `FeatureNavigation.kt` (e.g., `LibraryNavigation.kt`)

### Package Structure

```
app.otakureader.feature.{feature}/
├── {Feature}Screen.kt
├── {Feature}ViewModel.kt
├── {Feature}Mvi.kt
├── components/              # Feature-specific components
│   └── {Component}.kt
└── navigation/
    └── {Feature}Navigation.kt

app.otakureader.domain.{feature}/
├── model/
│   └── {Model}.kt
├── repository/
│   └── {Feature}Repository.kt
└── usecase/
    └── {Verb}{Noun}UseCase.kt

app.otakureader.data.{feature}/
└── repository/
    └── {Feature}RepositoryImpl.kt
```

## Common Patterns & Best Practices

### State Management

1. **Use StateFlow for state**:
   ```kotlin
   private val _state = MutableStateFlow(InitialState())
   val state: StateFlow<State> = _state.asStateFlow()
   ```

2. **Update state immutably**:
   ```kotlin
   _state.update { it.copy(isLoading = true) }
   ```

3. **Use Channel for one-time effects**:
   ```kotlin
   private val _effect = Channel<Effect>()
   val effect: Flow<Effect> = _effect.receiveAsFlow()

   // Send effect
   _effect.send(Effect.ShowError("Error message"))
   ```

### Error Handling

1. **Use Result for operations that can fail**:
   ```kotlin
   suspend fun performAction(): Result<Data> {
       return try {
           val data = fetchData()
           Result.success(data)
       } catch (e: Exception) {
           Result.failure(e)
       }
   }
   ```

2. **Handle errors in ViewModel**:
   ```kotlin
   useCase()
       .onSuccess { data ->
           _state.update { it.copy(data = data, error = null) }
       }
       .onFailure { error ->
           _state.update { it.copy(error = error.message) }
       }
   ```

### Coroutines

1. **Use viewModelScope in ViewModels**:
   ```kotlin
   fun loadData() {
       viewModelScope.launch {
           // Coroutine work
       }
   }
   ```

2. **Use appropriate dispatchers**:
   ```kotlin
   withContext(Dispatchers.IO) {
       // I/O work (network, database)
   }

   withContext(Dispatchers.Default) {
       // CPU-intensive work
   }
   ```

3. **Collect flows with lifecycle awareness in Composables**:
   ```kotlin
   val state by viewModel.state.collectAsStateWithLifecycle()
   ```

### Resource Management

1. **Use string resources for user-facing text**:
   ```kotlin
   Text(text = stringResource(R.string.feature_title))
   ```

2. **Use dimension resources for sizes**:
   ```kotlin
   Spacer(modifier = Modifier.height(dimensionResource(R.dimen.spacing_medium)))
   ```

3. **Use color resources from Material Theme**:
   ```kotlin
   Text(color = MaterialTheme.colorScheme.primary)
   ```

### Security Best Practices

1. **HTTP logging only in debug builds**:
   ```kotlin
   if (BuildConfig.DEBUG) {
       client.addInterceptor(HttpLoggingInterceptor())
   }
   ```

2. **Sanitize file paths**:
   ```kotlin
   // Replace invalid characters: /\:*?"<>|
   val sanitized = fileName.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim()
   ```

3. **Validate user input before database operations**:
   ```kotlin
   require(input.isNotBlank()) { "Input cannot be blank" }
   ```

## Build Configuration

### Gradle Convention Plugins

Use convention plugins from `/build-logic/convention/`:

```kotlin
// feature module build.gradle.kts
plugins {
    alias(libs.plugins.otakureader.android.feature)
    alias(libs.plugins.otakureader.android.library.compose)
    alias(libs.plugins.otakureader.android.hilt)
}
```

Available convention plugins:
- `otakureader.android.application` - Application module
- `otakureader.android.library` - Library module
- `otakureader.android.feature` - Feature module
- `otakureader.android.library.compose` - Compose library
- `otakureader.android.hilt` - Hilt configuration
- `otakureader.android.room` - Room database

### Dependencies

Use version catalog from `/gradle/libs.versions.toml`:

```kotlin
dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
```

## Testing

### Unit Tests

Test ViewModels and Use Cases:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class FeatureViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when refresh event, then state updates with data`() = runTest {
        // Given
        val useCase = mockk<GetDataUseCase>()
        coEvery { useCase() } returns flowOf(listOf(testData))
        val viewModel = FeatureViewModel(useCase)

        // When
        viewModel.onEvent(FeatureEvent.Refresh)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assertEquals(listOf(testData), viewModel.state.value.data)
        assertFalse(viewModel.state.value.isLoading)
    }
}
```

### UI Tests

Test Composables with UI tests:

```kotlin
@RunWith(AndroidJUnit4::class)
class FeatureScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun featureScreen_displaysData() {
        // Given
        val testState = FeatureState(data = listOf(testItem))

        // When
        composeTestRule.setContent {
            FeatureContent(
                state = testState,
                onEvent = {}
            )
        }

        // Then
        composeTestRule.onNodeWithText(testItem.title).assertIsDisplayed()
    }
}
```

## Commit Message Format

Follow conventional commits:

```
type(scope): subject

body (optional)

footer (optional)
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `style`: Formatting (no code change)
- `refactor`: Code restructuring
- `test`: Tests
- `chore`: Build/config
- `perf`: Performance improvement

**Examples**:
```
feat(reader): Add dual-page spread mode

fix(library): Correct unread badge count calculation

docs: Update architecture documentation

refactor(download): Extract download logic to separate use case
```

## Code Review Checklist

When generating code, ensure:

- [ ] Follows Clean Architecture (correct layer dependencies)
- [ ] Uses MVI pattern for features (State, Event, Effect)
- [ ] ViewModels are annotated with `@HiltViewModel`
- [ ] Repository interfaces in domain layer
- [ ] Repository implementations in data layer
- [ ] Use cases for business logic
- [ ] StateFlow for state, Channel for one-time effects
- [ ] Immutable state updates with `.copy()`
- [ ] Proper error handling with `Result` or try-catch
- [ ] Coroutines use appropriate dispatchers
- [ ] String resources for user-facing text
- [ ] Material 3 components in Compose UI
- [ ] Lifecycle-aware state collection (`collectAsStateWithLifecycle()`)
- [ ] No hardcoded values (use resources or constants)
- [ ] Proper null safety
- [ ] Descriptive naming following conventions
- [ ] Comments for complex logic
- [ ] No circular module dependencies

## Common Modules Reference

### Frequently Used Classes

**Domain Models**:
- `Manga` - Domain model for manga
- `Chapter` - Domain model for chapter
- `MangaSource` - Domain model for source
- `Category` - Domain model for category
- `History` - Domain model for reading history

**Preferences**:
- `GeneralPreferences` - General app settings (theme, dynamic color, pure black mode)
- `LibraryPreferences` - Library-specific settings
- `ReaderPreferences` - Reader-specific settings (mode, zoom, tap zones)
- `DownloadPreferences` - Download settings (auto-download, delete-after-reading)

**Download**:
- `DownloadManager` - Manages download queue with LinkedHashMap (insertion-order)
- `DownloadProvider` - Provides download storage, extracts CBZ, sanitizes paths
- CBZ files stored in `externalFilesDir/OtakuReader/{sourceId}/{mangaId}/{chapterId}.cbz`
- Loose images stored in `externalFilesDir/OtakuReader/{sourceId}/{mangaId}/{chapterId}/`

**Backup/Restore**:
- `BackupCreator` - Creates backup JSON with manga, categories, preferences
- `BackupRestorer` - Restores from backup JSON
- **Note**: DownloadPreferences and theme settings (colorScheme, usePureBlackDarkMode) are NOT backed up

**Database**:
- Current version: 5
- Migrations: v2 → v3 → v4 → v5 (MIGRATION_4_5 adds indexes)
- Use `fallbackToDestructiveMigration()` during development only

## Performance Considerations

1. **Use LazyColumn/LazyRow for long lists** (not Column/Row with scrolling)
2. **Minimize recomposition** with `remember` and `derivedStateOf`
3. **Use keys in LazyColumn items** for stable identity
4. **Database queries**: Add indexes for frequently queried columns
5. **Chunk batch operations** to stay under SQLite's 999 bind parameter limit (chunk to ≤997 for safety)
6. **Use Flow for reactive data** instead of polling
7. **Use WorkManager** for background tasks, not foreground services
8. **Image loading**: Use Coil with proper sizing and caching

## Extension Development

For writing manga source extensions, refer to `/API.md` for comprehensive guide.

Key points:
- Extend `HttpSource` for HTTP-based sources
- Implement required methods: `fetchPopularManga`, `fetchSearchManga`, `fetchMangaDetails`, `fetchChapterList`, `fetchPageList`
- Use `Filter` classes for search filters
- Return `MangaPage` for paginated results
- Use OkHttp client from `network` parameter

## Additional Resources

- **ARCHITECTURE.md**: Detailed architecture documentation
- **FEATURES.md**: Feature-by-feature implementation guide
- **API.md**: Extension API documentation
- **CONTRIBUTING.md**: Contribution guidelines
- **PERFORMANCE_IMPROVEMENTS.md**: Performance optimization techniques

## Summary

When generating code for Otaku Reader:
1. Follow Clean Architecture strictly (Domain is pure Kotlin, no Android deps)
2. Use MVI pattern for features (State, Event, Effect)
3. Use Hilt for dependency injection
4. Use StateFlow for reactive state, Channel for one-time effects
5. Use Jetpack Compose with Material 3
6. Follow naming conventions and package structure
7. Write clear commit messages (conventional commits)
8. Test ViewModels and Use Cases
9. Use appropriate Gradle convention plugins
10. Refer to existing code for patterns and consistency

Happy coding with GitHub Copilot! 🚀
