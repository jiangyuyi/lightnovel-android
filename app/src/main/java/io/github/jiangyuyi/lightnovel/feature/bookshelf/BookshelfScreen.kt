package io.github.jiangyuyi.lightnovel.feature.bookshelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jiangyuyi.lightnovel.core.ui.BookCard
import io.github.jiangyuyi.lightnovel.core.ui.EmptyPane
import io.github.jiangyuyi.lightnovel.core.ui.ErrorPane
import io.github.jiangyuyi.lightnovel.core.ui.LoadingPane
import io.github.jiangyuyi.lightnovel.core.ui.RefreshStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    loggedIn: Boolean,
    onLogin: () -> Unit,
    onBook: (Long) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(loggedIn) { viewModel.refresh() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TopAppBar(
                title = { Text("我的书架") },
                actions = {
                    if (loggedIn) TextButton(onClick = { viewModel.refresh(true) }) { Text("刷新") }
                },
            )
        }
        item { RefreshStatus(state.refreshing, state.refreshError) }
        if (!loggedIn) {
            item {
                EmptyPane("登录后可同步书架和阅读记录")
                Button(onClick = onLogin, modifier = Modifier.padding(horizontal = 24.dp)) { Text("登录 / 注册") }
            }
        } else when {
            state.loading -> item { LoadingPane() }
            state.error != null -> item { ErrorPane(state.error!!, onRetry = { viewModel.refresh(true) }) }
            state.books.isEmpty() -> item { EmptyPane("书架还是空的，去发现喜欢的作品吧") }
            else -> items(state.books, key = { it.id }) { book ->
                BookCard(book, onClick = { onBook(book.id) }, modifier = Modifier.padding(horizontal = 14.dp))
            }
        }
    }
}
