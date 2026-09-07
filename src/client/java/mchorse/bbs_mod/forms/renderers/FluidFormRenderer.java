package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.FluidForm;
import mchorse.bbs_mod.forms.renderers.utils.BillboardRenderLayers;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCacheEntry;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.simulation.FluidController;
import mchorse.bbs_mod.simulation.FluidSimulation;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.pose.Transform;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class FluidFormRenderer extends FormRenderer<FluidForm> implements ITickable
{
    private static final Link WHITE_TEXTURE = Link.bbs("textures/block/white.png");

    private FluidSimulation simulation;
    private FluidController controller = new FluidController();
    private long lastUpdate;

    public FluidFormRenderer(FluidForm form)
    {
        super(form);
        this.simulation = new FluidSimulation(64, 64);
    }

    @Override
    protected void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        context.batcher.flush();

        MatrixStack stack = new MatrixStack();

        stack.push();
        
        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);
        this.applyTransforms(uiMatrix, context.getTransition());

        /* Apply matrix to stack */
        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.translate(0F, 0.5F, 0F); /* Center it a bit */
        
        float scale = this.form.uiScale.get();
        stack.scale(scale, scale, scale);

        /* Shading fix for UI */
        MatrixStackUtils.invertUiNormalY(stack);

        BBSRendering.setupLevelLighting();

        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;
        
        this.renderFluid(format, BBSRendering.getEntityTranslucentProgram(),
            stack,
            OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE, Colors.WHITE,
            context.getTransition()
        );

        stack.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        VertexFormat format = VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL;
        ShaderProgram shader = BBSRendering.getEntityTranslucentProgram();

        this.renderFluid(format, shader, context.stack, context.overlay, context.light, context.color, context.getTransition());
        
        if (this.controller.debugEnabled && !this.controller.lastDebugSamples.isEmpty())
        {
            this.renderDebug(context);
        }
    }

    private void renderDebug(FormRenderingContext context)
    {
        BBSRendering.bindProgram(BBSRendering.getProgram(RenderPipelines.LINES));
        GL11.glLineWidth(2.0F);
        
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR_NORMAL_LINE_WIDTH);
        
        MatrixStack stack = context.stack;
        
        for (FluidController.FluidSample sample : this.controller.lastDebugSamples)
        {
            if (sample.localPos == null) continue;
            
            stack.push();
            stack.translate(sample.localPos.x, sample.localPos.y, sample.localPos.z);
            
            /* Draw sphere (simplified as 3 circles) */
            float r = (float) sample.radius;
            int segments = 12;
            
            Matrix4f matrix = stack.peek().getPositionMatrix();
            Matrix3f normal = stack.peek().getNormalMatrix();
            
            float nx1 = normal.m20();
            float ny1 = normal.m21();
            float nz1 = normal.m22();

            float nx2 = normal.m10();
            float ny2 = normal.m11();
            float nz2 = normal.m12();

            float nx3 = normal.m00();
            float ny3 = normal.m01();
            float nz3 = normal.m02();
            
            for (int i = 0; i < segments; i++)
            {
                float a1 = (float) (i * Math.PI * 2 / segments);
                float a2 = (float) ((i + 1) * Math.PI * 2 / segments);
                
                float c1 = (float) Math.cos(a1) * r;
                float s1 = (float) Math.sin(a1) * r;
                float c2 = (float) Math.cos(a2) * r;
                float s2 = (float) Math.sin(a2) * r;
                
                /* XY circle */
                builder.vertex(matrix, c1, s1, 0F).color(1F, 0F, 0F, 1F).normal(nx1, ny1, nz1).lineWidth(2F);
                builder.vertex(matrix, c2, s2, 0F).color(1F, 0F, 0F, 1F).normal(nx1, ny1, nz1).lineWidth(2F);
                
                /* XZ circle */
                builder.vertex(matrix, c1, 0F, s1).color(1F, 0F, 0F, 1F).normal(nx2, ny2, nz2).lineWidth(2F);
                builder.vertex(matrix, c2, 0F, s2).color(1F, 0F, 0F, 1F).normal(nx2, ny2, nz2).lineWidth(2F);
                
                /* YZ circle */
                builder.vertex(matrix, 0F, c1, s1).color(1F, 0F, 0F, 1F).normal(nx3, ny3, nz3).lineWidth(2F);
                builder.vertex(matrix, 0F, c2, s2).color(1F, 0F, 0F, 1F).normal(nx3, ny3, nz3).lineWidth(2F);
            }
            
            stack.pop();
        }
        
        BufferRenderer.drawWithGlobalProgram(builder.end());
        GL11.glLineWidth(1.0F);
    }

    private void renderFluid(VertexFormat format, ShaderProgram shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition)
    {
        Link t = this.form.texture.get();
        Texture texture = null;

        if (t != null)
        {
            texture = BBSModClient.getTextures().getTexture(t);
        }

        if (texture == null || !texture.isValid())
        {
            texture = BBSModClient.getTextures().getTexture(WHITE_TEXTURE);
        }

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.TRIANGLES, format);

        Color color = this.form.color.get();
        Color finalColor = color.copy();

        /* Multiply by overlay color (usually WHITE unless hit) */
        finalColor.mul(overlayColor);

        FluidForm.FluidMode mode = this.form.mode.get();

        if (mode == FluidForm.FluidMode.FULL_OCEAN)
        {
            this.renderOcean(builder, matrices, finalColor, overlay, light);
        }
        else if (mode == FluidForm.FluidMode.PROCEDURAL)
        {
            this.renderProceduralOcean(builder, matrices, finalColor, overlay, light);
        }
        else
        {
            this.renderDrop(builder, matrices, finalColor, overlay, light);
        }

        BillboardRenderLayers.draw(builder.end(), texture, false, false, finalColor.a >= 0.999F, false);
    }

    private void renderProceduralOcean(BufferBuilder builder, MatrixStack matrices, Color color, int overlay, int light)
    {
        float scaleX = Math.max(this.form.sizeX.get(), 0.001f);
        float scaleZ = Math.max(this.form.sizeZ.get(), 0.001f);
        
        int resX = Math.min(Math.max((int) (scaleX * 8), 16), 512);
        int resZ = Math.min(Math.max((int) (scaleZ * 8), 16), 512);

        if (this.simulation.getWidth() != resX || this.simulation.getHeight() != resZ)
        {
            this.simulation = new FluidSimulation(resX, resZ);
        }

        float amp = this.form.waveAmplitude.get();
        float freq = this.form.waveFrequency.get();
        float speed = this.form.flowSpeed.get();
        float turbulence = this.form.turbulence.get();
        
        float time = 0;
        
        if (MinecraftClient.getInstance().player != null)
        {
            time = (MinecraftClient.getInstance().player.age + MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(true)) * speed * 0.1F;
        }
        else
        {
            time = (System.currentTimeMillis() % 100000) / 1000f * 20f * speed * 0.1f;
        }

        long now = System.currentTimeMillis();
        long delay = (long) (30 / (speed <= 0.1 ? 0.1 : speed));

        if (now - this.lastUpdate > delay)
        {
            this.simulation.update();
            this.lastUpdate = now;
        }
        
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

        int segments = 16 * this.form.subdivisions.get();
        float stepX = scaleX / segments;
        float stepZ = scaleZ / segments;
        boolean smooth = this.form.smoothShading.get();
        
        Vector3f n1 = new Vector3f();
        Vector3f n2 = new Vector3f();
        Vector3f n3 = new Vector3f();
        Vector3f n4 = new Vector3f();

        for (int x = 0; x < segments; x++)
        {
            for (int z = 0; z < segments; z++)
            {
                float x1 = x * stepX - scaleX / 2;
                float z1 = z * stepZ - scaleZ / 2;
                float x2 = (x + 1) * stepX - scaleX / 2;
                float z2 = (z + 1) * stepZ - scaleZ / 2;

                float y11 = getProceduralWaveHeight(x1, z1, time, amp, freq, turbulence, scaleX, scaleZ);
                float y12 = getProceduralWaveHeight(x1, z2, time, amp, freq, turbulence, scaleX, scaleZ);
                float y21 = getProceduralWaveHeight(x2, z1, time, amp, freq, turbulence, scaleX, scaleZ);
                float y22 = getProceduralWaveHeight(x2, z2, time, amp, freq, turbulence, scaleX, scaleZ);

                getProceduralWaveNormal(x1, z1, time, amp, freq, turbulence, scaleX, scaleZ, n1);
                getProceduralWaveNormal(x1, z2, time, amp, freq, turbulence, scaleX, scaleZ, n2);
                getProceduralWaveNormal(x2, z1, time, amp, freq, turbulence, scaleX, scaleZ, n3);
                getProceduralWaveNormal(x2, z2, time, amp, freq, turbulence, scaleX, scaleZ, n4);

                if (!smooth)
                {
                    /* Triangle 1: (x1, y11, z1), (x1, y12, z2), (x2, y21, z1) */
                    float nx = -stepZ * (y21 - y11);
                    float ny = stepX * stepZ;
                    float nz = -stepX * (y12 - y11);
                    n1.set(nx, ny, nz).normalize();
                    n2.set(n1);
                    n3.set(n1);

                    /* Triangle 2: (x2, y21, z1), (x1, y12, z2), (x2, y22, z2) */
                    n4.set(stepZ * (y12 - y22), stepX * stepZ, -stepX * (y22 - y21)).normalize();
                }

                /* Triangle 1: (x1, z1), (x1, z2), (x2, z1) */
                addVertex(builder, matrix, normalMatrix, x1, y11, z1, 0, 0, color, overlay, light, n1);
                addVertex(builder, matrix, normalMatrix, x1, y12, z2, 0, 1, color, overlay, light, smooth ? n2 : n1);
                addVertex(builder, matrix, normalMatrix, x2, y21, z1, 1, 0, color, overlay, light, smooth ? n3 : n1);

                /* Triangle 2: (x2, z1), (x1, z2), (x2, z2) */
                addVertex(builder, matrix, normalMatrix, x2, y21, z1, 1, 0, color, overlay, light, smooth ? n3 : n4);
                addVertex(builder, matrix, normalMatrix, x1, y12, z2, 0, 1, color, overlay, light, smooth ? n2 : n4);
                addVertex(builder, matrix, normalMatrix, x2, y22, z2, 1, 1, color, overlay, light, n4);
            }
        }
    }
    
    private float getProceduralWaveHeight(float x, float z, float time, float amp, float freq, float turbulence, float scaleX, float scaleZ)
    {
        double val = Math.sin((x + z) * freq + time) * amp;
        val += Math.sin((x * 0.5 - z * 0.7) * freq * 1.5 + time * 1.2) * amp * 0.5;
        
        if (turbulence > 0) {
             val += Math.sin((x * 2.1 + z * 1.3) * freq * 3.0 + time * 2.5) * (amp * turbulence);
        }

        /* Add Physics Simulation */
        float u = (x + scaleX / 2) / scaleX;
        float v = (z + scaleZ / 2) / scaleZ;
        
        if (u >= 0 && u <= 1 && v >= 0 && v <= 1)
        {
            float fx = u * (this.simulation.getWidth() - 1);
            float fy = v * (this.simulation.getHeight() - 1);
            
            int x0 = (int) fx;
            int y0 = (int) fy;
            int x1 = x0 + 1;
            int y1 = y0 + 1;
            
            float wx = fx - x0;
            float wy = fy - y0;
            
            float h00 = this.simulation.getHeight(x0, y0);
            float h10 = this.simulation.getHeight(x1, y0);
            float h01 = this.simulation.getHeight(x0, y1);
            float h11 = this.simulation.getHeight(x1, y1);
            
            float hTop = h00 + (h10 - h00) * wx;
            float hBot = h01 + (h11 - h01) * wx;
            float h = hTop + (hBot - hTop) * wy;
            
            val += h * amp;
        }

        return (float) val;
    }
    
    private void getProceduralWaveNormal(float x, float z, float time, float amp, float freq, float turbulence, float scaleX, float scaleZ, Vector3f dest)
    {
        float delta = 0.1f;
        float hL = getProceduralWaveHeight(x - delta, z, time, amp, freq, turbulence, scaleX, scaleZ);
        float hR = getProceduralWaveHeight(x + delta, z, time, amp, freq, turbulence, scaleX, scaleZ);
        float hD = getProceduralWaveHeight(x, z - delta, time, amp, freq, turbulence, scaleX, scaleZ);
        float hU = getProceduralWaveHeight(x, z + delta, time, amp, freq, turbulence, scaleX, scaleZ);
        
        float dx = (hR - hL) / (2 * delta);
        float dz = (hU - hD) / (2 * delta);
        
        dest.set(-dx, 1.0f, -dz);
        dest.normalize();
    }

    private void renderOcean(BufferBuilder builder, MatrixStack matrices, Color color, int overlay, int light)
    {
        float scaleX = Math.max(this.form.sizeX.get(), 0.001f);
        float scaleZ = Math.max(this.form.sizeZ.get(), 0.001f);

        int resX = Math.min(Math.max((int) (scaleX * 8), 16), 512);
        int resZ = Math.min(Math.max((int) (scaleZ * 8), 16), 512);

        if (this.simulation.getWidth() != resX || this.simulation.getHeight() != resZ)
        {
            this.simulation = new FluidSimulation(resX, resZ);
        }

        float amp = this.form.waveAmplitude.get();
        float speed = this.form.flowSpeed.get();
        float turbulence = this.form.turbulence.get();
        
        long now = System.currentTimeMillis();
        long delay = (long) (30 / (speed <= 0.1 ? 0.1 : speed));
        
        if (now - this.lastUpdate > delay)
        {
            this.simulation.update();
            
            if (turbulence > 0 && Math.random() < turbulence * 0.1) 
            {
                int rx = (int) (Math.random() * (this.simulation.getWidth() - 2)) + 1;
                int ry = (int) (Math.random() * (this.simulation.getHeight() - 2)) + 1;
                this.simulation.addForce(rx, ry, 5.0F * amp);
            }
            
            this.lastUpdate = now;
        }
        
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

        /* Increase render resolution for smoother look */
        int simWidth = this.simulation.getWidth();
        int simHeight = this.simulation.getHeight();
        int factor = this.form.subdivisions.get();
        int renderWidth = simWidth * factor;
        int renderHeight = simHeight * factor;
        boolean smooth = this.form.smoothShading.get();
        
        float stepX = scaleX / (renderWidth - 1);
        float stepZ = scaleZ / (renderHeight - 1);
        
        Vector3f n1 = new Vector3f();
        Vector3f n2 = new Vector3f();
        Vector3f n3 = new Vector3f();
        Vector3f n4 = new Vector3f();

        for (int x = 0; x < renderWidth - 1; x++)
        {
            for (int z = 0; z < renderHeight - 1; z++)
            {
                float x1 = x * stepX - scaleX / 2;
                float z1 = z * stepZ - scaleZ / 2;
                float x2 = (x + 1) * stepX - scaleX / 2;
                float z2 = (z + 1) * stepZ - scaleZ / 2;

                /* Map render coordinates to simulation coordinates */
                float simX1 = (float) x / (renderWidth - 1) * (simWidth - 1);
                float simZ1 = (float) z / (renderHeight - 1) * (simHeight - 1);
                float simX2 = (float) (x + 1) / (renderWidth - 1) * (simWidth - 1);
                float simZ2 = (float) (z + 1) / (renderHeight - 1) * (simHeight - 1);

                float y11 = this.getSmoothedHeight(simX1, simZ1) * amp;
                float y12 = this.getSmoothedHeight(simX1, simZ2) * amp;
                float y21 = this.getSmoothedHeight(simX2, simZ1) * amp;
                float y22 = this.getSmoothedHeight(simX2, simZ2) * amp;

                this.getSmoothedNormal(simX1, simZ1, scaleX, scaleZ, amp, n1);
                this.getSmoothedNormal(simX1, simZ2, scaleX, scaleZ, amp, n2);
                this.getSmoothedNormal(simX2, simZ1, scaleX, scaleZ, amp, n3);
                this.getSmoothedNormal(simX2, simZ2, scaleX, scaleZ, amp, n4);

                if (!smooth)
                {
                     /* Calculate face normals for flat shading */
                     /* Triangle 1: (x1, y11, z1), (x1, y12, z2), (x2, y21, z1) */
                     float v1x = x1 - x1; float v1y = y12 - y11; float v1z = z2 - z1;
                     float v2x = x2 - x1; float v2y = y21 - y11; float v2z = z1 - z1;
                     
                     /* Cross product */
                     float nx = v1y * v2z - v1z * v2y;
                     float ny = v1z * v2x - v1x * v2z;
                     float nz = v1x * v2y - v1y * v2x;
                     float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                     n1.set(nx / len, ny / len, nz / len);
                     
                     /* Use n1 for all vertices of triangle 1 */
                     n2.set(n1);
                     n3.set(n1);
                }

                /* Triangle 1: (x1, z1), (x1, z2), (x2, z1) */
                addVertex(builder, matrix, normalMatrix, x1, y11, z1, 0, 0, color, overlay, light, n1);
                addVertex(builder, matrix, normalMatrix, x1, y12, z2, 0, 1, color, overlay, light, n2);
                addVertex(builder, matrix, normalMatrix, x2, y21, z1, 1, 0, color, overlay, light, n3);

                if (!smooth)
                {
                     /* Triangle 2: (x2, y21, z1), (x1, y12, z2), (x2, y22, z2) */
                     float v1x = x1 - x2; float v1y = y12 - y21; float v1z = z2 - z1;
                     float v2x = x2 - x2; float v2y = y22 - y21; float v2z = z2 - z1;
                     
                     float nx = v1y * v2z - v1z * v2y;
                     float ny = v1z * v2x - v1x * v2z;
                     float nz = v1x * v2y - v1y * v2x;
                     float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                     n3.set(nx / len, ny / len, nz / len); /* Reuse n3 */
                     
                     n2.set(n3);
                     n4.set(n3);
                }

                /* Triangle 2: (x2, z1), (x1, z2), (x2, z2) */
                addVertex(builder, matrix, normalMatrix, x2, y21, z1, 1, 0, color, overlay, light, n3);
                addVertex(builder, matrix, normalMatrix, x1, y12, z2, 0, 1, color, overlay, light, n2);
                addVertex(builder, matrix, normalMatrix, x2, y22, z2, 1, 1, color, overlay, light, n4);
            }
        }
    }

    private float getSmoothedHeight(float x, float z)
    {
        int x0 = (int) x;
        int z0 = (int) z;
        int x1 = x0 + 1;
        int z1 = z0 + 1;
        
        float h00 = this.simulation.getHeight(x0, z0);
        float h10 = this.simulation.getHeight(x1, z0);
        float h01 = this.simulation.getHeight(x0, z1);
        float h11 = this.simulation.getHeight(x1, z1);
        
        float tx = x - x0;
        float tz = z - z0;
        
        /* Smoothstep interpolation: t * t * (3 - 2 * t) */
        float sx = tx * tx * (3 - 2 * tx);
        float sz = tz * tz * (3 - 2 * tz);
        
        float h0 = h00 + (h10 - h00) * sx;
        float h1 = h01 + (h11 - h01) * sx;
        
        return h0 + (h1 - h0) * sz;
    }

    private void getSmoothedNormal(float x, float z, float scaleX, float scaleZ, float amp, Vector3f dest)
    {
        float delta = 0.1f;
        float hL = this.getSmoothedHeight(x - delta, z) * amp;
        float hR = this.getSmoothedHeight(x + delta, z) * amp;
        float hD = this.getSmoothedHeight(x, z - delta) * amp;
        float hU = this.getSmoothedHeight(x, z + delta) * amp;
        
        float dx = hR - hL;
        float dz = hU - hD;
        
        float simWidth = this.simulation.getWidth();
        float simHeight = this.simulation.getHeight();
        
        /* Adjust normal for world scale */
        dest.set(-dx * simWidth / scaleX, 2 * delta, -dz * simHeight / scaleZ);
        dest.normalize();
    }

    private void getDisplacedDropVertex(float lat, float lng, float baseSize, float amp, float time, int width, int height, Vector3f dest)
    {
        getDropVertex(lat, lng, baseSize, amp, time, dest);
        
        /* Map sphere UV to grid */
        /* lat is -PI/2 to PI/2. v should be 0 to 1. */
        /* lng is 0 to 2PI. u should be 0 to 1. */
        float u = (float) (lng / (2 * Math.PI));
        float v = (float) (lat / Math.PI + 0.5f);
        
        /* Clamp UV */
        if (u < 0) u = 0; if (u > 1) u = 1;
        if (v < 0) v = 0; if (v > 1) v = 1;
        
        float simH = this.simulation.getHeight((int)(u * (width - 1)), (int)(v * (height - 1))) * 0.1f;
        
        /* Displace along the position vector (which is from center) */
        dest.add(dest.x * simH, dest.y * simH, dest.z * simH);
    }

    private void getDropNormal(float lat, float lng, float baseSize, float amp, float time, int width, int height, Vector3f dest)
    {
        float delta = 0.01f;
        Vector3f p = new Vector3f();
        Vector3f pLat = new Vector3f();
        Vector3f pLng = new Vector3f();
        
        getDisplacedDropVertex(lat, lng, baseSize, amp, time, width, height, p);
        getDisplacedDropVertex(lat + delta, lng, baseSize, amp, time, width, height, pLat);
        getDisplacedDropVertex(lat, lng + delta, baseSize, amp, time, width, height, pLng);
        
        pLat.sub(p); /* Tangent along Latitude */
        pLng.sub(p); /* Tangent along Longitude */
        
        /* Cross product: Longitude (Horizontal) x Latitude (Vertical) -> Normal */
        dest.set(pLng).cross(pLat);
        dest.normalize();
    }

    private void renderDrop(BufferBuilder builder, MatrixStack matrices, Color color, int overlay, int light)
    {
        float baseSize = this.form.dropSize.get() * 0.5f;
        float tension = this.form.surfaceTension.get();
        float viscosity = this.form.viscosity.get();
        float speedParam = this.form.flowSpeed.get();

        long now = System.currentTimeMillis();
        long delay = (long) (30 / (speedParam <= 0.1 ? 0.1 : speedParam));

        if (now - this.lastUpdate > delay)
        {
            this.simulation.update();
            this.lastUpdate = now;
        }

        /* Wobble parameters */
        float amp = (1.0f - tension) * 0.2f;
        float speed = 2.0f + (1.0f - viscosity) * 3.0f;
        float time = (System.currentTimeMillis() % 100000) / 1000f * speed;

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Matrix3f normalMatrix = matrices.peek().getNormalMatrix();

        int factor = this.form.subdivisions.get();
        int stacks = 16 * factor;
        int slices = 16 * factor;
        boolean smooth = this.form.smoothShading.get();

        Vector3f v00 = new Vector3f();
        Vector3f v10 = new Vector3f();
        Vector3f v01 = new Vector3f();
        Vector3f v11 = new Vector3f();
        Vector3f n00 = new Vector3f();
        Vector3f n10 = new Vector3f();
        Vector3f n01 = new Vector3f();
        Vector3f n11 = new Vector3f();
        
        // Temps
        Vector3f t1 = new Vector3f();
        Vector3f t2 = new Vector3f();
        
        int width = this.simulation.getWidth();
        int height = this.simulation.getHeight();

        for (int i = 0; i < stacks; i++)
        {
            float lat0 = (float) Math.PI * (-0.5f + (float) i / stacks);
            float lat1 = (float) Math.PI * (-0.5f + (float) (i + 1) / stacks);

            for (int j = 0; j < slices; j++)
            {
                float lng0 = 2 * (float) Math.PI * (float) j / slices;
                float lng1 = 2 * (float) Math.PI * (float) (j + 1) / slices;

                getDisplacedDropVertex(lat0, lng0, baseSize, amp, time, width, height, v00);
                getDisplacedDropVertex(lat1, lng0, baseSize, amp, time, width, height, v10);
                getDisplacedDropVertex(lat0, lng1, baseSize, amp, time, width, height, v01);
                getDisplacedDropVertex(lat1, lng1, baseSize, amp, time, width, height, v11);
                
                float u0 = (float) j / slices;
                float v0 = (float) i / stacks;
                float u1 = (float) (j + 1) / slices;
                float v1 = (float) (i + 1) / stacks;

                if (smooth)
                {
                    getDropNormal(lat0, lng0, baseSize, amp, time, width, height, n00);
                    getDropNormal(lat1, lng0, baseSize, amp, time, width, height, n10);
                    getDropNormal(lat0, lng1, baseSize, amp, time, width, height, n01);
                    getDropNormal(lat1, lng1, baseSize, amp, time, width, height, n11);
                }
                else
                {
                    /* Triangle 1: v00, v10, v01 */
                    t1.set(v10).sub(v00);
                    t2.set(v01).sub(v00);
                    n00.set(t1).cross(t2, n00).normalize();
                    if (n00.dot(v00) < 0) n00.mul(-1);
                    
                    n10.set(n00);
                    n01.set(n00);
                    
                    /* Triangle 2: v01, v10, v11 */
                    t1.set(v10).sub(v01);
                    t2.set(v11).sub(v01);
                    n11.set(t1).cross(t2, n11).normalize();
                    if (n11.dot(v11) < 0) n11.mul(-1);
                }

                /* Triangle 1: v00, v10, v01 */
                addVertex(builder, matrix, normalMatrix, v00.x, v00.y, v00.z, u0, v0, color, overlay, light, n00);
                addVertex(builder, matrix, normalMatrix, v10.x, v10.y, v10.z, u0, v1, color, overlay, light, smooth ? n10 : n00);
                addVertex(builder, matrix, normalMatrix, v01.x, v01.y, v01.z, u1, v0, color, overlay, light, smooth ? n01 : n00);

                /* Triangle 2: v01, v10, v11 */
                addVertex(builder, matrix, normalMatrix, v01.x, v01.y, v01.z, u1, v0, color, overlay, light, smooth ? n01 : n11);
                addVertex(builder, matrix, normalMatrix, v10.x, v10.y, v10.z, u0, v1, color, overlay, light, smooth ? n10 : n11);
                addVertex(builder, matrix, normalMatrix, v11.x, v11.y, v11.z, u1, v1, color, overlay, light, n11);
            }
        }
    }

    private void getDropVertex(float lat, float lng, float radius, float amp, float time, Vector3f dest)
    {
        float y = (float) Math.sin(lat);
        float r = (float) Math.cos(lat);
        float x = (float) Math.cos(lng) * r;
        float z = (float) Math.sin(lng) * r;

        /* Wobble */
        float wobble = 1.0f + (float) Math.sin(lat * 5 + time) * (float) Math.cos(lng * 4 + time * 1.5f) * amp;

        dest.set(x * radius * wobble, y * radius * wobble, z * radius * wobble);
    }

    private void addVertex(BufferBuilder builder, Matrix4f matrix, Matrix3f normal, float x, float y, float z, float u, float v, Color color, int overlay, int light, Vector3f n)
    {
         Vector3f tn = new Vector3f(n);
         tn.mul(normal);

         builder.vertex(matrix, x, y, z)
               .color((int) (color.r * 255), (int) (color.g * 255), (int) (color.b * 255), (int) (color.a * 255))
               .texture(u, v)
               .overlay(overlay)
               .light(light)
               .normal(tn.x, tn.y, tn.z);
    }
    
    @Override
    public void tick(IEntity entity)
    {
        this.controller.debugEnabled = this.form.debug.get();
        float transition = 0F;
        float scaleX = Math.max(this.form.sizeX.get(), 0.001f);
        float scaleZ = Math.max(this.form.sizeZ.get(), 0.001f);

        List<BaseFilmController> controllers = new ArrayList<>(BBSModClient.getFilms().getControllers());

        UIDashboard dashboard = BBSModClient.peekDashboard();

        if (dashboard != null && dashboard.getPanels() != null && dashboard.getPanels().panel instanceof UIFilmPanel panel)
        {
            controllers.add(panel.getController().editorController);
        }

        BaseFilmController owner = null;

        for (BaseFilmController controller : controllers)
        {
            if (controller == null) continue;

            for (IEntity replayEntity : controller.getEntities().values())
            {
                if (replayEntity == entity)
                {
                    owner = controller;
                    break;
                }
            }

            if (owner != null)
            {
                break;
            }
        }

        Matrix4f defaultMatrix = BaseFilmController.getMatrixForRenderWithRotation(entity, 0, 0, 0, transition);
        Matrix4f targetMatrix = defaultMatrix;

        if (owner != null)
        {
            var total = BaseFilmController.getTotalMatrix(owner.getEntities(), this.form.anchor.get(), defaultMatrix, 0, 0, 0, transition, 0);

            if (total != null && total.a != null)
            {
                targetMatrix = total.a;
            }
        }

        Matrix4f formMatrix = null;
        var renderer = FormUtilsClient.getRenderer(this.form);

        if (renderer != null)
        {
            MatrixCache matrices = renderer.collectMatrices(entity, transition);
            MatrixCacheEntry root = matrices.get("");

            if (root != null)
            {
                formMatrix = root.matrix();
            }
        }

        Matrix4f surfaceMatrixWorld = new Matrix4f(targetMatrix);

        if (formMatrix != null)
        {
            surfaceMatrixWorld.mul(formMatrix);
        }

        this.controller.update(entity, this.simulation, scaleX, scaleZ, this.form.physicsSensitivity.get(), surfaceMatrixWorld, controllers);
    }

}
