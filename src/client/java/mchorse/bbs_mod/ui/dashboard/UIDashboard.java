package mchorse.bbs_mod.ui.dashboard;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.OrbitCamera;
import mchorse.bbs_mod.camera.controller.OrbitCameraController;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.video.VideoFormEngine;
import mchorse.bbs_mod.client.video.VideoFormPlayback;
import mchorse.bbs_mod.client.video.VideoRenderer;
import mchorse.bbs_mod.discord.DiscordPresenceManager;
import mchorse.bbs_mod.events.register.RegisterDashboardPanelsEvent;
import mchorse.bbs_mod.events.register.RegisterDockLayoutEvent;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.ui.UISettingsOverlayPanel;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.addons.UIAddonsOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.panels.IFlightSupported;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.dashboard.textures.UITextureManagerPanel;
import mchorse.bbs_mod.ui.dashboard.utils.UIGraphPanel;
import mchorse.bbs_mod.ui.dashboard.utils.UIOrbitCamera;
import mchorse.bbs_mod.ui.dashboard.utils.UIOrbitCameraKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIWorldFilmsBrowserPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIRenderingContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.home.UIDocumentTabsBar;
import mchorse.bbs_mod.ui.home.UIHomePanel;
import mchorse.bbs_mod.ui.model.UIModelPanel;
import mchorse.bbs_mod.ui.model_blocks.UIModelBlockPanel;
import mchorse.bbs_mod.ui.morphing.UIMorphingPanel;
import mchorse.bbs_mod.ui.news.UINewsPanel;
import mchorse.bbs_mod.ui.particles.UIParticleSchemePanel;
import mchorse.bbs_mod.ui.triggers.TriggerKeys;
import mchorse.bbs_mod.ui.triggers.UITriggerBlockPanel;
import mchorse.bbs_mod.ui.utility.UIUtilityOverlayPanel;
import mchorse.bbs_mod.ui.utility.audio.UIAudioEditorPanel;
import mchorse.bbs_mod.ui.utils.UIChalkboard;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import com.mojang.blaze3d.systems.RenderSystem;

import java.util.ArrayList;
import java.util.List;

public class UIDashboard extends UIBaseMenu
{
    public UIDashboardPanels panels;

    /* Camera data */
    public final UIOrbitCamera orbitUI = new UIOrbitCamera();
    public final UIOrbitCameraKeys orbitKeysUI = new UIOrbitCameraKeys(this);
    public final OrbitCamera orbit = this.orbitUI.orbit;
    public final OrbitCameraController camera = new OrbitCameraController(this.orbit, 5);

    public UISettingsOverlayPanel settingsPanel;
    public UIAddonsOverlayPanel addonsPanel;
    private Perspective lastPerspective = Perspective.FIRST_PERSON;

    private UIChalkboard chalkboard;

    public UIMainMenuBar menuBar;
    public UIDocumentTabsBar documentTabsBar;

