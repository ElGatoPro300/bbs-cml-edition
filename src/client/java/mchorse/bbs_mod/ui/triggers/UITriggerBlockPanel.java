package mchorse.bbs_mod.ui.triggers;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.blocks.entities.TriggerBlockEntity;
import mchorse.bbs_mod.camera.CameraUtils;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.TriggerBlockEntityRenderer;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.IFlightSupported;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.AABB;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.colors.Colors;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.joml.Vector2i;
import org.joml.Vector3d;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Trigger block panel with the same windowing system as the model block panel.
 * Three cards: List, Actions, Geometry. Each card has its own drag handle,
 * resizer, dock/region/column metadata, and is persisted across sessions.
 */
public class UITriggerBlockPanel extends UIDashboardPanel implements IFlightSupported
{
    private static final int SIDE_LEFT = 0;
    private static final int SIDE_RIGHT = 1;

    private static final int CARD_LIST = 0;
    private static final int CARD_ACTIONS = 1;
    private static final int CARD_GEOMETRY = 2;

    /* Default layout: List on LEFT, Actions + Geometry stacked on RIGHT. */
    private int leftCardX = 10, leftCardY = 10, leftCardWidth = 220, leftCardHeight = 400;
    private boolean leftCollapsed = false, leftDocked = true, leftVisible = true;
    private int leftDockSide = SIDE_LEFT, leftDockColumn = 0, leftDockRow = 0;

    private int middleCardX = 470, middleCardY = 410, middleCardWidth = 240, middleCardHeight = 400;
    private boolean middleCollapsed = false, middleDocked = true, middleVisible = true;
    private int middleDockSide = SIDE_RIGHT, middleDockColumn = 0, middleDockRow = 1;

    private int rightCardX = 470, rightCardY = 10, rightCardWidth = 240, rightCardHeight = 200;
    private boolean rightCollapsed = false, rightDocked = true, rightVisible = true;
    private int rightDockSide = SIDE_RIGHT, rightDockColumn = 0, rightDockRow = 0;

    private float leftWeight = 1F, middleWeight = 1F, rightWeight = 1F;

    /* Pre-drag snapshot — see model block panel for rationale. */
    private final boolean[] prevDragging = new boolean[3];
    private final int[] snapX = new int[3];
    private final int[] snapY = new int[3];
    private final int[] snapW = new int[3];
    private final int[] snapH = new int[3];
    private boolean dragSnapshotValid;

    public UIScrollView middleScrollView;
    public UIScrollView rightScrollView;

    public UIModelBlockPanel.UIPanelDragHandle leftDragHandle;
    public UIModelBlockPanel.UIPanelDragHandle middleDragHandle;
    public UIModelBlockPanel.UIPanelDragHandle rightDragHandle;

    public UIDraggable leftCardResizer;
    public UIDraggable middleCardResizer;
    public UIDraggable rightCardResizer;

    public UITriggerBlockEntityList list;
    public UITriggerEditor editor;

    private TriggerBlockEntity entity;
    private TriggerBlockEntity hovered;
    private final Set<TriggerBlockEntity> toSave = new HashSet<>();

