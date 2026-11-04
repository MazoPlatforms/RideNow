# RideNow (Android, Kotlin + Jetpack Compose)

Package: `com.ridenow.app` · minSdk 26 · targetSdk 35

## Run
1. Open this folder in Android Studio (Ladybug or newer). It will create the Gradle wrapper and sync.
2. Run the `app` configuration on a device or emulator (API 26+).

## What is built
- Animated splash screen (`ui/splash/SplashScreen.kt`) with dark and light themes.
- Design tokens (`ui/theme/Theme.kt`) taken from the RideNow foundations board.
- Android 12+ system splash handoff, edge-to-edge, adaptive and monochrome launcher icon.
- Reduced motion: the splash is static when system animations are off.

## Next
`AppRoot.kt` shows a placeholder after the splash. Replace it with the Welcome screen.
