package com.willbeeching.flix.plex

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists the last-known-good connection URI per Plex server
 * (clientIdentifier -> uri) in its own SharedPreferences file, following the
 * same simple key/value pattern as [com.willbeeching.flix.settings.ApiKeyManager]
 * and [PlexAuthManager].
 *
 * This is the big win for a screensaver process, which restarts constantly:
 * on the next [PlexApiClient.discoverServers] call, the cached URI is probed
 * first with a short timeout. On success, discovery for that server is
 * skipped entirely. On failure, the entry is left in place to be overwritten
 * once full discovery finds a new winner (see [PlexApiClient.discoverServers]) -
 * a single stale/unreachable read never needs an explicit "invalidate" step,
 * it's simply superseded by the next successful probe.
 */
class ServerConnectionCache(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "plex_server_connection_cache"
    }

    /**
     * The last connection URI that successfully answered a probe for this
     * server, or null if none is cached yet.
     */
    fun getCachedUri(clientIdentifier: String): String? {
        return prefs.getString(clientIdentifier, null)
    }

    /**
     * Records [uri] as the last-known-good connection for [clientIdentifier].
     */
    fun setCachedUri(clientIdentifier: String, uri: String) {
        prefs.edit().putString(clientIdentifier, uri).apply()
    }

    /**
     * Drops the cached entry for a single server (e.g. if it's removed from
     * the account).
     */
    fun clearCachedUri(clientIdentifier: String) {
        prefs.edit().remove(clientIdentifier).apply()
    }

    /**
     * Drops all cached entries, e.g. on sign-out.
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
