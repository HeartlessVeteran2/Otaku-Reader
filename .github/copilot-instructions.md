# Otaku Reader — GitHub Copilot Instructions

## What This Project Is

Otaku Reader is a modern Android manga reader app built from scratch by a solo
developer. It is a clean, maintainable alternative to Mihon/Tachiyomi that
inherits their extension ecosystem without inheriting their legacy architecture.
The app is ~98% complete and in a bug-fixing/stabilization phase.

The developer is newer to Kotlin. Always explain what was wrong and why the fix
works alongside any code changes. Never just drop a solution without context.

## Non-Negotiable Architecture Decision

**Tachiyomi extension compatibility is intentional and must never be broken.**

The app loads and runs existing Tachiyomi/Mihon extensions without modification.
This gives Otaku Reader access to 500+ community-maintained manga sources on day
one. Do not suggest replacing or reimplementing the extension system. Any fixes
in the extension layer must preserve full compatibility with the existing
Tachiyomi extension API and interface contracts.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVI (Model-View-Intent)
- **Dependency Injection:** Hilt
- **Database:** Room
- **Async:** Kotlin Coroutines + Flow
- **Extension System:** Tachiyomi-compatible

## Architecture Rules

### MVI
- State is immutable. Every UI change goes through an Intent → Reducer cycle
- ViewModels expose a single `StateFlow<UiState>` per screen
- Side effects go through a separate `SharedFlow`
- Never mutate state directly
- Never use LiveData

### Hilt
- All ViewModels use `@HiltViewModel`
- Repositories are `@Singleton` scoped unless there is a specific reason otherwise
- UseCases are `@Reusable` or unscoped
- Always verify `@InstallIn` scope matches the injection site
- If a binding is missing, check the module's `@InstallIn` before assuming the ViewModel is at fault

### Room
- Every DAO read function returns `Flow<T>` not a plain value
- Migrations must be explicit — never use `fallbackToDestructiveMigration()` in production
- Entities are separate from domain models — always use mappers

### Jetpack Compose
- Screens consume `UiState` from ViewModel StateFlow via `collectAsState()`
- Composables should be stateless where possible — hoist state up
- Use `LaunchedEffect` for one-time side effects only
- Use `rememberCoroutineScope()` for user-triggered async actions only

### Tachiyomi Extension Interface
- Extensions are loaded dynamically as APKs
- Interface contracts (Source, HttpSource, MangasPage, etc.) must match Tachiyomi exactly
- Never change interface signatures without verifying against Tachiyomi extension API spec
- The Komikku fork in the same GitHub org is a useful reference for this layer

## Common Bug Areas

When reviewing or fixing code, prioritize these areas:

1. **Hilt binding errors** — missing `@Provides`, wrong scope, missing `@InstallIn`
2. **Room DAO disconnects** — DAOs not injected into repos, repos not injected into UseCases
3. **MVI state not updating UI** — StateFlow not collected in Compose, reducer not emitting new state
4. **Extension loader** — ClassLoader issues, missing permissions, interface mismatch
5. **Gradle dependency conflicts** — version mismatches between Compose, Kotlin, and Hilt
6. **Navigation** — missing destinations, incorrect argument types in NavGraph
7. **Coroutine scope leaks** — `GlobalScope` used instead of `viewModelScope` or `lifecycleScope`

## Code Style

- Prefer extension functions over utility classes
- Prefer `sealed class` for UI state and intent modeling
- Keep ViewModels thin — business logic belongs in UseCases
- No hardcoded strings — use resource files
- No magic numbers — use named constants

## What NOT To Do

- Do not implement AI features (Smart Search, Recommendations, etc.) — planned for a later phase
- Do not add Firebase or any analytics/crash tooling unless explicitly asked
- Do not change the Tachiyomi extension interface under any circumstances
- Do not use `GlobalScope`
- Do not use LiveData
- Do not use XML layouts — this is a pure Compose project

## Developer Context

- Solo developer, veteran background, newer to Kotlin
- Multi-agent workflow: Kimi Claw (bulk GitHub tasks), Copilot (day-to-day),
  Gemini Code Assist, Claude (architecture + debugging)
- Has Google Cloud project with Gemini API access for future AI features
- Core stability is the current priority — not new features