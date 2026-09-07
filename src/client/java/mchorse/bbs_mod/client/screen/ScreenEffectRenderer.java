package mchorse.bbs_mod.client.screen;

import mchorse.bbs_mod.camera.clips.misc.BossBarClip;
import mchorse.bbs_mod.camera.clips.misc.BossBarState;
import mchorse.bbs_mod.camera.clips.misc.HotbarClip;
import mchorse.bbs_mod.camera.clips.misc.HotbarState;
import mchorse.bbs_mod.camera.clips.misc.ImageClip;
import mchorse.bbs_mod.camera.clips.misc.ImageOverlay;
import mchorse.bbs_mod.camera.clips.misc.Subtitle;
import mchorse.bbs_mod.camera.clips.misc.SubtitleClip;
import mchorse.bbs_mod.camera.clips.screen.ColorClip;
import mchorse.bbs_mod.camera.clips.screen.ColorEffect;
import mchorse.bbs_mod.camera.clips.screen.EyeClip;
import mchorse.bbs_mod.camera.clips.screen.EyeEffect;
import mchorse.bbs_mod.camera.clips.screen.GrainClip;
import mchorse.bbs_mod.camera.clips.screen.GrainEffect;
import mchorse.bbs_mod.camera.clips.screen.LetterboxClip;
import mchorse.bbs_mod.camera.clips.screen.LetterboxEffect;
import mchorse.bbs_mod.camera.clips.screen.ScreenNodeEffect;
import mchorse.bbs_mod.ui.film.UIBossBarRenderer;
import mchorse.bbs_mod.ui.film.UIHotbarRenderer;
import mchorse.bbs_mod.ui.film.UIImageRenderer;
import mchorse.bbs_mod.ui.film.UISubtitleRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public class ScreenEffectRenderer
{
    public static void render(Batcher2D batcher, ClipContext context, int screenW, int screenH)
    {
        List<ColorEffect> effects = ColorClip.getEffects(context);
        List<LetterboxEffect> letterboxEffects = LetterboxClip.getEffects(context);
        List<GrainEffect> grainEffects = GrainClip.getEffects(context);
        List<EyeEffect> eyeEffects = EyeClip.getEffects(context);
        List<Subtitle> subtitles = SubtitleClip.getSubtitles(context);
        List<HotbarState> hotbars = HotbarClip.getHotbars(context);
        List<ImageOverlay> images = ImageClip.getImages(context);
        List<BossBarState> bossBars = BossBarClip.getBossBars(context);

        convertNodeEffects(context, effects, grainEffects, letterboxEffects);

        if (effects.isEmpty() && letterboxEffects.isEmpty() && grainEffects.isEmpty() && eyeEffects.isEmpty()
            && subtitles.isEmpty() && hotbars.isEmpty() && images.isEmpty() && bossBars.isEmpty())
        {
            return;
        }

        /* Safety net: Subtitle's text FBO can shrink glViewport; restore after the pass. */
        int[] prevViewport = new int[4];

        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        RenderSystem.disableDepthTest();

        MatrixStack matrices = batcher.getContext().getMatrices();
        int effectIndex = 0;
        int letterboxIndex = 0;
        int grainIndex = 0;
        int eyeIndex = 0;
        int subtitleIndex = 0;
        int hotbarIndex = 0;
        int imageIndex = 0;
        int bossBarIndex = 0;

        List<ColorEffect> pendingShaderEffects = new ArrayList<>();
        List<GrainEffect> pendingGrainEffects = new ArrayList<>();

        while (effectIndex < effects.size()
            || letterboxIndex < letterboxEffects.size()
            || grainIndex < grainEffects.size()
            || eyeIndex < eyeEffects.size()
            || subtitleIndex < subtitles.size()
            || hotbarIndex < hotbars.size()
            || imageIndex < images.size()
            || bossBarIndex < bossBars.size())
        {
            int effOrder = effectIndex < effects.size() ? effects.get(effectIndex).renderOrder : Integer.MAX_VALUE;
            int letOrder = letterboxIndex < letterboxEffects.size() ? letterboxEffects.get(letterboxIndex).renderOrder : Integer.MAX_VALUE;
            int grnOrder = grainIndex < grainEffects.size() ? grainEffects.get(grainIndex).renderOrder : Integer.MAX_VALUE;
            int eyeOrder = eyeIndex < eyeEffects.size() ? eyeEffects.get(eyeIndex).renderOrder : Integer.MAX_VALUE;
            int subOrder = subtitleIndex < subtitles.size() ? subtitles.get(subtitleIndex).renderOrder : Integer.MAX_VALUE;
            int hotOrder = hotbarIndex < hotbars.size() ? hotbars.get(hotbarIndex).renderOrder : Integer.MAX_VALUE;
            int imgOrder = imageIndex < images.size() ? images.get(imageIndex).renderOrder : Integer.MAX_VALUE;
            int bosOrder = bossBarIndex < bossBars.size() ? bossBars.get(bossBarIndex).renderOrder : Integer.MAX_VALUE;

            int nextOrder = Math.min(
                Math.min(Math.min(effOrder, letOrder), Math.min(grnOrder, eyeOrder)),
                Math.min(Math.min(subOrder, hotOrder), Math.min(imgOrder, bosOrder))
            );

            boolean hasDirectDraw = (imgOrder == nextOrder)
                || (subOrder == nextOrder)
                || (hotOrder == nextOrder)
                || (bosOrder == nextOrder)
                || (eyeOrder == nextOrder)
                || (letOrder == nextOrder)
                || (effOrder == nextOrder && effects.get(effectIndex).hasOverlay);

            if (hasDirectDraw && (!pendingShaderEffects.isEmpty() || !pendingGrainEffects.isEmpty()))
            {
                batcher.flushDraw();
                ColorGradeRenderer.apply(pendingShaderEffects, pendingGrainEffects);
                ColorGradeRenderer.resyncMinecraftState(batcher);
                pendingShaderEffects.clear();
                pendingGrainEffects.clear();
            }

            if (eyeOrder == nextOrder)
            {
                renderEye(batcher, eyeEffects.get(eyeIndex), screenW, screenH);
                eyeIndex += 1;
            }
            else if (effOrder == nextOrder)
            {
                ColorEffect effect = effects.get(effectIndex);

                if (effect.hasOverlay)
                {
                    batcher.box(0, 0, screenW, screenH, effect.overlayColor);
                }

                if (effect.hasGrade || effect.hasVignette || effect.hasDistort || effect.hasCinematic)
                {
                    pendingShaderEffects.add(effect);
                }

                effectIndex += 1;
            }
            else if (grnOrder == nextOrder)
            {
                GrainEffect grain = grainEffects.get(grainIndex);

                if (grain.strength > 0F)
                {
                    pendingGrainEffects.add(grain);
                }

                grainIndex += 1;
            }
            else if (imgOrder == nextOrder)
            {
                UIImageRenderer.renderImage(matrices, batcher, images.get(imageIndex));
                imageIndex += 1;
            }
            else if (subOrder == nextOrder)
            {
                UISubtitleRenderer.renderSubtitle(matrices, batcher, subtitles.get(subtitleIndex));
                subtitleIndex += 1;
            }
            else if (hotOrder == nextOrder)
            {
                UIHotbarRenderer.renderHotbar(matrices, batcher, hotbars.get(hotbarIndex), 0, 0, screenW, screenH);
                hotbarIndex += 1;
            }
            else if (bosOrder == nextOrder)
            {
                UIBossBarRenderer.renderBossBar(matrices, batcher, bossBars.get(bossBarIndex), 0, 0, screenW, screenH);
                bossBarIndex += 1;
            }
            else if (letOrder == nextOrder)
            {
                renderLetterbox(batcher, letterboxEffects.get(letterboxIndex), screenW, screenH);
                letterboxIndex += 1;
            }
        }

        if (!pendingShaderEffects.isEmpty() || !pendingGrainEffects.isEmpty())
        {
            batcher.flushDraw();
            ColorGradeRenderer.apply(pendingShaderEffects, pendingGrainEffects);
            ColorGradeRenderer.resyncMinecraftState(batcher);
            pendingShaderEffects.clear();
            pendingGrainEffects.clear();
        }

        effects.clear();
        letterboxEffects.clear();
        grainEffects.clear();
        eyeEffects.clear();
        bossBars.clear();

        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        RenderSystem.enableDepthTest();
    }

    private static void convertNodeEffects(ClipContext context, List<ColorEffect> effects, List<GrainEffect> grainEffects, List<LetterboxEffect> letterboxEffects)
    {
        List<ScreenNodeEffect> nodeEffects = ScreenNodeEffect.getEffects(context);

        for (ScreenNodeEffect ne : nodeEffects)
        {
            ColorEffect ce = new ColorEffect();
            ce.renderOrder = ne.renderOrder;

            if (ne.brightness != 0F || ne.contrast != 0F || ne.saturation != 0F)
            {
                ce.hasGrade   = true;
                ce.brightness = ne.brightness;
                ce.contrast   = ne.contrast;
                ce.saturation = ne.saturation;
            }

            if (ne.vignetteStrength > 0F)
            {
                ce.hasVignette        = true;
                ce.vignetteStrength   = ne.vignetteStrength;
                ce.vignetteSmoothness = ne.vignetteSmoothness;
                ce.vignetteColor      = ne.vignetteColor;
            }

            if (ne.overlayAlpha > 0F)
            {
                ce.hasOverlay   = true;
                ce.overlayColor = Colors.setA(ne.overlayColor, ne.overlayAlpha);
            }

            if (ne.distortX != 0F || ne.distortY != 0F)
            {
                ce.hasDistort = true;
                ce.distortX   = ne.distortX;
                ce.distortY   = ne.distortY;
            }

            if (ce.hasGrade || ce.hasVignette || ce.hasOverlay || ce.hasDistort)
            {
                effects.add(ce);
            }

            if (ne.grainStrength > 0F)
            {
                GrainEffect ge = new GrainEffect();
                ge.renderOrder = ne.renderOrder;
                ge.strength    = ne.grainStrength;
                ge.size        = ne.grainSize;
                grainEffects.add(ge);
            }

            if (ne.letterboxSize > 0F)
            {
                LetterboxEffect le = new LetterboxEffect();
                le.renderOrder = ne.renderOrder;
                le.size        = ne.letterboxSize;
                le.color       = ne.letterboxColor;
                letterboxEffects.add(le);
            }
        }

        nodeEffects.clear();
    }

    private static void renderLetterbox(Batcher2D batcher, LetterboxEffect effect, int screenW, int screenH)
    {
        if (effect.width <= 0F)
        {
            return;
        }

        int barH = (int)(screenH * effect.size);

        if (barH <= 0)
        {
            return;
        }

        float zoom = effect.zoom <= 0F ? 1F : effect.zoom;
        boolean transformed = effect.rotation != 0F || zoom != 1F || effect.offsetX != 0F || effect.offsetY != 0F;

        if (transformed)
        {
            MatrixStack stack = batcher.getContext().getMatrices();

            stack.push();
            stack.translate(effect.offsetX * screenW, effect.offsetY * screenH, 0F);
            stack.translate(screenW / 2F, screenH / 2F, 0F);
            stack.multiply(RotationAxis.POSITIVE_Z.rotation(MathUtils.toRad(effect.rotation)));
            stack.scale(zoom, zoom, 1F);
            stack.translate(-screenW / 2F, -screenH / 2F, 0F);
            renderLetterboxBars(batcher, effect, screenW, screenH, barH);
            stack.pop();
        }
        else
        {
            renderLetterboxBars(batcher, effect, screenW, screenH, barH);
        }
    }

    private static void renderLetterboxBars(Batcher2D batcher, LetterboxEffect effect, int screenW, int screenH, int barH)
    {
        int color = effect.color;
        int smoothH = (int)(screenH * effect.smoothness);
        float barWidthFactor = effect.width;
        int barW = Math.max(1, Math.round(screenW * barWidthFactor));
        int barX = (screenW - barW) / 2;

        if (smoothH > 0 && smoothH < barH)
        {
            int solidH = barH - smoothH;
            int transparent = Colors.setA(color, 0F);

            /* Top bar: solid part + inner gradient */
            batcher.box(barX, 0, barX + barW, solidH, color);
            batcher.gradientVBox(barX, solidH, barX + barW, barH, color, transparent);

            /* Bottom bar: inner gradient + solid part */
            batcher.gradientVBox(barX, screenH - barH, barX + barW, screenH - solidH, transparent, color);
            batcher.box(barX, screenH - solidH, barX + barW, screenH, color);
        }
        else
        {
            /* Hard-edge bars */
            batcher.box(barX, 0, barX + barW, barH, color);
            batcher.box(barX, screenH - barH, barX + barW, screenH, color);
        }
    }

    private static void renderEye(Batcher2D batcher, EyeEffect effect, int screenW, int screenH)
    {
        float blink = Math.min(1F, effect.size / 0.025F);

        if (blink <= 0.001F)
        {
            return;
        }

        float zoom = effect.zoom <= 0F ? 1F : effect.zoom;
        boolean transformed = effect.rotation != 0F || zoom != 1F || effect.offsetX != 0F || effect.offsetY != 0F;

        if (transformed)
        {
            MatrixStack stack = batcher.getContext().getMatrices();

            stack.push();
            stack.translate(effect.offsetX * screenW, effect.offsetY * screenH, 0F);
            stack.translate(screenW / 2F, screenH / 2F, 0F);
            stack.multiply(RotationAxis.POSITIVE_Z.rotation(MathUtils.toRad(effect.rotation)));
            stack.scale(zoom, zoom, 1F);
            stack.translate(-screenW / 2F, -screenH / 2F, 0F);
            renderEyeMask(batcher, effect, screenW, screenH, blink);
            stack.pop();
        }
        else
        {
            renderEyeMask(batcher, effect, screenW, screenH, blink);
        }
    }

    /**
     * Elliptical eyelid mask. Smoothness feathers the inside rim of the eye opening:
     * a shrunken inner ellipse stays fully clear and the fade runs between the inner
     * and outer ellipse edges, so it follows the eye's shape on all sides.
     */
    private static void renderEyeMask(Batcher2D batcher, EyeEffect effect, int screenW, int screenH, float blink)
    {
        int color = effect.color;
        int alphaColor = Colors.setA(color, Colors.getA(color) * blink);
        int transparent = Colors.setA(color, 0F);
        float widthFactor = effect.width <= 0F ? 1F : effect.width;
        float open = Math.max(0F, 1F - effect.size);
        float halfW = screenW / 2F * widthFactor;
        float halfH = screenH / 2F * open;
        float centerX = screenW / 2F;
        float centerY = screenH / 2F;

        if (open > 0.3F && widthFactor > 0F)
        {
            double aspect = Math.sqrt(1D + Math.pow(screenW / (double) screenH / widthFactor, 2D));
            float eased = Math.min(1F, (open - 0.3F) / 0.7F);
            float scale = 1F + ((float) aspect - 1F) * eased;

            halfW *= scale;
            halfH *= scale;
        }

        if (halfH <= 0F)
        {
            batcher.box(0F, 0F, screenW, screenH, alphaColor);

            return;
        }

        /* Inner ellipse (fully clear); the feather band lies between it and the rim */
        float inner = 1F - MathUtils.clamp(effect.smoothness, 0F, 1F);
        float halfWIn = halfW * inner;
        float halfHIn = halfH * inner;
        boolean feather = inner < 1F;
        int spanStart = -1;

        for (int y = 0; y <= screenH; y++)
        {
            if (y < screenH && Math.abs((y + 0.5F) - centerY) / halfH >= 1F)
            {
                if (spanStart < 0)
                {
                    spanStart = y;
                }

                continue;
            }

            if (spanStart >= 0)
            {
                batcher.box(0F, spanStart, screenW, y, alphaColor);
                spanStart = -1;
            }

            if (y >= screenH)
            {
                break;
            }

            float dy = Math.abs((y + 0.5F) - centerY);
            float vy = dy / halfH;
            float halfEllipseW = halfW * (float) Math.sqrt(Math.max(0D, 1D - (double) vy * vy));
            int ellLeft = (int) Math.ceil(centerX - halfEllipseW);
            int ellRight = (int) Math.floor(centerX + halfEllipseW);

            if (feather)
            {
                float halfInnerW = 0F;
                int innerColor = transparent;

                if (halfHIn > 0F && dy < halfHIn)
                {
                    float viy = dy / halfHIn;

                    halfInnerW = halfWIn * (float) Math.sqrt(Math.max(0D, 1D - (double) viy * viy));
                }
                else
                {
                    /* Row is fully inside the feather band: its center alpha follows
                     * the radial position between the inner and outer ellipse */
                    float t = MathUtils.clamp((vy - inner) / (1F - inner), 0F, 1F);

                    innerColor = Colors.setA(color, Colors.getA(color) * blink * t);
                }

                int inLeft = (int) Math.ceil(centerX - halfInnerW);
                int inRight = (int) Math.floor(centerX + halfInnerW);

                if (ellLeft > 0)
                {
                    batcher.box(0F, y, ellLeft, y + 1, alphaColor);
                }

                if (inLeft > ellLeft)
                {
                    batcher.gradientHBox(ellLeft, y, inLeft, y + 1, alphaColor, innerColor);
                }

                if (ellRight < screenW)
                {
                    batcher.box(ellRight, y, screenW, y + 1, alphaColor);
                }

                if (inRight < ellRight)
                {
                    batcher.gradientHBox(inRight, y, ellRight, y + 1, innerColor, alphaColor);
                }
            }
            else
            {
                if (ellLeft > 0)
                {
                    batcher.box(0F, y, ellLeft, y + 1, alphaColor);
                }

                if (ellRight < screenW)
                {
                    batcher.box(ellRight, y, screenW, y + 1, alphaColor);
                }
            }
        }
    }
}
