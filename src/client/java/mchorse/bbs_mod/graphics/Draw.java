package mchorse.bbs_mod.graphics;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.iris.IrisFormPipelines;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix4f;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class Draw
{
    private static final float[] SCRATCH_COS_U = new float[128];
    private static final float[] SCRATCH_SIN_U = new float[128];
    private static final float[] SCRATCH_COS_V = new float[128];
    private static final float[] SCRATCH_SIN_V = new float[128];

    private static final BlendFunction BLEND = BlendFunction.TRANSLUCENT;

    private static final RenderPipeline POSITION_COLOR_TRIS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/draw_position_color"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_TRIS_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/draw_position_color_no_depth"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_LINES = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/draw_position_color_lines"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
            .withDepthWrite(true)
            .withCull(false)
            .build()
    );

    private static RenderLayer positionColorLayer;
    private static RenderLayer positionColorNoDepthLayer;
    private static RenderLayer positionColorLinesLayer;

    static
    {
        if (BBSRendering.isIrisLoaded())
        {
            IrisFormPipelines.registerColor(POSITION_COLOR_TRIS);
            IrisFormPipelines.registerColor(POSITION_COLOR_TRIS_NO_DEPTH);
            IrisFormPipelines.registerColor(POSITION_COLOR_LINES);
        }
    }

    public static RenderLayer getPositionColorLayer()
    {
        if (positionColorLayer == null)
        {
            positionColorLayer = RenderLayer.of(BBSMod.MOD_ID + "_draw_position_color",
                RenderSetup.builder(POSITION_COLOR_TRIS).translucent().build());
        }

        return positionColorLayer;
    }

    public static RenderLayer getPositionColorNoDepthLayer()
    {
        if (positionColorNoDepthLayer == null)
        {
            positionColorNoDepthLayer = RenderLayer.of(BBSMod.MOD_ID + "_draw_position_color_no_depth",
                RenderSetup.builder(POSITION_COLOR_TRIS_NO_DEPTH).translucent().build());
        }

        return positionColorNoDepthLayer;
    }

    public static RenderLayer getPositionColorLinesLayer()
    {
        if (positionColorLinesLayer == null)
        {
            positionColorLinesLayer = RenderLayer.of(BBSMod.MOD_ID + "_draw_position_color_lines",
                RenderSetup.builder(POSITION_COLOR_LINES).translucent().build());
        }

        return positionColorLinesLayer;
    }

    public static void flushLines(BufferBuilder builder)
    {
        flush(builder, getPositionColorLinesLayer());
    }

    public static void flush(BufferBuilder builder, RenderLayer layer)
    {
        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            layer.draw(built);
        }
    }

    public static void renderBox(MatrixStack stack, double x, double y, double z, double w, double h, double d)
    {
        renderBox(stack, x, y, z, w, h, d, 1F, 1F, 1F);
    }

    public static void renderBox(MatrixStack stack, double x, double y, double z, double w, double h, double d, float r, float g, float b)
    {
        renderBox(stack, x, y, z, w, h, d, r, g, b, 1F);
    }

    public static void renderBox(MatrixStack stack, double x, double y, double z, double w, double h, double d, float r, float g, float b, float a)
    {
        /* Iris TAA turns lines/alpha into stipple during the world pass. Queue solid edges for LAST.
         * Skip the shadow map — those passes leave a different MV and would spawn sky ghosts. */
        if (BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld())
        {
            if (!BBSRendering.isIrisShadowPass())
            {
                enqueueIrisBox(stack, x, y, z, w, h, d, r, g, b);
            }

            return;
        }

        stack.push();
        stack.translate(x, y, z);
        float fw = (float) w;
        float fh = (float) h;
        float fd = (float) d;
        float t = 1 / 96F + (float) (Math.sqrt(w * w + h + h + d + d) / 2000);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        /* Pillars: fillBox(builder, -t, -t, -t, t, t, t, r, g, b, a); */
        fillBox(builder, stack, -t, -t, -t, t, t + fh, t, r, g, b, a);
        fillBox(builder, stack, -t + fw, -t, -t, t + fw, t + fh, t, r, g, b, a);
        fillBox(builder, stack, -t, -t, -t + fd, t, t + fh, t + fd, r, g, b, a);
        fillBox(builder, stack, -t + fw, -t, -t + fd, t + fw, t + fh, t + fd, r, g, b, a);

        /* Top */
        fillBox(builder, stack, -t, -t + fh, -t, t + fw, t + fh, t, r, g, b, a);
        fillBox(builder, stack, -t, -t + fh, -t + fd, t + fw, t + fh, t + fd, r, g, b, a);
        fillBox(builder, stack, -t, -t + fh, -t, t, t + fh, t + fd, r, g, b, a);
        fillBox(builder, stack, -t + fw, -t + fh, -t, t + fw, t + fh, t + fd, r, g, b, a);

        /* Bottom */
        fillBox(builder, stack, -t, -t, -t, t + fw, t, t, r, g, b, a);
        fillBox(builder, stack, -t, -t, -t + fd, t + fw, t, t + fd, r, g, b, a);
        fillBox(builder, stack, -t, -t, -t, t, t, t + fd, r, g, b, a);
        fillBox(builder, stack, -t + fw, -t, -t, t + fw, t, t + fd, r, g, b, a);

        flush(builder, getPositionColorLayer());

        stack.pop();
    }


    private static final List<IrisBox> irisBoxQueue = new ArrayList<>();

    private static final class IrisBox
    {
        private final Matrix4f matrix;
        private final GpuBufferSlice projection;
        private final ProjectionType projectionType;
        private final float w;
        private final float h;
        private final float d;
        private final float r;
        private final float g;
        private final float b;

        private IrisBox(Matrix4f matrix, GpuBufferSlice projection, ProjectionType projectionType, float w, float h, float d, float r, float g, float b)
        {
            this.matrix = matrix;
            this.projection = projection;
            this.projectionType = projectionType;
            this.w = w;
            this.h = h;
            this.d = d;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    /**
     * Bake a camera-relative model matrix for the Iris LAST flush. Prefer the stack alone
     * when it already includes the view; otherwise compose with {@link BBSRendering#camera}.
     * Never multiply {@code RenderSystem.getModelViewMatrix()} — that was double-applying
     * the camera and parking boxes in the sky. Mirrors {@code Gizmo.composeVisualMatrix}.
     */
    private static Matrix4f bakeIrisBoxMatrix(MatrixStack stack, double x, double y, double z)
    {
        Matrix4f baked = new Matrix4f(stack.peek().getPositionMatrix());

        baked.translate((float) x, (float) y, (float) z);

        Matrix4f composed = new Matrix4f(BBSRendering.camera).mul(baked);
        float bakedDist = viewOriginLengthSq(baked);
        float composedDist = viewOriginLengthSq(composed);

        /* Double-applied view: composed collapses toward the view origin. */
        if (bakedDist > 1.0E-6F && composedDist < bakedDist * 0.49F)
        {
            return baked;
        }

        return composed;
    }

    private static float viewOriginLengthSq(Matrix4f view)
    {
        float ox = view.m30();
        float oy = view.m31();
        float oz = view.m32();

        return ox * ox + oy * oy + oz * oz;
    }

    private static void enqueueIrisBox(MatrixStack stack, double x, double y, double z, double w, double h, double d, float r, float g, float b)
    {
        Matrix4f matrix = bakeIrisBoxMatrix(stack, x, y, z);
        GpuBufferSlice projection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType projectionType = RenderSystem.getProjectionType();

        irisBoxQueue.add(new IrisBox(matrix, projection, projectionType, (float) w, (float) h, (float) d, r, g, b));
    }

    /** Flush hitboxes queued during the Iris world pass (call from WorldRenderEvents.LAST). */
    public static void flushIrisBoxes()
    {
        if (irisBoxQueue.isEmpty())
        {
            return;
        }

        boolean savedBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean savedDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        GpuBufferSlice savedProjection = RenderSystem.getProjectionMatrixBuffer();
        ProjectionType savedType = RenderSystem.getProjectionType();
        MatrixStack stack = new MatrixStack();

        GlStateManager._disableBlend();
        GlStateManager._disableDepthTest();
        MatrixStackUtils.pushIdentityModelView();

        try
        {
            for (IrisBox box : irisBoxQueue)
            {
                /* LAST no longer carries the solid-pass projection; rebind what was captured. */
                if (box.projection != null)
                {
                    RenderSystem.setProjectionMatrix(box.projection, box.projectionType != null ? box.projectionType : ProjectionType.PERSPECTIVE);
                }
                stack.push();
                stack.peek().getPositionMatrix().set(box.matrix);
                renderBoxSolidEdges(stack, box.w, box.h, box.d, box.r, box.g, box.b);
                stack.pop();
            }
        }
        finally
        {
            if (savedProjection != null)
            {
                RenderSystem.setProjectionMatrix(savedProjection, savedType);
            }
            MatrixStackUtils.popModelView();
            irisBoxQueue.clear();

            if (savedDepth)
            {
                GlStateManager._enableDepthTest();
            }

            if (savedBlend)
            {
                GlStateManager._enableBlend();
            }
        }
    }

    private static void renderBoxSolidEdges(MatrixStack stack, float fw, float fh, float fd, float r, float g, float b)
    {
        float t = 1 / 96F + (float) (Math.sqrt(fw * fw + fh + fh + fd + fd) / 2000);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        fillBox(builder, stack, -t, -t, -t, t, t + fh, t, r, g, b, 1F);
        fillBox(builder, stack, -t + fw, -t, -t, t + fw, t + fh, t, r, g, b, 1F);
        fillBox(builder, stack, -t, -t, -t + fd, t, t + fh, t + fd, r, g, b, 1F);
        fillBox(builder, stack, -t + fw, -t, -t + fd, t + fw, t + fh, t + fd, r, g, b, 1F);

        fillBox(builder, stack, -t, -t + fh, -t, t + fw, t + fh, t, r, g, b, 1F);
        fillBox(builder, stack, -t, -t + fh, -t + fd, t + fw, t + fh, t + fd, r, g, b, 1F);
        fillBox(builder, stack, -t, -t + fh, -t, t, t + fh, t + fd, r, g, b, 1F);
        fillBox(builder, stack, -t + fw, -t + fh, -t, t + fw, t + fh, t + fd, r, g, b, 1F);

        fillBox(builder, stack, -t, -t, -t, t + fw, t, t, r, g, b, 1F);
        fillBox(builder, stack, -t, -t, -t + fd, t + fw, t, t + fd, r, g, b, 1F);
        fillBox(builder, stack, -t, -t, -t, t, t, t + fd, r, g, b, 1F);
        fillBox(builder, stack, -t + fw, -t, -t, t + fw, t, t + fd, r, g, b, 1F);

        flush(builder, getPositionColorNoDepthLayer());
    }

    private static void renderBoxWireframe(MatrixStack stack, double x, double y, double z, double w, double h, double d, float r, float g, float b, float a)
    {
        stack.push();
        stack.translate(x, y, z);

        Matrix4f matrix = stack.peek().getPositionMatrix();
        float x1 = 0F;
        float y1 = 0F;
        float z1 = 0F;
        float x2 = (float) w;
        float y2 = (float) h;
        float z2 = (float) d;
        boolean savedBlend = GL11.glIsEnabled(GL11.GL_BLEND);

        GlStateManager._disableBlend();

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        wireLine(builder, matrix, x1, y1, z1, x2, y1, z1, r, g, b, a);
        wireLine(builder, matrix, x2, y1, z1, x2, y1, z2, r, g, b, a);
        wireLine(builder, matrix, x2, y1, z2, x1, y1, z2, r, g, b, a);
        wireLine(builder, matrix, x1, y1, z2, x1, y1, z1, r, g, b, a);

        wireLine(builder, matrix, x1, y2, z1, x2, y2, z1, r, g, b, a);
        wireLine(builder, matrix, x2, y2, z1, x2, y2, z2, r, g, b, a);
        wireLine(builder, matrix, x2, y2, z2, x1, y2, z2, r, g, b, a);
        wireLine(builder, matrix, x1, y2, z2, x1, y2, z1, r, g, b, a);

        wireLine(builder, matrix, x1, y1, z1, x1, y2, z1, r, g, b, a);
        wireLine(builder, matrix, x2, y1, z1, x2, y2, z1, r, g, b, a);
        wireLine(builder, matrix, x2, y1, z2, x2, y2, z2, r, g, b, a);
        wireLine(builder, matrix, x1, y1, z2, x1, y2, z2, r, g, b, a);

        flushLines(builder);

        if (savedBlend)
        {
            GlStateManager._enableBlend();
        }

        stack.pop();
    }

    private static void wireLine(BufferBuilder builder, Matrix4f matrix, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b, float a)
    {
        builder.vertex(matrix, x0, y0, z0).color(r, g, b, a);
        builder.vertex(matrix, x1, y1, z1).color(r, g, b, a);
    }
    /**
     * Fill a quad for {@link VertexFormats#POSITION_TEXTURE_COLOR_NORMAL}. Points should
     * be supplied in this order:
     *
     *     3 -------> 4
     *     ^
     *     |
     *     |
     *     2 <------- 1
     *
     * I.e. bottom left, bottom right, top left, top right, where left is -X and right is +X,
     * in case of a quad on fixed on Z axis.
     */
    public static void fillTexturedNormalQuad(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float u1, float v1, float u2, float v2, float r, float g, float b, float a, float nx, float ny, float nz)
    {
        Matrix4f matrix4f = stack.peek().getPositionMatrix();

        /* 1 - BL, 2 - BR, 3 - TR, 4 - TL */
        builder.vertex(matrix4f, x2, y2, z2).texture(u1, v2).color(r, g, b, a).normal(nx, ny, nz);
        builder.vertex(matrix4f, x1, y1, z1).texture(u2, v2).color(r, g, b, a).normal(nx, ny, nz);
        builder.vertex(matrix4f, x4, y4, z4).texture(u2, v1).color(r, g, b, a).normal(nx, ny, nz);

        builder.vertex(matrix4f, x2, y2, z2).texture(u1, v2).color(r, g, b, a).normal(nx, ny, nz);
        builder.vertex(matrix4f, x4, y4, z4).texture(u2, v1).color(r, g, b, a).normal(nx, ny, nz);
        builder.vertex(matrix4f, x3, y3, z3).texture(u1, v1).color(r, g, b, a).normal(nx, ny, nz);
    }

    public static void fillQuad(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, float r, float g, float b, float a)
    {
        Matrix4f matrix4f = stack.peek().getPositionMatrix();

        /* 1 - BR, 2 - BL, 3 - TL, 4 - TR */
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix4f, x2, y2, z2).color(r, g, b, a);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a);
        builder.vertex(matrix4f, x1, y1, z1).color(r, g, b, a);
        builder.vertex(matrix4f, x3, y3, z3).color(r, g, b, a);
        builder.vertex(matrix4f, x4, y4, z4).color(r, g, b, a);
    }

    public static void fillBoxTo(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, float r, float g, float b, float a)
    {
        if (stack == null)
        {
            stack = new MatrixStack();
            MatrixStackUtils.multiply(stack, RenderSystem.getModelViewMatrix());
        }

        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        Angle angle = Angle.angle(dx, dy, dz);

        stack.push();

        stack.translate(x1, y1, z1);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle.yaw));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle.pitch));

        fillBox(builder, stack, -thickness / 2, -thickness / 2, 0, thickness / 2, thickness / 2, (float) distance, r, g, b, a);

        stack.pop();
    }

    public static void fillBox(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b)
    {
        fillBox(builder, stack, x1, y1, z1, x2, y2, z2, r, g, b, 1F);
    }

    public static void fillBox(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a)
    {
        /* X */
        fillQuad(builder, stack, x1, y1, z2, x1, y2, z2, x1, y2, z1, x1, y1, z1, r, g, b, a);
        fillQuad(builder, stack, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);

        /* Y */
        fillQuad(builder, stack, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        fillQuad(builder, stack, x2, y2, z1, x1, y2, z1, x1, y2, z2, x2, y2, z2, r, g, b, a);

        /* Z */
        fillQuad(builder, stack, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, r, g, b, a);
        fillQuad(builder, stack, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
    }

    public static void coolerAxes(MatrixStack stack, float axisSize, float axisOffset, float outlineSize, float outlineOffset)
    {
        float scale = BBSSettings.axesScale.get();

        axisSize *= scale;
        axisOffset *= scale;
        outlineSize *= scale;
        outlineOffset *= scale;

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        fillBox(builder, stack, 0, -outlineOffset, -outlineOffset, outlineSize, outlineOffset, outlineOffset, 0, 0, 0);
        fillBox(builder, stack, -outlineOffset, 0, -outlineOffset, outlineOffset, outlineSize, outlineOffset, 0, 0, 0);
        fillBox(builder, stack, -outlineOffset, -outlineOffset, 0, outlineOffset, outlineOffset, outlineSize, 0, 0, 0);
        fillBox(builder, stack, -outlineOffset, -outlineOffset, -outlineOffset, outlineOffset, outlineOffset, outlineOffset, 0, 0, 0);

        fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize, axisOffset, axisOffset, 1, 0, 0);
        fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize, axisOffset, 0, 1, 0);
        fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize, 0, 0, 1);
        fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, 1, 1, 1);

        flush(builder, getPositionColorNoDepthLayer());
    }

    /**
     * Draws a solid cone (with a capped base) between two points, used for the tapered
     * arrow tips on gizmo translate handles. The base circle is perpendicular to the
     * apex-to-base direction, so it works for any axis without extra stack rotation.
     */
    public static void cone(BufferBuilder builder, MatrixStack stack, float apexX, float apexY, float apexZ, float baseX, float baseY, float baseZ, float radius, int segments, float r, float g, float b, float a)
    {
        Matrix4f mat = stack.peek().getPositionMatrix();

        float dx = baseX - apexX;
        float dy = baseY - apexY;
        float dz = baseZ - apexZ;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (len < 1.0E-6F)
        {
            return;
        }

        dx /= len;
        dy /= len;
        dz /= len;

        float upx = 0F;
        float upy = 1F;
        float upz = 0F;

        if (Math.abs(dy) > 0.99F)
        {
            upx = 1F;
            upy = 0F;
            upz = 0F;
        }

        float rx = dy * upz - dz * upy;
        float ry = dz * upx - dx * upz;
        float rz = dx * upy - dy * upx;
        float rl = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);

        rx /= rl;
        ry /= rl;
        rz /= rl;

        float ux = ry * dz - rz * dy;
        float uy = rz * dx - rx * dz;
        float uz = rx * dy - ry * dx;

        float[] cosA = SCRATCH_COS_U;
        float[] sinA = SCRATCH_SIN_U;

        for (int i = 0; i < segments; i++)
        {
            double ang = Math.PI * 2D * i / segments;

            cosA[i] = (float) Math.cos(ang);
            sinA[i] = (float) Math.sin(ang);
        }

        for (int i = 0; i < segments; i++)
        {
            int i2 = (i + 1) % segments;
            float c1 = cosA[i];
            float s1 = sinA[i];
            float c2 = cosA[i2];
            float s2 = sinA[i2];

            float x1 = baseX + (rx * c1 + ux * s1) * radius;
            float y1 = baseY + (ry * c1 + uy * s1) * radius;
            float z1 = baseZ + (rz * c1 + uz * s1) * radius;

            float x2 = baseX + (rx * c2 + ux * s2) * radius;
            float y2 = baseY + (ry * c2 + uy * s2) * radius;
            float z2 = baseZ + (rz * c2 + uz * s2) * radius;

            builder.vertex(mat, apexX, apexY, apexZ).color(r, g, b, a);
            builder.vertex(mat, x1, y1, z1).color(r, g, b, a);
            builder.vertex(mat, x2, y2, z2).color(r, g, b, a);

            builder.vertex(mat, x1, y1, z1).color(r, g, b, a);
            builder.vertex(mat, baseX, baseY, baseZ).color(r, g, b, a);
            builder.vertex(mat, x2, y2, z2).color(r, g, b, a);
        }
    }

    /**
     * Draws a standard UV sphere centered at the local origin, used for the invisible
     * free-rotate trackball hit volume (and its stencil id encoding).
     */
    public static void sphere(BufferBuilder builder, MatrixStack stack, float radius, int rings, int sectors, float r, float g, float b, float a)
    {
        Matrix4f mat = stack.peek().getPositionMatrix();
        float[] sinV = SCRATCH_SIN_V;
        float[] cosV = SCRATCH_COS_V;
        float[] sinU = SCRATCH_SIN_U;
        float[] cosU = SCRATCH_COS_U;

        for (int i = 0; i <= rings; i++)
        {
            double v = Math.PI * i / rings;

            sinV[i] = (float) Math.sin(v);
            cosV[i] = (float) Math.cos(v);
        }

        for (int j = 0; j <= sectors; j++)
        {
            double u = Math.PI * 2D * j / sectors;

            sinU[j] = (float) Math.sin(u);
            cosU[j] = (float) Math.cos(u);
        }

        for (int i = 0; i < rings; i++)
        {
            float sv1 = sinV[i];
            float cv1 = cosV[i];
            float sv2 = sinV[i + 1];
            float cv2 = cosV[i + 1];

            for (int j = 0; j < sectors; j++)
            {
                float cu1 = cosU[j];
                float su1 = sinU[j];
                float cu2 = cosU[j + 1];
                float su2 = sinU[j + 1];

                float x11 = sv1 * cu1 * radius;
                float y11 = cv1 * radius;
                float z11 = sv1 * su1 * radius;

                float x12 = sv2 * cu1 * radius;
                float y12 = cv2 * radius;
                float z12 = sv2 * su1 * radius;

                float x21 = sv1 * cu2 * radius;
                float y21 = y11;
                float z21 = sv1 * su2 * radius;

                float x22 = sv2 * cu2 * radius;
                float y22 = y12;
                float z22 = sv2 * su2 * radius;

                builder.vertex(mat, x11, y11, z11).color(r, g, b, a);
                builder.vertex(mat, x12, y12, z12).color(r, g, b, a);
                builder.vertex(mat, x22, y22, z22).color(r, g, b, a);

                builder.vertex(mat, x11, y11, z11).color(r, g, b, a);
                builder.vertex(mat, x22, y22, z22).color(r, g, b, a);
                builder.vertex(mat, x21, y21, z21).color(r, g, b, a);
            }
        }
    }

    public static void arc3D(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float thickness, float r, float g, float b)
    {
        arc3D(builder, stack, axis, radius, thickness, r, g, b, 0F, 360F, false);
    }

    public static void arc3D(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float thickness, float r, float g, float b, float startDeg, float sweepDeg)
    {
        arc3D(builder, stack, axis, radius, thickness, r, g, b, startDeg, sweepDeg, false);
    }

    /**
     * Torus-segment ring. Segment counts scale with sweep so half-rings and short process
     * arcs stay cheap; {@code lowDetail} is for invisible stencil/pick passes.
     */
    public static void arc3D(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float thickness, float r, float g, float b, float startDeg, float sweepDeg, boolean lowDetail)
    {
        float absSweep = Math.abs(sweepDeg);

        if (absSweep < 0.01F || thickness <= 0F || radius <= 0F)
        {
            return;
        }

        /* Visual: ~64×10 for a full ring (was 96×24). Stencil: ~36×5. Scales with sweep. */
        int segU = Math.max(lowDetail ? 6 : 12, Math.round((lowDetail ? 36F : 64F) * absSweep / 360F));
        /* Continuous drags can accumulate past 360°; never write past the scratch buffers. */
        segU = Math.min(segU, SCRATCH_COS_U.length - 1);
        int segV = lowDetail ? 5 : 10;
        segV = Math.min(segV, SCRATCH_COS_V.length - 1);
        double u0 = Math.toRadians(startDeg);
        double uStep = Math.toRadians(sweepDeg / (double) segU);
        double vStep = Math.PI * 2D / (double) segV;

        stack.push();

        if (axis == Axis.X) stack.multiply(RotationAxis.POSITIVE_Z.rotation(MathUtils.PI / 2F));
        if (axis == Axis.Z) stack.multiply(RotationAxis.POSITIVE_X.rotation(MathUtils.PI / 2F));

        float tubeR = thickness * 0.5F;
        Matrix4f mat = stack.peek().getPositionMatrix();

        float[] cosV = SCRATCH_COS_V;
        float[] sinV = SCRATCH_SIN_V;
        float[] cosU = SCRATCH_COS_U;
        float[] sinU = SCRATCH_SIN_U;

        for (int iv = 0; iv <= segV; iv++)
        {
            double v = vStep * iv;

            cosV[iv] = (float) Math.cos(v);
            sinV[iv] = (float) Math.sin(v);
        }

        for (int iu = 0; iu <= segU; iu++)
        {
            double u = u0 + uStep * iu;

            cosU[iu] = (float) Math.cos(u);
            sinU[iu] = (float) Math.sin(u);
        }

        for (int iu = 0; iu < segU; iu++)
        {
            float cu1 = cosU[iu];
            float su1 = sinU[iu];
            float cu2 = cosU[iu + 1];
            float su2 = sinU[iu + 1];

            for (int iv = 0; iv < segV; iv++)
            {
                float ring1 = radius + tubeR * cosV[iv];
                float ring2 = radius + tubeR * cosV[iv + 1];
                float y1 = tubeR * sinV[iv];
                float y2 = tubeR * sinV[iv + 1];

                float x11 = ring1 * cu1;
                float z11 = ring1 * su1;
                float x12 = ring2 * cu1;
                float z12 = ring2 * su1;
                float x21 = ring1 * cu2;
                float z21 = ring1 * su2;
                float x22 = ring2 * cu2;
                float z22 = ring2 * su2;

                builder.vertex(mat, x11, y1, z11).color(r, g, b, 1F);
                builder.vertex(mat, x12, y2, z12).color(r, g, b, 1F);
                builder.vertex(mat, x22, y2, z22).color(r, g, b, 1F);

                builder.vertex(mat, x11, y1, z11).color(r, g, b, 1F);
                builder.vertex(mat, x22, y2, z22).color(r, g, b, 1F);
                builder.vertex(mat, x21, y1, z21).color(r, g, b, 1F);
            }
        }

        stack.pop();
    }
}
