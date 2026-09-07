package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.events.UIOverlayCloseEvent;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class UIOverlayPanel extends UIElement
{
    public UILabel title;
    public UIElement icons;
    public UIIcon close;
    public UIElement content;

    private boolean moving;
    private boolean resizing;
    private boolean resizable;
    private int lastX;
    private int lastY;
    private int resizeMargin = 12;
    private int minWidth = 180;
    private int minHeight = 120;
    private int resizeMinWidth = 180;
    private int resizeMinHeight = 120;
    private boolean resizeFromLeft;

    private int initialOffsetX;
    private int initialOffsetY;
    private int initialWidthOffset;
    private int initialHeightOffset;

    public UIOverlayPanel(IKey title)
    {
        super();

        this.title = UI.label(title).color(Colors.WHITE);
        this.close = new UIIcon(Icons.CLOSE, (b) -> this.close());
        this.close.tooltip(UIKeys.GENERAL_CLOSE, Direction.LEFT);
        this.content = new UIElement();
        this.icons = new UIElement();

        this.title.labelAnchor(0.5F, 0.5F).relative(this).xy(0.5F, 0).anchor(0.5F, 0).w(0.8F).h(20);
        this.icons.relative(this).x(1F, -20).y(0).w(20).h(1F).column(0).stretch();
        this.content.relative(this).xy(0, 20).w(1F, -20).h(1F, -20);

        this.icons.add(this.close);

        this.add(this.title, this.icons, this.content);

        this.mouseEventPropagataion(EventPropagation.BLOCK_INSIDE);
        this.resizable = true;
    }

    public void setInitialOffset(int x, int y)
    {
        this.initialOffsetX = x;
        this.initialOffsetY = y;
    }

    public void setInitialSizeOffset(int w, int h)
    {
        this.initialWidthOffset = w;
        this.initialHeightOffset = h;
    }

    public UIOverlayPanel resizable()
    {
        return this.resizable(true);
    }

    public UIOverlayPanel resizable(boolean value)
    {
        this.resizable = value;

        return this;
    }

    public UIOverlayPanel minSize(int width, int height)
    {
        this.minWidth = Math.max(60, width);
        this.minHeight = Math.max(60, height);

        return this;
    }

    public UIOverlayPanel resizeMargin(int margin)
    {
        this.resizeMargin = Math.max(6, margin);

        return this;
    }

    public UIOverlayPanel resizeFromLeft()
    {
        this.resizeFromLeft = true;

        return this;
    }

    public String getKey()
    {
        String className = this.getClass().getSimpleName();

        if (className.isEmpty())
        {
            className = this.getClass().getSuperclass().getSimpleName();
        }

        if (this.title != null && this.title.label != null && this.title.label.get() != null && !this.title.label.get().isEmpty())
        {
            return className + "_" + this.title.label.get();
        }

        return className;
    }

    public void onClose(Consumer<UIOverlayCloseEvent> callback)
    {
        this.events.register(UIOverlayCloseEvent.class, callback);
    }

    public void close()
    {
        UIElement parent = this.getParent();

        if (parent instanceof UIOverlay)
        {
            ((UIOverlay) parent).closeItself();
        }
    }

    public void confirm()
    {}

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.isResizeHandleInside(context))
        {
            if (Window.isCtrlPressed())
            {
                this.flex.w.offset = this.initialWidthOffset;
                this.flex.h.offset = this.initialHeightOffset;

                if (this.getParent() != null)
                {
                    this.getParent().resize();
                }

                UIOverlay.saveOverlayState(this);

                return true;
            }

            this.resizing = true;
            this.resizeMinWidth = Math.min(this.minWidth, this.area.w);
            this.resizeMinHeight = Math.min(this.minHeight, this.area.h);
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            return true;
        }

        if (this.title.area.isInside(context))
        {
            if (Window.isCtrlPressed())
            {
                this.flex.x.offset = this.initialOffsetX;
                this.flex.y.offset = this.initialOffsetY;

                if (this.getParent() != null)
                {
                    this.getParent().resize();
                }

                UIOverlay.saveOverlayState(this);

                return true;
            }

            this.moving = true;
            this.lastX = context.mouseX;
            this.lastY = context.mouseY;

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.resizing || this.moving)
        {
            UIOverlay.saveOverlayState(this);
        }

        this.resizing = false;
        this.moving = super.subMouseReleased(context);

        return false;
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (!context.isFocused())
        {
            if (context.isPressed(Keys.CLOSE))
            {
                if (this.getParent() instanceof UIOverlay)
                {
                    this.close();

                    return true;
                }
            }
            else if (context.isPressed(Keys.CONFIRM))
            {
                this.confirm();

                return true;
            }
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.resizing && (context.mouseX != this.lastX || context.mouseY != this.lastY))
        {
            int dx = context.mouseX - this.lastX;
            int dy = context.mouseY - this.lastY;
            if (this.resizeFromLeft)
            {
                int maxW = Integer.MAX_VALUE;

                if (this.getParent() != null)
                {
                    maxW = this.area.ex() - this.getParent().area.x;
                }

                int desiredW = Math.min(maxW, Math.max(this.resizeMinWidth, this.area.w - dx));
                int deltaW = desiredW - this.area.w;

                this.flex.w.offset += deltaW;
            }
            else
            {
                int maxW = Integer.MAX_VALUE;
                int maxH = Integer.MAX_VALUE;

                if (this.getParent() != null)
                {
                    maxW = this.getParent().area.ex() - this.area.x;
                    maxH = this.getParent().area.ey() - this.area.y;
                }

                int desiredW = Math.min(maxW, Math.max(this.resizeMinWidth, this.area.w + dx));
                int desiredH = Math.min(maxH, Math.max(this.resizeMinHeight, this.area.h + dy));
                int deltaW = desiredW - this.area.w;
                int deltaH = desiredH - this.area.h;

                this.flex.w.offset += deltaW;
                this.flex.h.offset += deltaH;
            }

            if (this.getParent() != null)
            {
                this.getParent().resize();
            }

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;
        }

        if (this.moving && (context.mouseX != this.lastX || context.mouseY != this.lastY))
        {
            int dx = context.mouseX - this.lastX;
            int dy = context.mouseY - this.lastY;
            int lastX = this.area.x;
            int lastY = this.area.y;

            this.flex.x.offset += dx;
            this.flex.y.offset += dy;

            this.getParent().resize();

            if (lastX == this.area.x) this.flex.x.offset -= dx;
            if (lastY == this.area.y) this.flex.y.offset -= dy;

            this.lastX = context.mouseX;
            this.lastY = context.mouseY;
        }

        float transition = 1.0F;
        UIElement parent = this.getParent();

        if (parent instanceof UIOverlay)
        {
            transition = ((UIOverlay) parent).getOpenTransition();
        }

        /* Keep close/open scale subtle. Scaling by raw transition (~0..1) shrinks the
         * dark panel toward a speck and reads as a full-view blot on some frames. */
        boolean animateScale = transition < 0.999F;

        if (animateScale)
        {
            float scale = 0.92F + 0.08F * transition;
            float cx = this.area.mx();
            float cy = this.area.my();

            context.render.batcher.getContext().getMatrices().pushMatrix();
            context.render.batcher.getContext().getMatrices().translate(cx, cy);
            context.render.batcher.getContext().getMatrices().scale(scale, scale);
            context.render.batcher.getContext().getMatrices().translate(-cx, -cy);
        }

        this.renderBackground(context);

        super.render(context);

        if (animateScale)
        {
            context.batcher.flushDraw();
            context.batcher.getContext().getMatrices().popMatrix();
        }
    }

    protected void renderBackground(UIContext context)
    {
        context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 10, 0x44000000, 0x00000000);

        // Main background
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF141418);
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF2A2A35, 1);

        // Header Row
        int headerH = 20;
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + headerH, 0xFF1A1A22);
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.y + headerH, 0xFF2A2A35, 1);

        if (this.close.area.isInside(context))
        {
            this.close.area.render(context.batcher, Colors.RED | Colors.A100);
        }

        if (this.title.area.isInside(context))
        {
            context.batcher.icon(Icons.ALL_DIRECTIONS, Colors.GRAY, this.area.mx(), this.title.area.my(), 0.5F, 0.5F);
        }

        if (this.resizable)
        {
            int resizeColor = this.isResizeHandleInside(context) ? Colors.WHITE : Colors.GRAY;
            int left = this.area.x;
            int right = this.area.ex();
            int bottom = this.area.ey();

            if (this.resizeFromLeft)
            {
                context.batcher.box(left + 1, bottom - 1, left + 9, bottom, resizeColor);
                context.batcher.box(left, bottom - 9, left + 1, bottom - 1, resizeColor);
            }
            else
            {
                context.batcher.box(right - 9, bottom - 1, right - 1, bottom, resizeColor);
                context.batcher.box(right - 1, bottom - 9, right, bottom - 1, resizeColor);
            }
        }
    }

    private boolean isResizeHandleInside(UIContext context)
    {
        boolean side = this.resizeFromLeft
            ? context.mouseX <= this.area.x + this.resizeMargin
            : context.mouseX >= this.area.ex() - this.resizeMargin;

        return this.resizable
            && this.area.isInside(context)
            && side
            && context.mouseY >= this.area.ey() - this.resizeMargin;
    }

    public void onClose()
    {
        this.events.emit(new UIOverlayCloseEvent(this));
    }
}
