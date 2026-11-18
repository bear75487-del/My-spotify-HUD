package dev.spotifyhud.gui;

import dev.spotifyhud.SpotifyHudMod;
import dev.spotifyhud.config.HudAnchor;
import dev.spotifyhud.config.SpotifyHudConfig;
import dev.spotifyhud.hud.HudBounds;
import dev.spotifyhud.hud.SpotifyHudRenderer;
import dev.spotifyhud.playback.TrackInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Drag-to-position editor.
 *
 * <p>The screen draws the real HUD (through the normal renderer, so lyrics and
 * artwork behave exactly like in game) and lets the player drag the card with
 * the left mouse button. While dragging, the nearest corner becomes the new
 * anchor, which keeps the stored offsets positive and makes the HUD stick to
 * that corner when the window is resized.</p>
 */
public final class HudPositionScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int BUTTON_WIDTH = 110;
    private static final int EDGE_MARGIN = 4;

    private final Screen parent;
    private final SpotifyHudConfig config;

    private boolean dragging;
    private int grabX;
    private int grabY;

    public HudPositionScreen(Screen parent) {
        super(Text.translatable("spotifyhud.position.title"));
        this.parent = parent;
        this.config = SpotifyHudMod.getConfig();
    }

    @Override
    protected void init() {
        int buttonY = this.height - 26;

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("spotifyhud.position.reset"),
                        button -> {
                            this.config.anchor = HudAnchor.TOP_LEFT;
                            this.config.offsetX = 12;
                            this.config.offsetY = 12;
                            SpotifyHudMod.saveConfig();
                        })
                .dimensions(this.width / 2 - BUTTON_WIDTH - 4, buttonY, BUTTON_WIDTH, ROW_HEIGHT)
                .build());

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.done"),
                        button -> this.close())
                .dimensions(this.width / 2 + 4, buttonY, BUTTON_WIDTH, ROW_HEIGHT)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Background first so the HUD sits on top of the darkened world.
        this.renderBackground(context, mouseX, mouseY, delta);

        SpotifyHudRenderer renderer = SpotifyHudMod.getRenderer();
        MinecraftClient client = MinecraftClient.getInstance();
        TrackInfo track = SpotifyHudMod.getPlayback() == null
                ? TrackInfo.EMPTY
                : SpotifyHudMod.getPlayback().current();

        if (renderer != null) {
            renderer.render(context, client, track);

            HudBounds bounds = renderer.bounds(client, track, renderer.hasLyrics(track));
            int outline = this.dragging ? 0xCC1DB954 : 0x99FFFFFF;
            context.drawBorder(bounds.x() - 2, bounds.y() - 2, bounds.width() + 4, bounds.height() + 4, outline);
        }

        // Draw the widgets after the HUD so the action row stays readable and
        // clickable even if the card is dragged near the bottom edge.
        for (Element element : this.children()) {
            if (element instanceof Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        }

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("spotifyhud.position.hint"), this.width / 2, this.height - 40, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            HudBounds bounds = this.currentBounds();
            if (bounds.contains(mouseX, mouseY)) {
                this.dragging = true;
                this.grabX = (int) mouseX - bounds.x();
                this.grabY = (int) mouseY - bounds.y();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.dragging && button == 0) {
            HudBounds bounds = this.currentBounds();
            int targetX = (int) Math.round(mouseX) - this.grabX;
            int targetY = (int) Math.round(mouseY) - this.grabY;
            this.moveTo(targetX, targetY, bounds.width(), bounds.height());
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging && button == 0) {
            this.dragging = false;
            SpotifyHudMod.saveConfig();
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        SpotifyHudMod.saveConfig();
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private HudBounds currentBounds() {
        MinecraftClient client = MinecraftClient.getInstance();
        SpotifyHudRenderer renderer = SpotifyHudMod.getRenderer();
        TrackInfo track = SpotifyHudMod.getPlayback() == null
                ? TrackInfo.EMPTY
                : SpotifyHudMod.getPlayback().current();

        if (renderer == null) {
            return new HudBounds(0, 0, 0, 0);
        }
        return renderer.bounds(client, track, renderer.hasLyrics(track));
    }

    /**
     * Moves the card so its top-left corner lands on the requested position,
     * then derives anchor and offsets from the card centre. Clamping to the
     * screen keeps the card fully visible.
     */
    private void moveTo(int x, int y, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();

        // Leave a small margin so the card border and its drop shadow are never
        // clipped by the window edge. If the card is larger than the screen the
        // margin collapses and the card is simply kept on screen.
        int marginX = width + EDGE_MARGIN * 2 <= screenWidth ? EDGE_MARGIN : 0;
        int marginY = height + EDGE_MARGIN * 2 <= screenHeight ? EDGE_MARGIN : 0;

        int clampedX = clamp(x, marginX, screenWidth - width - marginX);
        int clampedY = clamp(y, marginY, screenHeight - height - marginY);

        boolean right = clampedX + width / 2 >= screenWidth / 2;
        boolean bottom = clampedY + height / 2 >= screenHeight / 2;

        HudAnchor anchor = bottom
                ? (right ? HudAnchor.BOTTOM_RIGHT : HudAnchor.BOTTOM_LEFT)
                : (right ? HudAnchor.TOP_RIGHT : HudAnchor.TOP_LEFT);

        this.config.anchor = anchor;
        this.config.offsetX = anchor.isRight() ? screenWidth - width - clampedX : clampedX;
        this.config.offsetY = anchor.isBottom() ? screenHeight - height - clampedY : clampedY;
        this.config.sanitize();
    }

    private static int clamp(int value, int minimum, int maximum) {
        if (maximum < minimum) {
            return minimum;
        }
        return Math.max(minimum, Math.min(maximum, value));
    }
}
