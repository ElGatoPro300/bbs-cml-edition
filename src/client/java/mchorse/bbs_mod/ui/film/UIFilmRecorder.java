package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.audio.AudioRenderer;
import mchorse.bbs_mod.camera.clips.misc.AudioClip;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.VideoRecorder;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;

import org.joml.Vector2i;

import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class UIFilmRecorder extends UIElement
{
    public UIFilmPanel editor;

    public Runnable onStop;

    private UIExit exit = new UIExit(this);
    private UIButton cancel;
    private int end;
    private int warmupTicks;
    private int pendingId;
    private int pendingW;
    private int pendingH;

    public boolean resetReplays = true;

    public UIFilmRecorder(UIFilmPanel editor)
    {
        super();

        this.editor = editor;
        this.cancel = new UIButton(UIKeys.FILM_CANCEL_RECORDING, (b) -> this.stop());
        this.add(this.cancel);

        this.markContainer();
        this.eventPropagataion(EventPropagation.BLOCK);
        this.noCulling();
    }

    public boolean isRecording()
    {
        return getRecorder().isRecording();
    }

    public boolean isWarmingUp()
    {
        return this.warmupTicks > 0;
    }

    public boolean isRecordingOrWarmingUp()
    {
        return this.isRecording() || this.isWarmingUp();
    }

    private UIContext getUIContext()
    {
        return this.editor.getContext();
    }

    private VideoRecorder getRecorder()
    {
        return BBSModClient.getVideoRecorder();
    }

    private boolean isRunning()
    {
        return this.editor.isRunning();
    }

    public void openMovies()
    {
        UIUtils.openFolder(BBSRendering.getVideoFolder());
    }

    public void startRecording(int duration, Texture texture)
    {
        float delay = BBSSettings.videoSettings.warmupDelay.get();

        if (delay > 0F)
        {
            this.startRecordingAfterLoad(duration, texture, (int) (delay * 20F));

            return;
        }

        this.startRecording(duration, texture.id, texture.width, texture.height);
    }

    public void startRecording(int duration, int id, int w, int h)
    {
        VideoRecorder recorder = this.getRecorder();
        UIContext context = this.getUIContext();

        if (this.isRunning() || recorder.isRecording() || duration <= 0)
        {
            return;
        }

        float delay = BBSSettings.videoSettings.warmupDelay.get();

        if (delay > 0F && this.warmupTicks <= 0)
        {
            Texture texture = BBSRendering.getTexture();
            int texId = texture != null ? texture.id : id;
            int texW = texture != null ? texture.width : w;
            int texH = texture != null ? texture.height : h;

            this.startRecordingAfterLoad(duration, texId, texW, texH, (int) (delay * 20F));

            return;
        }

        int min = this.editor.cameraEditor.clips.loopMin;
        int max = this.editor.cameraEditor.clips.loopMax;
        boolean looping = BBSSettings.editorLoop.get();

        this.end = looping && min != max ? Math.max(min, max) : duration;

        this.editor.setCursor(looping ? Math.min(min, max) : 0);
        this.editor.notifyServer(ActionState.RESTART);

        if (this.resetReplays)
        {
            this.editor.getController().createEntities();
        }

        if (!this.startFfmpegAndPlayback(id, w, h))
        {
            return;
        }

        context.menu.main.setEnabled(false);
        context.menu.main.setVisible(false);
        context.menu.overlay.add(this);
        context.menu.getRoot().add(this.exit);
    }

    /**
     * Spawns replay entities and waits the given number of ticks for assets
     * to finish loading before actually starting the video recorder.
     */
    public void startRecordingAfterLoad(int duration, Texture texture, int warmup)
    {
        int id = texture != null ? texture.id : 0;
        int w = texture != null ? texture.width : 0;
        int h = texture != null ? texture.height : 0;

        this.startRecordingAfterLoad(duration, id, w, h, warmup);
    }

    public void startRecordingAfterLoad(int duration, int id, int w, int h, int warmup)
    {
        if (this.isRunning() || this.getRecorder().isRecording() || duration <= 0 || this.warmupTicks > 0)
        {
            return;
        }

        UIContext context = this.getUIContext();
        boolean looping = BBSSettings.editorLoop.get();
        int min = this.editor.cameraEditor.clips.loopMin;
        int max = this.editor.cameraEditor.clips.loopMax;

        this.end = looping && min != max ? Math.max(min, max) : duration;
        this.warmupTicks = warmup;
        this.pendingId = id;
        this.pendingW = w;
        this.pendingH = h;

        this.editor.setCursor(looping ? Math.min(min, max) : 0);
        this.editor.notifyServer(ActionState.RESTART);

        if (this.resetReplays)
        {
            this.editor.getController().createEntities();
        }

        /* Add to overlay so render() is called and can count down the warmup */
        context.menu.main.setEnabled(false);
        context.menu.main.setVisible(false);
        context.menu.overlay.add(this);
        context.menu.getRoot().add(this.exit);
    }

    private long startTimeMs;

    private boolean startFfmpegAndPlayback(int id, int w, int h)
    {
        VideoRecorder recorder = this.getRecorder();
        UIContext context = this.getUIContext();

        try
        {
            File audioFile = null;
            boolean ambientAudio = BBSSettings.videoSettings.audioEnvironment.get();

            if (BBSSettings.videoSettings.audio.get())
            {
                Clips camera = this.editor.getData().camera;
                List<AudioClip> audioClips = camera.getClips(AudioClip.class);

                String name = StringUtils.createTimestampFilename() + ".wav";
                File file = new File(BBSRendering.getVideoFolder(), name);
                Vector2i range = BBSSettings.editorLoop.get() ? this.editor.getLoopingRange() : new Vector2i();

                if (AudioRenderer.renderAudio(file, audioClips, camera.calculateDuration(), 48000, TimeUtils.toSeconds(range.x), TimeUtils.toSeconds(range.y)))
                {
                    audioFile = file;
                }
            }

            recorder.filmStartTick = this.editor.getCursor();
            recorder.startRecording(audioFile, ambientAudio, id, w, h);
        }
        catch (Exception e)
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR, IKey.constant(e.getMessage())));

            context.menu.main.setEnabled(true);
            context.menu.main.setVisible(true);
            context.render.postRunnable(this::removeFromParent);
            context.render.postRunnable(this.exit::removeFromParent);

            return false;
        }

        this.startTimeMs = System.currentTimeMillis();
        this.editor.togglePlayback();

        return true;
    }

    public void stop()
    {
        UIContext context = this.getUIContext();

        this.warmupTicks = 0;

        context.render.postRunnable(this.exit::removeFromParent);

        if (this.getRecorder().isRecording())
        {
            try
            {
                this.getRecorder().stopRecording();
            }
            catch (Exception e) {}

            if (this.isRunning())
            {
                this.editor.togglePlayback();
            }

            context.menu.main.setEnabled(true);
            context.menu.main.setVisible(true);
            context.render.postRunnable(this::removeFromParent);

            if (this.onStop != null)
            {
                Runnable cb = this.onStop;

                this.onStop = null;
                context.render.postRunnable(cb);
            }
        }
        else
        {
            /* Stopped during warmup — just clean up the overlay */
            context.menu.main.setEnabled(true);
            context.menu.main.setVisible(true);
            context.render.postRunnable(this::removeFromParent);
        }
    }

    @Override
    public void render(UIContext context)
    {
        int sw = context.menu.width;
        int sh = context.menu.height;
        Batcher2D batcher = context.batcher;

        this.area.set(0, 0, sw, sh);
        context.resetTooltip();

        if (this.warmupTicks > 0)
        {
            this.warmupTicks--;

            /* 1. Mine-imator style solid opaque dark background (no transparency) */
            batcher.box(0, 0, sw, sh, Colors.A100 | 0x121214);

            /* 2. Calculate centered preview box matching recorded video aspect ratio */
            Texture texture = BBSRendering.getTexture();
            int tw = texture != null && texture.width > 0 ? texture.width : (this.pendingW > 0 ? this.pendingW : 16);
            int th = texture != null && texture.height > 0 ? texture.height : (this.pendingH > 0 ? this.pendingH : 9);
            float aspect = (float) tw / (float) th;

            int maxW = (int) (sw * 0.55F);
            int maxH = (int) (sh * 0.48F);

            int pw = maxW;
            int ph = (int) (pw / aspect);

            if (ph > maxH)
            {
                ph = maxH;
                pw = (int) (ph * aspect);
            }

            int px = (sw - pw) / 2;
            int py = (sh - ph) / 2 - 10;

            /* 3. Header title & subtitle above preview box */
            String title = UIKeys.FILM_RENDERING_VIDEO.get();
            int titleW = batcher.getFont().getWidth(title);
            batcher.textShadow(title, (sw - titleW) / 2, py - 42, Colors.A100 | BBSSettings.primaryColor.get());

            String subtitle = UIKeys.FILM_WARMUP_SUBTITLE.format(this.warmupTicks / 20F).get();
            int subW = batcher.getFont().getWidth(subtitle);
            batcher.textShadow(subtitle, (sw - subW) / 2, py - 24, Colors.LIGHTER_GRAY);

            /* 4. Preview box border and vertically-flipped live framebuffer texture */
            batcher.box(px - 3, py - 3, px + pw + 3, py + ph + 3, Colors.A100 | 0x26262a);

            if (texture != null && texture.id > 0)
            {
                batcher.texturedBox(texture, Colors.WHITE, px, py, pw, ph, 0, texture.height, texture.width, 0, texture.width, texture.height);
            }
            else
            {
                batcher.box(px, py, px + pw, py + ph, Colors.A100);
            }

            /* 5. Progress bar empty during warmup */
            int barW = (int) (sw * 0.45F);
            int barX = (sw - barW) / 2;
            int barY = py + ph + 42;

            batcher.box(barX, barY, barX + barW, barY + 6, Colors.A100 | 0x26262a);

            /* 6. Centered Cancel Button */
            int btnW = 160;
            int btnH = 20;
            int btnX = (sw - btnW) / 2;
            int btnY = barY + 14;

            this.cancel.area.set(btnX, btnY, btnW, btnH);

            if (this.warmupTicks == 0)
            {
                /* Warmup done — start ffmpeg and begin playback */
                this.startFfmpegAndPlayback(this.pendingId, this.pendingW, this.pendingH);
            }

            return;
        }

        int ticks = this.editor.getCursor();

        if (!this.isRecording())
        {
            return;
        }

        if (!this.isRunning() || ticks >= this.end)
        {
            this.stop();

            return;
        }

        /* 1. Mine-imator style solid opaque dark background (no transparency) */
        batcher.box(0, 0, sw, sh, Colors.A100 | 0x121214);

        /* 2. Calculate centered preview box matching recorded video aspect ratio */
        Texture texture = BBSRendering.getTexture();
        int tw = texture != null && texture.width > 0 ? texture.width : (this.pendingW > 0 ? this.pendingW : 16);
        int th = texture != null && texture.height > 0 ? texture.height : (this.pendingH > 0 ? this.pendingH : 9);
        float aspect = (float) tw / (float) th;

        int maxW = (int) (sw * 0.55F);
        int maxH = (int) (sh * 0.48F);

        int pw = maxW;
        int ph = (int) (pw / aspect);

        if (ph > maxH)
        {
            ph = maxH;
            pw = (int) (ph * aspect);
        }

        int px = (sw - pw) / 2;
        int py = (sh - ph) / 2 - 10;

        /* 3. Header title & subtitle above preview box */
        String title = UIKeys.FILM_RENDERING_VIDEO.get();
        int titleW = batcher.getFont().getWidth(title);
        batcher.textShadow(title, (sw - titleW) / 2, py - 42, Colors.A100 | BBSSettings.primaryColor.get());

        String subtitle = UIKeys.FILM_EXPORTING_SUBTITLE.get();
        int subW = batcher.getFont().getWidth(subtitle);
        batcher.textShadow(subtitle, (sw - subW) / 2, py - 24, Colors.LIGHTER_GRAY);

        /* 4. Preview box border and vertically-flipped live framebuffer texture */
        batcher.box(px - 3, py - 3, px + pw + 3, py + ph + 3, Colors.A100 | 0x26262a);

        if (texture != null && texture.id > 0)
        {
            /* Swap v1 (texture.height) and v2 (0) to orient OpenGL texture right-side up */
            batcher.texturedBox(texture, Colors.WHITE, px, py, pw, ph, 0, texture.height, texture.width, 0, texture.width, texture.height);
        }
        else
        {
            batcher.box(px, py, px + pw, py + ph, Colors.A100);
        }

        /* 5. Calculate progress and remaining time statistics */
        float progress = Math.min(1F, Math.max(0F, (float) ticks / Math.max(1, this.end)));
        int percent = Math.min(100, Math.max(0, (int) (progress * 100F)));
        VideoRecorder videoRecorder = this.getRecorder();
        int currentFrame = videoRecorder != null ? videoRecorder.getCounter() : ticks;
        int totalFrames = (int) (this.end * (BBSRendering.getVideoFrameRate() / 20F));

        long elapsedMs = System.currentTimeMillis() - this.startTimeMs;
        long remainingMs = 0L;

        if (progress > 0.01F && elapsedMs > 100L)
        {
            long estTotalMs = (long) (elapsedMs / progress);

            remainingMs = Math.max(0L, estTotalMs - elapsedMs);
        }

        int remMin = (int) (remainingMs / 60000L);
        int remSec = (int) ((remainingMs % 60000L) / 1000L);
        String remStr = remMin > 0
            ? UIKeys.FILM_REMAINING_MINUTES.format(remMin, remSec).get()
            : UIKeys.FILM_REMAINING_SECONDS.format(remSec).get();

        String frameStr = UIKeys.FILM_FRAME_PROGRESS.format(currentFrame, totalFrames, percent).get();

        /* 6. Render remaining time, frame progress, and progress bar below preview */
        int infoY = py + ph + 14;
        int remW = batcher.getFont().getWidth(remStr);
        batcher.textShadow(remStr, (sw - remW) / 2, infoY, Colors.WHITE);

        int frameW = batcher.getFont().getWidth(frameStr);
        batcher.textShadow(frameStr, (sw - frameW) / 2, infoY + 16, Colors.LIGHTER_GRAY);

        int barY = infoY + 34;
        int barH = 6;
        batcher.box(px, barY, px + pw, barY + barH, Colors.A100 | 0x26262a);
        if (progress > 0F)
        {
            batcher.box(px, barY, px + (int) (pw * progress), barY + barH, Colors.A100 | BBSSettings.primaryColor.get());
        }

        /* 7. Position and render Cancel button */
        int btnW = 160;
        int btnH = 28;
        int btnX = (sw - btnW) / 2;
        int btnY = barY + 18;

        this.cancel.area.set(btnX, btnY, btnW, btnH);

        super.render(context);
    }

    public static class UIExit extends UIElement
    {
        private UIFilmRecorder recorder;

        public UIExit(UIFilmRecorder recorder)
        {
            this.recorder = recorder;
        }

        @Override
        protected boolean subKeyPressed(UIContext context)
        {
            if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
            {
                this.recorder.stop();

                return true;
            }

            return super.subKeyPressed(context);
        }
    }
}
