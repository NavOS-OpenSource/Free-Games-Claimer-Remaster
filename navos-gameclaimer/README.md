# NavOS GameClaimer - Native Android App

Phase 3: Native Android Foundation

## Overview

NavOS GameClaimer is a native Android application for automatically claiming free games from various digital storefronts. This is the production app structure, building on the Phase 2 Browser PoC.

## Architecture

```
navos-gameclaimer/
├── app/src/main/
│   ├── java/com/navos/gameclaimer/
│   │   ├── MainActivity.kt           # Single-activity entry point
│   │   ├── GameClaimerApplication.kt # Application class
│   │   ├── ui/
│   │   │   ├── navigation/           # Compose Navigation
│   │   │   ├── screens/              # Screen composables
│   │   │   ├── components/           # Reusable UI components
│   │   │   └── theme/                # Material 3 theming
│   │   ├── domain/
│   │   │   ├── model/                # Business entities
│   │   │   ├── repository/           # Repository interfaces
│   │   │   └── usecase/              # Business logic (future)
│   │   ├── data/
│   │   │   ├── repository/           # Repository implementations (future)
│   │   │   ├── local/                # Room database (future)
│   │   │   └── remote/               # API clients (future)
│   │   ├── browser/                  # WebView handling (future)
│   │   └── di/                       # Dependency injection (future)
│   └── res/
│       ├── values/                   # Resources
│       └── xml/                      # Security configs
└── gradle/                           # Build configuration
```

## Supported Stores (Planned)

| Store | Status | Notes |
|-------|--------|-------|
| Epic Games | Phase 5 | Primary target |
| GOG | Phase 6+ | Secondary target |
| Steam | Phase 6+ | Free weekends only |
| Prime Gaming | Phase 6+ | Requires Amazon auth |

## Build Instructions

### Prerequisites
- Android Studio Hedgehog or newer
- JDK 17+ (JDK 25 works with Gradle 8.10+)
- Android SDK 35

### Build Debug APK
```bash
cd navos-gameclaimer
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

### Run Tests
```bash
./gradlew test
```

### Install on Emulator/Device
```bash
./gradlew installDebug
```

## Dependencies

| Library | Version | Purpose | License |
|---------|---------|---------|---------|
| Kotlin | 2.0.21 | Language | Apache 2.0 |
| Jetpack Compose BOM | 2024.06.00 | UI framework | Apache 2.0 |
| Material 3 | BOM-managed | Design system | Apache 2.0 |
| Navigation Compose | 2.7.7 | Screen navigation | Apache 2.0 |
| Room | 2.6.1 | Local database | Apache 2.0 |
| DataStore | 1.1.1 | Preferences | Apache 2.0 |
| Security Crypto | 1.1.0-alpha06 | Encrypted storage | Apache 2.0 |
| WorkManager | 2.9.0 | Background tasks | Apache 2.0 |
| AndroidX WebKit | 1.11.0 | WebView | Apache 2.0 |

## Security Design

### Data Protection
- **No credential storage**: Authentication via WebView session only
- **Encrypted preferences**: Using AndroidX Security Crypto
- **Backup disabled**: `allowBackup="false"`, no cloud backup
- **HTTPS only**: `usesCleartextTraffic="false"`, network security config

### WebView Security (Phase 5+)
- No JavaScript interface exposed
- File access disabled
- Navigation restricted to store domains
- Session isolation per store

### Planned Security Features
- Android Keystore for session tokens
- Certificate pinning for store APIs
- Biometric unlock option
- Secure logging (no credentials in logs)

## Phase Roadmap

- [x] Phase 1: Environment Audit
- [x] Phase 2: Browser PoC
- [ ] **Phase 3: Native Foundation** ← Current
- [ ] Phase 4: Secure Storage
- [ ] Phase 5: Epic Games Integration
- [ ] Phase 6: Additional Stores
- [ ] Phase 7: Background Claiming
- [ ] Phase 8-16: Polish & Release

## Development Notes

### What This Phase Does NOT Include
- Real store authentication
- Actual game claiming
- Background scheduling
- Push notifications
- Production signing

### Testing Strategy
- Unit tests for domain models
- UI tests for navigation
- Integration tests for store adapters (Phase 5+)
- Manual testing on physical devices

## Contributing

This project is part of NavOS. See the main repository for contribution guidelines.

## License

MIT License - See LICENSE file
