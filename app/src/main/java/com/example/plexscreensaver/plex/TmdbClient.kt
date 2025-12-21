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
 * Client for interacting with The Movie Database (TMDB) API
 * Used to fetch title logos and other artwork not available from Plex
 * @param apiKey API key (required - returns null results if not provided)
 */
class TmdbClient(private val apiKey: String?) {

    private val client = createTrustAllClient()
    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    // Simple in-memory cache to avoid duplicate API calls
    // TMDB free tier: 40 requests per 10 seconds - we're being careful!
    private val imageCache = mutableMapOf<String, ImagesResponse>()

    // Check if client is configured
    val isConfigured: Boolean
        get() = apiKey != null

    companion object {
        private const val TAG = "TmdbClient"
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/original"

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

    @JsonClass(generateAdapter = true)
    data class ImagesResponse(
        @Json(name = "id") val id: Int,
        @Json(name = "logos") val logos: List<LogoImage>?,
        @Json(name = "backdrops") val backdrops: List<BackdropImage>?
    )

    @JsonClass(generateAdapter = true)
    data class LogoImage(
        @Json(name = "file_path") val filePath: String,
        @Json(name = "width") val width: Int,
        @Json(name = "height") val height: Int,
        @Json(name = "vote_average") val voteAverage: Double? = 0.0,
        @Json(name = "vote_count") val voteCount: Int? = 0,
        @Json(name = "iso_639_1") val language: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class BackdropImage(
        @Json(name = "file_path") val filePath: String,
        @Json(name = "width") val width: Int,
        @Json(name = "height") val height: Int,
        @Json(name = "vote_average") val voteAverage: Double? = 0.0,
        @Json(name = "vote_count") val voteCount: Int? = 0,
        @Json(name = "iso_639_1") val language: String? = null
    ) {
        // Calculate aspect ratio for filtering
        val aspectRatio: Double
            get() = width.toDouble() / height.toDouble()

        // Check if this is close to 16:9 TV ratio (1.777...)
        val isTvRatio: Boolean
            get() = aspectRatio in 1.75..1.80
    }

    /**
     * Extract TMDB ID from a Plex GUID
     * Examples:
     * - tmdb://123 -> 123 (common Plex format)
     * - tmdb://movie/123 -> 123 (with type)
     * - tmdb://tv/456 -> 456 (with type)
     */
    fun extractTmdbId(guid: String, itemType: String?): Pair<String?, String?> {
        // Check for TMDB reference with type: tmdb://movie/123 or tmdb://tv/456
        val tmdbWithTypeRegex = Regex("tmdb://([^/]+)/(\\d+)")
        val matchWithType = tmdbWithTypeRegex.find(guid)
        if (matchWithType != null) {
            val type = matchWithType.groupValues[1] // "movie" or "tv"
            val id = matchWithType.groupValues[2]
            Log.d(TAG, "Extracted TMDB ID from guid: type=$type, id=$id")
            return Pair(type, id)
        }

        // Check for simple TMDB reference: tmdb://123
        val tmdbSimpleRegex = Regex("tmdb://(\\d+)")
        val matchSimple = tmdbSimpleRegex.find(guid)
        if (matchSimple != null) {
            val id = matchSimple.groupValues[1]
            // Infer type from Plex item type
            val type = when (itemType) {
                "movie" -> "movie"
                "show" -> "tv"
                else -> "movie" // Default to movie
            }
            Log.d(TAG, "Extracted TMDB ID from guid: type=$type (inferred from $itemType), id=$id")
            return Pair(type, id)
        }

        Log.d(TAG, "No TMDB ID found in guid: $guid")
        return Pair(null, null)
    }

    /**
     * Fetch images from TMDB with caching to minimize API calls
     * IMPORTANT: This fetches both logos and backdrops in ONE API call to save quota
     */
    private suspend fun fetchImagesFromTmdb(type: String, tmdbId: String): ImagesResponse? {
        // Skip if no API key configured
        if (apiKey == null) {
            Log.d(TAG, "TMDB API key not configured, skipping")
            return null
        }

        return withContext(Dispatchers.IO) {
            try {
                val cacheKey = "$type/$tmdbId"

                // Check cache first to avoid unnecessary API calls
                imageCache[cacheKey]?.let {
                    Log.d(TAG, "Using cached TMDB images for $cacheKey")
                    return@withContext it
                }

                val endpoint = when (type) {
                    "movie" -> "/movie/$tmdbId/images"
                    "tv", "show" -> "/tv/$tmdbId/images"
                    else -> {
                        Log.w(TAG, "Unknown media type: $type")
                        return@withContext null
                    }
                }

                val url = "$BASE_URL$endpoint?api_key=$apiKey"
                Log.d(TAG, "Fetching TMDB images from: $endpoint (API call)")

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Accept", "application/json")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string()

                if (!response.isSuccessful || body == null) {
                    Log.e(TAG, "Failed to fetch TMDB images: ${response.code}")
                    return@withContext null
                }

                val adapter = moshi.adapter(ImagesResponse::class.java)
                val imagesResponse = adapter.fromJson(body)

                // Cache the response to avoid re-fetching
                if (imagesResponse != null) {
                    imageCache[cacheKey] = imagesResponse
                    Log.d(TAG, "Cached TMDB images for $cacheKey (cache size: ${imageCache.size})")
                }

                imagesResponse
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching TMDB images", e)
                null
            }
        }
    }

    /**
     * Fetch title logo URL from TMDB
     */
    suspend fun getTitleLogoUrl(type: String, tmdbId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val imagesResponse = fetchImagesFromTmdb(type, tmdbId) ?: return@withContext null

                // Filter for English logos first, then get the best one (highest rated)
                val englishLogos = imagesResponse.logos
                    ?.filter { it.filePath.isNotEmpty() }
                    ?.filter { it.language == "en" || it.language == null }

                Log.d(TAG, "Found ${imagesResponse.logos?.size ?: 0} total logos, ${englishLogos?.size ?: 0} English logos")

                // Get the best logo (highest rated, or largest if no votes)
                val bestLogo = englishLogos
                    ?.maxByOrNull { (it.voteAverage ?: 0.0) * 100 + (it.voteCount ?: 0) }

                if (bestLogo != null) {
                    val logoUrl = "$IMAGE_BASE_URL${bestLogo.filePath}"
                    Log.d(TAG, "Selected TMDB logo: $logoUrl")
                    return@withContext logoUrl
                }

                Log.d(TAG, "No English logos found in TMDB")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Error selecting TMDB logo", e)
                null
            }
        }
    }

