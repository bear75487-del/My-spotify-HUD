# Spotify HUD

A standalone, client-side Minecraft **1.21.1 Fabric** mod that shows the track
currently playing in the Spotify desktop app as a compact on-screen overlay.

The feature was extracted from a larger client and rebuilt from scratch: no
Corz client classes, modules, fonts, commands, or shaders are referenced. The
only shared ideas are the playback source (Windows system media sessions), the
iTunes artwork lookup, and the LRCLIB lyrics lookup, all re-implemented here.

---

## Features

- **Now playing card** with album art, title, artist, and a live progress bar.
- **Synced LRC lyrics** (word-level karaoke highlighting when LRCLIB provides
  enhanced timestamps).
- **Album art** resolved from the public iTunes Search API, downloaded
  asynchronously, rounded and bilinear-filtered so it stays smooth.
- **Anti-aliased transport icons** (previous / play / pause / next) generated
  at 64x64 and scaled down, matching the original overlay's look.
- **Automatic Spotify detection** through the Windows 10/11 system media
  transport controls (SMTC).
- **Four themes**, adjustable scale, width, corner radius, opacity, lyric line
  count, anchor, and individual toggles for every HUD section.
- **Mod Menu integration** plus a **drag-to-position** editor.
- All settings persisted to `config/spotifyhud.json` and applied instantly.

---

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Fabric Loader | 0.16.0+ |
| Fabric API | any 1.21.1 build |
| Mod Menu | 11.0.0+ |
| Java | 21+ |
| OS for auto detection | Windows 10 or 11 |

Mod Menu is a declared dependency because the settings screen is exposed
through it. On non-Windows systems the mod loads and the settings screen still
works, but the overlay stays on its idle "Nothing playing" state.

---

## Build

A JDK 21 is required to run Gradle (the wrapper uses Gradle 8.12, which is the
minimum supported by Fabric Loom 1.9.2).

```powershell
# From the spotify-hud directory
.\gradlew.bat build
```

The remapped mod jar is written to:

```
build/libs/spotify-hud-1.0.0.jar
```

Copy it together with Fabric API and Mod Menu into your `.minecraft/mods`
folder.

For development:

```powershell
.\gradlew.bat runClient
```

The dev run directory is `run/`; settings are written to
`run/config/spotifyhud.json`.

---

## Using the HUD

- Open **Mod Menu -> Spotify HUD -> Configure**.
- Or bind **Open Spotify HUD settings** in Minecraft's key bindings. The
  keybind is unbound by default, so it never conflicts with existing controls.
- **Move HUD...** opens a preview screen. Drag the card with the left mouse
  button; the nearest corner becomes the anchor. **Reset Position** restores
  the top-left default and **Done** saves.

### Options

| Option | Default | Description |
| --- | --- | --- |
| HUD Enabled | on | Master switch for rendering and detection. |
| Auto Detect | on | Starts/stops the Windows media session reader. |
| Album Art | on | Shows the cover (or a music-note placeholder). |
| Progress Bar | on | Elapsed / remaining time and progress fill. |
| Lyrics | on | Synced lyric panel under the progress bar. |
| Controls | on | Previous / play-pause / next icons. |
| Shadow | on | Soft drop shadow behind the card. |
| Anchor | Top Left | Corner the offsets are measured from. |
| Theme | Spotify Dark | Spotify Dark, Midnight, Light, or Minimal. |
| Scale | 1.00x | 0.50x - 2.00x. |
| Card Width | 220 px | 160 - 360 base pixels. |
| Corner Radius | 8 px | 0 - 16 base pixels. |
| Background | 88% | Card opacity, 15% - 100%. |
| Lyric Lines | 2 | 1 - 3 lines in the lyric panel. |

The same values can be edited by hand in `config/spotifyhud.json`; the file is
sanitised on load so out-of-range or missing values cannot crash the HUD.

---

## How it works

### Playback (Windows only)

`WindowsMediaDetector` starts one hidden `powershell.exe` helper that talks to
`Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager`, the
same API the Windows media overlay uses. It polls roughly three times per
second and prints one `US`-separated record per update. The process is started
lazily, restarted if it dies, stopped when auto detection is disabled, and
killed on client shutdown.

`PlaybackTracker` stores an immutable `TrackInfo` snapshot. While a track is
playing the current position is extrapolated from the last sample, so the
progress bar and lyrics stay smooth between polls. When the media session
disappears (Spotify closed) or auto detection is switched off, the overlay
falls back to its idle placeholder after a short grace period instead of
displaying a stale track.

### Album art

