package app.otakureader.core.preferences

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keystore-backed encrypted storage for E-Hentai session cookies.
 *
 * Stores ipb_member_id, ipb_pass_hash, and the optional ExHentai igneous token in a
 * dedicated EncryptedSharedPreferences file. The cookies are captured when the user
 * authenticates via the in-app WebView and are later used by [EhFavoritesSyncWorker]
 * to fetch the favorites page without re-prompting for credentials.
 */
@Singleton
class EhSessionStore @Inject constructor(@param:ApplicationContext private val context: Context) {


    // Created via EncryptedPrefsFactory so Keystore corruption recovers instead of
    // crash-looping the app (stored secrets are reset; the user re-authenticates).
    private val prefs: SharedPreferences by lazy {
        EncryptedPrefsFactory.create(context, "eh_session_credentials")
    }

    fun saveSession(memberId: String, passHash: String, igneous: String = "") {
        prefs.edit()
            .putString(KEY_MEMBER_ID, memberId)
            .putString(KEY_PASS_HASH, passHash)
            .putString(KEY_IGNEOUS, igneous)
            .apply()
    }

    fun getSession(): EhSession? {
        val memberId = prefs.getString(KEY_MEMBER_ID, null) ?: return null
        val passHash = prefs.getString(KEY_PASS_HASH, null) ?: return null
        val igneous = prefs.getString(KEY_IGNEOUS, null).orEmpty()
        return EhSession(memberId = memberId, passHash = passHash, igneous = igneous)
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_MEMBER_ID)
            .remove(KEY_PASS_HASH)
            .remove(KEY_IGNEOUS)
            .apply()
    }

    fun isConfigured(): Boolean = prefs.getString(KEY_MEMBER_ID, null) != null

    private companion object {
        const val KEY_MEMBER_ID = "ipb_member_id"
        const val KEY_PASS_HASH = "ipb_pass_hash"
        const val KEY_IGNEOUS = "igneous"
    }
}

data class EhSession(
    val memberId: String,
    val passHash: String,
    /** ExHentai igneous token; empty string if using e-hentai.org instead. */
    val igneous: String,
)
