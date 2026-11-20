# RideNow (Android, Kotlin + Jetpack Compose)

Package: `com.ridenow.app` · minSdk 26 · targetSdk 35

## Run
1. Open this folder in Android Studio (Ladybug or newer). It will create the Gradle wrapper and sync.
2. Run the `app` configuration on a device or emulator (API 26+).

## What is built
- Animated splash screen (`ui/splash/SplashScreen.kt`) and Welcome screen, using the supplied RideNow logo, wordmark and icons.
- Phone number screen with country picker sheet (`ui/auth`): live number formatting per country, validation, keyboard-aware layout.
- Backend phone verification through `https://mzn-api.onrender.com`: WhatsApp OTP request/verify, server-created Rider accounts, refreshable sessions, logout, and server-backed profile updates.
- The splash restores the server session and profile-completion state: a new account sees profile setup once, while completed accounts go straight to Home after later sign-ins or on another device.
- Optional profile photos are JPEG-compressed on device and uploaded through the backend's short-lived private staging URL; the backend validates and normalizes the stored image.
- Session tokens are stored with Android Keystore-backed encrypted preferences. The app revalidates sessions on process start, serializes refresh-token rotation, disables cleartext traffic, and excludes app data from backup.
- Brand colours (`ui/theme/Theme.kt`): yellow #FEC604 sampled from the logo, near-black ink, dark and light themes.
- Android 12+ system splash handoff, edge-to-edge, adaptive and monochrome launcher icon.
- Reduced motion: the splash is static when system animations are off.

WhatsApp OTP must be entered in the app. The Android phone-number hint only suggests a number; it does not prove ownership and is never used to skip verification. API flows live in `ui/auth/PhoneAuthApi.kt`.
