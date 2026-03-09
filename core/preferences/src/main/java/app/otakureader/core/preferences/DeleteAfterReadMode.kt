package app.otakureader.core.preferences

/**
 * Per-manga override for deleting downloaded chapters after they are read.
 */
enum class DeleteAfterReadMode {
    /** Follow the global setting. */
    INHERIT,
    /** Always delete downloads for this manga after reading. */
    ENABLED,
    /** Never delete downloads for this manga after reading. */
    DISABLED
}
