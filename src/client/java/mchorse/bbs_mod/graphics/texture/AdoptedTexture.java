package mchorse.bbs_mod.graphics.texture;

import mchorse.bbs_mod.BBSMod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.GlTextureView;
import net.minecraft.util.Identifier;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Bridges a BBS raw-GL Texture into the vanilla two-phase GUI so
 * DrawContext.drawTexture can sample it. Zero-copy: adopts the GL id.
 */
public final class AdoptedTexture extends AbstractTexture
{
    private static final int USAGE = GpuTexture.USAGE_TEXTURE_BINDING;

    private static final Map<Texture, Cached> REGISTRY = new WeakHashMap<>();
    private static final Map<Integer, Cached> GLID_REGISTRY = new HashMap<>();
    private static int counter;

    private static final class Cached
    {
        private final Identifier id;
        private final int width;
        private final int height;

        private Cached(Identifier id, int width, int height)
        {
            this.id = id;
            this.width = width;
            this.height = height;
        }
    }

    public static Identifier identifier(Texture texture)
    {
        if (texture == null || !texture.isValid())
        {
            return null;
        }

        int width = Math.max(1, texture.width);
        int height = Math.max(1, texture.height);
        Cached cached = REGISTRY.get(texture);

        if (cached != null && cached.width == width && cached.height == height)
        {
            return cached.id;
        }

        Identifier id = cached != null ? cached.id : Identifier.of(BBSMod.MOD_ID, "adopted/" + (counter++));

        MinecraftClient.getInstance().getTextureManager().registerTexture(id,
            new AdoptedTexture(texture.id, "bbs_adopted_" + texture.id, width, height, texture.isLinear()));
        REGISTRY.put(texture, new Cached(id, width, height));

        return id;
    }

    public static Identifier identifier(int glId, int width, int height, boolean linear)
    {
        if (glId < 0)
        {
            return null;
        }

        int safeW = Math.max(1, width);
        int safeH = Math.max(1, height);
        Cached cached = GLID_REGISTRY.get(glId);

        if (cached != null && cached.width == safeW && cached.height == safeH)
        {
            return cached.id;
        }

        Identifier id = cached != null ? cached.id : Identifier.of(BBSMod.MOD_ID, "adopted/glid_" + glId);

        MinecraftClient.getInstance().getTextureManager().registerTexture(id,
            new AdoptedTexture(glId, "bbs_adopted_glid_" + glId, safeW, safeH, linear));
        GLID_REGISTRY.put(glId, new Cached(id, safeW, safeH));

        return id;
    }

    private AdoptedTexture(int glId, String label, int width, int height, boolean linear)
    {
        AdoptedGlTexture glTexture = new AdoptedGlTexture(glId, label, width, height);

        this.glTexture = glTexture;
        this.glTextureView = new AdoptedGlTextureView(glTexture);

        FilterMode filter = linear ? FilterMode.LINEAR : FilterMode.NEAREST;

        this.sampler = RenderSystem.getSamplerCache().get(
            AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, filter, filter, false);
    }

    @Override
    public void close()
    {
    }

    private static final class AdoptedGlTexture extends GlTexture
    {
        private AdoptedGlTexture(int glId, String label, int width, int height)
        {
            super(USAGE, label, TextureFormat.RGBA8,
                Math.max(1, width), Math.max(1, height), 1, 1, glId);
        }

        @Override
        public void close()
        {
        }
    }

    private static final class AdoptedGlTextureView extends GlTextureView
    {
        private AdoptedGlTextureView(AdoptedGlTexture texture)
        {
            super(texture, 0, 1);
        }

        @Override
        public void close()
        {
        }
    }
}
