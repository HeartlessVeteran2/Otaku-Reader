# Android & Kotlin Conventions

## Language Rules
- **Kotlin 2.3** with all stable language features enabled
- Prefer `val` over `var`
- Prefer immutability — `List` over `MutableList`, `StateFlow` over mutable state
- Use `sealed class` / `sealed interface` for restricted hierarchies (events, states, results)
- Use `data class` for state representations
- Avoid `!!` — use `?.let`, `?: return`, or explicit null handling

## Compose Patterns
- UI is 100% Jetpack Compose
- No XML layouts except for manifest/config
- Theme defined in `core/ui` — always use theme values, never hardcode colors/sizes
- `MaterialTheme.colorScheme` and `MaterialTheme.typography` for all styling

## State Management
```kotlin
// Good — immutable state, single source of truth
data class ReaderState(
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
)

// ViewModel pattern
class ReaderViewModel @Inject constructor(...) : ViewModel() {
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()
    
    fun onEvent(event: ReaderEvent) {
        when (event) {
            is ReaderEvent.NextPage -> { ... }
            is ReaderEvent.PrevPage -> { ... }
        }
    }
}
```

## Coroutines & Flow
- `viewModelScope` for UI-bound work
- `CoroutineScope` injected via Hilt for background work (downloads, sync)
- `Flow` for data streams; `StateFlow` for UI state
- `async/await` for parallel independent operations (e.g., loading multiple settings)
- Never block the main thread with DataStore reads — batch them:

```kotlin
// Good — parallel DataStore reads
coroutineScope {
    val setting1 = async { dataStore.setting1.first() }
    val setting2 = async { dataStore.setting2.first() }
    // ...
    ReaderSettings(
        setting1 = setting1.await(),
        setting2 = setting2.await(),
    )
}

// Bad — sequential, blocks cold start
val setting1 = dataStore.setting1.first()
val setting2 = dataStore.setting2.first()
```

## Dependency Injection (Hilt)
```kotlin
// Repository binding
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun bindMangaRepository(impl: MangaRepositoryImpl): MangaRepository
}

// ViewModel
@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val getMangaDetails: GetMangaDetailsUseCase,
    private val downloadChapter: DownloadChapterUseCase,
) : ViewModel() { ... }
```

## Performance Rules
- Coil memory cache capped at `min(15%, 256 MB)` — never increase without discussion
- `RGB_565` for opaque images (2x memory reduction)
- `onTrimMemory()` hooked up in `OtakuReaderApplication` to clear Coil cache under pressure
- `SmartPrefetchManager` uses LRU cache with 500-entry hard cap
- `clearCache()` called in `ViewModel.onCleared()`
- Use `contentType` on `LazyColumn`/`LazyVerticalGrid` items for Compose slot reuse

## Error Handling
- Repository layer catches exceptions and returns `Result<T>` or sealed error types
- ViewModel maps errors to user-facing messages
- Never crash on network errors — show retry UI
- Extension loading failures are graceful — mark as `Untrusted` or `Error`, don't crash
