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
    val loadingMore: Boolean = false,
    val markingRead: Boolean = false,
    val error: String? = null,
    val actionError: String? = null,
)

class MessagesViewModel(
    private val repository: LightNovelRepository,
    initialCategory: MessageCategory = MessageCategory.REPLY,
) : ViewModel() {
    private val _state = MutableStateFlow(MessagesState(category = initialCategory))
    val state: StateFlow<MessagesState> = _state.asStateFlow()

    init {
        refreshSummary()
        refresh()
    }

    fun select(category: MessageCategory) {
        if (_state.value.category == category &&
            (_state.value.messages.isNotEmpty() || _state.value.conversations.isNotEmpty())
        ) return
        _state.value = MessagesState(category = category, summary = _state.value.summary)
        refresh()
    }

    fun refresh() = load(page = 1, append = false)

    fun refreshSummary() {
        viewModelScope.launch {
            runCatching { repository.messageSummary() }
                .onSuccess { _state.value = _state.value.copy(summary = it) }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.category != MessageCategory.DM && current.hasMore && !current.loading && !current.loadingMore) {
            load(current.page + 1, append = true)
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

    private fun load(page: Int, append: Boolean) {
        val current = _state.value
        if (current.loading || current.loadingMore) return
        _state.value = current.copy(
            loading = !append,
            loadingMore = append,
            error = null,
            actionError = null,
        )
        viewModelScope.launch {
            val category = current.category
            if (category == MessageCategory.DM) {
                runCatching { repository.dmConversations() }
                    .onSuccess { conversations ->
                        val latest = _state.value
                        if (latest.category == category) {
                            _state.value = latest.copy(
                                conversations = conversations,
                                loading = false,
                                loadingMore = false,
                                page = 1,
                                total = conversations.size,
                                hasMore = false,
                            )
                        }
                    }
                    .onFailure { setLoadFailure(category, it) }
            } else {
                runCatching { repository.messages(category, page) }
                    .onSuccess { result ->
                        val latest = _state.value
                        if (latest.category == category) {
                            _state.value = latest.copy(
                                messages = if (append) {
                                    (latest.messages + result.items).distinctBy { it.id }
                                } else result.items,
                                loading = false,
                                loadingMore = false,
                                page = result.page,
                                total = result.total,
                                hasMore = result.hasMore,
                            )
                        }
                    }
                    .onFailure { setLoadFailure(category, it) }
            }
        }
    }

    private fun setLoadFailure(category: MessageCategory, throwable: Throwable) {
        val latest = _state.value
        if (latest.category == category) {
            _state.value = latest.copy(
                loading = false,
                loadingMore = false,
                error = throwable.message ?: "消息加载失败",
            )
        }
    }
}

data class DmThreadState(
    val messages: List<DmMessage> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class DmThreadViewModel(
    private val peer: UserSummary,
    private val repository: LightNovelRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DmThreadState())
    val state: StateFlow<DmThreadState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.dmMessages(peer.uid, peer) }
                .onSuccess { _state.value = DmThreadState(messages = it) }
                .onFailure {
                    _state.value = DmThreadState(error = it.message ?: "私信加载失败")
                }
        }
    }
}
