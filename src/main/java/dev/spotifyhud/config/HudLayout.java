package dev.spotifyhud.config;

import java.util.Locale;

/** Overall shape of the HUD, mostly used by the built-in presets. */
public enum HudLayout {
    /** Album art, progress bar and lyrics in a rounded card. */
    CARD,
    /** One-line bar: art, title, artist and remaining time. */
    COMPACT,
    /** Vanilla-tooltip look: art, stacked text, time and an equalizer. */
    TOOLTIP,
    /** Minimal strip: equalizer, title, artist and time. */
    HOTBAR;

    public String translationKey() {
        return "spotifyhud.layout." + name().toLowerCase(Locale.ROOT);
    }
}
