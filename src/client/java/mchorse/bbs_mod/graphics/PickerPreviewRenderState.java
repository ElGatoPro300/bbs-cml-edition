package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.BBSMod;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.UniformType;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import org.jspecify.annotations.Nullable;

public class PickerPreviewRenderState implements SimpleGuiElementRenderState
{
    /* UV1 carries the pick ID so differently highlighted previews can share a GUI batch. */
    private static final VertexFormat FORMAT = VertexFormat.builder()
        .add("Position", VertexFormatElement.POSITION)
        .add("Color", VertexFormatElement.COLOR)
        .add("UV0", VertexFormatElement.UV0)
        .add("UV1", VertexFormatElement.UV1)
        .build();

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
        RenderPipeline.builder()
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/picker_preview"))
            .withVertexShader(Identifier.of(BBSMod.MOD_ID, "core/picker_preview"))
            .withFragmentShader(Identifier.of(BBSMod.MOD_ID, "core/picker_preview"))
            .withVertexFormat(FORMAT, VertexFormat.DrawMode.QUADS)
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withSampler("Sampler0")
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build());

    private final TextureSetup textureSetup;
    private final Matrix3x2f matrix;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final int target;
    private final int highlight;
    private final ScreenRect scissor;
    private final ScreenRect bounds;

    public PickerPreviewRenderState(TextureSetup textureSetup, Matrix3x2fc matrix, int x, int y, int width, int height, int target, int highlight, @Nullable ScreenRect scissor)
    {
        this.textureSetup = textureSetup;
        this.matrix = new Matrix3x2f(matrix);
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.target = target;
        this.highlight = highlight;
        this.scissor = scissor;

        ScreenRect bounds = new ScreenRect(x, y, width, height).transformEachVertex(this.matrix);

        this.bounds = scissor == null ? bounds : bounds.intersection(scissor);
    }

    @Override
    public RenderPipeline pipeline()
    {
        return PIPELINE;
    }

    @Override
    public TextureSetup textureSetup()
    {
        return this.textureSetup;
    }

    @Override
    @Nullable
    public ScreenRect scissorArea()
    {
        return this.scissor;
    }

    @Override
    @Nullable
    public ScreenRect bounds()
    {
        return this.bounds;
    }

    @Override
    public void setupVertices(VertexConsumer vertices)
    {
        /* Picker targets use framebuffer coordinates, whose Y axis is opposite to GUI coordinates. */
        this.vertex(vertices, this.x, this.y, 0F, 1F);
        this.vertex(vertices, this.x, this.y + this.height, 0F, 0F);
        this.vertex(vertices, this.x + this.width, this.y + this.height, 1F, 0F);
        this.vertex(vertices, this.x + this.width, this.y, 1F, 1F);
    }

    private void vertex(VertexConsumer vertices, int x, int y, float u, float v)
    {
        vertices.vertex(this.matrix, x, y)
            .color(this.highlight)
            .texture(u, v)
            .overlay(this.target & 0xFFFF, (this.target >>> 16) & 0xFF);
    }
}