        /**
         * Fetch high-quality backdrop URL from TMDB (clean, 16:9 ratio, no logos)
         * Prioritizes TV-ratio (16:9) backdrops that are highly rated
         */
        suspend fun getBackdropUrl(type: String, tmdbId: String): String? {
            return withContext(Dispatchers.IO) {
                try {
                    val imagesResponse = fetchImagesFromTmdb(type, tmdbId) ?: return@withContext null

                    // Prefer null language backdrops (text-free, no logos/titles)
                    // Fall back to English language if no text-free options
                    val allBackdrops = imagesResponse.backdrops
                        ?.filter { it.filePath.isNotEmpty() }
                        ?: emptyList()

                    val textFreeBackdrops = allBackdrops.filter { it.language == null }
                    val englishBackdrops = allBackdrops.filter { it.language == "en" }

                    // Prefer text-free backdrops, fall back to English
                    val cleanBackdrops = if (textFreeBackdrops.isNotEmpty()) {
                        Log.d(TAG, "Using ${textFreeBackdrops.size} text-free backdrops (lang=null)")
                        textFreeBackdrops
                    } else if (englishBackdrops.isNotEmpty()) {
                        Log.d(TAG, "No text-free backdrops, falling back to ${englishBackdrops.size} English backdrops")
                        englishBackdrops
                    } else {
                        Log.d(TAG, "No suitable language backdrops found")
                        emptyList()
                    }

                    Log.d(TAG, "Found ${imagesResponse.backdrops?.size ?: 0} total backdrops, ${cleanBackdrops.size} clean backdrops")

                    // Prioritize 16:9 TV ratio backdrops (best for screensavers)
                    val tvRatioBackdrops = cleanBackdrops.filter { it.isTvRatio }

                    // Choose from TV ratio first, fall back to all clean backdrops
                    val candidateBackdrops = if (tvRatioBackdrops.isNotEmpty()) {
                        Log.d(TAG, "Found ${tvRatioBackdrops.size} TV-ratio (16:9) backdrops")
                        tvRatioBackdrops
                    } else {
                        Log.d(TAG, "No TV-ratio backdrops, using all ${cleanBackdrops.size} clean backdrops")
                        cleanBackdrops
                    }

                    // Select highest quality: highest rated, then highest resolution
                    // High vote average indicates clean, well-composed images (usually no logos)
                    val bestBackdrop = candidateBackdrops
                        .filter { (it.voteCount ?: 0) > 0 } // Must have at least 1 vote
                        .sortedWith(
                            compareByDescending<BackdropImage> { it.voteAverage ?: 0.0 }
                                .thenByDescending { it.width }
                        )
                        .firstOrNull()
                        ?: candidateBackdrops.maxByOrNull { it.width } // Fallback to highest resolution

                    if (bestBackdrop != null) {
                        val backdropUrl = "$IMAGE_BASE_URL${bestBackdrop.filePath}"
                        Log.d(TAG, "Selected backdrop: ${bestBackdrop.width}x${bestBackdrop.height} (${String.format("%.2f", bestBackdrop.aspectRatio)}:1) rating=${bestBackdrop.voteAverage}")
                        return@withContext backdropUrl
                    }

                    Log.d(TAG, "No suitable backdrops found in TMDB")
                    null
                } catch (e: Exception) {
                    Log.e(TAG, "Error selecting TMDB backdrop", e)
                    null
                }
            }
        }

