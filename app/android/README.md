# RideNow Android (Kotlin + Jetpack Compose)

Package `com.ridenow.app` · Android 8.0+ (minSdk 26) · targetSdk 35.

## Run

Open `app/android/` in Android Studio with Android SDK 35 and Java 17.
The CI workflow `.github/workflows/android-apk.yml` builds the **checked-in Kotlin sources**, not the archived ZIP. Run `:app:assembleDebug`.

## Mazo Maps (map-only integration)

MapLibre Native 13.6.1 renders public vector resources from
`https://maps.mazo.workers.dev/api/v1` with no Mapbox token or Cloudflare secrets.

- Home: native pan/zoom map, light/dark styles, permission-aware foreground device GPS and Locate control.
- Destination search: 300 ms debounce and cancellable `/places/search` requests. Tap a real place to save its geographic coordinates.
- Map picker: camera center longitude/latitude; it does not invent an address when reverse geocoding is unavailable.
- Route previews: cancellable `POST /route` driving request for real start/end coordinates, drawn GeoJSON line and selected markers. Coverage errors are explicit.
- Styles: `/style.json`, `/style/dark.json`; 15-second ETag version check while a map is on screen.
- Shows offline state; MapLibre cache is opportunistic. Explicit offline download/refresh is not implemented.

**Coverage:** the currently published Mazo vector archive contains a `places` layer, not a complete roads/buildings/water basemap. Local routing is Kattankudy-area only (80 m snap limit); no extrapolated straight-line fallback is used. Routes and travel times are map data previews and **not RideNow fare quotes**.

**Not connected:** RideNow OTP, bookings, dispatch, real driver positions, payment, notifications and rider session storage remain demo UI, unchanged. Vehicle fares are still sample USD values; do not use them for real bookings. The Driver Android app is not included here.

Map requests are public read-only, require TLS and never include a RideNow token. Device location remains local except when the user selects a destination and requests a route, which sends the two coordinates to Mazo Maps.

## Legacy ZIP

`app/android/RideNow-Android.zip` is retained unchanged as an archival source from the previous UI build. It is **not** the input for current APK builds and might differ from checked-in Kotlin sources.
