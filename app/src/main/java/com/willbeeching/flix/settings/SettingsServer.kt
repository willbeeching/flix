package com.willbeeching.flix.settings

import android.content.Context
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Local HTTP server for configuring API keys via web browser
 * Users can scan a QR code to access the settings page on their phone
 */
class SettingsServer(
    private val context: Context,
    port: Int = 8888
) : NanoHTTPD(port) {

    private val apiKeyManager = ApiKeyManager(context)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    companion object {
        private const val TAG = "SettingsServer"

        // API validation endpoints
        private const val FANART_TEST_URL = "https://webservice.fanart.tv/v3.2/movies/299536" // Avengers: Infinity War
        private const val TMDB_TEST_URL = "https://api.themoviedb.org/3/movie/550" // Fight Club
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        Log.d(TAG, "Request: $method $uri")

        return when {
            uri == "/" || uri == "/index.html" -> serveSettingsPage()
            uri == "/save" && method == Method.POST -> handleSaveKeys(session)
            uri == "/disconnect" && method == Method.POST -> handleDisconnect(session)
            uri == "/status" -> serveStatus()
            uri == "/style.css" -> serveStylesheet()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
        }
    }

    private fun serveSettingsPage(): Response {
        val currentFanartKey = apiKeyManager.getFanartApiKey() ?: ""
        val currentTmdbKey = apiKeyManager.getTmdbApiKey() ?: ""
        val hasFanart = apiKeyManager.hasFanartApiKey()
        val hasTmdb = apiKeyManager.hasTmdbApiKey()

        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Flix Settings</title>
                <link rel="preconnect" href="https://fonts.googleapis.com">
                <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
                <link href="https://fonts.googleapis.com/css2?family=Google+Sans:wght@400;500;600;700&display=swap" rel="stylesheet">
                <link rel="stylesheet" href="/style.css">
            </head>
            <body>
                <div class="container">
                    <h1>API Settings</h1>
                    <p class="subtitle">Enter your API keys</p>

                    <form id="settingsForm" method="POST" action="/save">
                        <div class="form-group">
                            <div class="label-row">
                                <label for="fanartKey">Fanart.tv API Key</label>
                                <span id="fanartStatus" class="${if (hasFanart) "status-connected" else "status-disconnected"}">
                                    ${if (hasFanart) "✓ Connected" else "Not configured"}
                                </span>
                            </div>
                            <div class="input-row">
                                <input type="text" id="fanartKey" name="fanartKey"
                                       value="$currentFanartKey"
                                       placeholder="Enter your Fanart.tv API key">
                                ${if (hasFanart) """<button type="button" class="btn-disconnect" onclick="disconnect('fanart')">Disconnect</button>""" else ""}
                            </div>
                            <p class="help-text">
                                Get your free key at <a href="https://fanart.tv/get-an-api-key/" target="_blank">fanart.tv</a>
                            </p>
                        </div>

                        <div class="form-group">
                            <div class="label-row">
                                <label for="tmdbKey">TMDB API Key</label>
                                <span id="tmdbStatus" class="${if (hasTmdb) "status-connected" else "status-disconnected"}">
                                    ${if (hasTmdb) "✓ Connected" else "Not configured"}
                                </span>
                            </div>
                            <div class="input-row">
                                <input type="text" id="tmdbKey" name="tmdbKey"
                                       value="$currentTmdbKey"
                                       placeholder="Enter your TMDB API key">
                                ${if (hasTmdb) """<button type="button" class="btn-disconnect" onclick="disconnect('tmdb')">Disconnect</button>""" else ""}
                            </div>
                            <p class="help-text">
                                Get your free key at <a href="https://www.themoviedb.org/settings/api" target="_blank">themoviedb.org</a>
                            </p>
                        </div>

                        <button type="submit" id="submitBtn" class="btn-primary">
                            <span id="btnText">Save & Validate</span>
                            <span id="btnLoading" class="hidden">Validating...</span>
                        </button>
                    </form>

                    <div id="message" class="message hidden"></div>

                    <p class="footer">Keys are validated before saving.</p>
                </div>

                <script>
                    document.getElementById('settingsForm').addEventListener('submit', async (e) => {
                        e.preventDefault();
                        const formData = new FormData(e.target);
                        const params = new URLSearchParams();
                        formData.forEach((value, key) => params.append(key, value));

                        const submitBtn = document.getElementById('submitBtn');
                        const btnText = document.getElementById('btnText');
                        const btnLoading = document.getElementById('btnLoading');
                        const messageEl = document.getElementById('message');

                        // Show loading state
                        submitBtn.disabled = true;
                        btnText.classList.add('hidden');
                        btnLoading.classList.remove('hidden');
                        messageEl.className = 'message hidden';

                        try {
                            const response = await fetch('/save', {
                                method: 'POST',
                                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                body: params.toString()
                            });
                            const result = await response.text();
                            if (response.ok && result.includes('✓')) {
                                messageEl.innerHTML = result + '<br><br><span style="font-size: 13px; opacity: 0.8;">Settings synced to device. You can close this page.</span>';
                            } else {
                                messageEl.textContent = result;
                            }
                            messageEl.className = response.ok ? 'message success' : 'message error';
                        } catch (error) {
                            messageEl.textContent = 'Error connecting to server';
                            messageEl.className = 'message error';
                        } finally {
                            // Reset button state
                            submitBtn.disabled = false;
                            btnText.classList.remove('hidden');
                            btnLoading.classList.add('hidden');
                        }
                    });

                    async function disconnect(service) {
                        const messageEl = document.getElementById('message');
                        try {
                            const response = await fetch('/disconnect', {
                                method: 'POST',
                                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                                body: 'service=' + service
                            });
                            if (response.ok) {
                                // Reload page to update UI
                                window.location.reload();
                            } else {
                                messageEl.textContent = 'Failed to disconnect';
                                messageEl.className = 'message error';
                            }
                        } catch (error) {
                            messageEl.textContent = 'Error disconnecting';
                            messageEl.className = 'message error';
                        }
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/html", html)
    }

    private fun serveStylesheet(): Response {
        val css = """
            * {
                box-sizing: border-box;
                margin: 0;
                padding: 0;
            }

            body {
                font-family: 'Google Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                background: #0A0A0A;
                min-height: 100vh;
                color: #fff;
                padding: 40px 20px;
            }

            .container {
                max-width: 500px;
                margin: 0 auto;
            }

            h1 {
                text-align: left;
                margin-bottom: 8px;
                font-size: 24px;
                font-weight: 600;
                letter-spacing: 0.5px;
            }

            .subtitle {
                text-align: left;
                color: rgba(255, 255, 255, 0.4);
                margin-bottom: 40px;
                font-size: 16px;
                font-weight: 500;
            }

            .form-group {
                margin-bottom: 24px;
            }

            .label-row {
                display: flex;
                justify-content: space-between;
                align-items: center;
                margin-bottom: 12px;
            }

            label {
                font-weight: 600;
                font-size: 16px;
                color: #fff;
                letter-spacing: 0.3px;
            }

            .status-connected {
                font-size: 14px;
                color: #00E676;
                font-weight: 500;
            }

            .status-disconnected {
                font-size: 14px;
                color: rgba(255, 255, 255, 0.4);
                font-weight: 500;
            }

            .input-row {
                display: flex;
                gap: 12px;
            }

            .input-row input {
                flex: 1;
            }

            .btn-disconnect {
                padding: 0 20px;
                height: 48px;
                border: none;
                border-radius: 12px;
                background: rgba(255, 255, 255, 0.05);
                color: #FF453A;
                font-size: 14px;
                font-weight: 600;
                font-family: 'Google Sans', sans-serif;
                cursor: pointer;
                transition: all 0.2s;
                white-space: nowrap;
                letter-spacing: 0.3px;
            }

            .btn-disconnect:hover {
                background: rgba(255, 69, 58, 0.15);
            }

            input[type="text"] {
                width: 100%;
                height: 48px;
                padding: 0 16px;
                border: none;
                border-radius: 12px;
                background: rgba(255, 255, 255, 0.05);
                color: #fff;
                font-size: 16px;
                font-family: 'Google Sans', sans-serif;
                transition: background 0.2s;
            }

            input[type="text"]:focus {
                outline: none;
                background: rgba(255, 255, 255, 0.08);
            }

            input[type="text"]::placeholder {
                color: rgba(255, 255, 255, 0.3);
            }

            .help-text {
                margin-top: 8px;
                font-size: 13px;
                color: rgba(255, 255, 255, 0.5);
            }

            .help-text a {
                color: rgba(255, 255, 255, 0.7);
                text-decoration: underline;
            }

            .help-text a:hover {
                color: #fff;
            }

            .btn-primary {
                width: 100%;
                height: 48px;
                padding: 0 24px;
                border: none;
                border-radius: 12px;
                background: #fff;
                color: #000;
                font-size: 16px;
                font-weight: 600;
                font-family: 'Google Sans', sans-serif;
                cursor: pointer;
                transition: all 0.2s;
                letter-spacing: 0.5px;
            }

            .btn-primary:hover {
                background: rgba(255, 255, 255, 0.9);
            }

            .btn-primary:active {
                transform: scale(0.98);
            }

            .btn-primary:disabled {
                background: rgba(255, 255, 255, 0.3);
                color: rgba(0, 0, 0, 0.5);
                cursor: not-allowed;
            }

            .hidden {
                display: none !important;
            }

            .message {
                margin-top: 24px;
                padding: 16px;
                border-radius: 12px;
                text-align: center;
                font-weight: 500;
                font-size: 15px;
            }

            .message.success {
                background: rgba(0, 230, 118, 0.1);
                color: #00E676;
            }

            .message.error {
                background: rgba(255, 69, 58, 0.1);
                color: #FF453A;
            }

            .footer {
                margin-top: 32px;
                text-align: center;
                font-size: 14px;
                color: rgba(255, 255, 255, 0.4);
            }
        """.trimIndent()

        return newFixedLengthResponse(Response.Status.OK, "text/css", css)
    }

    private fun handleSaveKeys(session: IHTTPSession): Response {
        try {
            val params = mutableMapOf<String, String>()
            session.parseBody(params)

            // Get form data from POST body
            val postData = params["postData"] ?: session.queryParameterString ?: ""
            val formParams = parseFormData(postData)

            val fanartKey = formParams["fanartKey"] ?: ""
            val tmdbKey = formParams["tmdbKey"] ?: ""

            val errors = mutableListOf<String>()
            val successes = mutableListOf<String>()

            // Validate and save Fanart.tv key
            if (fanartKey.isNotBlank()) {
                val fanartValid = validateFanartKey(fanartKey)
                if (fanartValid) {
                    apiKeyManager.setFanartApiKey(fanartKey)
                    successes.add("Fanart.tv")
                    Log.d(TAG, "Validated and saved Fanart.tv API key")
                } else {
                    errors.add("Fanart.tv key is invalid")
                    Log.w(TAG, "Fanart.tv API key validation failed")
                }
            }

            // Validate and save TMDB key
            if (tmdbKey.isNotBlank()) {
                val tmdbValid = validateTmdbKey(tmdbKey)
                if (tmdbValid) {
                    apiKeyManager.setTmdbApiKey(tmdbKey)
                    successes.add("TMDB")
                    Log.d(TAG, "Validated and saved TMDB API key")
                } else {
                    errors.add("TMDB key is invalid")
                    Log.w(TAG, "TMDB API key validation failed")
                }
            }

            // Build response message
            return when {
                errors.isNotEmpty() && successes.isEmpty() -> {
                    newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain",
                        "✗ ${errors.joinToString(", ")}")
                }
                errors.isNotEmpty() && successes.isNotEmpty() -> {
                    newFixedLengthResponse(Response.Status.OK, "text/plain",
                        "⚠ Saved: ${successes.joinToString(", ")} | Failed: ${errors.joinToString(", ")}")
                }
                successes.isNotEmpty() -> {
                    newFixedLengthResponse(Response.Status.OK, "text/plain",
                        "✓ Validated and saved: ${successes.joinToString(", ")}")
                }
                else -> {
                    newFixedLengthResponse(Response.Status.OK, "text/plain",
                        "No keys provided")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving keys", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
        }
    }

    private fun handleDisconnect(session: IHTTPSession): Response {
        try {
            val params = mutableMapOf<String, String>()
            session.parseBody(params)

            val postData = params["postData"] ?: session.queryParameterString ?: ""
            val formParams = parseFormData(postData)
            val service = formParams["service"] ?: ""

            when (service) {
                "fanart" -> {
                    apiKeyManager.clearFanartApiKey()
                    Log.d(TAG, "Disconnected Fanart.tv")
                }
                "tmdb" -> {
                    apiKeyManager.clearTmdbApiKey()
                    Log.d(TAG, "Disconnected TMDB")
                }
                else -> {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/plain", "Unknown service")
                }
            }

            return newFixedLengthResponse(Response.Status.OK, "text/plain", "Disconnected")
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
        }
    }

    /**
     * Validate a Fanart.tv API key by making a test request
     */
    private fun validateFanartKey(apiKey: String): Boolean {
        return try {
            val url = "$FANART_TEST_URL?api_key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val isValid = response.isSuccessful
            response.close()
            Log.d(TAG, "Fanart.tv key validation: ${if (isValid) "success" else "failed (${response.code})"}")
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "Fanart.tv key validation error", e)
            false
        }
    }

    /**
     * Validate a TMDB API key by making a test request
     */
    private fun validateTmdbKey(apiKey: String): Boolean {
        return try {
            val url = "$TMDB_TEST_URL?api_key=$apiKey"
            val request = Request.Builder().url(url).get().build()
            val response = httpClient.newCall(request).execute()
            val isValid = response.isSuccessful
            response.close()
            Log.d(TAG, "TMDB key validation: ${if (isValid) "success" else "failed (${response.code})"}")
            isValid
        } catch (e: Exception) {
            Log.e(TAG, "TMDB key validation error", e)
            false
        }
    }

    private fun parseFormData(data: String): Map<String, String> {
        return data.split("&")
            .mapNotNull { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    java.net.URLDecoder.decode(parts[0], "UTF-8") to
                    java.net.URLDecoder.decode(parts[1], "UTF-8")
                } else null
            }
            .toMap()
    }

    private fun serveStatus(): Response {
        val status = mapOf(
            "fanartKeySet" to apiKeyManager.hasFanartApiKey(),
            "tmdbKeySet" to apiKeyManager.hasTmdbApiKey()
        )
        return newFixedLengthResponse(Response.Status.OK, "application/json",
            "{\"fanartKeySet\": ${status["fanartKeySet"]}, \"tmdbKeySet\": ${status["tmdbKeySet"]}}")
    }

    /**
     * Get the device's local IP address
     */
    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP address", e)
        }
        return null
    }

    /**
     * Check if running on an emulator
     */
    fun isEmulator(): Boolean {
        return (android.os.Build.FINGERPRINT.startsWith("generic")
                || android.os.Build.FINGERPRINT.startsWith("unknown")
                || android.os.Build.MODEL.contains("google_sdk")
                || android.os.Build.MODEL.contains("Emulator")
                || android.os.Build.MODEL.contains("Android SDK built for x86")
                || android.os.Build.MANUFACTURER.contains("Genymotion")
                || android.os.Build.BRAND.startsWith("generic")
                || android.os.Build.DEVICE.startsWith("generic")
                || "google_sdk" == android.os.Build.PRODUCT)
    }

    /**
     * Get the full URL to access the settings page
     */
    fun getSettingsUrl(): String? {
        val ip = getLocalIpAddress() ?: return null
        return "http://$ip:$listeningPort"
    }

    /**
     * Get the port number for ADB forwarding
     */
    fun getPort(): Int = listeningPort
}
