# RideNow Rider and Driver APK builds

The workflow at `.github/workflows/android-apk.yml` runs when either Android project changes under `app/android/`, or manually from GitHub Actions.

- Rider project: `app/android/rider/`.
- Driver project: `app/android/driver/`.
- Both apps use MapLibre Native Android SDK `13.8.0` with Sifric's hosted native style; the SDK's map rendering is updated without replacing the current RideNow map and routing integration.
- Both optimized preview builds use Java 17, Gradle 8.11.1, and Android SDK 35.
- The Rider source ZIP is generated from the current tracked Rider project in GitHub Actions, so it includes the latest language picker, translations, and icon resources without keeping a stale ZIP inside the source tree. The supplied Driver ZIP is retained byte-for-byte. The Driver APK is compiled from a temporary Actions copy so its checked-in source and archive remain unchanged.
- Missing Gradle wrapper scripts and JARs are generated only in GitHub Actions. They are not committed to either source project.
- For compilation only, CI adjusts temporary Rider and Driver build copies with Kotlin compatibility and missing runtime-state fixes required by the supplied source. The repository source and Driver archive remain untouched. CI verifies APK signatures and ABI contents, checks tracked Android source checksums, and publishes the APKs, both source ZIPs, and SHA-256 checksums as a prerelease.
- APKs are debug-signed for installation and testing, not production or Play Store releases. Debug keys differ between runs; uninstall an older preview if Android rejects an update.

## APK variants

Rider and Driver each publish five standalone APKs. Choose one per app.

| File suffix | Device |
| --- | --- |
| `arm64-v8a.apk` | Most current Android phones |
| `armeabi-v7a.apk` | 32-bit ARM devices |
| `x86.apk` or `x86_64.apk` | Matching Intel devices or emulators |
| `universal.apk` | Any of the four architectures above |

The preview build inherits the release R8 optimizations and resource shrinking, then uses the debug signing key for installation. Native libraries are compressed in the APK and extracted on the device. The R8 configuration retains library-provided rules, including MapLibre's.

Use `:app:assemblePreview` for the universal and per-ABI APKs together. Pass `-PrideNowAbiMode=universal` to disable splitting or `-PrideNowAbiMode=split` to build only the four per-ABI APKs. For these commands, use a temporary build copy prepared as in GitHub Actions.

No physical-device smoke test is implied by CI compilation. Confirm the onboarding and main screens, Android permissions, routing and service coverage, and light/dark themes on-device before pilot use.
