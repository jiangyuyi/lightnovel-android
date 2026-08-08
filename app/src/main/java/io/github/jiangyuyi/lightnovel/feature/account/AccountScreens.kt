package io.github.jiangyuyi.lightnovel.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.jiangyuyi.lightnovel.core.model.PublishedWork
import io.github.jiangyuyi.lightnovel.core.model.ReadingHistoryItem
import io.github.jiangyuyi.lightnovel.core.model.SocialUser
import io.github.jiangyuyi.lightnovel.core.ui.EmptyPane
import io.github.jiangyuyi.lightnovel.core.ui.ErrorPane
import io.github.jiangyuyi.lightnovel.core.ui.LoadingPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(viewModel: SocialViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmUnfollow by remember { mutableStateOf<SocialUser?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AccountTopBar("关注与粉丝", onBack) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SocialMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.mode == mode,
                        onClick = { viewModel.select(mode) },
                        label = { Text("${mode.label}${if (state.mode == mode && state.total > 0) " ${state.total}" else ""}") },
                    )
                }
            }
        }
        state.actionError?.let { item { ErrorText(it) } }
        when {
            state.loading -> item { LoadingPane() }
            state.error != null && state.items.isEmpty() -> item { ErrorPane(state.error!!, onRetry = viewModel::refresh) }
            state.items.isEmpty() -> item { EmptyPane(if (state.mode == SocialMode.FOLLOWING) "还没有关注用户" else "还没有粉丝") }
            else -> {
                items(state.items, key = { it.user.uid }) { user ->
                    SocialUserCard(
                        user = user,
                        pending = user.user.uid in state.pendingUsers,
                        onFollow = {
                            if (user.followed) confirmUnfollow = user else viewModel.setFollow(user, true)
                        },
                    )
                }
                if (state.hasMore || state.loadingMore) {
                    item { LoadMoreButton(state.loadingMore, viewModel::loadMore) }
                }
            }
        }
    }

    confirmUnfollow?.let { user ->
        AlertDialog(
            onDismissRequest = { confirmUnfollow = null },
            title = { Text("取消关注？") },
            text = { Text("将不再关注 ${user.user.nickname}。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnfollow = null
                    viewModel.setFollow(user, false)
                }) { Text("取消关注") }
            },
            dismissButton = { TextButton(onClick = { confirmUnfollow = null }) { Text("返回") } },
        )
    }
}

@Composable
private fun SocialUserCard(user: SocialUser, pending: Boolean, onFollow: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = user.user.avatarUrl,
                contentDescription = user.user.nickname,
                modifier = Modifier.size(52.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(user.user.nickname, fontWeight = FontWeight.SemiBold)
                if (user.levelName.isNotBlank()) {
                    Text(user.levelName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    user.signature.ifBlank { "这个人还没有填写签名" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onFollow, enabled = !pending) {
                Text(if (pending) "处理中" else if (user.followed) "已关注" else "关注")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpen: (bookId: Long, chapterId: Long?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<ReadingHistoryItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AccountTopBar("阅读记录", onBack) }
        state.actionError?.let { item { ErrorText(it) } }
        when {
            state.loading -> item { LoadingPane() }
            state.error != null && state.items.isEmpty() -> item { ErrorPane(state.error!!, onRetry = viewModel::refresh) }
            state.items.isEmpty() -> item { EmptyPane("暂无阅读记录") }
            else -> {
                items(state.items, key = { it.book.id }) { item ->
                    HistoryCard(
                        item = item,
                        deleting = item.book.id in state.deleting,
                        onOpen = { onOpen(item.book.id, item.lastChapterId) },
                        onDelete = { deleting = item },
                    )
                }
                if (state.hasMore || state.loadingMore) item { LoadMoreButton(state.loadingMore, viewModel::loadMore) }
            }
        }
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除阅读记录？") },
            text = { Text("将从轻之国度账户删除《${item.book.title}》的阅读记录。") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    viewModel.delete(item)
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun HistoryCard(
    item: ReadingHistoryItem,
    deleting: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            AsyncImage(
                model = item.book.coverUrl,
                contentDescription = item.book.title,
                modifier = Modifier.size(width = 70.dp, height = 98.dp),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.book.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    item.lastChapterTitle.ifBlank { "继续阅读" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.readAt.isNotBlank()) {
                    Text(item.readAt, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpen) { Text("继续阅读") }
                    TextButton(onClick = onDelete, enabled = !deleting) { Text(if (deleting) "删除中" else "删除") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishingScreen(viewModel: PublishingViewModel, onBack: () -> Unit, onBook: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { AccountTopBar("发布管理", onBack) }
        item {
            Text(
                "展示作品、连载状态与审核进度；新建和编辑请暂时使用网站作者工作台。",
                modifier = Modifier.padding(horizontal = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            state.loading -> item { LoadingPane() }
            state.error != null && state.items.isEmpty() -> item { ErrorPane(state.error!!, onRetry = viewModel::refresh) }
            state.items.isEmpty() -> item { EmptyPane("还没有发布作品") }
            else -> {
                items(state.items, key = { it.bookId }) { work -> PublishedWorkCard(work) { onBook(work.bookId) } }
                if (state.hasMore || state.loadingMore) item { LoadMoreButton(state.loadingMore, viewModel::loadMore) }
            }
        }
    }
}

@Composable
private fun PublishedWorkCard(work: PublishedWork, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            AsyncImage(
                model = work.coverUrl,
                contentDescription = work.title,
                modifier = Modifier.size(width = 70.dp, height = 98.dp),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(work.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(work.type, work.status, work.reviewText).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (work.reviewStatus == "rejected") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    listOfNotNull(
                        work.volumeCount.takeIf { it > 0 }?.let { "$it 卷" },
                        work.chapterCount.takeIf { it > 0 }?.let { "$it 章" },
                        work.wordCount.takeIf { it > 0 }?.let { "${it / 10000.0}".take(4) + " 万字" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (work.updatedAt.isNotBlank()) {
                    Text("更新于 ${work.updatedAt}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
    )
}

@Composable
private fun LoadMoreButton(loading: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
    ) { Text(if (loading) "加载中…" else "加载更多") }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}
