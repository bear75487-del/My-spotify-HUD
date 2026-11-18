package dev.spotifyhud.config;

import java.util.Locale;

/**
 * Visual palettes for the HUD. Keeping the colours in one enum makes the
 * renderer independent from the configuration screen and makes adding a new
 * theme a one-line change.
 */
public enum HudTheme {
    SPOTIFY_DARK(
            0xF0121212, // card background
            0xFF1DB954, // accent / progress / active lyric
            0xFFF5F5F5, // title
            0xFFB3B3B3, // artist / time labels
            0xFF707070, // inactive lyrics
            0x33FFFFFF  // divider
    ),
    MIDNIGHT(
            0xF00B1220,
            0xFF5B8CFF,
            0xFFF2F5FF,
            0xFFA8B3CC,
            0xFF66718A,
            0x334A67B5
    ),
    LIGHT(
            0xF2F4F4F4,
            0xFF1DB954,
            0xFF161616,
            0xFF4B4B4B,
            0xFF8E8E8E,
            0x22000000
    ),
    MINIMAL(
            0xB8000000,
            0xFFFFFFFF,
            0xFFFFFFFF,
            0xFFBBBBBB,
            0xFF777777,
            0x33FFFFFF
    );

    private final int background;
    private final int accent;
    private final int title;
    private final int subtitle;
    private final int lyricInactive;
    private final int divider;

    HudTheme(int background, int accent, int title, int subtitle, int lyricInactive, int divider) {
        this.background = background;
        this.accent = accent;
        this.title = title;
        this.subtitle = subtitle;
        this.lyricInactive = lyricInactive;
        this.divider = divider;
    }

    public int background() {
        return this.background;
    }

    public int accent() {
        return this.accent;
    }

    public int title() {
        return this.title;
    }

    public int subtitle() {
        return this.subtitle;
    }

    public int lyricInactive() {
        return this.lyricInactive;
    }

    public int divider() {
        return this.divider;
    }

    public HudTheme next() {
        HudTheme[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String translationKey() {
        return "spotifyhud.theme." + name().toLowerCase(Locale.ROOT);
    }
}
