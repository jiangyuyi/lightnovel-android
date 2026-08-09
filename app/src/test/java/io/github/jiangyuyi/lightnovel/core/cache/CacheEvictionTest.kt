package io.github.jiangyuyi.lightnovel.core.cache

import org.junit.Assert.assertEquals
import org.junit.Test

class CacheEvictionTest {
    @Test
    fun oldestEntriesAreSelectedUntilCacheIsWithinLimit() {
        val entries = listOf(
            CacheEntrySize("public", "newest", bytes = 40, lastAccessMillis = 300),
            CacheEntrySize("public", "oldest", bytes = 30, lastAccessMillis = 100),
            CacheEntrySize("user:1", "middle", bytes = 35, lastAccessMillis = 200),
        )

        val evicted = selectLruEvictions(entries, totalBytes = 105, maxBytes = 50)

        assertEquals(listOf("oldest", "middle"), evicted.map { it.key })
    }

    @Test
    fun noEntriesAreSelectedWhenCacheIsWithinLimit() {
        val entries = listOf(CacheEntrySize("public", "book", bytes = 10, lastAccessMillis = 1))

        assertEquals(emptyList<CacheEntrySize>(), selectLruEvictions(entries, totalBytes = 10, maxBytes = 10))
    }
}
