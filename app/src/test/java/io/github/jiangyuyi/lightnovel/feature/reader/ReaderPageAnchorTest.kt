package io.github.jiangyuyi.lightnovel.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPageAnchorTest {
    private fun page(first: Int, last: Int) = ReaderPage(emptyList(), first, last)

    @Test fun splitParagraphKeepsNearestPageAfterConversion() {
        val pages = listOf(page(0, 5), page(5, 9), page(9, 15))
        assertEquals(1, readerPageForAnchor(pages, blockIndex = 5, currentPage = 1))
    }

    @Test fun movedParagraphFollowsItsNewPage() {
        val pages = listOf(page(0, 3), page(4, 6), page(7, 9))
        assertEquals(2, readerPageForAnchor(pages, blockIndex = 8, currentPage = 1))
    }

    @Test fun fewerPagesStayInBoundsAndDoNotRepresentANextChapter() {
        val pages = listOf(page(0, 3), page(3, 9))
        assertEquals(1, readerPageForAnchor(pages, blockIndex = 8, currentPage = 4))
        assertEquals(1, readerPageForAnchor(pages, blockIndex = 99, currentPage = 4))
        assertEquals(0, readerPageForAnchor(emptyList(), blockIndex = 0, currentPage = 4))
    }
}
