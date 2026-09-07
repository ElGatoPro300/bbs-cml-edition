package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.utils.LightingSettings;
import mchorse.bbs_mod.graphics.line.LineBuilder;
import mchorse.bbs_mod.graphics.line.SolidColorLineRenderer;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarPointerBlock;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.IKeyframeShapeRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.TimelineRuler;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.keyframes.factories.LightingSettingsKeyframeFactory;

import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.Collections;
import java.util.List;

public class UIKeyframeGraph implements IUIKeyframeGraph
{
    private static final int RULER_HEIGHT = 16;

    protected UIKeyframes keyframes;

    protected UIKeyframeSheet sheet;

    protected final Scale yAxis;

    public UIKeyframeGraph(UIKeyframes keyframes, UIKeyframeSheet sheet)
    {
        this.keyframes = keyframes;
        this.sheet = sheet;

        this.yAxis = new Scale(this.keyframes.area, ScrollDirection.VERTICAL).inverse();
    }

    @Override
    public UIKeyframes getHostKeyframes()
    {
        return this.keyframes;
    }

    /* Graphing */

    public int toGraphY(double value)
    {
        return (int) this.yAxis.to(value);
    }

    public double fromGraphY(int mouseY)
    {
        return this.yAxis.from(mouseY);
    }

    /**
     * Whether given mouse coordinates are near the given point?
     */
    private boolean isNear(double x, double y, int mouseX, int mouseY)
    {
        return this.isNear(x, y, mouseX, mouseY, UIKeyframeDopeSheet.DEFAULT_HIT_RADIUS_SQ);
    }

    private boolean isNear(double x, double y, int mouseX, int mouseY, double radiusSq)
    {
        return Math.pow(mouseX - x, 2) + Math.pow(mouseY - y, 2) < radiusSq;
    }

    public void resetViewY(UIKeyframeSheet current)
    {
        this.yAxis.set(0, 2);

        KeyframeChannel channel = current.channel;
        List<Keyframe> keyframes = channel.getKeyframes();
        int c = keyframes.size();

        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        if (c > 1)
        {
            for (int i = 0; i < c; i++)
            {
                Keyframe frame = keyframes.get(i);

                minY = Math.min(minY, frame.getY(i));
                maxY = Math.max(maxY, frame.getY(i));
            }
        }
        else
        {
            minY = -10;
            maxY = 10;

            if (c == 1)
            {
                minY = maxY = channel.get(0).getY(0);
            }
        }

        if (Math.abs(maxY - minY) < 0.01F)
        {
            /* Centerize */
            this.yAxis.setShift(minY);
            this.yAxis.anchor(0.5F);
        }
        else
        {
            /* Spread apart vertically */
            this.yAxis.viewOffset(minY, maxY, this.keyframes.area.h, 30);
        }
    }

    @Override
    public void resetView()
    {
        this.keyframes.resetViewX();
        this.resetViewY(this.sheet);
    }

    @Override
    public UIKeyframeSheet getLastSheet()
    {
        return this.sheet;
    }

    @Override
    public List<UIKeyframeSheet> getSheets()
    {
        return Collections.singletonList(this.sheet);
    }

    @Override
    public void selectByX(int mouseX)
    {
        List keyframes = this.sheet.channel.getKeyframes();

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe keyframe = (Keyframe) keyframes.get(i);
            int x = this.keyframes.toGraphX(keyframe.getTick());
            int y = this.toGraphY(keyframe.getFactory().getY(keyframe.getValue()));

            if (this.isNear(x, y, mouseX, 0))
            {
                this.sheet.selection.add(i);
            }
        }

