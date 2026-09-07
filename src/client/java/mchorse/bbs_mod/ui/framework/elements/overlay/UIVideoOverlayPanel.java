package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.video.VideoRenderer;
import mchorse.bbs_mod.data.storage.DataFileStorage;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.StringType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UILikeableVideoList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.video.VideoLikeManager;

import com.mojang.logging.LogUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;

public class UIVideoOverlayPanel extends UIStringOverlayPanel
{
    private static final String INTERNAL_PREFIX = "assets:videos/";
    private static final String INTERNAL_FOLDER = "videos";
    private static final String LEGACY_PREFIX = "assets:video/";
    private static final String LEGACY_FOLDER = "video";
    private static final String EXTERNAL_PREFIX = "external:";
    private static final String ADD_EXTERNAL_ENTRY = "<add_external_video>";
    private static final String PARENT_FOLDER_ENTRY = "<parent_folder>";
    private static final long DOUBLE_CLICK_INTERVAL = 300L;

    private final UIContext context;
    private final Consumer<String> originalCallback;
    private final ExternalVideoManager externalManager;
    private final VideoLikeManager likeManager;
    private final UIIcon clientButton;
    private final UIIcon externalButton;
    private final UIIcon favoriteButton;

    private ViewMode currentMode;
    private String selectedVideo;
    private String currentClientFolder = "";
    private String lastClickedFolder = "";
    private long lastFolderClickTime;

    public UIVideoOverlayPanel(Consumer<String> callback, UIContext context)
    {
        super(UIKeys.OVERLAYS_VIDEOS_MAIN, true, new ArrayList<>(), null, UIVideoOverlayPanel::formatDisplay);

        this.context = context;
        this.originalCallback = callback;
        this.externalManager = new ExternalVideoManager();
        this.likeManager = new VideoLikeManager();

        this.content.remove(this.strings);

        UILikeableVideoList likeableList = new UILikeableVideoList((list) ->
        {
            if (!list.isEmpty())
            {
                this.pickVideo(list.get(0));
            }
        }, this.likeManager);

        likeableList.scroll.scrollSpeed *= 2;

        likeableList.setEditCallback((videoName) ->
        {
            if (this.context == null)
            {
                return;
            }

            UIPromptOverlayPanel renamePanel = new UIPromptOverlayPanel(
                UIKeys.GENERAL_RENAME,
                UIKeys.GENERAL_RENAME,
                (newName) -> this.renameVideo(videoName, newName)
            );

            renamePanel.text.setText(videoName);
            renamePanel.text.filename();

            UIOverlay.addOverlay(this.context, renamePanel);
        });

        likeableList.setRemoveCallback((videoName) ->
        {
            if (this.context == null)
            {
                return;
            }

            UIConfirmOverlayPanel confirmPanel = new UIConfirmOverlayPanel(
                UIKeys.GENERAL_REMOVE,
                UIKeys.GENERAL_REMOVE,
                (confirmed) ->
                {
                    if (confirmed)
                    {
                        this.deleteVideo(videoName);
                    }
                }
            );

            UIOverlay.addOverlay(this.context, confirmPanel);
        });

        likeableList.setRefreshCallback(this::refreshVideoList);
        likeableList.setShowEditRemoveButtons(true);

        this.strings = new UISearchList<>(likeableList);
        this.strings.label(UIKeys.GENERAL_SEARCH).full(this.content).x(6).w(1F, -12);
        this.content.add(this.strings);

        this.clientButton = new UIIcon(Icons.FOLDER, (b) -> this.switchToMode(ViewMode.CLIENT));
        this.clientButton.tooltip(UIKeys.OVERLAYS_VIDEOS_CLIENT_MODE);
        this.externalButton = new UIIcon(Icons.EXTERNAL, (b) -> this.switchToMode(ViewMode.EXTERNAL));
        this.externalButton.tooltip(UIKeys.OVERLAYS_VIDEOS_EXTERNAL_MODE);
        this.favoriteButton = new UIIcon(Icons.HEART_ALT, (b) -> this.switchToMode(ViewMode.FAVORITE));
        this.favoriteButton.tooltip(UIKeys.OVERLAYS_VIDEOS_FAVORITE_MODE);

        this.icons.add(this.clientButton, this.externalButton, this.favoriteButton);

        this.callback(this::pickVideo);
        this.switchToMode(ViewMode.CLIENT);
    }

