package app.otakureader.data.tracking

import app.otakureader.core.preferences.TrackingPreferences
import app.otakureader.data.tracking.api.AniListApi
import app.otakureader.data.tracking.api.MyAnimeListApi
import app.otakureader.domain.model.TrackingService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TrackManager handles authentication and session management for tracking services.
 * Provides helper methods for OAuth flows and token management.
 */
@Singleton
class TrackManager @Inject constructor(
    private val trackingPreferences: TrackingPreferences,
    private val malApi: MyAnimeListApi,
    private val anilistApi: AniListApi
) {

    // --- MyAnimeList ---

    /**
     * Check if user is authenticated with MyAnimeList
     */
    suspend fun isMalAuthenticated(): Boolean {
        val token = trackingPreferences.malAccessToken.first()
        return !token.isNullOrEmpty()
    }

    /**
     * Get MAL username
     */
    suspend fun getMalUsername(): String? {
        return trackingPreferences.malUsername.first()
    }

    /**
     * Get MAL access token (with auto-refresh if needed)
     */
    suspend fun getMalAccessToken(): String? {
        val token = trackingPreferences.malAccessToken.first()
        val expiry = trackingPreferences.malTokenExpiry.first()

        // Check if token is expired
        if (token != null && expiry > 0 && System.currentTimeMillis() >= expiry) {
            // Token expired, try to refresh
            val refreshToken = trackingPreferences.malRefreshToken.first()
            if (refreshToken != null) {
                return try {
                    refreshMalToken(refreshToken)
                } catch (e: Exception) {
                    null
                }
            }
        }

        return token
    }

    /**
     * Complete MAL OAuth flow and store tokens
     */
    suspend fun completeMalOAuth(
        clientId: String,
        authorizationCode: String,
        codeVerifier: String
    ): Result<Unit> {
        return try {
            val response = malApi.getAccessToken(
                clientId = clientId,
                code = authorizationCode,
                codeVerifier = codeVerifier
            )

            trackingPreferences.setMalAccessToken(response.accessToken)
            trackingPreferences.setMalRefreshToken(response.refreshToken)
            trackingPreferences.setMalTokenExpiry(
                System.currentTimeMillis() + (response.expiresIn * 1000)
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Refresh MAL access token
     */
    private suspend fun refreshMalToken(refreshToken: String): String? {
        return try {
            val response = malApi.refreshAccessToken(
                clientId = MAL_CLIENT_ID,
                refreshToken = refreshToken
            )

            trackingPreferences.setMalAccessToken(response.accessToken)
            trackingPreferences.setMalRefreshToken(response.refreshToken)
            trackingPreferences.setMalTokenExpiry(
                System.currentTimeMillis() + (response.expiresIn * 1000)
            )

            response.accessToken
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Logout from MyAnimeList
     */
    suspend fun logoutMal() {
        trackingPreferences.clearMalAuth()
    }

    // --- AniList ---

    /**
     * Check if user is authenticated with AniList
     */
    suspend fun isAnilistAuthenticated(): Boolean {
        val token = trackingPreferences.anilistAccessToken.first()
        return !token.isNullOrEmpty()
    }

    /**
     * Get AniList username
     */
    suspend fun getAnilistUsername(): String? {
        return trackingPreferences.anilistUsername.first()
    }

    /**
     * Get AniList access token
     */
    suspend fun getAnilistAccessToken(): String? {
        val token = trackingPreferences.anilistAccessToken.first()
        val expiry = trackingPreferences.anilistTokenExpiry.first()

        // Check if token is expired (AniList tokens don't auto-refresh)
        if (token != null && expiry > 0 && System.currentTimeMillis() >= expiry) {
            return null
        }

        return token
    }

    /**
     * Complete AniList OAuth flow and store tokens
     */
    suspend fun completeAnilistOAuth(
        clientId: String,
        clientSecret: String,
        authorizationCode: String,
        redirectUri: String
    ): Result<Unit> {
        return try {
            val response = anilistApi.getAccessToken(
                clientId = clientId,
                clientSecret = clientSecret,
                code = authorizationCode,
                redirectUri = redirectUri
            )

            trackingPreferences.setAnilistAccessToken(response.accessToken)
            trackingPreferences.setAnilistTokenExpiry(
                System.currentTimeMillis() + (response.expiresIn * 1000)
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Logout from AniList
     */
    suspend fun logoutAnilist() {
        trackingPreferences.clearAnilistAuth()
    }

    // --- Kitsu ---

    /**
     * Check if user is authenticated with Kitsu
     */
    suspend fun isKitsuAuthenticated(): Boolean {
        val token = trackingPreferences.kitsuAccessToken.first()
        return !token.isNullOrEmpty()
    }

    /**
     * Get Kitsu username
     */
    suspend fun getKitsuUsername(): String? {
        return trackingPreferences.kitsuUsername.first()
    }

    /**
     * Logout from Kitsu
     */
    suspend fun logoutKitsu() {
        trackingPreferences.clearKitsuAuth()
    }

    // --- General ---

    /**
     * Check if a specific service is authenticated
     */
    suspend fun isServiceAuthenticated(service: TrackingService): Boolean {
        return when (service) {
            TrackingService.MYANIMELIST -> isMalAuthenticated()
            TrackingService.ANILIST -> isAnilistAuthenticated()
            TrackingService.KITSU -> isKitsuAuthenticated()
        }
    }

    /**
     * Get username for a specific service
     */
    suspend fun getUsername(service: TrackingService): String? {
        return when (service) {
            TrackingService.MYANIMELIST -> getMalUsername()
            TrackingService.ANILIST -> getAnilistUsername()
            TrackingService.KITSU -> getKitsuUsername()
        }
    }

    /**
     * Logout from a specific service
     */
    suspend fun logout(service: TrackingService) {
        when (service) {
            TrackingService.MYANIMELIST -> logoutMal()
            TrackingService.ANILIST -> logoutAnilist()
            TrackingService.KITSU -> logoutKitsu()
        }
    }

    companion object {
        // These would normally come from BuildConfig or environment variables
        private const val MAL_CLIENT_ID = "YOUR_MAL_CLIENT_ID"
        private const val ANILIST_CLIENT_ID = "YOUR_ANILIST_CLIENT_ID"
        private const val ANILIST_CLIENT_SECRET = "YOUR_ANILIST_CLIENT_SECRET"
    }
}
