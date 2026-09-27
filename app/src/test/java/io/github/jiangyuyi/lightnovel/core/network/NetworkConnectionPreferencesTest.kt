package io.github.jiangyuyi.lightnovel.core.network

import org.junit.Assert.*
import org.junit.Test

class NetworkConnectionPreferencesTest {
    @Test fun `write reuses successful read protocol and gets only one attempt`() {
        val preferences = NetworkConnectionPreferences()
        preferences.recordSuccess("api.lightnovel.fun", NetworkProtocol.HTTP2)
        assertEquals(listOf(NetworkProtocol.HTTP2), preferences.attemptsFor("api.lightnovel.fun", false))
    }

    @Test fun `read tries alternatives and a bounded final transient retry`() {
        val attempts = NetworkConnectionPreferences().attemptsFor("api.lightnovel.fun", true)
        assertEquals(4, attempts.size)
        assertEquals(NetworkProtocol.entries, attempts.take(3))
        assertEquals(attempts.first(), attempts.last())
    }

    @Test fun `preferences are isolated by hostname`() {
        val preferences = NetworkConnectionPreferences()
        preferences.recordSuccess("api.lightnovel.fun", NetworkProtocol.HTTP1)
        assertEquals(listOf(NetworkProtocol.AUTO), preferences.attemptsFor("res.lightnovel.fun", false))
    }

    @Test fun `a failed preferred connection is forgotten`() {
        val preferences = NetworkConnectionPreferences()
        preferences.recordSuccess("api.lightnovel.fun", NetworkProtocol.HTTP1)
        preferences.recordFailure("api.lightnovel.fun", NetworkProtocol.HTTP1)
        assertEquals(listOf(NetworkProtocol.AUTO), preferences.attemptsFor("api.lightnovel.fun", false))
    }

    @Test fun `late failure does not erase a different successful protocol`() {
        val preferences = NetworkConnectionPreferences()
        preferences.recordSuccess("api.lightnovel.fun", NetworkProtocol.HTTP2)
        preferences.recordSuccess("api.lightnovel.fun", NetworkProtocol.HTTP1)
        preferences.recordFailure("api.lightnovel.fun", NetworkProtocol.HTTP2)
        assertEquals(listOf(NetworkProtocol.HTTP1), preferences.attemptsFor("api.lightnovel.fun", false))
    }

    @Test fun `old network preference expires`() {
        var now = 0L
        val preferences = NetworkConnectionPreferences { now }
        preferences.recordSuccess("api.lightnovel.fun", NetworkProtocol.HTTP1)
        now = 300_000
        assertEquals(listOf(NetworkProtocol.AUTO), preferences.attemptsFor("api.lightnovel.fun", false))
    }
}