    /**
     * Try to get title logo and backdrop from Plex guid
     * Returns Pair(backdropUrl, logoUrl)
     * EFFICIENT: Makes only ONE API call to get both images!
     */
    suspend fun getImagesFromGuid(guid: String, itemType: String?): Pair<String?, String?> {
        val (type, id) = extractTmdbId(guid, itemType)
        if (type != null && id != null) {
                // Fetch once, use the cached response for both backdrop and logo
                val imagesResponse = fetchImagesFromTmdb(type, id)
                if (imagesResponse != null) {
                    // Process backdrop - prefer text-free (lang=null), 16:9 TV ratio, highly rated
                    val allBackdrops = imagesResponse.backdrops
                        ?.filter { it.filePath.isNotEmpty() }
                        ?: emptyList()

                    // Prefer text-free backdrops (lang=null), fall back to English
                    val textFreeBackdrops = allBackdrops.filter { it.language == null }
                    val englishBackdrops = allBackdrops.filter { it.language == "en" }
                    val cleanBackdrops = if (textFreeBackdrops.isNotEmpty()) textFreeBackdrops
                        else if (englishBackdrops.isNotEmpty()) englishBackdrops
                        else emptyList()

                    val tvRatioBackdrops = cleanBackdrops.filter { it.isTvRatio }
                    val candidateBackdrops = if (tvRatioBackdrops.isNotEmpty()) tvRatioBackdrops else cleanBackdrops

                    val bestBackdrop = candidateBackdrops
                        .filter { (it.voteCount ?: 0) > 0 }
                        .sortedWith(
                            compareByDescending<BackdropImage> { it.voteAverage ?: 0.0 }
                                .thenByDescending { it.width }
                        )
                        .firstOrNull()
                        ?: candidateBackdrops.maxByOrNull { it.width }

                    val backdropUrl = bestBackdrop?.let { "$IMAGE_BASE_URL${it.filePath}" }

                // Process logo
                val englishLogos = imagesResponse.logos
                    ?.filter { it.filePath.isNotEmpty() }
                    ?.filter { it.language == "en" || it.language == null }
                val bestLogo = englishLogos
                    ?.maxByOrNull { (it.voteAverage ?: 0.0) * 100 + (it.voteCount ?: 0) }
                val logoUrl = bestLogo?.let { "$IMAGE_BASE_URL${it.filePath}" }

                Log.d(TAG, "Got images from cache/single request: backdrop=${backdropUrl != null}, logo=${logoUrl != null}")
                return Pair(backdropUrl, logoUrl)
            }
        }
        return Pair(null, null)
    }

    /**
     * Try to get title logo from Plex guid
     */
    suspend fun getTitleLogoFromGuid(guid: String, itemType: String?): String? {
        val (type, id) = extractTmdbId(guid, itemType)
        if (type != null && id != null) {
            return getTitleLogoUrl(type, id)
        }
        return null
    }
}

