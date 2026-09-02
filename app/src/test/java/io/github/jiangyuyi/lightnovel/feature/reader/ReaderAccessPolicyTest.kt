package io.github.jiangyuyi.lightnovel.feature.reader

import io.github.jiangyuyi.lightnovel.core.model.ChapterDetail
import io.github.jiangyuyi.lightnovel.core.model.ChapterSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderAccessPolicyTest {
    @Test
    fun `blank non-coin chapter matches website inaccessible state`() {
        assertEquals(
            INACCESSIBLE_CHAPTER_MESSAGE,
            detail(accessType = "restricted", locked = true).readerAccessError(),
        )
    }

    @Test
    fun `coin chapter remains eligible for unlock prompt`() {
        assertNull(
            detail(accessType = "coin", locked = true, unlocked = false, coinPrice = 20).readerAccessError(),
        )
    }

    @Test
    fun `chapter with body remains readable even when catalog marks it locked`() {
        assertNull(
            detail(accessType = "restricted", locked = true, bodyText = "正文").readerAccessError(),
        )
    }

    private fun detail(
        accessType: String,
        locked: Boolean,
        unlocked: Boolean? = null,
        coinPrice: Int = 0,
        bodyText: String = "",
    ) = ChapterDetail(
        chapter = ChapterSummary(
            id = 259918,
            bookId = 327,
            volumeId = 1,
            title = "测试章节",
            locked = locked,
            accessType = accessType,
            unlocked = unlocked,
            coinPrice = coinPrice,
        ),
        bookTitle = "测试图书",
        volumeTitle = "测试分卷",
        bodyText = bodyText,
    )
}
