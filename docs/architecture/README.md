# Otaku Reader - Architecture Documentation

This document provides a comprehensive overview of the Otaku Reader application architecture.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Clean Architecture](#clean-architecture)
- [Module Structure](#module-structure)
- [Data Flow](#data-flow)
- [State Management](#state-management)
- [Navigation](#navigation)
- [Dependency Injection](#dependency-injection)
- [Module Dependency Graph](#module-dependency-graph)

## Architecture Overview

Otaku Reader follows **Clean Architecture** principles combined with **MVVM** (Model-View-ViewModel) and **MVI** (Model-View-Intent) patterns. The architecture is designed to be:

- **Testable**: Clear separation of concerns enables unit testing
- **Maintainable**: Modular structure makes changes easier
- **Scalable**: New features can be added without affecting existing code
- **Flexible**: Easy to swap implementations (e.g., database, API)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           PRESENTATION LAYER                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐ │
│  │    Screen    │  │   ViewModel  │  │   Contract   │  │    UI State │ │
│  │  (Compose)   │  │   (StateFlow)│  │  (UI Events) │  │  (UiState)  │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                            DOMAIN LAYER                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐ │
│  │   Use Case   │  │Domain Model  │  │  Repository  │  │    Result   │ │
│  │  (Business)  │  │  (Pure Data) │  │  (Interface) │  │   (Sealed)  │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                             DATA LAYER                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐ │
│  │ Repository   │  │   Database   │  │    API/      │  │  DataStore  │ │
│  │Implementation│  │    (Room)    │  │   Source     │  │ (Prefs)     │ │
│  └──────────────┘  └──────────────┘  └──────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────────────────────────┘
```

## Clean Architecture

### Layer Responsibilities

#### 1. Presentation Layer

The presentation layer contains all UI-related code and is responsible for:

- **Screens**: Composable functions that render UI
- **ViewModels**: Manage UI state and handle user interactions
- **Contracts**: Define UI events and states (MVI pattern)
- **Components**: Reusable UI components

```kotlin
// Example: Library Screen with MVI pattern
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    LibraryContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}
```

#### 2. Domain Layer

The domain layer contains business logic and is independent of Android framework:

- **Use Cases**: Encapsulate single business operations
- **Models**: Pure Kotlin data classes representing domain entities
- **Repository Interfaces**: Define data access contracts
- **Enums**: Domain-specific enumerations

```kotlin
// Example: Use Case
class GetLibraryMangaUseCase @Inject constructor(
    private val mangaRepository: MangaRepository
) {
    operator fun invoke(categoryId: Long? = null): Flow<List<LibraryManga>> {
        return mangaRepository.getLibraryManga(categoryId)
    }
}
```

#### 3. Data Layer

The data layer handles data operations and implements repository interfaces:

- **Repository Implementations**: Concrete data access implementations
- **Database**: Room database entities, DAOs, and migrations
- **Network**: API clients and source implementations
- **Preferences**: DataStore for user preferences

```kotlin
// Example: Repository Implementation
class MangaRepositoryImpl @Inject constructor(
    private val mangaDao: MangaDao,
    private val mangaApi: MangaApi,
    private val preferences: PreferencesDataSource
) : MangaRepository {
    // Implementation
}
```

## Module Structure

### Module Overview

```
otaku-reader/
├── app/                    # Application entry point
├── core/                   # Shared core modules
│   ├── ai/                # Gemini AI client & recommendation models
│   ├── common/            # Utilities and extensions
│   ├── database/          # Room database (v9) with migrations
│   ├── discord/           # Discord Rich Presence service
│   ├── extension/         # Extension loading & validation
│   ├── navigation/        # Navigation components
│   ├── network/           # Retrofit + OkHttp networking
│   ├── preferences/       # DataStore preferences & IncognitoManager
│   ├── tachiyomi-compat/  # Legacy Tachiyomi extension support
│   └── ui/                # Shared UI components (Material 3)
├── domain/                # Domain layer (pure Kotlin)
├── data/                  # Data layer implementations
│   ├── backup/            # Backup & restore
│   ├── download/          # Download manager & provider
│   ├── loader/            # Page loader (local-first)
│   ├── repository/        # Repository implementations
│   ├── sync/              # Cloud sync providers & manager
│   └── worker/            # WorkManager background jobs
├── feature/               # Feature modules
│   ├── about/             # About screen
│   ├── browse/            # Browse manga & extensions
│   ├── details/           # Manga details & chapter list
│   ├── history/           # Reading history
│   ├── library/           # Library management
│   ├── migration/         # Source migration
│   ├── onboarding/        # First-run onboarding
│   ├── opds/              # OPDS catalog browser
│   ├── reader/            # Manga reader (Single/Dual/Webtoon/Smart Panels)
│   ├── settings/          # App settings
│   ├── statistics/        # Reading statistics & charts
│   ├── tracking/          # Tracker integration (MAL, AniList, Kitsu…)
│   └── updates/           # Updates & downloads
├── source-api/            # Extension API contracts
└── baselineprofile/       # Baseline profile for startup optimization
```

### Module Dependencies

```
app
├── feature:*
│   ├── domain
│   ├── core:ui
│   ├── core:navigation
│   └── data (indirect via domain)
├── core:navigation
├── core:notifications
└── source-api

data
├── domain
├── core:database
├── core:preferences
└── source-api

domain
└── (no dependencies - pure Kotlin)

core:*
└── (varies by module)
```

## Data Flow

### Unidirectional Data Flow

Otaku Reader follows a unidirectional data flow pattern:

```
User Action → ViewModel → Use Case → Repository → Data Source
                                              ↓
UI ← StateFlow ← ViewModel ← Use Case ← Repository
```

### Data Flow Example: Library Screen

```
1. User opens Library screen
   ↓
2. ViewModel calls GetLibraryMangaUseCase
   ↓
3. Use Case calls MangaRepository.getLibraryManga()
   ↓
4. Repository fetches from Database (Room)
   ↓
5. Data flows back through the chain as Flow
   ↓
6. ViewModel updates StateFlow
   ↓
7. UI recomposes with new state
```

### Code Example

```kotlin
// 1. UI Event
sealed class LibraryEvent {
    data class OnMangaClick(val mangaId: Long) : LibraryEvent()
    data class OnCategorySelected(val categoryId: Long) : LibraryEvent()
    data object OnRefresh : LibraryEvent()
}

// 2. ViewModel
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibraryMangaUseCase: GetLibraryMangaUseCase,
    private val updateMangaFavoriteUseCase: UpdateMangaFavoriteUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.OnMangaClick -> navigateToDetails(event.mangaId)
            is LibraryEvent.OnCategorySelected -> loadCategory(event.categoryId)
            is LibraryEvent.OnRefresh -> refreshLibrary()
        }
    }

    private fun loadCategory(categoryId: Long) {
        getLibraryMangaUseCase(categoryId)
            .onEach { mangaList ->
                _state.update { it.copy(manga = mangaList) }
            }
            .launchIn(viewModelScope)
    }
}

// 3. Use Case
class GetLibraryMangaUseCase @Inject constructor(
    private val mangaRepository: MangaRepository
) {
    operator fun invoke(categoryId: Long? = null): Flow<List<LibraryManga>> {
        return mangaRepository.getLibraryManga(categoryId)
    }
}

// 4. Repository Interface (Domain)
interface MangaRepository {
    fun getLibraryManga(categoryId: Long?): Flow<List<LibraryManga>>
    suspend fun updateFavorite(mangaId: Long, isFavorite: Boolean)
}

// 5. Repository Implementation (Data)
class MangaRepositoryImpl @Inject constructor(
    private val mangaDao: MangaDao,
    private val mapper: MangaMapper
) : MangaRepository {
    
    override fun getLibraryManga(categoryId: Long?): Flow<List<LibraryManga>> {
        return mangaDao.getLibraryManga(categoryId)
            .map { entities -> entities.map(mapper::toDomain) }
    }
}
```

## State Management

### MVI Pattern

Otaku Reader uses the MVI (Model-View-Intent) pattern for state management:

```kotlin
// Contract: Defines UI State and Events
data class LibraryUiState(
    val isLoading: Boolean = false,
    val manga: List<LibraryManga> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategoryId: Long? = null,
    val error: String? = null
)

sealed class LibraryEvent {
    data class OnMangaClick(val mangaId: Long) : LibraryEvent()
    data class OnCategorySelected(val categoryId: Long) : LibraryEvent()
    data object OnRefresh : LibraryEvent()
}
```

### StateFlow for Reactive State

```kotlin
class LibraryViewModel @Inject constructor(
    private val getLibraryMangaUseCase: GetLibraryMangaUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryUiState()
        )

    init {
        loadLibrary()
    }

    private fun loadLibrary() {
        getLibraryMangaUseCase()
            .onStart { _state.update { it.copy(isLoading = true) } }
            .onEach { manga ->
                _state.update { 
                    it.copy(
                        isLoading = false,
                        manga = manga
                    )
                }
            }
            .catch { error ->
                _state.update { 
                    it.copy(
                        isLoading = false,
                        error = error.message
                    )
                }
            }
            .launchIn(viewModelScope)
    }
}
```

### UI State Handling

```kotlin
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    LibraryContent(
        state = state,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onEvent: (LibraryEvent) -> Unit
) {
    when {
        state.isLoading -> LoadingIndicator()
        state.error != null -> ErrorMessage(state.error)
        state.manga.isEmpty() -> EmptyLibrary()
        else -> LibraryGrid(
            manga = state.manga,
            onMangaClick = { onEvent(LibraryEvent.OnMangaClick(it.id)) }
        )
    }
}
```

## Navigation

### Navigation Structure

Otaku Reader uses Navigation Compose with type-safe navigation:

```kotlin
// Navigation Routes
sealed class Screen(val route: String) {
    data object Library : Screen("library")
    data object Updates : Screen("updates")
    data object Browse : Screen("browse")
    data object Search : Screen("search")
    data object Settings : Screen("settings")
    data object Stats : Screen("stats")
    data object MangaDetails : Screen("manga/{mangaId}")
    data object Reader : Screen("reader/{chapterId}")
}
```

### Navigation Graph

```kotlin
@Composable
fun OtakuReaderNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route,
        modifier = modifier
    ) {
        // Library
        composable(Screen.Library.route) {
            LibraryScreen(
                onMangaClick = { mangaId ->
                    navController.navigate("manga/$mangaId")
                }
            )
        }
        
        // Manga Details
        composable(
            route = Screen.MangaDetails.route,
            arguments = listOf(
                navArgument("mangaId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val mangaId = backStackEntry.arguments?.getLong("mangaId") ?: return@composable
            MangaDetailsScreen(
                mangaId = mangaId,
                onChapterClick = { chapterId ->
                    navController.navigate("reader/$chapterId")
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        
        // Reader
        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("chapterId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val chapterId = backStackEntry.arguments?.getLong("chapterId") ?: return@composable
            ReaderScreen(
                chapterId = chapterId,
                onBackClick = { navController.popBackStack() }
            )
        }
        
        // Other screens...
    }
}
```

### Bottom Navigation

```kotlin
@Composable
fun OtakuReaderBottomBar(
    navController: NavHostController,
    currentDestination: NavDestination?
) {
    val items = listOf(
        BottomNavItem.Library,
        BottomNavItem.Updates,
        BottomNavItem.Browse,
        BottomNavItem.Settings
    )
    
    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentDestination?.route == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
```

## Dependency Injection

### Hilt Setup

Otaku Reader uses Hilt for dependency injection:

```kotlin
// Application class
@HiltAndroidApp
class OtakuReaderApplication : Application()

// Module definition
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "komikku.db"
        ).build()
    }
    
    @Provides
    fun provideMangaDao(database: AppDatabase): MangaDao {
        return database.mangaDao()
    }
}

// Repository binding
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    abstract fun bindMangaRepository(
        impl: MangaRepositoryImpl
    ): MangaRepository
}
```

### ViewModel Injection

```kotlin
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibraryMangaUseCase: GetLibraryMangaUseCase,
    private val categoryRepository: CategoryRepository
) : ViewModel() {
    // ViewModel implementation
}
```

## Module Dependency Graph

```
                              ┌─────────────┐
                              │     app     │
                              └──────┬──────┘
                                     │
         ┌───────────────────────────┼───────────────────────────┐
         │                           │                           │
         ▼                           ▼                           ▼
┌─────────────────┐      ┌─────────────────┐      ┌─────────────────┐
│   feature:*     │      │  core:navigation│      │  source-api     │
└────────┬────────┘      └─────────────────┘      └─────────────────┘
         │
    ┌────┴────┬───────────────┬───────────────┬───────────────┐
    │         │               │               │               │
    ▼         ▼               ▼               ▼               ▼
┌───────┐ ┌─────────┐  ┌───────────┐  ┌─────────────┐  ┌───────────┐
│domain │ │core:ui  │  │core:common│  │core:database│  │data       │
└───┬───┘ └─────────┘  └───────────┘  └──────┬──────┘  └─────┬─────┘
    │                                         │               │
    │         ┌───────────────────────────────┘               │
    │         │                                               │
    └─────────┴───────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  source-api     │
                    └─────────────────┘
```

### Dependency Rules

1. **Domain Layer**: No dependencies on other layers
2. **Data Layer**: Depends only on Domain
3. **Presentation Layer**: Depends on Domain and Core modules
4. **Core Modules**: Can depend on each other but not on Feature modules
5. **Feature Modules**: Can depend on Domain, Core, and other Feature modules

## Best Practices

### Do's

- ✅ Use Use Cases for business logic
- ✅ Keep ViewModels thin, delegate to Use Cases
- ✅ Use StateFlow for reactive UI state
- ✅ Follow unidirectional data flow
- ✅ Write unit tests for Use Cases and ViewModels
- ✅ Use dependency injection
- ✅ Keep domain models pure (no Android dependencies)

### Don'ts

- ❌ Don't put business logic in ViewModels
- ❌ Don't access database directly from UI
- ❌ Don't use Android classes in domain layer
- ❌ Don't create circular dependencies
- ❌ Don't expose mutable state from ViewModels
- ❌ Don't use callbacks, prefer Flow/StateFlow

---

For more information, see:
- [Feature Documentation](FEATURES.md)
- [Extension API](API.md)
- [Contributing Guidelines](CONTRIBUTING.md)
