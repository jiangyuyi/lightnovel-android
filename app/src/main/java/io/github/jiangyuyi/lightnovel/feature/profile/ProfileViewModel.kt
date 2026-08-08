package io.github.jiangyuyi.lightnovel.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jiangyuyi.lightnovel.core.data.LightNovelRepository
import io.github.jiangyuyi.lightnovel.core.model.AccountProfile
import io.github.jiangyuyi.lightnovel.core.model.MessageSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileState(
    val profile: AccountProfile? = null,
    val messageSummary: MessageSummary = MessageSummary(),
    val loading: Boolean = false,
    val error: String? = null,
)

class ProfileViewModel(private val repository: LightNovelRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    fun refresh(loggedIn: Boolean) {
        if (!loggedIn) {
            _state.value = ProfileState()
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val profile = runCatching { repository.myProfile() }
            val messages = runCatching { repository.messageSummary() }.getOrDefault(MessageSummary())
            profile.onSuccess {
                _state.value = ProfileState(profile = it, messageSummary = messages)
            }.onFailure {
                _state.value = _state.value.copy(
                    loading = false,
                    messageSummary = messages,
                    error = it.message ?: "个人资料加载失败",
                )
            }
        }
    }
}
