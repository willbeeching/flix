# Setup Guide for Plex Screensaver

This guide will walk you through building and deploying the Plex Screensaver app to your Android device.

## Prerequisites

### Required Software

1. **Android Studio** (Latest version recommended)
   - Download from: https://developer.android.com/studio
   - Includes Android SDK and build tools

2. **Java Development Kit (JDK) 8 or higher**
   - Android Studio typically includes this
   - Or download from: https://adoptium.net/

3. **Git** (for cloning the repository)
   - Download from: https://git-scm.com/

### Device Requirements

- Android device running Android 5.0 (Lollipop) or higher
- USB cable for device connection (or wireless debugging setup)
- Developer options enabled on your device

## Building the App

### Step 1: Clone the Repository

```bash
git clone https://github.com/yourusername/plex-screensaver.git
cd plex-screensaver
```

### Step 2: Open in Android Studio

1. Launch Android Studio
2. Click "Open an Existing Project"
3. Navigate to the `plex-screensaver` directory
4. Click "OK"
5. Wait for Gradle to sync (this may take a few minutes)

### Step 3: Configure Android SDK

If prompted, install any missing SDK components:
- Android SDK Platform 34
- Android Build Tools
- Android Emulator (optional, for testing)

### Step 4: Build the Project

#### Option A: Using Android Studio

1. From the menu: `Build > Make Project`
2. Wait for the build to complete
3. Check the "Build" tab at the bottom for any errors

#### Option B: Using Command Line

```bash
# On macOS/Linux:
./gradlew assembleDebug

# On Windows:
gradlew.bat assembleDebug
```

The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

## Installing the App

### Method 1: Direct Install from Android Studio

1. **Enable Developer Options on your Android device:**
   - Go to Settings > About Phone
   - Tap "Build Number" 7 times
   - Go back to Settings > System > Developer Options
   - Enable "USB Debugging"

2. **Connect your device:**
   - Connect via USB cable
   - Approve the USB debugging prompt on your device

3. **Run the app:**
   - In Android Studio, click the green "Run" button (▶️)
   - Select your device from the list
   - Wait for installation to complete

### Method 2: Install APK Manually

1. **Build the APK** (see Step 4 above)

2. **Transfer to device:**
   - Copy `app/build/outputs/apk/debug/app-debug.apk` to your device
   - Via USB, email, cloud storage, etc.

3. **Install on device:**
   - Open the APK file on your device
   - Tap "Install"
   - If blocked, go to Settings > Security > Unknown Sources and enable it
   - Complete the installation

### Method 3: Install on Android TV via ADB

1. **Install ADB on your computer:**
   ```bash
   # macOS (using Homebrew):
   brew install android-platform-tools

   # Ubuntu/Debian:
   sudo apt-get install adb

   # Windows: Download from Android SDK Platform Tools
   ```

2. **Enable ADB on Android TV:**
   - Go to Settings > Device Preferences > About
   - Click "Build" 7 times to enable Developer Options
   - Go back to Device Preferences > Developer Options
   - Enable "USB Debugging" and "ADB Debugging"

3. **Connect via ADB:**
   ```bash
   # Find your TV's IP address in Settings > Network
   adb connect YOUR_TV_IP_ADDRESS:5555

   # Verify connection
   adb devices
   ```

4. **Install the APK:**
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```

## Configuration

### First-Time Setup

1. **Launch the app** on your device

2. **Connect to Plex:**
   - Tap "Connect to Plex"
   - Visit https://plex.tv/link on any device
   - Enter the 4-digit code shown on screen
   - Sign in to your Plex account
   - Authorize the app

3. **Set as Screensaver:**
   - Tap "Set as Screensaver"
   - Select "Plex Screensaver" from the list
   - Configure timeout settings if desired

### Android TV Specific Setup

1. Go to Settings > Display & Sound > Screensaver
2. Select "Plex Screensaver"
3. Set the screensaver timeout
4. (Optional) Configure "When to start" settings

## Troubleshooting Build Issues

### Gradle Sync Failed

**Issue:** Gradle sync fails with dependency errors

**Solution:**
1. Update Android Studio to the latest version
2. File > Invalidate Caches and Restart
3. Ensure you have an active internet connection
4. Try syncing again

### SDK Not Found

**Issue:** Android SDK path errors

**Solution:**
1. Open Android Studio preferences
2. Go to Appearance & Behavior > System Settings > Android SDK
3. Verify SDK location
4. Install missing SDK components

### Build Takes Too Long

**Issue:** Gradle build is very slow

**Solution:**
1. Add to `gradle.properties`:
   ```properties
   org.gradle.daemon=true
   org.gradle.parallel=true
   org.gradle.caching=true
   ```
2. Increase Gradle memory in Android Studio:
   - Help > Edit Custom VM Options
   - Add: `-Xmx4096m`

### APK Won't Install on Device

**Issue:** "App not installed" error

**Solution:**
1. Uninstall any existing version of the app
2. Enable "Install Unknown Apps" for the installer (Files app, Chrome, etc.)
3. Check available storage space
4. Ensure the APK is not corrupted (re-download if needed)

## Development Tips

### Enable Logging

To see detailed logs:
```bash
adb logcat | grep -E "(PlexAuth|PlexApi|PlexScreensaver)"
```

### Test on Emulator

1. In Android Studio: Tools > Device Manager
2. Create a new virtual device
3. Select a TV device for Android TV testing
4. Run the app on the emulator

### Debug Screensaver

1. Enable the screensaver in Settings
2. Reduce timeout to minimum (e.g., 1 minute)
3. Let device idle to trigger screensaver
4. Check logs for errors

### Modify Rotation Speed

Edit `PlexScreensaverService.kt`:
```kotlin
private const val ROTATION_INTERVAL_MS = 3000L // Change to desired milliseconds
```

## Building for Release

### Step 1: Generate Signing Key

```bash
keytool -genkey -v -keystore plex-screensaver.keystore \
  -alias plex-screensaver -keyalg RSA -keysize 2048 -validity 10000
```

### Step 2: Configure Signing

Add to `app/build.gradle.kts`:
```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("path/to/plex-screensaver.keystore")
            storePassword = "your-store-password"
            keyAlias = "plex-screensaver"
            keyPassword = "your-key-password"
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            // ... existing config
        }
    }
}
```

### Step 3: Build Release APK

```bash
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

## Next Steps

- Customize the app (change colors, timing, etc.)
- Test with different Plex libraries
- Submit feedback and bug reports
- Contribute improvements via pull request

## Getting Help

- Check the main README.md for troubleshooting
- Review Android Studio build output for errors
- Check logcat for runtime errors
- Open an issue on GitHub with details

Happy screensaver building! 🎬


