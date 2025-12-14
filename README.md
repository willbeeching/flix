# Plex Screensaver for Android TV

An Android screensaver app that displays beautiful artwork from your Plex Media Server library. Perfect for Android TV devices and Android phones/tablets.

## Features

- 🔐 **Plex Link Authentication** - Secure OAuth device linking, no manual server configuration needed
- 🎨 **High-Quality Artwork** - Shows 4K transparent logos and curated backdrops from your Plex library
- 📺 **Android TV Optimized** - Designed for the big screen with fullscreen display
- 🔄 **Smooth Transitions** - Beautiful crossfade effects with Ken Burns pan animation
- 🌈 **Dynamic Color Gradients** - Automatically extracts colors from artwork for ambient lighting effects
- ✨ **Blur Background Layer** - Subtle depth effect on Android 12+ (GPU-accelerated)
- 🎬 **Smart Logo Sizing** - Intelligently sizes logos based on aspect ratio for consistent visual weight
- 🖼️ **Curated Backgrounds** - Prioritizes Plex's clean backdrops over promotional images
- ⚡ **Auto-Discovery** - Automatically finds and connects to your Plex servers
- 📚 **Library Selection** - Choose specific libraries to display
- 📱 **Mobile Support** - Works on Android phones and tablets too

## Requirements

- Android 5.0 (API 21) or higher
- A Plex account
- A Plex Media Server with movie or TV show libraries

## Installation

### Option 1: Build from Source

1. Clone this repository
2. Open in Android Studio
3. Build and run on your device

### Option 2: Install APK

1. Download the latest APK from the releases page
2. Install on your Android device
3. Grant necessary permissions

## Setup Instructions

1. **Launch the app** on your Android device
2. **Connect to Plex**:
   - Tap "Connect to Plex"
   - Visit the URL shown on screen (https://plex.tv/link)
   - Enter the 4-digit code displayed
   - Authorize the app in your Plex account
3. **Set as Screensaver**:
   - Tap "Set as Screensaver"
   - Select "Plex Screensaver" from the list
   - Configure screensaver timeout if desired
4. **Enjoy!** - Your Plex artwork will now display when the screensaver activates

## How It Works

### Authentication Flow

1. App requests a PIN from Plex's OAuth API
2. User visits plex.tv/link and enters the code
3. App polls Plex API until authorization is complete
4. Auth token is securely stored locally

### Artwork Loading

1. Discovers available Plex servers using the auth token
2. Fetches library sections (movies and TV shows) with high-quality metadata
3. Extracts Plex clearLogos (4K transparent PNGs) from Image elements
4. Uses curated Plex backdrops, falling back to TMDB for maximum quality
5. Applies dynamic color extraction for gradient overlays
6. Displays artwork in rotation with smooth crossfade and Ken Burns effects

## Technical Details

### Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Networking**: OkHttp
- **Image Loading**: Coil
- **JSON Parsing**: Moshi
- **XML Parsing**: Android XmlPullParser
- **Async**: Kotlin Coroutines
- **Color Extraction**: AndroidX Palette
- **Visual Effects**: RenderEffect (Android 12+)

### Architecture

```
app/
├── plex/
│   ├── PlexAuthManager.kt      # Handles Plex Link OAuth flow
│   ├── PlexApiClient.kt         # Plex API interactions (with clearLogo support)
│   └── TmdbClient.kt            # TMDB fallback for logos/backdrops
├── screensaver/
│   └── ScreensaverController.kt # Shared screensaver logic with effects
├── service/
│   └── PlexScreensaverService.kt # DreamService implementation
└── ui/
    ├── MainActivity.kt          # Landing screen
    ├── SettingsActivity.kt      # Authentication UI
    ├── ServerSelectionActivity.kt # Choose Plex server
    ├── LibrarySelectionActivity.kt # Choose libraries
    ├── PreviewActivity.kt       # Test screensaver
    └── theme/                   # Material 3 theme
```

### Key Components

- **PlexAuthManager**: Manages the Plex Link authentication flow

  - Requests device PIN
  - Polls for user authorization
  - Stores auth token securely

- **PlexApiClient**: Interacts with Plex Media Server API

  - Discovers available servers
  - Fetches library sections with Image elements
  - Extracts clearLogo URLs (4K transparent PNGs)
  - Retrieves high-quality artwork metadata

- **TmdbClient**: Fetches supplementary artwork from TMDB

  - Gets high-resolution backdrops (up to 4K)
  - Retrieves title logos when Plex doesn't have them
  - Filters for clean images (high vote_average)
  - Caches responses to minimize API calls

- **ScreensaverController**: Manages screensaver display and effects
  - Loads artwork with smart prioritization (Plex first, TMDB fallback)
  - Rotates images every 10 seconds with 2-second crossfade
  - Extracts colors using Palette API for dynamic gradients
  - Applies Ken Burns pan effect for subtle motion
  - Intelligently sizes logos based on aspect ratio
  - Manages blur layer on Android 12+ devices

## Customization

You can customize various aspects by modifying the constants in the source code:

- **Rotation Interval**: Change `ROTATION_INTERVAL_MS` in `ScreensaverController.kt` (default: 10 seconds)
- **Crossfade Duration**: Adjust `CROSSFADE_DURATION_MS` in `ScreensaverController.kt` (default: 2 seconds)
- **Blur Intensity**: Modify blur radius in `init{}` block (default: 25px)
- **Logo Sizing**: Adjust percentage values in `adjustLogoSize()` (default: 12-18% of screen width)
- **Image Batch Size**: Modify `batchSize` parameter in `getArtworkFromSection()` (default: 300 items)
- **Library Selection**: Use the app UI to select specific libraries to display

## Privacy & Security

- Auth tokens are stored locally in encrypted SharedPreferences
- No data is sent to third parties
- All communication is directly between your device and Plex services
- The app only requests read-only access to your Plex libraries

## Troubleshooting

### Screensaver not showing artwork

1. Check that you're signed in to Plex in the app settings
2. Ensure your Plex server is accessible
3. Verify you have movie or TV show libraries with artwork
4. Try signing out and signing back in

### Authentication failing

1. Make sure you're entering the correct code at plex.tv/link
2. Check your internet connection
3. Try requesting a new code

### Images loading slowly

1. This is normal on first load as images are downloaded
2. Images are cached after first load
3. Consider your network speed and Plex server location

## Recent Improvements

- [x] Plex clearLogo support (4K transparent PNGs)
- [x] Blur background layer (Android 12+)
- [x] Smart logo sizing algorithm
- [x] Ken Burns pan effect
- [x] Library selection UI
- [x] Server selection UI
- [x] Dynamic color-extracted gradients
- [x] Prioritize curated Plex backgrounds

## Future Enhancements

- [ ] Add movie/show titles overlay (optional)
- [ ] TV remote control integration (pause/skip)
- [ ] Display recently watched content
- [ ] Show currently playing media
- [ ] Customizable rotation speed (UI setting)
- [ ] Photo library support

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Disclaimer

This is an unofficial app and is not affiliated with or endorsed by Plex Inc.

## Acknowledgments

- Plex for their excellent media server platform
- The Android development community
- All contributors to the libraries used in this project
