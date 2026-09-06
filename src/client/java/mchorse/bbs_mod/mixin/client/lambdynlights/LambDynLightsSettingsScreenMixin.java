package mchorse.bbs_mod.mixin.client.lambdynlights;

import dev.lambdaurora.spruceui.background.Background;
import dev.lambdaurora.spruceui.background.SimpleColorBackground;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "dev.lambdaurora.lambdynlights.gui.SettingsScreen")
public class LambDynLightsSettingsScreenMixin
{
    @Redirect(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Ldev/lambdaurora/lambdynlights/gui/RandomPrideFlagBackground;random()Ldev/lambdaurora/spruceui/background/Background;",
            remap = false
        ),
        require = 0
    )
    private Background bbs$overridePrideFlagBackground()
    {
        try
        {
            Class<?> prideClass = Class.forName("io.github.queerbric.pride.PrideFlagShapes");

            if (prideClass != null)
            {
                return (Background) Class.forName("dev.lambdaurora.lambdynlights.gui.RandomPrideFlagBackground")
                    .getMethod("random")
                    .invoke(null);
            }
        }
        catch (Throwable ignored)
        {
            /* PrideLib is optional/missing in dev environment or stripped distribution, fallback to safe background */
        }

        return new SimpleColorBackground(0x40000000);
    }
}
