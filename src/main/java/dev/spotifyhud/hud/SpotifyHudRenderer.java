package dev.spotifyhud.hud;

import dev.spotifyhud.art.AlbumArtService;
import dev.spotifyhud.config.HudColors;
import dev.spotifyhud.config.HudLayout;
import dev.spotifyhud.config.HudTheme;
import dev.spotifyhud.config.SpotifyHudConfig;
import dev.spotifyhud.lyrics.LyricsDoc;
import dev.spotifyhud.lyrics.LyricsLine;
import dev.spotifyhud.lyrics.LyricsService;
import dev.spotifyhud.lyrics.LyricsWord;
import dev.spotifyhud.playback.TrackInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Renderer for the overlay.
 *
 * <p>All geometry is expressed in base pixels and then scaled through the
 * {@link Matrix3x2fStack}, so the layout code does not have to know about the
 * configured HUD scale. Four layouts are supported: the original rounded
 * {@code CARD}, a one-line {@code COMPACT} bar, a vanilla-tooltip style
 * {@code TOOLTIP} panel and a minimal {@code HOTBAR} strip.</p>
 */
public final class SpotifyHudRenderer {
    private static final int PAD = 8;
    private static final int ART_SIZE = 44;
    private static final int ART_GAP = 8;
    private static final int PROGRESS_HEIGHT = 18;
    private static final int LYRIC_ROW_HEIGHT = 11;
    private static final int CONTROL_AREA = 44;
    private static final int CONTROL_PLAY_SIZE = 13;
    private static final long LYRICS_LOOKAHEAD_MS = 120L;
    private static final long LYRIC_TRANSITION_MS = 260L;

    private final SpotifyHudConfig config;
    private final LyricsService lyricsService;
    private final AlbumArtService albumArtService;

    private String lastLyricsKey;
    private int lastActiveLine = Integer.MIN_VALUE;
    private long lyricChangedAtMs;

    public SpotifyHudRenderer(SpotifyHudConfig config, LyricsService lyricsService, AlbumArtService albumArtService) {
        this.config = config;
        this.lyricsService = lyricsService;
        this.albumArtService = albumArtService;
    }

    public int baseWidth() {
        return this.config.cardWidth;
    }

    public boolean hasLyrics(TrackInfo track) {
        return this.config.layout == HudLayout.CARD
                && this.config.showLyrics
                && this.lyricsService.docFor(track) != null;
    }

    public boolean hasProgress(TrackInfo track) {
        return this.config.layout == HudLayout.CARD
                && this.config.showProgressBar
                && track != null
                && track.hasDuration();
    }

    /** Height of the card before the HUD scale is applied. */
    public int baseHeight(MinecraftClient client, TrackInfo track) {
        TrackInfo safeTrack = track == null ? TrackInfo.EMPTY : track;

        return switch (this.config.layout) {
            case COMPACT -> 34;
            case HOTBAR -> 22;
            case TOOLTIP -> 46;
            case CARD -> {
                LyricsDoc doc = this.hasLyrics(safeTrack) ? this.lyricsService.docFor(safeTrack) : null;
                int rows = this.lyricRows(client.textRenderer, safeTrack, doc);
                yield this.contentHeight(this.hasProgress(safeTrack), rows);
            }
        };
    }

    /**
     * Resolves the on-screen rectangle from the configured anchor, offsets and
     * scale. At least a few pixels of the card are kept visible if an offset
     * is out of range.
     */
    public HudBounds bounds(MinecraftClient client, TrackInfo track, boolean hasLyrics) {
        TrackInfo safeTrack = track == null ? TrackInfo.EMPTY : track;

        int width = Math.max(1, Math.round(this.baseWidth() * this.config.scale));
        int height = Math.max(1, Math.round(this.baseHeight(client, safeTrack) * this.config.scale));
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        int x = this.config.anchor.isRight()
                ? screenWidth - this.config.offsetX - width
                : this.config.offsetX;
        int y = this.config.anchor.isBottom()
                ? screenHeight - this.config.offsetY - height
                : this.config.offsetY;

        x = Math.max(-width + 16, Math.min(screenWidth - 16, x));
        y = Math.max(-height + 16, Math.min(screenHeight - 16, y));

        return new HudBounds(x, y, width, height);
    }

