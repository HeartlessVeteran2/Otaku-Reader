package app.otakureader.core.common.collection

/**
 * A thread-safe, fixed-capacity cache that evicts the least recently *used* entry.
 *
 * `accessOrder = true` on the backing map is the point, not a detail: with insertion order the
 * entries a user is actively working with would be evicted on the same schedule as ones they
 * left behind an hour ago, so the cache would keep discarding exactly what it should keep.
 *
 * Every operation is synchronized because `LinkedHashMap` is not thread-safe and a *read* mutates
 * it here — access-ordering relinks the entry on `get`. That makes the usual "concurrent reads
 * are fine" intuition wrong for this map specifically: two simultaneous `get`s can corrupt it.
 *
 * `ConcurrentHashMap` is not an alternative, because it has no eviction at all — which is the one
 * property this type exists to provide.
 */
class BoundedCache<K : Any, V : Any>(private val maxEntries: Int) {

    init {
        require(maxEntries > 0) { "maxEntries must be positive, was $maxEntries" }
    }

    private val entries = object : LinkedHashMap<K, V>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > maxEntries
    }

    operator fun get(key: K): V? = synchronized(entries) { entries[key] }

    operator fun set(key: K, value: V) {
        synchronized(entries) { entries[key] = value }
    }

    fun putAll(from: Map<K, V>) {
        synchronized(entries) { entries.putAll(from) }
    }

    fun clear() {
        synchronized(entries) { entries.clear() }
    }

    /** Current entry count. Exposed for tests asserting that eviction actually happened. */
    val size: Int get() = synchronized(entries) { entries.size }

    private companion object {
        const val INITIAL_CAPACITY = 16
        const val LOAD_FACTOR = 0.75f
    }
}
