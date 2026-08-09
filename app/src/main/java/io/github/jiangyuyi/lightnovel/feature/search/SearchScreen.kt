package io.github.jiangyuyi.lightnovel.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jiangyuyi.lightnovel.core.ui.BookCard
import io.github.jiangyuyi.lightnovel.core.ui.EmptyPane
import io.github.jiangyuyi.lightnovel.core.ui.ErrorPane
import io.github.jiangyuyi.lightnovel.core.ui.LoadingPane
import io.github.jiangyuyi.lightnovel.core.ui.RefreshStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(viewModel: SearchViewModel, onBook: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            TopAppBar(title = { Text("搜索") })
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("书名 / 作者 / 关键词") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); viewModel.search() }),
                )
                Button(onClick = { focus.clearFocus(); viewModel.search() }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("搜索")
                }
            }
        }
        item { RefreshStatus(state.refreshing, state.refreshError) }
        if (state.taxonomy.channels.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("分类", modifier = Modifier.padding(horizontal = 14.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.taxonomy.channels, key = { it.id }) { option ->
                            FilterChip(
                                selected = state.workType == (option.workType.ifBlank { option.id }),
                                onClick = { viewModel.setWorkType(option.workType.ifBlank { option.id }) },
                                label = { Text(option.label) },
                            )
                        }
                    }
                }
            }
        }
        if (state.taxonomy.tags.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.taxonomy.tags, key = { it.id }) { option ->
                        FilterChip(
                            selected = state.primaryTag == option.id,
                            onClick = { viewModel.setTag(option.id) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
        }
        when {
            state.loading -> item { LoadingPane() }
            state.error != null -> item { ErrorPane(state.error!!, onRetry = { viewModel.search(true) }) }
            state.results.isEmpty() -> item { EmptyPane("输入关键词或选择分类开始搜索") }
            else -> items(state.results, key = { it.id }) { book ->
                BookCard(book, onClick = { onBook(book.id) }, modifier = Modifier.padding(horizontal = 14.dp))
            }
        }
    }
}