    public void render(DrawContext context, MinecraftClient client, TrackInfo track) {
        TrackInfo safeTrack = track == null ? TrackInfo.EMPTY : track;
        HudBounds bounds = this.bounds(client, safeTrack, this.hasLyrics(safeTrack));

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(bounds.x(), bounds.y());
        matrices.scale(this.config.scale, this.config.scale);

        try {
            this.draw(context, client, safeTrack);
        } finally {
            matrices.popMatrix();
        }
    }

    private void draw(DrawContext context, MinecraftClient client, TrackInfo track) {
        switch (this.config.layout) {
            case COMPACT -> this.drawCompact(context, client.textRenderer, track);
            case TOOLTIP -> this.drawTooltip(context, client.textRenderer, track);
            case HOTBAR -> this.drawHotbar(context, client.textRenderer, track);
            case CARD -> this.drawCard(context, client, track);
        }
    }

    // ------------------------------------------------------------------
    // Card layout (original design)
    // ------------------------------------------------------------------

    private void drawCard(DrawContext context, MinecraftClient client, TrackInfo track) {
        LyricsDoc doc = this.hasLyrics(track) ? this.lyricsService.docFor(track) : null;
        HudTheme theme = this.config.theme;
        TextRenderer textRenderer = client.textRenderer;

        int width = this.baseWidth();
        int rows = this.lyricRows(textRenderer, track, doc);
        int height = this.contentHeight(this.hasProgress(track), rows);
        int background = ColorUtil.withOpacity(HudColors.background(this.config), this.config.backgroundOpacity);

        if (this.config.shadow) {
            RoundedRect.fill(context, 2, 3, width, height, this.config.cornerRadius, 0x55000000);
        }

        RoundedRect.fill(context, 0, 0, width, height, this.config.cornerRadius, background);

        this.drawHeader(context, textRenderer, track, theme, width);

        int sectionY = this.headerHeight();
        if (this.hasProgress(track)) {
            this.drawProgress(context, textRenderer, track, width, sectionY);
            sectionY += PROGRESS_HEIGHT;
        }

        if (doc != null) {
            this.drawLyrics(context, textRenderer, track, doc, width, sectionY);
        }
    }

    private int contentHeight(boolean hasProgress, int lyricRows) {
        int height = this.headerHeight();
        if (hasProgress) {
            height += PROGRESS_HEIGHT;
        }
        if (lyricRows > 0) {
            height += this.lyricPanelHeight(lyricRows);
        }
        return height;
    }

    private int headerHeight() {
        int content = this.config.showAlbumArt ? ART_SIZE : 22;
        return PAD * 2 + content;
    }

    private int lyricPanelHeight(int rows) {
        return 6 + rows * LYRIC_ROW_HEIGHT + 5;
    }

    /**
     * Number of display rows the current lyric window needs. Long lines wrap
     * onto extra rows instead of being truncated.
     */
    private int lyricRows(TextRenderer textRenderer, TrackInfo track, LyricsDoc doc) {
        if (doc == null || doc.isEmpty()) {
            return 0;
        }

        long positionMs = track.elapsedMs(System.currentTimeMillis()) + LYRICS_LOOKAHEAD_MS;
        return this.layoutRows(textRenderer, doc, positionMs, this.baseWidth() - PAD * 2).size();
    }

