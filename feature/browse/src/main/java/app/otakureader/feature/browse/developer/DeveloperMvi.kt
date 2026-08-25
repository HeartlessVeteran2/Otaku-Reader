package app.otakureader.feature.browse.developer

/**
 * A repository URL read from the developer's `dev-repos.txt`.
 *
 * [isAlreadyAdded] is computed against the *normalized* stored URLs rather than the raw strings,
 * because `ExtensionRepoRepositoryImpl` normalizes on write. Comparing raw text would show "Add"
 * beside a repository that is already configured, and tapping it would be a silent no-op — the
 * store is a `Set`, so the add succeeds and changes nothing.
 */
data class DeveloperSeed(
    val url: String,
    val isAlreadyAdded: Boolean,
)

data class DeveloperState(
    val isLoading: Boolean = true,
    val seeds: List<DeveloperSeed> = emptyList(),
    /**
     * Null until the unlock flag has been read once.
     *
     * Tri-state on purpose: the screen must not treat "not yet known" as "locked", or it would pop
     * itself during the first frame every time it opens.
     */
    val isUnlocked: Boolean? = null,
) {
    /** True when the build carries no `dev-repos.txt` — the normal state for a public build. */
    val hasNoSeeds: Boolean get() = !isLoading && seeds.isEmpty()

    val pendingCount: Int get() = seeds.count { !it.isAlreadyAdded }
}

sealed interface DeveloperEvent {
    data object AddAllSeeds : DeveloperEvent

    data class AddSeed(val url: String) : DeveloperEvent

    data object Lock : DeveloperEvent
}

sealed interface DeveloperEffect {
    data class ShowSnackbar(val message: String) : DeveloperEffect
}
