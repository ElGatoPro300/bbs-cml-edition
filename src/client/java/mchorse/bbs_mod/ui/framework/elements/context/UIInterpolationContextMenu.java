package mchorse.bbs_mod.ui.framework.elements.context;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.InterpolationUtils;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.renderers.InterpolationRenderer;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.CustomInterpolation;
import mchorse.bbs_mod.utils.interps.CustomInterpolationManager;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolation;
import mchorse.bbs_mod.utils.interps.Interpolations;

import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UIInterpolationContextMenu extends UIContextMenu
{
    public static final Map<IInterp, Icon> INTERP_ICON_MAP = new HashMap<>();

    private static final int PADDING = 10;
    private static final int MARGIN = 5;
    private static final int GRAPH_HEIGHT = 80;
    private static final int ARGUMENTS_HEIGHT = 45;

    public UIElement grid;
    public UITrackpad v1;
    public UITrackpad v2;
    public UITrackpad v3;
    public UITrackpad v4;

    public UIIcon copy;
    public UIIcon paste;
    
    public UIIcon prev;
    public UIIcon next;

    private Runnable callback;
    private Interpolation interpolation;
    private Map<IInterp, UIIcon> icons = new HashMap<>();
    
    private int page = 0;

    static
    {
        INTERP_ICON_MAP.put(Interpolations.LINEAR, Icons.INTERP_LINEAR);
        INTERP_ICON_MAP.put(Interpolations.CONST, Icons.INTERP_CONST);
        INTERP_ICON_MAP.put(Interpolations.STEP, Icons.INTERP_STEP);
        INTERP_ICON_MAP.put(Interpolations.QUAD_IN, Icons.INTERP_QUAD_IN);
        INTERP_ICON_MAP.put(Interpolations.QUAD_OUT, Icons.INTERP_QUAD_OUT);
        INTERP_ICON_MAP.put(Interpolations.QUAD_INOUT, Icons.INTERP_QUAD_INOUT);
        INTERP_ICON_MAP.put(Interpolations.CUBIC_IN, Icons.INTERP_CUBIC_IN);
        INTERP_ICON_MAP.put(Interpolations.CUBIC_OUT, Icons.INTERP_CUBIC_OUT);
        INTERP_ICON_MAP.put(Interpolations.CUBIC_INOUT, Icons.INTERP_CUBIC_INOUT);
        INTERP_ICON_MAP.put(Interpolations.EXP_IN, Icons.INTERP_EXP_IN);
        INTERP_ICON_MAP.put(Interpolations.EXP_OUT, Icons.INTERP_EXP_OUT);
        INTERP_ICON_MAP.put(Interpolations.EXP_INOUT, Icons.INTERP_EXP_INOUT);
        INTERP_ICON_MAP.put(Interpolations.BACK_IN, Icons.INTERP_BACK_IN);
        INTERP_ICON_MAP.put(Interpolations.BACK_OUT, Icons.INTERP_BACK_OUT);
        INTERP_ICON_MAP.put(Interpolations.BACK_INOUT, Icons.INTERP_BACK_INOUT);
        INTERP_ICON_MAP.put(Interpolations.ELASTIC_IN, Icons.INTERP_ELASTIC_IN);
        INTERP_ICON_MAP.put(Interpolations.ELASTIC_OUT, Icons.INTERP_ELASTIC_OUT);
        INTERP_ICON_MAP.put(Interpolations.ELASTIC_INOUT, Icons.INTERP_ELASTIC_INOUT);
        INTERP_ICON_MAP.put(Interpolations.BOUNCE_IN, Icons.INTERP_BOUNCE_IN);
        INTERP_ICON_MAP.put(Interpolations.BOUNCE_OUT, Icons.INTERP_BOUNCE_OUT);
        INTERP_ICON_MAP.put(Interpolations.BOUNCE_INOUT, Icons.INTERP_BOUNCE_INOUT);
        INTERP_ICON_MAP.put(Interpolations.SINE_IN, Icons.INTERP_SINE_IN);
        INTERP_ICON_MAP.put(Interpolations.SINE_OUT, Icons.INTERP_SINE_OUT);
        INTERP_ICON_MAP.put(Interpolations.SINE_INOUT, Icons.INTERP_SINE_INOUT);
        INTERP_ICON_MAP.put(Interpolations.QUART_IN, Icons.INTERP_QUART_IN);
        INTERP_ICON_MAP.put(Interpolations.QUART_OUT, Icons.INTERP_QUART_OUT);
        INTERP_ICON_MAP.put(Interpolations.QUART_INOUT, Icons.INTERP_QUART_INOUT);
        INTERP_ICON_MAP.put(Interpolations.QUINT_IN, Icons.INTERP_QUINT_IN);
        INTERP_ICON_MAP.put(Interpolations.QUINT_OUT, Icons.INTERP_QUINT_OUT);
        INTERP_ICON_MAP.put(Interpolations.QUINT_INOUT, Icons.INTERP_QUINT_INOUT);
        INTERP_ICON_MAP.put(Interpolations.CIRCLE_IN, Icons.INTERP_CIRCLE_IN);
        INTERP_ICON_MAP.put(Interpolations.CIRCLE_OUT, Icons.INTERP_CIRCLE_OUT);
        INTERP_ICON_MAP.put(Interpolations.CIRCLE_INOUT, Icons.INTERP_CIRCLE_INOUT);
        INTERP_ICON_MAP.put(Interpolations.CUBIC, Icons.INTERP_CUBIC_INOUT);
        INTERP_ICON_MAP.put(Interpolations.HERMITE, Icons.INTERP_CUBIC_INOUT);
        INTERP_ICON_MAP.put(Interpolations.BEZIER, Icons.INTERP_BEZIER);
        INTERP_ICON_MAP.put(Interpolations.BSPLINE, Icons.INTERP_BSPLINE);
        INTERP_ICON_MAP.put(Interpolations.AKIMA, Icons.INTERP_AKIMA);
        INTERP_ICON_MAP.put(Interpolations.TCB, Icons.INTERP_TCB);
        INTERP_ICON_MAP.put(Interpolations.NURBS, Icons.INTERP_NURBS);
    }

    public UIInterpolationContextMenu(Interpolation interpolation)
    {
        this.interpolation = interpolation;

        int w = 120;
        int h = (int) Math.ceil(interpolation.getMap().values().size() / (w / 20F)) * 20;
        int gridY = PADDING + GRAPH_HEIGHT + MARGIN + ARGUMENTS_HEIGHT;

        this.v1 = new UITrackpad((v) ->
        {
            this.interpolation.setV1(v);
            this.accept();
        });
        this.v2 = new UITrackpad((v) ->
        {
            this.interpolation.setV2(v);
            this.accept();
        });
        this.v3 = new UITrackpad((v) ->
        {
            this.interpolation.setV3(v);
            this.accept();
        });
        this.v4 = new UITrackpad((v) ->
        {
            this.interpolation.setV4(v);
            this.accept();
        });

        this.v1.setValue(interpolation.getV1());
        this.v2.setValue(interpolation.getV2());
        this.v3.setValue(interpolation.getV3());
        this.v4.setValue(interpolation.getV4());

        this.copy = new UIIcon(Icons.COPY, (b) ->
        {
            MapType data = new MapType();

            data.put("interp", this.interpolation.toData());
            Window.setInMemoryClipboard(data, "_CopyInterpolation");
        });
        this.copy.tooltip(UIKeys.INTERPOLATIONS_CONTEXT_COPY);

        this.paste = new UIIcon(Icons.PASTE, (b) ->
        {
            MapType copy = Window.getClipboardMap("_CopyInterpolation");

            if (copy == null)
            {
                return;
            }

            BaseValue.edit(this.interpolation, (v) -> v.fromData(copy.get("interp")));

            if (this.callback != null)
            {
                this.callback.run();
            }
        });
        this.paste.tooltip(UIKeys.INTERPOLATIONS_CONTEXT_PASTE);

        this.grid = new UIElement();
        this.grid.relative(this).xy(PADDING, gridY).w(w).h(h).grid(0).items(6);

        this.prev = new UIIcon(Icons.ARROW_LEFT, (b) -> this.setPage(this.page - 1));
        this.next = new UIIcon(Icons.ARROW_RIGHT, (b) -> this.setPage(this.page + 1));

        this.prev.relative(this).xy(PADDING, gridY + h + PADDING).w(w / 2).h(20);
        this.next.relative(this).xy(PADDING + w / 2, gridY + h + PADDING).w(w / 2).h(20);

        UIElement vs = UI.column(UI.row(this.v1, this.v2, this.copy), UI.row(this.v3, this.v4, this.paste));

        vs.relative(this).xy(PADDING, PADDING + GRAPH_HEIGHT + MARGIN).w(w);

        this.wh(w + PADDING * 2, gridY + h + PADDING + 20 + PADDING);
        this.add(vs, this.grid, this.prev, this.next);

        this.updateGrid();
        this.updatePageNavigation();
    }

    private void setPage(int page)
    {
        if (page < 0 || page > this.getMaxPage())
        {
            return;
        }

        this.page = page;
        this.updateGrid();
        this.updatePageNavigation();
    }

    private int getMaxPage()
    {
        CustomInterpolationManager.INSTANCE.load();
        int customCount = CustomInterpolationManager.INSTANCE.getList().size();
        int itemsPerPage = 36;
        int totalCustomItems = customCount + 1; // +1 for Add button
        int customPages = (int) Math.ceil(totalCustomItems / (double) itemsPerPage);
        
        return Math.max(1, customPages);
    }

    private void updatePageNavigation()
    {
        this.prev.setEnabled(this.page > 0);
        this.next.setEnabled(this.page < this.getMaxPage());
    }

    private void updateGrid()
    {
        this.grid.removeAll();
        this.icons.clear();
        this.keys().keybinds.clear();

        if (this.page == 0)
        {
            for (IInterp value : this.interpolation.getMap().values())
            {
                UIIcon icon = new UIIcon(INTERP_ICON_MAP.getOrDefault(value, Icons.INTERP_LINEAR), (b) ->
                {
                    this.interpolation.setInterp(value);
                    this.accept();
                });

                icon.tooltip(InterpolationUtils.getName(value));
                this.grid.add(icon);
                this.icons.put(value, icon);
                this.setupKeybind(value, icon);
            }
        }
        else
        {
            CustomInterpolationManager.INSTANCE.load();
            List<CustomInterpolation> list = CustomInterpolationManager.INSTANCE.getList();
            
            int itemsPerPage = 36;
            int startIndex = (this.page - 1) * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, list.size());

            for (int i = startIndex; i < endIndex; i++)
            {
                CustomInterpolation interp = list.get(i);
                UIIcon icon = new UIIcon(Icons.INTERP_CUSTOM, (b) ->
                {
                    this.interpolation.setInterp(interp);
                    this.accept();
                });
                icon.tooltip(IKey.raw(interp.getKey()));
                this.grid.add(icon);
                this.icons.put(interp, icon);
            }
            
            if (list.size() >= startIndex && list.size() < startIndex + itemsPerPage)
            {
                UIIcon icon = new UIIcon(Icons.INTERP_ADD, (b) ->
                {
                    UICustomInterpolationPanel panel = new UICustomInterpolationPanel()
                        .onSave((custom) ->
                        {
                            this.interpolation.setInterp(custom);
                            this.accept();
                        });

                    panel.onClose((e) -> this.updateGrid());
                    UIOverlay.addOverlay(this.getContext(), panel, 600, 400);
                });
                icon.tooltip(UIKeys.INTERPOLATIONS_CONTEXT_ADD);
    
                this.grid.add(icon);
            }
        }

        this.grid.resize();
    }

    private void accept()
    {
        if (this.callback != null)
        {
            this.callback.run();
        }
    }

    private void setupKeybind(IInterp interp, UIIcon icon)
    {
        IKey label = InterpolationUtils.getName(interp);
        IKey category = UIKeys.INTERPOLATIONS_KEY_CATEGORY;
        String key = interp.getKey();
        KeyCombo combo = new KeyCombo(label, interp.getKeyCode());

        if (key.endsWith("_in"))
        {
            combo = new KeyCombo(label, interp.getKeyCode(), GLFW.GLFW_KEY_LEFT_SHIFT);
        }
        else if (key.endsWith("_out"))
        {
            combo = new KeyCombo(label, interp.getKeyCode(), GLFW.GLFW_KEY_LEFT_CONTROL);
        }

        this.keys().register(combo.category(category), icon::clickItself);
    }

    public UIInterpolationContextMenu callback(Runnable callback)
    {
        this.callback = callback;

        return this;
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        this.xy(context.mouseX(), context.mouseY()).bounds(context.menu.overlay, 5);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        int color = BBSSettings.primaryColor.get();
        IInterp interp = this.interpolation.getInterp();
        UIIcon icon = this.icons.get(interp);
        Color fg = new Color().set(color);

        fg.a = 0.5F;

        InterpolationRenderer.renderInterpolationGraph(this.interpolation, context, fg, Colors.WHITE, this.area.x + PADDING, this.area.y + PADDING, this.area.w - PADDING * 2, GRAPH_HEIGHT, 20, 15);

        if (icon == null && interp != null)
        {
            /* Fallback: try to find the icon by checking equality with keys in the map
             This handles cases where the interp object is a different instance but equals() returns true
            */
            for (Map.Entry<IInterp, UIIcon> entry : this.icons.entrySet())
            {
                if (entry.getKey().equals(interp))
                {
                    icon = entry.getValue();
                    break;
                }
            }
        }

        if (icon != null)
        {
            UIDashboardPanels.renderHighlight(context.batcher, icon.area, Direction.BOTTOM);
        }
    }
}
