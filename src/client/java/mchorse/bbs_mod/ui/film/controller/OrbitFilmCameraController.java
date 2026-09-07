package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.joml.Vectors;

import net.minecraft.client.MinecraftClient;

import org.joml.Intersectionf;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.joml.Vector4i;

public class OrbitFilmCameraController implements ICameraController
{
    private static final float MIN_DISTANCE = 0.5F;
    private static final float DEFAULT_ORBIT_DISTANCE = 3F;
    private static final float SCROLL_STEP = 0.5F;

    private UIFilmController controller;

    public boolean enabled;

    private int dragging = -1;
    public Vector2f rotation = new Vector2f();
    private float roll;
    private Vector2i last = new Vector2i();
    private Vector3f position = new Vector3f();

    private float distance;
    private float offsetY;
    private boolean center;

    protected Vector3i velocityPosition = new Vector3i();
    protected Vector4i velocityAngle = new Vector4i();

    private boolean animating;
    private float animProgress;
    private final Vector3d animFromPosition = new Vector3d();
    private final Vector3d animToPosition = new Vector3d();
    private final Vector3f animFromRotation = new Vector3f();
    private final Vector3f animToRotation = new Vector3f();

    /* While a gizmo handle is dragged, keep the orbit pose fixed so editing Transform /
     * Anchor cannot chase the camera (shake + runaway rotation). */
    private boolean frozenForGizmoDrag;
    private final Vector3d frozenDragPosition = new Vector3d();
    private final Vector3f frozenDragRotation = new Vector3f();

    public OrbitFilmCameraController(UIFilmController controller)
    {
        this.controller = controller;
    }

    public boolean isAnimating()
    {
        return this.animating;
    }

    public int canStart(UIContext context)
    {
        if (this.animating)
        {
            return -1;
        }

        if (context.mouseButton == 0)
        {
            return 0;
        }

        if (context.mouseButton == 1)
        {
            return 1;
        }

        if (context.mouseButton == 2)
        {
            return 2;
        }

        return -1;
    }

    public void start(UIContext context)
    {
        if (!this.canInteract())
        {
            return;
        }

        int button = this.canStart(context);

        if (button < 0)
        {
            return;
        }

        this.center = button == 2 && Window.isKeyPressed(Keys.FLIGHT_ORBIT.getMainKey());
        this.dragging = button;
        this.last.set(context.mouseX, context.mouseY);

        if (this.center)
        {
            Vector3f rayDirection = this.rotateVector(0F, 0F, -1F, this.rotation.y, this.rotation.x, false);
            Vector3f normal = Vectors.TEMP_3F.set(rayDirection).mul(-1F, 0F, -1F).normalize();

            float t = Intersectionf.intersectRayPlane(this.position, rayDirection, new Vector3f(0, this.offsetY, 0), normal, 0.0001F);
            Vector3f p = new Vector3f(rayDirection).mul(t).add(this.position);

            p.x = 0;
            p.z = 0;

            this.distance = this.position.distance(p);
            this.offsetY = p.y;
        }
    }

    public void stop()
    {
        if (this.center)
        {
            this.position.set(this.rotateVector(0F, 0F, 1F, this.rotation.y, this.rotation.x, false).mul(this.distance));
            this.position.add(0, this.offsetY, 0);
        }

        this.dragging = -1;
        this.center = false;
    }

    public boolean keyPressed(UIContext context, Area area)
    {
        if (!this.enabled || context.isFocused())
        {
            return false;
        }

        /* Flight keys (WASD/Space/arrows) follow canInteract(): either flight mode,
         * or orbit-without-flight when that setting is enabled. With the setting off
         * (default), Space stays free for play/pause while orbit POV is selected. */
        if (this.canInteract() || area.isInside(context) || (!this.velocityPosition.equals(0, 0, 0) && context.getKeyAction() == KeyAction.RELEASED) || (!this.velocityAngle.equals(0, 0, 0, 0) && context.getKeyAction() == KeyAction.RELEASED))
        {
            if (!this.canInteract() && context.getKeyAction() != KeyAction.RELEASED)
            {
                return false;
            }

            int x = this.getFactor(context, Keys.FLIGHT_LEFT, Keys.FLIGHT_RIGHT, this.velocityPosition.x);
            int y = this.getFactor(context, Keys.FLIGHT_UP, Keys.FLIGHT_DOWN, this.velocityPosition.y);
            int z = this.getFactor(context, Keys.FLIGHT_FORWARD, Keys.FLIGHT_BACKWARD, this.velocityPosition.z);
            int pitch = this.getFactor(context, Keys.FLIGHT_TILT_UP, Keys.FLIGHT_TILT_DOWN, this.velocityAngle.x);
            int yaw = this.getFactor(context, Keys.FLIGHT_PAN_LEFT, Keys.FLIGHT_PAN_RIGHT, this.velocityAngle.y);
            boolean changed = x != this.velocityPosition.x || y != this.velocityPosition.y || z != this.velocityPosition.z || pitch != this.velocityAngle.x || yaw != this.velocityAngle.y;

            this.velocityPosition.set(x, y, z);
            this.velocityAngle.x = pitch;
            this.velocityAngle.y = yaw;

            return changed;
        }

        return false;
    }

