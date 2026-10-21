# NavOS GameClaimer - Browser Proof of Concept

## Purpose

This is a **Phase 2 proof-of-concept** for the NavOS GameClaimer Android port project.
It validates WebView capabilities for:

1. Loading public store pages (Epic Games Store)
2. Cookie persistence across app restarts
3. Basic page interaction
4. Navigation security

## Build Instructions

### Prerequisites
- Android Studio with SDK 35+ installed
- Android SDK Build-Tools 35.0.0+
- JDK 17+ (bundled with Android Studio)

### Build from Command Line

```bash
cd android-browser-poc
./gradlew assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Build from Android Studio

1. Open Android Studio
2. File -> Open -> Select `android-browser-poc` directory
3. Wait for Gradle sync
4. Run -> Run 'app'

## Testing

### Cookie Persistence Test

1. Launch the app
2. Navigate to Epic Games Store (default URL)
3. Click "Check Cookies" - note the cookie value
4. Close the app completely (swipe away)
5. Reopen the app
6. Click "Check Cookies" again
7. Compare cookie values - they should persist

### Store Compatibility Test

1. Launch the app
2. Click "Epic Free" button
3. Verify: Page loads without errors
4. Verify: Content is visible
5. Verify: Navigation works (click on a game)
6. Verify: Back button returns to previous page

## Security Notes

- **HTTPS only**: Non-HTTPS URLs are blocked
- **No JavaScript interface**: No native bridge exposed to web
- **Backup disabled**: App data excluded from cloud backup
- **Local addresses blocked**: localhost, 127.0.0.1, private IPs
- **No credentials**: This PoC does not store any credentials

## Limitations

This PoC does NOT:
- Implement actual game claiming
- Store user credentials
- Automate page interactions
- Bypass any store protections
- Support multiple accounts

These features are intentionally deferred to later phases.

## Architecture

```
MainActivity
├── BrowserPoCScreen (Compose)
│   ├── URL input + Go button
│   ├── Cookie info display
│   ├── Quick action buttons
│   └── WebView (AndroidView)
│       ├── WebViewClient (navigation)
│       └── WebChromeClient (chrome features)
```

## Phase 2 Deliverables

- [x] WebView embedding in Jetpack Compose
- [x] HTTPS-only navigation
- [x] Cookie persistence configuration
- [x] Epic Games Store page loading
- [x] Back navigation handling
- [x] Loading/error states
- [ ] Emulator test results (pending build)
