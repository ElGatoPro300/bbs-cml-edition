package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.util.PlayerInput;

import org.lwjgl.glfw.GLFW;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin
{
    private static float getMovementMultiplier(boolean positive, boolean negative)
    {
        return positive == negative ? 0F : (positive ? 1F : -1F);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    public void onTick(CallbackInfo info)
    {
        UIBaseMenu menu = UIScreen.getCurrentMenu();

        if (
            menu instanceof UIDashboard dashboard &&
            dashboard.getPanels().panel instanceof UIFilmPanel filmPanel &&
            filmPanel.getController().isControlling()
        ) {
            KeyboardInput input = (KeyboardInput) (Object) this;

            boolean forward = Window.isKeyPressed(GLFW.GLFW_KEY_W);
            boolean back = Window.isKeyPressed(GLFW.GLFW_KEY_S);
            boolean left = Window.isKeyPressed(GLFW.GLFW_KEY_A);
            boolean right = Window.isKeyPressed(GLFW.GLFW_KEY_D);

            input.movementForward = getMovementMultiplier(forward, back);
            input.movementSideways = getMovementMultiplier(left, right);

            boolean jump = Window.isKeyPressed(GLFW.GLFW_KEY_SPACE);
            boolean sneak = Window.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT);
            boolean sprint = Window.isKeyPressed(GLFW.GLFW_KEY_LEFT_CONTROL);

            input.playerInput = new PlayerInput(forward, back, left, right, jump, sneak, sprint);

            MinecraftClient.getInstance().options.jumpKey.setPressed(jump);
            MinecraftClient.getInstance().options.sneakKey.setPressed(sneak);

            if (MinecraftClient.getInstance().player != null && MinecraftClient.getInstance().player.shouldSlowDown())
            {
                input.movementSideways *= 0.3F;
                input.movementForward *= 0.3F;
            }

            UIFilmController controller = filmPanel.getController();
            boolean moving = input.movementForward != 0F || input.movementSideways != 0F;

            controller.dampenActorControlDrift(moving);
        }
    }
}
