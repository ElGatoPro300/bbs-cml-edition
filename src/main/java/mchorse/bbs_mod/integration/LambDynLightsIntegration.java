package mchorse.bbs_mod.integration;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.entity.GunProjectileEntity;
import mchorse.bbs_mod.forms.forms.BlockForm;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.LightForm;
import mchorse.bbs_mod.forms.forms.utils.StructureLightSettings;
import mchorse.bbs_mod.morphing.Morph;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;

import dev.lambdaurora.lambdynlights.api.DynamicLightHandlers;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;

public class LambDynLightsIntegration implements DynamicLightsInitializer
{
    @Override
    public void onInitializeDynamicLights()
    {
        DynamicLightHandlers.registerDynamicLightHandler(BBSMod.ACTOR_ENTITY, (ActorEntity entity) -> getLightLevelFromForm(entity.getForm()));
        DynamicLightHandlers.registerDynamicLightHandler(BBSMod.GUN_PROJECTILE_ENTITY, (GunProjectileEntity entity) -> getLightLevelFromForm(entity.getForm()));
        DynamicLightHandlers.registerDynamicLightHandler(EntityType.PLAYER, (PlayerEntity player) ->
        {
            Morph morph = Morph.getMorph(player);

            if (morph == null)
            {
                return 0;
            }

            return getLightLevelFromForm(morph.getForm());
        });
    }

    private static int getLightLevelFromForm(Form form)
    {
        if (form instanceof LightForm lightForm)
        {
            if (!lightForm.enabled.get())
            {
                return 0;
            }

            int level = lightForm.level.get();

            if (level < 0)
            {
                level = 0;
            }
            else if (level > 15)
            {
                level = 15;
            }

            return level;
        }

        if (form instanceof BlockForm blockForm)
        {
            StructureLightSettings sl = blockForm.structureLight.get();
            boolean enabled = (sl != null) ? sl.enabled : blockForm.emitLight.get();
            int intensity = (sl != null) ? sl.intensity : blockForm.lightIntensity.get();
            BlockState state = blockForm.blockState.get();

            if (enabled && state != null && state.getLuminance() > 0)
            {
                return Math.max(0, Math.min(15, Math.min(state.getLuminance(), intensity)));
            }
        }

        return 0;
    }
}

