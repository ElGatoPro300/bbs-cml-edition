package mchorse.bbs_mod.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.ExportChunkSettle;
import mchorse.bbs_mod.ui.utils.UIUtils;

import net.minecraft.client.MinecraftClient;

import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import sun.misc.Unsafe;

public class VideoRecorder
{
    private Process process;
    private WritableByteChannel channel;
    private boolean recording;

    private final BlockingQueue<ByteBuffer> frameQueue = new LinkedBlockingQueue<>();
    private final ConcurrentLinkedQueue<ByteBuffer> bufferPool = new ConcurrentLinkedQueue<>();
    private Thread encodingThread;
    private volatile boolean encodingThreadActive;

    private ByteBuffer buffer;
    private int textureId = -1;
    private int textureWidth;
    private int textureHeight;
    private int counter;

    public int serverTicks;
    public int lastServerTicks;
    public float filmStartTick;

    public boolean isRecording()
    {
        return this.recording;
    }

    /**
     * Current film time in ticks for the frame about to be / just being captured.
     * Matches camera playback: {@code start + frame * 20 / videoFPS}.
     */
    public float getFilmTime()
    {
        int fps = Math.max(1, BBSRendering.getVideoFrameRate());

        return this.filmStartTick + this.counter * (20F / fps);
    }

    public int getTextureId()
    {
        return this.textureId;
    }

    public int getCounter()
    {
        return this.counter;
    }

    private int[] pbos;
    private int pboIndex;
    private File filmAudioFile;
    private File ambientAudioFile;
    private Path exportFolder;
    private String movieName;
    private long exportStartTime;
    private boolean recordAmbientAudio;
    private AmbientAudioCapture ambientCapture;
    private boolean suppressFilmClipPlaybackForRender;

    /**
     * Whether HQ export is waiting for terrain/commands to settle before capturing.
     */
    private boolean settling;
    /** Number of consecutive frames terrain was idle; must reach required count. */
    private int settleStableFrames;
    /** After settle finishes, run a few ticks of item physics + dig fx before capture. */
    private int settleBloomFramesRemaining;
    private int settleMinFramesRemaining;
    private int settleRequiredStable;
    private boolean settleParticlesFrozen;
    private boolean captureAfterSettle;
    private long settleDeadlineMs;
    private long settleHardDeadlineMs;

    public boolean isHighQualityRender()
    {
        return BBSSettings.videoSettings != null
            && BBSSettings.videoSettings.highQualityRender != null
            && BBSSettings.videoSettings.highQualityRender.get();
    }

    public int getEffectiveHeldFrames()
    {
        int configured = BBSSettings.videoSettings.heldFrames.get();

        return this.isHighQualityRender() ? Math.max(3, configured) : configured;
    }

    public boolean isSettling()
    {
        return this.settling;
    }

    /**
     * World-tick catch-up while film is frozen. Fast during chunk wait, normal during bloom.
     */
    public int getSettleCatchUpTicks()
    {
        if (!this.settling)
        {
            return 1;
        }

        return this.settleParticlesFrozen ? 4 : 1;
    }

    /**
     * True while HQ is waiting on chunks — particles and item drops must not age/fall.
     * False during post-terrain bloom so drops fall and dig FX spray like vanilla.
     */
    public boolean areExportParticlesFrozen()
    {
        return this.settling && this.settleParticlesFrozen;
    }

    public void syncWorldFxFreeze()
    {
        /* Chunk wait: freeze drops + particles. Bloom: both run like vanilla (fall / spray). */
        boolean frozen = this.settling && this.settleParticlesFrozen;

        ExportWorldFxFreeze.setParticlesFrozen(frozen);
        ExportWorldFxFreeze.setItemPhysicsFrozen(frozen);
    }

