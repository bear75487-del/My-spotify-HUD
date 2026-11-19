package dev.spotifyhud;

import dev.spotifyhud.art.AlbumArtService;
import dev.spotifyhud.config.ConfigManager;
import dev.spotifyhud.config.SpotifyHudConfig;
import dev.spotifyhud.gui.HudPositionScreen;
import dev.spotifyhud.gui.SpotifyHudConfigScreen;
import dev.spotifyhud.hud.SpotifyHudRenderer;
import dev.spotifyhud.lyrics.LyricsService;
import dev.spotifyhud.playback.PlaybackTracker;
import dev.spotifyhud.playback.WindowsMediaDetector;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entry point.
 *
 * <p>All Spotify-specific state lives in this mod: configuration, playback
 * tracking, artwork, lyrics, and the HUD renderer. No code from the original
 * client base is referenced.</p>
 */
public final class SpotifyHudMod implements ClientModInitializer {
    public static final String MOD_ID = "spotifyhud";
    public static final Logger LOGGER = LoggerFactory.getLogger("SpotifyHUD");

    private static ConfigManager configManager;
    private static SpotifyHudConfig config;
    private static PlaybackTracker playback;
    private static LyricsService lyrics;
    private static AlbumArtService albumArt;
    private static WindowsMediaDetector detector;
    private static SpotifyHudRenderer renderer;
    private static KeyBinding openConfigKey;
    private static boolean shuttingDown;

    @Override
    public void onInitializeClient() {
        configManager = new ConfigManager();
        config = configManager.load();

        playback = new PlaybackTracker();
        lyrics = new LyricsService();
        albumArt = new AlbumArtService();
        renderer = new SpotifyHudRenderer(config, lyrics, albumArt);
        detector = new WindowsMediaDetector(playback);

        if (!detector.isSupported()) {
            LOGGER.info("Auto Detect requires Windows 10/11. The HUD will remain idle on this platform.");
        }

        openConfigKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "spotifyhud.key.open_config",
                InputUtil.Type.KEYSYM,
                InputUtil.UNKNOWN_KEY.getCode(),
                KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))
        ));

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!config.enabled || client.player == null || client.options.hudHidden) {
                return;
            }

            // The position editor draws its own copy while it is open.
            if (client.currentScreen instanceof HudPositionScreen) {
                return;
            }

            renderer.render(context, client, playback.current());
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            albumArt.drainUploads();

            boolean shouldDetect = config.enabled && config.autoDetect;
            detector.tick(shouldDetect);

            while (openConfigKey != null && openConfigKey.wasPressed()) {
                openConfig(client);
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> shutdown());
    }

    public static void openConfig(MinecraftClient client) {
        if (client == null) {
            return;
        }
        client.setScreen(new SpotifyHudConfigScreen(client.currentScreen));
    }

    public static void saveConfig() {
        if (configManager != null) {
            configManager.save();
        }
    }

    private static synchronized void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;

        if (detector != null) {
            detector.close();
        }
        if (albumArt != null) {
            albumArt.close();
        }
        if (lyrics != null) {
            lyrics.close();
        }
        if (configManager != null) {
            configManager.save();
        }
    }

    public static SpotifyHudConfig getConfig() {
        return config;
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }

    public static PlaybackTracker getPlayback() {
        return playback;
    }

    public static LyricsService getLyrics() {
        return lyrics;
    }

    public static AlbumArtService getAlbumArt() {
        return albumArt;
    }

    public static SpotifyHudRenderer getRenderer() {
        return renderer;
    }

    public static WindowsMediaDetector getDetector() {
        return detector;
    }
}
