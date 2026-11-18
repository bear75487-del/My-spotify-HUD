package dev.spotifyhud.hud;

/** Small colour helpers used by the renderer. */
public final class ColorUtil {
    private ColorUtil() {
    }

    public static int withOpacity(int argb, float opacity) {
        int alpha = (argb >>> 24) & 0xFF;
        int scaled = Math.round(alpha * Math.max(0.0F, Math.min(1.0F, opacity)));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    public static int scaleAlpha(int argb, float factor) {
        int alpha = (argb >>> 24) & 0xFF;
        int scaled = Math.round(alpha * Math.max(0.0F, Math.min(1.0F, factor)));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerp(int from, int to, float delta) {
        float t = Math.max(0.0F, Math.min(1.0F, delta));
        int a = lerpChannel(from >>> 24, to >>> 24, t);
        int r = lerpChannel(from >>> 16, to >>> 16, t);
        int g = lerpChannel(from >>> 8, to >>> 8, t);
        int b = lerpChannel(from, to, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, float t) {
        int start = from & 0xFF;
        int end = to & 0xFF;
        return Math.round(start + (end - start) * t) & 0xFF;
    }
}
