package io.github.jiangyuyi.lightnovel.feature.reader

import com.github.houbb.opencc4j.util.ZhConverterUtil
import io.github.jiangyuyi.lightnovel.core.model.ReaderChineseScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class ReaderDisplayContent(
    val bookTitle: String,
    val chapterTitle: String,
    val blocks: List<ReaderBlock>,
)

/** Presentation-only conversion: always start from the source, never overwrite cached chapters. */
internal object ReaderChineseConverter {
    suspend fun convert(source: ReaderDisplayContent, script: ReaderChineseScript): ReaderDisplayContent {
        if (script == ReaderChineseScript.ORIGINAL) return source
        return withContext(Dispatchers.Default) {
            source.copy(
                bookTitle = convertText(source.bookTitle, script),
                chapterTitle = convertText(source.chapterTitle, script),
                blocks = source.blocks.map { block ->
                    ensureActive()
                    when (block) {
                        is ReaderBlock.Heading -> block.copy(text = convertText(block.text, script))
                        is ReaderBlock.Paragraph -> block.copy(text = convertText(block.text, script))
                        is ReaderBlock.Link -> block.copy(
                            // A bare URL is also its label; preserve it verbatim for copying/inspection.
                            text = if (block.text == block.url) block.text else convertText(block.text, script),
                        )
                        is ReaderBlock.Illustration -> block
                    }
                },
            )
        }
    }

    private fun convertText(text: String, script: ReaderChineseScript): String = when {
        text.isEmpty() -> text
        script == ReaderChineseScript.SIMPLIFIED -> ZhConverterUtil.toSimple(text)
        script == ReaderChineseScript.TRADITIONAL -> ZhConverterUtil.toTraditional(text)
        else -> text
    }
}
