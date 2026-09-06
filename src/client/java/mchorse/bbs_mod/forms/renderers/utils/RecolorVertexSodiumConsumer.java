package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.render.VertexConsumer;

import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;

import org.lwjgl.system.MemoryStack;

/**
 * Sodium path: {@link mchorse.bbs_mod.mixin.client.sodium.ColorAttributeMixin} multiplies
 * {@link #newColor} when packing via {@link #push}. Vanilla {@code color}/{@code vertex}
 * already multiply in {@link RecolorVertexConsumer} — clear {@code newColor} for those
 * calls so Sodium 0.5.x BufferBuilder (1.20.4) does not square form opacity (vanish near
 * alpha 82/255 ≈ √0.1 discard; leaf shadows dither too fast vs solid VAO).
 */
public class RecolorVertexSodiumConsumer extends RecolorVertexConsumer implements VertexBufferWriter
{
    public RecolorVertexSodiumConsumer(VertexConsumer consumer, Color color)
    {
        this(consumer, color, null);
    }

    public RecolorVertexSodiumConsumer(VertexConsumer consumer, Color color, Color paintColor)
    {
        super(consumer, color, paintColor);

        newColor = color;
        newPaintColor = paintColor != null && paintColor.a != 0F ? paintColor : null;
    }

    @Override
    public boolean canUseIntrinsics()
    {
        return this.consumer instanceof VertexBufferWriter writer && writer.canUseIntrinsics();
    }

    @Override
    public void push(MemoryStack memoryStack, long l, int i, VertexFormatDescription vertexFormat)
    {
        if (this.consumer instanceof VertexBufferWriter writer)
        {
            writer.push(memoryStack, l, i, vertexFormat);
        }
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        Color savedColor = newColor;
        Color savedPaint = newPaintColor;

        newColor = null;
        newPaintColor = null;

        try
        {
            return super.color(red, green, blue, alpha);
        }
        finally
        {
            newColor = savedColor;
            newPaintColor = savedPaint;
        }
    }

    @Override
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float normalX, float normalY, float normalZ)
    {
        Color savedColor = newColor;
        Color savedPaint = newPaintColor;

        newColor = null;
        newPaintColor = null;

        try
        {
            super.vertex(x, y, z, red, green, blue, alpha, u, v, overlay, light, normalX, normalY, normalZ);
        }
        finally
        {
            newColor = savedColor;
            newPaintColor = savedPaint;
        }
    }
}
