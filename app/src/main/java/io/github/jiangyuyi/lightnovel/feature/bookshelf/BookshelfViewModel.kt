package io.github.jiangyuyi.lightnovel.feature.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jiangyuyi.lightnovel.core.data.LightNovelRepository
import io.github.jiangyuyi.lightnovel.core.model.BookSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class BookshelfState(
    val books: List<BookSummary> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val refreshError: String? = null,
    val lastUpdatedAt: Long? = null,
)

class BookshelfViewModel(private val repository: LightNovelRepository) : ViewModel() {
    private val _state = MutableStateFlow(BookshelfState())
    val state: StateFlow<BookshelfState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh(forceRefresh: Boolean = false) {
        if (!repository.session.value.loggedIn) {
            refreshJob?.cancel()
            _state.value = BookshelfState()
            return
        }
        refreshJob?.cancel()
        val hasContent = _state.value.books.isNotEmpty()
        _state.value = _state.value.copy(
            loading = !hasContent,
            refreshing = hasContent && forceRefresh,
            error = null,
            refreshError = null,
        )
        refreshJob = viewModelScope.launch {
            repository.bookshelfUpdates(forceRefresh)
                .catch { throwable ->
                    val current = _state.value
                    val message = throwable.message ?: "书架加载失败"
                    _state.value = if (current.books.isEmpty()) {
                        current.copy(loading = false, refreshing = false, error = message)
                    } else {
                        current.copy(loading = false, refreshing = false, refreshError = message)
                    }
                }
                .collect { update ->
                    _state.value = _state.value.copy(
                        books = update.data,
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
