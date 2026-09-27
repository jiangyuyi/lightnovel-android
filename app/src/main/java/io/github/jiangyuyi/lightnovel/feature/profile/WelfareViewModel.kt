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
    private var inconsistentClaimDate: String? = null

    fun refresh() {
        if (job?.isActive == true) return
        mutableState.value = mutableState.value.copy(loading = true, verified = false, message = null, error = null)
        job = viewModelScope.launch {
            try {
                val latest = load()
                if (latest.claimed || (!inconsistentClaimDate.isNullOrBlank() && latest.serverDate.isNotBlank() && latest.serverDate != inconsistentClaimDate)) {
                    inconsistentClaimDate = null
                }
                mutableState.value = mutableState.value.copy(
                    data = latest, loading = false, verified = inconsistentClaimDate == null,
                    message = null,
                    error = if (inconsistentClaimDate != null) INCONSISTENT_STATE else null,
                )
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
                claimError = readableError(e, claimResultUncertain = true)
            }
            // Always reconcile with the server, even after a timeout. Never retry a claim automatically.
            try {
                val latest = load()
                val before = requireNotNull(current.data)
                val shifted = !latest.claimed && (
                    (before.currentDay > 0 && latest.currentDay > 0 && before.currentDay != latest.currentDay) ||
                    (before.cycleStartDate.isNotBlank() && latest.cycleStartDate.isNotBlank() && before.cycleStartDate != latest.cycleStartDate)
                )
                if (shifted) inconsistentClaimDate = latest.serverDate
                mutableState.value = WelfareState(
                    data = latest,
                    verified = latest.claimed,
                    message = if (latest.claimed) "今日签到已领取" else null,
                    error = when {
                        latest.claimed -> null
                        shifted -> INCONSISTENT_STATE
                        else -> claimError ?: "服务器尚未确认领取，请刷新状态后再试"
                    },
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

    private fun readableError(e: Exception, claimResultUncertain: Boolean = false): String = when {
        e is ApiException && e.businessCode == 8 -> "登录已失效，请重新登录后签到"
        e is java.io.IOException && e !is ApiException -> if (claimResultUncertain) {
            "网络连接异常，领取结果需重新查询确认；请勿连续点击领取"
        } else "网络连接异常，请稍后刷新"
        else -> e.message ?: "网络连接失败，请稍后重试"
    }

    private companion object {
        const val INCONSISTENT_STATE = "签到天数发生变化，但服务器未确认今日已领。已暂停本页领取，请先在官方 App 核对，勿重复提交。"
    }
}