    public UIVideoOverlayPanel set(String value)
    {
        super.set(value == null ? "" : value);
        this.selectedVideo = value;

        return this;
    }

    private static Collection<String> getInternalVideos()
    {
        Set<String> videos = new HashSet<>();
        File root = getInternalRootFolder();

        if (!root.exists() || !root.isDirectory())
        {
            return videos;
        }

        collectInternalVideosRecursive(root, root, getInternalPrefix(root), videos);

        return videos;
    }

    private static String stripInternalPrefix(String value)
    {
        if (value.startsWith(INTERNAL_PREFIX))
        {
            return value.substring(INTERNAL_PREFIX.length());
        }

        if (value.startsWith(LEGACY_PREFIX))
        {
            return value.substring(LEGACY_PREFIX.length());
        }

        return value;
    }

    private static String formatDisplay(String value)
    {
        if (value == null)
        {
            return "";
        }

        if (value.equals(ADD_EXTERNAL_ENTRY))
        {
            return "external: add external video";
        }

        if (value.startsWith(EXTERNAL_PREFIX))
        {
            String path = value.substring(EXTERNAL_PREFIX.length());
            File file = new File(path);
            String name = file.getName();

            if (name.isEmpty())
            {
                name = path;
            }

            return "external: " + name;
        }

        return value;
    }

    private void switchToMode(ViewMode mode)
    {
        if (this.currentMode == mode)
        {
            return;
        }

        this.currentMode = mode;
        if (mode == ViewMode.CLIENT)
        {
            this.currentClientFolder = "";
            this.lastClickedFolder = "";
            this.lastFolderClickTime = 0;
        }
        this.updateButtonStates();
        this.refreshVideoList();
        this.updateListSelection();
    }

    private void updateButtonStates()
    {
        this.clientButton.active(this.currentMode == ViewMode.CLIENT);
        this.externalButton.active(this.currentMode == ViewMode.EXTERNAL);
        this.favoriteButton.active(this.currentMode == ViewMode.FAVORITE);
    }

    private void refreshVideoList()
    {
        List<String> external = this.externalManager.getVideos();
        external.sort(null);

        UILikeableVideoList listWidget = (UILikeableVideoList) this.strings.list;
        List<String> list = listWidget.getList();
        String none = UIKeys.GENERAL_NONE.get();

        listWidget.setShowOnlyLiked(false);
        listWidget.setShowEditRemoveButtons(true);

        list.clear();
        list.add(none);

        if (this.currentMode == ViewMode.CLIENT)
        {
            if (this.isMediaFoldersEnhancementsEnabled())
            {
                list.addAll(this.getCurrentClientFolderEntries());
            }
            else
            {
                List<String> internal = new ArrayList<>(getInternalVideos());

                internal.sort(null);
                list.addAll(internal);
            }
        }
        else if (this.currentMode == ViewMode.EXTERNAL)
        {
            list.add(ADD_EXTERNAL_ENTRY);
            list.addAll(external);
        }
        else if (this.currentMode == ViewMode.FAVORITE)
        {
            List<String> internal = new ArrayList<>(getInternalVideos());
            internal.sort(null);
            list.addAll(internal);
            list.addAll(external);
            listWidget.setShowOnlyLiked(true);
            listWidget.setShowEditRemoveButtons(false);
        }

        listWidget.update();

        String filter = this.strings.search.getText();

        this.strings.filter(filter, true);
        this.strings.resize();
    }

    private void updateListSelection()
    {
        if (this.selectedVideo == null)
        {
            return;
        }

        if (this.strings.list instanceof UILikeableVideoList list)
        {
            int index = list.getList().indexOf(this.selectedVideo);

            if (index >= 0)
            {
                list.setIndex(index);
            }
        }
    }

