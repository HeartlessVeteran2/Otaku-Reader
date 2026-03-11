package app.otakureader.domain.model

/**
 * Shared reader constants used by both the reader and details feature modules.
 * Centralised here in the domain layer so both features stay in sync automatically.
 */
object ReaderDefaults {
    /** Maximum pages to preload before/after the current page. */
    const val MAX_PRELOAD_PAGES = 10

    /** Default pages to preload when no per-manga override is set. */
    const val DEFAULT_PRELOAD_PAGES = 3
}
