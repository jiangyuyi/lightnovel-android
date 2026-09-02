package io.github.jiangyuyi.lightnovel.feature.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Placed below safeDrawing's top inset, outside both scrolling and paged content. */
@Composable
internal fun ReaderChapterHeader(bookTitle: String, chapterTitle: String, textColor: Color) {
    var showFullTitle by remember(bookTitle, chapterTitle) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClickLabel = "查看完整书名和章节") { showFullTitle = true }
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = bookTitle,
            color = textColor.copy(alpha = 0.7f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = chapterTitle,
            color = textColor.copy(alpha = 0.7f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
    if (showFullTitle) {
        AlertDialog(
            onDismissRequest = { showFullTitle = false },
            title = { Text("当前阅读") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(bookTitle)
                    Text(chapterTitle)
                }
            },
            confirmButton = { TextButton(onClick = { showFullTitle = false }) { Text("关闭") } },
        )
    }
}
