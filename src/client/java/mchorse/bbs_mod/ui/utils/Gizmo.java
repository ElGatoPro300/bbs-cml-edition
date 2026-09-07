package mchorse.bbs_mod.ui.utils;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoController;
import mchorse.bbs_mod.ui.utils.gizmo.GizmoMatrixUtils;
import mchorse.bbs_mod.ui.utils.gizmo.TransformOrientation;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import org.joml.Intersectiond;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CML-native gizmo core.
 *
 * Owns the addon-facing handle registry, the deferred (Iris) render queue, and the
 * translate/scale/rotate/combined/trackball handle table used by every 3D viewport
 * in the editor (Model, Form, Film replays, Animation state). Individual viewports
 * feed their picking/hover/drag lifecycle through {@link GizmoController}
 * instead of talking to this class directly, but this class remains a singleton so
 * addons registered through {@link #register(int, IGizmoHandler)} keep working unchanged.
 */
public class Gizmo
{
    private static final class DeferredGizmo
    {
        private final Matrix4f matrix;
        private final Matrix4f projection;
        private final boolean stencil;
        private final StencilMap stencilMap;

        private DeferredGizmo(Matrix4f matrix, boolean stencil, StencilMap stencilMap)
        {
            this.matrix = matrix;
            this.projection = new Matrix4f(RenderSystem.getProjectionMatrix());
            this.stencil = stencil;
            this.stencilMap = stencilMap;
        }
    }

    public interface IGizmoHandler
    {
        public void start(Gizmo gizmo, int index, int mouseX, int mouseY, UIPropTransform transform);
    }

    /* Solo-mode handle ids (translate/scale/rotate), unchanged since the original
     * implementation. Any precompiled addon referencing these constants keeps working. */
    public final static int STENCIL_X = 1;
    public final static int STENCIL_Y = 2;
    public final static int STENCIL_Z = 3;
    public final static int STENCIL_XZ = 4;
    public final static int STENCIL_XY = 5;
    public final static int STENCIL_ZY = 6;
    public final static int STENCIL_FREE = 7;

    /* Combined-mode-only handle ids: scale and rotate need distinct ids from the
     * move bars/planes above so translate + scale + rotate can be picked simultaneously. */
    public final static int STENCIL_SCALE_X = 8;
    public final static int STENCIL_SCALE_Y = 9;
    public final static int STENCIL_SCALE_Z = 10;
    public final static int STENCIL_ROTATE_X = 11;
    public final static int STENCIL_ROTATE_Y = 12;
    public final static int STENCIL_ROTATE_Z = 13;

    /* Free-rotate trackball hit sphere, available in solo Rotate and Combined mode. */
    public final static int STENCIL_TRACKBALL = 14;

    /* Reserved for a future screen-space translate handle. Not drawn or picked yet. */
    public final static int STENCIL_SCREEN = 15;

    /* Camera-facing view/arcball ring, drawn in Combined mode. */
    public final static int STENCIL_VIEW = 16;

    public final static int STENCIL_HANDLE_MAX = STENCIL_VIEW;

    private static final float[] COLOR_ACTIVE = { 1.00F, 1.00F, 1.00F };
    private static final float[] COLOR_X_IDLE = { 0.80F, 0.28F, 0.28F };
    private static final float[] COLOR_X_HOVER = { 1.00F, 0.35F, 0.35F };
    private static final float[] COLOR_Y_IDLE = { 0.30F, 0.75F, 0.35F };
    private static final float[] COLOR_Y_HOVER = { 0.40F, 1.00F, 0.45F };
    private static final float[] COLOR_Z_IDLE = { 0.28F, 0.50F, 0.95F };
    private static final float[] COLOR_Z_HOVER = { 0.35F, 0.62F, 1.00F };
    /* Trackball-only mode: ~1.75× the rotate/combined inner trackball sphere. */
    private static final float TOP_TRACKBALL_SIZE_FACTOR = 1.85F;
    /* Plane handle colors are a vivid blend of their two constituent axis colors
     * (X = red, Y = green, Z = blue), so e.g. the XY plane reads as a saturated
     * yellow (red + green) rather than an arbitrary unrelated hue. */
    private static final float[] COLOR_XZ_IDLE = { 0.85F, 0.25F, 0.85F };
    private static final float[] COLOR_XZ_HOVER = { 1.00F, 0.35F, 1.00F };
    private static final float[] COLOR_XY_IDLE = { 0.85F, 0.80F, 0.20F };
    private static final float[] COLOR_XY_HOVER = { 1.00F, 0.95F, 0.25F };
    private static final float[] COLOR_ZY_IDLE = { 0.20F, 0.75F, 0.80F };
    private static final float[] COLOR_ZY_HOVER = { 0.30F, 0.90F, 0.95F };
    private static final float[] COLOR_FREE_IDLE = { 1.00F, 1.00F, 1.00F };
    private static final float[] COLOR_VIEW_IDLE = { 0.80F, 0.80F, 0.80F };
    private static final float[] COLOR_VIEW_HOVER = { 1.00F, 1.00F, 1.00F };

    private static final float PLANE_ALPHA_IDLE = 0.62F;
    private static final float PLANE_ALPHA_HOVER = 0.82F;
    private static final float PLANE_ALPHA_ACTIVE = 0.95F;

    /* Relative sizing so the rotate rings visibly enclose the move arrows and scale cubes
     * (largest to smallest: rotate rings > scale cubes > move arrows), matching a Blender-style
     * combined gizmo cage rather than three same-size handle sets stacked on top of each other. */
    private static final float COMBINED_MOVE_SCALE = 0.85F;
    private static final float COMBINED_ROTATE_SCALE = 1.55F;
    private static final float COMBINED_SCALE_HANDLE_SCALE = 1.0F;
    /* Style 1 vs Style 2 share orbit size/thickness; they only differ in Combined tip
     * placement (cubes inside vs just outside the white view ring). Style 3 drops cones
     * and uses tip cubes on short shafts that reach near the colored orbit. */
    private static final float STYLE1_AXIS_LENGTH_FACTOR = 0.50F;
    /* Shaft + cone stay inside the colored orbits; scale cubes sit just outside the view ring. */
    private static final float STYLE2_AXIS_LENGTH_FACTOR = 0.68F;
    /* Short axis + tip cube — near the colored ring (between Style 1 mid-length and full orbit). */
    private static final float STYLE3_AXIS_LENGTH_FACTOR = 0.88F;
    private static final float VIEW_RING_SCALE = 1.12F;

    public final static Gizmo INSTANCE = new Gizmo();

    private Mode mode = Mode.TRANSLATE;
    private TransformOrientation activeOrientation = TransformOrientation.PARENT;
    /* BBSSettings.gizmoDefaultMode can't be read at class-init time (settings aren't loaded
     * from disk yet), so the configured starting mode is applied lazily the first time the
     * gizmo actually renders instead. */
    private boolean appliedDefaultMode = false;

    private int index = -1;
    private int hoveredIndex = -1;

    public final Matrix4f lastGizmoMatrix = new Matrix4f();
    public boolean hasGizmoMatrix;

    private UIPropTransform currentTransform;
    private Map<Integer, IGizmoHandler> handlers = new HashMap<>();
    private final List<DeferredGizmo> deferredGizmos = new ArrayList<>();

    private float lastSx = 1F;
    private float lastSy = 1F;
    private float lastSz = 1F;
    /** Radius of the last drawn trackball sphere in gizmo-local units (for click/drag picking). */
    private float lastTrackballRadius;

    /* Direction from the gizmo origin toward the camera in gizmo-local space, refreshed
     * every frame by computeScale(). Used to billboard the view/arcball ring. */
    private final Vector3f lastCamDir = new Vector3f(0F, 1F, 0F);
    /** Camera distance used by the last {@link #computeScale(MatrixStack)} call. */
    private float lastScaleDist;

    /* Visual state of the in-progress rotation sweep: which ring is being rotated, at what
     * angle (in the ring's arc3D u-parameterization) the drag started, and how far it has
     * swept since. Purely cosmetic; fed by UIPropTransform's ring drag. */
    private boolean arcActive;
    /** True when the active sweep belongs to the camera-facing view ring instead of an
     *  axis ring; its angles are measured in the billboarded ring's own space. */
    private boolean arcView;
    private Axis arcAxis = Axis.Y;
    private float arcStartU;
    private float arcLastU;
    private float arcSweep;
    /** When true the sweep fan is drawn in the gizmo orientation captured at drag start
     *  instead of the live (rotating) matrix, so local-mode drags keep a fixed origin. */
    private boolean arcFrozenOrientation;
    private final Matrix4f arcFrozenMatrix = new Matrix4f();
    private boolean arcFrozenViewRing;
    private final Vector3f arcFrozenCamDir = new Vector3f(0F, 1F, 0F);

    /* Yellow progress segment drawn while a translate/scale handle is being dragged. */
    private boolean dragProgressActive;
    private final Vector3f dragProgressStart = new Vector3f();
    private final Vector3f dragProgressEnd = new Vector3f();

    private Gizmo()
    {}

    public void register(int index, IGizmoHandler handler)
    {
        this.handlers.put(index, handler);
    }

    public static boolean isHandleIndex(int index)
    {
        return index >= STENCIL_X && index <= STENCIL_HANDLE_MAX;
    }

    /** Whether axis visuals (interactive gizmos or coolerAxes) should draw. F8 toggles this. */
    public static boolean isVisible()
    {
        return UIBaseMenu.renderAxes;
    }

    /** Hover, stencil pick and drag require both F8 axes on and the gizmos setting enabled. */
    public static boolean isInteractive()
    {
        return UIBaseMenu.renderAxes && BBSSettings.gizmos.get();
    }

    public boolean isDragging()
    {
        return this.index != -1;
    }

    public int getActiveHandle()
    {
        return this.index;
    }

    public Mode getMode()
    {
        return this.mode;
    }

    public boolean setMode(Mode mode)
    {
        if (!BBSSettings.gizmos.get())
        {
            return false;
        }

        boolean same = this.mode == mode;

        this.mode = mode;

        return !same;
    }

    public TransformOrientation getActiveOrientation()
    {
        return this.activeOrientation;
    }

    public void setActiveOrientation(TransformOrientation activeOrientation)
    {
        this.activeOrientation = activeOrientation == null ? TransformOrientation.PARENT : activeOrientation;
    }

    /** Continuously-updated (not just while dragging) handle hover state, fed by
     *  {@link GizmoController} every frame so the render
     *  pass can brighten a handle the mouse is over before the user commits to a drag. */
    public int getHoveredIndex()
    {
        return this.hoveredIndex;
    }

    public void setHoveredIndex(int hoveredIndex)
    {
        this.hoveredIndex = hoveredIndex;
    }

    /** True when the current mode draws a free-rotate trackball that can be armed for drag. */
    public boolean isTrackballPickable()
    {
        if (!isInteractive() || !this.hasGizmoMatrix || this.lastTrackballRadius <= 1.0E-6F)
        {
            return false;
        }

        if (this.mode == Mode.TOP)
        {
            return true;
        }

        if (this.mode != Mode.ROTATE && this.mode != Mode.COMBINED)
        {
            return false;
        }

        return BBSSettings.gizmoTrackball.get();
    }

    /**
     * Ray vs frosted trackball sphere in the same space as {@code gizmoMatrix}.
     * Used so a press can arm trackball drag without writing the sphere into the stencil buffer
     * (which would block bone picks).
     */
    public boolean hitsTrackball(Matrix4f gizmoMatrix, Vector3d rayOrigin, Vector3f rayDirection)
    {
        if (!this.isTrackballPickable() || gizmoMatrix == null || rayOrigin == null || rayDirection == null)
        {
            return false;
        }

        if (rayDirection.lengthSquared() <= 1.0E-12F)
        {
            return false;
        }

        Matrix4f inverse = new Matrix4f(gizmoMatrix).invert();
        Vector4f localOrigin = new Vector4f((float) rayOrigin.x, (float) rayOrigin.y, (float) rayOrigin.z, 1F).mul(inverse);
        Vector4f localDirection = new Vector4f(rayDirection.x, rayDirection.y, rayDirection.z, 0F).mul(inverse);
        Vector3d direction = new Vector3d(localDirection.x, localDirection.y, localDirection.z);

        if (direction.lengthSquared() <= 1.0E-12D)
        {
            return false;
        }

        direction.normalize();

        double radius = this.lastTrackballRadius;
        Vector2d hit = new Vector2d();

        return Intersectiond.intersectRaySphere(
            localOrigin.x, localOrigin.y, localOrigin.z,
            direction.x, direction.y, direction.z,
            0D, 0D, 0D,
            radius * radius,
            hit
        );
    }

    public boolean start(int index, int mouseX, int mouseY, UIPropTransform transform)
    {
        if (!isInteractive())
        {
            return false;
        }

        if (this.handlers.containsKey(index))
        {
            this.handlers.get(index).start(this, index, mouseX, mouseY, transform);
            this.index = index;

            return true;
        }

        if (!isHandleIndex(index))
        {
            return false;
        }

        this.index = index;
        this.currentTransform = transform;

        if (transform != null)
        {
            this.setActiveOrientation(transform.getOrientation());

            if (this.mode == Mode.COMBINED)
            {
                this.startCombined(index, transform);
            }
            else
            {
                this.startSolo(index, transform);
            }
        }

        return true;
    }

    private void startSolo(int index, UIPropTransform transform)
    {
        int mode = this.mode.ordinal();

        if (index == STENCIL_X) transform.enableMode(mode, Axis.X);
        else if (index == STENCIL_Y) transform.enableMode(mode, Axis.Y);
        else if (index == STENCIL_Z) transform.enableMode(mode, Axis.Z);
        else if (index == STENCIL_XY) transform.enablePlaneMode(mode, Axis.X, Axis.Y);
        else if (index == STENCIL_XZ) transform.enablePlaneMode(mode, Axis.X, Axis.Z);
        else if (index == STENCIL_ZY) transform.enablePlaneMode(mode, Axis.Z, Axis.Y);
        else if (index == STENCIL_TRACKBALL) transform.enableTrackballRotate(Mode.ROTATE.ordinal());
        else if (index == STENCIL_FREE)
        {
            if (this.mode == Mode.TRANSLATE) transform.enableFreeTranslation(mode);
            else if (this.mode == Mode.ROTATE) transform.enableFreeRotation(mode, Axis.X);
            else if (this.mode == Mode.SCALE) transform.enableUniformScale(mode);
        }
    }

    /** Same handle dispatch as {@link #startSolo(int, UIPropTransform)}, but routed through
     *  the *KeepGizmoMode variants so grabbing e.g. a scale cube while in Combined mode
     *  doesn't flip the whole gizmo back to solo Scale mode. */
    private void startCombined(int index, UIPropTransform transform)
    {
        int move = Mode.TRANSLATE.ordinal();
        int scale = Mode.SCALE.ordinal();
        int rotate = Mode.ROTATE.ordinal();

        if (index == STENCIL_X) transform.enableModeKeepGizmoMode(move, Axis.X);
        else if (index == STENCIL_Y) transform.enableModeKeepGizmoMode(move, Axis.Y);
        else if (index == STENCIL_Z) transform.enableModeKeepGizmoMode(move, Axis.Z);
        else if (index == STENCIL_XY) transform.enablePlaneModeKeepGizmoMode(move, Axis.X, Axis.Y);
        else if (index == STENCIL_XZ) transform.enablePlaneModeKeepGizmoMode(move, Axis.X, Axis.Z);
        else if (index == STENCIL_ZY) transform.enablePlaneModeKeepGizmoMode(move, Axis.Z, Axis.Y);
        else if (index == STENCIL_FREE) transform.enableFreeTranslation(move);
        else if (index == STENCIL_SCALE_X) transform.enableModeKeepGizmoMode(scale, Axis.X);
        else if (index == STENCIL_SCALE_Y) transform.enableModeKeepGizmoMode(scale, Axis.Y);
        else if (index == STENCIL_SCALE_Z) transform.enableModeKeepGizmoMode(scale, Axis.Z);
        else if (index == STENCIL_ROTATE_X) transform.enableModeKeepGizmoMode(rotate, Axis.X);
        else if (index == STENCIL_ROTATE_Y) transform.enableModeKeepGizmoMode(rotate, Axis.Y);
        else if (index == STENCIL_ROTATE_Z) transform.enableModeKeepGizmoMode(rotate, Axis.Z);
        else if (index == STENCIL_TRACKBALL) transform.enableTrackballRotate(rotate);
        else if (index == STENCIL_VIEW) transform.enableViewRotate(rotate);
    }

    public void stop()
    {
        this.index = -1;
        this.arcActive = false;
        this.clearDragProgress();

        if (this.currentTransform != null)
        {
            this.currentTransform.acceptChanges();
        }

        this.currentTransform = null;
    }

    public void setDragProgress(Vector3f start, Vector3f end)
    {
        this.dragProgressStart.set(start);
        this.dragProgressEnd.set(end);
        this.dragProgressActive = true;
    }

    public void clearDragProgress()
    {
        this.dragProgressActive = false;
    }

    /** Camera-facing flip sign (+1/-1) applied to handles on the given axis, so drag logic
     *  can be radial ("away from center") instead of tied to the positive axis direction. */
    public float getFlipSign(Axis axis)
    {
        if (axis == Axis.X) return this.lastSx;
        if (axis == Axis.Y) return this.lastSy;

        return this.lastSz;
    }

    /* ---- rotation sweep arc (visual only) ---- */

    public void startRotationArc(Vector3f local)
    {
        if (this.index == STENCIL_VIEW)
        {
            this.arcView = true;
            this.arcStartU = this.viewRingAngle(local);
            this.arcLastU = this.arcStartU;
            this.arcSweep = 0F;
            this.arcActive = true;

            return;
        }

        Axis axis = this.ringAxisFromIndex();

        if (axis == null)
        {
            this.arcActive = false;

            return;
        }

        this.arcView = false;
        this.arcAxis = axis;
        this.arcStartU = this.ringAngle(axis, local);
        this.arcLastU = this.arcStartU;
        this.arcSweep = 0F;
        this.arcActive = true;
    }

    /** Re-baselines the per-frame arc delta after a cursor teleport (window edge wrap,
     * mouse leaving the window, etc.) without clearing the accumulated {@link #arcSweep}. */
    public void reanchorRotationArc(Vector3f local)
    {
        if (!this.arcActive)
        {
            this.startRotationArc(local);

            return;
        }

        this.arcLastU = this.arcView ? this.viewRingAngle(local) : this.ringAngle(this.arcAxis, local);
    }

    public void updateRotationArc(Vector3f local)
    {
        if (!this.arcActive)
        {
            return;
        }

        float u = this.arcView ? this.viewRingAngle(local) : this.ringAngle(this.arcAxis, local);
        float delta = u - this.arcLastU;

        while (delta > 180F) delta -= 360F;
        while (delta < -180F) delta += 360F;

        this.arcSweep += delta;
        this.arcLastU = u;
    }

    /** Accumulates sweep from the rotation delta already applied to the value (used in
     *  local-mode drags where the gizmo matrix rotates and geometric arc tracking would
     *  drift or run too fast). */
    public void addRotationSweep(float deltaDegrees)
    {
        if (!this.arcActive)
        {
            return;
        }

        this.arcSweep += deltaDegrees;
    }

    /** Pins the sweep fan to the gizmo matrix at drag start so only its length grows. */
    public void setRotationArcFrozen(Matrix4f matrix, boolean viewRing)
    {
        this.arcFrozenOrientation = true;
        this.arcFrozenMatrix.set(matrix);
        this.arcFrozenViewRing = viewRing;

        if (viewRing)
        {
            this.arcFrozenCamDir.set(this.lastCamDir);
        }
    }

    public void clearRotationArc()
    {
        this.arcActive = false;
        this.arcView = false;
        this.arcSweep = 0F;
        this.arcFrozenOrientation = false;
        this.arcFrozenViewRing = false;
    }

    public boolean hasRotationArc()
    {
        return this.arcActive;
    }

    public float getRotationSweep()
    {
        return this.arcSweep;
    }

    private Axis ringAxisFromIndex()
    {
        if (this.index == STENCIL_X || this.index == STENCIL_ROTATE_X) return Axis.X;
        if (this.index == STENCIL_Y || this.index == STENCIL_ROTATE_Y) return Axis.Y;
        if (this.index == STENCIL_Z || this.index == STENCIL_ROTATE_Z) return Axis.Z;

        return null;
    }

    /** Angle (degrees) of a gizmo-local point in the given ring's {@link Draw#arc3D}
     *  u-parameterization, so the sweep fan lines up with the grab point exactly. */
    private float ringAngle(Axis axis, Vector3f local)
    {
        double u;

        if (axis == Axis.X) u = Math.atan2(local.z, local.y);
        else if (axis == Axis.Y) u = Math.atan2(local.z, local.x);
        else u = Math.atan2(-local.y, local.x);

        return (float) Math.toDegrees(u);
    }

    /** Same as {@link #ringAngle(Axis, Vector3f)} but for the camera-facing view ring: the
     *  gizmo-local point is rotated back into the billboarded ring's space (where the ring is
     *  a plain Y ring) before measuring the angle. */
    private float viewRingAngle(Vector3f local)
    {
        Quaternionf toRing = new Quaternionf().rotationTo(0F, 1F, 0F, this.lastCamDir.x, this.lastCamDir.y, this.lastCamDir.z).conjugate();
        Vector3f ringLocal = new Vector3f(local);

        toRing.transform(ringLocal);

        return this.ringAngle(Axis.Y, ringLocal);
    }

    /** During a drag only the grabbed handle stays visible in the colored pass; everything
     *  else is hidden until release so the user can see the model unobstructed. */
    private boolean showHandle(int handleId)
    {
        return this.index == -1 || this.index == handleId;
    }

    public void deferRender(Matrix4f matrix, boolean stencil, StencilMap stencilMap)
    {
        this.deferredGizmos.add(new DeferredGizmo(GizmoMatrixUtils.normalizeBasis(new Matrix4f(matrix)), stencil, stencilMap));
    }

    /**
     * Record the gizmo's model-view during the world pass without drawing. The
     * colored visual is composited later in {@link #renderInterface} so Iris
     * shader packs cannot displace it from the on-ground hitbox.
     */
    public void captureVisual(MatrixStack stack)
    {
        if (BBSRendering.isIrisShadowPass() || !isVisible())
        {
            return;
        }

        this.lastGizmoMatrix.set(GizmoMatrixUtils.normalizeBasis(new Matrix4f(stack.peek().getPositionMatrix())));
        GizmoMatrixUtils.applyViewCaptureAlignment(this.lastGizmoMatrix, this.activeOrientation);
        this.hasGizmoMatrix = true;
    }

    public void clearVisual()
    {
        this.hasGizmoMatrix = false;
    }

    /**
     * Compose the world-pass captured gizmo matrix with the camera rotation when needed.
     *
     * Some paths bake {@code camera} into {@code captured} already (Iris / world stack);
     * others store a camera-free offset (vanilla model-block). Picking wrongly between
     * {@code camera * captured} and {@code captured} makes the colored gizmo stick to the
     * screen center while the model orbits away.
     *
     * Rule: if composing pulls the view-space origin much closer to the camera than the
     * baked matrix, {@code captured} already includes the view — use baked. Otherwise apply
     * the camera (vanilla). Do <b>not</b> fall back via NDC frustum tests: at steep orbits
     * the correct origin can leave the pad while the double-camera origin stays centered,
     * which used to detach the gizmo from the model.
     */
    public static Matrix4f composeVisualMatrix(Matrix4f captured, Matrix4f cameraMatrix, Matrix4f projection, Matrix4f dest)
    {
        Matrix4f baked = new Matrix4f(captured);
        Matrix4f composed = new Matrix4f(cameraMatrix).mul(captured);
        float bakedDist = viewOriginLengthSq(baked);
        float composedDist = viewOriginLengthSq(composed);

        /* Double-applied view: composed collapses toward the view origin. */
        if (bakedDist > 1.0E-6F && composedDist < bakedDist * 0.49F)
        {
            dest.set(baked);
        }
        else
        {
            dest.set(composed);
        }

        return dest;
    }

    private static float viewOriginLengthSq(Matrix4f view)
    {
        float x = view.m30();
        float y = view.m31();
        float z = view.m32();

        return x * x + y * y + z * z;
    }

    /**
     * Draw the captured gizmo visual in the UI pass with the same projection and
     * viewport rect as the film/model preview that rendered the world this frame.
     */
    public void renderInterface(UIContext context, Matrix4f projection, Area area)
    {
        if (BBSRendering.isIrisShadowPass() || !isVisible() || !this.hasGizmoMatrix
            || context == null || projection == null || area == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        context.batcher.flush();

        MatrixStackUtils.cacheMatrices();
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_Z);

        /* Exact physical-to-logical ratio (the UI scale factor). Rounding this snapped fractional
         * scales like 1.5 up to 2, which offset/stretched the gizmo viewport and could push vy/vh
         * negative (GL_INVALID_VALUE). Same fix as UIModelRenderer#setupViewport. */
        float rx = (float) (mc.getWindow().getWidth() / (double) context.menu.width);
        float ry = (float) (mc.getWindow().getHeight() / (double) context.menu.height);
        float size = BBSModClient.getOriginalFramebufferScale();
        int vx = (int) (area.x * rx);
        int vy = (int) (mc.getWindow().getHeight() - (area.y + area.h) * ry);
        int vw = (int) (area.w * rx);
        int vh = (int) (area.h * ry);

        RenderSystem.viewport((int) (vx * size), (int) (vy * size), (int) (vw * size), (int) (vh * size));

        MatrixStack stack = new MatrixStack();

        MatrixStackUtils.multiply(stack, this.lastGizmoMatrix);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        this.render(stack);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        this.renderDragReadout(context, projection, area);

        RenderSystem.viewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        MatrixStackUtils.restoreMatrices();
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
    }

    private void renderDragReadout(UIContext context, Matrix4f projection, Area area)
    {
        if (!this.isDragging() || this.currentTransform == null)
        {
            return;
        }

        String text = this.currentTransform.buildGizmoDragReadout();

        if (text.isEmpty())
        {
            return;
        }

        Vector4f clip = new Vector4f(0F, 0F, 0F, 1F).mul(this.lastGizmoMatrix).mul(projection);

        if (clip.w <= 1.0E-6F)
        {
            return;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        int x = Math.round(area.x + (ndcX * 0.5F + 0.5F) * area.w) + 12;
        int y = Math.round(area.y + (0.5F - ndcY * 0.5F) * area.h) + 12;

        context.drawForegroundTextCard(text, x, y, Colors.WHITE, Colors.A75);
    }

    /**
     * Stencil pick pass counterpart to {@link #renderInterface}: identical viewport,
     * projection and captured matrix so handle ids line up with the drawn visual.
     */
    public void renderStencilInterface(UIContext context, Matrix4f projection, Area area, StencilMap map)
    {
        if (BBSRendering.isIrisShadowPass() || !isInteractive() || !this.hasGizmoMatrix
            || context == null || projection == null || area == null || map == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        MatrixStackUtils.cacheMatrices();
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_Z);

        /* Keep in sync with renderInterface: fractional UI scales must not be rounded. */
        float rx = (float) (mc.getWindow().getWidth() / (double) context.menu.width);
        float ry = (float) (mc.getWindow().getHeight() / (double) context.menu.height);
        float size = BBSModClient.getOriginalFramebufferScale();
        int vx = (int) (area.x * rx);
        int vy = (int) (mc.getWindow().getHeight() - (area.y + area.h) * ry);
        int vw = (int) (area.w * rx);
        int vh = (int) (area.h * ry);

        RenderSystem.viewport((int) (vx * size), (int) (vy * size), (int) (vw * size), (int) (vh * size));

        MatrixStack stack = new MatrixStack();

        MatrixStackUtils.multiply(stack, this.lastGizmoMatrix);
        this.renderStencil(stack, map);

        RenderSystem.viewport(0, 0, mc.getWindow().getFramebufferWidth(), mc.getWindow().getFramebufferHeight());
        MatrixStackUtils.restoreMatrices();
    }

    public boolean hasDeferred()
    {
        return !this.deferredGizmos.isEmpty();
    }

    public void clearDeferred()
    {
        this.deferredGizmos.clear();
    }

    public void renderDeferred(MatrixStack stack)
    {
        if (this.deferredGizmos.isEmpty())
        {
            return;
        }

        boolean iris = BBSRendering.isIrisShadersEnabled();
        Matrix4f savedProjection = new Matrix4f();
        Matrix4f savedModelView = new Matrix4f();

        if (iris)
        {
            savedProjection.set(RenderSystem.getProjectionMatrix());
            savedModelView.set(RenderSystem.getModelViewMatrix());
        }

        for (DeferredGizmo deferred : this.deferredGizmos)
        {
            if (iris)
            {
                /* WorldRenderEvents.LAST runs after Iris' own compositing passes and no
                 * longer carries the same projection matrix as RenderLayer#getSolid(), where
                 * the gizmo transform was captured. Re-binding the saved projection keeps the
                 * deferred draw aligned with the hitbox/stencil pass on the ground. */
                RenderSystem.setProjectionMatrix(deferred.projection, VertexSorter.BY_Z);
            }

            stack.push();

            /* The saved matrix is the FULL camera-relative transform captured when the gizmo
             * was deferred, so it must replace the stack top rather than be multiplied onto
             * it: at WorldRenderEvents.LAST the stack is not guaranteed to be identity
             * (notably with Iris shader packs), and composing the two shifted the gizmo to a
             * wrong position whenever shaders were enabled. */
            stack.peek().getPositionMatrix().set(deferred.matrix);
            stack.peek().getNormalMatrix().identity();

            if (deferred.stencil && deferred.stencilMap != null)
            {
                this.renderStencil(stack, deferred.stencilMap);
            }
            else
            {
                this.render(stack);
            }

            stack.pop();
        }

        if (iris)
        {
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorter.BY_Z);

            Matrix4fStack mvStack = RenderSystem.getModelViewStack();

            mvStack.pushMatrix();
            mvStack.set(savedModelView);
            RenderSystem.applyModelViewMatrix();
            mvStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }

        this.deferredGizmos.clear();
    }

    /* ---- shared per-frame scale/orientation bookkeeping ---- */

    private float computeScale(MatrixStack stack)
    {
        if (!this.appliedDefaultMode)
        {
            this.appliedDefaultMode = true;

            if (BBSSettings.gizmoDefaultMode != null)
            {
                Mode[] modes = Mode.values();
                int def = BBSSettings.gizmoDefaultMode.get();

                if (def >= 0 && def < modes.length)
                {
                    this.mode = modes[def];
                }
            }
        }

        Matrix4f inv = new Matrix4f(stack.peek().getPositionMatrix()).invert();
        Vector4f camPos = new Vector4f(0, 0, 0, 1).mul(inv);
        double dist = Math.sqrt(camPos.x * camPos.x + camPos.y * camPos.y + camPos.z * camPos.z);
        float axesScale = BBSSettings.axesScale == null ? 1F : BBSSettings.axesScale.get();

        this.lastScaleDist = (float) dist;
        this.updateFlipSigns(camPos.x, camPos.y, camPos.z);

        if (dist > 1.0E-6D)
        {
            this.lastCamDir.set((float) (camPos.x / dist), (float) (camPos.y / dist), (float) (camPos.z / dist));
        }

        boolean constantSize = BBSSettings.gizmoConstantSize == null || BBSSettings.gizmoConstantSize.get();

        if (!constantSize)
        {
            /* Fixed world size: appears smaller on screen when the camera moves away. */
            return 1.4F * 0.5F * axesScale;
        }

        float minFloor = BBSSettings.gizmoConstantSizeMin == null ? 0.5F : BBSSettings.gizmoConstantSizeMin.get();
        double distanceFactor = dist * 0.12D;

        if (minFloor <= 0F)
        {
            return (float) (1.4F * distanceFactor * axesScale);
        }

        return (float) (1.4F * Math.max(minFloor, distanceFactor) * axesScale);
    }

    /**
     * Extra line/hitbox fattening that grows smoothly with camera distance so thin rings
     * stay easy to see and click when zoomed out (visual and stencil use the same boost).
     */
    private float distanceThicknessBoost(float dist)
    {
        float t = MathUtils.clamp((dist - 3F) / 28F, 0F, 1F);

        /* Close: 1× — far: up to ~2.4× thickness on both preview and hitbox. */
        return 1F + t * 1.4F;
    }

    private float resolveThickness(boolean stencil)
    {
        float thickness = BBSSettings.axesThickness == null ? 1.2F : BBSSettings.axesThickness.get();
        boolean constantSize = BBSSettings.gizmoConstantSize == null || BBSSettings.gizmoConstantSize.get();

        if (!constantSize)
        {
            thickness *= this.distanceThicknessBoost(this.lastScaleDist);
        }

        if (stencil)
        {
            thickness *= BBSSettings.gizmoHitbox == null ? 1F : BBSSettings.gizmoHitbox.get();
        }

        return thickness;
    }

    private void updateFlipSigns(float camX, float camY, float camZ)
    {
        if (!this.shouldFlipAxesTowardCamera())
        {
            this.lastSx = 1F;
            this.lastSy = 1F;
            this.lastSz = 1F;

            return;
        }

        if (this.index == -1)
        {
            this.lastSx = camX >= 0 ? 1F : -1F;
            this.lastSy = camY >= 0 ? 1F : -1F;
            this.lastSz = camZ >= 0 ? 1F : -1F;
        }
    }

    /* ---- visual (colored) render pass ---- */

    public void render(MatrixStack stack)
    {
        if (!isVisible())
        {
            return;
        }

        Matrix4f normalized = GizmoMatrixUtils.normalizeBasis(new Matrix4f(stack.peek().getPositionMatrix()));
        stack.peek().getPositionMatrix().set(normalized);

        this.lastGizmoMatrix.set(normalized);
        this.hasGizmoMatrix = true;

        float scale = this.computeScale(stack);
        float thickness = this.resolveThickness(false);

        if (!BBSSettings.gizmos.get())
        {
            Draw.coolerAxes(stack, 0.25F, 0.015F * thickness, 0.26F, 0.025F * thickness);
            return;
        }

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        if (this.mode == Mode.ROTATE) this.drawRotate(builder, stack, scale, thickness, false, null);
        else if (this.mode == Mode.SCALE) this.drawScale(builder, stack, scale, thickness, false, null);
        else if (this.mode == Mode.COMBINED) this.drawCombined(builder, stack, scale, thickness, false, null);
        else if (this.mode == Mode.TOP) this.drawTop(builder, stack, scale, thickness, false, null);
        else this.drawTranslate(builder, stack, scale, thickness, false, null);

        this.drawActiveGuide(builder, stack, scale, thickness);
        this.drawDragProgress(builder, stack, scale, thickness);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        /* Explicitly reset the shader color multiplier: a shader pack's own compositing pass
         * (run just before WorldRenderEvents.LAST, which is when a shader pack is active and
         * this call is reached via renderDeferred()) can leave it at something other than
         * opaque white, which would otherwise silently tint every gizmo vertex color to black/
         * invisible even though the draw call itself succeeds. */
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        if (BBSRendering.isIrisShadersEnabled())
        {
            /* Vertex positions already include the full gizmo transform; Iris leaves a
             * stale terrain model-view on the global stack at WorldRenderEvents.LAST. */
            MatrixStackUtils.pushIdentityModelView();
        }

        this.drawBufferIfNotEmpty(builder);

        if (BBSRendering.isIrisShadersEnabled())
        {
            MatrixStackUtils.popModelView();
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /* ---- stencil (id-encoded) render pass ---- */

    public void renderStencil(MatrixStack stack, StencilMap map)
    {
        if (!isInteractive())
        {
            return;
        }

        Matrix4f normalized = GizmoMatrixUtils.normalizeBasis(new Matrix4f(stack.peek().getPositionMatrix()));
        stack.peek().getPositionMatrix().set(normalized);

        this.lastGizmoMatrix.set(normalized);
        this.hasGizmoMatrix = true;

        float scale = this.computeScale(stack);
        float thickness = this.resolveThickness(true);

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        if (this.mode == Mode.ROTATE) this.drawRotate(builder, stack, scale, thickness, true, map);
        else if (this.mode == Mode.SCALE) this.drawScale(builder, stack, scale, thickness, true, map);
        else if (this.mode == Mode.COMBINED) this.drawCombined(builder, stack, scale, thickness, true, map);
        else if (this.mode == Mode.TOP) this.drawTop(builder, stack, scale, thickness, true, map);
        else this.drawTranslate(builder, stack, scale, thickness, true, map);

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        /* Iris leaves a stale terrain ModelView; verts already include the full transform.
         * Do NOT bake BBSRendering.camera here for non-Iris: preview editors / form pickers /
         * model-block stencil already carry their orbit (or composed) view in the stack.
         * Multiplying the world frustum camera on top mis-picks handles. Film's empty
         * camera-relative stack sets ModelView in UIFilmController instead. */
        if (BBSRendering.isIrisShadersEnabled())
        {
            MatrixStackUtils.pushIdentityModelView();
        }

        this.drawBufferIfNotEmpty(builder);

        if (BBSRendering.isIrisShadersEnabled())
        {
            MatrixStackUtils.popModelView();
        }

        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /** Minecraft 1.21 throws if {@link BufferBuilder#end()} is called with no vertices
     *  (e.g. trackball-only stencil while the frosted sphere skips the pick pass). */
    private void drawBufferIfNotEmpty(BufferBuilder builder)
    {
        try
        {
            BufferRenderer.drawWithGlobalProgram(builder.end());
        }
        catch (IllegalStateException ignored)
        {
            /* Empty buffer — nothing to draw this pass. */
        }
    }

    /* ---- color helpers ---- */

    private float[] pickColor(int handleId, float[] idle, float[] hover)
    {
        if (this.index == handleId) return COLOR_ACTIVE;
        if (this.index == -1 && this.hoveredIndex == handleId) return hover;

        return idle;
    }

    private float pickPlaneAlpha(int handleId)
    {
        if (this.index == handleId) return PLANE_ALPHA_ACTIVE;
        if (this.index == -1 && this.hoveredIndex == handleId) return PLANE_ALPHA_HOVER;

        return PLANE_ALPHA_IDLE;
    }

    private float[] stencilColor(int handleId)
    {
        return new float[] { handleId / 255F, 0F, 0F };
    }

    private void box(BufferBuilder builder, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float[] color, float a)
    {
        Draw.fillBox(builder, stack, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2), color[0], color[1], color[2], a);
    }

    /* ---- translate handles (bars with cone tips + move planes + screen-move cube) ---- */

    /** Shaft / plane sizing shared with Combined so solo Move/Scale match its look. */
    private float moveHandleScale(float scale)
    {
        return scale * COMBINED_MOVE_SCALE;
    }

    private boolean isGizmoStyle2()
    {
        return BBSSettings.gizmoStyle != null && BBSSettings.gizmoStyle.get() == 1;
    }

    private boolean isGizmoStyle3()
    {
        return BBSSettings.gizmoStyle != null && BBSSettings.gizmoStyle.get() == 2;
    }

    /**
     * Combined shaft length vs cage radius. Style 1 keeps tip cubes inside the view ring;
     * Style 2 pushes them just outside; Style 3 ends the shaft at the colored ring.
     */
    private float gizmoAxisLengthFactor()
    {
        if (this.isGizmoStyle3())
        {
            return STYLE3_AXIS_LENGTH_FACTOR;
        }

        return this.isGizmoStyle2() ? STYLE2_AXIS_LENGTH_FACTOR : STYLE1_AXIS_LENGTH_FACTOR;
    }

    private float rotateCageRadius(float scale)
    {
        return 0.22F * scale * COMBINED_ROTATE_SCALE;
    }

    private float axisRingThickness(float scale, float thickness)
    {
        return 0.010F * scale * thickness;
    }

    private boolean useFullRotationRings()
    {
        return BBSSettings.gizmoFullRotationRings != null && BBSSettings.gizmoFullRotationRings.get();
    }

    private float moveAxisOffset(float scale, float thickness)
    {
        return 0.0075F * this.moveHandleScale(scale) * thickness;
    }

    private float moveAxisLength(float scale)
    {
        float cage = this.rotateCageRadius(scale);

        if (this.mode == Mode.COMBINED && this.isGizmoStyle3())
        {
            /* Short shaft; tip cube sits on the end (outer face at STYLE3 factor × cage). */
            float half = this.scaleHandleHalf(scale);
            float tip = cage * STYLE3_AXIS_LENGTH_FACTOR;

            return Math.max(tip - 2F * half, tip * 0.5F);
        }

        /* Combined Style 2 only lengthens shafts so tip cubes clear the view ring. */
        float factor = this.mode == Mode.COMBINED ? this.gizmoAxisLengthFactor() : STYLE1_AXIS_LENGTH_FACTOR;

        return cage * factor;
    }

    private float movePlaneInner(float scale)
    {
        return 0.08F * this.moveHandleScale(scale);
    }

    private float movePlaneOuter(float scale)
    {
        return 0.18F * this.moveHandleScale(scale);
    }

    private float scaleHandleHalf(float scale)
    {
        return 0.016F * scale * COMBINED_SCALE_HANDLE_SCALE;
    }

    private void drawTranslate(BufferBuilder builder, MatrixStack stack, float scale, float thickness, boolean stencil, StencilMap map)
    {
        float moveScale = this.moveHandleScale(scale);
        float axisSize = this.moveAxisLength(scale);
        float axisOffset = this.moveAxisOffset(scale, thickness);
        float planeInner = this.movePlaneInner(scale);
        float planeOuter = this.movePlaneOuter(scale);
        float offset = 0.001F * moveScale;

        this.drawMoveBars(builder, stack, axisSize, axisOffset, stencil, map);
        this.drawMovePlanes(builder, stack, planeInner, planeOuter, offset, stencil, map);
        this.drawScreenCube(builder, stack, axisOffset, stencil, map);
    }

    private void drawMoveBars(BufferBuilder builder, MatrixStack stack, float axisSize, float axisOffset, boolean stencil, StencilMap map)
    {
        this.drawMoveBars(builder, stack, axisSize, axisOffset, stencil, map, false, 0F, 0F);
    }

    /**
     * @param scaleCubesAtTip when true (combined mode), draw long shafts with a translate
     *                        cone under a scale cube at each tip (cube outward, triangle inward).
     * @param rotateCageRadius Combined colored-orbit radius; Style 2 parks cubes outside the view ring.
     */
    private void drawMoveBars(BufferBuilder builder, MatrixStack stack, float axisSize, float axisOffset, boolean stencil, StencilMap map, boolean scaleCubesAtTip, float cubeHalf, float rotateCageRadius)
    {
        float[] xCol = stencil ? this.stencilColor(STENCIL_X) : this.pickColor(STENCIL_X, COLOR_X_IDLE, COLOR_X_HOVER);
        float[] yCol = stencil ? this.stencilColor(STENCIL_Y) : this.pickColor(STENCIL_Y, COLOR_Y_IDLE, COLOR_Y_HOVER);
        float[] zCol = stencil ? this.stencilColor(STENCIL_Z) : this.pickColor(STENCIL_Z, COLOR_Z_IDLE, COLOR_Z_HOVER);
        /* Tip reach follows shaft length (same visual + stencil). Do not use axisOffset here —
         * stencil multiplies thickness via gizmoHitbox and would push pick cubes past the preview.
         * Combined tips use a shorter cone so a visible gap fits before the scale cube. */
        float tipLength = Math.abs(axisSize) * (scaleCubesAtTip ? 0.18F : 0.35F);
        float tipRadius = axisOffset * 2.4F;
        int coneSegments = stencil ? 6 : 10;

        if (this.showHandle(STENCIL_X) || (scaleCubesAtTip && this.showHandle(STENCIL_SCALE_X)))
        {
            if (this.showHandle(STENCIL_X))
            {
                this.box(builder, stack, 0, -axisOffset, -axisOffset, axisSize * this.lastSx, axisOffset, axisOffset, xCol, 1F);
            }

            if (scaleCubesAtTip)
            {
                this.drawCombinedAxisTip(builder, stack, Axis.X, axisSize * this.lastSx, tipRadius, tipLength, cubeHalf, coneSegments, xCol, stencil, rotateCageRadius);
            }
            else if (this.showHandle(STENCIL_X))
            {
                /* Apex is the far/outward point of the arrow, base is the wide disc that meets the bar. */
                Draw.cone(builder, stack, (axisSize + tipLength) * this.lastSx, 0, 0, axisSize * this.lastSx, 0, 0, tipRadius, coneSegments, xCol[0], xCol[1], xCol[2], 1F);
            }
        }

        if (this.showHandle(STENCIL_Y) || (scaleCubesAtTip && this.showHandle(STENCIL_SCALE_Y)))
        {
            if (this.showHandle(STENCIL_Y))
            {
                this.box(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize * this.lastSy, axisOffset, yCol, 1F);
            }

            if (scaleCubesAtTip)
            {
                this.drawCombinedAxisTip(builder, stack, Axis.Y, axisSize * this.lastSy, tipRadius, tipLength, cubeHalf, coneSegments, yCol, stencil, rotateCageRadius);
            }
            else if (this.showHandle(STENCIL_Y))
            {
                Draw.cone(builder, stack, 0, (axisSize + tipLength) * this.lastSy, 0, 0, axisSize * this.lastSy, 0, tipRadius, coneSegments, yCol[0], yCol[1], yCol[2], 1F);
            }
        }

        if (this.showHandle(STENCIL_Z) || (scaleCubesAtTip && this.showHandle(STENCIL_SCALE_Z)))
        {
            if (this.showHandle(STENCIL_Z))
            {
                this.box(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize * this.lastSz, zCol, 1F);
            }

            if (scaleCubesAtTip)
            {
                this.drawCombinedAxisTip(builder, stack, Axis.Z, axisSize * this.lastSz, tipRadius, tipLength, cubeHalf, coneSegments, zCol, stencil, rotateCageRadius);
            }
            else if (this.showHandle(STENCIL_Z))
            {
                Draw.cone(builder, stack, 0, 0, (axisSize + tipLength) * this.lastSz, 0, 0, axisSize * this.lastSz, tipRadius, coneSegments, zCol[0], zCol[1], zCol[2], 1F);
            }
        }
    }

    /**
     * Combined-mode tip: shaft → cone, then a gap, then the scale cube.
     * Style 2 keeps the cone inside the rings and parks the cube just outside the view ring
     * with no stem past the arrow tip. Style 3 draws only the cube on the colored ring edge.
     */
    private void drawCombinedAxisTip(BufferBuilder builder, MatrixStack stack, Axis axis, float shaftEnd, float tipRadius, float tipLength, float cubeHalf, int coneSegments, float[] translateColor, boolean stencil, float rotateCageRadius)
    {
        float sign = shaftEnd >= 0F ? 1F : -1F;

        if (this.isGizmoStyle3())
        {
            /* No cones: short shaft ends at the cube's inner face (shaftEnd from moveAxisLength). */
            float cubeCenter = Math.abs(shaftEnd) + cubeHalf;

            this.drawScaleTipCube(builder, stack, axis, cubeCenter * sign, cubeHalf, stencil);

            return;
        }

        float absEnd = Math.abs(shaftEnd);
        float coneBase = absEnd;
        float coneApex = absEnd + tipLength;
        /* Gap between arrow tip and cube so they do not stick together. */
        float tipCubeGap = cubeHalf * 1.35F;
        float cubeCenter = coneApex + tipCubeGap + cubeHalf;

        if (this.isGizmoStyle2() && rotateCageRadius > 0F)
        {
            /* Arrow stays inside the colored orbits; cube sits just outside the white view ring. */
            float viewRing = rotateCageRadius * VIEW_RING_SCALE;

            cubeCenter = viewRing + tipCubeGap + cubeHalf;
        }

        if (this.showHandle(axis == Axis.X ? STENCIL_X : axis == Axis.Y ? STENCIL_Y : STENCIL_Z))
        {
            if (axis == Axis.X)
            {
                Draw.cone(builder, stack, coneApex * sign, 0, 0, coneBase * sign, 0, 0, tipRadius, coneSegments, translateColor[0], translateColor[1], translateColor[2], 1F);
            }
            else if (axis == Axis.Y)
            {
                Draw.cone(builder, stack, 0, coneApex * sign, 0, 0, coneBase * sign, 0, tipRadius, coneSegments, translateColor[0], translateColor[1], translateColor[2], 1F);
            }
            else
            {
                Draw.cone(builder, stack, 0, 0, coneApex * sign, 0, 0, coneBase * sign, tipRadius, coneSegments, translateColor[0], translateColor[1], translateColor[2], 1F);
            }
        }

        this.drawScaleTipCube(builder, stack, axis, cubeCenter * sign, cubeHalf, stencil);
    }

    private void drawScaleTipCube(BufferBuilder builder, MatrixStack stack, Axis axis, float tip, float half, boolean stencil)
    {
        int id = axis == Axis.X ? STENCIL_SCALE_X : axis == Axis.Y ? STENCIL_SCALE_Y : STENCIL_SCALE_Z;

        if (!this.showHandle(id))
        {
            return;
        }

        float[] color = stencil
            ? this.stencilColor(id)
            : this.pickColor(id, axis == Axis.X ? COLOR_X_IDLE : axis == Axis.Y ? COLOR_Y_IDLE : COLOR_Z_IDLE,
                axis == Axis.X ? COLOR_X_HOVER : axis == Axis.Y ? COLOR_Y_HOVER : COLOR_Z_HOVER);

        if (axis == Axis.X)
        {
            this.box(builder, stack, tip - half, -half, -half, tip + half, half, half, color, 1F);
        }
        else if (axis == Axis.Y)
        {
            this.box(builder, stack, -half, tip - half, -half, half, tip + half, half, color, 1F);
        }
        else
        {
            this.box(builder, stack, -half, -half, tip - half, half, half, tip + half, color, 1F);
        }
    }

    private void drawMovePlanes(BufferBuilder builder, MatrixStack stack, float planeInner, float planeOuter, float offset, boolean stencil, StencilMap map)
    {
        float xzAlpha = stencil ? 1F : this.pickPlaneAlpha(STENCIL_XZ);
        float xyAlpha = stencil ? 1F : this.pickPlaneAlpha(STENCIL_XY);
        float zyAlpha = stencil ? 1F : this.pickPlaneAlpha(STENCIL_ZY);

        float[] xzCol = stencil ? this.stencilColor(STENCIL_XZ) : COLOR_XZ_IDLE;
        float[] xyCol = stencil ? this.stencilColor(STENCIL_XY) : COLOR_XY_IDLE;
        float[] zyCol = stencil ? this.stencilColor(STENCIL_ZY) : COLOR_ZY_IDLE;

        if (!stencil && this.index == -1 && this.hoveredIndex == STENCIL_XZ) xzCol = COLOR_XZ_HOVER;
        if (!stencil && this.index == -1 && this.hoveredIndex == STENCIL_XY) xyCol = COLOR_XY_HOVER;
        if (!stencil && this.index == -1 && this.hoveredIndex == STENCIL_ZY) zyCol = COLOR_ZY_HOVER;
        if (!stencil && this.index == STENCIL_XZ) xzCol = COLOR_ACTIVE;
        if (!stencil && this.index == STENCIL_XY) xyCol = COLOR_ACTIVE;
        if (!stencil && this.index == STENCIL_ZY) zyCol = COLOR_ACTIVE;

        if (this.showHandle(STENCIL_XZ))
        {
            this.box(builder, stack, planeInner * this.lastSx, -offset, planeInner * this.lastSz, planeOuter * this.lastSx, offset, planeOuter * this.lastSz, xzCol, xzAlpha);
        }

        if (this.showHandle(STENCIL_XY))
        {
            this.box(builder, stack, planeInner * this.lastSx, planeInner * this.lastSy, -offset, planeOuter * this.lastSx, planeOuter * this.lastSy, offset, xyCol, xyAlpha);
        }

        if (this.showHandle(STENCIL_ZY))
        {
            this.box(builder, stack, -offset, planeInner * this.lastSy, planeInner * this.lastSz, offset, planeOuter * this.lastSy, planeOuter * this.lastSz, zyCol, zyAlpha);
        }
    }

    private void drawScreenCube(BufferBuilder builder, MatrixStack stack, float axisOffset, boolean stencil, StencilMap map)
    {
        if (!this.showHandle(STENCIL_FREE))
        {
            return;
        }

        float[] color = stencil ? this.stencilColor(STENCIL_FREE) : this.pickColor(STENCIL_FREE, COLOR_FREE_IDLE, COLOR_FREE_IDLE);

        this.box(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, color, 1F);
    }

    /* ---- scale handles (thin shafts + Combined-sized cubes at the tips) ---- */

    private void drawScale(BufferBuilder builder, MatrixStack stack, float scale, float thickness, boolean stencil, StencilMap map)
    {
        float axisOffset = this.moveAxisOffset(scale, thickness);
        float axisSize = this.moveAxisLength(scale);
        float half = this.scaleHandleHalf(scale);

        float[] xCol = stencil ? this.stencilColor(STENCIL_X) : this.pickColor(STENCIL_X, COLOR_X_IDLE, COLOR_X_HOVER);
        float[] yCol = stencil ? this.stencilColor(STENCIL_Y) : this.pickColor(STENCIL_Y, COLOR_Y_IDLE, COLOR_Y_HOVER);
        float[] zCol = stencil ? this.stencilColor(STENCIL_Z) : this.pickColor(STENCIL_Z, COLOR_Z_IDLE, COLOR_Z_HOVER);
        float[] freeCol = stencil ? this.stencilColor(STENCIL_FREE) : this.pickColor(STENCIL_FREE, COLOR_FREE_IDLE, COLOR_FREE_IDLE);

        if (this.showHandle(STENCIL_X))
        {
            this.box(builder, stack, 0, -axisOffset, -axisOffset, axisSize * this.lastSx, axisOffset, axisOffset, xCol, 1F);
            this.box(builder, stack, axisSize * this.lastSx - half, -half, -half, axisSize * this.lastSx + half, half, half, xCol, 1F);
        }

        if (this.showHandle(STENCIL_Y))
        {
            this.box(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize * this.lastSy, axisOffset, yCol, 1F);
            this.box(builder, stack, -half, axisSize * this.lastSy - half, -half, half, axisSize * this.lastSy + half, half, yCol, 1F);
        }

        if (this.showHandle(STENCIL_Z))
        {
            this.box(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize * this.lastSz, zCol, 1F);
            this.box(builder, stack, -half, -half, axisSize * this.lastSz - half, half, half, axisSize * this.lastSz + half, zCol, 1F);
        }

        if (this.showHandle(STENCIL_FREE))
        {
            this.box(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, freeCol, 1F);
        }
    }

    /* ---- rotate handles (rings + trackball) ---- */

    private void drawTop(BufferBuilder builder, MatrixStack stack, float scale, float thickness, boolean stencil, StencilMap map)
    {
        float radius = 0.22F * scale;
        float topRadius = this.trackballRadius(radius, TOP_TRACKBALL_SIZE_FACTOR);

        this.lastTrackballRadius = topRadius;

        if (stencil)
        {
            float[] color = this.stencilColor(STENCIL_TRACKBALL);

            Draw.sphere(builder, stack, topRadius, 8, 12, color[0], color[1], color[2], 1F);

            return;
        }

        boolean active = this.index == STENCIL_TRACKBALL;
        float sa = active ? 0.32F : 0.16F;

        /* Smooth frosted sphere — no wireframe rings on the surface. */
        Draw.sphere(builder, stack, topRadius, 16, 24, 0.92F, 0.92F, 0.92F, sa);
    }

    private void drawRotate(BufferBuilder builder, MatrixStack stack, float scale, float thickness, boolean stencil, StencilMap map)
    {
        float radius = 0.30F * scale;
        float ringThickness = this.axisRingThickness(scale, thickness);

        /* Visual frosted sphere first; stencil skips the sphere so bones stay pickable. */
        this.drawTrackball(builder, stack, this.trackballRadius(radius, 1.85F), stencil, map);
        this.drawRings(builder, stack, radius, ringThickness, STENCIL_X, STENCIL_Y, STENCIL_Z, stencil);
    }

    /** Shared ring drawing for solo Rotate mode and Combined mode (they only differ in the
     *  stencil ids their rings pick as). Also draws the sweep fan on the active ring.
     *  Axis rings are camera-facing semicircles by default; full circles when the setting is on.
     *  The outer view ring stays a full circle. */
    private void drawRings(BufferBuilder builder, MatrixStack stack, float radius, float ringThickness, int idX, int idY, int idZ, boolean stencil)
    {
        float[] xCol = stencil ? this.stencilColor(idX) : this.pickColor(idX, COLOR_X_IDLE, COLOR_X_HOVER);
        float[] yCol = stencil ? this.stencilColor(idY) : this.pickColor(idY, COLOR_Y_IDLE, COLOR_Y_HOVER);
        float[] zCol = stencil ? this.stencilColor(idZ) : this.pickColor(idZ, COLOR_Z_IDLE, COLOR_Z_HOVER);

        if (this.showHandle(idZ))
        {
            this.drawAxisRotationRing(builder, stack, Axis.Z, radius, ringThickness, zCol, stencil);
        }

        if (this.showHandle(idX))
        {
            this.drawAxisRotationRing(builder, stack, Axis.X, radius, ringThickness, xCol, stencil);
        }

        if (this.showHandle(idY))
        {
            this.drawAxisRotationRing(builder, stack, Axis.Y, radius, ringThickness, yCol, stencil);
        }

        /* Swept-angle fan on the ring being dragged, drawn slightly smaller so it reads as a
         * pie inside the ring rather than covering it. */
        if (!stencil && this.arcActive && !this.arcView && (this.index == idX || this.index == idY || this.index == idZ))
        {
            this.drawRotationSweepArc(builder, stack, this.arcAxis, radius * 0.85F, ringThickness * 1.5F, false);
        }
    }

    /**
     * Draws an XYZ rotation ring: camera-facing 180° arc by default, or a full 360° circle
     * when {@link BBSSettings#gizmoFullRotationRings} is enabled. Visual and pick thickness match.
     * When {@link BBSSettings#gizmoFlipAxes} is off, half-rings stay fixed (no camera reorient),
     * matching translate/scale handles that stay on +X/+Y/+Z.
     */
    private void drawAxisRotationRing(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float ringThickness, float[] color, boolean stencil)
    {
        if (this.useFullRotationRings())
        {
            Draw.arc3D(builder, stack, axis, radius, ringThickness, color[0], color[1], color[2], 0F, 360F, stencil);

            return;
        }

        float startDeg = this.shouldFlipAxesTowardCamera()
            ? this.cameraFacingRingStartDeg(axis)
            : 0F;

        Draw.arc3D(builder, stack, axis, radius, ringThickness, color[0], color[1], color[2], startDeg, 180F, stencil);
    }

    private boolean shouldFlipAxesTowardCamera()
    {
        return BBSSettings.gizmoFlipAxes == null || BBSSettings.gizmoFlipAxes.get();
    }

    /**
     * Start angle (degrees) for a 180° sweep whose midpoint faces {@link #lastCamDir}.
     * Uses the same u-parameterization as {@link Draw#arc3D} / {@link #ringAngle}.
     */
    private float cameraFacingRingStartDeg(Axis axis)
    {
        float cx = this.lastCamDir.x;
        float cy = this.lastCamDir.y;
        float cz = this.lastCamDir.z;
        double midRad;

        if (axis == Axis.X)
        {
            /* Ring in YZ: u = atan2(z, y). */
            if (cy * cy + cz * cz < 1.0E-8F)
            {
                midRad = 0D;
            }
            else
            {
                midRad = Math.atan2(cz, cy);
            }
        }
        else if (axis == Axis.Y)
        {
            /* Ring in XZ: u = atan2(z, x). */
            if (cx * cx + cz * cz < 1.0E-8F)
            {
                midRad = 0D;
            }
            else
            {
                midRad = Math.atan2(cz, cx);
            }
        }
        else
        {
            /* Ring in XY: u = atan2(-y, x). */
            if (cx * cx + cy * cy < 1.0E-8F)
            {
                midRad = 0D;
            }
            else
            {
                midRad = Math.atan2(-cy, cx);
            }
        }

        return (float) Math.toDegrees(midRad) - 90F;
    }

    private float trackballRadius(float gizmoRadius, float modeFactor)
    {
        int setting = BBSSettings.gizmoTrackballScale == null ? 1 : BBSSettings.gizmoTrackballScale.get();

        /* Setting 1 = half the legacy trackball size; each step scales linearly up to 5× that. */
        return gizmoRadius * modeFactor * 0.5F * setting;
    }

    /**
     * Frosted center sphere for rotate / combined modes. Visual only in those modes so
     * bone stencil picks pass through; TOP mode writes its own pickable sphere separately.
     */
    private void drawTrackball(BufferBuilder builder, MatrixStack stack, float radius, boolean stencil, StencilMap map)
    {
        if (!BBSSettings.gizmoTrackball.get())
        {
            return;
        }

        if (!this.showHandle(STENCIL_TRACKBALL))
        {
            return;
        }

        if (stencil)
        {
            /* Idle: leave stencil empty so bones under the sphere stay pickable.
             * While actively dragging the trackball, keep a pick id so the empty-buffer
             * path is avoided and the handle stays owned for the rest of the drag. */
            if (this.index == STENCIL_TRACKBALL)
            {
                float[] color = this.stencilColor(STENCIL_TRACKBALL);

                Draw.sphere(builder, stack, radius, 8, 12, color[0], color[1], color[2], 1F);
            }

            return;
        }

        this.lastTrackballRadius = radius;

        boolean active = this.index == STENCIL_TRACKBALL;
        float alpha = active ? 0.28F : 0.14F;

        Draw.sphere(builder, stack, radius, 16, 24, 0.92F, 0.92F, 0.92F, alpha);
    }

    /** Camera-facing arcball ring, slightly larger than the axis rings. Dragging it rotates
     *  the object around the view axis, following the mouse around the ring. */
    private void drawViewRing(BufferBuilder builder, MatrixStack stack, float radius, float ringThickness, boolean stencil, StencilMap map)
    {
        if (!this.showHandle(STENCIL_VIEW))
        {
            return;
        }

        float[] color = stencil ? this.stencilColor(STENCIL_VIEW) : this.pickColor(STENCIL_VIEW, COLOR_VIEW_IDLE, COLOR_VIEW_HOVER);

        stack.push();
        stack.multiply(new Quaternionf().rotationTo(0F, 1F, 0F, this.lastCamDir.x, this.lastCamDir.y, this.lastCamDir.z));
        Draw.arc3D(builder, stack, Axis.Y, radius, ringThickness, color[0], color[1], color[2], 0F, 360F, stencil);

        /* Same swept-angle fan the axis rings get, drawn slightly inside the view ring. */
        if (!stencil && this.arcActive && this.arcView && this.index == STENCIL_VIEW)
        {
            this.drawRotationSweepArc(builder, stack, Axis.Y, radius * 0.9F, ringThickness * 1.5F, true);
        }

        stack.pop();
    }

    private void drawRotationSweepArc(BufferBuilder builder, MatrixStack stack, Axis axis, float radius, float thickness, boolean viewRing)
    {
        if (Math.abs(this.arcSweep) <= 0.01F)
        {
            return;
        }

        float[] color = this.sweepColor(axis, viewRing);

        if (this.arcFrozenOrientation)
        {
            MatrixStack arcStack = new MatrixStack();

            MatrixStackUtils.multiply(arcStack, this.arcFrozenMatrix);

            if (viewRing && this.arcFrozenViewRing)
            {
                arcStack.multiply(new Quaternionf().rotationTo(0F, 1F, 0F, this.arcFrozenCamDir.x, this.arcFrozenCamDir.y, this.arcFrozenCamDir.z));
            }

            Draw.arc3D(builder, arcStack, axis, radius, thickness, color[0], color[1], color[2], this.arcStartU, this.arcSweep);
        }
        else
        {
            Draw.arc3D(builder, stack, axis, radius, thickness, color[0], color[1], color[2], this.arcStartU, this.arcSweep);
        }
    }

    /** Rotation process-bar color matches the ring being dragged (view ring stays white). */
    private float[] sweepColor(Axis axis, boolean viewRing)
    {
        if (viewRing)
        {
            return COLOR_ACTIVE;
        }

        if (axis == Axis.X)
        {
            return COLOR_X_HOVER;
        }

        if (axis == Axis.Y)
        {
            return COLOR_Y_HOVER;
        }

        return COLOR_Z_HOVER;
    }

    /** Faint "infinite" line through the gizmo origin along the axis being dragged: the
     *  movement direction for translate/scale, or the rotation axis for the rings. */
    private void drawActiveGuide(BufferBuilder builder, MatrixStack stack, float scale, float thickness)
    {
        /* Plane drags show both of the plane's axes as a "+" cross so the user sees the two
         * directions the object can move in; single-axis drags show just their own line. */
        if (this.index == STENCIL_XY)
        {
            this.drawGuideLine(builder, stack, scale, thickness, Axis.X, COLOR_X_HOVER);
            this.drawGuideLine(builder, stack, scale, thickness, Axis.Y, COLOR_Y_HOVER);

            return;
        }
        else if (this.index == STENCIL_XZ)
        {
            this.drawGuideLine(builder, stack, scale, thickness, Axis.X, COLOR_X_HOVER);
            this.drawGuideLine(builder, stack, scale, thickness, Axis.Z, COLOR_Z_HOVER);

            return;
        }
        else if (this.index == STENCIL_ZY)
        {
            this.drawGuideLine(builder, stack, scale, thickness, Axis.Z, COLOR_Z_HOVER);
            this.drawGuideLine(builder, stack, scale, thickness, Axis.Y, COLOR_Y_HOVER);

            return;
        }

        Axis axis = null;
        float[] color = null;

        if (this.index == STENCIL_X || this.index == STENCIL_SCALE_X || this.index == STENCIL_ROTATE_X)
        {
            axis = Axis.X;
            color = COLOR_X_HOVER;
        }
        else if (this.index == STENCIL_Y || this.index == STENCIL_SCALE_Y || this.index == STENCIL_ROTATE_Y)
        {
            axis = Axis.Y;
            color = COLOR_Y_HOVER;
        }
        else if (this.index == STENCIL_Z || this.index == STENCIL_SCALE_Z || this.index == STENCIL_ROTATE_Z)
        {
            axis = Axis.Z;
            color = COLOR_Z_HOVER;
        }

        if (axis == null)
        {
            return;
        }

        this.drawGuideLine(builder, stack, scale, thickness, axis, color);
    }

    /** One faint guide line along an axis, sized/tinted by the guide line settings. The
     *  settings are read defensively (with hardcoded fallbacks) because this can run during a
     *  world render frame, before/without the settings registry being fully initialized. */
    private void drawGuideLine(BufferBuilder builder, MatrixStack stack, float scale, float thickness, Axis axis, float[] color)
    {
        float lengthSetting = BBSSettings.gizmoGuideLength == null ? 2F : BBSSettings.gizmoGuideLength.get();
        float thicknessSetting = BBSSettings.gizmoGuideThickness == null ? 2F : BBSSettings.gizmoGuideThickness.get();
        float alpha = BBSSettings.gizmoGuideOpacity == null ? 0.35F : BBSSettings.gizmoGuideOpacity.get();

        float length = 10F * scale * lengthSetting;
        float t = 0.0025F * scale * thickness * thicknessSetting;

        if (axis == Axis.X) this.box(builder, stack, -length, -t, -t, length, t, t, color, alpha);
        else if (axis == Axis.Y) this.box(builder, stack, -t, -length, -t, t, length, t, color, alpha);
        else this.box(builder, stack, -t, -t, -length, t, t, length, color, alpha);
    }

    /** Thick yellow segment from the drag grab point to the current mouse position. */
    private void drawDragProgress(BufferBuilder builder, MatrixStack stack, float scale, float thickness)
    {
        if (!this.dragProgressActive || !this.isScaleDragIndex())
        {
            return;
        }

        float dx = this.dragProgressEnd.x - this.dragProgressStart.x;
        float dy = this.dragProgressEnd.y - this.dragProgressStart.y;
        float dz = this.dragProgressEnd.z - this.dragProgressStart.z;

        if (dx * dx + dy * dy + dz * dz < 1.0E-10F)
        {
            return;
        }

        float thicknessSetting = BBSSettings.gizmoGuideThickness == null ? 2F : BBSSettings.gizmoGuideThickness.get();
        float alpha = BBSSettings.gizmoGuideOpacity == null ? 0.9F : Math.min(1F, BBSSettings.gizmoGuideOpacity.get() + 0.5F);
        float t = 0.006F * scale * thickness * thicknessSetting;

        Draw.fillBoxTo(
            builder, stack,
            this.dragProgressStart.x, this.dragProgressStart.y, this.dragProgressStart.z,
            this.dragProgressEnd.x, this.dragProgressEnd.y, this.dragProgressEnd.z,
            t,
            COLOR_ACTIVE[0], COLOR_ACTIVE[1], COLOR_ACTIVE[2], alpha
        );
    }

    private boolean isScaleDragIndex()
    {
        if (this.index == -1)
        {
            return false;
        }

        if (this.index == STENCIL_SCALE_X || this.index == STENCIL_SCALE_Y || this.index == STENCIL_SCALE_Z)
        {
            return true;
        }

        if (this.mode == Mode.SCALE && (this.index == STENCIL_X || this.index == STENCIL_Y || this.index == STENCIL_Z || this.index == STENCIL_FREE))
        {
            return true;
        }

        return false;
    }

    /* ---- combined mode (move + scale + rotate + trackball nested together) ---- */

    private void drawCombined(BufferBuilder builder, MatrixStack stack, float scale, float thickness, boolean stencil, StencilMap map)
    {
        float moveScale = this.moveHandleScale(scale);
        float rotateRadius = this.rotateCageRadius(scale);
        /* Same shaft / plane / cube sizing as solo Move & Scale. */
        float axisSize = this.moveAxisLength(scale);
        float axisOffset = this.moveAxisOffset(scale, thickness);
        float cubeHalf = this.scaleHandleHalf(scale);
        float planeInner = this.movePlaneInner(scale);
        float planeOuter = this.movePlaneOuter(scale);
        float offset = 0.001F * moveScale;
        float ringThickness = this.axisRingThickness(scale, thickness);

        /* Frosted sphere is visual-only (no stencil) so bones under it stay selectable. */
        this.drawTrackball(builder, stack, this.trackballRadius(rotateRadius, 1.85F), stencil, map);

        this.drawMoveBars(builder, stack, axisSize, axisOffset, stencil, map, true, cubeHalf, rotateRadius);
        this.drawMovePlanes(builder, stack, planeInner, planeOuter, offset, stencil, map);
        this.drawScreenCube(builder, stack, axisOffset, stencil, map);

        this.drawRings(builder, stack, rotateRadius, ringThickness, STENCIL_ROTATE_X, STENCIL_ROTATE_Y, STENCIL_ROTATE_Z, stencil);
        this.drawViewRing(builder, stack, rotateRadius * VIEW_RING_SCALE, ringThickness, stencil, map);
    }

    public static enum Mode
    {
        TRANSLATE, SCALE, ROTATE, COMBINED, TOP;
    }
}
