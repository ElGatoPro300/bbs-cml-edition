package mchorse.bbs_mod.ui.dashboard;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.text.RtlAwtTextRenderer;
import mchorse.bbs_mod.text.RtlTextEngine;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIAboutOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIOpenAssetOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.utils.UIGraphPanel;
import mchorse.bbs_mod.ui.film.UIFilmLogOverlayPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIWorldFilmsBrowserPanel;
import mchorse.bbs_mod.ui.film.utils.FilmProjectHandler;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.events.UIRemovedEvent;
import mchorse.bbs_mod.ui.framework.elements.overlay.UICreateAssetOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.selectors.UISelectorsOverlayPanel;
import mchorse.bbs_mod.ui.triggers.TriggerKeys;
import mchorse.bbs_mod.ui.triggers.UITriggerBlockPanel;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.ContextSeparatorAction;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.keys.KeyCombo;
import mchorse.bbs_mod.ui.utils.keys.Keybind;
import mchorse.bbs_mod.utils.RecentAssetsTracker;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.repos.IRepository;

import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

public class UIMainMenuBar extends UIElement
{
    private UIDashboard dashboard;
    UIMenuButton activeButton = null;
    UIWorldMenuButton activeWorldButton = null;
    private UIMenuButton fileMenu;
    private UIMenuButton editMenu;
    private UIMenuButton toolsMenu;
    private UIMenuButton windowMenu;
    private UIMenuButton helpMenu;
    private UIWorldMenuButton worldButton;

