package dev.spotifyhud.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.spotifyhud.SpotifyHudMod;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Crisp, anti-aliased HUD icons.
 *
 * <p>The icons are painted once with Java2D at 64x64 and uploaded as textures
 * with linear filtering. Drawing them as textures (instead of stacking
 * rectangles) keeps the play/pause glyphs smooth at every GUI scale and close
 * to the vector icons used by the original overlay.</p>
 *
 * <p>Every icon is white; {@link #draw} tints it at render time through the
 * shader colour, so the same texture works for all themes.</p>
 */
public final class HudIconTextures {
    private static final int SIZE = 64;

    private static Identifier play;
    private static Identifier pause;
    private static Identifier previous;
    private static Identifier next;
    private static Identifier note;

    private HudIconTextures() {
    }

    public static void drawPlay(DrawContext context, int x, int y, int size, int color) {
        draw(context, play(), x, y, size, color);
    }

    public static void drawPause(DrawContext context, int x, int y, int size, int color) {
        draw(context, pause(), x, y, size, color);
    }

    public static void drawPrevious(DrawContext context, int x, int y, int size, int color) {
        draw(context, previous(), x, y, size, color);
    }

    public static void drawNext(DrawContext context, int x, int y, int size, int color) {
        draw(context, next(), x, y, size, color);
    }

    public static void drawNote(DrawContext context, int x, int y, int size, int color) {
        draw(context, note(), x, y, size, color);
    }

    private static void draw(DrawContext context, Identifier texture, int x, int y, int size, int color) {
        if (texture == null || size <= 0) {
            return;
        }

        float alpha = (color >>> 24) / 255.0F;
        float red = (color >> 16 & 0xFF) / 255.0F;
        float green = (color >> 8 & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        RenderSystem.setShaderColor(red, green, blue, alpha);
        // The destination size is also used as the texture size, so the UVs
        // span the whole 64x64 glyph.
        context.drawTexture(texture, x, y, 0.0F, 0.0F, size, size, size, size);
        // Flush before resetting the shader colour; otherwise the tint would
        // be applied to whatever is drawn next instead of this icon.
        context.draw();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static Identifier play() {
        if (play == null) {
            play = create("play", graphics -> {
                GeneralPath path = new GeneralPath();
                path.moveTo(14.0, 10.0);
                path.lineTo(54.0, 32.0);
                path.lineTo(14.0, 54.0);
                path.closePath();
                graphics.fill(path);
            });
        }
        return play;
    }

    private static Identifier pause() {
        if (pause == null) {
            pause = create("pause", graphics -> {
                graphics.fill(new RoundRectangle2D.Double(15.5, 10.0, 12.0, 44.0, 7.0, 7.0));
                graphics.fill(new RoundRectangle2D.Double(36.5, 10.0, 12.0, 44.0, 7.0, 7.0));
            });
        }
        return pause;
    }

    private static Identifier previous() {
        if (previous == null) {
            previous = create("previous", graphics -> {
                graphics.fill(new RoundRectangle2D.Double(10.0, 12.0, 7.0, 40.0, 4.0, 4.0));
                GeneralPath path = new GeneralPath();
                path.moveTo(50.0, 12.0);
                path.lineTo(22.0, 32.0);
                path.lineTo(50.0, 52.0);
                path.closePath();
                graphics.fill(path);
            });
        }
        return previous;
    }

    private static Identifier next() {
        if (next == null) {
            next = create("next", graphics -> {
                GeneralPath path = new GeneralPath();
                path.moveTo(14.0, 12.0);
                path.lineTo(42.0, 32.0);
                path.lineTo(14.0, 52.0);
                path.closePath();
                graphics.fill(path);
                graphics.fill(new RoundRectangle2D.Double(47.0, 12.0, 7.0, 40.0, 4.0, 4.0));
            });
        }
        return next;
    }

    private static Identifier note() {
        if (note == null) {
            note = create("note", graphics -> {
                graphics.fill(new Ellipse2D.Double(12.0, 42.0, 24.0, 18.0));
                graphics.fill(new RoundRectangle2D.Double(34.0, 10.0, 5.0, 36.0, 2.0, 2.0));
                GeneralPath flag = new GeneralPath();
                flag.moveTo(38.0, 10.0);
                flag.lineTo(54.0, 17.0);
                flag.lineTo(54.0, 28.0);
                flag.lineTo(38.0, 21.0);
                flag.closePath();
                graphics.fill(flag);
            });
        }
        return note;
    }

    private static Identifier create(String name, Consumer<Graphics2D> painter) {
        BufferedImage buffer = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = buffer.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            graphics.setColor(java.awt.Color.WHITE);
            painter.accept(graphics);
        } finally {
            graphics.dispose();
        }

        NativeImage image = new NativeImage(SIZE, SIZE, false);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setColor(x, y, buffer.getRGB(x, y));
            }
        }

        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        // Bilinear filtering keeps the 64x64 glyphs smooth when scaled down.
        texture.setFilter(true, false);

        Identifier identifier = Identifier.of(SpotifyHudMod.MOD_ID, "hud_icons/" + name);
        MinecraftClient.getInstance().getTextureManager().registerTexture(identifier, texture);
        return identifier;
    }
}
