package dev.spotifyhud.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.spotifyhud.SpotifyHudMod;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads and persists {@link SpotifyHudConfig} as JSON inside the standard
 * Fabric config directory.
 */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path path;
    private SpotifyHudConfig config;

    public ConfigManager() {
        this.path = FabricLoader.getInstance().getConfigDir().resolve("spotifyhud.json");
    }

    /**
     * Reads the config from disk, or creates a fresh default one when the file
     * does not exist or cannot be parsed.
     */
    public SpotifyHudConfig load() {
        SpotifyHudConfig loaded = null;

        if (Files.isRegularFile(this.path)) {
            try (BufferedReader reader = Files.newBufferedReader(this.path, StandardCharsets.UTF_8)) {
                loaded = GSON.fromJson(reader, SpotifyHudConfig.class);
            } catch (IOException | RuntimeException exception) {
                SpotifyHudMod.LOGGER.warn("Could not read {}, using defaults.", this.path, exception);
            }
        }

        if (loaded == null) {
            loaded = new SpotifyHudConfig();
        }

        loaded.sanitize();
        this.config = loaded;

        // Write a file on first launch so users can discover and edit it.
        if (!Files.isRegularFile(this.path)) {
            this.save();
        }

        return loaded;
    }

    public SpotifyHudConfig get() {
        if (this.config == null) {
            return this.load();
        }
        return this.config;
    }

    public void set(SpotifyHudConfig config) {
        this.config = config;
        this.config.sanitize();
    }

    /** Saves the current config; failures are logged instead of crashing. */
    public void save() {
        if (this.config == null) {
            return;
        }

        this.config.sanitize();

        try {
            Path parent = this.path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            try (BufferedWriter writer = Files.newBufferedWriter(this.path, StandardCharsets.UTF_8)) {
                GSON.toJson(this.config, writer);
            }
        } catch (IOException | RuntimeException exception) {
            SpotifyHudMod.LOGGER.warn("Could not save {}", this.path, exception);
        }
    }

    public Path path() {
        return this.path;
    }
}
