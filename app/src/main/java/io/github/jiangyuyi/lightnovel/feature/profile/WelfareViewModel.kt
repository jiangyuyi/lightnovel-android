package io.github.jiangyuyi.lightnovel.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.jiangyuyi.lightnovel.core.cache.CacheSource
import io.github.jiangyuyi.lightnovel.core.cache.CacheUpdate
import io.github.jiangyuyi.lightnovel.core.model.WelfareSign
import io.github.jiangyuyi.lightnovel.core.network.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

data class WelfareState(
    val data: WelfareSign? = null,
    val loading: Boolean = false,
    val claiming: Boolean = false,
    val verified: Boolean = false,
    val cached: Boolean = false,
    val savedAtMillis: Long? = null,
    val message: String? = null,
    val error: String? = null,
)

class WelfareViewModel(
    private val load: suspend () -> WelfareSign,
    private val claim: suspend () -> Unit,
    private val updates: () -> Flow<CacheUpdate<WelfareSign>> = {
        flow { emit(CacheUpdate(load(), CacheSource.NETWORK, savedAtMillis = System.currentTimeMillis())) }
    },
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
                updates().collect { update ->
                    val latest = update.data
                    val live = update.source == CacheSource.NETWORK && update.error == null
                    if (live && (latest.claimed || (!inconsistentClaimDate.isNullOrBlank() && latest.serverDate.isNotBlank() && latest.serverDate != inconsistentClaimDate))) {
                        inconsistentClaimDate = null
                    }
                    mutableState.value = mutableState.value.copy(
                        data = latest, loading = update.refreshing, verified = live && inconsistentClaimDate == null,
                        cached = !live, savedAtMillis = update.savedAtMillis, message = null,
                        error = update.error?.let { readableError(it) }
                            ?: if (live && inconsistentClaimDate != null) INCONSISTENT_STATE else null,
                    )
                }
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
                // The same stream saves the confirmed result for the next cold launch.
                // Cached snapshots must never reconcile a mutation.
                val update = updates().last()
                update.error?.let { throw it }
                if (update.source != CacheSource.NETWORK) throw java.io.IOException("签到状态尚未确认")
                val latest = update.data
                val before = requireNotNull(current.data)
                val shifted = !latest.claimed && (
                    (before.currentDay > 0 && latest.currentDay > 0 && before.currentDay != latest.currentDay) ||
                    (before.cycleStartDate.isNotBlank() && latest.cycleStartDate.isNotBlank() && before.cycleStartDate != latest.cycleStartDate)
                )
                if (shifted) inconsistentClaimDate = latest.serverDate
                mutableState.value = WelfareState(
                    data = latest,
                    verified = latest.claimed,
                    savedAtMillis = update.savedAtMillis,
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

    private fun readableError(e: Throwable, claimResultUncertain: Boolean = false): String = when {
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
