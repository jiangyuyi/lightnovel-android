package io.github.jiangyuyi.lightnovel.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jiangyuyi.lightnovel.core.data.LightNovelRepository
import io.github.jiangyuyi.lightnovel.core.model.BookSummary
import io.github.jiangyuyi.lightnovel.core.model.DiscoverChannel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class DiscoverState(
    val channel: DiscoverChannel = DiscoverChannel.HOT,
    val books: List<BookSummary> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val refreshError: String? = null,
    val lastUpdatedAt: Long? = null,
)

class DiscoverViewModel(private val repository: LightNovelRepository) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverState())
    val state: StateFlow<DiscoverState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        select(DiscoverChannel.HOT)
    }

    fun select(channel: DiscoverChannel) {
        if (_state.value.channel == channel && _state.value.books.isNotEmpty()) return
        load(channel, forceRefresh = false)
    }

    fun refresh() = load(_state.value.channel, forceRefresh = true)

    fun retry() = refresh()

    private fun load(channel: DiscoverChannel, forceRefresh: Boolean) {
        loadJob?.cancel()
        if (channel == DiscoverChannel.COLLECTION) {
            _state.value = DiscoverState(channel = channel, loading = false)
            return
        }
        val current = _state.value
        val keepContent = current.channel == channel && current.books.isNotEmpty()
        _state.value = if (keepContent) {
            current.copy(refreshing = forceRefresh, error = null, refreshError = null)
        } else {
            DiscoverState(channel = channel)
        }
        loadJob = viewModelScope.launch {
            repository.discoverUpdates(channel, forceRefresh = forceRefresh)
                .catch { throwable ->
                    val latest = _state.value
                    val message = throwable.message ?: "加载失败"
                    _state.value = if (latest.books.isEmpty()) {
                        latest.copy(loading = false, refreshing = false, error = message)
                    } else {
                        latest.copy(loading = false, refreshing = false, refreshError = message)
                    }
                }
                .collect { update ->
                    if (_state.value.channel != channel) return@collect
                    _state.value = _state.value.copy(
                        books = update.data.items,
                        loading = false,
                        refreshing = update.refreshing,
                        error = null,
                        refreshError = update.error?.message,
                        lastUpdatedAt = update.savedAtMillis,
                    )
                }
        }
    }
}
