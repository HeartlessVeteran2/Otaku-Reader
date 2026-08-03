package app.otakureader.data.tracking.tracker

import app.otakureader.core.preferences.TrackerTokenStore
import app.otakureader.data.tracking.api.AniListApi
import app.otakureader.data.tracking.api.AniListGraphQlQuery
import app.otakureader.data.tracking.di.TrackerCredentials
import app.otakureader.domain.model.TrackEntry
import app.otakureader.domain.model.TrackStatus
import app.otakureader.domain.model.TrackerType
import app.otakureader.domain.tracking.Tracker
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

/**
 * Tracker implementation for [AniList](https://anilist.co/).
 *
 * AniList uses OAuth 2.0 (implicit grant / authorization-code) combined with
 * a GraphQL API.  The authorization code is passed as [password] in [login].
 *
 * AniList status strings map as follows:
 *  - "CURRENT"   → READING
 *  - "COMPLETED" → COMPLETED
 *  - "PAUSED"    → ON_HOLD
 *  - "DROPPED"   → DROPPED
 *  - "PLANNING"  → PLAN_TO_READ
 *  - "REPEATING" → RE_READING
 */
class AniListTracker(
    private val api: AniListApi,
    private val tokenStore: TrackerTokenStore,
    /**
     * Defaults to the build-injected credential; a parameter so tests can supply one.
     *
     * Reading `TrackerCredentials.ANILIST_CLIENT_ID` inside the function made both outcomes
     * untestable in any single build: with an id injected only the configured branch could run,
     * without one only the unconfigured branch, and the skipped half reported as a pass either
     * way. A parameter removes the ambient build state from the question entirely.
     */
    private val clientId: String = TrackerCredentials.ANILIST_CLIENT_ID,
) : Tracker {

    override val id: Int = TrackerType.ANILIST
    override val name: String = "AniList"

    private var accessToken: String? = tokenStore.getTokens(TrackerType.ANILIST)?.accessToken
    // No `currentUserId` here, unlike ShikimoriTracker. Shikimori's user-rate endpoints take a
    // user id as a parameter, so it genuinely needs one; AniList's `Media { mediaListEntry }` and
    // `SaveMediaListEntry` are already scoped to whoever the bearer token belongs to. The field
    // used to exist and was written only by `logout()` — nothing ever read it, and `login()` never
    // populated it, so it was null for the object's whole life. The plan called for adding a
    // `Viewer { id }` query to fill it; that would have fetched a value with no consumer.

    override val isLoggedIn: Boolean
        get() = accessToken != null

    /**
     * The URL to open for login.
     *
     * Without this override the base [Tracker] default returns null and `TrackingViewModel`
     * falls back to a bare `https://anilist.co/api/v2/oauth/authorize` — no `client_id`, no
     * `redirect_uri`, no `response_type` — which AniList rejects outright. Login could not
     * complete at all.
     *
     * `response_type=token` is the **implicit** grant, matching what [login] already expects:
     * it takes a bearer token directly and performs no code-for-token exchange. The
     * authorization-code flow would need a client secret, and a secret shipped inside an APK
     * is not a secret.
     *
     * [codeVerifier] is unused. It exists on the interface for the PKCE trackers (Kitsu, MAL);
     * the implicit grant has no code to protect, so there is nothing to bind it to. Accepting
     * and ignoring it is honest here — inventing a use would imply a protection that is not
     * present.
     *
     * [state] is emphatically *not* unused. The implicit grant hands the access token straight
     * to the redirect URI, so without a state the app would accept any `app.otakureader://
     * anilist-oauth#access_token=…` link anyone can get the device to open — an attacker-chosen
     * account silently substituted for the user's. The state is the only thing tying the token
     * that comes back to the login this app started.
     */
    @Suppress("UnusedParameter")
    override fun authorizationUrl(codeVerifier: String, state: String): String? {
        // Blank means the build had no ANILIST_CLIENT_ID. Null is the interface's "this tracker
        // has no authorization URL", and the caller now treats that as unconfigured rather than
        // substituting a bare endpoint — see TrackingViewModel.initiateLogin.
        if (clientId.isBlank()) return null
        return "https://anilist.co/api/v2/oauth/authorize" +
            "?client_id=$clientId" +
            "&redirect_uri=${TrackerCredentials.ANILIST_REDIRECT_URI}" +
            "&response_type=token" +
            // Percent-encoded rather than interpolated raw. Today's caller generates a UUID, which
            // needs no encoding, so this changes nothing now — but the callback compares the
            // returned value against the stored one for equality, and a state that ever grew a `&`
            // would split into two parameters and fail that comparison forever. DeepLinkHandler
            // decodes with the matching URLDecoder, so the pair round-trips.
            "&state=${URLEncoder.encode(state, StandardCharsets.UTF_8.name())}"
    }

    /** @param password the OAuth bearer token obtained from the AniList implicit flow. */
    override suspend fun login(username: String, password: String): Boolean {
        return try {
            accessToken = password
            tokenStore.saveTokens(trackerId = id, accessToken = password)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            accessToken = null
            false
        }
    }

    override fun logout() {
        accessToken = null
        tokenStore.clearTokens(id)
    }

    override suspend fun search(query: String): List<TrackEntry> {
        val gqlQuery = """
            query (${'$'}search: String) {
              Page { media(search: ${'$'}search, type: MANGA) {
                id title { romaji english } chapters coverImage { large }
              } }
            }
        """.trimIndent()
        val variables = buildJsonObject { put("search", query) }
        val response = api.query(AniListGraphQlQuery(gqlQuery, variables))
        // Same reason as in `update`: a rejected document arrives as HTTP 200, so without this a
        // refused search is indistinguishable from a search that genuinely found nothing, and the
        // user is told "no results" for a query that was never run.
        response.errors.firstOrNull()?.let { throw AniListGraphQlException(it.message) }
        return response.data?.page?.media.orEmpty().map { media ->
            TrackEntry(
                remoteId = media.id,
                mangaId = 0L,
                trackerId = id,
                title = media.title?.english ?: media.title?.romaji ?: "",
                remoteUrl = "https://anilist.co/manga/${media.id}",
                totalChapters = media.chapters ?: 0
            )
        }
    }

    /**
     * Look up a media entry, whether or not the user has it on their list.
     *
     * The list entry used to be required — `media.mediaListEntry ?: return null` — which made this
     * return null for every manga the user had not already added. That is exactly backwards for
     * the flow that matters: you search for something *because* it isn't tracked yet, and the app
     * then cannot resolve the thing it just found. `Media` and `mediaListEntry` are separate
     * concerns, so a missing list entry now means "not tracked yet" (default status, zero
     * progress) rather than "no such manga".
     *
     * `score(format: POINT_100)` is deliberate. Without the argument AniList returns the score in
     * whatever format the *user* picked — 0–10, 0–5 stars, three smileys — so the same number
     * meant different things per account, with nothing on the wire to say which. Naming the format
     * makes the response self-describing, and it removes the need to fetch
     * `mediaListOptions.scoreFormat` separately just to interpret it.
     */
    override suspend fun find(remoteId: Long): TrackEntry? {
        val gqlQuery = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: MANGA) {
                id title { romaji english } chapters
                mediaListEntry { id status score(format: POINT_100) progress }
              }
            }
        """.trimIndent()
        // Int, not a quoted string: the query declares `${'$'}id: Int`.
        val variables = buildJsonObject { put("id", remoteId) }
        return try {
            val response = api.query(AniListGraphQlQuery(gqlQuery, variables))
            val media = response.data?.media ?: return null
            val listEntry = media.mediaListEntry
            TrackEntry(
                remoteId = remoteId,
                mangaId = 0L,
                trackerId = id,
                title = media.title?.english ?: media.title?.romaji ?: "",
                remoteUrl = "https://anilist.co/manga/$remoteId",
                status = listEntry?.let { statusFromAniList(it.status) } ?: TrackStatus.PLAN_TO_READ,
                lastChapterRead = listEntry?.progress?.toFloat() ?: 0f,
                totalChapters = media.chapters ?: 0,
                score = listEntry?.score?.fromAniListPoint100() ?: 0f
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Push [entry] to AniList, throwing if the push does not land.
     *
     * This used to catch every exception and return the input entry unchanged. The interface
     * forbids that in as many words — *"implementations must throw on remote failure rather than
     * silently returning the input entry"* — and the reason is visible at the call sites:
     * `TrackerSyncRepositoryImpl` writes `syncStatus = SYNCED` and a `lastSuccessfulSync` stamp
     * immediately after this returns. Swallowing the failure recorded a successful sync for a
     * request that never reached AniList, and the entry then looked up to date forever. All three
     * call sites already sit inside `catch` blocks that mark `SyncStatus.ERROR`, so throwing is
     * what they were built to receive.
     *
     * `scoreRaw` is typed `Int` here because that is what AniList declares it as, and it is always
     * on the 0–100 scale regardless of the user's display format — which is the whole reason to
     * use it rather than `score`. Declaring it `Float` was the same defect as the string-typed
     * variables fixed in #1232, one line over.
     */
    override suspend fun update(entry: TrackEntry): TrackEntry {
        val gqlMutation = """
            mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus, ${'$'}scoreRaw: Int, ${'$'}progress: Int) {
              SaveMediaListEntry(
                mediaId: ${'$'}mediaId, status: ${'$'}status, scoreRaw: ${'$'}scoreRaw, progress: ${'$'}progress
              ) {
                id status score(format: POINT_100) progress
              }
            }
        """.trimIndent()
        // Each value keeps the type the mutation declares. Sending these as strings is what
        // made every update fail server-side while looking successful here.
        val variables = buildJsonObject {
            put("mediaId", entry.remoteId)
            put("status", statusToAniList(entry.status))
            put("scoreRaw", entry.score.toAniListPoint100())
            put("progress", entry.lastChapterRead.toInt())
        }
        val response = api.query(AniListGraphQlQuery(gqlMutation, variables))
        // Errors first, exactly as in `search`. GraphQL is not all-or-nothing: a response may
        // carry `data` *and* `errors` when one resolver fails and its siblings succeed, so
        // checking errors only where `savedEntry` is null would accept a partial write and
        // persist its half-filled values. Any error at all means this push is not something to
        // report as done.
        response.errors.firstOrNull()?.let { throw AniListGraphQlException(it.message) }
        // No confirmed entry means the mutation did not take, and this must not return normally.
        // GraphQL reports a rejected document with **HTTP 200**, so Retrofit throws nothing and
        // the exception-propagation fix above never fires for this case — an unauthenticated
        // token, a media id AniList doesn't have, a variable it won't accept. Returning `entry`
        // here would have been the very behaviour this change set out to remove, one branch over:
        // the caller writes `syncStatus = SYNCED` on the next line.
        val saved = response.data?.savedEntry
            ?: throw AniListGraphQlException("AniList did not confirm the update for media ${entry.remoteId}")
        // Prefer what AniList confirmed over what was sent. The mutation already asked for these
        // fields and then discarded them, so a value the server clamped or normalised — a score
        // above the user's maximum, progress past the final chapter — was written back locally as
        // whatever this app had guessed.
        return entry.copy(
            // A blank status means the field was absent from the response, not that the entry is
            // unset — mapping "" would run through statusFromAniList's else branch and quietly
            // rewrite the user's status to PLAN_TO_READ.
            status = saved.status.takeIf { it.isNotBlank() }?.let { statusFromAniList(it) } ?: entry.status,
            lastChapterRead = saved.progress.toFloat(),
            score = saved.score.fromAniListPoint100(),
        )
    }

    override fun toTrackStatus(remoteStatus: Int): TrackStatus = TrackStatus.fromOrdinal(remoteStatus)

    override fun toRemoteStatus(status: TrackStatus): Int = status.ordinal

    private fun statusFromAniList(aniListStatus: String): TrackStatus = when (aniListStatus) {
        "CURRENT" -> TrackStatus.READING
        "COMPLETED" -> TrackStatus.COMPLETED
        "PAUSED" -> TrackStatus.ON_HOLD
        "DROPPED" -> TrackStatus.DROPPED
        "PLANNING" -> TrackStatus.PLAN_TO_READ
        "REPEATING" -> TrackStatus.RE_READING
        else -> TrackStatus.PLAN_TO_READ
    }

    private fun statusToAniList(status: TrackStatus): String = when (status) {
        TrackStatus.READING -> "CURRENT"
        TrackStatus.COMPLETED -> "COMPLETED"
        TrackStatus.ON_HOLD -> "PAUSED"
        TrackStatus.DROPPED -> "DROPPED"
        TrackStatus.PLAN_TO_READ -> "PLANNING"
        TrackStatus.RE_READING -> "REPEATING"
    }

    /**
     * Canonical 0–10 score → AniList's 0–100 `scoreRaw`.
     *
     * `roundToInt`, not `toInt`: `0.7f * 10` is 6.9999995, and truncating would file a 0.7 as 6.
     * The clamp keeps a malformed local score from producing a request AniList rejects outright.
     */
    private fun Float.toAniListPoint100(): Int =
        (this * ANILIST_POINTS_PER_SCORE_POINT).roundToInt().coerceIn(ANILIST_MIN_SCORE, ANILIST_MAX_SCORE)

    /** AniList's 0–100 score → canonical 0–10. */
    private fun Float.fromAniListPoint100(): Float = this / ANILIST_POINTS_PER_SCORE_POINT

    private companion object {
        /**
         * How many AniList points make one point of [TrackEntry.score].
         *
         * `TrackEntry.score` has no documented scale, so the one that counts is what the other
         * trackers already store: Kitsu halves its 0–20 `ratingTwenty`, and MAL and Shikimori are
         * natively 0–10. That makes **0–10** the de-facto canonical scale, and AniList's
         * `scoreRaw`/`score(format: POINT_100)` is 0–100.
         *
         * Nothing converted between them. A user who rated something 8 sent `scoreRaw: 8`, which
         * AniList stores as 8/100 — an 0.8 out of 10. Every score written to AniList was a tenth
         * of what the user meant, and every score read back was ten times it.
         */
        const val ANILIST_POINTS_PER_SCORE_POINT = 10f

        /** The bounds AniList accepts for `scoreRaw`; anything outside is rejected. */
        const val ANILIST_MIN_SCORE = 0
        const val ANILIST_MAX_SCORE = 100
    }
}

/**
 * AniList accepted the request but refused the mutation.
 *
 * Distinct from the `IOException` a transport failure produces, because the two want different
 * handling: a dropped connection is worth retrying, a rejected media id never will be. Callers in
 * `TrackerSyncRepositoryImpl` catch `Exception` and mark `SyncStatus.ERROR` either way, so this
 * exists to carry AniList's own message to the log rather than to be caught separately today.
 */
class AniListGraphQlException(message: String) : Exception(message)
