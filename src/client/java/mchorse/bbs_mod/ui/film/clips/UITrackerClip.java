package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.misc.TrackerClientClip;
import mchorse.bbs_mod.camera.clips.misc.TrackerFrame;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.clips.widgets.UIBitToggle;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;

import net.minecraft.util.math.MathHelper;

import org.joml.Vector3d;

import io.netty.util.collection.IntObjectMap;

public class UITrackerClip extends UIClip<TrackerClientClip>
{
    public UIButton selector;
    public UIButton group;

    public UIPointModule point;
    public UIPointModule angle;
    public UITrackpad fov;
    public UIToggle lookAt;
    public UIToggle relative;
    public UIBitToggle active;

    public UIToggle useKeyframes;
    public UIButton edit;
    public UIKeyframeEditor keyframes;

    public UITrackerClip(TrackerClientClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    public void registerUI()
    {
        super.registerUI();

        this.selector = new UIButton(UIKeys.CAMERA_PANELS_TARGET_TITLE, (b) ->
        {
            UIFilmPanel panel = this.getParent(UIFilmPanel.class);

            if (panel != null)
            {
                UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().getEntities(), this.clip.selector.get(), (i) -> this.clip.selector.set(i));
            }
        });
        this.selector.tooltip(UIKeys.CAMERA_PANELS_TARGET_TOOLTIP);
        this.group = new UIButton(UIKeys.GENERIC_KEYFRAMES_ANCHOR_PICK_ATTACHMENT, (b) ->
        {
            UIAnchorKeyframeFactory.displayAttachments(this.getParent(UIFilmPanel.class), this.clip.selector.get(), this.clip.group.get(), (attachment) -> this.clip.group.set(attachment));
        });

        this.point = new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_OFFSET).contextMenu();
        this.angle = new UIPointModule(this.editor, UIKeys.CAMERA_PANELS_ANGLE).contextMenu();
        this.fov = new UITrackpad((v) -> this.clip.fov.set(v.floatValue()));
        this.fov.tooltip(UIKeys.CAMERA_PANELS_FOV);
        this.lookAt = new UIToggle(UIKeys.CAMERA_PANELS_LOOK_AT, b -> this.clip.lookAt.set(b.getValue()));
        this.relative = new UIToggle(UIKeys.CAMERA_PANELS_RELATIVE, b -> this.clip.relative.set(b.getValue()));
        this.active = new UIBitToggle((value) -> this.clip.active.set(value)).all();

