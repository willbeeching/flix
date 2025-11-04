# Quick Start Guide - Testing on Mac

## Prerequisites Check

You'll need Java (JDK) to build Android apps. Let's get you set up!

## Option 1: Install Android Studio (Recommended) ⭐

This is the easiest way - it includes everything you need:

### 1. Download Android Studio

```bash
# Open the download page
open https://developer.android.com/studio
```

Or download directly: **Android Studio Hedgehog (2023.1.1) or newer**

### 2. Install Android Studio

1. Download the `.dmg` file
2. Drag Android Studio to Applications
3. Launch Android Studio
4. Follow the setup wizard (install all recommended components)
5. Wait for SDK components to download (~5-10 minutes)

### 3. Open the Project

1. Click "Open" in Android Studio
2. Navigate to: `/Users/willbeeching/Sites/plex-screensaver`
3. Click "OK"
4. Wait for Gradle sync to complete (~2-5 minutes on first run)

### 4. Set Up an Emulator

1. Click the Device Manager icon (phone icon) in the toolbar
2. Click "Create Device"
3. For Android TV testing:
   - Category: **TV**
   - Select **Android TV (1080p)** or **4K Android TV**
4. For phone testing:
   - Category: **Phone**
   - Select **Pixel 6** or similar
5. Select a system image (recommended: **API 34 (Android 14)**)
6. Click "Download" if needed
7. Click "Finish"

### 5. Run the App

1. Click the green **Run** button (▶️) in the toolbar
2. Select your emulator from the device list
3. Wait for the emulator to boot (~1-2 minutes first time)
4. App will install and launch automatically!

---

## Option 2: Command Line with Homebrew (For Developers)

If you prefer command line:

### 1. Install Java (JDK)

```bash
# Install Homebrew if you don't have it
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Install OpenJDK 17
brew install openjdk@17

# Link it
sudo ln -sfn /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk

# Verify installation
java -version
```

### 2. Install Android Command Line Tools

```bash
# Download Android command line tools
brew install --cask android-commandlinetools

# Set ANDROID_HOME
echo 'export ANDROID_HOME=$HOME/Library/Android/sdk' >> ~/.zshrc
echo 'export PATH=$PATH:$ANDROID_HOME/emulator' >> ~/.zshrc
echo 'export PATH=$PATH:$ANDROID_HOME/platform-tools' >> ~/.zshrc
source ~/.zshrc

# Install SDK components
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0" "emulator" "system-images;android-34;google_apis;arm64-v8a"
```

### 3. Build the App

```bash
cd /Users/willbeeching/Sites/plex-screensaver

# Make gradlew executable (if needed)
chmod +x ./gradlew

# Build the app
./gradlew assembleDebug
```

### 4. Create an Emulator

```bash
# Create an Android TV emulator
avdmanager create avd -n PlexTV -k "system-images;android-34;google_apis;arm64-v8a" -d "tv_1080p"

# Or create a phone emulator
avdmanager create avd -n PixelTest -k "system-images;android-34;google_apis;arm64-v8a" -d "pixel_6"
```

### 5. Run the Emulator and Install

```bash
# Start the emulator in the background
emulator -avd PlexTV &

# Wait for it to boot (check with: adb devices)
# Then install the app
adb install app/build/outputs/apk/debug/app-debug.apk

# Launch the app
adb shell am start -n com.example.plexscreensaver/.ui.MainActivity
```

---

## Option 3: Test on a Real Android Device

### 1. Enable Developer Mode on Your Device

- Go to **Settings > About Phone/Tablet**
- Tap **Build Number** 7 times
- Go back to **Settings > System > Developer Options**
- Enable **USB Debugging**

### 2. Connect via USB

```bash
# Check connection
adb devices

# If you see your device, install the app
cd /Users/willbeeching/Sites/plex-screensaver
./gradlew installDebug
```

---

## Quick Commands Reference

```bash
# Build the project
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Uninstall
adb uninstall com.example.plexscreensaver

# View logs
adb logcat | grep -E "(Plex|Dream)"

# List connected devices
adb devices

# List emulators
emulator -list-avds
```

---

## Troubleshooting

### "No Java Runtime Found"

Install Java:

```bash
brew install openjdk@17
```

### "SDK location not found"

Create `local.properties` file:

```bash
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
```

### "Emulator won't start"

Make sure you have enough RAM (8GB minimum, 16GB recommended)

### "Gradle sync failed"

```bash
# Clean and rebuild
./gradlew clean
./gradlew assembleDebug
```

---

## Next Steps After Running

1. **Test Plex Link Auth:**

   - Click "Connect to Plex"
   - Visit plex.tv/link on another device
   - Enter the code shown

2. **Test the Screensaver:**

   - On emulator: Settings > Display > Screensaver
   - Select "Plex Screensaver"
   - Click "Preview" to test immediately

3. **View Logs:**
   ```bash
   adb logcat | grep -E "(PlexAuth|PlexApi|PlexScreensaver)"
   ```

---

## My Recommendation 🎯

**Use Android Studio!** It's much easier for testing and includes:

- ✅ Emulator built-in
- ✅ Easy debugging
- ✅ Visual log viewer
- ✅ One-click run
- ✅ All dependencies included

The command line approach is more complex and requires more setup.

**Download Android Studio here:** https://developer.android.com/studio

It's a ~1GB download but worth it! 🚀
