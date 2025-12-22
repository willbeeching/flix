package com.willbeeching.flix.plex

import android.util.Log
import android.util.Xml
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.HostnameVerifier

/**
 * Client for interacting with Plex Media Server API
 */
class PlexApiClient(private val authToken: String) {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
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
    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    companion object {
        private const val TAG = "PlexApiClient"
        private const val PLEX_TV_BASE = "https://plex.tv"
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
        val accessToken: String
    )

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
     * Discover available Plex servers
     */
    suspend fun discoverServers(): Result<List<PlexServer>> {
        return try {
            val request = Request.Builder()
                .url("$PLEX_TV_BASE/api/v2/resources?includeHttps=1&includeRelay=0")
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

            // Filter for servers and create PlexServer objects
            val servers = resources
                .filter { it.provides?.contains("server") == true && it.owned }
                .mapNotNull { resource ->
                    // Prefer local connections, then https, then http
                    val connection = resource.connections
                        ?.sortedWith(
                            compareByDescending<Connection> { it.local }
                                .thenByDescending { it.protocol == "https" }
                        )
                        ?.firstOrNull()

                    if (connection != null && resource.accessToken != null && resource.clientIdentifier != null) {
                        PlexServer(
                            name = resource.name,
                            clientIdentifier = resource.clientIdentifier,
                            uri = connection.uri,
                            accessToken = resource.accessToken
                        )
                    } else {
                        null
                    }
                }

            Log.d(TAG, "Discovered ${servers.size} servers")
            Result.success(servers)
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering servers", e)
            Result.failure(e)
        }
    }

    /**
     * Get library sections from a Plex server
     */
    suspend fun getLibrarySections(server: PlexServer): Result<List<LibrarySection>> {
        return try {
            val url = "${server.uri}/library/sections?X-Plex-Token=${server.accessToken}"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (!response.isSuccessful || responseBody == null) {
                return Result.failure(Exception("Failed to get library sections: ${response.code}"))
            }

            // Parse XML
            val sections = parseLibrarySections(responseBody)
            Log.d(TAG, "Found ${sections.size} library sections")
            Result.success(sections)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting library sections", e)
            Result.failure(e)
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
            Log.e(TAG, "Error getting artwork", e)
            Result.failure(e)
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

