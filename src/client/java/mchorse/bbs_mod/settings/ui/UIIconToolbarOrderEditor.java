package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.IIconToolbarValue;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.tooltips.LabelTooltip;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class UIIconToolbarOrderEditor extends UIElement
{
    private static final int BUTTON_SIZE = 20;
    private static final int DRAG_THRESHOLD = 3;

    private final IIconToolbarValue value;
    private final Function<String, Icon> iconProvider;
    private final Function<String, IKey> tooltipProvider;
    private final Runnable onChange;

    private final List<ToolbarSlot> slots = new ArrayList<>();

    private String dragId;
    private String pendingDragId;
    private int pendingDragX;
    private int hoveredIndex = -1;
    private int dropPreviewIndex = -1;
    private final DropPlaceholder dropPlaceholder = new DropPlaceholder();

    public UIIconToolbarOrderEditor(IIconToolbarValue value, Function<String, Icon> iconProvider, Function<String, IKey> tooltipProvider, Runnable onChange)
    {
        this.value = value;
        this.iconProvider = iconProvider;
        this.tooltipProvider = tooltipProvider;
        this.onChange = onChange;

        this.add(this.dropPlaceholder);
        this.rebuildSlots();

        if (value instanceof BaseValue)
        {
            ((BaseValue) value).postCallback((v, f) -> this.rebuildSlots());
        }
    }

    private void rebuildSlots()
    {
        this.removeAll();
        this.slots.clear();
        this.dropPreviewIndex = -1;

        List<String> order = this.value.getOrder();

        for (String id : order)
        {
            ToolbarSlot slot = new ToolbarSlot(id);

            this.slots.add(slot);
            this.add(slot);
        }

        this.add(this.dropPlaceholder);
        this.dropPlaceholder.setPlaceholderVisible(false);
        this.applyLayout(false);
    }

    private int getSlotIndex(String id)
    {
        for (int i = 0; i < this.slots.size(); i++)
        {
            if (this.slots.get(i).buttonId.equals(id))
            {
                return i;
            }
        }

        return -1;
    }

    private int getVisualInsertIndex(int mouseX)
    {
        int startX = this.area.x + 2;
        int columns = this.slots.size();

        for (int i = 0; i < columns; i++)
        {
            if (mouseX < startX + i * BUTTON_SIZE + BUTTON_SIZE / 2)
            {
                return i;
            }
        }

        return Math.max(columns - 1, 0);
    }

    private void applyLayout(boolean dragging)
    {
        int visibleCount = this.slots.size();

        if (dragging && this.dragId != null)
        {
            visibleCount += 1;
        }

        this.w(visibleCount * BUTTON_SIZE + 4);
        this.h(BUTTON_SIZE + 8);
        this.row(0).resize();
    }

    private void updateDragLayout(int previewIndex)
    {
        if (this.dragId == null)
        {
            return;
        }

        int from = this.getSlotIndex(this.dragId);

        if (from < 0)
        {
            return;
        }

        this.removeAll();

        int insertAt = previewIndex;
        int visibleIndex = 0;

        for (int i = 0; i < this.slots.size(); i++)
        {
            if (i == from)
            {
                continue;
            }

            if (visibleIndex == insertAt)
            {
                this.dropPlaceholder.setPlaceholderVisible(true);
                this.add(this.dropPlaceholder);
            }

            this.add(this.slots.get(i));
            visibleIndex += 1;
        }

        if (visibleIndex == insertAt)
        {
            this.dropPlaceholder.setPlaceholderVisible(true);
            this.add(this.dropPlaceholder);
        }
        else
        {
            this.dropPlaceholder.setPlaceholderVisible(false);
        }

        this.applyLayout(true);
    }

    private void restoreDragLayout()
    {
        this.dropPreviewIndex = -1;
        this.dropPlaceholder.setPlaceholderVisible(false);

        this.removeAll();

        for (ToolbarSlot slot : this.slots)
        {
            slot.skipRender = false;
            this.add(slot);
        }

        this.add(this.dropPlaceholder);
        this.applyLayout(false);
    }

    private void finishDrag(UIContext context)
    {
        if (this.dragId != null)
        {
            int from = this.getSlotIndex(this.dragId);
            int to = this.getVisualInsertIndex(context.mouseX);

            if (from >= 0 && to >= 0 && from != to)
            {
                this.value.moveButton(from, to);

                if (this.onChange != null)
                {
                    this.onChange.run();
                }

                this.rebuildSlots();
            }
            else
            {
                this.restoreDragLayout();
            }
        }

        this.dragId = null;
        this.pendingDragId = null;
        this.pendingDragX = 0;
    }

    private void startPendingDrag(String id, int mouseX)
    {
        this.pendingDragId = id;
        this.pendingDragX = mouseX;
        this.dragId = null;
    }

    @Override
    protected boolean subMouseReleased(UIContext context)
    {
        if (this.dragId != null || this.pendingDragId != null)
        {
            this.finishDrag(context);

            return true;
        }

        return super.subMouseReleased(context);
    }

    @Override
    public void render(UIContext context)
    {
        this.hoveredIndex = -1;

        if (this.pendingDragId != null && this.dragId == null && Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT))
        {
            int dx = context.mouseX - this.pendingDragX;

            if (Math.abs(dx) > DRAG_THRESHOLD)
            {
                this.dragId = this.pendingDragId;
                this.pendingDragId = null;
                this.dropPreviewIndex = -1;
            }
        }

        if (this.dragId != null)
        {
            int previewIndex = this.getVisualInsertIndex(context.mouseX);

            if (previewIndex != this.dropPreviewIndex)
            {
                this.dropPreviewIndex = previewIndex;
                this.updateDragLayout(previewIndex);
            }
        }

        super.render(context);

        if (this.hoveredIndex >= 0 && this.dragId == null)
        {
            ToolbarSlot slot = this.slots.get(this.hoveredIndex);

            context.tooltip.area.set(context.globalX(slot.area.x), context.globalY(slot.area.y), slot.area.w, slot.area.h);

            LabelTooltip tooltip = new LabelTooltip(this.tooltipProvider.apply(slot.buttonId), Direction.TOP);

            tooltip.renderTooltip(context);
        }

        if (this.dragId != null)
        {
            Icon icon = this.iconProvider.apply(this.dragId);
            int ghostX = context.mouseX - BUTTON_SIZE / 2;
            int ghostY = context.mouseY - BUTTON_SIZE / 2;
            int halo = Colors.setA(BBSSettings.primaryColor.get(), 0.55F);

            context.batcher.gradientVBox(ghostX, ghostY, ghostX + BUTTON_SIZE, ghostY + BUTTON_SIZE, 0, halo);
            context.batcher.icon(icon, Colors.WHITE, ghostX + BUTTON_SIZE / 2, ghostY + BUTTON_SIZE / 2, 0.5F, 0.5F);
        }
    }

    private class DropPlaceholder extends UIElement
    {
        private boolean placeholderVisible;

        private DropPlaceholder()
        {
            this.wh(BUTTON_SIZE, BUTTON_SIZE);
        }

        private void setPlaceholderVisible(boolean visible)
        {
            this.placeholderVisible = visible;
        }

        @Override
        public void render(UIContext context)
        {
            if (!this.placeholderVisible)
            {
                return;
            }

            int halo = Colors.setA(BBSSettings.primaryColor.get(), 0.35F);
            int border = Colors.setA(BBSSettings.primaryColor.get(), 0.65F);

            context.batcher.gradientVBox(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0, halo);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);

            super.render(context);
        }
    }

    private class ToolbarSlot extends UIElement
    {
        private final String buttonId;
        private boolean skipRender;

        private ToolbarSlot(String buttonId)
        {
            this.buttonId = buttonId;
            this.wh(BUTTON_SIZE, BUTTON_SIZE);
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (!this.area.isInside(context))
            {
                return false;
            }

            if (context.mouseButton == 1)
            {
                if (UIIconToolbarOrderEditor.this.value.canHide(this.buttonId))
                {
                    UIIconToolbarOrderEditor.this.value.toggleHidden(this.buttonId);

                    if (UIIconToolbarOrderEditor.this.onChange != null)
                    {
                        UIIconToolbarOrderEditor.this.onChange.run();
                    }

                    UIUtils.playClick();
                }

                return true;
            }

            if (context.mouseButton == 0)
            {
                UIIconToolbarOrderEditor.this.startPendingDrag(this.buttonId, context.mouseX);

                return true;
            }

            return false;
        }

        @Override
        public void render(UIContext context)
        {
            if (this.area.isInside(context))
            {
                context.requestCursor(GLFW.GLFW_HAND_CURSOR);
            }

            if (!this.skipRender && !this.buttonId.equals(UIIconToolbarOrderEditor.this.dragId))
            {
                boolean hidden = UIIconToolbarOrderEditor.this.value.isHidden(this.buttonId);
                boolean hovered = this.area.isInside(context);
                boolean dragging = this.buttonId.equals(UIIconToolbarOrderEditor.this.pendingDragId);
                int color = hidden ? Colors.setA(Colors.WHITE, 0.35F) : Colors.WHITE;
                int bgColor = Colors.A25;

                if (dragging)
                {
                    bgColor = Colors.setA(BBSSettings.primaryColor.get(), 0.55F);
                }
                else if (hovered)
                {
                    bgColor = Colors.setA(BBSSettings.primaryColor.get(), 0.35F);
                }

                context.batcher.gradientVBox(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0, bgColor);
                context.batcher.icon(UIIconToolbarOrderEditor.this.iconProvider.apply(this.buttonId), color, this.area.mx(), this.area.my(), 0.5F, 0.5F);

                if (hidden)
                {
                    context.batcher.box(this.area.x + 3, this.area.my(), this.area.ex() - 3, this.area.my() + 1, Colors.setA(Colors.RED, 0.75F));
                }

                if (this.area.isInside(context))
                {
                    UIIconToolbarOrderEditor.this.hoveredIndex = UIIconToolbarOrderEditor.this.getSlotIndex(this.buttonId);
                }
            }

            super.render(context);
        }
    }
}
