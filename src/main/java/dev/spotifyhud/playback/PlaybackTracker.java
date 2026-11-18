package dev.spotifyhud.playback;

/**
 * Thread-safe holder for the current track. The detector thread writes, while
 * the render thread reads; the record itself is immutable so only the
 * reference needs to be volatile.
 */
public final class PlaybackTracker {
    private volatile TrackInfo current = TrackInfo.EMPTY;

    public TrackInfo current() {
        return this.current;
    }

    /**
     * Called by the Windows media-session reader. All values come directly
     * from the operating system, including the extrapolated position.
     */
    public void updateFromDetector(String title, String artist, int durationSec, long elapsedMs, boolean playing) {
        String safeTitle = title == null ? "" : title.trim();
        String safeArtist = artist == null ? "" : artist.trim();

        if (safeTitle.isEmpty() && safeArtist.isEmpty()) {
            return;
        }

        int safeDuration = Math.max(0, durationSec);
        long safeElapsed = Math.max(0L, elapsedMs);
        if (safeDuration > 0) {
            safeElapsed = Math.min(safeElapsed, safeDuration * 1000L);
        }

        TrackInfo previous = this.current;
        long now = System.currentTimeMillis();

        // Avoid pointless allocations when the media session repeats itself.
        if (previous.sameTrack(new TrackInfo(safeTitle, safeArtist, safeDuration, 0L, 0L, playing))
                && Math.abs(previous.elapsedMs(now) - safeElapsed) < 750L
                && previous.playing() == playing) {
            return;
        }

        this.current = new TrackInfo(safeTitle, safeArtist, safeDuration, safeElapsed, now, playing);
    }

    /** Clears the overlay back to the idle placeholder. */
    public void clear() {
        this.current = TrackInfo.EMPTY;
    }
}
