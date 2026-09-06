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
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

import com.mojang.serialization.MapCodec;

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;

public class LambDynLightsIntegration implements DynamicLightsInitializer
{
    private static final BBSModEntityLuminance LUMINANCE_PROVIDER = new BBSModEntityLuminance();
    private static final EntityLuminance.Type TYPE = EntityLuminance.Type.register(
        Identifier.of("bbs_mod", "form_light"),
        MapCodec.unit(LUMINANCE_PROVIDER)
    );

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context)
    {
        context.entityLightSourceManager().onRegisterEvent().register(Identifier.of("bbs_mod", "dynamic_lights"), registerContext ->
        {
            registerContext.register(BBSMod.ACTOR_ENTITY, LUMINANCE_PROVIDER);
            registerContext.register(BBSMod.GUN_PROJECTILE_ENTITY, LUMINANCE_PROVIDER);
            registerContext.register(EntityType.PLAYER, LUMINANCE_PROVIDER);
        });
    }

    private static class BBSModEntityLuminance implements EntityLuminance
    {
        @Override
        public EntityLuminance.Type type()
        {
            return TYPE;
        }

        @Override
        public int getLuminance(ItemLightSourceManager itemManager, Entity entity)
        {
            if (entity instanceof ActorEntity actor)
            {
                return getLightLevelFromForm(actor.getForm());
            }
            else if (entity instanceof GunProjectileEntity projectile)
            {
                return getLightLevelFromForm(projectile.getForm());
            }
            else if (entity instanceof PlayerEntity player)
            {
                Morph morph = Morph.getMorph(player);

                if (morph != null)
                {
                    return getLightLevelFromForm(morph.getForm());
                }
            }

            return 0;
        }
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

