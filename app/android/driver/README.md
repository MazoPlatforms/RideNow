# RideNow Driver (Android, Kotlin + Jetpack Compose)

Package: `com.ridenow.driver` · minSdk 26 · targetSdk 35 · same structure and code style as the Rider app.

## Run
1. Open this folder in Android Studio (Ladybug or newer). It creates the Gradle wrapper and syncs.
2. Run the `app` configuration on a device or emulator (API 26+).

## Built so far
- Animated splash (`ui/splash/SplashScreen.kt`): a route draws across a faint city map to the symbol, wordmark and "from Sifric" credit, then the orbit dot, pulse and tagline.
- Three-slide intro (`ui/intro/IntroScreen.kt`): go online and accept requests, navigate to the pickup, cash fares and earnings.
- Background location (`ui/registration/BackgroundLocationScreen.kt`) and Personal information (`PersonalInfoScreen.kt`): step 2 and 3 of 11. Colors now follow the Rider app (brand yellow, same dark and light tokens). RTL fixes from the latest Rider build are ported.
- Identity (step 4) and Driving license (step 5) in `ui/registration`: NIC and license validation, date pickers, license class sheet, and front and back photo upload with a framing guide (`DocUploadTile`, `DocPhotoPair`).
- Terms (`ui/terms/TermsScreen.kt`): step 1 of 11 with document rows, two consent checkboxes and a Continue button that unlocks when both are ticked. Reusable `StepHeader` and `RideCheckbox` are in `ui/components`.
- Welcome (`ui/welcome/WelcomeScreen.kt`): animated halo around the logo, three benefit rows, Get started and I already have an account.
- Phone number and OTP (`ui/auth`): country picker sheet, phone-hint prompt, 6-digit code entry with shake on error. Demo rule as in Rider: any code works except 000000.
- Theme (`ui/theme/Theme.kt`): driver mint palette, dark and light. Strings in English, French and Spanish.
- Supplied RideNow Driver logos in the same resource layout and names as the Rider app (see `branding/LOGO_ASSET_MANIFEST.md`).

## Next: Vehicle details screen
`AppRoot.kt` still shows a placeholder for the Vehicle details screen after the driving license. Replace it next.