    public UIDashboard()
    {
        super();

        this.orbitUI.setControl(true);

        this.menuBar = new UIMainMenuBar(this);
        this.menuBar.relative(this.main).w(1F);

        /* Setup panels */
        this.documentTabsBar = new UIDocumentTabsBar(this);
        this.documentTabsBar.relative(this.main).y(20).w(1F).h(UIDocumentTabsBar.HEIGHT);

        this.panels = new UIDashboardPanels();
        this.panels.getEvents().register(UIDashboardPanels.PanelEvent.class, (e) ->
        {
            this.orbitUI.setControl(this.panels.isFlightSupported());

            if (this.panels.panel instanceof IFlightSupported panel)
            {
                this.orbit.setFovRoll(panel.supportsRollFOVControl());

                if (BBSSettings.editorOrbitRestrictToViewport.get())
                {
                    this.orbitUI.setViewportArea(panel::getFlightViewportArea);
                }
                else
                {
                    this.orbitUI.setViewportArea(null);
                }
            }
            else
            {
                this.orbitUI.setViewportArea(null);
            }

            this.copyCurrentEntityCamera();
            this.updateTabsBarVisibility(e.panel);
            this.menuBar.updateForPanel(e.panel);
            this.panels.updateTaskBarForPanel(e.panel);
            this.documentTabsBar.layoutFilmStatusIcons();
            DiscordPresenceManager.INSTANCE.updateFromMenu(this);
        });
        this.panels.relative(this.main).y(20 + UIDocumentTabsBar.HEIGHT).w(1F).h(1F, -(20 + UIDocumentTabsBar.HEIGHT));
        this.registerPanels();
        this.panels.registerWorldFilmsButton(this);

        BBSMod.events.post(new RegisterDashboardPanelsEvent(this));

        this.main.add(this.panels, this.documentTabsBar, this.menuBar);

        this.settingsPanel = new UISettingsOverlayPanel();
        this.addonsPanel = new UIAddonsOverlayPanel();

        this.chalkboard = new UIChalkboard();
        this.chalkboard.full(this.getRoot());
        this.getRoot().prepend(this.orbitUI);
        this.getRoot().add(this.orbitKeysUI);
        this.getRoot().add(this.chalkboard);

        if (!BBSSettings.welcomePanelSeen21.get())
        {
            UIWelcomePanel welcome = new UIWelcomePanel();
            welcome.full(this.getRoot());
            this.getRoot().add(welcome);
        }

        /* Register keys */
        IKey category = UIKeys.DASHBOARD_CATEGORY;

        this.main.keys().register(Keys.CYCLE_PANELS, this::cyclePanels).allowShift().category(category);
        this.overlay.keys().register(Keys.TOGGLE_VISIBILITY, () ->
        {
            if (this.panels.panel.canToggleVisibility())
            {
                this.main.toggleVisible();
            }
        }).category(category);
        this.overlay.keys().register(Keys.OPEN_UTILITY_PANEL, () ->
        {
            if (UIOverlay.has(this.context))
            {
                return;
            }

            UIOverlay.addOverlay(this.context, new UIUtilityOverlayPanel(UIKeys.UTILITY_TITLE, null), 320, 280);
        });
    }

    private void showAnnoyingPopups()
    {
        if (BBSRendering.isOptifinePresent())
        {
            UIOverlay.addOverlay(this.context, new UIMessageOverlayPanel(
                UIKeys.DASHBOARD_OPTIFINE_EW_TITLE,
                UIKeys.DASHBOARD_OPTIFINE_EW_DESCRIPTION
            ));
        }
    }

    public void copyCurrentEntityCamera()
    {
        Entity cameraEntity = MinecraftClient.getInstance().getCameraEntity();

        if (cameraEntity == null)
        {
            return;
        }

        Vec3d eyePos = cameraEntity.getEyePos();
        Camera camera = new Camera();

        camera.position.set(eyePos.getX(), eyePos.getY(), eyePos.getZ());
        camera.rotation.set(MathUtils.toRad(cameraEntity.getPitch()), MathUtils.toRad(cameraEntity.getHeadYaw() - 180), 0);
        camera.fov = MathUtils.toRad(MinecraftClient.getInstance().options.getFov().getValue().floatValue());

        this.orbit.setup(camera);
        this.camera.setup(BBSModClient.getCameraController().camera, 0F);
    }

    private void cyclePanels()
    {
        if (this.isDocumentPanel(this.panels.panel))
        {
            int direction = Window.isShiftPressed() ? -1 : 1;
            this.documentTabsBar.cycle(direction);
            UIUtils.playClick();
            return;
        }

        List<UIDashboardPanel> panels = this.panels.getVisiblePanels();

        if (panels.isEmpty())
        {
            return;
        }

        int direction = Window.isShiftPressed() ? -1 : 1;
        int index = panels.indexOf(this.panels.panel);
        int newIndex = MathUtils.cycler(index + direction, panels);

        this.setPanel(panels.get(newIndex));
        UIUtils.playClick();
    }

