package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.AudioClientClip;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.utils.clips.Clip;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;

import java.util.List;
import java.util.Map;

public class WorldFilmController extends BaseFilmController
{
    protected CameraClipContext context;
    protected Position position = new Position();

    public int tick;
    public int duration;

    public WorldFilmController(Film film)
    {
        super(film);

        this.createEntities();

        this.duration = film.camera.calculateDuration();
        this.context = new CameraClipContext();
        this.context.clips = film.camera;
    }

    public CameraClipContext getCameraContext()
    {
        return this.context;
    }

    /**
     * Applies camera clips (curves, audio triggers, etc.) into {@link #context}
     * so world lighting can read curve data outside the film editor.
     */
    public void applyCameraClips(float transition)
    {
        int tick = Math.max(this.tick, 0);
        float delta = this.paused ? 0F : transition;
        List<Clip> clips = this.context.clips.getClips(tick);

        this.context.clipData.clear();
        this.context.setup(tick, delta);

        for (Clip clip : clips)
        {
            this.context.apply(clip, this.position);
        }

        this.context.currentLayer = 0;
    }

    @Override
    public Map<String, Integer> getActors()
    {
        return BBSModClient.getFilms().actors.get(this.film.getId());
    }

    @Override
    public int getTick()
    {
        return this.tick;
    }

    @Override
    public boolean hasFinished()
    {
        return this.tick >= this.duration;
    }

    @Override
    public void update()
    {
        if (!this.paused)
        {
            this.tick += 1;
        }

        super.update();

        if (this.paused)
        {
            this.syncPausedActorAnimationFreeze();
        }

        /* Keep curve data fresh for time-of-day / sun-path even before render. */
        this.applyCameraClips(0F);
    }

    /**
     * World films skip the UPDATE loop while paused, so actor freeze flags must
     * be applied here for timeline-synced natural animations.
     */
    private void syncPausedActorAnimationFreeze()
    {
        boolean freeze = BBSSettings.editorActorPauseAnimations != null
            && BBSSettings.editorActorPauseAnimations.get();
        Map<String, Integer> actors = this.getActors();

        if (actors == null || MinecraftClient.getInstance().world == null)
        {
            return;
        }

        for (Integer entityId : actors.values())
        {
            if (entityId == null)
            {
                continue;
            }

            Entity entity = MinecraftClient.getInstance().world.getEntityById(entityId);

            if (entity instanceof ActorEntity actor)
            {
                actor.setPauseNaturalAnimations(freeze);
            }
        }
    }

    @Override
    public void startRenderFrame(float transition)
    {
        super.startRenderFrame(transition);
        this.applyCameraClips(transition);
    }

    @Override
    public void render(WorldRenderContext context)
    {
        super.render(context);

        this.applyCameraClips(context.tickCounter().getTickDelta(false));

        if (BBSSettings.recordingCameraPreview.get())
        {
            int tick = Math.max(this.tick, 0);

            Recorder.renderCameraPreviewTimeline(this.context.clips, tick, context.tickCounter().getTickDelta(true), this.duration, this.position, context.camera(), context.matrixStack());
        }

        AudioClientClip.manageSounds(this.context);
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        this.context.shutdown();
    }
}
