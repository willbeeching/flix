<!-- Contributing Guidelines -->

# Contributing to Flix

First off, thank you for considering contributing to Flix! It's people like you that make Flix such a great tool.

## Code of Conduct

This project and everyone participating in it is governed by common sense and mutual respect. Be kind, be professional, and be constructive.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check the existing issues to avoid duplicates. When you create a bug report, include as many details as possible:

-   Use a clear and descriptive title
-   Describe the exact steps to reproduce the problem
-   Provide specific examples
-   Describe the behavior you observed and what you expected
-   Include screenshots if applicable
-   Note your device, Android version, and app version

### Suggesting Enhancements

Enhancement suggestions are welcome! When creating an enhancement suggestion:

-   Use a clear and descriptive title
-   Provide a detailed description of the suggested enhancement
-   Explain why this enhancement would be useful
-   List any similar features in other apps if applicable

### Pull Requests

1. Fork the repo and create your branch from `master`
2. If you've added code, test it thoroughly
3. Ensure your code follows the existing style
4. Update documentation if needed
5. Write a clear commit message

## Development Setup

### Prerequisites

-   Android Studio (latest stable version)
-   JDK 11 or higher
-   Android SDK with API 21-34
-   Git

### Building the Project

1. Clone the repository:

```bash
git clone https://github.com/willbeeching/flix.git
cd flix
```

2. Open the project in Android Studio

3. Sync Gradle files

4. Build and run:

```bash
./gradlew assembleDebug
```

### Project Structure

```
app/
├── plex/              # Plex API clients
├── screensaver/       # Core screensaver logic
├── service/           # Android DreamService
├── settings/          # API key management
└── ui/                # Jetpack Compose UI screens
```

## Style Guidelines

### Kotlin Code

-   Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
-   Use meaningful variable and function names
-   Add comments for complex logic
-   Keep functions small and focused

### Compose UI

-   Use `Modifier` parameters consistently
-   Extract reusable composables
-   Use Google Sans font for consistency
-   Follow Material 3 design guidelines

### Commits

-   Use present tense ("Add feature" not "Added feature")
-   Use imperative mood ("Move cursor to..." not "Moves cursor to...")
-   Limit first line to 72 characters
-   Reference issues and PRs when applicable

Example commit messages:

```
Add support for music libraries

Fix crash when server is unreachable

Update ProGuard rules for Moshi
```

## Testing

### Manual Testing

Before submitting a PR, please test:

1. **Fresh install** - Install the app on a clean device/emulator
2. **Plex authentication** - Verify the QR code flow works
3. **API settings** - Test adding/removing API keys
4. **Server/library selection** - Ensure smooth navigation
5. **Screensaver preview** - Check artwork loads correctly
6. **Actual screensaver** - Set as screensaver and verify it activates

### Test Devices

Try to test on:

-   Android TV (preferred)
-   Android emulator (API 21, 29, 34)
-   Phone/tablet (optional but helpful)

## Security

If you discover a security vulnerability, please do NOT open a public issue. Email the maintainer directly (see GitHub profile).

## Areas Needing Help

We'd especially welcome contributions in these areas:

-   **Unit tests** - The project currently has no tests
-   **Accessibility** - Improving screen reader support
-   **Documentation** - More code comments and guides
-   **Translations** - Multi-language support
-   **Performance** - Optimization and profiling
-   **New features** - See the "Future Enhancements" section in README

## Questions?

Feel free to open an issue with the "question" label if you need help!

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing! 🎉
