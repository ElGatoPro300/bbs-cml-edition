package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;

import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;

import org.lwjgl.system.MemoryStack;

public class BlockPaintOverlayVertexSodiumConsumer extends BlockPaintOverlayVertexConsumer implements VertexBufferWriter
{
    public BlockPaintOverlayVertexSodiumConsumer(VertexConsumer consumer, Color paintColor)
    {
        super(consumer, paintColor);

        paintOverlayColor = paintColor;
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
