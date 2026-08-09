package io.github.jiangyuyi.lightnovel.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.jiangyuyi.lightnovel.core.model.BookSummary

@Composable
fun LoadingPane(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(3) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PlaceholderBlock(Modifier.size(width = 82.dp, height = 116.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlaceholderBlock(Modifier.fillMaxWidth(0.72f).height(20.dp))
                    PlaceholderBlock(Modifier.fillMaxWidth(0.42f).height(14.dp))
                    PlaceholderBlock(Modifier.fillMaxWidth().height(14.dp))
                    PlaceholderBlock(Modifier.fillMaxWidth(0.88f).height(14.dp))
                }
            }
        }
    }
}

@Composable
fun RefreshStatus(
    refreshing: Boolean,
    refreshError: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (!refreshError.isNullOrBlank()) {
            Text(
                text = "刷新失败，当前显示缓存内容：${displayErrorMessage(refreshError)}",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun PlaceholderBlock(modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        content = {},
    )
}

@Composable
fun ErrorPane(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(displayErrorMessage(message), color = MaterialTheme.colorScheme.error)
        if (onRetry != null) Button(onClick = onRetry) { Text("重试") }
    }
}

@Composable
fun EmptyPane(message: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun displayErrorMessage(message: String): String {
    val compact = message.trim()
    if (compact.contains("CronetUrlRequest", ignoreCase = true) ||
        compact.contains("ERR_INTERNET_", ignoreCase = true) ||
        compact.contains("ERR_NETWORK_", ignoreCase = true)
    ) {
        return "网络连接失败，请检查网络后重试"
    }
    return compact.lineSequence().firstOrNull().orEmpty().take(160).ifBlank { "加载失败，请稍后重试" }
}

@Composable
fun BookCard(book: BookSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = book.title,
                modifier = Modifier.size(width = 82.dp, height = 116.dp),
                contentScale = ContentScale.Crop,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    book.rank?.let {
                        Text("#$it  ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (book.author.isNotBlank()) {
                    Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    book.summary.ifBlank { "暂无简介" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    listOfNotNull(
                        book.chapterCount.takeIf { it > 0 }?.let { "$it 章" },
                        book.wordCount.takeIf { it > 0 }?.let { "${it / 10000} 万字" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
