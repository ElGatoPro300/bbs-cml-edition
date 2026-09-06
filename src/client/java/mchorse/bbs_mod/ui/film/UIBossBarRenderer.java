package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.clips.misc.BossBarState;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import com.mojang.blaze3d.opengl.GlStateManager;

import org.lwjgl.opengl.GL11;

import java.util.List;

public class UIBossBarRenderer
{
    private static final float REFERENCE_WIDTH = 1920F;
    private static final float REFERENCE_HEIGHT = 1080F;
    private static final int BASE_BAR_WIDTH = 182;
    private static final int BASE_BAR_HEIGHT = 5;
    private static final int TEXT_GAP = 2;
    private static final Identifier BOSS_BAR_BACKGROUND = Identifier.of("minecraft", "boss_bar/white_background");
    private static final Identifier BOSS_BAR_PROGRESS = Identifier.of("minecraft", "boss_bar/white_progress");

    public static void renderBossBars(MatrixStack stack, Batcher2D batcher, List<BossBarState> bossBars, int originX, int originY, int width, int height)
    {
        if (bossBars == null || bossBars.isEmpty())
        {
            return;
        }

        float resolutionScale = getResolutionScale(width, height);

        for (BossBarState bossBar : bossBars)
        {
            renderBossBar(stack, batcher, bossBar, originX, originY, width, height, resolutionScale);
        }
    }

    public static void renderBossBar(MatrixStack stack, Batcher2D batcher, BossBarState bossBar, int originX, int originY, int width, int height)
    {
        renderBossBar(stack, batcher, bossBar, originX, originY, width, height, getResolutionScale(width, height));
    }

    private static void renderBossBar(MatrixStack stack, Batcher2D batcher, BossBarState bossBar, int originX, int originY, int width, int height, float resolutionScale)
    {
        float alpha = MathHelper.clamp(bossBar.alpha, 0F, 1F);

        if (alpha <= 0F)
        {
            return;
        }

        float zoom = Math.max(0.05F, bossBar.zoom);
        float widthFactor = Math.max(0.05F, bossBar.width);
        float heightFactor = Math.max(0.05F, bossBar.height);
        float scaleX = resolutionScale * zoom * widthFactor;
        float scaleY = resolutionScale * zoom * heightFactor;
        int displayWidth = Math.max(1, Math.round(BASE_BAR_WIDTH * scaleX));
        int displayHeight = Math.max(1, Math.round(BASE_BAR_HEIGHT * scaleY));
        int x = originX + Math.round(width / 2F + bossBar.x * resolutionScale - displayWidth / 2F);
        int anchorY = originY + Math.round(bossBar.y * resolutionScale);
        float progress = MathHelper.clamp(bossBar.progress, 0F, 1F);
        int progressWidth = MathHelper.ceil(progress * displayWidth);
        boolean hasText = bossBar.text != null && !bossBar.text.isEmpty();
        float textScale = Math.max(0.05F, bossBar.textSize * zoom * resolutionScale);
        int fontHeight = batcher.getFont().getHeight();
        int textBlockHeight = hasText ? Math.round(fontHeight * textScale) : 0;
        int textY = anchorY;
        int barY = anchorY + textBlockHeight + (hasText ? TEXT_GAP : 0);
        float blockCenterX = x + displayWidth / 2F;

        batcher.flush();
        stack.push();

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        DrawContext context = batcher.getContext();

        context.fill(x, barY, x + displayWidth, barY + displayHeight, 0xFFFFFFFF);

        if (progressWidth > 0)
        {
            int color = bossBar.color;

            context.fill(x, barY, x + progressWidth, barY + displayHeight, color);
        }

        if (hasText)
        {
            int textWidth = batcher.getFont().getWidth(bossBar.text);
            int textX = Math.round(blockCenterX - textWidth / 2F);
            int textColor = applyAlpha(bossBar.textColor, alpha);
            float textCenterX = textX + textWidth / 2F;
            float textCenterY = textY + fontHeight / 2F;

            if (textScale != 1F)
            {
                stack.push();
                stack.translate(textCenterX, textCenterY, 0F);
                stack.scale(textScale, textScale, 1F);
                stack.translate(-textCenterX, -textCenterY, 0F);
            }

            batcher.text(bossBar.text, textX, textY, textColor, true);

            if (textScale != 1F)
            {
                stack.pop();
            }
        }

        GL11.glDisable(GL11.GL_BLEND);

        stack.pop();
        batcher.flush();
    }

    /* TODO 1.21.11: RenderSystem.setShaderColor removed */

    private static float getResolutionScale(int width, int height)
    {
        if (width <= 0 || height <= 0)
        {
            return 1F;
        }

        return Math.max(0.05F, Math.min(width / REFERENCE_WIDTH, height / REFERENCE_HEIGHT));
    }

    private static int applyAlpha(int color, float alpha)
    {
        int a = MathHelper.clamp(Math.round(MathHelper.clamp(alpha, 0F, 1F) * 255F), 0, 255);

        return (a << 24) | (color & 0x00FFFFFF);
    }
}
