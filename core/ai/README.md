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
