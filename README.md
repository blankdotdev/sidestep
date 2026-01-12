# Sidestep - Redirect to Alternative Frontends

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/screenshot_history.png" width="30%" />
  <img src="fastlane/metadata/android/en-US/images/screenshot_frontends.png" width="30%" />
  <img src="fastlane/metadata/android/en-US/images/screenshot_custom_redirects.png" width="30%" />
</p>

Privacy-focused Android app that intercepts social media URLs and redirects them to privacy-friendly alternative frontends while removing tracking parameters.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.blankdev.sidestep%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fblankdotdev%2Fsidestep%22%2C%22name%22%3A%22Sidestep%22%7D" target="_blank" rel="noopener">
  <img src="fastlane/metadata/android/en-US/images/badge_obtainium.png" alt="Get it on Obtainium" style="margin:0;vertical-align:middle;height:80px;">
</a>

## Features

### Smart URL Cleaning
Automatically removes tracking parameters from all URLs:
- UTM parameters (`utm_source`, `utm_medium`, etc.)
- Platform-specific trackers (Twitter `s`/`t`, YouTube `si`, Instagram `igsh`)
- E-commerce tracking (Amazon, affiliate links)
- Analytics parameters

### Automation
Granular control over how URLs are processed:
- **Unshorten URLs**: Automatically resolves shortened links (bit.ly, t.co, etc.)
- **Remove Tracking**: Strip analytics and tracking parameters
- **Immediate Navigation**: Automatically open the processed URL in your browser

### Privacy-Focused Redirects
Alternative frontends create a less cluttered web experience; bypassing closed ecosystems and promoting a more open internet. Sidestep supports several major platforms with configurable alternative frontends:

| Platform            | Alternative Frontends |
|---------------------|-----------------------|
| **Twitter/X**       | Nitter                |
| **Reddit**          | Redlib                |
| **YouTube**         | Invidious, Piped      |
| **Google Maps**     | OpenStreetMap         |
| **IMDb**            | LibreMDB              |
| **Medium**          | Scribe                |
| **Wikipedia**       | Wikiless              |
| **Goodreads**       | BiblioReads           |
| **Genius**          | Dumb, Intellectual    |
| **Tumblr**          | Priviblur             |
| **UrbanDictionary** | RuralDictionary       |
| **Imgur**           | Rimgo                 |
| **GitHub**          | GotHub                |
| **StackOverflow**   | AnonymousOverflow     |

### Flexible Configuration
- **Clean Only Mode**: Remove tracking without redirecting
- **Clean & Sidestep Mode**: Remove tracking AND redirect to alternative frontend
- **Custom Redirects**: Add your own domain mappings
- **Instance Picker**: Fetch live, healthy instances sorted by uptime

### History & Previews
- Track processed links with rich metadata
- Privacy-respecting previews fetched via DuckDuckGo (optional)
- Configurable retention policies (by days or item count)
- Never/Forever/Auto retention modes

### Privacy First
- **No Data Collection**: Zero analytics, zero tracking
- **Local Processing**: All URL cleaning happens on-device
- **Proxy Previews**: Metadata fetched via DuckDuckGo to avoid direct connections to source platforms (optional, disabled when history is off)

## Installation

### From Source
```bash
git clone https://github.com/yourusername/sidestep.git
cd sidestep
./gradlew assembleRelease
```

The APK will be in `app/build/outputs/apk/release/`

### Requirements
- Android 7.0 (API 24) or higher
- Android Studio (for development)

## Usage

### Quick Start
1. Install Sidestep
2. Share a social media link to Sidestep, or open a supported link
3. Sidestep will clean and optionally redirect the URL
4. Your default browser opens the cleaned/redirected link

### Setting as Default Handler
1. Open Sidestep → **Settings**
2. Tap **Set as Default**
3. Select **Open in app** for supported domains
4. Supported links will now automatically route through Sidestep

