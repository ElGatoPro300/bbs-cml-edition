package mchorse.bbs_mod.client.video;

import mchorse.bbs_mod.forms.forms.utils.VideoResolution;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.utils.FFMpegUtils;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dedicated VideoForm decode engine: ffmpeg on a background thread, GL upload on render thread.
 * Never blocks the game loop on decode. Always keeps the last good texture (no cyan blanks).
 * Decode resolution is hard-capped for FPS ({@link VideoResolution#effectiveDecodeLongSide}).
 */
public final class VideoFormEngine
{
    private static final int MAX_DIM = 8192;
    private static final long IDLE_EVICT_MS = 60000L;
    private static final Pattern SIZE_PATTERN = Pattern.compile("(?<!\\w)(\\d{2,5})\\s*[xX]\\s*(\\d{2,5})(?!\\w)");
    private static final Pattern FPS_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*fps");
    private static final Pattern DURATION_PATTERN = Pattern.compile("Duration:\\s*(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");

    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(2, new ThreadFactory()
    {
        private final AtomicLong n = new AtomicLong();

        @Override
        public Thread newThread(Runnable r)
        {
            Thread t = new Thread(r, "bbs-video-form-" + this.n.incrementAndGet());

            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);

            return t;
        }
    });

    private VideoFormEngine()
    {}

    /**
     * Render-thread entry: advance target time, upload any ready CPU frame, return GL texture.
     */
    public static Frame bindFrame(String path, long tickPosition, float speed, boolean loop, int resolutionPreset)
    {
        return bindFrame(path, tickPosition, speed, loop, resolutionPreset, true);
    }

    public static Frame bindFrame(String path, long tickPosition, float speed, boolean loop, int resolutionPreset, boolean playing)
    {
        File file = VideoFormPlayback.resolveFile(path);

        if (file == null)
        {
            return null;
        }

        int decodeSide = VideoResolution.effectiveDecodeLongSide(resolutionPreset);
        /* One session per file — resolution keyframes reconfigure decode size in place.
         * Keying by decodeSide spawned parallel ffmpeg+setSize races that corrupted the atlas. */
        String key = file.getAbsolutePath();
        Session session = SESSIONS.computeIfAbsent(key, (k) -> new Session(file, decodeSide));

        session.lastUsedMs = System.currentTimeMillis();
        session.ensureMaxLongSide(decodeSide);

        if (playing)
        {
            session.setPaused(false);
            session.request(tickPosition, speed, loop);
        }
        else
        {
            /* Game pause / still: freeze target and stop the worker from advancing. */
            session.setPaused(true);
            session.request(tickPosition, speed, loop);
        }

        session.uploadIfReady();

        if (session.textureAllocated && session.texture.isValid())
        {
            return new Frame(session.texture.id, session.width, session.height);
        }

        return null;
    }

    /**
     * Editor / item icon: ensure at least the first frame exists (paused stream).
     */
    public static Frame bindStill(String path, long tickPosition, int resolutionPreset)
    {
        return bindFrame(path, tickPosition, 1F, false, resolutionPreset, false);
    }

    public static void tickCleanup()
    {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Session>> it = SESSIONS.entrySet().iterator();

        while (it.hasNext())
        {
            Map.Entry<String, Session> entry = it.next();
            Session session = entry.getValue();

            if (now - session.lastUsedMs < IDLE_EVICT_MS)
            {
                continue;
            }

            session.close();
            it.remove();
        }
    }

    public static void releaseAll()
    {
        for (Session session : SESSIONS.values())
        {
            try
            {
                session.close();
            }
            catch (Throwable t)
            {}
        }

        SESSIONS.clear();
    }

    public static final class Frame
    {
        public final int textureId;
        public final int width;
        public final int height;

        public Frame(int textureId, int width, int height)
        {
            this.textureId = textureId;
            this.width = width;
            this.height = height;
        }
    }

    private static final class Session
    {
        private final File file;
        private final Texture texture = new Texture();
        private final Object uploadLock = new Object();
        private final AtomicBoolean workerRunning = new AtomicBoolean(false);
        private final AtomicBoolean paused = new AtomicBoolean(false);
        private final AtomicLong requestedFrame = new AtomicLong(0L);
        private final AtomicBoolean requestedLoop = new AtomicBoolean(true);

        private volatile int maxLongSide;
        private int nativeWidth = 16;
        private int nativeHeight = 9;
        private int width = 16;
        private int height = 9;
        private float fps = 24F;
        private double durationSec = 0D;
        private boolean probed;
        private boolean probeFailed;
        private boolean textureAllocated;
        private long lastUsedMs = System.currentTimeMillis();

        private Process process;
        private InputStream stdout;
        private long streamFrame = -1L;
        private boolean streamAlive;

        private ByteBuffer pendingPixels;
        private int pendingBytes;
        private int pendingWidth;
        private int pendingHeight;
        private volatile boolean pendingReady;
        private ByteBuffer readScratch;

        private Session(File file, int maxLongSide)
        {
            this.file = file;
            this.maxLongSide = maxLongSide > 0 ? maxLongSide : VideoResolution.P720;
            this.texture.setFormat(TextureFormat.RGBA_U8);
            this.texture.setFilter(GL11.GL_LINEAR);
            this.texture.setWrap(GL13.GL_CLAMP_TO_EDGE);
            this.texture.setSize(16, 9);
        }

        /**
         * Apply a new decode long-side cap (resolution keyframe). Restarts the stream so
         * ffmpeg scale and GL texture size stay matched — never TexSubImage into a wrong size.
         */
        private void ensureMaxLongSide(int decodeSide)
        {
            int side = decodeSide > 0 ? decodeSide : VideoResolution.P720;

            if (this.maxLongSide == side)
            {
                return;
            }

            synchronized (this.uploadLock)
            {
                this.maxLongSide = side;
                this.pendingReady = false;
                this.textureAllocated = false;

                if (this.probed && !this.probeFailed)
                {
                    this.applyDecodeSizeFromNative();
                    this.reallocDecodeBuffers();
                }
            }

            this.closeProcess();
            this.streamFrame = -1L;
            this.streamAlive = false;
        }

        private void applyDecodeSizeFromNative()
        {
            this.width = clampDim(this.nativeWidth);
            this.height = clampDim(this.nativeHeight);

            int longSide = Math.max(this.width, this.height);

            if (longSide > this.maxLongSide)
            {
                float scale = this.maxLongSide / (float) longSide;

                this.width = clampDim(Math.round(this.width * scale) & ~1);
                this.height = clampDim(Math.round(this.height * scale) & ~1);
            }

            long bytes = (long) this.width * (long) this.height * 4L;

            if (this.width < 2 || this.height < 2 || bytes <= 0L || bytes > Integer.MAX_VALUE)
            {
                this.probeFailed = true;

                return;
            }

            this.pendingBytes = (int) bytes;
        }

        private void reallocDecodeBuffers()
        {
            if (this.readScratch != null)
            {
                MemoryUtil.memFree(this.readScratch);
                this.readScratch = null;
            }

            if (this.pendingPixels != null)
            {
                MemoryUtil.memFree(this.pendingPixels);
                this.pendingPixels = null;
            }

            if (this.pendingBytes > 0 && !this.probeFailed)
            {
                this.readScratch = MemoryUtil.memAlloc(this.pendingBytes);
            }
        }

        private void setPaused(boolean value)
        {
            boolean wasPaused = this.paused.getAndSet(value);

            if (value && !wasPaused)
            {
                synchronized (this.uploadLock)
                {
                    /* Drop in-flight frames once on pause enter so play does not jitter forward. */
                    this.pendingReady = false;
                }
            }
        }

        private void request(long tickPosition, float speed, boolean loop)
        {
            long target = 0L;

            if (this.probed && !this.probeFailed)
            {
                target = this.computeTargetFrame(tickPosition, speed, loop);
            }
            else if (!this.probed)
            {
                /* Rough target until probe finishes on the worker. */
                target = Math.max(0L, (long) ((tickPosition / 20.0D) * Math.max(0.01F, speed) * 24F));
            }
            else
            {
                return;
            }

            this.requestedLoop.set(loop);
            this.requestedFrame.set(target);

            if (this.workerRunning.compareAndSet(false, true))
            {
                WORKERS.execute(this::workerLoop);
            }
        }

        private void workerLoop()
        {
            try
            {
                this.ensureProbed();

                if (this.probeFailed)
                {
                    return;
                }

                while (System.currentTimeMillis() - this.lastUsedMs < IDLE_EVICT_MS)
                {
                    long target = this.requestedFrame.get();
                    boolean loop = this.requestedLoop.get();
                    boolean paused = this.paused.get();

                    /* Paused still seeks to the scrubbed frame; only auto-advance is frozen. */
                    if (!this.streamAlive || this.streamFrame > target || target > this.streamFrame + 120L)
                    {
                        this.restartStream(Math.max(0L, target));
                    }

                    if (!this.streamAlive)
                    {
                        try
                        {
                            Thread.sleep(30L);
                        }
                        catch (InterruptedException ignored)
                        {
                            break;
                        }

                        continue;
                    }

                    while (this.streamAlive && this.streamFrame < target)
                    {
                        if (!this.readFrameToPending())
                        {
                            this.streamAlive = false;

                            if (loop && this.durationSec > 0.05D)
                            {
                                this.restartStream(0L);
                            }

                            break;
                        }

                        if (this.streamFrame >= target)
                        {
                            break;
                        }
                    }

                    try
                    {
                        Thread.sleep(paused ? 40L : 8L);
                    }
                    catch (InterruptedException ignored)
                    {
                        break;
                    }
                }
            }
            finally
            {
                this.closeProcess();
                this.workerRunning.set(false);
            }
        }

        private void uploadIfReady()
        {
            if (!this.pendingReady)
            {
                return;
            }

            synchronized (this.uploadLock)
            {
                if (!this.pendingReady || this.pendingPixels == null)
                {
                    return;
                }

                int uploadW = this.pendingWidth > 0 ? this.pendingWidth : this.width;
                int uploadH = this.pendingHeight > 0 ? this.pendingHeight : this.height;
                int expectedBytes = uploadW * uploadH * 4;

                if (uploadW < 2 || uploadH < 2 || this.pendingPixels.remaining() < expectedBytes)
                {
                    this.pendingReady = false;

                    return;
                }

                /* Resize when first frame OR after resolution keyframe changed decode size. */
                if (!this.textureAllocated || this.texture.width != uploadW || this.texture.height != uploadH)
                {
                    this.texture.setSize(uploadW, uploadH);
                    this.textureAllocated = true;
                }

                this.width = uploadW;
                this.height = uploadH;
                this.pendingPixels.position(0);
                this.pendingPixels.limit(expectedBytes);
                this.texture.bind();

                try
                {
                    /* Tightly packed RGBA — do NOT touch UNPACK_ROW_LENGTH. Setting it and
                     * failing to restore blacks out the block atlas for the rest of the session. */
                    GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                    GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                    GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, uploadW, uploadH, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, this.pendingPixels);
                }
                finally
                {
                    GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                    this.texture.unbind();
                }

                this.pendingReady = false;
            }
        }

        private boolean readFrameToPending()
        {
            if (this.stdout == null || this.readScratch == null)
            {
                return false;
            }

            try
            {
                this.readScratch.clear();
                int remaining = this.pendingBytes;
                byte[] chunk = new byte[65536];

                while (remaining > 0)
                {
                    int read = this.stdout.read(chunk, 0, Math.min(chunk.length, remaining));

                    if (read < 0)
                    {
                        return false;
                    }

                    this.readScratch.put(chunk, 0, read);
                    remaining -= read;
                }

                this.readScratch.flip();
                this.streamFrame++;

                synchronized (this.uploadLock)
                {
                    if (this.pendingPixels == null || this.pendingPixels.capacity() < this.pendingBytes)
                    {
                        if (this.pendingPixels != null)
                        {
                            MemoryUtil.memFree(this.pendingPixels);
                        }

                        this.pendingPixels = MemoryUtil.memAlloc(this.pendingBytes);
                    }

                    this.pendingPixels.clear();
                    this.pendingPixels.put(this.readScratch);
                    this.pendingPixels.flip();
                    this.pendingWidth = this.width;
                    this.pendingHeight = this.height;
                    this.pendingReady = true;
                }

                return true;
            }
            catch (Exception e)
            {
                return false;
            }
        }

        private void restartStream(long startFrame)
        {
            this.closeProcess();

            String ffmpeg = FFMpegUtils.getFFMPEG();

            if (ffmpeg == null || ffmpeg.isEmpty() || this.width < 2 || this.height < 2)
            {
                return;
            }

            try
            {
                ProcessBuilder builder = new ProcessBuilder(
                    ffmpeg,
                    "-hide_banner",
                    "-loglevel", "error",
                    "-ss", String.format(Locale.ROOT, "%.3f", Math.max(0D, startFrame / (double) Math.max(1F, this.fps))),
                    "-i", this.file.getAbsolutePath(),
                    "-an",
                    "-vf", "scale=" + this.width + ":" + this.height + ":flags=fast_bilinear",
                    "-r", String.format(Locale.ROOT, "%.3f", Math.min(30F, Math.max(12F, this.fps))),
                    "-f", "rawvideo",
                    "-pix_fmt", "rgba",
                    "-"
                );
                builder.redirectError(ProcessBuilder.Redirect.DISCARD);
                this.process = builder.start();
                this.stdout = new BufferedInputStream(this.process.getInputStream(), Math.min(this.pendingBytes * 2, 1 << 20));
                this.streamFrame = startFrame - 1L;
                this.streamAlive = true;
            }
            catch (Exception e)
            {
                e.printStackTrace();
                this.streamAlive = false;
            }
        }

        private void closeProcess()
        {
            this.streamAlive = false;

            if (this.stdout != null)
            {
                try
                {
                    this.stdout.close();
                }
                catch (Exception ignored)
                {}

                this.stdout = null;
            }

            if (this.process != null)
            {
                this.process.destroyForcibly();

                try
                {
                    this.process.waitFor();
                }
                catch (Exception ignored)
                {}

                this.process = null;
            }
        }

        private long computeTargetFrame(long tickPosition, float speed, boolean loop)
        {
            double timeSec = Math.max(0D, tickPosition / 20.0D) * Math.max(0.01F, speed);
            long targetFrame = (long) Math.floor(timeSec * this.fps);

            if (this.durationSec > 0.05D)
            {
                long totalFrames = Math.max(1L, Math.round(this.durationSec * this.fps));

                if (loop)
                {
                    targetFrame = Math.floorMod(targetFrame, totalFrames);
                }
                else
                {
                    targetFrame = Math.min(targetFrame, totalFrames - 1L);
                }
            }

            return Math.max(0L, targetFrame);
        }

        private void ensureProbed()
        {
            if (this.probed)
            {
                return;
            }

            this.probed = true;

            try
            {
                if (!this.probeWithFfmpeg())
                {
                    this.probeFailed = true;

                    return;
                }

                this.width = clampDim(this.width);
                this.height = clampDim(this.height);
                this.nativeWidth = this.width;
                this.nativeHeight = this.height;

                this.applyDecodeSizeFromNative();

                if (this.probeFailed)
                {
                    return;
                }

                this.reallocDecodeBuffers();
                /* Texture size is applied on the render thread in uploadIfReady. */
            }
            catch (Exception e)
            {
                e.printStackTrace();
                this.probeFailed = true;
            }
        }

        private boolean probeWithFfmpeg()
        {
            String ffmpeg = FFMpegUtils.getFFMPEG();

            if (ffmpeg == null || ffmpeg.isEmpty())
            {
                return false;
            }

            try
            {
                ProcessBuilder builder = new ProcessBuilder(
                    ffmpeg,
                    "-hide_banner",
                    "-i", this.file.getAbsolutePath(),
                    "-f", "null",
                    "-"
                );
                builder.redirectErrorStream(true);
                Process probe = builder.start();
                String output = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

                probe.waitFor();

                Matcher size = SIZE_PATTERN.matcher(output);
                Matcher fps = FPS_PATTERN.matcher(output);
                Matcher duration = DURATION_PATTERN.matcher(output);

                if (!size.find())
                {
                    return false;
                }

                this.width = Integer.parseInt(size.group(1));
                this.height = Integer.parseInt(size.group(2));

                if (fps.find())
                {
                    this.fps = Math.max(1F, Float.parseFloat(fps.group(1)));
                }

                /* Cap display decode rate — 24fps is enough for forms and saves CPU. */
                this.fps = Math.min(24F, this.fps);

                if (duration.find())
                {
                    double h = Double.parseDouble(duration.group(1));
                    double m = Double.parseDouble(duration.group(2));
                    double s = Double.parseDouble(duration.group(3));

                    this.durationSec = h * 3600D + m * 60D + s;
                }

                return this.width >= 2 && this.height >= 2;
            }
            catch (Exception e)
            {
                return false;
            }
        }

        private static int clampDim(int value)
        {
            return Math.max(2, Math.min(MAX_DIM, value));
        }

        private void close()
        {
            this.closeProcess();
            this.workerRunning.set(false);

            if (this.readScratch != null)
            {
                MemoryUtil.memFree(this.readScratch);
                this.readScratch = null;
            }

            synchronized (this.uploadLock)
            {
                if (this.pendingPixels != null)
                {
                    MemoryUtil.memFree(this.pendingPixels);
                    this.pendingPixels = null;
                }
            }

            if (this.texture.isValid())
            {
                this.texture.delete();
            }

            this.textureAllocated = false;
        }
    }
}
