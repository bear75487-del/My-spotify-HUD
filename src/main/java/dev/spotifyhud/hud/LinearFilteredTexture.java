package dev.spotifyhud.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.function.Supplier;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * A {@link NativeImageBackedTexture} that samples bilinearly.
 *
 * <p>In 1.21.11 the texture filter moved from a per-texture flag into the
 * sampler owned by the texture, and {@link NativeImageBackedTexture} defaults
 * to nearest-neighbour. Replacing the sampler keeps the 1.21.1 behaviour where
 * album covers and the 64x64 HUD icons were scaled down smoothly instead of
 * looking blocky.</p>
 */
public final class LinearFilteredTexture extends NativeImageBackedTexture {
    public LinearFilteredTexture(Supplier<String> name, NativeImage image) {
        super(name, image);
        this.sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
    }
}
