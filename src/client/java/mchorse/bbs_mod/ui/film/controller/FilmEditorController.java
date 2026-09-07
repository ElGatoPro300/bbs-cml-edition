package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.MobCemPoseCapture;
import mchorse.bbs_mod.film.RecorderMobCapture;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.ITickable;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.ui.utils.gizmo.TransformOrientation;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import net.minecraft.client.MinecraftClient;

import java.util.List;
import java.util.Map;

public class FilmEditorController extends BaseFilmController
{
    public UIFilmController controller;

    private int lastTick;
    private int pausedAnimationSteps;
    private boolean wasRunnerRunning;

    public FilmEditorController(Film film, UIFilmController controller)
    {
        super(film);

        this.controller = controller;
    }

    @Override
    public Map<String, Integer> getActors()
    {
        return this.controller.getActors();
    }

    @Override
    public int getTick()
    {
        return this.controller.panel.getCursor();
    }

    @Override
    protected void updateEntities(int ticks)
    {
        boolean running = this.controller.panel.getRunner().isRunning();

        ticks = this.getTick() + (running ? 1 : 0);
        this.pausedAnimationSteps = 0;

        if (!this.controller.isPlaying())
        {
            this.pausedAnimationSteps = Math.abs(ticks - this.lastTick);

            /* Play applies at cursor+1; pause drops that +1. That single-tick delta
             * is not a scrub — treating it as one would snap actors and look like a teleport. */
            if (this.wasRunnerRunning && !running && this.pausedAnimationSteps == 1)
            {
                this.pausedAnimationSteps = 0;
            }
        }

        super.updateEntities(ticks);

        /* Stubs do not run updateEntityAndForm while paused. After a swipe ends,
         * settle stale hand-swing prev so wrap cannot block procedural idle. */
        if (!this.controller.isPlaying())
        {
            for (IEntity entity : this.entities.values())
            {
                if (entity instanceof StubEntity stub)
                {
                    stub.settleFinishedHandSwing();
                }
            }
        }

        this.lastTick = ticks;
        this.wasRunnerRunning = running;
    }

    @Override
    protected void updateEntityAndForm(IEntity entity, int tick)
    {
        boolean isPlaying = this.controller.isPlaying();
        boolean isActor = !(entity instanceof MCEntity);

        if (isPlaying && isActor)
        {
            super.updateEntityAndForm(entity, tick);
        }
    }

    @Override
    protected boolean isActorPlaybackActive()
    {
        return this.controller.isPlaying();
    }

    @Override
    protected int getPausedAnimationAdvanceSteps()
    {
        return this.pausedAnimationSteps;
    }

    @Override
    protected int getPausedAnimationFromTick()
    {
        return this.lastTick;
    }

