package io.github.jiangyuyi.lightnovel.core.cache

import io.github.jiangyuyi.lightnovel.core.model.BookSummary
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedDataSourceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = BookSummary.serializer()

    @Test
    fun freshCacheIsReturnedWithoutNetworkRequest() = runTest {
        val store = FakeCacheStore().apply { putBook("public", "book", oldBook, savedAt = 900) }
        var fetchCount = 0
        val source = CachedDataSource(store, json) { 1_000 }

        val updates = source.updates("public", "book", CachePolicy(200), serializer) {
            fetchCount += 1
            newBook
        }.toList()

        assertEquals(1, updates.size)
        assertEquals(oldBook, updates.single().data)
        assertFalse(updates.single().refreshing)
        assertEquals(0, fetchCount)
    }

    @Test
    fun staleCacheIsShownBeforeNetworkReplacement() = runTest {
        val store = FakeCacheStore().apply { putBook("public", "book", oldBook, savedAt = 700) }
        val source = CachedDataSource(store, json) { 1_000 }

        val updates = source.updates("public", "book", CachePolicy(200), serializer) { newBook }.toList()

        assertEquals(listOf(oldBook, newBook), updates.map { it.data })
        assertTrue(updates.first().refreshing)
        assertEquals(CacheSource.NETWORK, updates.last().source)
        assertFalse(updates.last().refreshing)
    }

    @Test
    fun failedRefreshKeepsStaleContentAndReportsError() = runTest {
        val store = FakeCacheStore().apply { putBook("public", "book", oldBook, savedAt = 700) }
        val source = CachedDataSource(store, json) { 1_000 }

        val updates = source.updates("public", "book", CachePolicy(200), serializer) {
            error("网络不可用")
        }.toList()

        assertEquals(2, updates.size)
        assertEquals(oldBook, updates.last().data)
        assertFalse(updates.last().refreshing)
        assertNotNull(updates.last().error)
        assertEquals("网络不可用", updates.last().error?.message)
    }

    @Test
    fun forceRefreshKeepsFreshContentVisibleUntilReplacement() = runTest {
        val store = FakeCacheStore().apply { putBook("public", "book", oldBook, savedAt = 950) }
        val source = CachedDataSource(store, json) { 1_000 }

        val updates = source.updates(
            "public",
            "book",
            CachePolicy(200),
            serializer,
            forceRefresh = true,
        ) { newBook }.toList()

        assertEquals(listOf(oldBook, newBook), updates.map { it.data })
        assertTrue(updates.first().refreshing)
    }

    @Test
    fun corruptEntryIsRemovedAndReplacedFromNetwork() = runTest {
        val store = FakeCacheStore().apply {
            entries["public\u0000book"] = SerializedCacheEntry("not json", 950)
        }
        val source = CachedDataSource(store, json) { 1_000 }

        val updates = source.updates("public", "book", CachePolicy(200), serializer) { newBook }.toList()

        assertEquals(listOf(newBook), updates.map { it.data })
        assertEquals(newBook, store.book("public", "book"))
    }

    @Test
    fun clearingPrivateScopesDoesNotRemovePublicCache() = runTest {
        val store = FakeCacheStore().apply {
            putBook("public", "book", oldBook, savedAt = 900)
            putBook("user:42", "shelf", newBook, savedAt = 900)
        }
        CachedDataSource(store, json) { 1_000 }.clearPrivate()

        assertEquals(oldBook, store.book("public", "book"))
        assertEquals(null, store.book("user:42", "shelf"))
    }

    private fun FakeCacheStore.putBook(scope: String, key: String, book: BookSummary, savedAt: Long) {
        entries["$scope\u0000$key"] = SerializedCacheEntry(json.encodeToString(serializer, book), savedAt)
    }

    private fun FakeCacheStore.book(scope: String, key: String): BookSummary? =
        entries["$scope\u0000$key"]?.let { json.decodeFromString(serializer, it.payload) }

    private companion object {
        val oldBook = BookSummary(id = 1, title = "旧缓存")
        val newBook = BookSummary(id = 1, title = "网络新内容")
    }
}

private class FakeCacheStore : CacheStore {
    val entries = mutableMapOf<String, SerializedCacheEntry>()

    override suspend fun read(scope: String, key: String, allowDisk: Boolean): SerializedCacheEntry? =
        entries["$scope\u0000$key"]

    override suspend fun write(
        scope: String,
        key: String,
        entry: SerializedCacheEntry,
        allowDisk: Boolean,
    ) {
        entries["$scope\u0000$key"] = entry
    }

    override suspend fun remove(scope: String, key: String) {
        entries.remove("$scope\u0000$key")
    }

    override suspend fun removePrefix(scope: String, keyPrefix: String) {
        entries.keys.removeIf { it.startsWith("$scope\u0000$keyPrefix") }
    }

    override suspend fun clearPrivate() {
        entries.keys.removeIf { it.substringBefore('\u0000').startsWith("user:") }
    }
}
