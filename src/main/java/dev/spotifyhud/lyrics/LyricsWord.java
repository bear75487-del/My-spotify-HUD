package dev.spotifyhud.lyrics;

/** A single karaoke word with its own start/end timestamps. */
public record LyricsWord(String text, long startMs, long endMs) {
    public float progress(long timeMs) {
        long length = this.endMs - this.startMs;
        if (length <= 0L) {
            return timeMs >= this.startMs ? 1.0F : 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (float) (timeMs - this.startMs) / (float) length));
    }
}
