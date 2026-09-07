package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;

import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;

import org.lwjgl.system.MemoryStack;

/**
 * Guard against emission × ColorAttribute double-apply on the Sodium vertex path.
 */
public class GlowEmissionVertexSodiumConsumer extends GlowEmissionVertexConsumer implements VertexBufferWriter
{
    public GlowEmissionVertexSodiumConsumer(VertexConsumer consumer, Color color)
    {
        super(consumer, color);

        emissionColor = color;
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
        Color saved = emissionColor;

        emissionColor = null;

        try
        {
            return super.color(red, green, blue, alpha);
        }
        finally
        {
            emissionColor = saved;
        }
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha)
    {
        Color saved = emissionColor;

        emissionColor = null;

        try
        {
            return super.color(red, green, blue, alpha);
        }
        finally
        {
            emissionColor = saved;
        }
    }
}
