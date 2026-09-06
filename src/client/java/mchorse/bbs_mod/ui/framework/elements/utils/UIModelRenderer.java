package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.ModelPreviewRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUITreeEventListener;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.utils.Factor;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Intersectiond;
import org.joml.Matrix3d;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;

import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Model renderer GUI element
 *
 * This base class can be used for full screen model viewer.
 */
public abstract class UIModelRenderer extends UIElement implements IUITreeEventListener
{
    private static Vector3d vec = new Vector3d();
    private static Matrix3d mat = new Matrix3d();

    protected IEntity entity = new StubEntity();

    protected int timer;
    protected int dragging;

    public Camera camera = new Camera();

    public Vector3f pos = new Vector3f();
    public Factor distance = new Factor(0, 0, 100, (x) -> Math.pow(x, 2) / 100D);
    public boolean grid = true;

    private Vector3d cachedPlaneIntersection = new Vector3d();
    private Vector3f cachedPos = new Vector3f();
    private Camera cachedCamera = new Camera();
    private Vector3d plane = new Vector3d();
    private float lastX;
    private float lastY;

    private long tick;
    private Matrix4f transform = new Matrix4f();

    private final ModelPreviewRenderer preview = new ModelPreviewRenderer();
    protected int viewportW;
    protected int viewportH;

    private GpuTextureView previewTexture;
    private int previewVw;
    private int previewVh;

    private static final Vector3f LIGHT_A = new Vector3f(0F, 0.85F, -1F).normalize();
    private static final Vector3f LIGHT_B = new Vector3f(0F, 0.85F, 1F).normalize();

    private GpuBuffer lightsBuffer;
    private GpuBufferSlice lights;
    private final Vector3f lightDirA = new Vector3f();
    private final Vector3f lightDirB = new Vector3f();

    private boolean stencilViewport;
    private int stencilViewportW;
    private int stencilViewportH;

    public UIModelRenderer()
    {
        super();

        this.reset();
    }

    /**
     * When rendering the stencil pick pass into an FBO, the GL viewport must be {@code 0,0,fboW,fboH}
     * instead of window-relative coordinates so pick pixels align with the on-screen gizmo.
     */
    protected void beginStencilViewport(int fboW, int fboH)
    {
        this.stencilViewport = true;
        this.stencilViewportW = fboW;
        this.stencilViewportH = fboH;
    }

    protected void endStencilViewport()
    {
        this.stencilViewport = false;
    }

    public void setTransform(Matrix4f transform)
    {
        this.transform = transform;
    }

    public void setRotation(float yaw, float pitch)
    {
        this.camera.rotation.y = MathUtils.toRad(yaw);
        this.camera.rotation.x = MathUtils.toRad(pitch);
    }

    public void setPosition(float x, float y, float z)
    {
        this.pos.set(x, y, z);
    }

    public void setDistance(int distanceX)
    {
        this.distance.setX(distanceX);
    }

    public void setEntity(IEntity entity)
    {
        this.entity = entity;
    }

    public IEntity getEntity()
    {
        return this.entity;
    }

    public void reset()
    {
        this.setDistance(15);
        this.setPosition(0, 1, 0);
        this.setRotation(0, 0);
    }

    public boolean isDragging()
    {
        return this.dragging != 0;
    }

    public boolean isDraggingPosition()
    {
        return this.dragging == 2;
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.isDragging() && this.area.isInside(context) && (context.mouseButton == 0 || context.mouseButton == 2))
        {
            this.dragging = Window.isShiftPressed() || context.mouseButton == 2 ? 2 : 1;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            this.cachedPos.set(this.pos);
            this.cachedCamera.copy(this.camera);
            this.plane.set(0, 0, 1);
            this.rotateVector(this.plane);

            this.cachedPlaneIntersection = this.calculateOnPlane(context);
        }

