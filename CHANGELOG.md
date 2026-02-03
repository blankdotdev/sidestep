## [1.1.9] - 2026-02-02

### Security
- Fixed multiple Polynomial regular expression (ReDoS) vulnerabilities in `AlternativeInstancesFetcher`.

## [1.1.8] - 2026-02-02

- Added monochrome app icon for Android themed icons.
- Improved domain title identification for news and media sites.
- Enhanced podcast resolution and added support for `vxtwitter.com` and `fxtwitter.com`.

## [1.1.7] - 2026-02-01

### Added
- Resilient URL preview fetching supporting both DuckDuckGo HTML and Lite versions.
- Robust mobile headers for DuckDuckGo requests to avoid bot detection and improve reliability on physical devices.
- Internal debug logging for URL processing and preview fetching.

### Improved
- Optimized URL processing priority: navigation to cleaned URLs now occurs before background history and metadata operations for improved redirection speed.
- Elegant, non-hardcoded service name extraction for generic brand and subdomain identification.

## [1.1.5] - 2026-01-31

### Fixed
- Refactored unshortening logic to use optimized native redirects.
- Improved error recovery: preserves unshortening progress on failures.
- Enhanced JavaScript and HTML redirect detection.
- Cleaned up history UI to prevent flashing of old items.
- Added Mutex synchronization to `HistoryManager` to prevent race conditions.
- Removed `t.co` from the app choice list (unshortening already handles it).

## [1.1.4] - 2026-01-30

### Fixed
- Custom redirects now work correctly with TikTok short links (e.g., `vt.tiktok.com`, `vm.tiktok.com`).
- Removed special case that was preventing custom redirects from being applied after URL unshortening.

## [1.1.3] - 2026-01-29

### Security
- Removed `usesCleartextTraffic` flag from Manifest.
- Disabled `dependenciesInfo` blob generation in build config to prevent metadata leakage.

### Fixed
- Resolved double redirect delay for Reddit links.

## [1.1.2] - 2026-01-29

### Added
- Enhanced URL cleaning (Amazon, TikTok, etc.).
- Introduced "Fetch canonical links" toggle for deep link resolution.
- Added default custom redirects for Spotify and Apple Podcasts.

### Changed
- Improved UX and fixed bugs.

## [1.1.1] - 2026-01-16

### Fixed
- "Add to Obtainium" button in README (resolved type cast error and missing fields).
- Repository bloat: removed over 2,800 accidentally tracked build artifacts.
- Synchronization issues between GitHub and GitLab mirrors.

### Added
- Tracking parameter removal for `gaa_` headers (specifically for *The Times* and other Google Analytics attributed URLs).
- Comprehensive `.gitignore` to prevent future build artifact leaks.

## [1.1.0] - 2026-01-15

### Added
- Initial release with alternative frontends, URL cleaning, and history.
- License change to GNU AGPLv3.
- F-Droid metadata preparation.
