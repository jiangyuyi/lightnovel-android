package io.github.jiangyuyi.lightnovel.feature.profile

import io.github.jiangyuyi.lightnovel.core.model.WelfareSign
import io.github.jiangyuyi.lightnovel.core.cache.CacheSource
import io.github.jiangyuyi.lightnovel.core.cache.CacheUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow
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

    @Test fun `cold opening displays cache while eligibility is being refreshed`() = runTest(dispatcher) {
        val response = CompletableDeferred<WelfareSign>()
        var claims = 0
        val vm = WelfareViewModel({ error("must use cache stream") }, { claims++ }, {
            flow {
                emit(CacheUpdate(data(), CacheSource.CACHE, refreshing = true, savedAtMillis = 100))
                emit(CacheUpdate(response.await(), CacheSource.NETWORK, savedAtMillis = 200))
            }
        })
        vm.refresh()
        advanceUntilIdle()
        assertEquals(data(), vm.state.value.data)
        assertTrue(vm.state.value.loading)
        assertTrue(vm.state.value.cached)
        assertFalse(vm.state.value.verified)
        vm.signIn()
        assertEquals(0, claims)
        response.complete(data(true))
        advanceUntilIdle()
        assertTrue(vm.state.value.verified)
        assertFalse(vm.state.value.cached)
        assertEquals(200L, vm.state.value.savedAtMillis)
        assertTrue(vm.state.value.data!!.claimed)
    }

    @Test fun `failed cold refresh retains cached cycle and never authorizes claim`() = runTest(dispatcher) {
        var claims = 0
        val old = data().copy(serverDate = "2026-09-26", currentDay = 7)
        val vm = WelfareViewModel({ error("must use cache stream") }, { claims++ }, {
            flow {
                emit(CacheUpdate(old, CacheSource.CACHE, refreshing = true, savedAtMillis = 100))
                emit(CacheUpdate(old, CacheSource.CACHE, savedAtMillis = 100, error = IOException("offline")))
            }
        })
        vm.refresh()
        advanceUntilIdle()
        assertEquals(old, vm.state.value.data)
        assertFalse(vm.state.value.loading)
        assertFalse(vm.state.value.verified)
        assertNotNull(vm.state.value.error)
        vm.signIn()
        advanceUntilIdle()
        assertEquals(0, claims)
    }

    @Test fun `claim reconciliation ignores cached claimed flag on network failure`() = runTest(dispatcher) {
        var claims = 0
        val vm = WelfareViewModel({ error("must use cache stream") }, { claims++ }, {
            flow {
                if (claims == 0) emit(CacheUpdate(data(), CacheSource.NETWORK, savedAtMillis = 100))
                else {
                    emit(CacheUpdate(data(true), CacheSource.CACHE, refreshing = true, savedAtMillis = 100))
                    emit(CacheUpdate(data(true), CacheSource.CACHE, savedAtMillis = 100, error = IOException("offline")))
                }
            }
        })
        vm.refresh()
        advanceUntilIdle()
        vm.signIn()
        advanceUntilIdle()
        assertEquals(1, claims)
        assertFalse(vm.state.value.verified)
        assertFalse(vm.state.value.data!!.claimed)
        assertNull(vm.state.value.message)
        assertNotNull(vm.state.value.error)
    }

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
