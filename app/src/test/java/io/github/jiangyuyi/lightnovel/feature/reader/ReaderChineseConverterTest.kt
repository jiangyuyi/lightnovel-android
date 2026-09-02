package io.github.jiangyuyi.lightnovel.feature.reader

import io.github.jiangyuyi.lightnovel.core.model.ReaderChineseScript
import io.github.jiangyuyi.lightnovel.core.model.ReaderPreferences
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReaderChineseConverterTest {
    private val traditional = ReaderDisplayContent(
        bookTitle = "輕小說與圖書館",
        chapterTitle = "第一章：閱讀與選擇",
        blocks = listOf(
            ReaderBlock.Heading("第一章：閱讀與選擇"),
            ReaderBlock.Paragraph("這裡有龍與魔法，歡迎閱讀。", firstLineIndent = false),
            ReaderBlock.Illustration("https://example.org/圖書.jpg?簽名=Ab12", 600, 900),
            ReaderBlock.Link("下載圖書", "https://example.org/圖書.epub?簽名=Ab12"),
            ReaderBlock.Link("https://example.org/圖書.epub", "https://example.org/圖書.epub"),
        ),
    )

    @Test fun originalModeReturnsUntouchedContent() = runTest {
        assertSame(traditional, ReaderChineseConverter.convert(traditional, ReaderChineseScript.ORIGINAL))
    }

    @Test fun simplifiesTitlesParagraphsAndLabelsWithoutChangingResources() = runTest {
        val result = ReaderChineseConverter.convert(traditional, ReaderChineseScript.SIMPLIFIED)
        assertEquals("轻小说与图书馆", result.bookTitle)
        assertEquals("第一章：阅读与选择", result.chapterTitle)
        assertEquals(ReaderBlock.Heading("第一章：阅读与选择"), result.blocks[0])
        assertEquals(ReaderBlock.Paragraph("这里有龙与魔法，欢迎阅读。", false), result.blocks[1])
        assertSame(traditional.blocks[2], result.blocks[2])
        assertEquals("下载图书", (result.blocks[3] as ReaderBlock.Link).text)
        assertEquals((traditional.blocks[3] as ReaderBlock.Link).url, (result.blocks[3] as ReaderBlock.Link).url)
        assertEquals(traditional.blocks[4], result.blocks[4])
        assertEquals(traditional.blocks.size, result.blocks.size)
        assertEquals("輕小說與圖書館", traditional.bookTitle)
    }

    @Test fun traditionalConversionUsesPhrasesAndKeepsOtherText() = runTest {
        val source = ReaderDisplayContent("轻小说", "第一章", listOf(
            ReaderBlock.Paragraph("头发与发展，阅读图书。ABC 123 😀\nこんにちは"),
        ))
        val result = ReaderChineseConverter.convert(source, ReaderChineseScript.TRADITIONAL)
        assertEquals("輕小說", result.bookTitle)
        assertEquals("頭髮與發展，閱讀圖書。ABC 123 😀\nこんにちは", (result.blocks.single() as ReaderBlock.Paragraph).text)
        assertSame(source, ReaderChineseConverter.convert(source, ReaderChineseScript.ORIGINAL))
    }

    @Test fun emptyContentIsValidInBothModes() = runTest {
        val empty = ReaderDisplayContent("", "", emptyList())
        assertEquals(empty, ReaderChineseConverter.convert(empty, ReaderChineseScript.SIMPLIFIED))
        assertEquals(empty, ReaderChineseConverter.convert(empty, ReaderChineseScript.TRADITIONAL))
    }

    @Test fun repeatedSwitchesAlwaysStartFromOriginal() = runTest {
        repeat(3) {
            ReaderChineseConverter.convert(traditional, ReaderChineseScript.SIMPLIFIED)
            ReaderChineseConverter.convert(traditional, ReaderChineseScript.TRADITIONAL)
            assertSame(traditional, ReaderChineseConverter.convert(traditional, ReaderChineseScript.ORIGINAL))
        }
    }

    @Test fun oldPreferencesDefaultToOriginalAndNewPreferenceRoundTrips() {
        assertEquals(ReaderChineseScript.ORIGINAL, Json.decodeFromString<ReaderPreferences>("{}").chineseScript)
        ReaderChineseScript.entries.forEach { script ->
            val preferences = ReaderPreferences(chineseScript = script)
            assertEquals(preferences, Json.decodeFromString<ReaderPreferences>(
                Json.encodeToString(ReaderPreferences.serializer(), preferences),
            ))
        }
    }
}
