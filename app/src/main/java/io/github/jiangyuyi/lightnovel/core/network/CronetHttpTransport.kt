package io.github.jiangyuyi.lightnovel.core.network

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.ExperimentalCronetEngine
import org.chromium.net.UploadDataProviders
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class HttpResponse(
    val code: Int,
    val body: String,
    val protocol: String,
)

internal data class HttpBytesResponse(
    val code: Int,
    val body: ByteArray,
    val protocol: String,
)

internal interface HttpTransport {
    suspend fun postJson(url: String, body: String, retryConnections: Boolean = true): HttpResponse
    suspend fun getBytes(url: String): HttpBytesResponse
}

internal class CronetHttpTransport(context: Context) : HttpTransport {
    private val applicationContext = context.applicationContext
    private val executor: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "lightnovel-cronet").apply { isDaemon = true }
    }

    private val engines = mutableMapOf<NetworkRoute, CronetEngine>()

    private fun createEngine(route: NetworkRoute): CronetEngine =
        ExperimentalCronetEngine.Builder(applicationContext).run {
        enableQuic(true)
        enableHttp2(true)
        enableBrotli(true)
        // Mainland TCP routes to these hosts are unreliable on some networks. Try
        // QUIC immediately, but let system DNS follow the site's current CDN first.
        addQuicHint("www.lightnovel.fun", 443, 443)
        addQuicHint("api.lightnovel.fun", 443, 443)
        addQuicHint("res.lightnovel.fun", 443, 443)
        // These legacy Cloudflare routes are fallbacks only. The site currently
        // resolves through another CDN, so forcing them for every request causes
        // image TLS handshakes to be reset while the same URL works in a browser.
        route.address?.let { address ->
            setExperimentalOptions(
                """{"HostResolverRules":{"host_resolver_rules":"MAP www.lightnovel.fun $address, MAP api.lightnovel.fun $address, MAP res.lightnovel.fun $address"}}""",
            )
        }
        build()
    }

    @Synchronized
    private fun engineFor(route: NetworkRoute): CronetEngine =
        engines.getOrPut(route) { createEngine(route) }

    override suspend fun postJson(url: String, body: String, retryConnections: Boolean): HttpResponse {
        val response = request(url, "POST", body.toByteArray(Charsets.UTF_8), retryConnections)
        return HttpResponse(response.code, response.body.toString(Charsets.UTF_8), response.protocol)
    }

    override suspend fun getBytes(url: String): HttpBytesResponse = request(url, "GET", null)

    private suspend fun request(url: String, method: String, upload: ByteArray?, retryConnections: Boolean = true): HttpBytesResponse {
        var lastFailure: IOException? = null
        NETWORK_ROUTES.forEachIndexed { index, route ->
            try {
                return executeWithTimeout(engineFor(route), url, method, upload).also {
                    if (index > 0) {
                        Log.i(TAG, "${Uri.parse(url).host} connected through ${route.label}")
                    }
                }
            } catch (failure: IOException) {
                lastFailure = failure
                if (!retryConnections || index == NETWORK_ROUTES.lastIndex || !failure.isRetryableConnectionFailure()) {
                    throw failure
                }
                Log.w(
                    TAG,
                    "${Uri.parse(url).host} failed through ${route.label}; trying fallback",
                    failure,
                )
            }
        }
        throw lastFailure ?: IOException("网络连接失败")
    }

    private suspend fun executeWithTimeout(
        requestEngine: CronetEngine,
        url: String,
        method: String,
        upload: ByteArray?,
    ): HttpBytesResponse = try {
        withTimeout(ROUTE_TIMEOUT_MS) {
            executeOnce(requestEngine, url, method, upload)
        }
    } catch (failure: TimeoutCancellationException) {
        throw RouteTimeoutException(failure)
    }

    private suspend fun executeOnce(
        requestEngine: CronetEngine,
        url: String,
        method: String,
        upload: ByteArray?,
    ): HttpBytesResponse =
        suspendCancellableCoroutine { continuation ->
            val responseBytes = ByteArrayOutputStream()
            val readBuffer = ByteBuffer.allocateDirect(32 * 1024)

            val callback = object : UrlRequest.Callback() {
                override fun onRedirectReceived(
                    request: UrlRequest,
                    info: UrlResponseInfo,
                    newLocationUrl: String,
                ) = request.followRedirect()

                override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                    request.read(readBuffer)
                }

                override fun onReadCompleted(
                    request: UrlRequest,
                    info: UrlResponseInfo,
                    byteBuffer: ByteBuffer,
                ) {
                    byteBuffer.flip()
                    val chunk = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(chunk)
                    responseBytes.write(chunk)
                    byteBuffer.clear()
                    request.read(byteBuffer)
                }

                override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                    if (continuation.isActive) {
                        continuation.resume(
                            HttpBytesResponse(
                                code = info.httpStatusCode,
                                body = responseBytes.toByteArray(),
                                protocol = info.negotiatedProtocol,
                            ),
                        )
                    }
                }

                override fun onFailed(
                    request: UrlRequest,
                    info: UrlResponseInfo?,
                    error: CronetException,
                ) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            IOException("网络连接失败：${error.message}", error),
                        )
                    }
                }

                override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException("网络请求已取消"))
                    }
                }
            }

            val builder = requestEngine.newUrlRequestBuilder(url, callback, executor)
                .setHttpMethod(method)
                .addHeader("Accept", if (method == "GET") "image/*,*/*;q=0.8" else "application/json")
                .addHeader("Origin", "https://www.lightnovel.fun")
                .addHeader("Referer", "https://www.lightnovel.fun/")
                .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
            if (upload != null) {
                builder.addHeader("Content-Type", "application/json; charset=utf-8")
                    .setUploadDataProvider(UploadDataProviders.create(upload), executor)
            }
            val request = builder.build()

            continuation.invokeOnCancellation { request.cancel() }
            request.start()
        }

    private fun IOException.isRetryableConnectionFailure(): Boolean {
        if (this is RouteTimeoutException) return true
        val cronetError = cause as? CronetException ?: return false
        return RETRYABLE_NETWORK_ERRORS.any(cronetError.message.orEmpty()::contains)
    }

    private companion object {
        const val TAG = "LightNovelNetwork"
        const val ROUTE_TIMEOUT_MS = 12_000L

        val NETWORK_ROUTES = listOf(
            NetworkRoute("system DNS"),
            NetworkRoute("legacy Cloudflare 1", "104.26.6.43"),
            NetworkRoute("legacy Cloudflare 2", "104.26.7.43"),
            NetworkRoute("legacy Cloudflare 3", "172.67.73.171"),
        )

        val RETRYABLE_NETWORK_ERRORS = listOf(
            "ERR_CONNECTION_RESET",
            "ERR_QUIC_PROTOCOL_ERROR",
            "ERR_TIMED_OUT",
            "ERR_NAME_NOT_RESOLVED",
            "ERR_ADDRESS_UNREACHABLE",
            "ERR_NETWORK_CHANGED",
        )
    }
}

private data class NetworkRoute(
    val label: String,
    val address: String? = null,
)

private class RouteTimeoutException(cause: Throwable) : IOException("网络请求超时", cause)
