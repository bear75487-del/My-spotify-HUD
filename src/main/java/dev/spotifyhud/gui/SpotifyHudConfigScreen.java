package dev.spotifyhud.gui;

import dev.spotifyhud.SpotifyHudMod;
import dev.spotifyhud.config.HudAnchor;
import dev.spotifyhud.config.HudTheme;
import dev.spotifyhud.config.SpotifyHudConfig;
import dev.spotifyhud.gui.widget.OptionSlider;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Main configuration screen, also used as the Mod Menu config factory.
 *
 * <p>The screen is deliberately built from vanilla widgets only. Layout is a
 * fixed two-column grid: toggles on the left, layout/styling controls on the
 * right, with three actions at the bottom. Every change is written to disk
 * immediately (or on slider release) so closing the screen never loses state.</p>
 */
public final class SpotifyHudConfigScreen extends Screen {
    private static final int ROW_HEIGHT = 20;
    private static final int MAX_COLUMN_WIDTH = 200;
    private static final int COLUMN_GAP = 10;
    private static final int BUTTON_GAP = 6;
    private static final int FIRST_ROW_Y = 36;

    private final Screen parent;
    private final SpotifyHudConfig config;

    public SpotifyHudConfigScreen(Screen parent) {
        super(Text.translatable("spotifyhud.config.title"));
        this.parent = parent;
        this.config = SpotifyHudMod.getConfig();
    }

