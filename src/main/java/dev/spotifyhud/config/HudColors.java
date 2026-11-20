package dev.spotifyhud.config;

/**
 * Resolves the effective HUD palette.
 *
 * <p>Every element falls back to the selected {@link HudTheme}; a non-empty
 * hex override in {@link SpotifyHudConfig} replaces that element's RGB while
 * keeping the theme's alpha channel (so translucent elements such as the card
 * background and the divider stay translucent).</p>
 */
public final class HudColors {
    private HudColors() {
    }

    public static int background(SpotifyHudConfig config) {
        return resolve(config.backgroundColor, config.theme.background());
    }

    public static int title(SpotifyHudConfig config) {
        return resolve(config.titleColor, config.theme.title());
    }

    public static int subtitle(SpotifyHudConfig config) {
        return resolve(config.subtitleColor, config.theme.subtitle());
    }

    public static int lyricActive(SpotifyHudConfig config) {
        return resolve(config.lyricActiveColor, config.theme.accent());
    }

    public static int lyricInactive(SpotifyHudConfig config) {
        return resolve(config.lyricInactiveColor, config.theme.lyricInactive());
    }

    public static int progressFill(SpotifyHudConfig config) {
        return resolve(config.progressFillColor, config.theme.accent());
    }

    public static int progressTrack(SpotifyHudConfig config) {
        return resolve(config.progressTrackColor, config.theme.divider());
    }

    public static int icon(SpotifyHudConfig config) {
        return resolve(config.iconColor, config.theme.title());
    }

    public static int iconSecondary(SpotifyHudConfig config) {
        return resolve(config.iconColor, config.theme.subtitle());
    }

    public static int divider(SpotifyHudConfig config) {
        return resolve(config.dividerColor, config.theme.divider());
    }

    public static int equalizer(SpotifyHudConfig config) {
        return resolve(config.equalizerColor, config.theme.accent());
    }

    /**
     * Applies an override to a fallback colour, preserving the fallback's
     * alpha. Returns the fallback when the override is null/invalid.
     */
    public static int resolve(String override, int fallback) {
        int rgb = parseRgb(override);
        if (rgb < 0) {
            return fallback;
        }
        return (fallback & 0xFF000000) | rgb;
    }

    /** @return the 0xRRGGBB value, or -1 when the string is not six hex digits. */
    public static int parseRgb(String value) {
        if (value == null) {
            return -1;
        }

        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return -1;
        }

        try {
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    public static boolean isValid(String value) {
        return parseRgb(value) >= 0;
    }

    /** Normalises a hex string, or returns an empty string when invalid. */
    public static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int rgb = parseRgb(value);
        return rgb < 0 ? "" : format(rgb);
    }

    public static String format(int argb) {
        return String.format("#%06X", argb & 0xFFFFFF);
    }
}