    private void drawHeader(DrawContext context, TextRenderer textRenderer, TrackInfo track, HudTheme theme, int width) {
        boolean hasTrack = track.hasTrack();

        if (this.config.showAlbumArt) {
            this.drawAlbumArt(context, track, PAD, PAD, ART_SIZE, theme);
        }

        int textX = PAD + (this.config.showAlbumArt ? ART_SIZE + ART_GAP : 0);
        int controlsWidth = this.config.showControls && hasTrack ? CONTROL_AREA : 0;
        int maxTextWidth = width - textX - PAD - controlsWidth;

        int contentHeight = this.config.showAlbumArt ? ART_SIZE : 22;
        int textBlockHeight = textRenderer.fontHeight + 2 + textRenderer.fontHeight;
        int titleY = PAD + Math.max(0, (contentHeight - textBlockHeight) / 2);

        String title;
        String artist;

        if (hasTrack) {
            title = track.title().isBlank() ? track.artist() : track.title();
            artist = track.artist();
        } else {
            title = Text.translatable("spotifyhud.hud.nothing_playing").getString();
            artist = Text.translatable("spotifyhud.hud.start_spotify").getString();
        }

        drawFittedText(context, textRenderer, title, textX, titleY, maxTextWidth, HudColors.title(this.config));
        drawFittedText(context, textRenderer, artist, textX, titleY + textRenderer.fontHeight + 2, maxTextWidth,
                HudColors.subtitle(this.config));

        if (this.config.showControls && hasTrack) {
            int controlsCenterX = width - PAD - CONTROL_AREA / 2;
            int controlsCenterY = PAD + contentHeight / 2;
            HudIcons.drawControls(
                    context,
                    controlsCenterX,
                    controlsCenterY,
                    CONTROL_PLAY_SIZE,
                    track.playing(),
                    HudColors.iconSecondary(this.config),
                    HudColors.icon(this.config)
            );
        }
    }

    // ------------------------------------------------------------------
    // Compact layout: one line with art, inline text, time and a thin bar.
    // ------------------------------------------------------------------

    private void drawCompact(DrawContext context, TextRenderer textRenderer, TrackInfo track) {
        int width = this.baseWidth();
        int height = 34;
        this.drawPanel(context, width, height, this.config.shadow);

        int artSize = 24;
        int artX = 5;
        int artY = (height - artSize) / 2;
        if (this.config.showAlbumArt) {
            this.drawAlbumArt(context, track, artX, artY, artSize, this.config.theme);
        }

        int textX = this.config.showAlbumArt ? artX + artSize + 6 : 7;
        String time = track.hasDuration() ? "-" + formatTime(remainingMs(track)) : "";
        int timeWidth = time.isEmpty() ? 0 : textRenderer.getWidth(time);
        int right = width - 7;
        int maxTextWidth = right - textX - (timeWidth == 0 ? 0 : timeWidth + 6);
        int lineY = (height - textRenderer.fontHeight) / 2;

        this.drawInline(context, textRenderer, track, textX, lineY, maxTextWidth);
        if (!time.isEmpty()) {
            context.drawText(textRenderer, time, right - timeWidth, lineY, HudColors.subtitle(this.config), false);
        }

        if (this.config.showProgressBar && track.hasDuration()) {
            int barWidth = width;
            int filled = Math.round(barWidth * track.progress(System.currentTimeMillis()));
            context.fill(0, height - 2, barWidth, height, HudColors.progressTrack(this.config));
            if (filled > 0) {
                context.fill(0, height - 2, filled, height, HudColors.progressFill(this.config));
            }
        }
    }

    // ------------------------------------------------------------------
    // Tooltip layout: art, stacked text, time and an equalizer.
    // ------------------------------------------------------------------

