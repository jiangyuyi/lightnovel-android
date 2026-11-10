package io.github.jiangyuyi.lightnovel.core.ui

/** Keep top-level switching neutral; directional motion only indicates stack depth. */
internal enum class PageMotion { NONE, FADE, FORWARD, BACKWARD }

internal fun pageMotion(from: String?, to: String?, isPop: Boolean): PageMotion {
    if (from == null || to == null) return PageMotion.NONE
    if (from == to || from.startsWith("reader/") || to.startsWith("reader/")) return PageMotion.FADE
    if (from in topLevelRoutes && to in topLevelRoutes) return PageMotion.FADE
    return if (isPop) PageMotion.BACKWARD else PageMotion.FORWARD
}

private val topLevelRoutes = setOf("discover", "bookshelf", "search", "profile")
