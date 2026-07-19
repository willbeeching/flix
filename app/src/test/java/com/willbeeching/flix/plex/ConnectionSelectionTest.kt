package com.willbeeching.flix.plex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionSelectionTest {

    // ---- parseDashedPlexDirectIp ----

    @Test
    fun `parses a well-formed dashed IP from a plex-direct hostname`() {
        assertEquals(
            "10.0.0.8",
            parseDashedPlexDirectIp("10-0-0-8.abcd1234ef.plex.direct")
        )
    }

    @Test
    fun `parseDashedPlexDirectIp is case-insensitive on the suffix`() {
        assertEquals(
            "192.168.1.42",
            parseDashedPlexDirectIp("192-168-1-42.somehash.PLEX.DIRECT")
        )
    }

    @Test
    fun `returns null for a host that is not a plex-direct hostname`() {
        assertNull(parseDashedPlexDirectIp("10.0.0.8"))
        assertNull(parseDashedPlexDirectIp("myserver.local"))
    }

    @Test
    fun `returns null when the first label does not have exactly four dashed parts`() {
        assertNull(parseDashedPlexDirectIp("10-0-8.abcd1234ef.plex.direct"))
        assertNull(parseDashedPlexDirectIp("10-0-0-0-8.abcd1234ef.plex.direct"))
    }

    @Test
    fun `returns null for non-numeric or out-of-range octets`() {
        assertNull(parseDashedPlexDirectIp("10-0-abc-8.hash.plex.direct"))
        assertNull(parseDashedPlexDirectIp("999-0-0-8.hash.plex.direct"))
    }

    // ---- hostOf ----

    @Test
    fun `extracts the host from a normal connection uri`() {
        assertEquals("10.0.0.5", hostOf("https://10.0.0.5:32400"))
        assertEquals(
            "10-0-0-8.abcd1234ef.plex.direct",
            hostOf("https://10-0-0-8.abcd1234ef.plex.direct:32400")
        )
    }

    @Test
    fun `returns null for an unparseable uri`() {
        assertNull(hostOf("not a uri at all ://???"))
    }

    // ---- ipv4ToInt / isSameSubnet ----

    @Test
    fun `ipv4ToInt round-trips dotted quads and rejects garbage`() {
        assertTrue(ipv4ToInt("10.0.0.8") != null)
        assertEquals(ipv4ToInt("10.0.0.8"), ipv4ToInt("10.0.0.8"))
        assertNull(ipv4ToInt("not-an-ip"))
        assertNull(ipv4ToInt("10.0.0"))
        assertNull(ipv4ToInt("10.0.0.999"))
    }

    @Test
    fun `isSameSubnet matches within a slash-24`() {
        assertTrue(isSameSubnet("10.0.0.8", "10.0.0.240", 24))
        assertFalse(isSameSubnet("10.0.0.8", "10.0.1.8", 24))
    }

    @Test
    fun `isSameSubnet matches within a slash-16 across differing third octet`() {
        assertTrue(isSameSubnet("10.0.5.8", "10.0.200.240", 16))
        assertFalse(isSameSubnet("10.0.5.8", "10.1.5.8", 16))
    }

    @Test
    fun `isSameSubnet edge prefix lengths`() {
        assertTrue(isSameSubnet("1.2.3.4", "9.8.7.6", 0)) // /0 = everything matches
        assertTrue(isSameSubnet("10.0.0.8", "10.0.0.8", 32)) // /32 = exact match only
        assertFalse(isSameSubnet("10.0.0.8", "10.0.0.9", 32))
    }

    @Test
    fun `isSameSubnet is false for unparseable ips`() {
        assertFalse(isSameSubnet("not-an-ip", "10.0.0.8", 24))
    }

    // ---- candidateIpLiteral / isCandidateOnLocalSubnet ----

    private fun connection(
        uri: String,
        address: String = "ignored",
        local: Boolean = false,
        relay: Boolean = false,
        protocol: String = "https"
    ) = PlexApiClient.Connection(
        protocol = protocol,
        address = address,
        port = 32400,
        uri = uri,
        local = local,
        relay = relay,
        ipv6 = false
    )

    @Test
    fun `candidateIpLiteral resolves a plain IP uri without any dns`() {
        val conn = connection(uri = "https://10.0.0.8:32400")
        assertEquals("10.0.0.8", candidateIpLiteral(conn))
    }

    @Test
    fun `candidateIpLiteral resolves a dashed plex-direct hostname without any dns`() {
        val conn = connection(uri = "https://10-0-0-8.abcd1234ef.plex.direct:32400")
        assertEquals("10.0.0.8", candidateIpLiteral(conn))
    }

    @Test
    fun `candidateIpLiteral falls back to the address field when the uri is unparseable`() {
        val conn = connection(uri = "garbage ://", address = "10.0.0.9")
        assertEquals("10.0.0.9", candidateIpLiteral(conn))
    }

    @Test
    fun `candidateIpLiteral returns null when only real dns could resolve the host`() {
        val conn = connection(uri = "https://myplexserver.example.com:32400", address = "myplexserver.example.com")
        assertNull(candidateIpLiteral(conn))
    }

    @Test
    fun `isCandidateOnLocalSubnet is false with no local network info`() {
        val conn = connection(uri = "https://10.0.0.8:32400")
        assertFalse(isCandidateOnLocalSubnet(conn, null))
    }

    @Test
    fun `isCandidateOnLocalSubnet true when candidate shares the device subnet`() {
        val conn = connection(uri = "https://10.0.0.8:32400")
        val localNetwork = LocalNetworkInfo(ip = "10.0.0.42", prefixLength = 24)
        assertTrue(isCandidateOnLocalSubnet(conn, localNetwork))
    }

    @Test
    fun `isCandidateOnLocalSubnet false when candidate is on a different subnet - the account's real defect`() {
        // The unreachable candidates from the diagnosed bug: another server's
        // LAN/Docker address that is not routable from this device's network.
        val dockerConn = connection(uri = "https://172.18.0.1:32400")
        val otherLanConn = connection(uri = "https://10.1.0.157:32400")
        val localNetwork = LocalNetworkInfo(ip = "10.0.0.42", prefixLength = 24)
        assertFalse(isCandidateOnLocalSubnet(dockerConn, localNetwork))
        assertFalse(isCandidateOnLocalSubnet(otherLanConn, localNetwork))
    }

    // ---- sortConnections ----

    @Test
    fun `sortConnections prefers plex-reported local over everything else`() {
        val remote = connection(uri = "https://203.0.113.5:32400", local = false)
        val local = connection(uri = "https://10-0-0-8.hash.plex.direct:32400", local = true)

        val sorted = sortConnections(listOf(remote, local), localNetwork = null)

        assertEquals(local, sorted.first())
    }

    @Test
    fun `sortConnections does not down-rank plex-direct hostnames just for being plex-direct`() {
        // Both candidates are equally "local" per Plex; only the subnet heuristic
        // should decide the order - and it must be free to prefer the .plex.direct
        // one. The old buggy logic always sorted .plex.direct last regardless of
        // subnet, which this proves is no longer the case.
        val plainIpOnOtherSubnet = connection(uri = "https://192.168.5.8:32400", local = true)
        val plexDirectOnDeviceSubnet = connection(uri = "https://10-0-0-50.hash.plex.direct:32400", local = true)
        val localNetwork = LocalNetworkInfo(ip = "10.0.0.9", prefixLength = 24)

        val sorted = sortConnections(listOf(plainIpOnOtherSubnet, plexDirectOnDeviceSubnet), localNetwork)

        // The plex.direct one shares the device's subnet - it must win, proving
        // hostname shape isn't a tiebreaker (only the subnet heuristic is).
        assertEquals(plexDirectOnDeviceSubnet, sorted.first())
    }

    @Test
    fun `sortConnections uses the subnet heuristic as the second tiebreaker`() {
        val sameSubnet = connection(uri = "https://10.0.0.50:32400", local = false)
        val otherSubnet = connection(uri = "https://172.18.0.1:32400", local = false)
        val localNetwork = LocalNetworkInfo(ip = "10.0.0.42", prefixLength = 24)

        val sorted = sortConnections(listOf(otherSubnet, sameSubnet), localNetwork)

        assertEquals(sameSubnet, sorted.first())
    }

    @Test
    fun `sortConnections prefers non-relay over relay`() {
        val relay = connection(uri = "https://relay.plex.direct:32400", relay = true)
        val direct = connection(uri = "https://10.0.0.8:32400", relay = false)

        val sorted = sortConnections(listOf(relay, direct), localNetwork = null)

        assertEquals(direct, sorted.first())
    }

    @Test
    fun `sortConnections prefers https over http as the last tiebreaker`() {
        val http = connection(uri = "http://10.0.0.8:32400", protocol = "http")
        val https = connection(uri = "http://10.0.0.8:32443", protocol = "https")

        val sorted = sortConnections(listOf(http, https), localNetwork = null)

        assertEquals(https, sorted.first())
    }
}
