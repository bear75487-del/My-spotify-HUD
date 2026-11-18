package dev.spotifyhud.config;

import java.util.Locale;

/**
 * The four screen corners the HUD can be anchored to. Offsets are measured
 * from the chosen corner so the overlay stays put when the window is resized.
 */
public enum HudAnchor {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT;

    public boolean isRight() {
        return this == TOP_RIGHT || this == BOTTOM_RIGHT;
    }

    public boolean isBottom() {
        return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
    }

    public HudAnchor next() {
        HudAnchor[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public String translationKey() {
        return "spotifyhud.anchor." + name().toLowerCase(Locale.ROOT);
    }
}
