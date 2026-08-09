package io.github.jiangyuyi.lightnovel.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jiangyuyi.lightnovel.core.model.DiscoverChannel
import io.github.jiangyuyi.lightnovel.core.ui.BookCard
import io.github.jiangyuyi.lightnovel.core.ui.EmptyPane
import io.github.jiangyuyi.lightnovel.core.ui.ErrorPane
import io.github.jiangyuyi.lightnovel.core.ui.LoadingPane
import io.github.jiangyuyi.lightnovel.core.ui.RefreshStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(viewModel: DiscoverViewModel, onBook: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TopAppBar(
                title = { Text("轻之国度", color = MaterialTheme.colorScheme.primary) },
                actions = { TextButton(onClick = viewModel::refresh) { Text("刷新") } },
            )
            ScrollableTabRow(selectedTabIndex = state.channel.ordinal, edgePadding = 10.dp) {
                DiscoverChannel.entries.forEach { channel ->
                    Tab(
                        selected = state.channel == channel,
                        onClick = { viewModel.select(channel) },
                        text = { Text(channel.label) },
                    )
                }
            }
        }
        item { RefreshStatus(state.refreshing, state.refreshError) }
        when {
            state.loading -> item { LoadingPane() }
            state.error != null -> item { ErrorPane(state.error!!, onRetry = viewModel::retry) }
            state.books.isEmpty() -> item {
                EmptyPane(
                    if (state.channel == DiscoverChannel.COLLECTION) {
                        "网站的独立合集分区正在维护；书籍详情已支持分卷、章节和同书其他版本。"
                    } else {
                        "此分区暂时没有可显示的作品"
                    },
                )
            }
            else -> items(state.books, key = { it.id }) { book ->
                BookCard(book, onClick = { onBook(book.id) }, modifier = Modifier.padding(horizontal = 14.dp))
            }
        }
    }
}
