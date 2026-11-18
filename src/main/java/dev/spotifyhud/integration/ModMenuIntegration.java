package dev.spotifyhud.integration;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.spotifyhud.gui.SpotifyHudConfigScreen;

/**
 * Mod Menu entry point. Mod Menu calls this factory when the player clicks the
 * config button for Spotify HUD; the screen itself lives outside this package
 * so the mod still works if Mod Menu is not installed.
 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SpotifyHudConfigScreen::new;
    }
}
