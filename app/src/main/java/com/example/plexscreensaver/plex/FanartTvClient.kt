package com.example.plexscreensaver.plex

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Client for interacting with Fanart.tv API
 * Provides high-quality, text-free backgrounds for movies and TV shows
 * @param apiKey API key (required - returns null results if not provided)
 */
class FanartTvClient(private val apiKey: String?) {

    private val client = createTrustAllClient()
    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    // Cache to avoid duplicate API calls
    private val imageCache = mutableMapOf<String, FanartResponse>()

    // Check if client is configured
    val isConfigured: Boolean
        get() = apiKey != null

    companion object {
        private const val TAG = "FanartTvClient"
        private const val BASE_URL = "https://webservice.fanart.tv/v3.2"

        /**
         * Create OkHttpClient that bypasses SSL validation
         * Needed due to OCSP validation issues on some Android devices
         */
        private fun createTrustAllClient(): OkHttpClient {
            return try {
                val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                })

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())

                OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create trust-all client, using default", e)
                OkHttpClient.Builder().build()
            }
        }
    }

    // Response classes - each image includes width/height for resolution detection
    @JsonClass(generateAdapter = true)
    data class FanartResponse(
        // Movie images
        @Json(name = "moviebackground") val movieBackgrounds: List<FanartImage>? = null,
        @Json(name = "hdmovielogo") val movieLogos: List<FanartImage>? = null,
        // TV show images
        @Json(name = "showbackground") val showBackgrounds: List<FanartImage>? = null,
        @Json(name = "hdtvlogo") val tvLogos: List<FanartImage>? = null,
        @Json(name = "hdclearlogo") val clearLogos: List<FanartImage>? = null
    )

    @JsonClass(generateAdapter = true)
    data class FanartImage(
        @Json(name = "id") val id: String,
        @Json(name = "url") val url: String,
        @Json(name = "lang") val lang: String = "",
        @Json(name = "likes") val likes: String = "0",
        @Json(name = "width") val width: String = "0",
        @Json(name = "height") val height: String = "0"
    ) {
        val likesInt: Int
            get() = likes.toIntOrNull() ?: 0

        val widthInt: Int
            get() = width.toIntOrNull() ?: 0

        val heightInt: Int
            get() = height.toIntOrNull() ?: 0

        // Empty lang means text-free image
        val isTextFree: Boolean
            get() = lang.isEmpty()

        // Check if this is 4K resolution (3840x2160 or higher)
        val is4K: Boolean
            get() = widthInt >= 3840

        // Resolution string for logging
        val resolution: String
            get() = "${widthInt}x${heightInt}"
    }

    /**
     * Extract TVDB ID from a Plex GUID string (may contain multiple GUIDs separated by |)
     * Examples:
     * - tvdb://123 -> 123
     * - tmdb://456|tvdb://123|imdb://tt789 -> 123
     */
    fun extractTvdbId(guids: String): String? {
        // Search for TVDB reference anywhere in the GUID string
        val tvdbRegex = Regex("tvdb://(?:[^/|]+/)?(\\d+)")
        val match = tvdbRegex.find(guids)
        if (match != null) {
            val id = match.groupValues[1]
            Log.d(TAG, "Extracted TVDB ID from guids: $id")
            return id
        }
        Log.d(TAG, "No TVDB ID found in guids: $guids")
        return null
    }

    /**
     * Extract TMDB ID from Plex GUID string (may contain multiple GUIDs separated by |)
     */
    fun extractTmdbId(guids: String): String? {
        val tmdbRegex = Regex("tmdb://(?:[^/|]+/)?(\\d+)")
        val match = tmdbRegex.find(guids)
        if (match != null) {
            val id = match.groupValues[1]
            Log.d(TAG, "Extracted TMDB ID from guids: $id")
            return id
        }
        return null
    }

    /**
     * Fetch images from Fanart.tv
     */
    private suspend fun fetchFromFanart(endpoint: String): FanartResponse? {
        // Skip if no API key configured
        if (apiKey == null) {
            Log.d(TAG, "Fanart.tv API key not configured, skipping")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val cacheKey = endpoint
                imageCache[cacheKey]?.let {
                    Log.d(TAG, "Using cached Fanart.tv response for $cacheKey")
                    return@withContext it
                }

                val url = "$BASE_URL$endpoint?api_key=$apiKey"
                Log.d(TAG, "Fetching from Fanart.tv: $endpoint")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.w(TAG, "Fanart.tv API returned ${response.code} for $endpoint")
                    return@withContext null
                }

                val body = response.body?.string()
                if (body.isNullOrEmpty()) {
                    Log.w(TAG, "Empty response from Fanart.tv")
                    return@withContext null
                }

                val adapter = moshi.adapter(FanartResponse::class.java)
                val result = adapter.fromJson(body)

                // Cache successful response
                if (result != null) {
                    imageCache[cacheKey] = result
                    Log.d(TAG, "Cached Fanart.tv response for $cacheKey")
                }

                result
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching from Fanart.tv", e)
                null
            }
        }
    }

    /**
     * Get best background from a list of Fanart images
     * Prefers: 1) Highest resolution, 2) Text-free (lang=""), 3) Most likes
     * If preferredId is specified, returns that specific image
     */
    private fun selectBestBackground(backgrounds: List<FanartImage>?, preferredId: String? = null): FanartImage? {
        if (backgrounds.isNullOrEmpty()) return null

        // If a specific ID is requested, find it
        if (preferredId != null) {
            val preferred = backgrounds.find { it.id == preferredId }
            if (preferred != null) {
                Log.d(TAG, "Using preferred background ID $preferredId: ${preferred.resolution}, ${preferred.likesInt} likes")
                return preferred
            } else {
                Log.w(TAG, "Preferred background ID $preferredId not found, falling back to auto-select")
            }
        }

        // Prefer text-free backgrounds (empty lang)
        val textFree = backgrounds.filter { it.isTextFree }
        val candidates = if (textFree.isNotEmpty()) textFree else backgrounds.filter { it.lang == "en" }.ifEmpty { backgrounds }

        // Sort by resolution (highest first), then by likes
        val sorted = candidates.sortedWith(
            compareByDescending<FanartImage> { it.widthInt }
                .thenByDescending { it.likesInt }
        )

        val best = sorted.firstOrNull()
        if (best != null) {
            val langType = if (best.isTextFree) "text-free" else "lang=${best.lang}"
            Log.d(TAG, "Selected $langType background: ${best.resolution}, ${best.likesInt} likes")
        }
        return best
    }

    /**
     * Get best logo from a list of Fanart images
     * Prefers English logos (logos have text by design), sorted by resolution then likes
     */
    private fun selectBestLogo(logos: List<FanartImage>?): FanartImage? {
        if (logos.isNullOrEmpty()) return null

        // Prefer English logos
        val english = logos.filter { it.lang == "en" }
        val candidates = if (english.isNotEmpty()) english else logos

        // Sort by resolution (highest first), then by likes
        val sorted = candidates.sortedWith(
            compareByDescending<FanartImage> { it.widthInt }
                .thenByDescending { it.likesInt }
        )

        val best = sorted.firstOrNull()
        if (best != null) {
            Log.d(TAG, "Selected logo: ${best.resolution}, ${best.likesInt} likes")
        }
        return best
    }

    /**
     * Get background and logo for a movie using TMDB ID
     * Returns Pair(backdropUrl, logoUrl)
     */
    private suspend fun getMovieImages(tmdbId: String, preferredArtworkId: String? = null): Pair<String?, String?> {
        val response = fetchFromFanart("/movies/$tmdbId")

        // Get all backgrounds and select best by resolution (or preferred ID)
        val backgrounds = response?.movieBackgrounds
        Log.d(TAG, "Movie backgrounds: ${backgrounds?.size ?: 0} available")

        val backdrop = selectBestBackground(backgrounds, preferredArtworkId)
        if (backdrop != null) {
            val resLabel = if (backdrop.is4K) "4K" else "HD"
            Log.d(TAG, "✓ Movie background: $resLabel ${backdrop.resolution} - ${backdrop.url.substringAfterLast("/")}")
        }

        // Get best logo
        val logos = response?.movieLogos
        Log.d(TAG, "Movie logos: ${logos?.size ?: 0} available")
        val logo = selectBestLogo(logos)
        if (logo != null) {
            Log.d(TAG, "✓ Movie logo: ${logo.resolution} - ${logo.url.substringAfterLast("/")}")
        }

        return Pair(backdrop?.url, logo?.url)
    }

    /**
     * Get background and logo for a TV show using TVDB ID
     * Returns Pair(backdropUrl, logoUrl)
     */
    private suspend fun getTvShowImages(tvdbId: String, preferredArtworkId: String? = null): Pair<String?, String?> {
        val response = fetchFromFanart("/tv/$tvdbId")

        // Get all backgrounds and select best by resolution (or preferred ID)
        val backgrounds = response?.showBackgrounds
        Log.d(TAG, "TV backgrounds: ${backgrounds?.size ?: 0} available")

        val backdrop = selectBestBackground(backgrounds, preferredArtworkId)
        if (backdrop != null) {
            val resLabel = if (backdrop.is4K) "4K" else "HD"
            Log.d(TAG, "✓ TV background: $resLabel ${backdrop.resolution} - ${backdrop.url.substringAfterLast("/")}")
        }

        // Get best logo (try hdtvlogo first, then hdclearlogo)
        val tvLogos = response?.tvLogos
        val clearLogos = response?.clearLogos
        Log.d(TAG, "TV logos: ${tvLogos?.size ?: 0} hdtvlogo, ${clearLogos?.size ?: 0} hdclearlogo")
        val logo = selectBestLogo(tvLogos) ?: selectBestLogo(clearLogos)
        if (logo != null) {
            Log.d(TAG, "✓ TV logo: ${logo.resolution} - ${logo.url.substringAfterLast("/")}")
        }

        return Pair(backdrop?.url, logo?.url)
    }

    /**
     * Get background and logo for a TV show using TMDB ID
     * Returns Pair(backdropUrl, logoUrl)
     */
    private suspend fun getTvShowImagesByTmdb(tmdbId: String, preferredArtworkId: String? = null): Pair<String?, String?> {
        val response = fetchFromFanart("/tv/$tmdbId")

        // Get all backgrounds and select best by resolution (or preferred ID)
        val backgrounds = response?.showBackgrounds
        Log.d(TAG, "TV backgrounds (TMDB): ${backgrounds?.size ?: 0} available")

        val backdrop = selectBestBackground(backgrounds, preferredArtworkId)
        if (backdrop != null) {
            val resLabel = if (backdrop.is4K) "4K" else "HD"
            Log.d(TAG, "✓ TV background (TMDB): $resLabel ${backdrop.resolution} - ${backdrop.url.substringAfterLast("/")}")
        }

        // Get best logo
        val tvLogos = response?.tvLogos
        val clearLogos = response?.clearLogos
        Log.d(TAG, "TV logos (TMDB): ${tvLogos?.size ?: 0} hdtvlogo, ${clearLogos?.size ?: 0} hdclearlogo")
        val logo = selectBestLogo(tvLogos) ?: selectBestLogo(clearLogos)
        if (logo != null) {
            Log.d(TAG, "✓ TV logo (TMDB): ${logo.resolution} - ${logo.url.substringAfterLast("/")}")
        }

        return Pair(backdrop?.url, logo?.url)
    }

    /**
     * Get backdrop and logo from Plex GUID
     * Returns Pair(backdropUrl, logoUrl)
     * Automatically detects movie vs TV show and uses appropriate ID
     * @param preferredArtworkId Optional Fanart.tv image ID to use instead of auto-selecting
     */
    suspend fun getImagesFromGuid(guid: String, itemType: String?, preferredArtworkId: String? = null): Pair<String?, String?> {
        return withContext(Dispatchers.IO) {
            when (itemType) {
                "movie" -> {
                    val tmdbId = extractTmdbId(guid)
                    if (tmdbId != null) {
                        getMovieImages(tmdbId, preferredArtworkId)
                    } else Pair(null, null)
                }
                "show", "episode" -> {
                    // Try TVDB ID first (preferred for TV shows)
                    val tvdbId = extractTvdbId(guid)
                    if (tvdbId != null) {
                        val result = getTvShowImages(tvdbId, preferredArtworkId)
                        if (result.first != null || result.second != null) {
                            return@withContext result
                        }
                    }
                    // Fall back to TMDB ID if no TVDB ID or no results
                    val tmdbId = extractTmdbId(guid)
                    if (tmdbId != null) {
                        Log.d(TAG, "Trying TMDB ID $tmdbId for TV show (TVDB fallback)")
                        getTvShowImagesByTmdb(tmdbId, preferredArtworkId)
                    } else Pair(null, null)
                }
                else -> {
                    // Try movie first, then TV
                    val tmdbId = extractTmdbId(guid)
                    if (tmdbId != null) {
                        val result = getMovieImages(tmdbId, preferredArtworkId)
                        if (result.first != null || result.second != null) {
                            return@withContext result
                        }
                    }
                    val tvdbId = extractTvdbId(guid)
                    if (tvdbId != null) {
                        getTvShowImages(tvdbId, preferredArtworkId)
                    } else Pair(null, null)
                }
            }
        }
    }
}



