package mchorse.bbs_mod.client.video;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.misc.VideoClip;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.clips.Clip;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.watermedia.api.player.videolan.VideoPlayer;
import org.watermedia.videolan4j.factory.MediaPlayerFactory;

public class VideoRenderer
{
    private static class PlayerWrapper
    {
        public VideoPlayer player;
        public long lastBbsTime = -1;
        public long lastSeekTime = 0;
        public Boolean wasPlaying = null;
        public int lastVolume = -1;
        public Boolean lastLoops = null;
        public long lastVideoTime = -1;
        public long lastRenderTime = 0;

        public PlayerWrapper(VideoPlayer player)
        {
            this.player = player;
        }
    }

    private static final Map<String, PlayerWrapper> PLAYERS = new HashMap<>();
    private static MediaPlayerFactory FACTORY;
    private static boolean factoryFailed;

    public static void renderClip(MatrixStack stack, Batcher2D batcher, VideoClip video, int tick, boolean isRunning, Area area, UIContext context)
    {
        if (!video.enabled.get() || !video.isInside(tick))
        {
            return;
        }

        Area baseArea = area;
        int actualW = getVideoWidth(video.video.get());
        int actualH = getVideoHeight(video.video.get());

        int baseW = baseArea.w;
        int baseH = baseArea.h;

        if (actualW > 0 && actualH > 0)
        {
            float videoAspect = (float) actualW / actualH;
            float areaAspect = (float) baseArea.w / baseArea.h;

            if (videoAspect > areaAspect)
            {
                baseH = (int) (baseArea.w / videoAspect);
            }
            else
            {
                baseW = (int) (baseArea.h * videoAspect);
            }
        }

        float widthPercent = video.width.get() / 100F;
        float heightPercent = video.height.get() / 100F;

        if (video.width.get() == 0 && video.height.get() == 0)
        {
            widthPercent = 1F;
            heightPercent = 1F;
        }

        int vw = widthPercent == 0F ? 0 : Math.max(1, Math.round(baseW * Math.abs(widthPercent))) * (widthPercent < 0F ? -1 : 1);
        int vh = heightPercent == 0F ? 0 : Math.max(1, Math.round(baseH * Math.abs(heightPercent))) * (heightPercent < 0F ? -1 : 1);

        int vx = baseArea.x + (baseArea.w - vw) / 2 + video.x.get();
        int vy = baseArea.y + (baseArea.h - vh) / 2 + video.y.get();

        batcher.flush();

        render(stack,
            video.video.get(),
            tick - video.tick.get() + video.offset.get(),
            isRunning,
            video.volume.get(),
            vx, vy, vw, vh, video.opacity.get(),
            video.cropX.get(), video.cropY.get(), video.cropWidth.get(), video.cropHeight.get(),
            video.loops.get());
    }

    public static void renderClips(MatrixStack stack, Batcher2D batcher, List<Clip> clips, int tick, boolean isRunning, Area viewport, Area globalArea, UIContext context, int screenWidth, int screenHeight, boolean renderGlobal)
    {
        for (Clip clip : clips)
        {
            if (clip instanceof VideoClip && clip.isInside(tick) && clip.enabled.get())
            {
                VideoClip video = (VideoClip) clip;

                if (video.global.get() != renderGlobal)
                {
                    continue;
                }

                Area baseArea = (video.global.get() && globalArea != null) ? globalArea : viewport;
                int actualW = getVideoWidth(video.video.get());
                int actualH = getVideoHeight(video.video.get());

                int baseW = baseArea.w;
                int baseH = baseArea.h;

                if (actualW > 0 && actualH > 0)
                {
                    float videoAspect = (float) actualW / actualH;
                    float areaAspect = (float) baseArea.w / baseArea.h;

                    if (videoAspect > areaAspect)
                    {
                        baseH = (int) (baseArea.w / videoAspect);
                    }
                    else
                    {
                        baseW = (int) (baseArea.h * videoAspect);
                    }
                }

                float widthPercent = video.width.get() / 100F;
                float heightPercent = video.height.get() / 100F;

                if (video.width.get() == 0 && video.height.get() == 0)
                {
                    widthPercent = 1F;
                    heightPercent = 1F;
                }

                int vw = widthPercent == 0F ? 0 : Math.max(1, Math.round(baseW * Math.abs(widthPercent))) * (widthPercent < 0F ? -1 : 1);
                int vh = heightPercent == 0F ? 0 : Math.max(1, Math.round(baseH * Math.abs(heightPercent))) * (heightPercent < 0F ? -1 : 1);

                int vx = baseArea.x + (baseArea.w - vw) / 2 + video.x.get();
                int vy = baseArea.y + (baseArea.h - vh) / 2 + video.y.get();

                if (!video.global.get())
                {
                    if (context != null)
                    {
                        batcher.clip(viewport, context);
                    }
                    else
                    {
                        batcher.clip(viewport.x, viewport.y, viewport.w, viewport.h, screenWidth, screenHeight);
                    }
                }
                else
                {
                    batcher.flush();
                }

                render(stack,
                    video.video.get(),
                    tick - video.tick.get() + video.offset.get(),
                    isRunning,
                    video.volume.get(),
                    vx, vy, vw, vh, video.opacity.get(),
                    video.cropX.get(), video.cropY.get(), video.cropWidth.get(), video.cropHeight.get(),
                    video.loops.get());

                if (!video.global.get())
                {
                    if (context != null)
                    {
                        batcher.unclip(context);
                    }
                    else
                    {
                        batcher.unclip(screenWidth, screenHeight);
                    }
                }
            }
        }
    }

