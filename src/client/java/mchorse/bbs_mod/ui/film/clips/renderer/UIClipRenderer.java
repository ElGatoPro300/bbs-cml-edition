package mchorse.bbs_mod.ui.film.clips.renderer;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIClips;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Envelope;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;

import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;

public class UIClipRenderer <T extends Clip> implements IUIClipRenderer<T>
{
    private static final Color ENVELOPE_COLOR = new Color(0, 0, 0, 0.25F);

    /* Temporary objects */
    private static Vector2f vector = new Vector2f();
    private static Vector2f previous = new Vector2f();

    private static final RenderPipeline GUI_TRIANGLES = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/clip_envelope_triangles"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static RenderLayer guiTrianglesLayer;

    private static RenderLayer getTrianglesLayer()
    {
        if (guiTrianglesLayer == null)
        {
            guiTrianglesLayer = RenderLayer.of(BBSMod.MOD_ID + "_clip_envelope_triangles", RenderSetup.builder(GUI_TRIANGLES).translucent().build());
        }

        return guiTrianglesLayer;
    }

    @Override
    public void renderClip(UIContext context, UIClips clips, T clip, Area area, boolean selected, boolean current)
    {
        int y = area.y;
        int h = area.h;

        int left = area.x;
        int right = area.ex();

        if (current)
        {
            int color = BBSSettings.primaryColor.get();

            context.batcher.dropShadow(left + 2, y + 2, right - 2, y + h - 2, 8, Colors.A75 + color, color);
        }

        ClipFactoryData data = clips.getFactory().getData(clip);
        int color = Colors.A100 | data.color;

        if (clip.enabled.get())
        {
            this.renderBackground(context, color, clip, area, selected, current);
        }
        else
        {
            context.batcher.iconArea(Icons.DISABLED, color, left, y, (right - left), h);
        }

        context.batcher.outline(left, y, right, y + h, selected ? Colors.WHITE : Colors.A50);

        if (right - left > 10 && clip.envelope.enabled.get())
        {
            this.renderEnvelope(context, clip.envelope, clip.duration.get(), left + 1, y + 1, right - 1, y + h - 1);
        }

        FontRenderer font = context.batcher.getFont();
        String baseTitle = clip.title.get();
        String typeLabel = UIKeys.C_CLIP.get(clips.getFactory().getType(clip)).get();
        String label = baseTitle;

        if (baseTitle.isEmpty() || BBSSettings.editorClipTypeLabels.get())
        {
            if (baseTitle.isEmpty())
            {
                label = typeLabel;
            }
            else
            {
                label = typeLabel + " - " + baseTitle;
            }
        }

        boolean alignTop = h >= 28;
        int labelY = alignTop ? y + 3 : y + (h - font.getHeight()) / 2;
        float iconAnchorY = alignTop ? 0F : 0.5F;
        int iconY = alignTop ? y + 3 : y + h / 2;

        if (right - left >= 20)
        {
            context.batcher.icon(data.icon, Colors.mulA(Colors.mulRGB(Colors.WHITE, 0.75F), 0.5F), right - 2, iconY, 1F, iconAnchorY);
        }

        if (!label.isEmpty())
        {
            context.batcher.textShadow(label, left + 5, labelY);
        }
    }

    protected void renderBackground(UIContext context, int color, T clip, Area area, boolean selected, boolean current)
    {
        context.batcher.box(area.x, area.y, area.ex(), area.ey(), color);
    }