    private void pickVideo(String value)
    {
        if (this.isMediaFoldersEnhancementsEnabled() && this.currentMode == ViewMode.CLIENT && this.handleClientFolderClick(value))
        {
            return;
        }

        this.selectedVideo = value;

        if (value == null || value.isEmpty())
        {
            if (this.originalCallback != null)
            {
                this.originalCallback.accept("");
            }

            return;
        }

        if (value.equals(ADD_EXTERNAL_ENTRY))
        {
            if (this.context == null)
            {
                return;
            }

            UIPromptOverlayPanel panel = new UIPromptOverlayPanel(UIKeys.OVERLAYS_VIDEOS_ADD_EXTERNAL_TITLE, UIKeys.OVERLAYS_VIDEOS_ADD_EXTERNAL_MESSAGE, (text) ->
            {
                String cleaned = text == null ? "" : text.trim();

                if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\""))
                {
                    cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
                }

                if (cleaned.isEmpty())
                {
                    return;
                }

                this.externalManager.addExternal(cleaned);
                this.refreshVideoList();

                if (this.originalCallback != null)
                {
                    this.originalCallback.accept(EXTERNAL_PREFIX + cleaned);
                }
            });

            UIOverlay.addOverlay(this.context, panel);

            return;
        }

        if (this.originalCallback != null)
        {
            this.originalCallback.accept(value);
        }
    }

    private boolean isMediaFoldersEnhancementsEnabled()
    {
        return true;
    }

    private List<String> getCurrentClientFolderEntries()
    {
        List<String> entries = new ArrayList<>();
        File root = getInternalRootFolder();

        if (!root.exists() || !root.isDirectory())
        {
            return entries;
        }

        File folder = this.getCurrentClientFolderFile(root);

        if (!folder.exists() || !folder.isDirectory())
        {
            return entries;
        }

        if (!this.currentClientFolder.isEmpty())
        {
            entries.add(PARENT_FOLDER_ENTRY);
        }

        File[] files = folder.listFiles();

        if (files == null)
        {
            return entries;
        }

        List<String> folders = new ArrayList<>();
        List<String> videos = new ArrayList<>();
        String prefix = getInternalPrefix(root);

        for (File file : files)
        {
            String relative = getRelativePath(root, file);

            if (relative.isEmpty())
            {
                continue;
            }

            if (file.isDirectory())
            {
                folders.add(prefix + relative + "/");
                continue;
            }

            if (file.isFile() && isSupportedVideoFile(file.getName()))
            {
                videos.add(prefix + relative);
            }
        }

        folders.sort(null);
        videos.sort(null);
        entries.addAll(folders);
        entries.addAll(videos);

        return entries;
    }

    private File getCurrentClientFolderFile(File root)
    {
        if (this.currentClientFolder.isEmpty())
        {
            return root;
        }

        return new File(root, this.currentClientFolder.replace("/", File.separator));
    }

    private boolean handleClientFolderClick(String value)
    {
        if (value == null)
        {
            return false;
        }

        if (value.equals(PARENT_FOLDER_ENTRY))
        {
            this.navigateToClientParentFolder();

            return true;
        }

        if (!isClientFolderEntry(value))
        {
            return false;
        }

        this.selectedVideo = value;
        this.updateListSelection();

        long now = System.currentTimeMillis();
        boolean isDoubleClick = value.equals(this.lastClickedFolder) && now - this.lastFolderClickTime <= DOUBLE_CLICK_INTERVAL;

        this.lastClickedFolder = value;
        this.lastFolderClickTime = now;

        if (isDoubleClick)
        {
            this.openClientFolderEntry(value);
        }

        return true;
    }

    private void navigateToClientParentFolder()
    {
        if (this.currentClientFolder.isEmpty())
        {
            return;
        }

        int index = this.currentClientFolder.lastIndexOf('/');
        this.currentClientFolder = index >= 0 ? this.currentClientFolder.substring(0, index) : "";
        this.selectedVideo = null;
        this.lastClickedFolder = "";
        this.lastFolderClickTime = 0;
        this.refreshVideoList();
    }

    private void openClientFolderEntry(String value)
    {
        String prefix = value.startsWith(INTERNAL_PREFIX) ? INTERNAL_PREFIX : LEGACY_PREFIX;
        this.currentClientFolder = value.substring(prefix.length(), value.length() - 1);
        this.selectedVideo = null;
        this.lastClickedFolder = "";
        this.lastFolderClickTime = 0;
        this.refreshVideoList();
    }

    private static boolean isClientFolderEntry(String value)
    {
        return (value.startsWith(INTERNAL_PREFIX) || value.startsWith(LEGACY_PREFIX)) && value.endsWith("/");
    }

    private static File getInternalRootFolder()
    {
        File internalFolder = new File(BBSMod.getAssetsFolder(), INTERNAL_FOLDER);
        File legacyFolder = new File(BBSMod.getAssetsFolder(), LEGACY_FOLDER);

        if (internalFolder.exists())
        {
            return internalFolder;
        }

        if (legacyFolder.exists())
        {
            return legacyFolder;
        }

        return internalFolder;
    }

    private static String getInternalPrefix(File root)
    {
        return root.getName().equals(LEGACY_FOLDER) ? LEGACY_PREFIX : INTERNAL_PREFIX;
    }

    private static void collectInternalVideosRecursive(File root, File folder, String prefix, Set<String> videos)
    {
        File[] files = folder.listFiles();

        if (files == null)
        {
            return;
        }

        for (File file : files)
        {
            if (file.isDirectory())
            {
                collectInternalVideosRecursive(root, file, prefix, videos);
                continue;
            }

            if (!file.isFile() || !isSupportedVideoFile(file.getName()))
            {
                continue;
            }

            String relative = getRelativePath(root, file);

            if (!relative.isEmpty())
            {
                videos.add(prefix + relative);
            }
        }
    }

    private static String getRelativePath(File root, File file)
    {
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

    private static boolean isSupportedVideoFile(String name)
    {
        String lower = name.toLowerCase();

        return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".mkv") || lower.endsWith(".avi") || lower.endsWith(".webm");
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        if (this.clientButton.isActive())
        {
            this.clientButton.area.render(context.batcher, BBSSettings.primaryColor(Colors.A100));
        }

        if (this.externalButton.isActive())
        {
            this.externalButton.area.render(context.batcher, BBSSettings.primaryColor(Colors.A100));
        }

        if (this.favoriteButton.isActive())
        {
            this.favoriteButton.area.render(context.batcher, BBSSettings.primaryColor(Colors.A100));
        }
    }

    private void renameVideo(String oldName, String newName)
    {
        if (oldName == null || newName == null || oldName.equals(newName))
        {
            return;
        }

        if (oldName.equals(ADD_EXTERNAL_ENTRY) || oldName.equals(UIKeys.GENERAL_NONE.get()))
        {
            return;
        }

        if (oldName.startsWith(EXTERNAL_PREFIX))
        {
            String trimmed = newName.trim();

            if (trimmed.isEmpty())
            {
                return;
            }

            if (trimmed.startsWith(EXTERNAL_PREFIX))
            {
                trimmed = trimmed.substring(EXTERNAL_PREFIX.length());
            }

            String newValue = EXTERNAL_PREFIX + trimmed;

            this.externalManager.renameExternal(oldName, newValue);

            if (this.likeManager.isVideoLiked(oldName))
            {
                this.likeManager.removeVideo(oldName);
                this.likeManager.setVideoLiked(newValue, trimmed, true);
            }

            this.refreshVideoList();

            return;
        }

        String oldFileName = stripInternalPrefix(oldName);
        String newFileName = stripInternalPrefix(newName);

        int index = oldFileName.lastIndexOf('.');
        String extension = index >= 0 ? oldFileName.substring(index) : "";

        int newIndex = newFileName.lastIndexOf('.');

        if (newIndex >= 0)
        {
            newFileName = newFileName.substring(0, newIndex);
        }

        newFileName = newFileName + extension;

        File oldFile = new File(BBSMod.getAssetsFolder(), INTERNAL_FOLDER + "/" + oldFileName);

        if (!oldFile.exists())
        {
            oldFile = new File(BBSMod.getAssetsFolder(), LEGACY_FOLDER + "/" + oldFileName);
        }

        File newFile = new File(BBSMod.getAssetsFolder(), INTERNAL_FOLDER + "/" + newFileName);

        if (newFile.exists())
        {
            return;
        }

        if (!oldFile.exists())
        {
            return;
        }

        VideoRenderer.releaseVideo(oldName);

        if (oldFile.renameTo(newFile))
        {
            String newPath = INTERNAL_PREFIX + newFileName;

            if (this.likeManager.isVideoLiked(oldName))
            {
                this.likeManager.removeVideo(oldName);
                this.likeManager.setVideoLiked(newPath, newFileName, true);
            }

            this.refreshVideoList();
        }
    }

    private void deleteVideo(String videoName)
    {
        if (videoName == null || videoName.equals(ADD_EXTERNAL_ENTRY) || videoName.equals(UIKeys.GENERAL_NONE.get()))
        {
            return;
        }

        VideoRenderer.releaseVideo(videoName);

        if (videoName.startsWith(EXTERNAL_PREFIX))
        {
            this.externalManager.removeExternal(videoName);
            this.likeManager.removeVideo(videoName);
            this.refreshVideoList();

            return;
        }

        String fileName = stripInternalPrefix(videoName);
        File videoFile = new File(BBSMod.getAssetsFolder(), INTERNAL_FOLDER + "/" + fileName);

        if (!videoFile.exists())
        {
            videoFile = new File(BBSMod.getAssetsFolder(), LEGACY_FOLDER + "/" + fileName);
        }

        if (videoFile.exists() && videoFile.delete())
        {
            this.likeManager.removeVideo(videoName);
            this.refreshVideoList();
        }
    }

    private enum ViewMode
    {
        CLIENT,
        EXTERNAL,
        FAVORITE
    }

    private static class ExternalVideoManager
    {
        private static final Logger LOGGER = LogUtils.getLogger();
        private static final String FILE_NAME = "external_videos.dat";

        private final List<String> videos = new ArrayList<>();
        private final DataFileStorage storage;
        private boolean loadErrorLogged;
        private boolean saveErrorLogged;

        public ExternalVideoManager()
        {
            File dataDir = new File(BBSMod.getSettingsFolder().getParentFile(), "data");
            File videoDir = new File(dataDir, "video");

            if (!videoDir.exists())
            {
                videoDir.mkdirs();
            }

            File configFile = new File(videoDir, FILE_NAME);
            this.storage = new DataFileStorage(configFile);

            this.load();
        }

        private void load()
        {
            if (!this.storage.getFile().exists())
            {
                return;
            }

            try
            {
                BaseType data = this.storage.read();

                if (data != null && data.isList())
                {
                    this.videos.clear();

                    ListType list = data.asList();

                    for (BaseType item : list)
                    {
                        if (item.isString())
                        {
                            String value = item.asString();

                            if (value != null && !value.isEmpty())
                            {
                                this.videos.add(value);
                            }
                        }
                    }
                }
            }
            catch (Exception e)
            {
                if (!this.loadErrorLogged)
                {
                    LOGGER.error("Failed to load external videos from " + this.storage.getFile().getAbsolutePath(), e);
                    this.loadErrorLogged = true;
                }
            }
        }

        private void save()
        {
            try
            {
                ListType listData = new ListType();

                for (String video : this.videos)
                {
                    listData.add(new StringType(video));
                }

                this.storage.write(listData);
            }
            catch (Exception e)
            {
                if (!this.saveErrorLogged)
                {
                    LOGGER.error("Failed to save external videos to " + this.storage.getFile().getAbsolutePath(), e);
                    this.saveErrorLogged = true;
                }
            }
        }

        public void addExternal(String path)
        {
            String value = EXTERNAL_PREFIX + path;

            if (!this.videos.contains(value))
            {
                this.videos.add(value);
                this.save();
            }
        }

        public void renameExternal(String oldValue, String newValue)
        {
            int index = this.videos.indexOf(oldValue);

            if (index >= 0)
            {
                this.videos.set(index, newValue);
                this.save();
            }
        }

        public void removeExternal(String value)
        {
            if (this.videos.remove(value))
            {
                this.save();
            }
        }

        public List<String> getVideos()
        {
            return new ArrayList<>(this.videos);
        }
    }
}
