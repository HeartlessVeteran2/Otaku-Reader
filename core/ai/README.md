# Core AI Module

This module provides AI-powered features for Otaku Reader using Google's Gemini AI.

## Structure

```
core/ai/
├── src/main/java/app/otakureader/core/ai/
│   ├── GeminiClient.kt          # Client for interacting with Gemini API
│   ├── di/
│   │   └── AiModule.kt          # Hilt module for dependency injection
│   └── model/
│       └── AiModels.kt          # Data models for AI requests/responses
```

## Components

### GeminiClient

Wrapper around the Gemini Generative AI SDK. Provides methods to:
- Initialize the client with an API key
- Generate AI-powered content based on text prompts
- Check initialization status

### AiRepository

Domain layer interface defined in `domain/repository/AiRepository.kt` for AI operations.

### AiRepositoryImpl

Implementation in `data/repository/AiRepositoryImpl.kt` that uses `GeminiClient`.

## Usage

### 1. Initialize the Client

The client must be initialized with an API key before use:

```kotlin
@Inject
lateinit var geminiClient: GeminiClient

fun initialize() {
    geminiClient.initialize(apiKey = "your-api-key-here")
}
```

### 2. Generate Content

Use the repository or use case to generate AI content:

```kotlin
@Inject
lateinit var generateAiContentUseCase: GenerateAiContentUseCase

suspend fun generateSummary(prompt: String) {
    generateAiContentUseCase(prompt)
        .onSuccess { content ->
            // Handle generated content
        }
        .onFailure { error ->
            // Handle error
        }
}
```

## Dependencies

### Gemini AI SDK
- **Package**: `com.google.ai.client.generativeai:generativeai:0.9.0`
- **Exposure**: `api` - Types are exposed to consumers (data layer)
- **Purpose**: Generative AI text generation and content understanding

### ML Kit Text Recognition
- **Package**: `com.google.mlkit:text-recognition:16.0.1`
- **Exposure**: `implementation` - Types are internal to this module
- **Purpose**: On-device optical character recognition (OCR) for manga pages
- **Size Impact**: ~15-20 MB (models + native libraries)
- **Runtime Requirements**: Google Play Services ML Kit

### ML Kit Image Labeling
- **Package**: `com.google.mlkit:image-labeling:17.0.9`
- **Exposure**: `implementation` - Types are internal to this module
- **Purpose**: On-device image classification and content detection
- **Size Impact**: ~10-15 MB (models + native libraries)
- **Runtime Requirements**: Google Play Services ML Kit

### Firebase Firestore
- **Package**: `com.google.firebase:firebase-firestore-ktx` (version managed by BoM)
- **Firebase BoM**: `33.11.0` ensures compatible versions across Firebase components
- **Exposure**: `implementation` - Types are internal to this module
- **Purpose**: Cloud storage for AI-related data and user preferences
- **Runtime Requirements**: Google Play Services, internet connectivity

## API Surface Decisions

### Why `api` for Gemini AI SDK?
The `GeminiClient` class exposes types from the Gemini SDK (e.g., `GenerateContentResponse`) in its public API. Consumers in the data layer need access to these types, so we use `api(...)` to transitively expose the dependency.

### Why `implementation` for ML Kit and Firestore?
ML Kit and Firestore types are not exposed through this module's public API. All ML Kit and Firestore operations are encapsulated within internal classes. Using `implementation` keeps the module's API surface minimal and prevents dependency leakage.

**Important**: If future features need to expose ML Kit or Firestore types (e.g., returning `Text` objects from ML Kit), those dependencies should be changed to `api`.

## APK Size Impact

Adding ML Kit and Firebase dependencies increases the APK size:
- **Text Recognition**: ~15-20 MB
- **Image Labeling**: ~10-15 MB
- **Firebase Firestore**: ~5-8 MB
- **Total estimated increase**: ~30-43 MB

### Mitigation Strategies

1. **Dynamic Feature Modules** (Future Enhancement)
   - Move ML Kit features to a separate dynamic feature module
   - Download on-demand when users enable AI features
   - Reduces base APK size for users who don't use AI features

2. **Model Download Options**
   - ML Kit supports downloading models at runtime vs bundling
   - Can be configured in the consuming app module
   - Trade-off: Initial feature use requires network + download time

3. **ProGuard/R8 Optimization**
   - Ensure ProGuard/R8 is enabled in release builds
   - Unused code will be stripped automatically
   - Firebase and ML Kit include their own ProGuard rules

## Runtime Requirements

### Google Play Services
ML Kit and Firebase require Google Play Services. On devices without Play Services (e.g., F-Droid builds):
- ML Kit features will fail at runtime
- Use the `foss` build flavor which excludes this module
- The `core/ai-noop` module provides no-op implementations for FOSS builds

### Permissions
The app module must declare required permissions in `AndroidManifest.xml`:
- `INTERNET` - Required for Firestore and Gemini API
- ML Kit may require `CAMERA`/`READ_EXTERNAL_STORAGE` for certain use cases

### Initialization
Firebase must be initialized in the Application class. This is handled automatically by the Firebase SDK via ContentProvider, but ensure:
- `google-services.json` is present in the `app/` module for full flavor builds
- Firebase initialization happens before using Firestore

## Build Variants

This module is only included in the **full** build flavor:
```kotlin
// In data/build.gradle.kts
fullImplementation(projects.core.ai)
```

The **foss** flavor uses `core/ai-noop` instead, which provides no-op implementations of all AI features.

---

## Original Dependencies Documentation

- `com.google.ai.client.generativeai:generativeai:0.9.0` - Gemini AI SDK
- `core:common` - Common utilities
- Hilt for dependency injection
- Kotlin Coroutines for async operations

## Domain Layer

Use cases are available in `domain/usecase/ai/`:
- `GenerateAiContentUseCase` - Generate AI-powered content

## Integration

The AI module is integrated into the data layer via:
- `RepositoryModule` - Binds `AiRepositoryImpl` to `AiRepository`
- `UseCaseModule` - Provides `GenerateAiContentUseCase`

## Configuration

The API key should be configured in:
- Settings screen (recommended for user-provided keys)
- BuildConfig (for built-in functionality)
- Secure storage via `core:preferences`

## Future Enhancements

Potential features to add:
- Manga recommendations based on reading history
- Chapter summaries
- Genre analysis
- Reading pattern insights
- Translation assistance
- Character analysis
