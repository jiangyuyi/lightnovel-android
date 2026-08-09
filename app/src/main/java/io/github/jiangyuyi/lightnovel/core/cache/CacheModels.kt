package io.github.jiangyuyi.lightnovel.core.cache

data class CachePolicy(
    val ttlMillis: Long,
    val disk: Boolean = true,
) {
    fun isFresh(savedAtMillis: Long, nowMillis: Long): Boolean =
        nowMillis - savedAtMillis <= ttlMillis
}

data class SerializedCacheEntry(
    val payload: String,
    val savedAtMillis: Long,
)

enum class CacheSource {
    CACHE,
    NETWORK,
}

data class CacheUpdate<T>(
    val data: T,
    val source: CacheSource,
    val refreshing: Boolean = false,
    val savedAtMillis: Long,
    val error: Throwable? = null,
)

interface CacheStore {
    suspend fun read(scope: String, key: String, allowDisk: Boolean): SerializedCacheEntry?
    suspend fun write(scope: String, key: String, entry: SerializedCacheEntry, allowDisk: Boolean)
    suspend fun remove(scope: String, key: String)
    suspend fun removePrefix(scope: String, keyPrefix: String)
    suspend fun clearPrivate()
}

object CacheScopes {
    const val PUBLIC = "public"

    fun user(uid: Long): String = "user:${uid.coerceAtLeast(0)}"
}

object CachePolicies {
    val DISCOVER = CachePolicy(ttlMillis = 5 * 60_000L)
    val TAXONOMY = CachePolicy(ttlMillis = 24 * 60 * 60_000L)
    val SEARCH = CachePolicy(ttlMillis = 5 * 60_000L)
    val BOOK = CachePolicy(ttlMillis = 30 * 60_000L)
    val CHAPTER = CachePolicy(ttlMillis = 7 * 24 * 60 * 60_000L)
    val USER_FAST = CachePolicy(ttlMillis = 60_000L)
    val USER = CachePolicy(ttlMillis = 2 * 60_000L)
    val MESSAGE = CachePolicy(ttlMillis = 30_000L, disk = false)
}