    protected int getFactor(UIContext context, KeyCombo positive, KeyCombo negative, int x)
    {
        if (context.isPressed(positive.getMainKey()))
        {
            return 1;
        }
        else if (context.isPressed(negative.getMainKey()))
        {
            return -1;
        }
        else if (
            (context.isReleased(positive.getMainKey()) && x > 0) ||
            (context.isReleased(negative.getMainKey()) && x < 0)
        ) {
            return 0;
        }

        return x;
    }

    public void handleOrbiting(UIContext context)
    {
        if (this.dragging < 0 || !this.canInteract())
        {
            return;
        }

        int x = context.mouseX;
        int y = context.mouseY;
        float angleSpeed = this.controller.panel.dashboard.orbit.getAngleSpeed();

        if (this.dragging == 0 || this.dragging == 2)
        {
            this.rotation.add(
                -(y - this.last.y) * angleSpeed,
                -(x - this.last.x) * angleSpeed
            );
        }
        else if (this.dragging == 1)
        {
            this.roll += (x - this.last.x) * angleSpeed;
        }

        this.last.set(x, y);
    }

    public boolean scroll(int step)
    {
        if (this.center)
        {
            float next = this.distance - step * SCROLL_STEP;

            this.distance = Math.max(MIN_DISTANCE, next);
        }
        else
        {
            float next = this.position.z + step * SCROLL_STEP;

            this.position.z = next > -MIN_DISTANCE ? -MIN_DISTANCE : next;
        }

        return true;
    }

    public boolean update(UIContext context)
    {
        if (!this.enabled || context.isFocused())
        {
            return false;
        }

        boolean changed = false;

        if (this.animating)
        {
            float delta = MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false) / 20F;

            float duration = Math.max(0.1F, BBSSettings.editorOrbitTransitionDuration.get());

            this.animProgress = Math.min(1F, this.animProgress + delta / duration);

            if (this.animProgress >= 1F)
            {
                this.animating = false;
            }

            changed = true;
        }

        if (this.canInteract())
        {
            if (this.velocityPosition.lengthSquared() > 0 && !this.center)
            {
                this.position.add(this.rotateVector(-this.velocityPosition.x, this.velocityPosition.y, -this.velocityPosition.z, this.rotation.y, this.rotation.x).mul(this.getSpeed()));

                changed = true;
            }
            else if (this.center)
            {
                this.position.set(this.rotateVector(0F, 0F, 1F, this.rotation.y, this.rotation.x).mul(this.distance));
                this.position.add(0, this.offsetY, 0);
            }

            if (this.velocityAngle.lengthSquared() > 0)
            {
                float angleSpeed = this.controller.panel.dashboard.orbit.getAngleSpeed() * this.getSpeed() * 4F;

                this.rotation.x -= this.velocityAngle.x * angleSpeed;
                this.rotation.y -= this.velocityAngle.y * angleSpeed;

                changed = true;
            }
        }
        else
        {
            this.velocityPosition.set(0, 0, 0);
            this.velocityAngle.set(0, 0, 0, 0);
        }

