package dev.spotifyhud.lyrics;

import java.util.List;
import java.util.stream.Collectors;

/** One timed lyric line, optionally split into karaoke words. */
public record LyricsLine(long startMs, long endMs, List<LyricsWord> words) {
    public LyricsLine {
        words = List.copyOf(words);
    }

    public String text() {
        return this.words.stream().map(LyricsWord::text).collect(Collectors.joining(" "));
    }

    public boolean isInstrumental() {
        return this.words.isEmpty();
    }
}
