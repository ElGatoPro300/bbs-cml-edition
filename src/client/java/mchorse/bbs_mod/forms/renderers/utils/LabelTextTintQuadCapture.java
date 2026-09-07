package mchorse.bbs_mod.forms.renderers.utils;

import mchorse.bbs_mod.utils.TextureFont;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Captures text glyph quads (vanilla {@link TextRenderer} or
 * {@link TextureFont}) so a FlatColorTint pass can redraw them with a
 * per-fragment spatial mask — only letter texels are tinted, with a continuous falloff.
 */
public class LabelTextTintQuadCapture implements VertexConsumerProvider, VertexConsumer
{
    public static final class GlyphQuad
    {
        public final RenderLayer layer;
        public final float x0;
        public final float y0;
        public final float x1;
        public final float y1;
        public final float x2;
        public final float y2;
        public final float x3;
        public final float y3;
        public final float u0;
        public final float v0;
        public final float u1;
        public final float v1;
        public final float u2;
        public final float v2;
        public final float u3;
        public final float v3;

        private GlyphQuad(RenderLayer layer, float[] xs, float[] ys, float[] us, float[] vs)
        {
            this.layer = layer;
            this.x0 = xs[0];
            this.y0 = ys[0];
            this.x1 = xs[1];
            this.y1 = ys[1];
            this.x2 = xs[2];
            this.y2 = ys[2];
            this.x3 = xs[3];
            this.y3 = ys[3];
            this.u0 = us[0];
            this.v0 = vs[0];
            this.u1 = us[1];
            this.v1 = vs[1];
            this.u2 = us[2];
            this.v2 = vs[2];
            this.u3 = us[3];
            this.v3 = vs[3];
        }
    }

    private final List<GlyphQuad> quads = new ArrayList<>();
    private final float[] xs = new float[4];
    private final float[] ys = new float[4];
    private final float[] us = new float[4];
    private final float[] vs = new float[4];

    private RenderLayer currentLayer;
    private float pendingX;
    private float pendingY;
    private float pendingU;
    private float pendingV;
    private int vertexIndex;

    public void clear()
    {
        this.quads.clear();
        this.vertexIndex = 0;
        this.currentLayer = null;
    }

    public List<GlyphQuad> getQuads()
    {
        return this.quads;
    }

    public List<GlyphQuad> snapshot()
    {
        return new ArrayList<>(this.quads);
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer layer)
    {
        this.flushPartialQuad();
        this.currentLayer = layer;

        return this;
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z)
    {
        this.pendingX = x;
        this.pendingY = y;

        return this;
    }

    @Override
    public VertexConsumer vertex(Matrix4f matrix, float x, float y, float z)
    {
        /* Identity / text-local capture: bake matrix so callers may pass a real stack matrix. */
        float tx = matrix.m00() * x + matrix.m10() * y + matrix.m20() * z + matrix.m30();
        float ty = matrix.m01() * x + matrix.m11() * y + matrix.m21() * z + matrix.m31();

        this.pendingX = tx;
        this.pendingY = ty;

        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha)
    {
        return this;
    }

    @Override
    public VertexConsumer color(float red, float green, float blue, float alpha)
    {
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v)
    {
        this.pendingU = u;
        this.pendingV = v;

        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v)
    {
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v)
    {
        this.finishVertex();

        return this;
    }

    @Override
    public VertexConsumer light(int light)
    {
        this.finishVertex();

        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z)
    {
        return this;
    }

    private void finishVertex()
    {
        if (this.currentLayer == null || this.vertexIndex >= 4)
        {
            return;
        }

        this.xs[this.vertexIndex] = this.pendingX;
        this.ys[this.vertexIndex] = this.pendingY;
        this.us[this.vertexIndex] = this.pendingU;
        this.vs[this.vertexIndex] = this.pendingV;
        this.vertexIndex++;

        if (this.vertexIndex >= 4)
        {
            this.quads.add(new GlyphQuad(this.currentLayer, this.xs, this.ys, this.us, this.vs));
            this.vertexIndex = 0;
        }
    }

    private void flushPartialQuad()
    {
        this.vertexIndex = 0;
    }
}
