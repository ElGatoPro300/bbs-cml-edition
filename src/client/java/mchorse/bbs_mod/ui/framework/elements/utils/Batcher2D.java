package mchorse.bbs_mod.ui.framework.elements.utils;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.graphics.GuiQuadMesh;
import mchorse.bbs_mod.graphics.PickerPreviewRenderState;
import mchorse.bbs_mod.graphics.texture.AdoptedTexture;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.text.RtlAwtTextRenderer;
import mchorse.bbs_mod.text.RtlFontManager;
import mchorse.bbs_mod.text.RtlTextEngine;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.gui.render.state.TexturedQuadGuiElementRenderState;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;

import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.joml.Matrix3x2fc;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.util.List;
import java.util.function.Supplier;

/**
 * 2D immediate-mode UI drawing rewritten for 1.21.11.
 */
public class Batcher2D
{
    private static final BlendFunction BLEND = BlendFunction.TRANSLUCENT;

    private static final RenderPipeline GUI_QUADS = RenderPipelines.register(
        guiColorBuilder("gui_color_quads", VertexFormat.DrawMode.QUADS).build()
    );

    private static final RenderPipeline GUI_TRIANGLES = RenderPipelines.register(
        guiColorBuilder("gui_color_triangles", VertexFormat.DrawMode.TRIANGLES).build()
    );

    private static final RenderPipeline GUI_TRIANGLE_FAN = RenderPipelines.register(
        guiColorBuilder("gui_color_triangle_fan", VertexFormat.DrawMode.TRIANGLE_FAN).build()
    );

    private static RenderLayer guiQuadsLayer;
    private static RenderLayer guiTrianglesLayer;
    private static RenderLayer guiTriangleFanLayer;

    private static FontRenderer fontRenderer = new FontRenderer();
    private static FontRenderer vanillaFontRenderer = new FontRenderer();

    private DrawContext context;
    private FontRenderer font;

