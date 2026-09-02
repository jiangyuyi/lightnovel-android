package io.github.jiangyuyi.lightnovel.feature.book

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.jiangyuyi.lightnovel.core.ui.EmptyPane
import io.github.jiangyuyi.lightnovel.core.ui.ErrorPane
import io.github.jiangyuyi.lightnovel.core.ui.LoadingPane
import io.github.jiangyuyi.lightnovel.core.ui.RefreshStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookScreen(
    viewModel: BookViewModel,
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onBook: (Long) -> Unit,
    onRead: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var shareRequested by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TopAppBar(
                title = { Text(state.detail?.book?.title ?: "书籍详情", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(onClick = { shareRequested = true }, enabled = state.detail?.book?.id?.let { it > 0 } == true) {
                        Text("分享")
                    }
                    TextButton(onClick = { viewModel.load(true) }) { Text("刷新") }
                },
            )
        }
        item { RefreshStatus(state.refreshing, state.refreshError) }
        when {
            state.loading -> item { LoadingPane() }
            state.error != null && state.detail == null -> item { ErrorPane(state.error!!, onRetry = { viewModel.load(true) }) }
            state.detail == null -> item { EmptyPane("书籍不存在或暂不可见") }
            else -> {
                val detail = checkNotNull(state.detail)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AsyncImage(
                            model = detail.book.coverUrl,
                            contentDescription = detail.book.title,
                            modifier = Modifier.size(width = 116.dp, height = 164.dp),
                            contentScale = ContentScale.Crop,
                        )
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(detail.book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(detail.book.author.ifBlank { "作者未知" }, color = MaterialTheme.colorScheme.primary)
                            Text(
                                listOfNotNull(
                                    detail.book.volumeCount.takeIf { it > 0 }?.let { "$it 卷" },
                                    detail.book.chapterCount.takeIf { it > 0 }?.let { "$it 章" },
                                    detail.book.wordCount.takeIf { it > 0 }?.let { "${it / 10000} 万字" },
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Button(
                                onClick = { scope.launch { runCatching { viewModel.readingTarget() }.onSuccess(onRead) } },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("开始 / 继续阅读") }
                            OutlinedButton(
                                onClick = { viewModel.toggleBookshelf(onLogin) },
                                enabled = !state.shelfLoading,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.shelfLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text(if (state.inBookshelf) "移出书架" else "加入书架")
                            }
                        }
                    }
                }
                if (detail.book.tags.isNotEmpty()) {
                    item {
                        Text(
                            detail.book.tags.joinToString(" · "),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                }
                item {
                    SectionCard("简介") {
                        Text(detail.book.summary.ifBlank { "暂无简介" })
                    }
                }
                if (detail.alternateVersions.isNotEmpty()) {
                    item {
                        SectionCard("同书其他版本") {
                            detail.alternateVersions.forEach { version ->
                                Text(
                                    version.title,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth().clickable { onBook(version.id) }.padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
                item { Text("分卷与章节", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp)) }
                if (state.volumes.isEmpty()) {
                    item { EmptyPane("暂无目录") }
                } else {
                    items(state.volumes, key = { it.id }) { volume ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.toggleVolume(volume.id) }.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(volume.title, fontWeight = FontWeight.SemiBold)
                                        Text("${volume.chapterCount} 章", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Text(if (state.expandedVolumeId == volume.id) "收起" else "展开")
                                }
                                if (state.expandedVolumeId == volume.id) {
                                    if (state.loadingVolumeId == volume.id) {
                                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                        }
                                    } else {
                                        state.chapters[volume.id].orEmpty().forEach { chapter ->
                                            HorizontalDivider()
                                            Row(
                                                modifier = Modifier.fillMaxWidth()
                                                    .clickable { onRead(chapter.id) }
                                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                            ) {
                                                Text(chapter.title, Modifier.weight(1f))
                                                when {
                                                    chapter.accessType.equals("coin", ignoreCase = true) && chapter.unlocked == false -> Text(
                                                        chapter.coinPrice.takeIf { it > 0 }?.let { "需 $it 轻币" } ?: "需轻币",
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                    )
                                                    chapter.accessType.equals("coin", ignoreCase = true) && chapter.unlocked == null -> Text(
                                                        "轻币章节",
                                                        color = MaterialTheme.colorScheme.tertiary,
                                                    )
                                                    chapter.locked && chapter.unlocked != true -> Text(
                                                        "访问受限",
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                item { Text("评论（只读）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp)) }
                if (!state.commentsAvailable) {
                    item { EmptyPane("评论服务暂不可用，不影响阅读") }
                } else if (state.comments.isEmpty()) {
                    item { EmptyPane("暂无评论") }
                } else {
                    items(state.comments, key = { it.id }) { comment ->
                        SectionCard(comment.author.nickname) {
                            Text(comment.content)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOf(comment.createdAt, "赞 ${comment.likeCount}").filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (shareRequested) {
        val book = state.detail?.book
        val url = book?.id?.takeIf { it > 0 }?.let { "https://www.lightnovel.fun/book/$it" }.orEmpty()
        AlertDialog(
            onDismissRequest = { shareRequested = false },
            title = { Text("分享图书") },
            text = { Text(url) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("${book?.title.orEmpty()} 网页链接", url))
                        Toast.makeText(context, "网页链接已复制", Toast.LENGTH_SHORT).show()
                        shareRequested = false
                    },
                ) { Text("复制链接") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, book?.title.orEmpty())
                            putExtra(Intent.EXTRA_TEXT, "${book?.title.orEmpty()}\n$url")
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "分享图书"))
                        shareRequested = false
                    },
                ) { Text("系统分享") }
            },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}