    /**
     * After commands/actions fire, hold film time until terrain meshes are idle
     * for {@code highQualitySettleTicks} consecutive frames (or timeout), then
     * briefly unfreeze particles so dig/emit effects can spread before capture.
     */
    public void beginSettle()
    {
        if (!this.isHighQualityRender())
        {
            return;
        }

        int stable = Math.max(1, BBSSettings.videoSettings.highQualitySettleTicks.get());
        /*
         * Always burn world/client ticks first so block packets from large /fills
         * arrive before we trust an idle chunk builder (false-idle caused later commands).
         */
        int minWork = Math.max(24, Math.min(160, stable * 5));

        this.settling = true;
        this.settleRequiredStable = stable;
        this.settleStableFrames = 0;
        this.settleMinFramesRemaining = minWork;
        this.settleBloomFramesRemaining = 0;
        this.settleParticlesFrozen = true;
        this.syncWorldFxFreeze();
        /* Prefer waiting for real terrain idle; hard cap only avoids infinite hangs. */
        long now = System.currentTimeMillis();
        long timeoutMs = Math.max(300000L, stable * 30000L);

        this.settleDeadlineMs = now + timeoutMs;
        this.settleHardDeadlineMs = now + timeoutMs + 180000L;
        this.captureAfterSettle = true;
        BBSMod.getActions().setFreezeActions(true);
        BBSMod.getActions().setExportSyncOnly(true);
    }

    /**
     * @return {@code true} when settle finished and this frame should be captured
     */
    public boolean tickSettle()
    {
        if (!this.settling)
        {
            return false;
        }

        ExportChunkSettle.pumpUploads();

        /* Bloom: terrain ready — unfreeze items + particles so drops fall like vanilla. */
        if (this.settleBloomFramesRemaining > 0)
        {
            this.settleParticlesFrozen = false;
            this.syncWorldFxFreeze();
            this.settleBloomFramesRemaining -= 1;

            if (this.settleBloomFramesRemaining <= 0)
            {
                this.settling = false;
                this.settleStableFrames = 0;
                this.settleMinFramesRemaining = 0;
                this.settleParticlesFrozen = false;
                this.syncWorldFxFreeze();
                BBSMod.getActions().setFreezeActions(false);

                boolean capture = this.captureAfterSettle;

                this.captureAfterSettle = false;

                return capture;
            }

            return false;
        }

        if (this.settleMinFramesRemaining > 0)
        {
            this.settleMinFramesRemaining -= 1;
        }

        long now = System.currentTimeMillis();
        boolean softTimedOut = now >= this.settleDeadlineMs;
        boolean hardTimedOut = now >= this.settleHardDeadlineMs;
        boolean terrainIdle = ExportChunkSettle.isTerrainSettled();

        if (this.settleMinFramesRemaining <= 0 && terrainIdle)
        {
            this.settleStableFrames += 1;
        }
        else
        {
            this.settleStableFrames = 0;
        }

        boolean stableEnough = this.settleStableFrames >= this.settleRequiredStable;
        /* Soft timeout: only finish if terrain is at least idle; hard timeout always ends. */
        boolean softTimeoutOk = softTimedOut && terrainIdle && this.settleMinFramesRemaining <= 0;

        if (stableEnough || softTimeoutOk || hardTimedOut)
        {
            /*
             * After chunks are ready, let item gravity + dig particles run for a few ticks
             * like a normal break before capturing.
             */
            int bloom = Math.max(8, Math.min(16, this.settleRequiredStable * 2));

            this.settleBloomFramesRemaining = bloom;
            this.settleParticlesFrozen = false;
            this.settleStableFrames = 0;
            this.settleMinFramesRemaining = 0;
            this.syncWorldFxFreeze();

            return false;
        }

        return false;
    }

    public void clearSettle()
    {
        this.settling = false;
        this.settleStableFrames = 0;
        this.settleMinFramesRemaining = 0;
        this.settleRequiredStable = 0;
        this.settleBloomFramesRemaining = 0;
        this.settleParticlesFrozen = false;
        this.captureAfterSettle = false;
        this.settleDeadlineMs = 0L;
        this.settleHardDeadlineMs = 0L;
        this.syncWorldFxFreeze();

        try
        {
            BBSMod.getActions().setFreezeActions(false);
        }
        catch (Exception e)
        {}
    }

    public void enableExportSyncOnly()
    {
        try
        {
            BBSMod.getActions().setExportSyncOnly(true);
        }
        catch (Exception e)
        {}
    }

    public void disableExportSyncOnly()
    {
        try
        {
            BBSMod.getActions().setExportSyncOnly(false);
        }
        catch (Exception e)
        {}
    }

