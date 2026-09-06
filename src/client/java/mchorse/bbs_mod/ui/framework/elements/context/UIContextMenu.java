package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.utils.colors.Colors;

import org.lwjgl.glfw.GLFW;

public abstract class UIContextMenu extends UIElement
{
    public UIContextMenu()
    {
        super();

        this.eventPropagataion(EventPropagation.BLOCK_INSIDE);
    }

    public abstract boolean isEmpty();

    /**
     * Set mouse coordinate
     *
     * In this method for subclasses, you should setup the resizer
     */
    public abstract void setMouse(UIContext context);

    /**
     * Size this popup to {@code column} after wrapping labels and toggles so
     * long translations wrap instead of truncating with an ellipsis.
     */
    protected void sizeToColumn(UIElement column, UIContext context)
    {
        column.resize();

        for (UILabel label : column.getChildren(UILabel.class))
        {
            label.wrapToWidth();
        }

        for (UIToggle toggle : column.getChildren(UIToggle.class))
        {
            toggle.wrapToWidth();
        }

        column.resize();

        this.xy(context.mouseX(), context.mouseY())
            .wh(column.area.w, column.area.h)
            .bounds(context.menu.overlay, 5);
    }

    /**
     * Instantly detach (skips close animation). Used when replacing with another menu.
     */
    public void forceClose()
    {
        this.removeFromParent();
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.area.isInside(context))
        {
            this.removeFromParent();
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(GLFW.GLFW_KEY_ESCAPE))
        {
            this.removeFromParent();

            return true;
        }

        return super.subKeyPressed(context);
    }

    @Override
    public void render(UIContext context)
    {
        context.batcher.flush();

        this.renderBackground(context);

        super.render(context);
    }

    protected void renderBackground(UIContext context)
    {
        int color = BBSSettings.primaryColor.get();

        context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 10, Colors.A25 | color, color);
        this.area.render(context.batcher, Colors.A100);
    }
}