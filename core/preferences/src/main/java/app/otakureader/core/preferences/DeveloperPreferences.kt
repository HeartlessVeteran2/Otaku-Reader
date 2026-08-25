package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Whether the hidden developer screen has been unlocked on this install.
 *
 * Stored in plain DataStore rather than the encrypted store deliberately. The flag is not a
 * credential — it records that someone already passed the passphrase prompt, and
 * `DeveloperUnlock` is explicit that the gate is obscurity rather than a security boundary.
 * Encrypting a boolean whose plaintext is "true" would imply a protection that is not there.
 */
class DeveloperPreferences(private val dataStore: DataStore<Preferences>) {

    /** True once the passphrase has been entered; survives restarts until explicitly locked. */
    val isUnlocked: Flow<Boolean> = dataStore.data.map { it[Keys.UNLOCKED] ?: false }

    suspend fun setUnlocked(value: Boolean) = dataStore.edit { it[Keys.UNLOCKED] = value }

    private object Keys {
        val UNLOCKED = booleanPreferencesKey("developer_mode_unlocked")
    }
}
