package mchorse.bbs_mod.ui.utils.gizmo;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.Pair;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Reusable pick -> hover -> drag lifecycle for a single {@link GizmoSurface}, replacing the
 * near-identical "check stencil pick, verify handle range, start drag" block that used to be
 * hand-duplicated in every editor. One instance per surface/editor; the underlying {@link Gizmo}
 * stays a singleton (mode, active handle, deferred render queue are shared across all editors,
 * exactly as before this refactor).
 */
public class GizmoController
{
    private static final int TRACKBALL_DRAG_THRESHOLD_SQ = 16;

    private final GizmoSurface surface;
    private final Vector3d trackballRayOrigin = new Vector3d();
    private final Vector3f trackballRayDirection = new Vector3f();
    private final Matrix4f trackballGizmoMatrix = new Matrix4f();

    private boolean pendingTrackball;
    private int pendingMouseX;
    private int pendingMouseY;
    private UIPropTransform pendingTransform;
    private UIContext pendingContext;
    private Pair<Form, String> pendingClickThrough;

    public GizmoController(GizmoSurface surface)
    {
        this.surface = surface;
    }

    /**
     * Attempts to start a gizmo handle drag from whatever the surface's stencil last picked.
     * Trackball is special: a press over the sphere arms a pending drag so a click-without-drag
     * can still select the bone/form underneath, while a drag starts free-rotate.
     * Returns false (without side effects) if nothing was claimed, so the caller can fall
     * through to its normal (bone/form) pick handling.
     */
    public boolean tryStartHandleDrag(UIContext context, UIPropTransform transform)
    {
        if (transform == null || !Gizmo.isInteractive() || context == null || context.mouseButton != 0)
        {
            return false;
        }

        StencilFormFramebuffer stencil = this.surface.getGizmoStencil();
        int index = stencil.hasPicked() ? stencil.getIndex() : -1;

        if (Gizmo.isHandleIndex(index) && index != Gizmo.STENCIL_TRACKBALL)
        {
            this.clearPendingTrackball();
            this.surface.prepareGizmoDrag(transform);
            transform.setGizmoDragContext(context);

            return Gizmo.INSTANCE.start(index, context.mouseX, context.mouseY, transform);
        }

        if (index == Gizmo.STENCIL_TRACKBALL || this.hitsTrackball(context, transform))
        {
            this.armPendingTrackball(context, transform, this.snapshotClickThrough(stencil, index));

            return true;
        }

        return false;
    }

    /** If a pending trackball click was armed and never dragged, clears it and returns the
     *  bone/form pick that was under the sphere so the editor can select it. */
    public Pair<Form, String> consumePendingTrackballClick()
    {
        if (!this.pendingTrackball)
        {
            return null;
        }

        Pair<Form, String> pick = this.pendingClickThrough;

        this.clearPendingTrackball();

        return pick;
    }

    public boolean hasPendingTrackball()
    {
        return this.pendingTrackball;
    }

    /** Feeds the current stencil pick into the gizmo's continuous hover highlight. Safe to call
     *  every frame regardless of whether a drag is active; call from the surface's render pass
     *  right after its stencil pick is resolved for the frame. */
    public void renderGizmo(UIContext context, Matrix4f projection, Area area)
    {
        Gizmo.INSTANCE.renderInterface(context, projection, area);
    }

    public void updateHover()
    {
        if (!Gizmo.isInteractive())
        {
            Gizmo.INSTANCE.setHoveredIndex(-1);
            this.clearPendingTrackball();

            return;
        }

        this.tickPendingTrackball();

        StencilFormFramebuffer stencil = this.surface.getGizmoStencil();
        int index = stencil.hasPicked() ? stencil.getIndex() : -1;

        Gizmo.INSTANCE.setHoveredIndex(Gizmo.isHandleIndex(index) ? index : -1);
    }

    public void stop()
    {
        this.clearPendingTrackball();
        Gizmo.INSTANCE.stop();
    }

    private void armPendingTrackball(UIContext context, UIPropTransform transform, Pair<Form, String> clickThrough)
    {
        this.surface.prepareGizmoDrag(transform);
        transform.setGizmoDragContext(context);
        this.pendingTrackball = true;
        this.pendingMouseX = context.mouseX;
        this.pendingMouseY = context.mouseY;
        this.pendingTransform = transform;
        this.pendingContext = context;
        this.pendingClickThrough = clickThrough;
    }

    private void tickPendingTrackball()
    {
        if (!this.pendingTrackball || this.pendingTransform == null)
        {
            return;
        }

        UIContext context = this.pendingContext;

        if (context == null)
        {
            context = this.pendingTransform.getContext();
        }

        if (context == null)
        {
            return;
        }

        int dx = context.mouseX - this.pendingMouseX;
        int dy = context.mouseY - this.pendingMouseY;

        if (dx * dx + dy * dy < TRACKBALL_DRAG_THRESHOLD_SQ)
        {
            return;
        }

        UIPropTransform transform = this.pendingTransform;

        this.clearPendingTrackball();
        this.surface.prepareGizmoDrag(transform);
        transform.setGizmoDragContext(context);
        Gizmo.INSTANCE.start(Gizmo.STENCIL_TRACKBALL, context.mouseX, context.mouseY, transform);
    }

    private void clearPendingTrackball()
    {
        this.pendingTrackball = false;
        this.pendingTransform = null;
        this.pendingContext = null;
        this.pendingClickThrough = null;
    }

    private Pair<Form, String> snapshotClickThrough(StencilFormFramebuffer stencil, int index)
    {
        if (!stencil.hasPicked() || Gizmo.isHandleIndex(index))
        {
            return null;
        }

        return stencil.getPicked();
    }

    private boolean hitsTrackball(UIContext context, UIPropTransform transform)
    {
        if (context == null || transform == null || !Gizmo.INSTANCE.isTrackballPickable())
        {
            return false;
        }

        this.surface.prepareGizmoDrag(transform);

        UIPropTransform.IGizmoRayProvider provider = transform.getGizmoRayProvider();

        if (provider == null)
        {
            return false;
        }

        if (!provider.getGizmoMatrix(this.trackballGizmoMatrix))
        {
            return false;
        }

        if (!provider.getMouseRay(context, context.mouseX, context.mouseY, this.trackballRayOrigin, this.trackballRayDirection))
        {
            return false;
        }

        return Gizmo.INSTANCE.hitsTrackball(this.trackballGizmoMatrix, this.trackballRayOrigin, this.trackballRayDirection);
    }
}
