# RideNow Driver (Android, Kotlin + Jetpack Compose)

Package: `com.ridenow.driver` · minSdk 26 · targetSdk 35 · same structure and code style as the Rider app.

## Run
1. Open this folder in Android Studio (Ladybug or newer). It creates the Gradle wrapper and syncs.
2. Run the `app` configuration on a device or emulator (API 26+).

## Built so far
- Animated splash (`ui/splash/SplashScreen.kt`): a route draws across a faint city map to the stacked RideNow Driver lockup, then the orbit dot, pulse and tagline.
- Three-slide intro (`ui/intro/IntroScreen.kt`): go online and accept requests, navigate to the pickup, cash fares and earnings.
- Theme (`ui/theme/Theme.kt`): driver mint palette, dark and light. Strings in English, French and Spanish.
- Supplied RideNow Driver logos: launcher (adaptive, round, legacy, themed), notification icon, marks and wordmarks (`res/drawable-nodpi`). See `branding/ASSET_MANIFEST.md`.

## Next: Welcome screen
`AppRoot.kt` shows a placeholder after the intro. Replace it with the Welcome screen.
