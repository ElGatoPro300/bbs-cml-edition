package mchorse.bbs_mod.ui.film.toolbar;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

import org.joml.Vector3d;

import org.lwjgl.glfw.GLFW;

import java.util.function.Supplier;

/**
 * Viewport click-to-confirm interaction with pulsing primary-color edge fade
 * and a hint card at the bottom-left of the preview area.
 */
public class UIViewportInteraction
{
    private ViewportInteractionState state;

    public boolean isActive()
    {
        return this.state != null;
    }

    public void enter(ViewportInteractionState state)
    {
        this.state = state;
    }

    public void cancel()
    {
        this.state = null;
    }

    public IKey getHint()
    {
        return this.state == null ? null : this.state.hint;
    }

    /**
     * @return {@code true} if the event was consumed
     */
    public boolean handleMouseClicked(UIContext context, Area viewport, Supplier<Vector3d> rayTrace)
    {
        if (this.state == null)
        {
            return false;
        }

        if (context.mouseButton == 1)
        {
            this.cancel();

            return true;
        }

        if (context.mouseButton != 0)
        {
            return false;
        }

        /* Ray trace uses absolute viewport bounds; do not gate on the (possibly local)
           area passed by the caller so floating / nested previews still confirm. */
        Vector3d hit = rayTrace.get();

        if (hit != null)
        {
            this.state.onConfirm.accept(hit);
            this.cancel();
        }

        return true;
    }

    /**
     * @return {@code true} if the event was consumed
     */
    public boolean handleKeyPressed(UIContext context)
    {
        if (this.state == null)
        {
            return false;
        }

        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.cancel();

            return true;
        }

        return false;
    }

    public void renderOverlay(UIContext context, Area viewport)
    {
        if (this.state == null)
        {
            return;
        }

        renderEdgeFade(context, viewport);
    }

    public void renderHint(UIContext context, Area viewport, int bottomReserve)
    {
        if (this.state == null)
        {
            return;
        }

        TimelineInteractionHints.renderViewportHint(context, viewport, this.state.hint, bottomReserve);
    }

    public static void renderEdgeFade(UIContext context, Area viewport)
    {
        float alpha = TimelineInteractionHints.getPulseAlpha(
            TimelineToolbarSettings.INTERACTION_CLIP_PULSE_MIN_ALPHA,
            TimelineToolbarSettings.INTERACTION_CLIP_PULSE_MAX_ALPHA);
        int primary = BBSSettings.primaryColor.get();
        int edge = Colors.setA(primary, alpha);
        int transparent = Colors.setA(primary, 0F);
        int size = TimelineToolbarSettings.INTERACTION_VIEWPORT_EDGE_SIZE;

        context.batcher.gradientVBox(viewport.x, viewport.y, viewport.ex(), viewport.y + size, edge, transparent);
        context.batcher.gradientVBox(viewport.x, viewport.ey() - size, viewport.ex(), viewport.ey(), transparent, edge);
        context.batcher.gradientHBox(viewport.x, viewport.y, viewport.x + size, viewport.ey(), edge, transparent);
        context.batcher.gradientHBox(viewport.ex() - size, viewport.y, viewport.ex(), viewport.ey(), transparent, edge);
    }
}