    private void drawTooltip(DrawContext context, TextRenderer textRenderer, TrackInfo track) {
        int width = this.baseWidth();
        int height = 46;
        int radius = Math.max(2, this.config.cornerRadius / 2);
        int background = ColorUtil.withOpacity(HudColors.background(this.config), this.config.backgroundOpacity);

        if (this.config.shadow) {
            RoundedRect.fill(context, 1, 2, width, height, radius + 1, 0x55000000);
        }
        RoundedRect.fill(context, -1, -1, width + 2, height + 2, radius + 1, HudColors.divider(this.config));
        RoundedRect.fill(context, 0, 0, width, height, radius, background);

        int artSize = 30;
        int artX = 8;
        int artY = 8;
        if (this.config.showAlbumArt) {
            this.drawAlbumArt(context, track, artX, artY, artSize, this.config.theme);
        }

        int textX = this.config.showAlbumArt ? artX + artSize + 7 : 9;
        boolean hasTrack = track.hasTrack();

        String title = hasTrack
                ? (track.title().isBlank() ? track.artist() : track.title())
                : Text.translatable("spotifyhud.hud.nothing_playing").getString();
        String artist = hasTrack ? track.artist() : Text.translatable("spotifyhud.hud.start_spotify").getString();
        String time = hasTrack && track.hasDuration()
                ? formatTime(track.elapsedMs(System.currentTimeMillis())) + " / " + formatTime(track.durationSec() * 1000L)
                : "";

        int eqWidth = this.config.showEqualizer ? 20 : 0;
        int maxTextWidth = width - textX - 8 - (eqWidth == 0 ? 0 : eqWidth + 6);

        drawFittedText(context, textRenderer, title, textX, 7, maxTextWidth, HudColors.title(this.config));
        drawFittedText(context, textRenderer, artist, textX, 18, maxTextWidth, HudColors.subtitle(this.config));
        if (!time.isEmpty()) {
            drawFittedText(context, textRenderer, time, textX, 30, maxTextWidth, HudColors.subtitle(this.config));
        }

        if (this.config.showEqualizer) {
            EqualizerBars.draw(
                    context,
                    width - 8 - eqWidth,
                    9,
                    eqWidth,
                    12,
                    HudColors.equalizer(this.config),
                    track.playing(),
                    System.currentTimeMillis()
            );
        }
    }

    // ------------------------------------------------------------------
    // Hotbar layout: equalizer, inline text and time on a dark strip.
    // ------------------------------------------------------------------

    private void drawHotbar(DrawContext context, TextRenderer textRenderer, TrackInfo track) {
        int width = this.baseWidth();
        int height = 22;
        this.drawPanel(context, width, height, this.config.shadow);

        int x = 6;
        int eqWidth = 16;
        if (this.config.showEqualizer) {
            EqualizerBars.draw(
                    context,
                    x,
                    (height - 10) / 2,
                    eqWidth,
                    10,
                    HudColors.equalizer(this.config),
                    track.playing(),
                    System.currentTimeMillis()
            );
            x += eqWidth + 5;
        }

        String time = track.hasDuration()
                ? formatTime(track.elapsedMs(System.currentTimeMillis())) + "/" + formatTime(track.durationSec() * 1000L)
                : "";
        int timeWidth = time.isEmpty() ? 0 : textRenderer.getWidth(time);
        int right = width - 7;
        int maxTextWidth = right - x - (timeWidth == 0 ? 0 : timeWidth + 6);
        int lineY = (height - textRenderer.fontHeight) / 2;

        this.drawInline(context, textRenderer, track, x, lineY, maxTextWidth);
        if (!time.isEmpty()) {
            context.drawText(textRenderer, time, right - timeWidth, lineY, HudColors.subtitle(this.config), false);
        }
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private void drawPanel(DrawContext context, int width, int height, boolean shadow) {
        int radius = this.config.cornerRadius;
        int background = ColorUtil.withOpacity(HudColors.background(this.config), this.config.backgroundOpacity);
        if (shadow) {
            RoundedRect.fill(context, 2, 3, width, height, radius, 0x55000000);
        }
        RoundedRect.fill(context, 0, 0, width, height, radius, background);
    }

    private void drawInline(
            DrawContext context,
            TextRenderer textRenderer,
            TrackInfo track,
            int x,
            int y,
            int maxWidth
    ) {
        if (maxWidth <= 0) {
            return;
        }

        String title;
        String artist;
        if (track.hasTrack()) {
            title = track.title().isBlank() ? track.artist() : track.title();
            artist = track.artist();
        } else {
            title = Text.translatable("spotifyhud.hud.nothing_playing").getString();
            artist = Text.translatable("spotifyhud.hud.start_spotify").getString();
        }

        int titleColor = HudColors.title(this.config);
        int subtitleColor = HudColors.subtitle(this.config);
        String separator = " \u00b7 ";
        int separatorWidth = textRenderer.getWidth(separator);
        int titleWidth = textRenderer.getWidth(title);

        if (titleWidth >= maxWidth) {
            drawFittedText(context, textRenderer, title, x, y, maxWidth, titleColor);
            return;
        }

        context.drawText(textRenderer, title, x, y, titleColor, false);

        if (artist.isBlank()) {
            return;
        }

        int remaining = maxWidth - titleWidth;
        if (remaining < separatorWidth + textRenderer.getWidth("W")) {
            return;
        }

        context.drawText(textRenderer, separator, x + titleWidth, y, subtitleColor, false);
        drawFittedText(context, textRenderer, artist,
                x + titleWidth + separatorWidth, y, remaining - separatorWidth, subtitleColor);
    }

    private void drawAlbumArt(DrawContext context, TrackInfo track, int x, int y, int size, HudTheme theme) {
        Identifier texture = track.hasTrack()
                ? this.albumArtService.textureFor(track.mediaKey(), track.artist(), track.title())
                : null;

        if (texture != null) {
            // Passing the destination size as the texture size makes the UVs
            // span the whole dynamic texture (covers are 600x600).
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, size, size, size, size);
        } else {
            int placeholder = ColorUtil.withOpacity(theme.accent(), 0.20F);
            RoundedRect.fill(context, x, y, size, size, 6, placeholder);
            HudIcons.drawMusicNote(context, x + size / 2, y + size / 2, Math.round(size / 2.0F), theme.accent());
        }

        if (this.config.showEqualizer && this.config.layout == HudLayout.CARD) {
            EqualizerBars.draw(
                    context,
                    x + 2,
                    y + size - 9,
                    size - 4,
                    7,
                    HudColors.equalizer(this.config),
                    track.playing(),
                    System.currentTimeMillis()
            );
        }
    }

