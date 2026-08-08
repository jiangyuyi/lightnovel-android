package io.github.jiangyuyi.lightnovel.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.jiangyuyi.lightnovel.core.model.Session
import io.github.jiangyuyi.lightnovel.core.ui.ErrorPane
import io.github.jiangyuyi.lightnovel.core.ui.LoadingPane

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    session: Session,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onBookshelf: () -> Unit,
    onFollowing: () -> Unit,
    onFollowers: () -> Unit,
    onHistory: () -> Unit,
    onPublishing: () -> Unit,
    onMessages: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(session.loggedIn) { viewModel.refresh(session.loggedIn) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { TopAppBar(title = { Text("我的") }) }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Card(Modifier.fillMaxWidth()) {
                    if (session.loggedIn) {
                        val profile = state.profile
                        Row(
                            Modifier.fillMaxWidth().padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = profile?.user?.avatarUrl ?: session.user?.avatarUrl,
                                contentDescription = "头像",
                                modifier = Modifier.size(64.dp).clip(CircleShape),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    profile?.user?.nickname ?: session.user?.nickname ?: "已登录用户",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    listOfNotNull(
                                        (profile?.user?.uid ?: session.uid).takeIf { it > 0 }?.let { "UID $it" },
                                        profile?.levelName?.takeIf { it.isNotBlank() },
                                    ).joinToString(" · "),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                profile?.signature?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    } else {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("游客", style = MaterialTheme.typography.titleLarge)
                            Text("登录后可同步书架、阅读记录和个人消息")
                        }
                    }
                }

                when {
                    state.loading && state.profile == null -> LoadingPane()
                    state.error != null && state.profile == null -> ErrorPane(state.error!!, onRetry = {
                        viewModel.refresh(session.loggedIn)
                    })
                }

                if (session.loggedIn) {
                    state.profile?.let { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            ProfileStat("关注", profile.followingCount, onFollowing)
                            ProfileStat("粉丝", profile.fansCount, onFollowers)
                            ProfileStat("发布", profile.postCount, onPublishing)
                            ProfileStat("轻币", profile.coin, null)
                        }
                    }
                    Text("个人功能", style = MaterialTheme.typography.titleMedium)
                    ProfileEntry(
                        "消息中心",
                        "回复、@、点赞、粉丝、系统与私信",
                        onMessages,
                        badge = state.messageSummary.unreadCount.takeIf { it > 0 },
                    )
                    ProfileEntry("我的书架", "收藏与章节更新", onBookshelf)
                    ProfileEntry("阅读记录", "继续上次阅读", onHistory)
                    ProfileEntry("关注与粉丝", "查看用户关系", onFollowing)
                    ProfileEntry("发布管理", "作品状态与审核进度", onPublishing)
                    OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("退出登录") }
                } else {
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("登录 / 注册") }
                }

                Spacer(Modifier.height(2.dp))
                Text("说明", style = MaterialTheme.typography.titleMedium)
                Text("这是使用轻之国度当前公开 Web API 的非官方客户端，接口可能随网站升级而变化。")
                Text("评论当前为只读；完整写作工作台和社区发布功能仍在后续版本评估中。")
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun ProfileStat(label: String, value: Int, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier.clickable(enabled = onClick != null) { onClick?.invoke() }.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProfileEntry(title: String, subtitle: String, onClick: () -> Unit, badge: Int? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            badge?.let {
                Text(
                    it.coerceAtMost(99).let { value -> if (it > 99) "99+" else value.toString() },
                    modifier = Modifier.padding(horizontal = 10.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
