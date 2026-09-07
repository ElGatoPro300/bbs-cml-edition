package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.BBSSettings;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;

/**
 * BBS UI scale helpers. By default the BBS GUI uses {@link BBSSettings#userIntefaceScale}
 * without writing Minecraft's GUI scale. Linking to the game (legacy) is opt-in.
 */
public final class BbsGuiScale
{
    private static boolean restoringGameScale;

    private BbsGuiScale()
    {}

    public static boolean isLinkedToGame()
    {
        return BBSSettings.isUiScaleLinkedToGame();
    }

    public static boolean isRestoringGameScale()
    {
        return restoringGameScale;
    }

    /**
     * Exact BBS scale factor. {@code 0} means “use the window's current (game) scale”.
     */
    public static double getFactor()
    {
        float scale = BBSSettings.getUIScaleFactor();

        if (scale <= 0F)
        {
            MinecraftClient mc = MinecraftClient.getInstance();

            return mc == null || mc.getWindow() == null ? 1D : mc.getWindow().getScaleFactor();
        }

        return scale;
    }

    public static int getScaledWidth()
    {
        Window window = MinecraftClient.getInstance().getWindow();

        return scaledSize(window.getFramebufferWidth(), getFactor());
    }

    public static int getScaledHeight()
    {
        Window window = MinecraftClient.getInstance().getWindow();

        return scaledSize(window.getFramebufferHeight(), getFactor());
    }

    public static void resizeMenu(UIBaseMenu menu)
    {
        if (menu != null)
        {
            menu.resize(getScaledWidth(), getScaledHeight());
        }
    }

    public static int toBbsMouseX(double mouseX)
    {
        return toBbsMouse(mouseX);
    }

    public static int toBbsMouseY(double mouseY)
    {
        return toBbsMouse(mouseY);
    }

    public static void restoringGameScale(Runnable runnable)
    {
        restoringGameScale = true;

        try
        {
            runnable.run();
        }
        finally
        {
            restoringGameScale = false;
        }
    }

    /**
     * While BBS draws, point the window scale factor at the BBS value so scissor
     * and GUI projection match BBS coordinates. Restores the game scale afterward
     * so the hotbar / vanilla menus stay on Minecraft's GUI scale.
     */
    public static void withBbsWindowScale(Runnable draw)
    {
        if (isLinkedToGame())
        {
            draw.run();

            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Window window = mc.getWindow();
        double saved = window.getScaleFactor();
        ProjectionType savedProjectionType = RenderSystem.getProjectionType();
        Matrix4f savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());

        try
        {
            window.setScaleFactor(getFactor());
            int sw = window.getScaledWidth();
            int sh = window.getScaledHeight();
            RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0, sw, sh, 0, -1000, 3000), ProjectionType.ORTHOGRAPHIC);
            /* GameRenderer's GUI pass leaves modelView at z=-11000; with ortho
             * -1000..3000 that clips every vertex. Identity matches HUD overlays. */
            Matrix4fStack modelView = RenderSystem.getModelViewStack();

            modelView.pushMatrix();
            modelView.identity();

            try
            {
                draw.run();
            }
            finally
            {
                modelView.popMatrix();
            }
        }
        finally
        {
            restoringGameScale(() -> window.setScaleFactor(saved));
            RenderSystem.setProjectionMatrix(savedProjection, savedProjectionType);
        }
    }

    private static int toBbsMouse(double mouse)
    {
        if (isLinkedToGame())
        {
            return (int) mouse;
        }

        Window window = MinecraftClient.getInstance().getWindow();
        double bbs = getFactor();

        if (bbs <= 0D)
        {
            return (int) mouse;
        }

        return (int) (mouse * window.getScaleFactor() / bbs);
    }

    private static int scaledSize(int framebuffer, double factor)
    {
        if (factor <= 0D)
        {
            return Math.max(1, framebuffer);
        }

        int i = (int) (framebuffer / factor);

        return framebuffer / factor > i ? i + 1 : i;
    }
}
