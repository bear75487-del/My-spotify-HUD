package dev.spotifyhud.art;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.spotifyhud.SpotifyHudMod;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * Resolves album covers through the public iTunes Search API and uploads them
 * as dynamic Minecraft textures.
 *
 * <p>Network work happens on a single daemon thread. Decoding and texture
 * registration are deferred to the client thread through {@link #drainUploads()},
 * because the texture manager is not thread-safe.</p>
 */
public final class AlbumArtService implements AutoCloseable {
    private static final int MAX_CACHED_TEXTURES = 24;
    private static final long FAILURE_COOLDOWN_MS = 30_000L;
    private static final String USER_AGENT = "SpotifyHUD/1.0 (Fabric mod)";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6L))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SpotifyHUD-Artwork");
        thread.setDaemon(true);
        return thread;
    });

    private final Queue<DownloadedArt> uploads = new ConcurrentLinkedQueue<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> failedAt = new ConcurrentHashMap<>();

    /** Client-thread-only texture cache, ordered for simple LRU eviction. */
    private final Map<String, Identifier> textures = new LinkedHashMap<>(16, 0.75F, true);

    /**
     * Returns the cover for the given media key, or {@code null} while it is
     * still loading. Triggers an asynchronous lookup on the first call.
     */
    public Identifier textureFor(String mediaKey, String artist, String title) {
        this.drainUploads();

        if (mediaKey == null || mediaKey.isBlank()) {
            return null;
        }

        Identifier ready = this.textures.get(mediaKey);
        if (ready != null) {
            return ready;
        }

        this.request(mediaKey, artist, title);
        return null;
    }

    private void request(String mediaKey, String artist, String title) {
        String safeArtist = artist == null ? "" : artist;
        String safeTitle = title == null ? "" : title;
        if ((safeArtist + safeTitle).isBlank() || this.pending.contains(mediaKey)) {
            return;
        }

        Long failed = this.failedAt.get(mediaKey);
        if (failed != null && System.currentTimeMillis() - failed < FAILURE_COOLDOWN_MS) {
            return;
        }

        this.pending.add(mediaKey);
        this.executor.submit(() -> {
            try {
                byte[] png = this.downloadCover(safeArtist, safeTitle);
                if (png != null) {
                    this.uploads.add(new DownloadedArt(mediaKey, png));
                    this.failedAt.remove(mediaKey);
                } else {
                    this.failedAt.put(mediaKey, System.currentTimeMillis());
                }
            } catch (Throwable throwable) {
                this.failedAt.put(mediaKey, System.currentTimeMillis());
                SpotifyHudMod.LOGGER.debug("Album art lookup failed for '{} - {}'.", safeArtist, safeTitle, throwable);
            } finally {
                this.pending.remove(mediaKey);
            }
        });
    }

    private byte[] downloadCover(String artist, String title) throws Exception {
        String query = ((artist == null ? "" : artist) + " " + (title == null ? "" : title)).trim();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        URI searchUri = URI.create("https://itunes.apple.com/search?term=" + encoded + "&entity=song&limit=10");
        HttpRequest searchRequest = HttpRequest.newBuilder(searchUri)
                .timeout(Duration.ofSeconds(8L))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<String> searchResponse = HTTP.send(searchRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (searchResponse.statusCode() != 200) {
            return null;
        }

        JsonObject root = JsonParser.parseString(searchResponse.body()).getAsJsonObject();
        JsonArray results = root.has("results") && root.get("results").isJsonArray()
                ? root.getAsJsonArray("results")
                : null;

        if (results == null || results.isEmpty()) {
            return null;
        }

        // The first iTunes result is not always the requested song (searching
        // "artist title" can return another track from the same album), so
        // score every candidate and keep the best title/artist match.
        JsonObject best = null;
        double bestScore = Double.MAX_VALUE;

        for (JsonElement element : results) {
            if (!element.isJsonObject()) {
                continue;
            }

            JsonObject candidate = element.getAsJsonObject();
            if (!candidate.has("artworkUrl100") || !candidate.get("artworkUrl100").isJsonPrimitive()) {
                continue;
            }

            boolean titleMatch = matches(stringValue(candidate, "trackName"), title);
            boolean artistMatch = matches(stringValue(candidate, "artistName"), artist);
            if (!titleMatch && !artistMatch) {
                continue;
            }

            double score = (titleMatch ? 0.0D : 60.0D) + (artistMatch ? 0.0D : 25.0D);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best == null) {
            return null;
        }

        String artUrl = best.get("artworkUrl100").getAsString().replace("100x100bb", "600x600bb");
        HttpRequest artRequest = HttpRequest.newBuilder(URI.create(artUrl))
                .timeout(Duration.ofSeconds(8L))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        HttpResponse<byte[]> artResponse = HTTP.send(artRequest, HttpResponse.BodyHandlers.ofByteArray());
        if (artResponse.statusCode() != 200 || artResponse.body().length == 0) {
            return null;
        }

        return artResponse.body();
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

    /**
     * Decodes queued covers and registers them as textures. Must be called on
     * the client thread; called once per HUD frame and once per client tick.
     */
    public void drainUploads() {
        DownloadedArt art;
        while ((art = this.uploads.poll()) != null) {
            this.register(art);
        }
    }

    private void register(DownloadedArt art) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(art.png())) {
            // iTunes serves JPEG covers, and NativeImage.read only accepts PNG,
            // so decode with ImageIO and copy the pixels into a NativeImage.
            BufferedImage decoded = ImageIO.read(input);
            if (decoded == null) {
                SpotifyHudMod.LOGGER.debug("Unsupported album cover format.");
                return;
            }

            NativeImage image = toNativeImage(decoded);
            roundCorners(image);
            Identifier identifier = Identifier.of(
                    SpotifyHudMod.MOD_ID,
                    "album_art/" + Integer.toUnsignedString(art.mediaKey().hashCode(), 16)
            );

            MinecraftClient client = MinecraftClient.getInstance();
            Identifier previous = this.textures.remove(art.mediaKey());
            if (previous != null) {
                client.getTextureManager().destroyTexture(previous);
            }

            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            // Bilinear filtering is essential here: a 600x600 cover is drawn
            // into a 44x44 area, and nearest-neighbour sampling would make it
            // look blocky at every GUI scale.
            texture.setFilter(true, false);
            client.getTextureManager().registerTexture(identifier, texture);
            this.textures.put(art.mediaKey(), identifier);
            this.evictOldTextures();
        } catch (Throwable throwable) {
            SpotifyHudMod.LOGGER.debug("Could not register an album cover texture.", throwable);
        }
    }

    /**
     * Copies a decoded {@link BufferedImage} into an RGBA NativeImage.
     *
     * <p>{@code NativeImage.setColor} writes the raw integer into RGBA memory,
     * which means the integer is ABGR on little-endian systems. BufferedImage
     * hands out ARGB, so the red and blue channels have to be swapped.</p>
     */
    private static NativeImage toNativeImage(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        NativeImage image = new NativeImage(width, height, false);
        int[] pixels = source.getRGB(0, 0, width, height, null, 0, width);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setColor(x, y, toAbgr(pixels[y * width + x]));
            }
        }

        return image;
    }

    private static int toAbgr(int argb) {
        return (argb & 0xFF00FF00)
                | ((argb & 0x000000FF) << 16)
                | ((argb & 0x00FF0000) >>> 16);
    }

    /**
     * Rounds the corners of the decoded cover with an anti-aliased alpha mask.
     * Doing this on the source image (instead of masking on screen) keeps the
     * corners clean when the texture is downscaled to the HUD's art size.
     */
    private static void roundCorners(NativeImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }

        int radius = Math.max(2, Math.round(Math.min(width, height) * 0.12F));
        for (int y = 0; y < radius; y++) {
            for (int x = 0; x < radius; x++) {
                double coverage = cornerCoverage(x, y, radius);
                if (coverage >= 1.0D) {
                    continue;
                }

                applyCorner(image, x, y, width, height, coverage);
                applyCorner(image, width - 1 - x, y, width, height, coverage);
                applyCorner(image, x, height - 1 - y, width, height, coverage);
                applyCorner(image, width - 1 - x, height - 1 - y, width, height, coverage);
            }
        }
    }

    private static double cornerCoverage(int x, int y, int radius) {
        double dx = x + 0.5D - radius;
        double dy = y + 0.5D - radius;
        double distance = Math.sqrt(dx * dx + dy * dy);
        return Math.max(0.0D, Math.min(1.0D, radius + 0.5D - distance));
    }

    private static void applyCorner(NativeImage image, int x, int y, int width, int height, double coverage) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }

        int color = image.getColor(x, y);
        int alpha = (color >>> 24) & 0xFF;
        int maskedAlpha = (int) Math.round(alpha * coverage);
        image.setColor(x, y, (color & 0x00FFFFFF) | (maskedAlpha << 24));
    }

    private void evictOldTextures() {
        if (this.textures.size() <= MAX_CACHED_TEXTURES) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Iterator<Map.Entry<String, Identifier>> iterator = this.textures.entrySet().iterator();
        while (this.textures.size() > MAX_CACHED_TEXTURES && iterator.hasNext()) {
            Identifier identifier = iterator.next().getValue();
            iterator.remove();
            client.getTextureManager().destroyTexture(identifier);
        }
    }

    @Override
    public void close() {
        this.executor.shutdownNow();
        this.uploads.clear();

        MinecraftClient client = MinecraftClient.getInstance();
        for (Identifier identifier : this.textures.values()) {
            client.getTextureManager().destroyTexture(identifier);
        }
        this.textures.clear();
    }

    private record DownloadedArt(String mediaKey, byte[] png) {
    }
}
