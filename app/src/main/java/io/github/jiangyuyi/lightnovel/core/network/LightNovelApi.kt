package io.github.jiangyuyi.lightnovel.core.network

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.IOException

class ApiException(
    override val message: String,
    val httpCode: Int? = null,
    val businessCode: Int? = null,
) : IOException(message)

class LightNovelApi internal constructor(
    private val transport: HttpTransport,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    constructor(context: Context) : this(CronetHttpTransport(context))

    internal suspend fun getBytes(url: String): ByteArray {
        val response = transport.getBytes(url)
        if (response.code !in 200..299) throw ApiException("图片服务器返回 ${response.code}", response.code)
        return response.body
    }

    suspend fun post(path: String, body: JsonObject, commentApi: Boolean = false, retryConnections: Boolean = true, welfareApi: Boolean = false): JsonObject =
        run {
            val normalizedPath = path.removePrefix("/")
            val base = if (welfareApi) WELFARE_BASE_URL else if (commentApi) COMMENT_BASE_URL else WEB_BFF_BASE_URL
            val alternateRead = !welfareApi && !commentApi && retryConnections && normalizedPath in ALTERNATE_HOST_READ_PATHS
            val response = try {
                transport.postJson(base + normalizedPath, body.toString(), retryConnections && !alternateRead)
            } catch (failure: IOException) {
                if (!alternateRead) throw failure
                transport.postJson(WELFARE_BASE_URL + normalizedPath, body.toString(), retryConnections = true)
            }
            val raw = response.body
            if (response.code !in 200..299) {
                throw ApiException("服务器返回 ${response.code}", response.code)
            }
            val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
                ?: throw ApiException("服务器返回了无法识别的数据")
            val code = root.int("code")
            if (code != 0) {
                val message = root.string("message", "msg", "error").ifBlank { "请求失败（$code）" }
                throw ApiException(message, response.code, code)
            }
            root.obj("data", "d") ?: buildJsonObject {
                root.forEach { (key, value) -> if (key !in ENVELOPE_KEYS) put(key, value) }
            }
        }

    companion object {
        const val WEB_BFF_BASE_URL = "https://www.lightnovel.fun/api/pc-proxy/"
        const val COMMENT_BASE_URL = "https://api.lightnovel.fun/pc-comment-proxy/"
        const val WELFARE_BASE_URL = "https://api.lightnovel.fun/proxy/"
        private val ENVELOPE_KEYS = setOf("code", "message", "msg", "t")
        // Both official proxy domains carry these read APIs. Only a connection
        // failure can switch domains; business errors and mutations never do.
        private val ALTERNATE_HOST_READ_PATHS = setOf(
            "api/bff/home-feed-v1",
            "api/bff/home-original-feed-v1",
            "api/bff/home-fanfic-feed-v1",
            "api/bff/home-epub-feed-v1",
            "api/bff/home-recent-updates-feed-v1",
            "api/bff/book-rank-list-v1",
            "api/bff/apk-search-taxonomy-v1",
            "api/bff/apk-search-result-v1",
            "api/bff/my-home-v1",
            "api/bff/bookshelf-v1",
            "api/bff/reader-bootstrap-v1",
            "api/new-content-read/get-book-detail",
            "api/new-content-read/get-book-volumes",
            "api/new-content-read/get-volume-chapters",
            "api/new-content-read/get-chapter-detail",
            "api/new-content-read/get-book-library-state",
        )
    }
}

internal fun jsonBody(vararg pairs: Pair<String, Any?>): JsonObject = buildJsonObject {
    pairs.forEach { (key, value) ->
        when (value) {
            null -> Unit
            is String -> put(key, JsonPrimitive(value))
            is Number -> put(key, JsonPrimitive(value))
            is Boolean -> put(key, JsonPrimitive(value))
            is JsonObject -> put(key, value)
            else -> put(key, JsonPrimitive(value.toString()))
        }
    }
}