    private static RenderPipeline.Builder guiColorBuilder(String name, VertexFormat.DrawMode mode)
    {
        return RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/" + name))
            .withVertexFormat(VertexFormats.POSITION_COLOR, mode)
            .withBlend(BLEND)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false);
    }

    private static RenderLayer layer(RenderPipeline pipeline, String name, RenderLayer cached)
    {
        if (cached != null)
        {
            return cached;
        }

        return RenderLayer.of(BBSMod.MOD_ID + "_" + name, RenderSetup.builder(pipeline).translucent().build());
    }

    private static RenderLayer getQuadsLayer()
    {
        return guiQuadsLayer = layer(GUI_QUADS, "gui_color_quads", guiQuadsLayer);
    }

    private static RenderLayer getTrianglesLayer()
    {
        return guiTrianglesLayer = layer(GUI_TRIANGLES, "gui_color_triangles", guiTrianglesLayer);
    }

    private static RenderLayer getTriangleFanLayer()
    {
        return guiTriangleFanLayer = layer(GUI_TRIANGLE_FAN, "gui_color_triangle_fan", guiTriangleFanLayer);
    }

    private static void flush(BufferBuilder builder, RenderLayer renderLayer)
    {
        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            renderLayer.draw(built);
        }
    }

    public static FontRenderer getDefaultTextRenderer()
    {
        /* Lazily (re)load the user-selected .ttf font when configured, then draw the whole UI with it.
           Falls back to Minecraft's default font when no custom font is set or it failed to load. */
        CustomFontManager.ensureLoaded();
        RtlFontManager.ensureLoaded();

        TextRenderer custom = CustomFontManager.getCustomRenderer();

        fontRenderer.setRenderer(custom != null ? custom : MinecraftClient.getInstance().textRenderer);

        return fontRenderer;
    }

    /**
     * Minecraft's default font — used for subtitles and other in-world HUD that must not follow the
     * custom BBS UI font setting.
     */
    public static FontRenderer getVanillaTextRenderer()
    {
        vanillaFontRenderer.setRenderer(MinecraftClient.getInstance().textRenderer);

        return vanillaFontRenderer;
    }

    public Batcher2D(DrawContext context)
    {
        this.context = context;
        this.font = Batcher2D.getDefaultTextRenderer();
    }

    public DrawContext getContext()
    {
        return this.context;
    }

    public void setContext(DrawContext context)
    {
        this.context = context;
    }

    public void drawQuadMesh(GuiQuadMesh mesh)
    {
        if (mesh == null || mesh.isEmpty())
        {
            return;
        }

        ScreenRect scissor = this.context.scissorStack.peekLast();
        ScreenRect bounds = mesh.computeBounds(scissor);

        if (bounds == null)
        {
            return;
        }

        this.context.state.addSimpleElement(new GuiQuadMesh.State(
            RenderPipelines.GUI, TextureSetup.empty(),
            mesh.xs(), mesh.ys(), mesh.colors(), mesh.count(),
            scissor, bounds));
    }

    public void newRootLayer()
    {
        this.context.createNewRootLayer();
    }

    public FontRenderer getFont()
    {
        return this.font;
    }

    private Matrix3x2fc matrix()
    {
        return this.context.getMatrices();
    }

    /* Screen space clipping */

    public void clip(Area area, UIContext context)
    {
        this.clip(area.x, area.y, area.w, area.h, context);
    }

    public void clip(int x, int y, int w, int h, UIContext context)
    {
        this.clip(context.globalX(x), context.globalY(y), w, h, context.menu.width, context.menu.height);
    }

    public void clip(int x, int y, int w, int h, int sw, int sh)
    {
        Matrix3x2fStack matrices = this.context.getMatrices();

        matrices.pushMatrix();
        matrices.identity();
        this.context.enableScissor(x, y, x + w, y + h);
        matrices.popMatrix();
    }

    public void unclip(UIContext context)
    {
        this.unclip(context.menu.width, context.menu.height);
    }

    public void unclip(int sw, int sh)
    {
        this.context.disableScissor();
    }

    /* Solid rectangles */

    public void normalizedBox(float x1, float y1, float x2, float y2, int color)
    {
        float temp = x1;

        x1 = Math.min(x1, x2);
        x2 = Math.max(temp, x2);

        temp = y1;

        y1 = Math.min(y1, y2);
        y2 = Math.max(temp, y2);

        this.box(x1, y1, x2, y2, color);
    }

    public void box(float x1, float y1, float x2, float y2, int color)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, color, color, color, color);
    }

    public void line(int x1, int y1, int x2, int y2, float width, int color)
    {
        this.line((float) x1, (float) y1, (float) x2, (float) y2, width, color);
    }

    public void line(float x1, float y1, float x2, float y2, float width, int color)
    {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);

        if (len <= 0F)
        {
            return;
        }

        float half = width / 2F;
        float nx = -dy / len * half;
        float ny = dx / len * half;

        GuiQuadMesh mesh = new GuiQuadMesh();
        Matrix3x2fc matrix = this.matrix();

        mesh.vertex(matrix, x1 - nx, y1 - ny).color(color);
        mesh.vertex(matrix, x1 + nx, y1 + ny).color(color);
        mesh.vertex(matrix, x2 + nx, y2 + ny).color(color);
        mesh.vertex(matrix, x2 - nx, y2 - ny).color(color);

        this.drawQuadMesh(mesh);
    }

    public void box(float x, float y, float w, float h, int color1, int color2, int color3, int color4)
    {
        int x1 = (int) x;
        int y1 = (int) y;
        int x2 = (int) (x + w);
        int y2 = (int) (y + h);

        if (color1 == color2 && color1 == color3 && color1 == color4)
        {
            this.context.fill(x1, y1, x2, y2, color1);
        }
        else if (color1 == color2 && color3 == color4)
        {
            this.context.fillGradient(x1, y1, x2, y2, color1, color3);
        }
        else
        {
            GuiQuadMesh mesh = new GuiQuadMesh();
            Matrix3x2fc matrix = this.matrix();

            this.fillRect(mesh, matrix, x, y, w, h, color1, color2, color3, color4);
            this.drawQuadMesh(mesh);
        }
    }

    public void fillRect(VertexConsumer builder, Matrix3x2fc matrix, float x, float y, float w, float h, int color1, int color2, int color3, int color4)
    {
        builder.vertex(matrix, x, y).color(color1);
        builder.vertex(matrix, x, y + h).color(color3);
        builder.vertex(matrix, x + w, y + h).color(color4);
        builder.vertex(matrix, x + w, y).color(color2);
    }

    public void bevelBox(int x1, int y1, int x2, int y2, int fill, boolean shadow, boolean border)
    {
        if (border)
        {
            this.box(x1, y1, x2, y2, Colors.A100);

            x1++;
            y1++;
            x2--;
            y2--;
        }

        this.box(x1, y1, x2, y2, fill);

        if (!BBSSettings.interfaceShadows.get())
        {
            return;
        }

        int light = Colors.lerp(fill, Colors.WHITE, 0.35F);

        this.box(x1, y1, x2, y1 + 1, light);
        this.box(x1, y1, x1 + 1, y2, light);

        if (shadow)
        {
            this.box(x1, y2 - 2, x2, y2, Colors.lerp(fill, Colors.A100, 0.4F));
        }
    }

    public void dropShadow(int left, int top, int right, int bottom, int offset, int opaque, int shadow)
    {
        if (offset <= 0)
        {
            return;
        }

        for (int i = 1; i <= offset; i++)
        {
            int color = Colors.lerp(opaque, shadow, (float) i / offset);

            this.outline(left - i, top - i, right + i, bottom + i, color, 1);
        }
    }

    /* Gradients */

    public void gradientHBox(float x1, float y1, float x2, float y2, int leftColor, int rightColor)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, leftColor, rightColor, leftColor, rightColor);
    }

    public void gradientVBox(float x1, float y1, float x2, float y2, int topColor, int bottomColor)
    {
        this.box(x1, y1, x2 - x1, y2 - y1, topColor, topColor, bottomColor, bottomColor);
    }

    public void dropCircleShadow(int x, int y, int radius, int segments, int opaque, int shadow)
    {
    }

    public void dropCircleShadow(int x, int y, int radius, int offset, int segments, int opaque, int shadow)
    {
    }

    /* Outline methods */

    public void outlineCenter(float x, float y, float offset, int color)
    {
        this.outlineCenter(x, y, offset, color, 1);
    }

    public void outlineCenter(float x, float y, float offset, int color, int border)
    {
        this.outline(x - offset, y - offset, x + offset, y + offset, color, border);
    }

    public void outline(float x1, float y1, float x2, float y2, int color)
    {
        this.outline(x1, y1, x2, y2, color, 1);
    }

    public void outline(float x1, float y1, float x2, float y2, int color, int border)
    {
        this.box(x1, y1, x1 + border, y2, color);
        this.box(x2 - border, y1, x2, y2, color);
        this.box(x1 + border, y1, x2 - border, y1 + border, color);
        this.box(x1 + border, y2 - border, x2 - border, y2, color);
    }

    /* Icon */

    private static int darkenWhite(int color)
    {
        return (color & 0xFFFFFF) == 0xFFFFFF ? (color & 0xFF000000) : color;
    }

    public void icon(Icon icon, float x, float y)
    {
        this.icon(icon, Colors.WHITE, x, y);
    }

    public void icon(Icon icon, int color, float x, float y)
    {
        this.icon(icon, color, x, y, 0F, 0F);
    }

    public void icon(Icon icon, float x, float y, float ax, float ay)
    {
        this.icon(icon, Colors.WHITE, x, y, ax, ay);
    }

    public void icon(Icon icon, int color, float x, float y, float ax, float ay)
    {
        if (icon.texture == null)
        {
            return;
        }

        if (BBSSettings.isLightTheme())
        {
            color = darkenWhite(color);
        }

        x -= icon.w * ax;
        y -= icon.h * ay;

        this.texturedBox(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, icon.w, icon.h, icon.x, icon.y, icon.x + icon.w, icon.y + icon.h, icon.textureW, icon.textureH);
    }

    public void iconArea(Icon icon, float x, float y, float w, float h)
    {
        this.iconArea(icon, Colors.WHITE, x, y, w, h);
    }

    public void iconArea(Icon icon, int color, float x, float y, float w, float h)
    {
        if (BBSSettings.isLightTheme())
        {
            color = darkenWhite(color);
        }

        this.texturedArea(BBSModClient.getTextures().getTexture(icon.texture), color, x, y, w, h, icon.x, icon.y, icon.w, icon.h, icon.textureW, icon.textureH);
    }

    public void outlinedIcon(Icon icon, float x, float y, float ax, float ay)
    {
        this.outlinedIcon(icon, x, y, Colors.WHITE, ax, ay);
    }

    public void outlinedIcon(Icon icon, float x, float y, int color, float ax, float ay)
    {
        this.icon(icon, Colors.A100, x - 1, y, ax, ay);
        this.icon(icon, Colors.A100, x + 1, y, ax, ay);
        this.icon(icon, Colors.A100, x, y - 1, ax, ay);
        this.icon(icon, Colors.A100, x, y + 1, ax, ay);
        this.icon(icon, color, x, y, ax, ay);
    }

    /* Textured box */

    public void fullTexturedBox(Texture texture, float x, float y, float w, float h)
    {
        this.fullTexturedBox(texture, Colors.WHITE, x, y, w, h);
    }

    public void fullTexturedBox(Texture texture, int color, float x, float y, float w, float h)
    {
        this.texturedBox(texture, color, x, y, w, h, 0, 0, w, h, (int) w, (int) h);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2)
    {
        this.texturedBox(texture, color, x, y, w, h, u1, v1, u2, v2, texture.width, texture.height);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u, float v)
    {
        this.texturedBox(texture, color, x, y, w, h, u, v, u + w, v + h, texture.width, texture.height);
    }

    public void texturedBox(Texture texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        Identifier id = AdoptedTexture.identifier(texture);

        if (id == null)
        {
            return;
        }

        if (Colors.getA(color) <= 0F)
        {
            color = Colors.opaque(color);
        }

        this.context.drawTexture(RenderPipelines.GUI_TEXTURED, id,
            (int) x, (int) y, u1, v1, (int) w, (int) h,
            (int) (u2 - u1), (int) (v2 - v1), textureW, textureH, color);
    }

    public void texturedBox(GpuTextureView texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        if (texture == null || texture.isClosed() || textureW <= 0 || textureH <= 0 || w <= 0F || h <= 0F)
        {
            return;
        }

        TextureSetup setup = TextureSetup.of(texture, RenderSystem.getSamplerCache().get(
            AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false));
        TexturedQuadGuiElementRenderState state = new TexturedQuadGuiElementRenderState(RenderPipelines.GUI_TEXTURED,
            setup, new Matrix3x2f(this.context.getMatrices()), (int) x, (int) y, (int) (x + w), (int) (y + h),
            u1 / textureW, u2 / textureW, v1 / textureH, v2 / textureH, color, this.context.scissorStack.peekLast());

        if (state.bounds() != null)
        {
            this.context.state.addSimpleElement(state);
        }
    }

    public void texturedBox(int texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        this.drawAdoptedGlTexture(texture, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);
    }

    @Deprecated
    public void texturedBox(Supplier<RenderPipeline> shader, int texture, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        this.drawAdoptedGlTexture(texture, color, x, y, w, h, u1, v1, u2, v2, textureW, textureH);
    }

    private void drawAdoptedGlTexture(int glId, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        Identifier id = AdoptedTexture.identifier(glId, textureW, textureH, true);

        if (id == null)
        {
            return;
        }

        if (Colors.getA(color) <= 0F)
        {
            color = Colors.opaque(color);
        }

        this.context.drawTexture(RenderPipelines.GUI_TEXTURED, id,
            (int) x, (int) y, u1, v1, (int) w, (int) h,
            (int) (u2 - u1), (int) (v2 - v1), textureW, textureH, color);
    }

    private void fillTexturedBox(BufferBuilder builder, Matrix3x2fc matrix, int color, float x, float y, float w, float h, float u1, float v1, float u2, float v2, int textureW, int textureH)
    {
        builder.vertex(matrix, x, y + h).texture(u1 / (float) textureW, v2 / (float) textureH).color(color);
        builder.vertex(matrix, x + w, y + h).texture(u2 / (float) textureW, v2 / (float) textureH).color(color);
        builder.vertex(matrix, x + w, y).texture(u2 / (float) textureW, v1 / (float) textureH).color(color);
        builder.vertex(matrix, x, y + h).texture(u1 / (float) textureW, v2 / (float) textureH).color(color);
        builder.vertex(matrix, x + w, y).texture(u2 / (float) textureW, v1 / (float) textureH).color(color);
        builder.vertex(matrix, x, y).texture(u1 / (float) textureW, v1 / (float) textureH).color(color);
    }

    /* Repeatable textured box */

    public void texturedArea(Texture texture, int color, float x, float y, float w, float h, float u, float v, float tileW, float tileH, int tw, int th)
    {
        if (tileW <= 0 || tileH <= 0)
        {
            return;
        }

        for (float dy = 0; dy < h; dy += tileH)
        {
            float ph = Math.min(tileH, h - dy);

            for (float dx = 0; dx < w; dx += tileW)
            {
                float pw = Math.min(tileW, w - dx);

                this.texturedBox(texture, color, x + dx, y + dy, pw, ph, u, v, u + pw, v + ph, tw, th);
            }
        }
    }

    /* Text with default font */

    public void text(String label, float x, float y, int color)
    {
        this.text(label, x, y, color, false);
    }

    public void flushDraw()
    {
    }

    public void text(String label, float x, float y)
    {
        this.text(label, x, y, Colors.WHITE, false);
    }

    public void textShadow(String label, float x, float y)
    {
        this.text(label, x, y, Colors.WHITE, true);
    }

    public void textShadow(String label, float x, float y, int color)
    {
        this.text(label, x, y, color, true);
    }

    public void text(String label, float x, float y, int color, boolean shadow)
    {
        if (BBSSettings.isLightTheme())
        {
            shadow = false;
            color = darkenWhite(color);
        }

        this.drawTextDirect(label, x, y, color, shadow);
    }

    private void drawTextDirect(String label, float x, float y, int color, boolean shadow)
    {
        if (Colors.getA(color) <= 0F)
        {
            color = Colors.opaque(color);
        }

        this.context.drawText(this.font.getRenderer(), label, (int) x, (int) y, color, shadow);
    }

    /* Text helpers */

    public int wallText(String text, int x, int y, int color, int width)
    {
        return this.wallText(text, x, y, color, width, 12);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight)
    {
        return this.wallText(text, x, y, color, width, lineHeight, 0F, 0F);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight, float ax, float ay)
    {
        return wallText(text, x, y, color, width, lineHeight, ax, ay, true);
    }

    public int wallText(String text, int x, int y, int color, int width, int lineHeight, float ax, float ay, boolean shadow)
    {
        List<String> list = this.font.wrap(text, width);
        int h = (lineHeight * (list.size() - 1)) + this.font.getHeight();

        y -= h * ay;

        for (String string : list)
        {
            this.text(string.toString(), (int) (x + (width - this.font.getWidth(string)) * ax), y, color, shadow);

            y += lineHeight;
        }

        return h;
    }

    public void textCard(String text, float x, float y)
    {
        this.textCard(text, x, y, Colors.WHITE, Colors.A50);
    }

    public void textCard(String text, float x, float y, int color, int background)
    {
        this.textCard(text, x, y, color, background, 3);
    }

    public void textCard(String text, float x, float y, int color, int background, float offset)
    {
        this.textCard(text, x, y, color, background, offset, true);
    }

    public void textCard(String text, float x, float y, int color, int background, float offset, boolean shadow)
    {
        this.textCard(this.font, text, x, y, color, background, offset, shadow);
    }

    public void textCard(FontRenderer font, String text, float x, float y, int color, int background, float offset, boolean shadow)
    {
        int a = background >> 24 & 0xff;

        if (a != 0)
        {
            if (BBSSettings.isLightTheme() && (background & 0xFFFFFF) == 0)
            {
                background = (background & 0xFF000000) | 0xFFFFFF;
            }

            this.box(x - offset, y - offset, x + this.font.getWidth(text) + offset - 1, y + this.font.getHeight() + offset, background);
        }

        this.text(font, text, x, y, color, shadow);
    }

    public void text(FontRenderer font, String label, float x, float y, int color, boolean shadow)
    {
        if (BBSSettings.isLightTheme())
        {
            shadow = false;
            color = darkenWhite(color);
        }

        if (Colors.getA(color) <= 0F)
        {
            color = Colors.opaque(color);
        }

        this.context.drawText(font.getRenderer(), label, (int) x, (int) y, color, shadow);
    }

    public void drawPickerPreview(GpuTextureView texture, int index, int highlightColor, int x, int y, int w, int h)
    {
        if (texture == null || texture.isClosed() || index <= 0 || w <= 0 || h <= 0)
        {
            return;
        }

        TextureSetup setup = TextureSetup.of(texture, RenderSystem.getSamplerCache().get(
            AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE, FilterMode.NEAREST, FilterMode.NEAREST, false));
        PickerPreviewRenderState state = new PickerPreviewRenderState(setup, this.context.getMatrices(),
            x, y, w, h, index, highlightColor, this.context.scissorStack.peekLast());

        if (state.bounds() != null)
        {
            this.context.state.addSimpleElement(state);
        }
    }

    public void flush()
    {
    }
}
