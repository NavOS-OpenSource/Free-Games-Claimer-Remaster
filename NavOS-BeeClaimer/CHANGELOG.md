# NavOS BeeClaimer - Changelog

All notable changes to the Android app are documented in this file.

Format: `[YYYY-MM-DD] Category: Description`

---

## [2026-10-26] Steam F2P Game Claiming - WORKING

### Fixed
- **F2P game claiming now works entirely within the app** - no need to open Steam app
- Extracts `subid` from the "Add to Library" button's `onclick` attribute: `onclick="AddFreeLicense(285241, 'Aimlabs')"`
- Calls `AddFreeLicense(subid, title)` directly via WebView JavaScript injection

### Technical Details
- The subid is NOT available in page text or SteamDB links (requires browser extension)
- The subid IS available in the onclick attribute of the "Add to Library" button
- JS extraction pattern: `onclick\s*=\s*["']AddFreeLicense\s*\(\s*(\d+)`
- Falls back to `steam://install/{appId}` only if no Add to Library button exists

### Key Learning
- Steam store pages have different buttons for different game types:
  - **F2P games**: "Add to Library" button with `onclick="AddFreeLicense(subid, title)"`
  - **Owned games**: "Play Game" button
  - **Paid games**: "Add to Cart" button
- The `AddFreeLicense()` JavaScript function requires a `subid` (package ID), not `appId`
- FreeKeep-for-Steam uses HTTP POST to `/freelicense/addfreelicense/{subid}` but that's for promotional free-to-keep games, not regular F2P

### Files Changed
- `SteamStoreAdapter.kt` - Updated JS_ADD_F2P_GAME to extract subid from onclick
- `ManualClaimScreen.kt` - Added `RequiresSteamApp` state for fallback handling

---

## [2026-10-21] Phase 7 - Store Adapter Architecture

### Added
- Multi-store adapter system (Steam, Epic, GOG, etc.)
- WebView-based authentication and claiming
- Session persistence with Room database

---

## [2026-10-21] Phase 5 - Browser & Account Authentication

### Added
- WebView integration for store login
- Cookie management and session handling
- Account linking UI

---

## [2026-10-21] Phase 3 - Native Foundation

### Added
- Initial Android project setup
- Jetpack Compose UI foundation
- Material 3 theming (Brutalist design)
- Navigation architecture

---

## Notes for Future Development

### Steam Claiming Methods (Priority Order)
1. **onclick extraction** - Find "Add to Library" button, extract subid from onclick, call `AddFreeLicense(subid, title)`
2. **HTTP POST** - `POST /freelicense/addfreelicense/{subid}` with sessionid (for promotional games)
3. **Steam app fallback** - `steam://install/{appId}` intent (requires Steam app installed)

### Steam API Endpoints (Reference)
- Search free games: `GET /search/results/?specials=1&maxprice=free&json=1`
- App details: `GET /api/appdetails?appids={appId}`
- User library: `GET /dynamicstore/userdata/`
- Claim free license: `POST /freelicense/addfreelicense/{subid}` (needs sessionid)

### Session ID Extraction
```javascript
// From page HTML
g_sessionID = "abc123def456"
```

### Known Limitations
- F2P games without "Add to Library" button require Steam app
- Some games (like CS2) may already be in library without user knowing (CS:GO → CS2 migration)
- WebView cookies don't automatically share with HttpURLConnection
