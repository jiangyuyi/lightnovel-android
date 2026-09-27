package io.github.jiangyuyi.lightnovel.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date

@Composable
fun WelfareDialog(viewModel: WelfareViewModel, onDismiss: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    AlertDialog(
        onDismissRequest = { if (!state.claiming) onDismiss() },
        title = { Text("签到领轻币") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loading || state.claiming) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.data?.let { data ->
                    if (!state.verified) {
                        Text(
                            "上次记录${state.savedAtMillis?.let { " · ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))}" } ?: ""}；${if (state.loading) "正在刷新…" else "请刷新确认今日状态"}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text("轻币余额：${data.coin?.toString() ?: "暂不可用"}", style = MaterialTheme.typography.titleMedium)
                    data.todayCoin?.let { Text("${if (state.verified) "今日收益" else "上次记录的当日收益"}：$it 轻币") }
                    Text(data.title, fontWeight = FontWeight.Bold)
                    if (data.description.isNotBlank()) Text(data.description)
                    data.days.forEach { day ->
                        val active = day.claimable && !day.claimed
                        Card(colors = CardDefaults.cardColors(
                            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        )) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("第 ${day.day} 天")
                                Text("${day.amount} 轻币")
                                Text(if (day.claimed) "已领取" else if (active) { if (state.verified) "可领取" else "待确认" } else "待签到")
                            }
                        }
                    }
                    if (data.claimed && state.verified) Text("今日已领取，明天再来", color = MaterialTheme.colorScheme.primary)
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("奖励与领取资格以服务器为准；与官方 App 共用签到进度。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = viewModel::signIn,
                enabled = state.verified && state.data?.claimable == true && !state.loading && !state.claiming,
            ) { Text(when {
                state.claiming -> "正在领取…"
                state.loading -> "正在确认…"
                !state.verified && state.data != null -> "刷新后确认"
                else -> state.data?.buttonText ?: "签到领取"
            }) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = viewModel::refresh, enabled = !state.loading && !state.claiming) { Text("刷新") }
                TextButton(onClick = onDismiss, enabled = !state.claiming) { Text("关闭") }
            }
        },
    )
}
