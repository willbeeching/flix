# Changelog

All notable changes to Flix will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0-alpha] - 2024-12-22

**⚠️ ALPHA RELEASE - FOR TESTING ONLY**

This is an early alpha release for testing. Expect bugs and incomplete features!

### Added

-   GitHub Actions CI/CD workflow
-   Dependabot for automated dependency updates
-   Issue and PR templates
-   CONTRIBUTING.md guide
-   EditorConfig for consistent code formatting
-   Debug build variant with separate app ID
-   BuildConfig fields for version info

### Known Issues

-   Lint warnings present (non-blocking)
-   Requires manual API key configuration for enhanced artwork

## [1.0.0] - 2024-12-14

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