        this.pickSelected();
    }

    @Override
    public void selectInArea(Area area)
    {
        List keyframes = this.sheet.channel.getKeyframes();

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe keyframe = (Keyframe) keyframes.get(i);
            int x = this.keyframes.toGraphX(keyframe.getTick());
            int y = this.toGraphY(keyframe.getFactory().getY(keyframe.getValue()));

            if (area.isInside(x, y))
            {
                this.sheet.selection.add(i);
            }
        }

        this.pickSelected();
    }

    @Override
    public UIKeyframeSheet getSheet(int mouseY)
    {
        return this.sheet;
    }

    @Override
    public boolean addKeyframe(int mouseX, int mouseY)
    {
        float tick = (float) this.keyframes.fromGraphX(mouseX);
        UIKeyframeSheet sheet = this.sheet;

        if (!Window.isShiftPressed())
        {
            tick = Math.round(tick);
        }

        if (sheet != null)
        {
            this.addKeyframe(sheet, tick, sheet.channel.getFactory().yToValue(this.fromGraphY(mouseY)));
        }

        return sheet != null;
    }

    @Override
    public Pair<Keyframe, KeyframeType> findKeyframe(int mouseX, int mouseY)
    {
        List keyframes = this.sheet.channel.getKeyframes();
        double radiusSq = Window.isCtrlPressed()
            ? UIKeyframeDopeSheet.REMOVE_HIT_RADIUS_SQ
            : UIKeyframeDopeSheet.DEFAULT_HIT_RADIUS_SQ;

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe keyframe = (Keyframe) keyframes.get(i);
            int x = this.keyframes.toGraphX(keyframe.getTick());
            int y = this.toGraphY(keyframe.getFactory().getY(keyframe.getValue()));

            if (this.isNear(x, y, mouseX, mouseY, radiusSq))
            {
                return new Pair<>(keyframe, KeyframeType.REGULAR);
            }

            int lx = this.keyframes.toGraphX(keyframe.getTick() - keyframe.lx);
            int ly = this.toGraphY(keyframe.getFactory().getY(keyframe.getValue()) + keyframe.ly);

            if (this.isNear(lx, ly, mouseX, mouseY, radiusSq))
            {
                return new Pair<>(keyframe, KeyframeType.LEFT_HANDLE);
            }

            int rx = this.keyframes.toGraphX(keyframe.getTick() + keyframe.rx);
            int ry = this.toGraphY(keyframe.getFactory().getY(keyframe.getValue()) + keyframe.ry);

            if (this.isNear(rx, ry, mouseX, mouseY, radiusSq))
            {
                return new Pair<>(keyframe, KeyframeType.RIGHT_HANDLE);
            }
        }

        return null;
    }

    @Override
    public void pickKeyframe(Keyframe keyframe)
    {
        this.keyframes.pickKeyframe(keyframe);
    }

    @Override
    public void selectKeyframe(Keyframe keyframe)
    {
        this.clearSelection();

        UIKeyframeSheet sheet = this.getSheet(keyframe);

        if (sheet != null)
        {
            sheet.selection.add(keyframe);
            this.pickKeyframe(keyframe);

            double x = keyframe.getTick();
            int y = this.toGraphY(keyframe.getFactory().getY(keyframe.getValue()));

            this.keyframes.getXAxis().shiftIntoMiddle(x);
            this.yAxis.shiftIntoMiddle(y);
        }
    }

    @Override
    public void resize()
    {}

    @Override
    public boolean mouseClicked(UIContext context)
    {
        return false;
    }

    @Override
    public void mouseReleased(UIContext context)
    {}

    @Override
    public void mouseScrolled(UIContext context)
    {
        if (context.mouseWheelHorizontal != 0)
        {
            double offsetX = (25F * BBSSettings.scrollingSensitivityHorizontal.get() * context.mouseWheelHorizontal) / this.keyframes.getXAxis().getZoom();

            this.keyframes.getXAxis().setShift(this.keyframes.getXAxis().getShift() - offsetX);
        }
        else
        {
            boolean x = Window.isShiftPressed();
            boolean y = Window.isCtrlPressed();
            boolean none = !x && !y;

            /* Scaling X */
            if (x && !y || none)
            {
                if (context.mouseWheel != 0D)
                {
                    this.keyframes.getXAxis().zoomAnchor(Scale.getAnchorX(context, this.keyframes.area), Math.copySign(this.keyframes.getXAxis().getZoomFactor(), context.mouseWheel));
                }
            }

            /* Scaling Y */
            if (y && !x || none)
            {
                if (context.mouseWheel != 0D)
                {
                    this.yAxis.zoomAnchor(Scale.getAnchorY(context, this.keyframes.area), Math.copySign(this.yAxis.getZoomFactor(), context.mouseWheel));
                }
            }
        }
    }

    @Override
    public void handleMouse(UIContext context, int lastX, int lastY)
    {
        if (this.keyframes.isNavigating())
        {
            int mouseX = context.mouseX;
            int mouseY = context.mouseY;
            double offsetX = (mouseX - lastX) / this.keyframes.getXAxis().getZoom();
            double offsetY = -(mouseY - lastY) / this.yAxis.getZoom();

            this.keyframes.getXAxis().setShift(this.keyframes.getXAxis().getShift() - offsetX);
            this.yAxis.setShift(this.yAxis.getShift() - offsetY);
        }
    }

    @Override
    public void dragKeyframes(UIContext context, Pair<Keyframe, KeyframeType> type, int originalX, int originalY, float originalT, Object originalV)
    {
        if (type == null)
        {
            return;
        }

        IKeyframeFactory factory = this.sheet.channel.getFactory();
        Keyframe keyframe = type.a;

        if (type.b == KeyframeType.REGULAR)
        {
            float offsetX = (float) this.keyframes.fromGraphX(originalX) - originalT;
            double offsetY = this.fromGraphY(originalY) - factory.getY(originalV);

            float fx = (float) this.keyframes.fromGraphX(context.mouseX) - offsetX;
            double graphY = this.fromGraphY(context.mouseY) - offsetY;
            Object fy = factory == KeyframeFactories.LIGHTING_SETTINGS && originalV instanceof LightingSettings lighting
                ? LightingSettingsKeyframeFactory.applyGraphY(lighting, graphY)
                : factory.yToValue(graphY);

            if (!Window.isShiftPressed())
            {
                fx = Math.round(this.keyframes.fromGraphX(context.mouseX) - offsetX);
            }

            this.setTick(fx, false);
            this.setValue(fy, false);
        }
        else if (type.b == KeyframeType.LEFT_HANDLE)
        {
            keyframe.lx = Math.max(0, -(float) ((this.keyframes.fromGraphX(context.mouseX)) - keyframe.getTick()));
            keyframe.ly = (float) (this.fromGraphY(context.mouseY) - factory.getY(originalV));

            if (!Window.isShiftPressed())
            {
                keyframe.rx = keyframe.lx;
                keyframe.ry = -keyframe.ly;
            }
        }
        else if (type.b == KeyframeType.RIGHT_HANDLE)
        {
            keyframe.rx = Math.max(0, (float) ((this.keyframes.fromGraphX(context.mouseX)) - keyframe.getTick()));
            keyframe.ry = (float) (this.fromGraphY(context.mouseY) - factory.getY(originalV));

            if (!Window.isShiftPressed())
            {
                keyframe.lx = keyframe.rx;
                keyframe.ly = -keyframe.ry;
            }
        }

        this.keyframes.triggerChange();
    }

    @Override
    public void render(UIContext context)
    {
        this.renderGrid(context);
        context.batcher.clip(this.keyframes.area.x, this.keyframes.area.y + RULER_HEIGHT, this.keyframes.area.w, this.keyframes.area.h - RULER_HEIGHT, context);
        this.renderGraph(context);
        this.renderPreviewKeyframes(context);
        context.batcher.unclip(context);
    }

    /**
     * Render grid that allows easier to see where are specific ticks
     */
    protected void renderGrid(UIContext context)
    {
        /* Draw horizontal grid */
        Area area = this.keyframes.area;
        TimelineRuler.Step step = TimelineRuler.steps(this.keyframes.getXAxis());
        int mult = step.minor;
        int major = step.major;
        int hx = this.keyframes.getDuration() / mult;
        int ht = (int) this.keyframes.fromGraphX(area.x);

        for (int j = Math.max(ht / mult, 0); j <= hx; j++)
        {
            int x = this.keyframes.toGraphX(j * mult);

            if (x >= area.ex())
            {
                break;
            }

            String label = TimeUtils.formatTime(j * mult);
            boolean majorTick = (j * mult) % major == 0;
            int tickBottom = area.y + RULER_HEIGHT;
            int tickHeight = majorTick ? 8 : 4;

            context.batcher.box(x, area.y, x + 1, area.ey(), majorTick ? 0x1cffffff : 0x0affffff);
            context.batcher.box(x, tickBottom - tickHeight, x + 1, tickBottom, majorTick ? 0xddffffff : 0x77ffffff);

            if (majorTick)
            {
                context.batcher.textShadow(label, x + 4, area.y + 4, Colors.WHITE);
            }
        }

        /* Draw vertical grid */
        int ty = (int) this.fromGraphY(area.ey());
        int by = (int) this.fromGraphY(area.y - 12);

        int min = Math.min(ty, by) - 1;
        int max = Math.max(ty, by) + 1;
        mult = this.yAxis.getMult();

        min -= min % mult + mult;
        max -= max % mult - mult;

        for (int j = 0, c = (max - min) / mult; j < c; j++)
        {
            int y = this.toGraphY(min + j * mult);

            if (y > area.ey())
            {
                continue;
            }

            context.batcher.box(area.x, y, area.ex(), y + 1, 0x10ffffff);
            context.batcher.text(String.valueOf(min + j * mult), area.x + 4, y + 4);
        }
    }

    private void renderPreviewKeyframes(UIContext context)
    {
        Area area = this.keyframes.area;

        /* Render where the keyframe will be duplicated or added */
        if (!area.isInside(context) || TimelineToolbarPointerBlock.blocksPointer(context))
        {
            return;
        }

        float currentTick = (float) this.keyframes.fromGraphX(context.mouseX);

        if (this.keyframes.isStacking())
        {
            UIKeyframeSheet current = this.sheet;
            List<Keyframe> selected = current.selection.getSelected();
            IKeyframeFactory factory = current.channel.getFactory();
            float mMin = Integer.MAX_VALUE;
            float mMax = Integer.MIN_VALUE;

            for (Keyframe keyframe : selected)
            {
                mMin = Math.min(keyframe.getTick(), mMin);
                mMax = Math.max(keyframe.getTick(), mMax);
            }

            float length = mMax - mMin + this.keyframes.getStackOffset();
            int times = (int) Math.max(1, Math.ceil((currentTick - mMax) / length));
            float x = 0;

            for (int i = 0; i < times; i++)
            {
                for (Keyframe keyframe : selected)
                {
                    int y = (int) this.yAxis.to(factory.getY(keyframe.getValue()));
                    float tick = mMax + this.keyframes.getStackOffset() + (keyframe.getTick() - mMin) + x;

                    this.renderPreviewKeyframe(context, current, tick, y, Colors.YELLOW);
                }

                x += length;
            }
        }
        else if (Window.isCtrlPressed())
        {
            UIKeyframeSheet sheet = this.getSheet(context.mouseY);

            if (sheet != null)
            {
                float tick = currentTick;

                if (!Window.isShiftPressed())
                {
                    tick = Math.round(tick);
                }

                this.renderPreviewKeyframe(context, sheet, tick, context.mouseY, Colors.WHITE);
            }
        }
        else if (Window.isAltPressed())
        {
            UIKeyframeSheet current = this.sheet;
            List<Keyframe> selected = current.selection.getSelected();
            IKeyframeFactory factory = current.channel.getFactory();

            for (int i = 0; i < selected.size(); i++)
            {
                Keyframe first = selected.get(0);
                Keyframe keyframe = selected.get(i);
                int y = (int) this.yAxis.to(factory.getY(keyframe.getValue()));

                this.renderPreviewKeyframe(context, current, currentTick + (keyframe.getTick() - first.getTick()), y, Colors.YELLOW);
            }
        }
    }

    protected void renderPreviewKeyframe(UIContext context, UIKeyframeSheet sheet, double tick, int y, int color)
    {
        int x = this.keyframes.toGraphX(tick);
        Area area = this.keyframes.area;

        if (x < area.x || x > area.ex() || y < area.y || y > area.ey())
        {
            return;
        }

        int c;

        if (color == Colors.WHITE)
        {
            float baseOpacity = BBSSettings.keyframePreviewOpacity == null ? 0.75F : BBSSettings.keyframePreviewOpacity.get();
            float a = (float) Math.sin(context.getTickTransition() / 2D) * 0.1F + baseOpacity;

            c = BBSSettings.keyframePreviewHighlight(a);
        }
        else
        {
            float a = (float) Math.sin(context.getTickTransition() / 2D) * 0.15F + 0.85F;

            c = Colors.setA(color, a);
        }

        KeyframeShape shape = KeyframeShape.SQUARE;

        if (BBSSettings.defaultKeyframeShape != null)
        {
            int idx = BBSSettings.defaultKeyframeShape.get();
            KeyframeShape[] values = KeyframeShape.values();

            if (idx >= 0 && idx < values.length)
            {
                shape = values[idx];
            }
        }

        Keyframe preview = new Keyframe("preview", sheet.channel.getFactory());

        preview.setShape(shape);

        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        UIKeyframeDopeSheet.renderShape(preview, context, builder, matrix, x, y, 3, c);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    /**
     * Render the graph
     */
    @SuppressWarnings({"rawtypes", "IntegerDivisionInFloatingPointContext"})
    protected void renderGraph(UIContext context)
    {
        Matrix4f matrix = context.batcher.getContext().getMatrices().peek().getPositionMatrix();

        UIKeyframeSheet sheet = this.sheet;
        List keyframes = sheet.channel.getKeyframes();
        KeyframeSegment segment = new KeyframeSegment();

        /* Render graph */
        LineBuilder lineBuilder = new LineBuilder(0.7F);

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe frame = (Keyframe) keyframes.get(i);
            Keyframe prev = i > 0 ? (Keyframe) keyframes.get(i - 1) : null;
            int x = this.keyframes.toGraphX(frame.getTick());
            int y = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()));

            if (i == 0 && x > this.keyframes.area.x)
            {
                lineBuilder.add(this.keyframes.area.x, y);
            }

            if (prev != null)
            {
                IInterp interp = prev.getInterpolation().getInterp();
                int px = this.keyframes.toGraphX(prev.getTick());
                int py = this.toGraphY(sheet.channel.getFactory().getY(prev.getValue()));

                if (interp == Interpolations.CONST)
                {
                    lineBuilder.add(x, py);
                    lineBuilder.push();
                }
                else if (interp != Interpolations.LINEAR)
                {
                    float steps = 50F;

                    for (int j = 1; j <= steps; j++)
                    {
                        float a = j / steps;

                        segment.setup(prev, frame, prev.getTick() + a * (frame.getTick() - prev.getTick()));

                        float interpolate = this.toGraphY((float) frame.getFactory().getY(segment.createInterpolated()));

                        lineBuilder.add(Lerps.lerp(px, x, a), interpolate);
                    }
                }
            }

            lineBuilder.add(x, y);

            if (i == keyframes.size() - 1 && x < this.keyframes.area.ex())
            {
                lineBuilder.add(this.keyframes.area.ex(), y);
            }

            boolean add = false;

            if (frame.getInterpolation().getInterp() == Interpolations.BEZIER)
            {
                int rx = this.keyframes.toGraphX(frame.getTick() + frame.rx);
                int ry = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()) + frame.ry);

                lineBuilder.push();
                lineBuilder.add(x, y);
                lineBuilder.add(rx, ry);

                add = true;
            }

            if (prev != null && prev.getInterpolation().getInterp() == Interpolations.BEZIER)
            {
                int lx = this.keyframes.toGraphX(frame.getTick() - frame.lx);
                int ly = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()) + frame.ly);

                lineBuilder.push();
                lineBuilder.add(x, y);
                lineBuilder.add(lx, ly);

                add = true;
            }

            if (add)
            {
                lineBuilder.push();
                lineBuilder.add(x, y);
            }
        }

        lineBuilder.render(context.batcher, SolidColorLineRenderer.get(Colors.COLOR.set(Colors.setA(sheet.color, 1F))));

        /* Empty channels (e.g. cinematic tracks before any key is inserted) have nothing
         * to draw as quads — ending an empty BufferBuilder crashes on 1.21+. */
        if (keyframes.isEmpty())
        {
            return;
        }

        /* Render track bars (horizontal lines) */
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        /* Draw keyframe handles (outer) */
        int forcedIndex = 0;

        for (int i = 0; i < keyframes.size(); i++)
        {
            Keyframe frame = (Keyframe) keyframes.get(i);
            Keyframe prev = i > 0 ? (Keyframe) keyframes.get(i - 1) : null;
            float tick = frame.getTick();
            int x1 = this.keyframes.toGraphX(tick);
            int x2 = this.keyframes.toGraphX(tick + frame.getDuration());
            int y = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()));

            /* Render custom duration markers */
            if (x1 != x2)
            {
                int y1 = y - 8 + (forcedIndex % 2 == 1 ? -4 : 0);
                int color = sheet.selection.has(i) ? Colors.WHITE :  Colors.setA(Colors.mulRGB(sheet.color, 0.9F), 0.75F);

                context.batcher.fillRect(builder, matrix, x1, y1 - 2, 1, 5, color, color, color, color);
                context.batcher.fillRect(builder, matrix, x2, y1 - 2, 1, 5, color, color, color, color);
                context.batcher.fillRect(builder, matrix, x1 + 1, y1, x2 - x1, 1, color, color, color, color);

                forcedIndex += 1;
            }

            boolean isPointHover = !TimelineToolbarPointerBlock.blocksPointer(context)
                && this.isNear(
                    this.keyframes.toGraphX(frame.getTick()),
                    y,
                    context.mouseX,
                    context.mouseY,
                    Window.isCtrlPressed() ? UIKeyframeDopeSheet.REMOVE_HIT_RADIUS_SQ : UIKeyframeDopeSheet.DEFAULT_HIT_RADIUS_SQ
                );
            boolean toRemove = Window.isCtrlPressed() && isPointHover;

            if (this.keyframes.isSelecting())
            {
                isPointHover = isPointHover || this.keyframes.getGrabbingArea(context).isInside(x1, y);
            }

            int kc = frame.getColor() != null ? frame.getColor().getRGBColor() | Colors.A100 : sheet.color;
            int c = (sheet.selection.has(i) || isPointHover ? Colors.WHITE : kc) | Colors.A100;

            if (toRemove)
            {
                c = Colors.RED | Colors.A100;
            }

            int offset = toRemove ? 4 : 3;

            UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, x1, y, offset, c);

            if (frame.getInterpolation().getInterp() == Interpolations.BEZIER)
            {
                int rx = this.keyframes.toGraphX(frame.getTick() + frame.rx);
                int ry = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()) + frame.ry);

                UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, rx, ry, 3, c);
            }

            if (prev != null && prev.getInterpolation().getInterp() == Interpolations.BEZIER)
            {
                int lx = this.keyframes.toGraphX(frame.getTick() - frame.lx);
                int ly = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()) + frame.ly);

                UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, lx, ly, 3, c);
            }
        }

        /* Render keyframe handles (inner) */
        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe frame = (Keyframe) keyframes.get(j);
            Keyframe prev = j > 0 ? (Keyframe) keyframes.get(j - 1) : null;
            int y = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()));

            int c = sheet.selection.has(j) ? Colors.ACTIVE : 0;
            int mx = this.keyframes.toGraphX(frame.getTick());
            int mc = c | Colors.A100;
            IKeyframeShapeRenderer shapeResult = UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, mx, y, 2, mc);

            shapeResult.renderKeyframeBackground(context, builder, matrix, mx, y, 2, mc);

            if (frame.getInterpolation().getInterp() == Interpolations.BEZIER)
            {
                int rx = this.keyframes.toGraphX(frame.getTick() + frame.rx);
                int ry = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()) + frame.ry);

                shapeResult = UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, rx, ry, 2, c | Colors.A100);
                shapeResult.renderKeyframeBackground(context, builder, matrix, rx, ry, 2, c | Colors.A100);
            }

            if (prev != null && prev.getInterpolation().getInterp() == Interpolations.BEZIER)
            {
                int lx = this.keyframes.toGraphX(frame.getTick() - frame.lx);
                int ly = this.toGraphY(sheet.channel.getFactory().getY(frame.getValue()) + frame.ly);

                shapeResult = UIKeyframeDopeSheet.renderShape(frame, context, builder, matrix, lx, ly, 2, c | Colors.A100);
                shapeResult.renderKeyframeBackground(context, builder, matrix, lx, ly, 2, c | Colors.A100);
            }
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferRenderer.drawWithGlobalProgram(builder.end());
    }

    @Override
    public void postRender(UIContext context)
    {}

    @Override
    public void saveState(MapType extra)
    {
        extra.putDouble("y_min", this.yAxis.getMinValue());
        extra.putDouble("y_max", this.yAxis.getMaxValue());
    }

    @Override
    public void restoreState(MapType extra)
    {
        this.yAxis.view(extra.getDouble("y_min"), extra.getDouble("y_max"));
    }
}