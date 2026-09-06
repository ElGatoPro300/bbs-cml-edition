package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBS;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.ActionPlayer;
import mchorse.bbs_mod.actions.ActionState;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.misc.VideoClip;
import mchorse.bbs_mod.camera.clips.modifiers.TranslateClip;
import mchorse.bbs_mod.camera.clips.overwrite.IdleClip;
import mchorse.bbs_mod.camera.controller.CameraController;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.CrossWorldFilmLoader;
import mchorse.bbs_mod.client.CrossWorldFilmScanner;
import mchorse.bbs_mod.client.FilmLaunchHelper;
import mchorse.bbs_mod.client.WorldLaunchHelper;
import mchorse.bbs_mod.client.compat.HdrModCompat;
import mchorse.bbs_mod.client.renderer.MorphRenderer;
import mchorse.bbs_mod.client.video.VideoRenderer;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.events.register.RegisterFilmEditorFactoriesEvent;
import mchorse.bbs_mod.events.register.RegisterFilmSyncEvent;
import mchorse.bbs_mod.film.CrossWorldFilmEntry;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmContributor;
import mchorse.bbs_mod.film.Films;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.film.RecordingPauseHelper;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.URLSourcePack;
import mchorse.bbs_mod.settings.Settings;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.settings.values.ui.ValueUILayoutPreferences;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.UIPanelSwitcher;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.dashboard.panels.IFlightSupported;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.dashboard.panels.UIDataDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UICRUDOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIDataOverlayPanel;
import mchorse.bbs_mod.ui.dashboard.utils.IUIOrbitKeysHandler;
import mchorse.bbs_mod.ui.film.audio.UIAudioRecorder;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.film.clips.UIKeyframeClip;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.film.replays.overlays.UIReplayPropertiesPanel;
import mchorse.bbs_mod.ui.film.replays.overlays.UIReplaysOverlayPanel;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbar;
import mchorse.bbs_mod.ui.film.toolbar.TimelineToolbarDockSync;
import mchorse.bbs_mod.ui.film.utils.UIFilmUndoHandler;
import mchorse.bbs_mod.ui.film.utils.undo.UIUndoHistoryOverlay;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.context.UISimpleContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UINumberOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.EventPropagation;
import mchorse.bbs_mod.ui.framework.elements.utils.UIDraggable;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.home.UIHomePanel;
import mchorse.bbs_mod.ui.utility.UIUtilityOverlayPanel;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.UIDataUtils;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.presets.UICopyPasteController;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.RecentAssetsTracker;
import mchorse.bbs_mod.utils.Timer;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.iris.IrisUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.presets.PresetManager;
import mchorse.bbs_mod.utils.resources.Pixels;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;

import org.joml.Matrix4f;
import org.joml.Vector2i;
import org.joml.Vector3d;

