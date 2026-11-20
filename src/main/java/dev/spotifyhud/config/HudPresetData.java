package dev.spotifyhud.config;

/**
 * A saved "look": layout, colors and the toggles that change the card's shape.
 * Gson fills these fields directly, so the class stays Minecraft-free.
 */
public final class HudPresetData {
    public String name = "Preset";
    public HudLayout layout = HudLayout.CARD;

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

    public int cornerRadius = 8;
    public float backgroundOpacity = 0.88F;
    public boolean shadow = true;
    public boolean showAlbumArt = true;
    public boolean showLyrics = true;
    public boolean showProgressBar = true;
    public boolean showControls = true;
    public boolean showEqualizer = true;

    public HudPresetData() {
    }

    /** Snapshot of the current look under a user supplied name. */
    public static HudPresetData capture(SpotifyHudConfig config, String name) {
        HudPresetData preset = new HudPresetData();
        preset.name = name;
        preset.layout = config.layout;

        preset.titleColor = config.titleColor;
        preset.subtitleColor = config.subtitleColor;
        preset.lyricActiveColor = config.lyricActiveColor;
        preset.lyricInactiveColor = config.lyricInactiveColor;
        preset.progressFillColor = config.progressFillColor;
        preset.progressTrackColor = config.progressTrackColor;
        preset.iconColor = config.iconColor;
        preset.backgroundColor = config.backgroundColor;
        preset.dividerColor = config.dividerColor;
        preset.equalizerColor = config.equalizerColor;

        preset.cornerRadius = config.cornerRadius;
        preset.backgroundOpacity = config.backgroundOpacity;
        preset.shadow = config.shadow;
        preset.showAlbumArt = config.showAlbumArt;
        preset.showLyrics = config.showLyrics;
        preset.showProgressBar = config.showProgressBar;
        preset.showControls = config.showControls;
        preset.showEqualizer = config.showEqualizer;
        return preset;
    }

    public void applyTo(SpotifyHudConfig config) {
        config.layout = this.layout == null ? HudLayout.CARD : this.layout;

        config.titleColor = HudColors.sanitize(this.titleColor);
        config.subtitleColor = HudColors.sanitize(this.subtitleColor);
        config.lyricActiveColor = HudColors.sanitize(this.lyricActiveColor);
        config.lyricInactiveColor = HudColors.sanitize(this.lyricInactiveColor);
        config.progressFillColor = HudColors.sanitize(this.progressFillColor);
        config.progressTrackColor = HudColors.sanitize(this.progressTrackColor);
        config.iconColor = HudColors.sanitize(this.iconColor);
        config.backgroundColor = HudColors.sanitize(this.backgroundColor);
        config.dividerColor = HudColors.sanitize(this.dividerColor);
        config.equalizerColor = HudColors.sanitize(this.equalizerColor);

        config.cornerRadius = this.cornerRadius;
        config.backgroundOpacity = this.backgroundOpacity;
        config.shadow = this.shadow;
        config.showAlbumArt = this.showAlbumArt;
        config.showLyrics = this.showLyrics;
        config.showProgressBar = this.showProgressBar;
        config.showControls = this.showControls;
        config.showEqualizer = this.showEqualizer;
    }

    public void sanitize() {
        if (this.name == null) {
            this.name = "";
        }
        this.name = this.name.trim();
        if (this.name.length() > 24) {
            this.name = this.name.substring(0, 24);
        }

        if (this.layout == null) {
            this.layout = HudLayout.CARD;
        }

        this.titleColor = HudColors.sanitize(this.titleColor);
        this.subtitleColor = HudColors.sanitize(this.subtitleColor);
        this.lyricActiveColor = HudColors.sanitize(this.lyricActiveColor);
        this.lyricInactiveColor = HudColors.sanitize(this.lyricInactiveColor);
        this.progressFillColor = HudColors.sanitize(this.progressFillColor);
        this.progressTrackColor = HudColors.sanitize(this.progressTrackColor);
        this.iconColor = HudColors.sanitize(this.iconColor);
        this.backgroundColor = HudColors.sanitize(this.backgroundColor);
        this.dividerColor = HudColors.sanitize(this.dividerColor);
        this.equalizerColor = HudColors.sanitize(this.equalizerColor);

        this.cornerRadius = Math.max(0, Math.min(16, this.cornerRadius));
        this.backgroundOpacity = Math.max(0.15F, Math.min(1.0F, this.backgroundOpacity));
    }

