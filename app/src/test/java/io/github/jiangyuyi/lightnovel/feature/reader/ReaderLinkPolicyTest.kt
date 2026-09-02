package io.github.jiangyuyi.lightnovel.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderLinkPolicyTest {
    @Test
    fun `relative epub is classified as direct download`() {
        val target = ReaderLinkPolicy.classify("/files/test.epub", "下载")!!

        assertEquals(ReaderLinkKind.DIRECT_DOWNLOAD, target.kind)
        assertEquals("https://www.lightnovel.fun/files/test.epub", target.url)
        assertEquals("test.epub", target.suggestedFileName)
    }

    @Test
    fun `network disk is opened externally even when label says download`() {
        val target = ReaderLinkPolicy.classify("https://pan.baidu.com/s/demo", "下载")!!

        assertEquals(ReaderLinkKind.EXTERNAL_WEB, target.kind)
    }

    @Test
    fun `lookalike site host is not trusted as internal`() {
        val target = ReaderLinkPolicy.classify("https://lightnovel.fun.example.com/pay", "投币")!!

        assertEquals(ReaderLinkKind.EXTERNAL_WEB, target.kind)
    }

    @Test
    fun `active and local schemes are rejected`() {
        assertNull(ReaderLinkPolicy.classify("javascript:alert(1)"))
        assertNull(ReaderLinkPolicy.classify("file:///sdcard/a.epub"))
    }
}
