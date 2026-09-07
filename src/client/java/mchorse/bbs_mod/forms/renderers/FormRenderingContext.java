package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.utils.TextureBlend;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix4f;

public class FormRenderingContext
{
    public FormRenderType type;
    public IEntity entity;
    public MatrixStack stack;
    public MatrixStack world;
    public int light;
    public int overlay;
    public float transition;
    public final Camera camera = new Camera();
    public StencilMap stencilMap;
    public boolean ui;
    public int color;
    public boolean modelRenderer;
    public boolean relative;
    public boolean isShadowPass;
    public Matrix4f viewMatrix;
    public boolean renderEquipment;

    /** Overrides the form texture for this render pass only (e.g. illusion copies). */
    public Link textureOverride;

    /** Overrides texture crossfade for this render pass only. */
    public TextureBlend textureBlendOverride;

    /**
     * Trail history slot. {@code 0} is the primary form; illusion copies use a
     * unique positive id so each keeps an independent trail instead of sharing
     * (and teleporting) the main ribbon.
     */
    public int trailInstance;

    public FormRenderingContext()
    {}

    public FormRenderingContext set(FormRenderType type, IEntity entity, MatrixStack stack, int light, int overlay, float transition)
    {
        this.type = type == null ? FormRenderType.ENTITY : type;
        this.entity = entity;
        this.stack = stack;
        this.world = new MatrixStack();
        this.light = light;
        this.overlay = overlay;
        this.transition = transition;
        this.stencilMap = null;
        this.ui = false;
        this.color = 0xffffffff;
        this.relative = false;
        this.isShadowPass = false;
        this.viewMatrix = null;
        this.renderEquipment = true;
        this.textureOverride = null;
        this.textureBlendOverride = null;
        this.trailInstance = 0;

        if (entity != null && (this.type == FormRenderType.ENTITY || this.type == FormRenderType.MODEL_BLOCK))
        {
            double x = Lerps.lerp(entity.getPrevX(), entity.getX(), transition);
            double y = Lerps.lerp(entity.getPrevY(), entity.getY(), transition);
            double z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition);

            float bodyYaw = Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition);

            this.world.translate(x, y, z);
            this.world.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        }

        return this;
    }

    public FormRenderingContext camera(Camera camera)
    {
        this.camera.copy(camera);
        this.camera.updateView();

        return this;
    }

    public FormRenderingContext camera(net.minecraft.client.render.Camera camera)
    {
        this.camera.position.set(camera.getCameraPos().x, camera.getCameraPos().y, camera.getCameraPos().z);

        float rollDeg = 0F;
        CameraController controller = BBSModClient.getCameraController();

        if (controller.getCurrent() != null)
        {
            rollDeg = controller.getRoll();
        }

        this.camera.rotation.set(MathUtils.toRad(-camera.getPitch()), MathUtils.toRad(camera.getYaw()), MathUtils.toRad(rollDeg));
        this.camera.view.identity().rotate(camera.getRotation());

        if (Math.abs(rollDeg) > 1.0E-4F)
        {
            /* Match GameRendererMixin.tiltViewWhenHurt: roll is applied as Z after yaw/pitch. */
            this.camera.view.rotateZ(MathUtils.toRad(rollDeg));
        }

        this.camera.fov = MathUtils.toRad(MinecraftClient.getInstance().options.getFov().getValue());

        return this;
    }

    public FormRenderingContext stencilMap(StencilMap stencilMap)
    {
        this.stencilMap = stencilMap;

        return this;
    }

    public FormRenderingContext inUI()
    {
        this.ui = true;

        return this;
    }

    public FormRenderingContext color(int color)
    {
        this.color = color;

        return this;
    }

    public FormRenderingContext modelRenderer()
    {
        this.modelRenderer = true;

        return this;
    }

    /**
     * Orbit UI, form/model-block pickable preview, and inventory GUI items: draw soft live.
     * World post-deferred / Iris soft queues are never flushed for those passes — translucent
     * forms would vanish (inventory slots draw after the world flush).
     */
    public boolean isLocalPreview()
    {
        return this.ui
            || this.modelRenderer
            || this.type == FormRenderType.PREVIEW
            || this.type == FormRenderType.ITEM_INVENTORY;
    }

    public FormRenderingContext equipment(boolean renderEquipment)
    {
        this.renderEquipment = renderEquipment;

        return this;
    }

    public float getTransition()
    {
        return this.transition;
    }

    public boolean isPicking()
    {
        return this.stencilMap != null;
    }

    public int getPickingIndex()
    {
        return this.stencilMap == null ? -1 : this.stencilMap.objectIndex;
    }
}