    @Override
    protected void applyReplay(Replay replay, int ticks, IEntity entity)
    {
        List<String> groups = this.controller.getRecordingGroups();
        boolean isPlaying = this.controller.isPlaying();
        boolean isActor = !(entity instanceof MCEntity);
        boolean scrubbingStub = !isPlaying && isActor && this.pausedAnimationSteps > 0;
        double scrubFromX = entity.getX();
        double scrubFromY = entity.getY();
        double scrubFromZ = entity.getZ();

        boolean isControlled = entity == this.controller.getControlled();
        boolean recordingLive = this.controller.isRecording() && this.controller.getRecordingCountdown() <= 0;

        /* Outside recording sets exception so the take subject never replays old
         * client clips. Viewport keeps the stub for keyframe capture — skip swipe
         * (and other client actions) on the controlled entity while recording, but
         * still apply non-recorded keyframe groups when capturing a subset. */
        if (!isControlled || (recordingLive && groups != null))
        {
            replay.keyframes.apply(ticks, entity, isControlled ? groups : null);

            if (!isControlled && this.shouldApplyClientActions(entity))
            {
                replay.applyClientActions(ticks, entity, this.film);
            }
        }

        if (entity == this.controller.getControlled() && this.controller.isRecording() && this.controller.panel.getRunner().isRunning())
        {
            List<Replay> replays = this.film.replays.getList();
            int index = replays.indexOf(replay);
            int cursor = this.controller.panel.getCursor();

            MobCemPoseCapture.syncReplay(replay);
            replay.keyframes.record(cursor, entity, groups);

            if (ReplayKeyframes.wantsVanillaPoseActions(groups))
            {
                RecorderMobCapture.recordMountKeyframes(replays, index, replay.keyframes, entity, cursor);
            }

            if (MobCemPoseCapture.isActive(replay))
            {
                MobCemPoseCapture.recordPoseKeyframe(replay, replay.form.get(), entity, cursor, 0F);
            }

            if (this.controller.getRecordingCountdown() <= 0 && index >= 0)
            {
                BBSModClient.getFilms().getEditorProjectileCapture().recordEditorTick(this.film, index, cursor, BBSModClient.getFilms().getEditorMobCapture(), this.controller.getActors());
            }
        }

        ticks = this.getTick() + (this.controller.panel.getRunner().isRunning() ? 1 : 0);

        /* Special pausing logic for stubs (non-actor path / hidden stubs). */
        if (!isPlaying && isActor)
        {
            if (scrubbingStub)
            {
                entity.setPrevX(scrubFromX);
                entity.setPrevY(scrubFromY);
                entity.setPrevZ(scrubFromZ);
            }
            else
            {
                entity.setPrevX(entity.getX());
                entity.setPrevY(entity.getY());
                entity.setPrevZ(entity.getZ());
            }

            entity.setPrevYaw(entity.getYaw());
            entity.setPrevHeadYaw(entity.getHeadYaw());
            entity.setPrevBodyYaw(entity.getBodyYaw());
            entity.setPrevPitch(entity.getPitch());

            int diff = this.pausedAnimationSteps;

            while (diff > 0)
            {
                entity.update();

                if (entity.getForm() != null)
                {
                    entity.getForm().update(entity);
                }

                diff -= 1;
            }
        }
    }

    @Override
    protected boolean shouldEmitReplayMotionFx(IEntity entity)
    {
        return !this.controller.isControlling() || entity != this.controller.getControlled();
    }

    @Override
    protected boolean shouldApplyClientActions(IEntity entity)
    {
        return !this.controller.shouldSuppressClientActions(this.getTick());
    }

    @Override
    protected float getTransition(IEntity entity, float transition)
    {
        boolean current = this.isCurrent(entity) && this.controller.isControlling();
        float delta = !this.controller.isPlaying() && !current ? 0F : transition;

        return delta;
    }

    @Override
    protected boolean canUpdate(int i, Replay replay, IEntity entity, UpdateMode updateMode)
    {
        return super.canUpdate(i, replay, entity, updateMode)
            || this.controller.getPovMode() != UIFilmController.CAMERA_MODE_FIRST_PERSON
            || !this.isCurrent(entity)
            || !this.controller.orbit.enabled;
    }