    public UIDashboardPanels getPanels()
    {
        return this.panels;
    }

    @Override
    protected boolean handleControlCaptureMouse(int button, boolean pressed)
    {
        if (!(this.panels.panel instanceof UIFilmPanel filmPanel))
        {
            return false;
        }

        return pressed
            ? filmPanel.getController().handleControlMousePress(button)
            : filmPanel.getController().handleControlMouseRelease(button);
    }

    @Override
    protected boolean shouldParkMouseWhileControlling()
    {
        return this.panels.panel instanceof UIFilmPanel filmPanel
            && filmPanel.getController().shouldParkUiMouse();
    }

    @Override
    public boolean canPause()
    {
        if (UIWorldDropdownMenu.isOpen() || UIWorldPropertiesOverlayPanel.isOpen())
        {
            return false;
        }

        return this.panels.panel != null && this.panels.panel.canPause();
    }

    @Override
    public boolean canRefresh()
    {
        return this.panels.panel != null && this.panels.panel.canRefresh();
    }

    @Override
    public void onOpen(UIBaseMenu oldMenu)
    {
        super.onOpen(oldMenu);

        this.lastPerspective = MinecraftClient.getInstance().options.getPerspective();

        MinecraftClient.getInstance().options.setPerspective(Perspective.FIRST_PERSON);

        if (oldMenu != this)
        {
            this.panels.open();
            this.setPanel(this.panels.panel);
        }

        BBSModClient.getCameraController().add(this.camera);

        this.showAnnoyingPopups();
        UIHomePanel.onDashboardOpened(this);
        UINewsPanel.onDashboardOpened(this);
        RegisterDockLayoutEvent.postDashboardOpen(this);
    }

    @Override
    public void onClose(UIBaseMenu nextMenu)
    {
        RegisterDockLayoutEvent.postDashboardClose(this);
        super.onClose(nextMenu);

        if (nextMenu != this)
        {
            /* Any leave path (Escape, replaced screen, etc.) must restore gamemode. */
            EditorSpectatorHelper.restore();
            this.panels.close();
        }

        this.orbit.reset();
        BBSModClient.getCameraController().remove(this.camera);

        /* Escape/close often skips panel.disappear() — free VLC/ffmpeg caches here too. */
        VideoRenderer.releaseAllPlayers();
        VideoFormEngine.releaseAll();
        VideoFormPlayback.releaseAll();

        MinecraftClient.getInstance().options.setPerspective(this.lastPerspective);
    }

    @Override
    protected void closeMenu()
    {
        EditorSpectatorHelper.restore();
        super.closeMenu();

        if (!this.main.isVisible())
        {
            this.main.setVisible(true);
        }
    }

    private boolean isDocumentPanel(UIDashboardPanel panel)
    {
        return panel instanceof UIHomePanel
            || panel instanceof UIFilmPanel
            || panel instanceof UIModelPanel
            || panel instanceof UIParticleSchemePanel
            || panel instanceof UIAudioEditorPanel
            || panel instanceof UIGraphPanel;
    }

    private void updateTabsBarVisibility(UIDashboardPanel panel)
    {
        boolean show = this.isDocumentPanel(panel);

        this.documentTabsBar.setVisible(show);

        int tabsH = show ? UIDocumentTabsBar.HEIGHT : 0;

        this.panels.getFlex().y.offset = 20 + tabsH;
        this.panels.getFlex().h.offset = -(20 + tabsH);

        if (this.main.hasParent())
        {
            this.main.resize();
        }
    }

