package mchorse.bbs_mod.client.video;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.texture.TextureFormat;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.FFMpegUtils;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.system.MemoryUtil;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback ffmpeg decoder for VideoForm when WaterMedia/VLC is unavailable.
 * Prefer {@link VideoRenderer#prepareFormFrame} for performance.
 */
public final class VideoFormPlayback
{
    private static final int MAX_LONG_SIDE = 1280;
    private static final int MAX_DIM = 8192;
    private static final int MAX_FRAMES_PER_CALL = 1;
    private static final long SEEK_JUMP_FRAMES = 45L;
    private static final Pattern SIZE_PATTERN = Pattern.compile("(?<!\\w)(\\d{2,5})\\s*[xX]\\s*(\\d{2,5})(?!\\w)");
    private static final Pattern FPS_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*fps");
    private static final Pattern DURATION_PATTERN = Pattern.compile("Duration:\\s*(\\d+):(\\d+):(\\d+(?:\\.\\d+)?)");
    private static final Map<String, VideoFormPlayback> CACHE = new ConcurrentHashMap<>();
    private static String liveDecodeKey;
    private static long liveDecodeFrameMs;

    private final File file;
    private final Texture texture = new Texture();
    private final byte[] readChunk = new byte[65536];

    private int maxLongSide;
    private int nativeWidth = 16;
    private int nativeHeight = 9;
    private int width = 16;
    private int height = 9;
    private float fps = 30F;
    private double durationSec = 0D;
    private boolean probed;
    private boolean probeFailed;

    private Process process;
    private InputStream stdout;
    private ByteBuffer frameBuffer;
    private int frameBytes;
    private long currentFrameIndex = -1L;
    private boolean streamAlive;
    private boolean textureAllocated;

    private VideoFormPlayback(File file, int maxLongSide)
    {
        this.file = file;
        /* 0 = native (only a safety cap against absurd sizes). */
        this.maxLongSide = maxLongSide > 0 ? maxLongSide : MAX_DIM;
        this.texture.setFormat(TextureFormat.RGBA_U8);
        this.texture.setFilter(GL11.GL_LINEAR);
        this.texture.setWrap(GL13.GL_CLAMP_TO_EDGE);
        this.texture.setSize(16, 9);
    }

    public static VideoFormPlayback get(String path)
    {
        return get(path, MAX_LONG_SIDE);
    }

    public static VideoFormPlayback get(String path, int maxLongSide)
    {
        File file = resolveFile(path);

        if (file == null)
        {
            return null;
        }

        int limit = maxLongSide > 0 ? maxLongSide : 0;
        String key = file.getAbsolutePath();
        VideoFormPlayback playback = CACHE.computeIfAbsent(key, (k) -> new VideoFormPlayback(file, limit));

        playback.ensureMaxLongSide(limit);

        return playback;
    }

    public static void releaseAll()
    {
        for (VideoFormPlayback playback : CACHE.values())
        {
            try
            {
                playback.close();
            }
            catch (Throwable t)
            {}
        }

        CACHE.clear();
    }

    private void ensureMaxLongSide(int limit)
    {
        int side = limit > 0 ? limit : MAX_DIM;

        if (this.maxLongSide == side)
        {
            return;
        }

        this.maxLongSide = side;
        this.textureAllocated = false;
        this.closeProcess();
        this.currentFrameIndex = -1L;

        if (this.probed && !this.probeFailed)
        {
            this.applyDecodeSizeFromNative();
        }
    }

    private void applyDecodeSizeFromNative()
    {
        this.width = clampDim(this.nativeWidth);
        this.height = clampDim(this.nativeHeight);

        int longSide = Math.max(this.width, this.height);
        int limit = this.maxLongSide > 0 && this.maxLongSide < MAX_DIM ? this.maxLongSide : 0;

        if (limit > 0 && longSide > limit)
        {
            float scale = limit / (float) longSide;

            this.width = clampDim(Math.round(this.width * scale) & ~1);
            this.height = clampDim(Math.round(this.height * scale) & ~1);
        }

        long bytes = (long) this.width * (long) this.height * 4L;

        if (this.width < 2 || this.height < 2 || bytes <= 0L || bytes > Integer.MAX_VALUE)
        {
            this.probeFailed = true;

            return;
        }

        this.frameBytes = (int) bytes;

        if (this.frameBuffer != null)
        {
            MemoryUtil.memFree(this.frameBuffer);
            this.frameBuffer = null;
        }

        this.frameBuffer = MemoryUtil.memAlloc(this.frameBytes);
    }

    public Texture ensureFrame(long tickPosition, float speed, boolean loop)
    {
        this.ensureProbed();

        if (this.probeFailed || this.frameBuffer == null)
        {
            return null;
        }

        String key = this.file.getAbsolutePath();
        long frameMs = System.currentTimeMillis() / 50L;

        if (frameMs != liveDecodeFrameMs)
        {
            liveDecodeFrameMs = frameMs;
            liveDecodeKey = null;
        }

        if (liveDecodeKey == null)
        {
            liveDecodeKey = key;
        }
        else if (!liveDecodeKey.equals(key))
        {
            /* Another VideoForm already owns the single ffmpeg slot — keep last texture. */
            return this.currentFrameIndex >= 0L && this.textureAllocated ? this.texture : null;
        }

        long targetFrame = this.computeTargetFrame(tickPosition, speed, loop);

        if (targetFrame == this.currentFrameIndex && this.textureAllocated)
        {
            return this.texture;
        }

        if (!this.streamAlive || targetFrame < this.currentFrameIndex || targetFrame > this.currentFrameIndex + SEEK_JUMP_FRAMES)
        {
            this.restartStream(targetFrame);
        }

        if (!this.streamAlive)
        {
            return this.currentFrameIndex >= 0L && this.textureAllocated ? this.texture : null;
        }

        int decoded = 0;

        while (this.currentFrameIndex < targetFrame && decoded < MAX_FRAMES_PER_CALL)
        {
            if (!this.readOneFrame())
            {
                this.streamAlive = false;
                break;
            }

            decoded++;
        }

        if (this.streamAlive && this.currentFrameIndex < targetFrame - SEEK_JUMP_FRAMES)
        {
            this.restartStream(targetFrame);

            if (this.streamAlive)
            {
                this.readOneFrame();
            }
        }

        return this.currentFrameIndex >= 0L && this.textureAllocated ? this.texture : null;
    }

    public int getWidth()
    {
        return this.width;
    }

    public int getHeight()
    {
        return this.height;
    }

    public Texture peekTexture()
    {
        return this.currentFrameIndex >= 0L && this.textureAllocated && this.texture.isValid() ? this.texture : null;
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
        else if (loop)
        {
            targetFrame = Math.floorMod(targetFrame, Math.max(1L, Math.round(this.fps * 600L)));
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
            if (!this.probeWithFfprobe() && !this.probeWithFfmpeg())
            {
                this.probeFailed = true;

                return;
            }

            this.width = clampDim(this.width);
            this.height = clampDim(this.height);
            this.nativeWidth = this.width;
            this.nativeHeight = this.height;
            this.applyDecodeSizeFromNative();

            if (this.probeFailed || this.frameBuffer == null)
            {
                this.probeFailed = true;

                return;
            }

            /* Texture size is applied on first upload in readOneFrame. */
        }
        catch (Exception e)
        {
            e.printStackTrace();
            this.probeFailed = true;
        }
    }

    private boolean probeWithFfprobe()
    {
        File ffprobe = findFfprobe();

        if (ffprobe == null || !ffprobe.isFile())
        {
            return false;
        }

        try
        {
            ProcessBuilder builder = new ProcessBuilder(
                ffprobe.getAbsolutePath(),
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=width,height,r_frame_rate:format=duration",
                "-of", "csv=p=0",
                this.file.getAbsolutePath()
            );
            builder.redirectErrorStream(true);
            Process probe = builder.start();
            String output = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();

            if (probe.waitFor() != 0 || output.isEmpty())
            {
                return false;
            }

            String[] lines = output.split("[\\r\\n]+");
            String[] parts = lines[0].split(",");

            if (parts.length < 2)
            {
                return false;
            }

            int w = Integer.parseInt(parts[0].trim());
            int h = Integer.parseInt(parts[1].trim());

            if (w < 2 || h < 2 || w > MAX_DIM || h > MAX_DIM)
            {
                return false;
            }

            this.width = w;
            this.height = h;

            if (parts.length >= 3)
            {
                this.fps = parseFrameRate(parts[2].trim(), this.fps);
            }

            for (String line : lines)
            {
                for (String token : line.split(","))
                {
                    String t = token.trim();

                    if (t.isEmpty() || t.equalsIgnoreCase("N/A"))
                    {
                        continue;
                    }

                    try
                    {
                        double d = Double.parseDouble(t);

                        if (d > 0.05D && d < 86400D)
                        {
                            this.durationSec = d;
                        }
                    }
                    catch (NumberFormatException ignored)
                    {}
                }
            }

            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private boolean probeWithFfmpeg()
    {
        try
        {
            ProcessBuilder builder = new ProcessBuilder(
                FFMpegUtils.getFFMPEG(),
                "-hide_banner",
                "-i", this.file.getAbsolutePath()
            );
            builder.redirectErrorStream(true);
            Process probe = builder.start();
            String output = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            probe.waitFor();

            Matcher size = SIZE_PATTERN.matcher(output);
            boolean found = false;

            while (size.find())
            {
                int w = Integer.parseInt(size.group(1));
                int h = Integer.parseInt(size.group(2));

                if (w >= 16 && h >= 16 && w <= MAX_DIM && h <= MAX_DIM)
                {
                    this.width = w;
                    this.height = h;
                    found = true;
                    break;
                }
            }

            if (!found)
            {
                return false;
            }

            Matcher fps = FPS_PATTERN.matcher(output);

            if (fps.find())
            {
                this.fps = Math.max(1F, Float.parseFloat(fps.group(1)));
            }

            Matcher duration = DURATION_PATTERN.matcher(output);

            if (duration.find())
            {
                this.durationSec = Double.parseDouble(duration.group(1)) * 3600D
                    + Double.parseDouble(duration.group(2)) * 60D
                    + Double.parseDouble(duration.group(3));
            }

            return true;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    private static float parseFrameRate(String rate, float fallback)
    {
        try
        {
            if (rate.contains("/"))
            {
                String[] nh = rate.split("/");
                float n = Float.parseFloat(nh[0]);
                float d = Float.parseFloat(nh[1]);

                if (d > 0F)
                {
                    return Math.max(1F, n / d);
                }
            }

            return Math.max(1F, Float.parseFloat(rate));
        }
        catch (Exception e)
        {
            return fallback;
        }
    }

    private static int clampDim(int value)
    {
        return Math.max(2, Math.min(MAX_DIM, value));
    }

    private static File findFfprobe()
    {
        String ffmpeg = FFMpegUtils.getFFMPEG();

        if (ffmpeg == null || ffmpeg.isEmpty())
        {
            return null;
        }

        File ffmpegFile = new File(ffmpeg);
        File parent = ffmpegFile.getParentFile();

        if (parent == null)
        {
            return null;
        }

        String name = ffmpegFile.getName().toLowerCase(Locale.ROOT);

        if (name.equals("ffmpeg.exe"))
        {
            return new File(parent, "ffprobe.exe");
        }

        if (name.equals("ffmpeg"))
        {
            return new File(parent, "ffprobe");
        }

        File sibling = new File(parent, name.replace("ffmpeg", "ffprobe"));

        return sibling.isFile() ? sibling : null;
    }

    private void restartStream(long startFrame)
    {
        this.closeProcess();

        String ffmpeg = FFMpegUtils.getFFMPEG();

        if (ffmpeg == null || ffmpeg.isEmpty())
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
                "-vf", "scale=" + this.width + ":" + this.height,
                "-f", "rawvideo",
                "-pix_fmt", "rgba",
                "-"
            );
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            this.process = builder.start();
            this.stdout = new BufferedInputStream(this.process.getInputStream(), Math.min(this.frameBytes * 2, 1 << 20));
            this.currentFrameIndex = startFrame - 1L;
            this.streamAlive = true;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            this.streamAlive = false;
        }
    }

    private boolean readOneFrame()
    {
        try
        {
            this.frameBuffer.clear();
            int remaining = this.frameBytes;

            while (remaining > 0)
            {
                int read = this.stdout.read(this.readChunk, 0, Math.min(this.readChunk.length, remaining));

                if (read < 0)
                {
                    return false;
                }

                this.frameBuffer.put(this.readChunk, 0, read);
                remaining -= read;
            }

            this.frameBuffer.flip();
            this.currentFrameIndex++;

            if (!this.textureAllocated || this.texture.width != this.width || this.texture.height != this.height)
            {
                this.texture.setSize(this.width, this.height);
                this.textureAllocated = true;
            }

            this.texture.bind();

            try
            {
                /* Tightly packed RGBA — never set UNPACK_ROW_LENGTH (atlas black-world leak). */
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, this.width, this.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, this.frameBuffer);
            }
            finally
            {
                GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
                GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
                this.texture.unbind();
            }

            return true;
        }
        catch (Exception e)
        {
            return false;
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

    private void close()
    {
        this.closeProcess();

        if (this.frameBuffer != null)
        {
            MemoryUtil.memFree(this.frameBuffer);
            this.frameBuffer = null;
        }

        if (this.texture.isValid())
        {
            this.texture.delete();
        }
    }

    public static File resolveFile(String path)
    {
        if (path == null || path.isEmpty() || path.equalsIgnoreCase("none") || path.startsWith("<"))
        {
            return null;
        }

        if (path.startsWith("external:"))
        {
            String raw = path.substring("external:".length()).trim();
            File file = new File(raw);

            if (!file.isAbsolute())
            {
                file = new File(BBSMod.getGameFolder(), raw);
            }

            return file.isFile() ? file : null;
        }

        try
        {
            Link link = Link.create(path);
            File file = BBSMod.getProvider().getFile(link);

            if (file != null && file.isFile())
            {
                return file;
            }
        }
        catch (Throwable ignored)
        {}

        String relative = path.startsWith("assets:") ? path.substring("assets:".length()) : path;
        File assetsFile = BBSMod.getAssetsPath(relative);

        if (assetsFile.isFile())
        {
            return assetsFile;
        }

        String name = relative;
        int slash = Math.max(relative.lastIndexOf('/'), relative.lastIndexOf('\\'));

        if (slash >= 0)
        {
            name = relative.substring(slash + 1);
        }

        File[] candidates = new File[] {
            BBSMod.getAssetsPath("video/" + name),
            BBSMod.getAssetsPath("videos/" + name),
            new File(BBSMod.getGameFolder(), relative),
            new File(BBSMod.getGameFolder(), path)
        };

        for (File candidate : candidates)
        {
            if (candidate != null && candidate.isFile())
            {
                return candidate;
            }
        }

        return null;
    }
}
