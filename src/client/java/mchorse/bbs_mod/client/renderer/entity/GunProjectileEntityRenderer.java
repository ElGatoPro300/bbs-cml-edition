package mchorse.bbs_mod.client.renderer.entity;

import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.items.GunProperties;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.interps.Lerps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import com.mojang.blaze3d.opengl.GlStateManager;

public class GunProjectileEntityRenderer extends EntityRenderer<GunProjectileEntity, GunProjectileEntityRenderer.GunProjectileEntityState>
{
    public static class GunProjectileEntityState extends EntityRenderState {
        public GunProjectileEntity projectile;
        public float tickDelta;
    }

    public GunProjectileEntityRenderer(EntityRendererFactory.Context ctx)
    {
        super(ctx);
    }

    @Override
    public GunProjectileEntityState createRenderState() {
        return new GunProjectileEntityState();
    }

    @Override
    public void updateRenderState(GunProjectileEntity entity, GunProjectileEntityState state, float tickDelta) {
        super.updateRenderState(entity, state, tickDelta);
        state.projectile = entity;
        state.tickDelta = tickDelta;
    }

    public Identifier getTexture(GunProjectileEntityState state)
    {
        return Identifier.of("minecraft", "textures/entity/player/wide/steve.png");
    }

    @Override
    public void render(GunProjectileEntityState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState)
    {
        GunProjectileEntity projectile = state.projectile;
        if (projectile == null) return;
        
        float tickDelta = state.tickDelta;

        matrices.push();

        GunProperties properties = projectile.getProperties();
        int out = properties.lifeSpan - 2;

        float bodyYaw = projectile.getYaw();
        float pitch = projectile.getPitch();
        float scale = Lerps.envelope(projectile.age + tickDelta, 0, properties.fadeIn, out - properties.fadeOut, out);

        if (properties.yaw) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bodyYaw));
        if (properties.pitch) matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pitch));
        matrices.scale(scale, scale, scale);
        MatrixStackUtils.applyTransform(matrices, properties.projectileTransform);

        GlStateManager._enableDepthTest();
        FormUtilsClient.render(projectile.getForm(), new FormRenderingContext()
            .set(FormRenderType.ENTITY, new MCEntity(projectile), matrices, state.light, OverlayTexture.DEFAULT_UV, tickDelta)
            .camera(MinecraftClient.getInstance().gameRenderer.getCamera()));
        GlStateManager._disableDepthTest();

        matrices.pop();

        super.render(state, matrices, queue, cameraState);
    }
}
