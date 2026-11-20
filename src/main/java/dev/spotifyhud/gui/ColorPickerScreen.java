package dev.spotifyhud.gui;

import dev.spotifyhud.SpotifyHudMod;
import dev.spotifyhud.config.HudColors;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Hue / saturation / value picker modelled on the reference design: a
 * saturation-value square on top of a hue strip, with a hex field and a live
 * preview. The result is written back as {@code #RRGGBB}; "Use Theme" clears
 * the override so the element falls back to the active palette.
 */
public final class ColorPickerScreen extends Screen {
    private static final int SWATCH = 12;

    private final Screen parent;
    private final Text label;
    private final Consumer<String> setter;
    private final int fallbackColor;
    private final String originalValue;

    private float hue;
    private float saturation;
    private float value;

    private int areaX;
    private int areaY;
    private int areaWidth;
    private int areaHeight;
    private int hueX;
    private int hueY;
    private int hueWidth;
    private int hueHeight = 12;

    private int dragging;
    private boolean updatingField;
    private TextFieldWidget hexField;

    public ColorPickerScreen(Screen parent, Text label, String currentValue, Consumer<String> setter, int fallbackColor) {
        super(Text.translatable("spotifyhud.picker.title"));
        this.parent = parent;
        this.label = label;
        this.setter = setter;
        this.fallbackColor = fallbackColor;
        this.originalValue = currentValue == null ? "" : currentValue;

        int rgb = HudColors.parseRgb(this.originalValue);
        if (rgb < 0) {
            rgb = fallbackColor & 0xFFFFFF;
        }
        this.setHsvFromRgb(rgb);
    }

    @Override
    protected void init() {
        this.areaWidth = Math.max(120, Math.min(240, this.width - 80));
        this.areaHeight = Math.max(60, Math.min(120, this.height - 210));
        this.areaX = (this.width - this.areaWidth) / 2;
        this.areaY = 44;

        this.hueWidth = this.areaWidth;
        this.hueX = this.areaX;
        this.hueY = this.areaY + this.areaHeight + 8;

        int fieldWidth = 74;
        int fieldX = this.areaX + this.areaWidth - fieldWidth;
        int fieldY = this.hueY + this.hueHeight + 10;

        this.hexField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldWidth, 20,
                Text.translatable("spotifyhud.picker.hex"));
        this.hexField.setMaxLength(7);
        this.hexField.setDrawsBackground(true);
        this.updatingField = true;
        this.hexField.setText(HudColors.format(this.currentRgb()));
        this.updatingField = false;
        this.hexField.setChangedListener(this::onHexChanged);
        this.addDrawableChild(this.hexField);

        int buttonWidth = 84;
        int gap = 8;
        int total = buttonWidth * 2 + gap;
        int buttonX = (this.width - total) / 2;
        int buttonY = this.height - 30;

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("spotifyhud.picker.done"),
                        button -> {
                            this.apply(this.currentRgb());
                            this.close();
                        })
                .dimensions(buttonX, buttonY, buttonWidth, 20)
                .build());

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("spotifyhud.picker.theme"),
                        button -> {
                            this.setter.accept("");
                            SpotifyHudMod.saveConfig();
                            this.close();
                        })
                .dimensions(buttonX + buttonWidth + gap, buttonY, buttonWidth, 20)
                .build());
    }

    private void onHexChanged(String text) {
        if (this.updatingField) {
            return;
        }

        int rgb = HudColors.parseRgb(text);
        if (rgb >= 0) {
            this.setHsvFromRgb(rgb);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            if (this.insideArea(click.x(), click.y())) {
                this.dragging = 1;
                this.updateFromArea(click.x(), click.y());
                return true;
            }
            if (this.insideHue(click.x(), click.y())) {
                this.dragging = 2;
                this.updateFromHue(click.x());
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (this.dragging == 1) {
            this.updateFromArea(click.x(), click.y());
            return true;
        }
        if (this.dragging == 2) {
            this.updateFromHue(click.x());
            return true;
        }

        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (this.dragging != 0) {
            this.dragging = 0;
            this.apply(this.currentRgb());
        }

        return super.mouseReleased(click);
    }

    private boolean insideArea(double x, double y) {
        return x >= this.areaX && x < this.areaX + this.areaWidth
                && y >= this.areaY && y < this.areaY + this.areaHeight;
    }

    private boolean insideHue(double x, double y) {
        return x >= this.hueX && x < this.hueX + this.hueWidth
                && y >= this.hueY && y < this.hueY + this.hueHeight;
    }

    private void updateFromArea(double mouseX, double mouseY) {
        this.saturation = clamp((float) (mouseX - this.areaX) / this.areaWidth);
        this.value = 1.0F - clamp((float) (mouseY - this.areaY) / this.areaHeight);
        this.apply(this.currentRgb());
    }

    private void updateFromHue(double mouseX) {
        this.hue = clamp((float) (mouseX - this.hueX) / this.hueWidth);
        this.apply(this.currentRgb());
    }

    private void apply(int rgb) {
        this.setter.accept(HudColors.format(rgb));
        this.syncHexField();
    }

    private void syncHexField() {
        if (this.hexField == null) {
            return;
        }
        this.updatingField = true;
        this.hexField.setText(HudColors.format(this.currentRgb()));
        this.updatingField = false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.drawArea(context);
        this.drawHue(context);
        this.drawMarker(context);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, this.label, this.width / 2, 24, 0xFFB3B3B3);

        int previewX = this.areaX;
        int previewY = this.hueY + this.hueHeight + 12;
        context.fill(previewX - 1, previewY - 1, previewX + SWATCH + 1, previewY + SWATCH + 1, 0xFF3A3A3A);
        context.fill(previewX, previewY, previewX + SWATCH, previewY + SWATCH, 0xFF000000 | this.currentRgb());

        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("spotifyhud.picker.hex"), previewX + SWATCH + 6, previewY + 2, 0xFFE6E6E6);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawArea(DrawContext context) {
        int step = 4;
        for (int column = 0; column < this.areaWidth; column += step) {
            float s = Math.min(1.0F, (column + step * 0.5F) / this.areaWidth);
            for (int row = 0; row < this.areaHeight; row += step) {
                float v = 1.0F - Math.min(1.0F, (row + step * 0.5F) / this.areaHeight);
                int color = 0xFF000000 | hsvToRgb(this.hue, s, v);
                context.fill(
                        this.areaX + column,
                        this.areaY + row,
                        this.areaX + Math.min(column + step, this.areaWidth),
                        this.areaY + Math.min(row + step, this.areaHeight),
                        color
                );
            }
        }
    }

    private void drawHue(DrawContext context) {
        for (int column = 0; column < this.hueWidth; column++) {
            float h = (float) column / Math.max(1, this.hueWidth - 1);
            int color = 0xFF000000 | hsvToRgb(h, 1.0F, 1.0F);
            context.fill(this.hueX + column, this.hueY, this.hueX + column + 1, this.hueY + this.hueHeight, color);
        }
    }

    private void drawMarker(DrawContext context) {
        int markerX = this.areaX + Math.round(this.saturation * this.areaWidth);
        int markerY = this.areaY + Math.round((1.0F - this.value) * this.areaHeight);
        this.drawRing(context, markerX, markerY, 4, 0xFFFFFFFF);

        int hueMarkerX = this.hueX + Math.round(this.hue * (this.hueWidth - 1));
        int hueCenter = this.hueY + this.hueHeight / 2;
        this.drawRing(context, hueMarkerX, hueCenter, 5, 0xFFFFFFFF);
    }

    /** Small hollow ring built from four 1px rectangles. */
    private void drawRing(DrawContext context, int centerX, int centerY, int radius, int color) {
        int left = centerX - radius;
        int right = centerX + radius;
        int top = centerY - radius;
        int bottom = centerY + radius;

        context.fill(left, top, right, top + 1, color);
        context.fill(left, bottom, right + 1, bottom + 1, color);
        context.fill(left, top, left + 1, bottom + 1, color);
        context.fill(right, top, right + 1, bottom + 1, color);
    }

    private int currentRgb() {
        return hsvToRgb(this.hue, this.saturation, this.value);
    }

    private void setHsvFromRgb(int rgb) {
        int r = (rgb >>> 16) & 0xFF;
        int g = (rgb >>> 8) & 0xFF;
        int b = rgb & 0xFF;

        float red = r / 255.0F;
        float green = g / 255.0F;
        float blue = b / 255.0F;
        float max = Math.max(red, Math.max(green, blue));
        float min = Math.min(red, Math.min(green, blue));
        float delta = max - min;

        this.value = max;
        this.saturation = max <= 0.0F ? 0.0F : delta / max;

        if (delta <= 0.0F) {
            this.hue = 0.0F;
        } else if (max == red) {
            this.hue = ((green - blue) / delta % 6.0F + 6.0F) % 6.0F / 6.0F;
        } else if (max == green) {
            this.hue = ((blue - red) / delta + 2.0F) / 6.0F;
        } else {
            this.hue = ((red - green) / delta + 4.0F) / 6.0F;
        }
    }

    public static int hsvToRgb(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6.0F;
        float s = clamp(saturation);
        float v = clamp(value);

        int sector = (int) Math.floor(h);
        float fraction = h - sector;
        float p = v * (1.0F - s);
        float q = v * (1.0F - s * fraction);
        float t = v * (1.0F - s * (1.0F - fraction));

        float r;
        float g;
        float b;
        switch (sector % 6) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }

        int red = Math.round(clamp(r) * 255.0F);
        int green = Math.round(clamp(g) * 255.0F);
        int blue = Math.round(clamp(b) * 255.0F);
        return (red << 16) | (green << 8) | blue;
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    @Override
    public void close() {
        SpotifyHudMod.saveConfig();
        MinecraftClient.getInstance().setScreen(this.parent);
    }
}
