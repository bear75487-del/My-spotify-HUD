package dev.spotifyhud.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Minimal client for the public LRCLIB API.
 *
 * <p>The exact-match endpoints are tried first, followed by several search
 * fallbacks. Only synced lyrics are accepted so the karaoke highlight always
 * has a timeline to follow.</p>
 */
public final class LrcLibClient {
    private static final String USER_AGENT = "SpotifyHUD/1.0 (Fabric mod)";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6L))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * @return a parsed document, or {@code null} when LRCLIB has no synced
     *         lyrics for this track
     */
    public LyricsDoc fetch(String key, String artist, String title, int durationSec) throws Exception {
        String safeArtist = artist == null ? "" : artist.trim();
        String safeTitle = title == null ? "" : title.trim();
        if (safeTitle.isEmpty()) {
            return null;
        }

        String encodedArtist = encode(safeArtist);
        String encodedTitle = encode(safeTitle);
        String duration = Integer.toString(Math.max(0, durationSec));

        List<String> exactUrls = List.of(
                "https://lrclib.net/api/get-cached?artist_name=" + encodedArtist
                        + "&track_name=" + encodedTitle + "&duration=" + duration,
                "https://lrclib.net/api/get?artist_name=" + encodedArtist
                        + "&track_name=" + encodedTitle + "&duration=" + duration
        );

        for (String url : exactUrls) {
            String synced = this.requestSyncedObject(url);
            if (synced != null) {
                return LyricsParser.parse(key, synced, durationSec * 1000L);
            }
        }

        List<String> searchUrls = List.of(
                "https://lrclib.net/api/search?track_name=" + encodedTitle + "&artist_name=" + encodedArtist,
                "https://lrclib.net/api/search?q=" + encode(safeArtist + " " + safeTitle),
                "https://lrclib.net/api/search?track_name=" + encodedTitle
        );

        for (String url : searchUrls) {
            String synced = this.requestSyncedSearch(url, safeArtist, safeTitle, durationSec);
            if (synced != null) {
                return LyricsParser.parse(key, synced, durationSec * 1000L);
            }
        }

        return null;
    }

    private String requestSyncedObject(String url) throws Exception {
        String body = this.get(url);
        if (body == null) {
            return null;
        }

        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonObject()) {
            return null;
        }

        return readSynced(parsed.getAsJsonObject());
    }

    private String requestSyncedSearch(String url, String artist, String title, int durationSec) throws Exception {
        String body = this.get(url);
        if (body == null) {
            return null;
        }

        JsonElement parsed = JsonParser.parseString(body);
        if (!parsed.isJsonArray()) {
            return null;
        }

        JsonArray results = parsed.getAsJsonArray();
        JsonObject best = null;
        double bestScore = Double.MAX_VALUE;

        for (JsonElement element : results) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject candidate = element.getAsJsonObject();
            String synced = readSynced(candidate);
            if (synced == null) {
                continue;
            }

            double candidateDuration = durationSec;
            if (candidate.has("duration") && candidate.get("duration").isJsonPrimitive()) {
                try {
                    candidateDuration = candidate.get("duration").getAsDouble();
                } catch (RuntimeException ignored) {
                    // Keep the requested duration as a neutral fallback.
                }
            }

            double score = Math.abs(candidateDuration - durationSec);

            String candidateArtist = stringValue(candidate, "artistName");
            if (matches(candidateArtist, artist)) {
                score -= 10.0D;
            }

            String candidateTitle = stringValue(candidate, "trackName");
            if (matches(candidateTitle, title)) {
                score -= 3.0D;
            }

            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best == null ? null : readSynced(best);
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(7L))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            return null;
        }

        String body = response.body();
        return body == null || body.isBlank() ? null : body;
    }

    private static String readSynced(JsonObject object) {
        if (object == null || !object.has("syncedLyrics")) {
            return null;
        }

        JsonElement synced = object.get("syncedLyrics");
        if (!synced.isJsonPrimitive()) {
            return null;
        }

        String value = synced.getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private static String stringValue(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static boolean matches(String candidate, String expected) {
        if (candidate == null || expected == null) {
            return false;
        }

        String left = candidate.toLowerCase(Locale.ROOT).trim();
        String right = expected.toLowerCase(Locale.ROOT).trim();
        return !left.isEmpty() && !right.isEmpty() && (left.contains(right) || right.contains(left));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
