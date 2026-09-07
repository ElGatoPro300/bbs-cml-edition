package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.misc.ImageOverlay;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.client.BBSUniform;
import mchorse.bbs_mod.forms.renderers.utils.FormTextureBlendRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.Quad;
import mchorse.bbs_mod.utils.colors.Color;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class UIImageRenderer
{
    private static final Quad uvQuad = new Quad();
    private static final Matrix4f matrix = new Matrix4f();

    public static void renderImages(MatrixStack stack, Batcher2D batcher, List<ImageOverlay> images)
    {
        if (images.isEmpty())
        {
            return;
        }

        net.minecraft.client.gl.Framebuffer fb = MinecraftClient.getInstance().getFramebuffer();
        int width = fb.textureWidth / 2;
        int height = fb.textureHeight / 2;
        RenderSystem.backupProjectionMatrix();
        /* X/Y rotations move quad corners into Z. The old ±100 near/far clipped
         * those sides as angle increased; size the depth range for screen-scale quads. */
        float zExtent = Math.max(1000F, Math.max(width, height) * 8F);
        Matrix4f ortho = new Matrix4f().ortho(0, width, height, 0, -zExtent, zExtent);

        BBSRendering.setProjectionMatrix(ortho, ProjectionType.ORTHOGRAPHIC);
        BBSRendering.depthFunc(GL11.GL_ALWAYS);
        BBSRendering.disableCull();
        BBSRendering.enableBlend();
        BBSRendering.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (ImageOverlay overlay : images)
        {
            if (overlay.texture == null || overlay.color.a <= 0F || overlay.opacity <= 0F)
            {
                continue;
            }

            float widthPercent = overlay.width / 100F;
            float heightPercent = overlay.height / 100F;
            /* Keep sub-pixel size so width/height keyframes interpolate smoothly
             * instead of stair-stepping on whole pixels (worse over long spans). */
            float fw = widthPercent == 0F ? 0F : width * widthPercent;
            float fh = heightPercent == 0F ? 0F : height * heightPercent;

            if (fw == 0F || fh == 0F)
            {
                continue;
            }

            float x = width * overlay.windowX + overlay.x;
            float y = height * overlay.windowY + overlay.y;

            FormTextureBlendRenderer.draw(overlay.textureBlend, overlay.texture, (link, alphaFactor) ->
            {
                Texture texture = BBSModClient.getTextures().getTexture(link);

                if (texture == null)
                {
                    return;
                }

                float[] uv = computeUV(overlay, texture);
                Color drawColor = overlay.color.copy();

                drawColor.a *= alphaFactor;

                int color = drawColor.getARGBColor();
                float drawX = -fw * overlay.anchorX;
                float drawY = -fh * overlay.anchorY;

                stack.push();
                stack.translate(x, y, 0);

                /* Rotate around the image anchor in XYZ. Legacy "rotation" is Z
                 * (in-plane); rotationX/Y are additive and default to 0 for old films. */
                if (overlay.rotationX != 0F)
                {
                    stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(overlay.rotationX));
                }

                if (overlay.rotationY != 0F)
                {
                    stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(overlay.rotationY));
                }

                if (overlay.rotation != 0F)
                {
                    stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(overlay.rotation));
                }

                texture.setFilterMipmap(overlay.linear, overlay.mipmap);

                ShaderProgram program = BBSShaders.getImageOverlayProgram();

                if (program != null)
                {
                    BBSUniform.set(program, "BlendMode", overlay.blendMode);
                }

                Supplier<ShaderProgram> supplier = program != null ? () -> program : BBSRendering::getPositionTexColorProgram;

                if (overlay.blendMode != 0)
                {
                    batcher.flushDraw();
                    switch (overlay.blendMode)
                    {
                        case 1: /* Multiply — (1 - a*(1-src))*dst = a*src*dst + (1-a)*dst */
                            BBSRendering.blendFunc(GL11.GL_DST_COLOR, GL11.GL_ZERO);
                            break;
                        case 2: /* Screen — 1-(1-src)*(1-dst), smoothly fades to dst with alpha */
                            BBSRendering.blendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR);
                            break;
                        case 3: /* Add / Linear Dodge — src+dst */
                            BBSRendering.blendFunc(GL11.GL_ONE, GL11.GL_ONE);
                            break;
                        case 4: /* Saturation — modulates dest saturation via src color channels */
                            BBSRendering.blendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR);
                            break;
                        case 5: /* Incrustation (Silhouette Luma) — bright src punches hole in dest */
                            BBSRendering.blendFunc(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR);
                            break;
                        case 6: /* Exclusion — src*(1-dst) + dst*(1-src) = src+dst-2*src*dst */
                            BBSRendering.blendFunc(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR);
                            break;
                        case 7: /* Overlay / Vivid Multiply — 2*src*dst (white doubles/brightens, 50% gray neutral, black darkens) */
                            BBSRendering.blendFunc(GL11.GL_DST_COLOR, GL11.GL_SRC_COLOR);
                            break;
                        case 8: /* Color Dodge — src*src + dst */
                            BBSRendering.blendFunc(GL11.GL_SRC_COLOR, GL11.GL_ONE);
                            break;
                    }
                }
                batcher.texturedBox(() -> BBSShaders.imageOverlayPipeline, texture.id, color, drawX, drawY, fw, fh, uv[0], uv[1], uv[2], uv[3], texture.width, texture.height);
                if (overlay.blendMode != 0)
                {
                    batcher.flushDraw();
                    BBSRendering.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                }
                texture.setFilterMipmap(false, false);

                stack.pop();
            });
        }

        RenderSystem.restoreProjectionMatrix();
        BBSRendering.enableCull();
    }

    public static void renderImage(MatrixStack stack, Batcher2D batcher, ImageOverlay overlay)
    {
        if (overlay == null)
        {
            return;
        }

        renderImages(stack, batcher, Collections.singletonList(overlay));
    }

    private static float[] computeUV(ImageOverlay overlay, Texture texture)
    {
        float w = texture.width;
        float h = texture.height;
        float ow = w;
        float oh = h;
        Vector4f crop = overlay.crop;
        float uvTLx = crop.x / w;
        float uvTLy = crop.y / h;
        float uvBRx = 1F - crop.z / w;
        float uvBRy = 1F - crop.w / h;

        uvQuad.p1.set(uvTLx, uvTLy, 0);
        uvQuad.p2.set(uvBRx, uvTLy, 0);
        uvQuad.p3.set(uvTLx, uvBRy, 0);
        uvQuad.p4.set(uvBRx, uvBRy, 0);

        if (overlay.resizeCrop)
        {
            uvTLx = 0F;
            uvTLy = 0F;
            uvBRx = 1F;
            uvBRy = 1F;

            uvQuad.p1.set(uvTLx, uvTLy, 0);
            uvQuad.p2.set(uvBRx, uvTLy, 0);
            uvQuad.p3.set(uvTLx, uvBRy, 0);
            uvQuad.p4.set(uvBRx, uvBRy, 0);
        }

        /* UV shift only — image rotation is applied in screen space above. */
        if (overlay.offsetX != 0F || overlay.offsetY != 0F)
        {
            matrix.identity()
                .translate(overlay.offsetX / ow, overlay.offsetY / oh, 0);

            uvQuad.transform(matrix);
        }

        float u1 = Math.min(Math.min(uvQuad.p1.x, uvQuad.p2.x), Math.min(uvQuad.p3.x, uvQuad.p4.x)) * w;
        float v1 = Math.min(Math.min(uvQuad.p1.y, uvQuad.p2.y), Math.min(uvQuad.p3.y, uvQuad.p4.y)) * h;
        float u2 = Math.max(Math.max(uvQuad.p1.x, uvQuad.p2.x), Math.max(uvQuad.p3.x, uvQuad.p4.x)) * w;
        float v2 = Math.max(Math.max(uvQuad.p1.y, uvQuad.p2.y), Math.max(uvQuad.p3.y, uvQuad.p4.y)) * h;

        return new float[] {u1, v1, u2, v2};
    }
}