    private void drawProgress(DrawContext context, TextRenderer textRenderer, TrackInfo track, int width, int y) {
        long now = System.currentTimeMillis();
        long elapsedMs = track.elapsedMs(now);
        long remainingMs = Math.max(0L, track.durationSec() * 1000L - elapsedMs);

        String elapsed = formatTime(elapsedMs);
        String remaining = "-" + formatTime(remainingMs);
        int labelY = y + 1;
        int labelColor = HudColors.subtitle(this.config);

        context.drawText(textRenderer, elapsed, PAD, labelY, labelColor, false);

        int remainingWidth = textRenderer.getWidth(remaining);
        context.drawText(textRenderer, remaining, width - PAD - remainingWidth, labelY, labelColor, false);

        int barX = PAD;
        int barY = y + 11;
        int barWidth = width - PAD * 2;
        int barHeight = 3;

        RoundedRect.fill(context, barX, barY, barWidth, barHeight, 1, HudColors.progressTrack(this.config));

        int filled = Math.round(barWidth * track.progress(now));
        if (filled > 0) {
            RoundedRect.fill(context, barX, barY, filled, barHeight, 1, HudColors.progressFill(this.config));
        }
    }

    private void drawLyrics(DrawContext context, TextRenderer textRenderer, TrackInfo track, LyricsDoc doc, int width, int y) {
        if (doc.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long positionMs = track.elapsedMs(now) + LYRICS_LOOKAHEAD_MS;
        int active = doc.indexAt(positionMs);
        List<LyricRow> rows = this.layoutRows(textRenderer, doc, positionMs, width - PAD * 2);
        if (rows.isEmpty()) {
            return;
        }

        // Fade/slide the whole block whenever the highlighted line changes.
        if (!Objects.equals(doc.key(), this.lastLyricsKey) || active != this.lastActiveLine) {
            this.lastLyricsKey = doc.key();
            this.lastActiveLine = active;
            this.lyricChangedAtMs = now;
        }

        float raw = Math.min(1.0F, (now - this.lyricChangedAtMs) / (float) LYRIC_TRANSITION_MS);
        float reveal = smoothstep(raw);
        float lineFade = 0.25F + 0.75F * reveal;
        int slide = Math.round((1.0F - reveal) * 4.0F);

        context.fill(PAD, y, width - PAD, y + 1, HudColors.divider(this.config));

        int rowY = y + 7 + slide;
        int spaceWidth = textRenderer.getWidth(" ");
        int maxWidth = width - PAD * 2;

        int inactiveColor = ColorUtil.scaleAlpha(HudColors.lyricInactive(this.config), 0.62F);
        int activeColor = ColorUtil.scaleAlpha(HudColors.lyricActive(this.config), 0.98F);

        for (LyricRow row : rows) {
            int cursorX = PAD + Math.max(0, (maxWidth - row.width()) / 2);

            for (LyricsWord word : row.words()) {
                String wordText = word.text();
                if (wordText.isBlank()) {
                    continue;
                }

                int color = inactiveColor;
                int wordY = rowY;

                if (row.active()) {
                    float progress = word.progress(positionMs);
                    // Smoothstep gives the colour sweep a soft start and end.
                    float eased = smoothstep(progress);
                    color = ColorUtil.lerp(inactiveColor, activeColor, eased);

                    // Each word rises a couple of pixels and fades in as it is
                    // sung, which keeps the transition alive word by word.
                    float entrance = Math.min(1.0F, progress * 4.0F);
                    wordY -= Math.round((1.0F - entrance) * 2.0F);
                    color = ColorUtil.scaleAlpha(color, 0.72F + 0.28F * entrance);
                }

                context.drawText(textRenderer, wordText, cursorX, wordY,
                        ColorUtil.scaleAlpha(color, lineFade), false);
                cursorX += textRenderer.getWidth(wordText) + spaceWidth;
            }

            rowY += LYRIC_ROW_HEIGHT;
        }
    }

    /**
     * Greedy word wrap for the visible lyric window. Rows keep their source
     * line index so the active line can still be highlighted word by word.
     */
    private List<LyricRow> layoutRows(TextRenderer textRenderer, LyricsDoc doc, long positionMs, int maxWidth) {
        List<LyricRow> rows = new ArrayList<>();
        if (maxWidth <= 0) {
            return rows;
        }

        int active = doc.indexAt(positionMs);
        int count = Math.max(1, this.config.lyricLines);
        int first = Math.max(0, active - (count - 1) / 2);
        first = Math.min(first, Math.max(0, doc.lines().size() - count));
        int maxRows = Math.max(2, count * 2);
        int spaceWidth = textRenderer.getWidth(" ");

        for (int index = first; index < doc.lines().size() && rows.size() < maxRows; index++) {
            LyricsLine line = doc.lines().get(index);
            if (line.isInstrumental() || line.text().isBlank()) {
                continue;
            }

            List<LyricsWord> current = new ArrayList<>();
            int currentWidth = 0;

            for (LyricsWord word : line.words()) {
                int wordWidth = textRenderer.getWidth(word.text());
                if (!current.isEmpty() && currentWidth + spaceWidth + wordWidth > maxWidth) {
                    rows.add(new LyricRow(List.copyOf(current), currentWidth, index, index == active));
                    if (rows.size() >= maxRows) {
                        return rows;
                    }
                    current.clear();
                    currentWidth = 0;
                }

                if (!current.isEmpty()) {
                    currentWidth += spaceWidth;
                }
                current.add(word);
                currentWidth += wordWidth;
            }

            if (!current.isEmpty()) {
                rows.add(new LyricRow(List.copyOf(current), currentWidth, index, index == active));
            }
        }

        return rows;
    }

    private static float smoothstep(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return t * t * (3.0F - 2.0F * t);
    }

    private static long remainingMs(TrackInfo track) {
        long duration = Math.max(0L, track.durationSec() * 1000L);
        return Math.max(0L, duration - track.elapsedMs(System.currentTimeMillis()));
    }

    private static void drawFittedText(DrawContext context, TextRenderer textRenderer, String text, int x, int y, int maxWidth, int color) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return;
        }

        String value = text;
        if (textRenderer.getWidth(value) > maxWidth) {
            String ellipsis = "...";
            int available = maxWidth - textRenderer.getWidth(ellipsis);
            value = available > 0
                    ? textRenderer.trimToWidth(value, available) + ellipsis
                    : textRenderer.trimToWidth(value, maxWidth);
        }

        context.drawText(textRenderer, value, x, y, color, false);
    }

    private static String formatTime(long milliseconds) {
        long totalSeconds = Math.max(0L, milliseconds / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
    }

    private record LyricRow(List<LyricsWord> words, int width, int lineIndex, boolean active) {
    }
}
