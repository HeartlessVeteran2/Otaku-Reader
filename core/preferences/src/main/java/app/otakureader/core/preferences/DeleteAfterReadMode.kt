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

/**
 * Resolves whether a chapter's download should be deleted after it is read, applying the
 * per-manga override's precedence over the global default. Shared by every call site that
 * acts on "delete after reading" (the live in-session save path and the durable WorkManager
 * exit path) so the precedence rule can't drift between them.
 */
fun resolveShouldDeleteAfterRead(overrideMode: DeleteAfterReadMode, globalEnabled: Boolean): Boolean =
    when (overrideMode) {
        DeleteAfterReadMode.ENABLED -> true
        DeleteAfterReadMode.DISABLED -> false
        DeleteAfterReadMode.INHERIT -> globalEnabled
    }
