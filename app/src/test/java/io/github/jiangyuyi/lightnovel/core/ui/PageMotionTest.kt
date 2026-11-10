package io.github.jiangyuyi.lightnovel.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PageMotionTest {
    @Test
    fun initialCompositionDoesNotAnimateFromMissingDestination() {
        assertEquals(PageMotion.NONE, pageMotion(null, "discover", false))
        assertEquals(PageMotion.NONE, pageMotion("discover", null, true))
    }

    @Test
    fun allTopLevelPairsUseNeutralFadeIncludingRestoredBackStack() {
        val routes = listOf("discover", "bookshelf", "search", "profile")
        for (from in routes) for (to in routes) for (pop in listOf(false, true)) {
            assertEquals("$from -> $to, pop=$pop", PageMotion.FADE, pageMotion(from, to, pop))
        }
    }

    @Test
    fun secondaryPagesMoveForwardAndReverseOnBack() {
        for (route in listOf("book/{bookId}", "auth", "social/{mode}", "history", "publishing", "messages", "dm/{peerUid}/{nickname}")) {
            assertEquals(PageMotion.FORWARD, pageMotion("profile", route, false))
            assertEquals(PageMotion.BACKWARD, pageMotion(route, "profile", true))
        }
        assertEquals(PageMotion.FORWARD, pageMotion("messages", "dm/{peerUid}/{nickname}", false))
        assertEquals(PageMotion.BACKWARD, pageMotion("dm/{peerUid}/{nickname}", "messages", true))
    }

    @Test
    fun readerEntryAndExitNeverSlideText() {
        for (route in listOf("book/{bookId}", "history", "messages", "reader/{bookId}/{chapterId}")) {
            for (pop in listOf(false, true)) {
                assertEquals(PageMotion.FADE, pageMotion(route, "reader/{bookId}/{chapterId}", pop))
                assertEquals(PageMotion.FADE, pageMotion("reader/{bookId}/{chapterId}", route, pop))
            }
        }
    }

    @Test
    fun repeatedDestinationUsesFadeRatherThanFalseDepthChange() {
        assertEquals(PageMotion.FADE, pageMotion("book/{bookId}", "book/{bookId}", false))
    }
}