    private static String resolveVideoPath(String path)
    {
        if (path == null || path.isEmpty())
        {
            return null;
        }

        if (path.startsWith("external:"))
        {
            String raw = path.substring("external:".length()).trim();

            if (raw.isEmpty())
            {
                return null;
            }

            File file = new File(raw);

            if (!file.isAbsolute())
            {
                file = new File(BBSMod.getGameFolder(), raw);
            }

            return file.getAbsolutePath();
        }

        try
        {
            Link link = Link.create(path);
            File file = BBSMod.getProvider().getFile(link);

            if (file != null && file.exists())
            {
                return file.getAbsolutePath();
            }
        }
        catch (Throwable ignored)
        {}

        return path;
    }

    public static File getResolvedVideoFile(String path)
    {
        String resolved = resolveVideoPath(path);

        if (resolved == null || resolved.isEmpty())
        {
            return null;
        }

        File file = new File(resolved);

        return file.exists() ? file : null;
    }

    public static void render(MatrixStack stack, String path, long position, boolean playing, int volume, int x, int y, int w, int h, float opacity, int cropX, int cropY, int cropWidth, int cropHeight, boolean loops)
    {
        String resolved = resolveVideoPath(path);

        if (resolved == null || resolved.isEmpty())
        {
            return;
        }

        PlayerWrapper wrapper = PLAYERS.get(resolved);
        VideoPlayer player;

        if (wrapper == null)
        {
            if (factoryFailed)
            {
                return;
            }

            if (FACTORY == null)
            {
                try
                {
                    FACTORY = new MediaPlayerFactory(new String[] {"--avcodec-hw=none"});
                }
                catch (Throwable t)
                {
                    t.printStackTrace();
                    factoryFailed = true;
                    return;
                }
            }

            player = new VideoPlayer(FACTORY, MinecraftClient.getInstance());
            try
            {
                player.start(new File(resolved).toURI());
                player.setVolume(volume);
                wrapper = new PlayerWrapper(player);
                wrapper.lastVolume = volume;
                PLAYERS.put(resolved, wrapper);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                return;
            }
        }
        else
        {
            player = wrapper.player;
            if (wrapper.lastVolume != volume)
            {
                player.setVolume(volume);
                wrapper.lastVolume = volume;
            }
        }

        if (wrapper.lastLoops == null || wrapper.lastLoops != loops)
        {
            player.setRepeatMode(loops);
            wrapper.lastLoops = loops;
        }

        if (wrapper.wasPlaying == null || wrapper.wasPlaying != playing)
        {
            if (playing)
            {
                player.play();
            }
            else
            {
                player.pause();
            }
            wrapper.wasPlaying = playing;
        }

        long videoTime = player.getTime();
        long bbsTime = position * 50;
        long systemTime = System.currentTimeMillis();
        wrapper.lastRenderTime = systemTime;
        long duration = player.getDuration();

        if (!playing)
        {
            if (wrapper.lastVideoTime != -1 && videoTime != wrapper.lastVideoTime)
            {
                if ((systemTime - wrapper.lastSeekTime) > 1000)
                {
                    player.pause();
                }
            }
            wrapper.lastVideoTime = videoTime;
        }

        if (loops && duration > 0)
        {
            bbsTime = bbsTime % duration;
            if (bbsTime < 0) bbsTime += duration;
        }

        boolean shouldSeek = false;

        if (playing)
        {
            // When playing, sync only if drift is large and we haven't sought recently
            long diff = Math.abs(videoTime - bbsTime);

            if (loops && duration > 0)
            {
                long loopDiff = Math.abs(diff - duration);
                diff = Math.min(diff, loopDiff);
            }

            if (diff > 1000 && (systemTime - wrapper.lastSeekTime) > 3000)
            {
                shouldSeek = true;
            }
        }
        else
        {
            // When paused, seek only if the timeline cursor moved or if we are out of sync
            if (wrapper.lastBbsTime != bbsTime)
            {
                shouldSeek = true;
            }
            else if (Math.abs(videoTime - bbsTime) > 1000 && (systemTime - wrapper.lastSeekTime) > 3000)
            {
                shouldSeek = true;
            }
        }

        if (shouldSeek)
        {
            player.seekTo(bbsTime);
            wrapper.lastSeekTime = systemTime;
            wrapper.lastBbsTime = bbsTime;
        }
        else if (!playing)
        {
            // Update tracking even if we didn't seek, to avoid seeking on same frame later if conditions change
            wrapper.lastBbsTime = bbsTime;
        }

        int texture = player.texture();

        if (texture > 0)
        {
            int vw = player.width();
            int vh = player.height();

            if (w == 0 || h == 0)
            {
                if (vw > 0 && vh > 0)
                {
                    // Fit video into target area (x, y, w, h are treated as container if w=0 or h=0 passed initially? No, wait)
                    // The caller passes 'area.w' and 'area.h' if video.width/height are 0.
                    // But here we want to RESPECT aspect ratio if the user didn't specify exact dimensions.
                    
                    // Actually, let's look at how UIFilmPreview calls this.
                    // It passes video.width/height if set, OR area.w/area.h if 0.
                    // So if user sets 0, we get area.w/area.h.
                    // To support "fit to camera" with correct aspect ratio, we need to know if the caller WANTED original aspect ratio.
                    // But here, we are low level.
                    
                    // However, we can improve this:
                    // If the user specified explicit dimensions (w != area.w perhaps?), use them.
                    // But since we can't easily know the "container" size here without more args,
                    // let's assume if the input w/h match the viewport, we might want to fit.
                    
                    // BETTER APPROACH:
                    // We'll calculate the draw rect here.
                    // But wait, the previous code just drew a quad from x,y to x+w,y+h.
                    
                    // If we want to preserve aspect ratio, we need to adjust x, y, w, h.
                    // But 'render' just draws a quad.
                    // It's better to do the calculation in UIFilmPreview.
                    // However, I can't easily access 'player.width()' from UIFilmPreview without exposing the player wrapper or adding a getter.
                    
                    // So, let's modify THIS method to handle aspect ratio if a flag is set?
                    // Or better, let's return the player dimensions so UIFilmPreview can use them?
                    // No, that's complex async state.
                    
                    // Let's do this:
                    // If w and h are provided, we fill that rect.
                    // BUT, if the user wants "auto" size (0, 0 in clip), UIFilmPreview passes area.w/area.h.
                    // That stretches it.
                    
                    // We need a way to tell render() "Use these bounds but FIT the video inside".
                    // Or, we can query dimensions.
                    
                    // Let's add a method to get dimensions for a path.
                }
            }

            /* Recorte por lados (izq/arr/der/abajo) y ajuste de tamaño para evitar estirar. */
            float left = Math.max(0F, Math.min(1F, cropX / 100F));
            float top = Math.max(0F, Math.min(1F, cropY / 100F));
            float right = Math.max(0F, Math.min(1F, cropWidth / 100F));
            float bottom = Math.max(0F, Math.min(1F, cropHeight / 100F));

            float u0 = left;
            float v0 = top;
            float u1 = 1F - right;
            float v1 = 1F - bottom;

            float cropWidthPercent = u1 - u0;
            float cropHeightPercent = v1 - v0;

            if (cropWidthPercent <= 0F || cropHeightPercent <= 0F)
            {
                return;
            }

            int wSign = w < 0 ? -1 : 1;
            int hSign = h < 0 ? -1 : 1;
            int absW = Math.abs(w);
            int absH = Math.abs(h);
            int drawW = Math.round(absW * cropWidthPercent) * wSign;
            int drawH = Math.round(absH * cropHeightPercent) * hSign;

            if (drawW == 0 || drawH == 0)
            {
                return;
            }

            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderTexture(0, texture);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, opacity);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
            Matrix4f matrix = stack.peek().getPositionMatrix();

            /* Desplazar por recorte de izquierda/arriba para mantener el contenido en su lugar. */
            int drawX = x + Math.round(absW * left) * wSign;
            int drawY = y + Math.round(absH * top) * hSign;

            buffer.vertex(matrix, drawX, drawY + drawH, 0).texture(u0, v1);
            buffer.vertex(matrix, drawX + drawW, drawY + drawH, 0).texture(u1, v1);
            buffer.vertex(matrix, drawX + drawW, drawY, 0).texture(u1, v0);
            buffer.vertex(matrix, drawX, drawY, 0).texture(u0, v0);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
            
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    public static int getVideoWidth(String path)
    {
        String resolved = resolveVideoPath(path);
        PlayerWrapper wrapper = resolved == null ? null : PLAYERS.get(resolved);
        return wrapper != null && wrapper.player != null ? wrapper.player.width() : 0;
    }

    public static int getVideoHeight(String path)
    {
        String resolved = resolveVideoPath(path);
        PlayerWrapper wrapper = resolved == null ? null : PLAYERS.get(resolved);
        return wrapper != null && wrapper.player != null ? wrapper.player.height() : 0;
    }

    public static long getVideoDuration(String path)
    {
        String resolved = resolveVideoPath(path);

        if (resolved == null || resolved.isEmpty())
        {
            return 0L;
        }

        PlayerWrapper wrapper = PLAYERS.get(resolved);

        if (wrapper != null && wrapper.player != null)
        {
            return wrapper.player.getDuration();
        }

        if (factoryFailed)
        {
            return 0L;
        }

        if (FACTORY == null)
        {
            try
            {
                FACTORY = new MediaPlayerFactory(new String[] {"--avcodec-hw=none"});
            }
            catch (Throwable t)
            {
                t.printStackTrace();
                factoryFailed = true;
                return 0L;
            }
        }

        try
        {
            VideoPlayer player = new VideoPlayer(FACTORY, MinecraftClient.getInstance());
            player.start(new File(resolved).toURI());
            player.pause();

            wrapper = new PlayerWrapper(player);
            wrapper.lastRenderTime = System.currentTimeMillis();
            wrapper.wasPlaying = false;
            PLAYERS.put(resolved, wrapper);

            return player.getDuration();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            return 0L;
        }
    }

    public static void releaseVideo(String path)
    {
        String resolved = resolveVideoPath(path);

        if (resolved != null && PLAYERS.containsKey(resolved))
        {
            PlayerWrapper wrapper = PLAYERS.remove(resolved);

            if (wrapper != null && wrapper.player != null)
            {
                wrapper.player.release();
            }
        }
    }

    public static void update()
    {
        long now = System.currentTimeMillis();
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, PlayerWrapper> entry : PLAYERS.entrySet())
        {
            PlayerWrapper wrapper = entry.getValue();
            long diff = now - wrapper.lastRenderTime;

            if (diff > 1000)
            {
                if (wrapper.wasPlaying != null && wrapper.wasPlaying)
                {
                    wrapper.player.pause();
                    wrapper.wasPlaying = false;
                }
            }
            
            if (diff > 5000)
            {
                wrapper.player.release();
                toRemove.add(entry.getKey());
            }
        }
        
        for (String key : toRemove)
        {
            PLAYERS.remove(key);
        }
    }

    public static void stopAll()
    {
        for (PlayerWrapper wrapper : PLAYERS.values())
        {
            if (wrapper.wasPlaying != null && wrapper.wasPlaying)
            {
                wrapper.player.pause();
                wrapper.wasPlaying = false;
            }
        }
    }

    public static void cleanup()
    {
        for (PlayerWrapper wrapper : PLAYERS.values())
        {
            wrapper.player.release();
        }
        
        PLAYERS.clear();

        if (FACTORY != null)
        {
            FACTORY.release();
            FACTORY = null;
        }
    }
}
