package dev.spotifyhud.hud;

import dev.spotifyhud.art.AlbumArtService;
import dev.spotifyhud.config.HudTheme;
import dev.spotifyhud.config.SpotifyHudConfig;
import dev.spotifyhud.lyrics.LyricsDoc;
import dev.spotifyhud.lyrics.LyricsLine;
import dev.spotifyhud.lyrics.LyricsService;
import dev.spotifyhud.lyrics.LyricsWord;
import dev.spotifyhud.playback.TrackInfo;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * Stateless-ish renderer for the overlay.
 *
 * <p>All geometry is expressed in base pixels and then scaled through the
 * {@link Matrix3x2fStack}, so the layout code does not have to know about the
 * configured HUD scale. The only state is the animation clock used by the
 * current lyric line.</p>
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

    private final SpotifyHudConfig config;
    private final LyricsService lyricsService;
    private final AlbumArtService albumArtService;

    public SpotifyHudRenderer(SpotifyHudConfig config, LyricsService lyricsService, AlbumArtService albumArtService) {
        this.config = config;
        this.lyricsService = lyricsService;
        this.albumArtService = albumArtService;
    }

    public int baseWidth() {
        return this.config.cardWidth;
    }

    public boolean hasLyrics(TrackInfo track) {
        return this.config.showLyrics && this.lyricsService.docFor(track) != null;
    }

    public boolean hasProgress(TrackInfo track) {
        return this.config.showProgressBar && track != null && track.hasDuration();
    }

    public int baseHeight(boolean hasProgress, boolean hasLyrics) {
        int height = this.headerHeight();
        if (hasProgress) {
            height += PROGRESS_HEIGHT;
        }
        if (hasLyrics) {
            height += this.lyricPanelHeight();
        }
        return height;
    }

    private int headerHeight() {
        int content = this.config.showAlbumArt ? ART_SIZE : 22;
        return PAD * 2 + content;
    }

    private int lyricPanelHeight() {
        return 6 + this.config.lyricLines * LYRIC_ROW_HEIGHT + 5;
    }

    /**
     * Resolves the on-screen rectangle from the configured anchor, offsets and
     * scale. At least a few pixels of the card are kept visible if an offset
     * is out of range.
     */
    public HudBounds bounds(MinecraftClient client, TrackInfo track, boolean hasLyrics) {
        int width = Math.max(1, Math.round(this.baseWidth() * this.config.scale));
        int height = Math.max(1, Math.round(this.baseHeight(this.hasProgress(track), hasLyrics) * this.config.scale));
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
        LyricsDoc doc = this.config.showLyrics ? this.lyricsService.docFor(safeTrack) : null;
        HudBounds bounds = this.bounds(client, safeTrack, doc != null);

        Matrix3x2fStack matrices = context.getMatrices();
        matrices.pushMatrix();
        matrices.translate(bounds.x(), bounds.y());
        matrices.scale(this.config.scale, this.config.scale);

        try {
            this.draw(context, client, safeTrack, doc);
        } finally {
            matrices.popMatrix();
        }
    }

    private void draw(DrawContext context, MinecraftClient client, TrackInfo track, LyricsDoc doc) {
        HudTheme theme = this.config.theme;
        int width = this.baseWidth();
        boolean hasLyrics = doc != null;
        int height = this.baseHeight(this.hasProgress(track), hasLyrics);
        int background = ColorUtil.withOpacity(theme.background(), this.config.backgroundOpacity);

        if (this.config.shadow) {
            RoundedRect.fill(context, 2, 3, width, height, this.config.cornerRadius, 0x55000000);
        }

        RoundedRect.fill(context, 0, 0, width, height, this.config.cornerRadius, background);

        this.drawHeader(context, client.textRenderer, track, theme, width);

        int sectionY = this.headerHeight();
        if (this.hasProgress(track)) {
            this.drawProgress(context, client.textRenderer, track, theme, width, sectionY);
            sectionY += PROGRESS_HEIGHT;
        }

        if (doc != null) {
            this.drawLyrics(context, client.textRenderer, track, doc, theme, width, sectionY);
        }
    }

    private void drawHeader(DrawContext context, TextRenderer textRenderer, TrackInfo track, HudTheme theme, int width) {
        boolean hasTrack = track.hasTrack();

        if (this.config.showAlbumArt) {
            this.drawAlbumArt(context, track, theme);
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

        drawFittedText(context, textRenderer, title, textX, titleY, maxTextWidth, theme.title());
        drawFittedText(context, textRenderer, artist, textX, titleY + textRenderer.fontHeight + 2, maxTextWidth, theme.subtitle());

        if (this.config.showControls && hasTrack) {
            int controlsCenterX = width - PAD - CONTROL_AREA / 2;
            int controlsCenterY = PAD + contentHeight / 2;
            HudIcons.drawControls(
                    context,
                    controlsCenterX,
                    controlsCenterY,
                    CONTROL_PLAY_SIZE,
                    track.playing(),
                    theme.subtitle(),
                    theme.title()
            );
        }
    }

    private void drawAlbumArt(DrawContext context, TrackInfo track, HudTheme theme) {
        int x = PAD;
        int y = PAD;

        Identifier texture = track.hasTrack()
                ? this.albumArtService.textureFor(track.mediaKey(), track.artist(), track.title())
                : null;

        if (texture != null) {
            // Passing the destination size as the texture size makes the UVs
            // span the whole dynamic texture (covers are 600x600).
            context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F, ART_SIZE, ART_SIZE, ART_SIZE, ART_SIZE);
        } else {
            int placeholder = ColorUtil.withOpacity(theme.accent(), 0.20F);
            RoundedRect.fill(context, x, y, ART_SIZE, ART_SIZE, 6, placeholder);
            HudIcons.drawMusicNote(context, x + ART_SIZE / 2, y + ART_SIZE / 2, 22, theme.accent());
        }
    }

    private void drawProgress(DrawContext context, TextRenderer textRenderer, TrackInfo track, HudTheme theme, int width, int y) {
        long now = System.currentTimeMillis();
        long elapsedMs = track.elapsedMs(now);
        long durationMs = Math.max(1L, track.durationSec() * 1000L);
        long remainingMs = Math.max(0L, durationMs - elapsedMs);

        String elapsed = formatTime(elapsedMs);
        String remaining = "-" + formatTime(remainingMs);
        int labelY = y + 1;

        context.drawText(textRenderer, elapsed, PAD, labelY, theme.subtitle(), false);

        int remainingWidth = textRenderer.getWidth(remaining);
        context.drawText(textRenderer, remaining, width - PAD - remainingWidth, labelY, theme.subtitle(), false);

        int barX = PAD;
        int barY = y + 11;
        int barWidth = width - PAD * 2;
        int barHeight = 3;

        RoundedRect.fill(context, barX, barY, barWidth, barHeight, 1, theme.divider());

        int filled = Math.round(barWidth * track.progress(now));
        if (filled > 0) {
            RoundedRect.fill(context, barX, barY, filled, barHeight, 1, theme.accent());
        }
    }

    private void drawLyrics(DrawContext context, TextRenderer textRenderer, TrackInfo track, LyricsDoc doc, HudTheme theme, int width, int y) {
        if (doc.isEmpty()) {
            return;
        }

        context.fill(PAD, y, width - PAD, y + 1, theme.divider());

        long positionMs = track.elapsedMs(System.currentTimeMillis()) + LYRICS_LOOKAHEAD_MS;
        int active = doc.indexAt(positionMs);
        int count = Math.max(1, this.config.lyricLines);
        int first = Math.max(0, active - (count - 1) / 2);
        first = Math.min(first, Math.max(0, doc.lines().size() - count));

        int rowY = y + 7;
        for (int row = 0; row < count; row++) {
            int index = first + row;
            if (index >= doc.lines().size()) {
                break;
            }

            LyricsLine line = doc.lines().get(index);
            if (!line.isInstrumental()) {
                this.drawLyricLine(context, textRenderer, line, index == active, positionMs, theme, width, rowY);
            }

            rowY += LYRIC_ROW_HEIGHT;
        }
    }

    private void drawLyricLine(
            DrawContext context,
            TextRenderer textRenderer,
            LyricsLine line,
            boolean active,
            long positionMs,
            HudTheme theme,
            int width,
            int y
    ) {
        String text = line.text();
        if (text.isBlank()) {
            return;
        }

        int maxWidth = width - PAD * 2;
        int fullWidth = textRenderer.getWidth(text);
        if (fullWidth > maxWidth) {
            String ellipsis = "...";
            int available = maxWidth - textRenderer.getWidth(ellipsis);
            String trimmed = available > 0 ? textRenderer.trimToWidth(text, available) + ellipsis : textRenderer.trimToWidth(text, maxWidth);
            context.drawText(textRenderer, trimmed, PAD, y, active ? theme.accent() : ColorUtil.scaleAlpha(theme.lyricInactive(), 0.55F), false);
            return;
        }

        int cursorX = (width - fullWidth) / 2;
        int spaceWidth = textRenderer.getWidth(" ");
        int baseColor = ColorUtil.scaleAlpha(theme.lyricInactive(), active ? 0.95F : 0.55F);

        for (LyricsWord word : line.words()) {
            int color = active
                    ? ColorUtil.lerp(baseColor, theme.accent(), word.progress(positionMs))
                    : baseColor;

            context.drawText(textRenderer, word.text(), cursorX, y, color, false);
            cursorX += textRenderer.getWidth(word.text()) + spaceWidth;
        }
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
}
