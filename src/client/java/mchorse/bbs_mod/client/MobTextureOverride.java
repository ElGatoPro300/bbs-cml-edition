package mchorse.bbs_mod.client;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.resources.Link;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Bridges BBS links to vanilla Identifiers so RenderLayer-based rendering
 * (including Iris shader pipelines) can use custom mob textures reliably.
 */
public class MobTextureOverride
{
    private static final ThreadLocal<Link> ACTIVE_LINK = new ThreadLocal<>();
    private static final Map<Link, Identifier> CACHE = new HashMap<>();

    public static void begin(Link link)
    {
        if (link == null)
        {
            ACTIVE_LINK.remove();
        }
        else
        {
            ACTIVE_LINK.set(link);
        }
    }

    public static void end()
    {
        ACTIVE_LINK.remove();
    }

    public static Identifier getOverridden(Identifier fallback)
    {
        Link link = ACTIVE_LINK.get();

        if (link == null)
        {
            return fallback;
        }

        Identifier id = CACHE.computeIfAbsent(link, MobTextureOverride::registerDynamicTexture);

        return id == null ? fallback : id;
    }

    private static Identifier registerDynamicTexture(Link link)
    {
        try (InputStream stream = BBSMod.getProvider().getAsset(link))
        {
            NativeImage image = NativeImage.read(stream);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            String key = "bbs_mob_override_" + Integer.toUnsignedString(link.toString().hashCode());

            return MinecraftClient.getInstance().getTextureManager().registerDynamicTexture(key, texture);
        }
        catch (Exception e)
        {
            return null;
        }
    }
}
