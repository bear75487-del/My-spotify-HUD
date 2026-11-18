package dev.spotifyhud.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for standard LRC files plus the enhanced word-level
 * {@code <mm:ss.xx>} tags used by LRCLIB.
 *
 * <p>When a file has no word tags, each word receives an estimated slot based
 * on its length, which keeps the karaoke highlight moving smoothly.</p>
 */
public final class LyricsParser {
    private static final Pattern LINE_TIMESTAMP =
            Pattern.compile("\\[(\\d{1,3}):(\\d{1,2}(?:[.:]\\d{1,3})?)]");
    private static final Pattern WORD_TIMESTAMP =
            Pattern.compile("<(\\d{1,3}):(\\d{1,2}(?:[.:]\\d{1,3})?)>");
    private static final Pattern OFFSET = Pattern.compile("\\[offset:([+-]?\\d+)]");

    private static final int MAX_WORDS_PER_LINE = 96;

    private LyricsParser() {
    }

    public static LyricsDoc parse(String key, String lrc, long durationMs) {
        if (lrc == null || lrc.isBlank()) {
            return new LyricsDoc(key, List.of(), durationMs);
        }

        long offsetMs = readOffset(lrc);
        List<RawLine> rawLines = new ArrayList<>();

        for (String raw : lrc.replace("\uFEFF", "").split("\\r?\\n")) {
            Matcher matcher = LINE_TIMESTAMP.matcher(raw);
            List<Long> timestamps = new ArrayList<>();
            int cursor = 0;

            // Only accept timestamps that prefix the line, which avoids
            // treating metadata such as [ar:Artist] as a timestamp.
            while (matcher.find() && matcher.start() == cursor) {
                timestamps.add(Math.max(0L, timestampToMs(matcher.group(1), matcher.group(2)) + offsetMs));
                cursor = matcher.end();
            }

            if (timestamps.isEmpty()) {
                continue;
            }

            String text = raw.substring(cursor).trim();
            if (text.isEmpty()) {
                continue;
            }

            for (long timestamp : timestamps) {
                rawLines.add(new RawLine(timestamp, text));
            }
        }

        if (rawLines.isEmpty()) {
            return new LyricsDoc(key, List.of(), durationMs);
        }

        rawLines.sort(Comparator.comparingLong(RawLine::startMs));

        // Some files repeat the same timestamp for translations. Keep the
        // longest variant so the HUD shows the most complete line.
        List<RawLine> unique = new ArrayList<>(rawLines.size());
        for (RawLine candidate : rawLines) {
            if (!unique.isEmpty() && unique.get(unique.size() - 1).startMs() == candidate.startMs()) {
                RawLine previous = unique.get(unique.size() - 1);
                if (candidate.text().length() > previous.text().length()) {
                    unique.set(unique.size() - 1, candidate);
                }
            } else {
                unique.add(candidate);
            }
        }

        List<LyricsLine> lines = new ArrayList<>(unique.size());
        for (int i = 0; i < unique.size(); i++) {
            RawLine raw = unique.get(i);
            long start = raw.startMs();
            long end = i + 1 < unique.size()
                    ? unique.get(i + 1).startMs()
                    : Math.max(start + 4_000L, durationMs);

            if (end <= start) {
                end = start + 400L;
            }

            lines.add(new LyricsLine(start, end, buildWords(raw.text(), start, end)));
        }

        return new LyricsDoc(key, lines, durationMs);
    }

    private static long readOffset(String lrc) {
        Matcher matcher = OFFSET.matcher(lrc);
        if (!matcher.find()) {
            return 0L;
        }

        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static List<LyricsWord> buildWords(String text, long lineStart, long lineEnd) {
        String cleaned = text.replace('\uFEFF', ' ').replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return List.of();
        }

        List<Seed> seeds = new ArrayList<>();
        Matcher matcher = WORD_TIMESTAMP.matcher(text);
        int cursor = 0;
        long runStart = lineStart;
        boolean hasTags = false;

        while (matcher.find()) {
            hasTags = true;
            String chunk = text.substring(cursor, matcher.start()).trim();
            long tagTime = Math.max(lineStart, timestampToMs(matcher.group(1), matcher.group(2)));

            if (!chunk.isEmpty()) {
                addTokens(seeds, chunk, runStart, tagTime);
            }

            runStart = tagTime;
            cursor = matcher.end();
        }

        if (!hasTags) {
            addTokens(seeds, cleaned, lineStart, lineEnd);
        } else {
            String tail = text.substring(cursor).trim();
            if (!tail.isEmpty()) {
                addTokens(seeds, tail, runStart, lineEnd);
            }
            if (seeds.isEmpty()) {
                addTokens(seeds, cleaned, lineStart, lineEnd);
            }
        }

        List<LyricsWord> words = new ArrayList<>(seeds.size());
        for (int i = 0; i < seeds.size(); i++) {
            Seed seed = seeds.get(i);
            long end = i + 1 < seeds.size() ? seeds.get(i + 1).startMs : lineEnd;
            if (end <= seed.startMs) {
                end = seed.startMs + 120L;
            }
            words.add(new LyricsWord(seed.text, seed.startMs, end));
        }

        return words;
    }

    /**
     * Splits a chunk into words and spreads the available time across them
     * proportionally to their length (plus one for the following space).
     */
    private static void addTokens(List<Seed> seeds, String chunk, long from, long to) {
        String[] tokens = chunk.trim().split("\\s+");
        if (tokens.length == 0 || seeds.size() >= MAX_WORDS_PER_LINE) {
            return;
        }

        long start = Math.max(0L, from);
        long end = Math.max(start + 1L, to);

        double totalWeight = 0.0D;
        for (String token : tokens) {
            totalWeight += token.length() + 1.0D;
        }

        double consumed = 0.0D;
        long cursor = start;

        for (String token : tokens) {
            if (token.isEmpty() || seeds.size() >= MAX_WORDS_PER_LINE) {
                continue;
            }

            consumed += token.length() + 1.0D;
            long tokenEnd = start + Math.round((end - start) * (consumed / totalWeight));
            tokenEnd = Math.max(cursor, tokenEnd);

            seeds.add(new Seed(token, cursor, tokenEnd));
            cursor = tokenEnd;
        }
    }

    private static long timestampToMs(String minutes, String seconds) {
        double parsedSeconds = Double.parseDouble(seconds.replace(':', '.'));
        return Long.parseLong(minutes) * 60_000L + Math.round(parsedSeconds * 1_000.0D);
    }

    private record RawLine(long startMs, String text) {
    }

    private record Seed(String text, long startMs, long endMs) {
    }
}
