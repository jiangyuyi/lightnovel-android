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

    private val engines = mutableMapOf<NetworkProtocol, CronetEngine>()
    private val connectionPreferences = NetworkConnectionPreferences()

    private fun createEngine(protocol: NetworkProtocol): CronetEngine =
        ExperimentalCronetEngine.Builder(applicationContext).run {
        // Every engine follows system DNS and the site's current CDN. Fallbacks
        // change transport protocol, never map a hostname to a static address.
        enableQuic(protocol == NetworkProtocol.AUTO)
        enableHttp2(protocol != NetworkProtocol.HTTP1)
        enableBrotli(true)
        // Mainland TCP routes to these hosts are unreliable on some networks. Try
        // QUIC immediately, but let system DNS follow the site's current CDN first.
        if (protocol == NetworkProtocol.AUTO) {
            addQuicHint("www.lightnovel.fun", 443, 443)
            addQuicHint("api.lightnovel.fun", 443, 443)
            addQuicHint("res.lightnovel.fun", 443, 443)
        }
        build()
    }

    @Synchronized
    private fun engineFor(protocol: NetworkProtocol): CronetEngine =
        engines.getOrPut(protocol) { createEngine(protocol) }

    override suspend fun postJson(url: String, body: String, retryConnections: Boolean): HttpResponse {
        val response = request(url, "POST", body.toByteArray(Charsets.UTF_8), retryConnections)
        return HttpResponse(response.code, response.body.toString(Charsets.UTF_8), response.protocol)
    }

    override suspend fun getBytes(url: String): HttpBytesResponse = request(url, "GET", null)

    private suspend fun request(url: String, method: String, upload: ByteArray?, retryConnections: Boolean = true): HttpBytesResponse {
        var lastFailure: IOException? = null
        val host = Uri.parse(url).host.orEmpty()
        val protocols = connectionPreferences.attemptsFor(host, retryConnections)
        protocols.forEachIndexed { index, protocol ->
            try {
                return executeWithTimeout(engineFor(protocol), url, method, upload).also {
                    connectionPreferences.recordSuccess(host, protocol)
                    if (index > 0) {
                        Log.i(TAG, "$host connected through $protocol using system DNS")
                    }
                }
            } catch (failure: IOException) {
                connectionPreferences.recordFailure(host, protocol)
                lastFailure = failure
                if (!retryConnections || index == protocols.lastIndex || !failure.isRetryableConnectionFailure()) {
                    throw failure
                }
                Log.w(
                    TAG,
                    "$host failed through $protocol; trying another connection using system DNS",
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

private class RouteTimeoutException(cause: Throwable) : IOException("网络请求超时", cause)