import com.mojang.blaze3d.systems.RenderSystem;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class UIFilmPanel extends UIDataDashboardPanel<Film> implements IFlightSupported, IUIOrbitKeysHandler, ICursor
{
    private static boolean hasSyncedShaders;
    private RunnerCameraController runner;
    private boolean lastRunning;
    private boolean clearingSelections;
    /* Actor toggle rebuild remounts keyframe factories; must not steal the active tab
     * away from Replays / General when they share a group with Properties. */
    private int suppressLinkedPropertiesTabFocus;
    private int lastFilledCursor = -1;
    private final Position position = new Position(0, 0, 0, 0, 0);
    private final Position lastPosition = new Position(0, 0, 0, 0, 0);

    public UIElement editArea;
    public UIElement cameraEditArea;
    public UIElement actionEditArea;
    public UIElement unifiedEditArea;
    public UIDraggable draggableMain;
    public UIDraggable draggableEditor;
    public UIFilmRecorder recorder;
    public UIFilmPreview preview;
    private final List<UIDraggable> splitterHandles = new ArrayList<>();
    private final List<EditorLayoutNode.SplitterHandleInfo> splitterHandleInfos = new ArrayList<>();
    private final List<EditorLayoutNode.SplitterNode> splitterHandleTargets = new ArrayList<>();

    public UIIcon duplicateFilm;

    /* Main editors */
    public UIClipsPanel cameraEditor;
    public UIReplaysEditor replayEditor;
    public UIClipsPanel actionEditor;
    public UIReplaysOverlayPanel anchoredReplaysPanel;
    public UIReplayPropertiesPanel anchoredReplaysPropertiesPanel;
    private final Map<VideoClip, UIVideoPanel> videoPanelsByClip = new LinkedHashMap<>();
    private final Map<String, VideoClip> videoClipsByPanelId = new HashMap<>();

    /* Icon bar buttons */
    public UIIcon toggleHorizontal;
    public UIIcon layoutLock;
    public UIIcon layoutPresets;
    private UIWorkspaceTabBar workspaceTabs;
    public UIElement bottomIcons;
    private UIFilmStatusIcons statusIcons;
    private UICopyPasteController layoutPresetsController;
    /* Film-specific operations shown under the menu bar's Tools menu (moved off the old save icon). */
    private Consumer<ContextMenuManager> toolMenuActions;

    private Camera camera = new Camera();
    private boolean entered;
    private boolean resetFreeFlightLookDrag;
    private boolean freeFlightLookPrimed;
    private int freeFlightLookRawX;
    private int freeFlightLookRawY;

    /* Entity control */
    private boolean performingLayout;
    private UIFilmController controller;
    private UIFilmUndoHandler undoHandler;

    public final Matrix4f lastView = new Matrix4f();
    public final Matrix4f lastProjection = new Matrix4f();
    public final Matrix4f lastGizmoMatrix = new Matrix4f();
    public boolean hasLastGizmoMatrix;

    private Timer flightEditTime = new Timer(100);

    private List<UIElement> panels = new ArrayList<>();
    private int currentPanelIndex;
    private UIFilmFullscreenPlaybackBar fullscreenPlaybackBar;

    private boolean newFilm;
    private final Map<String, UIElement> panelById = new LinkedHashMap<>();
    private final Map<String, UIDraggable> dragHandlesById = new LinkedHashMap<>();
    private final Set<String> floatingPanels = new HashSet<>();
    private final Set<String> collapsedFloatingPanels = new HashSet<>();
    private final Set<String> collapsedDockedPanels = new HashSet<>();
    private final Set<String> hiddenPanels = new HashSet<>();
    private final Map<String, Vector2i> floatingPanelPositions = new HashMap<>();
    private final Map<String, Vector2i> floatingPanelSizes = new HashMap<>();
    private String activeDraggingFloatingPanelId = null;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private String activeResizingFloatingPanelId = null;
    private final List<Runnable> postUpdateActions = new ArrayList<>();
    private int lastDragMouseX;
    private int lastDragMouseY;
    private boolean tabReordering;
    private String tabReorderPanelId;
    private int tabReorderFromIndex;
    private int tabReorderDropPreview = -1;
    private int tabReorderGapX;
    private int tabReorderGapW;
    private EditorLayoutNode.TabbedNode tabReorderTabbedNode;
    private UITabBar tabReorderTabBar;
    private static final int HOME_BANNER_HEIGHT = 108;
    private static final float DRAG_HANDLE_HEIGHT_NORM = 0.02F;
    private static final float DRAG_HANDLE_TOP_OFFSET_NORM = 0.01F;
    private static final int PANEL_HEADER_HEIGHT = 18;
    /* Height of the Blockbench-style workspace tab bar (Camera / Replay / Action / Screen). */
    private static final int WORKSPACE_TAB_BAR_HEIGHT = 18;
    /* Extra hit area around a tab bar while dragging: stay in reorder mode inside this pad,
     * and only tear the panel out once the cursor leaves (~1 cm at typical GUI scale). */
    private static final int TAB_REORDER_DETACH_PADDING = 28;
    /* Minimum footprint of a floating window and the size of its bottom-right resize grip. */
    private static final int MIN_FLOATING_PANEL_WIDTH = 180;
    private static final int MIN_FLOATING_PANEL_HEIGHT = 100;
    private static final int FLOATING_RESIZE_HANDLE = 8;
    private final Set<String> dockedHeaderPanels = new HashSet<>();
    private static final int SPLITTER_HANDLE_PX = 6;
    private static final int DROP_ZONE_CENTER = -1;
    public static final int DROP_ZONE_TAB = 4;
    private static final String DROP_TARGET_WORKSPACE = "__workspace__";
    private static final float DROP_EDGE_MARGIN = 0.2F;
    private static final int EDITOR_MIN_SIZE_FOR_PX_HANDLES = 10;
    private static final String ANCHORED_REPLAYS_PANEL_ID = "replaysPanel";
    private static final String ANCHORED_REPLAYS_PROPERTIES_PANEL_ID = "replaysPropertiesPanel";
    public static final String VIDEO_PANEL_ID = "videoPanel";
    private static final String PRESET_REPLAYS_PANEL_ENABLED = "replays_panel_enabled";
    private static final String PRESET_REPLAYS_PANEL_FLOATING = "replays_panel_floating";
    private static final String PRESET_REPLAYS_PANEL_X = "replays_panel_x";
    private static final String PRESET_REPLAYS_PANEL_Y = "replays_panel_y";
    private static final String PRESET_REPLAYS_PANEL_WIDTH = "replays_panel_width";
    private static final String PRESET_REPLAYS_PANEL_HEIGHT = "replays_panel_height";
    private static final String PRESET_REPLAYS_PANEL_DOCKED_LAYOUT = "replays_panel_docked_layout";
    private static final String PRESET_HIDDEN_PANELS = "hidden_panels";
    private static final String[] LEGACY_LAYOUT_PRESETS = {
        "Horizontal (Bottom)",
        "Horizontal (Top)",
        "Vertical (Left)",
        "Vertical (Middle)",
        "Vertical (Right)"
    };
    private static final String[][] DEFAULT_LAYOUT_PRESETS = {
        {"Premiere Style", "premiere_style"},
        {"Horizontal (simplified)", "horizontal_simplified"},
        {"Vertical (simplified)", "vertical_simplified"}
    };
    private String draggingPanelId;
    private String dropTargetPanelId;
    private int dropTargetZone = DROP_ZONE_CENTER;
    public String mouseHeldPanelId;
    public int clickX, clickY;
    private UIElement homePage;
    private UISearchList<DataPath> homeFilmsSearch;
    private static final String PARENT_FOLDER_ENTRY = "..";

    private UIDataPathList homeFilmsList;
    private UIFilmMosaicGrid homeFilmsMosaic;
    private UIIcon homeViewToggle;
    private UIPanelSwitcher panelSwitcher;
    private UIElement homeActionsPanel;
    private UIButton homeCreateFilm;
    private UIButton homeOpenManager;
    private UIButton homeDuplicateCurrent;
    private UIButton homeRenameCurrent;
    private UIButton homeDeleteCurrent;
    private String homeLastClickedFilmId;
    private long homeLastClickTime;
    private final Map<String, CrossWorldFilmEntry> crossWorldFilmEntries = new HashMap<>();
    private final Map<String, String> crossWorldWorldLabels = new HashMap<>();
    private CrossWorldFilmEntry crossWorldPendingJoin;
    private String loadedFilmTabKey;
    private boolean crossWorldScanning;
    private final List<FilmDocumentTab> filmDocumentTabs = new ArrayList<>();
    private int activeFilmDocumentTab = -1;
    private boolean showingHomePage = true;

    private boolean shouldCaptureThumbnail;
    private int lastViewportRenderW = -1;
    private int lastViewportRenderH = -1;
    private final Map<String, Texture> thumbnails = new HashMap<>();
    private final Set<String> missingThumbnailIds = new HashSet<>();

    /* View mode is persisted globally — see BBSSettings.lastViewMosaic. */
    private static boolean lastShowingHomePage = true;

    /**
     * Initialize the camera editor with a camera profile.
     */

    public UIFilmPanel(UIDashboard dashboard)
    {
        super(dashboard);

        RegisterFilmEditorFactoriesEvent event = new RegisterFilmEditorFactoriesEvent();
        BBS.getEvents().post(event);

        this.controller = event.createController(this);

        this.runner = new RunnerCameraController(this, (playing) ->
        {
            this.notifyServer(playing ? ActionState.PLAY : ActionState.PAUSE);
        });
        this.runner.getContext().captureSnapshots();

        this.recorder = event.createRecorder(this);

        this.editArea = new UIElement();
        this.cameraEditArea = new UIElement();
        this.actionEditArea = new UIElement();
        this.unifiedEditArea = new UIElement()
        {
            @Override
            public void add(IUIElement element)
            {
                this.removeAll();
                super.add(element);
            }
        };
        this.preview = event.createPreview(this);

        this.draggableMain = new UIDraggable((context) ->
        {
            ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

            if (layout.isLayoutLocked())
            {
                return;
            }

            if (layout.isHorizontal())
            {
                if (layout.isMainOnTop())
                {
                    layout.setMainSizeH((context.mouseY - this.editor.area.y) / (float) this.editor.area.h);
                }
                else
                {
                    layout.setMainSizeH(1F - (context.mouseY - this.editor.area.y) / (float) this.editor.area.h);
                }

                float normalizedX = (context.mouseX - this.editor.area.x) / (float) this.editor.area.w;

                layout.setEditorSizeH(this.isHorizontalEditorOnLeft(layout) ? normalizedX : 1F - normalizedX);
            }
            else if (layout.isMiddleLayout())
            {
                layout.setMainSizeV((context.mouseX - this.editor.area.x) / (float) this.editor.area.w);
            }
            else
            {
                layout.setMainSizeV(this.calculateMainSizeVFromMouse(layout, context.mouseX));

                layout.setEditorSizeV((context.mouseY - this.editor.area.y) / (float) this.editor.area.h);
            }

            this.setupEditorFlex(true);
        });

        this.draggableMain.reference(() ->
        {
            return this.getMainHandlerReferencePosition();
        });
        this.draggableMain.rendering((context) ->
        {
            int size = 5;
            ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

            if (layout.isHorizontal())
            {
                int dividerX = this.getHorizontalDividerX(layout);
                int x = dividerX + 3;
                int y = (layout.isMainOnTop() ? this.editArea.area.y : this.editArea.area.ey()) - 3;

                context.batcher.box(x, y - size, x + 1, y, Colors.WHITE);
                context.batcher.box(x, y - 1, x + size, y, Colors.WHITE);

                x = dividerX - 3;
                y = (layout.isMainOnTop() ? this.editArea.area.y : this.editArea.area.ey()) - 3;

                context.batcher.box(x - 1, y - size, x, y, Colors.WHITE);
                context.batcher.box(x - size, y - 1, x, y, Colors.WHITE);
            }
            else
            {
                Vector2i position = this.getMainHandlerRenderPosition(layout);
                int x = position.x;
                int y = position.y;

                context.batcher.box(x, y - size, x + 1, y, Colors.WHITE);
                context.batcher.box(x, y - 1, x + size, y, Colors.WHITE);

                y += 1;

                context.batcher.box(x, y, x + 1, y + size, Colors.WHITE);
                context.batcher.box(x, y, x + size, y + 1, Colors.WHITE);
            }
        });

        this.draggableEditor = new UIDraggable((context) ->
        {
            ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

            if (layout.isLayoutLocked() || !layout.isMiddleLayout())
            {
                return;
            }

            float mainSize = layout.getMainSizeV();
            float editorSize = (context.mouseX - this.editor.area.x) / (float) this.editor.area.w - mainSize;

            layout.setEditorSizeH(editorSize);

            this.setupEditorFlex(true);
        });
        this.draggableEditor.reference(() ->
        {
            ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

            if (layout.isMiddleLayout())
            {
                return new Vector2i(this.cameraEditor.area.ex(), this.cameraEditor.area.my());
            }

            return new Vector2i(this.editArea.area.x, this.editArea.area.y);
        });
        this.draggableEditor.rendering((context) ->
        {
            int size = 5;
            Vector2i position = this.getEditorHandlerRenderPosition();
            int x = position.x;
            int y = position.y;

            context.batcher.box(x, y - size, x + 1, y, Colors.WHITE);
            context.batcher.box(x, y - 1, x + size, y, Colors.WHITE);

            y += 1;

            context.batcher.box(x, y, x + 1, y + size, Colors.WHITE);
            context.batcher.box(x, y, x + size, y + 1, Colors.WHITE);
        });

        /* Editors */
        this.cameraEditor = new UIClipsPanel(this, BBSMod.getFactoryCameraClips(), true);

        this.cameraEditor.clips.context((menu) ->
        {
            UIAudioRecorder.addOption(this, menu);
        });

        this.replayEditor = event.createReplayEditor(this);
        this.actionEditor = new UIClipsPanel(this, BBSMod.getFactoryActionClips(), false);
        this.anchoredReplaysPanel = new UIReplaysOverlayPanel(this, (replay) -> this.replayEditor.setReplay(replay, false, true));
        this.anchoredReplaysPanel.setDocked(true);
        this.anchoredReplaysPanel.setVisible(false);
        this.anchoredReplaysPropertiesPanel = new UIReplayPropertiesPanel(this.anchoredReplaysPanel);
        this.anchoredReplaysPropertiesPanel.setVisible(false);
        this.panelById.put("cameraTimeline", this.cameraEditor);
        this.panelById.put("replayTimeline", this.replayEditor);
        this.panelById.put("actionTimeline", this.actionEditor);
        this.panelById.put("preview", this.preview);
        this.panelById.put("editArea", this.editArea);
        this.panelById.put("cameraEditArea", this.cameraEditArea);
        this.panelById.put("actionEditArea", this.actionEditArea);
        this.panelById.put("unifiedEditArea", this.unifiedEditArea);
        this.panelById.put(ANCHORED_REPLAYS_PANEL_ID, this.anchoredReplaysPanel);
        this.panelById.put(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID, this.anchoredReplaysPropertiesPanel);

        /* Every window is an opaque hit target: a click inside a panel's area must
           never fall through to widgets of another panel stacked beneath it. */
        for (UIElement panelElement : this.panelById.values())
        {
            panelElement.mouseEventPropagataion(EventPropagation.BLOCK_INSIDE);
        }

        this.updateTargets();
        this.homePage = new UIElement()
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                UIFilmPanel.this.homeFilmsList.deselect();
                UIFilmPanel.this.handleHomeFilmsSelection(null);

                return super.subMouseClicked(context);
            }
        };
        this.homeActionsPanel = new UIElement();
        this.homeFilmsList = new UIDataPathList((list) -> this.handleHomeFilmsSelection(list))
        {
            @Override
            protected String elementToString(UIContext context, int i, DataPath element)
            {
                if (UIFilmPanel.this.isAllWorldsBrowseMode())
                {
                    if (element.folder && element.size() == 1 && !"..".equals(element.getLast()))
                    {
                        String label = UIFilmPanel.this.crossWorldWorldLabels.get(element.getLast());

                        if (label != null)
                        {
                            return label + "/";
                        }
                    }
                }

                return super.elementToString(context, i, element);
            }
        };
        this.homeFilmsList.setFileIcon(Icons.FILM);
        this.homeFilmsList.context((menu) ->
        {
            menu.action(Icons.FOLDER, UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE, this::addFolderFromHome);

            String selectedId = this.getSelectedHomeFilmId();
            if (selectedId != null)
            {
                menu.action(Icons.COPY, UIKeys.PANELS_CONTEXT_COPY, this::copyHomeFilm);
            }

            try
            {
                MapType clipboardData = Window.getClipboardMap("_ContentType_" + this.getType().getId());

                if (clipboardData != null)
                {
                    menu.action(Icons.PASTE, UIKeys.PANELS_CONTEXT_PASTE, () -> this.pasteHomeFilm(clipboardData));
                }
            }
            catch (Exception e)
            {}

            File folder = this.getType().getRepository().getFolder();

            if (folder != null)
            {
                menu.action(Icons.FOLDER, UIKeys.PANELS_CONTEXT_OPEN, () ->
                {
                    UIUtils.openFolder(new File(folder, this.homeFilmsList.getPath().toString()));
                });
            }
        });
        this.homeFilmsList.moveCallback = (from, to) ->
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

                for (FilmDocumentTab tab : this.filmDocumentTabs)
                {
                    if (!tab.home && fromStr.equals(tab.filmId))
                    {
                        tab.filmId = toStr;
                    }
                }
                this.rebuildFilmDocumentTabs();

                if (this.data != null && fromStr.equals(this.data.getId()))
                {
                    this.data.setId(toStr);
                }

                this.requestNames();
            }
        };
        this.homeFilmsSearch = new UISearchList<>(this.homeFilmsList).label(UIKeys.GENERAL_SEARCH);
        this.homeFilmsSearch.list.background();

        this.homeFilmsMosaic = new UIFilmMosaicGrid(
            this.homeFilmsList,
            this::getThumbnail,
            (path) -> path.getLast().equals("..") ? "../" : path.getLast(),
            (id) -> {
            DataPath path = this.homeFilmsMosaic.findPath(id);
            this.handleHomeFilmsSelection(Collections.singletonList(path != null ? path : new DataPath(id)));
        }, (id) -> {
            DataPath clickedPath = this.homeFilmsMosaic.findPath(id);
            if (clickedPath != null && clickedPath.folder) {
                if (clickedPath.getLast().equals("..")) {
                    this.homeFilmsList.goTo(this.homeFilmsList.getPath().getParent());
                } else {
                    this.homeFilmsList.goTo(clickedPath);
                }
                this.homeFilmsMosaic.filter("");
            } else {
                this.openFilmInDocumentTabs(id);
            }
        });
        boolean mosaic = BBSSettings.lastViewMosaic.get();

        this.homeFilmsMosaic.setVisible(mosaic);
        this.homeFilmsList.setVisible(!mosaic);

        Consumer<String> oldCallback = this.homeFilmsSearch.search.callback;
        this.homeFilmsSearch.search.callback = (str) -> {
            if (oldCallback != null) oldCallback.accept(str);
            this.homeFilmsMosaic.filter(str);
        };

        this.homeViewToggle = new UIIcon(mosaic ? Icons.LIST : Icons.GALLERY, (b) -> this.toggleMosaicView());
        this.homeViewToggle.tooltip(mosaic ? UIKeys.MODELS_HOME_VIEW_LIST : UIKeys.MODELS_HOME_VIEW_MOSAIC, Direction.LEFT);
        this.homeCreateFilm = this.createHomeButton(UIKeys.FILM_CRUD_ADD, Icons.ADD, (b) ->
        {
            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_ADD,
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
        this.homeDuplicateCurrent = this.createHomeButton(UIKeys.FILM_CRUD_DUPE, Icons.COPY, (b) ->
        {
            String selectedId = this.getSelectedHomeFilmId();
            if (selectedId == null) return;

            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_DUPE,
                UIKeys.PANELS_MODALS_DUPE,
                (str) -> {
                    String targetId = this.homeFilmsList.getPath(str).toString();
                    if (targetId.trim().isEmpty()) {
                        this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
                        return;
                    }
                    if (this.homeFilmsList.hasInHierarchy(targetId)) {
                        return;
                    }
                    this.getType().getRepository().load(selectedId, (originalFilm) -> {
                        if (originalFilm != null) {
                            this.getType().getRepository().save(targetId, originalFilm.toData().asMap());
                            this.requestNames();
                        }
                    });
                }
            );

            panel.text.setText(new DataPath(selectedId).getLast());
            panel.text.filename();

            UIOverlay.addOverlay(this.getContext(), panel);
        });
        this.homeRenameCurrent = this.createHomeButton(UIKeys.FILM_CRUD_RENAME, Icons.EDIT, (b) ->
        {
            String selectedId = this.getSelectedHomeFilmId();
            if (selectedId == null) return;

            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_RENAME,
                UIKeys.PANELS_MODALS_RENAME,
                (str) -> {
                    String targetId = this.homeFilmsList.getPath(str).toString();
                    if (targetId.trim().isEmpty()) {
                        this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
                        return;
                    }
                    if (this.homeFilmsList.hasInHierarchy(targetId)) {
                        return;
                    }
                    this.getType().getRepository().rename(selectedId, targetId);

                    for (FilmDocumentTab tab : this.filmDocumentTabs) {
                        if (!tab.home && selectedId.equals(tab.filmId)) {
                            tab.filmId = targetId;
                        }
                    }
                    this.rebuildFilmDocumentTabs();

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
        this.homeDeleteCurrent = this.createHomeButton(UIKeys.FILM_CRUD_REMOVE, Icons.REMOVE, (b) ->
        {
            String selectedId = this.getSelectedHomeFilmId();
            if (selectedId == null) return;

            UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
                UIKeys.GENERAL_REMOVE,
                UIKeys.PANELS_MODALS_REMOVE,
                (confirm) ->
                {
                    if (confirm) {
                        this.getType().getRepository().delete(selectedId);

                        for (int i = this.filmDocumentTabs.size() - 1; i >= 0; i--) {
                            FilmDocumentTab tab = this.filmDocumentTabs.get(i);
                            if (!tab.home && selectedId.equals(tab.filmId)) {
                                this.removeFilmDocumentTab(i);
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

        /* Icon bar buttons. The undo/redo history lives in the menu bar's Edit menu, the save
           action and film file tools live in its File menu, and the render queue is a viewport
           button — so none of them are added to this side bar anymore. */
        this.openOverlay.removeFromParent();
        this.saveIcon.removeFromParent();

        this.statusIcons = new UIFilmStatusIcons(this);
        this.statusIcons.setVisible(false);

        if (this.dashboard != null && this.dashboard.documentTabsBar != null)
        {
            this.dashboard.documentTabsBar.attachFilmStatusIcons(this.statusIcons);
        }

        this.toggleHorizontal = new UIIcon(this::getLayoutIcon, (b) -> this.openLayoutSelector())
        {
            @Override
            public boolean subMouseClicked(UIContext context)
            {
                if (context.mouseButton == 1 && this.area.isInside(context))
                {
                    UIFilmPanel.this.invertSelectedLayout();

                    return true;
                }

                return super.subMouseClicked(context);
            }
        };
        this.toggleHorizontal.tooltip(UIKeys.FILM_TOGGLE_LAYOUT, Direction.LEFT);
        this.layoutLock = new UIIcon(this::getLayoutLockIcon, (b) -> this.toggleLayoutLock());
        this.updateLayoutLockTooltip();
        this.workspaceTabs = new UIWorkspaceTabBar();
        this.layoutPresetsController = new UICopyPasteController(PresetManager.LAYOUTS, "_CopyFilmLayout")
            .supplier(this::getFilmLayoutPresetData)
            .consumer(this::applyFilmLayoutFromPreset);
        this.layoutPresets = new UIIcon(Icons.SAVED, (b) ->
        {
            UIContext ctx = this.getContext();
            this.ensureDefaultLayoutPresets();
            this.layoutPresetsController.openPresets(ctx, ctx.mouseX, ctx.mouseY);
        });
        this.layoutPresets.tooltip(UIKeys.FILM_LAYOUT_PRESETS, Direction.LEFT);

        /* Setup elements. The editor switches (camera/replay/action/screen) now live in the
           workspace tab bar at the top instead of this side icon bar. */
        this.workspaceTabs.relative(this).x(0).y(0).w(1F).h(WORKSPACE_TAB_BAR_HEIGHT);
        this.workspaceTabs.setVisible(false);

        this.bottomIcons = new UIElement();
        this.bottomIcons.row(0);
        this.bottomIcons.add(this.toggleHorizontal, this.layoutLock, this.layoutPresets);
        this.bottomIcons.relative(this).x(1F, -8).y(1F, -28).anchor(1F, 1F).h(20);
        this.bottomIcons.setVisible(false);
        this.iconBar.relative(this).x(1F, -20).y(0).w(20).h(1F).column(0).stretch();
        this.homePage.relative(this.editor).x(0.5F, -250).y(0).w(500).h(1F);

        this.panelSwitcher = new UIPanelSwitcher(this.dashboard);
        this.panelSwitcher.relative(this.homePage).x(0.5F, -87).y(1F, -32).w(175).h(24);

        UIElement spacing = new UIElement();
        spacing.h(8);

        this.homeActionsPanel.relative(this.homePage).x(0).y(HOME_BANNER_HEIGHT + 20).w(0.35F).h(1F, -(HOME_BANNER_HEIGHT + 20 + 10)).column(0).vertical().stretch();
        this.homeActionsPanel.add(this.homeCreateFilm, spacing, this.homeDuplicateCurrent, this.homeRenameCurrent, this.homeDeleteCurrent);
        this.homeFilmsSearch.relative(this.homePage).x(0.35F).y(HOME_BANNER_HEIGHT + 20).w(0.65F).h(1F, -(HOME_BANNER_HEIGHT + 20 + 10));
        this.homeFilmsSearch.search.w(1F, -25);
        this.homeFilmsMosaic.relative(this.homeFilmsSearch).x(0).y(20).w(1F).h(1F, -20);
        this.homeViewToggle.relative(this.homeFilmsSearch).x(1F, -22).y(0).w(20).h(20);
        this.homePage.add(new UIRenderable(this::renderHomeBanner), this.homeActionsPanel, this.homeFilmsSearch, this.homeFilmsMosaic, this.homeViewToggle, this.panelSwitcher);

        this.editor.add(this.cameraEditor, this.replayEditor, this.actionEditor, this.editArea, this.cameraEditArea, this.actionEditArea, this.unifiedEditArea, this.preview, this.anchoredReplaysPanel, this.anchoredReplaysPropertiesPanel, this.homePage, new UIRenderable(this::renderDockedPanelHeaders), new UIRenderable(this::renderIcons), new UIRenderable(this::renderDropZoneHighlight), new UIRenderable(this::renderFloatingPanelWindows));
        for (String id : this.panelById.keySet())
        {
            UIDraggable handle = this.createPanelDragHandle(id);
            this.dragHandlesById.put(id, handle);
            this.editor.add(handle);
        }
        this.editor.add(this.draggableMain, this.draggableEditor);
        this.add(this.controller, this.bottomIcons, this.workspaceTabs);
        this.overlay.namesList.setFileIcon(Icons.FILM);
        this.createHomeDocumentTab(true);

        /* Register keybinds */
        IKey modes = UIKeys.CAMERA_EDITOR_KEYS_MODES_TITLE;
        IKey editor = UIKeys.CAMERA_EDITOR_KEYS_EDITOR_TITLE;
        IKey looping = UIKeys.CAMERA_EDITOR_KEYS_LOOPING_TITLE;
        Supplier<Boolean> active = () -> this.data != null && !this.isFlying();

        this.keys().register(Keys.PLAUSE, () -> this.preview.plause.clickItself()).active(active).category(editor);
        this.keys().register(Keys.NEXT_CLIP, () -> this.setCursor(this.data.camera.findNextTick(this.getCursor()))).active(active).category(editor);
        this.keys().register(Keys.PREV_CLIP, () -> this.setCursor(this.data.camera.findPreviousTick(this.getCursor()))).active(active).category(editor);
        this.keys().register(Keys.NEXT, () -> this.setCursor(this.getCursor() + 1)).active(active).category(editor);
        this.keys().register(Keys.PREV, () -> this.setCursor(this.getCursor() - 1)).active(active).category(editor);
        this.keys().register(Keys.UNDO, this::undo).active(() -> this.data != null && !this.undoHandler.isFilmRecording()).category(editor);
        this.keys().register(Keys.REDO, this::redo).active(() -> this.data != null && !this.undoHandler.isFilmRecording()).category(editor);
        this.keys().register(Keys.FLIGHT, this::toggleFlight).active(() -> this.data != null).category(modes);
        this.keys().register(Keys.LOOPING, () ->
        {
            BBSSettings.editorLoop.set(!BBSSettings.editorLoop.get());
            UIUtils.playClick();
        }).active(active).category(looping);
        this.keys().register(Keys.LOOPING_SET_MIN, () -> this.cameraEditor.clips.setLoopMin()).active(active).category(looping);
        this.keys().register(Keys.LOOPING_SET_MAX, () -> this.cameraEditor.clips.setLoopMax()).active(active).category(looping);
        this.keys().register(Keys.JUMP_FORWARD, () -> this.setCursor(this.getCursor() + BBSSettings.editorJump.get())).active(active).category(editor);
        this.keys().register(Keys.JUMP_BACKWARD, () -> this.setCursor(this.getCursor() - BBSSettings.editorJump.get())).active(active).category(editor);
        this.keys().register(Keys.FILM_CONTROLLER_CYCLE_EDITORS, () ->
        {
            this.showPanel(MathUtils.cycler(this.getPanelIndex() + (Window.isShiftPressed() ? -1 : 1), this.panels));
            UIUtils.playClick();
        }).allowShift().active(active).category(editor);

        /* E over the camera timeline: open the keyframe editor of the selected clip */
        this.keys().register(Keys.FORMS_EDIT, () ->
        {
            UIClip clipPanel = this.cameraEditor.getClipPanel();

            if (clipPanel instanceof UIKeyframeClip keyframeClip)
            {
                keyframeClip.edit.clickItself();
            }
        }).active(() ->
        {
            UIContext context = this.getContext();

            return this.data != null && !this.isFlying() && context != null
                && this.cameraEditor.clips.area.isInside(context)
                && this.cameraEditor.getClipPanel() instanceof UIKeyframeClip;
        }).label(UIKeys.CAMERA_PANELS_EDIT_KEYFRAMES).category(editor);

        /* F6 utility panel: separate element so ignoreFocus does not affect other film keys */
        UIElement utilityPanelKeys = new UIElement().noCulling();

        utilityPanelKeys.keys().ignoreFocus().register(Keys.OPEN_UTILITY_PANEL, () ->
        {
            if (UIOverlay.has(this.getContext()))
            {
                return;
            }

            UIOverlay.addOverlay(this.getContext(), new UIUtilityOverlayPanel(UIKeys.UTILITY_TITLE, null), 320, 280);
        }).category(UIKeys.DASHBOARD_CATEGORY);
        this.add(utilityPanelKeys);

        this.toolMenuActions = (menu) ->
        {
            if (this.data == null)
            {
                return;
            }

            menu.action(Icons.ARROW_RIGHT, UIKeys.FILM_MOVE_TITLE, () ->
            {
                UIFilmMoveOverlayPanel panel = new UIFilmMoveOverlayPanel((vector) ->
                {
                    int topLayer = this.data.camera.getTopLayer() + 1;
                    int duration = this.data.camera.calculateDuration();
                    double dx = vector.x;
                    double dy = vector.y;
                    double dz = vector.z;

                    BaseValue.edit(this.data, (__) ->
                    {
                        TranslateClip clip = new TranslateClip();

                        clip.layer.set(topLayer);
                        clip.duration.set(duration);
                        clip.translate.get().set(dx, dy, dz);
                        __.camera.addClip(clip);

                        for (Replay replay : __.replays.getList())
                        {
                            for (Keyframe<Double> keyframe : replay.keyframes.x.getKeyframes()) keyframe.setValue(keyframe.getValue() + dx);
                            for (Keyframe<Double> keyframe : replay.keyframes.y.getKeyframes()) keyframe.setValue(keyframe.getValue() + dy);
                            for (Keyframe<Double> keyframe : replay.keyframes.z.getKeyframes()) keyframe.setValue(keyframe.getValue() + dz);

                            replay.actions.shift(dx, dy, dz);
                        }
                    });
                });

                UIOverlay.addOverlay(this.getContext(), panel, 200, 0.9F);
            });

            menu.action(Icons.TIME, UIKeys.FILM_INSERT_SPACE_TITLE, () ->
            {
                UINumberOverlayPanel panel = new UINumberOverlayPanel(UIKeys.FILM_INSERT_SPACE_TITLE, UIKeys.FILM_INSERT_SPACE_DESCRIPTION, (d) ->
                {
                    if (d.intValue() <= 0)
                    {
                        return;
                    }

                    for (Replay replay : this.data.replays.getList())
                    {
                        for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
                        {
                            channel.insertSpace(this.getCursor(), d.intValue());
                        }

                        for (KeyframeChannel channel : replay.properties.properties.values())
                        {
                            channel.insertSpace(this.getCursor(), d.intValue());
                        }
                    }
                });

                panel.value.limit(1).integer().setValue(1D);

                UIOverlay.addOverlay(this.getContext(), panel);
            });

            menu.action(Icons.LINE, UIKeys.FILM_REPLACE_INVENTORY, () ->
            {
                BaseValue.edit(this.getData().inventory, (inv) -> inv.fromPlayer(MinecraftClient.getInstance().player));
            });
        };

        this.fill(null);
        this.restoreFilmUILayoutSession();
        this.setAnchoredReplaysPanelEnabled(this.isAnchoredReplaysPanelEnabled());
        this.setupEditorFlex(false);
        this.updateFilmDocumentView();
        this.flightEditTime.mark();

        this.panels.add(this.cameraEditor);
        this.panels.add(this.replayEditor);
        this.panels.add(this.actionEditor);

        this.fullscreenPlaybackBar = new UIFilmFullscreenPlaybackBar(this);
        this.fullscreenPlaybackBar.keys().register(Keys.PLAUSE, () -> this.preview.plause.clickItself()).active(() -> this.fullscreenPlaybackBar.isKeybindActive()).category(editor);

        this.setUndoId("film_panel");
        this.cameraEditor.setUndoId("camera_editor");
        this.replayEditor.setUndoId("replay_editor");
        this.actionEditor.setUndoId("action_editor");

        UIElement element = new UIElement()
        {
            @Override
            protected boolean subMouseScrolled(UIContext context)
            {
                if (Window.isCtrlPressed() && !UIFilmPanel.this.isFlying())
                {
                    int magnitude = Window.isShiftPressed() ? BBSSettings.editorJump.get() : 1;
                    int newCursor = UIFilmPanel.this.getCursor() + (int) Math.copySign(magnitude, context.mouseWheel);

                    UIFilmPanel.this.setCursor(newCursor);

                    return true;
                }

                return super.subMouseScrolled(context);
            }
        };

        this.add(element);
    }



    private Vector2i getMainHandlerReferencePosition()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        if (layout.isHorizontal())
        {
            return new Vector2i(this.getHorizontalDividerX(layout), layout.isMainOnTop() ? this.editArea.area.y : this.editArea.area.ey());
        }

        if (layout.isMiddleLayout())
        {
            return new Vector2i(this.cameraEditor.area.x, this.editor.area.my());
        }

        return new Vector2i(this.draggableMain.area.mx(), this.editArea.area.y);
    }

    private Vector2i getMainHandlerRenderPosition(ValueEditorLayout layout)
    {
        if (layout.isMiddleLayout())
        {
            return new Vector2i(this.draggableMain.area.mx(), this.editor.area.my());
        }

        return new Vector2i(this.draggableMain.area.mx(), this.editArea.area.y - 3);
    }

    private Vector2i getEditorHandlerRenderPosition()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        if (layout.isMiddleLayout())
        {
            return new Vector2i(this.draggableEditor.area.mx(), this.editor.area.my());
        }

        return new Vector2i(this.editArea.area.x + 3, this.editArea.area.y - 3);
    }

    private int getHorizontalDividerX(ValueEditorLayout layout)
    {
        return this.isHorizontalEditorOnLeft(layout) ? this.editArea.area.ex() : this.editArea.area.x;
    }

    private boolean isHorizontalEditorOnLeft(ValueEditorLayout layout)
    {
        return layout.isHorizontalLayoutInverted();
    }

    private boolean isMainOnLeftForCurrentLayout(ValueEditorLayout layout)
    {
        if (layout.isHorizontal() || layout.isMiddleLayout())
        {
            return layout.isMainOnLeft();
        }

        return layout.isMainOnLeft() != layout.isVerticalLayoutInverted();
    }

    private float calculateMainSizeVFromMouse(ValueEditorLayout layout, int mouseX)
    {
        float normalizedX = (mouseX - this.editor.area.x) / (float) this.editor.area.w;

        if (layout.getLayout() == ValueEditorLayout.LAYOUT_VERTICAL_LEFT)
        {
            return layout.isVerticalLayoutInverted() ? 1F - normalizedX : normalizedX;
        }

        if (layout.getLayout() == ValueEditorLayout.LAYOUT_VERTICAL_RIGHT)
        {
            return 1F - normalizedX;
        }

        return this.isMainOnLeftForCurrentLayout(layout) ? normalizedX : 1F - normalizedX;
    }

    private void setupEditorFlex(boolean resize)
    {
        this.setupEditorFlex(resize, false, true);
    }

    private void setupEditorFlex(boolean resize, boolean fast, boolean recreateTabs)
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.getFilmLayoutRoot();
        Map<String, Integer> preservedTabScroll = recreateTabs ? this.captureTabBarScroll() : null;

        if (this.hasPanelInLayout(root, "main"))
        {
            root = this.migrateLegacyLayout(root);
            layout.setFilmLayoutRoot(root);
        }

        this.updateTargets();

        EditorLayoutNode migratedRoot = this.migrateReplaysPropertiesPanel(root);

        if (migratedRoot != root)
        {
            layout.setFilmLayoutRoot(migratedRoot);
            root = migratedRoot;
        }

        layout.syncFilmSplittersFromRoot(root);

        this.resetDynamicLayoutElements(recreateTabs);
        List<EditorLayoutNode.SplitterNode> splitters = layout.getFilmSplitters();

        if (fast && !layout.isLayoutLocked() && resize && this.splitterHandles.size() == this.splitterHandleInfos.size() && !this.splitterHandleInfos.isEmpty())
        {
            this.updateEditorFlexBoundsOnly(layout, root);
            this.syncReplaysPropertiesLayoutMode();
            this.resize();
            this.resize();

            return;
        }

        EditorLayoutNode visibleRoot = this.createVisibleDockedLayoutRoot(root);
        Map<String, float[]> bounds = this.computePanelBounds(visibleRoot);
        
        List<EditorLayoutNode.TabbedNode> tabbedNodes = new ArrayList<>();
        EditorLayoutNode.collectTabbedNodes(visibleRoot, tabbedNodes);
        Set<String> multiTabPanels = new HashSet<>();
        for (EditorLayoutNode.TabbedNode tabbed : tabbedNodes)
        {
            if (tabbed.tabs.size() > 1)
            {
                for (EditorLayoutNode tab : tabbed.tabs)
                {
                    if (tab instanceof EditorLayoutNode.PanelNode)
                    {
                        multiTabPanels.add(((EditorLayoutNode.PanelNode) tab).getPanelId());
                    }
                }
            }
        }

        this.applyPanelBoundsFromMap(bounds, multiTabPanels);

        if (layout.isLayoutLocked())
        {
            this.setPanelDragHandlesVisible(false);
            
            for (UIDraggable handle : this.splitterHandles)
            {
                handle.removeFromParent();
            }
            this.splitterHandles.clear();
        }
        else
        {
            if (recreateTabs)
            {
                this.rebuildSplitterHandles(layout, root, splitters);
            }

            this.splitterHandleInfos.clear();
            EditorLayoutNode.computeSplitterHandles(visibleRoot, 0F, 0F, 1F, 1F, this.splitterHandleInfos);
            this.refreshSplitterHandleTargets(root, visibleRoot);
            this.syncSplitterHandleBounds();
            this.applyDragHandleBoundsFromMap(bounds);
        }
        
        this.setupTabBars(root, visibleRoot, bounds, recreateTabs);
        this.restoreTabBarScroll(preservedTabScroll);
        this.syncReplaysPropertiesLayoutMode();

        if (resize)
        {
            this.resize();
            this.resize();
        }

        this.updateFilmDocumentView();
    }

    private EditorLayoutNode migrateLegacyLayout(EditorLayoutNode root)
    {
        if (root == null)
        {
            return null;
        }

        if (this.floatingPanels.remove("main"))
        {
            this.floatingPanels.add("cameraTimeline");
            this.floatingPanels.add("replayTimeline");
            this.floatingPanels.add("actionTimeline");
        }
        if (this.collapsedFloatingPanels.remove("main"))
        {
            this.collapsedFloatingPanels.add("cameraTimeline");
            this.collapsedFloatingPanels.add("replayTimeline");
            this.collapsedFloatingPanels.add("actionTimeline");
        }
        if (this.collapsedDockedPanels.remove("main"))
        {
            this.collapsedDockedPanels.add("cameraTimeline");
            this.collapsedDockedPanels.add("replayTimeline");
            this.collapsedDockedPanels.add("actionTimeline");
        }
        if (this.hiddenPanels.remove("main"))
        {
            this.hiddenPanels.add("cameraTimeline");
            this.hiddenPanels.add("replayTimeline");
            this.hiddenPanels.add("actionTimeline");
        }
        Vector2i pos = this.floatingPanelPositions.remove("main");
        if (pos != null)
        {
            this.floatingPanelPositions.put("cameraTimeline", pos);
            this.floatingPanelPositions.put("replayTimeline", new Vector2i(pos.x, pos.y + 100));
            this.floatingPanelPositions.put("actionTimeline", new Vector2i(pos.x + 100, pos.y));
        }
        Vector2i size = this.floatingPanelSizes.remove("main");
        if (size != null)
        {
            this.floatingPanelSizes.put("cameraTimeline", size);
            this.floatingPanelSizes.put("replayTimeline", size);
            this.floatingPanelSizes.put("actionTimeline", size);
        }

        if (this.hasPanelInLayout(root, "main"))
        {
            root = EditorLayoutNode.copyWithReplacedLeaf(
                root,
                "main",
                new EditorLayoutNode.SplitterNode(
                    true,
                    0.5F,
                    new EditorLayoutNode.PanelNode("replayTimeline"),
                    new EditorLayoutNode.SplitterNode(
                        false,
                        0.5F,
                        new EditorLayoutNode.PanelNode("cameraTimeline"),
                        new EditorLayoutNode.PanelNode("actionTimeline")
                    )
                )
            );
        }

        if (!this.hasPanelInLayout(root, "cameraEditArea") && !this.floatingPanels.contains("cameraEditArea") && !this.hiddenPanels.contains("cameraEditArea"))
        {
            if (this.hasPanelInLayout(root, "editArea"))
            {
                root = EditorLayoutNode.copyWithInsertSplitAt(root, "editArea", "cameraEditArea", EditorLayoutNode.EDGE_BOTTOM);
            }
            else
            {
                String target = this.getFirstDockedVisiblePanelId();

                if (target != null)
                {
                    root = EditorLayoutNode.copyWithInsertSplitAt(root, target, "cameraEditArea", EditorLayoutNode.EDGE_BOTTOM);
                }
            }
        }

        if (!this.hasPanelInLayout(root, "actionEditArea") && !this.floatingPanels.contains("actionEditArea") && !this.hiddenPanels.contains("actionEditArea"))
        {
            if (this.hasPanelInLayout(root, "cameraEditArea"))
            {
                root = EditorLayoutNode.copyWithInsertSplitAt(root, "cameraEditArea", "actionEditArea", EditorLayoutNode.EDGE_BOTTOM);
            }
            else if (this.hasPanelInLayout(root, "editArea"))
            {
                root = EditorLayoutNode.copyWithInsertSplitAt(root, "editArea", "actionEditArea", EditorLayoutNode.EDGE_BOTTOM);
            }
            else
            {
                String target = this.getFirstDockedVisiblePanelId();

                if (target != null)
                {
                    root = EditorLayoutNode.copyWithInsertSplitAt(root, target, "actionEditArea", EditorLayoutNode.EDGE_BOTTOM);
                }
            }
        }

        return root;
    }

    private void resetDynamicLayoutElements(boolean recreateTabs)
    {
        if (recreateTabs)
        {
            for (UITabBar bar : this.tabBars)
            {
                bar.removeFromParent();
            }

            this.tabBars.clear();
            this.editor.getChildren().removeIf(child -> child instanceof UITabBar);
        }
        for (UIElement panel : this.panelById.values())
        {
            panel.resetFlex();
            panel.setVisible(false);
        }
        this.draggableMain.setVisible(false);
        this.draggableEditor.setVisible(false);
        for (UIDraggable handle : this.dragHandlesById.values())
        {
            handle.resetFlex();
        }
    }

    private Map<String, float[]> computePanelBounds(EditorLayoutNode root)
    {
        Map<String, float[]> bounds = new HashMap<>();

        /* Hidden panels should not reserve docked layout space. We keep the user's saved
           layout tree intact (so re-enabling restores panels in-place), but compute the
           current bounds from a temporary tree with hidden docked leaves removed. */
        EditorLayoutNode boundsRoot = this.createVisibleDockedLayoutRoot(root);

        if (boundsRoot != null)
        {
            boundsRoot.computeBounds(0F, 0F, 1F, 1F, bounds);
        }

        return bounds;
    }

    private EditorLayoutNode createVisibleDockedLayoutRoot(EditorLayoutNode root)
    {
        EditorLayoutNode boundsRoot = root;

        for (String panelId : this.hiddenPanels)
        {
            if (panelId == null || panelId.isEmpty())
            {
                continue;
            }

            if (this.floatingPanels.contains(panelId))
            {
                continue;
            }

            if (boundsRoot != null && this.hasPanelInLayout(boundsRoot, panelId))
            {
                boundsRoot = EditorLayoutNode.copyWithRemovedLeaf(boundsRoot, panelId);
            }
        }

        return boundsRoot;
    }

    private void setPanelDragHandlesVisible(boolean visible)
    {
        for (UIDraggable handle : this.dragHandlesById.values())
        {
            handle.setVisible(visible);
        }
    }

    private void rebuildSplitterHandles(ValueEditorLayout layout, EditorLayoutNode root, List<EditorLayoutNode.SplitterNode> splitters)
    {
        for (UIDraggable handle : this.splitterHandles)
        {
            handle.removeFromParent();
        }
        this.splitterHandles.clear();

        EditorLayoutNode visibleRoot = this.createVisibleDockedLayoutRoot(root);

        /* Keep filmSplitters aligned with the saved layout tree, not the temporary
           visible tree used for bounds/handle placement. */
        layout.syncFilmSplittersFromRoot(root);

        this.splitterHandleInfos.clear();
        EditorLayoutNode.computeSplitterHandles(visibleRoot, 0F, 0F, 1F, 1F, this.splitterHandleInfos);
        this.refreshSplitterHandleTargets(root, visibleRoot);

        int handleCount = this.splitterHandleInfos.size();

        for (int i = 0; i < handleCount; i++)
        {
            final int index = i;
            UIDraggable handle = new UIDraggable((context) ->
            {
                if (index >= this.splitterHandleInfos.size())
                {
                    return;
                }

                float ratio = this.getSplitterRatioFromMouse(index, context.mouseX, context.mouseY);

                if (ratio >= 0F)
                {
                    this.setSplitterRatioForHandle(index, ratio);
                    this.setupEditorFlex(true, true, false);
                }
            });

            handle.dragEnd(() -> this.setupEditorFlex(true, false, false));
            handle.rendering((context) -> this.renderSplitter(context, index));
            this.applySplitterHandleBounds(handle, this.splitterHandleInfos.get(index));
            this.splitterHandles.add(handle);

            this.editor.add(handle);
            handle.resize();
        }
    }

    private Set<String> getDockedHiddenPanelIds()
    {
        Set<String> excluded = new HashSet<>();

        for (String panelId : this.hiddenPanels)
        {
            if (!this.floatingPanels.contains(panelId))
            {
                excluded.add(panelId);
            }
        }

        return excluded;
    }

    private void refreshSplitterHandleTargets(EditorLayoutNode layoutRoot, EditorLayoutNode visibleRoot)
    {
        this.splitterHandleTargets.clear();

        if (layoutRoot == null || visibleRoot == null)
        {
            return;
        }

        Set<String> excludedPanelIds = this.getDockedHiddenPanelIds();
        List<EditorLayoutNode.SplitterNode> visibleSplitters = new ArrayList<>();
        EditorLayoutNode.collectSplitters(visibleRoot, visibleSplitters);

        for (EditorLayoutNode.SplitterNode visibleSplitter : visibleSplitters)
        {
            this.splitterHandleTargets.add(this.resolveLayoutSplitter(layoutRoot, visibleSplitter, excludedPanelIds));
        }
    }

    private EditorLayoutNode.SplitterNode resolveLayoutSplitter(EditorLayoutNode layoutRoot, EditorLayoutNode.SplitterNode visibleSplitter, Set<String> excludedPanelIds)
    {
        if (layoutRoot == null || visibleSplitter == null)
        {
            return null;
        }

        String visibleSignature = this.getSplitterSignature(visibleSplitter, Collections.emptySet());
        List<EditorLayoutNode.SplitterNode> layoutSplitters = new ArrayList<>();
        EditorLayoutNode.collectSplitters(layoutRoot, layoutSplitters);

        for (EditorLayoutNode.SplitterNode candidate : layoutSplitters)
        {
            if (this.getSplitterSignature(candidate, excludedPanelIds).equals(visibleSignature))
            {
                return candidate;
            }
        }

        return null;
    }

    private String getSplitterSignature(EditorLayoutNode.SplitterNode splitter, Set<String> excludedPanelIds)
    {
        TreeSet<String> first = new TreeSet<>();
        TreeSet<String> second = new TreeSet<>();

        this.collectPanelIdsInSubtree(splitter.getFirst(), first, excludedPanelIds);
        this.collectPanelIdsInSubtree(splitter.getSecond(), second, excludedPanelIds);

        return splitter.isHorizontal() + "|" + String.join(",", first) + "|" + String.join(",", second);
    }

    private void collectPanelIdsInSubtree(EditorLayoutNode node, Set<String> out, Set<String> excludedPanelIds)
    {
        if (node instanceof EditorLayoutNode.PanelNode)
        {
            String panelId = ((EditorLayoutNode.PanelNode) node).getPanelId();

            if (!excludedPanelIds.contains(panelId))
            {
                out.add(panelId);
            }
        }
        else if (node instanceof EditorLayoutNode.SplitterNode)
        {
            EditorLayoutNode.SplitterNode splitter = (EditorLayoutNode.SplitterNode) node;

            this.collectPanelIdsInSubtree(splitter.getFirst(), out, excludedPanelIds);
            this.collectPanelIdsInSubtree(splitter.getSecond(), out, excludedPanelIds);
        }
        else if (node instanceof EditorLayoutNode.TabbedNode)
        {
            EditorLayoutNode.TabbedNode tabbed = (EditorLayoutNode.TabbedNode) node;

            for (EditorLayoutNode tab : tabbed.tabs)
            {
                this.collectPanelIdsInSubtree(tab, out, excludedPanelIds);
            }
        }
    }

    private void setSplitterRatioForHandle(int handleIndex, float ratio)
    {
        if (handleIndex < 0 || handleIndex >= this.splitterHandleTargets.size())
        {
            return;
        }

        EditorLayoutNode.SplitterNode target = this.splitterHandleTargets.get(handleIndex);

        if (target == null)
        {
            return;
        }

        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.getFilmLayoutRoot();

        layout.syncFilmSplittersFromRoot(root);

        List<EditorLayoutNode.SplitterNode> splitters = layout.getFilmSplitters();
        int layoutIndex = splitters.indexOf(target);

        if (layoutIndex >= 0)
        {
            layout.setFilmSplitterRatio(layoutIndex, ratio);
        }
        else
        {
            BaseValue.edit(layout, (v) -> target.setRatio(ratio));
        }
    }

    private void applySplitterHandleBounds(UIDraggable handle, EditorLayoutNode.SplitterHandleInfo info)
    {
        int ew = this.editor.area.w;
        int eh = this.editor.area.h;
        if (ew < EDITOR_MIN_SIZE_FOR_PX_HANDLES || eh < EDITOR_MIN_SIZE_FOR_PX_HANDLES)
        {
            handle.relative(this.editor).x(info.hx).y(info.hy).w(info.hw).h(info.hh);
            return;
        }

        if (info.horizontal)
        {
            float centerY = info.hy + info.hh * 0.5F;
            float hyNew = centerY - (SPLITTER_HANDLE_PX / (2F * eh));
            handle.relative(this.editor).x(info.hx).y(hyNew).w(info.hw).h(SPLITTER_HANDLE_PX);
        }
        else
        {
            float centerX = info.hx + info.hw * 0.5F;
            float hxNew = centerX - (SPLITTER_HANDLE_PX / (2F * ew));
            handle.relative(this.editor).x(hxNew).y(info.hy).w(SPLITTER_HANDLE_PX).h(info.hh);
        }
    }

    private void syncSplitterHandleBounds()
    {
        for (int i = 0; i < this.splitterHandles.size() && i < this.splitterHandleInfos.size(); i++)
        {
            this.applySplitterHandleBounds(this.splitterHandles.get(i), this.splitterHandleInfos.get(i));
        }
    }

    private float getSplitterRatioFromMouse(int index, int mouseX, int mouseY)
    {
        if (index < 0 || index >= this.splitterHandleInfos.size())
        {
            return -1F;
        }

        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        int ex = this.editor.area.x;
        int ey = this.editor.area.y;
        int ew = Math.max(1, this.editor.area.w);
        int eh = Math.max(1, this.editor.area.h);
        float ratio = info.horizontal
            ? (mouseY - (ey + info.py * eh)) / (info.ph * eh)
            : (mouseX - (ex + info.px * ew)) / (info.pw * ew);

        return MathUtils.clamp(ratio, 0.05F, 0.95F);
    }

    private Vector2i getSplitterHandleReferencePosition(int index, List<EditorLayoutNode.SplitterNode> splitters)
    {
        if (index < 0 || index >= this.splitterHandleInfos.size() || index >= splitters.size())
        {
            return new Vector2i(this.editor.area.x, this.editor.area.y);
        }

        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        float r = splitters.get(index).getRatio();
        int ex = this.editor.area.x;
        int ey = this.editor.area.y;
        int ew = Math.max(1, this.editor.area.w);
        int eh = Math.max(1, this.editor.area.h);
        int hx = ex + (int) ((info.px + (info.horizontal ? info.pw * 0.5F : r * info.pw)) * ew);
        int hy = ey + (int) ((info.py + (info.horizontal ? r * info.ph : info.ph * 0.5F)) * eh);

        return new Vector2i(hx, hy);
    }

    private void applyPanelBoundsFromMap(Map<String, float[]> bounds, Set<String> multiTabPanels)
    {
        for (UIElement el : this.panelById.values())
        {
            el.setVisible(false);
        }
        this.homePage.setVisible(false);
        this.dockedHeaderPanels.clear();

        /* When the layout is locked the window title bars are hidden, so docked panels fill
           their whole bounds with no header inset and no card header is registered. */
        boolean locked = BBSSettings.editorLayoutSettings.isLayoutLocked();
        int headerInset = locked ? 0 : PANEL_HEADER_HEIGHT;

        for (Map.Entry<String, float[]> e : bounds.entrySet())
        {
            String id = e.getKey();
            if (this.floatingPanels.contains(id))
            {
                continue;
            }
            UIElement el = this.panelById.get(id);
            if (el != null)
            {
                float[] b = e.getValue();
                /* Every docked window reserves a card title bar at the top (unless the layout is
                   locked). Tabbed panels use the tab bar (setupTabBars) as their header; the rest
                   get a card header drawn by renderDockedPanelHeaders. */
                el.relative(this.editor).x(b[0], 3).y(b[1], headerInset + 3).w(b[2], -6).h(b[3], -headerInset - 6);
                el.setVisible(!this.collapsedDockedPanels.contains(id));

                if (this.hiddenPanels.contains(id))
                {
                    el.setVisible(false);
                }
                else if (!multiTabPanels.contains(id) && !locked)
                {
                    this.dockedHeaderPanels.add(id);
                }
            }
        }

        for (String panelId : this.floatingPanels)
        {
            UIElement el = this.panelById.get(panelId);
            if (el != null)
            {
                boolean collapsed = this.collapsedFloatingPanels.contains(panelId);
                boolean hidden = this.hiddenPanels.contains(panelId);
                Vector2i pos = this.floatingPanelPositions.get(panelId);
                Vector2i size = this.floatingPanelSizes.get(panelId);
                if (pos != null && size != null)
                {
                    this.reflowFloatingPanelWithinEditor(panelId);

                    if (collapsed || hidden)
                    {
                        el.setVisible(false);
                    }
                    else
                    {
                        el.relative(this.editor).x(0F, pos.x).y(0F, pos.y + PANEL_HEADER_HEIGHT).w(0F, size.x).h(0F, size.y - PANEL_HEADER_HEIGHT);
                        el.setVisible(true);
                    }
                }
            }
        }
    }

    private void applyDragHandleBoundsFromMap(Map<String, float[]> bounds)
    {
        for (UIDraggable h : this.dragHandlesById.values())
        {
            h.setVisible(false);
        }

        for (Map.Entry<String, float[]> e : bounds.entrySet())
        {
            String id = e.getKey();
            if (this.floatingPanels.contains(id))
            {
                continue;
            }
            if (this.hiddenPanels.contains(id))
            {
                continue;
            }
            UIDraggable h = this.dragHandlesById.get(id);
            UIElement el = this.panelById.get(id);
            
            if (h != null && el != null)
            {
                float[] b = e.getValue();
                /* The drag handle covers the whole card title bar. */
                h.relative(this.editor).x(b[0], 3).y(b[1], 3).w(b[2], -6).h(0F, PANEL_HEADER_HEIGHT);
                h.setVisible(!BBSSettings.editorLayoutSettings.isLayoutLocked() && !this.usesPanelInternalDragHandle(id));
            }
        }
    }

    private boolean usesPanelInternalDragHandle(String panelId)
    {
        /* All docked panels (including the replays panel) use the generic drag handle. */
        return false;
    }

    private void updateEditorFlexBoundsOnly(ValueEditorLayout layout, EditorLayoutNode root)
    {
        EditorLayoutNode visibleRoot = this.createVisibleDockedLayoutRoot(root);
        Map<String, float[]> bounds = this.computePanelBounds(root);

        List<EditorLayoutNode.TabbedNode> tabbedNodes = new ArrayList<>();
        EditorLayoutNode.collectTabbedNodes(visibleRoot, tabbedNodes);
        Set<String> multiTabPanels = new HashSet<>();
        for (EditorLayoutNode.TabbedNode tabbed : tabbedNodes)
        {
            if (tabbed.tabs.size() > 1)
            {
                for (EditorLayoutNode tab : tabbed.tabs)
                {
                    if (tab instanceof EditorLayoutNode.PanelNode)
                    {
                        multiTabPanels.add(((EditorLayoutNode.PanelNode) tab).getPanelId());
                    }
                }
            }
        }

        this.applyPanelBoundsFromMap(bounds, multiTabPanels);
        this.splitterHandleInfos.clear();
        EditorLayoutNode.computeSplitterHandles(visibleRoot, 0F, 0F, 1F, 1F, this.splitterHandleInfos);
        this.refreshSplitterHandleTargets(root, visibleRoot);
        this.syncSplitterHandleBounds();
        this.applyDragHandleBoundsFromMap(bounds);
        
        this.setupTabBars(root, visibleRoot, bounds, false);

        /* Re-stack the editor from back to front. Child order is both the draw order and the
           (reversed) hit order, so this is what guarantees floating windows always sit on top of
           docked panels and never get painted over by them:

             docked content (already in the list) -> docked card headers + icons
             -> floating backdrops -> floating content -> floating chrome -> drop guides */
        this.editor.getChildren().removeIf(c -> c instanceof UIRenderable);
        this.editor.add(new UIRenderable(this::renderDockedPanelHeaders), new UIRenderable(this::renderIcons), new UIRenderable(this::renderFloatingPanelBackgrounds));
        this.liftFloatingPanelsToFront();
        this.editor.add(new UIRenderable(this::renderFloatingPanelWindows), new UIRenderable(this::renderDropZoneHighlight));
    }

    /**
     * Move every visible floating window's content element to the front of the editor's child
     * list (preserving their relative stacking), so they render and receive clicks above all
     * docked panels.
     */
    private void liftFloatingPanelsToFront()
    {
        List<IUIElement> floatingEls = new ArrayList<>();

        for (IUIElement child : new ArrayList<>(this.editor.getChildren()))
        {
            String id = this.findPanelIdForElement(child);

            if (id != null && this.floatingPanels.contains(id) && !this.hiddenPanels.contains(id))
            {
                floatingEls.add(child);
            }
        }

        this.editor.getChildren().removeAll(floatingEls);

        for (IUIElement el : floatingEls)
        {
            this.editor.add(el);
        }
    }

    private String findPanelIdForElement(IUIElement el)
    {
        for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
        {
            if (entry.getValue() == el)
            {
                return entry.getKey();
            }
        }

        return null;
    }

    private void clearPanelDragState()
    {
        this.draggingPanelId = null;
        this.dropTargetPanelId = null;
        this.dropTargetZone = DROP_ZONE_CENTER;

        /* Drop any stale drag latch on the header handles. A handle is hidden the
           moment its panel is torn out into a floating window, so it never receives
           the mouse release that would normally reset it — and a stale dragging flag
           would re-float the panel one frame after it docks. */
        for (UIDraggable handle : this.dragHandlesById.values())
        {
            handle.resetDrag();
        }

        /* Also drop the floating drag/resize pointers and the mouse-hold latch. Otherwise a
           window that was being undocked (which sets activeDraggingFloatingPanelId via
           ensurePanelFloatingForDrag) keeps following the cursor every frame in the update
           loop, even after the mouse button is released or the layout is reset. */
        this.activeDraggingFloatingPanelId = null;
        this.activeResizingFloatingPanelId = null;
        this.mouseHeldPanelId = null;
        this.clearTabReorderState();
    }

    private void clearTabReorderState()
    {
        this.tabReordering = false;
        this.tabReorderPanelId = null;
        this.tabReorderFromIndex = 0;
        this.tabReorderDropPreview = -1;
        this.tabReorderGapX = 0;
        this.tabReorderGapW = 0;
        this.tabReorderTabbedNode = null;
        this.tabReorderTabBar = null;
    }

    private boolean isInsideTabBarArea(UITabBar tabBar, int mouseX, int mouseY)
    {
        if (tabBar == null || !tabBar.isVisible())
        {
            return false;
        }

        int pad = TAB_REORDER_DETACH_PADDING;

        return mouseX >= tabBar.area.x - pad
            && mouseX < tabBar.area.ex() + pad
            && mouseY >= tabBar.area.y - pad
            && mouseY < tabBar.area.ey() + pad;
    }

    private UITabBar findTabBarForPanel(String panelId)
    {
        for (UITabBar tabBar : this.tabBars)
        {
            if (tabBar.containsPanel(panelId))
            {
                return tabBar;
            }
        }

        return null;
    }

    /**
     * Drop index among remaining visible tabs (dragged omitted), 0..count inclusive.
     */
    private int getTabDropPreviewIndex(UITabBar tabBar, int mouseX)
    {
        if (tabBar == null || this.tabReorderPanelId == null)
        {
            return -1;
        }

        int remaining = 0;

        for (IUIElement child : tabBar.getChildren())
        {
            if (!(child instanceof UITab))
            {
                continue;
            }

            UITab tab = (UITab) child;

            if (this.hiddenPanels.contains(tab.panelId) || this.tabReorderPanelId.equals(tab.panelId))
            {
                continue;
            }

            if (mouseX < tab.area.x + tab.area.w / 2)
            {
                return remaining;
            }

            remaining += 1;
        }

        return remaining;
    }

    private void updateTabReorder(int mouseX, int mouseY)
    {
        if (this.tabReorderPanelId == null)
        {
            return;
        }

        UITabBar tabBar = this.findTabBarForPanel(this.tabReorderPanelId);

        if (tabBar == null)
        {
            return;
        }

        this.tabReorderTabBar = tabBar;

        int preview = this.getTabDropPreviewIndex(tabBar, mouseX);

        if (preview >= 0)
        {
            this.tabReorderDropPreview = preview;
        }
    }

    private void finishTabReorder()
    {
        if (this.tabReorderPanelId == null)
        {
            this.clearTabReorderState();

            return;
        }

        int from = this.tabReorderFromIndex;
        int to = this.tabReorderDropPreview;

        String panelId = this.tabReorderPanelId;

        this.clearTabReorderState();

        if (to < 0)
        {
            return;
        }

        EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();
        EditorLayoutNode newRoot = EditorLayoutNode.copyWithReorderedTabs(root, panelId, from, to);

        if (newRoot != root)
        {
            BBSSettings.editorLayoutSettings.setFilmLayoutRoot(newRoot);
            this.setupEditorFlex(true, false, true);
            this.persistFilmUILayoutSession();
        }
    }

    private void startTabReorderFromFloat(String panelId, int mouseX, int mouseY)
    {
        /* Keep the grab offset captured when reorder started. The dragged tab is
         * parked off-screen during reorder, so its area is not a valid origin. */
        this.clearTabReorderState();
        this.startPanelDrag(panelId);
        this.ensurePanelFloatingForDrag(panelId, mouseX, mouseY);
        this.updateDropTargetFromMouse(mouseX, mouseY);
    }

    private boolean isPanelHeaderDragActive()
    {
        if (this.activeDraggingFloatingPanelId != null)
        {
            return true;
        }

        for (UIDraggable handle : this.dragHandlesById.values())
        {
            if (handle.isActivelyDragging())
            {
                return true;
            }
        }

        return false;
    }

    private boolean isPanelHeaderDragClick(String panelId, int mouseX, int mouseY)
    {
        if (this.floatingPanels.contains(panelId))
        {
            Vector2i pos = this.floatingPanelPositions.get(panelId);
            Vector2i size = this.floatingPanelSizes.get(panelId);

            if (pos == null || size == null)
            {
                return false;
            }

            int x = this.editor.area.x + pos.x;
            int y = this.editor.area.y + pos.y;
            int w = size.x;

            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + PANEL_HEADER_HEIGHT;
        }

        UIElement panel = this.panelById.get(panelId);

        if (panel == null || !panel.isVisible())
        {
            return false;
        }

        /* Docked panel widgets live below the card/tab title bar. */
        return mouseY < panel.area.y;
    }

    private void clearStalePanelDragOnContentClick(UIContext context)
    {
        if (context.mouseButton != 0 || BBSSettings.editorLayoutSettings.isLayoutLocked() || this.isPanelHeaderDragActive())
        {
            return;
        }

        for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
        {
            String panelId = entry.getKey();
            UIElement el = entry.getValue();

            if (!el.isVisible() || this.hiddenPanels.contains(panelId))
            {
                continue;
            }

            if (this.isPanelHeaderDragClick(panelId, context.mouseX, context.mouseY))
            {
                continue;
            }

            if (this.floatingPanels.contains(panelId))
            {
                Vector2i pos = this.floatingPanelPositions.get(panelId);
                Vector2i size = this.floatingPanelSizes.get(panelId);

                if (pos == null || size == null)
                {
                    continue;
                }

                int x = this.editor.area.x + pos.x;
                int y = this.editor.area.y + pos.y;
                int w = size.x;
                int h = this.collapsedFloatingPanels.contains(panelId) ? PANEL_HEADER_HEIGHT : size.y;

                if (context.mouseX >= x && context.mouseX <= x + w && context.mouseY >= y && context.mouseY <= y + h)
                {
                    if (this.activeDraggingFloatingPanelId != null || this.mouseHeldPanelId != null || this.draggingPanelId != null)
                    {
                        this.clearPanelDragState();
                    }

                    return;
                }
            }
            else if (el.area.isInside(context))
            {
                if (this.mouseHeldPanelId != null || this.draggingPanelId != null)
                {
                    this.clearPanelDragState();
                }

                return;
            }
        }
    }

    private void setDropIntent(DropIntent intent)
    {
        this.dropTargetPanelId = intent == null ? null : intent.targetId;
        this.dropTargetZone = intent == null ? DROP_ZONE_CENTER : intent.zone;
    }

    private void applyPanelDropResult(String dragId, String targetId, int zone)
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.getFilmLayoutRoot();
        EditorLayoutNode newRoot = DROP_TARGET_WORKSPACE.equals(targetId)
            ? this.buildWorkspaceDroppedLayout(root, dragId, zone)
            : this.buildDroppedLayout(root, dragId, targetId, zone);

        if (newRoot != null)
        {
            this.floatingPanels.remove(dragId);
            this.collapsedFloatingPanels.remove(dragId);
            layout.setFilmLayoutRoot(newRoot);
            this.setupEditorFlex(true);
        }
    }

    private EditorLayoutNode buildWorkspaceDroppedLayout(EditorLayoutNode root, String draggedId, int zone)
    {
        if (draggedId == null || zone == DROP_ZONE_CENTER || zone == DROP_ZONE_TAB)
        {
            return root;
        }

        if (root == null || !this.hasAnyDockedVisiblePanel())
        {
            return new EditorLayoutNode.PanelNode(draggedId);
        }

        String target = this.getWorkspaceEdgeTargetPanelId(zone);

        return target == null ? new EditorLayoutNode.PanelNode(draggedId) : EditorLayoutNode.copyWithInsertSplitAt(root, target, draggedId, zone);
    }

    private EditorLayoutNode buildDroppedLayout(EditorLayoutNode root, String draggedId, String targetId, int zone)
    {
        switch (zone)
        {
            case DROP_ZONE_CENTER:
                /* The dragged panel is always pulled out of the tree before a drop (a floating
                   window was never in it; a docked panel is torn out the moment its drag begins),
                   so an id-swap has nothing to swap and would make the window vanish. Stack it
                   into the target's slot as a tab instead, which is the intuitive "drop on the
                   middle" behaviour anyway. */
                return EditorLayoutNode.copyWithDockedLeaf(root, targetId, draggedId);

                        case DROP_ZONE_TAB:
                return EditorLayoutNode.copyWithDockedLeaf(root, targetId, draggedId);

            case EditorLayoutNode.EDGE_LEFT:
            case EditorLayoutNode.EDGE_RIGHT:
            case EditorLayoutNode.EDGE_TOP:
            case EditorLayoutNode.EDGE_BOTTOM:
                return EditorLayoutNode.copyWithInsertSplitAt(root, targetId, draggedId, zone);

            default:
                return root;
        }
    }

    private UIDraggable createPanelDragHandle(String panelId)
    {
        UIDraggable handle = new UIDraggable((context) ->
        {
            this.startPanelDrag(panelId);
            this.ensurePanelFloatingForDrag(panelId, context.mouseX, context.mouseY);
            this.updateFloatingPanelDragPosition(panelId, context.mouseX, context.mouseY);
            this.updateDropTargetFromMouse(context.mouseX, context.mouseY);
            this.lastDragMouseX = context.mouseX;
            this.lastDragMouseY = context.mouseY;
        })
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (this.area.isInside(context) && context.mouseButton == 0 && context.mouseX >= this.area.ex() - 20)
                {
                    UIFilmPanel.this.toggleCollapseDockedPanel(panelId);
                    UIUtils.playClick();

                    return true;
                }

                if (this.area.isInside(context) && context.mouseButton == 0)
                {
                    if (!UIFilmPanel.this.isPanelHeaderDragClick(panelId, context.mouseX, context.mouseY))
                    {
                        return false;
                    }

                    UIFilmPanel.this.dragOffsetX = context.mouseX - this.area.x;
                    UIFilmPanel.this.dragOffsetY = context.mouseY - this.area.y;
                }

                return super.subMouseClicked(context);
            }
        };

        handle.dragEnd(() ->
        {
            DropIntent intent = new DropIntent(this.dropTargetPanelId, this.dropTargetZone);
            if (!this.canApplyDropIntent(this.draggingPanelId, intent))
            {
                if (this.draggingPanelId != null)
                {
                    this.floatPanel(this.draggingPanelId, this.lastDragMouseX - 100, this.lastDragMouseY - 10);
                }
                this.clearPanelDragState();
                return;
            }

            this.applyPanelDropResult(this.draggingPanelId, intent.targetId, intent.zone);
            this.clearPanelDragState();
        });

        handle.threshold(4).hoverOnly()
            .enabled(() -> !BBSSettings.editorLayoutSettings.isLayoutLocked())
            .rendering((context) -> this.renderPanelDragHandle(context, handle));

        return handle;
    }

    private void toggleCollapseDockedPanel(String panelId)
    {
        if (this.collapsedDockedPanels.contains(panelId))
        {
            this.collapsedDockedPanels.remove(panelId);
        }
        else
        {
            this.collapsedDockedPanels.add(panelId);
        }

        this.setupEditorFlex(true);
        this.persistFilmUILayoutSession();
    }

    private void startPanelDrag(String panelId)
    {
        if (this.draggingPanelId == null)
        {
            this.draggingPanelId = panelId;
        }
    }

    private void ensurePanelFloatingForDrag(String panelId, int mouseX, int mouseY)
    {
        if (panelId == null || this.floatingPanels.contains(panelId))
        {
            return;
        }

        this.floatPanel(panelId, mouseX - this.dragOffsetX, mouseY - this.dragOffsetY);
        this.activeDraggingFloatingPanelId = panelId;
    }

    private void updateFloatingPanelDragPosition(String panelId, int mouseX, int mouseY)
    {
        if (panelId == null || !this.floatingPanels.contains(panelId))
        {
            return;
        }

        Vector2i pos = this.floatingPanelPositions.get(panelId);
        Vector2i size = this.floatingPanelSizes.get(panelId);

        if (pos == null || size == null)
        {
            return;
        }

        boolean collapsed = this.collapsedFloatingPanels.contains(panelId);
        int minW = MIN_FLOATING_PANEL_WIDTH;
        int minH = collapsed ? PANEL_HEADER_HEIGHT : MIN_FLOATING_PANEL_HEIGHT;

        /* The window's top-left tracks the cursor, kept far enough inside that at least a
           minimum-size window still fits against the right/bottom edge. */
        int newX = Math.max(0, Math.min(mouseX - this.editor.area.x - this.dragOffsetX, Math.max(0, this.editor.area.w - minW)));
        int newY = Math.max(0, Math.min(mouseY - this.editor.area.y - this.dragOffsetY, Math.max(0, this.editor.area.h - minH)));

        pos.set(newX, newY);

        /* Resize-against-the-border (model-block style): shrink the window so it stays fully
           inside from its new position instead of clamping the position and letting the title bar
           slip out from under the cursor. The shrink is permanent — the window keeps its reduced
           size when dragged back into open space. Collapsed windows only show their header, so
           their stored height is left alone. */
        int maxW = Math.max(minW, this.editor.area.w - newX);
        int newW = Math.max(minW, Math.min(size.x, maxW));
        int newH = size.y;

        if (!collapsed)
        {
            int maxH = Math.max(minH, this.editor.area.h - newY);
            newH = Math.max(minH, Math.min(size.y, maxH));
        }

        size.set(newW, newH);
    }

    /* Per-frame handling of a floating window being moved. The mouse-release event is sometimes
       swallowed before it reaches this panel, so the button is polled here: while held the window
       follows the cursor (updating the drop target); the moment it is released the drag is
       finalized, docking it if it was let go over a marker. */
    private void updateActiveFloatingPanelDrag(UIContext context)
    {
        if (this.activeDraggingFloatingPanelId == null)
        {
            return;
        }

        if (!Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT))
        {
            if (!BBSSettings.editorLayoutSettings.isLayoutLocked())
            {
                this.updateDropTargetFromMouse(context.mouseX, context.mouseY);
            }

            this.finishFloatingPanelDrag();

            return;
        }

        this.updateFloatingPanelDragPosition(this.activeDraggingFloatingPanelId, context.mouseX, context.mouseY);
        this.setupEditorFlex(true);

        if (!BBSSettings.editorLayoutSettings.isLayoutLocked())
        {
            this.updateDropTargetFromMouse(context.mouseX, context.mouseY);
        }
    }

    /* Per-frame handling of a floating window being resized from its bottom-right grip, ending the
       resize as soon as the button is released (polled, for the same missed-event reason). */
    private void updateActiveFloatingPanelResize(UIContext context)
    {
        if (this.activeResizingFloatingPanelId == null)
        {
            return;
        }

        if (!Window.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_LEFT)
            || this.collapsedFloatingPanels.contains(this.activeResizingFloatingPanelId))
        {
            this.activeResizingFloatingPanelId = null;

            return;
        }

        Vector2i pos = this.floatingPanelPositions.get(this.activeResizingFloatingPanelId);
        Vector2i size = this.floatingPanelSizes.get(this.activeResizingFloatingPanelId);

        if (pos == null || size == null)
        {
            return;
        }

        int maxW = Math.max(MIN_FLOATING_PANEL_WIDTH, this.editor.area.w - pos.x);
        int maxH = Math.max(MIN_FLOATING_PANEL_HEIGHT, this.editor.area.h - pos.y);
        int newW = context.mouseX - (this.editor.area.x + pos.x);
        int newH = context.mouseY - (this.editor.area.y + pos.y);

        size.set(
            Math.max(MIN_FLOATING_PANEL_WIDTH, Math.min(maxW, newW)),
            Math.max(MIN_FLOATING_PANEL_HEIGHT, Math.min(maxH, newH))
        );

        this.setupEditorFlex(true);
    }

    public void beginEmbeddedPanelDragHold(String panelId, int mouseX, int mouseY)
    {
        if (BBSSettings.editorLayoutSettings.isLayoutLocked())
        {
            return;
        }

        this.mouseHeldPanelId = panelId;
        this.clickX = mouseX;
        this.clickY = mouseY;
        this.lastDragMouseX = mouseX;
        this.lastDragMouseY = mouseY;
    }

    public void updateEmbeddedPanelDrag(String panelId, int mouseX, int mouseY)
    {
        if (this.mouseHeldPanelId != null && this.mouseHeldPanelId.equals(panelId) && this.draggingPanelId == null)
        {
            int dx = mouseX - this.clickX;
            int dy = mouseY - this.clickY;

            if (dx * dx + dy * dy > 5 * 5)
            {
                this.mouseHeldPanelId = null;
                this.startPanelDrag(panelId);
                this.updateDropTargetFromMouse(mouseX, mouseY);
            }
        }

        if (this.draggingPanelId != null && this.draggingPanelId.equals(panelId))
        {
            this.updateDropTargetFromMouse(mouseX, mouseY);
            this.lastDragMouseX = mouseX;
            this.lastDragMouseY = mouseY;
        }
    }

    public boolean finishEmbeddedPanelDrag(String panelId)
    {
        if (this.mouseHeldPanelId != null && this.mouseHeldPanelId.equals(panelId))
        {
            this.mouseHeldPanelId = null;

            return true;
        }

        if (this.draggingPanelId != null && this.draggingPanelId.equals(panelId))
        {
            DropIntent intent = new DropIntent(this.dropTargetPanelId, this.dropTargetZone);

            if (!this.canApplyDropIntent(this.draggingPanelId, intent))
            {
                /* Dropped on empty space: undock into a floating window, like the generic drag handle does. */
                this.floatPanel(this.draggingPanelId, this.lastDragMouseX - 100, this.lastDragMouseY - 10);
                this.clearPanelDragState();

                return true;
            }

            this.applyPanelDropResult(this.draggingPanelId, intent.targetId, intent.zone);
            this.clearPanelDragState();

            return true;
        }

        return false;
    }

    private void updateDropTargetFromMouse(int mouseX, int mouseY)
    {
        if (this.isDockSnapCancelled())
        {
            this.setDropIntent(null);

            return;
        }

        this.setDropIntent(this.resolveDropIntent(mouseX, mouseY));
    }

    private boolean isDockSnapCancelled()
    {
        return Window.isKeyPressed(GLFW.GLFW_KEY_LEFT_ALT) || Window.isKeyPressed(GLFW.GLFW_KEY_RIGHT_ALT);
    }

    private boolean canApplyDropIntent(String draggedId, DropIntent intent)
    {
        return draggedId != null && intent != null && intent.targetId != null && !draggedId.equals(intent.targetId);
    }

    private String resolveSiblingTabId(String targetId)
    {
        EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();
        if (root == null) return null;
        
        EditorLayoutNode.TabbedNode tabbed = this.findTabbedNodeContaining(root, targetId);
        if (tabbed == null) return null;
        
        for (EditorLayoutNode tab : tabbed.tabs)
        {
            if (tab instanceof EditorLayoutNode.PanelNode)
            {
                String id = ((EditorLayoutNode.PanelNode) tab).getPanelId();
                if (!id.equals(targetId))
                {
                    return id;
                }
            }
        }
        return null;
    }
    
    private EditorLayoutNode.TabbedNode findTabbedNodeContaining(EditorLayoutNode node, String panelId)
    {
        if (node instanceof EditorLayoutNode.TabbedNode)
        {
            EditorLayoutNode.TabbedNode tabbed = (EditorLayoutNode.TabbedNode) node;
            for (EditorLayoutNode tab : tabbed.tabs)
            {
                if (tab instanceof EditorLayoutNode.PanelNode && ((EditorLayoutNode.PanelNode) tab).getPanelId().equals(panelId))
                {
                    return tabbed;
                }
            }
        }
        else if (node instanceof EditorLayoutNode.SplitterNode)
        {
            EditorLayoutNode.SplitterNode splitter = (EditorLayoutNode.SplitterNode) node;
            EditorLayoutNode.TabbedNode res = this.findTabbedNodeContaining(splitter.getFirst(), panelId);
            if (res != null) return res;
            return this.findTabbedNodeContaining(splitter.getSecond(), panelId);
        }
        return null;
    }

    private boolean syncLinkedPropertiesTab(String panelId)
    {
        String linkedPanelId = this.getLinkedPropertiesPanelId(panelId);

        if (linkedPanelId == null)
        {
            return false;
        }

        EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();

        if (root != null && this.selectPanelInTabbedNode(root, linkedPanelId))
        {
            BBSSettings.editorLayoutSettings.setFilmLayoutRoot(root);
            return true;
        }

        return false;
    }

    public void clearSelectionsExcept(String timelineId)
    {
        if (this.clearingSelections)
        {
            return;
        }

        if (this.shouldRedirectProperties())
        {
            this.clearingSelections = true;
            try
            {
                /* Replay timeline interaction should not drop the camera clip selection; users
                 * often keep a camera clip selected while editing replay keyframes in unified layout. */
                if (!"cameraTimeline".equals(timelineId) && !"replayTimeline".equals(timelineId) && this.cameraEditor != null && this.cameraEditor.clips != null)
                {
                    this.cameraEditor.clips.pickClip(null);
                }
                if (!"actionTimeline".equals(timelineId) && this.actionEditor != null && this.actionEditor.clips != null)
                {
                    this.actionEditor.clips.pickClip(null);
                }
                if (!"replayTimeline".equals(timelineId) && this.replayEditor != null)
                {
                    this.replayEditor.clearSelection();
                }
            }
            finally
            {
                this.clearingSelections = false;
            }
        }
    }

    public void beginSuppressLinkedPropertiesTabFocus()
    {
        this.suppressLinkedPropertiesTabFocus++;
    }

    public void endSuppressLinkedPropertiesTabFocus()
    {
        this.suppressLinkedPropertiesTabFocus = Math.max(0, this.suppressLinkedPropertiesTabFocus - 1);
    }

    /**
     * Active panel id inside the multi-tab group that contains {@code panelId}, or
     * {@code null} when that panel is alone / not tabbed.
     */
    public String getActiveTabPanelId(String panelId)
    {
        if (panelId == null)
        {
            return null;
        }

        EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();
        EditorLayoutNode.TabbedNode tabbed = this.findTabbedNodeContaining(root, panelId);

        if (tabbed == null || tabbed.tabs.size() < 2)
        {
            return null;
        }

        int safeActiveTab = Math.max(0, Math.min(tabbed.tabs.size() - 1, tabbed.activeTab));
        EditorLayoutNode activeNode = tabbed.tabs.get(safeActiveTab);

        if (activeNode instanceof EditorLayoutNode.PanelNode)
        {
            return ((EditorLayoutNode.PanelNode) activeNode).getPanelId();
        }

        return null;
    }

    public void focusLinkedPropertiesTab(String panelId)
    {
        /* Undo/redo restores keyframe selection across all editors (including the replay
         * keyframe editor). That re-pick must not steal the properties tab while the user
         * is editing an embedded Image/Subtitle (or other camera) keyframe view. */
        if (this.undoHandler != null && this.undoHandler.isUndoing())
        {
            return;
        }

        if (this.suppressLinkedPropertiesTabFocus > 0)
        {
            /* Still remount/resize hosts so restored keyframe factories stay valid. */
            this.syncKeyframePropertiesHosts();

            return;
        }

        this.clearSelectionsExcept(panelId);

        String linkedPanelId = this.getLinkedPropertiesPanelId(panelId);

        if (linkedPanelId == null)
        {
            return;
        }

        boolean changed = this.syncLinkedPropertiesTab(panelId);
        UIElement linkedPanel = this.panelById.get(linkedPanelId);
        boolean needsRefresh = linkedPanel != null && !linkedPanel.isVisible();

        if (changed || needsRefresh)
        {
            this.setupEditorFlex(true, false, true);
        }
        else
        {
            /* Properties tab already active (common after film load restores active_tab).
             * Still retarget hosts and resize so a freshly mounted keyframe factory is not
             * left on the wrong/zero-sized panel until the user re-picks the keyframe. */
            this.syncKeyframePropertiesHosts();
        }
    }

    /**
     * Retarget clip/replay keyframe property hosts and force a layout pass on them.
     * Used when the properties tab is already visible so {@link #setupEditorFlex} was skipped.
     */
    private void syncKeyframePropertiesHosts()
    {
        this.updateTargets();

        UIElement cameraHost = this.getPropertiesHostElement(this.resolveCameraPropertiesPanelId());
        UIElement actionHost = this.getPropertiesHostElement(this.resolveActionPropertiesPanelId());
        UIElement replayHost = this.getPropertiesHostElement(this.resolveReplayPropertiesPanelId());

        if (cameraHost == null)
        {
            cameraHost = this.cameraEditArea;
        }

        if (actionHost == null)
        {
            actionHost = this.actionEditArea;
        }

        if (replayHost == null)
        {
            replayHost = this.editArea;
        }

        if (cameraHost != null)
        {
            cameraHost.resize();
        }

        if (actionHost != null)
        {
            actionHost.resize();
        }

        if (replayHost != null)
        {
            replayHost.resize();
        }
    }

    /**
     * Host panel where replay keyframe factories are mounted ({@code unifiedEditArea}
     * when redirected, otherwise {@code editArea}).
     */
    public UIElement getReplayKeyframePropertiesHost()
    {
        UIElement host = this.getPropertiesHostElement(this.resolveReplayPropertiesPanelId());

        return host != null ? host : this.editArea;
    }

    private String getLinkedPropertiesPanelId(String panelId)
    {
        if ("cameraTimeline".equals(panelId))
        {
            return this.resolveCameraPropertiesPanelId();
        }

        if ("replayTimeline".equals(panelId))
        {
            return this.resolveReplayPropertiesPanelId();
        }

        if ("actionTimeline".equals(panelId))
        {
            return this.resolveActionPropertiesPanelId();
        }

        return null;
    }

    /**
     * Prefer unified → dedicated camera host → general Properties when the
     * dedicated panel is hidden via the Window menu.
     */
    private String resolveCameraPropertiesPanelId()
    {
        if (this.shouldRedirectProperties())
        {
            return "unifiedEditArea";
        }

        if (this.isWindowPanelVisible("cameraEditArea"))
        {
            return "cameraEditArea";
        }

        if (this.isWindowPanelVisible("editArea"))
        {
            return "editArea";
        }

        return "cameraEditArea";
    }

    /**
     * Prefer unified → dedicated action host → general Properties (pre-
     * {@code actionEditArea} behaviour) → camera properties as last visible host.
     */
    private String resolveActionPropertiesPanelId()
    {
        if (this.shouldRedirectProperties())
        {
            return "unifiedEditArea";
        }

        if (this.isWindowPanelVisible("actionEditArea"))
        {
            return "actionEditArea";
        }

        if (this.isWindowPanelVisible("editArea"))
        {
            return "editArea";
        }

        if (this.isWindowPanelVisible("cameraEditArea"))
        {
            return "cameraEditArea";
        }

        return "actionEditArea";
    }

    private String resolveReplayPropertiesPanelId()
    {
        if (this.shouldRedirectProperties())
        {
            return "unifiedEditArea";
        }

        return "editArea";
    }

    private UIElement getPropertiesHostElement(String panelId)
    {
        if (panelId == null)
        {
            return null;
        }

        return this.panelById.get(panelId);
    }

    private boolean selectPanelInTabbedNode(EditorLayoutNode root, String panelId)
    {
        EditorLayoutNode.TabbedNode tabbed = this.findTabbedNodeContaining(root, panelId);

        if (tabbed == null)
        {
            return false;
        }

        for (int i = 0; i < tabbed.tabs.size(); i++)
        {
            EditorLayoutNode tab = tabbed.tabs.get(i);

            if (tab instanceof EditorLayoutNode.PanelNode && ((EditorLayoutNode.PanelNode) tab).getPanelId().equals(panelId))
            {
                if (tabbed.activeTab != i)
                {
                    tabbed.activeTab = i;

                    return true;
                }

                return false;
            }
        }

        return false;
    }

    private DropIntent resolveDropIntent(int mouseX, int mouseY)
    {
        String activeDragId = this.draggingPanelId != null ? this.draggingPanelId : this.activeDraggingFloatingPanelId;

        /* Per-panel dock cubes take priority: the window docks to the marker you're aiming at,
           not to a screen border just because the cursor drifted near one. */
        for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
        {
            if (!entry.getValue().isVisible()) continue;
            if (this.floatingPanels.contains(entry.getKey())) continue;
            if (this.hiddenPanels.contains(entry.getKey())) continue;
            if (entry.getKey().equals(activeDragId)) continue;

            Area area = entry.getValue().area;
            int guideZone = this.resolveDockGuideZoneFromMouse(area, mouseX, mouseY);

            if (guideZone != Integer.MIN_VALUE)
            {
                String targetId = entry.getKey();
                if (activeDragId != null && targetId.equals(activeDragId))
                {
                    if (guideZone != DROP_ZONE_CENTER && guideZone != DROP_ZONE_TAB)
                    {
                        String sibling = this.resolveSiblingTabId(targetId);
                        if (sibling != null)
                        {
                            targetId = sibling;
                        }
                    }
                }
                return new DropIntent(targetId, guideZone);
            }
        }

        /* Screen-edge (workspace) guides are only a fallback when no panel cube is hit. */
        DropIntent workspaceIntent = this.resolveWorkspaceDropIntent(mouseX, mouseY);

        if (workspaceIntent != null)
        {
            return workspaceIntent;
        }

        /* Floating windows dock only through the cube/edge markers handled above; skip the
           loose area-based docking so they never snap to a panel border on release. */
        if (this.activeDraggingFloatingPanelId != null)
        {
            return null;
        }

        for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
        {
            if (!entry.getValue().isVisible()) continue;
            if (this.floatingPanels.contains(entry.getKey())) continue;
            if (this.hiddenPanels.contains(entry.getKey())) continue;
            if (entry.getKey().equals(activeDragId)) continue;

            Area area = entry.getValue().area;
            if (area.isInside(mouseX, mouseY))
            {
                String targetId = entry.getKey();
                int zone = this.resolveDropZone(area, mouseX, mouseY);
                
                if (activeDragId != null && targetId.equals(activeDragId))
                {
                    if (zone != DROP_ZONE_CENTER && zone != DROP_ZONE_TAB)
                    {
                        String sibling = this.resolveSiblingTabId(targetId);
                        if (sibling != null)
                        {
                            targetId = sibling;
                        }
                    }
                }

                return new DropIntent(targetId, zone);
            }
        }

        return null;
    }

    private void renderPanelDragHandle(UIContext context, UIDraggable handle)
    {
        /* The card title bar (renderDockedPanelHeaders) is the visual; this handle is
           just the invisible drag region covering it. */
    }

    private IKey getPanelTitle(String panelId)
    {
        switch (panelId)
        {
            case "cameraEditor": return UIKeys.FILM_OPEN_CAMERA_EDITOR;
            case "replayEditor": return UIKeys.FILM_OPEN_REPLAY_EDITOR;
            case "actionEditor": return UIKeys.FILM_OPEN_ACTION_EDITOR;
            case ANCHORED_REPLAYS_PANEL_ID: return UIKeys.FILM_REPLAY_TITLE;
            case ANCHORED_REPLAYS_PROPERTIES_PANEL_ID: return UIKeys.FILM_REPLAY_SECTION_GENERAL;
            case "editArea": return UIKeys.RAW_PROPERTIES;
            case "cameraEditArea": return UIKeys.FILM_WORKSPACE_CAMERA_PROPERTIES;
            case "actionEditArea": return UIKeys.FILM_WORKSPACE_ACTION_PROPERTIES;
            case "unifiedEditArea": return UIKeys.FILM_WORKSPACE_UNIFIED_PROPERTIES;
            case "preview": return UIKeys.RAW_VIEWPORT;
            case "main": return UIKeys.RAW_TIMELINE;
            case "cameraTimeline": return UIKeys.FILM_CAMERA_TIMELINE;
            case "replayTimeline": return UIKeys.FILM_REPLAY_TIMELINE;
            case "actionTimeline": return UIKeys.FILM_ACTION_TIMELINE;
        }

        if (panelId.startsWith("videoPanel"))
        {
            VideoClip clip = this.videoClipsByPanelId.get(panelId);

            if (clip != null)
            {
                if (!clip.title.get().isEmpty())
                {
                    return IKey.raw(clip.title.get());
                }

                String videoPath = clip.video.get();

                if (!videoPath.isEmpty())
                {
                    String name = videoPath;

                    if (name.startsWith("external:"))
                    {
                        name = name.substring("external:".length()).trim();
                    }

                    int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));

                    if (lastSlash >= 0 && lastSlash < name.length() - 1)
                    {
                        name = name.substring(lastSlash + 1);
                    }

                    return IKey.raw("Video (" + name + ")");
                }
            }

            return UIKeys.C_CLIP.get("bbs:video");
        }

        return IKey.raw(panelId);
    }

    private Icon getPanelIcon(String panelId)
    {
        switch (panelId)
        {
            case "cameraEditor": return Icons.FRUSTUM;
            case "replayEditor": return Icons.SCENE;
            case "actionEditor": return Icons.ACTION;
            case ANCHORED_REPLAYS_PANEL_ID: return Icons.EDITOR;
            case ANCHORED_REPLAYS_PROPERTIES_PANEL_ID: return Icons.EDIT;
            case "editArea": return Icons.EDIT;
            case "cameraEditArea": return Icons.EDIT;
            case "actionEditArea": return Icons.EDIT;
            case "unifiedEditArea": return Icons.EDIT;
            case "preview": return Icons.CAMERA;
            case "main": return Icons.STOPWATCH;
            case "cameraTimeline": return Icons.FRUSTUM;
            case "replayTimeline": return Icons.SCENE;
            case "actionTimeline": return Icons.ACTION;
        }

        if (panelId.startsWith("videoPanel"))
        {
            return Icons.IMAGE;
        }

        return Icons.FILM;
    }

    public String[] getWindowPanelIds()
    {
        List<String> ids = new ArrayList<>(Arrays.asList("cameraTimeline", "replayTimeline", "actionTimeline", "preview", "editArea", "cameraEditArea", "actionEditArea", "unifiedEditArea", ANCHORED_REPLAYS_PANEL_ID, ANCHORED_REPLAYS_PROPERTIES_PANEL_ID));

        for (String panelId : this.videoClipsByPanelId.keySet())
        {
            ids.add(panelId);
        }

        return ids.toArray(new String[0]);
    }

    public void applySeparateReplayPropertiesPanelSetting()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.getFilmLayoutRoot();
        EditorLayoutNode next = root;

        if (!this.isSeparateReplayPropertiesPanelEnabled())
        {
            /* Same as unchecking Windows > General: keep the layout tree intact and embed
               properties into Replays. Avoid removing the leaf so unrelated splitters/tabs
               are not reshuffled. */
            this.hiddenPanels.add(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID);
            this.collapsedFloatingPanels.remove(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID);
            this.collapsedDockedPanels.remove(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID);
        }
        else
        {
            this.hiddenPanels.remove(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID);

            if (root != null
                && this.hasPanelInLayout(root, ANCHORED_REPLAYS_PANEL_ID)
                && !this.hasPanelInLayout(root, ANCHORED_REPLAYS_PROPERTIES_PANEL_ID)
                && !this.floatingPanels.contains(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID))
            {
                next = this.migrateReplaysPropertiesPanel(root);
            }
        }

        if (next != root)
        {
            layout.setFilmLayoutRoot(next);
        }

        this.syncReplaysPropertiesLayoutMode();
        this.setupEditorFlex(true);
        this.persistFilmUILayoutSession();
    }

    private boolean isSeparateReplayPropertiesPanelEnabled()
    {
        return BBSSettings.editorSeparateReplayPropertiesPanel == null || BBSSettings.editorSeparateReplayPropertiesPanel.get();
    }

    public void applyEmbeddedKeyframeSidePanelSetting()
    {
        if (this.cameraEditor != null && this.cameraEditor.clips != null)
        {
            this.cameraEditor.clips.applyEmbeddedKeyframePropertiesMode();
        }

        if (this.actionEditor != null && this.actionEditor.clips != null)
        {
            this.actionEditor.clips.applyEmbeddedKeyframePropertiesMode();
        }
    }

    /**
     * Select the general Properties tab (or unified properties) used by replay
     * keyframes and by embedded clip keyframe editors when the side-panel
     * overlay setting is disabled. Called when the user picks a keyframe so the
     * keyframe property panel is visible.
     */
    public void focusEmbeddedKeyframePropertiesTab()
    {
        if (this.undoHandler != null && this.undoHandler.isUndoing())
        {
            return;
        }

        if (this.suppressLinkedPropertiesTabFocus > 0)
        {
            this.syncKeyframePropertiesHosts();

            return;
        }

        String panelId = this.shouldRedirectProperties() ? "unifiedEditArea" : "editArea";

        this.focusPanelTab(panelId);
        /* Same edge case as {@link #focusLinkedPropertiesTab}: tab may already be active. */
        this.syncKeyframePropertiesHosts();
    }

    /**
     * After leaving an embedded clip keyframe view, show the clip form tab again.
     * Picking a keyframe focuses {@code editArea} (Properties); once the embed
     * closes that panel is empty while a clip remains selected, so restore
     * Camera/Action Properties (or the unified properties host).
     * <p>
     * Uses {@code recreateTabs=false}: a full tab-bar rebuild here (via
     * {@link #focusPanelTab}) mid clip-select / embed-close left the film
     * workspace letterboxed with a black strip on the right.
     */
    public void focusClipPropertiesTab(boolean cameraTimeline)
    {
        String panelId = cameraTimeline
            ? this.resolveCameraPropertiesPanelId()
            : this.resolveActionPropertiesPanelId();

        EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();
        boolean changed = root != null && this.selectPanelInTabbedNode(root, panelId);

        if (changed)
        {
            BBSSettings.editorLayoutSettings.setFilmLayoutRoot(root);
        }

        UIElement panel = this.panelById.get(panelId);
        boolean needsRefresh = panel != null && !panel.isVisible();

        if (changed || needsRefresh)
        {
            this.setupEditorFlex(true, false, false);
        }
        else
        {
            this.syncKeyframePropertiesHosts();
        }
    }

    /**
     * After Alt-picking a replay in the viewport: select the Replays ("Reproducción")
     * panel when it lives in a multi-tab group (more useful than empty properties tabs).
     * Still activates the replay timeline; linked properties are only focused when the
     * Replays panel cannot be selected as a tab.
     */
    public void focusAfterAltReplayPick()
    {
        EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();
        boolean focusedReplaysTab = false;

        if (root != null
            && !this.hiddenPanels.contains(ANCHORED_REPLAYS_PANEL_ID)
            && !this.floatingPanels.contains(ANCHORED_REPLAYS_PANEL_ID))
        {
            EditorLayoutNode.TabbedNode tabbed = this.findTabbedNodeContaining(root, ANCHORED_REPLAYS_PANEL_ID);

            if (tabbed != null && tabbed.tabs.size() >= 2)
            {
                focusedReplaysTab = true;
            }
        }

        /* Switch to the replay/keyframes tab even when another timeline tab is active. */
        this.focusPanelTab("replayTimeline");

        if (focusedReplaysTab)
        {
            this.beginSuppressLinkedPropertiesTabFocus();

            try
            {
                this.showPanel(this.replayEditor);
            }
            finally
            {
                this.endSuppressLinkedPropertiesTabFocus();
            }

            this.focusPanelTab(ANCHORED_REPLAYS_PANEL_ID);
        }
        else
        {
            this.focusLinkedPropertiesTab("replayTimeline");
            this.showPanel(this.replayEditor);
        }
    }

    /**
     * Activate {@code panelId} inside its tab group without clearing timeline
     * selections or re-entering through {@link #focusLinkedPropertiesTab}.
     */
    public void focusPanelTab(String panelId)
    {
        if (panelId == null)
        {
            return;
        }

        EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();
        boolean changed = root != null && this.selectPanelInTabbedNode(root, panelId);

        if (changed)
        {
            BBSSettings.editorLayoutSettings.setFilmLayoutRoot(root);
        }

        UIElement panel = this.panelById.get(panelId);
        boolean needsRefresh = panel != null && !panel.isVisible();

        if (changed || needsRefresh)
        {
            this.setupEditorFlex(true, false, true);
        }
    }

    public IKey getWindowPanelTitle(String panelId)
    {
        return this.getPanelTitle(panelId);
    }

    public Icon getWindowPanelIcon(String panelId)
    {
        return this.getPanelIcon(panelId);
    }

    public boolean shouldRedirectProperties()
    {
        return this.unifiedEditArea != null && this.hasPanelInLayout(BBSSettings.editorLayoutSettings.getFilmLayoutRoot(), "unifiedEditArea") && !this.hiddenPanels.contains("unifiedEditArea");
    }

    public void updateTargets()
    {
        UIElement cameraHost = this.getPropertiesHostElement(this.resolveCameraPropertiesPanelId());
        UIElement actionHost = this.getPropertiesHostElement(this.resolveActionPropertiesPanelId());
        UIElement replayHost = this.getPropertiesHostElement(this.resolveReplayPropertiesPanelId());

        if (cameraHost == null)
        {
            cameraHost = this.cameraEditArea;
        }

        if (actionHost == null)
        {
            actionHost = this.actionEditArea;
        }

        if (replayHost == null)
        {
            replayHost = this.editArea;
        }

        boolean cameraChanged = this.cameraEditor != null && this.cameraEditor.getTarget() != cameraHost;
        boolean actionChanged = this.actionEditor != null && this.actionEditor.getTarget() != actionHost;

        this.cameraEditor.target(cameraHost);
        this.actionEditor.target(actionHost);

        if (this.replayEditor != null && this.replayEditor.keyframeEditor != null)
        {
            this.replayEditor.keyframeEditor.target(replayHost);
        }

        /* Moving an open clip form onto the fallback host (hidden dedicated panel). */
        if (cameraChanged)
        {
            this.cameraEditor.remountClipPanel();
        }

        if (actionChanged)
        {
            this.actionEditor.remountClipPanel();
        }

        this.applyEmbeddedKeyframeSidePanelSetting();
    }

    public boolean isWindowPanelVisible(String panelId)
    {
        if (panelId == null)
        {
            return false;
        }

        /* Hidden panels are still present in the layout tree (or may remain floating),
           but must be treated as not visible for the Window menu state. */
        if (this.hiddenPanels.contains(panelId))
        {
            return false;
        }

        return this.floatingPanels.contains(panelId) || this.hasPanelInLayout(BBSSettings.editorLayoutSettings.getFilmLayoutRoot(), panelId);
    }

    public void setWindowPanelVisible(String panelId, boolean visible)
    {
        if (panelId == null)
        {
            return;
        }

        if (!this.panelById.containsKey(panelId))
        {
            return;
        }

        if (visible)
        {
            this.hiddenPanels.remove(panelId);

            if (!this.floatingPanels.contains(panelId) && !this.hasPanelInLayout(BBSSettings.editorLayoutSettings.getFilmLayoutRoot(), panelId))
            {
                if (ANCHORED_REPLAYS_PROPERTIES_PANEL_ID.equals(panelId))
                {
                    EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();
                    boolean canDockUnderReplays = root != null
                        && this.hasPanelInLayout(root, ANCHORED_REPLAYS_PANEL_ID)
                        && !this.floatingPanels.contains(ANCHORED_REPLAYS_PANEL_ID)
                        && this.isSeparateReplayPropertiesPanelEnabled();

                    if (canDockUnderReplays)
                    {
                        /* Legacy / incomplete trees: dock General under Replays rather than float. */
                        BBSSettings.editorLayoutSettings.setFilmLayoutRoot(
                            EditorLayoutNode.copyWithReplacedLeaf(root, ANCHORED_REPLAYS_PANEL_ID, this.createAnchoredReplaysColumn()));
                    }
                    else
                    {
                        this.floatingPanels.add(panelId);
                        this.ensureFloatingPanelSize(panelId);

                        if (!this.floatingPanelPositions.containsKey(panelId))
                        {
                            this.floatingPanelPositions.put(panelId, new Vector2i(this.editor.area.mx() - 140, this.editor.area.my() - 120));
                        }
                    }
                }
                else
                {
                    this.dockPanelNearVisibleTarget(panelId);
                }
            }
        }
        else
        {
            ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
            EditorLayoutNode root = layout.getFilmLayoutRoot();
            /* Don't remove the panel from the layout tree when hiding through Window menu.
               Removing leaves can collapse splitters/tab groups and unintentionally reshuffle
               unrelated panels. Keep the docked layout stable and only toggle visibility via
               hiddenPanels; re-enabling restores it in-place. */

            this.hiddenPanels.add(panelId);
            this.collapsedDockedPanels.remove(panelId);
            this.collapsedFloatingPanels.remove(panelId);

            if (root != null)
            {
                EditorLayoutNode.TabbedNode tabbed = this.findTabbedNodeContaining(root, panelId);

                if (tabbed != null)
                {
                    /* If the hidden panel was the active tab, promote another tab immediately so
                       bounds/tab rendering don't target a leaf that was removed from the visible
                       layout tree (which would leave an empty gap in the tab group). */
                    this.promoteActiveTabIfHidden(tabbed);
                    layout.setFilmLayoutRoot(root);
                }
            }

        }

        if (ANCHORED_REPLAYS_PROPERTIES_PANEL_ID.equals(panelId))
        {
            this.syncReplaysPropertiesLayoutMode();
        }

        /* Retarget clip forms when Camera/Action/Properties/Unified visibility changes
         * so a selected action clip still appears in Properties if Action is hidden. */
        if ("cameraEditArea".equals(panelId)
            || "actionEditArea".equals(panelId)
            || "editArea".equals(panelId)
            || "unifiedEditArea".equals(panelId))
        {
            this.updateTargets();
        }

        this.setupEditorFlex(true);
        this.persistFilmUILayoutSession();
    }

    private void restoreFilmUILayoutSession()
    {
        if (BBSSettings.uiLayoutPreferences == null)
        {
            return;
        }

        this.hiddenPanels.clear();
        this.hiddenPanels.addAll(BBSSettings.uiLayoutPreferences.getFilmHiddenPanels());

        this.collapsedDockedPanels.clear();
        this.collapsedDockedPanels.addAll(BBSSettings.uiLayoutPreferences.getFilmCollapsedDocked());

        this.collapsedFloatingPanels.clear();
        this.collapsedFloatingPanels.addAll(BBSSettings.uiLayoutPreferences.getFilmCollapsedFloating());

        this.floatingPanels.clear();
        this.floatingPanelPositions.clear();
        this.floatingPanelSizes.clear();

        Map<String, ValueUILayoutPreferences.FilmFloatingPanelState> floatingPanels = BBSSettings.uiLayoutPreferences.getFilmFloatingPanels();

        for (Map.Entry<String, ValueUILayoutPreferences.FilmFloatingPanelState> entry : floatingPanels.entrySet())
        {
            String panelId = entry.getKey();
            ValueUILayoutPreferences.FilmFloatingPanelState state = entry.getValue();

            if (!this.panelById.containsKey(panelId) || state == null)
            {
                continue;
            }

            this.floatingPanels.add(panelId);
            this.floatingPanelPositions.put(panelId, new Vector2i(state.x, state.y));
            this.floatingPanelSizes.put(panelId, new Vector2i(Math.max(MIN_FLOATING_PANEL_WIDTH, state.w), Math.max(MIN_FLOATING_PANEL_HEIGHT, state.h)));

            if (state.collapsed)
            {
                this.collapsedFloatingPanels.add(panelId);
            }
        }

        if (!this.floatingPanels.isEmpty())
        {
            ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
            EditorLayoutNode root = layout.getFilmLayoutRoot();
            boolean changed = false;

            for (String panelId : new ArrayList<>(this.floatingPanels))
            {
                if (root != null && this.hasPanelInLayout(root, panelId))
                {
                    root = EditorLayoutNode.copyWithRemovedLeaf(root, panelId);
                    changed = true;
                }
            }

            if (changed)
            {
                layout.setFilmLayoutRoot(root);
            }
        }
    }

    private void persistFilmUILayoutSession()
    {
        if (BBSSettings.uiLayoutPreferences == null)
        {
            return;
        }

        Map<String, ValueUILayoutPreferences.FilmFloatingPanelState> floatingPanels = new LinkedHashMap<>();

        for (String panelId : this.floatingPanels)
        {
            Vector2i position = this.floatingPanelPositions.get(panelId);
            Vector2i size = this.floatingPanelSizes.get(panelId);

            if (position == null || size == null)
            {
                continue;
            }

            ValueUILayoutPreferences.FilmFloatingPanelState state = new ValueUILayoutPreferences.FilmFloatingPanelState();

            state.x = position.x;
            state.y = position.y;
            state.w = size.x;
            state.h = size.y;
            state.collapsed = this.collapsedFloatingPanels.contains(panelId);
            floatingPanels.put(panelId, state);
        }

        List<String> hiddenPanels = new ArrayList<>();

        for (String panelId : this.getWindowPanelIds())
        {
            if (this.hiddenPanels.contains(panelId))
            {
                hiddenPanels.add(panelId);
            }
        }

        BBSSettings.uiLayoutPreferences.setFilmSession(
            hiddenPanels,
            floatingPanels,
            new ArrayList<>(this.collapsedDockedPanels),
            new ArrayList<>(this.collapsedFloatingPanels)
        );
    }

    private void dockPanelNearVisibleTarget(String panelId)
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.getFilmLayoutRoot();
        String target = this.getFirstDockedVisiblePanelId();

        if (root == null || target == null)
        {
            layout.setFilmLayoutRoot(new EditorLayoutNode.PanelNode(panelId));
            return;
        }

        layout.setFilmLayoutRoot(EditorLayoutNode.copyWithInsertSplitAt(root, target, panelId, EditorLayoutNode.EDGE_RIGHT));
    }

    private String getFirstDockedVisiblePanelId()
    {
        EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();

        for (String id : this.getWindowPanelIds())
        {
            if (!this.hiddenPanels.contains(id) && !this.floatingPanels.contains(id) && this.hasPanelInLayout(root, id))
            {
                return id;
            }
        }

        return null;
    }

    private DropIntent resolveWorkspaceDropIntent(int mouseX, int mouseY)
    {
        if (!this.editor.area.isInside(mouseX, mouseY))
        {
            return null;
        }

        int margin = Math.max(34, Math.min(72, Math.min(this.editor.area.w, this.editor.area.h) / 10));
        int zone = Integer.MIN_VALUE;

        if (mouseX <= this.editor.area.x + margin)
        {
            zone = EditorLayoutNode.EDGE_LEFT;
        }
        else if (mouseX >= this.editor.area.ex() - margin)
        {
            zone = EditorLayoutNode.EDGE_RIGHT;
        }
        else if (mouseY <= this.editor.area.y + margin)
        {
            zone = EditorLayoutNode.EDGE_TOP;
        }
        else if (mouseY >= this.editor.area.ey() - margin)
        {
            zone = EditorLayoutNode.EDGE_BOTTOM;
        }

        return zone == Integer.MIN_VALUE ? null : new DropIntent(DROP_TARGET_WORKSPACE, zone);
    }

    private int getDockedVisiblePanelCount()
    {
        EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();
        int count = 0;

        for (String id : this.getWindowPanelIds())
        {
            if (!this.hiddenPanels.contains(id) && !this.floatingPanels.contains(id) && this.hasPanelInLayout(root, id))
            {
                count++;
            }
        }

        return count;
    }

    private boolean hasAnyDockedVisiblePanel()
    {
        return this.getDockedVisiblePanelCount() > 0;
    }

    private String getWorkspaceEdgeTargetPanelId(int zone)
    {
        EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();
        Map<String, float[]> bounds = this.computePanelBounds(root);
        String best = null;
        float bestScore = zone == EditorLayoutNode.EDGE_RIGHT || zone == EditorLayoutNode.EDGE_BOTTOM ? -1F : Float.MAX_VALUE;

        for (String id : this.getWindowPanelIds())
        {
            if (this.hiddenPanels.contains(id) || this.floatingPanels.contains(id) || !bounds.containsKey(id))
            {
                continue;
            }

            float[] b = bounds.get(id);
            float score;

            if (zone == EditorLayoutNode.EDGE_LEFT)
            {
                score = b[0];
                if (score < bestScore)
                {
                    bestScore = score;
                    best = id;
                }
            }
            else if (zone == EditorLayoutNode.EDGE_RIGHT)
            {
                score = b[0] + b[2];
                if (score > bestScore)
                {
                    bestScore = score;
                    best = id;
                }
            }
            else if (zone == EditorLayoutNode.EDGE_TOP)
            {
                score = b[1];
                if (score < bestScore)
                {
                    bestScore = score;
                    best = id;
                }
            }
            else if (zone == EditorLayoutNode.EDGE_BOTTOM)
            {
                score = b[1] + b[3];
                if (score > bestScore)
                {
                    bestScore = score;
                    best = id;
                }
            }
        }

        return best;
    }

    public void openLayoutPresetsFromMenu(int x, int y)
    {
        if (this.layoutPresetsController == null)
        {
            return;
        }

        this.ensureDefaultLayoutPresets();
        this.layoutPresetsController.openPresets(this.getContext(), x, y);
    }

    /* Open the undo/redo history overlay (now reached from the menu bar's Edit menu). */
    public void openUndoHistory()
    {
        if (this.undoHandler == null)
        {
            return;
        }

        UIOverlay.addOverlay(this.getContext(), new UIUndoHistoryOverlay(this).resizable().minSize(300, 220), 300, 0.6F);
    }

    /* Open the render queue overlay (now reached from a viewport button). */
    public void openRenderQueueOverlay()
    {
        UIOverlay.addOverlay(this.getContext(), new UIRenderQueueOverlayPanel(this), 500, 0.65F);
    }

    /* Append the film-specific Tools menu operations (move film, insert space, replace inventory)
       that used to live on the save icon's right-click menu. */
    public void addToolMenuActions(ContextMenuManager menu)
    {
        if (this.toolMenuActions != null)
        {
            this.toolMenuActions.accept(menu);
        }
    }

    /**
     * Seed the editor layouts as real, file-backed presets so they show up in the presets
     * overlay just like user-saved layouts.
     */
    private void ensureDefaultLayoutPresets()
    {
        this.deleteLegacyLayoutPresets();

        for (String[] preset : DEFAULT_LAYOUT_PRESETS)
        {
            this.ensureDefaultLayoutPreset(preset[0], preset[1]);
        }
    }

    private void deleteLegacyLayoutPresets()
    {
        for (String id : LEGACY_LAYOUT_PRESETS)
        {
            PresetManager.LAYOUTS.delete(id);
        }
    }

    private void ensureDefaultLayoutPreset(String id, String resource)
    {
        MapType data = this.loadDefaultLayoutPreset(resource);

        if (data != null)
        {
            PresetManager.LAYOUTS.save(id, data);
        }
    }

    private MapType loadDefaultLayoutPreset(String resource)
    {
        try (InputStream stream = UIFilmPanel.class.getResourceAsStream("/assets/bbs/layout_presets/" + resource + ".json"))
        {
            if (stream == null)
            {
                return null;
            }

            BaseType data = DataToString.fromString(new String(stream.readAllBytes(), StandardCharsets.UTF_8));

            return data != null && data.isMap() ? data.asMap() : null;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public void applyLayoutPreset(int layoutId)
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        /* A built-in layout describes a fully docked arrangement, so pull every window
           back out of the floating/hidden state — otherwise undocked windows would stay
           floating and never reappear in the chosen layout. */
        this.clearPanelDragState();
        this.floatingPanels.clear();
        this.collapsedFloatingPanels.clear();
        this.hiddenPanels.clear();

        layout.setLayout(layoutId);
        this.applyLegacyLayoutSelection();
        this.setupEditorFlex(true);
        this.persistFilmUILayoutSession();
    }

    public boolean isLayoutPresetSelected(int layoutId)
    {
        return BBSSettings.editorLayoutSettings.getLayout() == layoutId;
    }

    public boolean isLayoutLocked()
    {
        return BBSSettings.editorLayoutSettings.isLayoutLocked();
    }

    public void toggleLayoutLockFromMenu()
    {
        this.toggleLayoutLock();
    }

    public void resetLayout()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        this.clearPanelDragState();
        this.floatingPanels.clear();
        this.collapsedFloatingPanels.clear();
        this.collapsedDockedPanels.clear();
        this.hiddenPanels.clear();
        layout.setFilmLayoutRoot(this.addAnchoredReplaysPanelToRoot(EditorLayoutNode.defaultFilmLayout()));
        BBSSettings.timelineToolbarDocks.resetToDefaults();
        TimelineToolbarDockSync.refreshFilmPanel(this);
        this.setupEditorFlex(true);
        this.persistFilmUILayoutSession();
    }

    private void renderPanelWindowSurfaces(UIContext context)
    {
        if (this.showingHomePage)
        {
            return;
        }

        int borderColor = 0xFF323232;
        boolean locked = BBSSettings.editorLayoutSettings.isLayoutLocked();

        if (locked)
        {
            for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
            {
                String panelId = entry.getKey();
                UIElement el = entry.getValue();

                if (el == null || !el.isVisible() || this.floatingPanels.contains(panelId) || this.hiddenPanels.contains(panelId))
                {
                    continue;
                }

                int x = el.area.x;
                int ex = el.area.ex();
                int y = el.area.y;
                int ey = el.area.ey();

                context.batcher.outline(x - 1, y - 1, ex + 1, ey + 1, borderColor);
                context.batcher.box(x, y, ex, ey, 0xFF111115);
            }
        }
        else
        {
            for (String panelId : this.dockedHeaderPanels)
            {
                UIElement el = this.panelById.get(panelId);

                if (el == null)
                {
                    continue;
                }

                int x = el.area.x;
                int ex = el.area.ex();
                int y = el.area.y - PANEL_HEADER_HEIGHT;
                boolean collapsed = this.collapsedDockedPanels.contains(panelId);
                int ey = collapsed ? el.area.y : el.area.ey();

                context.batcher.outline(x - 1, y - 1, ex + 1, ey + 1, borderColor);

                if (!collapsed)
                {
                    context.batcher.box(x, el.area.y, ex, ey, 0xFF111115);
                }
            }

            for (UITabBar tabBar : this.tabBars)
            {
                if (!tabBar.isVisible())
                {
                    continue;
                }

                UIElement active = tabBar.getActivePanel();
                if (active == null || !active.isVisible())
                {
                    continue;
                }

                int x = active.area.x;
                int ex = active.area.ex();
                int y = tabBar.area.y;
                int ey = active.area.ey();

                context.batcher.outline(x - 1, y - 1, ex + 1, ey + 1, borderColor);
                context.batcher.box(x, active.area.y, ex, ey, 0xFF111115);
            }
        }
    }

    /**
     * Opaque backdrop + border for each floating window. This is rendered as its own layer in
     * the editor's child list — directly beneath the floating window's content element — so the
     * backdrop actually blocks docked panels behind it instead of being painted over by them.
     */
    private void renderFloatingPanelBackgrounds(UIContext context)
    {
        if (this.showingHomePage)
        {
            return;
        }

        for (String panelId : this.floatingPanels)
        {
            if (this.hiddenPanels.contains(panelId))
            {
                continue;
            }

            UIElement el = this.panelById.get(panelId);
            Vector2i pos = this.floatingPanelPositions.get(panelId);
            Vector2i size = this.floatingPanelSizes.get(panelId);

            if (el == null || pos == null || size == null)
            {
                continue;
            }

            boolean collapsed = this.collapsedFloatingPanels.contains(panelId);

            int x = this.editor.area.x + pos.x;
            int y = this.editor.area.y + pos.y;
            int w = size.x;
            int h = collapsed ? PANEL_HEADER_HEIGHT : size.y;

            context.batcher.outline(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF5A5A5A);

            if (!collapsed)
            {
                /* Floating windows are fully opaque so neither the 3D viewport nor docked
                   panels behind them bleed through the panel contents. */
                context.batcher.box(x, y + PANEL_HEADER_HEIGHT, x + w, y + h, 0xFF141418);
            }
        }
    }

    /* Card-style title bar for every docked, non-tabbed window: the same
       premium dark header and primary-color hover treatment as model blocks. */
    private void renderDockedPanelHeaders(UIContext context)
    {
        if (this.showingHomePage)
        {
            return;
        }

        for (String panelId : this.dockedHeaderPanels)
        {
            UIElement el = this.panelById.get(panelId);

            if (el == null)
            {
                continue;
            }

            int x = el.area.x;
            int ex = el.area.ex();
            int by = el.area.y;
            int y = by - PANEL_HEADER_HEIGHT;

            this.renderCardHeader(context, panelId, x, y, ex, by, this.collapsedDockedPanels.contains(panelId));
        }
    }

    private void renderCardHeader(UIContext context, String panelId, int x, int y, int ex, int ey, boolean collapsed)
    {
        int h = ey - y;

        context.batcher.box(x, y, ex, ey, 0xFF1D1D1D);
        context.batcher.box(x, ey - 1, ex, ey, 0xFF3C3C3C);

        boolean hovered = context.mouseX >= x && context.mouseX < ex && context.mouseY >= y && context.mouseY < ey;
        int color = hovered ? (0xFF000000 | BBSSettings.primaryColor.get()) : 0xFFFFFFFF;
        int textY = y + (h - context.batcher.getFont().getHeight()) / 2;

        if (ex - x >= 18)
        {
            context.batcher.icon(this.getPanelIcon(panelId), color, x + 11, y + h / 2, 0.5F, 0.5F);
        }

        int titleX = x + 22;
        int titleW = ex - titleX - 22;

        if (titleW > 4)
        {
            String title = context.batcher.getFont().limitToWidth(this.getPanelTitle(panelId).get(), titleW);

            context.batcher.text(title, titleX, textY, color);
        }

        Icon chevronIcon = collapsed ? Icons.COLLAPSED : Icons.UNCOLLAPSED;

        if (ex - x >= 28)
        {
            context.batcher.icon(chevronIcon, color, ex - 11, y + h / 2, 0.5F, 0.5F);
        }
    }

    private int resolveDropZone(Area area, int mouseX, int mouseY)
    {
        int guideZone = this.resolveDockGuideZoneFromMouse(area, mouseX, mouseY);
        if (guideZone != Integer.MIN_VALUE)
        {
            return guideZone;
        }

        float nx = area.w <= 0 ? 0.5F : MathUtils.clamp((mouseX - area.x) / (float) area.w, 0F, 1F);
        float ny = area.h <= 0 ? 0.5F : MathUtils.clamp((mouseY - area.y) / (float) area.h, 0F, 1F);
        float edge = DROP_EDGE_MARGIN;

        if (nx > edge && nx < 1F - edge && ny > edge && ny < 1F - edge)
        {
            return DROP_ZONE_CENTER;
        }

        float left = nx;
        float right = 1F - nx;
        float top = ny;
        float bottom = 1F - ny;

        float nearest = left;
        int zone = EditorLayoutNode.EDGE_LEFT;
        if (right < nearest)
        {
            nearest = right;
            zone = EditorLayoutNode.EDGE_RIGHT;
        }
        if (top < nearest)
        {
            nearest = top;
            zone = EditorLayoutNode.EDGE_TOP;
        }
        if (bottom < nearest)
        {
            zone = EditorLayoutNode.EDGE_BOTTOM;
        }

        return zone;
    }

    private int resolveDockGuideZoneFromMouse(Area area, int mouseX, int mouseY)
    {
        /* Name bar (tab/stack) takes priority over the edge split markers; there is no separate
           center cube — the whole name bar is the "stack into one" target now. */
        int[] zones = new int[] {
            DROP_ZONE_TAB,
            EditorLayoutNode.EDGE_LEFT,
            EditorLayoutNode.EDGE_RIGHT,
            EditorLayoutNode.EDGE_TOP,
            EditorLayoutNode.EDGE_BOTTOM
        };
        int hitPadding = 8;

        for (int zone : zones)
        {
            int[] rect = this.getDockGuideRect(area, zone);
            if (rect == null)
            {
                continue;
            }

            if (mouseX >= rect[0] - hitPadding && mouseX <= rect[2] + hitPadding && mouseY >= rect[1] - hitPadding && mouseY <= rect[3] + hitPadding)
            {
                return zone;
            }
        }

        return Integer.MIN_VALUE;
    }

    private static class DropIntent
    {
        private final String targetId;
        private final int zone;

        private DropIntent(String targetId, int zone)
        {
            this.targetId = targetId;
            this.zone = zone;
        }
    }

    private void renderDropZoneHighlight(UIContext context)
    {
        if (this.showingHomePage)
        {
            return;
        }

        String activeDragId = this.draggingPanelId != null ? this.draggingPanelId : this.activeDraggingFloatingPanelId;
        if (BBSSettings.editorLayoutSettings.isLayoutLocked() || activeDragId == null)
        {
            return;
        }

        /* Refresh each frame so holding/releasing Alt updates snap without needing mouse movement. */
        this.updateDropTargetFromMouse(context.mouseX, context.mouseY);

        boolean snapCancelled = this.isDockSnapCancelled();
        DropIntent nearSnap = this.resolveDropIntent(context.mouseX, context.mouseY);

        /* Snapping to a whole-workspace edge: show just that single edge guide. */
        if (nearSnap != null && DROP_TARGET_WORKSPACE.equals(nearSnap.targetId))
        {
            this.renderDockGuideZone(context, this.editor.area, nearSnap.zone, !snapCancelled, snapCancelled);

            if (!snapCancelled)
            {
                this.renderDropPreviewLayout(context);
                this.renderDockSnapCancelTip(context, activeDragId);
            }

            return;
        }

        /* Otherwise show the full set of dock guides on whichever docked panel the cursor is
           currently over — continuously, for the whole drag, not only once a zone is hit. This
           is what makes docking discoverable: the guide cubes are always visible to aim at. */
        String guidePanelId = this.resolveDockGuidePanelId(context.mouseX, context.mouseY, activeDragId);

        if (guidePanelId == null)
        {
            return;
        }

        UIElement target = this.panelById.get(guidePanelId);

        if (target == null)
        {
            return;
        }

        Area targetArea = target.area;
        boolean targeted = nearSnap != null && guidePanelId.equals(nearSnap.targetId);
        int[] zones = new int[] {
            EditorLayoutNode.EDGE_LEFT,
            EditorLayoutNode.EDGE_RIGHT,
            EditorLayoutNode.EDGE_TOP,
            EditorLayoutNode.EDGE_BOTTOM,
            DROP_ZONE_TAB
        };

        for (int zone : zones)
        {
            boolean active = !snapCancelled && targeted && nearSnap != null && zone == nearSnap.zone;

            this.renderDockGuideZone(context, targetArea, zone, active, snapCancelled);
        }

        if (!snapCancelled)
        {
            this.renderDropPreviewLayout(context);

            if (nearSnap != null)
            {
                this.renderDockSnapCancelTip(context, activeDragId);
            }
        }
    }

    private void renderDockSnapCancelTip(UIContext context, String activeDragId)
    {
        if (activeDragId == null)
        {
            return;
        }

        String label = UIKeys.FILM_LAYOUT_SNAP_CANCEL_TIP.get();
        int textW = context.batcher.getFont().getWidth(label);
        int textH = context.batcher.getFont().getHeight();
        int tipX;
        int tipY;

        if (this.floatingPanels.contains(activeDragId))
        {
            Vector2i pos = this.floatingPanelPositions.get(activeDragId);
            Vector2i size = this.floatingPanelSizes.get(activeDragId);

            if (pos == null || size == null)
            {
                return;
            }

            int panelX = this.editor.area.x + pos.x;
            int panelY = this.editor.area.y + pos.y;
            int panelW = size.x;

            tipX = panelX + (panelW - textW) / 2;
            tipY = panelY - textH - 12;
        }
        else
        {
            UIElement panel = this.panelById.get(activeDragId);

            if (panel == null)
            {
                return;
            }

            tipX = panel.area.mx() - textW / 2;
            tipY = panel.area.y - PANEL_HEADER_HEIGHT - textH - 12;
        }

        tipX = MathUtils.clamp(tipX, this.editor.area.x + 4, Math.max(this.editor.area.x + 4, this.editor.area.ex() - textW - 4));
        tipY = Math.max(this.editor.area.y + 4, tipY);

        context.batcher.textCard(label, tipX, tipY, Colors.WHITE, Colors.A75);
    }

    private String resolveDockGuidePanelId(int mouseX, int mouseY, String activeDragId)
    {
        for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
        {
            if (!entry.getValue().isVisible())
            {
                continue;
            }

            if (this.floatingPanels.contains(entry.getKey()) || this.hiddenPanels.contains(entry.getKey()))
            {
                continue;
            }

            if (entry.getKey().equals(activeDragId))
            {
                continue;
            }

            /* Include the name bar (the title bar strip just above the content) so the tab marker
               shows and resolves when the cursor is over it, not only over the body. */
            Area area = entry.getValue().area;

            if (mouseX >= area.x && mouseX <= area.ex()
                && mouseY >= area.y - PANEL_HEADER_HEIGHT && mouseY <= area.ey())
            {
                return entry.getKey();
            }
        }

        return null;
    }

    private void renderDockGuideZone(UIContext context, Area area, int zone, boolean active)
    {
        this.renderDockGuideZone(context, area, zone, active, false);
    }

    private void renderDockGuideZone(UIContext context, Area area, int zone, boolean active, boolean suppressed)
    {
        int[] rect = this.getDockGuideRect(area, zone);
        if (rect == null)
        {
            return;
        }

        int baseColor = BBSSettings.primaryColor.get();
        int cx = (rect[0] + rect[2]) / 2;
        int cy = (rect[1] + rect[3]) / 2;
        int bg;
        int border;
        int coreColor;
        int core;

        if (suppressed)
        {
            int darkBase = Colors.mulRGB(0xFF000000 | baseColor, 0.55F);
            int darkBorder = Colors.mulRGB(0xFF000000 | baseColor, 0.4F);

            bg = Colors.setA(darkBase, 0.5F);
            border = Colors.setA(darkBorder, 0.5F);
            coreColor = Colors.setA(Colors.WHITE, 0.5F);
            core = 4;
            active = false;
        }
        else
        {
            bg = active ? Colors.setA(baseColor, 0.85F) : 0xEE1A1A20;
            border = active ? 0xFFFFFFFF : (0xFF000000 | baseColor);
            coreColor = 0xFFFFFFFF;
            core = active ? 5 : 4;
        }

        context.batcher.box(rect[0], rect[1], rect[2], rect[3], bg);
        context.batcher.outline(rect[0], rect[1], rect[2], rect[3], border);
        context.batcher.box(cx - core, cy - core, cx + core, cy + core, coreColor);

        if (active)
        {
            context.batcher.outline(rect[0] - 1, rect[1] - 1, rect[2] + 1, rect[3] + 1, 0x88FFFFFF);
        }
    }

    private int[] getDockGuideRect(Area area, int zone)
    {
        int x = area.x;
        int y = area.y;
        int ex = area.ex();
        int ey = area.ey();

        if (zone == DROP_ZONE_TAB)
        {
            /* The "stack as a tab" marker spans the target window's whole name bar — the title
               bar strip just above its content — so dropping anywhere on the name bar tabs the
               two windows together into one. */
            int m = 2;
            int top = y - PANEL_HEADER_HEIGHT + m;
            int bottom = y - m;

            if (bottom - top <= 2 || (ex - m) - (x + m) <= 2)
            {
                return null;
            }

            return new int[] {x + m, top, ex - m, bottom};
        }

        int w = Math.max(1, ex - x);
        int h = Math.max(1, ey - y);
        int size = 28;
        int half = size / 2;
        int cx = x + w / 2;
        int cy = y + h / 2;
        int orbitX = Math.max(size, Math.round(w * 0.24F));
        int orbitY = Math.max(size, Math.round(h * 0.24F));
        int rx1 = cx - half;
        int ry1 = cy - half;

        switch (zone)
        {
            case EditorLayoutNode.EDGE_LEFT:
                rx1 = cx - orbitX - half;
                ry1 = cy - half;
                break;
            case EditorLayoutNode.EDGE_RIGHT:
                rx1 = cx + orbitX - half;
                ry1 = cy - half;
                break;
            case EditorLayoutNode.EDGE_TOP:
                rx1 = cx - half;
                ry1 = cy - orbitY - half;
                break;
            case EditorLayoutNode.EDGE_BOTTOM:
                rx1 = cx - half;
                ry1 = cy + orbitY - half;
                break;
            case DROP_ZONE_CENTER:
                rx1 = cx - half;
                ry1 = cy - half;
                break;
            default:
                return null;
        }

        int margin = 2;
        int rx2 = rx1 + size;
        int ry2 = ry1 + size;

        if (rx1 < x + margin)
        {
            rx2 += (x + margin) - rx1;
            rx1 = x + margin;
        }

        if (ry1 < y + margin)
        {
            ry2 += (y + margin) - ry1;
            ry1 = y + margin;
        }

        if (rx2 > ex - margin)
        {
            rx1 -= rx2 - (ex - margin);
            rx2 = ex - margin;
        }

        if (ry2 > ey - margin)
        {
            ry1 -= ry2 - (ey - margin);
            ry2 = ey - margin;
        }

        if (rx2 - rx1 <= 2 || ry2 - ry1 <= 2)
        {
            return null;
        }

        return new int[] {rx1, ry1, rx2, ry2};
    }

    private void renderDropPreviewLayout(UIContext context)
    {
        String activeDragId = this.draggingPanelId != null ? this.draggingPanelId : this.activeDraggingFloatingPanelId;
        if (activeDragId == null || this.dropTargetPanelId == null)
        {
            return;
        }

        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.getFilmLayoutRoot();
        EditorLayoutNode preview = DROP_TARGET_WORKSPACE.equals(this.dropTargetPanelId)
            ? this.buildWorkspaceDroppedLayout(root, activeDragId, this.dropTargetZone)
            : this.buildDroppedLayout(root, activeDragId, this.dropTargetPanelId, this.dropTargetZone);

        if (preview == null)
        {
            return;
        }

        Map<String, float[]> bounds = this.computePanelBounds(preview);
        int baseColor = this.getDockGuideBaseColor();
        float opacity = this.getDockGuideOpacity();
        int previewFill = this.withAlpha(baseColor, opacity * 0.18F);
        int previewStrong = this.withAlpha(Colors.mulRGB(baseColor, 1.2F), opacity * 0.34F);
        int previewBorder = this.withAlpha(Colors.mulRGB(baseColor, 1.35F), opacity * 0.6F);

        for (Map.Entry<String, float[]> entry : bounds.entrySet())
        {
            float[] b = entry.getValue();
            int x = this.editor.area.x + Math.round(this.editor.area.w * b[0]);
            int y = this.editor.area.y + Math.round(this.editor.area.h * b[1]);
            int w = Math.max(1, Math.round(this.editor.area.w * b[2]));
            int h = Math.max(1, Math.round(this.editor.area.h * b[3]));
            int fill = entry.getKey().equals(activeDragId) ? previewStrong : previewFill;

            this.renderDropZoneRect(context, x, y, x + w, y + h, previewBorder, fill);
        }
    }

    private void renderDropZoneRect(UIContext context, Area a, int border, int fill)
    {
        this.renderDropZoneRect(context, a.x, a.y, a.ex(), a.ey(), border, fill);
    }

    private void renderDropZoneRect(UIContext context, int x, int y, int ex, int ey, int border, int fill)
    {
        context.batcher.box(x, y, ex, ey, fill);
        int t = 2;
        context.batcher.box(x, y, ex, y + t, border);
        context.batcher.box(x, ey - t, ex, ey, border);
        context.batcher.box(x, y, x + t, ey, border);
        context.batcher.box(ex - t, y, ex, ey, border);
    }

    private int getDockGuideBaseColor()
    {
        return BBSSettings.editorDockGuideColor == null ? 0x57CCFF : BBSSettings.editorDockGuideColor.get();
    }

    private float getDockGuideOpacity()
    {
        return BBSSettings.editorDockGuideOpacity == null ? 0.5F : MathUtils.clamp(BBSSettings.editorDockGuideOpacity.get(), 0F, 1F);
    }

    private int withAlpha(int color, float alpha)
    {
        return Colors.setA(color, MathUtils.clamp(alpha, 0F, 1F));
    }

    private void renderSplitter(UIContext context, int index)
    {
        if (index < 0 || index >= this.splitterHandles.size() || index >= this.splitterHandleInfos.size())
        {
            return;
        }

        UIDraggable splitter = this.splitterHandles.get(index);
        EditorLayoutNode.SplitterHandleInfo info = this.splitterHandleInfos.get(index);
        boolean active = splitter.area.isInside(context) || splitter.isDragging();
        int lineColor = active ? (0xFF000000 | BBSSettings.primaryColor.get()) : 0xAA666666;

        if (info.horizontal)
        {
            int cy = splitter.area.y + splitter.area.h / 2;
            context.batcher.box(splitter.area.x, cy - 1, splitter.area.ex(), cy + 1, lineColor);
        }
        else
        {
            int cx = splitter.area.x + splitter.area.w / 2;
            context.batcher.box(cx - 1, splitter.area.y, cx + 1, splitter.area.ey(), lineColor);
        }
    }

    public void pickClip(Clip clip, UIClipsPanel panel)
    {
        this.syncVideoPanels();

        if (panel == this.cameraEditor)
        {
            this.focusLinkedPropertiesTab("cameraTimeline");
            this.setFlight(false);
        }
        else if (panel == this.actionEditor)
        {
            this.focusLinkedPropertiesTab("actionTimeline");
        }
    }

    public String getPanelId(UIElement element)
    {
        if (element == null)
        {
            return null;
        }

        for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
        {
            if (entry.getValue() == element)
            {
                return entry.getKey();
            }
        }

        return null;
    }

    public int getPanelIndex()
    {
        if (this.currentPanelIndex >= 0 && this.currentPanelIndex < this.panels.size() && this.panels.get(this.currentPanelIndex).isVisible())
        {
            return this.currentPanelIndex;
        }

        for (int i = 0; i < this.panels.size(); i++)
        {
            if (this.panels.get(i).isVisible())
            {
                this.currentPanelIndex = i;

                return i;
            }
        }

        return this.currentPanelIndex >= 0 && this.currentPanelIndex < this.panels.size() ? this.currentPanelIndex : 0;
    }

    public void showPanel(int index)
    {
        if (index >= 0 && index < this.panels.size())
        {
            this.showPanel(this.panels.get(index));
        }
    }

    public void showPanel(UIElement element)
    {
        if (element == null)
        {
            return;
        }

        String panelId = this.getPanelId(element);

        if (panelId != null)
        {
            this.hiddenPanels.remove(panelId);

            EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();

            if (root != null && this.selectPanelInTabbedNode(root, panelId))
            {
                BBSSettings.editorLayoutSettings.setFilmLayoutRoot(root);
            }

            if (this.suppressLinkedPropertiesTabFocus == 0)
            {
                this.syncLinkedPropertiesTab(panelId);
            }
        }

        int index = this.panels.indexOf(element);

        if (index >= 0)
        {
            this.currentPanelIndex = index;
        }

        element.setVisible(true);

        if (this.isFlying())
        {
            this.toggleFlight();
        }

        /* Re-sync tab visibility so tabbed panels are correctly shown/hidden */
        this.setupEditorFlex(true);
    }

    public UIFilmController getController()
    {
        return this.controller;
    }

    public UIFilmUndoHandler getUndoHandler()
    {
        return this.undoHandler;
    }

    public RunnerCameraController getRunner()
    {
        return this.runner;
    }

    @Override
    protected UICRUDOverlayPanel createOverlayPanel()
    {
        UICRUDOverlayPanel crudPanel = super.createOverlayPanel();

        this.duplicateFilm = new UIIcon(Icons.SCENE, (b) ->
        {
            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_DUPE,
                UIKeys.PANELS_MODALS_DUPE,
                (str) -> this.dupeData(crudPanel.namesList.getPath(str).toString())
            );

            panel.text.setText(crudPanel.namesList.getCurrentFirst().getLast());
            panel.text.filename();

            UIOverlay.addOverlay(this.getContext(), panel);
        });

        this.duplicateFilm.tooltip(UIKeys.FILM_CRUD_DUPE, Direction.LEFT);

        return crudPanel;
    }

    private void dupeData(String name)
    {
        if (this.getData() != null && !this.overlay.namesList.hasInHierarchy(name))
        {
            this.discardProvisionalPosePreviews();
            this.save();
            this.overlay.namesList.addFile(name);

            Film data = new Film();
            Position position = new Position();
            IdleClip idle = new IdleClip();
            int tick = this.getCursor();

            position.set(this.getCamera());
            idle.duration.set(BBSSettings.getDefaultDuration());
            idle.position.set(position);
            data.camera.addClip(idle);
            data.setId(name);

            for (Replay replay : this.data.replays.getList())
            {
                Replay copy = new Replay(replay.getId());

                copy.form.set(FormUtils.copy(replay.form.get()));

                for (KeyframeChannel<?> channel : replay.keyframes.getChannels())
                {
                    if (!channel.isEmpty())
                    {
                        KeyframeChannel newChannel = (KeyframeChannel) copy.keyframes.get(channel.getId());

                        newChannel.insert(0, channel.interpolate(tick));
                    }
                }

                for (Map.Entry<String, KeyframeChannel> entry : replay.properties.properties.entrySet())
                {
                    KeyframeChannel channel = entry.getValue();

                    if (channel.isEmpty())
                    {
                        continue;
                    }

                    KeyframeChannel newChannel = new KeyframeChannel(channel.getId(), channel.getFactory());
                    KeyframeSegment segment = channel.find(tick);

                    if (segment != null)
                    {
                        newChannel.insert(0, segment.createInterpolated());
                    }

                    if (!newChannel.isEmpty())
                    {
                        copy.properties.properties.put(newChannel.getId(), newChannel);
                        copy.properties.add(newChannel);
                    }
                }

                data.replays.add(copy);
            }

            this.fill(data);
            this.save();
        }
    }

    private void openLayoutSelector()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        UIContext context = this.getContext();

        context.replaceContextMenu((menu) ->
        {
            menu.custom(new UISimpleContextMenu()
            {
                @Override
                public void setMouse(UIContext context)
                {
                    int w = 100;

                    for (ContextAction action : this.actions.getList())
                    {
                        w = Math.max(action.getWidth(context.batcher.getFont()), w);
                    }

                    int x = UIFilmPanel.this.toggleHorizontal.area.x;
                    int y = UIFilmPanel.this.toggleHorizontal.area.ey();

                    this.set(x, y, w, 0).h(this.actions.scroll.scrollSize).maxH(context.menu.height - 10).bounds(context.menu.overlay, 5);
                }
            });

            menu.action(Icons.EXCHANGE, UIKeys.FILM_LAYOUT_HORIZONTAL_BOTTOM, layout.getLayout() == ValueEditorLayout.LAYOUT_HORIZONTAL_BOTTOM, () ->
            {
                this.applyLayoutPreset(ValueEditorLayout.LAYOUT_HORIZONTAL_BOTTOM);
            });

            menu.action(Icons.CONVERT, UIKeys.FILM_LAYOUT_VERTICAL_LEFT, layout.getLayout() == ValueEditorLayout.LAYOUT_VERTICAL_LEFT, () ->
            {
                this.applyLayoutPreset(ValueEditorLayout.LAYOUT_VERTICAL_LEFT);
            });

            menu.action(Icons.ARROW_RIGHT, UIKeys.FILM_LAYOUT_VERTICAL_RIGHT, layout.getLayout() == ValueEditorLayout.LAYOUT_VERTICAL_RIGHT, () ->
            {
                this.applyLayoutPreset(ValueEditorLayout.LAYOUT_VERTICAL_RIGHT);
            });

            menu.action(Icons.MAIN_HANDLE, UIKeys.FILM_LAYOUT_VERTICAL_MIDDLE, layout.getLayout() == ValueEditorLayout.LAYOUT_VERTICAL_MIDDLE, () ->
            {
                this.applyLayoutPreset(ValueEditorLayout.LAYOUT_VERTICAL_MIDDLE);
            });
        });
    }

    private boolean isAnchoredReplaysPanelEnabled()
    {
        /* The replay list is a permanent docked window; it can be moved/floated but never disabled. */
        return true;
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

    private EditorLayoutNode createAnchoredReplaysColumn()
    {
        return new EditorLayoutNode.SplitterNode(
            true,
            0.65F,
            new EditorLayoutNode.PanelNode(ANCHORED_REPLAYS_PANEL_ID),
            new EditorLayoutNode.PanelNode(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID)
        );
    }

    private EditorLayoutNode createAnchoredReplaysPanelNode()
    {
        if (this.isSeparateReplayPropertiesPanelEnabled())
        {
            return this.createAnchoredReplaysColumn();
        }

        return new EditorLayoutNode.PanelNode(ANCHORED_REPLAYS_PANEL_ID);
    }

    private EditorLayoutNode migrateReplaysPropertiesPanel(EditorLayoutNode root)
    {
        if (root == null
            || !this.hasPanelInLayout(root, ANCHORED_REPLAYS_PANEL_ID)
            || this.hasPanelInLayout(root, ANCHORED_REPLAYS_PROPERTIES_PANEL_ID)
            || this.floatingPanels.contains(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID)
            || this.hiddenPanels.contains(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID))
        {
            return root;
        }

        /* Only auto-insert the General column when the setting prefers a separate panel.
           Windows > General still controls visibility via hiddenPanels without stripping
           leaves from the saved layout tree. */
        if (!this.isSeparateReplayPropertiesPanelEnabled())
        {
            return root;
        }

        return EditorLayoutNode.copyWithReplacedLeaf(root, ANCHORED_REPLAYS_PANEL_ID, this.createAnchoredReplaysColumn());
    }

    /**
     * Legacy layout presets store only {@code replaysPanel}. Insert the General panel
     * under Replays while leaving it in {@link #hiddenPanels}, so enabling Windows >
     * General docks below Replays instead of spawning a floating window.
     */
    private void ensureLegacyDockedReplaysPropertiesColumn()
    {
        if (!this.isSeparateReplayPropertiesPanelEnabled())
        {
            return;
        }

        if (this.floatingPanels.contains(ANCHORED_REPLAYS_PANEL_ID)
            || this.floatingPanels.contains(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID))
        {
            return;
        }

        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.getFilmLayoutRoot();

        if (root == null
            || !this.hasPanelInLayout(root, ANCHORED_REPLAYS_PANEL_ID)
            || this.hasPanelInLayout(root, ANCHORED_REPLAYS_PROPERTIES_PANEL_ID))
        {
            return;
        }

        layout.setFilmLayoutRoot(EditorLayoutNode.copyWithReplacedLeaf(
            root, ANCHORED_REPLAYS_PANEL_ID, this.createAnchoredReplaysColumn()));
    }

    private boolean isReplaysPropertiesPanelActive()
    {
        EditorLayoutNode root = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();

        if (this.hiddenPanels.contains(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID))
        {
            return false;
        }

        return this.floatingPanels.contains(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID) || this.hasPanelInLayout(root, ANCHORED_REPLAYS_PROPERTIES_PANEL_ID);
    }

    private void syncReplaysPropertiesLayoutMode()
    {
        if (this.anchoredReplaysPanel == null)
        {
            return;
        }

        boolean external = this.isReplaysPropertiesPanelActive();

        if (this.floatingPanels.contains(ANCHORED_REPLAYS_PANEL_ID) && !this.floatingPanels.contains(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID))
        {
            external = false;
        }

        this.anchoredReplaysPanel.setPropertiesExternal(external);
    }

    private void setAnchoredReplaysPanelEnabled(boolean enabled)
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.getFilmLayoutRoot();
        EditorLayoutNode baseRoot = this.removeAnchoredReplaysPanelFromRoot(root);

        if (enabled)
        {
            if (!this.floatingPanels.contains(ANCHORED_REPLAYS_PANEL_ID) && !this.hasPanelInLayout(root, ANCHORED_REPLAYS_PANEL_ID))
            {
                layout.setFilmLayoutRoot(this.addAnchoredReplaysPanelToRoot(baseRoot));
            }
            else if (!this.floatingPanels.contains(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID) && !this.hasPanelInLayout(layout.getFilmLayoutRoot(), ANCHORED_REPLAYS_PROPERTIES_PANEL_ID) && this.hasPanelInLayout(layout.getFilmLayoutRoot(), ANCHORED_REPLAYS_PANEL_ID) && this.isSeparateReplayPropertiesPanelEnabled())
            {
                layout.setFilmLayoutRoot(this.migrateReplaysPropertiesPanel(layout.getFilmLayoutRoot()));
            }
        }
        else
        {
            this.floatingPanels.remove(ANCHORED_REPLAYS_PANEL_ID);
            this.floatingPanels.remove(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID);
            this.collapsedFloatingPanels.remove(ANCHORED_REPLAYS_PANEL_ID);
            this.collapsedFloatingPanels.remove(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID);
            this.floatingPanelPositions.remove(ANCHORED_REPLAYS_PANEL_ID);
            this.floatingPanelPositions.remove(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID);
            this.floatingPanelSizes.remove(ANCHORED_REPLAYS_PANEL_ID);
            this.floatingPanelSizes.remove(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID);

            if (this.hasPanelInLayout(root, ANCHORED_REPLAYS_PANEL_ID) || this.hasPanelInLayout(root, ANCHORED_REPLAYS_PROPERTIES_PANEL_ID))
            {
                layout.setFilmLayoutRoot(baseRoot);
            }
        }

        this.anchoredReplaysPanel.setDocked(enabled);
        this.syncAnchoredReplaysPanelWithFilm();
        this.setupEditorFlex(true);
    }

    private EditorLayoutNode removeAnchoredReplaysPanelFromRoot(EditorLayoutNode root)
    {
        if (root == null)
        {
            return root;
        }

        EditorLayoutNode withoutProperties = EditorLayoutNode.copyWithRemovedLeaf(root, ANCHORED_REPLAYS_PROPERTIES_PANEL_ID);

        if (!this.hasPanelInLayout(withoutProperties, ANCHORED_REPLAYS_PANEL_ID))
        {
            return withoutProperties;
        }

        return EditorLayoutNode.copyWithRemovedLeaf(withoutProperties, ANCHORED_REPLAYS_PANEL_ID);
    }

    private EditorLayoutNode addAnchoredReplaysPanelToRoot(EditorLayoutNode root)
    {
        EditorLayoutNode baseRoot = root == null ? EditorLayoutNode.defaultFilmLayout() : this.removeAnchoredReplaysPanelFromRoot(root);

        if (this.floatingPanels.contains(ANCHORED_REPLAYS_PANEL_ID))
        {
            return baseRoot;
        }

        EditorLayoutNode replaysColumn = this.createAnchoredReplaysPanelNode();
        EditorLayoutNode inserted = EditorLayoutNode.copyWithReplacedLeaf(
            baseRoot,
            "editArea",
            new EditorLayoutNode.SplitterNode(
                false,
                0.38F,
                replaysColumn,
                new EditorLayoutNode.PanelNode("editArea")
            )
        );

        if (inserted == baseRoot)
        {
            inserted = EditorLayoutNode.copyWithInsertSplitAt(baseRoot, "editArea", ANCHORED_REPLAYS_PANEL_ID, EditorLayoutNode.EDGE_LEFT);

            if (inserted != baseRoot)
            {
                inserted = this.migrateReplaysPropertiesPanel(inserted);
            }
        }

        return inserted;
    }

    public void syncAnchoredReplaysPanelWithFilm()
    {
        if (this.anchoredReplaysPanel == null)
        {
            return;
        }

        List<Replay> replays = this.data == null ? Collections.emptyList() : this.data.replays.getList();

        this.anchoredReplaysPanel.replays.setList(replays);
        this.syncAnchoredReplaysPanelSelection(this.replayEditor == null ? null : this.replayEditor.getReplay(), false);
    }

    public void syncAnchoredReplaysPanelSelection(Replay replay, boolean select)
    {
        if (this.anchoredReplaysPanel == null)
        {
            return;
        }

        this.anchoredReplaysPanel.syncReplaySelection(replay, select);
    }

    public void focusAnchoredReplaysPanel()
    {
        if (this.anchoredReplaysPanel == null)
        {
            return;
        }

        /* The panel is always docked/visible; if it's floating just surface it. */
        if (this.floatingPanels.contains(ANCHORED_REPLAYS_PANEL_ID))
        {
            this.collapsedFloatingPanels.remove(ANCHORED_REPLAYS_PANEL_ID);
            this.bringPanelToFront(ANCHORED_REPLAYS_PANEL_ID);
            this.setupEditorFlex(true);
        }
    }

    private void toggleLayoutLock()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        layout.setLayoutLocked(!layout.isLayoutLocked());
        this.flushBbsSettings();
        this.clearPanelDragState();

        if (layout.isLayoutLocked())
        {
            TimelineToolbar.cancelAllDockDrags(this);
        }

        this.updateLayoutLockTooltip();
        this.setupEditorFlex(true);
    }

    private void flushBbsSettings()
    {
        Settings settings = BBSMod.getSettings().modules.get("bbs");

        if (settings != null)
        {
            settings.save();
        }
    }

    private void updateLayoutLockTooltip()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        boolean locked = layout.isLayoutLocked();

        this.layoutLock.active(locked);

        if (locked)
        {
            this.layoutLock.tooltip(UIKeys.FILM_LAYOUT_UNLOCK, Direction.LEFT);
        }
        else
        {
            this.layoutLock.tooltip(UIKeys.FILM_LAYOUT_LOCK, Direction.LEFT);
        }
    }

    private void invertSelectedLayout()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        if (layout.getLayout() == ValueEditorLayout.LAYOUT_HORIZONTAL_BOTTOM)
        {
            layout.setHorizontalLayoutInverted(!layout.isHorizontalLayoutInverted());
        }
        else if (layout.getLayout() == ValueEditorLayout.LAYOUT_HORIZONTAL_TOP)
        {
            layout.setHorizontalLayoutInverted(!layout.isHorizontalLayoutInverted());
        }
        else if (layout.getLayout() == ValueEditorLayout.LAYOUT_VERTICAL_LEFT)
        {
            layout.setVerticalLayoutInverted(!layout.isVerticalLayoutInverted());
        }
        else if (layout.getLayout() == ValueEditorLayout.LAYOUT_VERTICAL_RIGHT)
        {
            layout.setVerticalLayoutInverted(!layout.isVerticalLayoutInverted());
        }
        else if (layout.getLayout() == ValueEditorLayout.LAYOUT_VERTICAL_MIDDLE)
        {
            float editorSize = MathUtils.clamp(layout.getEditorSizeH(), 0.05F, 0.95F);
            float maxMainSize = Math.max(0.05F, 0.95F - editorSize);
            float mirroredMainSize = 1F - layout.getMainSizeV() - editorSize;

            layout.setMainSizeV(MathUtils.clamp(mirroredMainSize, 0.05F, maxMainSize));
            layout.setMiddleLayoutInverted(!layout.isMiddleLayoutInverted());
        }

        this.applyLegacyLayoutSelection();
        this.setupEditorFlex(true);
    }

    private Icon getLayoutIcon()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        if (layout.getLayout() == ValueEditorLayout.LAYOUT_VERTICAL_RIGHT)
        {
            return Icons.ARROW_RIGHT;
        }

        if (layout.getLayout() == ValueEditorLayout.LAYOUT_VERTICAL_LEFT)
        {
            return Icons.CONVERT;
        }

        if (layout.getLayout() == ValueEditorLayout.LAYOUT_VERTICAL_MIDDLE)
        {
            return Icons.MAIN_HANDLE;
        }

        return Icons.EXCHANGE;
    }

    private void applyLegacyLayoutSelection()
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.buildFilmLayoutFromLegacyState();
        layout.setFilmLayoutRoot(this.isAnchoredReplaysPanelEnabled() ? this.addAnchoredReplaysPanelToRoot(root) : root);
    }

    private Icon getLayoutLockIcon()
    {
        return BBSSettings.editorLayoutSettings.isLayoutLocked() ? Icons.LOCKED : Icons.UNLOCKED;
    }

    private MapType getFilmLayoutPresetData()
    {
        MapType data = new MapType();
        EditorLayoutNode currentRoot = BBSSettings.editorLayoutSettings.getFilmLayoutRoot();
        EditorLayoutNode root = this.removeAnchoredReplaysPanelFromRoot(currentRoot);
        boolean anchoredReplaysEnabled = this.isAnchoredReplaysPanelEnabled();
        boolean anchoredReplaysFloating = anchoredReplaysEnabled && this.floatingPanels.contains(ANCHORED_REPLAYS_PANEL_ID);

        data.put("film_layout", (root == null ? EditorLayoutNode.defaultFilmLayout() : root).toData());
        data.putInt("video_frame_width", BBSSettings.videoSettings.width.get());
        data.putInt("video_frame_height", BBSSettings.videoSettings.height.get());
        data.putInt("video_frame_rate", BBSSettings.videoSettings.frameRate.get());
        data.putInt("video_motion_blur", BBSSettings.videoSettings.motionBlur.get());
        data.putBool(PRESET_REPLAYS_PANEL_ENABLED, anchoredReplaysEnabled);
        data.putBool(PRESET_REPLAYS_PANEL_FLOATING, anchoredReplaysFloating);

        if (anchoredReplaysEnabled && !anchoredReplaysFloating)
        {
            EditorLayoutNode dockedRoot = currentRoot;

            if (dockedRoot == null || !this.hasPanelInLayout(dockedRoot, ANCHORED_REPLAYS_PANEL_ID))
            {
                dockedRoot = this.addAnchoredReplaysPanelToRoot(root);
            }

            if (dockedRoot != null)
            {
                data.put(PRESET_REPLAYS_PANEL_DOCKED_LAYOUT, dockedRoot.toData());
            }
        }

        if (anchoredReplaysFloating)
        {
            Vector2i position = this.floatingPanelPositions.get(ANCHORED_REPLAYS_PANEL_ID);
            Vector2i size = this.floatingPanelSizes.get(ANCHORED_REPLAYS_PANEL_ID);

            if (position != null)
            {
                data.putInt(PRESET_REPLAYS_PANEL_X, position.x);
                data.putInt(PRESET_REPLAYS_PANEL_Y, position.y);
            }

            if (size != null)
            {
                data.putInt(PRESET_REPLAYS_PANEL_WIDTH, size.x);
                data.putInt(PRESET_REPLAYS_PANEL_HEIGHT, size.y);
            }
        }

        BBSSettings.timelineToolbarDocks.writePreset(data);
        this.writeHiddenPanelsPreset(data);

        return data;
    }

    private void writeHiddenPanelsPreset(MapType data)
    {
        if (this.hiddenPanels.isEmpty())
        {
            return;
        }

        ListType list = new ListType();

        for (String panelId : this.getWindowPanelIds())
        {
            if (this.hiddenPanels.contains(panelId))
            {
                list.add(new StringType(panelId));
            }
        }

        if (!list.isEmpty())
        {
            data.put(PRESET_HIDDEN_PANELS, list);
        }
    }

    private void applyHiddenPanelsPreset(MapType data)
    {
        if (!data.has(PRESET_HIDDEN_PANELS))
        {
            return;
        }

        BaseType hiddenData = data.get(PRESET_HIDDEN_PANELS);

        if (hiddenData == null || !hiddenData.isList())
        {
            return;
        }

        for (BaseType item : hiddenData.asList())
        {
            if (item != null && item.isString())
            {
                String panelId = item.asString();

                if (panelId != null && !panelId.isEmpty() && this.panelById.containsKey(panelId))
                {
                    this.hiddenPanels.add(panelId);
                }
            }
        }
    }

    public void applyFilmLayoutFromPreset(MapType data, int mouseX, int mouseY)
    {
        this.applyFilmLayoutFromPreset(data);
    }

    public void applyFilmLayoutFromPreset(MapType data)
    {
        BaseType layoutData = data.get("film_layout");
        if (layoutData == null)
        {
            return;
        }

        EditorLayoutNode root = EditorLayoutNode.fromData(layoutData);
        if (root != null)
        {
            /* The template fully describes the docked tree, so clear any leftover
               floating/hidden state first. The replays panel's own float/dock state is
               restored right after from the preset's dedicated fields. */
            this.clearPanelDragState();
            this.floatingPanels.clear();
            this.collapsedFloatingPanels.clear();
            this.hiddenPanels.clear();

            /* Legacy presets created before ANCHORED_REPLAYS_PROPERTIES_PANEL_ID existed
               do not specify the General panel. Default General to hidden so properties render
               embedded inside the Replays window (classic backwards-compatible behavior). */
            boolean legacyGeneral = !this.hasPanelInPresetData(data, ANCHORED_REPLAYS_PROPERTIES_PANEL_ID);

            if (legacyGeneral)
            {
                this.hiddenPanels.add(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID);
            }

            BBSSettings.editorLayoutSettings.setFilmLayoutRoot(this.removeAnchoredReplaysPanelFromRoot(root));

            if (this.hasAnchoredReplaysPanelPresetState(data))
            {
                this.applyAnchoredReplaysPanelPresetState(data, root);
            }
            else
            {
                this.setAnchoredReplaysPanelEnabled(this.isAnchoredReplaysPanelEnabled());
            }

            /* Old presets only store replaysPanel. Insert the General leaf under Replays
               now (still hidden) so Windows > General docks in place instead of floating. */
            if (legacyGeneral)
            {
                this.ensureLegacyDockedReplaysPropertiesColumn();
            }
        }

        if (data.has("video_frame_width"))
        {
            BBSSettings.videoSettings.width.set(data.getInt("video_frame_width"));
        }

        if (data.has("video_frame_height"))
        {
            BBSSettings.videoSettings.height.set(data.getInt("video_frame_height"));
        }

        if (data.has("video_frame_rate"))
        {
            BBSSettings.videoSettings.frameRate.set(data.getInt("video_frame_rate"));
        }

        if (data.has("video_motion_blur"))
        {
            BBSSettings.videoSettings.motionBlur.set(data.getInt("video_motion_blur"));
        }

        BBSSettings.timelineToolbarDocks.applyPreset(data);
        this.applyHiddenPanelsPreset(data);
        TimelineToolbarDockSync.refreshFilmPanel(this);
        this.setupEditorFlex(true);
        this.persistFilmUILayoutSession();
    }

    private boolean hasAnchoredReplaysPanelPresetState(MapType data)
    {
        return data.has(PRESET_REPLAYS_PANEL_ENABLED)
            || data.has(PRESET_REPLAYS_PANEL_FLOATING)
            || data.has(PRESET_REPLAYS_PANEL_X)
            || data.has(PRESET_REPLAYS_PANEL_Y)
            || data.has(PRESET_REPLAYS_PANEL_WIDTH)
            || data.has(PRESET_REPLAYS_PANEL_HEIGHT)
            || data.has(PRESET_REPLAYS_PANEL_DOCKED_LAYOUT);
    }

    private boolean hasPanelInPresetData(MapType data, String panelId)
    {
        if (data == null)
        {
            return false;
        }

        if (data.has("film_layout"))
        {
            EditorLayoutNode node = EditorLayoutNode.fromData(data.get("film_layout"));

            if (node != null && this.hasPanelInLayout(node, panelId))
            {
                return true;
            }
        }

        if (data.has(PRESET_REPLAYS_PANEL_DOCKED_LAYOUT))
        {
            EditorLayoutNode node = EditorLayoutNode.fromData(data.get(PRESET_REPLAYS_PANEL_DOCKED_LAYOUT));

            if (node != null && this.hasPanelInLayout(node, panelId))
            {
                return true;
            }
        }

        if (data.has(PRESET_HIDDEN_PANELS))
        {
            BaseType hiddenData = data.get(PRESET_HIDDEN_PANELS);

            if (hiddenData != null && hiddenData.isList())
            {
                for (BaseType item : hiddenData.asList())
                {
                    if (item != null && item.isString() && panelId.equals(item.asString()))
                    {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void applyAnchoredReplaysPanelPresetState(MapType data, EditorLayoutNode baseRoot)
    {
        /* The panel is always enabled; only its docked/floating placement is restored from presets. */
        boolean floating = data.getBool(PRESET_REPLAYS_PANEL_FLOATING, this.floatingPanels.contains(ANCHORED_REPLAYS_PANEL_ID));
        Vector2i position = data.has(PRESET_REPLAYS_PANEL_X) && data.has(PRESET_REPLAYS_PANEL_Y)
            ? new Vector2i(data.getInt(PRESET_REPLAYS_PANEL_X), data.getInt(PRESET_REPLAYS_PANEL_Y))
            : null;
        Vector2i size = data.has(PRESET_REPLAYS_PANEL_WIDTH) && data.has(PRESET_REPLAYS_PANEL_HEIGHT)
            ? new Vector2i(data.getInt(PRESET_REPLAYS_PANEL_WIDTH), data.getInt(PRESET_REPLAYS_PANEL_HEIGHT))
            : null;

        BBSSettings.editorAnchoredReplaysPanel.set(true);

        if (floating)
        {
            this.showAnchoredReplaysPanelFloating(position, size);
        }
        else
        {
            EditorLayoutNode dockedRoot = data.has(PRESET_REPLAYS_PANEL_DOCKED_LAYOUT)
                ? EditorLayoutNode.fromData(data.get(PRESET_REPLAYS_PANEL_DOCKED_LAYOUT))
                : null;

            this.dockAnchoredReplaysPanel(dockedRoot == null ? baseRoot : dockedRoot);
        }
    }

    private void dockAnchoredReplaysPanel(EditorLayoutNode preferredRoot)
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = preferredRoot == null ? layout.getFilmLayoutRoot() : preferredRoot;
        EditorLayoutNode dockedRoot = root;

        if (dockedRoot == null || !this.hasPanelInLayout(dockedRoot, ANCHORED_REPLAYS_PANEL_ID))
        {
            dockedRoot = this.addAnchoredReplaysPanelToRoot(this.removeAnchoredReplaysPanelFromRoot(root));
        }

        this.floatingPanels.remove(ANCHORED_REPLAYS_PANEL_ID);
        this.collapsedFloatingPanels.remove(ANCHORED_REPLAYS_PANEL_ID);
        layout.setFilmLayoutRoot(dockedRoot);
        this.anchoredReplaysPanel.setDocked(true);
        this.syncAnchoredReplaysPanelWithFilm();
        this.setupEditorFlex(true);
    }

    private void showAnchoredReplaysPanelFloating(Vector2i position, Vector2i size)
    {
        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

        layout.setFilmLayoutRoot(this.removeAnchoredReplaysPanelFromRoot(layout.getFilmLayoutRoot()));

        this.floatingPanels.add(ANCHORED_REPLAYS_PANEL_ID);
        this.collapsedFloatingPanels.remove(ANCHORED_REPLAYS_PANEL_ID);

        if (size != null)
        {
            this.floatingPanelSizes.put(ANCHORED_REPLAYS_PANEL_ID, new Vector2i(size));
        }
        else
        {
            this.ensureFloatingPanelSize(ANCHORED_REPLAYS_PANEL_ID);
        }

        this.floatingPanelPositions.put(ANCHORED_REPLAYS_PANEL_ID, this.clampFloatingPanelPosition(ANCHORED_REPLAYS_PANEL_ID, position));
        this.anchoredReplaysPanel.setDocked(true);
        this.syncAnchoredReplaysPanelWithFilm();
        this.bringPanelToFront(ANCHORED_REPLAYS_PANEL_ID);
        this.setupEditorFlex(true);
    }

    private void ensureFloatingPanelSize(String panelId)
    {
        if (!this.floatingPanelSizes.containsKey(panelId))
        {
            this.floatingPanelSizes.put(panelId, this.createDefaultFloatingPanelSize(panelId));
        }
    }

    private Vector2i createDefaultFloatingPanelSize(String panelId)
    {
        if (panelId.equals("preview"))
        {
            return new Vector2i(320, 200);
        }

        if (panelId.equals(ANCHORED_REPLAYS_PANEL_ID))
        {
            return new Vector2i(360, 420);
        }

        if (panelId.equals(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID))
        {
            return new Vector2i(280, 220);
        }

        if (panelId.equals("editArea"))
        {
            return new Vector2i(300, 400);
        }

        if (panelId.equals("cameraEditArea"))
        {
            return new Vector2i(300, 400);
        }

        if (panelId.startsWith("videoPanel"))
        {
            if (this.preview != null && this.preview.area.w > 50 && this.preview.area.h > 50)
            {
                return new Vector2i(this.preview.area.w, this.preview.area.h);
            }

            return new Vector2i(320, 200);
        }

        return new Vector2i(400, 300);
    }

    public String getVideoPanelId(VideoClip clip)
    {
        return "videoPanel_" + Integer.toHexString(System.identityHashCode(clip));
    }

    public boolean hasGlobalVideoClip()
    {
        if (this.data == null)
        {
            return false;
        }

        for (Clip clip : this.data.camera.get())
        {
            if (clip instanceof VideoClip video && video.global.get() && clip.enabled.get())
            {
                return true;
            }
        }

        return false;
    }

    public void syncVideoPanels()
    {
        if (this.data == null)
        {
            this.clearVideoPanels();

            return;
        }

        Set<VideoClip> currentGlobalClips = new LinkedHashSet<>();

        for (Clip clip : this.data.camera.get())
        {
            if (clip instanceof VideoClip video && video.global.get())
            {
                currentGlobalClips.add(video);
            }
        }

        List<VideoClip> toRemove = new ArrayList<>();

        for (Map.Entry<VideoClip, UIVideoPanel> entry : this.videoPanelsByClip.entrySet())
        {
            if (!currentGlobalClips.contains(entry.getKey()))
            {
                toRemove.add(entry.getKey());
            }
        }

        boolean layoutChanged = false;

        for (VideoClip clip : toRemove)
        {
            String panelId = this.getVideoPanelId(clip);
            UIVideoPanel panel = this.videoPanelsByClip.remove(clip);
            this.videoClipsByPanelId.remove(panelId);

            if (panel != null)
            {
                panel.setVisible(false);
                panel.removeFromParent();
                this.panelById.remove(panelId);
                this.floatingPanels.remove(panelId);
                this.collapsedFloatingPanels.remove(panelId);
                this.hiddenPanels.remove(panelId);
                this.floatingPanelPositions.remove(panelId);
                this.floatingPanelSizes.remove(panelId);
                UIDraggable handle = this.dragHandlesById.remove(panelId);

                if (handle != null)
                {
                    handle.setVisible(false);
                    handle.removeFromParent();
                }

                layoutChanged = true;
            }

            if (!this.isVideoPathUsedByOtherClips(clip.video.get()))
            {
                VideoRenderer.releaseVideo(clip.video.get());
            }
        }

        int index = 0;

        for (VideoClip clip : currentGlobalClips)
        {
            String panelId = this.getVideoPanelId(clip);

            if (!this.videoPanelsByClip.containsKey(clip))
            {
                UIVideoPanel panel = new UIVideoPanel(this, clip);
                this.videoPanelsByClip.put(clip, panel);
                this.videoClipsByPanelId.put(panelId, clip);
                this.panelById.put(panelId, panel);
                this.editor.add(panel);
                UIDraggable handle = this.createPanelDragHandle(panelId);
                this.dragHandlesById.put(panelId, handle);
                this.editor.add(handle);
                this.floatingPanels.add(panelId);
                this.hiddenPanels.remove(panelId);

                int w = (this.preview != null && this.preview.area.w > 50) ? this.preview.area.w : 320;
                int h = (this.preview != null && this.preview.area.h > 50) ? this.preview.area.h : 200;
                this.floatingPanelSizes.put(panelId, new Vector2i(w, h));

                int offsetX = (index * 30);
                int offsetY = (index * 30);
                int baseX = (this.preview != null && this.preview.area.w > 0) ? Math.max(0, this.preview.area.x - this.editor.area.x) : 20;
                int baseY = (this.preview != null && this.preview.area.h > 0) ? Math.max(0, this.preview.area.y - this.editor.area.y) : 20;
                this.floatingPanelPositions.put(panelId, new Vector2i(baseX + offsetX, baseY + offsetY));
                this.bringPanelToFront(panelId);
                layoutChanged = true;
            }

            index += 1;
        }

        if (layoutChanged)
        {
            this.setupEditorFlex(true);
            this.persistFilmUILayoutSession();
        }
    }

    private boolean isVideoPathUsedByOtherClips(String path)
    {
        if (this.data == null || path == null || path.isEmpty())
        {
            return false;
        }

        for (Clip clip : this.data.camera.get())
        {
            if (clip instanceof VideoClip video && path.equals(video.video.get()) && video.enabled.get())
            {
                return true;
            }
        }

        return false;
    }

    public void clearVideoPanels()
    {
        for (Map.Entry<VideoClip, UIVideoPanel> entry : this.videoPanelsByClip.entrySet())
        {
            String panelId = this.getVideoPanelId(entry.getKey());
            UIVideoPanel panel = entry.getValue();

            if (panel != null)
            {
                panel.setVisible(false);
                panel.removeFromParent();
            }

            this.panelById.remove(panelId);
            this.floatingPanels.remove(panelId);
            this.collapsedFloatingPanels.remove(panelId);
            this.hiddenPanels.remove(panelId);
            this.floatingPanelPositions.remove(panelId);
            this.floatingPanelSizes.remove(panelId);
            UIDraggable handle = this.dragHandlesById.remove(panelId);

            if (handle != null)
            {
                handle.setVisible(false);
                handle.removeFromParent();
            }

            VideoRenderer.releaseVideo(entry.getKey().video.get());
        }

        this.videoPanelsByClip.clear();
        this.videoClipsByPanelId.clear();
    }

    public void openFloatingVideoPanel(VideoClip clip)
    {
        this.syncVideoPanels();

        if (clip != null)
        {
            String panelId = this.getVideoPanelId(clip);
            this.hiddenPanels.remove(panelId);
            this.collapsedFloatingPanels.remove(panelId);
            this.bringPanelToFront(panelId);
            this.setupEditorFlex(true);
            this.persistFilmUILayoutSession();
        }
    }

    private Vector2i clampFloatingPanelPosition(String panelId, Vector2i desiredPosition)
    {
        this.ensureFloatingPanelSize(panelId);

        Vector2i size = this.floatingPanelSizes.get(panelId);
        int maxX = Math.max(0, this.editor.area.w - size.x);
        int maxY = Math.max(0, this.editor.area.h - size.y);
        int x = desiredPosition == null ? 20 : desiredPosition.x;
        int y = desiredPosition == null ? 20 : desiredPosition.y;

        return new Vector2i(Math.max(0, Math.min(x, maxX)), Math.max(0, Math.min(y, maxY)));
    }

    @Override
    public void resize()
    {
        super.resize();

        if (this.showingHomePage)
        {
            this.editor.resize();
        }
        else if (this.editor.area.w >= EDITOR_MIN_SIZE_FOR_PX_HANDLES && this.editor.area.h >= EDITOR_MIN_SIZE_FOR_PX_HANDLES)
        {
            this.updateEditorFlexBoundsOnly(BBSSettings.editorLayoutSettings, BBSSettings.editorLayoutSettings.getFilmLayoutRoot());

            this.editor.resize();
        }

        this.updateFilmDocumentView();
    }

    @Override
    public void open()
    {
        super.open();

        RecordingPauseHelper.reset();

        Recorder recorder = BBSModClient.getFilms().stopRecording();

        if (recorder == null || recorder.hasNotStarted())
        {
            /* Actor playback is started in appear() so a selected film does not
             * respawn actors when the dashboard opens onto another panel. */
            return;
        }

        this.applyRecordedKeyframes(recorder, this.data);
        this.controller.refreshEntities();
    }



    public void receiveActions(String filmId, int replayId, int tick, BaseType clips)
    {
        Film film = this.data;

        if (film != null && film.getId().equals(filmId) && CollectionUtils.inRange(film.replays.getList(), replayId))
        {
            BaseValue.edit(film.replays.getList().get(replayId), IValueListener.FLAG_UNMERGEABLE, (replay) ->
            {
                Clips newClips = new Clips("", BBSMod.getFactoryActionClips());

                newClips.fromData(clips);
                replay.actions.copyOver(newClips, tick);
            });
        }

        this.save();
    }

    public void applyRecordedKeyframes(Recorder recorder, Film film)
    {
        int replayId = recorder.exception;
        Replay rp = CollectionUtils.getSafe(film.replays.getList(), replayId);

        if (rp != null)
        {
            BaseValue.edit(film, (f) ->
            {
                rp.keyframes.copyOver(recorder.keyframes, 0);

                Form form = rp.form.get();

                if (form != null)
                {
                    for (Map.Entry<String, KeyframeChannel> entry : recorder.properties.properties.entrySet())
                    {
                        KeyframeChannel channel = rp.properties.getOrCreate(form, entry.getKey());

                        if (channel != null && entry.getValue() != null)
                        {
                            channel.copyOver(entry.getValue(), 0);
                        }
                    }

                    BaseValue poseValue = rp.properties.get("pose");

                    if (poseValue instanceof KeyframeChannel<?> poseChannel)
                    {
                        poseChannel.simplify();
                    }
                }

                rp.inventory.fromData(recorder.inventory.toData());
                f.hp.set(recorder.hp);
                f.hunger.set(recorder.hunger);
                f.xpLevel.set(recorder.xpLevel);
                f.xpProgress.set(recorder.xpProgress);
            });
        }
    }

    @Override
    public void appear()
    {
        super.appear();

        this.syncViewportRenderMode();

        this.fillData();
        this.setFlight(false);

        /* Clear the re-entrancy guard before refreshing the view. updateFilmDocumentView() bails
           out early while performingLayout is true; if a prior layout pass left it set, returning
           to this panel would skip the refresh and leave the stale home page showing on top of the
           still-loaded film (the document tab would have to be clicked to recover). */
        this.performingLayout = false;

        if (this.activeFilmDocumentTab >= 0 && this.activeFilmDocumentTab < this.filmDocumentTabs.size())
        {
            this.activateFilmDocumentTab(this.activeFilmDocumentTab, false);
        }
        else
        {
            this.updateFilmDocumentView();
        }

        this.fullscreenPlaybackBar.attachToRoot();
        this.syncFilmActorPlayback(true);
        /* Dashboard close must not clear the out-of-editor HUD; re-assert after appear. */
        this.syncSelectedReplayHud();

        this.syncIrisShaderState(true);
    }

    private void syncIrisShaderState(boolean inFilmEditor)
    {
        if (BBSRendering.isIrisShadersEnabled() && IrisUtils.isExternalLODRenderingActive()
            && (BBSSettings.lodShaderReloadFix == null || BBSSettings.lodShaderReloadFix.get()))
        {
            if (hasSyncedShaders != inFilmEditor)
            {
                hasSyncedShaders = inFilmEditor;
                IrisUtils.reloadShaders();
            }
        }
    }

    @Override
    public void close()
    {
        /* Drop untouched pose/limb previews before persist so they never hit disk.
         * Must not run on periodic autosave — that orphaned live sheet channels mid-edit. */
        this.discardProvisionalPosePreviews();
        this.requestThumbnailCapture();
        this.save();
        lastShowingHomePage = this.showingHomePage;

        super.close();

        BBSRendering.setCustomSize(false);
        MorphRenderer.hidePlayer = false;
        this.lastViewportRenderW = -1;
        this.lastViewportRenderH = -1;
        VideoRenderer.stopAll();

        CameraController cameraController = this.getCameraController();

        this.cameraEditor.embedView(null);
        this.setFlight(false);
        cameraController.remove(this.runner);

        this.disableContext();
        this.replayEditor.close();

        this.notifyServer(ActionState.STOP);

        this.syncIrisShaderState(false);

        /* Keep selectedReplay / Right-Alt session while the film stays loaded.
         * Clearing here hid the top-left HUD after closing BBS with 0 even though
         * the film was still open. Session ends only when the film is closed
         * (home / fill(null)) via {@link #endOutOfEditorFilmSession}. */
    }

    @Override
    public void disappear()
    {
        VideoRenderer.cleanup();

        super.disappear();

        BBSRendering.setCustomSize(false);
        MorphRenderer.hidePlayer = false;
        this.lastViewportRenderW = -1;
        this.lastViewportRenderH = -1;

        this.setFlight(false);
        this.getCameraController().remove(this.runner);

        this.disableContext();
        this.fullscreenPlaybackBar.removeFromParent();
        this.syncFilmActorPlayback(false);
        this.syncIrisShaderState(false);
    }

    /**
     * FILM_EDITOR actors must only exist while this panel is showing a film.
     * Dashboard {@code open()} runs for every panel, so restarting there made
     * paused actors reappear when opening model/trigger blocks from the world.
     */
    private void syncFilmActorPlayback(boolean visible)
    {
        if (visible && this.data != null && !this.showingHomePage)
        {
            this.notifyServer(ActionState.RESTART);

            return;
        }

        this.notifyServer(ActionState.STOP);
    }

    private void disableContext()
    {
        this.runner.getContext().shutdown();
    }

    @Override
    public boolean needsBackground()
    {
        if (this.isCrossWorldPreviewInForeignWorld())
        {
            return true;
        }

        /* HDR Mod presents the film offscreen world full-screen; opaque chrome covers the bleed. */
        return HdrModCompat.isHdrPresentationActive();
    }

    @Override
    public boolean canPause()
    {
        return false;
    }

    @Override
    public boolean canRefresh()
    {
        return false;
    }

    @Override
    public boolean canToggleVisibility()
    {
        return !this.showingHomePage;
    }

    @Override
    public boolean canHideHUD()
    {
        return !this.showingHomePage;
    }

    @Override
    public ContentType getType()
    {
        return ContentType.FILMS;
    }

    @Override
    public IKey getTitle()
    {
        return UIKeys.FILM_TITLE;
    }

    @Override
    public UIDashboardPanel getMainPanel()
    {
        UIHomePanel home = this.dashboard.getPanel(UIHomePanel.class);

        return home != null ? home : this;
    }

    public void openFilmTab(String tabId)
    {
        if (tabId == null || tabId.trim().isEmpty())
        {
            return;
        }

        this.discardProvisionalPosePreviews();
        this.requestThumbnailCapture();
        this.save();
        this.openFilmInDocumentTabs(tabId);
    }

    @Override
    public void pickData(String id)
    {
        this.openFilmTab(id);
        RecentAssetsTracker.add(this.getType(), id);
    }

    /**
     * Remove untouched auto-inserted pose/limb previews from the in-memory film.
     * Call before persisting when leaving the current film context (close / switch tab).
     */
    private void discardProvisionalPosePreviews()
    {
        if (this.replayEditor != null)
        {
            this.replayEditor.discardUntouchedAutomaticKeyframes();
        }
    }

    @Override
    public void save()
    {
        this.requestThumbnailCapture();
        super.save();
    }

    @Override
    protected void manualSave()
    {
        if (this.data == null)
        {
            return;
        }

        this.save();
        UIUtils.playClick();

        if (this.statusIcons != null)
        {
            this.statusIcons.flashAutosave();
        }
    }

    @Override
    protected void onAutoSaved(UIContext context)
    {
        if (this.statusIcons != null)
        {
            this.statusIcons.flashAutosave();
        }
    }

    public void requestThumbnailCapture()
    {
        if (this.data != null && !this.showingHomePage && this.preview != null && this.preview.isVisible())
        {
            this.shouldCaptureThumbnail = true;
        }
    }

    public boolean needsViewportRender()
    {
        if (this.isCrossWorldPreviewInForeignWorld())
        {
            return false;
        }

        return this.data != null && !this.showingHomePage && this.preview != null && this.preview.isVisible();
    }

    @Override
    public boolean needsWorldRender()
    {
        return this.needsViewportRender();
    }

    public boolean isShowingHomePage()
    {
        return this.showingHomePage;
    }

    /**
     * Film is loaded and not on the film home/browser page — out-of-editor HUD,
     * Right-Alt recording and Right-Ctrl playback may run.
     */
    public boolean hasActiveFilmSession()
    {
        return this.data != null && !this.showingHomePage;
    }

    /** Re-publish the current replay for the top-left HUD after BBS is closed/reopened. */
    private void syncSelectedReplayHud()
    {
        if (!this.hasActiveFilmSession() || this.replayEditor == null)
        {
            return;
        }

        Replay replay = this.replayEditor.getReplay();

        if (replay != null)
        {
            BBSModClient.setSelectedReplay(replay);
        }
    }

    /**
     * Tear down out-of-editor recording/HUD when the film itself is closed
     * (home tab / fill(null) / document-bar film tab closed), not when merely
     * closing the BBS dashboard.
     */
    private void endOutOfEditorFilmSession(Film film)
    {
        if (this.controller != null)
        {
            this.controller.stopRecording();
        }

        Recorder recorder = BBSModClient.getFilms().stopRecording();

        if (recorder != null && !recorder.hasNotStarted() && film != null)
        {
            this.applyRecordedKeyframes(recorder, film);
            this.save();
        }

        BBSModClient.setSelectedReplay(null);

        if (film != null)
        {
            Films.stopFilm(film.getId());
        }
    }

    /**
     * Document-bar film tab was closed. If it was the film currently loaded in this
     * panel, clear data + HUD so P / Right Alt cannot keep using a closed film.
     */
    public void onDocumentFilmTabClosed(String closedTabId)
    {
        if (this.data == null || closedTabId == null)
        {
            return;
        }

        boolean matchesLoaded = closedTabId.equals(this.loadedFilmTabKey);

        if (!matchesLoaded)
        {
            CrossWorldFilmEntry decoded = CrossWorldFilmEntry.decodeKey(closedTabId);

            matchesLoaded = closedTabId.equals(this.data.getId())
                || (decoded != null && decoded.filmId.equals(this.data.getId())
                    && (this.loadedFilmTabKey == null || closedTabId.equals(this.loadedFilmTabKey)));
        }

        if (matchesLoaded)
        {
            this.discardProvisionalPosePreviews();
            this.requestThumbnailCapture();
            this.save();
            this.fill(null);
        }
    }

    private void syncViewportRenderMode()
    {
        boolean needsViewport = this.needsViewportRender();

        this.syncRunnerCamera(needsViewport);

        if (needsViewport)
        {
            /* Always render at the configured export resolution. Preview layout is handled
             * separately in UIFilmPreview.getViewport() (decoupled from BBSRendering size). */
            int renderW = BBSSettings.videoSettings.width.get();
            int renderH = BBSSettings.videoSettings.height.get();

            if (renderW % 2 == 1)
            {
                renderW -= 1;
            }

            if (renderH % 2 == 1)
            {
                renderH -= 1;
            }

            if (!BBSRendering.isCustomSize() || this.lastViewportRenderW != renderW || this.lastViewportRenderH != renderH)
            {
                BBSRendering.setCustomSize(true, renderW, renderH);
                this.lastViewportRenderW = renderW;
                this.lastViewportRenderH = renderH;
            }
        }
        else if (BBSRendering.isCustomSize())
        {
            BBSRendering.setCustomSize(false);
            this.lastViewportRenderW = -1;
            this.lastViewportRenderH = -1;
        }

        MorphRenderer.hidePlayer = needsViewport;
    }

    private void syncRunnerCamera(boolean needsViewport)
    {
        CameraController cameraController = this.getCameraController();

        if (needsViewport)
        {
            if (!cameraController.has(this.runner))
            {
                cameraController.add(this.runner);
            }
        }
        else if (cameraController.has(this.runner))
        {
            cameraController.remove(this.runner);
        }
    }

    public void setFilmThumbnailFromViewport()
    {
        this.requestThumbnailCapture();
    }

    @Override
    public void fillNames(Collection<String> names)
    {
        super.fillNames(names);

        DataPath currentPath = this.homeFilmsList.getPath().copy();
        DataPath selected = this.homeFilmsList.getCurrentFirst();
        String current = selected != null && !selected.folder ? selected.toString() : null;

        this.homeFilmsList.fill(names);
        this.homeFilmsList.goTo(currentPath);
        if (current != null)
        {
            this.homeFilmsList.setCurrentFile(current);
        }
        if (this.homeFilmsMosaic != null)
        {
            this.homeFilmsMosaic.fill(names, current);
        }

        this.missingThumbnailIds.clear();
        this.updateHomeButtonsState();
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

    @Override
    public void fillDefaultData(Film data)
    {
        super.fillDefaultData(data);

        IdleClip clip = new IdleClip();
        Camera camera = new Camera();
        MinecraftClient mc = MinecraftClient.getInstance();

        camera.set(mc.player, MathUtils.toRad(mc.options.getFov().getValue()));

        clip.layer.set(8);
        clip.duration.set(BBSSettings.getDefaultDuration());
        clip.fromCamera(camera);
        data.camera.addClip(clip);

        this.newFilm = true;
    }

    @Override
    public void fill(Film data)
    {
        /* End HUD / Right-Alt session before clearing data so recording can still be applied. */
        if (data == null && this.data != null)
        {
            this.endOutOfEditorFilmSession(this.data);
        }

        this.notifyServer(ActionState.STOP);
        super.fill(data);
        this.editor.setVisible(true);

        if (!this.isAllWorldsBrowseMode())
        {
            if (this.crossWorldPendingJoin == null
                || WorldLaunchHelper.isCurrentWorld(MinecraftClient.getInstance(), this.crossWorldPendingJoin.worldFolder))
            {
                if (this.crossWorldPendingJoin != null && data != null)
                {
                    this.closeCrossWorldFilmTab(data.getId());

                    if (this.dashboard != null && this.dashboard.documentTabsBar != null)
                    {
                        this.dashboard.documentTabsBar.closeCrossWorldFilmTabs(data.getId());
                    }
                }

                this.crossWorldPendingJoin = null;
            }
        }

        if (data != null && !this.isCrossWorldPreviewInForeignWorld())
        {
            this.notifyServer(ActionState.RESTART);
        }

        this.syncViewportRenderMode();
        this.syncActiveDocumentTabWithData(data);
        this.syncVideoPanels();
        RegisterFilmSyncEvent.postOpenFilm(data);
    }

    @Override
    public void showHomeView()
    {
        this.requestThumbnailCapture();

        for (int i = 0; i < this.filmDocumentTabs.size(); i++)
        {
            if (this.filmDocumentTabs.get(i).home)
            {
                this.activateFilmDocumentTab(i, false);

                return;
            }
        }

        super.showHomeView();
    }

    @Override
    protected void fillData(Film data)
    {
        if (this.data != null)
        {
            this.disableContext();
        }

        if (data != null)
        {
            this.undoHandler = new UIFilmUndoHandler(this);

            data.preCallback(this.undoHandler::handlePreValues);
        }
        else
        {
            this.undoHandler = null;
            /* selectedReplay is cleared in endOutOfEditorFilmSession via fill(null). */
        }

        this.toggleHorizontal.setEnabled(data != null);
        this.layoutLock.setEnabled(data != null);
        this.layoutPresets.setEnabled(data != null);
        this.duplicateFilm.setEnabled(data != null);

        this.actionEditor.setClips(null);
        this.runner.setWork(data == null ? null : data.camera);
        this.cameraEditor.setClips(data == null ? null : data.camera);
        this.replayEditor.setFilm(data);
        this.cameraEditor.pickClip(null);

        this.fillData();
        this.syncViewportRenderMode();

        if (this.isCrossWorldPreviewInForeignWorld())
        {
            this.controller.clearEntities();
        }
        else
        {
            this.controller.createEntities();
        }

        if (this.newFilm)
        {
            Clip main = this.data.camera.get(0);

            this.cameraEditor.clips.setSelected(main);
            this.cameraEditor.pickClip(main);
        }

        this.entered = data != null;
        this.updateHomeButtonsState();
        this.layoutFilmStatusIcons();
        this.newFilm = false;
    }

    public void undo()
    {
        if (this.data != null && !this.undoHandler.isFilmRecording())
        {
            this.undoHandler.submitUndo();

            if (this.undoHandler.undo(this.data))
            {
                this.refreshAfterUndo();
                UIUtils.playClick();
            }
        }
    }

    public void redo()
    {
        if (this.data != null && !this.undoHandler.isFilmRecording())
        {
            this.undoHandler.submitUndo();

            if (this.undoHandler.redo(this.data))
            {
                this.refreshAfterUndo();
                UIUtils.playClick();
            }
        }
    }

    public void refreshAfterUndo()
    {
        double cameraScroll = this.cameraEditor.clips.vertical.getScroll();
        double cameraShift = this.cameraEditor.clips.scale.getShift();
        double cameraZoom = this.cameraEditor.clips.scale.getZoom();
        double actionScroll = this.actionEditor.clips.vertical.getScroll();
        double actionShift = this.actionEditor.clips.scale.getShift();
        double actionZoom = this.actionEditor.clips.scale.getZoom();

        List<Integer> cameraSelection = new ArrayList<>(this.cameraEditor.clips.getSelection());
        List<Integer> actionSelection = new ArrayList<>(this.actionEditor.clips.getSelection());

        Clip cameraSelectedClip = this.cameraEditor.getClip();
        Clip actionSelectedClip = this.actionEditor.getClip();
        int cameraSelectedIdx = cameraSelectedClip == null ? -1 : this.cameraEditor.clips.getClips().getIndex(cameraSelectedClip);
        int actionSelectedIdx = actionSelectedClip == null ? -1 : this.actionEditor.clips.getClips().getIndex(actionSelectedClip);

        boolean hasCameraEmbed = this.cameraEditor.clips.hasEmbeddedView();
        boolean hasActionEmbed = this.actionEditor.clips.hasEmbeddedView();

        this.runner.setWork(this.data.camera);

        /* Undo mutates the same Clips instances; setClips() clears selection and closes editors. */
        boolean cameraRebound = this.cameraEditor.clips.getClips() != this.data.camera;

        if (cameraRebound)
        {
            this.cameraEditor.setClips(this.data.camera);
        }

        this.replayEditor.syncFilmForUndo(this.data);

        Replay replay = this.replayEditor.getReplay();
        Clips actionClips = replay == null ? null : replay.actions;
        boolean actionRebound = this.actionEditor.clips.getClips() != actionClips;

        if (actionRebound)
        {
            this.actionEditor.setClips(actionClips);
        }

        this.fillData();
        this.controller.createEntities();
        this.syncAnchoredReplaysPanelWithFilm();

        if (cameraRebound)
        {
            this.restoreClipsView(this.cameraEditor, cameraScroll, cameraShift, cameraZoom);
            this.restoreClipSelectionAfterUndo(this.cameraEditor, cameraSelection, cameraSelectedIdx, hasCameraEmbed);
        }

        if (actionRebound)
        {
            this.restoreClipsView(this.actionEditor, actionScroll, actionShift, actionZoom);
            this.restoreClipSelectionAfterUndo(this.actionEditor, actionSelection, actionSelectedIdx, hasActionEmbed);
        }
    }

    private void restoreClipSelectionAfterUndo(UIClipsPanel panel, List<Integer> selection, int selectedIdx, boolean hasEmbed)
    {
        if (panel.clips.getClips() == null)
        {
            return;
        }

        panel.clips.setSelection(selection);

        if (selectedIdx >= 0 && selectedIdx < panel.clips.getClips().get().size())
        {
            Clip newClip = panel.clips.getClips().get(selectedIdx);

            panel.pickClip(newClip);

            if (hasEmbed && panel.getClipPanel() != null)
            {
                try
                {
                    Field editField = panel.getClipPanel().getClass().getField("edit");
                    UIButton editButton = (UIButton) editField.get(panel.getClipPanel());

                    if (editButton != null)
                    {
                        editButton.clickItself();
                    }
                }
                catch (Exception e)
                {}
            }
        }
    }

    private void restoreClipsView(UIClipsPanel panel, double scroll, double shift, double zoom)
    {
        panel.clips.vertical.setScroll(scroll);
        panel.clips.scale.set(shift, zoom);
    }

    @Override
    public void forceSave()
    {
        super.forceSave();
        this.shouldCaptureThumbnail = true;
    }

    public File getThumbnailFile(String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }

        File worldFolder = BBSMod.getWorldFolder();

        if (worldFolder != null)
        {
            return new File(worldFolder, "config/bbs/thumbnails/films/" + id + ".png");
        }

        return new File(BBS.getGameFolder(), "config/bbs/thumbnails/films/" + id + ".png");
    }

    private String resolveThumbnailId(String listPath)
    {
        if (listPath == null || listPath.isEmpty())
        {
            return listPath;
        }

        CrossWorldFilmEntry entry = this.crossWorldFilmEntries.get(listPath);

        if (entry != null && !entry.filmId.endsWith("/"))
        {
            return entry.filmId;
        }

        if (this.isAllWorldsBrowseMode())
        {
            int slash = listPath.indexOf('/');

            if (slash > 0)
            {
                String worldFolder = listPath.substring(0, slash);

                if (this.crossWorldWorldLabels.containsKey(worldFolder))
                {
                    return listPath.substring(slash + 1);
                }
            }
        }

        return listPath;
    }

    public void deleteThumbnail(String id)
    {
        String thumbnailId = this.resolveThumbnailId(id);

        this.thumbnails.remove(thumbnailId);
        this.missingThumbnailIds.remove(thumbnailId);

        File file = this.getThumbnailFile(thumbnailId);

        if (file.exists())
        {
            file.delete();
        }
    }

    public void clearThumbnailCache()
    {
        this.thumbnails.clear();
        this.missingThumbnailIds.clear();

        File worldFolder = BBSMod.getWorldFolder();
        File folder = worldFolder != null
            ? new File(worldFolder, "config/bbs/thumbnails/films")
            : new File(BBS.getGameFolder(), "config/bbs/thumbnails/films");

        this.deleteFolder(folder);
    }

    private void deleteFolder(File folder)
    {
        if (!folder.exists())
        {
            return;
        }

        File[] files = folder.listFiles();

        if (files != null)
        {
            for (File file : files)
            {
                if (file.isDirectory())
                {
                    this.deleteFolder(file);
                }
                else
                {
                    file.delete();
                }
            }
        }

        folder.delete();
    }

    public Texture getThumbnail(String listPath)
    {
        String id = this.resolveThumbnailId(listPath);

        if (id == null || id.isEmpty() || id.endsWith("/"))
        {
            return null;
        }

        Texture cached = this.thumbnails.get(id);

        if (cached != null)
        {
            return cached;
        }

        if (this.missingThumbnailIds.contains(id))
        {
            return null;
        }

        File file = this.getThumbnailFile(id);

        if (file.exists())
        {
            try (FileInputStream stream = new FileInputStream(file))
            {
                Pixels pixels = Pixels.fromPNGStream(stream);

                if (pixels != null)
                {
                    Texture texture = Texture.textureFromPixels(pixels, GL11.GL_LINEAR);

                    this.thumbnails.put(id, texture);
                    this.missingThumbnailIds.remove(id);

                    return texture;
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        this.missingThumbnailIds.add(id);

        return null;
    }

    public boolean isFlying()
    {
        return this.dashboard.orbitUI.canControl();
    }

    /**
     * Confines left/right/middle click-drag camera rotate/roll/FOV to the preview panel (the
     * 3D viewport, including its overlay buttons), so it can never be triggered by clicking
     * elsewhere in the editor (menu bar, timelines, properties, etc.).
     */
    @Override
    public Area getFlightViewportArea()
    {
        return this.preview.area;
    }

    public void toggleFlight()
    {
        this.setFlight(!this.isFlying());
    }

    /**
     * Set flight mode
     */
    public void setFlight(boolean flight)
    {
        if (!this.isRunning() || !flight)
        {
            if (flight && this.controller.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT)
            {
                this.controller.orbit.syncFromCamera(this.getCamera(), 0F);
            }

            this.runner.setManual(flight ? this.position : null);
            this.dashboard.orbitUI.setControl(flight);
            this.updateFreeFlightMouseCapture(flight);

            if (!flight && this.controller.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT)
            {
                this.controller.orbit.stop();
            }

            /* Flush all keyframe edits accumulated during flight as a single undo step */
            if (this.undoHandler != null && !flight)
            {
                this.undoHandler.submitUndo();
                this.undoHandler.getUndoManager().markLastUndoNoMerging();
            }
            else
            {
                this.lastPosition.set(Position.ZERO);
            }
        }
    }

    public Vector2i getLoopingRange()
    {
        if (this.data == null)
        {
            return new Vector2i(0, 0);
        }

        Clip clip = this.cameraEditor.getClip();

        int min = -1;
        int max = -1;

        if (clip != null)
        {
            min = clip.tick.get();
            max = min + clip.duration.get();
        }

        UIClips clips = this.cameraEditor.clips;

        if (clips.loopMin != clips.loopMax && clips.loopMin >= 0 && clips.loopMin < clips.loopMax)
        {
            min = clips.loopMin;
            max = clips.loopMax;
        }

        max = Math.min(max, this.data.camera.calculateDuration());

        return new Vector2i(min, max);
    }

    @Override
    public void update()
    {
        this.syncViewportRenderMode();
        this.controller.update();

        if (BBSSettings.editorCameraPreviewPlayerSync.get() && this.needsViewportRender() && this.controller.getPovMode() == UIFilmController.CAMERA_MODE_CAMERA)
        {
            this.teleportToCamera();
        }

        if (this.data != null && this.editor.isVisible() && !this.showingHomePage)
        {
            this.incrementTimeWorked();
        }

        super.update();
    }

    private void incrementTimeWorked()
    {
        if (this.data == null)
        {
            return;
        }

        this.data.totalTimeWorked.set(this.data.totalTimeWorked.get() + 1);

        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player != null)
        {
            String name = player.getGameProfile().getName();
            FilmContributor contributor = null;

            for (FilmContributor c : this.data.contributors.getList())
            {
                if (c.name.get().equalsIgnoreCase(name))
                {
                    contributor = c;
                    break;
                }
            }

            if (contributor == null)
            {
                contributor = new FilmContributor(String.valueOf(this.data.contributors.getList().size()));
                contributor.name.set(name);
                this.data.contributors.add(contributor);
            }

            contributor.time.set(contributor.time.get() + 1);
        }
    }

    /* Rendering code */

    @Override
    public void renderPanelBackground(UIContext context)
    {
        super.renderPanelBackground(context);

        Texture texture = BBSRendering.getTexture();

        if (texture != null)
        {
            context.batcher.box(0, 0, context.menu.width, context.menu.height, Colors.A100);

            int w = context.menu.width;
            int h = context.menu.height;
            Vector2i resize = Vectors.resize(texture.width / (float) texture.height, w, h);
            Area area = new Area();

            area.setSize(resize.x, resize.y);
            area.setPos((w - area.w) / 2, (h - area.h) / 2);

            context.batcher.texturedBox(texture.id, Colors.WHITE, area.x, area.y, area.w, area.h, 0, texture.height, texture.width, 0, texture.width, texture.height);
        }

        this.updateLogic(context);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        if (BBSSettings.editorLayoutSettings.isLayoutLocked()) UIDashboardPanels.renderHighlightHorizontal(context.batcher, this.layoutLock.area);
    }

    /**
     * Draw everything on the screen
     */
    @Override
    public void render(UIContext context)
    {
        int savedMouseX = context.mouseX;
        int savedMouseY = context.mouseY;

        if (this.controller.isControlling())
        {
            context.mouseX = context.mouseY = -1;
        }

        this.controller.orbit.update(context);

        context.mouseX = savedMouseX;
        context.mouseY = savedMouseY;

        if (this.undoHandler != null && !this.isFlying())
        {
            this.undoHandler.submitUndo();
        }

        this.updateLogic(context);

        this.updateActiveFloatingPanelDrag(context);
        this.updateActiveFloatingPanelResize(context);

        if (!this.postUpdateActions.isEmpty())
        {
            for (Runnable r : this.postUpdateActions)
            {
                r.run();
            }
            this.postUpdateActions.clear();
        }

        this.area.render(context.batcher, 0xFF141418);

        if (!this.showingHomePage && this.editor.isVisible())
        {
            this.editor.area.render(context.batcher, 0xFF202020);
        }

        if (this.editor.isVisible() && this.preview.isVisible())
        {
            this.preview.area.render(context.batcher, Colors.A75);
        }

        this.renderPanelWindowSurfaces(context);
        super.render(context);
        this.renderDropZoneHighlight(context);

        if (this.shouldCaptureThumbnail && this.data != null && this.preview.isVisible())
        {
            File output = this.getThumbnailFile(this.data.getId());

            if (output != null)
            {
                output.getParentFile().mkdirs();
                String filmId = this.data.getId();

                this.preview.captureThumbnailNow(output, () ->
                {
                    Texture texture = this.thumbnails.remove(filmId);

                    if (texture != null)
                    {
                        texture.delete();
                    }

                    this.missingThumbnailIds.remove(filmId);
                });
            }

            this.shouldCaptureThumbnail = false;
        }
    }

    /**
     * Update logic for such components as repeat fixture, minema recording,
     * sync mode, flight mode, etc.
     */
    private void updateLogic(UIContext context)
    {
        this.syncVideoPanels();

        Clip clip = this.cameraEditor.getClip();

        /* Keep keyframe-linked clip fields in sync while playing (runner advances ticks without setCursor). */
        if (this.getCursor() != this.lastFilledCursor)
        {
            this.refreshCursorFields();
        }

        /* Loop fixture */
        if (BBSSettings.editorLoop.get() && this.isRunning())
        {
            Vector2i loop = this.getLoopingRange();
            int min = loop.x;
            int max = loop.y;
            int ticks = this.getCursor();

            if (!this.recorder.isRecording() && !this.controller.isRecording() && min >= 0 && max >= 0 && min < max && (ticks >= max - 1 || ticks < min))
            {
                this.setCursor(min);
            }
        }

        /* Animate flight mode */
        if (this.dashboard.orbitUI.canControl())
        {
            boolean orbitFlight = this.controller.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT;

            if (BBSSettings.editorFlightFreeLook.get())
            {
                if (orbitFlight)
                {
                    this.updateFreeFlightLookFromRawCursor(true);
                }
                else
                {
                    this.updateFreeFlightLookFromRawCursor(false);
                }
            }
            else if (this.resetFreeFlightLookDrag || this.freeFlightLookPrimed)
            {
                this.resetFreeFlightLookDrag = false;
                this.freeFlightLookPrimed = false;

                if (!orbitFlight)
                {
                    this.dashboard.orbitUI.orbit.release();
                }
            }

            if (!orbitFlight)
            {
                this.dashboard.orbit.apply(this.position);
            }

            Position current = new Position(this.getCamera());
            boolean check = this.flightEditTime.check();

            if (this.cameraEditor.getClip() != null && this.cameraEditor.isVisible() && !this.controller.isFreeCameraMode())
            {
                if (!this.lastPosition.equals(current) && check)
                {
                    this.cameraEditor.editClip(current);
                }
            }

            if (check)
            {
                this.lastPosition.set(current);
            }
        }
        else
        {
            this.dashboard.orbit.setup(this.getCamera());
        }

        /* Rewind playback back to 0 */
        if (this.lastRunning && !this.isRunning())
        {
            this.lastRunning = this.runner.isRunning();

            if (BBSSettings.editorRewind.get())
            {
                this.setCursor(0);
                this.notifyServer(ActionState.RESTART);
            }
        }
    }

    private void updateFreeFlightMouseCapture(boolean flight)
    {
        if (!BBSSettings.editorFlightFreeLook.get())
        {
            return;
        }

        net.minecraft.client.util.Window window = MinecraftClient.getInstance().getWindow();

        if (flight)
        {
            this.centerCursor(window);
            GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            this.resetFreeFlightLookDrag = true;
            this.freeFlightLookPrimed = false;
            this.dashboard.orbitUI.orbit.release();
        }
        else if (!this.controller.isControlling())
        {
            GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            this.resetFreeFlightLookDrag = false;
            this.freeFlightLookPrimed = false;
            this.dashboard.orbitUI.orbit.release();
        }
    }

    private boolean enforceFreeFlightMouseCapture()
    {
        if (!this.isFlying() || !BBSSettings.editorFlightFreeLook.get())
        {
            return false;
        }

        net.minecraft.client.util.Window window = MinecraftClient.getInstance().getWindow();

        if (GLFW.glfwGetInputMode(window.getHandle(), GLFW.GLFW_CURSOR) != GLFW.GLFW_CURSOR_DISABLED)
        {
            this.centerCursor(window);
            GLFW.glfwSetInputMode(window.getHandle(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
            return true;
        }

        return false;
    }

    private void centerCursor(net.minecraft.client.util.Window window)
    {
        mchorse.bbs_mod.graphics.window.Window.moveCursor(window.getWidth() / 2, window.getHeight() / 2);
    }

    private void updateFreeFlightLookFromRawCursor(boolean orbitFlight)
    {
        net.minecraft.client.util.Window window = MinecraftClient.getInstance().getWindow();

        if (this.enforceFreeFlightMouseCapture())
        {
            this.resetFreeFlightLookDrag = true;
            this.freeFlightLookPrimed = false;
        }

        double[] rawX = new double[1];
        double[] rawY = new double[1];
        GLFW.glfwGetCursorPos(window.getHandle(), rawX, rawY);
        int mouseX = (int) Math.round(rawX[0]);
        int mouseY = (int) Math.round(rawY[0]);

        if (this.resetFreeFlightLookDrag || !this.freeFlightLookPrimed)
        {
            this.freeFlightLookRawX = mouseX;
            this.freeFlightLookRawY = mouseY;

            this.resetFreeFlightLookDrag = false;
            this.freeFlightLookPrimed = true;

            if (!orbitFlight)
            {
                this.dashboard.orbitUI.orbit.release();
            }

            return;
        }

        int dx = mouseX - this.freeFlightLookRawX;
        int dy = mouseY - this.freeFlightLookRawY;

        this.freeFlightLookRawX = mouseX;
        this.freeFlightLookRawY = mouseY;

        if (dx != 0 || dy != 0)
        {
            float angleSpeed = this.dashboard.orbit.getAngleSpeed();

            if (orbitFlight)
            {
                this.controller.orbit.rotation.x += dy * angleSpeed;
                this.controller.orbit.rotation.y += dx * angleSpeed;
            }
            else
            {
                this.dashboard.orbitUI.orbit.rotation.x += dy * angleSpeed;
                this.dashboard.orbitUI.orbit.rotation.y += dx * angleSpeed;
            }
        }
    }

    @Override
    protected IUIElement childrenMouseScrolled(UIContext context)
    {
        if (this.dashboard.orbitUI.canControl() && context.mouseWheel != 0D)
        {
            int step = (int) Math.copySign(1, context.mouseWheel);

            if (this.isFlying())
            {
                this.dashboard.orbitUI.orbit.scroll(step);
            }
            else if (this.controller.getPovMode() == UIFilmController.CAMERA_MODE_ORBIT)
            {
                this.controller.orbit.scroll(step);
            }
            else
            {
                this.dashboard.orbitUI.orbit.scroll(step);
            }

            /* Consume scroll so other sections won't react */
            context.mouseWheel = 0D;

            return this;
        }

        return super.childrenMouseScrolled(context);
    }

    /**
     * Draw icons for indicating different active states.
     */
    private void renderIcons(UIContext context)
    {
        /* No global status icons are currently rendered here. */
    }

    @Override
    public void startRenderFrame(float tickDelta)
    {
        super.startRenderFrame(tickDelta);

        if (!this.needsViewportRender())
        {
            return;
        }

        this.controller.startRenderFrame(tickDelta);
    }

    @Override
    public void renderInWorld(WorldRenderContext context)
    {
        if (!this.needsViewportRender())
        {
            return;
        }

        super.renderInWorld(context);

        if (!BBSRendering.isIrisShadowPass())
        {
            this.lastProjection.set(RenderSystem.getProjectionMatrix());
            MatrixStack ms = context.matrixStack();
            if (ms != null)
            {
                this.lastView.set(ms.peek().getPositionMatrix());
            }
            else
            {
                this.lastView.set(RenderSystem.getModelViewMatrix());
            }
        }

        this.controller.renderFrame(context);
        this.cacheGizmoMatrix();
    }

    private void cacheGizmoMatrix()
    {
        if (Gizmo.INSTANCE.hasGizmoMatrix)
        {
            this.lastGizmoMatrix.set(Gizmo.INSTANCE.lastGizmoMatrix);
            this.hasLastGizmoMatrix = true;
        }
        else
        {
            this.hasLastGizmoMatrix = false;
        }
    }

    /* IUICameraWorkDelegate implementation */

    public void notifyServer(ActionState state)
    {
        if (this.data == null || !ClientNetwork.isIsBBSModOnServer())
        {
            return;
        }

        String id = this.data.getId();
        int tick = this.getCursor();

        ClientNetwork.sendActionState(id, state, tick);
    }

    public Camera getCamera()
    {
        return this.camera;
    }

    public Camera getWorldCamera()
    {
        return BBSModClient.getCameraController().camera;
    }

    public CameraController getCameraController()
    {
        return BBSModClient.getCameraController();
    }

    @Override
    public int getCursor()
    {
        return this.runner.ticks;
    }

    @Override
    public void setCursor(int value)
    {
        this.setCursor(value, true);
    }

    /**
     * @param applyWorldActions when false, soft-sync the server tick without
     *        walking {@link ActionPlayer#goTo} (avoids
     *        re-firing swipe / break / drop clips on a programmatic restore).
     */
    public void setCursor(int value, boolean applyWorldActions)
    {
        this.flightEditTime.mark();
        this.lastPosition.set(Position.ZERO);

        int previous = this.runner.ticks;

        this.runner.ticks = Math.max(0, value);

        this.notifyServer(applyWorldActions ? ActionState.SEEK : ActionState.SYNC);

        if (previous != this.runner.ticks)
        {
            this.refreshCursorFields();
        }
    }

    /**
     * Refresh open clip side panels so keyframe-linked fields match the timeline cursor.
     */
    private void refreshCursorFields()
    {
        /* Update lastFilledCursor only after a successful fill. If fillData throws
         * mid-panel (e.g. bad keyframe factory types), the next frame must retry
         * so later fields are not left permanently stale. */
        this.fillData();
        this.lastFilledCursor = this.runner.ticks;
    }

    public boolean isRunning()
    {
        return this.runner.isRunning();
    }

    public void togglePlayback()
    {
        this.setFlight(false);

        this.runner.toggle(this.getCursor());
        this.lastRunning = this.runner.isRunning();

        if (this.runner.isRunning())
        {
            this.cameraEditor.clips.scale.shiftIntoMiddle(this.getCursor());

            if (this.replayEditor.keyframeEditor != null)
            {
                this.replayEditor.keyframeEditor.view.getXAxis().shiftIntoMiddle(this.getCursor());
            }
        }
    }

    public boolean canUseKeybinds()
    {
        return !this.isFlying();
    }

    public void fillData()
    {
        this.cameraEditor.fillData();
        this.actionEditor.fillData();

        if (this.replayEditor.keyframeEditor != null && this.replayEditor.keyframeEditor.editor != null)
        {
            this.replayEditor.keyframeEditor.editor.update();
        }
    }

    public void teleportToCamera()
    {
        Camera camera = this.getCamera();
        Vector3d cameraPos = camera.position;
        double x = cameraPos.x;
        double y = cameraPos.y;
        double z = cameraPos.z;

        PlayerUtils.teleport(x, y, z, MathUtils.toDeg(camera.rotation.y) - 180F, MathUtils.toDeg(camera.rotation.x));
    }

    public boolean checkShowNoCamera()
    {
        boolean noCamera = this.getData().camera.calculateDuration() <= 0;

        if (noCamera)
        {
            UIOverlay.addOverlay(this.getContext(), new UIMessageOverlayPanel(
                UIKeys.FILM_NO_CAMERA_TITLE,
                UIKeys.FILM_NO_CAMERA_DESCRIPTION
            ));
        }

        return noCamera;
    }

    public void updateActors(String filmId, Map<String, Integer> actors)
    {
        if (this.data != null && this.data.getId().equals(filmId))
        {
            this.controller.updateActors(actors);
        }
    }

    @Override
    public boolean handleKeyPressed(UIContext context)
    {
        return this.controller.orbit.keyPressed(context, this.preview.area);
    }

    /**
     * Restores film editor UI from an undo/redo snapshot (panel, cursor, timelines,
     * embedded keyframe editors, scroll/zoom/viewport).
     */
    public void applyFilmUndoData(MapType data)
    {
        this.applyAllUndoData(data);
    }

    public MapType collectFilmUndoSnapshot()
    {
        UIElement root = this.getRoot();

        if (root == null)
        {
            return new MapType();
        }

        return (MapType) root.collectAllUndoData().copy();
    }

    public boolean hasEmbeddedClipView()
    {
        return this.cameraEditor.clips.hasEmbeddedView() || this.actionEditor.clips.hasEmbeddedView();
    }

    public String getEmbeddedClipEditorUndoId()
    {
        if (this.cameraEditor.clips.hasEmbeddedView())
        {
            return this.cameraEditor.getUndoId();
        }

        if (this.actionEditor.clips.hasEmbeddedView())
        {
            return this.actionEditor.getUndoId();
        }

        return "";
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        this.showPanel(data.getInt("panel"));
        this.setCursor(data.getInt("tick"));
        this.controller.createEntities();
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        data.putInt("panel", this.getPanelIndex());
        data.putInt("tick", this.getCursor());
    }

    @Override
    protected boolean canSave(UIContext context)
    {
        return !this.recorder.isRecording();
    }

    private void openSelectedFilmFromHome()
    {
        String selected = this.getSelectedHomeFilmId();

        if (selected != null)
        {
            this.openFilmInDocumentTabs(selected);
        }
    }

    private String getSelectedHomeFilmId()
    {
        String selected = null;
        if (this.homeFilmsMosaic != null && this.homeFilmsMosaic.isVisible())
        {
            selected = this.homeFilmsMosaic.selectedId;
        }
        else
        {
            DataPath path = this.homeFilmsList.getCurrentFirst();
            selected = path == null ? null : path.toString();
        }

        if (selected == null)
        {
            return null;
        }

        /* Check if the selected ID corresponds to a folder */
        DataPath selectedPath = this.homeFilmsMosaic != null ? this.homeFilmsMosaic.findPath(selected) : null;
        if (selectedPath == null)
        {
            DataPath path = this.homeFilmsList.getCurrentFirst();
            selectedPath = path;
        }
        if (selectedPath != null && selectedPath.folder)
        {
            return null;
        }

        return selected;
    }

    private void toggleMosaicView()
    {
        boolean isMosaic = !this.homeFilmsMosaic.isVisible();

        this.homeFilmsMosaic.setVisible(isMosaic);
        this.homeFilmsList.setVisible(!isMosaic);
        this.homeViewToggle.both(isMosaic ? Icons.LIST : Icons.GALLERY);
        this.homeViewToggle.tooltip(isMosaic ? UIKeys.MODELS_HOME_VIEW_LIST : UIKeys.MODELS_HOME_VIEW_MOSAIC, Direction.LEFT);

        BBSSettings.lastViewMosaic.set(isMosaic);

        if (isMosaic)
        {
            this.homeFilmsMosaic.filter("");
        }
    }

    private void addFolderFromHome()
    {
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_ADD_FOLDER,
            (str) -> {
                String path = this.homeFilmsList.getPath(str).toString();
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

    private void copyHomeFilm()
    {
        String selectedId = this.getSelectedHomeFilmId();
        if (selectedId == null) return;

        this.getType().getRepository().load(selectedId, (film) -> {
            if (film != null) {
                Window.setInMemoryClipboard(film.toData().asMap(), "_ContentType_" + this.getType().getId());
            }
        });
    }

    private void pasteHomeFilm(MapType data)
    {
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_ADD,
            UIKeys.PANELS_MODALS_ADD,
            (str) -> {
                String targetId = this.homeFilmsList.getPath(str).toString();
                if (targetId.trim().isEmpty()) {
                    this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);
                    return;
                }
                if (this.homeFilmsList.hasInHierarchy(targetId)) {
                    return;
                }

                Film newFilm = (Film) this.getType().getRepository().create(targetId, data);
                this.fill(newFilm);
            }
        );

        panel.text.filename();

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    @Override
    public void requestNames()
    {
        if (this.isAllWorldsBrowseMode())
        {
            this.refreshAllWorldsFilms();

            return;
        }

        UIDataUtils.requestNames(this.getType(), this::fillNames);
    }

    public boolean isAllWorldsBrowseMode()
    {
        return false;
    }

    private IKey getHomeListTitle()
    {
        if (this.isAllWorldsBrowseMode())
        {
            return UIKeys.FILM_HOME_WORLDS;
        }

        return UIKeys.FILM_HOME_LIST;
    }

    private void refreshAllWorldsFilms()
    {
        if (this.crossWorldScanning)
        {
            return;
        }

        this.crossWorldScanning = true;

        CrossWorldFilmScanner.scanAsync().whenComplete((entries, error) ->
        {
            MinecraftClient.getInstance().execute(() ->
            {
                this.crossWorldScanning = false;
                this.crossWorldFilmEntries.clear();
                this.crossWorldWorldLabels.clear();

                if (error != null || entries == null)
                {
                    if (error != null)
                    {
                        error.printStackTrace();
                    }

                    this.fillNames(Collections.emptyList());

                    return;
                }

                List<String> paths = new ArrayList<>();

                for (CrossWorldFilmEntry entry : entries)
                {
                    String path = entry.worldFolder + "/" + entry.filmId;

                    paths.add(path);
                    this.crossWorldFilmEntries.put(path, entry);
                    this.crossWorldWorldLabels.put(entry.worldFolder, entry.worldLabel);
                }

                this.fillNames(paths);
            });
        });
    }

    public boolean isFilmTabLoaded(String tabId)
    {
        return tabId != null && tabId.equals(this.loadedFilmTabKey);
    }

    public CrossWorldFilmEntry resolveCrossWorldEntryForTab(String tabId)
    {
        return this.resolveCrossWorldEntryFromTab(tabId);
    }

    private boolean shouldSetPendingJoin(CrossWorldFilmEntry entry)
    {
        if (entry == null)
        {
            return false;
        }

        return !WorldLaunchHelper.isCurrentWorld(MinecraftClient.getInstance(), entry.worldFolder);
    }

    private CrossWorldFilmEntry resolveCrossWorldEntryFromTab(String tabId)
    {
        CrossWorldFilmEntry decoded = CrossWorldFilmEntry.decodeKey(tabId);

        if (decoded != null)
        {
            String label = this.crossWorldWorldLabels.get(decoded.worldFolder);

            if (label != null)
            {
                return new CrossWorldFilmEntry(decoded.worldFolder, label, decoded.filmId);
            }

            return decoded;
        }

        return this.crossWorldFilmEntries.get(tabId);
    }

    private void requestCrossWorldFilmData(CrossWorldFilmEntry entry)
    {
        if (entry == null || entry.filmId.endsWith("/"))
        {
            return;
        }

        CrossWorldFilmLoader.load(entry.worldFolder, entry.filmId, (film) -> this.fill(film));
    }

    private boolean isCrossWorldPreviewInForeignWorld()
    {
        if (this.crossWorldPendingJoin == null)
        {
            return false;
        }

        return !WorldLaunchHelper.isCurrentWorld(MinecraftClient.getInstance(), this.crossWorldPendingJoin.worldFolder);
    }

    public boolean canShowJoinWorld()
    {
        if (this.crossWorldPendingJoin == null || this.crossWorldPendingJoin.filmId.endsWith("/"))
        {
            return false;
        }

        if (this.data == null)
        {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        return !WorldLaunchHelper.isCurrentWorld(client, this.crossWorldPendingJoin.worldFolder);
    }

    public void openCrossWorldFilm(CrossWorldFilmEntry entry)
    {
        if (entry == null || entry.filmId.endsWith("/"))
        {
            return;
        }

        this.crossWorldPendingJoin = this.shouldSetPendingJoin(entry) ? entry : null;
        this.crossWorldFilmEntries.put(entry.encodeKey(), entry);
        this.crossWorldWorldLabels.put(entry.worldFolder, entry.worldLabel);

        if (this.dashboard != null && this.dashboard.documentTabsBar != null)
        {
            this.dashboard.documentTabsBar.addOrActivate(ContentType.FILMS, entry.encodeKey());
        }
        else
        {
            this.openFilmInDocumentTabs(entry.encodeKey());
        }
    }

    public void joinPendingWorld()
    {
        if (this.crossWorldPendingJoin != null)
        {
            FilmLaunchHelper.launch(this.crossWorldPendingJoin);
            this.crossWorldPendingJoin = null;
        }
    }

    public void clearCrossWorldPendingJoin()
    {
        this.crossWorldPendingJoin = null;
    }

    public void closeCrossWorldFilmTab(String filmId)
    {
        if (filmId == null || filmId.isEmpty())
        {
            return;
        }

        for (int i = this.filmDocumentTabs.size() - 1; i >= 0; i--)
        {
            FilmDocumentTab tab = this.filmDocumentTabs.get(i);

            if (tab.home || tab.filmId == null)
            {
                continue;
            }

            CrossWorldFilmEntry decoded = CrossWorldFilmEntry.decodeKey(tab.filmId);

            if (decoded != null && filmId.equals(decoded.filmId))
            {
                this.removeFilmDocumentTab(i);
            }
        }
    }

    private void handleHomeFilmsSelection(List<DataPath> list)
    {
        String selected = this.getSelectedHomeFilmId();
        this.overlay.namesList.setCurrentFile(selected);

        this.updateHomeButtonsState();

        if (selected == null)
        {
            return;
        }

        if (this.isAllWorldsBrowseMode())
        {
            CrossWorldFilmEntry entry = this.crossWorldFilmEntries.get(selected);

            if (entry != null && !entry.filmId.endsWith("/"))
            {
                this.crossWorldPendingJoin = this.shouldSetPendingJoin(entry) ? entry : null;
            }
        }

        long now = System.currentTimeMillis();
        boolean sameAsPrevious = selected.equals(this.homeLastClickedFilmId);
        boolean doubleClick = sameAsPrevious && now - this.homeLastClickTime <= 300L;

        this.homeLastClickedFilmId = selected;
        this.homeLastClickTime = now;

        boolean alreadyOpen = this.findTabByFilmId(selected) >= 0
            || (this.dashboard != null && this.dashboard.documentTabsBar != null
                && this.dashboard.documentTabsBar.isOpen(ContentType.FILMS, selected));

        if (doubleClick || alreadyOpen)
        {
            if (this.isAllWorldsBrowseMode())
            {
                CrossWorldFilmEntry entry = this.crossWorldFilmEntries.get(selected);

                if (entry != null && !entry.filmId.endsWith("/"))
                {
                    this.crossWorldPendingJoin = this.shouldSetPendingJoin(entry) ? entry : null;
                    this.crossWorldFilmEntries.put(entry.encodeKey(), entry);
                    this.crossWorldWorldLabels.put(entry.worldFolder, entry.worldLabel);
                    FilmLaunchHelper.openCrossWorldFilm(entry);
                }
            }
            else
            {
                this.openFilmInDocumentTabs(selected);
            }
        }
    }

    private void updateHomeButtonsState()
    {
        boolean hasSelectedFilm = this.getSelectedHomeFilmId() != null;
        boolean enableIcons = !this.showingHomePage;

        if (this.homeDuplicateCurrent != null)
        {
            this.homeDuplicateCurrent.setEnabled(hasSelectedFilm);
        }

        if (this.homeRenameCurrent != null)
        {
            this.homeRenameCurrent.setEnabled(hasSelectedFilm);
        }

        if (this.homeDeleteCurrent != null)
        {
            this.homeDeleteCurrent.setEnabled(hasSelectedFilm);
        }

        if (this.openOverlay != null) this.openOverlay.setEnabled(enableIcons);
        if (this.saveIcon != null) this.saveIcon.setEnabled(enableIcons);
        if (this.toggleHorizontal != null) this.toggleHorizontal.setEnabled(enableIcons);
        if (this.layoutLock != null) this.layoutLock.setEnabled(enableIcons);
        if (this.layoutPresets != null) this.layoutPresets.setEnabled(enableIcons);
        this.layoutFilmStatusIcons();
    }

    private UIButton createHomeButton(IKey label, Icon icon, Consumer<UIButton> callback)
    {
        UIButton button = new UIButton(label, callback) {
            @Override
            protected void renderSkin(UIContext context)
            {
                int bg = this.hover ? Colors.setA(Colors.WHITE, 0.25F) : Colors.setA(0, 0.4F);
                this.area.render(context.batcher, bg);

                int color = this.isEnabled() ? Colors.LIGHTEST_GRAY : 0x44ffffff;

                if (icon != null) {
                    context.batcher.icon(icon, color, this.area.x + 4, this.area.y + this.area.h / 2 - icon.h / 2);
                }

                context.batcher.textShadow(this.label.get(), this.area.x + 22, this.area.y + this.area.h / 2 - 4, color);
            }
        };
        button.h(20);
        return button;
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

    private void createHomeDocumentTab(boolean activate)
    {
        this.filmDocumentTabs.add(new FilmDocumentTab(true, null));
        int index = this.filmDocumentTabs.size() - 1;

        this.rebuildFilmDocumentTabs();

        if (activate)
        {
            this.activateFilmDocumentTab(index, false);
        }
    }

    private void addHomeDocumentTab()
    {
        int insertAt = Math.max(0, this.activeFilmDocumentTab + 1);

        this.filmDocumentTabs.add(insertAt, new FilmDocumentTab(true, null));
        this.rebuildFilmDocumentTabs();
        this.activateFilmDocumentTab(insertAt, false);
    }

    private void ensureHomeDocumentTab()
    {
        for (FilmDocumentTab tab : this.filmDocumentTabs)
        {
            if (tab.home)
            {
                return;
            }
        }

        this.filmDocumentTabs.add(new FilmDocumentTab(true, null));
    }

    private int findTabByFilmId(String id)
    {
        for (int i = 0; i < this.filmDocumentTabs.size(); i++)
        {
            FilmDocumentTab tab = this.filmDocumentTabs.get(i);

            if (!tab.home && id.equals(tab.filmId))
            {
                return i;
            }
        }

        return -1;
    }

    private void openFilmInDocumentTabs(String id)
    {
        if (id == null || id.trim().isEmpty())
        {
            return;
        }

        int existingIndex = this.findTabByFilmId(id);

        if (existingIndex >= 0)
        {
            this.activateFilmDocumentTab(existingIndex, true);

            return;
        }

        if (this.activeFilmDocumentTab < 0 || this.activeFilmDocumentTab >= this.filmDocumentTabs.size())
        {
            if (this.filmDocumentTabs.isEmpty())
            {
                this.filmDocumentTabs.add(new FilmDocumentTab(true, null));
            }

            this.activeFilmDocumentTab = 0;
        }

        FilmDocumentTab active = this.filmDocumentTabs.get(this.activeFilmDocumentTab);

        if (active.home)
        {
            active.home = false;
            active.filmId = id;
            this.rebuildFilmDocumentTabs();
            this.activateFilmDocumentTab(this.activeFilmDocumentTab, true);
        }
        else
        {
            int insertAt = this.activeFilmDocumentTab + 1;
            this.filmDocumentTabs.add(insertAt, new FilmDocumentTab(false, id));
            this.rebuildFilmDocumentTabs();
            this.activateFilmDocumentTab(insertAt, true);
        }
    }

    private void activateFilmDocumentTab(int index, boolean loadFilm)
    {
        if (index < 0 || index >= this.filmDocumentTabs.size())
        {
            return;
        }

        if (this.data != null && this.activeFilmDocumentTab != index)
        {
            this.discardProvisionalPosePreviews();
            this.save();
        }

        this.activeFilmDocumentTab = index;

        FilmDocumentTab tab = this.filmDocumentTabs.get(index);

        if (tab.home)
        {
            if (this.replayEditor != null && this.replayEditor.replays != null && this.replayEditor.replays.hasParent() && this.replayEditor.replays.getParent() instanceof UIOverlay overlay)
            {
                overlay.closeItself();
            }

            this.fill(null);
        }
        else
        {
            CrossWorldFilmEntry crossWorldEntry = this.resolveCrossWorldEntryFromTab(tab.filmId);

            if (crossWorldEntry != null)
            {
                this.crossWorldPendingJoin = this.shouldSetPendingJoin(crossWorldEntry) ? crossWorldEntry : null;

                if (loadFilm || !tab.filmId.equals(this.loadedFilmTabKey))
                {
                    this.requestCrossWorldFilmData(crossWorldEntry);
                }
                else
                {
                    this.updateFilmDocumentView();
                }
            }
            else if (loadFilm || !tab.filmId.equals(this.loadedFilmTabKey))
            {
                this.crossWorldPendingJoin = null;
                this.requestData(tab.filmId);
            }
            else
            {
                this.updateFilmDocumentView();
            }
        }

        this.rebuildFilmDocumentTabs();
        this.loadedFilmTabKey = tab.home ? null : tab.filmId;
    }

    private void removeFilmDocumentTab(int index)
    {
        if (index < 0 || index >= this.filmDocumentTabs.size())
        {
            return;
        }

        this.filmDocumentTabs.remove(index);

        if (this.filmDocumentTabs.isEmpty())
        {
            this.filmDocumentTabs.add(new FilmDocumentTab(true, null));
            this.activeFilmDocumentTab = 0;
            this.rebuildFilmDocumentTabs();
            this.activateFilmDocumentTab(0, false);

            return;
        }

        if (index < this.activeFilmDocumentTab)
        {
            this.activeFilmDocumentTab--;
        }
        else if (index == this.activeFilmDocumentTab)
        {
            this.activeFilmDocumentTab = Math.max(0, Math.min(this.activeFilmDocumentTab, this.filmDocumentTabs.size() - 1));
        }

        this.rebuildFilmDocumentTabs();
        this.activateFilmDocumentTab(this.activeFilmDocumentTab, false);
    }

    private void rebuildFilmDocumentTabs()
    {
        /* No-op: the legacy tab bar UI was removed; the unified UIDocumentTabsBar at the dashboard level replaces it. */
    }

    private void syncActiveDocumentTabWithData(Film data)
    {
        if (data != null)
        {
            if (this.activeFilmDocumentTab < 0 || this.activeFilmDocumentTab >= this.filmDocumentTabs.size())
            {
                this.filmDocumentTabs.add(new FilmDocumentTab(false, data.getId()));
                this.activeFilmDocumentTab = this.filmDocumentTabs.size() - 1;
            }
            else
            {
                FilmDocumentTab tab = this.filmDocumentTabs.get(this.activeFilmDocumentTab);

                tab.home = false;
                tab.filmId = data.getId();
            }
        }

        this.rebuildFilmDocumentTabs();
        this.updateFilmDocumentView();
    }

    private void setWorkspaceVisible(boolean visible)
    {
        if (!visible)
        {
            this.cameraEditor.setVisible(false);
            this.replayEditor.setVisible(false);
            this.actionEditor.setVisible(false);
            this.editArea.setVisible(false);
            this.cameraEditArea.setVisible(false);
            this.preview.setVisible(false);
        }
        this.draggableMain.setVisible(visible);
        this.draggableEditor.setVisible(visible);

        if (!visible)
        {
            for (UIDraggable handle : this.dragHandlesById.values())
            {
                handle.setVisible(false);
            }
        }

        for (UIDraggable handle : this.splitterHandles)
        {
            handle.setVisible(visible);
        }

        for (UITabBar tabBar : this.tabBars)
        {
            tabBar.setVisible(visible);
        }
    }

    private void updateFilmDocumentView()
    {
        if (this.performingLayout)
        {
            return;
        }

        this.performingLayout = true;

        boolean home = this.activeFilmDocumentTab < 0
            || this.activeFilmDocumentTab >= this.filmDocumentTabs.size()
            || this.filmDocumentTabs.get(this.activeFilmDocumentTab).home
            || this.data == null;

        this.showingHomePage = home;
        this.homePage.setVisible(home);
        this.editor.setVisible(true);
        this.setWorkspaceVisible(!home);
        /* The side icon bar is now empty (its buttons moved to the menus and the workspace tab
           bar), so keep it hidden — this also removes the leftover vertical divider it drew. */
        this.iconBar.setVisible(false);
        this.workspaceTabs.setVisible(false);
        if (this.bottomIcons != null)
        {
            this.bottomIcons.setVisible(false);
        }
        this.updateHomeButtonsState();

        if (home)
        {
            this.editor.resetFlex().relative(this).w(1F).h(1F);
            this.crossWorldPendingJoin = null;
        }
        else
        {
            this.editor.resetFlex().relative(this).w(1F).h(1F);
            this.setupEditorFlex(true, false, false);
        }
        this.resize();
        TimelineToolbarDockSync.refreshFilmPanel(this);

        this.performingLayout = false;
        this.syncViewportRenderMode();
    }

    private void renderHomeBanner(UIContext context)
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
        int dividerX = this.homeFilmsSearch.area.x;

        // Render deeper background for the aurora to pop
        context.batcher.box(editorX, editorY, editorX + editorW, editorY + editorH, Colors.setA(0x0b0b0b, 1F));
        
        // Render Animated Aurora Effect
        int primary = BBSSettings.primaryColor.get();
        float tick = context.getTickTransition() * 0.015F;
        int segments = 40;
        float segW = editorW / (float) segments;
        
        Matrix4f matrix4f = context.batcher.getContext().getMatrices().peek().getPositionMatrix();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        
        float[] yBot1 = new float[segments + 1];
        float[] yMid1 = new float[segments + 1];
        int[] cMid1 = new int[segments + 1];
        
        float[] yBot2 = new float[segments + 1];
        float[] yMid2 = new float[segments + 1];
        int[] cMid2 = new int[segments + 1];
        
        for (int i = 0; i <= segments; i++)
        {
            float nx = (float) i / segments;
            
            float w1 = (float) Math.sin(tick * 1.2F + nx * 8F);
            float w2 = (float) Math.sin(tick * 0.7F + nx * 15F);
            float w3 = (float) Math.cos(tick * 0.4F - nx * 12F);
            float comb1 = (w1 + w2 + w3) / 3F;
            
            float curtainYTop = editorY + editorH * 0.05F;
            float curtainYBot = editorY + editorH * 0.5F + comb1 * (editorH * 0.35F);
            
            if (curtainYBot < curtainYTop + 10) curtainYBot = curtainYTop + 10;
            
            float transitionY = curtainYBot - editorH * 0.3F;
            if (transitionY < curtainYTop) transitionY = curtainYTop;
            
            yBot1[i] = curtainYBot;
            yMid1[i] = transitionY;
            cMid1[i] = Colors.setA(primary, 0.15F + Math.max(0, comb1) * 0.2F);
            
            float w4 = (float) Math.sin(tick * 1.5F - nx * 10F);
            float w5 = (float) Math.cos(tick * 0.9F + nx * 18F);
            float comb2 = (w4 + w5) / 2F;
            
            float curtain2YTop = editorY + editorH * 0.15F;
            float curtain2YBot = editorY + editorH * 0.75F + comb2 * (editorH * 0.25F);
            
            if (curtain2YBot < curtain2YTop + 10) curtain2YBot = curtain2YTop + 10;
            
            float transition2Y = curtain2YBot - editorH * 0.25F;
            if (transition2Y < curtain2YTop) transition2Y = curtain2YTop;
            
            yBot2[i] = curtain2YBot;
            yMid2[i] = transition2Y;
            cMid2[i] = Colors.setA(Colors.mulRGB(primary, 0.8F), 0.1F + Math.max(0, comb2) * 0.15F);
        }
        
        int colTop = Colors.setA(primary, 0.0F);
        int colBot = Colors.setA(primary, 0.0F);
        float yTop1 = editorY + editorH * 0.05F;
        float yTop2 = editorY + editorH * 0.15F;
        
        for (int i = 0; i < segments; i++)
        {
            float x1 = editorX + i * segW;
            float x2 = editorX + (i + 1) * segW;
            
            // Layer 1 - Upper Quad (yTop1 -> yMid1)
            builder.vertex(matrix4f, x1, yTop1, 0).color(colTop);
            builder.vertex(matrix4f, x1, yMid1[i], 0).color(cMid1[i]);
            builder.vertex(matrix4f, x2, yMid1[i+1], 0).color(cMid1[i+1]);
            builder.vertex(matrix4f, x2, yTop1, 0).color(colTop);
            
            // Layer 1 - Lower Quad (yMid1 -> yBot1)
            builder.vertex(matrix4f, x1, yMid1[i], 0).color(cMid1[i]);
            builder.vertex(matrix4f, x1, yBot1[i], 0).color(colBot);
            builder.vertex(matrix4f, x2, yBot1[i+1], 0).color(colBot);
            builder.vertex(matrix4f, x2, yMid1[i+1], 0).color(cMid1[i+1]);
            
            // Layer 2 - Upper Quad (yTop2 -> yMid2)
            builder.vertex(matrix4f, x1, yTop2, 0).color(colTop);
            builder.vertex(matrix4f, x1, yMid2[i], 0).color(cMid2[i]);
            builder.vertex(matrix4f, x2, yMid2[i+1], 0).color(cMid2[i+1]);
            builder.vertex(matrix4f, x2, yTop2, 0).color(colTop);
            
            // Layer 2 - Lower Quad (yMid2 -> yBot2)
            builder.vertex(matrix4f, x1, yMid2[i], 0).color(cMid2[i]);
            builder.vertex(matrix4f, x1, yBot2[i], 0).color(colBot);
            builder.vertex(matrix4f, x2, yBot2[i+1], 0).color(colBot);
            builder.vertex(matrix4f, x2, yMid2[i+1], 0).color(cMid2[i+1]);
        }
        
        BufferRenderer.drawWithGlobalProgram(builder.end());

        UIHomePanel home = this.dashboard.getPanel(UIHomePanel.class);
        if (home != null)
        {
            home.renderCardAndBanners(context, this.homePage, dividerX, this.getHomeListTitle().get(), true);
        }
    }

    private static class FilmDocumentTab
    {
        private boolean home;
        private String filmId;

        private FilmDocumentTab(boolean home, String filmId)
        {
            this.home = home;
            this.filmId = filmId;
        }
    }

    private final List<UITabBar> tabBars = new ArrayList<>();

    private EditorLayoutNode.TabbedNode resolveLayoutTabbedNode(EditorLayoutNode layoutRoot, EditorLayoutNode.TabbedNode visibleTabbed)
    {
        if (layoutRoot == null || visibleTabbed == null)
        {
            return null;
        }

        for (EditorLayoutNode tab : visibleTabbed.tabs)
        {
            if (tab instanceof EditorLayoutNode.PanelNode)
            {
                String panelId = ((EditorLayoutNode.PanelNode) tab).getPanelId();
                EditorLayoutNode.TabbedNode real = this.findTabbedNodeContaining(layoutRoot, panelId);

                if (real != null)
                {
                    return real;
                }
            }
        }

        return null;
    }

    private void promoteActiveTabIfHidden(EditorLayoutNode.TabbedNode tabbed)
    {
        if (tabbed == null || tabbed.tabs.isEmpty())
        {
            return;
        }

        int safeActiveTab = Math.max(0, Math.min(tabbed.tabs.size() - 1, tabbed.activeTab));
        EditorLayoutNode activeNode = tabbed.tabs.get(safeActiveTab);

        /* If the active tab was hidden (via Window menu), promote the first non-hidden
           tab to active so the tab group doesn't render empty (and so we still have
           stable bounds to place the tab strip). */
        if (activeNode instanceof EditorLayoutNode.PanelNode)
        {
            String activeId = ((EditorLayoutNode.PanelNode) activeNode).getPanelId();

            if (this.hiddenPanels.contains(activeId))
            {
                for (int t = 0; t < tabbed.tabs.size(); t++)
                {
                    EditorLayoutNode candidate = tabbed.tabs.get(t);

                    if (candidate instanceof EditorLayoutNode.PanelNode)
                    {
                        String id = ((EditorLayoutNode.PanelNode) candidate).getPanelId();

                        if (!this.hiddenPanels.contains(id))
                        {
                            tabbed.activeTab = t;

                            break;
                        }
                    }
                }
            }
        }
    }

    private UITabBar findTabBarForGroup(String anchorPanelId)
    {
        if (anchorPanelId == null)
        {
            return null;
        }

        for (UITabBar bar : this.tabBars)
        {
            if (bar.containsPanel(anchorPanelId))
            {
                return bar;
            }
        }

        return null;
    }

    /**
     * Stable key for a tab group so horizontal scroll survives tab-bar rebuilds
     * (selection / reorder). Panel ids are sorted so reorder does not change the key.
     */
    private String getTabBarScrollKey(EditorLayoutNode.TabbedNode tabbed)
    {
        if (tabbed == null)
        {
            return null;
        }

        List<String> ids = new ArrayList<>();

        for (EditorLayoutNode tab : tabbed.tabs)
        {
            if (tab instanceof EditorLayoutNode.PanelNode)
            {
                ids.add(((EditorLayoutNode.PanelNode) tab).getPanelId());
            }
        }

        if (ids.isEmpty())
        {
            return null;
        }

        Collections.sort(ids);

        return String.join("|", ids);
    }

    private Map<String, Integer> captureTabBarScroll()
    {
        Map<String, Integer> scrolls = new HashMap<>();

        for (UITabBar bar : this.tabBars)
        {
            String key = this.getTabBarScrollKey(bar.getTabbedNode());

            if (key != null)
            {
                scrolls.put(key, bar.scroll);
            }
        }

        return scrolls;
    }

    private void restoreTabBarScroll(Map<String, Integer> scrolls)
    {
        if (scrolls == null || scrolls.isEmpty())
        {
            return;
        }

        for (UITabBar bar : this.tabBars)
        {
            String key = this.getTabBarScrollKey(bar.getTabbedNode());
            Integer scroll = key == null ? null : scrolls.get(key);

            if (scroll != null)
            {
                bar.scroll = scroll;
                bar.clampScroll();
            }
        }
    }

    private float[] getTabGroupBounds(EditorLayoutNode.TabbedNode tabbed, Map<String, float[]> bounds)
    {
        if (tabbed == null || bounds == null)
        {
            return null;
        }

        int safeActiveTab = Math.max(0, Math.min(tabbed.tabs.size() - 1, tabbed.activeTab));
        EditorLayoutNode activeNode = tabbed.tabs.get(safeActiveTab);

        if (activeNode instanceof EditorLayoutNode.PanelNode)
        {
            String activeId = ((EditorLayoutNode.PanelNode) activeNode).getPanelId();
            float[] activeBounds = bounds.get(activeId);

            if (activeBounds != null)
            {
                return activeBounds;
            }
        }

        /* Bounds are computed from the visible layout tree, which may use a different active tab
           than the saved layout after hidden panels are removed. Fall back to any visible tab. */
        for (EditorLayoutNode tab : tabbed.tabs)
        {
            if (tab instanceof EditorLayoutNode.PanelNode)
            {
                String panelId = ((EditorLayoutNode.PanelNode) tab).getPanelId();

                if (!this.hiddenPanels.contains(panelId))
                {
                    float[] tabBounds = bounds.get(panelId);

                    if (tabBounds != null)
                    {
                        return tabBounds;
                    }
                }
            }
        }

        return null;
    }

    private void setupTabBars(EditorLayoutNode layoutRoot, EditorLayoutNode visibleRoot, Map<String, float[]> bounds, boolean recreate)
    {
        List<EditorLayoutNode.TabbedNode> tabbedNodes = new ArrayList<>();
        EditorLayoutNode.collectTabbedNodes(visibleRoot, tabbedNodes);

        Set<UITabBar> usedTabBars = new HashSet<>();

        for (int i = 0; i < tabbedNodes.size(); i++)
        {
            EditorLayoutNode.TabbedNode visibleTabbed = tabbedNodes.get(i);

            if (visibleTabbed.tabs.size() < 2)
            {
                continue;
            }

            EditorLayoutNode.TabbedNode tabbed = this.resolveLayoutTabbedNode(layoutRoot, visibleTabbed);

            if (tabbed == null)
            {
                continue;
            }

            this.promoteActiveTabIfHidden(tabbed);

            int safeActiveTab = Math.max(0, Math.min(tabbed.tabs.size() - 1, tabbed.activeTab));
            EditorLayoutNode activeNode = tabbed.tabs.get(safeActiveTab);

            if (activeNode instanceof EditorLayoutNode.PanelNode)
            {
                String activeId = ((EditorLayoutNode.PanelNode) activeNode).getPanelId();
                float[] b = this.getTabGroupBounds(tabbed, bounds);

                if (b != null)
                {
                    UITabBar tabBar;

                    if (recreate)
                    {
                        tabBar = new UITabBar(this, tabbed);
                        this.tabBars.add(tabBar);
                        this.editor.add(tabBar);
                    }
                    else
                    {
                        tabBar = this.findTabBarForGroup(activeId);

                        if (tabBar == null)
                        {
                            continue;
                        }

                        tabBar.rebindTabbedNode(tabbed);
                    }

                    usedTabBars.add(tabBar);

                    boolean locked = BBSSettings.editorLayoutSettings.isLayoutLocked();
                    /* Tab strips stay visible when the layout is locked so docked panels can still
                       be switched. Locking only disables dragging/resizing, not tab selection. */
                    int headerInset = PANEL_HEADER_HEIGHT;

                    tabBar.relative(this.editor).x(b[0], 3).y(b[1], 3).w(b[2], -6).h(0F, PANEL_HEADER_HEIGHT);
                    tabBar.setVisible(true);

                    for (EditorLayoutNode tab : tabbed.tabs)
                    {
                        if (tab instanceof EditorLayoutNode.PanelNode)
                        {
                            String panelId = ((EditorLayoutNode.PanelNode) tab).getPanelId();
                            UIElement el = this.panelById.get(panelId);

                            if (el != null)
                            {
                                el.relative(this.editor).x(b[0], 3).y(b[1], headerInset + 3).w(b[2], -6).h(b[3], -headerInset - 6);
                                boolean isActive = tab == activeNode && !this.hiddenPanels.contains(panelId);
                                el.setVisible(isActive);

                                UIDraggable handle = this.dragHandlesById.get(panelId);

                                if (handle != null)
                                {
                                    handle.setVisible(isActive && !BBSSettings.editorLayoutSettings.isLayoutLocked() && !this.usesPanelInternalDragHandle(panelId));
                                }
                            }

                            UIDraggable handle = this.dragHandlesById.get(panelId);

                            if (handle != null)
                            {
                                if (locked)
                                {
                                    handle.setVisible(false);
                                }
                                else
                                {
                                    boolean visibleHandle = tab == activeNode && !this.hiddenPanels.contains(panelId) && !this.usesPanelInternalDragHandle(panelId);
                                    handle.setVisible(visibleHandle);

                                    if (visibleHandle)
                                    {
                                        handle.relative(this.editor).x(b[0], 3).y(b[1], 3).w(b[2], -6).h(0F, PANEL_HEADER_HEIGHT);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        for (UITabBar bar : this.tabBars)
        {
            if (!usedTabBars.contains(bar))
            {
                bar.setVisible(false);
            }
        }

        this.layoutFilmStatusIcons();
    }

    private void layoutFilmStatusIcons()
    {
        if (this.dashboard != null && this.dashboard.documentTabsBar != null)
        {
            this.dashboard.documentTabsBar.layoutFilmStatusIcons();
        }
    }

    /* Blockbench-style workspace tab bar at the top of the editor: switches the active editor
       (Camera / Replay / Action / Screen) the way Blockbench's Edit / Paint / Animate tabs do. */
    private class UIWorkspaceTabBar extends UIElement
    {
        private static final int TAB_PADDING = 8;

        private final List<WorkspaceTab> tabs = new ArrayList<>();

        public UIWorkspaceTabBar()
        {
            this.tabs.add(new WorkspaceTab(UIKeys.FILM_WORKSPACE_CAMERA, () -> UIFilmPanel.this.cameraEditor));
            this.tabs.add(new WorkspaceTab(UIKeys.FILM_WORKSPACE_REPLAY, () -> UIFilmPanel.this.replayEditor));
            this.tabs.add(new WorkspaceTab(UIKeys.FILM_WORKSPACE_ACTION, () -> UIFilmPanel.this.actionEditor));
        }

        /* Right-align the name-only tabs (Blockbench-style) and cache each tab's bounds. */
        private void layoutTabs(UIContext context)
        {
            int x = this.area.ex();

            for (int i = this.tabs.size() - 1; i >= 0; i--)
            {
                WorkspaceTab tab = this.tabs.get(i);

                tab.w = context.batcher.getFont().getWidth(tab.label.get()) + TAB_PADDING * 2;
                x -= tab.w;
                tab.x = x;
            }
        }

        @Override
        public void render(UIContext context)
        {
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF18181C);
            context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), 0xFF3C3C3C);

            int primary = 0xFF000000 | BBSSettings.primaryColor.get();

            this.layoutTabs(context);

            int textY = this.area.y + (this.area.h - context.batcher.getFont().getHeight()) / 2;

            for (WorkspaceTab tab : this.tabs)
            {
                boolean active = tab.editor.get().isVisible();
                boolean hover = context.mouseX >= tab.x && context.mouseX < tab.x + tab.w
                    && context.mouseY >= this.area.y && context.mouseY < this.area.ey();

                if (active)
                {
                    context.batcher.box(tab.x, this.area.y, tab.x + tab.w, this.area.ey(), 0xFF1D1D1D);
                    context.batcher.box(tab.x, this.area.ey() - 2, tab.x + tab.w, this.area.ey(), primary);
                }
                else if (hover)
                {
                    context.batcher.box(tab.x, this.area.y, tab.x + tab.w, this.area.ey(), 0x22FFFFFF);
                }

                int color = active || hover ? primary : 0xFFFFFFFF;

                context.batcher.text(tab.label.get(), tab.x + TAB_PADDING, textY, color);
            }

            super.render(context);
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context))
            {
                for (WorkspaceTab tab : this.tabs)
                {
                    if (context.mouseX >= tab.x && context.mouseX < tab.x + tab.w)
                    {
                        UIFilmPanel.this.showPanel(tab.editor.get());
                        UIUtils.playClick();

                        break;
                    }
                }

                return true;
            }

            return super.subMouseClicked(context);
        }

        private class WorkspaceTab
        {
            private final IKey label;
            private final Supplier<UIElement> editor;
            private int x;
            private int w;

            private WorkspaceTab(IKey label, Supplier<UIElement> editor)
            {
                this.label = label;
                this.editor = editor;
            }
        }
    }

    private static class UITabBar extends UIElement
    {
        private UIFilmPanel panel;
        private EditorLayoutNode.TabbedNode tabbedNode;
        private int scroll;
        private int totalWidth;

        public UITabBar(UIFilmPanel panel, EditorLayoutNode.TabbedNode tabbedNode)
        {
            this.panel = panel;
            this.tabbedNode = tabbedNode;
            
            for (int i = 0; i < tabbedNode.tabs.size(); i++)
            {
                EditorLayoutNode tab = tabbedNode.tabs.get(i);
                if (tab instanceof EditorLayoutNode.PanelNode)
                {
                    String panelId = ((EditorLayoutNode.PanelNode) tab).getPanelId();
                    UITab tabButton = new UITab(panel, tabbedNode, i, panelId);
                    this.add(tabButton);
                }
            }
        }

        @Override
        public void render(UIContext context)
        {
            if (this.panel.tabReordering && this.panel.tabReorderTabBar == this && this.panel.tabReorderPanelId != null)
            {
                if (!this.panel.isInsideTabBarArea(this, context.mouseX, context.mouseY))
                {
                    this.panel.startTabReorderFromFloat(this.panel.tabReorderPanelId, context.mouseX, context.mouseY);
                }
                else
                {
                    this.panel.updateTabReorder(context.mouseX, context.mouseY);
                }
            }

            this.layoutTabs(context);
            context.batcher.clip(this.area, context);
            this.renderDropGap(context);
            super.render(context);
            context.batcher.unclip(context);
            this.renderDragGhost(context);
        }

        private void renderDropGap(UIContext context)
        {
            if (!this.panel.tabReordering || this.panel.tabReorderTabBar != this || this.panel.tabReorderDropPreview < 0 || this.panel.tabReorderGapW <= 0)
            {
                return;
            }

            int x1 = this.panel.tabReorderGapX;
            int y1 = this.area.y;
            int x2 = x1 + this.panel.tabReorderGapW;
            int y2 = this.area.ey();
            int halo = Colors.setA(BBSSettings.primaryColor.get(), 0.35F);
            int border = Colors.setA(BBSSettings.primaryColor.get(), 0.65F);

            context.batcher.gradientVBox(x1, y1, x2, y2, 0, halo);
            context.batcher.outline(x1, y1, x2, y2, border);
        }

        private void renderDragGhost(UIContext context)
        {
            if (!this.panel.tabReordering || this.panel.tabReorderTabBar != this || this.panel.tabReorderPanelId == null)
            {
                return;
            }

            UITab dragged = null;

            for (IUIElement child : this.getChildren())
            {
                if (child instanceof UITab tab && this.panel.tabReorderPanelId.equals(tab.panelId))
                {
                    dragged = tab;
                    break;
                }
            }

            if (dragged == null)
            {
                return;
            }

            int w = Math.max(this.panel.tabReorderGapW, 40);
            int h = this.area.h;
            int x = context.mouseX - this.panel.dragOffsetX;
            int y = this.area.y;

            context.batcher.box(x, y, x + w, y + h, 0xCC2A2A30);
            context.batcher.outline(x, y, x + w, y + h, 0xFF15151A);

            Icon icon = dragged.resolveIcon();
            String label = dragged.resolveName().get();
            int color = 0xFF000000 | BBSSettings.primaryColor.get();

            context.batcher.icon(icon, color, x + 11, y + h / 2, 0.5F, 0.5F);
            context.batcher.text(label, x + 22, y + (h - context.batcher.getFont().getHeight()) / 2, color);
        }

        private void layoutTabs(UIContext context)
        {
            int x = this.area.x - this.scroll;
            int total = 0;
            boolean reordering = this.panel.tabReordering && this.panel.tabReorderTabBar == this && this.panel.tabReorderPanelId != null;
            int dropPreview = this.panel.tabReorderDropPreview;
            int draggedWidth = 0;
            int remainingIndex = 0;

            this.panel.tabReorderGapX = 0;
            this.panel.tabReorderGapW = 0;

            if (reordering)
            {
                for (IUIElement child : this.getChildren())
                {
                    if (child instanceof UITab tab && this.panel.tabReorderPanelId.equals(tab.panelId))
                    {
                        IKey nameKey = tab.resolveName();

                        draggedWidth = 22 + context.batcher.getFont().getWidth(nameKey.get()) + 8;
                        break;
                    }
                }
            }

            for (IUIElement child : this.getChildren())
            {
                if (child instanceof UITab)
                {
                    UITab tab = (UITab) child;

                    /* Hidden panels should not appear as tabs. Otherwise users can still click
                       them back into view while the Window menu says they're hidden, and their
                       presence affects scroll/width calculations. */
                    boolean hidden = this.panel.hiddenPanels.contains(tab.panelId);
                    boolean isDragged = reordering && this.panel.tabReorderPanelId.equals(tab.panelId);

                    tab.setVisible(!hidden && !isDragged);

                    if (hidden)
                    {
                        continue;
                    }

                    IKey nameKey = tab.resolveName();
                    int w = isDragged ? draggedWidth : (22 + context.batcher.getFont().getWidth(nameKey.get()) + 8);

                    if (isDragged)
                    {
                        tab.area.set(this.area.x - 1000, this.area.y, w, this.area.h);
                        continue;
                    }

                    if (reordering && dropPreview >= 0 && remainingIndex == dropPreview && draggedWidth > 0)
                    {
                        this.panel.tabReorderGapX = x;
                        this.panel.tabReorderGapW = draggedWidth;
                        x += draggedWidth;
                        total += draggedWidth;
                    }

                    tab.area.x = x;
                    tab.area.y = this.area.y;
                    tab.area.h = this.area.h;
                    tab.area.w = w;
                    x += w;
                    total += w;
                    remainingIndex += 1;
                }
            }

            if (reordering && dropPreview >= 0 && remainingIndex == dropPreview && draggedWidth > 0)
            {
                this.panel.tabReorderGapX = x;
                this.panel.tabReorderGapW = draggedWidth;
                total += draggedWidth;
            }

            this.totalWidth = total;
            this.clampScroll();
        }

        private void clampScroll()
        {
            this.scroll = MathUtils.clamp(this.scroll, 0, Math.max(0, this.totalWidth - this.area.w));
        }

        public boolean containsPanel(String panelId)
        {
            for (EditorLayoutNode tab : this.tabbedNode.tabs)
            {
                if (tab instanceof EditorLayoutNode.PanelNode && ((EditorLayoutNode.PanelNode) tab).getPanelId().equals(panelId))
                {
                    return true;
                }
            }

            return false;
        }

        public EditorLayoutNode.TabbedNode getTabbedNode()
        {
            return this.tabbedNode;
        }

        public void rebindTabbedNode(EditorLayoutNode.TabbedNode tabbedNode)
        {
            this.tabbedNode = tabbedNode;

            for (IUIElement child : this.getChildren())
            {
                if (child instanceof UITab)
                {
                    UITab tab = (UITab) child;

                    for (int i = 0; i < tabbedNode.tabs.size(); i++)
                    {
                        EditorLayoutNode layoutTab = tabbedNode.tabs.get(i);

                        if (layoutTab instanceof EditorLayoutNode.PanelNode && ((EditorLayoutNode.PanelNode) layoutTab).getPanelId().equals(tab.panelId))
                        {
                            tab.rebind(tabbedNode, i);

                            break;
                        }
                    }
                }
            }
        }

        public UIElement getActivePanel()
        {
            int safeActiveTab = Math.max(0, Math.min(this.tabbedNode.tabs.size() - 1, this.tabbedNode.activeTab));
            EditorLayoutNode active = this.tabbedNode.tabs.get(safeActiveTab);

            if (active instanceof EditorLayoutNode.PanelNode)
            {
                return this.panel.panelById.get(((EditorLayoutNode.PanelNode) active).getPanelId());
            }

            return null;
        }

        @Override
        protected boolean subMouseReleased(UIContext context)
        {
            if (this.panel.tabReordering && this.panel.tabReorderTabBar == this)
            {
                this.panel.finishTabReorder();

                return true;
            }

            return super.subMouseReleased(context);
        }

        @Override
        protected IUIElement childrenMouseClicked(UIContext context)
        {
            return this.area.isInside(context) ? super.childrenMouseClicked(context) : null;
        }

        @Override
        protected IUIElement childrenMouseScrolled(UIContext context)
        {
            return this.area.isInside(context) ? super.childrenMouseScrolled(context) : null;
        }

        @Override
        protected boolean subMouseScrolled(UIContext context)
        {
            if (!this.area.isInside(context) || this.totalWidth <= this.area.w)
            {
                return false;
            }

            double wheel = context.mouseWheelHorizontal != 0D ? context.mouseWheelHorizontal : context.mouseWheel;

            if (wheel == 0D)
            {
                return false;
            }

            int lastScroll = this.scroll;

            this.scroll -= (int) Math.copySign(24, wheel);
            this.clampScroll();

            if (lastScroll != this.scroll)
            {
                context.markUpdateScroll();

                return true;
            }

            return false;
        }
    }

    private static class UITab extends UIElement
    {
        private static final int DRAG_THRESHOLD = 5;

        private UIFilmPanel panel;
        private EditorLayoutNode.TabbedNode tabbedNode;
        private int index;
        private String panelId;

        public UITab(UIFilmPanel panel, EditorLayoutNode.TabbedNode tabbedNode, int index, String panelId)
        {
            this.panel = panel;
            this.tabbedNode = tabbedNode;
            this.index = index;
            this.panelId = panelId;
        }

        public String getPanelId()
        {
            return this.panelId;
        }

        public void rebind(EditorLayoutNode.TabbedNode tabbedNode, int index)
        {
            this.tabbedNode = tabbedNode;
            this.index = index;
        }

        private IKey resolveName()
        {
            IKey name = IKey.raw(this.panelId);

            if (this.panelId.equals("cameraTimeline")) name = UIKeys.FILM_CAMERA_TIMELINE;
            else if (this.panelId.equals("replayTimeline")) name = UIKeys.FILM_REPLAY_TIMELINE;
            else if (this.panelId.equals("actionTimeline")) name = UIKeys.FILM_ACTION_TIMELINE;
            else if (this.panelId.equals("cameraEditor")) name = UIKeys.FILM_OPEN_CAMERA_EDITOR;
            else if (this.panelId.equals("replayEditor")) name = UIKeys.FILM_OPEN_REPLAY_EDITOR;
            else if (this.panelId.equals("actionEditor")) name = UIKeys.FILM_OPEN_ACTION_EDITOR;
            else if (this.panelId.equals(ANCHORED_REPLAYS_PANEL_ID)) name = UIKeys.FILM_REPLAY_TITLE;
            else if (this.panelId.equals(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID)) name = UIKeys.FILM_REPLAY_SECTION_GENERAL;
            else if (this.panelId.equals("editArea")) name = UIKeys.RAW_PROPERTIES;
            else if (this.panelId.equals("cameraEditArea")) name = UIKeys.FILM_WORKSPACE_CAMERA_PROPERTIES;
            else if (this.panelId.equals("actionEditArea")) name = UIKeys.FILM_WORKSPACE_ACTION_PROPERTIES;
            else if (this.panelId.equals("unifiedEditArea")) name = UIKeys.FILM_WORKSPACE_UNIFIED_PROPERTIES;
            else if (this.panelId.equals("preview")) name = UIKeys.RAW_VIEWPORT;
            else if (this.panelId.equals("main")) name = UIKeys.RAW_TIMELINE;

            return name;
        }

        private Icon resolveIcon()
        {
            Icon icon = Icons.FILM;

            if (this.panelId.equals("cameraTimeline")) icon = Icons.FRUSTUM;
            else if (this.panelId.equals("replayTimeline")) icon = Icons.SCENE;
            else if (this.panelId.equals("actionTimeline")) icon = Icons.ACTION;
            else if (this.panelId.equals("cameraEditor")) icon = Icons.FRUSTUM;
            else if (this.panelId.equals("replayEditor")) icon = Icons.SCENE;
            else if (this.panelId.equals("actionEditor")) icon = Icons.ACTION;
            else if (this.panelId.equals(ANCHORED_REPLAYS_PANEL_ID)) icon = Icons.EDITOR;
            else if (this.panelId.equals(ANCHORED_REPLAYS_PROPERTIES_PANEL_ID)) icon = Icons.EDIT;
            else if (this.panelId.equals("editArea")) icon = Icons.EDIT;
            else if (this.panelId.equals("cameraEditArea")) icon = Icons.EDIT;
            else if (this.panelId.equals("actionEditArea")) icon = Icons.EDIT;
            else if (this.panelId.equals("unifiedEditArea")) icon = Icons.EDIT;
            else if (this.panelId.equals("preview")) icon = Icons.CAMERA;
            else if (this.panelId.equals("main")) icon = Icons.STOPWATCH;

            return icon;
        }

        private UITabBar getTabBar()
        {
            IUIElement parent = this.getParent();

            if (parent instanceof UITabBar)
            {
                return (UITabBar) parent;
            }

            return null;
        }

        @Override
        public void render(UIContext context)
        {
            if (this.panel.tabReordering && this.panel.tabReorderPanelId != null && this.panel.tabReorderPanelId.equals(this.panelId))
            {
                /* Dragged tab is hidden; reorder updates run on the tab bar. */
            }
            else if (this.panel.mouseHeldPanelId != null && this.panel.mouseHeldPanelId.equals(this.panelId) && this.panel.draggingPanelId == null)
            {
                int dx = context.mouseX - this.panel.clickX;
                int dy = context.mouseY - this.panel.clickY;

                if (dx * dx + dy * dy > DRAG_THRESHOLD * DRAG_THRESHOLD)
                {
                    UITabBar tabBar = this.getTabBar();
                    ValueEditorLayout layout = BBSSettings.editorLayoutSettings;

                    if (tabBar != null && this.panel.isInsideTabBarArea(tabBar, context.mouseX, context.mouseY) && !layout.isLayoutLocked())
                    {
                        this.panel.mouseHeldPanelId = null;
                        this.panel.dragOffsetX = context.mouseX - this.area.x;
                        this.panel.dragOffsetY = context.mouseY - this.area.y;
                        this.panel.tabReordering = true;
                        this.panel.tabReorderPanelId = this.panelId;
                        this.panel.tabReorderFromIndex = this.index;
                        this.panel.tabReorderTabbedNode = this.tabbedNode;
                        this.panel.tabReorderTabBar = tabBar;
                        this.panel.tabReorderDropPreview = this.panel.getTabDropPreviewIndex(tabBar, context.mouseX);
                    }
                    else
                    {
                        this.panel.mouseHeldPanelId = null;
                        this.panel.dragOffsetX = context.mouseX - this.area.x;
                        this.panel.dragOffsetY = context.mouseY - this.area.y;
                        this.panel.startPanelDrag(this.panelId);
                        this.panel.ensurePanelFloatingForDrag(this.panelId, context.mouseX, context.mouseY);
                        this.panel.updateDropTargetFromMouse(context.mouseX, context.mouseY);
                    }
                }
            }

            if (this.panel.draggingPanelId != null && this.panel.draggingPanelId.equals(this.panelId))
            {
                this.panel.lastDragMouseX = context.mouseX;
                this.panel.lastDragMouseY = context.mouseY;
                this.panel.updateDropTargetFromMouse(context.mouseX, context.mouseY);
            }

            if (!this.isVisible())
            {
                return;
            }

            boolean active = this.tabbedNode.activeTab == this.index;
            boolean hovered = this.getParent() != null && this.getParent().area.isInside(context) && this.area.isInside(context);
            if (active)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF1D1D1D);
                context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), 0xFF3C3C3C);
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + 2, 0xFF000000 | BBSSettings.primaryColor.get());
            }
            else
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xDD17171B);
                context.batcher.box(this.area.x, this.area.ey() - 1, this.area.ex(), this.area.ey(), 0xFF3C3C3C);
            }

            Icon icon = this.resolveIcon();
            IKey name = this.resolveName();

            int color = hovered || active ? (0xFF000000 | BBSSettings.primaryColor.get()) : 0xFFFFFFFF;
            int textY = this.area.y + (this.area.h - context.batcher.getFont().getHeight()) / 2;
            int visibleRight = this.getParent() == null ? this.area.ex() : Math.min(this.area.ex(), this.getParent().area.ex());
            int textX = this.area.x + 22;
            int textW = visibleRight - textX - 4;

            if (this.area.x + 18 <= visibleRight)
            {
                context.batcher.icon(icon, color, this.area.x + 11, this.area.y + this.area.h / 2, 0.5F, 0.5F);
            }

            if (textW > 4)
            {
                context.batcher.text(context.batcher.getFont().limitToWidth(name.get(), textW), textX, textY, color);
            }

            this.area.w = 22 + context.batcher.getFont().getWidth(name.get()) + 8;

            super.render(context);
        }

        @Override
        protected boolean subMouseClicked(UIContext context)
        {
            if (super.subMouseClicked(context)) return true;
            
            if (this.area.isInside(context) && context.mouseButton == 0)
            {
                this.tabbedNode.activeTab = this.index;
                this.panel.syncLinkedPropertiesTab(this.panelId);
                UIElement tabElement = this.panel.panelById.get(this.panelId);

                if (tabElement != null)
                {
                    int panelIdx = this.panel.panels.indexOf(tabElement);

                    if (panelIdx >= 0)
                    {
                        this.panel.currentPanelIndex = panelIdx;
                    }
                }

                ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
                layout.setFilmLayoutRoot(layout.getFilmLayoutRoot());
                /* Keep existing tab bars so horizontal scroll is not wiped before a drag starts. */
                this.panel.setupEditorFlex(true, false, false);

                if (!layout.isLayoutLocked())
                {
                    this.panel.mouseHeldPanelId = this.panelId;
                    this.panel.clickX = context.mouseX;
                    this.panel.clickY = context.mouseY;
                }

                return true;
            }
            return false;
        }
        
        @Override
        protected boolean subMouseReleased(UIContext context)
        {
            if (this.panel.tabReordering && this.panel.tabReorderPanelId != null && this.panel.tabReorderPanelId.equals(this.panelId))
            {
                this.panel.finishTabReorder();

                return true;
            }

            if (this.panel.mouseHeldPanelId != null && this.panel.mouseHeldPanelId.equals(this.panelId))
            {
                this.panel.mouseHeldPanelId = null;
            }

            if (super.subMouseReleased(context))
            {
                return true;
            }

            if (this.panel.draggingPanelId != null && this.panel.draggingPanelId.equals(this.panelId))
            {
                DropIntent intent = new DropIntent(this.panel.dropTargetPanelId, this.panel.dropTargetZone);

                if (!this.panel.canApplyDropIntent(this.panel.draggingPanelId, intent))
                {
                    this.panel.floatPanel(this.panel.draggingPanelId, this.panel.lastDragMouseX - 100, this.panel.lastDragMouseY - 10);
                    this.panel.clearPanelDragState();

                    return true;
                }

                this.panel.applyPanelDropResult(this.panel.draggingPanelId, intent.targetId, intent.zone);
                this.panel.clearPanelDragState();

                return true;
            }

            return false;
        }
    }

    /* Custom floating windows logic */

    /**
     * Outcome of {@link #handleFloatingPanelClicks}, distinguishing a fully consumed click
     * (window chrome, or content inside a floating panel) from one that must still reach the
     * dashboard-level free camera-orbit controller (left/right/middle click-drag rotate, roll
     * and FOV over the 3D viewport, matching stock BBS behaviour) versus a click that didn't
     * land on any floating panel at all.
     */
    private enum FloatingClickResult
    {
        NOT_HANDLED,
        CONSUMED,
        VIEWPORT_PASSTHROUGH
    }

    @Override
    protected IUIElement childrenMouseClicked(UIContext context)
    {
        this.clearStalePanelDragOnContentClick(context);

        FloatingClickResult result = this.handleFloatingPanelClicks(context);

        if (result == FloatingClickResult.CONSUMED)
        {
            return this;
        }

        /* Deliberately skip super.childrenMouseClicked(): falling through to the normal
           z-order sibling iteration would let the click leak onto whichever docked panel sits
           behind the floating viewport window (the exact bug that was just fixed). Returning
           null here instead lets it bubble past this whole editor, all the way up to the
           dashboard root where the camera-orbit controller lives. */
        if (result == FloatingClickResult.VIEWPORT_PASSTHROUGH)
        {
            Area viewport = this.preview.getAbsoluteViewport();

            if (viewport.isInside(context.mouseX(), context.mouseY()))
            {
                if (this.replayEditor.handleViewportInteractionMouse(context, this.preview.getViewport()))
                {
                    return this;
                }

                if (this.controller.tryPickHoveredReplay(context))
                {
                    return this;
                }

                if (this.replayEditor.clickViewport(context, this.preview.getViewport()))
                {
                    return this;
                }
            }

            return null;
        }

        IUIElement clicked = super.childrenMouseClicked(context);

        return clicked;
    }

    @Override
    protected IUIElement childrenMouseReleased(UIContext context)
    {
        if (this.activeDraggingFloatingPanelId != null)
        {
            this.finishFloatingPanelDrag();
            return this;
        }

        if (this.activeResizingFloatingPanelId != null)
        {
            this.activeResizingFloatingPanelId = null;
            this.persistFilmUILayoutSession();
            return this;
        }
        return super.childrenMouseReleased(context);
    }

    /**
     * Finalize an in-progress floating-window drag: dock it if it was released over a valid drop
     * target, otherwise leave it floating where it landed, and always clear the drag state.
     *
     * Invoked both from the mouse-released event and from the per-frame button-release poll in
     * {@link #render(UIContext)}, since the event alone isn't reliably delivered to this panel.
     */
    private void finishFloatingPanelDrag()
    {
        String droppedPanelId = this.activeDraggingFloatingPanelId;

        if (droppedPanelId == null)
        {
            return;
        }

        this.activeDraggingFloatingPanelId = null;

        DropIntent intent = new DropIntent(this.dropTargetPanelId, this.dropTargetZone);

        if (!BBSSettings.editorLayoutSettings.isLayoutLocked() && this.canApplyDropIntent(droppedPanelId, intent))
        {
            this.applyFloatingPanelDockResult(droppedPanelId, intent.targetId, intent.zone);
        }

        this.clearPanelDragState();
        this.setupEditorFlex(true);
        this.persistFilmUILayoutSession();
    }

    public void applyFloatingPanelDockResult(String panelId, String targetId, int zone)
    {
        this.floatingPanels.remove(panelId);
        this.collapsedFloatingPanels.remove(panelId);
        this.collapsedDockedPanels.remove(panelId);
        this.hiddenPanels.remove(panelId);

        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.getFilmLayoutRoot();

        /* Dropping onto a screen edge targets the whole workspace, not a single panel,
           so it must use the workspace builder (the panel builder can't resolve the
           synthetic workspace target and would silently drop the window). */
        EditorLayoutNode newRoot = DROP_TARGET_WORKSPACE.equals(targetId)
            ? this.buildWorkspaceDroppedLayout(root, panelId, zone)
            : this.buildDroppedLayout(root, panelId, targetId, zone);

        if (newRoot != null)
        {
            layout.setFilmLayoutRoot(newRoot);
        }

        this.persistFilmUILayoutSession();
    }

    public void safeBringToFront(String panelId)
    {
        this.postUpdateActions.add(() -> this.bringPanelToFront(panelId));
    }

    public void bringPanelToFront(String panelId)
    {
        UIElement el = this.panelById.get(panelId);
        if (el != null)
        {
            this.editor.getChildren().remove(el);
            this.editor.getChildren().add(el);
        }
    }

    public void floatPanel(String panelId, int x, int y)
    {
        if (!this.panelById.containsKey(panelId))
        {
            return;
        }

        ValueEditorLayout layout = BBSSettings.editorLayoutSettings;
        EditorLayoutNode root = layout.getFilmLayoutRoot();
        if (root != null && this.getDockedVisiblePanelCount() > 1)
        {
            EditorLayoutNode newRoot = EditorLayoutNode.copyWithRemovedLeaf(root, panelId);
            layout.setFilmLayoutRoot(newRoot);
        }

        this.floatingPanels.add(panelId);
        this.hiddenPanels.remove(panelId);
        this.collapsedDockedPanels.remove(panelId);

        this.ensureFloatingPanelSize(panelId);

        int editorW = this.editor.area.w;
        int editorH = this.editor.area.h;
        Vector2i size = this.floatingPanelSizes.get(panelId);
        int posX = Math.max(0, Math.min(x - this.editor.area.x, editorW - size.x));
        int posY = Math.max(0, Math.min(y - this.editor.area.y, editorH - size.y));
        this.floatingPanelPositions.put(panelId, new Vector2i(posX, posY));

        this.bringPanelToFront(panelId);
        this.setupEditorFlex(true);
        this.persistFilmUILayoutSession();
    }

    private void fitFloatingPanelSize(String panelId)
    {
        Vector2i pos = this.floatingPanelPositions.get(panelId);
        Vector2i size = this.floatingPanelSizes.get(panelId);

        if (pos == null || size == null)
        {
            return;
        }

        int minW = MIN_FLOATING_PANEL_WIDTH;
        int minH = this.collapsedFloatingPanels.contains(panelId) ? PANEL_HEADER_HEIGHT : MIN_FLOATING_PANEL_HEIGHT;
        int maxW = Math.max(minW, this.editor.area.w - pos.x);
        int maxH = Math.max(minH, this.editor.area.h - pos.y);

        size.set(Math.max(minW, Math.min(size.x, maxW)), Math.max(minH, Math.min(size.y, maxH)));
    }

    /* Keep a floating window fully inside the editor: nudge its position back from the
       right/bottom edges first, then shrink it (model-block style) if it's still bigger
       than the remaining space, so the title bar and resize handle never end up
       off-screen after a drag, an undock or a game-window resize. */
    private void reflowFloatingPanelWithinEditor(String panelId)
    {
        Vector2i pos = this.floatingPanelPositions.get(panelId);
        Vector2i size = this.floatingPanelSizes.get(panelId);

        if (pos == null || size == null || this.editor.area.w <= 0 || this.editor.area.h <= 0)
        {
            return;
        }

        pos.set(
            Math.max(0, Math.min(pos.x, Math.max(0, this.editor.area.w - size.x))),
            Math.max(0, Math.min(pos.y, Math.max(0, this.editor.area.h - size.y)))
        );

        this.fitFloatingPanelSize(panelId);
    }

    public void toggleCollapseFloatingPanel(String panelId)
    {
        if (this.collapsedFloatingPanels.contains(panelId))
        {
            this.collapsedFloatingPanels.remove(panelId);
        }
        else
        {
            this.collapsedFloatingPanels.add(panelId);
        }
        this.setupEditorFlex(true);
        this.persistFilmUILayoutSession();
    }

    private FloatingClickResult handleFloatingPanelClicks(UIContext context)
    {
        if (this.showingHomePage)
        {
            return FloatingClickResult.NOT_HANDLED;
        }

        List<IUIElement> children = this.editor.getChildren();
        for (int i = children.size() - 1; i >= 0; i--)
        {
            IUIElement child = children.get(i);
            String panelId = null;
            for (Map.Entry<String, UIElement> entry : this.panelById.entrySet())
            {
                if (entry.getValue() == child)
                {
                    panelId = entry.getKey();
                    break;
                }
            }

            if (panelId != null && this.floatingPanels.contains(panelId))
            {
                if (this.hiddenPanels.contains(panelId))
                {
                    continue;
                }

                boolean collapsed = this.collapsedFloatingPanels.contains(panelId);
                Vector2i pos = this.floatingPanelPositions.get(panelId);
                Vector2i size = this.floatingPanelSizes.get(panelId);
                if (pos != null && size != null)
                {
                    int x = this.editor.area.x + pos.x;
                    int y = this.editor.area.y + pos.y;
                    int w = size.x;
                    int h = collapsed ? PANEL_HEADER_HEIGHT : size.y;

                    // Click in Title Bar
                    if (context.mouseX >= x && context.mouseX <= x + w && context.mouseY >= y && context.mouseY <= y + PANEL_HEADER_HEIGHT)
                    {
                        this.bringPanelToFront(panelId);

                        // Click in Expand/Collapse Button (right 20 pixels)
                        if (context.mouseX >= x + w - 20 && context.mouseX <= x + w - 4 && context.mouseY >= y + 3 && context.mouseY <= y + PANEL_HEADER_HEIGHT - 3)
                        {
                            if (context.mouseButton == 0)
                            {
                                this.toggleCollapseFloatingPanel(panelId);
                            }
                        }
                        else
                        {
                            if (context.mouseButton == 0)
                            {
                                this.activeDraggingFloatingPanelId = panelId;
                                this.dragOffsetX = context.mouseX - x;
                                this.dragOffsetY = context.mouseY - y;
                                this.draggingPanelId = panelId;
                            }
                        }
                        return FloatingClickResult.CONSUMED;
                    }

                    // Click in Bottom-Right Resize Handle (only if NOT collapsed)
                    if (!collapsed)
                    {
                        int rx = x + w - FLOATING_RESIZE_HANDLE;
                        int ry = y + h - FLOATING_RESIZE_HANDLE;
                        if (context.mouseX >= rx && context.mouseX <= x + w && context.mouseY >= ry && context.mouseY <= y + h)
                        {
                            this.bringPanelToFront(panelId);

                            if (context.mouseButton == 0)
                            {
                                this.activeResizingFloatingPanelId = panelId;
                            }
                            return FloatingClickResult.CONSUMED;
                        }
                    }

                    // Click inside body of the panel
                    if (context.mouseX >= x && context.mouseX <= x + w && context.mouseY >= y && context.mouseY <= y + h)
                    {
                        this.safeBringToFront(panelId);

                        /* Route the click to this floating window's contents first, so it
                           can't fall through to docked panels behind the window (e.g.
                           selecting a clip in a floating Camera Timeline must not also press
                           a button in the Camera Properties panel below). */
                        IUIElement consumer = child.isEnabled() ? child.mouseClicked(context) : null;

                        if (consumer != null)
                        {
                            return FloatingClickResult.CONSUMED;
                        }

                        /* Nothing inside the floating window wanted this click. For the 3D
                           viewport specifically, let it bubble up to the dashboard's free
                           camera-orbit controller instead of swallowing it, so left/right/
                           middle click-drag can still rotate/roll the camera and change FOV
                           while the mouse is over the viewport, exactly like stock BBS. Any
                           other floating panel keeps swallowing the click. */
                        return "preview".equals(panelId)
                            ? FloatingClickResult.VIEWPORT_PASSTHROUGH
                            : FloatingClickResult.CONSUMED;
                    }
                }
            }
        }

        return FloatingClickResult.NOT_HANDLED;
    }

    private void renderFloatingPanelWindows(UIContext context)
    {
        if (this.showingHomePage)
        {
            return;
        }

        for (String panelId : this.floatingPanels)
        {
            if (this.hiddenPanels.contains(panelId))
            {
                continue;
            }

            UIElement el = this.panelById.get(panelId);
            if (el == null)
            {
                continue;
            }

            boolean collapsed = this.collapsedFloatingPanels.contains(panelId);
            // If expanded, check if el is visible. If collapsed, el is invisible but we still want to render its title bar!
            if (!collapsed && !el.isVisible())
            {
                continue;
            }

            Vector2i pos = this.floatingPanelPositions.get(panelId);
            Vector2i size = this.floatingPanelSizes.get(panelId);
            if (pos == null || size == null)
            {
                continue;
            }

            int x = this.editor.area.x + pos.x;
            int y = this.editor.area.y + pos.y;
            int w = size.x;
            int h = collapsed ? PANEL_HEADER_HEIGHT : size.y;

            this.renderCardHeader(context, panelId, x, y, x + w, y + PANEL_HEADER_HEIGHT, collapsed);

            // Resize handle (only if NOT collapsed)
            if (!collapsed)
            {
                int rx = x + w - FLOATING_RESIZE_HANDLE;
                int ry = y + h - FLOATING_RESIZE_HANDLE;
                boolean hoverResize = context.mouseX >= rx && context.mouseX <= rx + FLOATING_RESIZE_HANDLE && context.mouseY >= ry && context.mouseY <= ry + FLOATING_RESIZE_HANDLE;
                int resizeColor = hoverResize ? (0xFF000000 | BBSSettings.primaryColor.get()) : 0xFF888888;
                context.batcher.box(rx + 2, ry + 5, rx + 6, ry + 6, resizeColor);
                context.batcher.box(rx + 4, ry + 3, rx + 6, ry + 4, resizeColor);
                context.batcher.box(rx + 5, ry + 1, rx + 6, ry + 2, resizeColor);
            }
        }
    }

    public static class UIVideoPanel extends UIElement
    {
        private final UIFilmPanel panel;
        private final VideoClip clip;

        public UIVideoPanel(UIFilmPanel panel, VideoClip clip)
        {
            this.panel = panel;
            this.clip = clip;
            this.mouseEventPropagataion(EventPropagation.BLOCK_INSIDE);
        }

        public VideoClip getClip()
        {
            return this.clip;
        }

        @Override
        public void render(UIContext context)
        {
            if (this.area.w <= 0 || this.area.h <= 0)
            {
                return;
            }

            /* Black background for letterboxing within the floating window */
            this.area.render(context.batcher, 0xFF000000);

            if (this.panel.getData() != null && this.clip != null && this.clip.enabled.get() && this.clip.isInside(this.panel.getCursor()))
            {
                context.batcher.clip(this.area, context);

                VideoRenderer.renderClip(
                    context.batcher.getContext().getMatrices(),
                    context.batcher,
                    this.clip,
                    this.panel.getCursor(),
                    this.panel.getRunner().isRunning(),
                    this.area,
                    context
                );

                context.batcher.unclip(context);
            }

            super.render(context);
        }
    }
}
