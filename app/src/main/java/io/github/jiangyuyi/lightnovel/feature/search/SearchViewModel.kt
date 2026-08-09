package io.github.jiangyuyi.lightnovel.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jiangyuyi.lightnovel.core.data.LightNovelRepository
import io.github.jiangyuyi.lightnovel.core.model.BookSummary
import io.github.jiangyuyi.lightnovel.core.model.SearchTaxonomy
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val workType: String = "",
    val primaryTag: String = "",
    val taxonomy: SearchTaxonomy = SearchTaxonomy(emptyList(), emptyList()),
    val results: List<BookSummary> = emptyList(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val refreshError: String? = null,
    val lastUpdatedAt: Long? = null,
)

class SearchViewModel(private val repository: LightNovelRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.taxonomyUpdates().catch { }.collect { update ->
                _state.value = _state.value.copy(taxonomy = update.data)
            }
        }
    }

    fun setQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun setWorkType(value: String) {
        _state.value = _state.value.copy(
            query = "",
            primaryTag = "",
            workType = if (_state.value.workType == value) "" else value,
        )
        search()
    }

    fun setTag(value: String) {
        _state.value = _state.value.copy(
            query = "",
            workType = "",
            primaryTag = if (_state.value.primaryTag == value) "" else value,
        )
        search()
    }

    fun search(forceRefresh: Boolean = false) {
        searchJob?.cancel()
        val hasContent = _state.value.results.isNotEmpty()
        _state.value = _state.value.copy(
            loading = !hasContent,
            refreshing = hasContent && forceRefresh,
            error = null,
            refreshError = null,
        )
        searchJob = viewModelScope.launch {
            val request = _state.value
            repository.searchUpdates(
                request.query,
                request.workType,
                request.primaryTag,
                forceRefresh = forceRefresh,
            ).catch { throwable ->
                val current = _state.value
                val message = throwable.message ?: "搜索失败"
                _state.value = if (current.results.isEmpty()) {
                    current.copy(loading = false, refreshing = false, error = message)
                } else {
                    current.copy(loading = false, refreshing = false, refreshError = message)
                }
            }.collect { update ->
                _state.value = _state.value.copy(
                    results = update.data.items,
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