`AlbumArtService` queries `itunes.apple.com/search`, upgrades the returned
`100x100bb` URL to `600x600bb`, downloads the image off-thread, rounds the
corners with an anti-aliased alpha mask, and registers it as a dynamic
texture on the client thread. The texture uses bilinear filtering. A 24-entry
LRU cache plus a 30-second failure cool-down limits network traffic.

### Lyrics

`LyricsService` asks LRCLIB (`/api/get-cached`, `/api/get`, then `/api/search`
fallbacks) for synced lyrics only, parses standard and enhanced LRC, and caches
the result. Word timings are estimated from word length when the file has no
word-level tags, which drives the karaoke highlight. Lookups never block
rendering.

### Rendering

`SpotifyHudRenderer` lays the card out in base pixels and applies the configured
scale through the matrix stack, so all geometry stays resolution independent.
It is attached through Fabric API's `HudRenderCallback`; no mixins are used.
`HudIconTextures` generates the transport glyphs with Java2D once and draws
them as tinted textures, avoiding the jagged edges of primitive rectangles.

---

## Directory structure

```
spotify-hud/
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/wrapper/
│   ├── gradle-wrapper.jar
│   └── gradle-wrapper.properties        # Gradle 8.12
├── LICENSE
├── README.md
└── src/main/
    ├── resources/
    │   ├── fabric.mod.json
    │   └── assets/spotifyhud/
    │       ├── icon.png                    # Mod Menu / mods-list icon
    │       └── lang/en_us.json
    └── java/dev/spotifyhud/
        ├── SpotifyHudMod.java            # entrypoint, lifecycle, keybind
        ├── config/
        │   ├── SpotifyHudConfig.java     # serialisable settings
        │   ├── ConfigManager.java        # JSON load/save
        │   ├── HudAnchor.java
        │   └── HudTheme.java
        ├── playback/
        │   ├── TrackInfo.java            # immutable playback snapshot
        │   ├── PlaybackTracker.java
        │   └── WindowsMediaDetector.java # SMTC via PowerShell
        ├── art/
        │   └── AlbumArtService.java      # iTunes lookup + textures
        ├── lyrics/
        │   ├── LyricsWord.java
        │   ├── LyricsLine.java
        │   ├── LyricsDoc.java
        │   ├── LyricsParser.java
        │   ├── LrcLibClient.java
        │   └── LyricsService.java
        ├── hud/
        │   ├── SpotifyHudRenderer.java
        │   ├── HudBounds.java
        │   ├── HudIcons.java             # icon layout
        │   ├── HudIconTextures.java      # generated anti-aliased glyphs
        │   ├── RoundedRect.java
        │   └── ColorUtil.java
        ├── gui/
        │   ├── SpotifyHudConfigScreen.java
        │   ├── HudPositionScreen.java
        │   └── widget/OptionSlider.java
        └── integration/
            └── ModMenuIntegration.java
```

---

## Extraction notes

The original implementation lived in a single ~1,500-line
`SpotifyHUD` class that relied on the host client for almost everything. The
standalone version keeps the behaviour but replaces the host infrastructure:

| Original dependency | Replacement |
| --- | --- |
| `Module` / `ShowHUD` module registry | `SpotifyHudConfig` + `HudRenderCallback` |
| Custom `VertexTextureFont` fonts | vanilla `TextRenderer` |
| `CorzRoundedUtils`, shader rounded rects | `RoundedRect` / `DrawContext.fill` |
| `IcoGenUtils` icon textures (Java2D) | `HudIconTextures` (same Java2D approach, independent code) |
| Client config screens | `SpotifyHudConfigScreen` + Mod Menu |
| `MatrixPeekUtils*` helpers | `MatrixStack` push/scale/pop |
| Host HTTP/cache helpers | `HttpClient` + `ConcurrentHashMap` caches |

No source file in `src/` mentions the original client by name.

---

## Platform and privacy

- Auto detection is **Windows 10/11 only**, because it uses Windows SMTC.
  macOS and Linux are not supported; the HUD remains idle there.
- The mod never asks for Spotify credentials and does not use the Spotify Web
  API. It only reads track metadata that Windows already exposes locally.
- Network requests go to `itunes.apple.com` (cover search) and `lrclib.net`
  (synced lyrics). Track artist/title/duration is sent in those requests, as is
  necessary to look up the song. No telemetry is collected.
- Failed lookups are cached briefly, and artwork/lyrics caches are bounded, so
  the mod does not hammer either service while a song repeats.





  (some elements are made by AI)


## License

MIT. See [LICENSE](LICENSE).
