package mchorse.bbs_mod.ui.dashboard.panels;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.EditorSpectatorHelper;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.UIDebugPanel;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureManagerPanel;
import mchorse.bbs_mod.ui.film.UIWorldFilmsBrowserPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.events.UIEvent;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.home.UIHomePanel;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.ui.news.UINewsPanel;
import mchorse.bbs_mod.ui.triggers.UITriggerBlockPanel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIDashboardPanels extends UIElement
{
    public List<UIDashboardPanel> panels = new ArrayList<>();
    public UIDashboardPanel panel;

    private final Map<UIDashboardPanel, UIIcon> panelButtonsMap = new HashMap<>();

    public UIElement taskBar;
    public UIElement pinned;
    public UIScrollView panelButtons;
    public UIIcon worldFilmsButton;

    /**
     * Render a selection highlight on one edge of the area: a solid color bar on the {@code direction}
     * side, fading into a gradient towards the opposite edge.
     */
    public static void renderHighlight(Batcher2D batcher, Area area, Direction direction)
    {
        int color = BBSSettings.primaryColor.get();
        int bar = Colors.A100 | color;
        int near = Colors.A75 | color;
        int far = color;
        int t = 2;

        switch (direction)
        {
            case TOP:
                batcher.box(area.x, area.y, area.ex(), area.y + t, bar);
                batcher.gradientVBox(area.x, area.y + t, area.ex(), area.ey(), near, far);
                break;
            case BOTTOM:
                batcher.box(area.x, area.ey() - t, area.ex(), area.ey(), bar);
                batcher.gradientVBox(area.x, area.y, area.ex(), area.ey() - t, far, near);
                break;
            case LEFT:
                batcher.box(area.x, area.y, area.x + t, area.ey(), bar);
                batcher.gradientHBox(area.x + t, area.y, area.ex(), area.ey(), near, far);
                break;
            case RIGHT:
                batcher.box(area.ex() - t, area.y, area.ex(), area.ey(), bar);
                batcher.gradientHBox(area.x, area.y, area.ex() - t, area.ey(), far, near);
                break;
        }
    }

    public static void renderHighlightHorizontal(Batcher2D batcher, Area area)
    {
        renderHighlight(batcher, area, Direction.BOTTOM);
    }

    public static void renderHighlight(Batcher2D batcher, Area area)
    {
        renderHighlight(batcher, area, Direction.BOTTOM);
    }

    public UIDashboardPanels()
    {
        this.taskBar = new UIElement();
        this.taskBar.relative(this).y(1F, -20).w(1F).h(20);
        this.pinned = new UIElement();
        this.pinned.relative(this.taskBar).h(20).row(0).resize();
        this.panelButtons = new UIScrollView(ScrollDirection.HORIZONTAL);
        this.panelButtons.relative(this.pinned).x(1F, 5).h(20).wTo(this.taskBar.area, 1F).column(0).scroll();
        this.panelButtons.scroll.cancelScrolling().noScrollbar();
        this.panelButtons.scroll.scrollSpeed = 5;
        this.taskBar.add(new UIRenderable(this::renderBackground), new UIRenderable(this::renderActiveHighlight), this.pinned, this.panelButtons);
        this.add(this.taskBar);
    }

    public void registerWorldFilmsButton(UIDashboard dashboard)
    {
        this.worldFilmsButton = new UIIcon(Icons.GLOBE, (b) ->
        {
            dashboard.setPanel(dashboard.getPanel(UIWorldFilmsBrowserPanel.class));
        });
        this.worldFilmsButton.tooltip(UIKeys.FILM_HOME_WORLDS, Direction.TOP);
        this.worldFilmsButton.relative(this.taskBar).x(1F, -4).y(0).anchor(1F, 0).wh(20, 20);
        this.taskBar.add(this.worldFilmsButton);
        this.panelButtons.getFlex().w.offset = -28;
    }

    public <T> T getPanel(Class<T> clazz)
    {
        for (UIDashboardPanel panel : this.panels)
        {
            if (panel.getClass() == clazz)
            {
                return (T) panel;
            }
        }

        return null;
    }

    public boolean isFlightSupported()
    {
        return this.panel instanceof IFlightSupported;
    }

    public void open()
    {
        for (UIDashboardPanel panel : this.panels)
        {
            panel.open();
        }
    }

    public void close()
    {
        for (UIDashboardPanel panel : this.panels)
        {
            panel.close();
        }
    }

    public void updateTaskBarForPanel(UIDashboardPanel panel)
    {
        boolean stripped = UIWorldFilmsBrowserPanel.isBrowserPanel(panel);
        UIIcon homeButton = this.panelButtonsMap.get(this.getPanel(UIHomePanel.class));
        UIIcon morphButton = this.panelButtonsMap.get(this.getPanel(UIMorphingPanel.class));
        UIIcon modelBlockButton = this.panelButtonsMap.get(this.getPanel(UIModelBlockPanel.class));
        UIIcon triggerBlockButton = this.panelButtonsMap.get(this.getPanel(UITriggerBlockPanel.class));
        UIIcon texturesButton = this.panelButtonsMap.get(this.getPanel(UITextureManagerPanel.class));
        UIIcon newsButton = this.panelButtonsMap.get(this.getPanel(UINewsPanel.class));
        UIIcon sandboxButton = this.panelButtonsMap.get(this.getPanel(UIDebugPanel.class));

        if (homeButton != null)
        {
            homeButton.setVisible(!stripped);
        }

        if (morphButton != null)
        {
            morphButton.setVisible(!stripped);
        }

        if (modelBlockButton != null)
        {
            modelBlockButton.setVisible(!stripped);
        }

        if (triggerBlockButton != null)
        {
            triggerBlockButton.setVisible(!stripped);
        }

        if (texturesButton != null)
        {
            texturesButton.setVisible(!stripped);
        }

        if (newsButton != null)
        {
            newsButton.setVisible(true);
            newsButton.marginLeft(stripped ? 0 : 10);
        }

        if (sandboxButton != null)
        {
            sandboxButton.setVisible(true);
            sandboxButton.marginLeft(0);
        }

        /* When stripped, hide the pinned container and force its area width to 0
           so that panelButtons (which starts at pinned.area right-edge + 5) snaps
           to the left. We do NOT change panelButtons's wTo: it still stretches to
           taskBar right minus 28 (globe button), which is exactly what we want. */
        if (stripped)
        {
            this.pinned.area.w = 0;
            /* Remove x offset so panelButtons starts at pinned.area.x + 0 + 5 = 5 */
            this.panelButtons.getFlex().relative = this.pinned.area;
            this.panelButtons.getFlex().x.set(1F, 0);
        }
        else
        {
            this.panelButtons.getFlex().relative = this.pinned.area;
            this.panelButtons.getFlex().x.set(1F, 5);
        }

        /* Restore wTo target in case it was cleared */
        this.panelButtons.getFlex().w.target = this.taskBar.area;
        this.panelButtons.getFlex().w.targetAnchor = 1F;
        this.panelButtons.getFlex().w.offset = -28;

        this.pinned.resize();
        this.panelButtons.resize();
        this.taskBar.resize();
    }

    public void setPanel(UIDashboardPanel panel)
    {
        UIDashboardPanel lastPanel = this.panel;

        if (this.panel != null)
        {
            this.panel.disappear();
            this.panel.removeFromParent();
        }

        this.panel = panel;

        this.getEvents().emit(new PanelEvent(this, lastPanel, panel));

        if (this.panel != null)
        {
            this.setPanelPlacement(panel);

            this.prepend(this.panel);
            this.panel.appear();
            this.panel.resize();
        }

        EditorSpectatorHelper.syncForPanel(this.panel);
    }

    private void setPanelPlacement(UIDashboardPanel panel)
    {
        panel.resetFlex().relative(this).w(1F).h(1F, -20);
    }

    public UIIcon registerPanel(UIDashboardPanel panel, IKey tooltip, Icon icon)
    {
        UIIcon button = new UIIcon(icon, (b) -> this.setPanel(panel));

        button.tooltip(tooltip, Direction.TOP);

        this.panels.add(panel);
        this.panelButtonsMap.put(panel, button);
        this.panelButtons.add(button);

        return button;
    }

    public UIIcon registerPinnedPanel(UIDashboardPanel panel, IKey tooltip, Icon icon)
    {
        UIIcon button = new UIIcon(icon, (b) -> this.setPanel(panel));

        button.tooltip(tooltip, Direction.TOP);

        this.panels.add(panel);
        this.panelButtonsMap.put(panel, button);
        this.pinned.add(button);

        return button;
    }

    public void registerHiddenPanel(UIDashboardPanel panel)
    {
        this.panels.add(panel);
    }

    public List<UIDashboardPanel> getVisiblePanels()
    {
        List<UIDashboardPanel> visible = new ArrayList<>();

        for (UIDashboardPanel p : this.panels)
        {
            if (this.panelButtonsMap.containsKey(p))
            {
                visible.add(p);
            }
        }

        return visible;
    }

    private void renderActiveHighlight(UIContext context)
    {
        if (this.panel == null) return;

        UIDashboardPanel current = this.panel.getMainPanel();
        UIIcon button = this.panelButtonsMap.get(current);

        while (button == null && current != null)
        {
            UIDashboardPanel next = current.getMainPanel();

            if (next == current) break;

            current = next;
            button = this.panelButtonsMap.get(current);
        }

        if (button != null)
        {
            renderHighlight(context.batcher, button.area);
        }
        else if (this.worldFilmsButton != null && this.panel instanceof UIWorldFilmsBrowserPanel)
        {
            renderHighlight(context.batcher, this.worldFilmsButton.area);
        }
    }

    protected void renderBackground(UIContext context)
    {
        Area area = this.taskBar.area;
        Area a = this.pinned.area;

        context.batcher.box(area.x, area.y, area.ex(), area.ey(), 0xFF141418);
        context.batcher.box(area.x, area.y, area.ex(), area.y + 1, 0xFF2A2A35);
        context.batcher.box(a.ex() + 2, a.y + 3, a.ex() + 3, a.ey() - 3, 0x22ffffff);
    }

    public static class PanelEvent extends UIEvent<UIDashboardPanels>
    {
        public final UIDashboardPanel lastPanel;
        public final UIDashboardPanel panel;

        public PanelEvent(UIDashboardPanels element, UIDashboardPanel lastPanel, UIDashboardPanel panel)
        {
            super(element);

            this.lastPanel = lastPanel;
            this.panel = panel;
        }
    }
}