    public UIMainMenuBar(UIDashboard dashboard)
    {
        this.dashboard = dashboard;

        this.h(20);

        UIElement brand = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                String title = "BBS";
                int y = this.area.my(context.batcher.getFont().getHeight());

                context.batcher.textShadow(title, this.area.x, y, 0xFFCCCCCC);
                super.render(context);
            }
        };
        brand.w(25).marginLeft(6);

        this.add(brand);
        this.fileMenu = new UIMenuButton(UIKeys.RAW_FILE, this, this::buildFileMenu);
        this.editMenu = new UIMenuButton(UIKeys.RAW_EDIT, this, this::buildEditMenu);
        this.toolsMenu = new UIMenuButton(UIKeys.RAW_TOOLS, this, this::buildToolsMenu);
        this.windowMenu = new UIMenuButton(UIKeys.RAW_WINDOW, this, this::buildWindowMenu);
        this.helpMenu = new UIMenuButton(UIKeys.RAW_HELP, this, this::buildHelpMenu);
        this.worldButton = new UIWorldMenuButton(UIKeys.RAW_WORLD, this);

        this.add(this.fileMenu, this.editMenu, this.toolsMenu, this.windowMenu, this.helpMenu, this.worldButton);

        this.row(2).preferred(999);
    }

    public void updateForPanel(UIDashboardPanel panel)
    {
        boolean stripped = UIWorldFilmsBrowserPanel.isBrowserPanel(panel);

        if (this.editMenu != null)
        {
            this.editMenu.setVisible(!stripped);
        }

        if (this.toolsMenu != null)
        {
            this.toolsMenu.setVisible(!stripped);
        }

        if (this.windowMenu != null)
        {
            this.windowMenu.setVisible(!stripped);
        }

        if (this.worldButton != null)
        {
            this.worldButton.setVisible(!stripped);
        }

        this.resize();
    }

    @Override
    public void render(UIContext context)
    {
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF141418);
        context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), 0xFF2A2A35);

        super.render(context);
    }

    /* ------------------------------------------------------------------ */
    /* Menu open/close                                                       */
    /* ------------------------------------------------------------------ */

    void toggleMenu(UIMenuButton button, Consumer<ContextMenuManager> consumer)
    {
        UIContext context = this.getContext();

        context.closeContextMenu();
        this.activeButton = null;
        this.activeWorldButton = null;

        /* Use wasActiveLastFrame (captured in render, before events fire) so that
           the context menu closing itself first doesn't confuse the toggle check. */
        if (!button.wasActiveLastFrame)
        {
            this.openMenuBelow(button, consumer);
        }
    }

    void openMenuBelow(UIMenuButton button, Consumer<ContextMenuManager> consumer)
    {
        UIContext context = this.getContext();

        this.activeWorldButton = null;

        ContextMenuManager manager = new ContextMenuManager();
        UICascadingMenu customMenu = new UICascadingMenu();
        manager.custom(customMenu);

        consumer.accept(manager);
        manager.create();
        customMenu.getEvents().register(UIRemovedEvent.class, (e) -> this.activeButton = null);

        context.replaceContextMenu(customMenu);

        if (context.contextMenu != null)
        {
            context.contextMenu.getFlex().x.set(0, button.area.x);
            context.contextMenu.getFlex().y.set(0, button.area.ey());
            context.contextMenu.bounds(context.menu.overlay, 5);
            context.contextMenu.resize();
        }

        this.activeButton = button;
    }

    void toggleWorldMenu(UIWorldMenuButton button)
    {
        UIContext context = this.getContext();

        context.closeContextMenu();
        this.activeButton = null;
        this.activeWorldButton = null;

        if (!button.wasActiveLastFrame)
        {
            this.openWorldDropdown(button);
        }
    }

    void openWorldDropdown(UIWorldMenuButton button)
    {
        UIContext context = this.getContext();
        UIWorldDropdownMenu menu = new UIWorldDropdownMenu();

        menu.getEvents().register(UIRemovedEvent.class, (e) -> this.activeWorldButton = null);
        this.activeButton = null;
        context.replaceContextMenu(menu);

        if (context.contextMenu != null)
        {
            int maxH = Math.max(114, context.menu.height - button.area.ey() - 10);

            menu.setMaxHeight(maxH);
            context.contextMenu.getFlex().x.set(0, button.area.x);
            context.contextMenu.getFlex().y.set(0, button.area.ey());
            context.contextMenu.bounds(context.menu.overlay, 5);
            context.contextMenu.resize();
        }

        this.activeWorldButton = button;
    }

    boolean hasAnyMenuOpen()
    {
        return this.activeButton != null || this.activeWorldButton != null;
    }

    /* ------------------------------------------------------------------ */
    /* Menu builders                                                         */
    /* ------------------------------------------------------------------ */

    private void buildFileMenu(ContextMenuManager menu)
    {
        boolean stripped = UIWorldFilmsBrowserPanel.isBrowserPanel(this.dashboard.panels.panel);

        if (!stripped)
        {
            menu.action(Icons.ADD, UIKeys.RAW_NEW, () -> this.openNewSubmenu());
            menu.action(Icons.FOLDER, UIKeys.RAW_OPEN, () -> this.openOpenPopup());
            menu.action(Icons.TIME, UIKeys.RAW_RECENT, () -> this.openRecentSubmenu());

            if (this.dashboard.panels.panel instanceof UIFilmPanel filmPanel && filmPanel.getData() != null)
            {
                menu.action(Icons.UPLOAD, UIKeys.FILM_EXPORT_PROJECT, () -> FilmProjectHandler.exportProject(filmPanel));
            }
        }

        menu.action(Icons.SETTINGS, UIKeys.CONFIG_TITLE, () -> UIOverlay.addOverlay(this.getContext(), this.dashboard.settingsPanel, 580, 340));
        menu.action(Icons.JOYSTICK, UIKeys.ADDONS_TITLE, () -> UIOverlay.addOverlay(this.getContext(), this.dashboard.addonsPanel, 520, 320));
    }

    private void buildEditMenu(ContextMenuManager menu)
    {
        menu.action(Icons.UNDO, UIKeys.CAMERA_EDITOR_KEYS_EDITOR_UNDO, () -> this.triggerKey(Keys.UNDO));
        menu.action(Icons.REDO, UIKeys.CAMERA_EDITOR_KEYS_EDITOR_REDO, () -> this.triggerKey(Keys.REDO));

        if (this.dashboard.panels.panel instanceof UIFilmPanel film)
        {
            menu.action(Icons.LIST, UIKeys.FILM_OPEN_HISTORY, film::openUndoHistory);
        }
    }

    private void buildToolsMenu(ContextMenuManager menu)
    {
        menu.action(Icons.PROPERTIES, UIKeys.SELECTORS_TITLE, () ->
            UIOverlay.addOverlayRight(this.getContext(), new UISelectorsOverlayPanel(), 240));
        menu.action(Icons.GRAPH, UIKeys.GRAPH_TOOLTIP, () -> {
            if (this.dashboard.documentTabsBar != null)
            {
                this.dashboard.documentTabsBar.addOrActivate(ContentType.GRAPH, "graph_calculator");
            }
        });

        if (this.dashboard.panels.panel instanceof UIFilmPanel filmPanel && filmPanel.getData() != null)
        {
            filmPanel.addToolMenuActions(menu);

            menu.action(Icons.TIME, UIKeys.FILM_LOG_TOOLS, () -> {
                UIOverlay.addOverlay(this.getContext(), new UIFilmLogOverlayPanel(filmPanel), 280, 240);
            });
        }
    }

    private void buildHelpMenu(ContextMenuManager menu)
    {
        menu.action(Icons.HELP, UIKeys.RAW_ABOUT, () -> UIOverlay.addOverlay(this.getContext(), new UIAboutOverlayPanel(UIKeys.RAW_ABOUT, this.dashboard), 560, 440));
    }

    private void buildWindowMenu(ContextMenuManager menu)
    {
        if (this.dashboard.panels.panel instanceof UIModelBlockPanel panel)
        {
            menu.action(panel.isLeftVisible() ? Icons.CHECKMARK : Icons.NONE, UIKeys.MODEL_BLOCKS_TITLE, () ->
            {
                panel.setLeftVisible(!panel.isLeftVisible());
            });
            menu.action(panel.isMiddleVisible() ? Icons.CHECKMARK : Icons.NONE, UIKeys.MODEL_BLOCKS_PROPERTIES, () ->
            {
                panel.setMiddleVisible(!panel.isMiddleVisible());
            });
            menu.action(panel.isRightVisible() ? Icons.CHECKMARK : Icons.NONE, UIKeys.MODEL_BLOCKS_TRANSFORMS, () ->
            {
                panel.setRightVisible(!panel.isRightVisible());
            });
            menu.action(Icons.REFRESH, UIKeys.DASHBOARD_MENU_RESET_LAYOUT, panel::resetLayout);
        }
        else if (this.dashboard.panels.panel instanceof UITriggerBlockPanel trigger)
        {
            menu.action(trigger.isListVisible() ? Icons.CHECKMARK : Icons.NONE, TriggerKeys.TITLE, () ->
            {
                trigger.setListVisible(!trigger.isListVisible());
            });
            menu.action(trigger.isActionsVisible() ? Icons.CHECKMARK : Icons.NONE, TriggerKeys.ACTIONS, () ->
            {
                trigger.setActionsVisible(!trigger.isActionsVisible());
            });
            menu.action(trigger.isGeometryVisible() ? Icons.CHECKMARK : Icons.NONE, TriggerKeys.GEOMETRY, () ->
            {
                trigger.setGeometryVisible(!trigger.isGeometryVisible());
            });
            menu.action(Icons.REFRESH, UIKeys.DASHBOARD_MENU_RESET_LAYOUT, trigger::resetLayout);
        }
        else if (this.dashboard.panels.panel instanceof UIFilmPanel film)
        {
            this.buildWindowMenuForFilm(menu, film);
        }
        else if (this.dashboard.panels.panel instanceof UIParticleSchemePanel particles)
        {
            this.buildWindowMenuForParticles(menu, particles);
        }
        else
        {
            menu.action(Icons.NONE, UIKeys.DASHBOARD_MENU_NO_WINDOWS, () -> {});
        }
    }

    private void buildWindowMenuForParticles(ContextMenuManager menu, UIParticleSchemePanel particles)
    {
        UICascadingMenu mainMenu = (UICascadingMenu) menu.menu;

        menu.action(this.createNormalWindowAction(particles.isWindowPanelVisible("preview") ? Icons.CHECKMARK : Icons.NONE, UIKeys.RAW_VIEWPORT, mainMenu, () ->
        {
            particles.setWindowPanelVisible("preview", !particles.isWindowPanelVisible("preview"));
            this.openWindowMenuParticles(particles);
        }));

        menu.action(this.createNormalWindowAction(particles.isWindowPanelVisible("general") ? Icons.CHECKMARK : Icons.NONE, UIKeys.SNOWSTORM_GENERAL_TITLE, mainMenu, () ->
        {
            particles.setWindowPanelVisible("general", !particles.isWindowPanelVisible("general"));
            this.openWindowMenuParticles(particles);
        }));

        menu.action(this.createNormalWindowAction(particles.isWindowPanelVisible("emitter") ? Icons.CHECKMARK : Icons.NONE, UIKeys.SNOWSTORM_EMITTER_TITLE, mainMenu, () ->
        {
            particles.setWindowPanelVisible("emitter", !particles.isWindowPanelVisible("emitter"));
            this.openWindowMenuParticles(particles);
        }));

        menu.action(this.createNormalWindowAction(particles.isWindowPanelVisible("particle") ? Icons.CHECKMARK : Icons.NONE, UIKeys.SNOWSTORM_PARTICLE_TITLE, mainMenu, () ->
        {
            particles.setWindowPanelVisible("particle", !particles.isWindowPanelVisible("particle"));
            this.openWindowMenuParticles(particles);
        }));

        menu.action(this.createNormalWindowAction(particles.isWindowPanelVisible("appearance") ? Icons.CHECKMARK : Icons.NONE, UIKeys.SNOWSTORM_APPEARANCE_TITLE, mainMenu, () ->
        {
            particles.setWindowPanelVisible("appearance", !particles.isWindowPanelVisible("appearance"));
            this.openWindowMenuParticles(particles);
        }));

        menu.action(this.createNormalWindowAction(particles.isWindowPanelVisible("molang") ? Icons.CHECKMARK : Icons.NONE, UIKeys.SNOWSTORM_MOLANG_TITLE, mainMenu, () ->
        {
            particles.setWindowPanelVisible("molang", !particles.isWindowPanelVisible("molang"));
            this.openWindowMenuParticles(particles);
        }));

        menu.action(new ContextSeparatorAction());
        menu.action(this.createNormalWindowAction(Icons.TRASH, UIKeys.SNOWSTORM_RESTART_EMITTER, mainMenu, particles::restartEmitter));
        menu.action(new ContextSeparatorAction());
        menu.action(this.createNormalWindowAction(Icons.REFRESH, UIKeys.DASHBOARD_MENU_RESET_LAYOUT, mainMenu, particles::resetLayout));
        menu.action(this.createNormalWindowAction(particles.isLayoutLocked() ? Icons.LOCKED : Icons.UNLOCKED, particles.isLayoutLocked() ? UIKeys.FILM_LAYOUT_UNLOCK : UIKeys.FILM_LAYOUT_LOCK, mainMenu, particles::toggleLayoutLock));
        menu.action(this.createNormalWindowAction(Icons.SAVED, UIKeys.FILM_LAYOUT_PRESETS, mainMenu, () ->
        {
            int x = this.activeButton == null ? this.area.x : this.activeButton.area.x;
            int y = this.activeButton == null ? this.area.ey() : this.activeButton.area.ey();

            particles.openLayoutPresets(x, y);
        }));
    }

    private void openWindowMenuParticles(UIParticleSchemePanel particles)
    {
        UIContextMenu oldMenu = this.getContext().contextMenu;

        ContextMenuManager manager = new ContextMenuManager();
        UICascadingMenu customMenu = new UICascadingMenu()
        {
            @Override
            public void setMouse(UIContext context)
            {
                int w = 100;
                for (ContextAction action : this.actions.getList())
                {
                    w = Math.max(action.getWidth(context.batcher.getFont()), w);
                }

                int posX = oldMenu == null ? context.mouseX() : oldMenu.area.x;
                int posY = oldMenu == null ? context.mouseY() : oldMenu.area.y;

                this.set(posX, posY, w, 0).h(this.actions.scroll.scrollSize).maxH(context.menu.height - 10).bounds(context.menu.overlay, 5);
            }
        };

        manager.custom(customMenu);
        this.buildWindowMenuForParticles(manager, particles);
        manager.create();

        this.getContext().replaceContextMenu(customMenu);
    }

    private void buildWindowMenuForFilm(ContextMenuManager menu, UIFilmPanel film)
    {
        UICascadingMenu mainMenu = (UICascadingMenu) menu.menu;

        menu.action(this.createHoverSubmenuAction(Icons.COLLAPSED, UIKeys.RAW_TIMELINE, mainMenu, (y) ->
        {
            this.openWindowTimelineSubmenuCascading(film, mainMenu, y);
        }));

        menu.action(this.createHoverSubmenuAction(Icons.COLLAPSED, UIKeys.RAW_PROPERTIES, mainMenu, (y) ->
        {
            this.openWindowPropertiesSubmenuCascading(film, mainMenu, y);
        }));

        menu.action(new ContextSeparatorAction());

        menu.action(this.createNormalWindowAction(film.isWindowPanelVisible("preview") ? Icons.CHECKMARK : Icons.NONE, UIKeys.RAW_VIEWPORT, mainMenu, () ->
        {
            film.setWindowPanelVisible("preview", !film.isWindowPanelVisible("preview"));
            this.openWindowMenu(film);
        }));

        menu.action(this.createNormalWindowAction(film.isWindowPanelVisible("replaysPanel") ? Icons.CHECKMARK : Icons.NONE, UIKeys.FILM_REPLAY_TITLE, mainMenu, () ->
        {
            film.setWindowPanelVisible("replaysPanel", !film.isWindowPanelVisible("replaysPanel"));
            this.openWindowMenu(film);
        }));

        if (BBSSettings.editorSeparateReplayPropertiesPanel == null || BBSSettings.editorSeparateReplayPropertiesPanel.get())
        {
            menu.action(this.createNormalWindowAction(film.isWindowPanelVisible("replaysPropertiesPanel") ? Icons.CHECKMARK : Icons.NONE, UIKeys.FILM_REPLAY_SECTION_GENERAL, mainMenu, () ->
            {
                film.setWindowPanelVisible("replaysPropertiesPanel", !film.isWindowPanelVisible("replaysPropertiesPanel"));
                this.openWindowMenu(film);
            }));
        }

        menu.action(new ContextSeparatorAction());
        menu.action(this.createNormalWindowAction(Icons.REFRESH, UIKeys.DASHBOARD_MENU_RESET_LAYOUT, mainMenu, film::resetLayout));
        menu.action(this.createNormalWindowAction(film.isLayoutLocked() ? Icons.LOCKED : Icons.UNLOCKED, film.isLayoutLocked() ? UIKeys.FILM_LAYOUT_UNLOCK : UIKeys.FILM_LAYOUT_LOCK, mainMenu, film::toggleLayoutLockFromMenu));
        menu.action(this.createNormalWindowAction(Icons.SAVED, UIKeys.FILM_LAYOUT_PRESETS, mainMenu, () ->
        {
            int x = this.activeButton == null ? this.area.x : this.activeButton.area.x;
            int y = this.activeButton == null ? this.area.ey() : this.activeButton.area.ey();

            film.openLayoutPresetsFromMenu(x, y);
        }));
    }

    private void openWindowMenu(UIFilmPanel film)
    {
        UIContextMenu oldMenu = this.getContext().contextMenu;

        ContextMenuManager manager = new ContextMenuManager();
        UICascadingMenu customMenu = new UICascadingMenu()
        {
            @Override
            public void setMouse(UIContext context)
            {
                int w = 100;
                for (ContextAction action : this.actions.getList())
                {
                    w = Math.max(action.getWidth(context.batcher.getFont()), w);
                }

                int posX = oldMenu == null ? context.mouseX() : oldMenu.area.x;
                int posY = oldMenu == null ? context.mouseY() : oldMenu.area.y;

                this.set(posX, posY, w, 0).h(this.actions.scroll.scrollSize).maxH(context.menu.height - 10).bounds(context.menu.overlay, 5);
            }
        };

        manager.custom(customMenu);
        this.buildWindowMenuForFilm(manager, film);
        manager.create();

        this.getContext().replaceContextMenu(customMenu);
    }

    private void openWindowTimelineSubmenuCascading(UIFilmPanel film, UICascadingMenu mainMenu, int itemY)
    {
        if (mainMenu.submenu != null)
        {
            mainMenu.submenu.removeFromParent();
        }

        ContextMenuManager manager = new ContextMenuManager();
        UISimpleContextMenu submenu = new UISimpleContextMenu()
        {
            @Override
            public void setMouse(UIContext context)
            {
                int w = 100;
                for (ContextAction action : this.actions.getList())
                {
                    w = Math.max(action.getWidth(context.batcher.getFont()), w);
                }

                int posX = mainMenu.area.ex();
                int posY = itemY;

                this.set(posX, posY, w, 0).h(this.actions.scroll.scrollSize).maxH(context.menu.height - 10).bounds(context.menu.overlay, 5);
            }

            @Override
            public boolean subMouseClicked(UIContext context)
            {
                if (!this.area.isInside(context) && !mainMenu.area.isInside(context))
                {
                    this.removeFromParent();
                    mainMenu.removeFromParent();
                }
                return super.subMouseClicked(context);
            }
        };

        manager.custom(submenu);

        manager.action(film.isWindowPanelVisible("cameraTimeline") ? Icons.CHECKMARK : Icons.NONE, UIKeys.FILM_WORKSPACE_CAMERA, () ->
        {
            film.setWindowPanelVisible("cameraTimeline", !film.isWindowPanelVisible("cameraTimeline"));
            this.openWindowTimelineSubmenuCascading(film, mainMenu, itemY);
        });
        manager.action(film.isWindowPanelVisible("replayTimeline") ? Icons.CHECKMARK : Icons.NONE, UIKeys.FILM_WORKSPACE_REPLAY, () ->
        {
            film.setWindowPanelVisible("replayTimeline", !film.isWindowPanelVisible("replayTimeline"));
            this.openWindowTimelineSubmenuCascading(film, mainMenu, itemY);
        });
        manager.action(film.isWindowPanelVisible("actionTimeline") ? Icons.CHECKMARK : Icons.NONE, UIKeys.FILM_WORKSPACE_ACTION, () ->
        {
            film.setWindowPanelVisible("actionTimeline", !film.isWindowPanelVisible("actionTimeline"));
            this.openWindowTimelineSubmenuCascading(film, mainMenu, itemY);
        });

        manager.create();
        submenu.setMouse(this.getContext());
        submenu.resize();

        mainMenu.submenu = submenu;
        this.getContext().menu.overlay.add(submenu);
    }

    private void openWindowPropertiesSubmenuCascading(UIFilmPanel film, UICascadingMenu mainMenu, int itemY)
    {
        if (mainMenu.submenu != null)
        {
            mainMenu.submenu.removeFromParent();
        }

        ContextMenuManager manager = new ContextMenuManager();
        UISimpleContextMenu submenu = new UISimpleContextMenu()
        {
            @Override
            public void setMouse(UIContext context)
            {
                int w = 100;
                for (ContextAction action : this.actions.getList())
                {
                    w = Math.max(action.getWidth(context.batcher.getFont()), w);
                }

                int posX = mainMenu.area.ex();
                int posY = itemY;

                this.set(posX, posY, w, 0).h(this.actions.scroll.scrollSize).maxH(context.menu.height - 10).bounds(context.menu.overlay, 5);
            }

            @Override
            public boolean subMouseClicked(UIContext context)
            {
                if (!this.area.isInside(context) && !mainMenu.area.isInside(context))
                {
                    this.removeFromParent();
                    mainMenu.removeFromParent();
                }
                return super.subMouseClicked(context);
            }
        };

        manager.custom(submenu);

        manager.action(film.isWindowPanelVisible("unifiedEditArea") ? Icons.CHECKMARK : Icons.NONE, UIKeys.FILM_REPLAY_SECTION_GENERAL, () ->
        {
            film.setWindowPanelVisible("unifiedEditArea", !film.isWindowPanelVisible("unifiedEditArea"));
            this.openWindowPropertiesSubmenuCascading(film, mainMenu, itemY);
        });
        manager.action(film.isWindowPanelVisible("editArea") ? Icons.CHECKMARK : Icons.NONE, UIKeys.FILM_WORKSPACE_REPLAY, () ->
        {
            film.setWindowPanelVisible("editArea", !film.isWindowPanelVisible("editArea"));
            this.openWindowPropertiesSubmenuCascading(film, mainMenu, itemY);
        });
        manager.action(film.isWindowPanelVisible("cameraEditArea") ? Icons.CHECKMARK : Icons.NONE, UIKeys.FILM_WORKSPACE_CAMERA, () ->
        {
            film.setWindowPanelVisible("cameraEditArea", !film.isWindowPanelVisible("cameraEditArea"));
            this.openWindowPropertiesSubmenuCascading(film, mainMenu, itemY);
        });
        manager.action(film.isWindowPanelVisible("actionEditArea") ? Icons.CHECKMARK : Icons.NONE, UIKeys.FILM_WORKSPACE_ACTION, () ->
        {
            film.setWindowPanelVisible("actionEditArea", !film.isWindowPanelVisible("actionEditArea"));
            this.openWindowPropertiesSubmenuCascading(film, mainMenu, itemY);
        });

        manager.create();
        submenu.setMouse(this.getContext());
        submenu.resize();

        mainMenu.submenu = submenu;
        this.getContext().menu.overlay.add(submenu);
    }

    private ContextAction createHoverSubmenuAction(Icon icon, IKey label, UICascadingMenu mainMenu, Consumer<Integer> onHover)
    {
        return new ContextAction(icon, label, () -> {})
        {
            private boolean wasHovered = false;

            @Override
            public void render(UIContext context, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected)
            {
                super.render(context, font, x, y, w, h, hover, selected);

                if (hover && !this.wasHovered)
                {
                    this.wasHovered = true;
                    onHover.accept(y);
                }
                else if (!hover)
                {
                    this.wasHovered = false;
                }
            }
        };
    }

    private ContextAction createNormalWindowAction(Icon icon, IKey label, UICascadingMenu mainMenu, Runnable onClick)
    {
        return new ContextAction(icon, label, onClick)
        {
            @Override
            public void render(UIContext context, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected)
            {
                super.render(context, font, x, y, w, h, hover, selected);

                if (hover && mainMenu.submenu != null)
                {
                    mainMenu.submenu.removeFromParent();
                    mainMenu.submenu = null;
                }
            }
        };
    }

    public static class UICascadingMenu extends UISimpleContextMenu
    {
        public UIContextMenu submenu;

        @Override
        public void removeFromParent()
        {
            super.removeFromParent();
            if (this.submenu != null)
            {
                this.submenu.removeFromParent();
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Submenu actions                                                       */
    /* ------------------------------------------------------------------ */

    private void openNewSubmenu()
    {
        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.FILM, UIKeys.FILM_TITLE, () -> this.createNewAsset(ContentType.FILMS));
            menu.action(Icons.PARTICLE, UIKeys.PANELS_PARTICLES, () -> this.createNewAsset(ContentType.PARTICLES));
            menu.action(Icons.PLAYER, UIKeys.MODELS_TITLE, () -> this.createNewAsset(ContentType.MODELS));
        });
    }

    private void openRecentSubmenu()
    {
        this.getContext().replaceContextMenu((menu) ->
        {
            if (RecentAssetsTracker.RECENT.isEmpty())
            {
                menu.action(Icons.NONE, UIKeys.RAW_NO_RECENT_ASSETS, () -> {});
                return;
            }

            for (RecentAssetsTracker.Entry entry : RecentAssetsTracker.RECENT)
            {
                if (RecentAssetsTracker.shouldExcludeFromRecent(entry.type, entry.id))
                {
                    continue;
                }

                menu.action(this.iconFor(entry.type), IKey.raw(entry.id), () ->
                {
                    UIDataDashboardPanel panel = entry.type != null ? entry.type.get(this.dashboard) : null;

                    if (panel != null)
                    {
                        this.dashboard.setPanel(panel);
                        panel.pickData(entry.id);
                    }
                });
            }
        });
    }

    private void createNewAsset(ContentType type)
    {
        UICreateAssetOverlayPanel panel = new UICreateAssetOverlayPanel(
            type,
            (name) ->
            {
                IRepository repository = type.getRepository();
                ValueGroup created = (ValueGroup) repository.create(name);

                if (created != null)
                {
                    repository.save(name, created.toData().asMap());
                }

                UIDataDashboardPanel dashboardPanel = type.get(this.dashboard);

                if (dashboardPanel != null)
                {
                    this.dashboard.setPanel(dashboardPanel);
                    dashboardPanel.pickData(name);
                }
            }
        );
        UIOverlay.addOverlay(this.getContext(), panel, 260, 160);
    }

    private void openOpenPopup()
    {
        UIOverlay.addOverlay(this.getContext(), new UIOpenAssetOverlayPanel(UIKeys.RAW_OPEN_ASSET, this.dashboard), 520, 320);
    }

    private void triggerKey(KeyCombo combo)
    {
        if (this.dashboard.panels.panel == null)
        {
            return;
        }

        for (Keybind keybind : this.dashboard.panels.panel.keys().keybinds)
        {
            if (combo.equals(keybind.getCombo()) && keybind.isActive())
            {
                keybind.callback.run();
                return;
            }
        }
    }

    private Icon iconFor(ContentType type)
    {
        if (type == ContentType.FILMS) return Icons.FILM;
        if (type == ContentType.PARTICLES) return Icons.PARTICLE;
        if (type == ContentType.MODELS) return Icons.PLAYER;
        if (type == ContentType.SOUNDS) return Icons.SOUND;

        return Icons.NONE;
    }

    /* ------------------------------------------------------------------ */
    /* Menu button                                                           */
    /* ------------------------------------------------------------------ */

    public static class UIWorldMenuButton extends UIButton
    {
        final UIMainMenuBar bar;
        private boolean prevHover = false;
        boolean wasActiveLastFrame = false;

        public UIWorldMenuButton(IKey label, UIMainMenuBar bar)
        {
            super(label, null);

            this.bar = bar;
            this.callback = (b) -> this.bar.toggleWorldMenu(this);
            this.setSizeFromLabel(label);
        }

        private void setSizeFromLabel(IKey label)
        {
            try
            {
                int textWidth = RtlAwtTextRenderer.isReady() && RtlTextEngine.isActive()
                    ? RtlAwtTextRenderer.getWidth(label.get())
                    : MinecraftClient.getInstance().textRenderer.getWidth(label.get());
                this.w(textWidth + 10);
            }
            catch (Exception e)
            {
                this.w(28);
            }
        }

        @Override
        public void resize()
        {
            this.setSizeFromLabel(this.label);
            super.resize();
        }

        @Override
        public void render(UIContext context)
        {
            /* Freeze while pressed: context menu closes on mouse-down (outside click) before
             * release fires the toggle — updating here would clear wasActive and reopen. */
            if (!this.pressed)
            {
                this.wasActiveLastFrame = this.bar.activeWorldButton == this;
            }

            boolean nowHovered = this.area.isInside(context);

            if (nowHovered && !this.prevHover && this.bar.hasAnyMenuOpen() && this.bar.activeWorldButton != this)
            {
                this.bar.openWorldDropdown(this);
            }

            this.prevHover = nowHovered;

            super.render(context);
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            boolean active = this.bar.activeWorldButton == this;
            boolean hovered = this.area.isInside(context);

            if (active)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(),
                    Colors.setA(BBSSettings.accentRgb(), 0.55F));
            }
            else if (hovered)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A25);
            }

            int x = this.area.mx(context.batcher.getFont().getWidth(this.label.get()));
            int y = this.area.my(context.batcher.getFont().getHeight());

            context.batcher.textShadow(this.label.get(), x, y, Colors.WHITE);
        }
    }

    public static class UIMenuButton extends UIButton
    {
        final UIMainMenuBar bar;
        final Consumer<ContextMenuManager> menuConsumer;
        private boolean prevHover = false;

        /* Captured during render (before events fire) — used by toggleMenu to
           determine whether this button's menu was open when the click started. */
        boolean wasActiveLastFrame = false;

        public UIMenuButton(IKey label, UIMainMenuBar bar, Consumer<ContextMenuManager> menuConsumer)
        {
            super(label, null);

            this.bar = bar;
            this.menuConsumer = menuConsumer;
            this.callback = (b) -> this.bar.toggleMenu(this, this.menuConsumer);

            try
            {
                int textWidth = RtlAwtTextRenderer.isReady() && RtlTextEngine.isActive()
                    ? RtlAwtTextRenderer.getWidth(label.get())
                    : MinecraftClient.getInstance().textRenderer.getWidth(label.get());
                this.w(textWidth + 10);
            }
            catch (Exception e)
            {
                this.w(28);
            }
        }

        @Override
        public void resize()
        {
            try
            {
                int textWidth = RtlAwtTextRenderer.isReady() && RtlTextEngine.isActive()
                    ? RtlAwtTextRenderer.getWidth(this.label.get())
                    : MinecraftClient.getInstance().textRenderer.getWidth(this.label.get());
                this.w(textWidth + 10);
            }
            catch (Exception e)
            {
                this.w(28);
            }
            super.resize();
        }

        @Override
        public void render(UIContext context)
        {
            /* Freeze while pressed — same as World: menu closes on mouse-down before release. */
            if (!this.pressed)
            {
                this.wasActiveLastFrame = this.bar.activeButton == this;
            }

            boolean nowHovered = this.area.isInside(context);

            /* Switch menus on hover when another menu is already open */
            if (nowHovered && !this.prevHover
                && this.bar.hasAnyMenuOpen()
                && this.bar.activeButton != this)
            {
                this.bar.openMenuBelow(this, this.menuConsumer);
            }

            this.prevHover = nowHovered;

            super.render(context);
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            boolean active = this.bar.activeButton == this;
            boolean hovered = this.area.isInside(context);

            if (active)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(),
                    Colors.setA(BBSSettings.accentRgb(), 0.55F));
            }
            else if (hovered)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.A25);
            }

            int x = this.area.mx(context.batcher.getFont().getWidth(this.label.get()));
            int y = this.area.my(context.batcher.getFont().getHeight());

            context.batcher.textShadow(this.label.get(), x, y, Colors.WHITE);
        }
    }
}
