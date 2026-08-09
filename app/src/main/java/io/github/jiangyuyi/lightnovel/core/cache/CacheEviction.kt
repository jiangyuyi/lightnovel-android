package io.github.jiangyuyi.lightnovel.core.cache

internal data class CacheEntrySize(
    val scope: String,
    val key: String,
    val bytes: Long,
    val lastAccessMillis: Long,
)

internal fun selectLruEvictions(
    entries: List<CacheEntrySize>,
    totalBytes: Long,
    maxBytes: Long,
): List<CacheEntrySize> {
    if (totalBytes <= maxBytes) return emptyList()
    var remaining = totalBytes
    return buildList {
        entries.sortedBy(CacheEntrySize::lastAccessMillis).forEach { entry ->
            if (remaining <= maxBytes) return@forEach
            add(entry)
            remaining -= entry.bytes.coerceAtLeast(0)
        }
    }
}
