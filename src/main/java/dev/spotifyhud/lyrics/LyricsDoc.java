package dev.spotifyhud.lyrics;

import java.util.List;

/** Parsed lyrics for one song. */
public record LyricsDoc(String key, List<LyricsLine> lines, long durationMs) {
    public LyricsDoc {
        lines = List.copyOf(lines);
    }

    public boolean isEmpty() {
        return this.lines.isEmpty();
    }

    /**
     * Returns the index of the last line whose start time is at or before
     * {@code timeMs}. Returns 0 before the first line and -1 when empty.
     */
    public int indexAt(long timeMs) {
        if (this.lines.isEmpty()) {
            return -1;
        }

        int low = 0;
        int high = this.lines.size() - 1;
        int result = 0;

        while (low <= high) {
            int middle = (low + high) >>> 1;
            LyricsLine line = this.lines.get(middle);
            if (line.startMs() <= timeMs) {
                result = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }

        return result;
    }
}
