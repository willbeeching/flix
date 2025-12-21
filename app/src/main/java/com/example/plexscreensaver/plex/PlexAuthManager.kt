package com.example.plexscreensaver.plex

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * Manages Plex Link authentication flow (OAuth device linking)
 */
class PlexAuthManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
            level = okhttp3.logging.HttpLoggingInterceptor.Level.BODY
        })
        .build()
    private val moshi = Moshi.Builder()
        .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    companion object {
        private const val TAG = "PlexAuthManager"
        private const val PREFS_NAME = "plex_auth"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SELECTED_SERVER_NAME = "selected_server_name"
        private const val KEY_SELECTED_SERVER_ID = "selected_server_id"
        private const val KEY_SELECTED_LIBRARIES = "selected_libraries"

        private const val PLEX_TV_BASE = "https://plex.tv"
        private const val PLEX_PRODUCT = "Plex Screensaver"
        private const val PLEX_VERSION = "1.0"
        private const val PLEX_DEVICE = "Android TV"

        // Client identifier (should be unique per app)
        private const val CLIENT_IDENTIFIER = "com.example.plexscreensaver"
    }

    @JsonClass(generateAdapter = true)
    data class PinResponse(
        val id: Int,
        val code: String,
        val qr: String? = null,
        @Json(name = "product") val product: String? = null,
        @Json(name = "trusted") val trusted: Boolean = false,
        @Json(name = "clientIdentifier") val clientIdentifier: String? = null,
        @Json(name = "authToken") val authToken: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class PinCheckResponse(
        val id: Int,
        val code: String,
        @Json(name = "authToken") val authToken: String? = null
    )

    /**
     * Result of requesting a Plex Link PIN
     */
    data class LinkResult(
        val pinId: Int,
        val code: String,
        val linkUrl: String
    )

    /**
     * Get the device ID, creating one if it doesn't exist
     */
    private fun getDeviceId(): String {
        var deviceId = prefs.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    /**
     * Build common Plex headers
     */
    private fun getPlexHeaders(): Map<String, String> {
        return mapOf(
            "X-Plex-Product" to PLEX_PRODUCT,
            "X-Plex-Version" to PLEX_VERSION,
            "X-Plex-Client-Identifier" to getDeviceId(),
            "X-Plex-Device" to PLEX_DEVICE,
            "X-Plex-Device-Name" to "Android TV Screensaver",
            "X-Plex-Platform" to "Android",
            "X-Plex-Platform-Version" to "13.0",
            "Accept" to "application/json",
            "Content-Type" to "application/x-www-form-urlencoded"
        )
    }

    /**
     * Step 1: Request a PIN from Plex for device linking
     */
    suspend fun requestPin(): Result<LinkResult> {
        return try {
            // Explicitly request a standard 4-character PIN (not strong)
            val requestBody = "strong=false".toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val request = Request.Builder()
                .url("$PLEX_TV_BASE/api/v2/pins")
                .post(requestBody)
                .apply {
                    getPlexHeaders().forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "PIN request response code: ${response.code}")
            Log.d(TAG, "PIN request response headers: ${response.headers}")
            Log.d(TAG, "PIN request response body: $responseBody")

            if (!response.isSuccessful || responseBody == null) {
                val errorMsg = "Failed to request PIN: ${response.code} - ${response.message}"
                Log.e(TAG, errorMsg)
                Log.e(TAG, "Response body: $responseBody")
                return Result.failure(Exception(errorMsg))
            }

            val adapter = moshi.adapter(PinResponse::class.java)
            val pinResponse = adapter.fromJson(responseBody)
                ?: return Result.failure(Exception("Failed to parse PIN response"))

            Log.d(TAG, "PIN requested successfully: ${pinResponse.code}")
            Log.d(TAG, "QR URL from Plex: ${pinResponse.qr}")

            // Construct the proper link URL with the PIN code embedded
            val linkUrl = "$PLEX_TV_BASE/link#!?code=${pinResponse.code}"

            Result.success(
                LinkResult(
                    pinId = pinResponse.id,
                    code = pinResponse.code,
                    linkUrl = linkUrl
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting PIN", e)
            Result.failure(e)
        }
    }

    /**
     * Step 2: Poll Plex to check if the user has linked the device
     * Returns the auth token if linking is complete, null otherwise
     */
    suspend fun checkPin(pinId: Int): Result<String?> {
        return try {
            val request = Request.Builder()
                .url("$PLEX_TV_BASE/api/v2/pins/$pinId")
                .get()
                .apply {
                    getPlexHeaders().forEach { (key, value) ->
                        addHeader(key, value)
                    }
                }
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "PIN check response code: ${response.code}")
            Log.d(TAG, "PIN check response body: $responseBody")

            if (!response.isSuccessful || responseBody == null) {
                val errorMsg = "Failed to check PIN: ${response.code} - ${response.message}"
                Log.e(TAG, errorMsg)
                Log.e(TAG, "Response body: $responseBody")
                return Result.failure(Exception(errorMsg))
            }

            val adapter = moshi.adapter(PinCheckResponse::class.java)
            val checkResponse = adapter.fromJson(responseBody)
                ?: return Result.failure(Exception("Failed to parse PIN check response"))

            if (checkResponse.authToken != null) {
                // Save the token
                saveAuthToken(checkResponse.authToken)
                Log.d(TAG, "Auth token obtained successfully")
            }

            Result.success(checkResponse.authToken)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking PIN", e)
            Result.failure(e)
        }
    }

    /**
     * Poll for PIN completion with timeout
     * @param pinId The PIN ID to poll
     * @param timeoutSeconds Maximum time to poll in seconds
     * @param intervalMs Polling interval in milliseconds
     */
    suspend fun pollForAuth(
        pinId: Int,
        timeoutSeconds: Int = 300,
        intervalMs: Long = 2000
    ): Result<String> {
        val startTime = System.currentTimeMillis()
        val timeoutMs = timeoutSeconds * 1000L
        var attemptCount = 0

        Log.d(TAG, "Starting to poll for auth. PIN ID: $pinId, Timeout: ${timeoutSeconds}s")

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            attemptCount++
            Log.d(TAG, "Polling attempt #$attemptCount for PIN $pinId")

            val result = checkPin(pinId)

            if (result.isFailure) {
                Log.e(TAG, "PIN check failed: ${result.exceptionOrNull()?.message}")
                return Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }

            val token = result.getOrNull()
            if (token != null) {
                Log.d(TAG, "Auth token received! Polling complete after $attemptCount attempts")
                return Result.success(token)
            }

            Log.d(TAG, "No token yet, waiting ${intervalMs}ms before next check...")
            delay(intervalMs)
        }

        Log.e(TAG, "Polling timeout after $attemptCount attempts and ${timeoutSeconds}s")
        return Result.failure(Exception("Timeout waiting for user to link device"))
    }

    /**
     * Save the auth token
     */
    private fun saveAuthToken(token: String) {
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    /**
     * Get the saved auth token
     */
    fun getAuthToken(): String? {
        return prefs.getString(KEY_AUTH_TOKEN, null)
    }

    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean {
        return getAuthToken() != null
    }

    /**
     * Sign out and clear the auth token
     */
    fun signOut() {
        prefs.edit()
            .remove(KEY_AUTH_TOKEN)
            .remove(KEY_SELECTED_SERVER_NAME)
            .remove(KEY_SELECTED_SERVER_ID)
            .remove(KEY_SELECTED_LIBRARIES)
            .apply()
        Log.d(TAG, "User signed out")
    }

    /**
     * Save the selected server
     */
    fun saveSelectedServer(serverName: String, serverId: String) {
        prefs.edit()
            .putString(KEY_SELECTED_SERVER_NAME, serverName)
            .putString(KEY_SELECTED_SERVER_ID, serverId)
            .apply()
        Log.d(TAG, "Selected server saved: $serverName")
    }

    /**
     * Get the selected server name
     */
    fun getSelectedServerName(): String? {
        return prefs.getString(KEY_SELECTED_SERVER_NAME, null)
    }

    /**
     * Get the selected server ID
     */
    fun getSelectedServerId(): String? {
        return prefs.getString(KEY_SELECTED_SERVER_ID, null)
    }

    /**
     * Check if a server is selected
     */
    fun hasSelectedServer(): Boolean {
        return getSelectedServerId() != null
    }

    /**
     * Save selected library IDs (comma-separated)
     */
    fun saveSelectedLibraries(libraryIds: Set<String>) {
        prefs.edit()
            .putString(KEY_SELECTED_LIBRARIES, libraryIds.joinToString(","))
            .apply()
        Log.d(TAG, "Selected libraries saved: ${libraryIds.size} libraries")
    }

    /**
     * Get selected library IDs
     */
    fun getSelectedLibraries(): Set<String> {
        val librariesStr = prefs.getString(KEY_SELECTED_LIBRARIES, null)
        return if (librariesStr.isNullOrEmpty()) {
            emptySet()
        } else {
            librariesStr.split(",").toSet()
        }
    }

    /**
     * Check if libraries are selected
     */
    fun hasSelectedLibraries(): Boolean {
        return getSelectedLibraries().isNotEmpty()
    }
}