        return changed;
    }

    protected float getSpeed()
    {
        return this.controller.panel.dashboard.orbit.getSpeed();
    }

    protected Vector3f rotateVector(float x, float y, float z, float yaw, float pitch)
    {
        return this.rotateVector(x, y, z, yaw, pitch, BBSSettings.editorHorizontalFlight.get());
    }

    protected Vector3f rotateVector(float x, float y, float z, float yaw, float pitch, boolean horizontal)
    {
        Matrix3f rotation = new Matrix3f();
        Vector3f rotate = new Vector3f(x, y, z);

        rotation.rotateY(yaw);

        if (!horizontal)
        {
            rotation.rotateX(pitch);
        }

        rotation.transform(rotate);

        return rotate;
    }

    public void syncFromCamera(Camera camera, float transition)
    {
        Vector3f anchor = this.getAnchor(transition);

        if (anchor == null)
        {
            return;
        }

        float renderYaw = this.getRenderYaw(transition);
        Vector3f offsetWorld = new Vector3f(
            (float) (camera.position.x - anchor.x),
            (float) (camera.position.y - anchor.y),
            (float) (camera.position.z - anchor.z)
        );
        Vector3f localOffset = this.rotateVector(offsetWorld.x, offsetWorld.y, offsetWorld.z, -renderYaw, 0F);

        if (this.center)
        {
            this.distance = localOffset.length();
            this.offsetY = localOffset.y;
        }
        else
        {
            this.position.set(localOffset);
        }

        this.rotation.x = -camera.rotation.x;
        this.rotation.y = -camera.rotation.y - renderYaw;
        this.roll = camera.rotation.z;
    }

    public Vector3f getAnchorPoint(float transition)
    {
        return this.getAnchor(transition);
    }

    public void pickTarget(Replay replay, Camera camera, Vector3f oldAnchor, float transition)
    {
        this.animateToTarget(camera, oldAnchor, transition);
    }

    public void animateToTarget(Camera fromCamera, Vector3f oldAnchor, float transition)
    {
        Vector3f newAnchor = this.getAnchor(transition);

        if (newAnchor == null)
        {
            return;
        }

        this.animFromPosition.set(fromCamera.position);
        this.animFromRotation.set(fromCamera.rotation);

        float renderYaw = this.getRenderYaw(transition);
        float orbitDistance = this.getOrbitDistance();
        Vector3f offset = this.rotateVector(0F, 0F, 1F, this.rotation.y + renderYaw, this.rotation.x, false).mul(orbitDistance);

        offset.y += this.offsetY;

        Camera toCamera = new Camera();

        toCamera.position.set(newAnchor);
        toCamera.position.add(offset);
        toCamera.rotation.set(-this.rotation.x, -(this.rotation.y + renderYaw), this.roll);

        this.syncFromCamera(toCamera, transition);

        if (!BBSSettings.editorOrbitSmoothTransition.get())
        {
            this.animating = false;
            this.animProgress = 1F;

            return;
        }

        this.animToPosition.set(toCamera.position);
        this.animToRotation.set(toCamera.rotation);

        this.animating = true;
        this.animProgress = 0F;
        this.dragging = -1;
        this.center = false;
    }

    /**
     * Whether orbit mouse/keyboard controls are active: always while flying,
     * or without flight when {@link BBSSettings#editorOrbitWithoutFlight} is on
     * (off by default so Space can still play/pause in orbit POV).
     */
    private boolean canInteract()
    {
        if (this.controller.panel.isFlying())
        {
            return true;
        }

        return BBSSettings.editorOrbitWithoutFlight.get()
            && this.controller.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT;
    }

    private float getOrbitDistance()
    {
        if (this.center)
        {
            return Math.max(MIN_DISTANCE, this.distance);
        }

        float length = this.position.length();

        return length > MIN_DISTANCE ? length : DEFAULT_ORBIT_DISTANCE;
    }

    @Override
    public void setup(Camera camera, float transition)
    {
        if (this.animating)
        {
            float t = this.smoothStep(this.animProgress);

            camera.position.x = Lerps.lerp((float) this.animFromPosition.x, (float) this.animToPosition.x, t);
            camera.position.y = Lerps.lerp((float) this.animFromPosition.y, (float) this.animToPosition.y, t);
            camera.position.z = Lerps.lerp((float) this.animFromPosition.z, (float) this.animToPosition.z, t);
            camera.rotation.x = Lerps.lerp(this.animFromRotation.x, this.animToRotation.x, t);
            camera.rotation.y = this.lerpAngle(this.animFromRotation.y, this.animToRotation.y, t);
            camera.rotation.z = Lerps.lerp(this.animFromRotation.z, this.animToRotation.z, t);

            return;
        }

        if (Gizmo.INSTANCE.isDragging())
        {
            if (!this.frozenForGizmoDrag)
            {
                this.computeCamera(camera, transition);
                this.frozenDragPosition.set(camera.position);
                this.frozenDragRotation.set(camera.rotation);
                this.frozenForGizmoDrag = true;
            }
            else
            {
                camera.position.set(this.frozenDragPosition);
                camera.rotation.set(this.frozenDragRotation);
            }

            return;
        }

        this.frozenForGizmoDrag = false;
        this.computeCamera(camera, transition);
    }

    private void computeCamera(Camera camera, float transition)
    {
        Vector3f anchor = this.getAnchor(transition);

        if (anchor == null)
        {
            return;
        }

        float renderYaw = this.getRenderYaw(transition);
        Vector3f offset = this.rotateVector(this.position.x, this.position.y, this.position.z, renderYaw, 0F);

        if (this.center)
        {
            offset = this.rotateVector(0F, 0F, 1F, this.rotation.y + renderYaw, this.rotation.x, false).mul(this.distance);
            offset.add(0, this.offsetY, 0);
        }

        camera.position.set(anchor);
        camera.position.add(offset);
        camera.rotation.set(-this.rotation.x, -(this.rotation.y + renderYaw), this.roll);
    }

    private float getRenderYaw(float transition)
    {
        IEntity entity = this.getOrbitEntity();

        if (entity == null)
        {
            return 0F;
        }

        return MathUtils.toRad(-Lerps.lerp(entity.getPrevBodyYaw(), entity.getBodyYaw(), transition) + 180F);
    }

    private IEntity getOrbitEntity()
    {
        IEntity entity = this.controller.getCurrentEntity();
        Replay replay = this.controller.panel.replayEditor.getReplay();

        if (replay != null && replay.isGroup.get())
        {
            Replay pivot = null;
            String uuid = replay.uuid.get();

            for (Replay r : this.controller.panel.getData().replays.getList())
            {
                if (r.group.get().contains(uuid))
                {
                    pivot = r;
                    break;
                }
            }

            if (pivot != null)
            {
                int index = this.controller.panel.getData().replays.getList().indexOf(pivot);
                IEntity pivotEntity = this.controller.getEntities().get(index);

                if (pivotEntity != null)
                {
                    entity = pivotEntity;
                }
            }
        }

        return entity;
    }

    private Vector3f getAnchor(float transition)
    {
        IEntity entity = this.getOrbitEntity();

        if (entity == null)
        {
            return null;
        }

        Form form = entity.getForm();
        double h = entity.getPickingHitbox().h / 2;
        double x = Lerps.lerp(entity.getPrevX(), entity.getX(), transition);
        double y = Lerps.lerp(entity.getPrevY(), entity.getY(), transition) + h;
        double z = Lerps.lerp(entity.getPrevZ(), entity.getZ(), transition);

        if (form != null)
        {
            MatrixCache map = FormUtilsClient.getRenderer(form).collectMatrices(entity, transition);
            String group = "anchor";

            if (form instanceof ModelForm modelForm)
            {
                ModelInstance model = ModelFormRenderer.getModel(modelForm);

                if (model != null)
                {
                    String anchor = model.getAnchor();

                    group = anchor.isEmpty() ? group : anchor;
                }
            }

            Matrix4f anchorMatrix = map.get(group).matrix();

            if (anchorMatrix != null)
            {
                Anchor v = form.anchor.get();
                Matrix4f defaultMatrix = BaseFilmController.getMatrixForRenderWithRotation(entity, x, y, z, transition);
                Pair<Matrix4f, Float> totalMatrix = BaseFilmController.getTotalMatrix(this.controller.getEntities(), v, defaultMatrix, x, y, z, transition, 0);

                if (totalMatrix.a != null)
                {
                    defaultMatrix = totalMatrix.a;
                }

                defaultMatrix.mul(anchorMatrix);

                Vector3f translate = defaultMatrix.getTranslation(Vectors.TEMP_3F);

                x += translate.x;
                y += translate.y;
                z += translate.z;
            }
        }

        return new Vector3f((float) x, (float) y, (float) z);
    }

    private float lerpAngle(float from, float to, float t)
    {
        float diff = to - from;

        while (diff > MathUtils.PI)
        {
            diff -= MathUtils.PI * 2F;
        }

        while (diff < -MathUtils.PI)
        {
            diff += MathUtils.PI * 2F;
        }

        return from + diff * t;
    }

    private float smoothStep(float t)
    {
        return t * t * (3F - 2F * t);
    }

    @Override
    public int getPriority()
    {
        return 20;
    }

    public void reset()
    {
        this.position.set(0F, 0F, -DEFAULT_ORBIT_DISTANCE);
        this.distance = DEFAULT_ORBIT_DISTANCE;
        this.rotation.set(0F, Math.PI);
        this.roll = 0F;
        this.dragging = -1;
        this.center = false;
        this.animating = false;
        this.animProgress = 0F;
        this.frozenForGizmoDrag = false;
        this.velocityPosition.set(0);
        this.velocityAngle.set(0);
    }
}
