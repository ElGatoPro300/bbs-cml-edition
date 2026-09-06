package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.render.VertexConsumer;

import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;

import com.mojang.blaze3d.vertex.VertexFormat;

import org.lwjgl.system.MemoryStack;

public class TextGlowEmissionVertexSodiumConsumer extends TextGlowEmissionVertexConsumer implements VertexBufferWriter
{
    public TextGlowEmissionVertexSodiumConsumer(VertexConsumer consumer, Color color)
    {
        super(consumer, color);
    }

    @Override
    public void push(MemoryStack memoryStack, long l, int i, VertexFormat vertexFormat)
    {
        if (this.consumer instanceof VertexBufferWriter writer)
        {
            writer.push(memoryStack, l, i, vertexFormat);
        }
    }
}
