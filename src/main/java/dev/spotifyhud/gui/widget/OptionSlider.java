package dev.spotifyhud.gui.widget;

import dev.spotifyhud.hud.RoundedRect;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

/**
 * A generic float slider with a label on the left and its value on the right.
 *
 * <p>Vanilla's {@link SliderWidget} centres its label and draws the handle on
 * top of it, which makes the value unreadable at common GUI scales. This
 * subclass keeps the vanilla drag behaviour but replaces the widget painting:
 * the label and value sit in the padded corners and a thin progress track runs
 * along the bottom edge, so nothing ever overlaps the text.</p>
 *
 * <p>Saving is delayed until the mouse is released so dragging does not cause
 * a disk write per frame.</p>
 */
public final class OptionSlider extends SliderWidget {
    private static final int TEXT_PADDING = 7;
    private static final int TEXT_COLOR = 0xFFE6E6E6;
    private static final int VALUE_COLOR = 0xFF1DB954;
    private static final int TEXT_COLOR_DISABLED = 0xFF7A7A7A;
    private static final int BORDER_COLOR = 0xFF3A3A3A;
    private static final int TRACK_COLOR = 0xFF3A3A3A;
    private static final int FILL_COLOR = 0xFF1DB954;

    private final Text label;
    private final float minimum;
    private final float maximum;
    private final Consumer<Float> onChange;
    private final Function<Float, String> formatter;
    private final Runnable onCommit;

    public OptionSlider(
            int x,
            int y,
            int width,
            int height,
            Text label,
            float minimum,
            float maximum,
            float value,
            Consumer<Float> onChange,
            Function<Float, String> formatter,
            Runnable onCommit
    ) {
        super(x, y, width, height, label, normalize(value, minimum, maximum));
        this.label = label;
        this.minimum = minimum;
        this.maximum = maximum;
        this.onChange = onChange;
        this.formatter = formatter;
        this.onCommit = onCommit;
        this.updateMessage();
    }

    private static double normalize(float value, float minimum, float maximum) {
        if (maximum <= minimum) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, (value - minimum) / (maximum - minimum)));
    }

    private float currentValue() {
        return this.minimum + (float) this.value * (this.maximum - this.minimum);
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Text.empty()
                .append(this.label)
                .append(Text.literal(": " + this.formatter.apply(this.currentValue()))));
    }

    @Override
    protected void applyValue() {
        this.onChange.accept(this.currentValue());
    }

    /**
     * Custom painting: rounded background, label, value, and a two-pixel
     * progress track pinned to the bottom edge of the widget.
     */
    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        int x = this.getX();
        int y = this.getY();
        int width = this.getWidth();
        int height = this.getHeight();

        int background;
        if (!this.active) {
            background = 0xFF141414;
        } else if (this.isFocused()) {
            background = 0xFF2E2E2E;
        } else if (this.isHovered()) {
            background = 0xFF2A2A2A;
        } else {
            background = 0xFF1F1F1F;
        }

        RoundedRect.fill(context, x, y, width, height, 4, background);
        // One-pixel border drawn as four thin rectangles; a rounded rect is not
        // needed at this size and keeps the widget cheap to draw.
        context.fill(x, y, x + width, y + 1, BORDER_COLOR);
        context.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR);
        context.fill(x, y, x + 1, y + height, BORDER_COLOR);
        context.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR);

        var textRenderer = MinecraftClient.getInstance().textRenderer;
        int textY = y + (height - textRenderer.fontHeight) / 2 - 1;

        String valueText = this.formatter.apply(this.currentValue());
        int valueWidth = textRenderer.getWidth(valueText);
        int valueX = x + width - TEXT_PADDING - valueWidth;

        int labelColor = this.active ? TEXT_COLOR : TEXT_COLOR_DISABLED;
        int available = Math.max(0, valueX - TEXT_PADDING - (x + TEXT_PADDING) - 4);
        Text labelText = this.label;
        if (textRenderer.getWidth(labelText) > available) {
            labelText = Text.literal(textRenderer.trimToWidth(labelText.getString(), available));
        }

        context.drawTextWithShadow(textRenderer, labelText, x + TEXT_PADDING, textY, labelColor);
        context.drawTextWithShadow(textRenderer, valueText, valueX, textY, this.active ? VALUE_COLOR : TEXT_COLOR_DISABLED);

        // Progress track. It sits below the text baseline and spans the inner
        // width, so it reads as a slider without touching either label.
        int trackY = y + height - 4;
        int trackX = x + TEXT_PADDING;
        int trackWidth = width - TEXT_PADDING * 2;
        if (trackWidth > 0) {
            context.fill(trackX, trackY, trackX + trackWidth, trackY + 2, TRACK_COLOR);
            int filled = (int) Math.round(trackWidth * this.value);
            if (filled > 0) {
                context.fill(trackX, trackY, trackX + filled, trackY + 2, FILL_COLOR);
            }

            // Small handle marker at the current position, kept inside the
            // track so it can never cover the text above.
            int handleX = trackX + Math.max(0, Math.min(trackWidth - 3, filled - 1));
            context.fill(handleX, trackY - 1, handleX + 3, trackY + 3, FILL_COLOR);
        }
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        super.onRelease(mouseX, mouseY);
        this.onCommit.run();
    }
}
