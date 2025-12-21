package com.example.plexscreensaver.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages API keys for external services (Fanart.tv, TMDB)
 * Stores keys securely in SharedPreferences
 * Returns null if no key is configured - sources without keys are skipped
 */
class ApiKeyManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "api_keys"
        private const val KEY_FANART_TV = "fanart_tv_api_key"
        private const val KEY_TMDB = "tmdb_api_key"
    }

    /**
     * Get Fanart.tv API key (null if not configured)
     */
    fun getFanartApiKey(): String? {
        return prefs.getString(KEY_FANART_TV, null)
    }

    /**
     * Set Fanart.tv API key
     */
    fun setFanartApiKey(apiKey: String) {
        prefs.edit().putString(KEY_FANART_TV, apiKey.trim()).apply()
    }

    /**
     * Clear Fanart.tv API key
     */
    fun clearFanartApiKey() {
        prefs.edit().remove(KEY_FANART_TV).apply()
    }

    /**
     * Check if Fanart.tv API key is configured
     */
    fun hasFanartApiKey(): Boolean {
        return prefs.getString(KEY_FANART_TV, null) != null
    }

    /**
     * Get TMDB API key (null if not configured)
     */
    fun getTmdbApiKey(): String? {
        return prefs.getString(KEY_TMDB, null)
    }

    /**
     * Set TMDB API key
     */
    fun setTmdbApiKey(apiKey: String) {
        prefs.edit().putString(KEY_TMDB, apiKey.trim()).apply()
    }

    /**
     * Clear TMDB API key
     */
    fun clearTmdbApiKey() {
        prefs.edit().remove(KEY_TMDB).apply()
    }

    /**
     * Check if TMDB API key is configured
     */
    fun hasTmdbApiKey(): Boolean {
        return prefs.getString(KEY_TMDB, null) != null
    }

    /**
     * Clear all API keys
     */
    fun clearAllKeys() {
        prefs.edit().clear().apply()
    }
}
