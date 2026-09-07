package mchorse.bbs_mod.ui.model;

import mchorse.bbs_mod.BBSClient;
import mchorse.bbs_mod.BBSFeatures;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.animation.ActionsConfig;
import mchorse.bbs_mod.cubic.animation.gecko.config.GeckoAnimationsConfig;
import mchorse.bbs_mod.cubic.animation.gecko.validation.GeckoAnimationValidator;
import mchorse.bbs_mod.cubic.model.ModelConfig;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.URLSourcePack;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.UIPanelSwitcher;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.home.UIHomePanel;
import mchorse.bbs_mod.ui.model.UIModelIKPanel;
import mchorse.bbs_mod.ui.model.UIModelUIStyles;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.pose.UIPoseEditor;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.RecentAssetsTracker;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.resources.Pixels;

import net.minecraft.client.MinecraftClient;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
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

import org.slf4j.Logger;

public class UIModelPanel extends UIDataDashboardPanel<ModelConfig> implements IUIModelPanelHost
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final GeckoAnimationValidator GECKO_VALIDATOR = new GeckoAnimationValidator();

    private static int leftPanelWidth = 200;
    private static int rightPanelWidth = 200;

    public UIModelEditorRenderer renderer;
    public UIIcon reloadIcon;
    
    public UIElement mainView;
    public List<UIElement> panels = new ArrayList<>();
    public List<UIIcon> panelButtons = new ArrayList<>();
    
    private UIElement homePage;
    private UISearchList<DataPath> homeModelsSearch;
    private UIDataPathList homeModelsList;
    private UIModelMosaicGrid homeModelsMosaic;
    private UIIcon homeViewToggle;
    private UIPanelSwitcher panelSwitcher;
    private UIElement homeActionsPanel;
    private UIButton homeCreateModel;
    private UIButton homeDuplicateCurrent;
    private UIButton homeRenameCurrent;
    private UIButton homeDeleteCurrent;
    private String homeLastClickedModelId;
    private long homeLastClickTime;
    private final List<ModelDocumentTab> modelDocumentTabs = new ArrayList<>();
    private int activeModelDocumentTab = -1;
    private boolean showingHomePage = true;
    private boolean pickingBone;

    public UIElement modelSettingsPanel;
    public UIModelPhysBonePanel physBonesPanel;
    public UIModelConstraintsPanel constraintsPanel;
    public UIModelGeometryPanel geometryPanel;
    public UIModelIKPanel ikPanel;
    public UIScrollView sectionsView;
    public UIScrollView rightView;
    public List<UIModelSection> sections = new ArrayList<>();

    public UIModelPanel(UIDashboard dashboard)
    {
        super(dashboard);
        this.overlay.resizable().minSize(260, 220);

        this.overlay.add.setEnabled(false);

        this.reloadIcon = new UIIcon(Icons.REFRESH, (b) ->
        {
            if (this.data != null)
            {
                String modelId = this.data.getId();
                BBSClient.getModels().loadModel(modelId);
                this.renderer.invalidatePreviewModel();
                this.fillData(this.data);
            }
        });
        this.reloadIcon.tooltip(UIKeys.MODELS_RELOAD, Direction.LEFT);
        this.iconBar.add(this.reloadIcon);

        this.renderer = new UIModelEditorRenderer();
        this.renderer.relative(this).wTo(this.iconBar.getFlex()).h(1F);
        this.renderer.setCallback(this::pickBone);
        this.renderer.setCubeCallback(this::pickCube);

        this.editor.resetFlex().relative(this).w(1F).h(1F);

        UIRenderable viewportBackground = new UIRenderable((context) ->
        {
            if (this.renderer.area.w > 0 && this.renderer.area.h > 0)
            {
                context.batcher.box(this.renderer.area.x, this.renderer.area.y, this.renderer.area.ex(), this.renderer.area.ey(), UIModelUIStyles.FORM_VIEWPORT_BACKGROUND);
            }
        });

        /* Background must render before the 3D viewport; renderer stays earlier in the child list
           so editor side panels keep mouse priority (childrenMouseClicked walks last-to-first). */
        this.prepend(this.renderer);
        this.prepend(viewportBackground);

        this.homePage = new UIElement()
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                UIModelPanel.this.homeModelsList.deselect();
                UIModelPanel.this.handleHomeModelsSelection(null);

                return super.subMouseClicked(context);
            }
        };
        this.homeActionsPanel = new UIElement();
        this.homeModelsList = new UIDataPathList((list) -> this.handleHomeModelsSelection(list));
        this.homeModelsList.setFileIcon(Icons.MORPH);
        this.homeModelsList.context((menu) ->
        {
            menu.action(Icons.FOLDER, UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE, this::addFolderFromHome);

            String selectedId = this.getSelectedHomeModelId();
            if (selectedId != null)
            {
                menu.action(Icons.COPY, UIKeys.PANELS_CONTEXT_COPY, this::copyHomeModel);
            }

            try
            {
                MapType clipboardData = Window.getClipboardMap("_ContentType_" + this.getType().getId());

                if (clipboardData != null)
                {
                    menu.action(Icons.PASTE, UIKeys.PANELS_CONTEXT_PASTE, () -> this.pasteHomeModel(clipboardData));
                }
            }
            catch (Exception e)
            {}

            File folder = this.getType().getRepository().getFolder();

            if (folder != null)
            {
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, () ->
                {
                    UIUtils.openFolder(new File(folder, this.homeModelsList.getPath().toString()));
                });
            }
        });
        this.homeModelsList.moveCallback = (from, to) ->
        {
            String fromStr = from.toString();
            String toStr = to.toString();

            if (from.folder)
            {
                this.getType().getRepository().renameFolder(fromStr, toStr, (bool) ->
                {
                    if (bool)
                    {
                        this.requestNames();
                    }
                });
            }
            else
            {
                this.getType().getRepository().rename(fromStr, toStr);

                for (ModelDocumentTab tab : this.modelDocumentTabs)
                {
                    if (!tab.home && fromStr.equals(tab.modelId))
                    {
                        tab.modelId = toStr;
                    }
                }
                this.rebuildModelDocumentTabs();

                if (this.data != null && fromStr.equals(this.data.getId()))
                {
                    this.data.setId(toStr);
                }

                this.requestNames();
            }
        };
        this.homeModelsSearch = new UISearchList<>(this.homeModelsList).label(UIKeys.GENERAL_SEARCH);
        this.homeModelsSearch.list.background();

        this.homeModelsMosaic = new UIModelMosaicGrid((modelId) -> {
            this.homeModelsList.setCurrentFile(modelId);
            this.handleHomeModelsSelection(null);
        }, (modelId) -> this.openModelInDocumentTabs(modelId));

        Consumer<String> oldCallback = this.homeModelsSearch.search.callback;
        this.homeModelsSearch.search.callback = (str) -> {
            if (oldCallback != null) oldCallback.accept(str);
            this.homeModelsMosaic.filter(str);
        };

        boolean mosaic = BBSSettings.lastViewMosaic.get();
        this.homeViewToggle = new UIIcon(mosaic ? Icons.LIST : Icons.GALLERY, (b) -> this.toggleMosaicView());
        this.homeViewToggle.tooltip(mosaic ? UIKeys.MODELS_HOME_VIEW_LIST : UIKeys.MODELS_HOME_VIEW_MOSAIC, Direction.LEFT);
        
        this.homeCreateModel = this.createHomeButton(UIKeys.MODELS_CRUD_ADD, Icons.ADD, (b) ->
        {
            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.MODELS_CRUD_ADD,
                UIKeys.PANELS_MODALS_ADD,
                (str) -> {
                    try {
                        Method m = UIDataOverlayPanel.class.getDeclaredMethod("addNewData", String.class, MapType.class);
                        m.setAccessible(true);
                        m.invoke(this.overlay, str, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            );
            panel.text.filename();
            UIOverlay.addOverlay(this.getContext(), panel);
        });
        this.homeDuplicateCurrent = this.createHomeButton(UIKeys.MODELS_CRUD_DUPE, Icons.COPY, (b) ->
        {
            String selectedId = this.getSelectedHomeModelId();
            if (selectedId == null) return;

            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_DUPE,
                UIKeys.PANELS_MODALS_DUPE,
                (str) -> {
                    String targetId = this.homeModelsList.getPath(str).toString();
                    if (targetId.trim().isEmpty()) {
                        this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
                        return;
                    }
                    if (this.homeModelsList.hasInHierarchy(targetId)) {
                        return;
                    }
                    this.getType().getRepository().load(selectedId, (originalModel) -> {
                        if (originalModel != null) {
                            this.getType().getRepository().save(targetId, originalModel.toData().asMap());
                            this.requestNames();
                        }
                    });
                }
            );

            panel.text.setText(new DataPath(selectedId).getLast());
            panel.text.filename();

            UIOverlay.addOverlay(this.getContext(), panel);
        });
        this.homeRenameCurrent = this.createHomeButton(UIKeys.MODELS_CRUD_RENAME, Icons.EDIT, (b) ->
        {
            String selectedId = this.getSelectedHomeModelId();
            if (selectedId == null) return;

            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_RENAME,
                UIKeys.PANELS_MODALS_RENAME,
                (str) -> {
                    String targetId = this.homeModelsList.getPath(str).toString();
                    if (targetId.trim().isEmpty()) {
                        this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
                        return;
                    }
                    if (this.homeModelsList.hasInHierarchy(targetId)) {
                        return;
                    }
                    this.getType().getRepository().rename(selectedId, targetId);

                    for (ModelDocumentTab tab : this.modelDocumentTabs) {
                        if (!tab.home && selectedId.equals(tab.modelId)) {
                            tab.modelId = targetId;
                        }
                    }
                    this.rebuildModelDocumentTabs();

                    if (this.data != null && selectedId.equals(this.data.getId())) {
                        this.data.setId(targetId);
                    }

                    this.requestNames();
                }
            );

            panel.text.setText(new DataPath(selectedId).getLast());
            panel.text.filename();

            UIOverlay.addOverlay(this.getContext(), panel);
        });
        this.homeDeleteCurrent = this.createHomeButton(UIKeys.MODELS_CRUD_REMOVE, Icons.REMOVE, (b) ->
        {
            String selectedId = this.getSelectedHomeModelId();
            if (selectedId == null) return;

            UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
                UIKeys.GENERAL_REMOVE,
                UIKeys.PANELS_MODALS_REMOVE,
                (confirm) ->
                {
                    if (confirm) {
                        this.getType().getRepository().delete(selectedId);

                        for (int i = this.modelDocumentTabs.size() - 1; i >= 0; i--) {
                            ModelDocumentTab tab = this.modelDocumentTabs.get(i);
                            if (!tab.home && selectedId.equals(tab.modelId)) {
                                this.removeModelDocumentTab(i);
                            }
                        }

                        if (this.data != null && selectedId.equals(this.data.getId())) {
                            this.fill(null);
                        }

                        this.requestNames();
                    }
                }
            );

            UIOverlay.addOverlay(this.getContext(), panel);
        });
        this.updateHomeButtonsState();

        this.iconBar.relative(this).x(1F, -20).y(0).w(20).h(1F).column(0).stretch();

        this.homePage.relative(this.editor).x(0.5F, -250).y(0).w(500).h(1F);
        this.homeActionsPanel.relative(this.homePage).x(0).y(UIHomePanel.HOME_BANNER_HEIGHT + 20).w(0.35F).h(1F, -(UIHomePanel.HOME_BANNER_HEIGHT + 20 + 10)).column(0).vertical().stretch();
        
        this.panelSwitcher = new UIPanelSwitcher(this.dashboard);
        this.panelSwitcher.relative(this.homePage).x(0.5F, -87).y(1F, -32).w(175).h(24);

        UIElement spacing = new UIElement();
        spacing.h(8);

        this.homeActionsPanel.add(this.homeCreateModel, spacing, this.homeDuplicateCurrent, this.homeRenameCurrent, this.homeDeleteCurrent);
        this.homeModelsSearch.relative(this.homePage).x(0.35F).y(UIHomePanel.HOME_BANNER_HEIGHT + 20).w(0.65F).h(1F, -(UIHomePanel.HOME_BANNER_HEIGHT + 20 + 10));
        this.homeModelsSearch.search.w(1F, -25);
        this.homeModelsMosaic.relative(this.homeModelsSearch).x(0).y(20).w(1F).h(1F, -20);
        this.homeModelsMosaic.setVisible(mosaic);
        this.homeModelsSearch.list.setVisible(!mosaic);
        this.homeViewToggle.relative(this.homeModelsSearch).x(1F, -22).y(0).w(20).h(20);
        this.homePage.add(new UIRenderable(this::renderHomeBackground), this.homeActionsPanel, this.homeModelsSearch, this.homeModelsMosaic, this.homeViewToggle, this.panelSwitcher);

        this.mainView = new UIElement();
        this.mainView.relative(this.editor).y(0).w(1F).h(1F);

        this.editor.add(this.mainView, this.homePage);
        this.iconBar.prepend(new UIRenderable((context) ->
        {
            if (!this.iconBar.isVisible())
            {
                return;
            }

            context.batcher.box(this.iconBar.area.x, this.iconBar.area.y, this.iconBar.area.ex(), this.iconBar.area.ey(), UIModelUIStyles.STRIP_BACKGROUND);
        }));
        this.iconBar.prepend(new UIRenderable(this::renderIcons));

        /* Model Settings Panel */
        if (BBSSettings.uiLayoutPreferences != null)
        {
            leftPanelWidth = (int) BBSSettings.uiLayoutPreferences.getFormPanelWidth("UIModelPanel_left", 200F);
            rightPanelWidth = (int) BBSSettings.uiLayoutPreferences.getFormPanelWidth("UIModelPanel_right", 200F);
        }

        this.modelSettingsPanel = new UIElement();
        this.modelSettingsPanel.relative(this.mainView).w(1F).h(1F);
        
        this.sectionsView = UI.scrollView(20, 10);
        this.sectionsView.scroll.cancelScrolling().opposite().scrollSpeed *= 3;
        this.sectionsView.relative(this.modelSettingsPanel).y(0).w(leftPanelWidth).h(1F);
        
        this.rightView = UI.scrollView(20, 10);
        this.rightView.scroll.cancelScrolling().scrollSpeed *= 3;
        this.rightView.relative(this.modelSettingsPanel).x(1F, -rightPanelWidth).y(0).w(rightPanelWidth).h(1F);

        UIDraggable leftDraggable = new UIDraggable((context) ->
        {
            int diff = context.mouseX - this.sectionsView.area.x;
            int maxW = Math.max(140, this.modelSettingsPanel.area.w / 2 - 20);
            leftPanelWidth = MathUtils.clamp(diff, 140, maxW);

            this.sectionsView.w(leftPanelWidth);
            this.modelSettingsPanel.resize();
        }).dragEnd(() ->
        {
            if (BBSSettings.uiLayoutPreferences != null)
            {
                BBSSettings.uiLayoutPreferences.setFormPanelWidth("UIModelPanel_left", leftPanelWidth);
            }
        });
        leftDraggable.relative(this.sectionsView).x(1F).y(0).w(6).h(1F).anchorX(0.5F);
        leftDraggable.hoverOnly().rendering((context) ->
        {
            int color = leftDraggable.isDragging() ? BBSSettings.primaryColor.get() | Colors.A100 : (leftDraggable.area.isInside(context) ? Colors.A75 : Colors.A25);
            context.batcher.box(leftDraggable.area.x + 2, leftDraggable.area.y, leftDraggable.area.x + 4, leftDraggable.area.ey(), color);
        });

        UIDraggable rightDraggable = new UIDraggable((context) ->
        {
            int diff = this.modelSettingsPanel.area.ex() - context.mouseX;
            int maxW = Math.max(140, this.modelSettingsPanel.area.w / 2 - 20);
            rightPanelWidth = MathUtils.clamp(diff, 140, maxW);

            this.rightView.x(1F, -rightPanelWidth).w(rightPanelWidth);
            this.modelSettingsPanel.resize();
        }).dragEnd(() ->
        {
            if (BBSSettings.uiLayoutPreferences != null)
            {
                BBSSettings.uiLayoutPreferences.setFormPanelWidth("UIModelPanel_right", rightPanelWidth);
            }
        });
        rightDraggable.relative(this.rightView).x(0F).y(0).w(6).h(1F).anchorX(0.5F);
        rightDraggable.hoverOnly().rendering((context) ->
        {
            int color = rightDraggable.isDragging() ? BBSSettings.primaryColor.get() | Colors.A100 : (rightDraggable.area.isInside(context) ? Colors.A75 : Colors.A25);
            context.batcher.box(rightDraggable.area.x + 2, rightDraggable.area.y, rightDraggable.area.x + 4, rightDraggable.area.ey(), color);
        });
        
        this.modelSettingsPanel.add(this.sectionsView, this.rightView, leftDraggable, rightDraggable);

        this.editor.prepend(new UIRenderable((context) ->
        {
            if (UIModelPanel.this.showingHomePage)
            {
                return;
            }

            if (UIModelPanel.this.modelSettingsPanel.isVisible() && UIModelPanel.this.modelSettingsPanel.getParent() == UIModelPanel.this.mainView)
            {
                context.batcher.box(UIModelPanel.this.sectionsView.area.x, UIModelPanel.this.sectionsView.area.y, UIModelPanel.this.sectionsView.area.ex(), UIModelPanel.this.sectionsView.area.ey(), 0xFF111115);
                context.batcher.outline(UIModelPanel.this.sectionsView.area.x - 1, UIModelPanel.this.sectionsView.area.y - 1, UIModelPanel.this.sectionsView.area.ex() + 1, UIModelPanel.this.sectionsView.area.ey() + 1, 0xFF5A5A5A);
                context.batcher.box(UIModelPanel.this.rightView.area.x, UIModelPanel.this.rightView.area.y, UIModelPanel.this.rightView.area.ex(), UIModelPanel.this.rightView.area.ey(), 0xFF111115);
                context.batcher.outline(UIModelPanel.this.rightView.area.x - 1, UIModelPanel.this.rightView.area.y - 1, UIModelPanel.this.rightView.area.ex() + 1, UIModelPanel.this.rightView.area.ey() + 1, 0xFF5A5A5A);
            }
        }));

        this.physBonesPanel = new UIModelPhysBonePanel(this);
        this.constraintsPanel = new UIModelConstraintsPanel(this);

        /* Sections setup */
        this.overlay.namesList.setFileIcon(Icons.MORPH);

        this.addSection(new UIModelGeneralSection(this));
        
        UIModelPartsSection parts = new UIModelPartsSection(this);
        this.sections.add(parts);
        this.setRight(parts.poseEditor);
        this.renderer.transform = parts.poseEditor.transform;

        this.addSection(new UIModelArmorSection(this));
        this.addSection(new UIModelItemsSection(this));
        this.addSection(new UIModelHandsSection(this));
        this.addSection(new UIModelSneakingSection(this));
        this.addSection(new UIModelGeckoAnimationsSection(this));

        if (BBSFeatures.MODEL_PROCEDURAL_LOOK_AT_UI)
        {
            this.addSection(new UIModelLookAtSection(this));
        }

        /* Register Panels */
        UIElement spacer = new UIElement();
        spacer.relative(this.iconBar).w(1F).h(10);
        this.iconBar.add(spacer);

        this.geometryPanel = new UIModelGeometryPanel(this);
        this.ikPanel = new UIModelIKPanel(this);

        this.registerPanel(this.modelSettingsPanel, UIKeys.MODELS_SETTINGS, Icons.MODELS_SETTINGS);

        if (BBSFeatures.MODEL_IK_UI)
        {
            this.registerPanel(this.ikPanel, UIKeys.MODELS_IK_EDITOR, Icons.IK);
        }

        this.registerPanel(this.physBonesPanel, UIKeys.MODELS_PHYS_BONES_EDITOR, Icons.DYNAMIC_BONES);
        this.registerPanel(this.constraintsPanel, UIKeys.MODELS_CONSTRAINTS_EDITOR, Icons.LOCKED);
        this.registerPanel(this.geometryPanel, UIKeys.MODELS_GEOMETRY_EDITOR, Icons.GEOMETRY_EDITOR);

        this.setPanel(this.modelSettingsPanel);
        
        this.createHomeDocumentTab(true);
        this.fill(null);
        this.updateModelDocumentView();
    }
    
    private void renderIcons(UIContext context)
    {
        for (int i = 0, c = this.panels.size(); i < c; i++)
        {
            if (this.mainView.getChildren().contains(this.panels.get(i)))
            {
                int index = this.iconBar.getChildren().size() - this.panels.size() + i;

                if (index >= 0 && index < this.iconBar.getChildren().size())
                {
                    IUIElement child = this.iconBar.getChildren().get(index);

                    if (child instanceof UIIcon)
                    {
                        UIDashboardPanels.renderHighlight(context.batcher, ((UIIcon) child).area);
                    }
                }
            }
        }

        if (this.reloadIcon != null)
        {
            Area a = this.reloadIcon.area;

            context.batcher.box(a.x + 3, a.ey() + 4, a.ex() - 3, a.ey() + 5, 0x22ffffff);
        }
        else if (this.saveIcon != null)
        {
            Area a = this.saveIcon.area;

            context.batcher.box(a.x + 3, a.ey() + 4, a.ex() - 3, a.ey() + 5, 0x22ffffff);
        }
    }

    private static final mchorse.bbs_mod.utils.colors.Color TEMP_COLOR = new mchorse.bbs_mod.utils.colors.Color();

    private static int getInterpolatedColor(int a, int b, float x)
    {
        Colors.interpolate(TEMP_COLOR, a, b, x);
        return TEMP_COLOR.getARGBColor();
    }

    private void drawBeveledBlock(UIContext context, float bx, float by, float size, int color)
    {
        int rx = Math.round(bx);
        int ry = Math.round(by);
        int rsize = Math.round(size);
        if (rsize < 1) rsize = 1;

        // Base box
        context.batcher.box(rx, ry, rx + rsize, ry + rsize, color);

        // Bevel highlights
        float alpha = Colors.getA(color);
        int whiteBevel = Colors.setA(Colors.WHITE, alpha * 0.3F);
        int blackBevel = Colors.setA(0xff000000, alpha * 0.4F);

        // Top bevel
        context.batcher.box(rx, ry, rx + rsize, ry + 1, whiteBevel);
        // Left bevel
        context.batcher.box(rx, ry, rx + 1, ry + rsize, whiteBevel);
        // Bottom shadow
        context.batcher.box(rx, ry + rsize - 1, rx + rsize, ry + rsize, blackBevel);
        // Right shadow
        context.batcher.box(rx + rsize - 1, ry, rx + rsize, ry + rsize, blackBevel);
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
        int dividerX = this.homeModelsSearch.area.x;

        // Render solid dark background matching films
        context.batcher.box(editorX, editorY, editorX + editorW, editorY + editorH, Colors.setA(0x0b0b0b, 1F));

        int primary = BBSSettings.accentRgb();
        float tick = context.getTickTransition();

        float blockSize = 12F;
        float gap = 1F;
        float gridW = blockSize + gap;

        // Calculate columns for left and right visible areas separately
        int leftCols = Math.round((pageX - editorX) / gridW);
        if (leftCols < 1) leftCols = 1;

        int rightCols = Math.round(((editorX + editorW - 65) - (pageX + pageW)) / gridW);
        if (rightCols < 1) rightCols = 1;

        // Calculate slots per side based on screen dimensions to guarantee no horizontal overlaps
        int maxLeftSlots = leftCols - 3;
        if (maxLeftSlots < 3) maxLeftSlots = 3;
        int maxRightSlots = rightCols - 3;
        if (maxRightSlots < 3) maxRightSlots = 3;

        int desiredHalfPieces = 10;
        int leftHalf = Math.min(desiredHalfPieces, maxLeftSlots);
        int rightHalf = Math.min(desiredHalfPieces, maxRightSlots);

        int numPieces = leftHalf + rightHalf;

        for (int p = 0; p < numPieces; p++)
        {
            float alignedX;

            if (p < leftHalf)
            {
                // Left visible area: map p uniquely to a non-overlapping column using a coprime multiplier
                int step = 3;
                if (leftHalf % 3 == 0) step = 5;
                int slot = (p * step) % leftHalf;

                int colIndex = Math.round(((float) slot / (leftHalf - 1 == 0 ? 1 : leftHalf - 1)) * (leftCols - 3));
                if (colIndex < 0) colIndex = 0;
                alignedX = editorX + colIndex * gridW;
            }
            else
            {
                // Right visible area: map rIndex uniquely to a non-overlapping column
                int rIndex = p - leftHalf;
                int step = 3;
                if (rightHalf % 3 == 0) step = 5;
                int slot = (rIndex * step) % rightHalf;

                int colIndex = Math.round(((float) slot / (rightHalf - 1 == 0 ? 1 : rightHalf - 1)) * (rightCols - 3));
                if (colIndex < 0) colIndex = 0;
                alignedX = (pageX + pageW) + colIndex * gridW;
            }

            float speed = 0.35F + (float) ((p * 3.17F) % 0.45F);

            float fallOffset = (tick * speed) % (editorH + 80);
            float startY = (float) ((p * 47.19F) % (editorH + 80));
            float pieceY = editorY - 40 + ((startY + fallOffset) % (editorH + 80));
            float alignedY = Math.round(pieceY / gridW) * gridW;

            int shapeType = p % 7;
            int[][] blocks;
            switch (shapeType)
            {
                case 0: // O (2x2 square)
                    blocks = new int[][] {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
                    break;
                case 1: // I (4x1 line)
                    blocks = new int[][] {{0, 0}, {0, 1}, {0, 2}, {0, 3}};
                    break;
                case 2: // T
                    blocks = new int[][] {{0, 0}, {1, 0}, {2, 0}, {1, 1}};
                    break;
                case 3: // L
                    blocks = new int[][] {{0, 0}, {0, 1}, {0, 2}, {1, 2}};
                    break;
                case 4: // J
                    blocks = new int[][] {{1, 0}, {1, 1}, {1, 2}, {0, 2}};
                    break;
                case 5: // S
                    blocks = new int[][] {{1, 0}, {2, 0}, {0, 1}, {1, 1}};
                    break;
                default: // Z
                    blocks = new int[][] {{0, 0}, {1, 0}, {1, 1}, {2, 1}};
                    break;
            }

            int rotation = (int) (tick * 0.012F + p) % 4;
            float shadeFactor = 0.5F + (float) ((p * 3.14F) % 0.5F);
            int pieceColor = Colors.mulRGB(primary, shadeFactor);

            for (int[] b : blocks)
            {
                int rx = b[0];
                int ry = b[1];

                for (int r = 0; r < rotation; r++)
                {
                    int temp = rx;
                    rx = 1 - (ry - 1);
                    ry = temp;
                }

                float blockX = alignedX + rx * gridW;
                float blockY = alignedY + ry * gridW;

                if (blockY >= editorY - gridW && blockY < editorY + editorH)
                {
                    float edgeFade = 1.0F;
                    float distToTop = blockY - editorY;
                    float distToBot = (editorY + editorH) - blockY;
                    if (distToTop < 30) edgeFade = distToTop / 30F;
                    else if (distToBot < 30) edgeFade = distToBot / 30F;

                    int finalColor = Colors.setA(pieceColor, 0.4F * edgeFade);
                    drawBeveledBlock(context, blockX, blockY, blockSize, finalColor);
                }
            }
        }
        
        UIHomePanel home = this.dashboard.getPanel(UIHomePanel.class);
        if (home != null)
        {
            home.renderCardAndBanners(context, this.homePage, dividerX, UIKeys.MODELS_HOME_LIST.get());
        }
    }

    private void clickWithContext(UIElement element)
    {
        UIContext context = this.getContext();

        if (context == null || element == null)
        {
            return;
        }

        element.clickItself(context);
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

    private void toggleMosaicView()
    {
        boolean mosaic = !BBSSettings.lastViewMosaic.get();

        BBSSettings.lastViewMosaic.set(mosaic);
        this.homeModelsSearch.list.setVisible(!mosaic);
        this.homeModelsMosaic.setVisible(mosaic);

        if (mosaic)
        {
            this.homeViewToggle.both(Icons.LIST);
            this.homeViewToggle.tooltip(UIKeys.MODELS_HOME_VIEW_LIST, Direction.LEFT);

            this.homeModelsMosaic.filter(this.homeModelsSearch.search.getText());
        }
        else
        {
            this.homeViewToggle.both(Icons.GALLERY);
            this.homeViewToggle.tooltip(UIKeys.MODELS_HOME_VIEW_MOSAIC, Direction.LEFT);
        }
    }

    private void updateHomeButtonsState()
    {
        boolean hasSelectedModel = this.homeModelsList != null && this.homeModelsList.getCurrentFirst() != null;
        boolean enableIcons = !this.showingHomePage;

        if (this.homeDuplicateCurrent != null)
        {
            this.homeDuplicateCurrent.setEnabled(hasSelectedModel);
        }

        if (this.homeRenameCurrent != null)
        {
            this.homeRenameCurrent.setEnabled(hasSelectedModel);
        }

        if (this.homeDeleteCurrent != null)
        {
            this.homeDeleteCurrent.setEnabled(hasSelectedModel);
        }

        if (this.reloadIcon != null) this.reloadIcon.setEnabled(enableIcons && this.data != null);
        if (this.saveIcon != null) this.saveIcon.setEnabled(enableIcons && this.data != null);
        if (this.openOverlay != null) this.openOverlay.setEnabled(enableIcons);
        for (UIIcon button : this.panelButtons) {
            button.setEnabled(enableIcons && this.data != null);
        }
    }

    private String getSelectedHomeModelId()
    {
        DataPath selected = this.homeModelsList.getCurrentFirst();

        if (selected == null || selected.folder)
        {
            return null;
        }

        return selected.toString();
    }

    private void handleHomeModelsSelection(List<DataPath> list)
    {
        String selected = this.getSelectedHomeModelId();
        this.overlay.namesList.setCurrentFile(selected);

        this.updateHomeButtonsState();

        if (selected == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        boolean sameAsPrevious = selected.equals(this.homeLastClickedModelId);
        boolean doubleClick = sameAsPrevious && now - this.homeLastClickTime <= 300L;

        this.homeLastClickedModelId = selected;
        this.homeLastClickTime = now;

        if (doubleClick)
        {
            this.openModelInDocumentTabs(selected);
        }
    }

    private void addFolderFromHome()
    {
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_ADD_FOLDER,
            (str) -> {
                String path = this.homeModelsList.getPath(str).toString();
                if (path.trim().isEmpty()) {
                    this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
                    return;
                }
                this.getType().getRepository().addFolder(path, (bool) -> {
                    if (bool) {
                        this.requestNames();
                    }
                });
            }
        );

        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void copyHomeModel()
    {
        String selectedId = this.getSelectedHomeModelId();
        if (selectedId == null) return;

        this.getType().getRepository().load(selectedId, (model) -> {
            if (model != null) {
                Window.setInMemoryClipboard(model.toData().asMap(), "_ContentType_" + this.getType().getId());
            }
        });
    }

    private void pasteHomeModel(MapType data)
    {
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_ADD,
            UIKeys.PANELS_MODALS_ADD,
            (str) -> {
                String targetId = this.homeModelsList.getPath(str).toString();
                if (targetId.trim().isEmpty()) {
                    this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
                    return;
                }
                if (this.homeModelsList.hasInHierarchy(targetId)) {
                    return;
                }

                ModelConfig newModel = (ModelConfig) this.getType().getRepository().create(targetId, data);
                this.fill(newModel);
            }
        );

        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void createHomeDocumentTab(boolean activate)
    {
        this.modelDocumentTabs.add(new ModelDocumentTab(true, null));
        int index = this.modelDocumentTabs.size() - 1;

        this.rebuildModelDocumentTabs();

        if (activate)
        {
            this.activateModelDocumentTab(index, false);
        }
    }

    private void addHomeDocumentTab()
    {
        int insertAt = Math.max(0, this.activeModelDocumentTab + 1);

        this.modelDocumentTabs.add(insertAt, new ModelDocumentTab(true, null));
        this.rebuildModelDocumentTabs();
        this.activateModelDocumentTab(insertAt, false);
    }

    private int findTabByModelId(String id)
    {
        for (int i = 0; i < this.modelDocumentTabs.size(); i++)
        {
            ModelDocumentTab tab = this.modelDocumentTabs.get(i);

            if (!tab.home && id.equals(tab.modelId))
            {
                return i;
            }
        }

        return -1;
    }

    private void openModelInDocumentTabs(String id)
    {
        if (id == null || id.trim().isEmpty())
        {
            return;
        }

        int existingIndex = this.findTabByModelId(id);

        if (existingIndex >= 0)
        {
            this.activateModelDocumentTab(existingIndex, true);

            return;
        }

        if (this.activeModelDocumentTab < 0 || this.activeModelDocumentTab >= this.modelDocumentTabs.size())
        {
            if (this.modelDocumentTabs.isEmpty())
            {
                this.modelDocumentTabs.add(new ModelDocumentTab(true, null));
            }

            this.activeModelDocumentTab = 0;
        }

        ModelDocumentTab active = this.modelDocumentTabs.get(this.activeModelDocumentTab);

        if (active.home)
        {
            active.home = false;
            active.modelId = id;
            this.rebuildModelDocumentTabs();
            this.activateModelDocumentTab(this.activeModelDocumentTab, true);
        }
        else
        {
            int insertAt = this.activeModelDocumentTab + 1;
            this.modelDocumentTabs.add(insertAt, new ModelDocumentTab(false, id));
            this.rebuildModelDocumentTabs();
            this.activateModelDocumentTab(insertAt, true);
        }
    }

    private void activateModelDocumentTab(int index, boolean loadModel)
    {
        if (index < 0 || index >= this.modelDocumentTabs.size())
        {
            return;
        }

        if (this.data != null && this.activeModelDocumentTab != index)
        {
            this.save();
        }

        this.activeModelDocumentTab = index;

        ModelDocumentTab tab = this.modelDocumentTabs.get(index);

        if (tab.home)
        {
            this.updateModelDocumentView();
        }
        else
        {
            if (loadModel || this.data == null || this.data.getId() == null || !this.data.getId().equals(tab.modelId))
            {
                this.requestData(tab.modelId);
            }
            else
            {
                this.updateModelDocumentView();
            }
        }

        this.rebuildModelDocumentTabs();
    }

    private void removeModelDocumentTab(int index)
    {
        if (index < 0 || index >= this.modelDocumentTabs.size())
        {
            return;
        }

        this.modelDocumentTabs.remove(index);

        if (this.modelDocumentTabs.isEmpty())
        {
            this.modelDocumentTabs.add(new ModelDocumentTab(true, null));
            this.activeModelDocumentTab = 0;
            this.rebuildModelDocumentTabs();
            this.activateModelDocumentTab(0, false);

            return;
        }

        if (index < this.activeModelDocumentTab)
        {
            this.activeModelDocumentTab--;
        }
        else if (index == this.activeModelDocumentTab)
        {
            this.activeModelDocumentTab = Math.max(0, Math.min(this.activeModelDocumentTab, this.modelDocumentTabs.size() - 1));
        }

        this.rebuildModelDocumentTabs();
        this.activateModelDocumentTab(this.activeModelDocumentTab, false);
    }

    private void rebuildModelDocumentTabs()
    {
        /* No-op: the legacy tab bar UI was removed; the unified UIDocumentTabsBar at the dashboard level replaces it. */
    }

    private void syncActiveDocumentTabWithData(ModelConfig data)
    {
        if (data != null)
        {
            if (this.activeModelDocumentTab < 0 || this.activeModelDocumentTab >= this.modelDocumentTabs.size())
            {
                this.modelDocumentTabs.add(new ModelDocumentTab(false, data.getId()));
                this.activeModelDocumentTab = this.modelDocumentTabs.size() - 1;
            }
            else
            {
                ModelDocumentTab tab = this.modelDocumentTabs.get(this.activeModelDocumentTab);

                tab.home = false;
                tab.modelId = data.getId();
            }
        }

        this.rebuildModelDocumentTabs();
        this.updateModelDocumentView();
    }

    private void updateModelDocumentView()
    {
        boolean home = this.activeModelDocumentTab < 0
            || this.activeModelDocumentTab >= this.modelDocumentTabs.size()
            || this.modelDocumentTabs.get(this.activeModelDocumentTab).home
            || this.data == null;

        this.showingHomePage = home;
        this.homePage.setVisible(home);
        this.mainView.setVisible(!home);
        this.iconBar.setVisible(!home);

        if (this.renderer != null)
        {
            this.renderer.setVisible(!home);
        }

        if (home)
        {
            this.editor.resetFlex().relative(this).w(1F).h(1F);
        }
        else
        {
            this.editor.resetFlex().relative(this).wTo(this.iconBar.area).h(1F);
        }
        this.resize();

        this.updateHomeButtonsState();
    }

    @Override
    public void fill(ModelConfig data)
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
    public void fillNames(Collection<String> names)
    {
        super.fillNames(names);

        DataPath selected = this.homeModelsList != null ? this.homeModelsList.getCurrentFirst() : null;
        String current = selected != null && !selected.folder ? selected.toString() : null;

        if (this.homeModelsList != null) {
            this.homeModelsList.fill(names);
            this.homeModelsList.setCurrentFile(current);
        }
        if (this.homeModelsMosaic != null) {
            this.homeModelsMosaic.fill(names, current);
        }
        this.updateHomeButtonsState();
    }

    @Override
    public void pickData(String id)
    {
        this.save();
        this.openModelInDocumentTabs(id);
        RecentAssetsTracker.add(this.getType(), id);
    }

    @Override
    protected boolean shouldOpenOverlayOnFirstResize()
    {
        return false;
    }

    private static class ModelDocumentTab
    {
        private boolean home;
        private String modelId;

        private ModelDocumentTab(boolean home, String modelId)
        {
            this.home = home;
            this.modelId = modelId;
        }
    }

    /**
     * A scrollable mosaic grid showing model preview cards with live 3D renderers.
     * Each card contains a UIModelEditorRenderer for the preview and the model name label.
     */
    public static class UIModelMosaicGrid extends UIScrollView
    {
        private static final int CARD_SIZE = 100;
        private static final int CARD_GAP = 6;
        private static final int CARD_LABEL_H = 16;

        private final Consumer<String> selectCallback;
        private final Consumer<String> doubleClickCallback;

        private final List<String> allModelIds = new ArrayList<>();
        private final List<String> modelIds = new ArrayList<>();
        private String selectedId;
        private String lastClickedId;
        private long lastClickTime;
        private int lastCols = -1;
        private boolean rebuilding = false;

        public UIModelMosaicGrid(Consumer<String> selectCallback, Consumer<String> doubleClickCallback)
        {
            super();
            this.selectCallback = selectCallback;
            this.doubleClickCallback = doubleClickCallback;
            this.scroll.scrollSpeed = 20;
        }

        public void fill(Collection<String> names, String selectedId)
        {
            this.allModelIds.clear();
            for (String name : names)
            {
                if (!name.endsWith("/"))
                {
                    this.allModelIds.add(name);
                }
            }
            this.selectedId = selectedId;
            this.lastCols = -1;
            
            /* Apply current filter (or empty) to populate modelIds and rebuild */
            this.filter("");
        }

        public void filter(String query)
        {
            this.modelIds.clear();
            String lowerQuery = query == null ? "" : query.toLowerCase();
            
            for (String id : this.allModelIds)
            {
                if (id.toLowerCase().contains(lowerQuery))
                {
                    this.modelIds.add(id);
                }
            }
            
            this.buildCards();
            
            if (this.hasParent())
            {
                this.resize();
            }
        }

        private void buildCards()
        {
            this.removeAll();
            if (this.modelIds.isEmpty()) return;

            int effectiveW = this.area.w > 0 ? this.area.w : 500;
            int cols = Math.max(1, (effectiveW - CARD_GAP) / (CARD_SIZE + CARD_GAP));

            for (int i = 0; i < this.modelIds.size(); i++)
            {
                final String id = this.modelIds.get(i);
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
                            UIModelMosaicGrid.this.onCardClicked(id);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void render(UIContext context)
                    {
                        boolean selected = id.equals(UIModelMosaicGrid.this.selectedId);
                        int border = selected ? BBSSettings.accentRgb() : Colors.setA(Colors.WHITE, 0.1F);
                        int bg = selected ? Colors.setA(BBSSettings.accentRgb(), 0.1F) : Colors.setA(0, 0.2F);
                        
                        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), bg);
                        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);

                        super.render(context);

                        String label = new DataPath(id).getLast();
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

                UIModelPreviewRenderer renderer = new UIModelPreviewRenderer();
                renderer.relative(card).x(0).y(0).w(CARD_SIZE).h(CARD_SIZE);
                renderer.setModel(id);

                card.add(renderer);
                this.add(card);
            }

            int rows = (this.modelIds.size() + cols - 1) / cols;
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
            int effectiveW = this.area.w > 0 ? this.area.w : 500;
            int cols = Math.max(1, (effectiveW - CARD_GAP) / (CARD_SIZE + CARD_GAP));
            if (!this.modelIds.isEmpty() && !this.rebuilding)
            {
                if (cols != this.lastCols)
                {
                    this.lastCols = cols;
                    this.rebuilding = true;
                    this.buildCards();
                    this.rebuilding = false;
                }

                int rows = (this.modelIds.size() + cols - 1) / cols;
                int totalH = CARD_GAP + rows * (CARD_SIZE + CARD_LABEL_H + CARD_GAP);
                this.scroll.scrollSize = totalH;
            }
            super.resize();
        }
    }

    private UIElement createUnavailablePanel()
    {
        UIElement panel = new UIElement();
        panel.relative(this.mainView).w(1F).h(1F);
        
        UILabel label = new UILabel(UIKeys.COMING_SOON)
        {
            @Override
            public void render(UIContext context)
            {
                context.batcher.getContext().getMatrices().push();
                
                int cx = this.area.mx();
                int cy = this.area.my();
                
                context.batcher.getContext().getMatrices().translate(cx, cy, 0);
                context.batcher.getContext().getMatrices().scale(2F, 2F, 1F);
                context.batcher.getContext().getMatrices().translate(-cx, -cy, 0);
                
                super.render(context);
                
                context.batcher.getContext().getMatrices().pop();
            }
        }.background();
        
        label.relative(panel).w(1F).xy(0.5F, 0.5F).anchor(0.5F, 0.5F);
        label.labelAnchor(0.5F, 0.5F);
        panel.add(label);
        
        return panel;
    }



    public UIIcon registerPanel(UIElement panel, IKey tooltip, Icon icon)
    {
        UIIcon button = new UIIcon(icon, (b) -> this.setPanel(panel));

        if (tooltip != null)
        {
            button.tooltip(tooltip, Direction.LEFT);
        }

        this.panels.add(panel);
        this.panelButtons.add(button);
        this.iconBar.add(button);

        return button;
    }

    public void setPanel(UIElement panel)
    {
        if (this.geometryPanel != null && panel != this.geometryPanel)
        {
            this.geometryPanel.onPanelClosed();
        }

        this.mainView.removeAll();
        this.mainView.add(panel);
        this.resetEditorScrolls();
        this.rightView.removeAll();

        if (panel == this.modelSettingsPanel)
        {
            this.setRight(this.getPoseEditor());
            this.renderer.transform = this.getPoseEditor().transform;
        }
        else if (panel == this.geometryPanel)
        {
            this.renderer.transform = this.geometryPanel.getGizmoTransformEditor();
            this.geometryPanel.onPanelOpened();
        }
        else if (panel == this.ikPanel)
        {
            /* No special gizmo for IK panel in v1 */
        }

        this.mainView.resize();
    }
    
    public void setRight(UIElement element)
    {
        this.rightView.removeAll();

        if (element != null && element.getParent() != null && element.getParent() != this.rightView)
        {
            element.removeFromParent();
        }

        this.rightView.add(element);
        this.rightView.scroll.setScroll(0);
        this.rightView.resize();
    }

    private void resetEditorScrolls()
    {
        this.sectionsView.scroll.dragging = false;
        this.rightView.scroll.dragging = false;
        this.sectionsView.scroll.setScroll(0);
        this.rightView.scroll.setScroll(0);
    }
    
    @Override
    public void save()
    {
        boolean hasData = this.data != null;
        boolean editorEnabled = this.editor != null && this.editor.isEnabled();

        LOGGER.debug("Model Editor save requested: hasData={}, update={}, editorEnabled={}", hasData, this.update, editorEnabled);

        if (!hasData)
        {
            LOGGER.warn("Model Editor save skipped: no model is selected");
            return;
        }

        if (!editorEnabled)
        {
            LOGGER.warn("Model Editor save skipped: editor is disabled for model {}", this.data.getId());
            return;
        }

        if (this.update)
        {
            LOGGER.warn("Model Editor save requested while update flag is true for model {}. Forcing save anyway", this.data.getId());
        }

        this.forceSave();
    }

    @Override
    public void forceSave()
    {
        if (this.data == null)
        {
            LOGGER.warn("Model Editor forceSave skipped: no model data");
            return;
        }

        if (!this.prepareAnimationCode())
        {
            return;
        }

        LOGGER.debug("Model Editor forceSave start: model={}", this.data.getId());

        for (UIModelSection section : this.sections)
        {
            section.setConfig(this.data);
        }

        try
        {
            super.forceSave();
        }
        catch (Exception e)
        {
            LOGGER.error("Model Editor forceSave failed during repository save for model {}", this.data.getId(), e);
            return;
        }

        if (this.data == null)
        {
            return;
        }

        if (this.geometryPanel != null)
        {
            this.geometryPanel.setConfig(this.data);
        }

        this.sectionsView.resize();
        this.rightView.resize();

        Morph morph = Morph.getMorph(MinecraftClient.getInstance().player);

        if (morph != null)
        {
            Form form = morph.getForm();

            if (form instanceof ModelForm && ((ModelForm) form).model.get().equals(this.data.getId()))
            {
                FormRenderer renderer = FormUtilsClient.getRenderer(form);

                if (renderer instanceof ModelFormRenderer)
                {
                    ((ModelFormRenderer) renderer).invalidateCachedModel();
                }
            }
        }

        if (this.renderer != null)
        {
            this.renderer.invalidatePreviewModel();
        }

        LOGGER.debug("Model Editor forceSave completed: model={}", this.data.getId());
        this.setSaveDirty(false);
    }

    public void persistModelDataWithoutReload()
    {
        if (this.data == null)
        {
            LOGGER.warn("Model Editor persist without reload skipped: no model data");
            return;
        }

        if (!this.prepareAnimationCode())
        {
            return;
        }

        LOGGER.debug("Model Editor persist without reload start: model={}", this.data.getId());

        try
        {
            super.forceSave();
        }
        catch (Exception e)
        {
            LOGGER.error("Model Editor persist without reload failed for model {}", this.data.getId(), e);
            return;
        }

        Morph morph = Morph.getMorph(MinecraftClient.getInstance().player);

        if (morph != null)
        {
            Form form = morph.getForm();

            if (form instanceof ModelForm && ((ModelForm) form).model.get().equals(this.data.getId()))
            {
                FormRenderer renderer = FormUtilsClient.getRenderer(form);

                if (renderer instanceof ModelFormRenderer)
                {
                    ((ModelFormRenderer) renderer).invalidateCachedModel();
                }
            }
        }

        LOGGER.debug("Model Editor persist without reload completed: model={}", this.data.getId());
        this.setSaveDirty(false);
    }

    private boolean prepareAnimationCode()
    {
        if (this.data == null)
        {
            return false;
        }

        ActionsConfig actions = this.data.animations.get();

        if (actions == null)
        {
            return true;
        }

        GeckoAnimationsConfig geckoSanitized = GECKO_VALIDATOR.sanitize(actions.geckoAnimations);
        ModelInstance preview = this.renderer == null ? null : this.renderer.getPreviewModelInstance();
        Set<String> bones = new HashSet<>();
        Set<String> animations = new HashSet<>();

        if (preview != null && preview.model != null)
        {
            preview.model.getAllGroups().forEach((group) -> bones.add(group.id));
            preview.model.getAllBOBJBones().forEach((bone) -> bones.add(bone.name));
        }

        if (preview != null && preview.animations != null)
        {
            animations.addAll(preview.animations.animations.keySet());
        }

        List<String> geckoValidationErrors = GECKO_VALIDATOR.validate(geckoSanitized, bones, animations);

        if (!geckoValidationErrors.isEmpty())
        {
            LOGGER.error("Model Editor save blocked by invalid gecko animation config for model {}: {}", this.data.getId(), String.join("; ", geckoValidationErrors));
            return false;
        }

        actions.geckoAnimations.copy(geckoSanitized);
        actions.geckoAnimationsJavascript = "var geckoAnimations = { enabled: " + geckoSanitized.enabled + " };";

        return true;
    }

    public UIPoseEditor getPoseEditor()
    {
        for (UIModelSection section : this.sections)
        {
            if (section instanceof UIModelPartsSection)
            {
                return ((UIModelPartsSection) section).poseEditor;
            }
        }

        return null;
    }

    private void pickBone(String bone)
    {
        /* UIModelPartsSection.selectBone() -> UIPoseEditor.selectBone() -> pickCallback ->
           setSelectedBone() re-enters this method with the same bone, which would otherwise
           recurse forever and overflow the stack. */
        if (this.pickingBone)
        {
            return;
        }

        this.pickingBone = true;

        try
        {
            this.renderer.setSelectedBone(bone);

            for (UIModelSection section : this.sections)
            {
                section.deselect();
                section.onBoneSelected(bone);

                if (section instanceof UIModelPartsSection)
                {
                    ((UIModelPartsSection) section).selectBone(bone);
                    this.setRight(((UIModelPartsSection) section).poseEditor);
                }
            }

            if (this.ikPanel.hasParent())
            {
                this.ikPanel.onBoneSelected(bone);
            }

            if (this.physBonesPanel.hasParent())
            {
                this.physBonesPanel.onBoneSelected(bone);
            }

            if (this.constraintsPanel.hasParent())
            {
                this.constraintsPanel.onBoneSelected(bone);
            }

            if (this.geometryPanel != null && this.geometryPanel.hasParent())
            {
                this.geometryPanel.selectBone(bone);
            }
        }
        finally
        {
            this.pickingBone = false;
        }
    }

    private void pickCube(UIModelEditorRenderer.PickedCube pickedCube)
    {
        if (this.geometryPanel != null && this.geometryPanel.hasParent() && pickedCube != null)
        {
            this.geometryPanel.selectCube(pickedCube.groupId, pickedCube.cubeIndex);
        }
        else if (pickedCube != null)
        {
            this.pickBone(pickedCube.groupId);
        }
    }
    
    public void dirty()
    {
        this.renderer.dirty();
        this.setSaveDirty(true);
    }

    private void setSaveDirty(boolean dirty)
    {
        if (this.saveIcon != null)
        {
            this.saveIcon.both(dirty ? Icons.SAVE : Icons.SAVED);
        }
    }

    private void addSection(UIModelSection section)
    {
        this.sections.add(section);
        this.sectionsView.add(section);
    }

    @Override
    public ContentType getType()
    {
        return ContentType.MODELS;
    }

    @Override
    public UIDashboardPanel getMainPanel()
    {
        UIHomePanel home = this.dashboard.getPanel(UIHomePanel.class);

        return home != null ? home : this;
    }

    @Override
    protected IKey getTitle()
    {
        return UIKeys.MODELS_TITLE;
    }

    @Override
    protected void fillData(ModelConfig data)
    {
        this.setSaveDirty(false);

        for (UIIcon button : this.panelButtons)
        {
            button.setEnabled(data != null);
        }

        if (data != null)
        {
            this.renderer.setModel(data.getId());
            this.renderer.setConfig(data);
            
            for (UIModelSection section : this.sections)
            {
                section.setConfig(data);
            }

            if (this.geometryPanel != null)
            {
                this.geometryPanel.setConfig(data);
            }

            if (this.ikPanel != null)
            {
                this.ikPanel.setConfig(data);
            }

            if (this.physBonesPanel != null)
            {
                this.physBonesPanel.setConfig(data);
            }

            if (this.constraintsPanel != null)
            {
                this.constraintsPanel.setConfig(data);
            }

            this.sectionsView.resize();
            this.rightView.resize();
            this.resetEditorScrolls();
        }
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);
    }

    @Override
    public void resize()
    {
        super.resize();
        this.renderer.resize();
    }

    @Override
    protected boolean canSave(UIContext context)
    {
        return false;
    }

    @Override
    public void close()
    {}

    @Override
    public UIElement getMainView()
    {
        return this.mainView;
    }

    @Override
    public UIModelEditorRenderer getModelRenderer()
    {
        return this.renderer;
    }

    @Override
    public void setWorkspacePanel(UIElement panel)
    {
        this.setPanel(panel);
    }

    @Override
    public ModelConfig getModelConfig()
    {
        return this.data;
    }

    @Override
    public void openTransformEditor(UIDashboardPanel panel)
    {
        this.dashboard.getPanels().setPanel(panel);
    }

    @Override
    public void returnFromSubEditor()
    {
        this.renderer.dirty();
        this.dashboard.getPanels().setPanel(this);
    }

    @Override
    public List<UIModelSection> getSections()
    {
        return this.sections;
    }

    @Override
    public UIDashboard getDashboard()
    {
        return this.dashboard;
    }

    @Override
    public UIModelPanel getModelPanel()
    {
        return this;
    }

    @Override
    public String getSelectedBone()
    {
        return this.renderer.getSelectedBone();
    }

    @Override
    public void setSelectedBone(String bone)
    {
        this.pickBone(bone);
    }
}
