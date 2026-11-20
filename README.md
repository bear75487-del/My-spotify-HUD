# Spotify HUD

A standalone, client-side Minecraft **1.21.11 Fabric** mod that shows the track
currently playing in the Spotify desktop app as a compact on-screen overlay.

> Looking for the Minecraft 1.21.1 build? It lives on the
> [`mc1.21.1`](https://github.com/bear75487-del/My-spotify-HUD/tree/mc1.21.1)
> branch and in the [1.21.1 release](https://github.com/bear75487-del/My-spotify-HUD/releases/tag/1.21.1).

| Release | Minecraft | Mod version | Branch |
| --- | --- | --- | --- |
| [2.0.0](https://github.com/bear75487-del/My-spotify-HUD/releases/tag/2.0.0) | 1.21.11 | 2.0.0 | `main` |
| [1.21.1](https://github.com/bear75487-del/My-spotify-HUD/releases/tag/1.21.1) | 1.21.1 | 1.0.0 | `mc1.21.1` |

The feature was extracted from a larger client and rebuilt from scratch: no
Corz client classes, modules, fonts, commands, or shaders are referenced. The
only shared ideas are the playback source (Windows system media sessions), the
iTunes artwork lookup, and the LRCLIB lyrics lookup, all re-implemented here.

---

## Features

- **Now playing card** with album art, title, artist, and a live progress bar.
- **Synced LRC lyrics** (word-level karaoke highlighting when LRCLIB provides
  enhanced timestamps), with a smooth fade/slide on every line change.
- **Word-by-word transitions**: each word fades in, rises slightly, and blends
  from the inactive color to the accent as it is sung.
- **Wrapped lyrics**: long lines break onto extra rows instead of ending in
  `...`, and the card grows to fit them.
- **Animated equalizer** that reacts while playback is running and settles when
  paused.
- **Four layouts**: the original rounded card, a one-line compact bar, a
  vanilla-tooltip panel, and a minimal hotbar strip.
- **Custom colors** with a real HSV picker (saturation/value square plus hue
  strip): title, subtitle, active/inactive lyrics, progress fill and track,
  icons, background, divider, and equalizer.
- **Presets**: built-in looks based on the reference designs (Compact, Vanilla
  Tooltip, Hotbar) plus a preset maker that saves the current colors, layout and
  toggles under your own name.
- **Album art** resolved from the public iTunes Search API, downloaded
  asynchronously, rounded and bilinear-filtered so it stays smooth.
- **Anti-aliased transport icons** (previous / play / pause / next) generated
  at 64x64 and scaled down, matching the original overlay's look.
- **Automatic Spotify detection** through the Windows 10/11 system media
  transport controls (SMTC).
- **Four themes**, adjustable scale, width, corner radius, opacity, lyric line
  count, anchor, per-element color overrides, and individual toggles for every
  HUD section.
- **Mod Menu integration** plus a **drag-to-position** editor.
- All settings persisted to `config/spotifyhud.json` and applied instantly.

---

## Requirements

| Component | Version |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | 0.20.1+ |
| Fabric API | any 1.21.11 build |
| Mod Menu | 17.0.0+ (optional) |
| Java | 21+ |
| OS for auto detection | Windows 10 or 11 |

Mod Menu is optional: when it is installed the settings screen is exposed
through it, otherwise the keybind still opens the screen. On non-Windows
systems the mod loads and the settings screen still works, but the overlay
stays on its idle "Nothing playing" state.

---

## Build

A JDK 25 is required to run Gradle (the wrapper uses Gradle 9.8.2 with Fabric
Loom 1.19.3); the mod itself is compiled for Java 21.

```powershell
# From the spotify-hud directory
.\gradlew.bat build
```

The remapped mod jar is written to:

```
build/libs/spotify-hud-2.0.0.jar
```

Copy it together with Fabric API into your `.minecraft/mods` folder (Mod Menu
is optional).

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
- **Colors...** opens the color editor. **Elements** lists every HUD color; click
  a swatch button to open the HSV picker, or use **Use Theme** to clear the
  override. **Presets** applies the built-in looks from the reference designs
  and can save the current colors + layout + toggles as a named preset.

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
| Equalizer | on | Animated bars on the cover (card) or beside the text. |
| Anchor | Top Left | Corner the offsets are measured from. |
| Layout | Card | Card, Compact, Tooltip, or Hotbar. |
| Theme | Spotify Dark | Spotify Dark, Midnight, Light, or Minimal. |
| Scale | 1.00x | 0.50x - 2.00x. |
| Card Width | 220 px | 160 - 360 base pixels. |
| Corner Radius | 8 px | 0 - 16 base pixels. |
| Background | 88% | Card opacity, 15% - 100%. |
| Lyric Lines | 2 | 1 - 3 visible lyric lines (wrapped rows may add more). |
| Colors | theme | Optional `#RRGGBB` overrides for every element. |

The same values can be edited by hand in `config/spotifyhud.json`; the file is
sanitised on load so out-of-range, malformed, or missing values cannot crash
the HUD. Color overrides live in the same file as `titleColor`,
`subtitleColor`, `lyricActiveColor`, `lyricInactiveColor`, `progressFillColor`,
`progressTrackColor`, `iconColor`, `backgroundColor`, `dividerColor`, and
`equalizerColor`; an empty string means "use the theme". The same file also
stores `layout` (`CARD`, `COMPACT`, `TOOLTIP`, `HOTBAR`) and the array of
user-created presets under `presets`.

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

`SpotifyHudRenderer` lays the HUD out in base pixels and applies the configured
scale through the matrix stack, so all geometry stays resolution independent.
It is attached through Fabric API's `HudRenderCallback`; no mixins are used.
It draws one of four layouts, sharing the same color resolver and text fitting
helpers. Colors are resolved by `HudColors`, which merges a per-element hex
override with the active theme (keeping the theme alpha so translucent parts
stay translucent). Lyric lines are greedily word-wrapped to the card width and
the panel height follows the resulting row count. When the highlighted line
changes the block fades in and slides a few pixels into place over 260 ms, and
inside the active line every word eases from the inactive to the accent color,
rising two pixels as it starts. `HudIconTextures` generates the transport glyphs
with Java2D once and draws them as tinted textures, avoiding the jagged edges of
primitive rectangles; `EqualizerBars` animates five bars from two out-of-phase
sine waves.

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
│   └── gradle-wrapper.properties        # Gradle 9.8.2
├── LICENSE
├── CHANGELOG.md
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
        │   ├── HudColors.java            # per-element color resolution
        │   ├── HudLayout.java            # card / compact / tooltip / hotbar
        │   ├── HudPresetData.java        # saved look + built-in presets
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
        │   ├── LinearFilteredTexture.java # bilinear circle-texture patch
        │   ├── EqualizerBars.java        # animated equalizer
        │   ├── RoundedRect.java
        │   └── ColorUtil.java
        ├── gui/
        │   ├── SpotifyHudConfigScreen.java
        │   ├── HudColorsScreen.java      # elements + presets tabs
        │   ├── ColorPickerScreen.java    # HSV color picker
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

## License

MIT. See [LICENSE](LICENSE).