        return false;
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context) && !this.isDragging())
        {
            int x = Integer.compare(-(int) context.mouseWheel, 0);

            if (Window.isCtrlPressed())
            {
                x *= 8;
            }

            this.distance.setX(this.distance.getX() + x);
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        this.dragging = 0;

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        this.updateLogic(context);

        context.batcher.clip(this.area, context);
        this.renderModel(context);
        context.batcher.unclip(context);

        super.render(context);
    }

    private void updateLogic(UIContext context)
    {
        long tick = context.getTick();
        long i = tick - this.tick;

        if (i > 10)
        {
            i = 10;
        }

        while (i > 0)
        {
            this.update();
            i --;
        }

        this.tick = tick;
    }

    /**
     * Update logic
     */
    protected void update()
    {
        this.timer += 1;
        this.entity.setAge(this.timer);
    }

    private void renderModel(UIContext context)
    {
        /* The model is rendered into a GPU target; only the resulting texture is recorded in the
         * current DrawContext. This keeps the 3D pass out of the 2D Matrix3x2fStack. */
        this.renderModelToTexture(context);

        if (this.previewTexture != null && this.previewVw > 0 && this.previewVh > 0)
        {
            context.batcher.newRootLayer();
            context.batcher.texturedBox(this.previewTexture, Colors.WHITE,
                this.area.x, this.area.y, this.area.w, this.area.h,
                0, this.previewVh, this.previewVw, 0, this.previewVw, this.previewVh);
            context.batcher.newRootLayer();
        }

        this.processInputs(context);
    }

    /**
     * Render the 3D preview into its own color/depth target. The caller may invoke this from the
     * world phase to avoid mixing an immediate render pass with GUI recording.
     */
    public void renderModelToTexture(UIContext context)
    {
        this.setupPosition();
        this.setupViewport(context);

        int vw = this.viewportW;
        int vh = this.viewportH;

        this.previewTexture = null;

        if (vw <= 0 || vh <= 0)
        {
            return;
        }

        boolean previousWorldRender = BBSRendering.renderingWorld;
        BBSRendering.renderingWorld = false;

        try
        {
            this.preview.begin(vw, vh, this.camera.projection);
            RenderSystem.setShaderLights(this.editorLights());

            if (this.grid)
            {
                this.renderGrid(context);
            }

            this.renderUserModel(context);
            this.previewTexture = this.preview.getColorView();
            this.previewVw = vw;
            this.previewVh = vh;
        }
        finally
        {
            this.preview.end();
            BBSRendering.renderingWorld = previousWorldRender;
        }
    }

    @Override
    public void onAddedToTree(UIElement element)
    {}

    @Override
    public void onRemovedFromTree(UIElement element)
    {
        this.releasePreview();
    }

    @Override
    protected void onRemove(UIElement parent)
    {
        super.onRemove(parent);
        this.releasePreview();
    }

    private void releasePreview()
    {
        this.previewTexture = null;
        this.preview.close();

        if (this.lightsBuffer != null)
        {
            this.lightsBuffer.close();
            this.lightsBuffer = null;
            this.lights = null;
        }
    }

    public MatrixStack createCameraStack()
    {
        MatrixStack stack = new MatrixStack();

        MatrixStackUtils.multiply(stack, this.camera.view);
        stack.translate(-this.camera.position.x, -this.camera.position.y, -this.camera.position.z);
        MatrixStackUtils.multiply(stack, this.transform);

        return stack;
    }

    private GpuBufferSlice editorLights()
    {
        this.lightDirA.set(LIGHT_A);
        this.lightDirB.set(LIGHT_B);

        BBSRendering.setupMatchingWorldDiffuseLighting();

        try (MemoryStack stack = MemoryStack.stackPush())
        {
            ByteBuffer data = Std140Builder.onStack(stack, DiffuseLighting.UBO_SIZE)
                .putVec3(this.lightDirA)
                .putVec3(this.lightDirB)
                .get();

            if (this.lightsBuffer == null)
            {
                this.lightsBuffer = RenderSystem.getDevice().createBuffer(() -> "BBS editor preview lights UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, data);
                this.lights = this.lightsBuffer.slice(0, DiffuseLighting.UBO_SIZE);
            }
            else
            {
                RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.lights, data);
            }
        }

        return this.lights;
    }

    protected void processInputs(UIContext context)
    {
        int mouseX = context.mouseX;
        int mouseY = context.mouseY;

        if (this.isDragging())
        {
            if (this.isDraggingPosition())
            {
                if (this.lastX != context.mouseX || this.lastY != context.mouseY)
                {
                    Vector3d newPoint = this.calculateOnPlane(context);

                    this.pos.set(this.cachedPos);
                    this.pos.sub((float) newPoint.x, (float) newPoint.y, (float) newPoint.z);
                    this.pos.add((float) this.cachedPlaneIntersection.x, (float) this.cachedPlaneIntersection.y, (float) this.cachedPlaneIntersection.z);

                    this.lastX = mouseX;
                    this.lastY = mouseY;
                }
            }
            else
            {
                this.camera.rotation.y -= MathUtils.toRad(this.lastX - mouseX);
                this.camera.rotation.x -= MathUtils.toRad(this.lastY - mouseY);

                this.lastX = mouseX;
                this.lastY = mouseY;
            }
        }
    }

    public void setupPosition()
    {
        this.camera.position.set(this.pos);

        vec.set(0, 0, -this.distance.getValue());
        this.rotateVector(vec);

        this.camera.position.x += vec.x;
        this.camera.position.y += vec.y;
        this.camera.position.z += vec.z;
    }

    private Vector3d calculateOnPlane(UIContext context)
    {
        Vector3d vector = new Vector3d();
        Vector3d origin = new Vector3d(this.cachedCamera.position).sub(this.cachedPos);
        Vector3d destination = new Vector3d(this.cachedCamera.getMouseDirection(context.mouseX, context.mouseY, context.globalX(this.area.x), context.globalY(this.area.y), this.area.w, this.area.h)).mul(this.distance.getValue() * 2).add(origin);
        Intersectiond.intersectLineSegmentPlane(origin.x, origin.y, origin.z, destination.x, destination.y, destination.z, this.plane.x, this.plane.y, this.plane.z, 0, vector);

        return vector;
    }

    private void rotateVector(Vector3d vec)
    {
        mat.identity().rotateX(this.camera.rotation.x);
        mat.transform(vec);
        mat.identity().rotateY(MathUtils.PI - this.camera.rotation.y);
        mat.transform(vec);
    }

    protected void setupViewport(UIContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (this.stencilViewport)
        {
            this.viewportW = this.stencilViewportW;
            this.viewportH = this.stencilViewportH;

            if (this.viewportW > 0 && this.viewportH > 0)
            {
                this.camera.updatePerspectiveProjection(this.viewportW, this.viewportH);
            }

            this.camera.updateView();

            return;
        }

        /* The preview target owns its viewport. Only its physical dimensions are needed here; the
         * UI position is applied later by DrawContext when the target texture is composited. */
        boolean previousWorldRender = BBSRendering.renderingWorld;
        BBSRendering.renderingWorld = false;

        try
        {
            float rx = (float) (mc.getWindow().getWidth() / (double) context.menu.width);
            float ry = (float) (mc.getWindow().getHeight() / (double) context.menu.height);

            this.viewportW = (int) (this.area.w * rx);
            this.viewportH = (int) (this.area.h * ry);
        }
        finally
        {
            BBSRendering.renderingWorld = previousWorldRender;
        }

        if (this.viewportW > 0 && this.viewportH > 0)
        {
            this.camera.updatePerspectiveProjection(this.viewportW, this.viewportH);
        }

        this.camera.updateView();
    }

    /**
     * Draw your model here
     */
    protected abstract void renderUserModel(UIContext context);

    /**
     * Render block of grass under the model (which signify where
     * located the ground below the model)
     */
    protected void renderGrid(UIContext context)
    {
        Matrix4f matrix4f = this.createCameraStack().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int x = 0; x <= 10; x ++)
        {
            if (x == 0)
            {
                builder.vertex(matrix4f, x - 5, 0, -5).color(0F, 0F, 1F, 1F);
                builder.vertex(matrix4f, x - 5, 0, 5).color(0F, 0F, 1F, 1F);
            }
            else
            {
                builder.vertex(matrix4f, x - 5, 0, -5).color(0.25F, 0.25F, 0.25F, 1F);
                builder.vertex(matrix4f, x - 5, 0, 5).color(0.25F, 0.25F, 0.25F, 1F);
            }
        }

        for (int x = 0; x <= 10; x ++)
        {
            if (x == 0)
            {
                builder.vertex(matrix4f, -5, 0, x - 5).color(1F, 0F, 0F, 1F);
                builder.vertex(matrix4f, 5, 0, x - 5).color(1F, 0F, 0F, 1F);
            }
            else
            {
                builder.vertex(matrix4f, -5, 0, x - 5).color(0.25F, 0.25F, 0.25F, 1F);
                builder.vertex(matrix4f, 5, 0, x - 5).color(0.25F, 0.25F, 0.25F, 1F);
            }
        }

        Draw.flushLines(builder);
    }
}
