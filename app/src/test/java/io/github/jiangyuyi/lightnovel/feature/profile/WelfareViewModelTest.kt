package io.github.jiangyuyi.lightnovel.feature.profile

import io.github.jiangyuyi.lightnovel.core.model.WelfareSign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WelfareViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private fun data(claimed: Boolean = false) = WelfareSign(100, 0, "签到", "", claimed, !claimed, "领取", emptyList())
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `duplicate taps submit only once and reconcile balance`() = runTest(dispatcher) {
        var claims = 0
        val vm = WelfareViewModel({ data(claims > 0) }, { claims++ })
        vm.signIn()
        assertEquals(0, claims)
        vm.refresh()
        advanceUntilIdle()
        vm.signIn()
        vm.signIn()
        advanceUntilIdle()
        assertEquals(1, claims)
        assertTrue(vm.state.value.data!!.claimed)
        assertTrue(vm.state.value.verified)
        assertNotNull(vm.state.value.message)
        vm.signIn()
        advanceUntilIdle()
        assertEquals(1, claims)
    }

    @Test fun `timeout followed by confirmed claim is treated as claimed`() = runTest(dispatcher) {
        var submitted = false
        val vm = WelfareViewModel({ data(submitted) }, { submitted = true; throw IOException("timeout") })
        vm.refresh()
        advanceUntilIdle()
        vm.signIn()
        advanceUntilIdle()
        assertTrue(vm.state.value.data!!.claimed)
        assertNull(vm.state.value.error)
    }

    @Test fun `failed reconciliation locks claims until a successful refresh`() = runTest(dispatcher) {
        var offline = false
        var claims = 0
        val vm = WelfareViewModel({ if (offline) throw IOException("offline") else data() }, { claims++; offline = true })
        vm.refresh()
        advanceUntilIdle()
        vm.signIn()
        advanceUntilIdle()
        assertFalse(vm.state.value.verified)
        assertFalse(vm.state.value.claiming)
        assertNotNull(vm.state.value.error)
        vm.signIn()
        advanceUntilIdle()
        assertEquals(1, claims)
        offline = false
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value.verified)
    }

    @Test fun `refresh error preserves visible data but disables claims`() = runTest(dispatcher) {
        var offline = false
        val vm = WelfareViewModel({ if (offline) throw IOException("offline") else data() }, {})
        vm.refresh()
        advanceUntilIdle()
        offline = true
        vm.refresh()
        advanceUntilIdle()
        assertNotNull(vm.state.value.data)
        assertFalse(vm.state.value.verified)
        assertNotNull(vm.state.value.error)
    }

    @Test fun `advanced day without confirmed claim stays blocked after refresh`() = runTest(dispatcher) {
        var day = 2
        var claims = 0
        val vm = WelfareViewModel(
            { data().copy(currentDay = day, serverDate = "2026-09-27") },
            { claims++; day++; throw IOException("ERR_QUIC_PROTOCOL_ERROR") },
        )
        vm.refresh()
        advanceUntilIdle()
        vm.signIn()
        advanceUntilIdle()
        assertFalse(vm.state.value.verified)
        assertTrue(vm.state.value.error!!.contains("签到天数发生变化"))
        vm.refresh()
        advanceUntilIdle()
        vm.signIn()
        advanceUntilIdle()
        assertFalse(vm.state.value.verified)
        assertEquals(1, claims)
    }

    @Test fun `network failure with unchanged day requires explicit refresh`() = runTest(dispatcher) {
        val vm = WelfareViewModel({ data() }, { throw IOException("ERR_QUIC_PROTOCOL_ERROR") })
        vm.refresh()
        advanceUntilIdle()
        vm.signIn()
        advanceUntilIdle()
        assertFalse(vm.state.value.verified)
        assertFalse(vm.state.value.error!!.contains("QUIC"))
    }

    @Test fun `next server date unlocks an inconsistent claim`() = runTest(dispatcher) {
        var day = 2
        var date = "2026-09-27"
        val vm = WelfareViewModel(
            { data().copy(currentDay = day, serverDate = date) },
            { day++ },
        )
        vm.refresh()
        advanceUntilIdle()
        vm.signIn()
        advanceUntilIdle()
        assertFalse(vm.state.value.verified)
        date = "2026-09-28"
        vm.refresh()
        advanceUntilIdle()
        assertTrue(vm.state.value.verified)
        assertNull(vm.state.value.error)
    }

    @Test fun `failed refresh clears a previous successful claim message`() = runTest(dispatcher) {
        var claimed = false
        var offline = false
        val vm = WelfareViewModel(
            { if (offline) throw IOException("offline") else data(claimed) },
            { claimed = true },
        )
        vm.refresh()
        advanceUntilIdle()
        vm.signIn()
        advanceUntilIdle()
        assertNotNull(vm.state.value.message)
        offline = true
        vm.refresh()
        assertNull(vm.state.value.message)
        advanceUntilIdle()
        assertNull(vm.state.value.message)
        assertNotNull(vm.state.value.error)
    }
}
