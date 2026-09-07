package mchorse.bbs_mod.ui.utility.audio;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.audio.SoundManager;
import mchorse.bbs_mod.audio.SoundPlayer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.URLSourcePack;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.UIPanelSwitcher;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.UISidebarDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.navigation.UIControlBar;
import mchorse.bbs_mod.ui.framework.elements.navigation.UIIconTabButton;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.home.UIHomePanel;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.RecentAssetsTracker;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.resources.Pixels;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.lwjgl.opengl.GL11;

import java.io.File;
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

public class UIAudioEditorPanel extends UISidebarDashboardPanel
{
    public UIIcon pickAudio;
    public UIIcon plause;
    public UIIcon saveColors;
    public UIAudioEditor audioEditor;

    // Tab and Home fields

    private int activeAudioDocumentTab = -1;
    private final List<AudioDocumentTab> audioDocumentTabs = new ArrayList<>();

    private UIElement mainView;

    private UIElement homePage;
    private UISearchList<String> homeAudiosSearch;
    private UIStringList homeAudiosList;
    private UIAudioMosaicGrid homeAudiosMosaic;
    private UIIcon homeViewToggle;
    private UIPanelSwitcher panelSwitcher;
    private UIElement homeActionsPanel;
    private UIButton homeOpenFolder;
    private UIButton homeRefreshList;
    private UIButton homeRenameCurrent;
    private UIButton homeDeleteCurrent;
    private String homeLastClickedAudio;
    private long homeLastClickTime;
    private static final String AUDIO_PREFIX = "assets:audio/";
    private static final String PARENT_FOLDER_ENTRY = "<parent_folder>";
    private static final long DOUBLE_CLICK_INTERVAL = 250L;

    private String currentFolder = "";
    private String lastClickedFolder = "";
    private long lastFolderClickTime;

    private boolean showingHomePage = true;
    private float lastRenderTicks = -1;
    private final float[] barPeaks = new float[64];
    public static class AudioDocumentTab
    {
        public boolean home;
        public Link audioLink;

        public AudioDocumentTab(boolean home, Link audioLink)
        {
            this.home = home;
            this.audioLink = audioLink;
        }
    }



    public UIAudioEditorPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.iconBar.relative(this).x(1F, -20).y(0).w(20).h(1F).column(0).stretch();

        this.mainView = new UIElement();
        this.mainView.relative(this.editor).y(0).w(1F).h(1F);

        this.pickAudio = new UIIcon(Icons.MORE, (b) -> UIOverlay.addOverlay(this.getContext(), new UISoundOverlayPanel(this::pickAudioFromOverlay)));
        this.plause = new UIIcon(() ->
        {
            SoundPlayer player = this.audioEditor.getPlayer();

            if (player == null)
            {
                return Icons.STOP;
            }

            return player.isPlaying() ? Icons.PAUSE : Icons.PLAY;
        }, (b) -> this.audioEditor.togglePlayback());
        this.saveColors = new UIIcon(Icons.SAVED, (b) -> this.saveColors());
        this.audioEditor = new UIAudioEditor();
        this.audioEditor.full(this.mainView);

        this.mainView.add(this.audioEditor);

        this.iconBar.add(this.pickAudio, this.plause, this.saveColors);

        // Home dashboard layout
        this.homePage = new UIElement()
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                UIAudioEditorPanel.this.homeAudiosList.deselect();
                UIAudioEditorPanel.this.handleHomeAudiosSelection(null);

