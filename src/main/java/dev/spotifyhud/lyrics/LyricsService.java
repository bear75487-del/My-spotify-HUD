package dev.spotifyhud.lyrics;

import dev.spotifyhud.SpotifyHudMod;
import dev.spotifyhud.playback.TrackInfo;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Caches lyrics, coalesces in-flight requests, and retries failed lookups
 * after a cool-down. Rendering code only ever calls {@link #docFor(TrackInfo)},
 * which never blocks.
 */
public final class LyricsService implements AutoCloseable {
    private static final int MAX_CACHE_SIZE = 48;
    private static final long FAILURE_RETRY_MS = 60_000L;

    private final LrcLibClient client = new LrcLibClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SpotifyHUD-Lyrics");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, LyricsDoc> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> failedAt = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    /** @return parsed lyrics for the current track, or {@code null} while loading/unavailable */
    public LyricsDoc docFor(TrackInfo track) {
        if (track == null || !track.hasTrack() || !track.hasDuration()) {
            return null;
        }

        String key = track.mediaKey();
        LyricsDoc cached = this.cache.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        Long failed = this.failedAt.get(key);
        if (failed != null && System.currentTimeMillis() - failed < FAILURE_RETRY_MS) {
            return null;
        }

        if (this.pending.add(key)) {
            String artist = track.artist();
            String title = track.title();
            int durationSec = track.durationSec();

            this.executor.submit(() -> {
                try {
                    LyricsDoc loaded = this.client.fetch(key, artist, title, durationSec);
                    if (loaded != null && !loaded.isEmpty()) {
                        this.cache.put(key, loaded);
                        this.failedAt.remove(key);
                        this.trimCache();
                    } else {
                        this.failedAt.put(key, System.currentTimeMillis());
                    }
                } catch (Throwable throwable) {
                    this.failedAt.put(key, System.currentTimeMillis());
                    SpotifyHudMod.LOGGER.debug("Lyrics lookup failed for '{} - {}'.", artist, title, throwable);
                } finally {
                    this.pending.remove(key);
                }
            });
        }

        return null;
    }

    public boolean hasDoc(TrackInfo track) {
        return this.docFor(track) != null;
    }

    private void trimCache() {
        if (this.cache.size() <= MAX_CACHE_SIZE) {
            return;
        }

        Iterator<String> iterator = this.cache.keySet().iterator();
        while (this.cache.size() > MAX_CACHE_SIZE && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    @Override
    public void close() {
        this.executor.shutdownNow();
        this.cache.clear();
        this.failedAt.clear();
        this.pending.clear();
    }
}
