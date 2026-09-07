package mchorse.bbs_mod.settings.ui;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.ui.ValueVideoSettings;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.ScrollDirection;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UISettingsOverlayPanel extends UIOverlayPanel
{
    private static final int SIDEBAR_WIDTH = 180;
    private static UISettingsOverlayPanel activeSession;

    public UIElement sidebarContainer;
    public UIScrollView sidebar;
    public UIElement panel;
    public UIScrollView options;
    public UITextbox search;

    private Settings settings;
    private String selectedCategoryId;
    private boolean isKeybindsSelected;
    private UISettingsTab currentTab;

    private final Map<String, String> sessionSnapshots = new HashMap<>();
    private final IValueListener sessionChangeListener = (v, f) ->
    {
        if (this.trackingSession && !this.applyingSession)
        {
            this.hasPendingChanges = true;
        }
    };
    private UIButton applyButton;
    private UIButton cancelButton;
    private UIIcon reload;
    private boolean trackingSession;
    private boolean applyingSession;
    private boolean hasPendingChanges;
    private float actionBarProgress;

    public UISettingsOverlayPanel()
    {
        super(UIKeys.CONFIG_TITLE);
        this.title.color(Colors.WHITE);
        this.resizable();
        this.content.w(1F);

        this.sidebarContainer = new UIElement();
        this.sidebarContainer.relative(this.content).x(0).y(0).w(SIDEBAR_WIDTH).h(1F);

        this.search = new UITextbox(100, (str) -> this.refresh());
        this.search.placeholder(UIKeys.GENERAL_SEARCH);
        this.search.relative(this.sidebarContainer).x(6).y(6).w(1F, -12).h(20);

        this.sidebar = new UIScrollView(ScrollDirection.VERTICAL);
        this.sidebar.relative(this.sidebarContainer).x(0).y(32).w(1F).h(1F, -32);
        this.sidebar.column(2).vertical().stretch().scroll().padding(6);

        this.panel = new UIElement();
        this.panel.relative(this.content).x(SIDEBAR_WIDTH).y(0).w(1F, -SIDEBAR_WIDTH).h(1F);

        this.options = new UIScrollView(ScrollDirection.VERTICAL);
        this.options.scroll.scrollSpeed = 51;
        this.options.relative(this.panel).x(6).y(6).w(1F, -12).h(1F, -12);
        this.options.column(8).scroll().vertical().stretch().padding(10).height(20);

        this.panel.add(this.options);
        this.sidebarContainer.add(this.search, this.sidebar);
        this.content.add(this.sidebarContainer, this.panel);

        for (Settings module : BBSMod.getSettings().modules.values())
        {
            module.postCallback(this.sessionChangeListener);
        }

        this.applyButton = new UIButton(UIKeys.CONFIG_APPLY, (b) -> this.applySettings());
        this.cancelButton = new UIButton(UIKeys.CONFIG_CANCEL, (b) -> this.cancelSessionChanges());
        this.applyButton.setVisible(false);
        this.cancelButton.setVisible(false);
        this.applyButton.relative(this).x(1F, -12).y(1F, -36).w(84).h(20).anchor(1F, 1F);
        this.cancelButton.relative(this.applyButton).x(0F, -6).w(84).h(20).anchor(0F, 1F);
        this.add(this.applyButton, this.cancelButton);

        this.reload = new UIIcon(Icons.REFRESH, (b) -> this.applySettings());
        this.reload.tooltip(UIKeys.CONFIG_RELOAD, Direction.LEFT);
        this.icons.removeAll();
        this.icons.relative(this).x(1F, -40).y(0).w(40).h(20);
        this.icons.row(0);
        this.icons.add(this.reload, this.close);

        this.rebuildTabs();
        this.markContainer();
    }

    public static boolean isDeferringLiveSettings()
    {
        return activeSession != null && activeSession.trackingSession && activeSession.hasPendingChanges;
    }

    private void beginSession()
    {
        activeSession = this;
        this.captureSessionSnapshot();
        this.trackingSession = true;
        this.hasPendingChanges = false;
        this.actionBarProgress = 0F;
    }

    private void captureSessionSnapshot()
    {
        this.sessionSnapshots.clear();

        for (Map.Entry<String, Settings> entry : BBSMod.getSettings().modules.entrySet())
        {
            this.sessionSnapshots.put(entry.getKey(), entry.getValue().toJson());
        }
    }

    private void cancelSessionChanges()
    {
        this.applyingSession = true;

        try
        {
            for (Map.Entry<String, String> entry : this.sessionSnapshots.entrySet())
            {
                Settings module = BBSMod.getSettings().modules.get(entry.getKey());

                if (module != null)
                {
                    module.fromData(DataToString.fromString(entry.getValue()));
                }
            }

            this.hasPendingChanges = false;
            this.actionBarProgress = 0F;
            this.refresh();
            UIUtils.playClick();
        }
        finally
        {
            this.applyingSession = false;
        }
    }

    @Override
    public void removeFromParent()
    {
        if (activeSession == this)
        {
            activeSession = null;
        }

        this.trackingSession = false;
        super.removeFromParent();
    }

    private void applySettings()
    {
        this.applyingSession = true;

        try
        {
            BBSSettings.syncAppliedAppearance();
            BBSModClient.reloadFromSettings();
            this.captureSessionSnapshot();
            this.hasPendingChanges = false;
            this.actionBarProgress = 0F;
            this.refresh();

            if (this.getContext() != null)
            {
                this.getContext().notifyInfo(UIKeys.CONFIG_APPLIED);
            }

            UIUtils.playClick();
        }
        finally
        {
            this.applyingSession = false;
        }
    }

    @Override
    public void render(UIContext context)
    {
        if (this.getParent() != null && !this.trackingSession)
        {
            this.beginSession();
        }

        if (this.getParent() == null)
        {
            this.trackingSession = false;

            if (activeSession == this)
            {
                activeSession = null;
            }
        }

        float target = this.hasPendingChanges ? 1F : 0F;
        this.actionBarProgress = Lerps.lerp(this.actionBarProgress, target, 0.22F);

        boolean showActions = this.actionBarProgress > 0.02F;
        this.applyButton.setVisible(showActions);
        this.cancelButton.setVisible(showActions);

        if (showActions)
        {
            int slide = (int) ((1F - this.actionBarProgress) * 30F);
            this.applyButton.area.setPoints(this.area.ex() - 96, this.area.ey() - 36 - slide, this.area.ex() - 12, this.area.ey() - 16 - slide);
            this.cancelButton.area.setPoints(this.area.ex() - 96, this.area.ey() - 62 - slide, this.area.ex() - 12, this.area.ey() - 42 - slide);
        }

        super.render(context);

        if (showActions)
        {
            int slide = (int) ((1F - this.actionBarProgress) * 30F);
            int x2 = this.area.ex() - 8;
            int y2 = this.area.ey() - 8 - slide;
            context.batcher.box(x2 - 92, y2 - 58, x2, y2, Colors.A90);
        }
    }

    private void rebuildTabs()
    {
        this.sidebar.removeAll();
        this.currentTab = null;

        UISettingsTab firstTab = null;

        Settings bbsSettings = BBSMod.getSettings().modules.get("bbs");
        if (bbsSettings != null)
        {
            for (ValueGroup category : bbsSettings.categories.values())
            {
                if (!category.isVisible())
                {
                    continue;
                }
                String categoryId = category.getId();
                IKey label = L10n.lang(UIValueFactory.getCategoryTitleKey(category));
                Icon icon = this.getCategoryIcon(categoryId);
                UISettingsTab tab = new UISettingsTab(label, icon, categoryId, false, (t) -> this.selectCategory(categoryId, t));
                tab.tooltip(label, Direction.RIGHT);
                this.sidebar.add(tab);

                if (firstTab == null)
                {
                    firstTab = tab;
                }
            }
        }

        Settings keybindsSettings = BBSMod.getSettings().modules.get("keybinds");
        if (keybindsSettings != null)
        {
            IKey label = L10n.lang(UIValueFactory.getTitleKey(keybindsSettings));
            UISettingsTab tab = new UISettingsTab(label, Icons.KEY_CAP, null, true, (t) -> this.selectKeybinds(t));
            tab.tooltip(label, Direction.RIGHT);
            this.sidebar.add(tab);

            if (firstTab == null)
            {
                firstTab = tab;
            }
        }

        if (firstTab != null)
        {
            if (firstTab.isKeybinds)
            {
                this.selectKeybinds(firstTab);
            }
            else
            {
                this.selectCategory(firstTab.categoryId, firstTab);
            }
        }
    }

    private Icon getCategoryIcon(String categoryId)
    {
        switch (categoryId)
        {
            case "appearance": return Icons.PROCESSOR;
            case "axes": return Icons.GRAPH;
            case "tutorials": return Icons.HELP;
            case "background": return Icons.IMAGE;
            case "chroma_sky": return Icons.GLOBE;
            case "scrollbars": return Icons.LIST;
            case "multiskin": return Icons.PLAYER;
            case "video": return Icons.VIDEO_CAMERA;
            case "editor": return Icons.CAMERA;
            case "replays": return Icons.POSE;
            case "recording": return Icons.PROPERTIES;
            case "model_blocks": return Icons.BLOCK;
            case "entity_selectors": return Icons.VISIBLE;
            case "dc": return Icons.SKULL;
            case "shader_curves": return Icons.CURVES;
            case "fluid_simulation": return Icons.FILTER;
            case "compatibility": return Icons.SAVE;
            case "audio": return Icons.SOUND;
            case "cdn": return Icons.USER;
            default: return Icons.SETTINGS;
        }
    }

    public void selectCategory(String categoryId, UISettingsTab tab)
    {
        this.settings = BBSMod.getSettings().modules.get("bbs");
        this.selectedCategoryId = categoryId;
        this.isKeybindsSelected = false;

        if (this.currentTab != null)
        {
            this.currentTab.selected = false;
        }

        this.currentTab = tab;

        if (this.currentTab != null)
        {
            this.currentTab.selected = true;
        }

        if (this.search != null)
        {
            this.search.setText("");
        }

        this.refresh();
    }

    public void selectCategory(String categoryId)
    {
        for (IUIElement element : this.sidebar.getChildren())
        {
            if (element instanceof UISettingsTab)
            {
                UISettingsTab tab = (UISettingsTab) element;

                if (categoryId.equals(tab.categoryId))
                {
                    this.selectCategory(categoryId, tab);
                    break;
                }
            }
        }
    }

    public void selectKeybinds(UISettingsTab tab)
    {
        this.settings = BBSMod.getSettings().modules.get("keybinds");
        this.selectedCategoryId = null;
        this.isKeybindsSelected = true;

        if (this.currentTab != null)
        {
            this.currentTab.selected = false;
        }

        this.currentTab = tab;

        if (this.currentTab != null)
        {
            this.currentTab.selected = true;
        }

        if (this.search != null)
        {
            this.search.setText("");
        }

        this.refresh();
    }

    public void refresh()
    {
        if (this.settings == null)
        {
            return;
        }

        this.options.removeAll();

        boolean first = true;
        String query = this.search.getText().trim().toLowerCase();

        for (ValueGroup category : this.settings.categories.values())
        {
            if (!category.isVisible())
            {
                continue;
            }

            if (query.isEmpty() && this.selectedCategoryId != null && !category.getId().equals(this.selectedCategoryId))
            {
                continue;
            }

            String catTitleKey = UIValueFactory.getCategoryTitleKey(category);
            String catTooltipKey = UIValueFactory.getCategoryTooltipKey(category);
            boolean categoryMatches = query.isEmpty() || this.matchesQuery(query,
                L10n.lang(catTitleKey).get(),
                L10n.lang(catTooltipKey).get(),
                category.getId()
            );

            UILabel label = UI.label(L10n.lang(catTitleKey)).labelAnchor(0, 1).color(0xff000000 | BBSSettings.accentRgb()).background(() -> 0xFF1A1A22);
            label.tooltip(L10n.lang(catTooltipKey), Direction.BOTTOM);

            if (category.getId().equals("video"))
            {
                label.h(20);

                UIIcon presets = new UIIcon(Icons.FILM, (b) ->
                {
                    this.getContext().replaceContextMenu((menu) ->
                    {
                        ValueVideoSettings videoSettings = BBSSettings.videoSettings;
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_720p, () ->
                        {
                            videoSettings.width.set(1280);
                            videoSettings.height.set(720);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_1080P, () ->
                        {
                            videoSettings.width.set(1920);
                            videoSettings.height.set(1080);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_SHORTS_1080P, () ->
                        {
                            videoSettings.width.set(1080);
                            videoSettings.height.set(1920);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_1440P, () ->
                        {
                            videoSettings.width.set(2560);
                            videoSettings.height.set(1440);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_4K, () ->
                        {
                            videoSettings.width.set(3840);
                            videoSettings.height.set(2160);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_CINEMASCOPE_1080P, () ->
                        {
                            videoSettings.width.set(1920);
                            videoSettings.height.set(804);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_CINEMASCOPE_1440P, () ->
                        {
                            videoSettings.width.set(2560);
                            videoSettings.height.set(1072);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_CINEMASCOPE_4K, () ->
                        {
                            videoSettings.width.set(3840);
                            videoSettings.height.set(1608);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_DCI_2K_SCOPE, () ->
                        {
                            videoSettings.width.set(2048);
                            videoSettings.height.set(858);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_DCI_2K_FLAT, () ->
                        {
                            videoSettings.width.set(1998);
                            videoSettings.height.set(1080);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_DCI_4K_SCOPE, () ->
                        {
                            videoSettings.width.set(4096);
                            videoSettings.height.set(1716);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_DCI_4K_FLAT, () ->
                        {
                            videoSettings.width.set(3996);
                            videoSettings.height.set(2160);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_ULTRAWIDE_1080P, () ->
                        {
                            videoSettings.width.set(2560);
                            videoSettings.height.set(1080);
                        });
                        menu.action(Icons.FILM, UIKeys.VIDEO_SETTINGS_PRESETS_ULTRAWIDE_1440P, () ->
                        {
                            videoSettings.width.set(3440);
                            videoSettings.height.set(1440);
                        });
                    });
                });
                presets.tooltip(UIKeys.GENERAL_PRESETS, Direction.LEFT);
                presets.relative(label).x(1F, -20).y(0).w(20).h(20);

                label.add(presets);
            }

            List<UIElement> options = new ArrayList<>();

            for (BaseValue value : category.getAll())
            {
                if (!value.isVisible())
                {
                    continue;
                }
                boolean valueMatches = categoryMatches || query.isEmpty() || this.matchesQuery(query,
                    L10n.lang(UIValueFactory.getValueLabelKey(value)).get(),
                    L10n.lang(UIValueFactory.getValueCommentKey(value)).get(),
                    value.getId()
                );

                if (!valueMatches)
                {
                    continue;
                }

                /* Populate interpolation labels for default interpolation settings on client side */
                if (value == BBSSettings.defaultInterpolation || value == BBSSettings.defaultModelInterpolation || value == BBSSettings.defaultPathInterpolation || value == BBSSettings.defaultCameraKeyframeInterpolation)
                {
                    try
                    {
                        List<IKey> interpKeys = new ArrayList<>();

                        for (String k : Interpolations.MAP.keySet())
                        {
                            interpKeys.add(UIKeys.C_INTERPOLATION.get(k));
                        }

                        if (value instanceof ValueInt)
                        {
                            ((ValueInt) value).modes(interpKeys.toArray(new IKey[0]));
                        }
                    }
                    catch (Throwable ignored) {}
                }

                if (value == BBSSettings.editorReplayHudPosition)
                {
                    if (value instanceof ValueInt)
                    {
                        String key = UIValueFactory.getValueLabelKey(value);

                        ((ValueInt) value).modes(
                            L10n.lang(key + ".top_left"),
                            L10n.lang(key + ".top_right"),
                            L10n.lang(key + ".bottom_left"),
                            L10n.lang(key + ".bottom_right")
                        );
                    }
                }

                if (value == BBSSettings.editorImportMode)
                {
                    if (value instanceof ValueInt)
                    {
                        String key = UIValueFactory.getValueLabelKey(value);

                        ((ValueInt) value).modes(
                            L10n.lang(key + ".safe"),
                            L10n.lang(key + ".original")
                        );
                    }
                }

                List<UIElement> elements = UIValueMap.create(value, this);

                for (UIElement element : elements)
                {
                    options.add(element);
                }
            }

            if (options.isEmpty())
            {
                continue;
            }

            UIElement firstContainer = UI.column(5, 0, 20, label, options.remove(0)).marginTop(first ? 0 : 24);

            this.options.add(firstContainer);

            for (UIElement element : options)
            {
                this.options.add(element);
            }

            first = false;
        }

        this.resize();
    }

    private boolean matchesQuery(String query, String... values)
    {
        if (query.isEmpty())
        {
            return true;
        }

        for (String value : values)
        {
            if (value != null && value.toLowerCase().contains(query))
            {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        // Main background
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF141418);
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF2A2A35, 1);

        // Header Row
        int headerH = 20;
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + headerH, 0xFF1A1A22);
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.y + headerH, 0xFF2A2A35, 1);

        // Left sidebar
        context.batcher.box(this.sidebarContainer.area.x, this.sidebarContainer.area.y, this.sidebarContainer.area.ex(), this.sidebarContainer.area.ey(), 0xFF111115);
        context.batcher.outline(this.sidebarContainer.area.x, this.sidebarContainer.area.y, this.sidebarContainer.area.ex(), this.sidebarContainer.area.ey(), 0xFF22222A, 1);

        if (this.close.area.isInside(context))
        {
            this.close.area.render(context.batcher, Colors.RED | Colors.A100);
        }
        else if (this.reload.area.isInside(context))
        {
            this.reload.area.render(context.batcher, Colors.setA(BBSSettings.accentRgb(), 0.75F));
        }

        // Resize handles
        int resizeColor = Colors.GRAY;
        int right = this.area.ex();
        int bottom = this.area.ey();
        context.batcher.box(right - 9, bottom - 1, right - 1, bottom, resizeColor);
        context.batcher.box(right - 1, bottom - 9, right, bottom - 1, resizeColor);
    }

    public static class UISettingsTab extends UIClickable<UISettingsTab>
    {
        public IKey label;
        public Icon icon;
        public boolean selected;
        public String categoryId;
        public boolean isKeybinds;

        public UISettingsTab(IKey label, Icon icon, String categoryId, boolean isKeybinds, Consumer<UISettingsTab> callback)
        {
            super(callback);

            this.label = label;
            this.icon = icon;
            this.categoryId = categoryId;
            this.isKeybinds = isKeybinds;
            this.h(20);
        }

        @Override
        protected UISettingsTab get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            int bg = this.selected ? 0xFF1D1D26 : (this.hover ? 0xFF181820 : 0xFF111115);
            context.batcher.box(this.area.x + 2, this.area.y, this.area.ex() - 2, this.area.ey(), bg);

            if (this.selected)
            {
                UIDashboardPanels.renderHighlight(context.batcher, this.area, Direction.LEFT);
            }
            else if (this.hover)
            {
                this.area.render(context.batcher, Colors.setA(Colors.WHITE, 0.1F));
            }

            int textX = this.area.x + 8;
            int textColor = this.selected ? (0xff000000 | BBSSettings.accentRgb()) : 0xFFCCCCCC;

            if (this.icon != null)
            {
                context.batcher.icon(this.icon, textColor, this.area.x + 6, this.area.my() - 8);
                textX = this.area.x + 24;
            }

            int y = this.area.my(context.batcher.getFont().getHeight());

            context.batcher.clip(this.area.x, this.area.y, this.area.w - 6, this.area.h, context);
            context.batcher.textShadow(this.label.get(), textX, y, textColor);
            context.batcher.unclip(context);
        }
    }
}
