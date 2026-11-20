package dev.spotifyhud.gui;

import dev.spotifyhud.SpotifyHudMod;
import dev.spotifyhud.config.HudColors;
import dev.spotifyhud.config.HudPresetData;
import dev.spotifyhud.config.SpotifyHudConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/**
 * Colours and presets.
 *
 * <p>Two tabs share the screen: <em>Elements</em> edits each HUD colour with
 * the HSV picker, and <em>Presets</em> applies the built-in looks taken from
 * the reference designs or saves the current look as a new preset.</p>
 */
public final class HudColorsScreen extends Screen {
    private static final int TAB_ELEMENTS = 0;
    private static final int TAB_PRESETS = 1;

    private static final int ROW_HEIGHT = 20;
    private static final int LABEL_WIDTH = 150;
    private static final int FIELD_WIDTH = 86;
    private static final int SWATCH = 12;

    private final Screen parent;
    private final SpotifyHudConfig config;
    private final String status;
    private final List<ColorEntry> entries = new ArrayList<>();

    private int tab;
    private int rowStep = ROW_HEIGHT + 2;
    private int firstRowY = 58;
    private int labelX;
    private TextFieldWidget nameField;

    public HudColorsScreen(Screen parent) {
        this(parent, TAB_ELEMENTS);
    }

    public HudColorsScreen(Screen parent, int tab) {
        this(parent, tab, "");
    }

    public HudColorsScreen(Screen parent, int tab, String status) {
        super(Text.translatable("spotifyhud.colors.title"));
        this.parent = parent;
        this.config = SpotifyHudMod.getConfig();
        this.tab = tab;
        this.status = status == null ? "" : status;
    }