### Configuration
Navigate to **Settings** to:
- Toggle between "Clean only" and "Clean & Sidestep" for each platform
- Pick healthy alternative instances
- Configure custom domain redirects
- Adjust history retention policies
- Change app theme (Light/Dark/System)

## Architecture

```
┌─────────────────┐
│   MainActivity  │  Main UI, URL input, history display
└────────┬────────┘
         │
    ┌────┴────────────────────────────┐
    │                                 │
┌───▼──────────┐              ┌──────▼─────────┐
│  UrlCleaner  │              │ UrlUnshortener │
│              │              │                │
│ • Remove     │              │ • Resolve      │
│   tracking   │              │   shortened    │
│ • Normalize  │              │   URLs         │
└──────────────┘              └────────────────┘
         │
    ┌────┴────────────────────────────┐
    │                                 │
┌───▼────────────────┐     ┌──────────▼──────────────┐
│ HistoryManager     │     │ PreviewFetcher          │
│                    │     │                         │
│ • Store entries    │     │ • Fetch metadata via    │
│ • Apply retention  │     │   DuckDuckGo            │
└────────────────────┘     └─────────────────────────┘
         │
┌────────▼──────────────────────┐
│ AlternativeInstancesFetcher   │
│                               │
│ • Fetch healthy instances     │
│ • Provide fallback domains    │
└───────────────────────────────┘
```

## Privacy

Sidestep is designed with privacy as the core principle:

- **No Network Permissions for Tracking**: Only network access is for fetching alternative instances and URL resolution
- **No Analytics**: No Firebase, no crash reporting, no telemetry
- **Local Storage Only**: All history stored locally in SharedPreferences
- **Optional Metadata Fetching**: URL previews are fetched via DuckDuckGo search **only when history is enabled**. Set history retention to "Never" in settings to disable all metadata fetching and operate in pure URL-cleaning mode with zero external requests.
- **Open Source**: Fully auditable code

## Acknowledgments

Sidestep is built upon the work of many privacy-focused developers:

- [Nitter](https://github.com/zedeus/nitter) - Twitter alternative frontend
- [Redlib](https://github.com/redlib-org/redlib) - Reddit alternative frontend
- [Invidious](https://github.com/iv-org/invidious) - YouTube alternative frontend
- [Piped](https://github.com/TeamPiped/Piped) - YouTube alternative frontend
- [LibreMDB](https://github.com/zyachel/libremdb) - IMDb alternative frontend
- [Scribe](https://sr.ht/~edwardloveall/Scribe) - Medium alternative frontend
- [Wikiless](https://github.com/Metastem/wikiless) - Wikipedia alternative frontend
- [BiblioReads](https://github.com/nesaku/BiblioReads) - Goodreads alternative frontend
- [Dumb](https://github.com/rramiachraf/dumb) - Genius alternative frontend
- [Intellectual](https://github.com/Insprill/intellectual) - Genius alternative frontend
- [Priviblur](https://github.com/syeopite/priviblur) - Tumblr alternative frontend
- [RuralDictionary](https://codeberg.org/zortazert/rural-dictionary) - UrbanDictionary alternative frontend
- [rimgo](https://codeberg.org/rimgo/rimgo) - Imgur alternative frontend
- [GotHub](https://github.com/neofelix/gothub) - GitHub alternative frontend
- [AnonymousOverflow](https://github.com/httpjamesm/AnonymousOverflow) - StackOverflow alternative frontend
- [LibRedirect](https://github.com/libredirect/instances) - Source for instance data

Shoutouts to:

- Josh [Side of Burritos](https://sideofburritos.com)
- [Jason Swaan](https://github.com/duyfken)
- [James Pond](https://sr.ht/~jamesponddotco/)
- [Moritz](https://github.com/digitalblossom)
- [zyachel](https://github.com/zyachel)
- [mendel5](https://github.com/mendel5)

## License

This project is GNU AGPLv3 licensed - see [LICENSE](LICENSE) file for details.
