package io.github.jiangyuyi.lightnovel.core.network

import java.util.concurrent.ConcurrentHashMap

internal enum class NetworkProtocol { AUTO, HTTP2, HTTP1 }

/** Only transport preference is remembered; hostname resolution remains dynamic. */
internal class NetworkConnectionPreferences(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private data class Success(val protocol: NetworkProtocol, val atMillis: Long)
    private val successful = ConcurrentHashMap<String, Success>()

    fun attemptsFor(host: String, retryAllowed: Boolean): List<NetworkProtocol> {
        val remembered = successful[host]?.takeIf { nowMillis() - it.atMillis < 300_000 }?.protocol
        val ordered = (listOfNotNull(remembered) + NetworkProtocol.entries).distinct()
        // One final read attempt tolerates a transient reset after other protocols
        // have been tried. A mutation always gets exactly one attempt.
        return if (retryAllowed) ordered + ordered.first() else ordered.take(1)
    }

    fun recordSuccess(host: String, protocol: NetworkProtocol) {
        successful[host] = Success(protocol, nowMillis())
    }

    fun recordFailure(host: String, protocol: NetworkProtocol) {
        val previous = successful[host] ?: return
        if (previous.protocol == protocol) successful.remove(host, previous)
    }
}