                return super.subMouseClicked(context);
            }
        };

        this.homeActionsPanel = new UIElement();
        this.homeAudiosList = new UIStringList((list) -> this.handleHomeAudiosSelection(list))
        {
            @Override
            protected String elementToString(UIContext context, int i, String element)
            {
                if (element.equals(PARENT_FOLDER_ENTRY))
                {
                    return "../";
                }

                if (UIAudioEditorPanel.this.isFolderEntry(element))
                {
                    String path = element.substring(0, element.length() - 1);
                    int slash = path.lastIndexOf('/');
                    String name = slash >= 0 ? path.substring(slash + 1) : path;

                    return name;
                }

                if (element.startsWith(AUDIO_PREFIX))
                {
                    return element.substring(AUDIO_PREFIX.length());
                }

                return element;
            }

            @Override
            protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
            {
                boolean isFolder = UIAudioEditorPanel.this.isFolderEntry(element);
                String displayText = this.elementToString(context, i, element);
                int textY = y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2;
                int textX = x + 4;

                if (isFolder)
                {
                    context.batcher.icon(Icons.FOLDER, textX - 2, y);
                    textX += 16;
                }

                context.batcher.textShadow(displayText, textX, textY, hover ? Colors.HIGHLIGHT : Colors.WHITE);
            }

            @Override
            public boolean subMouseReleased(UIContext context)
            {
                if (this.sorting && !this.isFiltering())
                {
                    if (this.isDragging())
                    {
                        int index = this.scroll.getIndex(context.mouseX, context.mouseY);
                        if (index == -2)
                        {
                            index = this.getList().size() - 1;
                        }

                        if (index != this.dragging && this.exists(index))
                        {
                            String dragged = this.getList().get(this.dragging);
                            String target = this.getList().get(index);

                            if (!dragged.equals(PARENT_FOLDER_ENTRY))
                            {
                                UIAudioEditorPanel.this.moveAudioFile(dragged, target);
                            }
                        }
                    }
                    this.dragging = -1;
                }

                this.scroll.mouseReleased(context);
                return super.subMouseReleased(context);
            }
        };
        this.homeAudiosList.sorting();
        this.homeAudiosSearch = new UISearchList<>(this.homeAudiosList).label(UIKeys.GENERAL_SEARCH);
        this.homeAudiosSearch.list.background();

        this.homeAudiosMosaic = new UIAudioMosaicGrid((id) -> {
            this.handleHomeAudiosSelection(Collections.singletonList(id));
        }, (id) -> {
            if (!this.isFolderEntry(id) && !id.equals(PARENT_FOLDER_ENTRY)) {
                this.openAudioInDocumentTabs(Link.create(id));
            }
        });

        boolean mosaic = BBSSettings.lastViewMosaic.get();

        this.homeAudiosMosaic.setVisible(mosaic);
        this.homeAudiosList.setVisible(!mosaic);

        Consumer<String> oldCallback = this.homeAudiosSearch.search.callback;
        this.homeAudiosSearch.search.callback = (str) -> {
            if (oldCallback != null) oldCallback.accept(str);
            this.homeAudiosMosaic.filter(str);
        };

        this.homeViewToggle = new UIIcon(mosaic ? Icons.LIST : Icons.GALLERY, (b) -> this.toggleMosaicView());
        this.homeViewToggle.tooltip(mosaic ? UIKeys.MODELS_HOME_VIEW_LIST : UIKeys.MODELS_HOME_VIEW_MOSAIC, Direction.LEFT);

        this.homeOpenFolder = this.createHomeButton(UIKeys.AUDIO_CRUD_OPEN_FOLDER, Icons.FOLDER, (b) ->
        {
            UIUtils.openFolder(new File(BBSMod.getAssetsFolder(), "audio"));
        });

        this.homeRefreshList = this.createHomeButton(UIKeys.AUDIO_CRUD_REFRESH, Icons.REFRESH, (b) ->
        {
            this.requestNames();
        });

        this.homeRenameCurrent = this.createHomeButton(UIKeys.AUDIO_CRUD_RENAME, Icons.EDIT, (b) ->
        {
            String selected = this.getSelectedHomeAudio();

            if (selected == null)
            {
                return;
            }

            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
                UIKeys.AUDIO_CRUD_RENAME,
                IKey.raw(""),
                (str) ->
                {
                    if (str == null || str.trim().isEmpty()) return;

                    String ext = selected.endsWith(".wav") ? ".wav" : ".ogg";
                    String oldFileName = selected.replace("assets:audio/", "");
                    String newFileName = str.endsWith(ext) ? str : str + ext;

                    File oldFile = new File(BBSMod.getAssetsFolder(), "audio/" + oldFileName);
                    File newFile = new File(BBSMod.getAssetsFolder(), "audio/" + newFileName);

                    if (newFile.exists())
                    {
                        this.getContext().notifyError(UIKeys.RAW_FILE_ALREADY_EXISTS);
                        return;
                    }

                    if (oldFile.renameTo(newFile))
                    {
                        Link oldLink = Link.create(selected);
                        Link newLink = Link.create("assets:audio/" + newFileName);

                        for (AudioDocumentTab tab : this.audioDocumentTabs)
                        {
                            if (!tab.home && oldLink.equals(tab.audioLink))
                            {
                                tab.audioLink = newLink;
                            }
                        }

                        if (this.audioEditor.getAudio() != null && oldLink.equals(this.audioEditor.getAudio()))
                        {
                            this.audioEditor.setup(newLink);
                        }

                        this.rebuildAudioDocumentTabs();
                        this.requestNames();
                    }
                }
            );

            String baseName = selected.replace("assets:audio/", "");
            panel.text.setText(baseName);
            panel.text.filename();
            UIOverlay.addOverlay(this.getContext(), panel);
        });

        this.homeDeleteCurrent = this.createHomeButton(UIKeys.AUDIO_CRUD_REMOVE, Icons.REMOVE, (b) ->
        {
            String selected = this.getSelectedHomeAudio();

            if (selected == null)
            {
                return;
            }

            UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
                UIKeys.AUDIO_CRUD_REMOVE,
                UIKeys.PANELS_MODALS_REMOVE,
                (bool) ->
                {
                    if (bool)
                    {
                        String fileName = selected.replace("assets:audio/", "");
                        File audioFile = new File(BBSMod.getAssetsFolder(), "audio/" + fileName);

                        if (audioFile.exists() && audioFile.delete())
                        {
                            Link targetLink = Link.create(selected);

                            if (BBSModClient.getSounds() != null)
                            {
                                BBSModClient.getSounds().stop(Link.assets("audio/" + fileName));
                            }

                            for (int i = this.audioDocumentTabs.size() - 1; i >= 0; i--)
                            {
                                AudioDocumentTab tab = this.audioDocumentTabs.get(i);
                                if (!tab.home && targetLink.equals(tab.audioLink))
                                {
                                    this.removeAudioDocumentTab(i);
                                }
                            }

                            this.requestNames();
                        }
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

        this.homeActionsPanel.add(this.homeOpenFolder, this.homeRefreshList, spacing, this.homeRenameCurrent, this.homeDeleteCurrent);
        this.homeAudiosSearch.relative(this.homePage).x(0.35F).y(UIHomePanel.HOME_BANNER_HEIGHT + 20).w(0.65F).h(1F, -(UIHomePanel.HOME_BANNER_HEIGHT + 20 + 10));
        this.homeAudiosSearch.search.w(1F, -25);
        this.homeAudiosMosaic.relative(this.homeAudiosSearch).x(0).y(20).w(1F).h(1F, -20);
        this.homeViewToggle.relative(this.homeAudiosSearch).x(1F, -22).y(0).w(20).h(20);
        this.homePage.add(new UIRenderable(this::renderHomeBackground), this.homeActionsPanel, this.homeAudiosSearch, this.homeAudiosMosaic, this.homeViewToggle, this.panelSwitcher);

        this.editor.add(this.mainView, this.homePage);

        this.createHomeDocumentTab(true);
        this.openAudio(null);
        this.updateAudioDocumentView();

        this.keys().register(Keys.PLAUSE, this.audioEditor::togglePlayback);
        this.keys().register(Keys.SAVE, this::saveColors);
        this.keys().register(Keys.OPEN_DATA_MANAGER, this.pickAudio::clickItself);
    }

    private void toggleMosaicView()
    {
        boolean isMosaic = !this.homeAudiosMosaic.isVisible();

        BBSSettings.lastViewMosaic.set(isMosaic);
        this.homeAudiosMosaic.setVisible(isMosaic);
        this.homeAudiosList.setVisible(!isMosaic);
        this.homeViewToggle.both(isMosaic ? Icons.LIST : Icons.GALLERY);
        this.homeViewToggle.tooltip(isMosaic ? UIKeys.MODELS_HOME_VIEW_LIST : UIKeys.MODELS_HOME_VIEW_MOSAIC, Direction.LEFT);
        if (isMosaic)
        {
            this.homeAudiosMosaic.resize();
        }
    }

    private void handleHomeAudiosSelection(List<String> selections)
    {
        String selected = selections == null || selections.isEmpty() ? null : selections.get(0);

        if (selected != null)
        {
            if (selected.equals(PARENT_FOLDER_ENTRY))
            {
                long now = System.currentTimeMillis();
                if (now - this.homeLastClickTime < 250)
                {
                    this.navigateToParentFolder();
                }
                this.homeLastClickTime = now;
                this.updateHomeButtonsState();
                return;
            }

            if (this.isFolderEntry(selected))
            {
                long now = System.currentTimeMillis();
                boolean isDoubleClick = selected.equals(this.lastClickedFolder) && now - this.lastFolderClickTime <= DOUBLE_CLICK_INTERVAL;

                this.lastClickedFolder = selected;
                this.lastFolderClickTime = now;
                this.homeLastClickTime = now;

                if (isDoubleClick)
                {
                    this.openFolderEntry(selected);
                }
                this.updateHomeButtonsState();
                return;
            }
        }

        this.homeLastClickedAudio = selected;
        this.updateHomeButtonsState();

        if (selected != null)
        {
            long now = System.currentTimeMillis();

            if (now - this.homeLastClickTime < 250)
            {
                this.openAudioInDocumentTabs(Link.create(selected));
            }

            this.homeLastClickTime = now;
        }
    }

    private void updateHomeButtonsState()
    {
        String selected = this.getSelectedHomeAudio();
        boolean hasSelected = selected != null;
        boolean isFolder = hasSelected && (selected.equals(PARENT_FOLDER_ENTRY) || this.isFolderEntry(selected));

        this.homeRenameCurrent.setEnabled(hasSelected && !isFolder);
        this.homeDeleteCurrent.setEnabled(hasSelected && !isFolder);
    }

    private List<String> getCurrentFolderEntries()
    {
        List<String> entries = new ArrayList<>();
        File folder = this.getCurrentAudioFolder();

        if (!folder.exists() || !folder.isDirectory())
        {
            return entries;
        }

        if (!this.currentFolder.isEmpty())
        {
            entries.add(PARENT_FOLDER_ENTRY);
        }

        File[] files = folder.listFiles();

        if (files == null)
        {
            return entries;
        }

        List<String> folders = new ArrayList<>();
        List<String> audios = new ArrayList<>();

        for (File file : files)
        {
            if (file.isDirectory())
            {
                String relative = this.getRelativeAudioPath(file);

                if (!relative.isEmpty())
                {
                    folders.add(AUDIO_PREFIX + relative + "/");
                }

                continue;
            }

            if (!file.isFile())
            {
                continue;
            }

            String name = file.getName().toLowerCase();

            if (!name.endsWith(".wav") && !name.endsWith(".ogg"))
            {
                continue;
            }

            String relative = this.getRelativeAudioPath(file);

            if (!relative.isEmpty())
            {
                audios.add(AUDIO_PREFIX + relative);
            }
        }

        folders.sort(null);
        audios.sort(null);
        entries.addAll(folders);
        entries.addAll(audios);

        return entries;
    }

    private File getAudioRootFolder()
    {
        return new File(BBSMod.getAssetsFolder(), "audio");
    }

    private File getCurrentAudioFolder()
    {
        File root = this.getAudioRootFolder();

        if (this.currentFolder.isEmpty())
        {
            return root;
        }

        return new File(root, this.currentFolder.replace("/", File.separator));
    }

    private String getRelativeAudioPath(File file)
    {
        File root = this.getAudioRootFolder();
        String rootPath = root.getAbsolutePath();
        String filePath = file.getAbsolutePath();

        if (!filePath.startsWith(rootPath))
        {
            return "";
        }

        String relative = filePath.substring(rootPath.length()).replace('\\', '/');

        if (relative.startsWith("/"))
        {
            relative = relative.substring(1);
        }

        return relative;
    }

    private boolean isFolderEntry(String entry)
    {
        return entry.startsWith(AUDIO_PREFIX) && entry.endsWith("/");
    }

    private void navigateToParentFolder()
    {
        if (this.currentFolder.isEmpty())
        {
            return;
        }

        int index = this.currentFolder.lastIndexOf('/');
        this.currentFolder = index >= 0 ? this.currentFolder.substring(0, index) : "";
        this.homeLastClickedAudio = null;
        this.lastClickedFolder = "";
        this.lastFolderClickTime = 0;
        this.requestNames();
        this.homeAudiosSearch.search.setText("");
        this.homeAudiosSearch.filter("", true);
    }

    private void openFolderEntry(String entry)
    {
        String relative = entry.substring(AUDIO_PREFIX.length(), entry.length() - 1);
        this.currentFolder = relative;
        this.homeLastClickedAudio = null;
        this.lastClickedFolder = "";
        this.lastFolderClickTime = 0;
        this.requestNames();
        this.homeAudiosSearch.search.setText("");
        this.homeAudiosSearch.filter("", true);
    }

    private File getFileFromAudioEntry(String audio)
    {
        if (audio == null || !audio.startsWith(AUDIO_PREFIX))
        {
            return null;
        }

        String relative = audio.substring(AUDIO_PREFIX.length());

        return new File(this.getAudioRootFolder(), relative.replace("/", File.separator));
    }

    private void moveAudioFile(String fromAudio, String toAudioOrFolder)
    {
        File sourceFile = this.getFileFromAudioEntry(fromAudio);

        if (sourceFile == null || !sourceFile.exists())
        {
            return;
        }

        File targetFolder;

        if (toAudioOrFolder.equals(PARENT_FOLDER_ENTRY))
        {
            File root = this.getAudioRootFolder();
            if (this.currentFolder.isEmpty())
            {
                return;
            }
            int index = this.currentFolder.lastIndexOf('/');
            String parentRelative = index >= 0 ? this.currentFolder.substring(0, index) : "";
            targetFolder = parentRelative.isEmpty() ? root : new File(root, parentRelative.replace("/", File.separator));
        }
        else if (this.isFolderEntry(toAudioOrFolder))
        {
            String folderRelative = toAudioOrFolder.substring(AUDIO_PREFIX.length(), toAudioOrFolder.length() - 1);
            targetFolder = new File(this.getAudioRootFolder(), folderRelative.replace("/", File.separator));
        }
        else
        {
            File targetFile = this.getFileFromAudioEntry(toAudioOrFolder);
            if (targetFile == null)
            {
                return;
            }
            targetFolder = targetFile.getParentFile();
        }

        if (targetFolder == null || !targetFolder.isDirectory())
        {
            return;
        }

        File destinationFile = new File(targetFolder, sourceFile.getName());

        if (sourceFile.equals(destinationFile))
        {
            return;
        }

        try
        {
            if (sourceFile.renameTo(destinationFile))
            {
                this.requestNames();

                String oldLinkStr = Link.create(fromAudio).toString();
                String newRelative = this.getRelativeAudioPath(destinationFile);
                String newLinkStr = Link.create(AUDIO_PREFIX + newRelative).toString();

                for (AudioDocumentTab tab : this.audioDocumentTabs)
                {
                    if (!tab.home && tab.audioLink != null && tab.audioLink.toString().equals(oldLinkStr))
                    {
                        tab.audioLink = Link.create(newLinkStr);
                    }
                }

                if (this.audioEditor.isEditing() && this.audioEditor.getAudio() != null && this.audioEditor.getAudio().toString().equals(oldLinkStr))
                {
                    this.audioEditor.setup(Link.create(newLinkStr));
                }

                this.rebuildAudioDocumentTabs();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private String getSelectedHomeAudio()
    {
        if (this.homeAudiosMosaic != null && this.homeAudiosMosaic.isVisible())
        {
            return this.homeAudiosMosaic.selectedId;
        }
        return this.homeAudiosList == null ? null : this.homeAudiosList.getCurrentFirst();
    }

    private void pickAudioFromOverlay(Link link)
    {
        if (link != null)
        {
            this.openAudioInDocumentTabs(link);
        }
    }

    private void createHomeDocumentTab(boolean activate)
    {
        this.audioDocumentTabs.add(new AudioDocumentTab(true, null));
        int index = this.audioDocumentTabs.size() - 1;

        this.rebuildAudioDocumentTabs();

        if (activate)
        {
            this.activateAudioDocumentTab(index, false);
        }
    }

    private void addHomeDocumentTab()
    {
        int insertAt = Math.max(0, this.activeAudioDocumentTab + 1);

        this.audioDocumentTabs.add(insertAt, new AudioDocumentTab(true, null));
        this.rebuildAudioDocumentTabs();
        this.activateAudioDocumentTab(insertAt, false);
    }

    private int findTabByAudio(Link link)
    {
        for (int i = 0; i < this.audioDocumentTabs.size(); i++)
        {
            AudioDocumentTab tab = this.audioDocumentTabs.get(i);

            if (!tab.home && link.equals(tab.audioLink))
            {
                return i;
            }
        }

        return -1;
    }

    private void openAudioInDocumentTabs(Link link)
    {
        if (link == null)
        {
            return;
        }

        RecentAssetsTracker.add(ContentType.SOUNDS, link.toString());

        int existingIndex = this.findTabByAudio(link);

        if (existingIndex >= 0)
        {
            this.activateAudioDocumentTab(existingIndex, true);
            return;
        }

        if (this.activeAudioDocumentTab < 0 || this.activeAudioDocumentTab >= this.audioDocumentTabs.size())
        {
            if (this.audioDocumentTabs.isEmpty())
            {
                this.audioDocumentTabs.add(new AudioDocumentTab(true, null));
            }
            this.activeAudioDocumentTab = 0;
        }

        AudioDocumentTab active = this.audioDocumentTabs.get(this.activeAudioDocumentTab);

        if (active.home)
        {
            active.home = false;
            active.audioLink = link;
            this.rebuildAudioDocumentTabs();
            this.activateAudioDocumentTab(this.activeAudioDocumentTab, true);
        }
        else
        {
            int insertAt = this.activeAudioDocumentTab + 1;
            this.audioDocumentTabs.add(insertAt, new AudioDocumentTab(false, link));
            this.rebuildAudioDocumentTabs();
            this.activateAudioDocumentTab(insertAt, true);
        }
    }

    private void activateAudioDocumentTab(int index, boolean loadAudio)
    {
        if (index < 0 || index >= this.audioDocumentTabs.size())
        {
            return;
        }

        this.activeAudioDocumentTab = index;

        AudioDocumentTab tab = this.audioDocumentTabs.get(index);

        if (tab.home)
        {
            this.updateAudioDocumentView();
        }
        else
        {
            if (loadAudio || this.audioEditor.getAudio() == null || !this.audioEditor.getAudio().equals(tab.audioLink))
            {
                this.openAudio(tab.audioLink);
            }
            else
            {
                this.updateAudioDocumentView();
            }
        }

        this.rebuildAudioDocumentTabs();
    }

    private void removeAudioDocumentTab(int index)
    {
        if (index < 0 || index >= this.audioDocumentTabs.size())
        {
            return;
        }

        this.audioDocumentTabs.remove(index);

        if (this.audioDocumentTabs.isEmpty())
        {
            this.audioDocumentTabs.add(new AudioDocumentTab(true, null));
            this.activeAudioDocumentTab = 0;
            this.rebuildAudioDocumentTabs();
            this.activateAudioDocumentTab(0, false);
            return;
        }

        if (index < this.activeAudioDocumentTab)
        {
            this.activeAudioDocumentTab--;
        }
        else if (index == this.activeAudioDocumentTab)
        {
            this.activeAudioDocumentTab = Math.max(0, Math.min(this.activeAudioDocumentTab, this.audioDocumentTabs.size() - 1));
        }

        this.rebuildAudioDocumentTabs();
        this.activateAudioDocumentTab(this.activeAudioDocumentTab, false);
    }

    private void rebuildAudioDocumentTabs()
    {
        /* No-op: the legacy tab bar UI was removed; the unified UIDocumentTabsBar at the dashboard level replaces it. */
    }

    private void syncActiveDocumentTabWithData(Link link)
    {
        if (link != null)
        {
            if (this.activeAudioDocumentTab < 0 || this.activeAudioDocumentTab >= this.audioDocumentTabs.size())
            {
                this.audioDocumentTabs.add(new AudioDocumentTab(false, link));
                this.activeAudioDocumentTab = this.audioDocumentTabs.size() - 1;
            }
            else
            {
                AudioDocumentTab tab = this.audioDocumentTabs.get(this.activeAudioDocumentTab);
                if (tab.home)
                {
                    tab.home = false;
                    tab.audioLink = link;
                }
                else if (!link.equals(tab.audioLink))
                {
                    int existing = this.findTabByAudio(link);
                    if (existing >= 0)
                    {
                        this.activeAudioDocumentTab = existing;
                    }
                    else
                    {
                        tab.audioLink = link;
                    }
                }
            }
        }

        this.rebuildAudioDocumentTabs();
        this.updateAudioDocumentView();
    }

    private void updateAudioDocumentView()
    {
        boolean home = this.activeAudioDocumentTab < 0
            || this.activeAudioDocumentTab >= this.audioDocumentTabs.size()
            || this.audioDocumentTabs.get(this.activeAudioDocumentTab).home
            || this.audioEditor.getAudio() == null;

        this.showingHomePage = home;
        this.homePage.setVisible(home);
        this.mainView.setVisible(!home);
        this.iconBar.setVisible(!home);

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

    public void openAudioFile(String id)
    {
        this.openAudioInDocumentTabs(Link.create(id));
    }

    @Override
    public void showHomeView()
    {
        this.openAudio(null);
    }

    @Override
    public UIDashboardPanel getMainPanel()
    {
        UIHomePanel home = this.dashboard.getPanel(UIHomePanel.class);

        return home != null ? home : this;
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

    @Override
    public void requestNames()
    {
        List<String> entries = new ArrayList<>(this.getCurrentFolderEntries());

        this.homeAudiosList.clear();
        this.homeAudiosList.add(entries);
        if (this.homeAudiosMosaic != null)
        {
            this.homeAudiosMosaic.fill(entries, this.getSelectedHomeAudio());
        }
        this.updateHomeButtonsState();
    }

    private static Set<String> getSoundEvents()
    {
        Set<String> locations = new HashSet<>();

        for (Link link : BBSMod.getProvider().getLinksFromPath(Link.assets("audio")))
        {
            String pathLower = link.path.toLowerCase();
            boolean supported = pathLower.endsWith(".wav") || pathLower.endsWith(".ogg");

            if (supported)
            {
                locations.add(link.toString());
            }
        }

        return locations;
    }

    private void openAudio(Link link)
    {
        this.audioEditor.setup(link);
        this.saveColors.setEnabled(this.audioEditor.isEditing());
        this.syncActiveDocumentTabWithData(link);

        if (link != null && this.dashboard != null && this.dashboard.documentTabsBar != null)
        {
            this.dashboard.documentTabsBar.addOrActivate(ContentType.SOUNDS, link.toString());
        }
    }

    private void saveColors()
    {
        Link audio = this.audioEditor.getAudio();
        SoundManager sounds = BBSModClient.getSounds();

        sounds.saveColorCodes(new Link(audio.source, audio.path + ".json"), this.audioEditor.getColorCodes());
        sounds.deleteSound(audio);
    }



    private static final mchorse.bbs_mod.utils.colors.Color TEMP_COLOR = new mchorse.bbs_mod.utils.colors.Color();

    private static int getInterpolatedColor(int a, int b, float x)
    {
        Colors.interpolate(TEMP_COLOR, a, b, x);
        return TEMP_COLOR.getARGBColor();
    }

    @Override
    public void render(UIContext context)
    {
        int color = BBSSettings.primaryColor.get();

        this.area.render(context.batcher, Colors.mulRGB(color | Colors.A100, 0.2F));

        super.render(context);
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
        int dividerX = this.homeAudiosSearch.area.x;

        // Render solid 0x0b0b0b dark background matching films
        context.batcher.box(editorX, editorY, editorX + editorW, editorY + editorH, Colors.setA(0x0b0b0b, 1F));

        // Update elapsed ticks for decay of peaks
        float currentTicks = context.getTickTransition();
        if (this.lastRenderTicks < 0) this.lastRenderTicks = currentTicks;
        float elapsedTicks = Math.max(0, currentTicks - this.lastRenderTicks);
        this.lastRenderTicks = currentTicks;

        // Base equalizer gradient colors on user primary color
        int primary = BBSSettings.primaryColor.get();
        int darkColor = Colors.mulRGB(primary, 0.45F);
        Colors.interpolate(TEMP_COLOR, primary, Colors.WHITE, 0.6F);
        int brightColor = TEMP_COLOR.getARGBColor();

        // Render 2D Equalizer Sound Bars
        int numBars = 32;
        float barW = (float) editorW / numBars;
        float gap = 2F;
        float maxHeight = editorH * 0.75F;
        float stepH = 6F;
        int maxBlocks = Math.round(maxHeight / stepH);

        for (int i = 0; i < numBars; i++)
        {
            float xNorm = (float) i / numBars;
            float speed = 0.04F + xNorm * 0.08F;

            float w1 = (float) Math.sin(currentTicks * speed + i * 0.4F);
            float w2 = (float) Math.sin(currentTicks * speed * 2.3F - i * 0.9F);
            float w3 = (float) Math.cos(currentTicks * speed * 0.5F + i * 1.5F);

            float val = (w1 * 0.4F + w2 * 0.3F + w3 * 0.3F + 1F) / 2F;
            float envelope = 0.3F + 0.7F * (float) Math.sin(xNorm * Math.PI);
            val *= envelope;
            val = Math.max(0.02F, Math.min(0.98F, val));

            // Peak tracking and decay
            float peak = this.barPeaks[i];
            if (val > peak)
            {
                peak = val;
            }
            else
            {
                peak -= elapsedTicks * 0.005F;
                if (peak < 0) peak = 0;
            }
            this.barPeaks[i] = peak;

            int activeBlocks = Math.round(val * maxBlocks);
            int peakBlockIndex = Math.round(peak * maxBlocks);

            int bx = Math.round(editorX + i * barW + gap / 2F);
            int bw = Math.round(barW - gap);
            if (bw < 1) bw = 1;

            for (int j = 0; j < activeBlocks; j++)
            {
                float yNorm = (float) j / maxBlocks;
                int color = getInterpolatedColor(darkColor, brightColor, yNorm);

                color = Colors.setA(color, 0.45F);

                int by = Math.round(editorY + editorH - (j + 1) * stepH + 1.5F);
                int bh = Math.round(stepH - 1.5F);

                context.batcher.box(bx, by, bx + bw, by + bh, color);
            }

            if (peakBlockIndex > 0)
            {
                float yNorm = (float) peakBlockIndex / maxBlocks;
                int peakColor = getInterpolatedColor(darkColor, brightColor, yNorm);

                peakColor = Colors.setA(peakColor, 0.65F);

                int peakY = Math.round(editorY + editorH - peakBlockIndex * stepH + 1.5F);
                int peakH = 2;

                context.batcher.box(bx, peakY, bx + bw, peakY + peakH, peakColor);
            }
        }

        // Drop shadow for the main page panel
        context.batcher.gradientHBox(pageX - 18, pageY, pageX, pageY + pageH, 0, Colors.setA(0x000000, 0.7F));
        context.batcher.gradientHBox(pageX + pageW, pageY, pageX + pageW + 18, pageY + pageH, Colors.setA(0x000000, 0.7F), 0);

        // Panel backgrounds
        context.batcher.box(pageX, pageY, pageX + pageW, pageY + pageH, Colors.setA(0x1e1e1e, 1F));

        UIHomePanel home = this.dashboard.getPanel(UIHomePanel.class);
        if (home != null)
        {
            home.renderCardAndBanners(context, this.homePage, dividerX, UIKeys.AUDIO_HOME_LIST.get());
        }
    }

    public class UIAudioMosaicGrid extends UIScrollView
    {
        private static final int CARD_SIZE = 100;
        private static final int CARD_GAP = 6;
        private static final int CARD_LABEL_H = 16;

        private final Consumer<String> selectCallback;
        private final Consumer<String> doubleClickCallback;

        private final List<String> allAudioIds = new ArrayList<>();
        private final List<String> audioIds = new ArrayList<>();
        public String selectedId;
        private String lastClickedId;
        private long lastClickTime;
        private int lastCols = -1;
        private boolean rebuilding = false;

        public UIAudioMosaicGrid(Consumer<String> selectCallback, Consumer<String> doubleClickCallback)
        {
            super();
            this.selectCallback = selectCallback;
            this.doubleClickCallback = doubleClickCallback;
            this.scroll.scrollSpeed = 20;
        }

        public void fill(Collection<String> names, String selectedId)
        {
            this.allAudioIds.clear();
            for (String name : names)
            {
                this.allAudioIds.add(name);
            }
            this.selectedId = selectedId;
            this.lastCols = -1;
            
            this.filter("");
        }

        public void filter(String query)
        {
            this.audioIds.clear();
            String lowerQuery = query == null ? "" : query.toLowerCase();
            
            for (String id : this.allAudioIds)
            {
                String name = id;
                if (id.startsWith(AUDIO_PREFIX))
                {
                    name = id.substring(AUDIO_PREFIX.length());
                }
                
                if (name.toLowerCase().contains(lowerQuery))
                {
                    this.audioIds.add(id);
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
            if (this.audioIds.isEmpty()) return;

            int effectiveW = this.area.w > 0 ? this.area.w : 500;
            int cols = Math.max(1, (effectiveW - CARD_GAP) / (CARD_SIZE + CARD_GAP));

            for (int i = 0; i < this.audioIds.size(); i++)
            {
                final String id = this.audioIds.get(i);
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
                            UIAudioMosaicGrid.this.onCardClicked(id);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public void render(UIContext context)
                    {
                        boolean selected = id.equals(UIAudioMosaicGrid.this.selectedId);
                        int border = selected ? BBSSettings.primaryColor.get() : Colors.setA(Colors.WHITE, 0.1F);
                        int bg = selected ? Colors.setA(BBSSettings.primaryColor.get(), 0.1F) : Colors.setA(0, 0.2F);
                        
                        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), bg);
                        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);

                        super.render(context);
                        
                        /* Render audio icon in center */
                        int iconX = this.area.mx();
                        int iconY = this.area.y + CARD_SIZE / 2;
                        boolean isFolder = id.endsWith("/");
                        Icon icon = isFolder || id.equals(PARENT_FOLDER_ENTRY) ? Icons.FOLDER : Icons.SOUND;
                        
                        context.batcher.getContext().getMatrices().pushMatrix();
                        context.batcher.getContext().getMatrices().translate((float) iconX, (float) iconY);
                        context.batcher.getContext().getMatrices().scale(2F, 2F);
                        context.batcher.getContext().getMatrices().translate((float) -iconX, (float) -iconY);
                        
                        context.batcher.icon(icon, iconX, iconY, 0.5F, 0.5F);
                        
                        context.batcher.getContext().getMatrices().popMatrix();

                        String label = id;
                        if (id.startsWith(AUDIO_PREFIX))
                        {
                            label = id.substring(AUDIO_PREFIX.length());
                        }
                        if (id.equals(PARENT_FOLDER_ENTRY))
                        {
                            label = "../";
                        }
                        
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

            int rows = (this.audioIds.size() + cols - 1) / cols;
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
            if (!this.audioIds.isEmpty() && !this.rebuilding)
            {
                if (cols != this.lastCols)
                {
                    this.lastCols = cols;
                    this.rebuilding = true;
                    this.buildCards();
                    this.rebuilding = false;
                }

                int rows = (this.audioIds.size() + cols - 1) / cols;
                int totalH = CARD_GAP + rows * (CARD_SIZE + CARD_LABEL_H + CARD_GAP);
                this.scroll.scrollSize = totalH;
            }
            super.resize();
        }
    }
}