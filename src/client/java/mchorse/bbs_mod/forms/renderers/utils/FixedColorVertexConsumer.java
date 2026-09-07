package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.render.VertexConsumer;

import org.joml.Matrix4f;

/**
 * VertexConsumer que fija un color constante (incluido alpha) en el
 * Buffer subyacente mediante {@link VertexConsumer#fixedColor}.
 *
 * Útil para casos donde el renderer nunca llama a {@link VertexConsumer#color},
 * como muchos Block Entity renderers; así la transparencia global se aplica
 * igualmente.
 */
public class FixedColorVertexConsumer implements VertexConsumer
{
    private final VertexConsumer delegate;
    private final Color color;
    private final int r, g, b, a;

    public FixedColorVertexConsumer(VertexConsumer delegate, Color color)
    {
        this.delegate = delegate;
        this.color = color;
        this.r = (int)(color.r * 255f);
        this.g = (int)(color.g * 255f);
        this.b = (int)(color.b * 255f);
        this.a = (int)(color.a * 255f);
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z)
    {
        return this.delegate.vertex(x, y, z).color(r, g, b, a);
    }

    @Override
    public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z)
    {
        return this.delegate.vertex(matrix, x, y, z).color(r, g, b, a);
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        return this.delegate.color(red, green, blue, alpha);
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        return this.delegate.texture(u, v);
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        return this.delegate.overlay(u, v);
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        return this.delegate.light(u, v);
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        return this.delegate.normal(x, y, z);
    }

}
