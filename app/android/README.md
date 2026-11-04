# RideNow (Android, Kotlin + Jetpack Compose)

Package: `com.ridenow.app` · minSdk 26 · targetSdk 35

## Run
1. Open this folder in Android Studio (Ladybug or newer). It will create the Gradle wrapper and sync.
2. Run the `app` configuration on a device or emulator (API 26+).

## What is built
- Animated splash screen (`ui/splash/SplashScreen.kt`) and Welcome screen, using the supplied RideNow logo, wordmark and icons.
- Phone number screen with country picker sheet (`ui/auth`): live number formatting per country, validation, keyboard-aware layout.
- Brand colours (`ui/theme/Theme.kt`): yellow #FEC604 sampled from the logo, near-black ink, dark and light themes.
- Android 12+ system splash handoff, edge-to-edge, adaptive and monochrome launcher icon.
- Reduced motion: the splash is static when system animations are off.

## Next: phone number screen.
`AppRoot.kt` shows a placeholder after the splash. Replace it with the Welcome screen.