    @Override
    protected void init() {
        int columnWidth = this.columnWidth();
        int left = this.width / 2 - COLUMN_GAP / 2 - columnWidth;
        int right = this.width / 2 + COLUMN_GAP / 2;

        // ------------------------------------------------------------------
        // Left column: general behaviour and content toggles.
        // ------------------------------------------------------------------
        int y = FIRST_ROW_Y;

        this.addToggle(left, y, columnWidth, "spotifyhud.option.enabled",
                () -> this.config.enabled, value -> this.config.enabled = value);
        y += ROW_HEIGHT;

        this.addToggle(left, y, columnWidth, "spotifyhud.option.auto_detect",
                () -> this.config.autoDetect, value -> this.config.autoDetect = value);
        y += ROW_HEIGHT;

        this.addToggle(left, y, columnWidth, "spotifyhud.option.album_art",
                () -> this.config.showAlbumArt, value -> this.config.showAlbumArt = value);
        y += ROW_HEIGHT;

        this.addToggle(left, y, columnWidth, "spotifyhud.option.progress_bar",
                () -> this.config.showProgressBar, value -> this.config.showProgressBar = value);
        y += ROW_HEIGHT;

        this.addToggle(left, y, columnWidth, "spotifyhud.option.lyrics",
                () -> this.config.showLyrics, value -> this.config.showLyrics = value);
        y += ROW_HEIGHT;

        this.addToggle(left, y, columnWidth, "spotifyhud.option.controls",
                () -> this.config.showControls, value -> this.config.showControls = value);
        y += ROW_HEIGHT;

        this.addToggle(left, y, columnWidth, "spotifyhud.option.shadow",
                () -> this.config.shadow, value -> this.config.shadow = value);

        // ------------------------------------------------------------------
        // Right column: anchor, theme, and visual sliders.
        // ------------------------------------------------------------------
        y = FIRST_ROW_Y;

        this.addCycle(right, y, columnWidth, Text.translatable("spotifyhud.option.anchor"),
                HudAnchor.values(),
                () -> this.config.anchor,
                value -> this.config.anchor = value,
                value -> Text.translatable(value.translationKey()));
        y += ROW_HEIGHT;

        this.addCycle(right, y, columnWidth, Text.translatable("spotifyhud.option.theme"),
                HudTheme.values(),
                () -> this.config.theme,
                value -> this.config.theme = value,
                value -> Text.translatable(value.translationKey()));
        y += ROW_HEIGHT;

        this.addSlider(right, y, columnWidth, "spotifyhud.option.scale",
                0.5F, 2.0F, this.config.scale,
                value -> this.config.scale = value,
                value -> String.format(Locale.ROOT, "%.2fx", value));
        y += ROW_HEIGHT;

        this.addSlider(right, y, columnWidth, "spotifyhud.option.width",
                160.0F, 360.0F, this.config.cardWidth,
                value -> this.config.cardWidth = Math.round(value),
                value -> Math.round(value) + " px");
        y += ROW_HEIGHT;

        this.addSlider(right, y, columnWidth, "spotifyhud.option.radius",
                0.0F, 16.0F, this.config.cornerRadius,
                value -> this.config.cornerRadius = Math.round(value),
                value -> Integer.toString(Math.round(value)));
        y += ROW_HEIGHT;

        this.addSlider(right, y, columnWidth, "spotifyhud.option.opacity",
                0.15F, 1.0F, this.config.backgroundOpacity,
                value -> this.config.backgroundOpacity = value,
                value -> Math.round(value * 100.0F) + "%");
        y += ROW_HEIGHT;

        this.addSlider(right, y, columnWidth, "spotifyhud.option.lyric_lines",
                1.0F, 3.0F, this.config.lyricLines,
                value -> this.config.lyricLines = Math.round(value),
                value -> Integer.toString(Math.round(value)));

        // ------------------------------------------------------------------
        // Bottom action row.
        // ------------------------------------------------------------------
        int buttonWidth = Math.min(120, Math.max(60, (this.width - 40 - BUTTON_GAP * 2) / 3));
        int totalWidth = buttonWidth * 3 + BUTTON_GAP * 2;
        int buttonX = (this.width - totalWidth) / 2;
        int buttonY = this.height - 26;

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("spotifyhud.option.position"),
                        button -> MinecraftClient.getInstance().setScreen(new HudPositionScreen(this)))
                .dimensions(buttonX, buttonY, buttonWidth, ROW_HEIGHT)
                .build());

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("spotifyhud.option.reset"),
                        button -> {
                            this.config.resetToDefaults();
                            SpotifyHudMod.saveConfig();
                            // Rebuild the screen so every widget shows the new values.
                            MinecraftClient.getInstance().setScreen(new SpotifyHudConfigScreen(this.parent));
                        })
                .dimensions(buttonX + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, ROW_HEIGHT)
                .build());

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.done"),
                        button -> this.close())
                .dimensions(buttonX + (buttonWidth + BUTTON_GAP) * 2, buttonY, buttonWidth, ROW_HEIGHT)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int columnWidth = this.columnWidth();
        int left = this.width / 2 - COLUMN_GAP / 2 - columnWidth;
        int right = this.width / 2 + COLUMN_GAP / 2;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("spotifyhud.category.general"), left + columnWidth / 2, 24, 0xFFB3B3B3);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("spotifyhud.category.layout"), right + columnWidth / 2, 24, 0xFFB3B3B3);

        if (SpotifyHudMod.getDetector() != null && !SpotifyHudMod.getDetector().isSupported()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("spotifyhud.config.windows_only"), this.width / 2, this.height - 38, 0xFFFFAA00);
        }
    }

    @Override
    public void close() {
        SpotifyHudMod.saveConfig();
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private int columnWidth() {
        return Math.min(MAX_COLUMN_WIDTH, Math.max(100, (this.width - 40 - COLUMN_GAP) / 2));
    }

    /**
     * Adds a boolean option rendered as a single button. The label is rebuilt
     * after every click so it always mirrors the stored value.
     */
    private void addToggle(
            int x,
            int y,
            int width,
            String labelKey,
            BooleanSupplier getter,
            Consumer<Boolean> setter
    ) {
        Text label = Text.translatable(labelKey);
        this.addDrawableChild(ButtonWidget.builder(
                        toggleText(label, getter.getAsBoolean()),
                        button -> {
                            setter.accept(!getter.getAsBoolean());
                            button.setMessage(toggleText(label, getter.getAsBoolean()));
                            SpotifyHudMod.saveConfig();
                        })
                .dimensions(x, y, width, ROW_HEIGHT)
                .build());
    }

    /**
     * Adds an enum option that cycles through {@code values} on click.
     */
    private <T> void addCycle(
            int x,
            int y,
            int width,
            Text label,
            T[] values,
            Supplier<T> getter,
            Consumer<T> setter,
            Function<T, Text> valueText
    ) {
        this.addDrawableChild(ButtonWidget.builder(
                        cycleText(label, valueText.apply(getter.get())),
                        button -> {
                            T current = getter.get();
                            int index = 0;
                            for (int i = 0; i < values.length; i++) {
                                if (values[i].equals(current)) {
                                    index = i;
                                    break;
                                }
                            }

                            T next = values[(index + 1) % values.length];
                            setter.accept(next);
                            button.setMessage(cycleText(label, valueText.apply(next)));
                            SpotifyHudMod.saveConfig();
                        })
                .dimensions(x, y, width, ROW_HEIGHT)
                .build());
    }

    private void addSlider(
            int x,
            int y,
            int width,
            String labelKey,
            float minimum,
            float maximum,
            float value,
            Consumer<Float> setter,
            Function<Float, String> formatter
    ) {
        this.addDrawableChild(new OptionSlider(
                x,
                y,
                width,
                ROW_HEIGHT,
                Text.translatable(labelKey),
                minimum,
                maximum,
                value,
                setter,
                formatter,
                SpotifyHudMod::saveConfig
        ));
    }

    private static Text toggleText(Text label, boolean value) {
        return Text.empty()
                .append(label)
                .append(": ")
                .append(Text.translatable(value ? "spotifyhud.value.on" : "spotifyhud.value.off"));
    }

    private static Text cycleText(Text label, Text value) {
        return Text.empty()
                .append(label)
                .append(": ")
                .append(value);
    }
}