        this.keyframes = new UIKeyframeEditor((consumer) -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.backgroundRenderer((context) ->
        {
            UIReplaysEditor.renderBackground(context, this.keyframes.view, (Clips) this.clip.getParent(), Math.round(this.clip.tick.get()), this.clip);
        });
        this.keyframes.view.duration(() -> this.clip.duration.get());
        this.keyframes.setUndoId("tracker_keyframes");

        this.useKeyframes = new UIToggle(UIKeys.SCREEN_PANELS_USE_KEYFRAMES, (b) ->
        {
            boolean enabled = b.getValue();
            float tick = this.getClipTick();

            if (enabled)
            {
                this.clip.useKeyframes.set(true);
                this.clip.ensureChannelsSeeded();
                this.updateSheets();
            }
            else
            {
                /* Fold the values playing right now back into the statics, so
                 * turning keyframes off doesn't snap the camera. */
                this.clip.offset.set(this.clip.evaluateOffset(tick));
                this.clip.angle.set(this.clip.evaluateAngle(tick));
                this.clip.fov.set(this.clip.evaluateFov(tick));
                this.clip.selector.set(this.clip.evaluateTarget(tick));
                this.clip.group.set(this.clip.evaluateAttachment(tick));
                this.clip.useKeyframes.set(false);

                if (this.keyframes.hasParent())
                {
                    this.editor.embedView(null);
                }
            }

            this.fillData();
        });
        this.useKeyframes.tooltip(UIKeys.SCREEN_PANELS_USE_KEYFRAMES_TOOLTIP);

        this.edit = new UIButton(UIKeys.GENERAL_EDIT, (b) ->
        {
            if (!this.clip.useKeyframes.get())
            {
                return;
            }

            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
            this.keyframes.view.getGraph().clearSelection();
        });
        this.edit.keys().register(Keys.FORMS_EDIT, () -> this.edit.clickItself());
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_TARGET, this.selector, this.group));
        this.panels.add(this.point);
        this.panels.add(this.angle);
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_FOV, this.fov, this.lookAt, this.relative, this.active));
        this.panels.add(this.section(UIKeys.CAMERA_PANELS_KEYFRAMES, this.useKeyframes, this.edit));
    }

    /**
     * Turn a camera the user placed (by flying, or by orbiting the viewport) into
     * the offset and angle that reproduce it, so the tracker is authored the same
     * way overwriting clips are, instead of through its trackpads only.
     *
     * <p>With keyframe mode on, the solved values are inserted as keyframes at the
     * playhead instead of overwriting the static offset and angle.</p>
     *
     * <p>The bone's frame is resolved fresh against the pose on screen, so
     * scrubbing while flying stays honest. Relative mode is the exception: the
     * camera's travel is measured against the clips underneath, which aren't
     * evaluated while flying, so the last evaluated one is used.</p>
     */
    @Override
    public void editClip(Position position)
    {
        if (this.clip.useKeyframes.get())
        {
            this.recordKeyframes(position);
        }
        else
        {
            this.applyCameraPosition(position);
        }

        super.editClip(position);
    }

    private void applyCameraPosition(Position position)
    {
        TrackerFrame frame = this.resolveFrame(position);

        if (frame == null)
        {
            return;
        }

        Point offset = this.clip.offset.get();
        Point angle = this.clip.angle.get();

        boolean lookAt = this.clip.lookAt.get();

        /* Look-at doesn't place the camera — its offset moves the point being
         * looked at — so only the framing offset is authorable there */
        if (!lookAt && this.isActive(0, 1, 2))
        {
            Vector3d current = frame.position(offset);
            Point solved = frame.solveOffset(
                this.pick(0, position.point.x, current.x),
                this.pick(1, position.point.y, current.y),
                this.pick(2, position.point.z, current.z)
            );

            if (solved != null)
            {
                this.clip.offset.set(solved);
            }
        }

        if (this.isActive(3, 4, 5))
        {
            Angle current = lookAt ? frame.lookAtAngles(offset, angle) : frame.angles(angle);
            float yaw = this.pick(3, position.angle.yaw, current.yaw);
            float pitch = this.pick(4, position.angle.pitch, current.pitch);
            float roll = this.pick(5, position.angle.roll, current.roll);
            Point solved = lookAt ? frame.solveLookAtAngles(offset, yaw, pitch, roll) : frame.solveAngles(yaw, pitch, roll);

            /* Euler solve wraps to ±180; keep continuity with the previous offset. */
            this.clip.angle.set(unwrapAngle(angle, solved));
        }

        /* Only when it actually moved: an unchanged value still notifies, and a
         * value that joins and leaves the frame's edit breaks undo merging */
        if (this.clip.isActive(6) && position.angle.fov != this.clip.fov.get())
        {
            this.clip.fov.set(position.angle.fov);
        }
    }

    /**
     * Keyframe-mode counterpart of {@link #applyCameraPosition(Position)}: the
     * same inverse solve, but the result lands as keyframes at the playhead, so
     * flying (or orbiting) authors the tracker's channels exactly like the
     * keyframe clip's ones.
     */
    private void recordKeyframes(Position position)
    {
        TrackerFrame frame = this.resolveFrame(position);

        if (frame == null)
        {
            return;
        }

        float tick = this.getClipTick();
        Point offset = this.clip.evaluateOffset(tick);
        Point angle = this.clip.evaluateAngle(tick);
        boolean lookAt = this.clip.lookAt.get();

        if (!lookAt && this.isActive(0, 1, 2))
        {
            Vector3d current = frame.position(offset);
            Point solved = frame.solveOffset(
                this.pick(0, position.point.x, current.x),
                this.pick(1, position.point.y, current.y),
                this.pick(2, position.point.z, current.z)
            );

            if (solved != null)
            {
                this.insertKeyframe(this.clip.keyframeX, tick, solved.x);
                this.insertKeyframe(this.clip.keyframeY, tick, solved.y);
                this.insertKeyframe(this.clip.keyframeZ, tick, solved.z);
            }
        }

        if (this.isActive(3, 4, 5))
        {
            Angle current = lookAt ? frame.lookAtAngles(offset, angle) : frame.angles(angle);
            float yaw = this.pick(3, position.angle.yaw, current.yaw);
            float pitch = this.pick(4, position.angle.pitch, current.pitch);
            float roll = this.pick(5, position.angle.roll, current.roll);
            Point solved = lookAt ? frame.solveLookAtAngles(offset, yaw, pitch, roll) : frame.solveAngles(yaw, pitch, roll);

            /* Euler solve wraps to ±180 (e.g. 144 → -144). Unwrap against the
             * angle already playing so a small turn doesn't insert a half-spin. */
            solved = unwrapAngle(angle, solved);

            this.insertKeyframe(this.clip.keyframePitch, tick, solved.x);
            this.insertKeyframe(this.clip.keyframeYaw, tick, solved.y);
            this.insertKeyframe(this.clip.keyframeRoll, tick, solved.z);
        }

        if (this.clip.isActive(6))
        {
            this.insertKeyframe(this.clip.keyframeFov, tick, position.angle.fov);
        }
    }

    private void insertKeyframe(KeyframeChannel<Double> channel, float tick, double value)
    {
        KeyframeSegment<Double> segment = channel.findSegment(tick);
        int insert = channel.insert(tick, value);

        if (segment != null)
        {
            channel.get(insert).copyOverExtra(segment.a);
        }
    }

    /**
     * Map {@code next} onto the 360° branch closest to {@code previous}, so a
     * continuous turn (144 → 204) isn't rewritten as a wrap (144 → -144).
     */
    private static Point unwrapAngle(Point previous, Point next)
    {
        return new Point(
            unwrapDegrees(previous.x, next.x),
            unwrapDegrees(previous.y, next.y),
            unwrapDegrees(previous.z, next.z)
        );
    }

    private static double unwrapDegrees(double previous, double next)
    {
        double delta = next - previous;

        delta -= 360D * Math.round(delta / 360D);

        return previous + delta;
    }

    private float getClipTick()
    {
        return MathHelper.clamp(this.editor.getCursor() - Math.round(this.clip.tick.get()), 0, this.clip.duration.get());
    }

    private TrackerFrame resolveFrame(Position position)
    {
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);
        UIContext context = this.getContext();
        int selector = this.clip.useKeyframes.get()
            ? this.clip.evaluateTarget(this.getClipTick())
            : this.clip.selector.get();
        String group = this.clip.useKeyframes.get()
            ? this.clip.evaluateAttachment(this.getClipTick())
            : this.clip.group.get();

        if (panel == null || context == null || selector < 0)
        {
            return null;
        }

        IntObjectMap<IEntity> entities = panel.getController().getEntities();
        TrackerFrame frame = TrackerFrame.resolve(
            entities,
            entities.get(selector),
            group,
            position.point.x,
            position.point.y,
            position.point.z,
            context.getTransition()
        );

        if (frame != null && this.clip.relative.get())
        {
            if (!this.clip.isEvaluated())
            {
                return null;
            }

            frame.relative(this.clip.getUnderneath(), this.clip.position);
        }

        return frame;
    }

    /**
     * A channel the tracker doesn't drive ({@link TrackerClientClip#isActive})
     * shows whatever the clips underneath put there, so solving against it would
     * fold a foreign value into the offset. Those channels keep what the tracker
     * currently produces instead, which the inverse maps back to itself.
     */
    /** Whether the tracker drives any of the given channels at all. */
    private boolean isActive(int... bits)
    {
        for (int bit : bits)
        {
            if (this.clip.isActive(bit))
            {
                return true;
            }
        }

        return false;
    }

    private float pick(int bit, float camera, float current)
    {
        return this.clip.isActive(bit) ? camera : current;
    }

    private double pick(int bit, double camera, double current)
    {
        return this.clip.isActive(bit) ? camera : current;
    }

    private void updateSheets()
    {
        this.keyframes.setChannels(this.clip.channels);

        for (UIKeyframeSheet sheet : this.keyframes.view.getGraph().getSheets())
        {
            if ("target".equals(sheet.id))
            {
                sheet.title = UIKeys.CAMERA_PANELS_TARGET;
            }
            else if ("attachment".equals(sheet.id))
            {
                sheet.title = IKey.constant("attachment");
            }
            else if ("kf_fov".equals(sheet.id))
            {
                sheet.title = IKey.constant("fov");
            }
        }
    }

    @Override
    public void fillData()
    {
        super.fillData();

        boolean keyframed = this.clip.useKeyframes.get();

        /* Skip rewriting focused keyframe factory inputs mid-edit. */
        if (!this.keyframes.isEditorInputFocused())
        {
            this.point.fill(this.clip.offset);
            this.angle.fill(this.clip.angle);
            this.fov.setValue(this.clip.fov.get());
        }

        this.lookAt.setValue(this.clip.lookAt.get());
        this.relative.setValue(this.clip.relative.get());
        this.active.setValue(this.clip.active.get());

        this.useKeyframes.setValue(keyframed);
        this.edit.setEnabled(keyframed);

        /* Statics are ignored while keyframe mode drives the tracker. */
        this.point.setEnabled(!keyframed);
        this.angle.setEnabled(!keyframed);
        this.fov.setEnabled(!keyframed);

        /* Avoid rebuilding sheets on every scrub/edit — setChannels clears keyframe pick. */
        if (this.keyframes.view.getGraph().getSheets().size() != this.clip.channels.length)
        {
            this.updateSheets();
        }
    }

    @Override
    protected UIElement resolveClipEmbeddableView(String undoId)
    {
        return undoId.equals(this.keyframes.getUndoId()) ? this.keyframes : null;
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.getString("embed").equals("tracker_keyframes") && this.clip.useKeyframes.get())
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        if (this.keyframes.hasParent())
        {
            data.putString("embed", "tracker_keyframes");
        }
    }
}
