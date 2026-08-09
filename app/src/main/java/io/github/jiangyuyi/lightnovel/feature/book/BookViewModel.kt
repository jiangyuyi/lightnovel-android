package io.github.jiangyuyi.lightnovel.feature.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jiangyuyi.lightnovel.core.data.LightNovelRepository
import io.github.jiangyuyi.lightnovel.core.model.BookDetail
import io.github.jiangyuyi.lightnovel.core.model.ChapterSummary
import io.github.jiangyuyi.lightnovel.core.model.Comment
import io.github.jiangyuyi.lightnovel.core.model.Volume
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class BookState(
    val detail: BookDetail? = null,
    val volumes: List<Volume> = emptyList(),
    val chapters: Map<Long, List<ChapterSummary>> = emptyMap(),
    val expandedVolumeId: Long? = null,
    val loadingVolumeId: Long? = null,
    val comments: List<Comment> = emptyList(),
    val commentsAvailable: Boolean = true,
    val inBookshelf: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val shelfLoading: Boolean = false,
    val error: String? = null,
    val refreshError: String? = null,
    val lastUpdatedAt: Long? = null,
)

class BookViewModel(
    private val bookId: Long,
    private val repository: LightNovelRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BookState())
    val state: StateFlow<BookState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var detailRefreshing = false
    private var volumesRefreshing = false
    private var commentsRefreshing = false
    private var shelfMembership: Boolean? = null

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        val hasContent = _state.value.detail != null
        _state.value = _state.value.copy(
            loading = !hasContent,
            refreshing = hasContent && forceRefresh,
            error = null,
            refreshError = null,
        )
        loadJob = viewModelScope.launch {
            launch {
                repository.bookDetailUpdates(bookId, forceRefresh)
                    .catch { throwable -> handleEssentialFailure(throwable) }
                    .collect { update ->
                        detailRefreshing = update.refreshing
                        val shelfState = shelfMembership ?: update.data.book.inBookshelf
                        _state.value = _state.value.copy(
                            detail = update.data,
                            inBookshelf = shelfState ?: _state.value.inBookshelf,
                            loading = false,
                            refreshing = isRefreshing(),
                            error = null,
                            refreshError = update.error?.message,
                            lastUpdatedAt = update.savedAtMillis,
                        )
                    }
            }
            launch {
                repository.volumesUpdates(bookId, forceRefresh = forceRefresh)
                    .catch { throwable -> handleSecondaryFailure(throwable) }
                    .collect { update ->
                        volumesRefreshing = update.refreshing
                        _state.value = _state.value.copy(
                            volumes = update.data.items,
                            refreshing = isRefreshing(),
                            refreshError = update.error?.message ?: _state.value.refreshError,
                            lastUpdatedAt = maxOf(_state.value.lastUpdatedAt ?: 0L, update.savedAtMillis),
                        )
                    }
            }
            launch {
                repository.commentsUpdates(bookId, forceRefresh = forceRefresh)
                    .catch { throwable ->
                        commentsRefreshing = false
                        _state.value = _state.value.copy(
                            commentsAvailable = _state.value.comments.isNotEmpty(),
                            refreshing = isRefreshing(),
                            refreshError = throwable.message ?: "评论加载失败",
                        )
                    }
                    .collect { update ->
                        commentsRefreshing = update.refreshing
                        _state.value = _state.value.copy(
                            comments = update.data.items,
                            commentsAvailable = true,
                            refreshing = isRefreshing(),
                            refreshError = update.error?.message ?: _state.value.refreshError,
                        )
                    }
            }
            if (repository.session.value.loggedIn) {
                launch {
                    repository.bookshelfUpdates()
                        .catch { }
                        .collect { update ->
                            val inShelf = update.data.any { it.id == bookId }
                            shelfMembership = inShelf
                            _state.value = _state.value.copy(inBookshelf = inShelf)
                        }
                }
            }
        }
    }

    fun toggleVolume(volumeId: Long) {
        if (_state.value.expandedVolumeId == volumeId) {
            _state.value = _state.value.copy(expandedVolumeId = null)
            return
        }
        _state.value = _state.value.copy(expandedVolumeId = volumeId)
        if (_state.value.chapters.containsKey(volumeId)) return
        _state.value = _state.value.copy(loadingVolumeId = volumeId, refreshError = null)
        viewModelScope.launch {
            repository.chaptersUpdates(bookId, volumeId)
                .catch { throwable ->
                    _state.value = _state.value.copy(
                        loadingVolumeId = null,
                        refreshError = throwable.message ?: "章节目录加载失败",
                    )
                }
                .collect { update ->
                    _state.value = _state.value.copy(
                        chapters = _state.value.chapters + (volumeId to update.data.items),
                        loadingVolumeId = if (update.refreshing) volumeId else null,
                        refreshError = update.error?.message,
                    )
                }
        }
    }

    fun toggleBookshelf(onLoginRequired: () -> Unit) {
        if (!repository.session.value.loggedIn) {
            onLoginRequired()
            return
        }
        if (_state.value.shelfLoading) return
        val target = !_state.value.inBookshelf
        _state.value = _state.value.copy(shelfLoading = true)
        viewModelScope.launch {
            runCatching { repository.setBookshelf(bookId, target) }
                .onSuccess {
                    shelfMembership = it
                    _state.value = _state.value.copy(inBookshelf = it, shelfLoading = false)
                }
                .onFailure { _state.value = _state.value.copy(shelfLoading = false, error = it.message) }
        }
    }

    suspend fun readingTarget(): Long = repository.readerBootstrapUpdates(bookId).first().data.chapterId

    private fun isRefreshing(): Boolean = detailRefreshing || volumesRefreshing || commentsRefreshing

    private fun handleEssentialFailure(throwable: Throwable) {
        detailRefreshing = false
        val message = throwable.message ?: "书籍加载失败"
        _state.value = if (_state.value.detail == null) {
            _state.value.copy(loading = false, refreshing = isRefreshing(), error = message)
        } else {
            _state.value.copy(loading = false, refreshing = isRefreshing(), refreshError = message)
        }
    }

    private fun handleSecondaryFailure(throwable: Throwable) {
        volumesRefreshing = false
        _state.value = _state.value.copy(
            refreshing = isRefreshing(),
            refreshError = throwable.message ?: "目录加载失败",
        )
    }
}
