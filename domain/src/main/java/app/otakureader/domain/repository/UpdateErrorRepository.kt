package app.otakureader.domain.repository

import app.otakureader.domain.model.UpdateError
import kotlinx.coroutines.flow.Flow

interface UpdateErrorRepository {
    /** Emits the current set of unresolved library-update failures, newest first. */
    fun observeErrors(): Flow<List<UpdateError>>

    /** Records (or replaces) the failure for [mangaId]. */
    suspend fun recordError(mangaId: Long, errorMessage: String)

    /** Clears the failure for [mangaId], e.g. after it next updates successfully or the user dismisses it. */
    suspend fun clearError(mangaId: Long)

    /** Clears every recorded failure. */
    suspend fun clearAllErrors()
}
