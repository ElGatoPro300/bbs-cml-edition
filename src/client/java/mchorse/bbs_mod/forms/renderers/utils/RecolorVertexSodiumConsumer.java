package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.mixin.client.sodium.ColorAttributeMixin;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;

import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;

import org.lwjgl.system.MemoryStack;

/**
 * Sodium path: {@link ColorAttributeMixin} multiplies
 * {@link #newColor} when packing via {@link #push}. Vanilla {@code color} already multiplies
 * in {@link RecolorVertexConsumer} — clear {@code newColor} for those calls so BufferBuilder
 * does not square form opacity (vanish near alpha 82/255; leaf shadows dither too fast vs solid VAO).
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
    public void push(MemoryStack memoryStack, long l, int i, VertexFormat vertexFormat)
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
}
