package mchorse.bbs_mod.ui.film.toolbar;

import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.colors.Colors;

import org.joml.Vector3i;

import org.lwjgl.glfw.GLFW;

/**
 * Cursor-following clip placement preview with pulsing outline, hint text and
 * Esc / RMB cancel. Used by toolbar add/import/microphone flows and future
 * Phase 2d/3 clip placement actions.
 */
public class UIClipPlacementInteraction
{
    private ClipPlacementInteractionState state;

    public boolean isActive()
    {
        return this.state != null;
    }

    public void enter(ClipPlacementInteractionState state)
    {
        this.state = state;
    }

    public void cancel(UIClips clips)
    {
        this.state = null;
        clips.clearPlacementPreview();
    }

    public void updatePreview(UIClips clips, UIContext context)
    {
        if (this.state == null || clips.hasEmbeddedView())
        {
            return;
        }

        if (!clips.getVerticalArea().isInside(context))
        {
            clips.clearPlacementPreview();

            return;
        }

        int tick = this.state.lockedTick >= 0
            ? this.state.lockedTick
            : clips.fromGraphX(context.mouseX);
        int layer = this.state.lockedLayer >= 0
            ? this.state.lockedLayer
            : clips.fromLayerY(context.mouseY);

        if (layer < 0)
        {
            clips.clearPlacementPreview();

            return;
        }

        clips.setPlacementPreview(clips.computePlacementSize(tick, layer, this.state.duration));
    }

    /**
     * @return {@code true} if the event was consumed
     */
    public boolean handleMouseClicked(UIClips clips, UIContext context)
    {
        if (this.state == null)
        {
            return false;
        }

        if (context.mouseButton == 1)
        {
            this.cancel(clips);

            return true;
        }

        if (context.mouseButton != 0)
        {
            return false;
        }

        if (!clips.getVerticalArea().isInside(context))
        {
            return true;
        }

        Vector3i preview = clips.getPlacementPreview();

        if (preview != null)
        {
            this.state.onConfirm.place(preview.x, preview.y, preview.z);
            this.cancel(clips);
        }
        else
        {
            clips.getContext().notifyError(UIKeys.CAMERA_TIMELINE_CANT_FIT_NOTIFICATION);
        }

        return true;
    }

    /**
     * @return {@code true} if the event was consumed
     */
    public boolean handleKeyPressed(UIClips clips, UIContext context)
    {
        if (this.state == null)
        {
            return false;
        }

        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.cancel(clips);

            return true;
        }

        return false;
    }

    public void renderHint(UIContext context, Area area, UIElement source)
    {
        if (this.state == null)
        {
            return;
        }

        TimelineInteractionHints.renderHint(context, area, this.state.hint, source);
    }

    public static void renderPulsingOutline(UIContext context, int x, int y, int ex, int ey)
    {
        float alpha = TimelineInteractionHints.getPulseAlpha(
            TimelineToolbarSettings.INTERACTION_CLIP_PULSE_MIN_ALPHA,
            TimelineToolbarSettings.INTERACTION_CLIP_PULSE_MAX_ALPHA);
        int outline = Colors.setA(Colors.WHITE, alpha);
        int fill = Colors.setA(Colors.WHITE, alpha * 0.2F);

        context.batcher.box(x, y, ex, ey, fill);
        context.batcher.outline(x, y, ex, ey, outline);
    }

    public static void renderPulsingTickColumn(UIContext context, int x, int y, int ey)
    {
        TimelineInteractionHints.renderPulsingTickColumn(context, x, y, ey);
    }
}