    protected void registerPanels()
    {
        this.panels.registerPinnedPanel(new UIHomePanel(this), UIKeys.RAW_HOME, Icons.SERVER);
        this.panels.registerPanel(new UIMorphingPanel(this), UIKeys.MORPHING_TITLE, Icons.MORPH);
        this.panels.registerPanel(new UIModelBlockPanel(this), UIKeys.MODEL_BLOCKS_TITLE, Icons.BLOCK);
        this.panels.registerPanel(new UITriggerBlockPanel(this), TriggerKeys.TITLE, Icons.TRIGGER);
        this.panels.registerPanel(new UITextureManagerPanel(this), UIKeys.TEXTURES_TOOLTIP, Icons.MATERIAL).marginLeft(10);
        UINewsPanel newsPanel = new UINewsPanel(this);
        UIIcon newsButton = this.panels.registerPanel(newsPanel, UIKeys.NEWS_TITLE, Icons.NEWS);
        newsButton.marginLeft(10);
        UINewsPanel.attachIcon(newsButton);

        /* Editor panels — reachable only through the unified document tab bar, not via dashboard buttons */
        this.panels.registerHiddenPanel(new UIWorldFilmsBrowserPanel(this));
        this.panels.registerHiddenPanel(new UIFilmPanel(this));
        this.panels.registerHiddenPanel(new UIModelPanel(this));
        this.panels.registerHiddenPanel(new UIParticleSchemePanel(this));
        this.panels.registerHiddenPanel(new UIAudioEditorPanel(this));
        this.panels.registerHiddenPanel(new UIGraphPanel(this));

        this.panels.registerPanel(new UIDebugPanel(this), UIKeys.RAW_SANDBOX, Icons.CODE);

        this.setPanel(this.getPanel(UIHomePanel.class));
    }

    @Override
    public boolean canHideHUD()
    {
        return this.panels.panel == null || this.panels.panel.canHideHUD();
    }

    @Override
    public boolean needsWorldRender()
    {
        return this.panels.panel != null && this.panels.panel.needsWorldRender();
    }

    public <T> T getPanel(Class<T> clazz)
    {
        return this.panels.getPanel(clazz);
    }

    public void setPanel(UIDashboardPanel panel)
    {
        for (IUIElement element : new ArrayList<>(this.overlay.getChildren()))
        {
            if (element instanceof UIOverlay)
            {
                ((UIOverlay) element).closeItself();
            }
        }

        this.panels.setPanel(panel);
    }

    @Override
    public void update()
    {
        super.update();

        if (this.panels.panel != null)
        {
            this.panels.panel.update();
        }

        if (this.main.isVisible())
        {
            UINewsPanel.tickAuto(this);
            UINewsPanel.tickPriorityAnnouncement(this);
        }
    }

    @Override
    protected void preRenderMenu(UIRenderingContext context)
    {
        if (!this.main.isVisible())
        {
            if (this.panels.panel != null)
            {
                this.panels.panel.renderPanelBackground(this.context);
            }

            return;
        }

        if (this.panels.panel != null && (this.panels.panel.needsBackground() || !this.panels.panel.needsWorldRender()))
        {
            this.background(context);
        }
        else
        {
            context.batcher.gradientVBox(0, 0, this.width, this.height / 8, Colors.A25, 0);
            context.batcher.gradientVBox(0, this.height - this.height / 8, this.width, this.height, 0, Colors.A25);
        }
    }

    private void background(UIRenderingContext context)
    {
        Link background = BBSSettings.backgroundImage.get();
        int color = BBSSettings.backgroundColor.get();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (background == null)
        {
            context.batcher.box(0, 0, this.width, this.height, color);
        }
        else
        {
            context.batcher.texturedBox(context.getTextures().getTexture(background), color, 0, 0, this.width, this.height, 0, 0, this.width, this.height, this.width, this.height);
        }
    }

    @Override
    public void startRenderFrame(float tickDelta)
    {
        super.startRenderFrame(tickDelta);

        if (this.panels.panel != null)
        {
            this.panels.panel.startRenderFrame(tickDelta);
        }
    }

    public void renderInWorld(WorldRenderContext context)
    {
        super.renderInWorld(context);

        if (this.panels.panel != null)
        {
            this.panels.panel.renderInWorld(context);
        }
    }
}
