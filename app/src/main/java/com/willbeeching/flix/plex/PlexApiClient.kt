package com.willbeeching.flix.plex

import android.content.Context
import android.util.Log
import android.util.Xml
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.StringReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HostnameVerifier
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Client for interacting with Plex Media Server API
 *
 * @param context Optional application context. When supplied, the
 * last-known-good connection URI for each server is cached (see
 * [ServerConnectionCache]) and tried first on the next [discoverServers]
 * call. Omitting it (existing behavior) simply means every call does a full
 * concurrent discovery with no cross-restart shortcut.
 */
class PlexApiClient(private val authToken: String, context: Context? = null) {

    private val connectionCache: ServerConnectionCache? = context?.let { ServerConnectionCache(it) }

    // Client used for real data (library listings, artwork, images). Timeouts stay
    // generous - a slow-but-reachable server fetching a large batch of artwork must
    // not be cut off at probe-length timeouts.
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .apply {
            // Trust all certificates (needed for local Plex servers with self-signed certs)
            try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())

                sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                hostnameVerifier(HostnameVerifier { _, _ -> true })

                Log.d(TAG, "SSL certificate validation disabled for local Plex servers")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting up SSL trust manager", e)
            }
        }
        .build()

    // Client used ONLY for discovery probes ("is this candidate reachable at all").
    // Built via client.newBuilder() so it shares the same Dispatcher (thread pool)
    // and ConnectionPool as the data client above - we get a much shorter timeout
    // without spinning up a second pool of connections/threads. A reachable LAN
    // Plex server answers in tens of milliseconds, so PROBE_TIMEOUT_SECONDS just
    // needs to comfortably outlast that, not a slow WAN round trip.
    private val probeClient = client.newBuilder()
        .connectTimeout(PROBE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(PROBE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(PROBE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    companion object {
        private const val TAG = "PlexApiClient"
        private const val PLEX_TV_BASE = "https://plex.tv"
        private const val PROBE_TIMEOUT_SECONDS = 3L
    }

    @JsonClass(generateAdapter = true)
    data class ResourceResponse(
        @Json(name = "name") val name: String,
        @Json(name = "product") val product: String? = null,
        @Json(name = "productVersion") val productVersion: String? = null,
        @Json(name = "platform") val platform: String? = null,
        @Json(name = "platformVersion") val platformVersion: String? = null,
        @Json(name = "device") val device: String? = null,
        @Json(name = "clientIdentifier") val clientIdentifier: String? = null,
        @Json(name = "provides") val provides: String? = null,
        @Json(name = "owned") val owned: Boolean = false,
        @Json(name = "accessToken") val accessToken: String? = null,
        @Json(name = "publicAddress") val publicAddress: String? = null,
        @Json(name = "httpsRequired") val httpsRequired: Boolean = false,
        @Json(name = "synced") val synced: Boolean = false,
        @Json(name = "relay") val relay: Boolean = false,
        @Json(name = "dnsRebindingProtection") val dnsRebindingProtection: Boolean = false,
        @Json(name = "natLoopbackSupported") val natLoopbackSupported: Boolean = false,
        @Json(name = "connections") val connections: List<Connection>? = null
    )

    @JsonClass(generateAdapter = true)
    data class Connection(
        @Json(name = "protocol") val protocol: String,
        @Json(name = "address") val address: String,
        @Json(name = "port") val port: Int,
        @Json(name = "uri") val uri: String,
        @Json(name = "local") val local: Boolean = false,
        @Json(name = "relay") val relay: Boolean = false,
        @Json(name = "IPv6") val ipv6: Boolean = false
    )

    data class PlexServer(
        val name: String,
        val clientIdentifier: String,
        val uri: String,
        val accessToken: String,
        val connectionStatus: ConnectionStatus = ConnectionStatus.VERIFIED,
        val isRelay: Boolean = false
    )

    enum class ConnectionStatus {
        VERIFIED,       // Connection tested and working
        UNVERIFIED,     // Connection not tested, may or may not work
        FAILED          // All connections failed during testing
    }

    data class LibrarySection(
        val id: String,
        val title: String,
        val type: String // movie, show, artist, photo
    )

    data class ArtworkItem(
        val title: String,
        val thumbUrl: String?,
        val artUrl: String?,
        val titleCardUrl: String?, // Logo/title card for overlay
        val rating: String?,
        val year: String?,
        val type: String, // movie, show, episode, etc.
        val ratingKey: String?, // For fetching additional metadata
        val guid: String?, // Plex GUID (may contain TMDB ID)
        val preferredArtworkId: String? = null // Optional Fanart.tv artwork ID to use
    )

    /**
     * Discover available Plex servers.
     *
     * Includes relay connections and shows servers even if connection testing
     * fails entirely (see [ConnectionStatus]).
     *
     * Concurrency: every server is probed in parallel, and within each server
     * every connection candidate is probed in parallel too (structured
     * concurrency via [coroutineScope]/[async] + [raceFirstSuccess], which
     * cancels every losing probe as soon as one candidate succeeds). This
     * replaces the old fully-sequential loop, which cost
     * `unreachable candidates x 15s timeout` in a straight line - with three
     * servers on an account and a couple of unreachable LAN/Docker addresses
     * from other servers on the account, that alone was 45-90s of dead time
     * before any artwork could load.
     *
     * Each server also gets a fast path: if a previous run cached a working
     * connection URI for it ([ServerConnectionCache]), that URI is probed
     * first, and a hit skips discovery for that server entirely. A screensaver
     * process restarts constantly, so this turns most cold starts into a
     * single ~tens-of-milliseconds LAN round trip instead of a fresh race.
     */
    suspend fun discoverServers(): Result<List<PlexServer>> {
        return try {
            // Include relay connections (was previously disabled with includeRelay=0)
            val request = Request.Builder()
                .url("$PLEX_TV_BASE/api/v2/resources?includeHttps=1&includeRelay=1")
                .get()
                .addHeader("X-Plex-Token", authToken)
                .addHeader("X-Plex-Client-Identifier", "com.willbeeching.flix")
                .addHeader("Accept", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "Discover servers response code: ${response.code}")
            Log.d(TAG, "Discover servers response: ${responseBody?.take(500)}")

            if (!response.isSuccessful || responseBody == null) {
                val errorMsg = "Failed to discover servers: ${response.code} - ${response.message}. Body: ${responseBody?.take(200)}"
                Log.e(TAG, errorMsg)
                return Result.failure(Exception(errorMsg))
            }

            // Parse JSON array
            val adapter = moshi.adapter<List<ResourceResponse>>(
                com.squareup.moshi.Types.newParameterizedType(
                    List::class.java,
                    ResourceResponse::class.java
                )
            )

            val resources = adapter.fromJson(responseBody)
                ?: return Result.failure(Exception("Failed to parse resources"))

            Log.d(TAG, "Found ${resources.size} total resources from Plex.tv")

            // Filter for servers (both owned and shared)
            val serverResources = resources.filter { it.provides?.contains("server") == true }
            Log.d(TAG, "Found ${serverResources.size} servers (filtering for 'owned' or accessible)")

            // Cheap, non-blocking, local-only lookup - never a network call.
            val localNetworkInfo = getLocalNetworkInfo()

            val validResources = serverResources.mapNotNull { resource ->
                if (resource.accessToken == null || resource.clientIdentifier == null) {
                    Log.w(TAG, "⚠ Server ${resource.name} missing accessToken or clientIdentifier")
                    null
                } else {
                    resource
                }
            }

            // Probe every server concurrently; each resolveServer() call itself
            // probes that server's candidates concurrently (or short-circuits on
            // a cache hit). No server's slowness blocks any other server.
            val servers = coroutineScope {
                validResources
                    .map { resource -> async(Dispatchers.IO) { resolveServer(resource, localNetworkInfo) } }
                    .awaitAll()
            }

            Log.d(TAG, "Discovered ${servers.size} servers total")
            Log.d(TAG, "  - ${servers.count { it.connectionStatus == ConnectionStatus.VERIFIED }} verified")
            Log.d(TAG, "  - ${servers.count { it.connectionStatus == ConnectionStatus.UNVERIFIED }} unverified")
            Log.d(TAG, "  - ${servers.count { it.connectionStatus == ConnectionStatus.FAILED }} failed")
            Log.d(TAG, "  - ${servers.count { it.isRelay }} using relay")

            Result.success(servers)
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering servers", e)
            Result.failure(e)
        }
    }

    /**
     * Resolves a single server resource to a [PlexServer]: tries the cached
     * connection first, then races all candidates concurrently, then falls
     * back to the best-ordered candidate as UNVERIFIED if nothing answered.
     */
    private suspend fun resolveServer(
        resource: ResourceResponse,
        localNetworkInfo: LocalNetworkInfo?
    ): PlexServer {
        val clientIdentifier = resource.clientIdentifier!!
        val accessToken = resource.accessToken!!

        if (resource.connections.isNullOrEmpty()) {
            Log.w(TAG, "⚠ Server ${resource.name} has no connection URIs available")
            // Still show it but mark as FAILED
            return PlexServer(
                name = resource.name,
                clientIdentifier = clientIdentifier,
                uri = "unknown",
                accessToken = accessToken,
                connectionStatus = ConnectionStatus.FAILED,
                isRelay = false
            )
        }

        val sortedConnections = sortConnections(resource.connections, localNetworkInfo)

        Log.d(TAG, "Server ${resource.name} has ${sortedConnections.size} connections:")
        sortedConnections.forEach { conn ->
            Log.d(TAG, "  - ${conn.uri} (local=${conn.local}, relay=${conn.relay}, ipv6=${conn.ipv6})")
        }

        // Fast path: last-known-good connection from a previous run. A hit
        // means this server needs exactly one short probe, not a full race.
        val cachedUri = connectionCache?.getCachedUri(clientIdentifier)
        if (cachedUri != null) {
            if (probeConnection(cachedUri, accessToken)) {
                Log.d(TAG, "✓ Cache hit for ${resource.name}: $cachedUri (discovery skipped)")
                val cachedConnection = resource.connections.find { it.uri == cachedUri }
                return PlexServer(
                    name = resource.name,
                    clientIdentifier = clientIdentifier,
                    uri = cachedUri,
                    accessToken = accessToken,
                    connectionStatus = ConnectionStatus.VERIFIED,
                    isRelay = cachedConnection?.relay ?: false
                )
            }
            Log.d(TAG, "✗ Cached connection for ${resource.name} ($cachedUri) failed probe, falling back to full discovery")
        }

        // Race every candidate concurrently; first success wins, the rest are cancelled.
        val winner = raceFirstSuccess(sortedConnections) { connection ->
            if (probeConnection(connection.uri, accessToken)) connection else null
        }

        return if (winner != null) {
            Log.d(TAG, "✓ Connection successful: ${winner.uri}")
            connectionCache?.setCachedUri(clientIdentifier, winner.uri)
            PlexServer(
                name = resource.name,
                clientIdentifier = clientIdentifier,
                uri = winner.uri,
                accessToken = accessToken,
                connectionStatus = ConnectionStatus.VERIFIED,
                isRelay = winner.relay
            )
        } else {
            // Use first available connection as fallback (best-ordered candidate)
            val fallbackConnection = sortedConnections.first()
            Log.w(TAG, "⚠ No verified connections for ${resource.name}, using fallback: ${fallbackConnection.uri}")
            PlexServer(
                name = resource.name,
                clientIdentifier = clientIdentifier,
                uri = fallbackConnection.uri,
                accessToken = accessToken,
                connectionStatus = ConnectionStatus.UNVERIFIED,
                isRelay = fallbackConnection.relay
            )
        }
    }

    /**
     * Probes whether a connection URI is reachable, using [probeClient]'s
     * short timeout - NOT [client]'s generous data timeout. Uses OkHttp's
     * async `enqueue` (not the blocking `execute()`) via [executeSuspend] so
     * that cancelling the coroutine (a losing race participant) actually
     * cancels the in-flight socket/call instead of leaving a thread blocked.
     */
    private suspend fun probeConnection(uri: String, accessToken: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("$uri/?X-Plex-Token=$accessToken")
                .get()
                .build()

            val response = executeSuspend(probeClient, request)
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Probe failed for $uri: ${e.message}")
            false
        }
    }

    /**
     * Cheap, non-blocking, local-only lookup of the device's active IPv4
     * address and subnet prefix (used only to bias candidate ordering - see
     * [sortConnections]). Uses [NetworkInterface] enumeration only; performs
     * no DNS resolution and no network I/O.
     */
    private fun getLocalNetworkInfo(): LocalNetworkInfo? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (netIf in interfaces) {
                if (!netIf.isUp || netIf.isLoopback) continue
                for (interfaceAddress in netIf.interfaceAddresses) {
                    val addr = interfaceAddress.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        return LocalNetworkInfo(ip, interfaceAddress.networkPrefixLength.toInt())
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Unable to determine local network info: ${e.message}")
            null
        }
    }

    /**
     * Suspends until [request] completes on [okClient], using OkHttp's
     * asynchronous `enqueue` rather than the blocking `execute()`. Cancelling
     * the calling coroutine cancels the underlying [Call] - this is what lets
     * [raceFirstSuccess] actually abort losing probes instead of leaving them
     * running to their own timeout on a background thread.
     */
    private suspend fun executeSuspend(okClient: OkHttpClient, request: Request): Response =
        suspendCancellableCoroutine { cont ->
            val call = okClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isCancelled) return
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isCancelled) {
                        response.close()
                        return
                    }
                    cont.resume(response)
                }
            })
        }

    /**
     * Get library sections from a Plex server
     */
    suspend fun getLibrarySections(server: PlexServer): Result<List<LibrarySection>> {
        return try {
            val url = "${server.uri}/library/sections?X-Plex-Token=${server.accessToken}"
            Log.d(TAG, "Fetching library sections from: ${server.uri}")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                val errorMsg = "Failed to get library sections: ${response.code} ${response.message}"
                Log.e(TAG, errorMsg)
                return Result.failure(Exception(errorMsg))
            }

            // Parse XML
            val sections = parseLibrarySections(responseBody)
            Log.d(TAG, "Found ${sections.size} library sections")
            Result.success(sections)
        } catch (e: Exception) {
            // Provide helpful error message for .plex.direct DNS issues
            val errorMsg = if (server.uri.contains(".plex.direct") && 
                              (e is java.net.UnknownHostException || e.message?.contains("resolve") == true)) {
                "DNS resolution failed for .plex.direct hostname. This is a known Android TV issue. " +
                "Try ensuring your Plex server has a direct local IP connection available, or restart your router/Plex server."
            } else {
                "Error connecting to ${server.uri}: ${e.message}"
            }
            Log.e(TAG, errorMsg, e)
            Result.failure(Exception(errorMsg, e))
        }
    }

    /**
     * Get artwork items from a library section with smart batching
     * For large libraries (>1000 items), we fetch random batches to:
     * - Keep memory usage reasonable
     * - Provide variety across screensaver sessions
     * - Load quickly without blocking
     */
    suspend fun getArtworkFromSection(
        server: PlexServer,
        sectionId: String,
        batchSize: Int = 300  // Smart batch size: fast to load, good variety
    ): Result<List<ArtworkItem>> {
        return try {
            // First, get the total count (lightweight request)
            val countUrl = "${server.uri}/library/sections/$sectionId/all?X-Plex-Container-Size=1&X-Plex-Token=${server.accessToken}"
            val countRequest = Request.Builder().url(countUrl).get().build()
            val countResponse = client.newCall(countRequest).execute()
            val countBody = countResponse.body?.string()

            // Parse total size from MediaContainer
            val totalSize = countBody?.let {
                Regex("<MediaContainer[^>]*size=\"(\\d+)\"").find(it)?.groupValues?.get(1)?.toIntOrNull()
            } ?: batchSize

            Log.d(TAG, "Library section $sectionId has $totalSize total items")

            // For large libraries, use random offset to get different items each session
            val offset = if (totalSize > batchSize) {
                kotlin.random.Random.nextInt(0, (totalSize - batchSize).coerceAtLeast(0))
            } else {
                0
            }

            // Fetch a batch with random offset
            // sort=random ensures variety within the batch too
            // includeExtras=1 and includeImages=1 to get Image child elements (clearLogo)
            val url = "${server.uri}/library/sections/$sectionId/all?includeGuids=1&includeExtras=1&includeImages=1&sort=random&X-Plex-Container-Size=$batchSize&X-Plex-Container-Start=$offset&X-Plex-Token=${server.accessToken}"

            Log.d(TAG, "Fetching $batchSize items from section $sectionId (offset: $offset) with Image elements")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return Result.failure(Exception("Failed to get artwork: ${response.code}"))
            }

            // Parse XML
            val items = parseArtworkItems(responseBody, server.uri, server.accessToken)

            Log.d(TAG, "Loaded ${items.size} artwork items from section (${items.size} of $totalSize total)")
            Result.success(items)
        } catch (e: Exception) {
            // Provide helpful error message for .plex.direct DNS issues
            val errorMsg = if (server.uri.contains(".plex.direct") && 
                              (e is java.net.UnknownHostException || e.message?.contains("resolve") == true)) {
                "DNS resolution failed for .plex.direct hostname. This is a known Android TV issue. " +
                "Please try restarting the app and selecting your server again."
            } else {
                "Error getting artwork: ${e.message}"
            }
            Log.e(TAG, errorMsg, e)
            Result.failure(Exception(errorMsg, e))
        }
    }

    /**
     * Parse library sections from XML response
     */
    private fun parseLibrarySections(xml: String): List<LibrarySection> {
        val sections = mutableListOf<LibrarySection>()

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "Directory") {
                    val id = parser.getAttributeValue(null, "key")
                    val title = parser.getAttributeValue(null, "title")
                    val type = parser.getAttributeValue(null, "type")

                    if (id != null && title != null && type != null) {
                        sections.add(LibrarySection(id, title, type))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing library sections", e)
        }

        return sections
    }

    /**
     * Parse artwork items from XML response
     */
    private fun parseArtworkItems(
        xml: String,
        serverUri: String,
        token: String
    ): List<ArtworkItem> {
        val items = mutableListOf<ArtworkItem>()

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            var currentItem: MutableMap<String, String>? = null
            var itemGuids = mutableListOf<String>()
            var clearLogoUrl: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "Video", "Directory" -> {
                            currentItem = mutableMapOf()
                            itemGuids = mutableListOf()
                            clearLogoUrl = null  // Reset for new item

                            val title = parser.getAttributeValue(null, "title") ?: ""
                            val thumb = parser.getAttributeValue(null, "thumb")
                            val art = parser.getAttributeValue(null, "art")
                            val banner = parser.getAttributeValue(null, "banner")
                            val theme = parser.getAttributeValue(null, "theme")
                            val rating = parser.getAttributeValue(null, "rating")
                            val year = parser.getAttributeValue(null, "year")
                            val type = parser.getAttributeValue(null, "type") ?: "unknown"
                            val ratingKey = parser.getAttributeValue(null, "ratingKey")
                            val guid = parser.getAttributeValue(null, "guid")

                            currentItem["title"] = title
                            currentItem["thumb"] = thumb ?: ""
                            currentItem["art"] = art ?: ""
                            currentItem["banner"] = banner ?: ""
                            currentItem["theme"] = theme ?: ""
                            currentItem["rating"] = rating ?: ""
                            currentItem["year"] = year ?: ""
                            currentItem["type"] = type
                            currentItem["ratingKey"] = ratingKey ?: ""
                            currentItem["guid"] = guid ?: ""
                        }
                        "Guid" -> {
                            // Plex stores multiple GUIDs including TMDB
                            val guidId = parser.getAttributeValue(null, "id")
                            if (guidId != null) {
                                itemGuids.add(guidId)
                            }
                        }
                        "Image" -> {
                            // NEW: Parse Image child elements for clearLogo
                            val imageType = parser.getAttributeValue(null, "type")
                            val imageUrl = parser.getAttributeValue(null, "url")

                            if (imageType == "clearLogo" && imageUrl != null) {
                                clearLogoUrl = imageUrl
                                Log.d(TAG, "Found clearLogo: $imageUrl")
                            }
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if ((parser.name == "Video" || parser.name == "Directory") && currentItem != null) {
                        val title = currentItem["title"] ?: ""
                        val thumb = currentItem["thumb"]
                        val art = currentItem["art"]
                        val banner = currentItem["banner"]
                        val theme = currentItem["theme"]
                        val rating = currentItem["rating"]
                        val year = currentItem["year"]
                        val type = currentItem["type"] ?: "unknown"
                        val ratingKey = currentItem["ratingKey"]

                        // Store ALL GUIDs (tmdb://, tvdb://, etc.) joined by | for different services
                        // Fanart.tv needs TVDB ID, TMDB client needs TMDB ID
                        val allGuids = if (itemGuids.isNotEmpty()) {
                            itemGuids.joinToString("|")
                        } else {
                            currentItem["guid"] ?: ""
                        }

                        // Debug: Log GUIDs for first few items
                        if (items.size < 3) {
                            Log.d(TAG, "Item: $title")
                            Log.d(TAG, "  Main GUID: ${currentItem["guid"]}")
                            Log.d(TAG, "  Child GUIDs: $itemGuids")
                            Log.d(TAG, "  All GUIDs: $allGuids")
                            Log.d(TAG, "  clearLogo: $clearLogoUrl")
                        }

                        val thumbUrl = thumb?.takeIf { it.isNotEmpty() }?.let { buildImageUrl(serverUri, it, token) }
                        val artUrl = art?.takeIf { it.isNotEmpty() }?.let { buildImageUrl(serverUri, it, token) }

                        // Use Plex clearLogo if available, otherwise will fetch from TMDB later
                        val titleCardUrl = clearLogoUrl?.takeIf { it.isNotEmpty() }?.let {
                            buildImageUrl(serverUri, it, token)
                        }

                        if (thumbUrl != null || artUrl != null) {
                            items.add(
                                ArtworkItem(
                                    title = title,
                                    thumbUrl = thumbUrl,
                                    artUrl = artUrl,
                                    titleCardUrl = titleCardUrl, // Use Plex clearLogo!
                                    rating = rating,
                                    year = year,
                                    type = type,
                                    ratingKey = ratingKey,
                                    guid = allGuids
                                )
                            )
                        }

                        currentItem = null
                        itemGuids.clear()
                        clearLogoUrl = null
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing artwork items", e)
        }

        return items
    }

    /**
     * Try to fetch title card/logo from undocumented Plex endpoints
     */
    suspend fun getTitleCardUrl(server: PlexServer, ratingKey: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Try various undocumented endpoints
                val endpoints = listOf(
                    "/library/metadata/$ratingKey?includeExtras=1&includeFields=art,thumb,banner,titleCard",
                    "/library/metadata/$ratingKey?includeFields=titleCard",
                    "/library/metadata/$ratingKey/arts",
                    "/library/metadata/$ratingKey?includeImages=1",
                    "/library/metadata/$ratingKey?includeExtended=1"
                )

                for (endpoint in endpoints) {
                    val url = "${server.uri}$endpoint&X-Plex-Token=${server.accessToken}"
                    Log.d(TAG, "Trying endpoint: $endpoint")

                    val request = Request.Builder()
                        .url(url)
                        .addHeader("X-Plex-Product", "PlexScreensaver")
                        .addHeader("X-Plex-Version", "1.0")
                        .addHeader("X-Plex-Device", "Android")
                        .addHeader("X-Plex-Device-Name", "Screensaver")
                        .addHeader("X-Plex-Client-Identifier", "com.willbeeching.flix")
                        .addHeader("Accept", "application/json")
                        .build()

                    val response = client.newCall(request).execute()
                    val body = response.body?.string()

                    if (response.isSuccessful && body != null) {
                        Log.d(TAG, "Response from $endpoint: ${body.take(500)}")

                        // Look for various title card fields in XML
                        val titleCardPatterns = listOf(
                            "titleCard=\"([^\"]+)\"",
                            "banner=\"([^\"]+)\"",
                            "logo=\"([^\"]+)\"",
                            "<Image[^>]*type=\"titleCard\"[^>]*url=\"([^\"]+)\"",
                            "<Image[^>]*type=\"banner\"[^>]*url=\"([^\"]+)\""
                        )

                        for (pattern in titleCardPatterns) {
                            val regex = Regex(pattern)
                            val match = regex.find(body)
                            if (match != null) {
                                val path = match.groupValues[1]
                                Log.d(TAG, "Found title card path: $path")
                                return@withContext buildImageUrl(server.uri, path, server.accessToken, width = 800, format = "jpg")
                            }
                        }
                    }
                }

                Log.d(TAG, "No title card found for ratingKey: $ratingKey")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching title card", e)
                null
            }
        }
    }

    /**
     * Build a full image URL from a Plex path
     */
    private fun buildImageUrl(serverUri: String, path: String, token: String, width: Int? = null, format: String? = null): String {
        val params = mutableListOf("X-Plex-Token=$token")
        if (width != null) {
            params.add("width=$width")
        }
        if (format != null) {
            params.add("format=$format")
        }
        return "$serverUri$path?${params.joinToString("&")}"
    }
}

