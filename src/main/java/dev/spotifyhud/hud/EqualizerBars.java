package dev.spotifyhud.hud;

import net.minecraft.client.gui.DrawContext;

/**
 * Small animated equalizer drawn from plain rectangles.
 *
 * <p>The bars are driven by two out-of-phase sine waves so the pattern never
 * looks like a simple loop. When playback is paused the bars settle into a low
 * static pattern instead of freezing mid-animation.</p>
 */
public final class EqualizerBars {
    private static final int BARS = 5;

    private EqualizerBars() {
    }

    public static void draw(
            DrawContext context,
            int x,
            int y,
            int width,
            int height,
            int color,
            boolean playing,
            long nowMs
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int gap = Math.max(1, width / 22);
        int barWidth = Math.max(1, (width - gap * (BARS - 1)) / BARS);
        int used = barWidth * BARS + gap * (BARS - 1);
        int start = x + Math.max(0, (width - used) / 2);
        int radius = Math.max(1, barWidth / 2);
        double t = playing ? nowMs / 1000.0 * 5.4 : 0.0;

        for (int i = 0; i < BARS; i++) {
            float level;
            if (playing) {
                double a = Math.sin(t + i * 0.85) * 0.5 + 0.5;
                double b = Math.sin(t * 1.7 + i * 1.9) * 0.5 + 0.5;
                level = (float) (0.28 + 0.72 * (a * 0.6 + b * 0.4));
            } else {
                level = 0.22F + 0.08F * (i % 2);
            }

            int barHeight = Math.max(2, Math.round(level * height));
            RoundedRect.fill(
                    context,
                    start + i * (barWidth + gap),
                    y + height - barHeight,
                    barWidth,
                    barHeight,
                    radius,
                    color
            );
        }
    }
}
