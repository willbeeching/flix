package com.example.plexscreensaver.screensaver

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.load
import com.example.plexscreensaver.R
import com.example.plexscreensaver.plex.PlexApiClient
import com.example.plexscreensaver.plex.PlexAuthManager
import com.example.plexscreensaver.plex.TmdbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared controller for screensaver logic used by both DreamService and PreviewActivity
 */
class ScreensaverController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val imageView: ImageView,
    private val imageViewAlternate: ImageView,
    private val imageViewBlurred: ImageView? = null,
    private val titleLogoView: ImageView,
    private val gradientLeft: View? = null,
    private val gradientRight: View? = null
) {
    private val authManager = PlexAuthManager(context)
    private val tmdbClient = TmdbClient()
    private var rotationJob: Job? = null
    private val artworkItems = mutableListOf<PlexApiClient.ArtworkItem>()
    private var currentIndex = 0
    private var currentLogoUrl: String? = null
    private var currentBackdropUrl: String? = null
    private var useAlternateView = false
    private var isFirstImage = true

    // Track current gradient colors for smooth transitions
    private var currentGradientColor: Int = 0xFF000000.toInt()
    private var gradientAnimator: ValueAnimator? = null

    companion object {
        private const val TAG = "ScreensaverController"
        private const val ROTATION_INTERVAL_MS = 10000L // 10 seconds total per slide
        private const val CROSSFADE_DURATION_MS = 2000 // 2 seconds for smoother transitions
        private const val LOGO_FADEOUT_DURATION_MS = 500L // Duration of logo fade out
        private const val LOGO_FADEOUT_BEFORE_TRANSITION_MS = 2000L // Start fading 2s before image crossfade
    }

    init {
        // Configure image views
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.adjustViewBounds = false
        imageViewAlternate.scaleType = ImageView.ScaleType.CENTER_CROP
        imageViewAlternate.adjustViewBounds = false

        // Configure blurred background layer
        imageViewBlurred?.let {
            it.scaleType = ImageView.ScaleType.CENTER_CROP
            it.adjustViewBounds = false

            // Apply blur effect on Android 12+ (GPU accelerated)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                it.setRenderEffect(
                    RenderEffect.createBlurEffect(
                        25f, 25f, // Blur radius in pixels
                        Shader.TileMode.CLAMP
                    )
                )
                Log.d(TAG, "Blur effect enabled (Android 12+)")
            } else {
                Log.d(TAG, "Blur effect not supported (Android < 12), using gradient only")
            }
        }
    }

    /**
     * Start the screensaver - load artwork and begin rotation
     */
    fun start() {
        loadArtwork()
    }

    /**
     * Stop the screensaver rotation
     */
    fun stop() {
        rotationJob?.cancel()
        rotationJob = null
        gradientAnimator?.cancel()
        gradientAnimator = null
    }

    /**
     * Load artwork from Plex
     */
    private fun loadArtwork() {
        scope.launch(Dispatchers.IO) {
            try {
                // Check authentication
                if (!authManager.isAuthenticated()) {
                    Log.e(TAG, "Not authenticated with Plex")
                    return@launch
                }

                val authToken = authManager.getAuthToken()!!
                val apiClient = PlexApiClient(authToken)

                // Discover servers
                val serversResult = apiClient.discoverServers()
                if (serversResult.isFailure) {
                    Log.e(TAG, "Failed to discover servers", serversResult.exceptionOrNull())
                    return@launch
                }

                val servers = serversResult.getOrNull() ?: emptyList()
                Log.d(TAG, "Discovered ${servers.size} servers")

                if (servers.isEmpty()) {
                    Log.e(TAG, "No Plex servers found")
                    return@launch
                }

                // Use selected server or first available
                val selectedServerId = authManager.getSelectedServerId()
                val server = servers.find { it.clientIdentifier == selectedServerId } ?: servers.first()
                Log.d(TAG, "Using server: ${server.name}")

                // Save selected server if not already saved
                if (!authManager.hasSelectedServer()) {
                    authManager.saveSelectedServer(server.name, server.clientIdentifier)
                }

                // Get library sections
                val sectionsResult = apiClient.getLibrarySections(server)

                if (sectionsResult.isFailure) {
                    Log.e(TAG, "Failed to get library sections", sectionsResult.exceptionOrNull())
                    return@launch
                }

                val sections = sectionsResult.getOrNull() ?: emptyList()
                Log.d(TAG, "Found ${sections.size} library sections")
                sections.forEach { section ->
                    Log.d(TAG, "Section: ${section.title}, type: ${section.type}, id: ${section.id}")
                }

                // Filter by selected libraries if any
                val selectedLibraries = authManager.getSelectedLibraries()
                val targetSections = if (selectedLibraries.isNotEmpty()) {
                    // Selected libraries are stored as IDs (strings), so filter by ID
                    sections.filter { it.id in selectedLibraries }
                } else {
                    // If no libraries selected, use all movie/show sections
                    sections.filter { it.type == "movie" || it.type == "show" }
                }

                Log.d(TAG, "Selected libraries: $selectedLibraries")
                Log.d(TAG, "Target sections after filtering: ${targetSections.map { "${it.title} (${it.id})" }}")

                // Fetch artwork from all sections (already filtered to movie/show)
                val allItems = mutableListOf<PlexApiClient.ArtworkItem>()
                for (section in targetSections) {
                    val artworkResult = apiClient.getArtworkFromSection(
                        server,
                        section.id,
                        batchSize = 300
                    )

                    artworkResult.onSuccess { items ->
                        Log.d(TAG, "Found ${items.size} artwork items from ${section.title}")
                        allItems.addAll(items)
                    }.onFailure { error ->
                        Log.e(TAG, "Failed to get artwork from section ${section.title}", error)
                    }
                }

                // Deduplicate by GUID
                val seenGuids = mutableSetOf<String>()
                val uniqueItems = allItems.filter { item ->
                    val key = item.guid ?: item.title
                    if (seenGuids.contains(key)) {
                        false
                    } else {
                        seenGuids.add(key)
                        true
                    }
                }

                artworkItems.clear()
                artworkItems.addAll(uniqueItems)

                // Shuffle for variety
                artworkItems.shuffle(kotlin.random.Random.Default)

                Log.d(TAG, "Loaded ${artworkItems.size} unique items after deduplication")
                Log.d(TAG, "First 5 items AFTER shuffle: ${artworkItems.take(5).map { it.title }}")
                Log.d(TAG, "Last 5 items AFTER shuffle: ${artworkItems.takeLast(5).map { it.title }}")

                // Start rotation automatically after loading completes
                withContext(Dispatchers.Main) {
                    startRotation()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading artwork", e)
            }
        }
    }

    /**
     * Start rotating through images
     */
    fun startRotation() {
        if (artworkItems.isEmpty()) {
            Log.w(TAG, "Cannot start rotation - no artwork items loaded")
            return
        }

        // Don't start if already running
        if (rotationJob?.isActive == true) {
            Log.d(TAG, "Rotation already running")
            return
        }

        // Cancel any existing rotation job
        rotationJob?.cancel()

        rotationJob = scope.launch {
            // Show first image immediately
            showNextImage()

            while (isActive) {
                // Wait for the full rotation interval
                delay(ROTATION_INTERVAL_MS)
                // Show next image (logo fade-out will happen inside)
                showNextImage()
            }
        }

        Log.d(TAG, "Started rotation with ${artworkItems.size} items")
    }


    /**
     * Show the next image in the rotation
     */
    private fun showNextImage() {
        if (artworkItems.isEmpty()) {
            return
        }

        val item = artworkItems[currentIndex]

        Log.d(TAG, "Showing image #$currentIndex: ${item.title}")

        // If item doesn't have a clearLogo from Plex, try to fetch from TMDB
        if (item.titleCardUrl == null && item.guid != null) {
            scope.launch(Dispatchers.IO) {
                val (tmdbBackdropUrl, tmdbLogoUrl) = tmdbClient.getImagesFromGuid(item.guid, item.type)

                // Skip if no logo found from either source
                if (tmdbLogoUrl == null) {
                    Log.d(TAG, "✗ Skipping ${item.title} - no logo from Plex or TMDB, moving to next")
                    withContext(Dispatchers.Main) {
                        currentIndex = (currentIndex + 1) % artworkItems.size
                        showNextImage() // Recursively show next
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    // PRIORITY: Use Plex artwork if available, fallback to TMDB
                    // Plex backdrops are curated to be clean (no promotional text)
                    val imageUrl = item.artUrl ?: tmdbBackdropUrl ?: item.thumbUrl

                    // Load if we have a URL and it's different from current (or this is the first image)
                    if (imageUrl != null) {
                        if (imageUrl != currentBackdropUrl || currentBackdropUrl == null) {
                            Log.d(TAG, "Loading backdrop for ${item.title}")

                            // Fade out logo first if not the first image
                            if (!isFirstImage && titleLogoView.alpha > 0f) {
                                Log.d(TAG, "Fading out logo before transition")
                                titleLogoView.animate()
                                    .alpha(0f)
                                    .setDuration(LOGO_FADEOUT_DURATION_MS)
                                    .start()
                                // Wait for fade out, then wait 2 more seconds
                                delay(LOGO_FADEOUT_DURATION_MS + LOGO_FADEOUT_BEFORE_TRANSITION_MS)
                            }

                            currentBackdropUrl = imageUrl
                            loadImageWithCrossfade(imageUrl)
                        }
                    }

                    // Show logo (TMDB fallback since Plex didn't have one)
                    when {
                        tmdbLogoUrl != null && tmdbLogoUrl != currentLogoUrl -> {
                            Log.d(TAG, "✓ Using TMDB logo for ${item.title} (Plex backdrop: ${item.artUrl != null})")

                            // Fade out old logo first if visible
                            if (titleLogoView.alpha > 0f) {
                                titleLogoView.animate()
                                    .alpha(0f)
                                    .setDuration(500)
                                    .withEndAction {
                                        // After fade out, load new logo
                                        currentLogoUrl = tmdbLogoUrl
                                        titleLogoView.visibility = View.INVISIBLE
                                        titleLogoView.load(tmdbLogoUrl) {
                                            crossfade(false)
                                            listener(
                                                onSuccess = { _, result ->
                                                    adjustLogoSize(result.drawable)
                                                    titleLogoView.alpha = 0f
                                                    titleLogoView.visibility = View.VISIBLE
                                                    titleLogoView.animate()
                                                        .alpha(1f)
                                                        .setDuration(CROSSFADE_DURATION_MS.toLong())
                                                        .setStartDelay(1000)
                                                        .start()
                                                },
                                                onError = { _, _ ->
                                                    titleLogoView.visibility = View.GONE
                                                    currentLogoUrl = null
                                                }
                                            )
                                        }
                                    }
                                    .start()
                            } else {
                                // No logo currently showing, load directly
                                currentLogoUrl = tmdbLogoUrl
                                titleLogoView.visibility = View.INVISIBLE
                                titleLogoView.load(tmdbLogoUrl) {
                                    crossfade(false)
                                    listener(
                                        onSuccess = { _, result ->
                                            adjustLogoSize(result.drawable)
                                            titleLogoView.alpha = 0f
                                            titleLogoView.visibility = View.VISIBLE
                                            titleLogoView.animate()
                                                .alpha(1f)
                                                .setDuration(CROSSFADE_DURATION_MS.toLong())
                                                .setStartDelay(1000)
                                                .start()
                                        },
                                        onError = { _, _ ->
                                            titleLogoView.visibility = View.GONE
                                            currentLogoUrl = null
                                        }
                                    )
                                }
                            }
                        }
                        tmdbLogoUrl == null -> {
                            // Fade out logo if visible
                            if (titleLogoView.alpha > 0f) {
                                titleLogoView.animate()
                                    .alpha(0f)
                                    .setDuration(500)
                                    .withEndAction {
                                        titleLogoView.visibility = View.GONE
                                    }
                                    .start()
                            } else {
                                titleLogoView.visibility = View.GONE
                            }
                            currentLogoUrl = null
                        }
                        else -> {
                            // Logo hasn't changed, keep current logo visible
                        }
                    }

                    // Update the item with TMDB data so we don't fetch again
                    if (tmdbLogoUrl != null || tmdbBackdropUrl != null) {
                        artworkItems[currentIndex] = item.copy(
                            titleCardUrl = tmdbLogoUrl,
                            // Keep Plex artUrl if we have it, otherwise use TMDB backdrop
                            artUrl = item.artUrl ?: tmdbBackdropUrl
                        )
                    }
                }
            }
        } else {
            // Already have clearLogo from Plex - use Plex artwork directly!
            Log.d(TAG, "✓ Using Plex clearLogo for ${item.title}")
            val imageUrl = item.artUrl ?: item.thumbUrl

            // Load if we have a URL and it's different from current (or this is the first image)
            if (imageUrl != null) {
                if (imageUrl != currentBackdropUrl || currentBackdropUrl == null) {
                    // Fade out logo first if not the first image
                    scope.launch {
                        if (!isFirstImage && titleLogoView.alpha > 0f) {
                            Log.d(TAG, "Fading out logo before transition")
                            titleLogoView.animate()
                                .alpha(0f)
                                .setDuration(LOGO_FADEOUT_DURATION_MS)
                                .start()
                            // Wait for fade out, then wait 2 more seconds
                            delay(LOGO_FADEOUT_DURATION_MS + LOGO_FADEOUT_BEFORE_TRANSITION_MS)
                        }

                        currentBackdropUrl = imageUrl
                        loadImageWithCrossfade(imageUrl)
                    }
                }
            }

            when {
                item.titleCardUrl != null && item.titleCardUrl != currentLogoUrl -> {
                    currentLogoUrl = item.titleCardUrl
                    // Keep hidden until sized properly
                    titleLogoView.visibility = View.INVISIBLE
                    titleLogoView.load(item.titleCardUrl) {
                        crossfade(false)  // We'll handle the fade ourselves
                        listener(
                            onSuccess = { _, result ->
                                // Adjust logo size based on aspect ratio for consistent visual weight
                                adjustLogoSize(result.drawable)
                                // Now show it with proper size - fade in after 1 second delay
                                titleLogoView.alpha = 0f
                                titleLogoView.visibility = View.VISIBLE
                                titleLogoView.animate()
                                    .alpha(1f)
                                    .setDuration(CROSSFADE_DURATION_MS.toLong())
                                    .setStartDelay(1000) // 1 second delay for sequential reveal
                                    .start()
                            },
                            onError = { _, _ ->
                                titleLogoView.visibility = View.GONE
                                currentLogoUrl = null
                            }
                        )
                    }
                }
                item.titleCardUrl == null -> {
                    titleLogoView.visibility = View.GONE
                    currentLogoUrl = null
                }
                else -> {
                    // Logo hasn't changed, keep current logo visible
                }
            }
        }

        currentIndex = (currentIndex + 1) % artworkItems.size
    }

    /**
     * Load an image with smooth crossfade between two ImageViews
     * This prevents the black flash that occurs when using a single ImageView
     */
    private fun loadImageWithCrossfade(imageUrl: String) {
        // Determine which view to load into (alternate between them)
        val targetView = if (useAlternateView) imageViewAlternate else imageView
        val currentView = if (useAlternateView) imageView else imageViewAlternate

        val targetViewName = if (useAlternateView) "alternate" else "primary"
        Log.d(TAG, "Loading into $targetViewName view: $imageUrl")

        // Toggle for next time
        useAlternateView = !useAlternateView

        // IMPORTANT: Set target view to invisible BEFORE loading to prevent flash
        targetView.alpha = 0f

        // Also load into blurred background layer (if available)
        imageViewBlurred?.load(imageUrl) {
            crossfade(false)
            size(coil.size.Size.ORIGINAL)
            scale(coil.size.Scale.FILL)
        }

        // Load the new image into the target view (starts hidden)
        targetView.load(imageUrl) {
            crossfade(false)  // We'll handle the fade manually
            error(R.drawable.ic_placeholder)
            size(coil.size.Size.ORIGINAL)
            scale(coil.size.Scale.FILL)
            listener(
                onSuccess = { _, _ ->
                    Log.d(TAG, "✓ Backdrop loaded successfully into $targetViewName view")

                    // Extract color from image and update gradients
                    updateGradientsWithImageColor(targetView)

                    // For first image, show immediately without animation
                    if (isFirstImage) {
                        Log.d(TAG, "First image - showing immediately")
                        isFirstImage = false
                        targetView.alpha = 1f
                        targetView.visibility = View.VISIBLE
                        applyKenBurnsEffect(targetView)
                    } else {
                        // For subsequent images, use crossfade animation
                        Log.d(TAG, "Crossfading - target: ${targetView.alpha} -> 1.0, current: ${currentView.alpha} -> 0.0")

                        // Apply Ken Burns effect before starting fade
                        applyKenBurnsEffect(targetView)

                        // Animate both views
                        targetView.animate()
                            .alpha(1f)
                            .setDuration(CROSSFADE_DURATION_MS.toLong())
                            .withEndAction {
                                Log.d(TAG, "Fade complete - target now at: ${targetView.alpha}")
                            }
                            .start()

                        currentView.animate()
                            .alpha(0f)
                            .setDuration(CROSSFADE_DURATION_MS.toLong())
                            .start()
                    }
                },
                onError = { _, result ->
                    Log.e(TAG, "✗ Error loading backdrop into $targetViewName view: $imageUrl", result.throwable)
                }
            )
        }
    }

    /**
     * Extract dominant dark color from image and apply to gradients
     */
    private fun updateGradientsWithImageColor(targetView: ImageView) {
        val drawable = targetView.drawable ?: return

        scope.launch(Dispatchers.Default) {
            try {
                val bitmap = drawable.toBitmap(width = 200, height = 200) // Smaller for performance
                val palette = Palette.from(bitmap).generate()

                // Get dark muted color, fallback to dark vibrant, then black
                val dominantColor = palette.darkMutedSwatch?.rgb
                    ?: palette.darkVibrantSwatch?.rgb
                    ?: palette.mutedSwatch?.rgb
                    ?: 0xFF000000.toInt()

                withContext(Dispatchers.Main) {
                    updateGradientColor(dominantColor)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting palette color", e)
            }
        }
    }

    /**
     * Update gradient views with extracted color
     * Animates smoothly from current color to new color
     */
    private fun updateGradientColor(baseColor: Int) {
        // Cancel any existing animation
        gradientAnimator?.cancel()

        // Animate from current color to new color
        gradientAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentGradientColor, baseColor).apply {
            duration = CROSSFADE_DURATION_MS.toLong() // Match image crossfade duration

            addUpdateListener { animator ->
                val animatedColor = animator.animatedValue as Int

                val startColor = (animatedColor and 0x00FFFFFF) or 0xCC000000.toInt() // 80% opacity
                val centerColor = (animatedColor and 0x00FFFFFF) or 0x1A000000 // 10% opacity
                val endColor = 0x00000000 // Transparent

                // Update left gradient (linear, bottom-left to top-right)
                gradientLeft?.background = GradientDrawable(
                    GradientDrawable.Orientation.BL_TR,
                    intArrayOf(startColor, centerColor, endColor)
                )

                // Update right gradient (radial from bottom-right corner)
                gradientRight?.background = GradientDrawable(
                    GradientDrawable.Orientation.BR_TL, // Placeholder for radial
                    intArrayOf(startColor, centerColor, endColor)
                ).apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    gradientRadius = 800f * context.resources.displayMetrics.density
                    // Note: Can't set center position via GradientDrawable, handled by XML
                }
            }

            start()
        }

        // Update current color for next transition
        currentGradientColor = baseColor
    }

    /**
     * Apply subtle horizontal pan effect (like Netflix idle screens)
     * Gentle left-right motion with slight zoom to prevent edge gaps
     */
    private fun applyKenBurnsEffect(targetView: ImageView) {
        // Random horizontal direction: pan left or right
        val panDistance = 40f
        val startTranslateX = if (Math.random() < 0.5) -panDistance else panDistance
        val endTranslateX = -startTranslateX

        // Slight zoom to ensure no empty space shows when panning
        val scale = 1.08f

        // Cancel any existing animation to prevent glitches
        targetView.animate().cancel()
        targetView.clearAnimation()

        // Reset position and apply zoom
        targetView.scaleX = scale
        targetView.scaleY = scale
        targetView.translationY = 0f
        targetView.translationX = startTranslateX

        // Animate horizontal pan - much longer duration to continue throughout entire display
        // Make it 2x the rotation interval to ensure smooth continuous motion
        val animationDuration = ROTATION_INTERVAL_MS * 2
        targetView.animate()
            .translationX(endTranslateX)
            .setDuration(animationDuration.toLong())
            .setInterpolator(android.view.animation.LinearInterpolator())
            .withEndAction(null)  // Clear any end actions
            .start()
    }

    /**
     * Adjust logo size for consistent visual weight across all aspect ratios
     * Uses continuous scaling based on screen width percentage (like Plex mobile)
     */
    private fun adjustLogoSize(drawable: android.graphics.drawable.Drawable) {
        val intrinsicWidth = drawable.intrinsicWidth
        val intrinsicHeight = drawable.intrinsicHeight

        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            return
        }

        val aspectRatio = intrinsicWidth.toFloat() / intrinsicHeight.toFloat()

        // Get screen dimensions
        val screenWidthDp = context.resources.configuration.screenWidthDp.toFloat()
        val screenHeightDp = context.resources.configuration.screenHeightDp.toFloat()

        // Target a percentage of screen width based on aspect ratio
        // This creates smooth, continuous sizing instead of discrete jumps
        val targetWidthPercentage = when {
            aspectRatio > 8f -> 0.18f  // Very wide logos (e.g., 12:1) can be bigger
            aspectRatio > 4f -> 0.16f  // Wide logos (e.g., 5:1 clearLogos)
            aspectRatio > 2f -> 0.14f  // Medium logos (e.g., 3:1)
            else -> 0.12f              // Square-ish logos (e.g., 1:1)
        }

        val targetWidthDp = screenWidthDp * targetWidthPercentage
        val targetHeightDp = targetWidthDp / aspectRatio

        // Clamp to reasonable bounds
        val maxWidthDp = screenWidthDp * 0.25f  // Never more than 25% of screen
        val maxHeightDp = screenHeightDp * 0.15f // Never more than 15% of screen height
        val minHeightDp = 40f // Ensure readability

        val finalHeightDp = targetHeightDp.coerceIn(minHeightDp, maxHeightDp)

        // If height was clamped, recalculate width to maintain aspect ratio
        val finalWidthDp = if (finalHeightDp != targetHeightDp) {
            (finalHeightDp * aspectRatio).coerceAtMost(maxWidthDp)
        } else {
            targetWidthDp.coerceAtMost(maxWidthDp)
        }

        val density = context.resources.displayMetrics.density
        val widthPx = (finalWidthDp * density).toInt()
        val heightPx = (finalHeightDp * density).toInt()

        val layoutParams = titleLogoView.layoutParams
        layoutParams.width = widthPx
        layoutParams.height = heightPx
        titleLogoView.layoutParams = layoutParams

        Log.d(TAG, "Logo: ${intrinsicWidth}x${intrinsicHeight} (${String.format("%.2f", aspectRatio)}:1) → ${finalWidthDp.toInt()}x${finalHeightDp.toInt()}dp (screen: ${screenWidthDp.toInt()}x${screenHeightDp.toInt()}dp)")
    }
}

