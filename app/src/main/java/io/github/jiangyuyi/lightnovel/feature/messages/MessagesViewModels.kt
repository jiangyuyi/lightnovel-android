package io.github.jiangyuyi.lightnovel.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jiangyuyi.lightnovel.core.data.LightNovelRepository
import io.github.jiangyuyi.lightnovel.core.model.DmConversation
import io.github.jiangyuyi.lightnovel.core.model.DmMessage
import io.github.jiangyuyi.lightnovel.core.model.MessageCategory
import io.github.jiangyuyi.lightnovel.core.model.MessageSummary
import io.github.jiangyuyi.lightnovel.core.model.NotificationMessage
import io.github.jiangyuyi.lightnovel.core.model.UserSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class MessagesState(
    val category: MessageCategory = MessageCategory.REPLY,
    val summary: MessageSummary = MessageSummary(),
    val messages: List<NotificationMessage> = emptyList(),
    val conversations: List<DmConversation> = emptyList(),
    val page: Int = 0,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val markingRead: Boolean = false,
    val error: String? = null,
    val refreshError: String? = null,
    val actionError: String? = null,
    val lastUpdatedAt: Long? = null,
)

class MessagesViewModel(
    private val repository: LightNovelRepository,
    initialCategory: MessageCategory = MessageCategory.REPLY,
) : ViewModel() {
    private val _state = MutableStateFlow(MessagesState(category = initialCategory))
    val state: StateFlow<MessagesState> = _state.asStateFlow()

    init {
        refreshSummary(forceRefresh = false)
        load(page = 1, append = false, forceRefresh = false)
    }

    fun select(category: MessageCategory) {
        if (_state.value.category == category &&
            (_state.value.messages.isNotEmpty() || _state.value.conversations.isNotEmpty())
        ) return
        _state.value = MessagesState(category = category, summary = _state.value.summary)
        load(page = 1, append = false, forceRefresh = false)
    }

    fun refresh() = load(page = 1, append = false, forceRefresh = true)

    fun refreshSummary(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            repository.messageSummaryUpdates(forceRefresh)
                .catch { }
                .collect { update -> _state.value = _state.value.copy(summary = update.data) }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.category != MessageCategory.DM && current.hasMore && !current.loading && !current.loadingMore) {
            load(current.page + 1, append = true, forceRefresh = false)
        }
    }

    fun markCurrentRead() {
        val current = _state.value
        if (current.markingRead || current.summary.count(current.category) <= 0) return
        _state.value = current.copy(markingRead = true, actionError = null)
        viewModelScope.launch {
            val category = current.category
            runCatching { repository.markMessageCategoryRead(category) }
                .onSuccess {
                    val latest = _state.value
                    if (latest.category == category) {
                        _state.value = latest.copy(
                            markingRead = false,
                            summary = latest.summary.clear(category),
                            messages = latest.messages.map { it.copy(unread = false) },
                            conversations = latest.conversations.map { it.copy(unreadCount = 0) },
                        )
                    }
                }
                .onFailure {
                    val latest = _state.value
                    if (latest.category == category) {
                        _state.value = latest.copy(
                            markingRead = false,
                            actionError = it.message ?: "标记已读失败",
                        )
                    }
                }
        }
    }

    private fun load(page: Int, append: Boolean, forceRefresh: Boolean) {
        val current = _state.value
        if (current.loading || current.loadingMore || current.refreshing) return
        val hasContent = current.messages.isNotEmpty() || current.conversations.isNotEmpty()
        _state.value = current.copy(
            loading = !append && !hasContent,
            refreshing = !append && hasContent && forceRefresh,
            loadingMore = append,
            error = null,
            refreshError = null,
            actionError = null,
        )
        viewModelScope.launch {
            val category = current.category
            if (category == MessageCategory.DM) {
                repository.dmConversationsUpdates(forceRefresh)
                    .catch { setLoadFailure(category, it) }
                    .collect { update ->
                        val latest = _state.value
                        if (latest.category == category) {
                            _state.value = latest.copy(
                                conversations = update.data,
                                loading = false,
                                refreshing = update.refreshing,
                                loadingMore = false,
                                page = 1,
                                total = update.data.size,
                                hasMore = false,
                                error = null,
                                refreshError = update.error?.message,
                                lastUpdatedAt = update.savedAtMillis,
                            )
                        }
                    }
            } else {
                repository.messagesUpdates(category, page, forceRefresh = forceRefresh)
                    .catch { setLoadFailure(category, it) }
                    .collect { update ->
                        val latest = _state.value
                        if (latest.category == category) {
                            val result = update.data
                            _state.value = latest.copy(
                                messages = if (append) {
                                    (latest.messages + result.items).distinctBy { it.id }
                                } else result.items,
                                loading = false,
                                refreshing = update.refreshing,
                                loadingMore = false,
                                page = result.page,
                                total = result.total,
                                hasMore = result.hasMore,
                                error = null,
                                refreshError = update.error?.message,
                                lastUpdatedAt = update.savedAtMillis,
                            )
                        }
                    }
            }
        }
    }

    private fun setLoadFailure(category: MessageCategory, throwable: Throwable) {
        val latest = _state.value
        if (latest.category == category) {
            val message = throwable.message ?: "消息加载失败"
            val hasContent = latest.messages.isNotEmpty() || latest.conversations.isNotEmpty()
            _state.value = latest.copy(
                loading = false,
                refreshing = false,
                loadingMore = false,
                error = message.takeIf { !hasContent },
                refreshError = message.takeIf { hasContent },
            )
        }
    }
}

data class DmThreadState(
    val messages: List<DmMessage> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val refreshError: String? = null,
    val lastUpdatedAt: Long? = null,
)

class DmThreadViewModel(
    private val peer: UserSummary,
    private val repository: LightNovelRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DmThreadState())
    val state: StateFlow<DmThreadState> = _state.asStateFlow()

    init { refresh(forceRefresh = false) }

    fun refresh(forceRefresh: Boolean = true) {
        val hasContent = _state.value.messages.isNotEmpty()
        _state.value = _state.value.copy(
            loading = !hasContent,
            refreshing = hasContent && forceRefresh,
            error = null,
            refreshError = null,
        )
        viewModelScope.launch {
            repository.dmMessagesUpdates(peer.uid, peer, forceRefresh)
                .catch { throwable ->
                    val current = _state.value
                    val message = throwable.message ?: "私信加载失败"
                    _state.value = if (current.messages.isEmpty()) {
                        current.copy(loading = false, refreshing = false, error = message)
                    } else {
                        current.copy(loading = false, refreshing = false, refreshError = message)
                    }
                }
                .collect { update ->
                    _state.value = _state.value.copy(
                        messages = update.data,
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
