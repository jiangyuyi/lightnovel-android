package io.github.jiangyuyi.lightnovel.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jiangyuyi.lightnovel.core.data.LightNovelRepository
import io.github.jiangyuyi.lightnovel.core.model.ChapterDetail
import io.github.jiangyuyi.lightnovel.core.model.LocalReadingProgress
import io.github.jiangyuyi.lightnovel.core.model.ReaderPreferences
import io.github.jiangyuyi.lightnovel.core.preferences.ReaderPreferencesStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ReaderState(
    val chapter: ChapterDetail? = null,
    val preferences: ReaderPreferences = ReaderPreferences(),
    val restoredParagraph: Int = 0,
    val controlsVisible: Boolean = false,
    val settingsVisible: Boolean = false,
    val unlockPromptVisible: Boolean = false,
    val coinBalance: Int? = null,
    val coinBalanceLoading: Boolean = false,
    val loggedIn: Boolean = false,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val refreshError: String? = null,
    val lastUpdatedAt: Long? = null,
)

class ReaderViewModel(
    private val bookId: Long,
    initialChapterId: Long,
    private val repository: LightNovelRepository,
    private val preferenceStore: ReaderPreferencesStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()
    private var currentChapterId = initialChapterId
    private var chapterLoadJob: Job? = null
    private var progressJob: Job? = null
    private var settingsSyncJob: Job? = null

    init {
        viewModelScope.launch {
            repository.session.collect { session ->
                _state.value = _state.value.copy(loggedIn = session.loggedIn)
            }
        }
        viewModelScope.launch {
            preferenceStore.preferences.collect { preferences ->
                _state.value = _state.value.copy(preferences = preferences)
            }
        }
        loadChapter(initialChapterId)
    }

    fun loadChapter(chapterId: Long, forceRefresh: Boolean = false) {
        if (chapterId <= 0) return
        chapterLoadJob?.cancel()
        progressJob?.cancel()
        val hasCurrentChapter = _state.value.chapter?.chapter?.id == chapterId
        currentChapterId = chapterId
        _state.value = _state.value.copy(
            chapter = _state.value.chapter.takeIf { hasCurrentChapter },
            loading = !hasCurrentChapter,
            refreshing = hasCurrentChapter && forceRefresh,
            error = null,
            refreshError = null,
            settingsVisible = false,
            controlsVisible = false,
        )
        chapterLoadJob = viewModelScope.launch {
            val progress = preferenceStore.progress(bookId).first()
            var accessRefreshScheduled = false
            repository.chapterUpdates(bookId, chapterId, forceRefresh)
                .catch { throwable ->
                    if (currentChapterId != chapterId) return@catch
                    val current = _state.value
                    val message = throwable.message ?: "章节加载失败"
                    _state.value = if (current.chapter == null) {
                        current.copy(loading = false, refreshing = false, error = message)
                    } else {
                        current.copy(loading = false, refreshing = false, refreshError = message)
                    }
                }
                .collect { update ->
                    if (currentChapterId != chapterId) return@collect
                    val accessError = update.data.readerAccessError()
                    _state.value = _state.value.copy(
                        chapter = update.data,
                        restoredParagraph = progress?.takeIf { it.chapterId == chapterId }?.paragraphIndex ?: 0,
                        loading = false,
                        refreshing = update.refreshing,
                        error = accessError,
                        refreshError = update.error?.message.takeIf { accessError == null },
                        lastUpdatedAt = update.savedAtMillis,
                        controlsVisible = false,
                        unlockPromptVisible = update.data.chapter.requiresCoinUnlock(),
                    )
                    if (update.data.chapter.requiresCoinUnlock()) refreshCoinBalance()
                    if (!forceRefresh && update.data.chapter.requiresCoinUnlock() && !accessRefreshScheduled) {
                        accessRefreshScheduled = true
                        viewModelScope.launch { loadChapter(chapterId, forceRefresh = true) }
                    }
                }
        }
    }

    fun previous() = _state.value.chapter?.previousChapterId?.let(::loadChapter)

    fun next() = _state.value.chapter?.nextChapterId?.let(::loadChapter)

    fun retry() = loadChapter(currentChapterId, forceRefresh = true)

    fun refreshAfterWebUnlock() {
        _state.value = _state.value.copy(coinBalance = null)
        loadChapter(currentChapterId, forceRefresh = true)
    }

    fun showUnlockPrompt(show: Boolean) {
        _state.value = _state.value.copy(unlockPromptVisible = show)
    }

    fun refreshCoinBalance(force: Boolean = false) {
        if (!repository.session.value.loggedIn || _state.value.coinBalanceLoading || (!force && _state.value.coinBalance != null)) return
        _state.value = _state.value.copy(coinBalanceLoading = true)
        viewModelScope.launch {
            runCatching { repository.myProfile().coin }
                .onSuccess { balance -> _state.value = _state.value.copy(coinBalance = balance, coinBalanceLoading = false) }
                .onFailure { _state.value = _state.value.copy(coinBalanceLoading = false, refreshError = it.message) }
        }
    }

    fun toggleControls() {
        _state.value = _state.value.copy(controlsVisible = !_state.value.controlsVisible)
    }

    fun showSettings(show: Boolean) {
        _state.value = _state.value.copy(settingsVisible = show, controlsVisible = true)
    }

    fun updatePreferences(transform: (ReaderPreferences) -> ReaderPreferences) {
        val previous = _state.value.preferences
        val updated = transform(previous)
        viewModelScope.launch { preferenceStore.update(updated) }
        // The three-way script preference is local; do not overwrite website settings
        // when this is the only change, or cancel an already pending settings sync.
        if (updated.copy(chineseScript = previous.chineseScript) == previous) return
        settingsSyncJob?.cancel()
        settingsSyncJob = viewModelScope.launch {
            delay(700)
            runCatching { repository.saveReaderSettings(updated) }
        }
    }

    fun saveProgress(paragraphIndex: Int, totalParagraphs: Int) {
        val chapter = _state.value.chapter ?: return
        val percent = if (totalParagraphs <= 1) 100 else {
            ((paragraphIndex.toFloat() / (totalParagraphs - 1)) * 100).toInt().coerceIn(0, 100)
        }
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            delay(800)
            preferenceStore.saveProgress(
                bookId,
                LocalReadingProgress(chapter.chapter.id, paragraphIndex, percent),
            )
            runCatching {
                repository.saveReadingProgress(
                    bookId = bookId,
                    volumeId = chapter.chapter.volumeId,
                    chapterId = chapter.chapter.id,
                    paragraphIndex = paragraphIndex,
                    percent = percent,
                )
            }
        }
    }
}
