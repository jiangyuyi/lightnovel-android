package io.github.jiangyuyi.lightnovel.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderContentParserTest {
    @Test
    fun `server html replaces legacy res tag with illustration in reading order`() {
        val blocks = ReaderContentParser.parse(
            bodyHtml = """
                <p class="ln-paragraph--indent">八奈见害羞地捂着脸。</p>
                <p><img src="https://api.lightnovel.fun/upload-files/images/a.jpg?m=signed&amp;t=1" img-width="1443" img-height="2048" /></p>
                <p class="ln-paragraph--indent">额，也就是说……？</p>
            """.trimIndent(),
            bodyText = "八奈见害羞地捂着脸。\n[res]0,151372[/res]\n额，也就是说……？",
        )

        assertEquals(3, blocks.size)
        assertEquals("八奈见害羞地捂着脸。", (blocks[0] as ReaderBlock.Paragraph).text)
        val image = blocks[1] as ReaderBlock.Illustration
        assertEquals("https://api.lightnovel.fun/upload-files/images/a.jpg?m=signed&t=1", image.url)
        assertEquals(1443, image.width)
        assertEquals(2048, image.height)
        assertEquals("额，也就是说……？", (blocks[2] as ReaderBlock.Paragraph).text)
    }

    @Test
    fun `text fallback never exposes raw res markup`() {
        val blocks = ReaderContentParser.parse("", "前文\n[res]0,151372[/res]\n后文")
        val visible = blocks.filterIsInstance<ReaderBlock.Paragraph>().joinToString("\n") { it.text }

        assertFalse(visible.contains("[res]"))
        assertTrue(visible.contains("插图暂时无法加载"))
    }

    @Test
    fun `html entities and numeric code points are decoded`() {
        val blocks = ReaderContentParser.parse("<p>A&amp;B &#x3002;</p>", "")

        assertEquals("A&B 。", (blocks.single() as ReaderBlock.Paragraph).text)
    }

    @Test
    fun `html anchors keep their label and normalized url`() {
        val blocks = ReaderContentParser.parse(
            "<p>下载：<a href=\"/download/book.epub?key=1&amp;t=2\">点击下载 EPUB</a></p>",
            "",
        )

        assertEquals("下载：", (blocks[0] as ReaderBlock.Paragraph).text)
        val link = blocks[1] as ReaderBlock.Link
        assertEquals("点击下载 EPUB", link.text)
        assertEquals("https://www.lightnovel.fun/download/book.epub?key=1&t=2", link.url)
    }

    @Test
    fun `dangerous schemes are discarded instead of becoming reader links`() {
        val blocks = ReaderContentParser.parse("<p><a href=\"javascript:alert(1)\">领取下载</a></p>", "")

        assertTrue(blocks.none { it is ReaderBlock.Link })
    }

    @Test
    fun `plain text urls become clickable blocks`() {
        val blocks = ReaderContentParser.parse("", "网盘：https://pan.baidu.com/s/demo")

        assertEquals("网盘：", (blocks[0] as ReaderBlock.Paragraph).text)
        assertEquals("https://pan.baidu.com/s/demo", (blocks[1] as ReaderBlock.Link).url)
    }
}
