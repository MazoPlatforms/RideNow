# RideNow Android APK build

The workflow at `.github/workflows/android-apk.yml` runs on updates to `app/android/**`, or manually.

- Builds the **checked-in** `app/android/` Kotlin/Jetpack Compose source with Java 17, Gradle 8.9, Android SDK 35.
- Includes MapLibre Native and Mazo Maps public style/search/route APIs; no RideNow backend is connected.
- Generates temporary Gradle wrapper scripts and JAR; never commits generated wrapper files.
- Verifies the debug APK with Android `apksigner` and checks tracked Android files remain unchanged.
- Publishes a debug-signed prerelease APK plus file checksums and source checksums.
- Retains and publishes `app/android/RideNow-Android.zip` as an **unchanged legacy archive**. The ZIP is not the build input and must not be represented as matching current Kotlin source.
- Debug signing changes between runs; uninstall an old preview when Android rejects a new signature.

No physical-device smoke test is implied by CI compilation. Confirm on-device style, GPS permission, network failures, search cancellation, map picker, Kattankudy route, non-coverage 422, light/dark theme, and app foreground/background transitions.

Reference implementation and API limits: `MazoPlatforms/newmaps/docs/RIDENOW_ANDROID_MAPLIBRE.md`.