    /**
     * Render envelope's preview (either through keyframes or simple)
     */
    private void renderEnvelope(UIContext context, Envelope envelope, int duration, int x1, int y1, int x2, int y2)
    {
        Matrix3x2fc matrix = context.batcher.getContext().getMatrices();

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        if (envelope.keyframes.get())
        {
            if (!envelope.channel.isEmpty())
            {
                this.renderEnvelopesKeyframes(builder, matrix, envelope.channel, duration, x1, y1, x2, y2);
            }
        }
        else
        {
            this.renderSimpleEnvelope(builder, matrix, envelope, duration, x1, y1, x2, y2);
        }

        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            getTrianglesLayer().draw(built);
        }
    }

    /**
     * Render keyframe based envelope.
     */
    private void renderEnvelopesKeyframes(BufferBuilder builder, Matrix3x2fc matrix, KeyframeChannel<Double> channel, int duration, int x1, int y1, int x2, int y2)
    {
        Keyframe<Double> prevKeyframe = null;
        int c = ENVELOPE_COLOR.getARGBColor();

        for (Keyframe<Double> keyframe : channel.getKeyframes())
        {
            if (prevKeyframe != null)
            {
                Vector2f point = this.calculateEnvelopePoint(vector, (int) keyframe.getTick(), keyframe.getValue().floatValue(), duration, x1, y1, x2, y2);
                Vector2f prevPoint = this.calculateEnvelopePoint(previous, (int) prevKeyframe.getTick(), prevKeyframe.getValue().floatValue(), duration, x1, y1, x2, y2);

                builder.vertex(matrix, prevPoint.x, y2).color(c);
                builder.vertex(matrix, point.x, point.y).color(c);
                builder.vertex(matrix, prevPoint.x, prevPoint.y).color(c);

                builder.vertex(matrix, point.x, y2).color(c);
                builder.vertex(matrix, point.x, point.y).color(c);
                builder.vertex(matrix, prevPoint.x, y2).color(c);
            }

            prevKeyframe = keyframe;
        }

        /* Finish the end */
        if (prevKeyframe != null && prevKeyframe.getTick() < duration)
        {
            Vector2f point = this.calculateEnvelopePoint(vector, (int) prevKeyframe.getTick(), prevKeyframe.getValue().floatValue(), duration, x1, y1, x2, y2);

            builder.vertex(matrix, point.x, y2).color(c);
            builder.vertex(matrix, x2, point.y).color(c);
            builder.vertex(matrix, point.x, point.y).color(c);

            builder.vertex(matrix, x2, y2).color(c);
            builder.vertex(matrix, x2, point.y).color(c);
            builder.vertex(matrix, point.x, y2).color(c);
        }
    }

    /**
     * Render simple envelope (using start and end values).
     */
    protected void renderSimpleEnvelope(BufferBuilder builder, Matrix3x2fc matrix, Envelope envelope, int duration, int x1, int y1, int x2, int y2)
    {
        /* First triangle */
        int c = ENVELOPE_COLOR.getARGBColor();
        Vector2f point = this.calculateEnvelopePoint(vector, (int) envelope.getStartX(duration), 0, duration, x1, y1, x2, y2);
        builder.vertex(matrix, point.x, point.y).color(c);

        previous.set(point);
        point = this.calculateEnvelopePoint(vector, (int) envelope.getStartDuration(duration), 1, duration, x1, y1, x2, y2);
        builder.vertex(matrix, point.x, y2).color(c);
        builder.vertex(matrix, point.x, point.y).color(c);

        /* Second triangle */
        previous.set(point);
        point = this.calculateEnvelopePoint(vector, (int) envelope.getEndDuration(duration), 1, duration, x1, y1, x2, y2);
        builder.vertex(matrix, point.x, point.y).color(c);
        builder.vertex(matrix, previous.x, y2).color(c);
        builder.vertex(matrix, point.x, y2).color(c);

        /* Third triangle */
        builder.vertex(matrix, point.x, point.y).color(c);
        builder.vertex(matrix, previous.x, previous.y).color(c);
        builder.vertex(matrix, previous.x, y2).color(c);

        /* Fourth triangle */
        previous.set(point);
        point = this.calculateEnvelopePoint(vector, (int) envelope.getEndX(duration), 0, duration, x1, y1, x2, y2);
        builder.vertex(matrix, previous.x, previous.y).color(c);
        builder.vertex(matrix, previous.x, y2).color(c);
        builder.vertex(matrix, point.x, point.y).color(c);
    }

    protected Vector2f calculateEnvelopePoint(Vector2f vector, int tick, float value, int duration, int x1, int y1, int x2, int y2)
    {
        int width = x2 - x1;
        int height = y2 - y1;

        /* 1 - value due to higher numbers are lower on the screen */
        vector.x = MathUtils.clamp((tick / (float) duration) * width + x1, x1, x2);
        vector.y = (1 - MathUtils.clamp(value, 0, 1)) * height + y1;

        return vector;
    }
}