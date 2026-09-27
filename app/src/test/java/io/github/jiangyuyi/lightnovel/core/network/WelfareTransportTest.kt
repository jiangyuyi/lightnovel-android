package io.github.jiangyuyi.lightnovel.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class WelfareTransportTest {
    private class RecordingTransport : HttpTransport {
        var url = ""
        var retries = true
        var body = ""
        val urls = mutableListOf<String>()
        var failWeb = false
        var businessCode = 0
        override suspend fun postJson(url: String, body: String, retryConnections: Boolean): HttpResponse {
            this.url = url
            urls += url
            this.body = body
            retries = retryConnections
            if (failWeb && url.startsWith(LightNovelApi.WEB_BFF_BASE_URL)) throw IOException("connection reset")
            return HttpResponse(200, """{"code":$businessCode,"data":{}}""", "http/1.1")
        }
        override suspend fun getBytes(url: String): HttpBytesResponse = error("unused")
    }

    @Test fun `welfare claim uses official app proxy without retries`() = runTest {
        val transport = RecordingTransport()
        LightNovelApi(transport).post("api/bff/claim-welfare-sign-v1", jsonBody("security_key" to "test-only"), retryConnections = false, welfareApi = true)
        assertEquals("https://api.lightnovel.fun/proxy/api/bff/claim-welfare-sign-v1", transport.url)
        assertFalse(transport.retries)
        assertTrue(transport.body.contains("test-only"))
    }

    @Test fun `welfare reads retain safe connection fallback`() = runTest {
        val transport = RecordingTransport()
        LightNovelApi(transport).post("api/bff/welfare-home-v1", jsonBody(), welfareApi = true)
        assertTrue(transport.retries)
    }

    @Test fun `normal api and comment routes remain unchanged`() = runTest {
        val transport = RecordingTransport()
        val api = LightNovelApi(transport)
        api.post("api/bff/my-home-v1", jsonBody())
        assertTrue(transport.url.startsWith(LightNovelApi.WEB_BFF_BASE_URL))
        api.post("test", jsonBody(), commentApi = true)
        assertEquals(LightNovelApi.COMMENT_BASE_URL + "test", transport.url)
    }

    @Test fun `read can fall back to the official app domain without static address`() = runTest {
        val transport = RecordingTransport().apply { failWeb = true }
        LightNovelApi(transport).post("api/bff/home-feed-v1", jsonBody("page" to 1))
        assertEquals(listOf(
            LightNovelApi.WEB_BFF_BASE_URL + "api/bff/home-feed-v1",
            LightNovelApi.WELFARE_BASE_URL + "api/bff/home-feed-v1",
        ), transport.urls)
        assertTrue(transport.retries)
    }

    @Test fun `mutation never switches to an alternate domain`() = runTest {
        val transport = RecordingTransport().apply { failWeb = true }
        try {
            LightNovelApi(transport).post("api/new-content-read/toggle-book-shelf", jsonBody())
            fail("expected connection failure")
        } catch (_: IOException) { }
        assertEquals(1, transport.urls.size)
    }

    @Test fun `business error does not trigger host fallback`() = runTest {
        val transport = RecordingTransport().apply { businessCode = 8 }
        try {
            LightNovelApi(transport).post("api/bff/home-feed-v1", jsonBody())
            fail("expected business error")
        } catch (e: ApiException) {
            assertEquals(8, e.businessCode)
        }
        assertEquals(1, transport.urls.size)
    }
}
