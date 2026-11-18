package dev.spotifyhud.playback;

import java.util.Locale;

/**
 * Immutable snapshot of the active Spotify track.
 *
 * <p>{@code baseElapsedMs} is the playback position at {@code sampledAtMs}. For
 * a playing track the live position is extrapolated from those two values, so
 * the progress bar stays smooth even though the Windows media session is only
 * polled a few times per second.</p>
 */
public record TrackInfo(
        String title,
        String artist,
        int durationSec,
        long baseElapsedMs,
        long sampledAtMs,
        boolean playing
) {
    public static final TrackInfo EMPTY = new TrackInfo("", "", 0, 0L, 0L, false);

    public boolean hasTrack() {
        return !this.title.isBlank() || !this.artist.isBlank();
    }

    public boolean hasDuration() {
        return this.durationSec > 0;
    }

    /** Current playback position in milliseconds, clamped to the track length. */
    public long elapsedMs(long nowMs) {
        long elapsed = this.baseElapsedMs;
        if (this.playing) {
            elapsed += Math.max(0L, nowMs - this.sampledAtMs);
        }

        elapsed = Math.max(0L, elapsed);
        if (this.hasDuration()) {
            elapsed = Math.min(elapsed, this.durationSec * 1000L);
        }
        return elapsed;
    }

    public float progress(long nowMs) {
        if (!this.hasDuration()) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, (float) this.elapsedMs(nowMs) / (this.durationSec * 1000.0F)));
    }

    /**
     * Stable cache key shared by the artwork and lyrics services. Spotify
     * metadata is not normalised by the media API, so lower-casing everything
     * avoids redundant network calls when only capitalisation changes.
     */
    public String mediaKey() {
        return (this.artist + "\u001f" + this.title).toLowerCase(Locale.ROOT).trim();
    }

    public TrackInfo withPosition(long elapsedMs, boolean nowPlaying, long nowMs) {
        return new TrackInfo(
                this.title,
                this.artist,
                this.durationSec,
                Math.max(0L, elapsedMs),
                nowMs,
                nowPlaying
        );
    }

    /** Content equality that ignores the constantly changing clock fields. */
    public boolean sameTrack(TrackInfo other) {
        return other != null
                && this.title.equals(other.title)
                && this.artist.equals(other.artist)
                && this.durationSec == other.durationSec;
    }
}
