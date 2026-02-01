# Changelog

All notable changes to Flix will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.3-alpha] - 2026-02-01

### Added

-   Connection status indicators in server selection (colored dots)
-   Relay connection support (previously disabled)
-   Enhanced diagnostic logging for server discovery

### Changed

-   Increased connection timeout from 5s to 15s for better reliability
-   Server discovery now includes relay connections (`includeRelay=1`)
-   Servers with failed connection tests are now shown with status indicators
-   Server list displays connection status via colored dots (green/orange/red)

### Fixed

-   Server discovery issues for users behind complex NAT/firewall setups
-   Servers not appearing when only relay connections available
-   Connection timeout too short for slower networks
-   Improved error handling when server discovery fails

## [0.1.2-alpha] - 2025-12-30

### Added

-   Version number display on main screen
-   Connection testing for Plex servers to find working URI
-   BuildConfig flag to control Flix logo visibility (debug vs release builds)

### Fixed

-   APK signing - releases are now properly signed and installable
-   DNS resolution errors for .plex.direct hostnames on Android TV
-   Connection timeouts (5s connect, 10s read/write) prevent hanging
-   Android TV screensaver settings navigation fallback
-   Connection now prefers direct IP addresses over .plex.direct hostnames

### Known Issues

-   Manual navigation may be required for screensaver settings on some Android TV devices

## [0.1.1-alpha] - 2025-12-30

### Fixed

-   Initial APK signing configuration for GitHub Actions

## [0.1.0-alpha] - 2025-12-22

**⚠️ ALPHA RELEASE - FOR TESTING ONLY**

This is an early alpha release for testing. Expect bugs and incomplete features!

### Added

-   GitHub Actions CI/CD workflow
-   Issue and PR templates
-   CONTRIBUTING.md guide
-   EditorConfig for consistent code formatting
-   Debug build variant with separate app ID
-   BuildConfig fields for version info

### Known Issues

-   Lint warnings present (non-blocking)
-   Requires manual API key configuration for enhanced artwork

## [1.0.0] - 2025-12-14

### Added

-   QR code-based Plex authentication with auto-fill PIN
-   Web-based API key configuration via QR code
-   Support for Fanart.tv HD artwork
-   Support for TMDB backdrops and logos
-   Server selection screen
-   Library selection screen
-   API settings screen with real-time validation
-   Preview mode for testing screensaver
-   Dynamic color-extracted gradients
-   Ken Burns pan effect
-   Smart aspect ratio-aware logo sizing
-   Blur background layer (Android 12+)
-   4K Plex clearLogo support
-   Smooth crossfade transitions
-   Auto-discovery of Plex servers

### Security

-   SSL certificate validation for external APIs
-   Secure API key storage in SharedPreferences
-   Local-only HTTP server for configuration
-   OAuth device linking for Plex authentication
-   ProGuard/R8 obfuscation enabled

### Fixed

-   Logo alignment on screensaver
-   Button spacing on main screen
-   QR code sizing consistency
-   SSL validation for external APIs

---

## Release Notes Template

Use this for future releases:

```markdown
## [X.Y.Z] - YYYY-MM-DD

### Added

-   New features

### Changed

-   Changes to existing functionality

### Deprecated

-   Features that will be removed

### Removed

-   Removed features

### Fixed

-   Bug fixes

### Security

-   Security improvements
```
