# RideNow Rider and Driver APK builds

The workflow at `.github/workflows/android-apk.yml` runs when either Android project changes under `app/android/`, or manually from GitHub Actions.

- Rider project: `app/android/rider/`.
- Driver project: `app/android/driver/`.
- Both debug APKs build with Java 17, Gradle 8.9, and Android SDK 35.
- The Rider source ZIP is generated from the current tracked Rider project in GitHub Actions, so it includes the latest language picker, translations, and icon resources without keeping a stale ZIP inside the source tree. The supplied Driver ZIP is retained byte-for-byte. The Driver APK is compiled from a temporary Actions copy so its checked-in source and archive remain unchanged.
- Missing Gradle wrapper scripts and JARs are generated only in GitHub Actions. They are not committed to either source project.
- For compilation only, CI adjusts temporary Rider and Driver build copies with Kotlin compatibility and missing runtime-state fixes required by the supplied source. The repository source and Driver archive remain untouched. CI verifies each APK signature, checks tracked Android source checksums, and publishes both APKs, both source ZIPs, and SHA-256 checksums as a prerelease.
- APKs are debug-signed for installation and testing, not production or Play Store releases. Debug keys differ between runs; uninstall an older preview if Android rejects an update.

No physical-device smoke test is implied by CI compilation. Confirm the onboarding and main screens, Android permissions, routing and service coverage, and light/dark themes on-device before pilot use.
