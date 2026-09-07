package mchorse.bbs_mod.client.renderer.entity;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.ModelBlockEntityRenderer;
import mchorse.bbs_mod.client.renderer.MorphFireRenderer;
import mchorse.bbs_mod.cubic.render.vanilla.ArmorRenderer;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.utils.FormDeathTilt;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.ArmorEntityModel;
import net.minecraft.client.render.entity.model.ElytraEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

public class ActorEntityRenderer extends EntityRenderer<ActorEntity>
{
    public static ArmorRenderer armorRenderer;

    public ActorEntityRenderer(EntityRendererFactory.Context ctx)
    {
        super(ctx);

        /* Private copies — ArmorRenderer mutates pivots/wings; never share with vanilla players. */
        armorRenderer = new ArmorRenderer(
            new ArmorEntityModel(TexturedModelData.of(ArmorEntityModel.getModelData(new Dilation(0.5F)), 64, 32).createModel()),
            new ArmorEntityModel(TexturedModelData.of(ArmorEntityModel.getModelData(new Dilation(1.0F)), 64, 32).createModel()),
            new ElytraEntityModel(ElytraEntityModel.getTexturedModelData().createModel()),
            ctx.getModelManager()
        );

        this.shadowRadius = 0.5F;
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

        EntityRenderer<?> renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(entity);

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
    public Identifier getTexture(ActorEntity entity)
    {
        return Identifier.of("minecraft:textures/entity/player/wide/steve.png");
    }

    @Override
    public void render(ActorEntity livingEntity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light)
    {
        this.applyShadowRadius(livingEntity);

        if (this.shouldDrawCustomGroundShadow(livingEntity))
        {
            this.renderFilmGroundShadow(livingEntity, tickDelta, matrices, vertexConsumers);
        }

        matrices.push();

        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, livingEntity.prevBodyYaw, livingEntity.bodyYaw);
        int overlay = livingEntity.shouldShowDamageFlashOverlay()
            ? LivingEntityRenderer.getOverlay(livingEntity, 0F)
            : OverlayTexture.DEFAULT_UV;
        float animDelta = livingEntity.areNaturalAnimationsPaused() ? 0F : tickDelta;

        this.setupTransforms(livingEntity, matrices, bodyYaw, animDelta);

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        FormUtilsClient.render(livingEntity.getForm(), new FormRenderingContext()
            .set(FormRenderType.ENTITY, livingEntity.getEntity(), matrices, light, overlay, animDelta)
            .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));

        if (livingEntity.getEntity().getFireTicks() > 0)
        {
            MorphFireRenderer.render(
                matrices,
                vertexConsumers,
                livingEntity.getEntity(),
                livingEntity.getForm(),
                animDelta,
                MinecraftClient.getInstance().gameRenderer.getCamera(),
                false
            );
        }

        BBSRendering.restoreWorldRenderState();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.disableBlend();

        matrices.pop();

        super.render(livingEntity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    private boolean shouldDrawCustomGroundShadow(ActorEntity entity)
    {
        return entity.shouldRenderFilmGroundShadow()
            && !BBSRendering.isIrisShadersEnabled()
            && !BBSRendering.isIrisShadowPass();
    }

    private void renderFilmGroundShadow(ActorEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers)
    {
        double x = MathHelper.lerp(tickDelta, entity.prevX, entity.getX()) + entity.getFilmShadowOffsetX();
        double y = MathHelper.lerp(tickDelta, entity.prevY, entity.getY());
        double z = MathHelper.lerp(tickDelta, entity.prevZ, entity.getZ()) + entity.getFilmShadowOffsetZ();

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
    protected boolean hasLabel(ActorEntity entity)
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
        FormDeathTilt.apply(matrices, entity.getEntity(), entity.getForm(), tickDelta);
    }
}
