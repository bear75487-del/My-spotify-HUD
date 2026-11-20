package dev.spotifyhud.config;

/**
 * Plain serialisable configuration. Gson fills these fields directly, so the
 * class intentionally has no dependency on Minecraft.
 */
public final class SpotifyHudConfig {
    // General
    public boolean enabled = true;
    public boolean autoDetect = true;

    // Content
    public boolean showAlbumArt = true;
    public boolean showProgressBar = true;
    public boolean showLyrics = true;
    public boolean showControls = true;

    // Styling
    public boolean shadow = true;
    public HudTheme theme = HudTheme.SPOTIFY_DARK;
    public HudLayout layout = HudLayout.CARD;
    public boolean showEqualizer = true;
    public int cornerRadius = 8;
    public float backgroundOpacity = 0.88F;

    // Per-element colour overrides ("#RRGGBB"); empty means "use the theme".
    public String titleColor = "";
    public String subtitleColor = "";
    public String lyricActiveColor = "";
    public String lyricInactiveColor = "";
    public String progressFillColor = "";
    public String progressTrackColor = "";
    public String iconColor = "";
    public String backgroundColor = "";
    public String dividerColor = "";
    public String equalizerColor = "";

    // Position / size
    public HudAnchor anchor = HudAnchor.TOP_LEFT;
    public int offsetX = 12;
    public int offsetY = 12;
    public float scale = 1.0F;
    public int cardWidth = 220;
    public int lyricLines = 2;

    // User created looks (see HudPresetData); built-in presets live in code.
    public java.util.List<HudPresetData> presets = new java.util.ArrayList<>();

    /**
     * Clamps every value into a range the renderer can safely handle. Also
     * repairs nulls produced by hand-edited or older config files.
     */
    public void sanitize() {
        if (this.theme == null) {
            this.theme = HudTheme.SPOTIFY_DARK;
        }
        if (this.anchor == null) {
            this.anchor = HudAnchor.TOP_LEFT;
        }
        if (this.layout == null) {
            this.layout = HudLayout.CARD;
        }
        if (this.presets == null) {
            this.presets = new java.util.ArrayList<>();
        }

        this.scale = clamp(this.scale, 0.5F, 2.0F);
        this.cardWidth = clamp(this.cardWidth, 160, 360);
        this.cornerRadius = clamp(this.cornerRadius, 0, 16);
        this.backgroundOpacity = clamp(this.backgroundOpacity, 0.15F, 1.0F);
        this.lyricLines = clamp(this.lyricLines, 1, 3);
        this.offsetX = Math.max(0, this.offsetX);
        this.offsetY = Math.max(0, this.offsetY);

        this.titleColor = sanitizeColor(this.titleColor);
        this.subtitleColor = sanitizeColor(this.subtitleColor);
        this.lyricActiveColor = sanitizeColor(this.lyricActiveColor);
        this.lyricInactiveColor = sanitizeColor(this.lyricInactiveColor);
        this.progressFillColor = sanitizeColor(this.progressFillColor);
        this.progressTrackColor = sanitizeColor(this.progressTrackColor);
        this.iconColor = sanitizeColor(this.iconColor);
        this.backgroundColor = sanitizeColor(this.backgroundColor);
        this.dividerColor = sanitizeColor(this.dividerColor);
        this.equalizerColor = sanitizeColor(this.equalizerColor);

        // Drop broken entries and cap the list so the JSON stays small.
        this.presets.removeIf(java.util.Objects::isNull);
        for (HudPresetData preset : this.presets) {
            preset.sanitize();
        }
        if (this.presets.size() > 24) {
            this.presets.subList(24, this.presets.size()).clear();
        }
    }

    /**
     * Restores the factory defaults while keeping the same instance so any
     * screen or listener holding a reference sees the change immediately.
     */
    public void resetToDefaults() {
        SpotifyHudConfig defaults = new SpotifyHudConfig();
        this.enabled = defaults.enabled;
        this.autoDetect = defaults.autoDetect;
        this.showAlbumArt = defaults.showAlbumArt;
        this.showProgressBar = defaults.showProgressBar;
        this.showLyrics = defaults.showLyrics;
        this.showControls = defaults.showControls;
        this.shadow = defaults.shadow;
        this.theme = defaults.theme;
        this.layout = defaults.layout;
        this.showEqualizer = defaults.showEqualizer;
        this.cornerRadius = defaults.cornerRadius;
        this.backgroundOpacity = defaults.backgroundOpacity;
        this.anchor = defaults.anchor;
        this.offsetX = defaults.offsetX;
        this.offsetY = defaults.offsetY;
        this.scale = defaults.scale;
        this.cardWidth = defaults.cardWidth;
        this.lyricLines = defaults.lyricLines;
        this.titleColor = defaults.titleColor;
        this.subtitleColor = defaults.subtitleColor;
        this.lyricActiveColor = defaults.lyricActiveColor;
        this.lyricInactiveColor = defaults.lyricInactiveColor;
        this.progressFillColor = defaults.progressFillColor;
        this.progressTrackColor = defaults.progressTrackColor;
        this.iconColor = defaults.iconColor;
        this.backgroundColor = defaults.backgroundColor;
        this.dividerColor = defaults.dividerColor;
        this.equalizerColor = defaults.equalizerColor;
        // Saved presets are user content, so a reset keeps them.
    }

    private static String sanitizeColor(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return HudColors.isValid(value) ? HudColors.format(HudColors.parseRgb(value)) : "";
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
