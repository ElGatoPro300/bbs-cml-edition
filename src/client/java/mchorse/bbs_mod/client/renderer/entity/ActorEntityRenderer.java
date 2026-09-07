package mchorse.bbs_mod.client.renderer.entity;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.client.renderer.MorphFireRenderer;
import mchorse.bbs_mod.cubic.render.vanilla.ArmorRenderer;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.utils.FormDeathTilt;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Atlases;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import com.mojang.blaze3d.opengl.GlStateManager;

import org.lwjgl.opengl.GL11;

public class ActorEntityRenderer extends EntityRenderer<ActorEntity, ActorEntityRenderer.ActorEntityState>
{
    public static class ActorEntityState extends LivingEntityRenderState {
        public ActorEntity entity;
        public float tickDelta;
        public float bodyYaw;
        public float prevBodyYaw;
        public float deathTime;
        public boolean isSleeping;
    }

    public static ArmorRenderer armorRenderer;

    public ActorEntityRenderer(EntityRendererFactory.Context ctx)
    {
        super(ctx);

        /* Private copies — ArmorRenderer mutates pivots/wings; never share with vanilla players. */
        armorRenderer = new ArmorRenderer(
            new BipedEntityModel(ctx.getPart(EntityModelLayers.PLAYER_EQUIPMENT.getModelData(EquipmentSlot.LEGS))),
            new BipedEntityModel(ctx.getPart(EntityModelLayers.PLAYER_EQUIPMENT.getModelData(EquipmentSlot.CHEST))),
            new ElytraEntityModel(ctx.getPart(EntityModelLayers.ELYTRA)),
            MinecraftClient.getInstance().getAtlasManager().getAtlasTexture(Atlases.ARMOR_TRIMS)
        );

        // this.shadowRadius = 0.5F;
    }

    /**
     * Keep dispatcher {@link #shadowRadius} in sync with this entity's film shadow.
     * Without shaders the ground blob is drawn in {@link #render} (size X/Z + offset);
     * with a shader pack the vanilla radius is used so packs that still sample the
     * shadow {@code .png} can respect the replay toggle / size.
     */
    public static void updateShadowRadius(ActorEntity entity)
    {
        if (entity == null)
        {
            return;
        }

        EntityRenderer<?, ?> renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity);

        if (renderer instanceof ActorEntityRenderer actorRenderer)
        {
            actorRenderer.applyShadowRadius(entity);
        }
    }

    private void applyShadowRadius(ActorEntity entity)
    {
        if (!entity.shouldRenderFilmGroundShadow())
        {
            this.shadowRadius = 0F;

            return;
        }

        float radius = Math.max(entity.getFilmShadowRadiusX(), entity.getFilmShadowRadiusZ());

        if (BBSRendering.isIrisShadersEnabled())
        {
            /* Packs that still draw the vanilla shadow texture honor this; Comp/BSL
             * mesh shadows are separate and stay as they are for stubs. */
            this.shadowRadius = radius;
        }
        else
        {
            /* Custom blob below handles XZ / offset — suppress the circular default. */
            this.shadowRadius = 0F;
        }
    }

    @Override
    public ActorEntityState createRenderState() {
        return new ActorEntityState();
    }

    @Override
    public void updateRenderState(ActorEntity entity, ActorEntityState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.entity = entity;
        state.tickDelta = tickDelta;
        state.bodyYaw = entity.getBodyYaw();
        state.prevBodyYaw = entity.lastBodyYaw;
        state.deathTime = (float)entity.deathTime;
        state.isSleeping = entity.isInPose(EntityPose.SLEEPING);
    }

    public Identifier getTexture(ActorEntityState state)
    {
        return Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    }

    @Override
    public void render(ActorEntityState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState)
    {
        ActorEntity livingEntity = state.entity;
        if (livingEntity == null) return;

        float tickDelta = state.tickDelta;

        this.applyShadowRadius(livingEntity);

        if (this.shouldDrawCustomGroundShadow(livingEntity))
        {
            this.renderFilmGroundShadow(livingEntity, tickDelta, matrices, MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers());
        }
        matrices.push();

        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, state.prevBodyYaw, state.bodyYaw);
        int overlay = livingEntity.shouldShowDamageFlashOverlay()
            ? LivingEntityRenderer.getOverlay(state, 0F)
            : OverlayTexture.DEFAULT_UV;
        float animDelta = livingEntity.areNaturalAnimationsPaused() ? 0F : tickDelta;

        this.setupTransforms(livingEntity, matrices, bodyYaw, animDelta);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        FormUtilsClient.render(livingEntity.getForm(), new FormRenderingContext()
            .set(FormRenderType.ENTITY, livingEntity.getWrappingEntity(), matrices, state.light, overlay, animDelta)
            .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));

        if (livingEntity.getWrappingEntity().getFireTicks() > 0)
        {
            MorphFireRenderer.render(
                matrices,
                MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers(),
                livingEntity.getWrappingEntity(),
                livingEntity.getForm(),
                animDelta,
                MinecraftClient.getInstance().gameRenderer.getCamera(),
                false
            );
        }

        BBSRendering.restoreWorldRenderState();
        GlStateManager._disableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
        GlStateManager._disableBlend();

        matrices.pop();

        super.render(state, matrices, queue, cameraState);
    }

    private boolean shouldDrawCustomGroundShadow(ActorEntity entity)
    {
        return entity.shouldRenderFilmGroundShadow()
            && !BBSRendering.isIrisShadersEnabled()
            && !BBSRendering.isIrisShadowPass();
    }

    private void renderFilmGroundShadow(ActorEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers)
    {
        double x = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) + entity.getFilmShadowOffsetX();
        double y = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY());
        double z = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) + entity.getFilmShadowOffsetZ();

        matrices.push();
        /* X/Z follow the sample point; Y lifts the PNG (entity Y stays at feet to avoid fade). */
        matrices.translate(entity.getFilmShadowOffsetX(), 0F, entity.getFilmShadowOffsetZ());
        ModelBlockEntityRenderer.renderShadow(
            vertexConsumers,
            matrices,
            tickDelta,
            x,
            y,
            z,
            0F,
            entity.getFilmShadowOffsetY(),
            0F,
            entity.getFilmShadowRadiusX(),
            entity.getFilmShadowRadiusZ(),
            entity.getFilmShadowOpacity());
        matrices.pop();
    }

    @Override
    protected boolean hasLabel(ActorEntity entity, double squaredDistanceToCamera)
    {
        /* Same visibility rules as stub film nametags / vanilla labels. */
        return entity.hasCustomName();
    }

    protected boolean isVisible(ActorEntity entity)
    {
        return !entity.isInvisible();
    }

    protected void setupTransforms(ActorEntity entity, MatrixStack matrices, float bodyYaw, float tickDelta)
    {
        if (!entity.isInPose(EntityPose.SLEEPING))
        {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        }

        /* Float death_time tip for ModelForm and MobForm (morph.deathTime stays 0). */
        FormDeathTilt.apply(matrices, new MCEntity(entity), entity.getForm(), tickDelta);
    }
}
