package io.github.jiangyuyi.lightnovel.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.*
import org.junit.Test

class WelfareParserTest {
    private fun parse(raw: String) = parseWelfareSign(Json.parseToJsonElement(raw) as JsonObject)

    @Test fun `cycle and day are taken from server rather than device date`() {
        val result = parse("""{"server_time":"2026-09-27 01:19:30","sign_in":{"current_day":1,"cycle_start_date":"2026-09-27","enabled":1,"claimable":1}}""")
        assertEquals(1, result.currentDay)
        assertEquals("2026-09-27", result.cycleStartDate)
        assertEquals("2026-09-27", result.serverDate)
    }

    @Test fun `server reward amounts and zero balance are preserved`() {
        val result = parse("""{"wallet":{"coin":0,"today_coin":0},"sign_in":{"enabled":1,"claimable":1,"claimed":0,"button_text":"领取","rewards":[{"day":1,"reward_amount":138,"claimable":1},{"day":2,"reward_amount":154}]}}""")
        assertEquals(0, result.coin)
        assertTrue(result.claimable)
        assertEquals(138, result.days.first().amount)
        assertTrue(result.days.first().claimable)
    }

    @Test fun `already claimed wins over inconsistent claimable flag`() {
        val result = parse("""{"sign_in":{"enabled":true,"claimed":true,"claimable":true}}""")
        assertTrue(result.claimed)
        assertFalse(result.claimable)
        assertEquals("今日已领取", result.buttonText)
        assertNull(result.coin)
    }

    @Test fun `missing eligibility fails closed`() {
        assertFalse(parse("""{"sign_in":{}}""").claimable)
        assertFalse(parse("""{"sign_in":{"enabled":0,"claimable":1}}""").claimable)
    }

    @Test(expected = ApiException::class) fun `guest cannot sign in`() {
        parse("""{"sign_in":{"status":"guest","claimable":1}}""")
    }

    @Test(expected = ApiException::class) fun `missing sign module is not a valid empty response`() {
        parse("""{"wallet":{"coin":100}}""")
    }
}
