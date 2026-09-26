package io.github.jiangyuyi.lightnovel.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jiangyuyi.lightnovel.core.model.WelfareSign
import io.github.jiangyuyi.lightnovel.core.network.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WelfareState(
    val data: WelfareSign? = null,
    val loading: Boolean = false,
    val claiming: Boolean = false,
    val verified: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class WelfareViewModel(
    private val load: suspend () -> WelfareSign,
    private val claim: suspend () -> Unit,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WelfareState())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        if (job?.isActive == true) return
        mutableState.value = mutableState.value.copy(loading = true, verified = false, error = null)
        job = viewModelScope.launch {
            try {
                mutableState.value = mutableState.value.copy(data = load(), loading = false, verified = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = mutableState.value.copy(loading = false, error = readableError(e))
            }
        }
    }

    fun signIn() {
        val current = mutableState.value
        if (job?.isActive == true || !current.verified || current.data?.claimable != true) return
        mutableState.value = current.copy(claiming = true, verified = false, message = null, error = null)
        job = viewModelScope.launch {
            var claimError: String? = null
            try {
                claim()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                claimError = readableError(e)
            }
            // Always reconcile with the server, even after a timeout. Never retry a claim automatically.
            try {
                val latest = load()
                mutableState.value = WelfareState(
                    data = latest,
                    verified = true,
                    message = if (latest.claimed) "今日签到已领取，余额已更新" else null,
                    error = if (latest.claimed) null else claimError ?: "服务器尚未确认领取，请刷新状态后再试",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = mutableState.value.copy(
                    claiming = false,
                    error = "领取结果暂未确认，请先刷新状态，勿重复领取。${readableError(e)}",
                )
            }
        }
    }

    private fun readableError(e: Exception): String = when {
        e is ApiException && e.businessCode == 8 -> "登录已失效，请重新登录后签到"
        else -> e.message ?: "网络连接失败，请稍后重试"
    }
}
