package app.otakureader.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a DataStore read failure does to a setting that is collected exactly once (#1208).
 *
 * `NetworkSettings` subscribes to each of these flows at startup and never re-subscribes, so
 * whatever the flow does on failure is what that setting does for the rest of the process. A flow
 * that merely *ends* — with an exception or with a fallback value — leaves the setting pinned at
 * its startup default while the settings screen still shows what the user chose, and no later
 * write ever arrives.
 *
 * That is the property under test, and it is not the same as "a fallback value is emitted": one
 * emission then completion satisfies the fallback and fails this.
 */
class NetworkPreferencesResilienceTest {

    private val userAgentKey = stringPreferencesKey("network_user_agent")

    /** Throws on first collection and succeeds afterwards, like a transient read error. */
    private class FlakyDataStore(private val recovered: Preferences) : DataStore<Preferences> {
        var attempts = 0

        override val data: Flow<Preferences> = flow {
            attempts++
            if (attempts == 1) throw IOException("corrupt on first read")
            emit(recovered)
        }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw UnsupportedOperationException("not part of this test")
    }

    /**
     * The fallback is necessary but not sufficient. Emitting it and completing is exactly the shape
     * that looks fixed and is not — the collector goes away and the setting never updates again.
     */
    @Test
    fun `a read failure falls back and then delivers the recovered value`() = runTest {
        val store = FlakyDataStore(
            mutablePreferencesOf(userAgentKey to "Recovered/1").toPreferences(),
        )

        val seen = NetworkPreferences(store).userAgent.take(2).toList()

        assertEquals("the fallback, then the real value once the read succeeds", listOf("", "Recovered/1"), seen)
    }

    /** A failure that is not a disk problem is a bug here, and must not be swallowed. */
    @Test(expected = IllegalStateException::class)
    fun `a non-IO failure propagates`() = runTest {
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flow { throw IllegalStateException("bug") }
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences = emptyPreferences()
        }

        NetworkPreferences(store).userAgent.take(1).toList()
    }
}
