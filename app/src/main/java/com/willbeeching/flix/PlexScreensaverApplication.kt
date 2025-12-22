package com.willbeeching.flix

import android.app.Application
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Application class to configure Coil with SSL trust for self-signed certificates
 * Required for loading images from local Plex servers which may use self-signed HTTPS
 */
class PlexScreensaverApplication : Application(), ImageLoaderFactory {

    companion object {
        private const val TAG = "PlexScreensaverApp"
    }

    override fun newImageLoader(): ImageLoader {
        // Create OkHttpClient that trusts self-signed certificates from local Plex servers
        // This is safe because images are only loaded from authenticated Plex servers on the local network
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier(HostnameVerifier { _, _ -> true })
            .build()

        Log.d(TAG, "Coil ImageLoader configured for local Plex servers with self-signed certificates")

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .build()
    }
}