    /**
     * Start recording the video using ffmpeg
     */
    public void startRecording(File audioFile, boolean ambientAudio, int textureId, int width, int height)
    {
        if (this.recording)
        {
            return;
        }

        this.counter = 0;
        this.filmAudioFile = audioFile;
        this.ambientAudioFile = null;
        this.movieName = StringUtils.createTimestampFilename();
        this.recordAmbientAudio = ambientAudio;
        this.suppressFilmClipPlaybackForRender = BBSSettings.editorMuteRenderAudioClips != null && BBSSettings.editorMuteRenderAudioClips.get();
        this.exportStartTime = System.currentTimeMillis();
        this.textureId = textureId;
        this.textureWidth = width;
        this.textureHeight = height;

        if (this.isHighQualityRender())
        {
            this.enableExportSyncOnly();
        }

        LoopbackAudioController.suppressFilmClipPlayback(this.suppressFilmClipPlaybackForRender);

        int size = width * height * 3;

        if (this.buffer == null)
        {
            this.buffer = MemoryUtil.memAlloc(size);
        }

        try
        {
            File movies = BBSRendering.getVideoFolder();

            movies.mkdirs();

            Path path = Paths.get(movies.toString());
            this.exportFolder = path;
            String params = this.filmAudioFile != null && !this.recordAmbientAudio
                ? BBSSettings.videoSettings.argumentsAudio.get()
                : BBSSettings.videoSettings.arguments.get();
            StringBuilder filters = new StringBuilder("vflip");
            float frameRate = (float) BBSRendering.getVideoFrameRate();

            if (this.recordAmbientAudio)
            {
                this.enableAmbientCapture((int) Math.max(1, frameRate));
            }

            int motionBlur = BBSRendering.getMotionBlur();

            for (int i = 0; i < motionBlur; i++)
            {
                filters.append(",tblend=all_mode=average,framestep=2");
            }

            params = params.replace("%WIDTH%", String.valueOf(width));
            params = params.replace("%HEIGHT%", String.valueOf(height));
            params = params.replace("%FPS%", String.valueOf(frameRate));
            params = params.replace("%NAME%", this.movieName);
            params = params.replace("%FILTERS%", filters.toString());

            if (this.filmAudioFile != null)
            {
                params = params.replace("%AUDIO_TRACK%", "\"" + this.filmAudioFile.getAbsolutePath() + "\"");
            }

            List<String> args = new ArrayList<>();
            String encoder = FFMpegUtils.getFFMPEG();

            args.add(encoder);
            args.addAll(Arrays.asList(params.split(" ")));

            System.out.println("Recording video with following arguments: " + args);

            this.pbos = new int[3];
            this.pboIndex = 0;

            for (int i = 0; i < 3; i++)
            {
                this.pbos[i] = GL30.glGenBuffers();

                GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, this.pbos[i]);
                GL30.glBufferData(GL30.GL_PIXEL_PACK_BUFFER, size, GL30.GL_STREAM_READ);
            }

            GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, 0);

            ProcessBuilder builder = new ProcessBuilder(args);
            File log = path.resolve(this.movieName.concat(".log")).toFile();

            if (!BBSSettings.videoEncoderLog.get())
            {
                log = BBSMod.getSettingsPath("video.log");
            }

            builder.directory(path.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(log);

            this.process = builder.start();

            /**
             * Java wraps the process output stream into a BufferedOutputStream,
             *
             * but its little buffer is just slowing everything down with the
             * huge amount of data we're dealing here, so unwrap it with this little
             * hack.
             */
            OutputStream os = this.process.getOutputStream();
            Unsafe unsafe = UnsafeUtils.getUnsafe();

            if (os instanceof FilterOutputStream)
            {
                try
                {
                    Field outField = FilterOutputStream.class.getDeclaredField("out");

                    os = (OutputStream) unsafe.getObject(os, unsafe.objectFieldOffset(outField));
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }

            this.channel = Channels.newChannel(os);
            this.recording = true;

            this.frameQueue.clear();
            this.bufferPool.clear();
            this.encodingThreadActive = true;
            this.encodingThread = new Thread(this::runEncodingLoop, "BBS Video Encoder Worker");
            this.encodingThread.start();

            UIUtils.playClick(2F);
        }
        catch (Exception e)
        {
            this.disableAmbientCapture();
            LoopbackAudioController.suppressFilmClipPlayback(false);
            this.suppressFilmClipPlaybackForRender = false;
            e.printStackTrace();
        }

        this.serverTicks = this.lastServerTicks = 0;
        this.clearSettle();
    }

