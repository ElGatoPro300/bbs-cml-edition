package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

public class UIDashboardPanel extends UIElement
{
    public final UIDashboard dashboard;

    public UIDashboardPanel(UIDashboard dashboard)
    {
        super();

        this.dashboard = dashboard;
        this.markContainer();
    }

    public boolean needsBackground()
    {
        return true;
    }

    /**
     * When false, the vanilla world pass is skipped while this panel is open (opaque UI covers
     * the screen). Defaults to the inverse of {@link #needsBackground()}.
     */
    public boolean needsWorldRender()
    {
        return !this.needsBackground();
    }

    public boolean canHideHUD()
    {
        return true;
    }

    public boolean canToggleVisibility()
    {
        return true;
    }

    public boolean canPause()
    {
        return true;
    }

    public boolean canRefresh()
    {
        return true;
    }

    public void appear()
    {}

    public void disappear()
    {}

    public void open()
    {}

    public void close()
    {}

    public void update()
    {}

    public void startRenderFrame(float tickDelta)
    {}

    public void renderInWorld(WorldRenderContext context)
    {}

    public void renderPanelBackground(UIContext context)
    {}

    public UIDashboardPanel getMainPanel()
    {
        return this;
    }

    public void showHomeView()
    {}
}