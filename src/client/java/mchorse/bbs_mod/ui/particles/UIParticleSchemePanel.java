package mchorse.bbs_mod.ui.particles;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.renderers.ParticleFormRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.molang.expressions.MolangExpression;
import mchorse.bbs_mod.particles.ParticleScheme;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.URLSourcePack;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.UIPanelSwitcher;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextEditor;
import mchorse.bbs_mod.ui.framework.elements.layout.ILayoutSource;
import mchorse.bbs_mod.ui.framework.elements.layout.UIDockLayout;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.home.UIHomePanel;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeAppearanceSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeCollisionSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeCurvesSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeExpirationSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeGeneralSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeInitializationSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeLifetimeSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeLightingSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeMotionSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeRateSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeRotationSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeShapeSection;
import mchorse.bbs_mod.ui.particles.sections.UIParticleSchemeSpaceSection;
import mchorse.bbs_mod.ui.particles.utils.MolangSyntaxHighlighter;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.RecentAssetsTracker;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.presets.PresetManager;
import mchorse.bbs_mod.utils.resources.Pixels;

import net.minecraft.client.MinecraftClient;

import com.mojang.blaze3d.systems.RenderSystem;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class UIParticleSchemePanel extends UIDataDashboardPanel<ParticleScheme>
{
    /**
     * Default particle placeholder that comes with the engine.
     */
    public static final Link PARTICLE_PLACEHOLDER = Link.assets("particles/default_placeholder.json");

    public UITextEditor textEditor;
    public UIParticleSchemeRenderer renderer;

    public UIScrollView generalView;
    public UIScrollView emitterView;
    public UIScrollView particleView;
    public UIScrollView appearanceView;

    public UIDockLayout dock;

    public List<UIParticleSchemeSection> sections = new ArrayList<>();

    private UICopyPasteController layoutPresetsController;
    private String molangId;

    // Tab and Home fields

    private int activeParticleDocumentTab = -1;
    private final List<ParticleDocumentTab> particleDocumentTabs = new ArrayList<>();

    private UIElement mainView;

    private UIElement homePage;
    private UISearchList<DataPath> homeParticlesSearch;
    private UIDataPathList homeParticlesList;
    private UIPanelSwitcher panelSwitcher;
    private UIElement homeActionsPanel;
    private UIButton homeCreateParticle;
    private UIButton homeDuplicateCurrent;
    private UIButton homeRenameCurrent;
    private UIButton homeDeleteCurrent;
    private String homeLastClickedParticleId;
    private long homeLastClickTime;
    private boolean showingHomePage = true;

    private UIParticleMosaicGrid homeParticlesMosaic;
    private UIIcon homeViewToggle;
    private UIParticleStatusIcons statusIcons;

    public static class ParticleDocumentTab
    {
        public boolean home;
        public String particleId;

        public ParticleDocumentTab(boolean home, String particleId)
        {
            this.home = home;
            this.particleId = particleId;
        }
    }

    public UIParticleSchemePanel(UIDashboard dashboard)
    {
        super(dashboard);
        this.overlay.resizable().minSize(260, 220);

        this.statusIcons = new UIParticleStatusIcons(this);
        if (this.dashboard != null && this.dashboard.documentTabsBar != null)
        {
            this.dashboard.documentTabsBar.attachParticleStatusIcons(this.statusIcons);
        }

        this.iconBar.setVisible(false);
        this.editor.relative(this).w(1F).h(1F);

        this.mainView = new UIElement();
        this.mainView.relative(this.editor).y(0).w(1F).h(1F);

        this.renderer = new UIParticleSchemeRenderer();

        this.textEditor = new UITextEditor(null).highlighter(new MolangSyntaxHighlighter());
        this.textEditor.background();

        this.generalView = this.createSectionView();
        this.emitterView = this.createSectionView();
        this.particleView = this.createSectionView();
        this.appearanceView = this.createSectionView();

        /* Dockable layout: section groups (tabbed), MoLang and 3D preview each their own panel,
         * sharing the docking system with the film editor. */
        this.dock = new UIDockLayout();
        this.dock.relative(this.mainView).w(1F).h(1F);
        this.dock.source(this.createLayoutSource())
            .frameless("preview")
            .gate(() -> this.data != null);
        this.dock.addPanel("general", this.wrapScroll(this.generalView), Icons.GEAR);
        this.dock.addPanel("emitter", this.wrapScroll(this.emitterView), Icons.BUBBLE);
        this.dock.addPanel("particle", this.wrapScroll(this.particleView), Icons.PARTICLE);
        this.dock.addPanel("appearance", this.wrapScroll(this.appearanceView), Icons.MATERIAL);
        this.dock.addPanel("molang", this.textEditor, Icons.CODE);
        this.dock.addPanel("preview", this.renderer, Icons.VIDEO_CAMERA);
        this.dock.mount();
        this.mainView.add(this.dock);

        UIIcon close = new UIIcon(Icons.CLOSE, (b) -> this.editMoLang(null, null, null));
        close.relative(this.textEditor).x(1F, -20);
        this.textEditor.add(close);
        this.overlay.namesList.setFileIcon(Icons.PARTICLE);

        this.layoutPresetsController = new UICopyPasteController(PresetManager.PARTICLE_LAYOUTS, "_CopyParticleLayout")
            .supplier(this::getLayoutPresetData)
            .consumer(this::applyLayoutFromPreset);

        /* General tab */
        this.addSection(this.generalView, new UIParticleSchemeGeneralSection(this));
        this.addSection(this.generalView, new UIParticleSchemeCurvesSection(this));
        this.addSection(this.generalView, new UIParticleSchemeSpaceSection(this));
        this.addSection(this.generalView, new UIParticleSchemeInitializationSection(this));
        /* Emitter tab */
        this.addSection(this.emitterView, new UIParticleSchemeRateSection(this));
        this.addSection(this.emitterView, new UIParticleSchemeLifetimeSection(this));
        this.addSection(this.emitterView, new UIParticleSchemeShapeSection(this));
        /* Particle tab */
        UIParticleSchemeMotionSection motionSection = new UIParticleSchemeMotionSection(this);
        UIParticleSchemeRotationSection rotationSection = new UIParticleSchemeRotationSection(this);

        motionSection.link(rotationSection);
        rotationSection.link(motionSection);

        this.addSection(this.particleView, motionSection);
        this.addSection(this.particleView, rotationSection);
        this.addSection(this.particleView, new UIParticleSchemeExpirationSection(this));
        /* Appearance tab */
        this.addSection(this.appearanceView, new UIParticleSchemeAppearanceSection(this));
        this.addSection(this.appearanceView, new UIParticleSchemeLightingSection(this));
        this.addSection(this.appearanceView, new UIParticleSchemeCollisionSection(this));

        // Home dashboard layout
        this.homePage = new UIElement()
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                UIParticleSchemePanel.this.homeParticlesList.deselect();
                UIParticleSchemePanel.this.handleHomeParticlesSelection(null);

                return super.subMouseClicked(context);
            }
        };

        this.homeActionsPanel = new UIElement();
        this.homeParticlesList = new UIDataPathList((list) -> this.handleHomeParticlesSelection(list));
        this.homeParticlesList.setFileIcon(Icons.PARTICLE);
        this.homeParticlesList.context((menu) ->
        {
            menu.action(Icons.FOLDER, UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE, this::addFolderFromHome);

            String selectedId = this.getSelectedHomeParticleId();

            if (selectedId != null)
            {
                menu.action(Icons.COPY, UIKeys.PANELS_CONTEXT_COPY, this::copyHomeParticle);
            }

            try
            {
                MapType clipboardData = Window.getClipboardMap("_ContentType_" + this.getType().getId());

                if (clipboardData != null)
                {
                    menu.action(Icons.PASTE, UIKeys.PANELS_CONTEXT_PASTE, () -> this.pasteHomeParticle(clipboardData));
                }
            }
            catch (Exception e)
            {}

            File folder = this.getType().getRepository().getFolder();

            if (folder != null)
            {
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, () ->
                    UIUtils.openFolder(new File(folder, this.homeParticlesList.getPath().toString()))
                );
            }
        });

        this.homeParticlesList.moveCallback = (from, to) ->
        {
            String fromStr = from.toString();
            String toStr = to.toString();

            this.getType().getRepository().rename(fromStr, toStr);

            for (ParticleDocumentTab tab : this.particleDocumentTabs)
            {
                if (!tab.home && fromStr.equals(tab.particleId))
                {
                    tab.particleId = toStr;
                }
            }

            if (this.data != null && fromStr.equals(this.data.getId()))
            {
                this.data.setId(toStr);
            }

            this.rebuildParticleDocumentTabs();
            this.requestNames();
        };

        this.homeParticlesSearch = new UISearchList<>(this.homeParticlesList).label(UIKeys.GENERAL_SEARCH);
        this.homeParticlesSearch.list.background();

        this.homeParticlesMosaic = new UIParticleMosaicGrid((id) -> {
            DataPath path = this.homeParticlesMosaic.findPath(id);
            this.handleHomeParticlesSelection(Collections.singletonList(path != null ? path : new DataPath(id)));
        }, (id) -> {
            DataPath clickedPath = this.homeParticlesMosaic.findPath(id);
            if (clickedPath != null && clickedPath.folder) {
                if (clickedPath.getLast().equals("..")) {
                    this.homeParticlesList.goTo(this.homeParticlesList.getPath().getParent());
                } else {
                    this.homeParticlesList.goTo(clickedPath);
                }
                this.homeParticlesMosaic.filter("");
            } else {
                this.openParticleInDocumentTabs(id);
            }
        });

        boolean mosaic = BBSSettings.lastViewMosaic.get();

        this.homeParticlesMosaic.setVisible(mosaic);
        this.homeParticlesList.setVisible(!mosaic);

        Consumer<String> oldCallback = this.homeParticlesSearch.search.callback;
        this.homeParticlesSearch.search.callback = (str) -> {
            if (oldCallback != null) oldCallback.accept(str);
            this.homeParticlesMosaic.filter(str);
        };

        this.homeViewToggle = new UIIcon(mosaic ? Icons.LIST : Icons.GALLERY, (b) -> this.toggleMosaicView());
        this.homeViewToggle.tooltip(mosaic ? UIKeys.MODELS_HOME_VIEW_LIST : UIKeys.MODELS_HOME_VIEW_MOSAIC, Direction.LEFT);

        this.homeCreateParticle = this.createHomeButton(UIKeys.PARTICLES_CRUD_ADD, Icons.ADD, (b) ->
        {
            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.PARTICLES_CRUD_ADD,
                IKey.raw(""),
                (str) ->
                {
                    String targetId = this.homeParticlesList.getPath(str).toString();
                    if (!this.homeParticlesList.hasInHierarchy(targetId))
                    {
                        this.save();
                        this.homeParticlesList.addFile(targetId);
                        ParticleScheme data = (ParticleScheme) this.getType().getRepository().create(targetId);
                        this.fillDefaultData(data);
                        this.fill(data);
                        this.save();
                        this.requestNames();
                    }
                }
            );

            panel.text.filename();
            UIOverlay.addOverlay(this.getContext(), panel);
        });

        this.homeDuplicateCurrent = this.createHomeButton(UIKeys.PARTICLES_CRUD_DUPE, Icons.COPY, (b) ->
        {
            String selectedId = this.getSelectedHomeParticleId();

            if (selectedId == null)
            {
                return;
            }

            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.PARTICLES_CRUD_DUPE,
                IKey.raw(""),
                (str) ->
                {
                    String targetId = this.homeParticlesList.getPath(str).toString();
                    if (!this.homeParticlesList.hasInHierarchy(targetId))
                    {
                        this.save();

                        File folder = this.getType().getRepository().getFolder();
                        File source = new File(folder, selectedId);
                        File destination = new File(folder, targetId);

                        if (source.isDirectory())
                        {
                            try
                            {
                                IOUtils.copyFolder(source, destination);
                            }
                            catch (IOException e)
                            {
                                e.printStackTrace();
                            }
                        }

                        this.getType().getRepository().save(targetId, this.data.toData().asMap());
                        this.homeParticlesList.addFile(targetId, false);
                        this.requestNames();
                    }
                }
            );

            panel.text.filename();
            UIOverlay.addOverlay(this.getContext(), panel);
        });

        this.homeRenameCurrent = this.createHomeButton(UIKeys.PARTICLES_CRUD_RENAME, Icons.EDIT, (b) ->
        {
            String selectedId = this.getSelectedHomeParticleId();

            if (selectedId == null)
            {
                return;
            }

            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.PARTICLES_CRUD_RENAME,
                IKey.raw(""),
                (str) ->
                {
                    String targetId = this.homeParticlesList.getPath(str).toString();
                    if (!this.homeParticlesList.hasInHierarchy(targetId))
                    {
                        this.getType().getRepository().rename(selectedId, targetId);

                        for (ParticleDocumentTab tab : this.particleDocumentTabs)
                        {
                            if (!tab.home && selectedId.equals(tab.particleId))
                            {
                                tab.particleId = targetId;
                            }
                        }

                        if (this.data != null && selectedId.equals(this.data.getId()))
                        {
                            this.data.setId(targetId);
                        }

                        this.rebuildParticleDocumentTabs();
                        this.requestNames();
                    }
                }
            );

            panel.text.filename();
            UIOverlay.addOverlay(this.getContext(), panel);
        });

        this.homeDeleteCurrent = this.createHomeButton(UIKeys.PARTICLES_CRUD_REMOVE, Icons.REMOVE, (b) ->
        {
            String selectedId = this.getSelectedHomeParticleId();

            if (selectedId == null)
            {
                return;
            }

            UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
                UIKeys.PARTICLES_CRUD_REMOVE,
                UIKeys.PANELS_MODALS_REMOVE,
                (bool) ->
                {
                    if (bool)
                    {
                        this.getType().getRepository().delete(selectedId);
                        this.homeParticlesList.removeFile(selectedId);

                        for (int i = this.particleDocumentTabs.size() - 1; i >= 0; i--)
                        {
                            ParticleDocumentTab tab = this.particleDocumentTabs.get(i);
                            if (!tab.home && selectedId.equals(tab.particleId))
                            {
                                this.removeParticleDocumentTab(i);
                            }
                        }

                        this.requestNames();
                    }
                }
            );

            UIOverlay.addOverlay(this.getContext(), panel);
        });

        this.updateHomeButtonsState();

        this.homePage.relative(this.editor).x(0.5F, -250).y(0).w(500).h(1F);
        this.homeActionsPanel.relative(this.homePage).x(0).y(UIHomePanel.HOME_BANNER_HEIGHT + 20).w(0.35F).h(1F, -(UIHomePanel.HOME_BANNER_HEIGHT + 20 + 10)).column(0).vertical().stretch();
        
        this.panelSwitcher = new UIPanelSwitcher(this.dashboard);
        this.panelSwitcher.relative(this.homePage).x(0.5F, -87).y(1F, -32).w(175).h(24);

        UIElement spacing = new UIElement();
        spacing.h(8);

        this.homeActionsPanel.add(this.homeCreateParticle, spacing, this.homeDuplicateCurrent, this.homeRenameCurrent, this.homeDeleteCurrent);
        this.homeParticlesSearch.relative(this.homePage).x(0.35F).y(UIHomePanel.HOME_BANNER_HEIGHT + 20).w(0.65F).h(1F, -(UIHomePanel.HOME_BANNER_HEIGHT + 20 + 10));
        this.homeParticlesSearch.search.w(1F, -25);
        this.homeParticlesMosaic.relative(this.homeParticlesSearch).x(0).y(20).w(1F).h(1F, -20);
        this.homeViewToggle.relative(this.homeParticlesSearch).x(1F, -22).y(0).w(20).h(20);
        this.homePage.add(new UIRenderable(this::renderHomeBackground), this.homeActionsPanel, this.homeParticlesSearch, this.homeParticlesMosaic, this.homeViewToggle, this.panelSwitcher);

        this.editor.add(this.mainView, this.homePage);

        this.createHomeDocumentTab(true);
        this.fill(null);
        this.updateParticleDocumentView();
    }

    private void handleHomeParticlesSelection(List<DataPath> paths)
    {
        DataPath selected = paths == null || paths.isEmpty() ? null : paths.get(0);
        String selectedId = selected != null && !selected.folder ? selected.toString() : null;

        this.homeLastClickedParticleId = selectedId;
        this.updateHomeButtonsState();

        if (selectedId != null)
        {
            long now = System.currentTimeMillis();

            if (now - this.homeLastClickTime < 250)
            {
                this.openParticleInDocumentTabs(selectedId);
            }

            this.homeLastClickTime = now;
        }
    }

    private void updateHomeButtonsState()
    {
        String selectedId = this.getSelectedHomeParticleId();
        boolean hasSelected = selectedId != null;

        this.homeDuplicateCurrent.setEnabled(hasSelected);
        this.homeRenameCurrent.setEnabled(hasSelected);
        this.homeDeleteCurrent.setEnabled(hasSelected);
    }

    private void toggleMosaicView()
    {
        boolean isMosaic = !this.homeParticlesMosaic.isVisible();

        this.homeParticlesMosaic.setVisible(isMosaic);
        this.homeParticlesList.setVisible(!isMosaic);
        this.homeViewToggle.both(isMosaic ? Icons.LIST : Icons.GALLERY);
        this.homeViewToggle.tooltip(isMosaic ? UIKeys.MODELS_HOME_VIEW_LIST : UIKeys.MODELS_HOME_VIEW_MOSAIC, Direction.LEFT);

        BBSSettings.lastViewMosaic.set(isMosaic);

        if (isMosaic)
        {
            this.homeParticlesMosaic.filter("");
        }
    }

    private String getSelectedHomeParticleId()
    {
        String selected = null;
        if (this.homeParticlesMosaic != null && this.homeParticlesMosaic.isVisible())
        {
            selected = this.homeParticlesMosaic.selectedId;
        }
        else
        {
            DataPath path = this.homeParticlesList == null ? null : this.homeParticlesList.getCurrentFirst();
            selected = path == null ? null : path.toString();
        }

        if (selected == null)
        {
            return null;
        }

        /* Check if the selected ID corresponds to a folder */
        DataPath selectedPath = this.homeParticlesMosaic != null ? this.homeParticlesMosaic.findPath(selected) : null;
        if (selectedPath == null)
        {
            DataPath path = this.homeParticlesList != null ? this.homeParticlesList.getCurrentFirst() : null;
            selectedPath = path;
        }
        if (selectedPath != null && selectedPath.folder)
        {
            return null;
        }

        return selected;
    }

    private void addFolderFromHome()
    {
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE,
            IKey.raw(""),
            (str) ->
            {
                String targetId = this.homeParticlesList.getPath(str).toString();
                if (targetId.trim().isEmpty())
                {
                    this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
                    return;
                }
                this.getType().getRepository().addFolder(targetId, (bool) ->
                {
                    if (bool)
                    {
                        this.requestNames();
                    }
                });
            }
        );

        panel.text.filename();
        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void copyHomeParticle()
    {
        String selectedId = this.getSelectedHomeParticleId();
        if (selectedId != null && this.data != null && selectedId.equals(this.data.getId()))
        {
            Window.setInMemoryClipboard(this.data.toData().asMap(), "_ContentType_" + this.getType().getId());
        }
    }

    private void pasteHomeParticle(MapType clipboardData)
    {
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.PANELS_MODALS_ADD,
            IKey.raw(""),
            (str) ->
            {
                String targetId = this.homeParticlesList.getPath(str).toString();
                if (!this.homeParticlesList.hasInHierarchy(targetId))
                {
                    this.save();
                    this.homeParticlesList.addFile(targetId);
                    ParticleScheme data = (ParticleScheme) this.getType().getRepository().create(targetId, clipboardData);
                    this.fill(data);
                    this.save();
                    this.requestNames();
                }
            }
        );

        panel.text.filename();
        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void createHomeDocumentTab(boolean activate)
    {
        this.particleDocumentTabs.add(new ParticleDocumentTab(true, null));
        int index = this.particleDocumentTabs.size() - 1;

        this.rebuildParticleDocumentTabs();

        if (activate)
        {
            this.activateParticleDocumentTab(index, false);
        }
    }

    private void addHomeDocumentTab()
    {
        int insertAt = Math.max(0, this.activeParticleDocumentTab + 1);

        this.particleDocumentTabs.add(insertAt, new ParticleDocumentTab(true, null));
        this.rebuildParticleDocumentTabs();
        this.activateParticleDocumentTab(insertAt, false);
    }

    private int findTabByParticleId(String id)
    {
        for (int i = 0; i < this.particleDocumentTabs.size(); i++)
        {
            ParticleDocumentTab tab = this.particleDocumentTabs.get(i);

            if (!tab.home && id.equals(tab.particleId))
            {
                return i;
            }
        }

        return -1;
    }

    private void openParticleInDocumentTabs(String id)
    {
        if (id == null || id.trim().isEmpty())
        {
            return;
        }

        int existingIndex = this.findTabByParticleId(id);

        if (existingIndex >= 0)
        {
            this.activateParticleDocumentTab(existingIndex, true);
            return;
        }

        if (this.activeParticleDocumentTab < 0 || this.activeParticleDocumentTab >= this.particleDocumentTabs.size())
        {
            if (this.particleDocumentTabs.isEmpty())
            {
                this.particleDocumentTabs.add(new ParticleDocumentTab(true, null));
            }
            this.activeParticleDocumentTab = 0;
        }

        ParticleDocumentTab active = this.particleDocumentTabs.get(this.activeParticleDocumentTab);

        if (active.home)
        {
            active.home = false;
            active.particleId = id;
            this.rebuildParticleDocumentTabs();
            this.activateParticleDocumentTab(this.activeParticleDocumentTab, true);
        }
        else
        {
            int insertAt = this.activeParticleDocumentTab + 1;
            this.particleDocumentTabs.add(insertAt, new ParticleDocumentTab(false, id));
            this.rebuildParticleDocumentTabs();
            this.activateParticleDocumentTab(insertAt, true);
        }
    }

    private void activateParticleDocumentTab(int index, boolean loadParticle)
    {
        if (index < 0 || index >= this.particleDocumentTabs.size())
        {
            return;
        }

        if (this.data != null && this.activeParticleDocumentTab != index)
        {
            this.save();
        }

        this.activeParticleDocumentTab = index;

        ParticleDocumentTab tab = this.particleDocumentTabs.get(index);

        if (tab.home)
        {
            this.updateParticleDocumentView();
        }
        else
        {
            if (loadParticle || this.data == null || this.data.getId() == null || !this.data.getId().equals(tab.particleId))
            {
                this.requestData(tab.particleId);
            }
            else
            {
                this.updateParticleDocumentView();
            }
        }

        this.rebuildParticleDocumentTabs();
    }

    private void removeParticleDocumentTab(int index)
    {
        if (index < 0 || index >= this.particleDocumentTabs.size())
        {
            return;
        }

        this.particleDocumentTabs.remove(index);

        if (this.particleDocumentTabs.isEmpty())
        {
            this.particleDocumentTabs.add(new ParticleDocumentTab(true, null));
            this.activeParticleDocumentTab = 0;
            this.rebuildParticleDocumentTabs();
            this.activateParticleDocumentTab(0, false);
            return;
        }

        if (index < this.activeParticleDocumentTab)
        {
            this.activeParticleDocumentTab--;
        }
        else if (index == this.activeParticleDocumentTab)
        {
            this.activeParticleDocumentTab = Math.max(0, Math.min(this.activeParticleDocumentTab, this.particleDocumentTabs.size() - 1));
        }

        this.rebuildParticleDocumentTabs();
        this.activateParticleDocumentTab(this.activeParticleDocumentTab, false);
    }

    private void rebuildParticleDocumentTabs()
    {
        /* No-op: the legacy tab bar UI was removed; the unified UIDocumentTabsBar at the dashboard level replaces it. */
    }

    private void syncActiveDocumentTabWithData(ParticleScheme data)
    {
        if (data != null)
        {
            if (this.activeParticleDocumentTab < 0 || this.activeParticleDocumentTab >= this.particleDocumentTabs.size())
            {
                this.particleDocumentTabs.add(new ParticleDocumentTab(false, data.getId()));
                this.activeParticleDocumentTab = this.particleDocumentTabs.size() - 1;
            }
            else
            {
                ParticleDocumentTab tab = this.particleDocumentTabs.get(this.activeParticleDocumentTab);
                if (tab.home)
                {
                    tab.home = false;
                    tab.particleId = data.getId();
                }
                else if (!data.getId().equals(tab.particleId))
                {
                    int existing = this.findTabByParticleId(data.getId());
                    if (existing >= 0)
                    {
                        this.activeParticleDocumentTab = existing;
                    }
                    else
                    {
                        tab.particleId = data.getId();
                    }
                }
            }
        }

        this.rebuildParticleDocumentTabs();
        this.updateParticleDocumentView();
    }

    private void updateParticleDocumentView()
    {
        boolean home = this.activeParticleDocumentTab < 0
            || this.activeParticleDocumentTab >= this.particleDocumentTabs.size()
            || this.particleDocumentTabs.get(this.activeParticleDocumentTab).home
            || this.data == null;

        this.showingHomePage = home;
        this.homePage.setVisible(home);
        this.mainView.setVisible(!home);
        this.iconBar.setVisible(false);

        this.editor.resetFlex().relative(this).w(1F).h(1F);
        if (!home && this.dock != null)
        {
            this.dock.setupFlex(true);
        }
        if (this.dashboard != null && this.dashboard.documentTabsBar != null)
        {
            this.dashboard.documentTabsBar.layoutStatusIcons();
        }
        this.resize();

        this.updateHomeButtonsState();
    }

    private UIButton createHomeButton(IKey label, Icon icon, Consumer<UIButton> callback)
    {
        UIButton button = new UIButton(label, callback) {
            @Override
            protected void renderSkin(UIContext context)
            {
                int bg = this.hover ? Colors.setA(Colors.WHITE, 0.25F) : Colors.setA(0, 0.4F);
                this.area.render(context.batcher, bg);

                int color = this.isEnabled() ? Colors.LIGHTEST_GRAY : 0x88444444;

                if (icon != null) {
                    context.batcher.icon(icon, color, this.area.x + 4, this.area.y + this.area.h / 2 - icon.h / 2);
                }

                context.batcher.textShadow(this.label.get(), this.area.x + 22, this.area.y + this.area.h / 2 - 4, color);
            }
        };
        button.h(20);
        return button;
    }

    public void editMoLang(String id, Consumer<String> callback, MolangExpression expression)
    {
        /* The MoLang editor is its own dock panel (always present); editing just swaps its target. */
        this.molangId = id;
        this.textEditor.callback = callback;
        this.textEditor.setText(expression == null ? "" : expression.toString());
    }

    @Override
    protected IKey getTitle()
    {
        return UIKeys.SNOWSTORM_TITLE;
    }

    @Override
    public ContentType getType()
    {
        return ContentType.PARTICLES;
    }

    @Override
    public UIDashboardPanel getMainPanel()
    {
        UIHomePanel home = this.dashboard.getPanel(UIHomePanel.class);

        return home != null ? home : this;
    }

    public void dirty()
    {
        this.renderer.emitter.setupVariables();
    }

    @Override
    public boolean canPause()
    {
        return false;
    }

    public boolean isWindowPanelVisible(String panelId)
    {
        if (panelId == null || this.dock == null)
        {
            return false;
        }

        EditorLayoutNode root = this.dock.getLayoutRoot();

        return this.hasPanelInLayout(root, panelId);
    }

    public void setWindowPanelVisible(String panelId, boolean visible)
    {
        if (panelId == null || this.dock == null)
        {
            return;
        }

        EditorLayoutNode root = this.dock.getLayoutRoot();
        boolean currentlyInLayout = this.hasPanelInLayout(root, panelId);

        if (visible && !currentlyInLayout)
        {
            EditorLayoutNode newRoot = new EditorLayoutNode.SplitterNode(
                false,
                0.3F,
                new EditorLayoutNode.PanelNode(panelId),
                root
            );

            BBSSettings.editorLayoutSettings.setParticleLayoutRoot(newRoot);
            this.dock.refresh();
        }
        else if (!visible && currentlyInLayout)
        {
            EditorLayoutNode newRoot = EditorLayoutNode.copyWithRemovedLeaf(root, panelId);

            if (newRoot != null)
            {
                BBSSettings.editorLayoutSettings.setParticleLayoutRoot(newRoot);
                this.dock.refresh();
            }
        }
    }

    private boolean hasPanelInLayout(EditorLayoutNode node, String panelId)
    {
        if (node instanceof EditorLayoutNode.PanelNode)
        {
            return ((EditorLayoutNode.PanelNode) node).getPanelId().equals(panelId);
        }

        if (node instanceof EditorLayoutNode.SplitterNode)
        {
            EditorLayoutNode.SplitterNode splitter = (EditorLayoutNode.SplitterNode) node;

            return this.hasPanelInLayout(splitter.getFirst(), panelId) || this.hasPanelInLayout(splitter.getSecond(), panelId);
        }

        if (node instanceof EditorLayoutNode.TabbedNode)
        {
            EditorLayoutNode.TabbedNode tabbed = (EditorLayoutNode.TabbedNode) node;

            for (EditorLayoutNode tab : tabbed.tabs)
            {
                if (this.hasPanelInLayout(tab, panelId))
                {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isLayoutLocked()
    {
        return this.dock != null && this.dock.isLocked();
    }

    public void toggleLayoutLock()
    {
        if (this.dock != null)
        {
            this.dock.toggleLock();
        }
    }

    public void resetLayout()
    {
        if (this.dock != null)
        {
            this.dock.resetLayout();
        }
    }

    public void openLayoutPresets(int x, int y)
    {
        if (this.layoutPresetsController != null)
        {
            this.layoutPresetsController.openPresets(this.getContext(), x, y);
        }
    }

    /**
     * Rebuild the preview emitter from scratch. Needed after structural changes (e.g. switching a
     * motion axis between dynamic and parametric), since already-spawned particles keep the manual
     * flags from their spawn and would otherwise lag behind the new mode.
     */
    public void restartEmitter()
    {
        if (this.data != null)
        {
            this.renderer.setScheme(this.data);
        }
    }

    private MapType getLayoutPresetData()
    {
        MapType data = new MapType();

        data.put("particle_layout", this.dock.getLayoutRoot().toData());

        return data;
    }

    private void applyLayoutFromPreset(MapType data, int mouseX, int mouseY)
    {
        BaseType layoutData = data.get("particle_layout");

        if (layoutData == null)
        {
            return;
        }

        this.dock.applyLayoutRoot(EditorLayoutNode.fromData(layoutData));
    }

    private ILayoutSource createLayoutSource()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        return new ILayoutSource()
        {
            @Override
            public BaseValue value()
            {
                return layout;
            }

            @Override
            public EditorLayoutNode getRoot()
            {
                return layout.getParticleLayoutRoot();
            }

            @Override
            public void setRoot(EditorLayoutNode root)
            {
                layout.setParticleLayoutRoot(root);
            }

            @Override
            public List<EditorLayoutNode.SplitterNode> getSplitters()
            {
                return layout.getParticleSplitters();
            }

            @Override
            public List<EditorLayoutNode.SplitterNode> getSplittersForWrite()
            {
                return layout.getParticleSplittersForWrite();
            }

            @Override
            public EditorLayoutNode getDefault()
            {
                return EditorLayoutNode.defaultParticleLayout();
            }
        };
    }

    private UIScrollView createSectionView()
    {
        UIScrollView view = UI.scrollView(20, 10);
        view.scroll.cancelScrolling().opposite().scrollSpeed *= 3;

        return view;
    }

    /**
     * Wrap a content element (column/scroll layout) in a plain container before docking it.
     * The dock resets each panel's flex (which would wipe a column layout's {@code flex.post}),
     * so the actual content must live one level down where the dock never touches it.
     */
    private UIElement wrapScroll(UIElement content)
    {
        UIElement panel = new UIElement();

        content.relative(panel).w(1F).h(1F);
        panel.add(content);

        return panel;
    }

    private void addSection(UIScrollView view, UIParticleSchemeSection section)
    {
        this.sections.add(section);
        view.add(section);
    }



    @Override
    public void fill(ParticleScheme data)
    {
        super.fill(data);
        this.editor.setVisible(true);
        this.syncActiveDocumentTabWithData(data);
    }

    @Override
    public void showHomeView()
    {
        super.showHomeView();
    }

    @Override
    protected void fillData(ParticleScheme data)
    {
        this.editMoLang(null, null, null);

        if (this.data != null)
        {
            this.renderer.setScheme(this.data);

            for (UIParticleSchemeSection section : this.sections)
            {
                section.setScheme(this.data);
            }

            this.generalView.resize();
            this.emitterView.resize();
            this.particleView.resize();
            this.appearanceView.resize();
        }
        else
        {
            this.renderer.setScheme(null);
        }

        /* Dock gate shows/hides the preview + sections panels based on data presence. */
        this.dock.setupFlex(true);
    }

    @Override
    public void fillNames(Collection<String> names)
    {
        super.fillNames(names);

        DataPath currentPath = this.homeParticlesList != null ? this.homeParticlesList.getPath().copy() : DataPath.EMPTY.copy();
        DataPath selected = this.homeParticlesList != null ? this.homeParticlesList.getCurrentFirst() : null;
        String current = selected != null && !selected.folder ? selected.toString() : null;

        if (this.homeParticlesList != null)
        {
            this.homeParticlesList.fill(names);
            this.homeParticlesList.goTo(currentPath);
            if (current != null)
            {
                this.homeParticlesList.setCurrentFile(current);
            }
        }
        if (this.homeParticlesMosaic != null)
        {
            this.homeParticlesMosaic.fill(names, current);
        }
        this.updateHomeButtonsState();
    }

    @Override
    public void pickData(String id)
    {
        this.save();
        this.openParticleInDocumentTabs(id);
        RecentAssetsTracker.add(this.getType(), id);
    }

    @Override
    public void forceSave()
    {
        super.forceSave();

        ParticleFormRenderer.lastUpdate = System.currentTimeMillis();
    }

    @Override
    public void fillDefaultData(ParticleScheme data)
    {
        super.fillDefaultData(data);

        try (InputStream asset = BBSMod.getProvider().getAsset(PARTICLE_PLACEHOLDER))
        {
            MapType map = DataToString.mapFromString(IOUtils.readText(asset));

            ParticleScheme.PARSER.fromData(data, map);
        }
        catch (Exception e)
        {}
    }

    public UIParticleStatusIcons getStatusIcons()
    {
        return this.statusIcons;
    }

    public boolean isShowingHomePage()
    {
        return this.showingHomePage;
    }

    @Override
    public void open()
    {
        super.open();

        if (this.dashboard != null && this.dashboard.documentTabsBar != null && this.statusIcons != null)
        {
            this.dashboard.documentTabsBar.attachParticleStatusIcons(this.statusIcons);
            this.dashboard.documentTabsBar.layoutStatusIcons();
        }
    }

    @Override
    public void manualSave()
    {
        this.save();
    }

    @Override
    public void save()
    {
        super.save();

        if (this.statusIcons != null)
        {
            this.statusIcons.flashAutosave();
        }
    }

    @Override
    public void appear()
    {
        super.appear();

        this.textEditor.updateHighlighter();

        if (this.dashboard != null && this.dashboard.documentTabsBar != null)
        {
            this.dashboard.documentTabsBar.layoutStatusIcons();
        }
    }

    @Override
    public void close()
    {
        super.close();

        if (this.renderer.emitter != null)
        {
            this.renderer.emitter.particles.clear();
        }
    }

    @Override
    public void resize()
    {
        super.resize();

        if (this.dock != null)
        {
            this.dock.setupFlex(true);
        }
    }

    @Override
    protected void renderBackground(UIContext context)
    {
    }

    @Override
    public void render(UIContext context)
    {
        int color = BBSSettings.accentRgb();

        this.area.render(context.batcher, Colors.mulRGB(color | Colors.A100, 0.2F));

        super.render(context);

        if (this.molangId != null)
        {
            FontRenderer font = context.batcher.getFont();
            int w = font.getWidth(this.molangId);

            context.batcher.textCard(this.molangId, this.textEditor.area.ex() - 6 - w, this.textEditor.area.ey() - 6 - font.getHeight());
        }
    }

    @Override
    protected boolean shouldOpenOverlayOnFirstResize()
    {
        return false;
    }

    @Override
    protected boolean shouldRenderOpenOverlayHint()
    {
        return false;
    }



    private static final mchorse.bbs_mod.utils.colors.Color TEMP_COLOR = new mchorse.bbs_mod.utils.colors.Color();

    private static int getInterpolatedColor(int a, int b, float x)
    {
        Colors.interpolate(TEMP_COLOR, a, b, x);
        return TEMP_COLOR.getARGBColor();
    }

    private void renderHomeBackground(UIContext context)
    {
        if (!this.showingHomePage)
        {
            return;
        }

        int editorX = this.editor.area.x;
        int editorY = this.editor.area.y;
        int editorW = this.editor.area.w;
        int editorH = this.editor.area.h;
        int pageX = this.homePage.area.x;
        int pageY = this.homePage.area.y;
        int pageW = this.homePage.area.w;
        int pageH = this.homePage.area.h;
        int dividerX = this.homeParticlesSearch.area.x;

        // Render solid dark background matching films
        context.batcher.box(editorX, editorY, editorX + editorW, editorY + editorH, Colors.setA(0x0b0b0b, 1F));

        // Render Dynamic Snow Storm / Snowfall (Optimized & Deterministic)
        int primary = BBSSettings.accentRgb();
        int numFlakes = 160;
        float tick = context.getTickTransition();

        for (int i = 0; i < numFlakes; i++)
        {
            float xPercent = (float) ((i * 17.53F + 0.1F) % 1.0F);
            float baseX = editorX + xPercent * editorW;

            float speed = 0.5F + (float) ((i * 13.27F) % 0.8F);
            float fallOffset = (tick * speed) % (editorH + 40);
            float startY = (float) ((i * 37.89F) % (editorH + 40));
            float flakeY = editorY - 20 + ((startY + fallOffset) % (editorH + 40));

            float wobbleSpeed = 0.05F + (float) ((i * 7.41F) % 0.05F);
            float wobbleWidth = 4F + (float) ((i * 9.15F) % 8F);
            float wobbleX = (float) Math.sin(tick * wobbleSpeed + i) * wobbleWidth;
            float flakeX = baseX + wobbleX;

            float size = 1.0F + (float) ((i * 21.63F) % 3.0F);
            float alphaNorm = (size - 1.0F) / 3.0F;
            float baseAlpha = 0.25F + alphaNorm * 0.55F;

            // Fade out near top/bottom edges
            float edgeFade = 1.0F;
            float distToTop = flakeY - editorY;
            float distToBot = (editorY + editorH) - flakeY;
            if (distToTop < 30) edgeFade = distToTop / 30F;
            else if (distToBot < 30) edgeFade = distToBot / 30F;
            float finalAlpha = baseAlpha * edgeFade;

            // Mix user primary color with white: larger flakes are whiter/brighter, smaller ones have beautiful primary color tint
            int baseColor = getInterpolatedColor(primary, Colors.WHITE, 0.3F + alphaNorm * 0.5F);
            int finalColor = Colors.setA(baseColor, finalAlpha);

            int rx = Math.round(flakeX - size / 2F);
            int ry = Math.round(flakeY - size / 2F);
            int rsize = Math.round(size);
            if (rsize < 1) rsize = 1;

            context.batcher.box(rx, ry, rx + rsize, ry + rsize, finalColor);
        }

        // Drop shadow for the main page panel
        context.batcher.gradientHBox(pageX - 18, pageY, pageX, pageY + pageH, 0, Colors.setA(0x000000, 0.7F));
        context.batcher.gradientHBox(pageX + pageW, pageY, pageX + pageW + 18, pageY + pageH, Colors.setA(0x000000, 0.7F), 0);

        // Panel backgrounds
        context.batcher.box(pageX, pageY, pageX + pageW, pageY + pageH, Colors.setA(0x1e1e1e, 1F));

        UIHomePanel home = this.dashboard.getPanel(UIHomePanel.class);
        if (home != null)
        {
            home.renderCardAndBanners(context, this.homePage, dividerX, UIKeys.PARTICLES_HOME_LIST.get());
        }
    }

    public class UIParticleMosaicGrid extends UIScrollView
    {
        private static final int CARD_SIZE = 100;
        private static final int CARD_GAP = 6;
        private static final int CARD_LABEL_H = 16;

        private final Consumer<String> selectCallback;
        private final Consumer<String> doubleClickCallback;

        private final List<DataPath> particlePaths = new ArrayList<>();
        public String selectedId;
        private String lastClickedId;
        private long lastClickTime;
        private int lastCols = -1;
        private boolean rebuilding = false;

        public UIParticleMosaicGrid(Consumer<String> selectCallback, Consumer<String> doubleClickCallback)
        {
            super();
            this.selectCallback = selectCallback;
            this.doubleClickCallback = doubleClickCallback;
            this.scroll.scrollSpeed = 20;
        }

        public void fill(Collection<String> names, String selectedId)
        {
            this.selectedId = selectedId;
            this.lastCols = -1;
            this.filter("");
        }

        public void filter(String query)
        {
            this.particlePaths.clear();
            
            for (DataPath path : UIParticleSchemePanel.this.homeParticlesList.getFilteredList())
            {
                this.particlePaths.add(path);
            }
            
            this.buildCards();
            
            if (this.hasParent())
            {
                this.resize();
            }
        }

        public DataPath findPath(String id)
        {
            for (DataPath path : this.particlePaths)
            {
                if (path.toString().equals(id))
                {
                    return path;
                }
            }
            return null;
        }

        private void buildCards()
        {
            this.removeAll();
            if (this.particlePaths.isEmpty()) return;

            int effectiveW = this.area.w > 0 ? this.area.w : 500;
            int cols = Math.max(1, (effectiveW - CARD_GAP) / (CARD_SIZE + CARD_GAP));

            for (int i = 0; i < this.particlePaths.size(); i++)
            {
                final DataPath path = this.particlePaths.get(i);
                final String id = path.toString();
                final int col = i % cols;
                final int row = i / cols;

                int cx = CARD_GAP + col * (CARD_SIZE + CARD_GAP);
                int cy = CARD_GAP + row * (CARD_SIZE + CARD_GAP + CARD_LABEL_H);

                UIElement card = new UIElement()
                {
                    @Override
                    public boolean subMouseClicked(UIContext context)
                    {
                        if (this.area.isInside(context))
                        {
                            UIParticleMosaicGrid.this.onCardClicked(id);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void render(UIContext context)
                    {
                        boolean selected = id.equals(UIParticleMosaicGrid.this.selectedId);
                        int border = selected ? BBSSettings.accentRgb() : Colors.setA(Colors.WHITE, 0.1F);
                        int bg = selected ? Colors.setA(BBSSettings.accentRgb(), 0.1F) : Colors.setA(0, 0.2F);
                        
                        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), bg);
                        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);

                        super.render(context);

                        boolean isFolder = path.folder;
                        boolean isParent = id.equals("..");
                        
                        /* Render icon in center */
                        int iconX = this.area.mx();
                        int iconY = this.area.y + CARD_SIZE / 2;
                        Icon icon = isFolder || isParent ? Icons.FOLDER : Icons.PARTICLE;
                        
                        context.batcher.getContext().getMatrices().push();
                        context.batcher.getContext().getMatrices().translate(iconX, iconY, 0);
                        context.batcher.getContext().getMatrices().scale(2F, 2F, 1F);
                        context.batcher.getContext().getMatrices().translate(-iconX, -iconY, 0);
                        
                        context.batcher.icon(icon, iconX, iconY, 0.5F, 0.5F);
                        
                        context.batcher.getContext().getMatrices().pop();

                        String label = isParent ? "../" : path.getLast();
                        int maxW = this.area.w - 4;
                        if (context.batcher.getFont().getWidth(label) > maxW)
                        {
                            while (label.length() > 1 && context.batcher.getFont().getWidth(label + "...") > maxW)
                            {
                                label = label.substring(0, label.length() - 1);
                            }
                            label = label + "...";
                        }
                        context.batcher.textShadow(label, this.area.x + 2, this.area.y + CARD_SIZE + 2);
                    }
                };

                card.relative(this).x(cx).y(cy).w(CARD_SIZE).h(CARD_SIZE + CARD_LABEL_H);
                this.add(card);
            }

            int rows = (this.particlePaths.size() + cols - 1) / cols;
            int totalH = CARD_GAP + rows * (CARD_SIZE + CARD_LABEL_H + CARD_GAP);
            this.scroll.scrollSize = totalH;
            this.scroll.clamp();
        }

        private void onCardClicked(String id)
        {
            long now = System.currentTimeMillis();
            boolean sameAsPrev = id.equals(this.lastClickedId);
            boolean doubleClick = sameAsPrev && now - this.lastClickTime <= 300L;

            this.lastClickedId = id;
            this.lastClickTime = now;
            this.selectedId = id;

            if (this.selectCallback != null)
            {
                this.selectCallback.accept(id);
            }

            if (doubleClick && this.doubleClickCallback != null)
            {
                this.doubleClickCallback.accept(id);
            }
        }

        @Override
        public void resize()
        {
            super.resize();
            int effectiveW = this.area.w > 0 ? this.area.w : 500;
            int cols = Math.max(1, (effectiveW - CARD_GAP) / (CARD_SIZE + CARD_GAP));
            if (cols != this.lastCols && !this.rebuilding)
            {
                this.rebuilding = true;
                this.buildCards();
                this.rebuilding = false;
                this.lastCols = cols;
                super.resize();
            }
        }
    }
}
