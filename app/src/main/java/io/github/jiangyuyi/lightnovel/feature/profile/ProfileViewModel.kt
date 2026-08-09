package io.github.jiangyuyi.lightnovel.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jiangyuyi.lightnovel.core.data.LightNovelRepository
import io.github.jiangyuyi.lightnovel.core.model.AccountProfile
import io.github.jiangyuyi.lightnovel.core.model.MessageSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class ProfileState(
    val profile: AccountProfile? = null,
    val messageSummary: MessageSummary = MessageSummary(),
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val refreshError: String? = null,
    val lastUpdatedAt: Long? = null,
)

class ProfileViewModel(private val repository: LightNovelRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()
    private var refreshJob: Job? = null

    fun refresh(loggedIn: Boolean, forceRefresh: Boolean = false) {
        if (!loggedIn) {
            refreshJob?.cancel()
            _state.value = ProfileState()
            return
        }
        refreshJob?.cancel()
        val hasContent = _state.value.profile != null
        _state.value = _state.value.copy(
            loading = !hasContent,
            refreshing = hasContent && forceRefresh,
            error = null,
            refreshError = null,
        )
        refreshJob = viewModelScope.launch {
            launch {
                repository.profileUpdates(forceRefresh)
                    .catch { throwable ->
                        val current = _state.value
                        val message = throwable.message ?: "个人资料加载失败"
                        _state.value = if (current.profile == null) {
                            current.copy(loading = false, refreshing = false, error = message)
                        } else {
                            current.copy(loading = false, refreshing = false, refreshError = message)
                        }
                    }
                    .collect { update ->
                        _state.value = _state.value.copy(
                            profile = update.data,
                            loading = false,
                            refreshing = update.refreshing,
                            error = null,
                            refreshError = update.error?.message,
                            lastUpdatedAt = update.savedAtMillis,
                        )
                    }
            }
            launch {
                repository.messageSummaryUpdates(forceRefresh)
                    .catch { }
                    .collect { update ->
                        _state.value = _state.value.copy(messageSummary = update.data)
                    }
            }
        }
    }
}
