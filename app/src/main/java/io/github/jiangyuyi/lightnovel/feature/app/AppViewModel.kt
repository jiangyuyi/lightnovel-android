package io.github.jiangyuyi.lightnovel.feature.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jiangyuyi.lightnovel.core.data.LightNovelRepository
import io.github.jiangyuyi.lightnovel.core.model.Session
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AppViewModel(private val repository: LightNovelRepository) : ViewModel() {
    val session: StateFlow<Session> = repository.session

    init {
        viewModelScope.launch { repository.restoreSession() }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }
}
