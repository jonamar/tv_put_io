# Put.io for Android TV (Privacy-Hardened Fork)

A privacy-focused fork of [SmileyJoe's excellent put.io Android TV app](https://github.com/SmileyJoe/tv_put_io).

Huge thanks to SmileyJoe for building a solid, feature-complete put.io client. This fork preserves all functionality while eliminating telemetry and extending device compatibility.

## Privacy Modifications

**This fork removes all tracking, analytics, and phone-home behavior.** Your viewing habits are your business.

### What Was Removed:
- **Firebase Analytics** - Usage tracking eliminated
- **Firebase Crashlytics** - Crash reporting to Google eliminated
- **Facebook Stetho** - Network debugging bridge (security hole) eliminated
- **ExoPlayer EventLogger** - Playback event logging disabled (code preserved for debugging)

**Philosophy**: Privacy should be the default, not an afterthought. Apps don't need to report your every action to corporate servers. This fork communicates only with:
- `put.io` API (obviously required)
- `themoviedb.org` API (for movie metadata/posters - optional, you control the API key)
- `statuspage.io` (put.io service status RSS - optional)

No Google. No Facebook. No telemetry.

## Backwards Compatibility

**Minimum SDK lowered from 26 → 25** (Android 8.0 → Android 7.1)

Enables installation on older Android TV devices (Anker Nebula Mars Pro 2, etc.) without sacrificing features. The original minSdk 26 requirement was unnecessarily restrictive—this app uses no Android 8+ specific APIs.

## Features (Unchanged)

- Browse put.io folders and stream videos
- ExoPlayer-based playback with adaptive bitrate streaming
- Subtitle support (multiple languages)
- Resume playback tracking (syncs with put.io)
- TMDB metadata integration (movie posters, cast info, descriptions)
- Custom groups (Movies, Series, Watch Later, Favorites)
- Genre filtering and organization
- MP4 conversion support for incompatible formats
- YouTube trailer playback
- Android TV Leanback UI (optimized for remote control navigation)

## Building

1. Register OAuth app at https://app.put.io/settings/account/oauth/apps
   - Callback URL: `urn:ietf:wg:oauth:2.0:oob`
2. (Optional) Get TMDB API token at https://www.themoviedb.org/settings/api
3. Add to `gradle.properties`:
   ```
   putio_client_id=YOUR_CLIENT_ID
   putio_client_secret=YOUR_CLIENT_SECRET
   tmdb_auth_token=YOUR_TMDB_TOKEN
   ```
4. Build: `./gradlew assembleDebug`

APK will be in `app/build/outputs/apk/debug/app-debug.apk`

## License

Inherits original license from SmileyJoe/tv_put_io. No affiliation with put.io.