    /** Built-in presets derived from the reference designs. */
    public static final class BuiltIn {
        private BuiltIn() {
        }

        public static java.util.List<HudPresetData> all() {
            return java.util.List.of(compact(), tooltip(), hotbar());
        }

        /** Reference: compact.png — one line, blue-grey text, remaining time. */
        public static HudPresetData compact() {
            HudPresetData preset = new HudPresetData();
            preset.name = "spotifyhud.preset.compact";
            preset.layout = HudLayout.COMPACT;
            preset.backgroundColor = "#0B1622";
            preset.titleColor = "#A8C6E8";
            preset.subtitleColor = "#8A9DAF";
            preset.lyricActiveColor = "#4E9BE8";
            preset.lyricInactiveColor = "#5E6B7A";
            preset.progressFillColor = "#3E8BDB";
            preset.progressTrackColor = "#1D2C3C";
            preset.iconColor = "#A8C6E8";
            preset.dividerColor = "#1E2E3E";
            preset.equalizerColor = "#7FB6F0";
            preset.cornerRadius = 6;
            preset.backgroundOpacity = 0.92F;
            preset.shadow = true;
            preset.showAlbumArt = true;
            preset.showLyrics = false;
            preset.showProgressBar = true;
            preset.showControls = false;
            preset.showEqualizer = false;
            return preset;
        }

        /** Reference: vanilla tooltip.png — dark violet panel, equalizer. */
        public static HudPresetData tooltip() {
            HudPresetData preset = new HudPresetData();
            preset.name = "spotifyhud.preset.tooltip";
            preset.layout = HudLayout.TOOLTIP;
            preset.backgroundColor = "#170F1E";
            preset.titleColor = "#FFFFFF";
            preset.subtitleColor = "#A7A7A7";
            preset.lyricActiveColor = "#C9A6FF";
            preset.lyricInactiveColor = "#6E6E6E";
            preset.progressFillColor = "#C9A6FF";
            preset.progressTrackColor = "#3A2A4A";
            preset.iconColor = "#FFFFFF";
            preset.dividerColor = "#3A2A4A";
            preset.equalizerColor = "#D8D8D8";
            preset.cornerRadius = 3;
            preset.backgroundOpacity = 0.94F;
            preset.shadow = false;
            preset.showAlbumArt = true;
            preset.showLyrics = false;
            preset.showProgressBar = false;
            preset.showControls = false;
            preset.showEqualizer = true;
            return preset;
        }

        /** Reference: hotbar.png — minimal strip with an equalizer. */
        public static HudPresetData hotbar() {
            HudPresetData preset = new HudPresetData();
            preset.name = "spotifyhud.preset.hotbar";
            preset.layout = HudLayout.HOTBAR;
            preset.backgroundColor = "#0A0A0A";
            preset.titleColor = "#E6E6E6";
            preset.subtitleColor = "#9A9A9A";
            preset.lyricActiveColor = "#FFFFFF";
            preset.lyricInactiveColor = "#6E6E6E";
            preset.progressFillColor = "#BFBFBF";
            preset.progressTrackColor = "#2B2B2B";
            preset.iconColor = "#E6E6E6";
            preset.dividerColor = "#2B2B2B";
            preset.equalizerColor = "#BFBFBF";
            preset.cornerRadius = 2;
            preset.backgroundOpacity = 0.85F;
            preset.shadow = false;
            preset.showAlbumArt = false;
            preset.showLyrics = false;
            preset.showProgressBar = false;
            preset.showControls = false;
            preset.showEqualizer = true;
            return preset;
        }
    }
}
