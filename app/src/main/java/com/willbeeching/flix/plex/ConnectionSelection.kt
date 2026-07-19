package com.willbeeching.flix.plex

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Cheap, non-blocking description of the device's active local network,
 * used only to bias connection-candidate ordering toward the same subnet.
 * Built from [java.net.NetworkInterface] enumeration only - never from a
 * real DNS lookup or network call.
 */
internal data class LocalNetworkInfo(val ip: String, val prefixLength: Int)

/**
 * Parses the dashed-IP form Plex embeds in .plex.direct hostnames, e.g.
 * "10-0-0-8.abcd1234.plex.direct" -> "10.0.0.8". Returns null for anything
 * that isn't a well-formed dashed IPv4 address in the first DNS label.
 *
 * This is a string-only heuristic - it never performs DNS resolution.
 */
internal fun parseDashedPlexDirectIp(host: String): String? {
    if (!host.endsWith(".plex.direct", ignoreCase = true)) return null
    val firstLabel = host.substringBefore('.')
    val octets = firstLabel.split("-")
    if (octets.size != 4) return null
    val values = octets.map { it.toIntOrNull() ?: return null }
    if (values.any { it !in 0..255 }) return null
    return values.joinToString(".")
}

/**
 * Extracts the host portion of a connection URI, e.g.
 * "https://10.0.0.8:32400" -> "10.0.0.8". Returns null if [uriString] isn't
 * a parseable URI. This does not perform DNS resolution - [URI.getHost]
 * only parses the authority component of the string.
 */
internal fun hostOf(uriString: String): String? {
    return try {
        URI(uriString).host
    } catch (e: Exception) {
        null
    }
}

/**
 * Converts a dotted-quad IPv4 string to its 32-bit integer representation,
 * or null if [ip] isn't a valid IPv4 literal.
 */
internal fun ipv4ToInt(ip: String): Int? {
    val parts = ip.split(".")
    if (parts.size != 4) return null
    var result = 0
    for (part in parts) {
        val octet = part.toIntOrNull() ?: return null
        if (octet !in 0..255) return null
        result = (result shl 8) or octet
    }
    return result
}

/**
 * True if [ipA] and [ipB] fall in the same network under a [prefixLength]-bit
 * CIDR mask. Returns false if either string isn't a valid IPv4 literal.
 */
internal fun isSameSubnet(ipA: String, ipB: String, prefixLength: Int): Boolean {
    if (prefixLength <= 0) return true
    if (prefixLength >= 32) return ipA == ipB
    val a = ipv4ToInt(ipA) ?: return false
    val b = ipv4ToInt(ipB) ?: return false
    val mask = -1 shl (32 - prefixLength)
    return (a and mask) == (b and mask)
}

/**
 * Best-effort IPv4 literal for a connection, resolved WITHOUT any DNS:
 * either the connection's host is already a dotted-quad, or it's a
 * .plex.direct hostname whose first label encodes the IP (see
 * [parseDashedPlexDirectIp]). Returns null when the host can only be
 * resolved via real DNS - in that case subnet matching is simply skipped
 * for this candidate (it still gets probed, just not prioritized for it).
 */
internal fun candidateIpLiteral(connection: PlexApiClient.Connection): String? {
    val host = hostOf(connection.uri) ?: connection.address
    ipv4ToInt(host)?.let { return host }
    return parseDashedPlexDirectIp(host)
}

/**
 * Whether [connection] can cheaply be shown to sit on the same subnet as
 * [localNetwork]. Always false (never "unknown") when it can't be
 * determined cheaply - this is a sort-order bias, not a filter.
 */
internal fun isCandidateOnLocalSubnet(
    connection: PlexApiClient.Connection,
    localNetwork: LocalNetworkInfo?
): Boolean {
    if (localNetwork == null) return false
    val candidateIp = candidateIpLiteral(connection) ?: return false
    return isSameSubnet(candidateIp, localNetwork.ip, localNetwork.prefixLength)
}

/**
 * Orders connection candidates for probing. Preference order:
 *  1. Plex's own "local" flag (its opinion of whether this is a LAN address)
 *  2. Our own same-subnet heuristic (cheap, DNS-free - see [isCandidateOnLocalSubnet])
 *  3. Non-relay over relay
 *  4. https over http
 *
 * NOTE: .plex.direct hostnames are deliberately NOT down-ranked here. They
 * used to be treated as "avoid if possible" because DNS resolution for them
 * was assumed to indicate a non-local/remote connection, but that premise is
 * wrong: plex.tv issues .plex.direct hostnames (which carry a valid wildcard
 * TLS cert) for nearly every connection, including LAN ones. Local-first
 * ordering is preserved above via the `local` flag and the subnet heuristic,
 * not via hostname shape.
 */
internal fun sortConnections(
    connections: List<PlexApiClient.Connection>,
    localNetwork: LocalNetworkInfo?
): List<PlexApiClient.Connection> {
    return connections.sortedWith(
        compareByDescending<PlexApiClient.Connection> { it.local }
            .thenByDescending { isCandidateOnLocalSubnet(it, localNetwork) }
            .thenByDescending { !it.relay }
            .thenByDescending { it.protocol == "https" }
    )
}

/**
 * Races [probe] across [items] concurrently using structured concurrency
 * ([coroutineScope] + [launch]) and returns the first non-null result.
 * Every other in-flight probe is cancelled as soon as a winner is found (or
 * once every candidate has been tried and none succeeded) - no losing call
 * is left running past this function returning.
 *
 * A probe that throws is treated as a failure (null), not as a fatal error
 * for the whole race - one bad candidate must not sink the others.
 */
internal suspend fun <T, R : Any> raceFirstSuccess(
    items: List<T>,
    probe: suspend (T) -> R?
): R? = coroutineScope {
    if (items.isEmpty()) return@coroutineScope null

    // Buffered so a cancelled/losing job's send never suspends.
    val results = Channel<R?>(capacity = items.size)
    val jobs = items.map { item ->
        launch {
            val outcome = try {
                probe(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            results.send(outcome)
        }
    }

    var winner: R? = null
    var received = 0
    while (received < items.size && winner == null) {
        winner = results.receive()
        received++
    }
    jobs.forEach { it.cancel() }
    winner
}
