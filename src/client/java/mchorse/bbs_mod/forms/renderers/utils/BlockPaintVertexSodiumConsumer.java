package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;

import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;

import org.lwjgl.system.MemoryStack;

/**
 * Same Sodium double-tint guard as {@link RecolorVertexSodiumConsumer}.
 * Negative paint uses this consumer — without the guard, leaf/cutout shadow Bayer sees opacity².
 */
public class BlockPaintVertexSodiumConsumer extends BlockPaintVertexConsumer implements VertexBufferWriter
{
    public BlockPaintVertexSodiumConsumer(VertexConsumer consumer, Color color, Color paintColor)
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

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha)
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
