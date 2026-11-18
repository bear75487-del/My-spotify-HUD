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
    public int cornerRadius = 8;
    public float backgroundOpacity = 0.88F;

    // Position / size
    public HudAnchor anchor = HudAnchor.TOP_LEFT;
    public int offsetX = 12;
    public int offsetY = 12;
    public float scale = 1.0F;
    public int cardWidth = 220;
    public int lyricLines = 2;

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

        this.scale = clamp(this.scale, 0.5F, 2.0F);
        this.cardWidth = clamp(this.cardWidth, 160, 360);
        this.cornerRadius = clamp(this.cornerRadius, 0, 16);
        this.backgroundOpacity = clamp(this.backgroundOpacity, 0.15F, 1.0F);
        this.lyricLines = clamp(this.lyricLines, 1, 3);
        this.offsetX = Math.max(0, this.offsetX);
        this.offsetY = Math.max(0, this.offsetY);
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
        this.cornerRadius = defaults.cornerRadius;
        this.backgroundOpacity = defaults.backgroundOpacity;
        this.anchor = defaults.anchor;
        this.offsetX = defaults.offsetX;
        this.offsetY = defaults.offsetY;
        this.scale = defaults.scale;
        this.cardWidth = defaults.cardWidth;
        this.lyricLines = defaults.lyricLines;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