    public UITriggerBlockPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.list = new UITriggerBlockEntityList((l) -> this.fill(l.get(0), false))
        {
            @Override
            public void render(UIContext context)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF141418);
                super.render(context);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF3C3C3C);
            }
        };
        this.list.context((menu) ->
        {
            if (this.entity != null) menu.action(UIKeys.MODEL_BLOCKS_KEYS_TELEPORT, this::teleport);
        });
        this.list.background(0);

        this.editor = new UITriggerEditor();

        this.middleScrollView = UI.scrollView(5, 12, this.editor.actionsContent);
        this.middleScrollView.scroll.opposite().cancelScrolling();

        this.rightScrollView = UI.scrollView(5, 12, this.editor.geometryContent);
        this.rightScrollView.scroll.opposite().cancelScrolling();

        this.leftDragHandle = new UIModelBlockPanel.UIPanelDragHandle(
            TriggerKeys.TITLE,
            Icons.TRIGGER,
            () -> this.leftCollapsed,
            () -> { this.leftCollapsed = !this.leftCollapsed; this.resize(); this.saveLayout(); },
            (context) -> {
                this.leftDocked = false;
                this.leftCardX = context.mouseX - this.area.x;
                this.leftCardY = context.mouseY - this.area.y;
                this.clampPositions();
                this.resize();
            }
        );
        this.leftDragHandle.reference(() -> new Vector2i(this.area.x + this.leftCardX, this.area.y + this.leftCardY));
        this.leftDragHandle.threshold(4);
        this.leftDragHandle.dragEnd(() -> { this.handleDragEnd(CARD_LIST, this.getContext()); this.saveLayout(); });

        this.leftCardResizer = new UIDraggable((context) ->
        {
            if (this.leftDocked)
            {
                int below = getCardBelow(CARD_LIST);
                if (below != -1 && !this.leftCollapsed && !isCardCollapsed(below))
                {
                    resizeCardHeight(CARD_LIST, below, context.mouseY);
                }
                else
                {
                    resizeCardWidth(CARD_LIST, context.mouseX);
                }
            }
            else
            {
                int minWidth = 180;
                int maxWidth = Math.max(minWidth, this.area.w / 2);
                this.leftCardWidth = Math.max(minWidth, Math.min(maxWidth, context.mouseX - (this.area.x + this.leftCardX)));
                int minHeight = 100;
                int maxHeight = this.area.h - this.leftCardY;
                this.leftCardHeight = Math.max(minHeight, Math.min(maxHeight, context.mouseY - (this.area.y + this.leftCardY)));
            }
            this.resize();
        });
        this.leftCardResizer.rendering((c) -> renderResizer(CARD_LIST, this.leftCardResizer, c));
        this.leftCardResizer.dragEnd(this::saveLayout);

        this.middleDragHandle = new UIModelBlockPanel.UIPanelDragHandle(
            IKey.constant("Actions"),
            Icons.PROPERTIES,
            () -> this.middleCollapsed,
            () -> { this.middleCollapsed = !this.middleCollapsed; this.resize(); this.saveLayout(); },
            (context) -> {
                this.middleDocked = false;
                this.middleCardX = context.mouseX - this.area.x;
                this.middleCardY = context.mouseY - this.area.y;
                this.clampPositions();
                this.resize();
            }
        );
        this.middleDragHandle.reference(() -> new Vector2i(this.area.x + this.middleCardX, this.area.y + this.middleCardY));
        this.middleDragHandle.threshold(4);
        this.middleDragHandle.dragEnd(() -> { this.handleDragEnd(CARD_ACTIONS, this.getContext()); this.saveLayout(); });

        this.middleCardResizer = new UIDraggable((context) ->
        {
            if (this.middleDocked)
            {
                int below = getCardBelow(CARD_ACTIONS);
                if (below != -1 && !this.middleCollapsed && !isCardCollapsed(below))
                {
                    resizeCardHeight(CARD_ACTIONS, below, context.mouseY);
                }
                else
                {
                    resizeCardWidth(CARD_ACTIONS, context.mouseX);
                }
            }
            else
            {
                int minWidth = 180;
                int maxWidth = Math.max(minWidth, this.area.w / 2);
                this.middleCardWidth = Math.max(minWidth, Math.min(maxWidth, context.mouseX - (this.area.x + this.middleCardX)));
                int minHeight = 100;
                int maxHeight = this.area.h - this.middleCardY;
                this.middleCardHeight = Math.max(minHeight, Math.min(maxHeight, context.mouseY - (this.area.y + this.middleCardY)));
            }
            this.resize();
        });
        this.middleCardResizer.rendering((c) -> renderResizer(CARD_ACTIONS, this.middleCardResizer, c));
        this.middleCardResizer.dragEnd(this::saveLayout);

        this.rightDragHandle = new UIModelBlockPanel.UIPanelDragHandle(
            IKey.constant("Geometry"),
            Icons.GEAR,
            () -> this.rightCollapsed,
            () -> { this.rightCollapsed = !this.rightCollapsed; this.resize(); this.saveLayout(); },
            (context) -> {
                this.rightDocked = false;
                this.rightCardX = context.mouseX - this.area.x;
                this.rightCardY = context.mouseY - this.area.y;
                this.clampPositions();
                this.resize();
            }
        );
        this.rightDragHandle.reference(() -> new Vector2i(this.area.x + this.rightCardX, this.area.y + this.rightCardY));
        this.rightDragHandle.threshold(4);
        this.rightDragHandle.dragEnd(() -> { this.handleDragEnd(CARD_GEOMETRY, this.getContext()); this.saveLayout(); });

        this.rightCardResizer = new UIDraggable((context) ->
        {
            if (this.rightDocked)
            {
                int below = getCardBelow(CARD_GEOMETRY);
                if (below != -1 && !this.rightCollapsed && !isCardCollapsed(below))
                {
                    resizeCardHeight(CARD_GEOMETRY, below, context.mouseY);
                }
                else
                {
                    resizeCardWidth(CARD_GEOMETRY, context.mouseX);
                }
            }
            else
            {
                int minWidth = 180;
                int maxWidth = Math.max(minWidth, this.area.w / 2);
                this.rightCardWidth = Math.max(minWidth, Math.min(maxWidth, context.mouseX - (this.area.x + this.rightCardX)));
                int minHeight = 100;
                int maxHeight = this.area.h - this.rightCardY;
                this.rightCardHeight = Math.max(minHeight, Math.min(maxHeight, context.mouseY - (this.area.y + this.rightCardY)));
            }
            this.resize();
        });
        this.rightCardResizer.rendering((c) -> renderResizer(CARD_GEOMETRY, this.rightCardResizer, c));
        this.rightCardResizer.dragEnd(this::saveLayout);

        this.loadLayout();

        this.list.relative(this);
        this.leftDragHandle.relative(this);
        this.leftCardResizer.relative(this);
        this.middleDragHandle.relative(this);
        this.middleScrollView.relative(this);
        this.middleCardResizer.relative(this);
        this.rightDragHandle.relative(this);
        this.rightScrollView.relative(this);
        this.rightCardResizer.relative(this);

        this.add(this.leftDragHandle, this.list,
                 this.middleDragHandle, this.middleScrollView,
                 this.rightDragHandle, this.rightScrollView,
                 this.leftCardResizer, this.middleCardResizer, this.rightCardResizer);

        this.keys().register(Keys.MODEL_BLOCKS_TELEPORT, this::teleport);
    }

    /* ----------------------------------------------------------------------
       Card accessors
       ---------------------------------------------------------------------- */

    private int getCardX(int i) { return i == 0 ? this.leftCardX : i == 1 ? this.middleCardX : this.rightCardX; }
    private void setCardX(int i, int x) { if (i == 0) this.leftCardX = x; else if (i == 1) this.middleCardX = x; else this.rightCardX = x; }
    private int getCardY(int i) { return i == 0 ? this.leftCardY : i == 1 ? this.middleCardY : this.rightCardY; }
    private void setCardY(int i, int y) { if (i == 0) this.leftCardY = y; else if (i == 1) this.middleCardY = y; else this.rightCardY = y; }
    private int getCardWidth(int i) { return i == 0 ? this.leftCardWidth : i == 1 ? this.middleCardWidth : this.rightCardWidth; }
    private void setCardWidth(int i, int w) { if (i == 0) this.leftCardWidth = w; else if (i == 1) this.middleCardWidth = w; else this.rightCardWidth = w; }
    private int getCardHeight(int i) { return i == 0 ? this.leftCardHeight : i == 1 ? this.middleCardHeight : this.rightCardHeight; }
    private void setCardHeight(int i, int h) { if (i == 0) this.leftCardHeight = h; else if (i == 1) this.middleCardHeight = h; else this.rightCardHeight = h; }

    private boolean isCardCollapsed(int i) { return i == 0 ? this.leftCollapsed : i == 1 ? this.middleCollapsed : this.rightCollapsed; }
    private void setCardCollapsed(int i, boolean c) { if (i == 0) this.leftCollapsed = c; else if (i == 1) this.middleCollapsed = c; else this.rightCollapsed = c; }
    private boolean isCardVisible(int i) { return i == 0 ? this.leftVisible : i == 1 ? this.middleVisible : this.rightVisible; }
    private void setCardVisible(int i, boolean v) { if (i == 0) this.leftVisible = v; else if (i == 1) this.middleVisible = v; else this.rightVisible = v; }
    private boolean isCardDocked(int i) { return i == 0 ? this.leftDocked : i == 1 ? this.middleDocked : this.rightDocked; }
    private void setCardDocked(int i, boolean d) { if (i == 0) this.leftDocked = d; else if (i == 1) this.middleDocked = d; else this.rightDocked = d; }

    private int getCardDockSide(int i) { return i == 0 ? this.leftDockSide : i == 1 ? this.middleDockSide : this.rightDockSide; }
    private void setCardDockSide(int i, int s) { if (i == 0) this.leftDockSide = s; else if (i == 1) this.middleDockSide = s; else this.rightDockSide = s; }
    private int getCardDockColumn(int i) { return i == 0 ? this.leftDockColumn : i == 1 ? this.middleDockColumn : this.rightDockColumn; }
    private void setCardDockColumn(int i, int c) { if (i == 0) this.leftDockColumn = c; else if (i == 1) this.middleDockColumn = c; else this.rightDockColumn = c; }
    private int getCardDockRow(int i) { return i == 0 ? this.leftDockRow : i == 1 ? this.middleDockRow : this.rightDockRow; }
    private void setCardDockRow(int i, int r) { if (i == 0) this.leftDockRow = r; else if (i == 1) this.middleDockRow = r; else this.rightDockRow = r; }

    private float getCardWeight(int i) { return i == 0 ? this.leftWeight : i == 1 ? this.middleWeight : this.rightWeight; }
    private void setCardWeight(int i, float w) { if (i == 0) this.leftWeight = w; else if (i == 1) this.middleWeight = w; else this.rightWeight = w; }

    /* ----------------------------------------------------------------------
       Layout helpers
       ---------------------------------------------------------------------- */

    private void resizeCardWidth(int i, int mouseX)
    {
        int minWidth = 180;
        int maxWidth = Math.max(minWidth, this.area.w / 2);
        int cardX = getCardX(i);
        int cardW = getCardWidth(i);
        int localMouseX = mouseX - this.area.x;
        int newW = (getCardDockSide(i) == SIDE_LEFT) ? (localMouseX - cardX) : ((cardX + cardW) - localMouseX);

        newW = Math.max(minWidth, Math.min(maxWidth, newW));
        setColumnWidth(getCardDockSide(i), getCardDockColumn(i), newW);
    }

    private void resizeCardHeight(int i, int below, int mouseY)
    {
        int hA = getCardHeight(i);
        int hB = getCardHeight(below);
        int hTotal = hA + hB;
        int new_hA = mouseY - (this.area.y + getCardY(i));
        new_hA = Math.max(40, Math.min(hTotal - 40, new_hA));
        int new_hB = hTotal - new_hA;
        float total = getCardWeight(i) + getCardWeight(below);
        setCardWeight(i, total * ((float) new_hA / hTotal));
        setCardWeight(below, total * ((float) new_hB / hTotal));
    }

    private void setColumnWidth(int side, int column, int w)
    {
        for (int i = 0; i < 3; i++)
        {
            if (isCardVisible(i) && isCardDocked(i) && getCardDockSide(i) == side && getCardDockColumn(i) == column)
            {
                setCardWidth(i, w);
            }
        }
    }

    private List<Integer> columnsOnSide(int side)
    {
        TreeSet<Integer> cols = new TreeSet<>();
        for (int i = 0; i < 3; i++)
        {
            if (isCardVisible(i) && isCardDocked(i) && getCardDockSide(i) == side) cols.add(getCardDockColumn(i));
        }
        return new ArrayList<>(cols);
    }

    private List<Integer> cardsInColumn(int side, int column)
    {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < 3; i++)
        {
            if (isCardVisible(i) && isCardDocked(i) && getCardDockSide(i) == side && getCardDockColumn(i) == column) list.add(i);
        }
        list.sort((a, b) -> Integer.compare(getCardDockRow(a), getCardDockRow(b)));
        return list;
    }

    private void normalizeDockOrder()
    {
        for (int side = 0; side <= SIDE_RIGHT; side++)
        {
            List<Integer> cols = columnsOnSide(side);
            for (int newCol = 0; newCol < cols.size(); newCol++)
            {
                int oldCol = cols.get(newCol);
                List<Integer> cards = cardsInColumn(side, oldCol);
                for (int row = 0; row < cards.size(); row++) setCardDockRow(cards.get(row), row);
                for (int card : cards) setCardDockColumn(card, 100 + newCol);
            }
        }
        for (int i = 0; i < 3; i++)
        {
            if (isCardVisible(i) && isCardDocked(i) && getCardDockColumn(i) >= 100)
            {
                setCardDockColumn(i, getCardDockColumn(i) - 100);
            }
        }
    }

    private int getCardBelow(int i)
    {
        if (!isCardVisible(i) || !isCardDocked(i)) return -1;
        int side = getCardDockSide(i);
        int col = getCardDockColumn(i);
        int row = getCardDockRow(i);
        int belowIdx = -1;
        int minRowBelow = Integer.MAX_VALUE;
        for (int j = 0; j < 3; j++)
        {
            if (j != i && isCardVisible(j) && isCardDocked(j) && getCardDockSide(j) == side && getCardDockColumn(j) == col)
            {
                int r = getCardDockRow(j);
                if (r > row && r < minRowBelow) { minRowBelow = r; belowIdx = j; }
            }
        }
        return belowIdx;
    }

    /* ----------------------------------------------------------------------
       Dock zones, drop preview, drag end
       ---------------------------------------------------------------------- */

    private static class DockZone
    {
        final int side;
        final int column;
        final boolean newColumn;
        final int cx, cy;
        DockZone(int side, int column, boolean newColumn, int cx, int cy)
        {
            this.side = side; this.column = column; this.newColumn = newColumn;
            this.cx = cx; this.cy = cy;
        }
    }

    private List<DockZone> computeDockZones(int draggingCard)
    {
        List<DockZone> zones = new ArrayList<>();
        boolean[] sideHasCard = new boolean[2];

        for (int j = 0; j < 3; j++)
        {
            if (j != draggingCard && isCardVisible(j) && isCardDocked(j))
            {
                int cardX = this.dragSnapshotValid ? this.snapX[j] : getCardX(j);
                int cardY = this.dragSnapshotValid ? this.snapY[j] : getCardY(j);
                int cardW = this.dragSnapshotValid ? this.snapW[j] : getCardWidth(j);
                int cardH = this.dragSnapshotValid ? this.snapH[j] : getCardHeight(j);

                int side = getCardDockSide(j);
                int col = getCardDockColumn(j);
                sideHasCard[side] = true;

                int cx = this.area.x + cardX + cardW / 2;
                int cy = this.area.y + cardY + cardH / 2;

                zones.add(new DockZone(side, col, false, cx, cy));

                int leftSlot = (side == SIDE_LEFT) ? col : col + 1;
                int rightSlot = (side == SIDE_LEFT) ? col + 1 : col;

                zones.add(new DockZone(side, leftSlot, true, this.area.x + cardX + 18, cy));
                zones.add(new DockZone(side, rightSlot, true, this.area.x + cardX + cardW - 18, cy));
            }
        }

        int cy = this.area.y + this.area.h / 2;
        if (!sideHasCard[SIDE_LEFT])
        {
            zones.add(new DockZone(SIDE_LEFT, 0, true, this.area.x + 110, cy));
        }
        if (!sideHasCard[SIDE_RIGHT])
        {
            zones.add(new DockZone(SIDE_RIGHT, 0, true, this.area.ex() - 110, cy));
        }

        return zones;
    }

    private void applyDock(int i, DockZone zone, int dropY)
    {
        setCardDocked(i, true);
        setCardDockSide(i, zone.side);

        if (zone.newColumn)
        {
            for (int j = 0; j < 3; j++)
            {
                if (j != i && isCardVisible(j) && isCardDocked(j) && getCardDockSide(j) == zone.side && getCardDockColumn(j) >= zone.column)
                {
                    setCardDockColumn(j, getCardDockColumn(j) + 1);
                }
            }
            setCardDockColumn(i, zone.column);
            setCardDockRow(i, 0);
        }
        else
        {
            setCardDockColumn(i, zone.column);

            List<Integer> existing = new ArrayList<>();
            for (int j = 0; j < 3; j++)
            {
                if (j != i && isCardVisible(j) && isCardDocked(j) && getCardDockSide(j) == zone.side && getCardDockColumn(j) == zone.column)
                {
                    existing.add(j);
                }
            }
            existing.sort((a, b) -> Integer.compare(getCardDockRow(a), getCardDockRow(b)));

            int insertPos = existing.size();
            for (int k = 0; k < existing.size(); k++)
            {
                int other = existing.get(k);
                int otherMidY = this.dragSnapshotValid ? this.snapY[other] + this.snapH[other] / 2 : getCardY(other) + getCardHeight(other) / 2;
                if (dropY < otherMidY) { insertPos = k; break; }
            }
            existing.add(insertPos, i);
            for (int r = 0; r < existing.size(); r++) setCardDockRow(existing.get(r), r);
        }

        normalizeDockOrder();
    }

    private int[] simulateDrop(int draggingCard, DockZone zone, int dropY)
    {
        boolean[] sDocked = new boolean[3];
        int[] sSide = new int[3], sCol = new int[3], sRow = new int[3];
        int[] sX = new int[3], sY = new int[3], sW = new int[3], sH = new int[3];
        for (int j = 0; j < 3; j++)
        {
            sDocked[j] = isCardDocked(j); sSide[j] = getCardDockSide(j); sCol[j] = getCardDockColumn(j); sRow[j] = getCardDockRow(j);
            sX[j] = getCardX(j); sY[j] = getCardY(j); sW[j] = getCardWidth(j); sH[j] = getCardHeight(j);
        }

        applyDock(draggingCard, zone, dropY);
        clampPositions();
        int[] rect = {getCardX(draggingCard), getCardY(draggingCard), getCardWidth(draggingCard), getCardHeight(draggingCard)};

        for (int j = 0; j < 3; j++)
        {
            setCardDocked(j, sDocked[j]); setCardDockSide(j, sSide[j]); setCardDockColumn(j, sCol[j]); setCardDockRow(j, sRow[j]);
            setCardX(j, sX[j]); setCardY(j, sY[j]); setCardWidth(j, sW[j]); setCardHeight(j, sH[j]);
        }
        return rect;
    }

    private void handleDragEnd(int i, UIContext context)
    {
        if (context == null || this.area == null) return;
        int dropY = context.mouseY - this.area.y;

        DockZone target = null;
        for (DockZone zone : computeDockZones(i))
        {
            if (context.mouseX >= zone.cx - 18 && context.mouseX <= zone.cx + 18 && context.mouseY >= zone.cy - 18 && context.mouseY <= zone.cy + 18)
            {
                target = zone;
                break;
            }
        }

        if (target != null) applyDock(i, target, dropY);
        else { setCardDocked(i, false); normalizeDockOrder(); }

        this.resize();
    }

    /* ----------------------------------------------------------------------
       Resizer position / render
       ---------------------------------------------------------------------- */

    private void positionResizer(int i, UIDraggable resizer)
    {
        int cx = getCardX(i);
        int cy = getCardY(i);
        int cw = getCardWidth(i);
        int ch = getCardHeight(i);

        if (isCardDocked(i))
        {
            int below = getCardBelow(i);
            if (below != -1 && !isCardCollapsed(i) && !isCardCollapsed(below))
            {
                resizer.x(cx).y(cy + ch - 5).w(cw).h(10);
            }
            else
            {
                if (getCardDockSide(i) == SIDE_LEFT) resizer.x(cx + cw - 5).y(0).w(10).h(this.area.h);
                else resizer.x(cx - 5).y(0).w(10).h(this.area.h);
            }
        }
        else
        {
            resizer.x(cx + cw - 10).y(cy + ch - 10).w(10).h(10);
        }
    }

    private void renderResizer(int i, UIDraggable resizer, UIContext c)
    {
        boolean active = resizer.area.isInside(c) || resizer.isDragging();
        if (isCardDocked(i))
        {
            int activeColor = 0xFF000000 | BBSSettings.primaryColor.get();
            int idleColor = 0xAA666666;
            int below = getCardBelow(i);
            if (below != -1 && !isCardCollapsed(i) && !isCardCollapsed(below))
            {
                int midY = resizer.area.y + resizer.area.h / 2;
                int color = active ? activeColor : idleColor;
                c.batcher.box(resizer.area.x, midY - 1, resizer.area.ex(), midY + 1, color);
            }
            else
            {
                int midX = resizer.area.x + resizer.area.w / 2;
                int color = active ? activeColor : idleColor;
                c.batcher.box(midX - 1, resizer.area.y, midX + 1, resizer.area.ey(), color);
            }
        }
        else
        {
            int color = active ? (0xFF000000 | BBSSettings.primaryColor.get()) : 0xFF888888;
            int rx = resizer.area.ex() - 8;
            int ry = resizer.area.ey() - 8;
            c.batcher.box(rx + 2, ry + 5, rx + 6, ry + 6, color);
            c.batcher.box(rx + 4, ry + 3, rx + 6, ry + 4, color);
            c.batcher.box(rx + 5, ry + 1, rx + 6, ry + 2, color);
        }
    }

    /* ----------------------------------------------------------------------
       Layout
       ---------------------------------------------------------------------- */

    private void clampPositions()
    {
        if (this.area == null || this.area.w <= 0 || this.area.h <= 0) return;

        int minWidth = 180;
        int maxWidth = Math.max(minWidth, this.area.w / 2);
        this.leftCardWidth = Math.max(minWidth, Math.min(maxWidth, this.leftCardWidth));
        this.middleCardWidth = Math.max(minWidth, Math.min(maxWidth, this.middleCardWidth));
        this.rightCardWidth = Math.max(minWidth, Math.min(maxWidth, this.rightCardWidth));

        for (int side = SIDE_LEFT; side <= SIDE_RIGHT; side++)
        {
            List<Integer> columns = columnsOnSide(side);
            if (columns.isEmpty()) continue;

            int[] colWidths = new int[columns.size()];
            for (int c = 0; c < columns.size(); c++)
            {
                int max = minWidth;
                for (int card : cardsInColumn(side, columns.get(c))) max = Math.max(max, getCardWidth(card));
                colWidths[c] = max;
            }

            int[] colX = new int[columns.size()];
            if (side == SIDE_LEFT)
            {
                int x = 0;
                for (int c = 0; c < columns.size(); c++) { colX[c] = x; x += colWidths[c]; }
            }
            else
            {
                int x = this.area.w;
                for (int c = 0; c < columns.size(); c++) { x -= colWidths[c]; colX[c] = x; }
            }

            for (int c = 0; c < columns.size(); c++)
            {
                List<Integer> cards = cardsInColumn(side, columns.get(c));
                int count = cards.size();
                if (count == 0) continue;

                int collapsedCount = 0;
                float totalWeight = 0;
                for (int idx : cards)
                {
                    if (isCardCollapsed(idx)) collapsedCount++;
                    else totalWeight += getCardWeight(idx);
                }
                int remainingHeight = this.area.h - (collapsedCount * 22);

                int currentY = 0;
                for (int r = 0; r < count; r++)
                {
                    int idx = cards.get(r);
                    int h;
                    if (isCardCollapsed(idx)) h = 22;
                    else if (totalWeight > 0) h = Math.round(remainingHeight * (getCardWeight(idx) / totalWeight));
                    else
                    {
                        int expandedCount = count - collapsedCount;
                        h = expandedCount > 0 ? remainingHeight / expandedCount : 0;
                    }
                    if (r == count - 1) h = this.area.h - currentY;

                    setCardX(idx, colX[c]); setCardY(idx, currentY); setCardWidth(idx, colWidths[c]); setCardHeight(idx, h);
                    currentY += h;
                }
            }
        }

        for (int i = 0; i < 3; i++)
        {
            if (isCardVisible(i) && !isCardDocked(i))
            {
                int w = getCardWidth(i);
                int h = getCardHeight(i);
                if (isCardCollapsed(i)) h = 22;
                else h = Math.max(100, Math.min(this.area.h - getCardY(i), h));

                int maxCardX = this.area.w - w;
                int maxCardY = this.area.h - h;
                int cx = Math.max(0, Math.min(maxCardX, getCardX(i)));
                int cy = Math.max(0, Math.min(maxCardY, getCardY(i)));

                setCardX(i, cx); setCardY(i, cy); setCardHeight(i, h);
            }
        }
    }

    @Override
    public void resize()
    {
        this.clampPositions();

        /* Title bars */
        this.leftDragHandle.x(this.leftCardX).y(this.leftCardY).w(this.leftCardWidth).h(22);
        this.middleDragHandle.x(this.middleCardX).y(this.middleCardY).w(this.middleCardWidth).h(22);
        this.rightDragHandle.x(this.rightCardX).y(this.rightCardY).w(this.rightCardWidth).h(22);

        /* Left card body — the list fills the body with padding. */
        int pad = 6;
        int bodyX = this.leftCardX + pad;
        int bodyY = this.leftCardY + 22 + pad;
        int bodyW = this.leftCardWidth - pad * 2;
        int bodyH = this.leftCardHeight - 22 - pad * 2;
        this.list.x(bodyX).y(bodyY).w(bodyW).h(Math.max(0, bodyH));

        /* Middle and right card bodies — scroll views. */
        this.middleScrollView.x(this.middleCardX).y(this.middleCardY + 22).w(this.middleCardWidth).h(this.middleCardHeight - 22);
        this.rightScrollView.x(this.rightCardX).y(this.rightCardY + 22).w(this.rightCardWidth).h(this.rightCardHeight - 22);

        boolean leftBody = this.leftVisible && !this.leftCollapsed;
        boolean middleBody = this.middleVisible && !this.middleCollapsed && this.entity != null;
        boolean rightBody = this.rightVisible && !this.rightCollapsed && this.entity != null;

        this.leftDragHandle.setVisible(this.leftVisible);
        this.list.setVisible(leftBody);
        this.leftCardResizer.setVisible(leftBody);

        this.middleDragHandle.setVisible(this.middleVisible && this.entity != null);
        this.middleScrollView.setVisible(middleBody);
        this.middleCardResizer.setVisible(middleBody);

        this.rightDragHandle.setVisible(this.rightVisible && this.entity != null);
        this.rightScrollView.setVisible(rightBody);
        this.rightCardResizer.setVisible(rightBody);

        positionResizer(CARD_LIST, this.leftCardResizer);
        positionResizer(CARD_ACTIONS, this.middleCardResizer);
        positionResizer(CARD_GEOMETRY, this.rightCardResizer);

        super.resize();
    }

    /* ----------------------------------------------------------------------
       Render
       ---------------------------------------------------------------------- */

    @Override
    public void render(UIContext context)
    {
        for (int i = 0; i < 3; i++)
        {
            UIDraggable handle = (i == 0) ? this.leftDragHandle : ((i == 1) ? this.middleDragHandle : this.rightDragHandle);
            boolean dragging = handle.isDragging();
            if (dragging && !this.prevDragging[i])
            {
                for (int j = 0; j < 3; j++)
                {
                    this.snapX[j] = getCardX(j); this.snapY[j] = getCardY(j);
                    this.snapW[j] = getCardWidth(j); this.snapH[j] = getCardHeight(j);
                }
                this.dragSnapshotValid = true;
            }
            else if (!dragging && this.prevDragging[i])
            {
                this.dragSnapshotValid = false;
            }
            this.prevDragging[i] = dragging;
        }

        if (this.leftDragHandle.isDragging()) this.leftDragHandle.updateDrag(context);
        if (this.middleDragHandle.isDragging()) this.middleDragHandle.updateDrag(context);
        if (this.rightDragHandle.isDragging()) this.rightDragHandle.updateDrag(context);
        if (this.leftCardResizer.isDragging()) this.leftCardResizer.updateDrag(context);
        if (this.middleCardResizer.isDragging()) this.middleCardResizer.updateDrag(context);
        if (this.rightCardResizer.isDragging()) this.rightCardResizer.updateDrag(context);

        for (int i = 0; i < 3; i++)
        {
            if (isCardVisible(i))
            {
                if (i > 0 && this.entity == null) continue;

                int cx = this.area.x + getCardX(i);
                int cy = this.area.y + getCardY(i);
                int cw = getCardWidth(i);
                int ch = getCardHeight(i);

                context.batcher.outline(cx - 1, cy - 1, cx + cw + 1, cy + ch + 1, 0xFF444444);
                if (!isCardCollapsed(i))
                {
                    context.batcher.box(cx, cy + 22, cx + cw, cy + ch, 0xCE111115);
                }
            }
        }

        int draggingCard = -1;
        if (this.leftDragHandle.isActivelyDragging()) draggingCard = CARD_LIST;
        else if (this.middleDragHandle.isActivelyDragging()) draggingCard = CARD_ACTIONS;
        else if (this.rightDragHandle.isActivelyDragging()) draggingCard = CARD_GEOMETRY;

        String label = UIKeys.FILM_CONTROLLER_SPEED.format(this.dashboard.orbit.speed.getValue()).get();
        FontRenderer font = context.batcher.getFont();
        int labelW = font.getWidth(label);
        int labelX = this.area.w - labelW - 5;
        int labelY = this.area.ey() - font.getHeight() - 5;

        context.batcher.textCard(label, labelX, labelY, Colors.WHITE, Colors.A50);
        super.render(context);

        /* Dock cubes + drop preview, drawn on top of children. */
        int baseColor = BBSSettings.editorDockGuideColor == null ? 0x57CCFF : BBSSettings.editorDockGuideColor.get();
        float opacity = BBSSettings.editorDockGuideOpacity == null ? 0.5F : Math.max(0F, Math.min(1F, BBSSettings.editorDockGuideOpacity.get()));
        int fill = Colors.setA(Colors.mulRGB(baseColor, 1.2F), opacity * 0.34F);
        int border = Colors.setA(Colors.mulRGB(baseColor, 1.35F), opacity * 0.6F);

        if (draggingCard != -1)
        {
            int dropY = context.mouseY - this.area.y;

            List<DockZone> zones = computeDockZones(draggingCard);
            DockZone hoveredZone = null;
            for (DockZone zone : zones)
            {
                boolean hovered = context.mouseX >= zone.cx - 18 && context.mouseX <= zone.cx + 18 && context.mouseY >= zone.cy - 18 && context.mouseY <= zone.cy + 18;
                if (checkAndRenderDockCube(context, zone.cx, zone.cy, hovered) && hoveredZone == null) hoveredZone = zone;
            }

            if (hoveredZone != null)
            {
                int[] rect = simulateDrop(draggingCard, hoveredZone, dropY);
                int previewX = this.area.x + rect[0];
                int previewY = this.area.y + rect[1];
                int previewW = rect[2];
                int previewH = rect[3];

                context.batcher.box(previewX, previewY, previewX + previewW, previewY + previewH, fill);

                int t = 2;
                context.batcher.box(previewX, previewY, previewX + previewW, previewY + t, border);
                context.batcher.box(previewX, previewY + previewH - t, previewX + previewW, previewY + previewH, border);
                context.batcher.box(previewX, previewY, previewX + t, previewY + previewH, border);
                context.batcher.box(previewX + previewW - t, previewY, previewX + previewW, previewY + previewH, border);
            }
        }
    }

    private boolean checkAndRenderDockCube(UIContext context, int cx, int cy, boolean hovered)
    {
        int x1 = cx - 14, y1 = cy - 14, x2 = cx + 14, y2 = cy + 14;

        int baseColor = BBSSettings.primaryColor.get();
        int bg = hovered ? Colors.setA(baseColor, 0.85F) : 0xEE1A1A20;
        int borderColor = hovered ? 0xFFFFFFFF : (0xFF000000 | baseColor);

        context.batcher.box(x1, y1, x2, y2, bg);
        context.batcher.outline(x1, y1, x2, y2, borderColor);

        int coreSize = hovered ? 5 : 4;
        context.batcher.box(cx - coreSize, cy - coreSize, cx + coreSize, cy + coreSize, 0xFFFFFFFF);

        if (hovered) context.batcher.outline(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0x88FFFFFF);

        return context.mouseX >= x1 - 4 && context.mouseX <= x2 + 4 && context.mouseY >= y1 - 4 && context.mouseY <= y2 + 4;
    }

    /* ----------------------------------------------------------------------
       Visibility (Window menu)
       ---------------------------------------------------------------------- */

    public boolean isListVisible() { return this.leftVisible; }
    public void setListVisible(boolean v) { this.leftVisible = v; this.resize(); this.saveLayout(); }
    public boolean isActionsVisible() { return this.middleVisible; }
    public void setActionsVisible(boolean v) { this.middleVisible = v; this.resize(); this.saveLayout(); }
    public boolean isGeometryVisible() { return this.rightVisible; }
    public void setGeometryVisible(boolean v) { this.rightVisible = v; this.resize(); this.saveLayout(); }

    public void resetLayout()
    {
        this.leftCardX = 10; this.leftCardY = 10; this.leftCardWidth = 220; this.leftCardHeight = 400;
        this.leftCollapsed = false; this.leftDocked = true; this.leftVisible = true;
        this.leftDockSide = SIDE_LEFT; this.leftDockColumn = 0; this.leftDockRow = 0;

        this.middleCardX = 470; this.middleCardY = 410; this.middleCardWidth = 240; this.middleCardHeight = 400;
        this.middleCollapsed = false; this.middleDocked = true; this.middleVisible = true;
        this.middleDockSide = SIDE_RIGHT; this.middleDockColumn = 0; this.middleDockRow = 1;

        this.rightCardX = 470; this.rightCardY = 10; this.rightCardWidth = 240; this.rightCardHeight = 200;
        this.rightCollapsed = false; this.rightDocked = true; this.rightVisible = true;
        this.rightDockSide = SIDE_RIGHT; this.rightDockColumn = 0; this.rightDockRow = 0;

        this.leftWeight = 1F; this.middleWeight = 1F; this.rightWeight = 1F;

        this.resize();
        this.saveLayout();
    }

    /* ----------------------------------------------------------------------
       Persistence
       ---------------------------------------------------------------------- */

    private void saveLayout()
    {
        if (BBSSettings.triggerBlockPanelLayout == null) return;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++)
        {
            if (i > 0) sb.append(';');
            sb.append(getCardX(i)).append(',').append(getCardY(i)).append(',').append(getCardWidth(i)).append(',').append(getCardHeight(i)).append(',')
              .append(isCardCollapsed(i) ? 1 : 0).append(',').append(isCardDocked(i) ? 1 : 0).append(',')
              .append(getCardDockColumn(i)).append(',').append(getCardDockRow(i)).append(',')
              .append(isCardVisible(i) ? 1 : 0).append(',').append(getCardWeight(i)).append(',').append(getCardDockSide(i));
        }
        BBSSettings.triggerBlockPanelLayout.set(sb.toString());
    }

    private void loadLayout()
    {
        if (BBSSettings.triggerBlockPanelLayout == null) return;
        String raw = BBSSettings.triggerBlockPanelLayout.get();
        if (raw == null || raw.isEmpty()) return;

        try
        {
            String[] parts = raw.split(";");
            if (parts.length < 3) return;

            for (int i = 0; i < 3; i++)
            {
                String[] f = parts[i].split(",");
                if (f.length != 11) return;

                setCardX(i, Integer.parseInt(f[0])); setCardY(i, Integer.parseInt(f[1])); setCardWidth(i, Integer.parseInt(f[2])); setCardHeight(i, Integer.parseInt(f[3]));
                setCardCollapsed(i, f[4].equals("1")); setCardDocked(i, f[5].equals("1"));
                setCardDockColumn(i, Integer.parseInt(f[6])); setCardDockRow(i, Integer.parseInt(f[7]));
                setCardVisible(i, f[8].equals("1")); setCardWeight(i, Float.parseFloat(f[9]));
                setCardDockSide(i, Integer.parseInt(f[10]));
            }
            normalizeDockOrder();
        }
        catch (Exception e)
        {}
    }

    /* ----------------------------------------------------------------------
       Trigger-specific behaviour
       ---------------------------------------------------------------------- */

    private void teleport()
    {
        if (this.entity != null)
        {
            BlockPos pos = this.entity.getPos();
            PlayerUtils.teleport(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            UIUtils.playClick();
        }
    }

    public void fill(TriggerBlockEntity entity, boolean select)
    {
        if (this.entity != null) this.toSave.add(this.entity);

        this.entity = entity;
        this.editor.setEntity(entity);

        if (select) this.list.setCurrentScroll(entity);

        this.resize();
    }

    @Override
    public void close()
    {
        super.close();

        if (this.entity != null) this.toSave.add(this.entity);

        for (TriggerBlockEntity entity : this.toSave) this.save(entity);

        this.toSave.clear();
        this.saveLayout();
    }

    private void save(TriggerBlockEntity entity)
    {
        if (entity != null) ClientNetwork.sendTriggerBlockUpdate(entity.getPos(), entity);
    }

    @Override public boolean needsBackground() { return false; }
    @Override public boolean canPause() { return false; }
    @Override public boolean supportsRollFOVControl() { return false; }

    @Override
    public void open()
    {
        super.open();

        BBSRendering.ensureMainFramebuffer();
        this.updateList();
    }

    private void updateList()
    {
        this.list.clear();
        this.list.add(TriggerBlockEntityRenderer.capturedTriggerBlocks);

        if (this.entity != null && !this.entity.isRemoved())
        {
            if (!this.list.getList().contains(this.entity)) this.list.add(this.entity);
            this.list.setCurrentScroll(this.entity);
        }
        else
        {
            this.fill(null, false);
        }
    }

    @Override
    public void renderInWorld(WorldRenderContext context)
    {
        super.renderInWorld(context);

        this.hovered = null;

        if (context.matrixStack() == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d pos = camera.getPos();

        Vector3f mouseDirection = CameraUtils.getMouseDirection(
            RenderSystem.getProjectionMatrix(),
            context.matrixStack().peek().getPositionMatrix(),
            (int) mc.mouse.getX(), (int) mc.mouse.getY(), 0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight()
        );

        this.hovered = this.getClosestObject(new Vector3d(pos.x, pos.y, pos.z), mouseDirection);

        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        context.matrixStack().push();
        context.matrixStack().translate(-pos.x, -pos.y, -pos.z);

        if (this.entity != null)
        {
            this.renderBox(context.matrixStack(), this.entity, 0F, 1F, 0F);

            if (this.entity.region.get())
            {
                RenderSystem.disableDepthTest();
                this.renderRegionBox(context.matrixStack(), this.entity, 1F, 1F, 1F);
                RenderSystem.enableDepthTest();
            }
        }

        for (TriggerBlockEntity entity : TriggerBlockEntityRenderer.capturedTriggerBlocks)
        {
            if (this.entity == entity) continue;

            if (this.hovered == entity) this.renderBox(context.matrixStack(), entity, 0F, 1F, 0F);
            else this.renderBox(context.matrixStack(), entity, -1F, -1F, -1F);
        }

        context.matrixStack().pop();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    private void renderBox(MatrixStack stack, TriggerBlockEntity entity, float r, float g, float b)
    {
        BlockPos bp = entity.getPos();
        Vector3f p1 = entity.pos1.get();
        Vector3f p2 = entity.pos2.get();

        double minX = Math.min(p1.x, p2.x);
        double minY = Math.min(p1.y, p2.y);
        double minZ = Math.min(p1.z, p2.z);
        double maxX = Math.max(p1.x, p2.x);
        double maxY = Math.max(p1.y, p2.y);
        double maxZ = Math.max(p1.z, p2.z);

        double x = bp.getX() + minX;
        double y = bp.getY() + minY;
        double z = bp.getZ() + minZ;
        double w = maxX - minX;
        double h = maxY - minY;
        double d = maxZ - minZ;

        if (r == -1) Draw.renderBox(stack, x, y, z, w, h, d);
        else Draw.renderBox(stack, x, y, z, w, h, d, r, g, b);
    }

    private void renderRegionBox(MatrixStack stack, TriggerBlockEntity entity, float r, float g, float b)
    {
        Box box = entity.getRegionBox();
        Draw.renderBox(stack, box.minX, box.minY, box.minZ, box.maxX - box.minX, box.maxY - box.minY, box.maxZ - box.minZ, r, g, b);
    }

    protected double getDistance(TriggerBlockEntity object, Vector3d pos, Vector3f dir)
    {
        return RayTracing.intersect(pos, dir, this.getHitbox(object));
    }

    protected TriggerBlockEntity getClosestObject(Vector3d pos, Vector3f dir)
    {
        TriggerBlockEntity closest = null;
        double current = Double.POSITIVE_INFINITY;

        for (TriggerBlockEntity entity : TriggerBlockEntityRenderer.capturedTriggerBlocks)
        {
            double result = this.getDistance(entity, pos, dir);
            if (result >= 0 && result < current) { current = result; closest = entity; }
        }
        return closest;
    }

    private AABB getHitbox(TriggerBlockEntity closest)
    {
        BlockPos pos = closest.getPos();
        Vector3f p1 = closest.pos1.get();
        Vector3f p2 = closest.pos2.get();

        double minX = Math.min(p1.x, p2.x);
        double minY = Math.min(p1.y, p2.y);
        double minZ = Math.min(p1.z, p2.z);
        double maxX = Math.max(p1.x, p2.x);
        double maxY = Math.max(p1.y, p2.y);
        double maxZ = Math.max(p1.z, p2.z);

        return new AABB(pos.getX() + minX, pos.getY() + minY, pos.getZ() + minZ, maxX - minX, maxY - minY, maxZ - minZ);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (super.subMouseClicked(context)) return true;

        if (this.hovered != null && context.mouseButton == 0)
        {
            this.fill(this.hovered, true);
            return true;
        }

        return false;
    }
}
