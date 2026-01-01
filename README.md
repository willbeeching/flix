# Flix - A Plex powered, Netflix inspired screensaver for Android TV

[![Android CI](https://github.com/willbeeching/flix/workflows/Android%20CI/badge.svg)](https://github.com/willbeeching/flix/actions)
[![Platform](https://img.shields.io/badge/Platform-Android%205.0%2B-green.svg)](https://android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org)

A modern Android screensaver app that displays stunning artwork from your Plex Media Server library.

**[Video Demo & Write-up](https://willbeeching.com/a-plex-powered-netflix-inspired-screensaver/)**

## Features

-   **QR Code Authentication** - Scan and connect to Plex instantly with your phone
-   **Enhanced Artwork** - High-quality 4K logos and backdrops from Plex, Fanart.tv, and TMDB
-   **Web-Based API Setup** - Configure artwork APIs via QR code from your mobile device
-   **Smooth Transitions** - Elegant crossfade effects with Ken Burns pan animation
-   **Dynamic Gradients** - Automatically extracts colors from artwork for ambient lighting
-   **Smart Logo Sizing** - Intelligently sizes logos based on aspect ratio
-   **Auto-Discovery** - Automatically finds and connects to your Plex servers
-   **Library Selection** - Choose specific libraries to display

## Requirements

-   Android 5.0 (API 21) or higher
-   Android TV or any Android device
-   A Plex account
-   A Plex Media Server with movie or TV show libraries
-   Optional: Fanart.tv and TMDB API keys for enhanced artwork

## Quick Start

### 1. Install the App

**Option A: Build from Source**

```bash
git clone https://github.com/willbeeching/flix.git
cd flix
./gradlew assembleDebug
```

**Option B: Install APK**

-   Download the latest APK from releases
-   Install on your Android TV device

### 2. Connect to Plex

1. Launch Flix on your Android TV
2. Scan the QR code with your phone or visit the displayed URL
3. Authorize the device in your Plex account
4. Select your Plex server
5. Choose which libraries to display

### 3. Configure API Keys (Optional)

For the best artwork quality, add API keys:

1. From the main screen, select **API Settings**
2. Scan the QR code with your phone
3. Enter your API keys:
    - **Fanart.tv**: Get free key at [fanart.tv](https://fanart.tv/get-an-api-key/)
    - **TMDB**: Get free key at [themoviedb.org/settings/api](https://www.themoviedb.org/settings/api)
4. Keys are validated automatically before saving

### 4. Set as Screensaver

**Standard Method (Most Android TV Devices):**

1. Tap **Set as Screensaver** from the main menu
2. Select "Flix" from the screensaver list
3. Configure your timeout preferences
4. Enjoy beautiful artwork from your library!

**Alternative Method (Google TV Streamer 4K, Chromecast with Google TV, etc):**

Some devices don't show third-party screensavers in settings. Use ADB instead:

```bash
# Connect to your device
adb connect YOUR_DEVICE_IP

# Set Flix as screensaver
adb shell settings put secure screensaver_components com.willbeeching.flix/.service.PlexScreensaverService

# Enable screensaver
adb shell settings put secure screensaver_enabled 1

# Set it to activate on sleep
adb shell settings put secure screensaver_activate_on_sleep 1

# Verify it's set (should show: com.willbeeching.flix/.service.PlexScreensaverService)
adb shell settings get secure screensaver_components
```

To test immediately:
```bash
adb shell am start -n com.android.dreams/.Somnambulator
```

### Main Screens

-   **Home** - Quick access to preview, settings, and screensaver activation
-   **Server Selection** - Choose which Plex server to use
-   **Library Selection** - Pick specific libraries (movies, TV shows) to display
-   **API Settings** - QR-based web configuration for artwork APIs

## How It Works

### Authentication Flow

1. App requests a PIN from Plex's OAuth API
2. QR code is generated with authentication URL and PIN pre-filled
3. User scans with phone and authorizes instantly
4. Auth token is securely stored locally
5. App discovers available Plex servers automatically

### Artwork Loading

1. Connects to selected Plex server and chosen libraries
2. Fetches high-quality metadata for movies and TV shows
3. Prioritizes artwork sources in this order:
    - Plex clearLogos (4K transparent PNGs)
    - Fanart.tv HD logos and backgrounds (if API key configured)
    - TMDB backdrops and logos (if API key configured)
    - Plex built-in artwork as fallback
4. Extracts dominant colors for dynamic gradient overlays
5. Displays artwork in rotation with smooth effects

### API Key Configuration

The web-based API setup provides a seamless experience:

1. Local HTTP server starts on your Android TV device
2. QR code is displayed with your device's local IP address
3. Scan with your phone to access the web interface
4. Enter API keys in a clean, mobile-optimized form
5. Keys are validated in real-time before saving
6. Settings sync instantly to your TV

## Technical Details

### Tech Stack

-   **Language**: Kotlin
-   **UI Framework**: Jetpack Compose
-   **Typography**: Google Sans
-   **Networking**: OkHttp
-   **Image Loading**: Coil
-   **JSON Parsing**: Moshi
-   **XML Parsing**: Android XmlPullParser
-   **Async**: Kotlin Coroutines
-   **Color Extraction**: AndroidX Palette
-   **QR Codes**: ZXing
-   **Local Server**: NanoHTTPD
-   **Visual Effects**: RenderEffect (Android 12+)

### Architecture

```
app/
├── plex/
│   ├── PlexAuthManager.kt         # Plex Link OAuth with QR codes
│   ├── PlexApiClient.kt           # Plex API interactions
│   ├── FanartTvClient.kt          # Fanart.tv API client
│   └── TmdbClient.kt              # TMDB API client
├── screensaver/
│   └── ScreensaverController.kt   # Core screensaver logic
├── service/
│   └── PlexScreensaverService.kt  # DreamService implementation
├── settings/
│   ├── ApiKeyManager.kt           # Secure API key storage
│   └── SettingsServer.kt          # Local HTTP server for web setup
└── ui/
    ├── MainActivity.kt            # Main hub screen
    ├── ApiSettingsActivity.kt     # QR-based API setup
    ├── ServerSelectionActivity.kt # Server selection
    ├── LibrarySelectionActivity.kt # Library selection
    ├── PreviewActivity.kt         # Screensaver preview
    └── theme/
        ├── Theme.kt               # Material 3 theme
        └── Type.kt                # Google Sans typography
```

### Key Components

-   **PlexAuthManager**: Manages Plex authentication

    -   Generates authentication QR codes with pre-filled PINs
    -   Polls for authorization status
    -   Securely stores auth tokens

-   **PlexApiClient**: Plex Media Server API

    -   Discovers servers via Plex.tv
    -   Fetches library sections with metadata
    -   Extracts 4K clearLogos and artwork URLs

-   **FanartTvClient**: Fanart.tv API integration

    -   Retrieves HD logos (clearlogo, hdtvlogo)
    -   Fetches high-quality backgrounds
    -   Caches responses to minimize API calls

-   **TmdbClient**: TMDB API integration

    -   Gets 4K backdrops and logos
    -   Filters for clean, high-quality images
    -   Provides fallback when Plex/Fanart.tv unavailable

-   **SettingsServer**: Local web server for API setup

    -   Serves mobile-optimized configuration UI
    -   Validates API keys before saving
    -   Provides real-time status updates
    -   Handles emulator detection with ADB forwarding instructions

-   **ScreensaverController**: Display management
    -   Intelligent artwork prioritization
    -   10-second rotation with 2-second crossfades
    -   Dynamic color extraction for gradients
    -   Ken Burns effect for subtle motion
    -   Aspect ratio-aware logo sizing

## Customization

### Code Customization

Edit constants in `ScreensaverController.kt`:

```kotlin
private const val ROTATION_INTERVAL_MS = 10000L    // Time per slide
private const val CROSSFADE_DURATION_MS = 2000L    // Fade duration
private const val LOGO_FADE_DURATION_MS = 1000L    // Logo fade
private const val PROMO_MODE = false                // Demo mode toggle
```

### Logo Sizing

Adjust percentages in `adjustLogoSize()` for different logo sizes:

```kotlin
val targetWidthPercentage = when {
    aspectRatio > 10f -> 0.22f // Ultra-wide logos
    aspectRatio > 7f -> 0.20f  // Very wide logos
    aspectRatio > 5f -> 0.18f  // Wide logos (clearLogos)
    aspectRatio > 3f -> 0.15f  // Medium-wide logos
    aspectRatio > 2f -> 0.12f  // Medium logos
    else -> 0.10f              // Square-ish logos
}
```

### UI Customization

All screens use consistent styling defined in:

-   `Theme.kt` - Colors and theming
-   `Type.kt` - Google Sans typography
-   Dark background: `#0A0A0A`
-   Accent colors defined throughout UI code

## Privacy & Security

-   Auth tokens stored in encrypted SharedPreferences
-   API keys validated before storage
-   No data sent to third parties
-   Direct communication between device and Plex/API services
-   Read-only access to Plex libraries
-   Local web server only accessible on your network
-   Server auto-stops when leaving API settings
-   SSL certificate validation for all external APIs (TMDB, Fanart.tv, Plex.tv)
-   SSL bypass only for local Plex servers with self-signed certificates

## Troubleshooting

### Screensaver not appearing in settings

Some Android TV devices (Google TV Streamer 4K, Chromecast with Google TV) only show the Ambient/Google Photos screensaver by default. Use the ADB method in the installation section above to set Flix as your screensaver.

**Common error:** `Error: Activity class {...PlexScreensaverService} does not exist`

This happens when trying to launch the screensaver as an activity. Use the correct command:
```bash
adb shell settings put secure screensaver_components com.willbeeching.flix/.service.PlexScreensaverService
```

### No artwork showing

1. Verify you're signed in (check main screen status)
2. Ensure Plex server is accessible from your device
3. Confirm you've selected at least one library
4. Check that selected libraries contain movies/TV shows
5. Try the Preview feature to test without waiting for screensaver

### QR code won't scan

1. Ensure phone and TV are on same network
2. Try entering the URL manually
3. If on emulator, use ADB forwarding (instructions shown on screen)
4. Check firewall isn't blocking local connections

### Authentication failing

1. Verify internet connection on Android TV
2. Make sure you authorize on the Plex website
3. Try requesting a new PIN (restart the auth flow)
4. Check Plex.tv is accessible

### API keys not working

1. Verify keys are valid (test at fanart.tv or themoviedb.org)
2. Check internet connectivity
3. Review validation error messages
4. Try disconnecting and re-entering keys

### Emulator setup

When using Android Emulator:

1. Note the port shown on API Settings screen
2. Run: `adb forward tcp:PORT tcp:PORT`
3. Access via `http://localhost:PORT` on your host machine

## Contributing

Contributions are welcome! We'd love your help making Flix even better.

**Ways to contribute:**

-   Report bugs and suggest features via [GitHub Issues](https://github.com/willbeeching/flix/issues)
-   Submit pull requests for bug fixes or new features
-   Improve documentation
-   Share the project with others

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Disclaimer

This is an unofficial app and is not affiliated with or endorsed by Plex Inc., Fanart.tv, or The Movie Database (TMDB).

## Acknowledgments

-   [Plex](https://www.plex.tv/) for their excellent media server platform
-   [Fanart.tv](https://fanart.tv/) for high-quality artwork
-   [The Movie Database](https://www.themoviedb.org/) for comprehensive media data
-   The Android development community
-   All contributors and open source libraries used in this project
