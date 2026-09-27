package io.github.jiangyuyi.lightnovel.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SignDay(val day: Int, val amount: Int, val claimed: Boolean, val claimable: Boolean)

@Serializable
data class WelfareSign(
    val coin: Int?,
    val todayCoin: Int?,
    val title: String,
    val description: String,
    val claimed: Boolean,
    val claimable: Boolean,
    val buttonText: String,
    val days: List<SignDay>,
    val currentDay: Int = 0,
    val cycleStartDate: String = "",
    val serverDate: String = "",
)
