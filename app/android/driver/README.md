# RideNow Driver (Android, Kotlin + Jetpack Compose)

Package: `com.ridenow.driver` · minSdk 26 · targetSdk 35 · same structure and code style as the Rider app.

## Run
1. Open this folder in Android Studio (Ladybug or newer). It creates the Gradle wrapper and syncs.
2. Run the `app` configuration on a device or emulator (API 26+).

## Built so far
- Animated splash (`ui/splash/SplashScreen.kt`): a route draws across a faint city map to the symbol, wordmark and "from Sifric" credit, then the orbit dot, pulse and tagline.
- Three-slide intro (`ui/intro/IntroScreen.kt`): go online and accept requests, navigate to the pickup, cash fares and earnings.
- Terms, Background location and Notifications are standalone permission screens (no step count). The numbered registration is 6 steps: Personal information, Identity and license, Vehicle details, Vehicle documents and photos, Availability and area, Bank account; then Review. Colors now follow the Rider app (brand yellow, same dark and light tokens). RTL fixes from the latest Rider build are ported.
- Trips (`ui/trip`): incoming request with countdown, heading to pickup, arrived, trip in progress, cash collection and rider rating. Finished trips feed Home and Earnings. UI preview settings (auto jump from Submitted to Home, sample requests) are in `ui/preview/PreviewConfig.kt`.
- Bottom bar: one shared design on every tab (tinted pill behind the selected icon, unread dot on Inbox).
- Earnings, Inbox and Account tabs (`ui/earnings`, `ui/inbox`, `ui/account`) with a shared bottom bar. Appearance and language switch for real; sign out resets the application.
- Home (`ui/home/HomeScreen.kt`): map with the driver position, GO and END button (locked until a bank account is added), real time online, and a location status chip.
- Status screens (`ui/status`): Application submitted and pending, Action required, Not approved, and Approved with Welcome.
- Bank account (step 11, can be skipped) and Review and submit: per-section summaries with Edit links that return to Review, and a confirmation before Submit.
- Availability (step 9) and Service area (step 10): work type, days, hours and reminder; city, radius with map preview, zones and outside-area switch.
- Vehicle documents (step 7) and Vehicle photos (step 8): document cards with photo and expiry date, and six photo slots with car outlines as framing guides.
- Vehicle details (step 6): type chips, make, model, year, color sheet and registration number with validation.
- Merged steps (`IdentityLicenseScreen`, `VehicleMediaScreen`, `WorkPlanScreen`) built from reusable form sections (`IdentityContent`, `LicenseContent`, `VehicleDocsContent`, `VehiclePhotosContent`, `AvailabilityContent`, `ServiceAreaContent`). Identity and license details:: NIC and license validation, date pickers, license class sheet, and front and back photo upload with a framing guide (`DocUploadTile`, `DocPhotoPair`).
- Terms (`ui/terms/TermsScreen.kt`): standalone screen with document rows, two consent checkboxes and a Continue button that unlocks when both are ticked. Reusable `StepHeader` and `RideCheckbox` are in `ui/components`.
- Welcome (`ui/welcome/WelcomeScreen.kt`): animated halo around the logo, three benefit rows, Get started and I already have an account.
- Phone number and OTP (`ui/auth`): country picker sheet, phone-hint prompt, 6-digit code entry with shake on error. Demo rule as in Rider: any code works except 000000.
- Theme (`ui/theme/Theme.kt`): driver mint palette, dark and light. Strings in English, French and Spanish.
- Supplied RideNow Driver logos in the same resource layout and names as the Rider app (see `branding/LOGO_ASSET_MANIFEST.md`).

## Next: Backend (sign in, requests, earnings)
Home, Earnings, Inbox and Account tabs are built. Trips, earnings and messages stay empty until a backend exists, and the status outcomes remain a demo (each Check status tap advances) until a backend decides.

## Driver update: screens from the concept screenshots (no side drawer)
Navigation: top header (logo, bell with unread dot, profile avatar) and a five-tab bottom bar: Home, Earnings, Rides, Support, Account. The drawer items live in the Account tab, the bell and the avatar.

- Notifications (`ui/notifications`): All / Rides / System, clear all.
- Earnings (`ui/earnings`): period pills, summary with change, stats, bar chart with range menu, recent rides; Earnings Details per ride.
- Ride History and Ride Details (`ui/rides`): status pills, search, filter sheet, grouped by day, map, rider contact buttons, breakdown, share.
- Driver Profile, Edit Profile (`ui/profile`); Vehicle Details, Edit Vehicle, Vehicle Documents (`ui/vehicle`).
- Payouts & Transactions with Withdraw, Bank Account (view, add, edit, remove) (`ui/payouts`).
- Settings, Appearance (theme, map style, accent colour, text size, launcher icon), Language (`ui/more`), Notification Settings, Privacy & Security (App Lock uses the phone screen lock), Payment Methods, Delete Account (`ui/settings`).
- Support Center, FAQs, Tickets, ticket chat with attachments, New Ticket, Help & Safety, Safety Center, Emergency Assist, Share Live Trip, Report an Issue (`ui/support`); Legal & About (`ui/legal`).

Notes
- Sample data: `PreviewConfig.SAMPLE_DATA` fills rides, payouts, notifications and tickets (`ui/data/DriverStore.kt`). Set it to false to start empty. Replace the store loaders with API calls when a backend exists.
- New text is in `res/values/strings_driver.xml` (English). Other languages fall back to English until translated.
- Fares are shown as `LKR` through `ui/util/Money.kt`; change `CURRENCY` there.
- Legal pages, emergency number (`emergency_number`) and company details are placeholders to replace.
- The launcher icon choice uses `activity-alias` entries in the manifest.
- Rate rider removed: the trip-complete screen only collects the fare now.
- Fixed from the supplied source: `RideIcons.Lock` was declared twice.
- Not compiled here (no Android SDK in the build environment). Run a Gradle build and fix any small compile errors.
