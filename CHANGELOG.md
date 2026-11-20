# Changelog

All notable changes to Spotify HUD are documented here.

## 2.0.0 — Minecraft 1.21.11

The 1.21.11 port plus a full visual customization pass.

### Added
- **Minecraft 1.21.11 support** — Yarn `1.21.11+build.6`, Fabric Loader 0.20.1,
  Fabric API 0.141.6 and an optional Mod Menu 17 integration.
- **HSV color picker** — click any color in the new editor to open a
  saturation/value square with a hue strip, hex field, live preview and a
  "Use Theme" button.
- **Tabbed color editor** — an *Elements* tab with a swatch for every part of
  the HUD, and a *Presets* tab for whole looks.
- **Preset maker** — saves the current colors, layout, opacity, radius and
  section toggles under a name; saving again with the same name overwrites it,
  and presets can be deleted from the list.
- **Built-in presets based on the reference designs**:
  - *Compact* — one-line bar with cover, inline title/artist and remaining time.
  - *Vanilla Tooltip* — dark violet panel with stacked text, elapsed/total time
    and an equalizer.
  - *Hotbar* — minimal strip with equalizer, title, artist and time.
- **Four layouts** (`Card`, `Compact`, `Tooltip`, `Hotbar`) selectable from the
  main config screen.
- **Animated equalizer** — five bars driven by two out-of-phase waves; overlays
  the album art in card layout and sits beside the text in the other layouts.
  It settles into a low static pattern when playback is paused.
- **Wrapped lyrics** — long lines break greedily onto extra rows instead of
  ending in `...`; the lyric panel grows to fit the rows.
- **Line transitions** — the lyric block fades in and slides slightly whenever
  the highlighted line changes (260 ms, smoothstep).
- **Word transitions** — inside the active line, every word eases from the
  inactive color to the accent color and rises two pixels as it starts.

### Changed
- The config screen's bottom row now has four buttons: *Move HUD...*,
  *Colors...*, *Reset Defaults*, *Done*.
- Colors are resolved through `HudColors`; an override replaces the element's
  RGB but keeps the theme alpha, so translucent parts stay translucent.
- `equalizerColor`, `layout` and the user `presets` array are stored in
  `config/spotifyhud.json`; empty colors mean "use the theme".
- Album art now uses `LinearFilteredTexture`, a small bilinear-sampling texture
  patch, because 1.21.11 removed `Texture.setFilter`.
- The HUD height is computed per layout (and per wrapped lyric row count) so
  the card never reserves empty space.

### Fixed
- **`IllegalStateException: Can only blur once per frame`** when opening the
  Move HUD screen — the screen no longer calls `renderBackground` a second time.
- Long lyric lines were truncated with `...`; they now wrap.
- Malformed or hand-edited color values no longer reach the renderer; they are
  sanitized on load and fall back to the theme.
- *Reset Defaults* resets the look but keeps user-created presets.

### Compatibility
- The Minecraft 1.21.1 build stays at **1.0.0** on the `mc1.21.1` branch and the
  [1.21.1 release](https://github.com/bear75487-del/My-spotify-HUD/releases/tag/1.21.1).

## 1.0.0 — Minecraft 1.21.1

First public release. Now-playing card with album art, title/artist, progress
bar, synced word-level lyrics, transport icons, four themes, drag-to-position
editor and Mod Menu integration.
