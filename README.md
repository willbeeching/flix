# Plex Screensaver for Android TV

An Android screensaver app that displays beautiful artwork from your Plex Media Server library. Perfect for Android TV devices and Android phones/tablets.

## Features

- 🔐 **Plex Link Authentication** - Secure OAuth device linking, no manual server configuration needed
- 🎨 **Automatic Artwork Display** - Shows movie posters and backdrops from your Plex library
- 📺 **Android TV Optimized** - Designed for the big screen with fullscreen display
- 🔄 **Smooth Transitions** - Beautiful crossfade effects between images
- ⚡ **Auto-Discovery** - Automatically finds and connects to your Plex servers
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
2. Fetches library sections (movies and TV shows)
3. Retrieves artwork URLs for available media
4. Displays artwork in rotation with crossfade transitions

## Technical Details

### Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Networking**: OkHttp
- **Image Loading**: Coil
- **JSON Parsing**: Moshi
- **XML Parsing**: Android XmlPullParser
- **Async**: Kotlin Coroutines

### Architecture

```
app/
├── plex/
│   ├── PlexAuthManager.kt      # Handles Plex Link OAuth flow
│   └── PlexApiClient.kt         # Plex API interactions
├── service/
│   └── PlexScreensaverService.kt # DreamService implementation
└── ui/
    ├── MainActivity.kt          # Landing screen
    ├── SettingsActivity.kt      # Authentication UI
    └── theme/                   # Material 3 theme
```

### Key Components

- **PlexAuthManager**: Manages the Plex Link authentication flow

  - Requests device PIN
  - Polls for user authorization
  - Stores auth token securely

- **PlexApiClient**: Interacts with Plex Media Server API

  - Discovers available servers
  - Fetches library sections
  - Retrieves artwork metadata

- **PlexScreensaverService**: DreamService that displays artwork
  - Loads artwork on start
  - Rotates images every 5 seconds
  - Applies smooth crossfade transitions

## Customization

You can customize various aspects by modifying the constants in the source code:

- **Rotation Interval**: Change `ROTATION_INTERVAL_MS` in `PlexScreensaverService.kt`
- **Crossfade Duration**: Adjust `CROSSFADE_DURATION_MS` in `PlexScreensaverService.kt`
- **Image Limit**: Modify `limit` parameter in `getArtworkFromSection()` calls
- **Library Types**: Filter different library types in the `loadArtwork()` method

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

## Future Enhancements

- [ ] Allow users to select specific libraries to display
- [ ] Add movie/show titles overlay
- [ ] Blur effect transitions (Netflix-style)
- [ ] TV remote control integration (pause/skip)
- [ ] Display recently watched content
- [ ] Show currently playing media
- [ ] Customizable rotation speed
- [ ] Ken Burns effect (pan & zoom)

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
