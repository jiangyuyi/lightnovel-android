package io.github.jiangyuyi.lightnovel.core.model

data class SignDay(val day: Int, val amount: Int, val claimed: Boolean, val claimable: Boolean)

data class WelfareSign(
    val coin: Int?,
    val todayCoin: Int?,
    val title: String,
    val description: String,
    val claimed: Boolean,
    val claimable: Boolean,
    val buttonText: String,
    val days: List<SignDay>,
)
