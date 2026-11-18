package dev.spotifyhud.hud;

import net.minecraft.client.gui.DrawContext;

/**
 * Draws filled rectangles with rounded corners using only {@link
 * DrawContext#fill(int, int, int, int, int)}.
 *
 * <p>This keeps the mod entirely independent from the original client's
 * custom shader/rounded-rect utilities while still producing a soft card
 * silhouette at any GUI scale.</p>
 */
public final class RoundedRect {
    private RoundedRect() {
    }

    public static void fill(DrawContext context, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        if (r == 0) {
            context.fill(x, y, x + width, y + height, color);
            return;
        }

        for (int row = 0; row < height; row++) {
            int inset = 0;
            if (row < r) {
                inset = cornerInset(r, row);
            } else if (row >= height - r) {
                inset = cornerInset(r, height - 1 - row);
            }

            context.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
        }
    }

    private static int cornerInset(int radius, int row) {
        double dy = radius - 0.5D - row;
        double inside = radius * (double) radius - dy * dy;
        double dx = Math.sqrt(Math.max(0.0D, inside));
        return Math.max(0, (int) Math.round(radius - dx));
    }
}
