package com.example.plexscreensaver.service

import android.service.dreams.DreamService
import android.view.View
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import com.example.plexscreensaver.R
import com.example.plexscreensaver.screensaver.ScreensaverController

/**
 * Dream Service that displays Plex artwork as a screensaver
 * Uses shared ScreensaverController for all logic
 */
class PlexScreensaverService : DreamService() {

    private lateinit var controller: ScreensaverController
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        // Configure dream
        isInteractive = false
        isFullscreen = true

        // Set the content view
        setContentView(R.layout.dream_screensaver)

        // Initialize views
        val imageView: ImageView = findViewById(R.id.screensaver_image)
        val imageViewAlternate: ImageView = findViewById(R.id.screensaver_image_alternate)
        val imageViewBlurred: ImageView? = findViewById(R.id.screensaver_image_blurred)
        val titleLogoView: ImageView = findViewById(R.id.title_logo)
        val gradientLeft: View? = findViewById(R.id.logo_gradient)
        val gradientRight: View? = findViewById(R.id.logo_gradient_right)

        // Initialize controller with shared logic
        controller = ScreensaverController(
            context = this,
            scope = serviceScope,
            imageView = imageView,
            imageViewAlternate = imageViewAlternate,
            imageViewBlurred = imageViewBlurred,
            titleLogoView = titleLogoView,
            gradientLeft = gradientLeft,
            gradientRight = gradientRight
        )

        // Start loading artwork
        controller.start()
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        controller.startRotation()
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()
        controller.stop()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        controller.stop()
        serviceScope.coroutineContext[Job]?.cancel()
    }
}
