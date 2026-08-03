package app.otakureader.core.common.collection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Covers the two properties this type exists for: it stays bounded, and it evicts the right entry.
 *
 * The eviction *order* matters more than the bound. A cache that evicted by insertion order would
 * also stay within its limit and would also pass a naive size assertion — while discarding
 * precisely the entries the user is working with. So the tests below construct the case where the
 * two policies disagree.
 */
class BoundedCacheTest {

    @Test
    fun `entries past the cap are evicted`() {
        val cache = BoundedCache<Int, String>(3)

        repeat(5) { cache[it] = "v$it" }

        assertEquals(3, cache.size)
        // The two oldest are gone; nothing was touched, so oldest means least recently written.
        assertNull(cache[0])
        assertNull(cache[1])
        assertNotNull(cache[4])
    }

    /**
     * The case that separates least-recently-USED from least-recently-INSERTED.
     *
     * Key 0 is the oldest by insertion but the newest by access, so an insertion-ordered cache
     * evicts it and an access-ordered one keeps it. Without the read in the middle this test
     * would pass under either policy and prove nothing.
     */
    @Test
    fun `reading an entry protects it from eviction`() {
        val cache = BoundedCache<Int, String>(3)
        cache[0] = "a"
        cache[1] = "b"
        cache[2] = "c"

        cache[0] // touch the oldest

        cache[3] = "d" // forces one eviction

        assertEquals("a", cache[0])
        // Key 1 is now the least recently used and is the one that goes.
        assertNull(cache[1])
    }

    @Test
    fun `putAll respects the cap`() {
        val cache = BoundedCache<Int, String>(2)

        cache.putAll((0..4).associateWith { "v$it" })

        assertEquals(2, cache.size)
    }

    @Test
    fun `clear empties the cache`() {
        val cache = BoundedCache<Int, String>(4)
        repeat(3) { cache[it] = "v$it" }

        cache.clear()

        assertEquals(0, cache.size)
        assertNull(cache[0])
    }

    @Test
    fun `a non-positive capacity is rejected`() {
        // A zero-capacity cache would evict everything immediately and silently behave as a
        // no-op, which is far harder to notice than a construction failure.
        assertThrows(IllegalArgumentException::class.java) { BoundedCache<Int, String>(0) }
    }

    /**
     * Concurrent access must not corrupt the map.
     *
     * This is not a general "is it thread-safe" gesture: access ordering means a *read* structurally
     * modifies the backing `LinkedHashMap`, so concurrent gets — not just concurrent writes — can
     * corrupt it. That is the exact reason `get` is synchronized, and the reason
     * `ConcurrentHashMap` could not be swapped in for the lock.
     */
    @Test
    fun `concurrent reads and writes stay consistent`() {
        val cache = BoundedCache<Int, String>(64)

        // Real threads, deliberately — not `runTest` with `async`. A coroutine test runs on a
        // single virtual-time dispatcher, so those coroutines would interleave cooperatively and
        // never actually contend. The test would have looked like a concurrency test and proven
        // nothing about it.
        val threads = (0 until 8).map { worker ->
            Thread {
                repeat(2_000) { i ->
                    val key = (worker * 2_000 + i) % 128
                    cache[key] = "v$key"
                    cache[key]
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // The invariant is the bound; which specific keys survive is timing-dependent. Without
        // the lock this typically corrupts the map or loops forever rather than failing here.
        assertEquals(64, cache.size)
    }
}
