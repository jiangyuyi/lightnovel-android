package io.github.jiangyuyi.lightnovel.core.cache

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqliteCacheStore(
    context: Context,
    private val maxBytes: Long = 96L * 1024L * 1024L,
) : CacheStore {
    private val helper = CacheDatabase(context.applicationContext)
    private val memory = ConcurrentHashMap<String, SerializedCacheEntry>()

    override suspend fun read(scope: String, key: String, allowDisk: Boolean): SerializedCacheEntry? {
        val memoryKey = memoryKey(scope, key)
        memory[memoryKey]?.let { return it }
        if (!allowDisk) return null
        return withContext(Dispatchers.IO) {
            val database = helper.readableDatabase
            database.query(
                TABLE,
                arrayOf(COLUMN_PAYLOAD, COLUMN_SAVED_AT),
                "$COLUMN_SCOPE = ? AND $COLUMN_KEY = ?",
                arrayOf(scope, key),
                null,
                null,
                null,
                "1",
            ).use { cursor ->
                if (!cursor.moveToFirst()) return@withContext null
                val entry = SerializedCacheEntry(
                    payload = cursor.getString(0),
                    savedAtMillis = cursor.getLong(1),
                )
                memory[memoryKey] = entry
                helper.writableDatabase.update(
                    TABLE,
                    ContentValues().apply { put(COLUMN_LAST_ACCESS, System.currentTimeMillis()) },
                    "$COLUMN_SCOPE = ? AND $COLUMN_KEY = ?",
                    arrayOf(scope, key),
                )
                entry
            }
        }
    }

    override suspend fun write(
        scope: String,
        key: String,
        entry: SerializedCacheEntry,
        allowDisk: Boolean,
    ) {
        memory[memoryKey(scope, key)] = entry
        if (!allowDisk) return
        withContext(Dispatchers.IO) {
            val bytes = entry.payload.toByteArray(Charsets.UTF_8).size.toLong()
            helper.writableDatabase.insertWithOnConflict(
                TABLE,
                null,
                ContentValues().apply {
                    put(COLUMN_SCOPE, scope)
                    put(COLUMN_KEY, key)
                    put(COLUMN_PAYLOAD, entry.payload)
                    put(COLUMN_SAVED_AT, entry.savedAtMillis)
                    put(COLUMN_LAST_ACCESS, entry.savedAtMillis)
                    put(COLUMN_BYTES, bytes)
                },
                SQLiteDatabase.CONFLICT_REPLACE,
            )
            pruneIfNeeded(helper.writableDatabase)
        }
    }

    override suspend fun remove(scope: String, key: String) {
        memory.remove(memoryKey(scope, key))
        withContext(Dispatchers.IO) {
            helper.writableDatabase.delete(
                TABLE,
                "$COLUMN_SCOPE = ? AND $COLUMN_KEY = ?",
                arrayOf(scope, key),
            )
        }
    }

    override suspend fun removePrefix(scope: String, keyPrefix: String) {
        memory.keys.removeIf { it.startsWith(memoryKey(scope, keyPrefix)) }
        withContext(Dispatchers.IO) {
            helper.writableDatabase.delete(
                TABLE,
                "$COLUMN_SCOPE = ? AND $COLUMN_KEY LIKE ?",
                arrayOf(scope, "$keyPrefix%"),
            )
        }
    }

    override suspend fun clearPrivate() {
        memory.keys.removeIf { it.substringBefore('\u0000').startsWith(USER_SCOPE_PREFIX) }
        withContext(Dispatchers.IO) {
            helper.writableDatabase.delete(TABLE, "$COLUMN_SCOPE LIKE ?", arrayOf("$USER_SCOPE_PREFIX%"))
        }
    }

    private fun pruneIfNeeded(database: SQLiteDatabase) {
        val totalBytes = database.rawQuery("SELECT COALESCE(SUM($COLUMN_BYTES), 0) FROM $TABLE", null).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
        if (totalBytes <= maxBytes) return

        val entries = database.query(
            TABLE,
            arrayOf(COLUMN_SCOPE, COLUMN_KEY, COLUMN_BYTES, COLUMN_LAST_ACCESS),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CacheEntrySize(
                            scope = cursor.getString(0),
                            key = cursor.getString(1),
                            bytes = cursor.getLong(2),
                            lastAccessMillis = cursor.getLong(3),
                        ),
                    )
                }
            }
        }
        selectLruEvictions(entries, totalBytes, maxBytes).forEach { entry ->
            database.delete(
                TABLE,
                "$COLUMN_SCOPE = ? AND $COLUMN_KEY = ?",
                arrayOf(entry.scope, entry.key),
            )
            memory.remove(memoryKey(entry.scope, entry.key))
        }
    }

    private fun memoryKey(scope: String, key: String): String = "$scope\u0000$key"

    private class CacheDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE $TABLE (
                    $COLUMN_SCOPE TEXT NOT NULL,
                    $COLUMN_KEY TEXT NOT NULL,
                    $COLUMN_PAYLOAD TEXT NOT NULL,
                    $COLUMN_SAVED_AT INTEGER NOT NULL,
                    $COLUMN_LAST_ACCESS INTEGER NOT NULL,
                    $COLUMN_BYTES INTEGER NOT NULL,
                    PRIMARY KEY ($COLUMN_SCOPE, $COLUMN_KEY)
                )
                """.trimIndent(),
            )
            database.execSQL("CREATE INDEX cache_last_access ON $TABLE ($COLUMN_LAST_ACCESS)")
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            database.execSQL("DROP TABLE IF EXISTS $TABLE")
            onCreate(database)
        }
    }

    companion object {
        private const val DATABASE_NAME = "content_cache.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE = "cache_entries"
        private const val COLUMN_SCOPE = "scope"
        private const val COLUMN_KEY = "entry_key"
        private const val COLUMN_PAYLOAD = "payload"
        private const val COLUMN_SAVED_AT = "saved_at"
        private const val COLUMN_LAST_ACCESS = "last_access"
        private const val COLUMN_BYTES = "byte_size"
        private const val USER_SCOPE_PREFIX = "user:"
    }
}