    @Override
    protected void init() {
        this.entries.clear();
        this.nameField = null;

        int tabWidth = Math.min(100, Math.max(70, this.width / 4));
        int tabX = this.width / 2 - tabWidth - 3;

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("spotifyhud.colors.tab.elements"),
                        button -> MinecraftClient.getInstance().setScreen(new HudColorsScreen(this.parent, TAB_ELEMENTS)))
                .dimensions(tabX, 34, tabWidth, 18)
                .build());

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("spotifyhud.colors.tab.presets"),
                        button -> MinecraftClient.getInstance().setScreen(new HudColorsScreen(this.parent, TAB_PRESETS)))
                .dimensions(tabX + tabWidth + 6, 34, tabWidth, 18)
                .build());

        if (this.tab == TAB_ELEMENTS) {
            this.initElements();
        } else {
            this.initPresets();
        }
    }

    private void initElements() {
        this.entries.add(new ColorEntry(
                "spotifyhud.colors.title_color",
                () -> this.config.titleColor,
                value -> this.config.titleColor = value,
                () -> this.config.theme.title()));
        this.entries.add(new ColorEntry(
                "spotifyhud.colors.subtitle_color",
                () -> this.config.subtitleColor,
                value -> this.config.subtitleColor = value,
                () -> this.config.theme.subtitle()));
        this.entries.add(new ColorEntry(
                "spotifyhud.colors.lyric_active",
                () -> this.config.lyricActiveColor,
                value -> this.config.lyricActiveColor = value,
                () -> this.config.theme.accent()));
        this.entries.add(new ColorEntry(
                "spotifyhud.colors.lyric_inactive",
                () -> this.config.lyricInactiveColor,
                value -> this.config.lyricInactiveColor = value,
                () -> this.config.theme.lyricInactive()));
        this.entries.add(new ColorEntry(
                "spotifyhud.colors.progress_fill",
                () -> this.config.progressFillColor,
                value -> this.config.progressFillColor = value,
                () -> this.config.theme.accent()));
        this.entries.add(new ColorEntry(
                "spotifyhud.colors.progress_track",
                () -> this.config.progressTrackColor,
                value -> this.config.progressTrackColor = value,
                () -> this.config.theme.divider()));
        this.entries.add(new ColorEntry(
                "spotifyhud.colors.icons",
                () -> this.config.iconColor,
                value -> this.config.iconColor = value,
                () -> this.config.theme.title()));
        this.entries.add(new ColorEntry(
                "spotifyhud.colors.background",
                () -> this.config.backgroundColor,
                value -> this.config.backgroundColor = value,
                () -> this.config.theme.background()));
        this.entries.add(new ColorEntry(
                "spotifyhud.colors.divider",
                () -> this.config.dividerColor,
                value -> this.config.dividerColor = value,
                () -> this.config.theme.divider()));
        this.entries.add(new ColorEntry(
                "spotifyhud.colors.equalizer",
                () -> this.config.equalizerColor,
                value -> this.config.equalizerColor = value,
                () -> this.config.theme.accent()));

        int totalWidth = LABEL_WIDTH + FIELD_WIDTH + 8 + SWATCH;
        this.labelX = this.width / 2 - totalWidth / 2;

        int available = Math.max(80, this.height - 96 - 10);
        this.rowStep = Math.max(14, Math.min(ROW_HEIGHT + 2, available / Math.max(1, this.entries.size())));
        this.firstRowY = Math.max(46, Math.min(58, this.height - 96 - this.rowStep * this.entries.size()));

        for (int i = 0; i < this.entries.size(); i++) {
            ColorEntry entry = this.entries.get(i);
            int y = this.firstRowY + i * this.rowStep;

            this.addDrawableChild(ButtonWidget.builder(
                            Text.literal(this.effectiveHex(entry)),
                            button -> MinecraftClient.getInstance().setScreen(new ColorPickerScreen(
                                    this,
                                    Text.translatable(entry.labelKey()),
                                    entry.getter().get(),
                                    entry.setter(),
                                    entry.fallback().getAsInt())))
                    .dimensions(this.labelX + LABEL_WIDTH, y, FIELD_WIDTH, Math.min(ROW_HEIGHT, this.rowStep - 1))
                    .build());
        }

        int buttonWidth = Math.min(110, Math.max(70, (this.width - 60 - 8) / 2));
        int gap = 8;
        int total = buttonWidth * 2 + gap;
        int buttonX = (this.width - total) / 2;
        int buttonY = this.height - 26;

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("spotifyhud.colors.reset"),
                        button -> {
                            this.config.titleColor = "";
                            this.config.subtitleColor = "";
                            this.config.lyricActiveColor = "";
                            this.config.lyricInactiveColor = "";
                            this.config.progressFillColor = "";
                            this.config.progressTrackColor = "";
                            this.config.iconColor = "";
                            this.config.backgroundColor = "";
                            this.config.dividerColor = "";
                            this.config.equalizerColor = "";
                            this.config.sanitize();
                            SpotifyHudMod.saveConfig();
                            MinecraftClient.getInstance().setScreen(new HudColorsScreen(this.parent, TAB_ELEMENTS));
                        })
                .dimensions(buttonX, buttonY, buttonWidth, ROW_HEIGHT)
                .build());

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.done"),
                        button -> this.close())
                .dimensions(buttonX + buttonWidth + gap, buttonY, buttonWidth, ROW_HEIGHT)
                .build());
    }

    private void initPresets() {
        List<PresetItem> presets = new ArrayList<>();
        for (HudPresetData preset : HudPresetData.BuiltIn.all()) {
            presets.add(new PresetItem(preset, true, -1));
        }

        List<HudPresetData> userPresets = this.config.presets;
        for (int i = 0; i < userPresets.size(); i++) {
            presets.add(new PresetItem(userPresets.get(i), false, i));
        }

        int columns = this.width >= 380 ? 2 : 1;
        int columnWidth = Math.min(160, (this.width - 60 - (columns - 1) * 10) / columns);
        int rows = Math.max(1, (presets.size() + columns - 1) / columns);
        int available = Math.max(60, this.height - 104);
        this.rowStep = Math.max(14, Math.min(22, available / rows));
        this.firstRowY = 58;

        int totalWidth = columnWidth * columns + (columns - 1) * 10;
        int startX = (this.width - totalWidth) / 2;

        for (int index = 0; index < presets.size(); index++) {
            PresetItem item = presets.get(index);
            int column = index % columns;
            int row = index / columns;
            int x = startX + column * (columnWidth + 10);
            int y = this.firstRowY + row * this.rowStep;
            int rowHeight = Math.min(20, this.rowStep - 1);

            boolean hasDelete = !item.builtIn();
            int applyWidth = hasDelete ? columnWidth - 22 : columnWidth;

            this.addDrawableChild(ButtonWidget.builder(
                            Text.literal(this.presetName(item.preset())),
                            button -> this.applyPreset(item.preset()))
                    .dimensions(x, y, applyWidth, rowHeight)
                    .build());

            if (hasDelete) {
                this.addDrawableChild(ButtonWidget.builder(
                                Text.literal("\u00d7"),
                                button -> this.deletePreset(item.index()))
                        .dimensions(x + applyWidth + 2, y, 18, rowHeight)
                        .build());
            }
        }

        int nameWidth = Math.min(150, Math.max(70, this.width / 2 - 40));
        this.nameField = new TextFieldWidget(this.textRenderer,
                this.width / 2 - nameWidth - 4, this.height - 52, nameWidth, 20,
                Text.translatable("spotifyhud.presets.name"));
        this.nameField.setMaxLength(24);
        this.nameField.setPlaceholder(Text.translatable("spotifyhud.presets.name"));
        this.addDrawableChild(this.nameField);

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("spotifyhud.presets.save"),
                        button -> this.savePreset())
                .dimensions(this.width / 2 + 4, this.height - 52, nameWidth, 20)
                .build());

        int doneWidth = Math.min(120, Math.max(70, this.width / 3));
        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("gui.done"),
                        button -> this.close())
                .dimensions(this.width / 2 - doneWidth / 2, this.height - 26, doneWidth, ROW_HEIGHT)
                .build());
    }

    private void applyPreset(HudPresetData preset) {
        preset.applyTo(this.config);
        this.config.sanitize();
        SpotifyHudMod.saveConfig();
        String message = Text.translatable("spotifyhud.presets.applied",
                this.presetName(preset)).getString();
        // Re-enter the screen so the swatches and buttons reflect the preset.
        MinecraftClient.getInstance().setScreen(new HudColorsScreen(this.parent, this.tab, message));
    }

    private void savePreset() {
        String name = this.nameField == null ? "" : this.nameField.getText().trim();
        if (name.isEmpty()) {
            name = Text.translatable("spotifyhud.presets.unnamed").getString() + " " + (this.config.presets.size() + 1);
        }

        HudPresetData preset = HudPresetData.capture(this.config, name);

        // Saving under an existing name overwrites that preset.
        for (int i = 0; i < this.config.presets.size(); i++) {
            HudPresetData existing = this.config.presets.get(i);
            if (existing != null && name.equalsIgnoreCase(existing.name)) {
                this.config.presets.set(i, preset);
                SpotifyHudMod.saveConfig();
                MinecraftClient.getInstance().setScreen(new HudColorsScreen(this.parent, this.tab));
                return;
            }
        }

        this.config.presets.add(preset);
        SpotifyHudMod.saveConfig();
        MinecraftClient.getInstance().setScreen(new HudColorsScreen(this.parent, this.tab));
    }

    private void deletePreset(int index) {
        if (index >= 0 && index < this.config.presets.size()) {
            this.config.presets.remove(index);
            SpotifyHudMod.saveConfig();
            MinecraftClient.getInstance().setScreen(new HudColorsScreen(this.parent, this.tab));
        }
    }

    private String presetName(HudPresetData preset) {
        String name = preset.name == null ? "" : preset.name;
        if (name.startsWith("spotifyhud.")) {
            return Text.translatable(name).getString();
        }
        return name.isBlank() ? Text.translatable("spotifyhud.presets.unnamed").getString() : name;
    }

    private String effectiveHex(ColorEntry entry) {
        String current = entry.getter().get();
        return HudColors.isValid(current)
                ? HudColors.format(HudColors.parseRgb(current))
                : HudColors.format(entry.fallback().getAsInt());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFFFF);

        if (this.tab == TAB_ELEMENTS) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("spotifyhud.colors.hint"), this.width / 2, 24, 0xFFAAAAAA);

            for (int i = 0; i < this.entries.size(); i++) {
                ColorEntry entry = this.entries.get(i);
                int y = this.firstRowY + i * this.rowStep;
                int textY = y + (Math.min(ROW_HEIGHT, this.rowStep - 1) - 8) / 2;

                context.drawTextWithShadow(this.textRenderer,
                        Text.translatable(entry.labelKey()), this.labelX, textY, 0xFFE6E6E6);

                int swatchX = this.labelX + LABEL_WIDTH + FIELD_WIDTH + 8;
                int swatchY = y + (Math.min(ROW_HEIGHT, this.rowStep - 1) - SWATCH) / 2;
                int color = entry.effectiveColor();
                context.fill(swatchX - 1, swatchY - 1, swatchX + SWATCH + 1, swatchY + SWATCH + 1, 0xFF3A3A3A);
                context.fill(swatchX, swatchY, swatchX + SWATCH, swatchY + SWATCH, color);
            }
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.translatable("spotifyhud.presets.hint"), this.width / 2, 24, 0xFFAAAAAA);

            if (!this.status.isEmpty()) {
                context.drawCenteredTextWithShadow(this.textRenderer,
                        Text.literal(this.status), this.width / 2, this.height - 68, 0xFF1DB954);
            }
        }
    }

    @Override
    public void close() {
        SpotifyHudMod.saveConfig();
        MinecraftClient.getInstance().setScreen(this.parent);
    }

    private record PresetItem(HudPresetData preset, boolean builtIn, int index) {
    }

    private final class ColorEntry {
        private final String labelKey;
        private final Supplier<String> getter;
        private final Consumer<String> setter;
        private final IntSupplier fallback;

        private ColorEntry(String labelKey, Supplier<String> getter, Consumer<String> setter, IntSupplier fallback) {
            this.labelKey = labelKey;
            this.getter = getter;
            this.setter = setter;
            this.fallback = fallback;
        }

        private String labelKey() {
            return this.labelKey;
        }

        private Supplier<String> getter() {
            return this.getter;
        }

        private Consumer<String> setter() {
            return this.setter;
        }

        private IntSupplier fallback() {
            return this.fallback;
        }

        private int effectiveColor() {
            String current = this.getter.get();
            int rgb = HudColors.parseRgb(current);
            int base = this.fallback.getAsInt();
            return rgb < 0 ? base : ((base & 0xFF000000) | rgb);
        }
    }
}
