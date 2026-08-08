package io.github.jiangyuyi.lightnovel.feature.messages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.jiangyuyi.lightnovel.core.model.DmConversation
import io.github.jiangyuyi.lightnovel.core.model.DmMessage
import io.github.jiangyuyi.lightnovel.core.model.MessageCategory
import io.github.jiangyuyi.lightnovel.core.model.NotificationMessage
import io.github.jiangyuyi.lightnovel.core.model.UserSummary
import io.github.jiangyuyi.lightnovel.core.ui.EmptyPane
import io.github.jiangyuyi.lightnovel.core.ui.ErrorPane
import io.github.jiangyuyi.lightnovel.core.ui.LoadingPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesScreen(
    viewModel: MessagesViewModel,
    onBack: () -> Unit,
    onConversation: (DmConversation) -> Unit,
    onTarget: (bookId: Long, chapterId: Long?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TopAppBar(
                title = { Text("消息中心") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(
                        onClick = viewModel::markCurrentRead,
                        enabled = !state.markingRead && state.summary.count(state.category) > 0,
                    ) { Text(if (state.markingRead) "处理中" else "标为已读") }
                },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MessageCategory.entries.forEach { category ->
                    val unread = state.summary.count(category)
                    FilterChip(
                        selected = state.category == category,
                        onClick = { viewModel.select(category) },
                        label = { Text(if (unread > 0) "${category.label} $unread" else category.label) },
                    )
                }
            }
        }
        item {
            Text(
                if (state.category == MessageCategory.DM) {
                    "私信当前为只读，发送功能将在完成反骚扰与失败回滚设计后开放。"
                } else {
                    "点击含作品目标的通知可跳转到书籍或章节；动态精确定位暂未开放。"
                },
                modifier = Modifier.padding(horizontal = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.actionError?.let { item { MessageErrorText(it) } }
        when {
            state.loading -> item { LoadingPane() }
            state.error != null && state.messages.isEmpty() && state.conversations.isEmpty() -> {
                item { ErrorPane(state.error!!, onRetry = viewModel::refresh) }
            }
            state.category == MessageCategory.DM && state.conversations.isEmpty() -> {
                item { EmptyPane("暂无私信会话") }
            }
            state.category != MessageCategory.DM && state.messages.isEmpty() -> {
                item { EmptyPane("暂无${state.category.label}消息") }
            }
            state.category == MessageCategory.DM -> {
                items(state.conversations, key = { it.id }) { conversation ->
                    ConversationCard(conversation) { onConversation(conversation) }
                }
            }
            else -> {
                items(state.messages, key = { it.id }) { message ->
                    NotificationCard(message) {
                        message.targetBookId?.let { onTarget(it, message.targetChapterId) }
                    }
                }
                if (state.hasMore || state.loadingMore) {
                    item {
                        OutlinedButton(
                            onClick = viewModel::loadMore,
                            enabled = !state.loadingMore,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        ) { Text(if (state.loadingMore) "加载中…" else "加载更多") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationCard(conversation: DmConversation, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (conversation.unreadCount > 0) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = conversation.user.avatarUrl,
                contentDescription = conversation.user.nickname,
                modifier = Modifier.size(52.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(conversation.user.nickname, fontWeight = FontWeight.SemiBold)
                    if (conversation.unreadCount > 0) {
                        Text("未读 ${conversation.unreadCount}", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    conversation.lastMessage.ifBlank { "点击查看会话" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (conversation.updatedAt.isNotBlank()) {
                    Text(conversation.updatedAt, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(message: NotificationMessage, onClick: () -> Unit) {
    val hasTarget = message.targetBookId != null
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).clickable(enabled = hasTarget, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (message.unread) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            } else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = message.user?.avatarUrl ?: message.sourceAvatarUrl,
                contentDescription = message.sourceName,
                modifier = Modifier.size(46.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(message.title, fontWeight = FontWeight.SemiBold)
                if (message.sourceName.isNotBlank()) {
                    Text(message.sourceName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(message.content, style = MaterialTheme.typography.bodyMedium)
                message.quoteText.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "“$it”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                message.relatedTitle.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (message.createdAt.isNotBlank()) {
                    Text(message.createdAt, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmThreadScreen(
    peer: UserSummary,
    viewModel: DmThreadViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TopAppBar(
                title = { Text("与 ${peer.nickname} 的私信") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        }
        item {
            Text(
                "只读会话：本版本不会发送消息。",
                modifier = Modifier.padding(horizontal = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            state.loading -> item { LoadingPane() }
            state.error != null -> item { ErrorPane(state.error!!, onRetry = viewModel::refresh) }
            state.messages.isEmpty() -> item { EmptyPane("暂无私信消息") }
            else -> items(state.messages, key = { it.id }) { DmBubble(it) }
        }
    }
}

@Composable
private fun DmBubble(message: DmMessage) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 340.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.mine) {
                    MaterialTheme.colorScheme.primaryContainer
                } else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!message.mine) {
                    Text(message.sender.nickname, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(message.content)
                if (message.createdAt.isNotBlank()) {
                    Text(
                        message.createdAt,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageErrorText(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}
