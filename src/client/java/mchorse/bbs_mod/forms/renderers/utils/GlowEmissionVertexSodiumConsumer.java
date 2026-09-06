package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.render.VertexConsumer;

import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;

import org.lwjgl.system.MemoryStack;

/**
 * Guard against emission × ColorAttribute double-apply on Sodium 0.5.x (1.20.4).
 */
public class GlowEmissionVertexSodiumConsumer extends GlowEmissionVertexConsumer implements VertexBufferWriter
{
    public GlowEmissionVertexSodiumConsumer(VertexConsumer consumer, Color color)
    {
        super(consumer, color);

        emissionColor = color;
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
