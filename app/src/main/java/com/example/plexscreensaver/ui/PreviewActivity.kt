package com.example.plexscreensaver.ui

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.plexscreensaver.R
import com.example.plexscreensaver.screensaver.ScreensaverController

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
        val imageViewBlurred: ImageView? = findViewById(R.id.screensaver_image_blurred)
        val titleLogoView: ImageView = findViewById(R.id.title_logo)
        val gradientLeft: View? = findViewById(R.id.logo_gradient)
        val gradientRight: View? = findViewById(R.id.logo_gradient_right)

        // Initialize controller with shared logic
        controller = ScreensaverController(
            context = this,
            scope = lifecycleScope,
            imageView = imageView,
            imageViewAlternate = imageViewAlternate,
            imageViewBlurred = imageViewBlurred,
            titleLogoView = titleLogoView,
            gradientLeft = gradientLeft,
            gradientRight = gradientRight
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
