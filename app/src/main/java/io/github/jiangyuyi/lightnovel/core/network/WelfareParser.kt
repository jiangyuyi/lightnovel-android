package io.github.jiangyuyi.lightnovel.core.network

import io.github.jiangyuyi.lightnovel.core.model.SignDay
import io.github.jiangyuyi.lightnovel.core.model.WelfareSign
import kotlinx.serialization.json.JsonObject

internal fun parseWelfareSign(data: JsonObject): WelfareSign {
    val sign = data.obj("sign_in") ?: throw ApiException("签到数据不完整，请稍后刷新")
    if (data.obj("guest")?.bool("is_logged_in") == false || sign.string("status") == "guest") {
        throw ApiException("登录已失效，请重新登录后签到", businessCode = 8)
    }
    val wallet = data.obj("wallet")
    val claimed = sign.bool("claimed") == true
    return WelfareSign(
        coin = wallet?.takeIf { it.element("coin") != null }?.int("coin"),
        todayCoin = wallet?.takeIf { it.element("today_coin") != null }?.int("today_coin"),
        title = sign.string("title").ifBlank { "每日签到" },
        description = sign.string("sub_title"),
        claimed = claimed,
        claimable = sign.bool("enabled") == true && sign.bool("claimable") == true && !claimed,
        buttonText = if (claimed) "今日已领取" else sign.string("button_text").ifBlank { "暂不可领取" },
        days = sign.array("rewards").mapNotNull { item ->
            val day = item as? JsonObject ?: return@mapNotNull null
            if (day.int("day") <= 0) return@mapNotNull null
            SignDay(day.int("day"), day.int("reward_amount"), day.bool("claimed") == true, day.bool("claimable") == true)
        },
    )
}