    private void enableAmbientCapture(int frameRate) throws IOException
    {
        MinecraftClient.getInstance().getSoundManager().stopAll();
        BBSModClient.getSounds().deleteSounds();
        LoopbackAudioController.suppressFilmClipPlayback(this.suppressFilmClipPlaybackForRender || this.filmAudioFile != null);
        LoopbackAudioController.requestCapture(true);
        MinecraftClient.getInstance().getSoundManager().reloadSounds();
        MinecraftClient.getInstance().getSoundManager().stopAll();
        this.ambientCapture = AmbientAudioCapture.open(this.exportFolder, this.movieName, frameRate);
    }

    private void disableAmbientCapture()
    {
        boolean hadCapture = this.recordAmbientAudio || this.ambientCapture != null || LoopbackAudioController.isCaptureRequested();

        try
        {
            if (this.ambientCapture != null)
            {
                this.ambientCapture.close();
                this.ambientAudioFile = this.ambientCapture.getFile();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            this.ambientCapture = null;
            LoopbackAudioController.suppressFilmClipPlayback(this.suppressFilmClipPlaybackForRender);
            LoopbackAudioController.requestCapture(false);
            LoopbackAudioController.setLoopbackDevice(0L);

            if (hadCapture)
            {
                MinecraftClient.getInstance().getSoundManager().stopAll();
                MinecraftClient.getInstance().getSoundManager().reloadSounds();
                MinecraftClient.getInstance().getSoundManager().stopAll();
                BBSModClient.getSounds().deleteSounds();
            }
        }
    }

    private File findOutputVideo()
    {
        if (this.exportFolder == null)
        {
            return null;
        }

        String[] extensions = new String[] {"mp4", "mkv", "mov", "webm", "avi"};

        for (String extension : extensions)
        {
            File candidate = this.exportFolder.resolve(this.movieName + "." + extension).toFile();

            if (candidate.isFile())
            {
                return candidate;
            }
        }

        try
        {
            return Files.list(this.exportFolder)
                .map(Path::toFile)
                .filter(File::isFile)
                .filter((f) -> f.lastModified() >= this.exportStartTime)
                .filter((f) ->
                {
                    String name = f.getName().toLowerCase(Locale.ROOT);

                    return name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".avi");
                })
                .max(Comparator.comparingLong(File::lastModified))
                .orElse(null);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    private void mergeAudioTrack(File inputVideo, File inputAudio)
    {
        if (inputVideo == null || inputAudio == null || !inputVideo.isFile() || !inputAudio.isFile())
        {
            return;
        }

        String name = inputVideo.getName();
        int dot = name.lastIndexOf('.');
        String extension = dot == -1 ? "mp4" : name.substring(dot + 1);
        String base = dot == -1 ? name : name.substring(0, dot);
        File tempOutput = new File(inputVideo.getParentFile(), base + "_ambient." + extension);
        List<String> args = new ArrayList<>();

        args.add(FFMpegUtils.getFFMPEG());
        args.add("-y");
        args.add("-i");
        args.add(inputVideo.getAbsolutePath());
        args.add("-i");
        args.add(inputAudio.getAbsolutePath());
        args.add("-c:v");
        args.add("copy");
        args.add("-c:a");
        args.add("aac");
        args.add("-b:a");
        args.add("192k");
        args.add("-shortest");
        args.add(tempOutput.getAbsolutePath());

        ProcessBuilder builder = new ProcessBuilder(args);
        builder.directory(inputVideo.getParentFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(BBSMod.getSettingsPath("video_audio_merge.log"));

        try
        {
            Process process = builder.start();

            if (process.waitFor(5, TimeUnit.MINUTES) && process.exitValue() == 0 && tempOutput.isFile())
            {
                File backup = new File(inputVideo.getParentFile(), base + "_noaudio." + extension);

                if (backup.exists())
                {
                    backup.delete();
                }

                if (inputVideo.renameTo(backup))
                {
                    if (!tempOutput.renameTo(inputVideo))
                    {
                        backup.renameTo(inputVideo);
                    }
                    else
                    {
                        backup.delete();
                    }
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private File mixAudioTracks(File first, File second)
    {
        if (first == null || !first.isFile())
        {
            return second;
        }

        if (second == null || !second.isFile())
        {
            return first;
        }

        File mixed = this.exportFolder.resolve(this.movieName + "_mix.wav").toFile();
        List<String> args = new ArrayList<>();

        args.add(FFMpegUtils.getFFMPEG());
        args.add("-y");
        args.add("-i");
        args.add(first.getAbsolutePath());
        args.add("-i");
        args.add(second.getAbsolutePath());
        args.add("-filter_complex");
        args.add("amix=inputs=2:duration=longest");
        args.add("-c:a");
        args.add("pcm_s16le");
        args.add(mixed.getAbsolutePath());

        ProcessBuilder builder = new ProcessBuilder(args);
        builder.directory(this.exportFolder.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(BBSMod.getSettingsPath("video_audio_mix.log"));

        try
        {
            Process process = builder.start();

            if (process.waitFor(2, TimeUnit.MINUTES) && process.exitValue() == 0 && mixed.isFile())
            {
                return mixed;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return second;
    }

    private void cleanupTemporaryAudioFiles(File filmAudio, File ambientAudio, String movieName, Path exportFolder)
    {
        this.deleteIfExists(filmAudio);
        this.deleteIfExists(ambientAudio);

        if (movieName == null || exportFolder == null)
        {
            return;
        }

        this.deleteIfExists(exportFolder.resolve(movieName + "_mix.wav").toFile());
        this.deleteIfExists(exportFolder.resolve(movieName + "_ambient.wav").toFile());
    }

    private void deleteIfExists(File file)
    {
        if (file != null && file.isFile())
        {
            file.delete();
        }
    }

    private void runEncodingLoop()
    {
        while (this.encodingThreadActive || !this.frameQueue.isEmpty())
        {
            try
            {
                ByteBuffer buf = this.frameQueue.poll(10, TimeUnit.MILLISECONDS);

                if (buf != null)
                {
                    if (this.channel != null && this.channel.isOpen())
                    {
                        this.channel.write(buf);
                    }

                    this.bufferPool.offer(buf);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Stop recording
     */
    public void stopRecording()
    {
        if (!this.recording)
        {
            return;
        }

        this.flushPendingPboFrames();

        this.encodingThreadActive = false;

        if (this.encodingThread != null)
        {
            try
            {
                this.encodingThread.join(10000);
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }

            this.encodingThread = null;
        }

        if (this.pbos != null)
        {
            for (int pbo : this.pbos)
            {
                GL30.glDeleteBuffers(pbo);
            }
        }

        this.pbos = null;
        this.textureId = -1;

        if (this.buffer != null)
        {
            MemoryUtil.memFree(this.buffer);

            this.buffer = null;
        }

        for (ByteBuffer buf : this.bufferPool)
        {
            if (buf != null)
            {
                MemoryUtil.memFree(buf);
            }
        }
        this.bufferPool.clear();

        for (ByteBuffer buf : this.frameQueue)
        {
            if (buf != null)
            {
                MemoryUtil.memFree(buf);
            }
        }
        this.frameQueue.clear();

        try
        {
            if (this.channel != null && this.channel.isOpen())
            {
                this.channel.close();
            }

            this.channel = null;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
        }

        try
        {
            if (this.process != null)
            {
                this.process.waitFor(1, TimeUnit.MINUTES);
                this.process.destroy();
            }

            this.process = null;
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
        }

        if (this.recordAmbientAudio)
        {
            this.disableAmbientCapture();
            File mixed = this.mixAudioTracks(this.filmAudioFile, this.ambientAudioFile);

            this.mergeAudioTrack(this.findOutputVideo(), mixed);
        }

        if (!BBSSettings.videoSettings.audioSeparateFile.get())
        {
            this.cleanupTemporaryAudioFiles(this.filmAudioFile, this.ambientAudioFile, this.movieName, this.exportFolder);
        }

        this.recording = false;
        this.filmAudioFile = null;
        this.movieName = null;
        this.exportFolder = null;
        this.recordAmbientAudio = false;
        this.suppressFilmClipPlaybackForRender = false;
        LoopbackAudioController.suppressFilmClipPlayback(false);

        UIUtils.playClick(0.5F);

        this.serverTicks = this.lastServerTicks = 0;
        this.clearSettle();
        this.disableExportSyncOnly();
    }

    /**
     * Encode PBO frames already submitted but not yet mapped (pipeline delay of
     * {@code pbos.length - 1}). Must run on the render thread before deleting PBOs.
     */
    private void flushPendingPboFrames()
    {
        if (this.pbos == null || this.counter < this.pbos.length - 1)
        {
            return;
        }

        int pending = this.pbos.length - 1;

        for (int i = 0; i < pending; i++)
        {
            int readPbo = (this.pboIndex + 1) % this.pbos.length;

            GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, this.pbos[readPbo]);

            ByteBuffer mappedBuffer = GL30.glMapBuffer(GL30.GL_PIXEL_PACK_BUFFER, GL30.GL_READ_ONLY);

            if (mappedBuffer != null)
            {
                int size = this.textureWidth * this.textureHeight * 3;
                ByteBuffer buf = this.bufferPool.poll();

                if (buf == null || buf.capacity() < size)
                {
                    buf = MemoryUtil.memAlloc(size);
                }

                buf.clear();
                buf.put(mappedBuffer);
                buf.flip();

                this.frameQueue.offer(buf);
            }

            GL30.glUnmapBuffer(GL30.GL_PIXEL_PACK_BUFFER);
            this.pboIndex = readPbo;
        }

        GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, 0);
    }

    /**
     * Record a frame
     */
    public void recordFrame()
    {
        if (!this.recording)
        {
            return;
        }

        try
        {
            /* Async PBO ring: write the current texture into pbos[pbo], then map
             * pbos[next] which was filled (N - 1) frames ago. Priming frames must
             * not be encoded — with 3 PBOs that is the first two calls; encoding
             * an unwritten buffer is what produced a solid black first video frame. */
            int pbo = this.pboIndex;
            int nextPbo = (this.pboIndex + 1) % this.pbos.length;
            int pipelineDelay = this.pbos.length - 1;

            GL30.glPixelStorei(GL30.GL_PACK_ALIGNMENT, 1);
            GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, this.pbos[pbo]);
            GL30.glBindTexture(GL30.GL_TEXTURE_2D, this.textureId);
            GL30.glGetTexImage(GL30.GL_TEXTURE_2D, 0, GL30.GL_BGR, GL30.GL_UNSIGNED_BYTE, 0);

            GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, this.pbos[nextPbo]);

            ByteBuffer mappedBuffer = GL30.glMapBuffer(GL30.GL_PIXEL_PACK_BUFFER, GL30.GL_READ_ONLY);

            if (mappedBuffer != null && this.counter >= pipelineDelay)
            {
                int size = this.textureWidth * this.textureHeight * 3;
                ByteBuffer buf = this.bufferPool.poll();

                if (buf == null || buf.capacity() < size)
                {
                    buf = MemoryUtil.memAlloc(size);
                }

                buf.clear();
                buf.put(mappedBuffer);
                buf.flip();

                this.frameQueue.offer(buf);
            }

            GL30.glUnmapBuffer(GL30.GL_PIXEL_PACK_BUFFER);
            GL30.glBindBuffer(GL30.GL_PIXEL_PACK_BUFFER, 0);

            this.pboIndex = nextPbo;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        if (this.recordAmbientAudio && this.ambientCapture != null)
        {
            this.ambientCapture.captureFrame();
        }

        this.counter += 1;
    }

    /**
     * Toggle recording of the video
     */
    public void toggleRecording(int textureId, int textureWidth, int textureHeight)
    {
        if (this.recording)
        {
            this.stopRecording();
        }
        else
        {
            this.startRecording(null, false, textureId, textureWidth, textureHeight);
        }

        UIUtils.playClick();
    }
}
