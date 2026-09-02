package io.github.jiangyuyi.lightnovel.feature.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.math.floor
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class ReaderPage(
    val elements: List<ReaderPageElement>,
    val firstBlockIndex: Int,
    val lastBlockIndex: Int,
)

internal fun readerPageForAnchor(pages: List<ReaderPage>, blockIndex: Int, currentPage: Int): Int {
    // A paragraph may span several pages. Prefer the matching page nearest the old
    // position instead of always jumping back to the beginning of that paragraph.
    return pages.indices.filter { blockIndex in pages[it].firstBlockIndex..pages[it].lastBlockIndex }
        .minByOrNull { abs(it - currentPage) }
        ?: pages.indexOfLast { it.firstBlockIndex <= blockIndex }.coerceAtLeast(0)
}

internal sealed interface ReaderPageElement {
    val blockIndex: Int

    data class Text(
        val text: String,
        val heading: Boolean,
        val firstLineIndent: Boolean,
        val linkUrl: String? = null,
        override val blockIndex: Int,
    ) : ReaderPageElement

    data class Illustration(
        val block: ReaderBlock.Illustration,
        val heightPx: Int,
        override val blockIndex: Int,
    ) : ReaderPageElement
}

internal fun paginateReaderBlocks(
    blocks: List<ReaderBlock>,
    textMeasurer: TextMeasurer,
    paragraphStyle: TextStyle,
    headingStyle: TextStyle,
    density: Density,
    pageWidthPx: Int,
    pageHeightPx: Int,
    spacingPx: Int,
): List<ReaderPage> {
    if (pageWidthPx <= 0 || pageHeightPx <= 0) return emptyList()

    val pages = mutableListOf<ReaderPage>()
    var elements = mutableListOf<ReaderPageElement>()
    var remainingHeight = pageHeightPx

    fun finishPage() {
        if (elements.isEmpty()) return
        pages += ReaderPage(
            elements = elements,
            firstBlockIndex = elements.minOf { it.blockIndex },
            lastBlockIndex = elements.maxOf { it.blockIndex },
        )
        elements = mutableListOf()
        remainingHeight = pageHeightPx
    }

    fun reserveSpacing(minimumContentHeight: Int) {
        if (elements.isEmpty()) return
        if (remainingHeight - spacingPx < minimumContentHeight) finishPage()
        else remainingHeight -= spacingPx
    }

    blocks.forEachIndexed { blockIndex, block ->
        when (block) {
            is ReaderBlock.Heading,
            is ReaderBlock.Paragraph,
            is ReaderBlock.Link,
            -> {
                val sourceText = when (block) {
                    is ReaderBlock.Heading -> block.text
                    is ReaderBlock.Paragraph -> block.text
                    is ReaderBlock.Link -> block.text
                    else -> error("unreachable")
                }
                val heading = block is ReaderBlock.Heading
                val shouldIndent = (block as? ReaderBlock.Paragraph)?.firstLineIndent == true
                val baseStyle = if (heading) headingStyle else paragraphStyle
                val lineHeightPx = with(density) { baseStyle.lineHeight.toPx() }.roundToInt().coerceAtLeast(1)
                var offset = 0

                while (offset < sourceText.length) {
                    reserveSpacing(lineHeightPx)
                    if (remainingHeight < lineHeightPx) finishPage()
                    val maxLines = floor(remainingHeight.toDouble() / lineHeightPx).toInt().coerceAtLeast(1)
                    val indentThisFragment = shouldIndent && offset == 0
                    val measuredStyle = if (indentThisFragment) {
                        baseStyle.copy(textIndent = TextIndent(firstLine = baseStyle.fontSize * 2))
                    } else {
                        baseStyle.copy(textIndent = TextIndent.None)
                    }
                    val remainingText = sourceText.substring(offset)
                    val layout = textMeasurer.measure(
                        text = AnnotatedString(remainingText),
                        style = measuredStyle,
                        overflow = TextOverflow.Clip,
                        maxLines = maxLines,
                        constraints = Constraints(maxWidth = pageWidthPx),
                    )
                    val end = layout.getLineEnd(layout.lineCount - 1).coerceAtLeast(1).coerceAtMost(remainingText.length)
                    elements += ReaderPageElement.Text(
                        text = remainingText.substring(0, end),
                        heading = heading,
                        firstLineIndent = indentThisFragment,
                        linkUrl = (block as? ReaderBlock.Link)?.url,
                        blockIndex = blockIndex,
                    )
                    remainingHeight = (remainingHeight - layout.size.height).coerceAtLeast(0)
                    offset += end
                    if (offset < sourceText.length) finishPage()
                }
            }

            is ReaderBlock.Illustration -> {
                val aspectRatio = if (block.width != null && block.height != null && block.width > 0 && block.height > 0) {
                    block.width.toFloat() / block.height
                } else {
                    0.72f
                }
                val desiredHeight = (pageWidthPx / aspectRatio).roundToInt().coerceAtMost(pageHeightPx)
                reserveSpacing(desiredHeight)
                if (remainingHeight <= 0) finishPage()
                val imageHeight = desiredHeight.coerceAtMost(remainingHeight).coerceAtLeast(1)
                elements += ReaderPageElement.Illustration(block, imageHeight, blockIndex)
                remainingHeight = (remainingHeight - imageHeight).coerceAtLeast(0)
            }
        }
    }
    finishPage()
    return pages.ifEmpty { listOf(ReaderPage(emptyList(), 0, 0)) }
}
