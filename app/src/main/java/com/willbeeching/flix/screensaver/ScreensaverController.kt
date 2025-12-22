package com.willbeeching.flix.screensaver

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.View
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.load
import com.willbeeching.flix.R
import com.willbeeching.flix.plex.FanartTvClient
import com.willbeeching.flix.plex.PlexApiClient
import com.willbeeching.flix.plex.PlexAuthManager
import com.willbeeching.flix.plex.TmdbClient
import com.willbeeching.flix.settings.ApiKeyManager
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
    private val titleLogoView: ImageView,
    private val gradientLeft: View? = null,
    private val gradientRight: View? = null,
    private val flixLogoView: View? = null
) {
    private val authManager = PlexAuthManager(context)
    private val apiKeyManager = ApiKeyManager(context)
    private val tmdbClient = TmdbClient(apiKeyManager.getTmdbApiKey())
    private val fanartClient = FanartTvClient(apiKeyManager.getFanartApiKey())
    private var rotationJob: Job? = null
    private val artworkItems = mutableListOf<PlexApiClient.ArtworkItem>()
    private var currentIndex = 0
    private var currentLogoUrl: String? = null
    private var currentBackdropUrl: String? = null
    private var useAlternateView = false
    private var isFirstImage = true

    // Track current gradient colors for smooth transitions (separate for each corner)
    private var currentLeftGradientColor: Int = 0xFF000000.toInt()
    private var currentRightGradientColor: Int = 0xFF000000.toInt()
    private var leftGradientAnimator: ValueAnimator? = null
    private var rightGradientAnimator: ValueAnimator? = null

    companion object {
        private const val TAG = "ScreensaverController"
        private const val ROTATION_INTERVAL_MS = 10000L // 10 seconds total per slide
        private const val CROSSFADE_DURATION_MS = 2000L // 2 seconds for backdrop transitions
        private const val LOGO_FADE_IN_DELAY_MS = 500L // Delay after backdrop before logo appears
        private const val LOGO_FADE_DURATION_MS = 1000L // Logo fade in/out duration
        private const val LOGO_DISPLAY_TIME_MS = 15000L // How long logo stays visible

        // PROMO MODE: Set to true to use curated promo sequence instead of Plex library
        private const val PROMO_MODE = false

        // Curated promo items for demo/screenshots
        private val PROMO_ITEMS = listOf(
            // Movies (TMDB IDs)
            PlexApiClient.ArtworkItem(
                title = "Interstellar",
                thumbUrl = null,
                artUrl = null,
                titleCardUrl = null,
                rating = null,
                year = "2014",
                type = "movie",
                ratingKey = null,
                guid = "tmdb://157336",
                preferredArtworkId = "86717"
            ),
            PlexApiClient.ArtworkItem(
                title = "Gravity",
                thumbUrl = null,
                artUrl = null,
                titleCardUrl = null,
                rating = null,
                year = "2013",
                type = "movie",
                ratingKey = null,
                guid = "tmdb://49047"
            ),
            PlexApiClient.ArtworkItem(
                title = "The Dark Knight",
                thumbUrl = null,
                artUrl = null,
                titleCardUrl = null,
                rating = null,
                year = "2012",
                type = "movie",
                ratingKey = null,
                guid = "tmdb://49026",
                preferredArtworkId = "19713"
            ),
            PlexApiClient.ArtworkItem(
                title = "Skyfall",
                thumbUrl = null,
                artUrl = null,
                titleCardUrl = null,
                rating = null,
                year = "2012",
                type = "movie",
                ratingKey = null,
                guid = "tmdb://37724",
                preferredArtworkId = "17052"
            ),
            PlexApiClient.ArtworkItem(
                title = "Pluribus",
                thumbUrl = null,
                artUrl = null,
                titleCardUrl = null,
                rating = null,
                year = "2025",
                type = "show",
                ratingKey = null,
                guid = "tvdb://436457",
                preferredArtworkId = "205212"
            ),
            // TV Shows (TVDB IDs)
            PlexApiClient.ArtworkItem(
                title = "The Last of Us",
                thumbUrl = null,
                artUrl = null,
                titleCardUrl = null,
                rating = null,
                year = "2023",
                type = "show",
                ratingKey = null,
                guid = "tvdb://392256"
            ),
            PlexApiClient.ArtworkItem(
                title = "Silo",
                thumbUrl = null,
                artUrl = null,
                titleCardUrl = null,
                rating = null,
                year = "2017",
                type = "show",
                ratingKey = null,
                guid = "tvdb://330942",
                preferredArtworkId = "81773"
            ),
            PlexApiClient.ArtworkItem(
                title = "House of the Dragon",
                thumbUrl = null,
                artUrl = null,
                titleCardUrl = null,
                rating = null,
                year = "2022",
                type = "show",
                ratingKey = null,
                guid = "tvdb://371572"
            )
        )
    }

    init {
        // Configure image views
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        imageView.adjustViewBounds = false
        imageViewAlternate.scaleType = ImageView.ScaleType.CENTER_CROP
        imageViewAlternate.adjustViewBounds = false
    }

    /**
     * Start the screensaver - load artwork and begin rotation
     */
    fun start() {
        if (PROMO_MODE) {
            Log.d(TAG, "🎬 PROMO MODE ENABLED - Using curated showcase items")
            // Show Flix logo and right gradient in promo mode
            flixLogoView?.visibility = View.VISIBLE
            gradientRight?.visibility = View.VISIBLE
            loadPromoItems()
        } else {
            // Hide Flix logo and right gradient when using actual Plex library
            flixLogoView?.visibility = View.GONE
            gradientRight?.visibility = View.GONE
            loadArtwork()
        }
    }

    /**
     * Load curated promo items for demo/screenshots
     */
    private fun loadPromoItems() {
        artworkItems.clear()
        artworkItems.addAll(PROMO_ITEMS)
        Log.d(TAG, "Loaded ${artworkItems.size} promo items: ${artworkItems.map { it.title }}")

        // Start rotation on main thread
        scope.launch(Dispatchers.Main) {
            startRotation()
        }
    }

    /**
     * Stop the screensaver rotation
     */
    fun stop() {
        rotationJob?.cancel()
        rotationJob = null
        leftGradientAnimator?.cancel()
        leftGradientAnimator = null
        rightGradientAnimator?.cancel()
        rightGradientAnimator = null
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

        // Track how many items we've shown (for promo mode fade to black)
        var itemsShown = 0

        rotationJob = scope.launch {
            // Show first image immediately
            showNextImage()
            itemsShown++

            while (isActive) {
                // Wait for logo display time
                delay(LOGO_DISPLAY_TIME_MS)

                // Fade out logo before backdrop transition
                if (titleLogoView.alpha > 0f) {
                    titleLogoView.animate()
                        .alpha(0f)
                        .setDuration(LOGO_FADE_DURATION_MS)
                        .start()
                    delay(LOGO_FADE_DURATION_MS)
                }

                // In promo mode, fade to black after showing all items
                if (PROMO_MODE && itemsShown >= artworkItems.size) {
                    Log.d(TAG, "🎬 Promo complete - fading to black")
                    fadeToBlack()
                    break
                }

                // Now transition to next image
                showNextImage()
                itemsShown++
            }
        }

        Log.d(TAG, "Started rotation with ${artworkItems.size} items")
    }

    /**
     * Fade all elements to black for clean loop point
     */
    private fun fadeToBlack() {
        val fadeDuration = CROSSFADE_DURATION_MS

        // Fade out both image views
        imageView.animate()
            .alpha(0f)
            .setDuration(fadeDuration)
            .start()

        imageViewAlternate.animate()
            .alpha(0f)
            .setDuration(fadeDuration)
            .start()

        // Fade out gradients
        gradientLeft?.animate()
            ?.alpha(0f)
            ?.setDuration(fadeDuration)
            ?.start()

        gradientRight?.animate()
            ?.alpha(0f)
            ?.setDuration(fadeDuration)
            ?.start()

        Log.d(TAG, "Fading to black complete")
    }


    /**
     * Show the next image in the rotation
     */
    private fun showNextImage() {
        if (artworkItems.isEmpty()) {
            return
        }

        // Capture index before any async operations
        val itemIndex = currentIndex
        val item = artworkItems[itemIndex]

        // Increment index for next call BEFORE any async work
        currentIndex = (currentIndex + 1) % artworkItems.size

        Log.d(TAG, "Showing image #$itemIndex: ${item.title}")

        // Fetch artwork from external sources
        if (item.guid != null) {
            scope.launch(Dispatchers.IO) {
                // Try Fanart.tv first for both backdrop and logo
                val (fanartBackdropUrl, fanartLogoUrl) = fanartClient.getImagesFromGuid(item.guid, item.type, item.preferredArtworkId)

                // Then try TMDB for backdrop and logo
                val (tmdbBackdropUrl, tmdbLogoUrl) = tmdbClient.getImagesFromGuid(item.guid, item.type)

                // Logo priority: Plex first, then Fanart.tv, then TMDB
                val logoUrl = item.titleCardUrl ?: fanartLogoUrl ?: tmdbLogoUrl

                // Skip if no logo found from any source
                if (logoUrl == null) {
                    Log.d(TAG, "✗ Skipping ${item.title} - no logo from Plex, Fanart.tv, or TMDB")
                    withContext(Dispatchers.Main) {
                        showNextImage() // Show next (index already incremented)
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    // Backdrop priority: Fanart.tv first (curated, text-free), then Plex, then TMDB
                    val imageUrl = fanartBackdropUrl ?: item.artUrl ?: tmdbBackdropUrl ?: item.thumbUrl

                    val backdropSource = when {
                        fanartBackdropUrl != null -> "Fanart.tv"
                        item.artUrl != null -> "Plex"
                        tmdbBackdropUrl != null -> "TMDB"
                        else -> "thumb"
                    }
                    val logoSource = when {
                        item.titleCardUrl != null -> "Plex"
                        fanartLogoUrl != null -> "Fanart.tv"
                        else -> "TMDB"
                    }
                    Log.d(TAG, "Using backdrop: $backdropSource, logo: $logoSource")

                    if (imageUrl != null) {
                        currentBackdropUrl = imageUrl
                        loadImageWithCrossfadeAndLogo(imageUrl, logoUrl)
                    }

                    // Update the item with resolved data so we don't fetch again
                    artworkItems[itemIndex] = item.copy(
                        titleCardUrl = logoUrl,
                        artUrl = fanartBackdropUrl ?: tmdbBackdropUrl ?: item.artUrl
                    )
                }
            }
        } else {
            // No GUID - use Plex data directly
            Log.d(TAG, "✓ No GUID for ${item.title}, using Plex data")
            val imageUrl = item.artUrl ?: item.thumbUrl
            val logoUrl = item.titleCardUrl

            // Skip if no logo
            if (logoUrl == null) {
                Log.d(TAG, "✗ Skipping ${item.title} - no logo available")
                showNextImage() // Index already incremented
                return
            }

            if (imageUrl != null) {
                currentBackdropUrl = imageUrl
                loadImageWithCrossfadeAndLogo(imageUrl, logoUrl)
            }
        }
    }

    /**
     * Load an image with smooth crossfade between two ImageViews
     * This prevents the black flash that occurs when using a single ImageView
     */
    private fun loadImageWithCrossfade(imageUrl: String) {
        loadImageWithCrossfadeAndLogo(imageUrl, null)
    }

    /**
     * Load backdrop and logo in sequence:
     * 1. Backdrop crossfades in
     * 2. Delay
     * 3. Logo fades in
     */
    private fun loadImageWithCrossfadeAndLogo(imageUrl: String, logoUrl: String?) {
        // Determine which view to load into (alternate between them)
        val targetView = if (useAlternateView) imageViewAlternate else imageView
        val currentView = if (useAlternateView) imageView else imageViewAlternate

        val targetViewName = if (useAlternateView) "alternate" else "primary"
        Log.d(TAG, "Loading into $targetViewName view: $imageUrl")

        // Toggle for next time
        useAlternateView = !useAlternateView

        // IMPORTANT: Set target view to invisible BEFORE loading to prevent flash
        targetView.alpha = 0f

        // Load the new image into the target view (starts hidden)
        targetView.load(imageUrl) {
            crossfade(false)  // We'll handle the fade manually
            error(R.drawable.ic_placeholder)
            size(coil.size.Size.ORIGINAL)
            scale(coil.size.Scale.FILL)
            listener(
                onSuccess = { _, _ ->
                    Log.d(TAG, "✓ Backdrop loaded successfully into $targetViewName view")

                    // Extract colors from image and update gradients
                    updateGradientsWithImageColor(targetView)

                    // For first image, show immediately without animation
                    if (isFirstImage) {
                        Log.d(TAG, "First image - showing immediately")
                        isFirstImage = false
                        targetView.alpha = 1f
                        targetView.visibility = View.VISIBLE
                        applyKenBurnsEffect(targetView)
                        // Load logo after delay
                        if (logoUrl != null) {
                            loadLogoWithDelay(logoUrl, LOGO_FADE_IN_DELAY_MS)
                        }
                    } else {
                        // Crossfade animation for backdrop
                        Log.d(TAG, "Crossfading backdrop")

                        // Apply Ken Burns effect before starting fade
                        applyKenBurnsEffect(targetView)

                        // Animate both views
                        targetView.animate()
                            .alpha(1f)
                            .setDuration(CROSSFADE_DURATION_MS)
                            .withEndAction {
                                Log.d(TAG, "Backdrop fade complete")
                                // Now load logo after delay
                                if (logoUrl != null) {
                                    loadLogoWithDelay(logoUrl, LOGO_FADE_IN_DELAY_MS)
                                }
                            }
                            .start()

                        currentView.animate()
                            .alpha(0f)
                            .setDuration(CROSSFADE_DURATION_MS)
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
     * Load and show logo after a delay
     */
    private fun loadLogoWithDelay(logoUrl: String, delayMs: Long) {
        currentLogoUrl = logoUrl
        titleLogoView.visibility = View.INVISIBLE
        titleLogoView.alpha = 0f

        titleLogoView.load(logoUrl) {
            crossfade(false)
            listener(
                onSuccess = { _, result ->
                    adjustLogoSize(result.drawable)
                    titleLogoView.visibility = View.VISIBLE
                    // Fade in after delay
                    titleLogoView.animate()
                        .alpha(1f)
                        .setDuration(LOGO_FADE_DURATION_MS)
                        .setStartDelay(delayMs)
                        .start()
                    Log.d(TAG, "Logo fading in after ${delayMs}ms delay")
                },
                onError = { _, _ ->
                    titleLogoView.visibility = View.GONE
                    currentLogoUrl = null
                }
            )
        }
    }

    /**
     * Apply subtle horizontal pan effect (like Netflix idle screens)
     * Gentle left-right motion with slight zoom to prevent edge gaps
     */
    private fun applyKenBurnsEffect(targetView: ImageView) {
        // Random horizontal direction: pan left or right
        val panDistance = 60f
        val startTranslateX = if (Math.random() < 0.5) -panDistance else panDistance
        val endTranslateX = -startTranslateX

        // Zoom in more for a more obvious pan effect
        val scale = 1.15f

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
        // Smaller sizes for better balance with Flix logo
        val targetWidthPercentage = when {
            aspectRatio > 10f -> 0.22f // Ultra-wide logos (e.g., 15:1)
            aspectRatio > 7f -> 0.20f  // Very wide logos (e.g., 10:1)
            aspectRatio > 5f -> 0.18f  // Wide logos (e.g., 6:1 clearLogos)
            aspectRatio > 3f -> 0.15f  // Medium-wide logos (e.g., 4:1)
            aspectRatio > 2f -> 0.12f  // Medium logos (e.g., 3:1)
            else -> 0.10f              // Square-ish logos (e.g., 1:1)
        }

        val targetWidthDp = screenWidthDp * targetWidthPercentage
        val targetHeightDp = targetWidthDp / aspectRatio

        // Clamp to reasonable bounds
        val maxWidthDp = screenWidthDp * 0.25f  // Max 25% of screen width
        val maxHeightDp = screenHeightDp * 0.08f // Max 8% of screen height
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

        // Use FrameLayout.LayoutParams to ensure bottom|start gravity
        // Bottom margin set to 60dp to align with Flix logo
        val marginStartPx = (60 * density).toInt()
        val marginBottomPx = (60 * density).toInt()
        val layoutParams = android.widget.FrameLayout.LayoutParams(widthPx, heightPx).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
            setMargins(marginStartPx, 0, 0, marginBottomPx)
        }
        titleLogoView.layoutParams = layoutParams

        Log.d(TAG, "Logo: ${intrinsicWidth}x${intrinsicHeight} (${String.format("%.2f", aspectRatio)}:1) → ${finalWidthDp.toInt()}x${finalHeightDp.toInt()}dp (screen: ${screenWidthDp.toInt()}x${screenHeightDp.toInt()}dp)")
    }

    /**
     * Extract colors from specific corners of the image and apply to gradients
     * Bottom-left corner color for left gradient, bottom-right for right gradient
     */
    private fun updateGradientsWithImageColor(targetView: ImageView) {
        val drawable = targetView.drawable ?: return

        scope.launch(Dispatchers.Default) {
            try {
                // Get bitmap from drawable
                val originalBitmap = if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    drawable.toBitmap()
                }

                // Convert hardware bitmap to software bitmap if needed
                val softwareBitmap = if (originalBitmap.config == android.graphics.Bitmap.Config.HARDWARE) {
                    originalBitmap.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
                } else {
                    originalBitmap
                }

                val width = softwareBitmap.width
                val height = softwareBitmap.height

                // Define corner regions (bottom 40% height, left/right 40% width)
                val cornerWidth = (width * 0.4f).toInt()
                val cornerHeight = (height * 0.4f).toInt()
                val bottomY = height - cornerHeight

                // Extract bottom-left corner
                val bottomLeftBitmap = android.graphics.Bitmap.createBitmap(
                    softwareBitmap,
                    0,
                    bottomY,
                    cornerWidth,
                    cornerHeight
                )

                // Extract bottom-right corner
                val bottomRightBitmap = android.graphics.Bitmap.createBitmap(
                    softwareBitmap,
                    width - cornerWidth,
                    bottomY,
                    cornerWidth,
                    cornerHeight
                )

                // Generate palettes for each corner
                val leftPalette = Palette.from(bottomLeftBitmap).generate()
                val rightPalette = Palette.from(bottomRightBitmap).generate()

                // Get dark colors from each corner
                val leftColor = leftPalette.darkMutedSwatch?.rgb
                    ?: leftPalette.darkVibrantSwatch?.rgb
                    ?: leftPalette.mutedSwatch?.rgb
                    ?: 0xFF000000.toInt()

                val rightColor = rightPalette.darkMutedSwatch?.rgb
                    ?: rightPalette.darkVibrantSwatch?.rgb
                    ?: rightPalette.mutedSwatch?.rgb
                    ?: 0xFF000000.toInt()

                Log.d(TAG, "Corner colors - Left: #${Integer.toHexString(leftColor)}, Right: #${Integer.toHexString(rightColor)}")

                withContext(Dispatchers.Main) {
                    updateGradientColors(leftColor, rightColor)
                }

                // Clean up corner bitmaps
                bottomLeftBitmap.recycle()
                bottomRightBitmap.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting palette colors", e)
            }
        }
    }

    /**
     * Update gradient views with extracted colors from each corner
     * Animates smoothly from current colors to new colors
     */
    private fun updateGradientColors(leftColor: Int, rightColor: Int) {
        // Cancel any existing animations
        leftGradientAnimator?.cancel()
        rightGradientAnimator?.cancel()

        // Animate left gradient
        leftGradientAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentLeftGradientColor, leftColor).apply {
            duration = CROSSFADE_DURATION_MS

            addUpdateListener { animator ->
                val animatedColor = animator.animatedValue as Int

                // Create gradient colors with the extracted color
                val startColor = (animatedColor and 0x00FFFFFF) or 0xF5000000.toInt() // 96% opacity
                val centerColor = (animatedColor and 0x00FFFFFF) or 0xB3000000.toInt() // 70% opacity
                val midColor = (animatedColor and 0x00FFFFFF) or 0x4D000000 // 30% opacity
                val endColor = 0x00000000 // Transparent

                gradientLeft?.background = GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(startColor, centerColor, midColor, endColor)
                    gradientRadius = 0.70f * context.resources.displayMetrics.widthPixels
                    setGradientCenter(0f, 1f) // Bottom-left
                }
            }

            start()
        }

        // Animate right gradient
        rightGradientAnimator = ValueAnimator.ofObject(ArgbEvaluator(), currentRightGradientColor, rightColor).apply {
            duration = CROSSFADE_DURATION_MS

            addUpdateListener { animator ->
                val animatedColor = animator.animatedValue as Int

                // Create gradient colors with the extracted color
                val startColor = (animatedColor and 0x00FFFFFF) or 0xF5000000.toInt() // 96% opacity
                val centerColor = (animatedColor and 0x00FFFFFF) or 0xB3000000.toInt() // 70% opacity
                val midColor = (animatedColor and 0x00FFFFFF) or 0x4D000000 // 30% opacity
                val endColor = 0x00000000 // Transparent

                gradientRight?.background = GradientDrawable().apply {
                    gradientType = GradientDrawable.RADIAL_GRADIENT
                    colors = intArrayOf(startColor, centerColor, midColor, endColor)
                    gradientRadius = 0.50f * context.resources.displayMetrics.widthPixels
                    setGradientCenter(1f, 1f) // Bottom-right
                }
            }

            start()
        }

        // Update current colors for next transition
        currentLeftGradientColor = leftColor
        currentRightGradientColor = rightColor
    }
}

