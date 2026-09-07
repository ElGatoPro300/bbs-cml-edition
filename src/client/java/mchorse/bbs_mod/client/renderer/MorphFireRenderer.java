package mchorse.bbs_mod.client.renderer;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.mixin.client.EntityAccessor;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.interps.Lerps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Renders vanilla entity fire overlay on morph replays.
 */
public final class MorphFireRenderer
{
    private static final Quaternionf TEMP_QUATERNION = new Quaternionf();
    private static final EntityRenderState FIRE_RENDER_STATE = new EntityRenderState();
    private static ActorEntity proxy;

    private MorphFireRenderer()
    {}

    public static void render(MatrixStack matrices, VertexConsumerProvider consumers, IEntity morph, Form form, float tickDelta, Camera camera, boolean relative)
    {
        if (morph.getFireTicks() <= 0)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld world = mc.world;

        if (world == null)
        {
            return;
        }

        if (MorphFireRenderer.proxy == null || MorphFireRenderer.proxy.getEntityWorld() != world)
        {
            MorphFireRenderer.proxy = new ActorEntity(BBSMod.ACTOR_ENTITY, world);
        }

        ActorEntity entity = MorphFireRenderer.proxy;
        float[] size = MorphFireRenderer.getFireDimensions(morph, form);
        float bodyYaw = Lerps.lerp(morph.getPrevBodyYaw(), morph.getBodyYaw(), tickDelta);
        EntityRenderManager dispatcher = mc.getEntityRenderDispatcher();
        boolean irisWorld = BBSRendering.isIrisShadersEnabled() && BBSRendering.isRenderingWorld();

        matrices.push();

        if (irisWorld && !relative)
        {
            /* Iris bakes the terrain matrix into the stack; strip it and rebuild the
             * camera-relative entity transform the same way as ParticleFormRenderer. */
            Matrix4f composed = BBSRendering.stripTerrainPositionMatrix(new Matrix4f(matrices.peek().getPositionMatrix()));
            Matrix4f oriented = new Matrix4f(MatrixStackUtils.getInverseViewRotationMatrix());

            oriented.mul(composed);

            matrices.loadIdentity();
            matrices.multiplyPositionMatrix(MatrixStackUtils.getViewRotationMatrix());
            MatrixStackUtils.multiply(matrices, oriented);
        }
        else if (relative)
        {
            matrices.multiply(camera.getRotation().conjugate(MorphFireRenderer.TEMP_QUATERNION));
        }

        matrices.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.toRad(bodyYaw)));

        FIRE_RENDER_STATE.width = size[0];
        FIRE_RENDER_STATE.height = size[1];

        matrices.pop();
    }

    private static float[] getFireDimensions(IEntity morph, Form form)
    {
        if (form instanceof MobForm mobForm)
        {
            EntityType<?> type = Registries.ENTITY_TYPE.get(Identifier.of(mobForm.mobID.get()));

            if (type != null)
            {
                EntityDimensions dimensions = type.getDimensions();

                if (morph.isSneaking())
                {
                    dimensions = dimensions.scaled(0.8F);
                }

                return new float[] {dimensions.width(), dimensions.height()};
            }
        }

        if (form != null && form.hitbox.get())
        {
            float height = form.hitboxHeight.get();

            if (morph.isSneaking())
            {
                height *= form.hitboxSneakMultiplier.get();
            }

            return new float[] {form.hitboxWidth.get(), height};
        }

        if (morph instanceof MCEntity mcEntity)
        {
            Entity mc = mcEntity.getMcEntity();

            return new float[] {mc.getWidth(), mc.getHeight()};
        }

        AABB hitbox = morph.getPickingHitbox();

        return new float[] {(float) hitbox.w, (float) hitbox.h};
    }
}
