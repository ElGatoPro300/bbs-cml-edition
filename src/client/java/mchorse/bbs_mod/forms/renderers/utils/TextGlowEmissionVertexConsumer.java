package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumer;

import org.joml.Matrix4f;

/**
 * Glow overlay for text geometry. Multiplies glow emission with per-vertex text tint
 * so label glow matches the text color (custom fonts use {@code color(float)}).
 */
public class TextGlowEmissionVertexConsumer implements VertexConsumer
{
    protected VertexConsumer consumer;
    protected Color color;

    public TextGlowEmissionVertexConsumer(VertexConsumer consumer, Color color)
    {
        this.consumer = consumer;
        this.color = color;
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
        int r = MathUtils.clamp((int) (this.color.r * red), 0, 255);
        int g = MathUtils.clamp((int) (this.color.g * green), 0, 255);
        int b = MathUtils.clamp((int) (this.color.b * blue), 0, 255);
        int a = MathUtils.clamp((int) (this.color.a * alpha), 0, 255);

        return this.consumer.color(r, g, b, a);
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha)
    {
        float r = MathUtils.clamp(this.color.r * red, 0F, 1F);
        float g = MathUtils.clamp(this.color.g * green, 0F, 1F);
        float b = MathUtils.clamp(this.color.b * blue, 0F, 1F);
        float a = MathUtils.clamp(this.color.a * alpha, 0F, 1F);

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
