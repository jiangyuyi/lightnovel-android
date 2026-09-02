package io.github.jiangyuyi.lightnovel.feature.reader

import io.github.jiangyuyi.lightnovel.core.model.ChapterDetail
import io.github.jiangyuyi.lightnovel.core.model.ChapterSummary

internal const val INACCESSIBLE_CHAPTER_MESSAGE = "没有权限访问该章节，或内容已删除"

internal fun ChapterSummary.requiresCoinUnlock(): Boolean =
    accessType.equals("coin", ignoreCase = true) && unlocked != true

internal fun ChapterDetail.readerAccessError(): String? {
    if (chapter.requiresCoinUnlock()) return null
    if (bodyHtml.isNotBlank() || bodyText.isNotBlank()) return null
    return INACCESSIBLE_CHAPTER_MESSAGE
}
