package mchorse.bbs_mod.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.controller.ICameraController;
import mchorse.bbs_mod.camera.controller.PlayCameraController;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.ItemUseRenderState;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Films
{
    private List<BaseFilmController> controllers = new ArrayList<BaseFilmController>();
    private Recorder recorder;
    private final RecorderMobCapture editorMobCapture = new RecorderMobCapture();
    private final RecorderProjectileCapture editorProjectileCapture = new RecorderProjectileCapture();

    public Map<String, Map<String, Integer>> actors = new HashMap<>();

    /* Static helpers */

    public static void playFilm(String filmId, boolean withCamera)
    {
        if (ClientNetwork.isIsBBSModOnServer())
        {
            ClientNetwork.sendToggleFilm(filmId, withCamera);
        }
        else
        {
            if (BBSModClient.getFilms().has(filmId))
            {
                stopFilm(filmId);
            }
            else
            {
                ContentType.FILMS.getRepository().load(filmId, (data) ->
                {
                    MinecraftClient.getInstance().execute(() -> playFilm((Film) data, withCamera));
                });
            }
        }
    }

    public static void playFilm(Film film, boolean withCamera)
    {
        FirstPersonFilmController filmController = new FirstPersonFilmController(film);

        if (withCamera && !film.hasFirstPerson())
        {
            PlayCameraController controller = new PlayCameraController(film.camera);

            controller.getContext().entities.putAll(filmController.getEntities());
            BBSModClient.getCameraController().add(controller);
        }

        BBSModClient.getFilms().add(filmController);
    }

    public static void pauseFilm(String filmId)
    {
        if (ClientNetwork.isIsBBSModOnServer())
        {
            ClientNetwork.sendPauseFilm(filmId);
        }
        else
        {
            if (BBSModClient.getFilms().has(filmId))
            {
                togglePauseFilm(filmId);
            }
        }
    }

    public static void togglePauseFilm(String filmId)
    {
        BaseFilmController controller = BBSModClient.getFilms().getController(filmId);

        if (controller != null)
        {
            controller.togglePause();
        }
    }

    public static void stopFilm(String filmId)
    {
        Film film = BBSModClient.getFilms().remove(filmId);
        ICameraController current = BBSModClient.getCameraController().getCurrent();

        if (film != null && current instanceof PlayCameraController play)
        {
            if (play.getContext().clips == film.camera)
            {
                if (BBSModClient.getCameraController().remove(play) instanceof RunnerCameraController controller)
                {
                    controller.getContext().shutdown();
                }
            }
        }
    }

    /* Instance API */

    public BaseFilmController getController(String filmId)
    {
        for (BaseFilmController controller : this.controllers)
        {
            if (controller.film.getId().equals(filmId))
            {
                return controller;
            }
        }

        return null;
    }

    public List<BaseFilmController> getControllers()
    {
        return this.controllers;
    }

    public Recorder getRecorder()
    {
        return this.recorder;
    }

    public RecorderMobCapture getEditorMobCapture()
    {
        return this.editorMobCapture;
    }

    public RecorderProjectileCapture getEditorProjectileCapture()
    {
        return this.editorProjectileCapture;
    }

    public FirstPersonBobbingSample getFirstPersonBobbingSample(float tickDelta)
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null)
        {
            return null;
        }

        for (BaseFilmController controller : this.controllers)
        {
            if (controller == null || controller.film == null)
            {
                continue;
            }

            Map<String, Integer> actors = this.actors.get(controller.film.getId());

            if (actors == null || actors.isEmpty())
            {
                continue;
            }

            for (Replay replay : controller.film.replays.getList())
            {
                if (replay == null || !replay.enabled.get() || !replay.fp.get())
                {
                    continue;
                }

                Integer actorId = actors.get(replay.getId());

                if (actorId == null || actorId != player.getId())
                {
                    continue;
                }

                float tick = replay.getTick(controller.getTick()) + tickDelta;
                float vX = replay.keyframes.vX.interpolate(tick).floatValue();
                float vZ = replay.keyframes.vZ.interpolate(tick).floatValue();
                boolean grounded = replay.keyframes.grounded.interpolate(tick) > 0D;

                return new FirstPersonBobbingSample(vX, vZ, grounded, controller.paused);
            }
        }

        return null;
    }

    public void startRecording(Film film, int replayId, int tick)
    {
        /* Safety: never leave integrated-server ticks blocked after recording starts. */
        RecordingPauseHelper.reset();

        Morph morph = Morph.getMorph(MinecraftClient.getInstance().player);

        this.recorder = new Recorder(film, morph == null ? null : morph.getForm(), replayId, tick);

        MobCaptureRecordingSetup setup = MobCaptureRecordingSetup.pending;

        if (setup != null)
        {
            this.recorder.getMobCapture().applyRecordingSetup(setup);

            if (setup.shouldCapture())
            {
                this.recorder.getMobCapture().bulkCapture(film, tick, setup, null);
            }

            MobCaptureRecordingSetup.pending = null;
        }

        if (ClientNetwork.isIsBBSModOnServer())
        {
            ClientNetwork.sendActionRecording(film.getId(), replayId, this.recorder.getTick(), this.recorder.countdown, true);
        }

        Replay replay = CollectionUtils.getSafe(film.replays.getList(), replayId);

        if (replay != null)
        {
            MobCemPoseCapture.syncReplay(replay);
            ClientNetwork.sendPlayerForm(replay.form.get());
        }
    }

    public Recorder stopRecording()
    {
        RecordingPauseHelper.reset();

        Recorder recorder = this.recorder;

        this.recorder = null;

        if (recorder != null)
        {
            for (KeyframeChannel<?> channel : recorder.keyframes.getChannels())
            {
                channel.simplify();
            }

            recorder.getMobCapture().simplify(recorder.film);
            recorder.getProjectileCapture().simplify(recorder.film);

            if (ClientNetwork.isIsBBSModOnServer())
            {
                ClientNetwork.sendActionRecording(recorder.film.getId(), recorder.exception, recorder.initialTick, 0, false);
            }

            recorder.shutdown();
            recorder.getMobCapture().clear();
            recorder.getProjectileCapture().clear();
        }

        return recorder;
    }

    public void add(BaseFilmController controller)
    {
        this.controllers.add(controller);
    }

    public boolean has(String filmId)
    {
        for (BaseFilmController controller : this.controllers)
        {
            if (controller.film.getId().equals(filmId))
            {
                return true;
            }
        }

        return false;
    }

    public Film remove(String id)
    {
        Iterator<BaseFilmController> it = this.controllers.iterator();

        while (it.hasNext())
        {
            BaseFilmController next = it.next();

            if (next.film.getId().equals(id))
            {
                next.shutdown();
                it.remove();
                ItemUseRenderState.releaseLocalPlayerUse();

                return next.film;
            }
        }

        return null;
    }

    public void updateActors(String filmId, Map<String, Integer> actors)
    {
        this.actors.put(filmId, actors);
    }

    public void startRenderFrame(float transition)
    {
        if (this.recorder != null)
        {
            this.recorder.startRenderFrame(transition);
        }

        for (BaseFilmController controller : this.controllers)
        {
            controller.startRenderFrame(transition);
        }
    }

    public void update()
    {
        this.controllers.removeIf((film) ->
        {
            film.update();

            if (film.hasFinished())
            {
                film.shutdown();
                ItemUseRenderState.releaseLocalPlayerUse();
            }

            return film.hasFinished();
        });

        if (this.recorder != null)
        {
            this.recorder.update();
        }
    }

    public void updateEndWorld()
    {
        boolean wasDriving = ItemUseRenderState.isDrivingLocalPlayerUse();

        ItemUseRenderState.beginEndWorldUpdate();

        for (BaseFilmController controller : this.controllers)
        {
            controller.updateEndWorld();
        }

        if (wasDriving && !ItemUseRenderState.isDrivingLocalPlayerUse())
        {
            ItemUseRenderState.releaseLocalPlayerUse();
        }
    }

    public void render(WorldRenderContext context)
    {
        Gizmo.INSTANCE.clearVisual();

        GlStateManager._enableDepthTest();

        for (BaseFilmController controller : this.controllers)
        {
            controller.render(context);
        }

        if (this.recorder != null)
        {
            this.recorder.render(context);
        }

        /* Leave world depth usable for later translucent / particle passes. */
        GlStateManager._enableDepthTest();
        GlStateManager._depthFunc(GL11.GL_LEQUAL);
    }

    public void renderHud(Batcher2D batcher2D, float tickDelta)
    {
        Recorder recorder = BBSModClient.getFilms().getRecorder();

        if (recorder != null && BBSSettings.recordingOverlays.get())
        {
            String label = recorder.hasNotStarted() ?
                String.valueOf(TimeUtils.toSeconds(recorder.countdown)) :
                UIKeys.FILM_RECORDING.format(recorder.getTick()).get();
            int x = 5;
            int y = 5;
            int w = batcher2D.getFont().getWidth(label);

            batcher2D.box(x, y, x + 18 + w + 3, y + 16, Colors.A50);
            batcher2D.icon(Icons.SPHERE, Colors.RED | Colors.A100, x, y);
            batcher2D.textShadow(label, x + 18, y + 4);

            /* Render audio waveform */
            List<AudioClip> audioClips = new ArrayList<>();

            for (Clip clip : recorder.film.camera.get())
            {
                if (clip instanceof AudioClip)
                {
                    audioClips.add((AudioClip) clip);
                }
            }

            int sw = MinecraftClient.getInstance().getWindow().getScaledWidth();
            int sh = MinecraftClient.getInstance().getWindow().getScaledHeight();
            w = (int) (sw * BBSSettings.audioWaveformWidth.get());
            x = sw / 2 - w / 2;
            y = sh / 2 + 100;

            AudioRenderer.renderAll(batcher2D, audioClips, recorder.getTick() + tickDelta, x, y, w, BBSSettings.audioWaveformHeight.get(), sw, sh);
        }
    }

    public void reset()
    {
        controllers.clear();

        recorder = null;
    }

    public static class FirstPersonBobbingSample
    {
        public final float vX;
        public final float vZ;
        public final boolean grounded;
        public final boolean paused;

        public FirstPersonBobbingSample(float vX, float vZ, boolean grounded, boolean paused)
        {
            this.vX = vX;
            this.vZ = vZ;
            this.grounded = grounded;
            this.paused = paused;
        }
    }
}
