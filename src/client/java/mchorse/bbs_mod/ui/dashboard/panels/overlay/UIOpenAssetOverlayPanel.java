package mchorse.bbs_mod.ui.dashboard.panels.overlay;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UICreateAssetOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.home.UIHomePanel;
import mchorse.bbs_mod.ui.model.UIModelPreviewRenderer;
import mchorse.bbs_mod.ui.utility.audio.UIAudioEditorPanel;
import mchorse.bbs_mod.ui.utils.UIDataUtils;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.RecentAssetsTracker;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.repos.IRepository;

import net.minecraft.util.Util;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class UIOpenAssetOverlayPanel extends UIOverlayPanel
{
    private static final int SIDEBAR_W = 110;
    private static final int TOOLBAR_H = 28;
    private static final int CARD_W = 90;
    private static final int CARD_THUMB_H = 60;
    private static final int CARD_LABEL_H = 20;
    private static final int CARD_H = CARD_THUMB_H + CARD_LABEL_H;
    private static final int CARD_GAP = 6;

    private final UIDashboard dashboard;

    /* Sidebar */
    private final UIElement sidebar;
    private UITypeTab activeTab;

    /* Toolbar */
    private final UIElement toolbar;
    private final UIElement breadcrumb;
    private final UITextbox searchBox;
    private final UIIcon backButton;
    private final UIIcon addButton;
    private final UIIcon dragModeToggle;
    private final UIIcon viewToggle;

    /* Content */
    private final UIElement contentArea;
    final UIAssetGrid assetGrid;
    final UIAssetList assetList;

    /* State */
    ContentType currentType;
    private List<String> allNames = new ArrayList<>();
    String currentFolder = "";
    private String searchQuery = "";
    private boolean gridMode = BBSSettings.lastViewMosaic.get();
    private boolean dragMode = false;
    private long lastClickTime = 0;
    private String lastClickedId = null;

    /* Drag state (shared by both views) */
    String dragId = null;
    boolean dragIsFolder = false;
    int dragStartX = 0;
    int dragStartY = 0;
    boolean isDragging = false;
    String dragHighlightFolder = null;

    public UIOpenAssetOverlayPanel(IKey title, UIDashboard dashboard)
    {
        super(title);
        this.title.color(Colors.WHITE);
        this.resizable();

        this.dashboard = dashboard;

        /* ---- Sidebar ---- */
        this.sidebar = new UIElement();
        this.sidebar.relative(this.content).x(0).y(0).w(SIDEBAR_W).h(1F);
        this.sidebar.column(0).vertical().stretch().padding(6);

        /* ---- Toolbar ---- */
        this.toolbar = new UIElement();
        this.toolbar.relative(this.content).x(SIDEBAR_W).y(0).w(1F, -SIDEBAR_W).h(TOOLBAR_H);

        this.backButton = new UIIcon(Icons.ARROW_LEFT, (b) -> this.navigateUp());
        this.backButton.tooltip(UIKeys.RAW_BACK, Direction.BOTTOM);
        this.backButton.relative(this.toolbar).y(4).w(20).h(20);

        this.breadcrumb = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                String path = UIOpenAssetOverlayPanel.this.currentFolder.isEmpty()
                    ? "/"
                    : "/" + UIOpenAssetOverlayPanel.this.currentFolder;
                int ty = this.area.my(context.batcher.getFont().getHeight());
                context.batcher.textShadow(path, this.area.x + 2, ty, Colors.LIGHTER_GRAY);
                super.render(context);
            }
        };
        this.breadcrumb.relative(this.toolbar).x(24).y(0).w(130).h(TOOLBAR_H);

        this.searchBox = new UITextbox(200, (str) ->
        {
            this.searchQuery = str;
            this.refreshContent();
        });
        this.searchBox.placeholder(UIKeys.GENERAL_SEARCH);
        this.searchBox.relative(this.toolbar).x(158).y(4).w(1F, -158 - 4 - 68).h(20);

        this.addButton = new UIIcon(Icons.ADD, (b) -> this.openCreateMenu());
        this.addButton.tooltip(UIKeys.GENERAL_ADD, Direction.LEFT);
        this.addButton.relative(this.toolbar).x(1F, -68).y(4).w(20).h(20);

        this.dragModeToggle = new UIIcon(Icons.MOVE_TO, (b) -> this.toggleDragMode());
        this.dragModeToggle.tooltip(UIKeys.PANELS_OPEN_DRAG_MODE, Direction.LEFT);
        this.dragModeToggle.activeBackground(Colors.setA(BBSSettings.primaryColor.get(), 0.45F));
        this.dragModeToggle.relative(this.toolbar).x(1F, -44).y(4).w(20).h(20);

        this.viewToggle = new UIIcon(this.gridMode ? Icons.LIST : Icons.GALLERY, (b) -> this.toggleView());
        this.viewToggle.tooltip(UIKeys.RAW_TOGGLE_VIEW, Direction.LEFT);
        this.viewToggle.relative(this.toolbar).x(1F, -20).y(4).w(20).h(20);

        this.toolbar.add(this.backButton, this.breadcrumb, this.searchBox, this.addButton, this.dragModeToggle, this.viewToggle);

        /* ---- Content area ---- */
        this.contentArea = new UIElement();
        this.contentArea.relative(this.content).x(SIDEBAR_W).y(TOOLBAR_H).w(1F, -SIDEBAR_W).h(1F, -TOOLBAR_H);

        /* Grid view */
        this.assetGrid = new UIAssetGrid(this);
        this.assetGrid.relative(this.contentArea).w(1F).h(1F);

        /* List view */
        this.assetList = new UIAssetList(this);
        this.assetList.relative(this.contentArea).w(1F).h(1F);
        this.assetGrid.setVisible(this.gridMode);
        this.assetList.setVisible(!this.gridMode);

        this.contentArea.add(this.assetGrid, this.assetList);
        this.content.add(this.sidebar, this.toolbar, this.contentArea);

        /* ---- Sidebar type buttons ---- */
        this.addTypeTab(Icons.FILM, UIKeys.FILM_TITLE, ContentType.FILMS);
        this.addTypeTab(Icons.PLAYER, UIKeys.MODELS_TITLE, ContentType.MODELS);
        this.addTypeTab(Icons.PARTICLE, UIKeys.PANELS_PARTICLES, ContentType.PARTICLES);
        this.addTypeTab(Icons.SOUND, UIKeys.PANELS_AUDIOS, null);

        /* ---- Open folder button (bottom of sidebar) ---- */
        UIIcon openFolderButton = new UIIcon(Icons.FOLDER, (b) -> this.openOSFolder());
        openFolderButton.relative(this.content).x(0).y(1F, -24).w(SIDEBAR_W - 1).h(20);
        openFolderButton.tooltip(UIKeys.RAW_SHOW_IN_FILE_EXPLORER, Direction.RIGHT);
        this.content.add(openFolderButton);

        /* Default selection */
        UITypeTab firstTab = this.sidebar.getChildren(UITypeTab.class).get(0);
        this.selectTab(firstTab, ContentType.FILMS);

        this.markContainer();
    }

    /* ------------------------------------------------------------------ */
    /* Setup helpers                                                         */
    /* ------------------------------------------------------------------ */

    private void addTypeTab(Icon icon, IKey label, ContentType type)
    {
        UITypeTab tab = new UITypeTab(icon, label, (t) -> this.selectTab(t, type));
        this.sidebar.add(tab);
    }

    private void selectTab(UITypeTab tab, ContentType type)
    {
        if (this.activeTab != null)
        {
            this.activeTab.selected = false;
        }

        this.activeTab = tab;
        tab.selected = true;

        this.lastClickedId = null;
        this.currentType = type;
        this.currentFolder = "";
        this.searchQuery = "";
        this.searchBox.setText("");
        this.addButton.setVisible(type != null);
        this.loadNames(type);
    }

    private void loadNames(ContentType type)
    {
        if (type != null)
        {
            UIDataUtils.requestNames(type, (names) ->
            {
                this.allNames.clear();
                this.allNames.addAll(names);
                this.refreshContent();
            });
        }
        else
        {
            /* Audio: collect from disk */
            this.allNames.clear();
            File folder = new File(BBSMod.getAssetsFolder(), "audio");

            if (folder.exists() && folder.isDirectory())
            {
                this.collectAudioFiles(folder, "", this.allNames);
            }

            this.refreshContent();
        }
    }

    private void collectAudioFiles(File dir, String relPath, List<String> result)
    {
        File[] files = dir.listFiles();

        if (files == null)
        {
            return;
        }

        for (File file : files)
        {
            if (file.isDirectory())
            {
                this.collectAudioFiles(file, relPath + file.getName() + "/", result);
            }
            else
            {
                String name = file.getName().toLowerCase();

                if (name.endsWith(".wav") || name.endsWith(".ogg"))
                {
                    result.add("assets:audio/" + relPath + file.getName());
                }
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Navigation                                                            */
    /* ------------------------------------------------------------------ */

    public void navigateInto(String folder)
    {
        this.lastClickedId = null;
        this.currentFolder = folder;
        this.searchQuery = "";
        this.searchBox.setText("");
        this.refreshContent();
    }

    private void navigateUp()
    {
        if (this.currentFolder.isEmpty())
        {
            return;
        }

        this.lastClickedId = null;
        this.currentFolder = this.getParentFolder();
        this.searchQuery = "";
        this.searchBox.setText("");
        this.refreshContent();
    }

    String getParentFolder()
    {
        if (this.currentFolder.isEmpty())
        {
            return "";
        }

        String path = this.currentFolder.endsWith("/")
            ? this.currentFolder.substring(0, this.currentFolder.length() - 1)
            : this.currentFolder;
        int slash = path.lastIndexOf('/');

        return slash < 0 ? "" : path.substring(0, slash + 1);
    }

    /* ------------------------------------------------------------------ */
    /* OS folder opening                                                     */
    /* ------------------------------------------------------------------ */

    private File getTypeFolder()
    {
        if (this.currentType != null)
        {
            return this.currentType.getRepository().getFolder();
        }

        return new File(BBSMod.getAssetsFolder(), "audio");
    }

    private void openOSFolder()
    {
        try
        {
            File folder = this.getTypeFolder();

            if (!folder.exists())
            {
                folder.mkdirs();
            }

            Util.getOperatingSystem().open(folder);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Click tracking (single vs double click)                               */
    /* ------------------------------------------------------------------ */

    void handleCardClick(String id, boolean isFolder, int mouseX, int mouseY)
    {
        if (!this.dragMode)
        {
            if (isFolder)
            {
                if (id.equals(".."))
                {
                    this.navigateUp();
                }
                else
                {
                    this.navigateInto(id);
                }
            }
            else
            {
                this.openAsset(id);
            }

            return;
        }

        long now = System.currentTimeMillis();

        if (id.equals(this.lastClickedId) && now - this.lastClickTime < 400)
        {
            this.lastClickedId = null;
            this.lastClickTime = 0;
            this.cancelDrag();

            if (isFolder)
            {
                if (id.equals(".."))
                {
                    this.navigateUp();
                }
                else
                {
                    this.navigateInto(id);
                }
            }
            else
            {
                this.openAsset(id);
            }
        }
        else
        {
            this.lastClickedId = id;
            this.lastClickTime = now;
            this.dragId = id;
            this.dragIsFolder = isFolder;
            this.dragStartX = mouseX;
            this.dragStartY = mouseY;
            this.isDragging = false;
            this.dragHighlightFolder = null;
        }
    }

    void cancelDrag()
    {
        this.dragId = null;
        this.isDragging = false;
        this.dragHighlightFolder = null;
    }

    /* ------------------------------------------------------------------ */
    /* Asset context menu actions                                            */
    /* ------------------------------------------------------------------ */

    @SuppressWarnings("unchecked")
    void renameAssetPrompt(String id)
    {
        if (this.currentType == null)
        {
            return;
        }

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_RENAME,
            UIKeys.PANELS_MODALS_RENAME,
            (name) ->
            {
                if (name.trim().isEmpty())
                {
                    return;
                }

                String newId = this.currentFolder.isEmpty() ? name : this.currentFolder + name;
                this.currentType.getRepository().rename(id, newId);
                RecentAssetsTracker.remove(this.currentType, id);
                this.loadNames(this.currentType);
                this.notifyHomePanel();
            }
        );

        panel.text.setText(baseName(id));
        panel.text.filename();
        UIOverlay.addOverlay(this.getContext(), panel);
    }

    @SuppressWarnings("unchecked")
    void duplicateAssetPrompt(String id)
    {
        if (this.currentType == null)
        {
            return;
        }

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_DUPE,
            UIKeys.PANELS_MODALS_DUPE,
            (name) ->
            {
                if (name.trim().isEmpty())
                {
                    return;
                }

                String newId = this.currentFolder.isEmpty() ? name : this.currentFolder + name;
                IRepository<ValueGroup> repo = (IRepository<ValueGroup>) this.currentType.getRepository();

                repo.load(id, (data) ->
                {
                    if (data != null)
                    {
                        repo.save(newId, data.toData().asMap());
                    }

                    this.loadNames(this.currentType);
                });
            }
        );

        panel.text.setText(baseName(id));
        panel.text.filename();
        UIOverlay.addOverlay(this.getContext(), panel);
    }

    void deleteAssetConfirm(String id)
    {
        if (this.currentType == null)
        {
            return;
        }

        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
            UIKeys.GENERAL_REMOVE,
            UIKeys.PANELS_MODALS_REMOVE,
            (confirm) ->
            {
                if (!confirm)
                {
                    return;
                }

                this.currentType.getRepository().delete(id);

                if (this.currentType == ContentType.FILMS)
                {
                    UIFilmPanel filmPanel = this.dashboard.getPanel(UIFilmPanel.class);

                    if (filmPanel != null)
                    {
                        filmPanel.deleteThumbnail(id);
                    }
                }

                RecentAssetsTracker.remove(this.currentType, id);
                this.loadNames(this.currentType);
                this.notifyHomePanel();
            }
        );

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    /* ------------------------------------------------------------------ */
    /* Folder context menu actions                                           */
    /* ------------------------------------------------------------------ */

    private void openCreateMenu()
    {
        if (this.currentType == null || this.getContext() == null)
        {
            return;
        }

        this.getContext().replaceContextMenu(this::fillCreateMenu);
    }

    void fillCreateMenu(ContextMenuManager menu)
    {
        if (this.currentType == null)
        {
            return;
        }

        menu.action(Icons.ADD, UIKeys.GENERAL_ADD, this::addAssetPrompt);
        menu.action(Icons.FOLDER, UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE, this::addFolderPrompt);
    }

    @SuppressWarnings("unchecked")
    void addAssetPrompt()
    {
        if (this.currentType == null)
        {
            return;
        }

        UICreateAssetOverlayPanel panel = new UICreateAssetOverlayPanel(
            this.currentType,
            this.currentFolder,
            (name) ->
            {
                IRepository repository = this.currentType.getRepository();
                ValueGroup created = (ValueGroup) repository.create(name);

                if (created != null)
                {
                    repository.save(name, created.toData().asMap());
                }

                this.loadNames(this.currentType);
            }
        );

        UIOverlay.addOverlay(this.getContext(), panel, 260, 160);
    }

    void addFolderPrompt()
    {
        if (this.currentType == null)
        {
            return;
        }

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.PANELS_MODALS_ADD_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_ADD_FOLDER,
            (name) ->
            {
                if (name.trim().isEmpty())
                {
                    return;
                }

                String path = this.currentFolder.isEmpty() ? name : this.currentFolder + name;
                this.currentType.getRepository().addFolder(path, (success) ->
                    this.loadNames(this.currentType));
            }
        );

        panel.text.filename();
        UIOverlay.addOverlay(this.getContext(), panel);
    }

    void renameFolderPrompt(String folder)
    {
        if (this.currentType == null)
        {
            return;
        }

        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.PANELS_MODALS_RENAME_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_RENAME_FOLDER,
            (name) ->
            {
                if (name.trim().isEmpty())
                {
                    return;
                }

                String path = folder.endsWith("/")
                    ? folder.substring(0, folder.length() - 1)
                    : folder;
                int lastSlash = path.lastIndexOf('/');
                String parentPath = lastSlash < 0 ? "" : path.substring(0, lastSlash + 1);
                String newPath = parentPath + name;

                this.currentType.getRepository().renameFolder(path, newPath, (success) ->
                    this.loadNames(this.currentType));
            }
        );

        panel.text.setText(baseName(folder));
        panel.text.filename();
        UIOverlay.addOverlay(this.getContext(), panel);
    }

    void deleteFolderConfirm(String folder)
    {
        if (this.currentType == null)
        {
            return;
        }

        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
            UIKeys.PANELS_MODALS_REMOVE_FOLDER_TITLE,
            UIKeys.PANELS_MODALS_REMOVE_FOLDER,
            (confirm) ->
            {
                if (!confirm)
                {
                    return;
                }

                String path = folder.endsWith("/")
                    ? folder.substring(0, folder.length() - 1)
                    : folder;
                this.currentType.getRepository().deleteFolder(path, (success) ->
                    this.loadNames(this.currentType));
            }
        );

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    /* ------------------------------------------------------------------ */
    /* Drag and drop                                                         */
    /* ------------------------------------------------------------------ */

    private String findFolderAt(int mouseX, int mouseY)
    {
        if (this.gridMode)
        {
            return this.assetGrid.findFolderAt(mouseX, mouseY);
        }

        return this.assetList.findFolderAt(mouseX, mouseY);
    }

    private void processDrop(int mouseX, int mouseY)
    {
        String targetFolder = this.findFolderAt(mouseX, mouseY);

        if (targetFolder == null)
        {
            return;
        }

        String normalizedTarget = targetFolder.equals("..") ? this.getParentFolder() : targetFolder;

        if (this.dragIsFolder)
        {
            if (normalizedTarget.startsWith(this.dragId) || normalizedTarget.equals(this.dragId))
            {
                return;
            }

            this.moveFolder(this.dragId, normalizedTarget);
        }
        else
        {
            this.moveAsset(this.dragId, normalizedTarget);
        }
    }

    private void notifyHomePanel()
    {
        UIHomePanel homePanel = this.dashboard.getPanel(UIHomePanel.class);

        if (homePanel != null)
        {
            homePanel.refreshRecentList();
        }
    }

    void moveFolder(String folderPath, String targetFolder)
    {
        if (this.currentType == null)
        {
            return;
        }

        String name = baseName(folderPath);
        String newPath = targetFolder.isEmpty() ? name : targetFolder + name;
        String oldPath = folderPath.endsWith("/") ? folderPath.substring(0, folderPath.length() - 1) : folderPath;

        if (!newPath.equals(oldPath))
        {
            this.currentType.getRepository().renameFolder(oldPath, newPath, (success) ->
                this.loadNames(this.currentType));
        }
    }

    void moveAsset(String id, String targetFolder)
    {
        if (this.currentType == null)
        {
            return;
        }

        String filename = baseName(id);
        String newId = targetFolder.isEmpty() ? filename : targetFolder + filename;

        if (!newId.equals(id))
        {
            this.currentType.getRepository().rename(id, newId);
            this.loadNames(this.currentType);
        }
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (context.mouseButton == 0)
        {
            boolean wasDragging = this.isDragging;

            if (this.dragMode && this.isDragging && this.dragId != null)
            {
                this.processDrop(context.mouseX, context.mouseY);
            }

            if (wasDragging)
            {
                this.lastClickedId = null;
            }

            this.dragId = null;
            this.isDragging = false;
            this.dragHighlightFolder = null;
        }

        return super.subMouseReleased(context);
    }

    /* ------------------------------------------------------------------ */
    /* Content refresh                                                       */
    /* ------------------------------------------------------------------ */

    private void refreshContent()
    {
        String query = this.searchQuery.trim().toLowerCase();
        String prefix = this.currentFolder;
        List<String> folders = new ArrayList<>();
        List<String> files = new ArrayList<>();

        if (!prefix.isEmpty() && query.isEmpty())
        {
            folders.add("..");
        }

        for (String name : this.allNames)
        {
            String stripped = stripPrefix(name, prefix);

            if (stripped == null || stripped.isEmpty())
            {
                continue;
            }

            if (!query.isEmpty())
            {
                /* In search mode: flat, files only, match anywhere in full name */
                if (name.toLowerCase().contains(query))
                {
                    files.add(name);
                }
            }
            else
            {
                int slash = stripped.indexOf('/');

                if (slash < 0)
                {
                    /* File directly in this folder */
                    files.add(name);
                }
                else
                {
                    /* Subdirectory — show folder entry once */
                    String folderName = prefix + stripped.substring(0, slash + 1);

                    if (!folders.contains(folderName))
                    {
                        folders.add(folderName);
                    }
                }
            }
        }

        /* Sort folders (skip leading ".." entry) and files alphabetically */
        int folderStart = (!folders.isEmpty() && folders.get(0).equals("..")) ? 1 : 0;

        if (folderStart < folders.size())
        {
            List<String> sortable = folders.subList(folderStart, folders.size());
            sortable.sort(String.CASE_INSENSITIVE_ORDER);
        }

        files.sort(String.CASE_INSENSITIVE_ORDER);

        this.backButton.setEnabled(!this.currentFolder.isEmpty());
        this.assetGrid.fill(folders, files, this.currentType);
        this.assetList.fill(folders, files, this.currentType);
    }

    /* Strip the given prefix from the name. Returns null if name doesn't start with prefix. */
    private static String stripPrefix(String name, String prefix)
    {
        if (prefix.isEmpty())
        {
            return name;
        }

        if (name.startsWith(prefix))
        {
            return name.substring(prefix.length());
        }

        return null;
    }

    /* ------------------------------------------------------------------ */
    /* Open / view toggle                                                    */
    /* ------------------------------------------------------------------ */

    public void openAsset(String id)
    {
        if (this.dashboard.documentTabsBar != null)
        {
            /* The sounds tab in this overlay uses currentType == null as a sentinel
               for "load from disk" (since SOUNDS has no repository), but every
               other code path stores audio tabs with ContentType.SOUNDS. Pass
               SOUNDS to the tab bar so its find() reconciles with the audio
               panel's later addOrActivate(SOUNDS, …) call — otherwise it builds
               a duplicate tab and the file appears not to open. */
            ContentType tabType = this.currentType != null ? this.currentType : ContentType.SOUNDS;

            this.dashboard.documentTabsBar.addOrActivate(tabType, id);
        }
        else if (this.currentType != null)
        {
            this.dashboard.setPanel(this.currentType.get(this.dashboard));
            this.currentType.get(this.dashboard).pickData(id);
        }
        else
        {
            UIAudioEditorPanel panel = this.dashboard.getPanel(UIAudioEditorPanel.class);

            this.dashboard.setPanel(panel);
            panel.openAudioFile(id);
        }

        this.close();
    }

    private void toggleDragMode()
    {
        this.dragMode = !this.dragMode;
        this.dragModeToggle.active(this.dragMode);
        this.cancelDrag();
    }

    private void toggleView()
    {
        this.gridMode = !this.gridMode;
        BBSSettings.lastViewMosaic.set(this.gridMode);
        this.assetGrid.setVisible(this.gridMode);
        this.assetList.setVisible(!this.gridMode);
        this.viewToggle.both(this.gridMode ? Icons.LIST : Icons.GALLERY);
        this.refreshContent();
    }

    /* ------------------------------------------------------------------ */
    /* Sidebar                                                               */
    /* ------------------------------------------------------------------ */

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
        context.batcher.box(this.sidebar.area.x, this.sidebar.area.y, this.sidebar.area.ex(), this.area.ey(), 0xFF111115);
        context.batcher.outline(this.sidebar.area.x, this.sidebar.area.y, this.sidebar.area.ex(), this.area.ey(), 0xFF22222A, 1);

        // Toolbar bottom border
        context.batcher.box(this.toolbar.area.x, this.toolbar.area.ey() - 1, this.toolbar.area.ex(), this.toolbar.area.ey(), 0xFF22222A);

        // Resize handles
        int resizeColor = Colors.GRAY;
        int right = this.area.ex();
        int bottom = this.area.ey();
        context.batcher.box(right - 9, bottom - 1, right - 1, bottom, resizeColor);
        context.batcher.box(right - 1, bottom - 9, right, bottom - 1, resizeColor);
    }

    /* ------------------------------------------------------------------ */
    /* Static helpers                                                        */
    /* ------------------------------------------------------------------ */

    static String baseName(String path)
    {
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int slash = p.lastIndexOf('/');

        return slash < 0 ? p : p.substring(slash + 1);
    }

    /* ------------------------------------------------------------------ */
    /* Inner: type tab                                                       */
    /* ------------------------------------------------------------------ */

    public static class UITypeTab extends UIClickable<UITypeTab>
    {
        private final Icon icon;
        private final IKey label;
        public boolean selected;

        public UITypeTab(Icon icon, IKey label, Consumer<UITypeTab> callback)
        {
            super(callback);

            this.icon = icon;
            this.label = label;
            this.h(20);
        }

        @Override
        protected UITypeTab get()
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
                context.batcher.box(this.area.x + 2, this.area.y, this.area.x + 5, this.area.ey(), 0xFF1976D2);
            }

            int textX = this.area.x + 8;
            int textColor = this.selected ? (0xff000000 | BBSSettings.primaryColor.get()) : 0xFFCCCCCC;

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

    /* ------------------------------------------------------------------ */
    /* Inner: asset grid                                                     */
    /* ------------------------------------------------------------------ */

    public static class UIAssetGrid extends UIScrollView
    {
        private final UIOpenAssetOverlayPanel owner;
        private List<String> folders = new ArrayList<>();
        private List<String> files = new ArrayList<>();
        private ContentType type;
        private int lastW = -1;

        public UIAssetGrid(UIOpenAssetOverlayPanel owner)
        {
            this.owner = owner;
            this.scroll.scrollSpeed = 20;

            /* Right-click on blank grid space → add folder */
            this.context((menu) -> this.owner.fillCreateMenu(menu));
        }

        public void fill(List<String> folders, List<String> files, ContentType type)
        {
            this.folders = new ArrayList<>(folders);
            this.files = new ArrayList<>(files);
            this.type = type;
            this.lastW = -1;
            this.rebuild();
        }

        private void rebuild()
        {
            this.removeAll();

            int w = this.area.w;

            if (w <= 0)
            {
                return;
            }

            int cols = Math.max(1, (w - CARD_GAP) / (CARD_W + CARD_GAP));
            int totalItems = this.folders.size() + this.files.size();
            int idx = 0;

            for (String folder : this.folders)
            {
                int col = idx % cols;
                int row = idx / cols;
                int cx = CARD_GAP + col * (CARD_W + CARD_GAP);
                int cy = CARD_GAP + row * (CARD_H + CARD_GAP);
                UIFolderCard card = new UIFolderCard(folder, this.owner);
                card.relative(this).x(cx).y(cy).w(CARD_W).h(CARD_H);
                this.add(card);
                idx++;
            }

            for (String file : this.files)
            {
                int col = idx % cols;
                int row = idx / cols;
                int cx = CARD_GAP + col * (CARD_W + CARD_GAP);
                int cy = CARD_GAP + row * (CARD_H + CARD_GAP);
                UIFileCard card = new UIFileCard(file, this.type, this.owner);
                card.relative(this).x(cx).y(cy).w(CARD_W).h(CARD_H);
                this.add(card);
                idx++;
            }

            int rows = totalItems == 0 ? 0 : (totalItems + cols - 1) / cols;
            this.scroll.scrollSize = CARD_GAP + rows * (CARD_H + CARD_GAP);
            this.scroll.clamp();

            if (this.hasParent())
            {
                super.resize();
            }
        }

        @Override
        public void resize()
        {
            super.resize();

            int w = this.area.w;

            if (w > 0 && w != this.lastW)
            {
                this.lastW = w;
                this.rebuild();
            }
        }

        /* ---- Drag helpers ---- */

        String findFolderAt(int mouseX, int mouseY)
        {
            if (mouseX < this.area.x || mouseX >= this.area.ex()
                || mouseY < this.area.y || mouseY >= this.area.ey())
            {
                return null;
            }

            int scrollAmount = (int) this.scroll.getScroll();

            for (IUIElement child : this.getChildren())
            {
                if (!(child instanceof UIFolderCard))
                {
                    continue;
                }

                UIFolderCard fc = (UIFolderCard) child;
                int vy = fc.area.y - scrollAmount;

                if (mouseX >= fc.area.x && mouseX < fc.area.ex()
                    && mouseY >= vy && mouseY < vy + CARD_H)
                {
                    return fc.folder;
                }
            }

            return null;
        }

        /* ---- Rendering ---- */

        @Override
        public void render(UIContext context)
        {
            /* Check drag threshold */
            if (this.owner.dragMode && this.owner.dragId != null && !this.owner.isDragging)
            {
                int dx = context.mouseX - this.owner.dragStartX;
                int dy = context.mouseY - this.owner.dragStartY;

                if (dx * dx + dy * dy > 36)
                {
                    this.owner.isDragging = true;
                }
            }

            /* Update highlight target */
            if (this.owner.dragMode && this.owner.isDragging)
            {
                this.owner.dragHighlightFolder = this.findFolderAt(context.mouseX, context.mouseY);
            }

            super.render(context);

            /* Draw drag ghost outside clip bounds */
            if (this.owner.dragMode && this.owner.isDragging && this.owner.dragId != null)
            {
                this.renderDragGhost(context);
            }
        }

        @Override
        protected void postRender(UIContext context)
        {
            /* Draw folder highlight inside clip */
            if (this.owner.dragMode && this.owner.isDragging && this.owner.dragHighlightFolder != null)
            {
                for (IUIElement child : this.getChildren())
                {
                    if (!(child instanceof UIFolderCard))
                    {
                        continue;
                    }

                    UIFolderCard fc = (UIFolderCard) child;

                    if (fc.folder.equals(this.owner.dragHighlightFolder))
                    {
                        context.batcher.box(fc.area.x, fc.area.y, fc.area.ex(), fc.area.ey(),
                            Colors.setA(BBSSettings.primaryColor.get(), 0.35F));
                        context.batcher.outline(fc.area.x, fc.area.y, fc.area.ex(), fc.area.ey(),
                            BBSSettings.primaryColor(Colors.A100));
                        break;
                    }
                }
            }
        }

        private void renderDragGhost(UIContext context)
        {
            int gx = context.mouseX - CARD_W / 2;
            int gy = context.mouseY - CARD_H / 2;
            int primary = BBSSettings.primaryColor.get();

            context.batcher.box(gx, gy, gx + CARD_W, gy + CARD_H, Colors.setA(primary, 0.55F));
            context.batcher.outline(gx, gy, gx + CARD_W, gy + CARD_H, primary | Colors.A100);

            String label = UIOpenAssetOverlayPanel.baseName(this.owner.dragId);
            int maxW = CARD_W - 6;

            if (context.batcher.getFont().getWidth(label) > maxW)
            {
                while (label.length() > 1 && context.batcher.getFont().getWidth(label + "..") > maxW)
                {
                    label = label.substring(0, label.length() - 1);
                }

                label += "..";
            }

            int ty = gy + CARD_THUMB_H + (CARD_LABEL_H - context.batcher.getFont().getHeight()) / 2;
            context.batcher.textShadow(label, gx + 3, ty, Colors.WHITE);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Inner: folder card                                                    */
    /* ------------------------------------------------------------------ */

    public static class UIFolderCard extends UIClickable<UIFolderCard>
    {
        final String folder;
        private final UIOpenAssetOverlayPanel owner;

        public UIFolderCard(String folder, UIOpenAssetOverlayPanel owner)
        {
            super(null);

            this.folder = folder;
            this.owner = owner;

            /* Right-click context menu (only for real folders, with repository) */
            if (!folder.equals("..") && owner.currentType != null)
            {
                this.context((menu) ->
                {
                    menu.action(Icons.EDIT, UIKeys.PANELS_MODALS_RENAME_FOLDER_TITLE,
                        () -> owner.renameFolderPrompt(folder));
                    menu.action(Icons.REMOVE, UIKeys.PANELS_MODALS_REMOVE_FOLDER_TITLE,
                        () -> owner.deleteFolderConfirm(folder));
                });
            }
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context))
            {
                this.owner.handleCardClick(this.folder, true, context.mouseX, context.mouseY);

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        protected UIFolderCard get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            int bg = this.hover
                ? Colors.setA(BBSSettings.primaryColor.get(), 0.3F)
                : Colors.setA(0, 0.35F);

            this.area.render(context.batcher, bg);

            /* Folder icon, centered in thumb area */
            context.batcher.getContext().getMatrices().push();
            context.batcher.getContext().getMatrices().translate(this.area.mx(), this.area.y + CARD_THUMB_H / 2F, 0);
            context.batcher.getContext().getMatrices().scale(2F, 2F, 1F);
            context.batcher.icon(Icons.FOLDER, Colors.WHITE, -8, -8);
            context.batcher.getContext().getMatrices().pop();

            /* Name strip */
            int stripY = this.area.y + CARD_THUMB_H;
            context.batcher.box(this.area.x, stripY, this.area.ex(), this.area.ey(), Colors.A50);

            String label = folder.equals("..") ? ".." : baseName(this.folder);
            int maxW = this.area.w - 6;

            if (context.batcher.getFont().getWidth(label) > maxW)
            {
                while (label.length() > 1 && context.batcher.getFont().getWidth(label + "..") > maxW)
                {
                    label = label.substring(0, label.length() - 1);
                }

                label += "..";
            }

            int ty = stripY + (CARD_LABEL_H - context.batcher.getFont().getHeight()) / 2;
            context.batcher.textShadow(label, this.area.x + 3, ty, Colors.LIGHTER_GRAY);

            /* Border */
            int border = this.hover
                ? BBSSettings.primaryColor(Colors.A100)
                : Colors.setA(Colors.WHITE, 0.1F);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);
        }

        private static String baseName(String fullPath)
        {
            String p = fullPath.endsWith("/") ? fullPath.substring(0, fullPath.length() - 1) : fullPath;
            int slash = p.lastIndexOf('/');

            return slash < 0 ? p : p.substring(slash + 1);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Inner: file card                                                      */
    /* ------------------------------------------------------------------ */

    public static class UIFileCard extends UIClickable<UIFileCard>
    {
        private final String id;
        private final ContentType type;
        private final UIOpenAssetOverlayPanel owner;

        public UIFileCard(String id, ContentType type, UIOpenAssetOverlayPanel owner)
        {
            super(null);

            this.id = id;
            this.type = type;
            this.owner = owner;

            if (type == ContentType.MODELS)
            {
                UIModelPreviewRenderer renderer = new UIModelPreviewRenderer();
                renderer.relative(this).x(2).y(2).w(1F, -4).h(CARD_THUMB_H - 4);
                renderer.setModel(id);
                this.add(renderer);
            }

            /* Right-click context menu (only for types with a repository) */
            if (type != null)
            {
                this.context((menu) ->
                {
                    menu.action(Icons.EDIT, UIKeys.FILM_CRUD_RENAME,
                        () -> owner.renameAssetPrompt(id));
                    menu.action(Icons.COPY, UIKeys.FILM_CRUD_DUPE,
                        () -> owner.duplicateAssetPrompt(id));
                    menu.action(Icons.REMOVE, UIKeys.FILM_CRUD_REMOVE,
                        () -> owner.deleteAssetConfirm(id));
                });
            }
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context))
            {
                this.owner.handleCardClick(this.id, false, context.mouseX, context.mouseY);

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        protected UIFileCard get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            this.area.render(context.batcher, Colors.setA(0, 0.3F));

            /* Thumbnail area */
            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + CARD_THUMB_H, Colors.setA(0, 0.2F));

            if (this.type == ContentType.FILMS)
            {
                UIFilmPanel filmPanel = this.owner.dashboard.getPanel(UIFilmPanel.class);
                Texture thumbnail = filmPanel != null ? filmPanel.getThumbnail(this.id) : null;

                if (thumbnail != null)
                {
                    int maxW = this.area.w - 4;
                    int tw = maxW;
                    int th = (int) (tw * (thumbnail.height / (float) thumbnail.width));

                    if (th > CARD_THUMB_H - 4)
                    {
                        th = CARD_THUMB_H - 4;
                        tw = (int) (th * (thumbnail.width / (float) thumbnail.height));
                    }

                    int tx = this.area.x + 2 + (maxW - tw) / 2;
                    int ty = this.area.y + 2 + (CARD_THUMB_H - 4 - th) / 2;

                    context.batcher.fullTexturedBox(thumbnail, tx, ty, tw, th);
                }
                else
                {
                    this.renderCenteredIcon(context, Icons.FILM);
                }
            }
            else if (this.type == ContentType.PARTICLES)
            {
                this.renderCenteredIcon(context, Icons.PARTICLE);
            }
            else if (this.type == null)
            {
                this.renderCenteredIcon(context, Icons.SOUND);
            }
            /* Models: renderer child handles it */

            /* Hover overlay */
            if (this.hover)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + CARD_THUMB_H, Colors.A25);
            }

            /* Label strip */
            int stripY = this.area.y + CARD_THUMB_H;
            context.batcher.box(this.area.x, stripY, this.area.ex(), this.area.ey(), Colors.A50);

            String label = UIOpenAssetOverlayPanel.baseName(this.id);
            int maxW = this.area.w - 6;

            if (context.batcher.getFont().getWidth(label) > maxW)
            {
                while (label.length() > 1 && context.batcher.getFont().getWidth(label + "..") > maxW)
                {
                    label = label.substring(0, label.length() - 1);
                }

                label += "..";
            }

            int ty = stripY + (CARD_LABEL_H - context.batcher.getFont().getHeight()) / 2;
            context.batcher.textShadow(label, this.area.x + 3, ty, Colors.WHITE);

            /* Border */
            int border = this.hover
                ? BBSSettings.primaryColor(Colors.A100)
                : Colors.setA(Colors.WHITE, 0.08F);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);
        }

        private void renderCenteredIcon(UIContext context, Icon icon)
        {
            context.batcher.icon(icon, Colors.WHITE, this.area.mx() - 8, this.area.y + CARD_THUMB_H / 2 - 8);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Inner: asset list (list view)                                         */
    /* ------------------------------------------------------------------ */

    public static class UIAssetList extends UIScrollView
    {
        private static final int ROW_H = 20;
        private static final int ROW_GAP = 1;

        private final UIOpenAssetOverlayPanel owner;
        private List<String> folders = new ArrayList<>();
        private List<String> files = new ArrayList<>();
        private ContentType type;

        public UIAssetList(UIOpenAssetOverlayPanel owner)
        {
            this.owner = owner;
            this.scroll.scrollSpeed = 20;

            this.context((menu) -> this.owner.fillCreateMenu(menu));
        }

        public void fill(List<String> folders, List<String> files, ContentType type)
        {
            this.folders = new ArrayList<>(folders);
            this.files = new ArrayList<>(files);
            this.type = type;
            this.rebuild();
        }

        private void rebuild()
        {
            this.removeAll();

            int y = 2;

            for (String folder : this.folders)
            {
                UIAssetRow row = new UIAssetRow(folder, true, this.type, this);
                row.relative(this).x(2).y(y).w(1F, -4).h(ROW_H);
                this.add(row);
                y += ROW_H + ROW_GAP;
            }

            for (String file : this.files)
            {
                UIAssetRow row = new UIAssetRow(file, false, this.type, this);
                row.relative(this).x(2).y(y).w(1F, -4).h(ROW_H);
                this.add(row);
                y += ROW_H + ROW_GAP;
            }

            this.scroll.scrollSize = y;
            this.scroll.clamp();

            if (this.hasParent())
            {
                super.resize();
            }
        }

        String findFolderAt(int mouseX, int mouseY)
        {
            if (mouseX < this.area.x || mouseX >= this.area.ex()
                || mouseY < this.area.y || mouseY >= this.area.ey())
            {
                return null;
            }

            int scrollAmount = (int) this.scroll.getScroll();

            for (IUIElement child : this.getChildren())
            {
                if (!(child instanceof UIAssetRow))
                {
                    continue;
                }

                UIAssetRow row = (UIAssetRow) child;

                if (!row.isFolder)
                {
                    continue;
                }

                int ry = row.area.y - scrollAmount;

                if (mouseX >= row.area.x && mouseX < row.area.ex()
                    && mouseY >= ry && mouseY < ry + ROW_H)
                {
                    return row.id;
                }
            }

            return null;
        }

        @Override
        public void render(UIContext context)
        {
            /* Check drag threshold */
            if (this.owner.dragMode && this.owner.dragId != null && !this.owner.isDragging)
            {
                int dx = context.mouseX - this.owner.dragStartX;
                int dy = context.mouseY - this.owner.dragStartY;

                if (dx * dx + dy * dy > 36)
                {
                    this.owner.isDragging = true;
                }
            }

            /* Update highlight target */
            if (this.owner.dragMode && this.owner.isDragging)
            {
                this.owner.dragHighlightFolder = this.findFolderAt(context.mouseX, context.mouseY);
            }

            super.render(context);

            /* Draw drag ghost outside clip */
            if (this.owner.dragMode && this.owner.isDragging && this.owner.dragId != null)
            {
                this.renderDragGhost(context);
            }
        }

        @Override
        protected void postRender(UIContext context)
        {
            /* Draw folder highlight inside clip */
            if (this.owner.dragMode && this.owner.isDragging && this.owner.dragHighlightFolder != null)
            {
                for (IUIElement child : this.getChildren())
                {
                    if (!(child instanceof UIAssetRow))
                    {
                        continue;
                    }

                    UIAssetRow row = (UIAssetRow) child;

                    if (row.isFolder && row.id.equals(this.owner.dragHighlightFolder))
                    {
                        context.batcher.box(row.area.x, row.area.y, row.area.ex(), row.area.ey(),
                            Colors.setA(BBSSettings.primaryColor.get(), 0.35F));
                        context.batcher.outline(row.area.x, row.area.y, row.area.ex(), row.area.ey(),
                            BBSSettings.primaryColor(Colors.A100));
                        break;
                    }
                }
            }
        }

        private void renderDragGhost(UIContext context)
        {
            int gx = context.mouseX + 12;
            int gy = context.mouseY - ROW_H / 2;
            int primary = BBSSettings.primaryColor.get();
            int ghostW = 160;

            context.batcher.box(gx, gy, gx + ghostW, gy + ROW_H, Colors.setA(primary, 0.55F));
            context.batcher.outline(gx, gy, gx + ghostW, gy + ROW_H, primary | Colors.A100);

            Icon icon = this.owner.dragIsFolder ? Icons.FOLDER : null;
            int textX = gx + 4;

            if (icon != null)
            {
                context.batcher.icon(icon, Colors.WHITE, gx + 4, gy + (ROW_H - icon.h) / 2);
                textX = gx + 4 + icon.w + 4;
            }

            String label = UIOpenAssetOverlayPanel.baseName(this.owner.dragId);
            int maxW = gx + ghostW - textX - 2;

            if (context.batcher.getFont().getWidth(label) > maxW)
            {
                while (label.length() > 1 && context.batcher.getFont().getWidth(label + "..") > maxW)
                {
                    label = label.substring(0, label.length() - 1);
                }

                label += "..";
            }

            int ty = gy + (ROW_H - context.batcher.getFont().getHeight()) / 2;
            context.batcher.textShadow(label, textX, ty, Colors.WHITE);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Inner: asset list row                                                 */
    /* ------------------------------------------------------------------ */

    public static class UIAssetRow extends UIClickable<UIAssetRow>
    {
        final String id;
        final boolean isFolder;
        private final ContentType type;
        private final UIAssetList list;

        public UIAssetRow(String id, boolean isFolder, ContentType type, UIAssetList list)
        {
            super(null);

            this.id = id;
            this.isFolder = isFolder;
            this.type = type;
            this.list = list;

            if (isFolder)
            {
                if (!id.equals("..") && list.owner.currentType != null)
                {
                    this.context((menu) ->
                    {
                        menu.action(Icons.EDIT, UIKeys.PANELS_MODALS_RENAME_FOLDER_TITLE,
                            () -> list.owner.renameFolderPrompt(id));
                        menu.action(Icons.REMOVE, UIKeys.PANELS_MODALS_REMOVE_FOLDER_TITLE,
                            () -> list.owner.deleteFolderConfirm(id));
                    });
                }
            }
            else if (type != null)
            {
                this.context((menu) ->
                {
                    menu.action(Icons.EDIT, UIKeys.FILM_CRUD_RENAME,
                        () -> list.owner.renameAssetPrompt(id));
                    menu.action(Icons.COPY, UIKeys.FILM_CRUD_DUPE,
                        () -> list.owner.duplicateAssetPrompt(id));
                    menu.action(Icons.REMOVE, UIKeys.FILM_CRUD_REMOVE,
                        () -> list.owner.deleteAssetConfirm(id));
                });
            }
        }

        @Override
        protected UIAssetRow get()
        {
            return this;
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context))
            {
                this.list.owner.handleCardClick(this.id, this.isFolder, context.mouseX, context.mouseY);

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            boolean isDragTarget = this.isFolder
                && this.list.owner.dragMode
                && this.list.owner.isDragging
                && this.id.equals(this.list.owner.dragHighlightFolder);

            int bg = isDragTarget
                ? Colors.setA(BBSSettings.primaryColor.get(), 0.25F)
                : (this.hover ? Colors.A25 : Colors.setA(0, 0.15F));

            context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), bg);

            Icon icon = this.getRowIcon();
            int textX = this.area.x + 4;

            if (icon != null)
            {
                int iconY = this.area.y + (this.area.h - icon.h) / 2;

                context.batcher.icon(icon, Colors.WHITE, this.area.x + 4, iconY);
                textX = this.area.x + 4 + icon.w + 4;
            }

            String label = UIOpenAssetOverlayPanel.baseName(this.id);
            int maxW = this.area.ex() - textX - 2;

            if (context.batcher.getFont().getWidth(label) > maxW)
            {
                while (label.length() > 1 && context.batcher.getFont().getWidth(label + "..") > maxW)
                {
                    label = label.substring(0, label.length() - 1);
                }

                label += "..";
            }

            int ty = this.area.my(context.batcher.getFont().getHeight());
            int textColor = this.isFolder ? Colors.LIGHTER_GRAY : Colors.WHITE;

            context.batcher.textShadow(label, textX, ty, textColor);
        }

        private Icon getRowIcon()
        {
            if (this.isFolder)
            {
                return Icons.FOLDER;
            }

            if (this.type == ContentType.FILMS)
            {
                return Icons.FILM;
            }

            if (this.type == ContentType.MODELS)
            {
                return Icons.PLAYER;
            }

            if (this.type == ContentType.PARTICLES)
            {
                return Icons.PARTICLE;
            }

            if (this.type == null || this.type == ContentType.SOUNDS)
            {
                return Icons.SOUND;
            }

            return null;
        }
    }
}
