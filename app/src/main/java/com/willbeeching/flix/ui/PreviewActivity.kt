package com.willbeeching.flix.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.willbeeching.flix.R
import com.willbeeching.flix.screensaver.ScreensaverController

/**
 * Preview activity for testing the screensaver
 * Uses shared ScreensaverController for consistent behavior
 */
class PreviewActivity : AppCompatActivity() {

    private lateinit var controller: ScreensaverController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

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
            scope = lifecycleScope,
            imageView = imageView,
            imageViewAlternate = imageViewAlternate,
            titleLogoView = titleLogoView,
            gradientLeft = gradientLeft,
            gradientRight = gradientRight,
            flixLogoView = flixLogoView
        )

        // Start the screensaver
        controller.start()
    }

    override fun onResume() {
        super.onResume()
        controller.startRotation()
    }

    override fun onPause() {
        super.onPause()
        controller.stop()
    }
}
