package io.github.jiangyuyi.lightnovel.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jiangyuyi.lightnovel.core.data.LightNovelRepository
import io.github.jiangyuyi.lightnovel.core.model.PublishedWork
import io.github.jiangyuyi.lightnovel.core.model.ReadingHistoryItem
import io.github.jiangyuyi.lightnovel.core.model.SocialUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

enum class SocialMode(val label: String) { FOLLOWING("关注"), FOLLOWERS("粉丝") }

data class SocialState(
    val mode: SocialMode = SocialMode.FOLLOWING,
    val items: List<SocialUser> = emptyList(),
    val page: Int = 0,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val pendingUsers: Set<Long> = emptySet(),
    val error: String? = null,
    val refreshError: String? = null,
    val actionError: String? = null,
    val lastUpdatedAt: Long? = null,
)

class SocialViewModel(
    private val repository: LightNovelRepository,
    initialMode: SocialMode,
) : ViewModel() {
    private val _state = MutableStateFlow(SocialState(mode = initialMode))
    val state: StateFlow<SocialState> = _state.asStateFlow()

    init { load(page = 1, append = false, forceRefresh = false) }

    fun select(mode: SocialMode) {
        if (_state.value.mode == mode && _state.value.items.isNotEmpty()) return
        _state.value = SocialState(mode = mode)
        load(page = 1, append = false, forceRefresh = false)
    }

    fun refresh() = load(page = 1, append = false, forceRefresh = true)

    fun loadMore() {
        val current = _state.value
        if (current.hasMore && !current.loading && !current.loadingMore) {
            load(current.page + 1, append = true, forceRefresh = false)
        }
    }

    fun setFollow(user: SocialUser, follow: Boolean) {
        val uid = user.user.uid
        if (uid in _state.value.pendingUsers) return
        _state.value = _state.value.copy(
            pendingUsers = _state.value.pendingUsers + uid,
            actionError = null,
        )
        viewModelScope.launch {
            runCatching { repository.setUserFollow(uid, follow) }
                .onSuccess {
                    val current = _state.value
                    val nextItems = if (current.mode == SocialMode.FOLLOWING && !follow) {
                        current.items.filterNot { it.user.uid == uid }
                    } else {
                        current.items.map { item ->
                            if (item.user.uid == uid) item.copy(followed = follow) else item
                        }
                    }
                    _state.value = current.copy(
                        items = nextItems,
                        total = if (current.mode == SocialMode.FOLLOWING && !follow) {
                            (current.total - 1).coerceAtLeast(0)
                        } else current.total,
                        pendingUsers = current.pendingUsers - uid,
                    )
                }
                .onFailure {
                    val current = _state.value
                    _state.value = current.copy(
                        pendingUsers = current.pendingUsers - uid,
                        actionError = it.message ?: "关注状态更新失败",
                    )
                }
        }
    }

    private fun load(page: Int, append: Boolean, forceRefresh: Boolean) {
        val current = _state.value
        if (current.loading || current.loadingMore || current.refreshing) return
        val hasContent = current.items.isNotEmpty()
        _state.value = current.copy(
            loading = !append && !hasContent,
            refreshing = !append && hasContent && forceRefresh,
            loadingMore = append,
            error = null,
            refreshError = null,
            actionError = null,
        )
        viewModelScope.launch {
            val mode = _state.value.mode
            val updates = if (mode == SocialMode.FOLLOWING) {
                repository.followingUpdates(page, forceRefresh = forceRefresh)
            } else {
                repository.followersUpdates(page, forceRefresh = forceRefresh)
            }
            updates.catch { throwable ->
                val latest = _state.value
                if (latest.mode == mode) {
                    val message = throwable.message ?: "用户列表加载失败"
                    _state.value = latest.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = message.takeIf { latest.items.isEmpty() },
                        refreshError = message.takeIf { latest.items.isNotEmpty() },
                    )
                }
            }.collect { update ->
                val latest = _state.value
                if (latest.mode != mode) return@collect
                val result = update.data
                _state.value = latest.copy(
                    items = if (append) (latest.items + result.items).distinctBy { it.user.uid } else result.items,
                    page = result.page,
                    total = result.total,
                    hasMore = result.hasMore,
                    loading = false,
                    refreshing = update.refreshing,
                    loadingMore = false,
                    error = null,
                    refreshError = update.error?.message,
                    lastUpdatedAt = update.savedAtMillis,
                )
            }
        }
    }
}

data class HistoryState(
    val items: List<ReadingHistoryItem> = emptyList(),
    val page: Int = 0,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val deleting: Set<Long> = emptySet(),
    val error: String? = null,
    val refreshError: String? = null,
    val actionError: String? = null,
    val lastUpdatedAt: Long? = null,
)

