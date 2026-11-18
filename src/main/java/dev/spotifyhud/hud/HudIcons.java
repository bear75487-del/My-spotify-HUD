package dev.spotifyhud.hud;

import net.minecraft.client.gui.DrawContext;

/**
 * Vector-style media glyphs for the HUD header.
 *
 * <p>The actual shapes live in {@link HudIconTextures}; this class only deals
 * with layout. All coordinates are in base (unscaled) HUD pixels.</p>
 */
public final class HudIcons {
    private HudIcons() {
    }

    /**
     * Draws the previous / play-pause / next control cluster centred on the
     * given point. The transport buttons are slightly smaller than the
     * play/pause glyph, mirroring the original overlay's proportions.
     */
    public static void drawControls(
            DrawContext context,
            int centerX,
            int centerY,
            int playSize,
            boolean playing,
            int sideColor,
            int centerColor
    ) {
        int sideSize = Math.max(6, Math.round(playSize * 10.0F / 13.0F));
        int gap = Math.max(3, Math.round(playSize * 5.0F / 13.0F));
        int totalWidth = sideSize * 2 + playSize + gap * 2;
        int left = centerX - totalWidth / 2;
        int sideY = centerY - sideSize / 2;
        int centerIconY = centerY - playSize / 2;

        HudIconTextures.drawPrevious(context, left, sideY, sideSize, sideColor);
        HudIconTextures.drawNext(context, left + sideSize + gap + playSize + gap, sideY, sideSize, sideColor);

        if (playing) {
            HudIconTextures.drawPause(context, left + sideSize + gap, centerIconY, playSize, centerColor);
        } else {
            HudIconTextures.drawPlay(context, left + sideSize + gap, centerIconY, playSize, centerColor);
        }
    }

    /** Music note used as the album-art placeholder, centred on a point. */
    public static void drawMusicNote(DrawContext context, int centerX, int centerY, int size, int color) {
        HudIconTextures.drawNote(context, centerX - size / 2, centerY - size / 2, size, color);
    }
}