    @Override
    protected void renderEntity(WorldRenderContext context, Replay replay, IEntity entity, int index)
    {
        boolean current = this.isCurrent(entity);

        if (!(this.controller.getPovMode() == UIFilmController.CAMERA_MODE_FIRST_PERSON && current))
        {
            if (replay.actor.get())
            {
                this.renderActorModeEntity(context, replay, entity);
            }
            else
            {
                super.renderEntity(context, replay, entity, index);
            }
        }

        boolean isPlaying = this.controller.isPlaying();
        int ticks = replay.getTick(this.getTick());
        ValueOnionSkin onionSkin = this.controller.getOnionSkin();
        BaseValue value = replay.properties.get(onionSkin.group.get());

        if (value == null)
        {
            value = replay.properties.get("pose");
        }

        if (value instanceof KeyframeChannel<?> pose && entity instanceof StubEntity)
        {
            boolean canRender = onionSkin.enabled.get();

            if (!onionSkin.all.get())
            {
                canRender = canRender && current;
            }

            if (canRender)
            {
                KeyframeSegment<?> segment = pose.findSegment(ticks);

                if (segment != null)
                {
                    this.renderOnion(replay, pose.getKeyframes().indexOf(segment.a), -1, pose, onionSkin.preColor.get(), onionSkin.preFrames.get(), context, isPlaying, entity);
                    this.renderOnion(replay, pose.getKeyframes().indexOf(segment.b), 1, pose, onionSkin.postColor.get(), onionSkin.postFrames.get(), context, isPlaying, entity);

                    replay.keyframes.apply(ticks, entity);
                    float tick = ticks + this.getTransition(entity, MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false));
                    Form form = entity.getForm();
                    replay.properties.applyProperties(form, tick);

                    if (!isPlaying)
                    {
                        entity.setPrevX(entity.getX());
                        entity.setPrevY(entity.getY());
                        entity.setPrevZ(entity.getZ());
                        entity.setPrevYaw(entity.getYaw());
                        entity.setPrevHeadYaw(entity.getHeadYaw());
                        entity.setPrevBodyYaw(entity.getBodyYaw());
                        entity.setPrevPitch(entity.getPitch());
                    }
                }
            }
        }
    }

    /**
     * Actor mode: draw gizmos from the live {@link ActorEntity} only.
     * Never fall back to the stub body — after combat death the physical actor
     * is removed and a stub fallback looked like a revived corpse following
     * the remaining keyframes. Scrub keeps world clips; combat HP is silent.
     */
    private void renderActorModeEntity(WorldRenderContext context, Replay replay, IEntity stub)
    {
        int replayTick = replay.getTick(this.getTick());

        if (!this.isReplayVisible(replay, replayTick))
        {
            return;
        }

        IEntity physical = this.getPhysicalActorEntity(replay);

        if (physical == null || this.isActorPickingBlocked(replay))
        {
            return;
        }

        /* Keep bone selection from the stub (isCurrent), then swap to the physical
         * actor so pose matrices match what ActorEntityRenderer draws. */
        FilmControllerContext filmContext = this.getFilmControllerContext(context, replay, stub);

        if (filmContext.bone == null && filmContext.bone2 == null)
        {
            return;
        }

        filmContext.entity = physical;
        filmContext.physicalActor(true);
        filmContext.transition = this.getTransition(stub, MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false));
        filmContext.stack.push();

        try
        {
            if (!this.applyGroupProperties(replay, filmContext))
            {
                return;
            }

            renderEntity(filmContext);
        }
        finally
        {
            filmContext.stack.pop();
        }
    }

    private void renderOnion(Replay replay, int index, int direction, KeyframeChannel<?> pose, int color, int frames, WorldRenderContext context, boolean isPlaying, IEntity entity)
    {
        List<? extends Keyframe<?>> keyframes = pose.getKeyframes();
        float alpha = Colors.getA(color);

        for (; CollectionUtils.inRange(keyframes, index) && frames > 0; index += direction)
        {
            Keyframe<?> keyframe = keyframes.get(index);

            if (keyframe.getTick() == this.getTick())
            {
                continue;
            }

            int tick1 = (int) keyframe.getTick();
            replay.keyframes.apply(tick1, entity);
            float tick = (int) keyframe.getTick();
            Form form = entity.getForm();
            replay.properties.resetProperties(form);
            replay.properties.applyProperties(form, tick);

            BaseFilmController.renderEntity(FilmControllerContext.instance
                .setup(this.getEntities(), entity, replay, context)
                .film(this.film)
                .propertyTick(tick)
                .filmTick(this.getTick())
                .color(Colors.setA(color, alpha))
                .transition(0F));

            frames -= 1;
            alpha *= alpha;
        }
    }

    @Override
    protected FilmControllerContext getFilmControllerContext(WorldRenderContext context, Replay replay, IEntity entity)
    {
        Pair<String, TransformOrientation> bone = this.isCurrent(entity) && !this.controller.panel.recorder.isRecording() ? this.controller.getBone() : null;
        String aBone = bone == null ? null : bone.a;
        boolean local = bone != null && bone.b != null;
        String aBone2 = null;
        boolean local2 = false;

        if (replay.axesPreview.get())
        {
            aBone2 = replay.axesPreviewBone.get();
            local2 = true;
        }

        if (this.controller.panel.recorder.isRecording())
        {
            aBone = null;
            local = false;
            aBone2 = null;
            local2 = false;
        }

        return super.getFilmControllerContext(context, replay, entity)
            .transition(this.getTransition(entity, MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false)))
            .bone(aBone, local)
            .bone2(aBone2, local2);
    }

    private boolean isCurrent(IEntity entity)
    {
        return entity == this.controller.getCurrentEntity();
    }
}