class HistoryViewModel(private val repository: LightNovelRepository) : ViewModel() {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()

    init { load(1, false, false) }

    fun refresh() = load(1, false, true)

    fun loadMore() {
        val current = _state.value
        if (current.hasMore && !current.loading && !current.loadingMore && !current.refreshing) {
            load(current.page + 1, true, false)
        }
    }

    fun delete(item: ReadingHistoryItem) {
        val bookId = item.book.id
        if (bookId in _state.value.deleting) return
        _state.value = _state.value.copy(deleting = _state.value.deleting + bookId, actionError = null)
        viewModelScope.launch {
            runCatching { repository.deleteReadingHistory(bookId) }
                .onSuccess {
                    val current = _state.value
                    _state.value = current.copy(
                        items = current.items.filterNot { it.book.id == bookId },
                        total = (current.total - 1).coerceAtLeast(0),
                        deleting = current.deleting - bookId,
                    )
                }
                .onFailure {
                    val current = _state.value
                    _state.value = current.copy(
                        deleting = current.deleting - bookId,
                        actionError = it.message ?: "阅读记录删除失败",
                    )
                }
        }
    }

    private fun load(page: Int, append: Boolean, forceRefresh: Boolean) {
        val current = _state.value
        if (current.loading || current.loadingMore || current.refreshing) return
        val hasContent = current.items.isNotEmpty()
        _state.value = current.copy(
            loading = !append && !hasContent,
            refreshing = !append && hasContent && forceRefresh,
            loadingMore = append,
            error = null,
            refreshError = null,
            actionError = null,
        )
        viewModelScope.launch {
            repository.readingHistoryUpdates(page, forceRefresh = forceRefresh)
                .catch { throwable ->
                    val latest = _state.value
                    val message = throwable.message ?: "阅读记录加载失败"
                    _state.value = latest.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = message.takeIf { latest.items.isEmpty() },
                        refreshError = message.takeIf { latest.items.isNotEmpty() },
                    )
                }
                .collect { update ->
                    val latest = _state.value
                    val result = update.data
                    _state.value = latest.copy(
                        items = if (append) (latest.items + result.items).distinctBy { it.book.id } else result.items,
                        page = result.page,
                        total = result.total,
                        hasMore = result.hasMore,
                        loading = false,
                        refreshing = update.refreshing,
                        loadingMore = false,
                        error = null,
                        refreshError = update.error?.message,
                        lastUpdatedAt = update.savedAtMillis,
                    )
                }
        }
    }
}

data class PublishingState(
    val items: List<PublishedWork> = emptyList(),
    val page: Int = 0,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val refreshError: String? = null,
    val lastUpdatedAt: Long? = null,
)

class PublishingViewModel(private val repository: LightNovelRepository) : ViewModel() {
    private val _state = MutableStateFlow(PublishingState())
    val state: StateFlow<PublishingState> = _state.asStateFlow()

    init { load(1, false, false) }

    fun refresh() = load(1, false, true)

    fun loadMore() {
        val current = _state.value
        if (current.hasMore && !current.loading && !current.loadingMore && !current.refreshing) {
            load(current.page + 1, true, false)
        }
    }

    private fun load(page: Int, append: Boolean, forceRefresh: Boolean) {
        val current = _state.value
        if (current.loading || current.loadingMore || current.refreshing) return
        val hasContent = current.items.isNotEmpty()
        _state.value = current.copy(
            loading = !append && !hasContent,
            refreshing = !append && hasContent && forceRefresh,
            loadingMore = append,
            error = null,
            refreshError = null,
        )
        viewModelScope.launch {
            repository.publishedWorksUpdates(page, forceRefresh = forceRefresh)
                .catch { throwable ->
                    val latest = _state.value
                    val message = throwable.message ?: "发布管理加载失败"
                    _state.value = latest.copy(
                        loading = false,
                        refreshing = false,
                        loadingMore = false,
                        error = message.takeIf { latest.items.isEmpty() },
                        refreshError = message.takeIf { latest.items.isNotEmpty() },
                    )
                }
                .collect { update ->
                    val latest = _state.value
                    val result = update.data
                    _state.value = latest.copy(
                        items = if (append) (latest.items + result.items).distinctBy { it.bookId } else result.items,
                        page = result.page,
                        total = result.total,
                        hasMore = result.hasMore,
                        loading = false,
                        refreshing = update.refreshing,
                        loadingMore = false,
                        error = null,
                        refreshError = update.error?.message,
                        lastUpdatedAt = update.savedAtMillis,
                    )
                }
        }
    }
}
