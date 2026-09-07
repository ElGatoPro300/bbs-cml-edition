package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.client.MobTextureOverride;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(RenderLayer.class)
public class RenderLayerTextureOverrideMixin
{
    @ModifyVariable(method = "getEntityCutoutNoCull", at = @At("HEAD"), argsOnly = true, require = 0)
    private static Identifier bbs$overrideEntityCutoutNoCull(Identifier id)
    {
        return MobTextureOverride.getOverridden(id);
    }

    @ModifyVariable(method = "getEntityCutout", at = @At("HEAD"), argsOnly = true, require = 0)
    private static Identifier bbs$overrideEntityCutout(Identifier id)
    {
        return MobTextureOverride.getOverridden(id);
    }

    @ModifyVariable(method = "getEntityTranslucent", at = @At("HEAD"), argsOnly = true, require = 0)
    private static Identifier bbs$overrideEntityTranslucent(Identifier id)
    {
        return MobTextureOverride.getOverridden(id);
    }

    @ModifyVariable(method = "getEntityTranslucentCull", at = @At("HEAD"), argsOnly = true, require = 0)
    private static Identifier bbs$overrideEntityTranslucentCull(Identifier id)
    {
        return MobTextureOverride.getOverridden(id);
    }

    @ModifyVariable(method = "getItemEntityTranslucentCull", at = @At("HEAD"), argsOnly = true, require = 0)
    private static Identifier bbs$overrideItemEntityTranslucentCull(Identifier id)
    {
        return MobTextureOverride.getOverridden(id);
    }

    @ModifyVariable(method = "getOutline", at = @At("HEAD"), argsOnly = true, require = 0)
    private static Identifier bbs$overrideOutline(Identifier id)
    {
        return MobTextureOverride.getOverridden(id);
    }
}
