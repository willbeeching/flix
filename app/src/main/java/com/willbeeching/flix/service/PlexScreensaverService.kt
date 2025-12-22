package com.willbeeching.flix.service

import android.service.dreams.DreamService
import android.view.View
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import com.willbeeching.flix.R
import com.willbeeching.flix.screensaver.ScreensaverController

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
        val titleLogoView: ImageView = findViewById(R.id.title_logo)
        val gradientLeft: View? = findViewById(R.id.logo_gradient)
        val gradientRight: View? = findViewById(R.id.logo_gradient_right)
        val flixLogoView: View? = findViewById(R.id.plexflix_logo)

        // Initialize controller with shared logic
        controller = ScreensaverController(
            context = this,
            scope = serviceScope,
            imageView = imageView,
            imageViewAlternate = imageViewAlternate,
            titleLogoView = titleLogoView,
            gradientLeft = gradientLeft,
            gradientRight = gradientRight,
            flixLogoView = flixLogoView
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
