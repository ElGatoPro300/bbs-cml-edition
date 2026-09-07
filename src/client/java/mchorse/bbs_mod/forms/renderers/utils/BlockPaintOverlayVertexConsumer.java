package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;

import org.joml.Matrix4f;

public class BlockPaintOverlayVertexConsumer implements VertexConsumer
{
    public static Color paintOverlayColor;

    protected VertexConsumer consumer;
    protected Color paintColor;
    protected float strength;

    public BlockPaintOverlayVertexConsumer(VertexConsumer consumer, Color paintColor)
    {
        this.consumer = consumer;
        this.paintColor = paintColor;
        this.strength = paintColor.a;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z)
    {
        return this.consumer.vertex(x, y, z);
    }

    @Override
    public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z)
    {
        return this.consumer.vertex(matrix, x, y, z);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        int r = MathUtils.clamp((int) (this.paintColor.r * 255F), 0, 255);
        int g = MathUtils.clamp((int) (this.paintColor.g * 255F), 0, 255);
        int b = MathUtils.clamp((int) (this.paintColor.b * 255F), 0, 255);
        int a = MathUtils.clamp((int) (this.strength * alpha), 0, 255);

        return this.consumer.color(r, g, b, a);
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        return this.consumer.texture(u, v);
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        return this.consumer.overlay(u, v);
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        return this.consumer.light(LightmapTextureManager.MAX_LIGHT_COORDINATE);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        return this.consumer.normal(x, y, z);
    }
}
