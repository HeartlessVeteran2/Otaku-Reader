package app.komikku.core.common.mvi

/**
 * Marker interface for MVI UI state objects.
 * Implementations should be data classes with sensible defaults.
 */
interface UiState

/**
 * Marker interface for MVI UI events (user actions).
 * Implementations should be sealed interfaces.
 */
interface UiEvent

/**
 * Marker interface for MVI UI effects (one-shot side effects like navigation or snackbars).
 * Implementations should be sealed interfaces.
 */
interface UiEffect
