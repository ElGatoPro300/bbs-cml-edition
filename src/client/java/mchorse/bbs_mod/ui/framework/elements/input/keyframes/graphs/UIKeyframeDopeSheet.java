package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.utils.TimeUtils;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.events.register.RegisterClipInteractionEvent;
import mchorse.bbs_mod.events.register.RegisterFilmSyncEvent;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.graphics.GuiQuadMesh;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarPointerBlock;
import mchorse.bbs_mod.ui.film.toolbar.TimelineTrackEligibility;
import mchorse.bbs_mod.ui.film.toolbar.UIInteractionModeOverlay;
import mchorse.bbs_mod.ui.film.toolbar.UIKeyframeSelectNeighborInteraction;
import mchorse.bbs_mod.ui.forms.editors.utils.UIStructureOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIVisibleRenderKeyframeUtils;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.IKeyframeShapeRenderer;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.shapes.KeyframeShapeRenderers;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.tooltips.LabelTooltip;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Scale;
import mchorse.bbs_mod.ui.utils.Scroll;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.TimelineRuler;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeShape;

import net.minecraft.client.render.VertexConsumer;

import org.joml.Matrix3x2fc;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UIKeyframeDopeSheet implements IUIKeyframeGraph
{
    private UIKeyframeSheet hoveredCompactSheet;

    private static final int LEVEL_INDENT = 8;
    private static final int TRACK_LINE_HALF_HEIGHT = 1;
    private static final int TRACKS_BOTTOM_MARGIN = 36;
    private static final int RULER_HEIGHT = 16;
    private static final double COMPANION_SPLIT_RATIO = 0.52D;
    private static final double PRIMARY_LINE_RATIO = 0.30D;
    private static final double COMPANION_LINE_RATIO = 0.72D;

    private UIKeyframes keyframes;

    private List<UIKeyframeSheet> sheets = new ArrayList<>();
    private UIKeyframeSheet lastSheet;

    private Scroll dopeSheet;
    private Scroll sidebarScrollbar;
    private double trackHeight;
    private int topMargin = Math.max(TOP_MARGIN, RULER_HEIGHT);
    private int sidebarScroll;
    private int sidebarScrollMax;
    private boolean sidebarDragging;
    private float sidebarDragRatio;
    private int sidebarWidth = SIDEBAR_WIDTH;

    public static IKeyframeShapeRenderer renderShape(Keyframe frame, UIContext context, VertexConsumer builder, Matrix3x2fc matrix, int x, int y, int offset, int c)
    {
        KeyframeShape keyframeShape = frame.getShape();
        IKeyframeShapeRenderer shape = KeyframeShapeRenderers.SHAPES.get(keyframeShape);

        shape.renderKeyframe(context, builder, matrix, x, y, offset, c);

        return shape;
    }

    public UIKeyframeDopeSheet(UIKeyframes keyframes)
    {
        this.keyframes = keyframes;
        this.dopeSheet = new Scroll(this.keyframes.area);
        this.sidebarScrollbar = new Scroll(new Area(), 1, ScrollDirection.HORIZONTAL);

        this.setTrackHeight(16);
    }

    @Override
    public UIKeyframes getHostKeyframes()
    {
        return this.keyframes;
    }

    public double getTrackHeight()
    {
        return this.trackHeight;
    }

    private float getProvisionalBlinkAlpha(float baseAlpha)
    {
        /* Smooth pulse with a high visible peak so provisional keyframes are easy to spot. */
        float t = (System.currentTimeMillis() % 1200L) / 1200F;
        float wave = 0.5F + 0.5F * (float) Math.sin(t * (float) (Math.PI * 2D));
        float minAlpha = Math.max(baseAlpha * 0.8F, 0.25F);
        float maxAlpha = 0.95F;

        return MathUtils.clamp(minAlpha + (maxAlpha - minAlpha) * wave, 0F, 1F);
    }

    public void setTrackHeight(double height)
    {
        this.trackHeight = MathUtils.clamp(height, 8D, 100D);
        this.dopeSheet.scrollSpeed = (int) this.trackHeight * 2;
        this.refreshScrollSize(true);
    }

    public void setTopMargin(int topMargin)
    {
        this.topMargin = Math.max(RULER_HEIGHT, topMargin);
        this.refreshScrollSize(true);
    }

    private void refreshScrollSize(boolean animateFit)
    {
        this.dopeSheet.scrollSize = (int) this.trackHeight * this.sheets.size() + this.topMargin + TRACKS_BOTTOM_MARGIN;
        this.dopeSheet.fitAfterContentResize(animateFit);
    }

    private int getRowIndex(UIKeyframeSheet sheet)
    {
        if (sheet == null)
        {
            return -1;
        }

        int index = this.sheets.indexOf(sheet);

        if (index >= 0)
        {
            return index;
        }

        for (int i = 0; i < this.sheets.size(); i++)
        {
            UIKeyframeSheet primary = this.sheets.get(i);

            if (primary.companion == sheet)
            {
                return i;
            }
        }

        return -1;
    }

    private int getTrackLineY(UIKeyframeSheet sheet, int rowIndex)
    {
        int rowTop = this.getDopeSheetY(rowIndex);

        if (sheet == null || rowIndex < 0)
        {
            return rowTop + (int) this.trackHeight / 2;
        }

        UIKeyframeSheet primary = CollectionUtils.getSafe(this.sheets, rowIndex);

        if (primary != null && primary.companion == sheet)
        {
            return rowTop + (int) (this.trackHeight * COMPANION_LINE_RATIO);
        }

        if (primary != null && primary.companion != null && primary == sheet)
        {
            return rowTop + (int) (this.trackHeight * PRIMARY_LINE_RATIO);
        }

        return rowTop + (int) this.trackHeight / 2;
    }

    @Override
    public UIKeyframeSheet getSheet(Keyframe keyframe)
    {
        if (keyframe == null)
        {
            return null;
        }

        Object channel = keyframe.getParent();
        UIKeyframeSheet first = null;
        UIKeyframeSheet selected = null;

        for (UIKeyframeSheet sheet : this.sheets)
        {
            UIKeyframeSheet match = null;

            if (sheet.channel == channel)
            {
                match = sheet;
            }
            else if (sheet.companion != null && sheet.companion.channel == channel)
            {
                match = sheet.companion;
            }

            if (match == null)
            {
                continue;
            }

            if (first == null)
            {
                first = match;
            }

            if (match.selection.has(keyframe))
            {
                selected = match;

                /* Prefer nested Color grade row when both could match selection. */
                if (StringUtils.fileName(match.id).equals("color_grade"))
                {
                    return match;
                }
            }
        }

        if (selected != null)
        {
            return selected;
        }

        if (this.lastSheet != null && this.lastSheet.channel == channel)
        {
            return this.lastSheet;
        }

        return first;
    }

    public void rememberSheet(UIKeyframeSheet sheet)
    {
        if (sheet != null)
        {
            this.lastSheet = sheet;
        }
    }

    @Override
    public UIKeyframeSheet getSheet(String id)
    {
        for (UIKeyframeSheet sheet : this.sheets)
        {
            if (sheet.id.equals(id))
            {
                return sheet;
            }

            if (sheet.companion != null && sheet.companion.id.equals(id))
            {
                return sheet.companion;
            }
        }

        return null;
    }

    private String getSidebarTitle(String title, FontRenderer font, int availableWidth)
    {
        return title == null ? "" : title;
    }

    private int getSidebarIconWidth(UIKeyframeSheet sheet)
    {
        int iconWidth = 2 + sheet.level * LEVEL_INDENT;

        if (sheet.groupHeader)
        {
            Icon arrow = this.getGroupArrow(sheet);
            int base = this.isWorldOrModelGroup(sheet) || this.isFormGroup(sheet) ? 2 : 6;

            return base + sheet.level * LEVEL_INDENT + arrow.w + 4;
        }

        Icon arrow = sheet.toggleExpanded != null ? (sheet.expanded ? Icons.UNCOLLAPSED : Icons.COLLAPSED) : null;
        Icon icon = sheet.getIcon();

        iconWidth = 4 + sheet.level * LEVEL_INDENT + (arrow != null ? arrow.w + 4 : 0) + (icon != null ? icon.w + 4 : 0);

        return iconWidth;
    }

    private String getEffectiveSidebarTitle(UIKeyframeSheet sheet)
    {
        if (sheet == null)
        {
            return "";
        }

        if (sheet.groupHeader)
        {
            return sheet.title.get();
        }

        return sheet.title.get();
    }

    private boolean isWorldOrModelGroup(UIKeyframeSheet sheet)
    {
        return sheet.groupKey != null && (sheet.groupKey.endsWith("__world__") || sheet.groupKey.endsWith("__model__") || sheet.groupKey.endsWith("__vanilla_poses__") || sheet.groupKey.endsWith("__vanilla_actions__"));
    }

    private boolean isRootFormGroup(UIKeyframeSheet sheet)
    {
        return sheet.groupHeader && sheet.level == 0 && !this.isWorldOrModelGroup(sheet);
    }

    private boolean isFormGroup(UIKeyframeSheet sheet)
    {
        return sheet.groupHeader && !this.isWorldOrModelGroup(sheet);
    }

    private Icon getGroupArrow(UIKeyframeSheet sheet)
    {
        if (!sheet.groupHeader)
        {
            return null;
        }

        if (this.isWorldOrModelGroup(sheet) || this.isFormGroup(sheet))
        {
            return sheet.groupExpanded ? Icons.UNCOLLAPSED : Icons.COLLAPSED;
        }

        return sheet.groupExpanded ? Icons.ARROW_DOWN : Icons.ARROW_RIGHT;
    }

    /* Graphing */

    public Scroll getYAxis()
    {
        return this.dopeSheet;
    }

    @Override
    public int getSidebarWidth()
    {
        return this.sidebarWidth;
    }

    public void setSidebarWidth(int sidebarWidth)
    {
        int min = 24;
        int max = this.keyframes.area.w > 0 ? Math.max(min, this.keyframes.area.w / 2) : Integer.MAX_VALUE;

        this.sidebarWidth = Math.max(min, Math.min(max, sidebarWidth));
    }

    public boolean isCompactSidebar()
    {
        return this.sidebarWidth < 60;
    }

    public int getDopeSheetY()
    {
        return this.keyframes.area.y + this.topMargin - (int) this.dopeSheet.getScroll();
    }

    public int getDopeSheetY(int sheet)
    {
        return this.getDopeSheetY() + sheet * (int) this.trackHeight;
    }

    public int getDopeSheetY(UIKeyframeSheet sheet)
    {
        return this.getDopeSheetY(this.getRowIndex(sheet));
    }

    public static final double DEFAULT_HIT_RADIUS_SQ = 25D;

    /* Matches the enlarged ctrl-delete diamond (offset 4 -> 6px radius). */
    public static final double REMOVE_HIT_RADIUS_SQ = 36D;

    /**
     * Whether given mouse coordinates are near the given point?
     */
    public static boolean isNear(double x, double y, int mouseX, int mouseY, boolean checkOnlyX)
    {
        return isNear(x, y, mouseX, mouseY, checkOnlyX, DEFAULT_HIT_RADIUS_SQ);
    }

    public static boolean isNear(double x, double y, int mouseX, int mouseY, boolean checkOnlyX, double radiusSq)
    {
        if (checkOnlyX)
        {
            return Math.pow(mouseX - x, 2) < radiusSq;
        }

        return Math.pow(mouseX - x, 2) + Math.pow(mouseY - y, 2) < radiusSq;
    }

    /* Sheet management */

    @Override
    public void resetView()
    {
        this.keyframes.resetViewX();
    }

    @Override
    public UIKeyframeSheet getLastSheet()
    {
        return this.lastSheet == null ? CollectionUtils.getSafe(this.sheets, 0) : this.lastSheet;
    }

    @Override
    public List<UIKeyframeSheet> getSheets()
    {
        return this.sheets;
    }

    public void removeAllSheets()
    {
        this.sheets.clear();
    }

    public void addSheet(UIKeyframeSheet sheet)
    {
        this.sheets.add(sheet);
    }

    /* Selection */

    @Override
    public void clearSelection()
    {
        for (UIKeyframeSheet sheet : this.sheets)
        {
            sheet.selection.clear();

            if (sheet.companion != null)
            {
                sheet.companion.selection.clear();
            }
        }

        this.pickKeyframe(null);
    }

    @Override
    public void selectAll()
    {
        for (UIKeyframeSheet sheet : this.sheets)
        {
            sheet.selection.all();

            if (sheet.companion != null)
            {
                sheet.companion.selection.all();
            }
        }

        this.pickSelected();
    }

    @Override
    public void selectByX(int mouseX)
    {
        for (int i = 0; i < sheets.size(); i++)
        {
            UIKeyframeSheet sheet = sheets.get(i);

            if (sheet.groupHeader)
            {
                continue;
            }

            List keyframes = sheet.channel.getKeyframes();

            for (int j = 0; j < keyframes.size(); j++)
            {
                Keyframe keyframe = (Keyframe) keyframes.get(j);
                int x = this.keyframes.toGraphX(keyframe.getTick());
                int y = this.getTrackLineY(sheet, i);

                if (this.isNear(x, y, mouseX, 0, true))
                {
                    sheet.selection.add(j);
                }
            }

            if (sheet.companion != null)
            {
                List companionFrames = sheet.companion.channel.getKeyframes();

                for (int j = 0; j < companionFrames.size(); j++)
                {
                    Keyframe keyframe = (Keyframe) companionFrames.get(j);
                    int x = this.keyframes.toGraphX(keyframe.getTick());
                    int y = this.getTrackLineY(sheet.companion, i);

                    if (this.isNear(x, y, mouseX, 0, true))
                    {
                        sheet.companion.selection.add(j);
                    }
                }
            }
        }

        this.pickSelected();
    }

    @Override
    public void selectInArea(Area area)
    {
        List<UIKeyframeSheet> sheets = this.getSheets();

        for (int i = 0; i < sheets.size(); i++)
        {
            UIKeyframeSheet sheet = sheets.get(i);
            List keyframes = sheet.channel.getKeyframes();

            for (int j = 0; j < keyframes.size(); j++)
            {
                Keyframe keyframe = (Keyframe) keyframes.get(j);
                int x = this.keyframes.toGraphX(keyframe.getTick());
                int y = this.getTrackLineY(sheet, i);

                if (area.isInside(x, y))
                {
                    sheet.selection.add(j);
                }
            }

            if (sheet.companion != null)
            {
                List companionFrames = sheet.companion.channel.getKeyframes();

                for (int j = 0; j < companionFrames.size(); j++)
                {
                    Keyframe keyframe = (Keyframe) companionFrames.get(j);
                    int x = this.keyframes.toGraphX(keyframe.getTick());
                    int y = this.getTrackLineY(sheet.companion, i);

                    if (area.isInside(x, y))
                    {
                        sheet.companion.selection.add(j);
                    }
                }
            }
        }

        this.pickSelected();
    }

    @Override
    public UIKeyframeSheet getSheet(int mouseY)
    {
        if (mouseY < this.keyframes.area.y + RULER_HEIGHT)
        {
            return null;
        }

        int dopeSheetY = this.getDopeSheetY();
        int index = (mouseY - dopeSheetY) / (int) this.trackHeight;
        UIKeyframeSheet primary = CollectionUtils.getSafe(this.sheets, index);

        if (primary != null && primary.companion != null)
        {
            int rowTop = this.getDopeSheetY(index);

            if (mouseY >= rowTop + (int) (this.trackHeight * COMPANION_SPLIT_RATIO))
            {
                return primary.companion;
            }
        }

        return primary;
    }

    @Override
    public Keyframe addKeyframe(UIKeyframeSheet sheet, float tick, Object value)
    {
        Keyframe keyframe = IUIKeyframeGraph.super.addKeyframe(sheet, tick, value);

        if (keyframe != null && FormUtils.isVisiblePropertyPath(sheet.id))
        {
            UIVisibleRenderKeyframeUtils.syncRenderOnVisibleInsert(sheet.channel, tick);
        }

        return keyframe;
    }

    @Override
    public boolean addKeyframe(int mouseX, int mouseY)
    {
        float tick = (float) this.keyframes.fromGraphX(mouseX);
        UIKeyframeSheet sheet = this.getSheet(mouseY);

        if (!Window.isShiftPressed())
        {
            tick = Math.round(tick);
        }

        if (sheet != null && !sheet.groupHeader)
        {
            this.addKeyframe(sheet, tick, null);
        }

        return sheet != null && !sheet.groupHeader;
    }

    @Override
    public Pair<Keyframe, KeyframeType> findKeyframe(int mouseX, int mouseY)
    {
        UIKeyframeSheet sheet = this.getSheet(mouseY);

        if (sheet == null || sheet.groupHeader)
        {
            return null;
        }

        List keyframes = sheet.channel.getKeyframes();
        int i = this.getRowIndex(sheet);
        double radiusSq = Window.isCtrlPressed() ? REMOVE_HIT_RADIUS_SQ : DEFAULT_HIT_RADIUS_SQ;

        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe keyframe = (Keyframe) keyframes.get(j);
            int x = this.keyframes.toGraphX(keyframe.getTick());
            int y = this.getTrackLineY(sheet, i);

            if (this.isNear(x, y, mouseX, mouseY, false, radiusSq))
            {
                return new Pair<>(keyframe, KeyframeType.REGULAR);
            }
        }

        return null;
    }

    @Override
    public void onCallback(Keyframe keyframe)
    {
        UIKeyframeSheet sheet = this.getSheet(keyframe);

        if (sheet != null)
        {
            this.lastSheet = sheet;
        }
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
            int rowIndex = this.getRowIndex(sheet);
            int y = (int) (rowIndex * this.trackHeight) + this.topMargin;

            this.keyframes.getXAxis().shiftIntoMiddle(x);
            this.dopeSheet.scrollTo((int) (y - (this.dopeSheet.area.h - this.trackHeight) / 2));
        }
    }

    @Override
    public void resize()
    {
        /* Update size only; overscroll fit/animation is handled by
         * refreshScrollSize / renderGraph after content changes. */
        this.dopeSheet.scrollSize = (int) this.trackHeight * this.sheets.size() + this.topMargin + TRACKS_BOTTOM_MARGIN;
    }

    /* Input handling */

    /**
     * Handles expand/collapse clicks on track group headers and nested limb rows
     * in the sidebar without triggering dope-sheet keyframe input.
     *
     * @return {@code true} when a toggle was performed
     */
    public boolean tryHandleSidebarToggleClick(UIContext context)
    {
        if (context.mouseButton != 0 || !this.keyframes.area.isInside(context))
        {
            return false;
        }

        UIKeyframeSheet sheet = this.getSheet(context.mouseY);

        if (sheet == null)
        {
            return false;
        }

        FontRenderer font = context.batcher.getFont();
        String title = this.getEffectiveSidebarTitle(sheet);
        int availableWidth = Math.max(1, this.sidebarWidth - this.getSidebarIconWidth(sheet) - 6);
        String displayTitle = this.getSidebarTitle(title, font, availableWidth);
        Icon arrow = sheet.groupHeader
            ? this.getGroupArrow(sheet)
            : (sheet.toggleExpanded != null ? (sheet.expanded ? Icons.UNCOLLAPSED : Icons.COLLAPSED) : null);

        int levelIndent = this.isCompactSidebar() ? 0 : LEVEL_INDENT;
        int left = this.keyframes.area.x + sheet.level * levelIndent - (this.isCompactSidebar() ? 0 : this.sidebarScroll);

        if (!this.isCompactSidebar() && sheet.groupHeader && !this.isWorldOrModelGroup(sheet) && !this.isFormGroup(sheet))
        {
            left += 4;
        }

        int iconWidth = 2 + (arrow != null ? arrow.w + 4 : 0);
        int clickableWidth = this.isCompactSidebar()
            ? this.sidebarWidth
            : Math.min(this.sidebarWidth - sheet.level * LEVEL_INDENT, iconWidth + font.getWidth(displayTitle) + 6);

        clickableWidth = Math.max(0, clickableWidth);

        if (context.mouseX < left || context.mouseX > left + clickableWidth)
        {
            return false;
        }

        if (sheet.groupHeader && sheet.toggleGroup != null)
        {
            sheet.toggleGroup.run();

            return true;
        }

        if (!sheet.groupHeader && sheet.toggleExpanded != null)
        {
            sheet.toggleExpanded.run();

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseClicked(UIContext context)
    {
        if (this.handleSidebarScrollbarClick(context))
        {
            return true;
        }

        if (this.tryHandleSidebarToggleClick(context))
        {
            return true;
        }

        return this.dopeSheet.mouseClicked(context);
    }

    @Override
    public void mouseReleased(UIContext context)
    {
        this.dopeSheet.mouseReleased(context);
        this.sidebarScrollbar.mouseReleased(context);
        this.sidebarDragging = false;
    }

    @Override
    public void mouseScrolled(UIContext context)
    {
        Area area = this.keyframes.area;
        boolean inSidebar = area.isInside(context) && context.mouseX < area.x + this.sidebarWidth;

        if (inSidebar)
        {
            this.updateSidebarScrollLimits(context);
        }

        /* When hovering tracker names, wheel input should drive sidebar's horizontal scroll.
         * Priority:
         * 1) Real horizontal wheel
         * 2) Shift + vertical wheel
         * 3) Vertical wheel fallback
         */
        if (inSidebar && (context.mouseWheelHorizontal != 0D || context.mouseWheel != 0D))
        {
            if (this.sidebarScrollMax <= 0)
            {
                return;
            }

            double wheel = context.mouseWheelHorizontal;

            if (wheel == 0D)
            {
                wheel = context.mouseWheel;
            }

            float sensitivity = BBSSettings.scrollingSensitivityHorizontal.get();
            int delta = (int) Math.round(25F * sensitivity * wheel);

            if (delta == 0)
            {
                delta = wheel > 0 ? 1 : -1;
            }

            this.sidebarScrollbar.scrollBy(-delta);
            this.sidebarScrollbar.updateTarget();
            this.sidebarScroll = (int) Math.round(this.sidebarScrollbar.getScroll());

            return;
        }

        if (context.mouseWheelHorizontal != 0)
        {
            double offsetX = (25F * BBSSettings.scrollingSensitivityHorizontal.get() * context.mouseWheelHorizontal) / this.keyframes.getXAxis().getZoom();

            this.keyframes.getXAxis().setShift(this.keyframes.getXAxis().getShift() - offsetX);
        }
        else if (Window.isShiftPressed())
        {
            this.dopeSheet.mouseScroll(context);
        }
        else if (Window.isAltPressed())
        {
            this.setTrackHeight(this.trackHeight - context.mouseWheel);
        }
        else if (context.mouseWheel != 0D)
        {
            this.keyframes.getXAxis().zoomAnchor(Scale.getAnchorX(context, this.keyframes.area), Math.copySign(this.keyframes.getXAxis().getZoomFactor(), context.mouseWheel));
        }
    }

    @Override
    public void handleMouse(UIContext context, int lastX, int lastY)
    {
        this.dopeSheet.drag(context);

        if (this.sidebarDragging)
        {
            this.scrollSidebarToMouse(context.mouseX);
        }

        this.sidebarScroll = (int) Math.round(this.sidebarScrollbar.getScroll());

        if (this.keyframes.isNavigating())
        {
            int mouseX = context.mouseX;
            int mouseY = context.mouseY;
            double offset = (mouseX - lastX) / this.keyframes.getXAxis().getZoom();

            this.keyframes.getXAxis().setShift(this.keyframes.getXAxis().getShift() - offset);
            this.dopeSheet.scrollBy(-(mouseY - lastY));
        }
    }

    @Override
    public void dragKeyframes(UIContext context, Pair<Keyframe, KeyframeType> type, int originalX, int originalY, float originalT, Object originalV)
    {
        float offset = (float) (this.keyframes.fromGraphX(originalX) - originalT);
        float tick = (float) this.keyframes.fromGraphX(context.mouseX) - offset;

        if (!Window.isShiftPressed())
        {
            tick = Math.round(this.keyframes.fromGraphX(context.mouseX) - offset);
        }

        this.setTick(tick, false);
        this.keyframes.triggerChange();
    }

    /* Rendering */

    @Override
    public void render(UIContext context)
    {
        this.renderGrid(context);
        context.batcher.clip(this.keyframes.area.x, this.keyframes.area.y + RULER_HEIGHT, this.keyframes.area.w, this.keyframes.area.h - RULER_HEIGHT, context);
        this.renderGraph(context);
        this.keyframes.renderKeyframeInsertPreviews(context);
        this.keyframes.renderKeyframeDuplicatePreviews(context);
        this.keyframes.renderKeyframePastePreviews(context);
        this.keyframes.renderKeyframeSelectSamePreview(context);
        this.keyframes.renderInteractionTickPulse(context);
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

            if (x < area.x + this.sidebarWidth)
            {
                continue;
            }

            String label = TimeUtils.formatTime(j * mult);
            boolean majorTick = (j * mult) % major == 0;
            int tickBottom = area.y + RULER_HEIGHT;
            int tickHeight = majorTick ? 8 : 4;

            context.batcher.box(x, area.y, x + 1, area.ey(), majorTick ? 0x1cffffff : 0x0affffff);
            context.batcher.box(x, tickBottom - tickHeight, x + 1, tickBottom, majorTick ? 0xddffffff : 0x77ffffff);

            if (majorTick)
            {
                context.batcher.textShadow(label, x + 4, area.y + 2, Colors.WHITE);
            }
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

        if (this.keyframes.isStacking())
        {
            List<UIKeyframeSheet> sheets = new ArrayList<>();
            float currentTick = (float) this.keyframes.fromGraphX(context.mouseX);

            for (UIKeyframeSheet sheet : this.getSheets())
            {
                if (sheet.selection.hasAny())
                {
                    sheets.add(sheet);
                }
            }

            for (UIKeyframeSheet current : sheets)
            {
                List<Keyframe> selected = current.selection.getSelected();
                float mmin = Integer.MAX_VALUE;
                float mmax = Integer.MIN_VALUE;

                for (Keyframe keyframe : selected)
                {
                    mmin = Math.min(keyframe.getTick(), mmin);
                    mmax = Math.max(keyframe.getTick(), mmax);
                }

                float length = mmax - mmin + this.keyframes.getStackOffset();
                int times = (int) Math.max(1, Math.ceil((currentTick - mmax) / length));
                float x = 0;

                for (int i = 0; i < times; i++)
                {
                    for (Keyframe keyframe : selected)
                    {
                        float tick = mmax + this.keyframes.getStackOffset() + (keyframe.getTick() - mmin) + x;

                        this.renderPreviewKeyframe(context, current, tick, Colors.YELLOW);
                    }

                    x += length;
                }
            }
        }
        else if (Window.isCtrlPressed() && !this.keyframes.isKeyframeInsertActive())
        {
            UIKeyframeSheet sheet = this.getSheet(context.mouseY);

            if (sheet != null)
            {
                float tick = (float) this.keyframes.fromGraphX(context.mouseX);

                if (!Window.isShiftPressed())
                {
                    tick = Math.round(tick);
                }

                this.renderPreviewKeyframe(context, sheet, tick, Colors.WHITE);
            }
        }
        else if (Window.isAltPressed() && !Window.isShiftPressed() && !this.keyframes.isKeyframeDuplicateActive()
            && !this.keyframes.isKeyframePasteActive())
        {
            float anchor = (float) Math.round(this.keyframes.fromGraphX(context.mouseX));

            this.renderDuplicatePreviews(context, anchor, context.mouseY);
        }
    }

    /**
     * Yellow duplicate previews (Alt+hover and toolbar duplicate interaction).
     */
    public void renderDuplicatePreviews(UIContext context, float anchorTick, int targetMouseY)
    {
        List<UIKeyframeSheet> sheets = new ArrayList<>();

        for (UIKeyframeSheet sheet : this.getSheets())
        {
            if (sheet.selection.hasAny())
            {
                sheets.add(sheet);
            }
        }

        if (sheets.isEmpty())
        {
            return;
        }

        float anchor = anchorTick;

        if (sheets.size() == 1)
        {
            UIKeyframeSheet current = sheets.get(0);
            UIKeyframeSheet hovered = this.getSheet(targetMouseY);

            if (hovered == null || current.channel.getFactory() != hovered.channel.getFactory())
            {
                return;
            }

            if (!this.isTrackRowVisible(hovered))
            {
                return;
            }

            List<Keyframe> selected = current.selection.getSelected();

            for (int i = 0; i < selected.size(); i++)
            {
                Keyframe first = selected.get(0);
                Keyframe keyframe = selected.get(i);

                this.renderPreviewKeyframe(context, hovered, anchor + (keyframe.getTick() - first.getTick()), Colors.YELLOW);
            }
        }
        else
        {
            float min = Float.MAX_VALUE;

            for (UIKeyframeSheet sheet : sheets)
            {
                List<Keyframe> selected = sheet.selection.getSelected();

                for (Keyframe keyframe : selected)
                {
                    min = Math.min(min, keyframe.getTick());
                }
            }

            for (UIKeyframeSheet sheet : sheets)
            {
                if (!this.isTrackRowVisible(sheet))
                {
                    continue;
                }

                List<Keyframe> selected = sheet.selection.getSelected();

                for (int i = 0; i < selected.size(); i++)
                {
                    Keyframe keyframe = selected.get(i);

                    this.renderPreviewKeyframe(context, sheet, anchor + (keyframe.getTick() - min), Colors.YELLOW);
                }
            }
        }
    }

    public boolean canDuplicatePreview(float anchorTick, int targetMouseY)
    {
        List<UIKeyframeSheet> sheets = new ArrayList<>();

        for (UIKeyframeSheet sheet : this.getSheets())
        {
            if (sheet.selection.hasAny())
            {
                sheets.add(sheet);
            }
        }

        if (sheets.isEmpty())
        {
            return false;
        }

        if (sheets.size() == 1)
        {
            UIKeyframeSheet current = sheets.get(0);
            UIKeyframeSheet hovered = this.getSheet(targetMouseY);

            return hovered != null && current.channel.getFactory() == hovered.channel.getFactory();
        }

        return true;
    }

    /**
     * Yellow paste previews (toolbar paste-at-cursor interaction).
     */
    public void renderPastePreviews(UIContext context, float anchorTick, int targetMouseY,
        Map<String, UIKeyframes.PastedKeyframes> keyframes)
    {
        if (keyframes.isEmpty())
        {
            return;
        }

        float anchor = anchorTick;

        if (keyframes.size() == 1)
        {
            UIKeyframes.PastedKeyframes pasted = keyframes.values().iterator().next();

            if (pasted.keyframes.isEmpty())
            {
                return;
            }

            UIKeyframeSheet hovered = this.getSheet(targetMouseY);

            if (hovered == null || hovered.channel.getFactory() != pasted.factory)
            {
                return;
            }

            if (!this.isTrackRowVisible(hovered))
            {
                return;
            }

            float first = pasted.keyframes.get(0).getTick();

            for (Keyframe keyframe : pasted.keyframes)
            {
                this.renderPreviewKeyframe(context, hovered, anchor + (keyframe.getTick() - first), Colors.YELLOW);
            }
        }
        else
        {
            float min = Float.MAX_VALUE;

            for (Map.Entry<String, UIKeyframes.PastedKeyframes> entry : keyframes.entrySet())
            {
                if (entry.getValue().keyframes.isEmpty())
                {
                    continue;
                }

                entry.getValue().keyframes.sort((a, b) -> Float.compare(a.getTick(), b.getTick()));

                min = Math.min(min, entry.getValue().keyframes.get(0).getTick());
            }

            for (Map.Entry<String, UIKeyframes.PastedKeyframes> entry : keyframes.entrySet())
            {
                if (entry.getValue().keyframes.isEmpty())
                {
                    continue;
                }

                float entryMin = entry.getValue().keyframes.get(0).getTick();

                for (UIKeyframeSheet sheet : this.getSheets())
                {
                    if (!sheet.id.equals(entry.getKey()))
                    {
                        continue;
                    }

                    if (!this.isTrackRowVisible(sheet))
                    {
                        continue;
                    }

                    float d = min == Float.MAX_VALUE ? 0F : entryMin - min;

                    for (Keyframe keyframe : entry.getValue().keyframes)
                    {
                        this.renderPreviewKeyframe(context, sheet, anchor + (keyframe.getTick() - entryMin) + d, Colors.YELLOW);
                    }
                }
            }
        }
    }

    public boolean canPastePreview(float anchorTick, int targetMouseY,
        Map<String, UIKeyframes.PastedKeyframes> keyframes)
    {
        if (keyframes.isEmpty())
        {
            return false;
        }

        if (keyframes.size() == 1)
        {
            UIKeyframes.PastedKeyframes pasted = keyframes.values().iterator().next();

            if (pasted.keyframes.isEmpty())
            {
                return false;
            }

            UIKeyframeSheet hovered = this.getSheet(targetMouseY);

            return hovered != null && hovered.channel.getFactory() == pasted.factory;
        }

        return true;
    }

    public boolean isTrackRowVisible(UIKeyframeSheet sheet)
    {
        int y = this.getDopeSheetY(sheet);
        int top = this.keyframes.area.y + RULER_HEIGHT;
        int bottom = this.keyframes.area.ey();

        return y + (int) this.trackHeight > top && y < bottom;
    }

    public void renderPreviewKeyframeAt(UIContext context, UIKeyframeSheet sheet, float tick, int color)
    {
        this.renderPreviewKeyframe(context, sheet, tick, color);
    }

    private void renderPreviewKeyframe(UIContext context, UIKeyframeSheet sheet, double tick, int color)
    {
        int x = this.keyframes.toGraphX(tick);
        int y = this.getTrackLineY(sheet, this.getRowIndex(sheet));
        Area area = this.keyframes.area;
        int minX = area.x + this.sidebarWidth;

        if (x < minX || x > area.ex() || y < area.y || y > area.ey())
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

        Matrix3x2fc matrix = context.batcher.getContext().getMatrices();
        GuiQuadMesh builder = new GuiQuadMesh();

        renderShape(preview, context, builder, matrix, x, y, 3, c);
        context.batcher.drawQuadMesh(builder);
    }

    /**
     * Render the graph
     */
    @SuppressWarnings({"rawtypes", "IntegerDivisionInFloatingPointContext"})
    protected void renderGraph(UIContext context)
    {
        this.hoveredCompactSheet = null;

        if (this.sheets.isEmpty())
        {
            return;
        }

        this.dopeSheet.scrollSize = (int) this.trackHeight * this.sheets.size() + this.topMargin + TRACKS_BOTTOM_MARGIN;
        this.dopeSheet.fitAfterContentResize(true);

        Area area = this.keyframes.area;
        this.updateSidebarScrollLimits(context);
        Matrix3x2fc matrix = context.batcher.getContext().getMatrices();

        int sidebarX = area.x - this.sidebarScroll;

        for (int i = 0; i < this.sheets.size(); i++)
        {
            int y = this.getDopeSheetY(i);

            if (y + this.trackHeight < area.y || y > area.ey())
            {
                continue;
            }

            UIKeyframeSheet sheet = this.sheets.get(i);
            List keyframes = sheet.channel.getKeyframes();

            boolean hover = !TimelineToolbarPointerBlock.blocksPointer(context)
                && area.isInside(context) && context.mouseY >= y && context.mouseY < y + this.trackHeight;
            int my = sheet.companion != null
                ? y + (int) (this.trackHeight * PRIMARY_LINE_RATIO)
                : y + (int) this.trackHeight / 2;
            int cc = Colors.setA(sheet.color, hover ? 0.8F : 0.35F);
            int startX = area.x + this.sidebarWidth;
            int endX = area.ex();

            if (i % 2 != 0)
            {
                context.batcher.box(startX, y, endX, (float) (y + this.trackHeight), 0x26000000);
            }

            context.batcher.box(startX, (float) (y + this.trackHeight) - 1, endX, (float) (y + this.trackHeight), 0x16000000);

            if (sheet.companion != null)
            {
                int dividerY = y + (int) (this.trackHeight * COMPANION_SPLIT_RATIO);

                context.batcher.box(startX, dividerY, endX, dividerY + 1, 0x44000000);
            }

            if (sheet.groupHeader)
            {
                FontRenderer font = context.batcher.getFont();
                String title = this.getEffectiveSidebarTitle(sheet);
                int availableWidth = Math.max(1, this.sidebarWidth - this.getSidebarIconWidth(sheet) - 6);
                String displayTitle = this.getSidebarTitle(title, font, availableWidth);

                Icon arrow = this.getGroupArrow(sheet);
                int iconX = this.isCompactSidebar()
                    ? sidebarX + (this.sidebarWidth - arrow.w) / 2
                    : sidebarX + (this.isWorldOrModelGroup(sheet) || this.isFormGroup(sheet) ? 2 : 6) + sheet.level * LEVEL_INDENT;

                int iconY = my - arrow.h / 2;
                int textX = iconX + arrow.w + 4;
                int textY = my - font.getHeight() / 2;
                int textW = font.getWidth(displayTitle);

                if (this.isFormGroup(sheet))
                {
                    int primary = BBSSettings.primaryColor.get();
                    int leftColor = Colors.setA(primary, 0.5F);
                    int rightColor = Colors.setA(primary, 0F);

                    context.batcher.box(area.x, y, area.x + 2, (float) (y + this.trackHeight), Colors.A100 | primary);
                    context.batcher.gradientHBox(area.x, y, area.x + this.sidebarWidth, (float) (y + this.trackHeight), leftColor, rightColor);
                }

                context.batcher.clip(area.x, y, this.sidebarWidth, (int) this.trackHeight, context);
                context.batcher.icon(arrow, iconX, iconY);
                if (!this.isCompactSidebar())
                {
                    context.batcher.textShadow(displayTitle, textX, textY);
                }
                context.batcher.unclip(context);

                if (hover && this.isCompactSidebar() && context.mouseX < area.x + this.sidebarWidth)
                {
                    this.hoveredCompactSheet = sheet;
                }

                continue;
            }

            if (this.keyframes.isTrackInteractionActive() && this.keyframes.isTrackInteractionEligible(sheet))
            {
                float alpha = hover
                    ? UIInteractionModeOverlay.getHoveredTrackPulseAlpha()
                    : UIInteractionModeOverlay.getEligibleTrackAlpha();
                int pulseColor = Colors.setA(sheet.color, alpha);

                context.batcher.box(startX, y, endX, (float) (y + this.trackHeight), pulseColor);
            }
            else if (this.keyframes.isSelectNeighborInteractionActive()
                && sheet == this.keyframes.getSelectNeighborHoverSheet())
            {
                int pulseColor = Colors.setA(sheet.color, UIKeyframeSelectNeighborInteraction.getTrackPulseAlpha());

                context.batcher.box(startX, y, endX, (float) (y + this.trackHeight), pulseColor);
            }

            /* Render track bars (horizontal lines) */
            GuiQuadMesh builder = new GuiQuadMesh();

            context.batcher.fillRect(builder, matrix, startX, my - TRACK_LINE_HALF_HEIGHT, endX - startX, TRACK_LINE_HALF_HEIGHT * 2, cc, cc, cc, cc);

            if (sheet.separator)
            {
                int c = Colors.setA(sheet.color, 0F);
                int sepStartX = area.x;

                sepStartX += this.sidebarWidth;

                /* Render separator */
                context.batcher.fillRect(builder, matrix, sepStartX, y, endX - sepStartX, (int) this.trackHeight, c | Colors.A25, c | Colors.A25, c, c);
            }

            /* Render bars indicating same values */
            for (int j = 1; j < keyframes.size(); j++)
            {
                Keyframe previous = (Keyframe) keyframes.get(j - 1);
                Keyframe frame = (Keyframe) keyframes.get(j);
                int c = Colors.YELLOW | Colors.A25;
                int xx = this.keyframes.toGraphX(previous.getTick());
                int xxx = this.keyframes.toGraphX(frame.getTick());

                xx = Math.max(xx, area.x + this.sidebarWidth);
                xxx = Math.max(xxx, area.x + this.sidebarWidth);

                if (previous.getFactory().compare(previous.getValue(), frame.getValue()))
                {
                    if (xxx > xx)
                    {
                        context.batcher.fillRect(builder, matrix, xx, my - TRACK_LINE_HALF_HEIGHT, xxx - xx, TRACK_LINE_HALF_HEIGHT * 2, c, c, c, c);
                    }
                }

                if (Math.abs(xxx - xx) < 5)
                {
                    if (xx >= area.x + this.sidebarWidth)
                    {
                        c = Colors.YELLOW | Colors.A50;

                        context.batcher.fillRect(builder, matrix, xx - 2, my + 5, xxx - xx + 4, 2, c, c, c, c);
                    }
                }
            }

            /* Draw keyframe handles (outer) */
            int forcedIndex = 0;

            for (int j = 0; j < keyframes.size(); j++)
            {
                Keyframe frame = (Keyframe) keyframes.get(j);
                float tick = frame.getTick();
                int x1 = this.keyframes.toGraphX(tick);
                int x2 = this.keyframes.toGraphX(tick + frame.getDuration());

                /* Render custom duration markers */
                if (x1 != x2)
                {
                    int rx1 = x1;
                    int rx2 = x2;

                    rx1 = Math.max(x1, area.x + this.sidebarWidth);
                    rx2 = Math.max(x2, area.x + this.sidebarWidth);

                    if (rx2 > rx1)
                    {
                        int y1 = my - 8 + (sheet.companion != null ? 0 : (forcedIndex % 2 == 1 ? -4 : 0));
                        int color = sheet.selection.has(j) ? Colors.WHITE :  Colors.setA(Colors.mulRGB(sheet.color, 0.9F), 0.75F);

                        if (rx1 == x1) context.batcher.fillRect(builder, matrix, rx1, y1 - 2, 1, 5, color, color, color, color);
                        if (rx2 == x2) context.batcher.fillRect(builder, matrix, rx2, y1 - 2, 1, 5, color, color, color, color);
                        context.batcher.fillRect(builder, matrix, rx1, y1, rx2 - rx1, 1, color, color, color, color);
                    }

                    forcedIndex += 1;
                }

                if (x1 < area.x + this.sidebarWidth)
                {
                    continue;
                }

                boolean isPointHover = this.isNear(
                    this.keyframes.toGraphX(frame.getTick()),
                    my,
                    context.mouseX,
                    context.mouseY,
                    Window.isAltPressed() && Window.isShiftPressed(),
                    Window.isCtrlPressed() ? REMOVE_HIT_RADIUS_SQ : DEFAULT_HIT_RADIUS_SQ
                );
                boolean toRemove = Window.isCtrlPressed() && isPointHover;

                if (this.keyframes.isSelecting())
                {
                    isPointHover = isPointHover || this.keyframes.getGrabbingArea(context).isInside(x1, my);
                }

                boolean provisional = frame.getColor() != null && frame.getColor().a < 0.99F;
                float blinkAlpha = provisional ? this.getProvisionalBlinkAlpha(frame.getColor().a) : 1F;
                int kc = frame.getColor() != null
                    ? (provisional ? Colors.setA(frame.getColor().getRGBColor(), blinkAlpha) : frame.getColor().getARGBColor())
                    : (sheet.color | Colors.A100);
                int c = sheet.selection.has(j) || isPointHover
                    ? (provisional ? Colors.setA(Colors.WHITE, blinkAlpha) : Colors.WHITE)
                    : kc;

                if (toRemove)
                {
                    c = Colors.RED | Colors.A100;
                }

                int offset = toRemove ? 4 : 3;

                renderShape(frame, context, builder, matrix, x1, my, offset, c);
            }

            /* Render keyframe handles (inner) */
            for (int j = 0; j < keyframes.size(); j++)
            {
                Keyframe frame = (Keyframe) keyframes.get(j);
                int mx = this.keyframes.toGraphX(frame.getTick());

                if (mx < area.x + this.sidebarWidth)
                {
                    continue;
                }

                int c = sheet.selection.has(j) ? Colors.ACTIVE : 0;
                int mc = c | Colors.A100;
                IKeyframeShapeRenderer shapeResult = renderShape(frame, context, builder, matrix, mx, my, 2, mc);

                shapeResult.renderKeyframeBackground(context, builder, matrix, mx, my, 2, mc);
            }

            context.batcher.drawQuadMesh(builder);

            if (sheet.companion != null)
            {
                int companionLineY = y + (int) (this.trackHeight * COMPANION_LINE_RATIO);

                this.renderCompanionChannel(context, matrix, area, startX, endX, companionLineY, sheet.companion, hover);
            }

            FontRenderer font = context.batcher.getFont();
            String title = this.getEffectiveSidebarTitle(sheet);
            int availableWidth = Math.max(1, this.sidebarWidth - this.getSidebarIconWidth(sheet) - 6);
            String displayTitle = this.getSidebarTitle(title, font, availableWidth);

            Icon icon = sheet.getIcon();
            Icon arrow = sheet.toggleExpanded != null ? (sheet.expanded ? Icons.UNCOLLAPSED : Icons.COLLAPSED) : null;

            int iconWidth = 2 + sheet.level * LEVEL_INDENT + (arrow != null ? arrow.w + 4 : 0);
            if (icon != null) iconWidth += icon.w + 4;
            int lw = font.getWidth(displayTitle);

            int totalWidth = iconWidth + lw + 10;

            int c1 = hover ? Colors.setA(sheet.color, 0.28F) : 0x00000000;
            int c2 = hover ? Colors.setA(sheet.color, 0.08F) : 0x00000000;

            context.batcher.box(area.x, y, area.x + 2, y + (int) this.trackHeight, sheet.color | Colors.A100);

            context.batcher.gradientHBox(area.x, y, area.x + this.sidebarWidth, y + (int) this.trackHeight, c1, c2);

            context.batcher.clip(area.x, y, this.sidebarWidth, (int) this.trackHeight, context);

            int labelMy = sheet.companion != null ? y + (int) (this.trackHeight * PRIMARY_LINE_RATIO) : my;

            if (this.isCompactSidebar())
            {
                Icon displayIcon = icon != null ? icon : arrow;

                if (displayIcon != null)
                {
                    int iconX = sidebarX + (this.sidebarWidth - displayIcon.w) / 2;
                    context.batcher.icon(displayIcon, iconX, labelMy - displayIcon.h / 2);
                }
            }
            else
            {
                if (arrow != null)
                {
                    context.batcher.icon(arrow, sidebarX + 4 + sheet.level * LEVEL_INDENT, labelMy - arrow.h / 2);
                }

                int currentX = sidebarX + 4 + sheet.level * LEVEL_INDENT + (arrow != null ? arrow.w + 4 : 0);

                if (icon != null)
                {
                    context.batcher.icon(icon, currentX, labelMy - icon.h / 2);
                    currentX += icon.w + 4;
                }

                if (hover)
                {
                    context.batcher.textShadow(displayTitle, currentX, labelMy - font.getHeight() / 2);
                }
                else
                {
                    context.batcher.textShadow(displayTitle, currentX, labelMy - font.getHeight() / 2, Colors.WHITE & 0xeeffffff);
                }

                if (sheet.companion != null)
                {
                    int companionMy = y + (int) (this.trackHeight * COMPANION_LINE_RATIO);
                    String centerTitle = this.getEffectiveSidebarTitle(sheet.companion);
                    int centerAvailable = Math.max(1, this.sidebarWidth - (sheet.level + 1) * LEVEL_INDENT - 10);
                    String centerDisplay = this.getSidebarTitle(centerTitle, font, centerAvailable);
                    int centerX = sidebarX + 4 + (sheet.level + 1) * LEVEL_INDENT;

                    context.batcher.textShadow(centerDisplay, centerX, companionMy - font.getHeight() / 2, Colors.setA(Colors.WHITE, hover ? 1F : 0.85F));
                }
            }

            context.batcher.unclip(context);

            if (hover && this.isCompactSidebar() && context.mouseX < area.x + this.sidebarWidth)
            {
                this.hoveredCompactSheet = sheet;
            }
        }

        RegisterClipInteractionEvent.postDopeSheetRender(context, area);
    }

    @Override
    public void postRender(UIContext context)
    {
        this.dopeSheet.drag(context);
        this.dopeSheet.renderScrollbar(context.batcher);
        this.renderSidebarScrollbar(context);

        if (this.isCompactSidebar() && this.hoveredCompactSheet != null)
        {
            context.tooltip.area.set(context.mouseX + 8, context.mouseY - 8, 0, 0);
            new LabelTooltip(this.hoveredCompactSheet.title, Direction.RIGHT).renderTooltip(context);
        }
    }

    private void renderSidebarScrollbar(UIContext context)
    {
        if (this.isCompactSidebar())
        {
            return;
        }

        Area area = this.keyframes.area;
        boolean inSidebar = area.isInside(context) && context.mouseX < area.x + this.sidebarWidth;

        this.updateSidebarScrollLimits(context);
        this.updateSidebarScrollbarArea(area);

        if (!inSidebar)
        {
            return;
        }

        int barHeight = this.sidebarScrollbar.getScrollbarWidth();
        int y = area.ey() - barHeight;
        int trackX = area.x;
        int trackW = this.sidebarWidth;
        int scrollbarColor = Colors.setA(BBSSettings.scrollbarShadow.get(), 0.25F);

        context.batcher.box(trackX, y, trackX + trackW, y + barHeight, Colors.A25);

        if (this.sidebarScrollMax <= 0)
        {
            Scroll.bar(context.batcher, trackX, y, trackX + trackW, y + barHeight, scrollbarColor);
            return;
        }

        Area knob = this.sidebarScrollbar.getScrollbarArea();
        Scroll.bar(context.batcher, knob.x, knob.y, knob.ex(), knob.ey(), scrollbarColor);
    }

    private void updateSidebarScrollLimits(UIContext context)
    {
        if (this.isCompactSidebar())
        {
            this.sidebarScrollMax = 0;
            this.sidebarScroll = 0;
            this.sidebarScrollbar.scrollSize = this.sidebarWidth;
            this.sidebarScrollbar.setScroll(0);
            return;
        }
        FontRenderer font = context.batcher.getFont();
        int maxWidth = this.sidebarWidth;

        for (UIKeyframeSheet sheet : this.sheets)
        {
            String title = this.getEffectiveSidebarTitle(sheet);
            int titleWidth = font.getWidth(title);

            if (sheet.groupHeader)
            {
                Icon arrow = this.getGroupArrow(sheet);
                int base = 6 + sheet.level * LEVEL_INDENT;

                if (this.isWorldOrModelGroup(sheet) || this.isFormGroup(sheet))
                {
                    base = 2 + sheet.level * LEVEL_INDENT;
                }

                int width = base + arrow.w + 4 + titleWidth + 4;
                maxWidth = Math.max(maxWidth, width);

                continue;
            }

            Icon arrow = sheet.toggleExpanded != null ? (sheet.expanded ? Icons.UNCOLLAPSED : Icons.COLLAPSED) : null;
            Icon icon = sheet.getIcon();
            int iconWidth = 2 + sheet.level * LEVEL_INDENT + (arrow != null ? arrow.w + 4 : 0) + (icon != null ? icon.w + 4 : 0);
            int totalWidth = iconWidth + titleWidth + 10;

            maxWidth = Math.max(maxWidth, totalWidth);
        }

        this.sidebarScrollMax = Math.max(0, maxWidth - this.sidebarWidth);
        this.sidebarScroll = Math.max(0, Math.min(this.sidebarScrollMax, this.sidebarScroll));
        this.sidebarScrollbar.scrollSize = this.sidebarWidth + this.sidebarScrollMax;
        this.sidebarScrollbar.setScroll(this.sidebarScroll);
    }

    private void updateSidebarScrollbarArea(Area area)
    {
        int barHeight = this.sidebarScrollbar.getScrollbarWidth();

        this.sidebarScrollbar.area.set(area.x, area.ey() - barHeight, this.sidebarWidth, barHeight);
    }

    /* State recovery */

    @Override
    public void saveState(MapType extra)
    {
        extra.putDouble("track_height", this.trackHeight);
        extra.putDouble("scroll", this.dopeSheet.getScroll());
    }

    @Override
    public void restoreState(MapType extra)
    {
        this.setTrackHeight(extra.getDouble("track_height"));
        this.dopeSheet.setScroll(extra.getDouble("scroll"));
    }

    private void renderCompanionChannel(UIContext context, Matrix3x2fc matrix, Area area, int startX, int endX, int lineY, UIKeyframeSheet sheet, boolean rowHover)
    {
        List keyframes = sheet.channel.getKeyframes();
        int cc = Colors.setA(sheet.color, rowHover ? 0.65F : 0.28F);
        GuiQuadMesh builder = new GuiQuadMesh();

        context.batcher.fillRect(builder, matrix, startX, lineY - TRACK_LINE_HALF_HEIGHT, endX - startX, TRACK_LINE_HALF_HEIGHT * 2, cc, cc, cc, cc);

        for (int j = 1; j < keyframes.size(); j++)
        {
            Keyframe previous = (Keyframe) keyframes.get(j - 1);
            Keyframe frame = (Keyframe) keyframes.get(j);
            int c = Colors.YELLOW | Colors.A25;
            int xx = Math.max(this.keyframes.toGraphX(previous.getTick()), area.x + this.sidebarWidth);
            int xxx = Math.max(this.keyframes.toGraphX(frame.getTick()), area.x + this.sidebarWidth);

            if (previous.getFactory().compare(previous.getValue(), frame.getValue()) && xxx > xx)
            {
                context.batcher.fillRect(builder, matrix, xx, lineY - TRACK_LINE_HALF_HEIGHT, xxx - xx, TRACK_LINE_HALF_HEIGHT * 2, c, c, c, c);
            }
        }

        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe frame = (Keyframe) keyframes.get(j);
            int x1 = this.keyframes.toGraphX(frame.getTick());

            if (x1 < area.x + this.sidebarWidth)
            {
                continue;
            }

            boolean isPointHover = this.isNear(
                x1,
                lineY,
                context.mouseX,
                context.mouseY,
                Window.isAltPressed() && Window.isShiftPressed(),
                Window.isCtrlPressed() ? REMOVE_HIT_RADIUS_SQ : DEFAULT_HIT_RADIUS_SQ
            );
            boolean provisional = frame.getColor() != null && frame.getColor().a < 0.99F;
            float blinkAlpha = provisional ? this.getProvisionalBlinkAlpha(frame.getColor().a) : 1F;
            int kc = frame.getColor() != null
                ? (provisional ? Colors.setA(frame.getColor().getRGBColor(), blinkAlpha) : frame.getColor().getARGBColor())
                : (sheet.color | Colors.A100);
            int c = sheet.selection.has(j) || isPointHover
                ? (provisional ? Colors.setA(Colors.WHITE, blinkAlpha) : Colors.WHITE)
                : kc;

            renderShape(frame, context, builder, matrix, x1, lineY, Window.isCtrlPressed() && isPointHover ? 4 : 3, c);
        }

        for (int j = 0; j < keyframes.size(); j++)
        {
            Keyframe frame = (Keyframe) keyframes.get(j);
            int mx = this.keyframes.toGraphX(frame.getTick());

            if (mx < area.x + this.sidebarWidth)
            {
                continue;
            }

            int c = sheet.selection.has(j) ? Colors.ACTIVE : 0;
            int mc = c | Colors.A100;
            IKeyframeShapeRenderer shapeResult = renderShape(frame, context, builder, matrix, mx, lineY, 2, mc);

            shapeResult.renderKeyframeBackground(context, builder, matrix, mx, lineY, 2, mc);
        }

        context.batcher.drawQuadMesh(builder);

        RegisterFilmSyncEvent.postRenderDopeSheet(context, this.keyframes.area);
    }

    private boolean handleSidebarScrollbarClick(UIContext context)
    {
        if (!this.keyframes.area.isInside(context) || this.sidebarScrollMax <= 0)
        {
            return false;
        }

        this.updateSidebarScrollLimits(context);
        this.updateSidebarScrollbarArea(this.keyframes.area);

        if (!this.sidebarScrollbar.area.isInside(context.mouseX, context.mouseY))
        {
            return false;
        }

        Area knob = this.sidebarScrollbar.getScrollbarArea();

        if (knob.w <= 0)
        {
            return false;
        }

        if (knob.isInside(context.mouseX, context.mouseY))
        {
            this.sidebarDragRatio = (context.mouseX - knob.x) / (float) knob.w;
            this.sidebarDragRatio = MathUtils.clamp(this.sidebarDragRatio, 0F, 1F);
        }
        else
        {
            this.sidebarDragRatio = 0.5F;
            this.scrollSidebarToMouse(context.mouseX);
        }

        this.sidebarDragging = true;

        return true;
    }

    private void scrollSidebarToMouse(int mouseX)
    {
        int trackX = this.sidebarScrollbar.area.x;
        int trackW = this.sidebarScrollbar.area.w;
        int knobW = Math.max(1, this.sidebarScrollbar.getScrollbar());
        int maxOffset = Math.max(1, trackW - knobW);
        int minMouse = trackX + Math.round(knobW * this.sidebarDragRatio);
        int maxMouse = trackX + trackW - Math.round(knobW * (1F - this.sidebarDragRatio));
        int clampedMouse = Math.max(minMouse, Math.min(maxMouse, mouseX));
        float progress = (clampedMouse - (trackX + knobW * this.sidebarDragRatio)) / (float) maxOffset;

        progress = MathUtils.clamp(progress, 0F, 1F);
        this.sidebarScrollbar.setScroll(progress * this.sidebarScrollMax);
        this.sidebarScroll = (int) Math.round(this.sidebarScrollbar.getScroll());
    }
}
