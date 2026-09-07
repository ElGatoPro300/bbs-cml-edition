package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.actions.types.AttackActionClip;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.MobCemPoseCapture;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.AnchorForm;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.math.IExpression;
import mchorse.bbs_mod.math.MathBuilder;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.IValueListener;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueForm;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.controller.FilmEditorController;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.film.replays.overlays.UIReplaysOverlayPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIFolderPickerOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UINumberOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.ContextSeparatorAction;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import mchorse.bbs_mod.utils.pose.Transform;
import mchorse.bbs_mod.utils.resources.Pixels;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import org.joml.Vector3d;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This GUI is responsible for drawing replays available in the
 * director thing
 */
public class UIReplayList extends UIList<Replay> {
    public static final List<BiConsumer<UIReplayList, ContextMenuManager>> extensions = new ArrayList<>();

    static final Vector3f LIGHT_A = new Vector3f(0.85F, 0.85F, -1F).normalize();
    static final Vector3f LIGHT_B = new Vector3f(-0.85F, 0.85F, 1F).normalize();

    private static String LAST_PROCESS = "v";
    private static String LAST_PICK_FAVORITE_CATEGORY_ID = null;
    private static String LAST_OFFSET = "0";
    private static List<String> LAST_PROCESS_PROPERTIES = Arrays.asList("x");
    private static int LAST_PROCESS_SECTION = 0;
    private static int LAST_PROCESS_GRID_COLUMNS = 4;
    private static double LAST_PROCESS_GRID_SPACING_X = 2D;
    private static double LAST_PROCESS_GRID_SPACING_Z = 2D;
    private static double LAST_PROCESS_CIRCLE_RADIUS = 3D;
    private static int LAST_PROCESS_CIRCLE_COUNT = 8;
    private static double LAST_PROCESS_CIRCLE_START_ANGLE = 0D;
    private static double LAST_PROCESS_LINE_DIRECTION = 0D;
    private static double LAST_PROCESS_LINE_SPACING = 2D;
    private static double LAST_PROCESS_SCATTER_AREA_X = 10D;
    private static double LAST_PROCESS_SCATTER_AREA_Z = 10D;
    private static double LAST_PROCESS_SCATTER_SEED = 0D;
    private static double LAST_PROCESS_SCATTER_MIN_SEPARATION = 1D;
    private static boolean LAST_PROCESS_SNAP_TERRAIN = false;
    private static int LAST_OFFSET_SECTION = 0;
    private static double LAST_OFFSET_STEP = 1D;
    private static double LAST_OFFSET_RANDOM_SEED = 0D;
    private static double LAST_OFFSET_RANDOM_MIN = -1D;
    private static double LAST_OFFSET_RANDOM_MAX = 1D;
    private static String LAST_RANDOM_SKINS_STEVE_MODEL = "";
    private static String LAST_RANDOM_SKINS_ALEX_MODEL = "";
    private static final String GROUP_CLIPBOARD_KEY = "_CopyReplayGroup";

    public UIFilmPanel panel;
    public UIReplaysOverlayPanel overlay;

    private Map<String, Boolean> expandedGroups = new HashMap<>();
    private List<Replay> visualList = new ArrayList<>();

    public UIReplayList(Consumer<List<Replay>> callback, UIReplaysOverlayPanel overlay, UIFilmPanel panel) {
        super(callback);

        this.overlay = overlay;
        this.panel = panel;

        this.multi().sorting();
        this.context(this::fillContextMenu);
    }

    /**
     * Minecut Media cards call {@link #fillContextMenu} while this list may be hidden
     * and not attached to the UI root, so {@link #getContext()} is null. Fall back to
     * the film panel which stays in the tree.
     */
    private UIContext resolveUiContext()
    {
        UIContext context = this.getContext();

        if (context != null)
        {
            return context;
        }

        return this.panel == null ? null : this.panel.getContext();
    }

    /**
     * Minecut Media cards: drag-reorder while this classic list may be hidden.
     * Uses the same group-aware swap path as the default list UI.
     */
    public void reorderReplay(int from, int to)
    {
        if (from != to && this.exists(from) && this.exists(to))
        {
            this.handleSwap(from, to);
        }
    }

    /**
     * Shared context menu for the classic list and Minecut Media cards / Add button.
     */
    public void fillContextMenu(ContextMenuManager menu) {
        menu.action(Icons.REFRESH, UIKeys.SCENE_REPLAYS_CONTEXT_RELOAD, this::reloadReplays);
        this.addContextSeparator(menu);

        boolean selectedGroup = this.isSelected() && this.getCurrentFirst().isGroup.get();

        if (!selectedGroup) {
            menu.action(Icons.ADD, UIKeys.SCENE_REPLAYS_CONTEXT_ADD, this::addReplay);
        }

        if (this.isSelected()) {
            boolean isGroup = this.getCurrentFirst().isGroup.get();

            if (isGroup) {
                int duration = this.panel.getData().camera.calculateDuration();
                menu.action(Icons.ADD, UIKeys.SCENE_REPLAYS_CONTEXT_ADD, this::addReplay);

                if (duration > 0) {
                    menu.action(Icons.PLAY, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_CAMERA, () -> this.fromCamera(duration));
                }

                menu.action(Icons.BLOCK, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK, this::fromModelBlock);
                menu.action(Icons.COPY, UIKeys.SCENE_REPLAYS_CONTEXT_COPY_GROUP, this::copyGroup);

                MapType copyGroup = Window.getClipboardMap(GROUP_CLIPBOARD_KEY);

                if (copyGroup != null) {
                    menu.action(Icons.PASTE, UIKeys.SCENE_REPLAYS_CONTEXT_PASTE_GROUP, () -> this.pasteGroup(copyGroup));
                }

                menu.action(Icons.DUPE, UIKeys.SCENE_REPLAYS_CONTEXT_DUPE_GROUP, this::duplicateGroup);
                menu.action(Icons.REMOVE, UIKeys.SCENE_REPLAYS_CONTEXT_DELETE_GROUP, this::deleteGroup);
                menu.action(Icons.FOLDER, UIKeys.SCENE_REPLAYS_CONTEXT_UNGROUP, this::ungroupReplay);
            } else {
                int duration = this.panel.getData().camera.calculateDuration();
                MapType copyReplay = Window.getClipboardMap("_CopyReplay");
                boolean shift = Window.isShiftPressed();
                int compactedOptions = BBSSettings.replayContextOptions == null ? 0 : BBSSettings.replayContextOptions.get();
                boolean separatedMode = compactedOptions == 1;
                boolean compactedMode = compactedOptions == 2;

                if (duration > 0) {
                    menu.action(Icons.PLAY, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_CAMERA, () -> this.fromCamera(duration));
                }

                menu.action(Icons.BLOCK, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK, this::fromModelBlock);
                menu.action(Icons.COPY, UIKeys.SCENE_REPLAYS_CONTEXT_COPY, this::copyReplay);

                if (copyReplay != null) {
                    menu.action(Icons.PASTE, UIKeys.SCENE_REPLAYS_CONTEXT_PASTE, () -> this.pasteReplay(copyReplay));
                }

                menu.action(Icons.DUPE, UIKeys.SCENE_REPLAYS_CONTEXT_DUPE, () -> {
                    if (Window.isShiftPressed() || shift) {
                        this.dupeReplay();
                    } else {
                        UIContext context = this.resolveUiContext();

                        if (context == null) {
                            this.dupeReplay();

                            return;
                        }

                        UINumberOverlayPanel numberPanel = new UINumberOverlayPanel(
                                UIKeys.SCENE_REPLAYS_CONTEXT_DUPE, UIKeys.SCENE_REPLAYS_CONTEXT_DUPE_DESCRIPTION,
                                (n) -> {
                                    for (int i = 0; i < n; i++) {
                                        this.dupeReplay();
                                    }
                                });

                        numberPanel.value.limit(1).integer();
                        numberPanel.value.setValue(1D);

                        UIOverlay.addOverlay(context, numberPanel);
                    }
                });
                menu.action(Icons.REMOVE, UIKeys.SCENE_REPLAYS_CONTEXT_REMOVE, this::removeReplay);

                if (separatedMode) {
                    this.addContextSeparator(menu);
                }

                if (!compactedMode) {
                    menu.action(Icons.COPY, UIKeys.SCENE_REPLAYS_CONTEXT_COPY_KEYFRAMES, this::openCopyKeyframesMenu);
                    menu.action(Icons.PASTE, UIKeys.SCENE_REPLAYS_CONTEXT_PASTE_KEYFRAMES,
                            () -> this.pasteToReplays(Window.getClipboardMap("_CopyKeyframes")));

                    if (separatedMode) {
                        this.addContextSeparator(menu);
                    }

                    menu.action(Icons.BLOCK, UIKeys.SCENE_REPLAYS_CONTEXT_RANDOM_SKINS, this::applyRandomSkins);
                    menu.action(Icons.ALL_DIRECTIONS, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS, this::processReplays);
                    menu.action(Icons.TIME, UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME, this::offsetTimeReplays);

                    if (separatedMode) {
                        this.addContextSeparator(menu);
                    }
                }

                menu.action(Icons.FOLDER, UIKeys.SCENE_REPLAYS_CONTEXT_ADD_GROUP, this::addGroup);

                if (!this.getCurrentFirst().group.get().isEmpty()) {
                    menu.action(Icons.FOLDER, UIKeys.SCENE_REPLAYS_CONTEXT_LEAVE_GROUP, this::leaveGroup);
                }

                if (compactedMode) {
                    menu.action(Icons.MORE, UIKeys.SCENE_REPLAYS_CONTEXT_MORE_OPTIONS, this::openReplayMoreOptionsMenu);
                }
            }
        } else {
            MapType copyReplay = Window.getClipboardMap("_CopyReplay");
            int duration = this.panel.getData().camera.calculateDuration();

            if (copyReplay != null) {
                menu.action(Icons.PASTE, UIKeys.SCENE_REPLAYS_CONTEXT_PASTE, () -> this.pasteReplay(copyReplay));
            }

            if (duration > 0) {
                menu.action(Icons.PLAY, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_CAMERA, () -> this.fromCamera(duration));
            }

            menu.action(Icons.BLOCK, UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK, this::fromModelBlock);
        }

        for (BiConsumer<UIReplayList, ContextMenuManager> consumer : extensions) {
            consumer.accept(this, menu);
        }
    }

    private boolean hasSelectedKeyframes() {
        UIReplaysEditor replayEditor = this.panel.replayEditor;

        if (replayEditor == null || replayEditor.keyframeEditor == null || replayEditor.keyframeEditor.view == null) {
            return false;
        }

        return replayEditor.keyframeEditor.view.getGraph().getSelected() != null;
    }

    private void openCopyKeyframesMenu() {
        UIContext context = this.resolveUiContext();

        if (context == null) {
            return;
        }

        context.replaceContextMenu((sub) -> {
            sub.autoKeys();
            sub.action(Icons.POSE, UIKeys.FILM_REPLAY_COPY_POSES, () -> this.copyKeyframesFiltered(KeyframeFactories.POSE));
            sub.action(Icons.ALL_DIRECTIONS, UIKeys.FILM_REPLAY_COPY_TRANSFORMS, () -> this.copyKeyframesFiltered(KeyframeFactories.TRANSFORM));
            sub.action(Icons.IMAGE, UIKeys.FILM_REPLAY_COPY_TEXTURE, () -> this.copyKeyframesByPropertySuffixes("texture"));
            sub.action(Icons.STRUCTURE, UIKeys.FILM_REPLAY_COPY_MODEL, () -> this.copyKeyframesByPropertySuffixes("model"));
        });
    }

    private void openReplayMoreOptionsMenu() {
        UIContext context = this.resolveUiContext();

        if (context == null) {
            return;
        }

        context.replaceContextMenu((sub) -> {
            sub.autoKeys();
            sub.action(Icons.COPY, UIKeys.SCENE_REPLAYS_CONTEXT_COPY_KEYFRAMES, this::openCopyKeyframesMenu);
            sub.action(Icons.PASTE, UIKeys.SCENE_REPLAYS_CONTEXT_PASTE_KEYFRAMES, () -> this.pasteToReplays(Window.getClipboardMap("_CopyKeyframes")));
            sub.action(Icons.BLOCK, UIKeys.SCENE_REPLAYS_CONTEXT_RANDOM_SKINS, this::applyRandomSkins);
            sub.action(Icons.ALL_DIRECTIONS, UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS, this::processReplays);
            sub.action(Icons.TIME, UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME, this::offsetTimeReplays);
        });
    }

    private void addContextSeparator(ContextMenuManager menu) {
        menu.action(new ContextSeparatorAction());
    }

    private void copyKeyframesFiltered(IKeyframeFactory... factories) {
        UIReplaysEditor replayEditor = this.panel.replayEditor;

        if (replayEditor == null || replayEditor.keyframeEditor == null || replayEditor.keyframeEditor.view == null) {
            // Fallback to export from replay directly
            MapType fallback = exportAllKeyframesFromReplay(this.getCurrentFirst(), factories);
            if (fallback != null && !fallback.isEmpty()) {
                Window.setInMemoryClipboard(fallback, "_CopyKeyframes");
            }
            return;
        }

        MapType data = replayEditor.keyframeEditor.view.serializeKeyframesByFactories(factories);

        if (data == null || data.isEmpty()) {
            data = exportAllKeyframesFromReplay(this.getCurrentFirst(), factories);
        }

        if (data != null && !data.isEmpty()) {
            Window.setInMemoryClipboard(data, "_CopyKeyframes");
        }
    }

    private void copyKeyframesByPropertySuffixes(String... suffixes) {
        UIReplaysEditor replayEditor = this.panel.replayEditor;

        if (replayEditor == null || replayEditor.keyframeEditor == null || replayEditor.keyframeEditor.view == null) {
            MapType fallback = exportKeyframesFromReplayByPropertySuffixes(this.getCurrentFirst(), suffixes);
            if (fallback != null && !fallback.isEmpty()) {
                Window.setInMemoryClipboard(fallback, "_CopyKeyframes");
            }
            return;
        }

        MapType data = replayEditor.keyframeEditor.view.serializeKeyframesByPropertySuffixes(suffixes);

        if (data == null || data.isEmpty()) {
            data = exportKeyframesFromReplayByPropertySuffixes(this.getCurrentFirst(), suffixes);
        }

        if (data != null && !data.isEmpty()) {
            Window.setInMemoryClipboard(data, "_CopyKeyframes");
        }
    }

    private MapType exportAllKeyframesFromReplay(Replay replay, IKeyframeFactory... factories) {
        if (replay == null) {
            return null;
        }

        Set<IKeyframeFactory> allow = new HashSet<>();
        if (factories != null && factories.length > 0) {
            allow.addAll(Arrays.asList(factories));
        }

        MapType out = new MapType();

        for (Map.Entry<String, KeyframeChannel> entry : replay.properties.properties.entrySet()) {
            KeyframeChannel channel = entry.getValue();

            if (!allow.isEmpty() && !allow.contains(channel.getFactory())) {
                continue;
            }

            ListType list = new ListType();

            for (Object kfObj : channel.getKeyframes()) {
                Keyframe<?> kf = (Keyframe<?>) kfObj;
                list.add(kf.toData());
            }

            if (!list.isEmpty()) {
                MapType data = new MapType();
                data.putString("type", CollectionUtils.getKey(KeyframeFactories.FACTORIES, channel.getFactory()));
                data.put("keyframes", list);

                out.put(entry.getKey(), data);
            }
        }

        return out;
    }

    private MapType exportKeyframesFromReplayByPropertySuffixes(Replay replay, String... suffixes) {
        if (replay == null) {
            return null;
        }

        Set<String> allow = new HashSet<>();
        if (suffixes != null && suffixes.length > 0) {
            allow.addAll(Arrays.asList(suffixes));
        }

        MapType out = new MapType();

        for (Map.Entry<String, KeyframeChannel> entry : replay.properties.properties.entrySet()) {
            if (!allow.isEmpty() && !matchesPropertySuffix(entry.getKey(), allow)) {
                continue;
            }

            KeyframeChannel channel = entry.getValue();
            ListType list = new ListType();

            for (Object kfObj : channel.getKeyframes()) {
                Keyframe<?> kf = (Keyframe<?>) kfObj;
                list.add(kf.toData());
            }

            if (!list.isEmpty()) {
                MapType data = new MapType();
                data.putString("type", CollectionUtils.getKey(KeyframeFactories.FACTORIES, channel.getFactory()));
                data.put("keyframes", list);

                out.put(entry.getKey(), data);
            }
        }

        return out;
    }

    private boolean matchesPropertySuffix(String property, Set<String> allow) {
        for (String suffix : allow) {
            if (property.equals(suffix) || property.endsWith("/" + suffix)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void handleSwap(int from, int to) {
        Replay src = this.list.get(from);
        Replay dest = this.list.get(to);

        if (src.isGroup.get()) {
            String srcPath = getReplayPath(src);
            String srcFullPath = srcPath.isEmpty() ? src.uuid.get() : srcPath + "/" + src.uuid.get();

            String destPath = getReplayPath(dest);
            String destFullPath = destPath.isEmpty() ? dest.uuid.get() : destPath + "/" + dest.uuid.get();

            if (destFullPath.equals(srcFullPath) || destFullPath.startsWith(srcFullPath + "/") ||
                    dest.group.get().equals(srcFullPath) || dest.group.get().startsWith(srcFullPath + "/")) {
                return;
            }
        }

        if (dest.isGroup.get()) {
            String destPath = getReplayPath(dest);
            String destGroupPath = destPath.isEmpty() ? dest.uuid.get() : destPath + "/" + dest.uuid.get();
            String srcGroup = src.group.get();

            if (!srcGroup.equals(destGroupPath)) {

                Replay insertionAnchor = dest;
                List<Replay> allReplays = this.panel.getData().replays.getAllTyped();

                String srcPathForCheck = getReplayPath(src);
                String srcFullPathForCheck = srcPathForCheck.isEmpty() ? src.uuid.get()
                        : srcPathForCheck + "/" + src.uuid.get();

                for (Replay r : allReplays) {
                    if (r == src)
                        continue;

                    if (src.isGroup.get()) {
                        String g = r.group.get();
                        if (g.equals(srcFullPathForCheck) || g.startsWith(srcFullPathForCheck + "/")) {
                            continue;
                        }
                    }

                    String g = r.group.get();
                    if (g.equals(destGroupPath) || g.startsWith(destGroupPath + "/")) {
                        insertionAnchor = r;
                    }
                }

                if (src.isGroup.get()) {
                    String oldPath = getReplayPath(src);
                    String oldFullPath = oldPath.isEmpty() ? src.uuid.get() : oldPath + "/" + src.uuid.get();

                    src.group.set(destGroupPath);

                    String newPath = getReplayPath(src);
                    String newFullPath = newPath.isEmpty() ? src.uuid.get() : newPath + "/" + src.uuid.get();

                    this.updateGroupPath(oldFullPath, newFullPath);
                } else {
                    src.group.set(destGroupPath);
                }

                this.moveReplayAndChildren(src, insertionAnchor, true);

                this.expandedGroups.put(destGroupPath, true);
                this.buildVisualList();
                this.updateFilmEditor();

                return;
            }
        }

        String destGroup = dest.group.get();

        if (src.isGroup.get()) {
            String oldPath = getReplayPath(src);
            String oldFullPath = oldPath.isEmpty() ? src.uuid.get() : oldPath + "/" + src.uuid.get();

            src.group.set(destGroup);

            String newPath = getReplayPath(src);
            String newFullPath = newPath.isEmpty() ? src.uuid.get() : newPath + "/" + src.uuid.get();

            if (!oldFullPath.equals(newFullPath)) {
                this.updateGroupPath(oldFullPath, newFullPath);
            }
        } else {
            src.group.set(destGroup);
        }

        this.moveReplayAndChildren(src, dest, from < to);
    }

    private void moveReplayAndChildren(Replay src, Replay dest, boolean insertAfter) {
        Film data = this.panel.getData();
        List<Replay> list = data.replays.getAllTyped();
        List<Replay> toMove = new ArrayList<>();

        toMove.add(src);

        if (src.isGroup.get()) {
            String srcPath = getReplayPath(src);
            String srcFullPath = srcPath.isEmpty() ? src.uuid.get() : srcPath + "/" + src.uuid.get();

            for (Replay r : list) {
                if (r == src)
                    continue;

                String g = r.group.get();
                if (g.equals(srcFullPath) || g.startsWith(srcFullPath + "/")) {
                    toMove.add(r);
                }
            }
        }

        data.preNotify(IValueListener.FLAG_UNMERGEABLE);

        list.removeAll(toMove);

        int destIndex = list.indexOf(dest);

        if (destIndex != -1) {
            int insertIndex = insertAfter ? destIndex + 1 : destIndex;
            insertIndex = Math.max(0, Math.min(insertIndex, list.size()));
            list.addAll(insertIndex, toMove);
        } else {
            list.addAll(toMove);
        }

        data.replays.sync();
        data.postNotify(IValueListener.FLAG_UNMERGEABLE);

        this.buildVisualList();
        this.updateFilmEditor();

        int newIndex = this.visualList.indexOf(src);
        if (newIndex != -1) {
            this.setIndex(newIndex);
        }
    }

    private void pasteToReplays(MapType data) {
        UIReplaysEditor replayEditor = this.panel.replayEditor;
        List<Replay> selectedReplays = replayEditor.replays.replays.getCurrent();

        if (data == null) {
            return;
        }

        Map<String, UIKeyframes.PastedKeyframes> parsedKeyframes = UIKeyframes.parseKeyframes(data);

        if (parsedKeyframes.isEmpty()) {
            return;
        }

        UINumberOverlayPanel offsetPanel = new UINumberOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_PASTE_KEYFRAMES_TITLE,
                UIKeys.SCENE_REPLAYS_CONTEXT_PASTE_KEYFRAMES_DESCRIPTION, (n) -> {
                    for (Replay replay : selectedReplays) {
                        int randomOffset = (int) (n.intValue() * Math.random());

                        for (Map.Entry<String, UIKeyframes.PastedKeyframes> entry : parsedKeyframes.entrySet()) {
                            String id = entry.getKey();
                            UIKeyframes.PastedKeyframes pastedKeyframes = entry.getValue();
                            KeyframeChannel channel = (KeyframeChannel) replay.keyframes.get(id);

                            if (channel == null || channel.getFactory() != pastedKeyframes.factory) {
                                channel = replay.properties.getOrCreate(replay.form.get(), id);
                            }

                            for (Keyframe kf : pastedKeyframes.keyframes) {
                                float finalTick = kf.getTick() + randomOffset;
                                int index = channel.insert(finalTick, kf.getValue());
                                Keyframe inserted = channel.get(index);

                                inserted.copy(kf);
                                inserted.setTick(finalTick);
                            }

                            channel.sort();
                        }
                    }
                });

        UIOverlay.addOverlay(this.resolveUiContext(), offsetPanel);
    }

    private void processReplays() {
        UITextbox expression = new UITextbox((t) -> LAST_PROCESS = t);
        UIStringList properties = new UIStringList(null);
        UIIcon sectionExpression = new UIIcon(Icons.CODE, (b) -> {
        });
        UIIcon sectionGrid = new UIIcon(Icons.MAZE, (b) -> {
        });
        UIIcon sectionCircle = new UIIcon(Icons.CIRCLE, (b) -> {
        });
        UIIcon sectionLine = new UIIcon(Icons.LINE, (b) -> {
        });
        UIIcon sectionScatter = new UIIcon(Icons.PARTICLE, (b) -> {
        });
        UITrackpad gridColumns = new UITrackpad((v) -> LAST_PROCESS_GRID_COLUMNS = Math.max(1, v.intValue()));
        UITrackpad gridSpacingX = new UITrackpad((v) -> LAST_PROCESS_GRID_SPACING_X = v.doubleValue());
        UITrackpad gridSpacingZ = new UITrackpad((v) -> LAST_PROCESS_GRID_SPACING_Z = v.doubleValue());
        UITrackpad circleRadius = new UITrackpad((v) -> LAST_PROCESS_CIRCLE_RADIUS = v.doubleValue());
        UITrackpad circleCount = new UITrackpad((v) -> LAST_PROCESS_CIRCLE_COUNT = Math.max(1, v.intValue()));
        UITrackpad circleStartAngle = new UITrackpad((v) -> LAST_PROCESS_CIRCLE_START_ANGLE = v.doubleValue());
        UITrackpad lineDirection = new UITrackpad((v) -> LAST_PROCESS_LINE_DIRECTION = v.doubleValue());
        UITrackpad lineSpacing = new UITrackpad((v) -> LAST_PROCESS_LINE_SPACING = v.doubleValue());
        UITrackpad scatterAreaX = new UITrackpad((v) -> LAST_PROCESS_SCATTER_AREA_X = v.doubleValue());
        UITrackpad scatterAreaZ = new UITrackpad((v) -> LAST_PROCESS_SCATTER_AREA_Z = v.doubleValue());
        UITrackpad scatterSeed = new UITrackpad((v) -> LAST_PROCESS_SCATTER_SEED = v.doubleValue());
        UITrackpad scatterMinSeparation = new UITrackpad((v) -> LAST_PROCESS_SCATTER_MIN_SEPARATION = v.doubleValue());
        UIToggle snapTerrain = new UIToggle(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_SNAP_TERRAIN, LAST_PROCESS_SNAP_TERRAIN, (t) -> LAST_PROCESS_SNAP_TERRAIN = t.getValue());
        UIElement gridControls = UI.column(4,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_GRID_COLUMNS),
                gridColumns,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_GRID_SPACING_X).marginTop(6),
                gridSpacingX,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_GRID_SPACING_Z).marginTop(6),
                gridSpacingZ);
        UIElement circleControls = UI.column(4,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_CIRCLE_RADIUS),
                circleRadius,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_CIRCLE_COUNT).marginTop(6),
                circleCount,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_CIRCLE_START_ANGLE).marginTop(6),
                circleStartAngle);
        UIElement lineControls = UI.column(4,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_LINE_DIRECTION),
                lineDirection,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_LINE_SPACING).marginTop(6),
                lineSpacing);
        UIElement scatterControls = UI.column(4,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_SCATTER_AREA_X),
                scatterAreaX,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_SCATTER_AREA_Z).marginTop(6),
                scatterAreaZ,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_SCATTER_SEED).marginTop(6),
                scatterSeed,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_SCATTER_MIN_SEPARATION).marginTop(6),
                scatterMinSeparation);
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_TITLE,
                UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_DESCRIPTION, (b) -> {
                    if (b) {
                        if (LAST_PROCESS_SECTION == 1) {
                            List<Integer> indices = new ArrayList<>(this.current);

                            Collections.sort(indices);

                            int count = indices.size();

                            if (count == 0) {
                                return;
                            }

                            int columns = Math.max(1, (int) gridColumns.getValue());
                            int rows = (int) Math.ceil(count / (double) columns);
                            double spacingX = gridSpacingX.getValue();
                            double spacingZ = gridSpacingZ.getValue();

                            for (int order = 0; order < count; order++) {
                                int index = indices.get(order);
                                Replay replay = this.list.get(index);

                                int col = order % columns;
                                int row = order / columns;

                                double xOffset = (col - (columns - 1) / 2D) * spacingX;
                                double zOffset = (row - (rows - 1) / 2D) * spacingZ;

                                this.applyOffset(replay, "x", xOffset);
                                this.applyOffset(replay, "z", zOffset);

                                if (snapTerrain.getValue()) {
                                    this.snapReplayToTerrain(replay);
                                }
                            }

                            return;
                        }

                        if (LAST_PROCESS_SECTION == 2) {
                            List<Integer> indices = new ArrayList<>(this.current);

                            Collections.sort(indices);

                            int count = indices.size();

                            if (count == 0) {
                                return;
                            }

                            int divisor = Math.max(1, (int) circleCount.getValue());
                            double radius = circleRadius.getValue();
                            double startAngle = circleStartAngle.getValue();

                            for (int order = 0; order < count; order++) {
                                int index = indices.get(order);
                                Replay replay = this.list.get(index);

                                double angle = Math.toRadians(startAngle + (order * 360D / divisor));
                                double xOffset = Math.cos(angle) * radius;
                                double zOffset = Math.sin(angle) * radius;

                                this.applyOffset(replay, "x", xOffset);
                                this.applyOffset(replay, "z", zOffset);

                                if (snapTerrain.getValue()) {
                                    this.snapReplayToTerrain(replay);
                                }
                            }

                            return;
                        }

                        if (LAST_PROCESS_SECTION == 3) {
                            List<Integer> indices = new ArrayList<>(this.current);

                            Collections.sort(indices);

                            int count = indices.size();

                            if (count == 0) {
                                return;
                            }

                            double direction = lineDirection.getValue();
                            double spacing = lineSpacing.getValue();
                            double angle = Math.toRadians(direction);
                            double stepX = Math.cos(angle) * spacing;
                            double stepZ = Math.sin(angle) * spacing;
                            double center = (count - 1) / 2D;

                            for (int order = 0; order < count; order++) {
                                int index = indices.get(order);
                                Replay replay = this.list.get(index);

                                double offset = order - center;
                                double xOffset = stepX * offset;
                                double zOffset = stepZ * offset;

                                this.applyOffset(replay, "x", xOffset);
                                this.applyOffset(replay, "z", zOffset);

                                if (snapTerrain.getValue()) {
                                    this.snapReplayToTerrain(replay);
                                }
                            }

                            return;
                        }

                        if (LAST_PROCESS_SECTION == 4) {
                            List<Integer> indices = new ArrayList<>(this.current);

                            Collections.sort(indices);

                            int count = indices.size();

                            if (count == 0) {
                                return;
                            }

                            double areaX = scatterAreaX.getValue();
                            double areaZ = scatterAreaZ.getValue();
                            double minSep = scatterMinSeparation.getValue();
                            double minSepSq = minSep * minSep;
                            long seed = (long) Math.round(scatterSeed.getValue());

                            java.util.Random random = new java.util.Random(seed);
                            List<double[]> placed = new ArrayList<>();

                            for (int order = 0; order < count; order++) {
                                int index = indices.get(order);
                                Replay replay = this.list.get(index);

                                double xOffset = 0D;
                                double zOffset = 0D;
                                boolean accepted = false;

                                for (int attempt = 0; attempt < 200; attempt++) {
                                    double candidateX = (random.nextDouble() - 0.5D) * areaX;
                                    double candidateZ = (random.nextDouble() - 0.5D) * areaZ;

                                    boolean ok = true;

                                    if (minSep > 0D) {
                                        for (double[] point : placed) {
                                            double dx = candidateX - point[0];
                                            double dz = candidateZ - point[1];

                                            if (dx * dx + dz * dz < minSepSq) {
                                                ok = false;
                                                break;
                                            }
                                        }
                                    }

                                    if (ok) {
                                        xOffset = candidateX;
                                        zOffset = candidateZ;
                                        accepted = true;
                                        break;
                                    }
                                }

                                if (!accepted) {
                                    xOffset = (random.nextDouble() - 0.5D) * areaX;
                                    zOffset = (random.nextDouble() - 0.5D) * areaZ;
                                }

                                placed.add(new double[] { xOffset, zOffset });

                                this.applyOffset(replay, "x", xOffset);
                                this.applyOffset(replay, "z", zOffset);

                                if (snapTerrain.getValue()) {
                                    this.snapReplayToTerrain(replay);
                                }
                            }

                            return;
                        }

                        MathBuilder builder = new MathBuilder();
                        int min = Integer.MAX_VALUE;

                        builder.register("i");
                        builder.register("o");
                        builder.register("v");
                        builder.register("ki");

                        IExpression parse;

                        try {
                            parse = builder.parse(expression.getText());
                        } catch (Exception e) {
                            return;
                        }

                        LAST_PROCESS_PROPERTIES = new ArrayList<>(properties.getCurrent());

                        for (int index : this.current) {
                            min = Math.min(min, index);
                        }

                        for (int index : this.current) {
                            Replay replay = this.list.get(index);

                            builder.variables.get("i").set(index);
                            builder.variables.get("o").set(index - min);

                            for (String s : properties.getCurrent()) {
                                KeyframeChannel channel = (KeyframeChannel) replay.keyframes.get(s);
                                List keyframes = channel.getKeyframes();

                                for (int i = 0; i < keyframes.size(); i++) {
                                    Keyframe kf = (Keyframe) keyframes.get(i);

                                    builder.variables.get("v").set(kf.getFactory().getY(kf.getValue()));
                                    builder.variables.get("ki").set(i);

                                    kf.setValue(kf.getFactory().yToValue(parse.doubleValue()), true);
                                }
                            }

                            if (snapTerrain.getValue()) {
                                this.snapReplayToTerrain(replay);
                            }
                        }
                    }
                })
        {
            @Override
            protected void renderBackground(UIContext context)
            {
                super.renderBackground(context);

                UIIcon active = null;

                if (sectionExpression.isActive())
                {
                    active = sectionExpression;
                }
                else if (sectionGrid.isActive())
                {
                    active = sectionGrid;
                }
                else if (sectionCircle.isActive())
                {
                    active = sectionCircle;
                }
                else if (sectionLine.isActive())
                {
                    active = sectionLine;
                }
                else if (sectionScatter.isActive())
                {
                    active = sectionScatter;
                }

                if (active != null)
                {
                    UIDashboardPanels.renderHighlightHorizontal(context.batcher, active.area);
                }
            }
        };

        for (KeyframeChannel<?> channel : this.getCurrentFirst().keyframes.getChannels()) {
            if (KeyframeFactories.isNumeric(channel.getFactory())) {
                properties.add(channel.getId());
            }
        }

        properties.background().multi().sort();
        properties.relative(expression).y(-28).w(1F).h(16 * 9).anchor(0F, 1F);

        if (!LAST_PROCESS_PROPERTIES.isEmpty()) {
            properties.setCurrentScroll(LAST_PROCESS_PROPERTIES.get(0));
        }

        for (String property : LAST_PROCESS_PROPERTIES) {
            properties.addIndex(properties.getList().indexOf(property));
        }

        expression.setText(LAST_PROCESS);
        expression.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_EXPRESSION_TOOLTIP);
        expression.relative(panel.confirm).y(-1F, -5).w(1F).h(20);

        sectionExpression.active(LAST_PROCESS_SECTION == 0)
                .tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_SECTION_EXPRESSION);
        sectionGrid.active(LAST_PROCESS_SECTION == 1).tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_SECTION_GRID);
        sectionCircle.active(LAST_PROCESS_SECTION == 2).tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_SECTION_CIRCLE);
        sectionLine.active(LAST_PROCESS_SECTION == 3).tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_SECTION_LINE);
        sectionScatter.active(LAST_PROCESS_SECTION == 4).tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_PROCESS_SECTION_SCATTER);

        sectionExpression.callback = (b) -> {
            LAST_PROCESS_SECTION = 0;
            sectionExpression.active(true);
            sectionGrid.active(false);
            sectionCircle.active(false);
            sectionLine.active(false);
            sectionScatter.active(false);
            gridControls.setVisible(false);
            circleControls.setVisible(false);
            lineControls.setVisible(false);
            scatterControls.setVisible(false);
            expression.setVisible(true);
            properties.setVisible(true);
        };

        sectionGrid.callback = (b) -> {
            LAST_PROCESS_SECTION = 1;
            sectionExpression.active(false);
            sectionGrid.active(true);
            sectionCircle.active(false);
            sectionLine.active(false);
            sectionScatter.active(false);
            gridControls.setVisible(true);
            circleControls.setVisible(false);
            lineControls.setVisible(false);
            scatterControls.setVisible(false);
            expression.setVisible(false);
            properties.setVisible(false);
        };

        sectionCircle.callback = (b) -> {
            LAST_PROCESS_SECTION = 2;
            sectionExpression.active(false);
            sectionGrid.active(false);
            sectionCircle.active(true);
            sectionLine.active(false);
            sectionScatter.active(false);
            gridControls.setVisible(false);
            circleControls.setVisible(true);
            lineControls.setVisible(false);
            scatterControls.setVisible(false);
            expression.setVisible(false);
            properties.setVisible(false);
        };

        sectionLine.callback = (b) -> {
            LAST_PROCESS_SECTION = 3;
            sectionExpression.active(false);
            sectionGrid.active(false);
            sectionCircle.active(false);
            sectionLine.active(true);
            sectionScatter.active(false);
            gridControls.setVisible(false);
            circleControls.setVisible(false);
            lineControls.setVisible(true);
            scatterControls.setVisible(false);
            expression.setVisible(false);
            properties.setVisible(false);
        };

        sectionScatter.callback = (b) -> {
            LAST_PROCESS_SECTION = 4;
            sectionExpression.active(false);
            sectionGrid.active(false);
            sectionCircle.active(false);
            sectionLine.active(false);
            sectionScatter.active(true);
            gridControls.setVisible(false);
            circleControls.setVisible(false);
            lineControls.setVisible(false);
            scatterControls.setVisible(true);
            expression.setVisible(false);
            properties.setVisible(false);
        };

        gridColumns.limit(1).integer().values(1, 1, 5).setValue(LAST_PROCESS_GRID_COLUMNS);
        gridSpacingX.values(0.1D, 0.01D, 1D).setValue(LAST_PROCESS_GRID_SPACING_X);
        gridSpacingZ.values(0.1D, 0.01D, 1D).setValue(LAST_PROCESS_GRID_SPACING_Z);

        circleRadius.values(0.1D, 0.01D, 1D).setValue(LAST_PROCESS_CIRCLE_RADIUS);
        circleCount.limit(1).integer().values(1, 1, 5).setValue(LAST_PROCESS_CIRCLE_COUNT);
        circleStartAngle.values(1D, 0.1D, 10D).setValue(LAST_PROCESS_CIRCLE_START_ANGLE);

        lineDirection.values(1D, 0.1D, 10D).setValue(LAST_PROCESS_LINE_DIRECTION);
        lineSpacing.values(0.1D, 0.01D, 1D).setValue(LAST_PROCESS_LINE_SPACING);

        scatterAreaX.values(0.1D, 0.01D, 1D).setValue(LAST_PROCESS_SCATTER_AREA_X);
        scatterAreaZ.values(0.1D, 0.01D, 1D).setValue(LAST_PROCESS_SCATTER_AREA_Z);
        scatterSeed.values(1D, 0.1D, 10D).setValue(LAST_PROCESS_SCATTER_SEED);
        scatterMinSeparation.values(0.1D, 0.01D, 1D).setValue(LAST_PROCESS_SCATTER_MIN_SEPARATION);

        gridControls.relative(panel.content).x(6).y(70).w(1F, -12).h(1F, -110);
        gridControls.setVisible(LAST_PROCESS_SECTION == 1);

        circleControls.relative(panel.content).x(6).y(70).w(1F, -12).h(1F, -110);
        circleControls.setVisible(LAST_PROCESS_SECTION == 2);

        lineControls.relative(panel.content).x(6).y(70).w(1F, -12).h(1F, -110);
        lineControls.setVisible(LAST_PROCESS_SECTION == 3);

        scatterControls.relative(panel.content).x(6).y(70).w(1F, -12).h(1F, -110);
        scatterControls.setVisible(LAST_PROCESS_SECTION == 4);

        expression.setVisible(LAST_PROCESS_SECTION == 0);
        properties.setVisible(LAST_PROCESS_SECTION == 0);

        snapTerrain.relative(panel.confirm).x(6).y(-1F, -24).w(1F, -12);

        panel.confirm.w(1F, -10);
        panel.content.add(gridControls, circleControls, lineControls, scatterControls, expression, properties, snapTerrain);
        panel.icons.add(sectionExpression, sectionGrid, sectionCircle, sectionLine, sectionScatter);

        UIOverlay.addOverlay(this.resolveUiContext(), panel, 240, 320);
    }

    private void applyOffset(Replay replay, String property, double offset) {
        BaseValue value = replay.keyframes.get(property);

        if (!(value instanceof KeyframeChannel<?> channel)) {
            return;
        }

        @SuppressWarnings("rawtypes")
        KeyframeChannel rawChannel = (KeyframeChannel) channel;
        @SuppressWarnings("rawtypes")
        List<Keyframe> keyframes = rawChannel.getKeyframes();

        for (int i = 0; i < keyframes.size(); i++) {
            Keyframe kf = keyframes.get(i);
            double currentValue = rawChannel.getFactory().getY(kf.getValue());

            kf.setValue(rawChannel.getFactory().yToValue(currentValue + offset), true);
        }
    }

    private void snapReplayToTerrain(Replay replay) {
        World world = MinecraftClient.getInstance().world;

        if (world == null || replay.keyframes.y.getKeyframes().isEmpty()) {
            return;
        }

        float tick = this.getFirstPositionTick(replay);
        double x = replay.keyframes.x.interpolate(tick);
        double y = replay.keyframes.y.interpolate(tick);
        double z = replay.keyframes.z.interpolate(tick);
        Double terrainY = this.getTerrainY(world, x, z);

        if (terrainY == null) {
            return;
        }

        double offset = terrainY - y;

        if (offset != 0D) {
            this.applyOffset(replay, "y", offset);
        }
    }

    private float getFirstPositionTick(Replay replay) {
        float tick = Float.MAX_VALUE;

        tick = Math.min(tick, this.getFirstTick(replay.keyframes.x));
        tick = Math.min(tick, this.getFirstTick(replay.keyframes.y));
        tick = Math.min(tick, this.getFirstTick(replay.keyframes.z));

        return tick == Float.MAX_VALUE ? 0F : tick;
    }

    private float getFirstTick(KeyframeChannel<Double> channel) {
        List<Keyframe<Double>> keyframes = channel.getKeyframes();

        if (keyframes.isEmpty()) {
            return Float.MAX_VALUE;
        }

        return keyframes.get(0).getTick();
    }

    private Double getTerrainY(World world, double x, double z) {
        int top = world.getTopY();
        int bottom = world.getBottomY();
        double distance = Math.max(0D, top - bottom + 2D);
        Vec3d start = new Vec3d(x, top + 1D, z);
        BlockHitResult result = RayTracing.rayTrace(world, start, new Vec3d(0D, -1D, 0D), distance);

        if (result.getType() == HitResult.Type.BLOCK) {
            return result.getPos().y;
        }

        return null;
    }

    private void offsetTimeReplays() {
        UITextbox tick = new UITextbox((t) -> LAST_OFFSET = t);
        UIIcon sectionExpression = new UIIcon(Icons.CODE, (b) -> {
        });
        UIIcon sectionStagger = new UIIcon(Icons.TIME, (b) -> {
        });
        UIIcon sectionAlternating = new UIIcon(Icons.EXCHANGE, (b) -> {
        });
        UIIcon sectionRandom = new UIIcon(Icons.REFRESH, (b) -> {
        });
        UITrackpad staggerStep = new UITrackpad((v) -> LAST_OFFSET_STEP = v.doubleValue());
        UITrackpad randomSeed = new UITrackpad((v) -> LAST_OFFSET_RANDOM_SEED = v.doubleValue());
        UITrackpad randomMin = new UITrackpad((v) -> LAST_OFFSET_RANDOM_MIN = v.doubleValue());
        UITrackpad randomMax = new UITrackpad((v) -> LAST_OFFSET_RANDOM_MAX = v.doubleValue());
        UIElement staggerControls = UI.column(4,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_STEP),
                staggerStep);
        UIElement randomControls = UI.column(4,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_SEED),
                randomSeed,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_RANDOM_MIN).marginTop(6),
                randomMin,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_RANDOM_MAX).marginTop(6),
                randomMax);
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_TITLE,
                UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_DESCRIPTION, (b) -> {
                    if (b) {
                        if (LAST_OFFSET_SECTION == 1) {
                            List<Integer> indices = new ArrayList<>(this.current);

                            Collections.sort(indices);

                            double step = staggerStep.getValue();
                            int count = indices.size();

                            for (int order = 0; order < count; order++) {
                                int index = indices.get(order);
                                Replay replay = this.list.get(index);
                                float tickv = (float) (order * step);

                                BaseValue.edit(replay, (r) -> r.shift(tickv));
                            }

                            return;
                        }

                        if (LAST_OFFSET_SECTION == 2) {
                            List<Integer> indices = new ArrayList<>(this.current);

                            Collections.sort(indices);

                            double step = staggerStep.getValue();
                            int count = indices.size();

                            for (int order = 0; order < count; order++) {
                                int index = indices.get(order);
                                Replay replay = this.list.get(index);
                                float tickv = (float) ((order % 2 == 0 ? 1D : -1D) * step);

                                BaseValue.edit(replay, (r) -> r.shift(tickv));
                            }

                            return;
                        }

                        if (LAST_OFFSET_SECTION == 3) {
                            List<Integer> indices = new ArrayList<>(this.current);

                            Collections.sort(indices);

                            double seed = randomSeed.getValue();
                            double min = randomMin.getValue();
                            double max = randomMax.getValue();
                            double start = Math.min(min, max);
                            double end = Math.max(min, max);
                            java.util.Random random = new java.util.Random((long) Math.round(seed));
                            int count = indices.size();

                            for (int order = 0; order < count; order++) {
                                int index = indices.get(order);
                                Replay replay = this.list.get(index);
                                float tickv = (float) (start + (end - start) * random.nextDouble());

                                BaseValue.edit(replay, (r) -> r.shift(tickv));
                            }

                            return;
                        }

                        MathBuilder builder = new MathBuilder();
                        int min = Integer.MAX_VALUE;

                        builder.register("i");
                        builder.register("o");

                        IExpression parse = null;

                        try {
                            parse = builder.parse(tick.getText());
                        } catch (Exception e) {
                        }

                        for (int index : this.current) {
                            min = Math.min(min, index);
                        }

                        for (int index : this.current) {
                            Replay replay = this.list.get(index);

                            builder.variables.get("i").set(index);
                            builder.variables.get("o").set(index - min);

                            float tickv = parse == null ? 0F : (float) parse.doubleValue();

                            BaseValue.edit(replay, (r) -> r.shift(tickv));
                        }
                    }
                })
        {
            @Override
            protected void renderBackground(UIContext context)
            {
                super.renderBackground(context);

                UIIcon active = null;

                if (sectionExpression.isActive())
                {
                    active = sectionExpression;
                }
                else if (sectionStagger.isActive())
                {
                    active = sectionStagger;
                }
                else if (sectionAlternating.isActive())
                {
                    active = sectionAlternating;
                }
                else if (sectionRandom.isActive())
                {
                    active = sectionRandom;
                }

                if (active != null)
                {
                    UIDashboardPanels.renderHighlightHorizontal(context.batcher, active.area);
                }
            }
        };

        tick.setText(LAST_OFFSET);
        tick.tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_EXPRESSION_TOOLTIP);
        tick.relative(panel.confirm).y(-1F, -5).w(1F).h(20);

        sectionExpression.active(LAST_OFFSET_SECTION == 0)
                .tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_SECTION_EXPRESSION);
        sectionStagger.active(LAST_OFFSET_SECTION == 1)
                .tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_SECTION_STAGGER);
        sectionAlternating.active(LAST_OFFSET_SECTION == 2)
                .tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_SECTION_ALTERNATING);
        sectionRandom.active(LAST_OFFSET_SECTION == 3).tooltip(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_SECTION_RANDOM);

        sectionExpression.callback = (b) -> {
            LAST_OFFSET_SECTION = 0;
            sectionExpression.active(true);
            sectionStagger.active(false);
            sectionAlternating.active(false);
            sectionRandom.active(false);
            staggerControls.setVisible(false);
            randomControls.setVisible(false);
            tick.setVisible(true);
            panel.setMessage(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_DESCRIPTION_EXPRESSION);
        };

        sectionStagger.callback = (b) -> {
            LAST_OFFSET_SECTION = 1;
            sectionExpression.active(false);
            sectionStagger.active(true);
            sectionAlternating.active(false);
            sectionRandom.active(false);
            staggerControls.setVisible(true);
            randomControls.setVisible(false);
            tick.setVisible(false);
            panel.setMessage(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_DESCRIPTION_STAGGER);
        };

        sectionAlternating.callback = (b) -> {
            LAST_OFFSET_SECTION = 2;
            sectionExpression.active(false);
            sectionStagger.active(false);
            sectionAlternating.active(true);
            sectionRandom.active(false);
            staggerControls.setVisible(true);
            randomControls.setVisible(false);
            tick.setVisible(false);
            panel.setMessage(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_DESCRIPTION_ALTERNATING);
        };

        sectionRandom.callback = (b) -> {
            LAST_OFFSET_SECTION = 3;
            sectionExpression.active(false);
            sectionStagger.active(false);
            sectionAlternating.active(false);
            sectionRandom.active(true);
            staggerControls.setVisible(false);
            randomControls.setVisible(true);
            tick.setVisible(false);
            panel.setMessage(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_DESCRIPTION_RANDOM);
        };

        staggerStep.values(1D, 0.1D, 10D).setValue(LAST_OFFSET_STEP);
        randomSeed.values(1D, 0.1D, 10D).setValue(LAST_OFFSET_RANDOM_SEED);
        randomMin.values(1D, 0.1D, 10D).setValue(LAST_OFFSET_RANDOM_MIN);
        randomMax.values(1D, 0.1D, 10D).setValue(LAST_OFFSET_RANDOM_MAX);
        staggerControls.relative(panel.confirm).x(6).y(-1F, -58).w(1F, -12).h(44);
        staggerControls.setVisible(LAST_OFFSET_SECTION == 1 || LAST_OFFSET_SECTION == 2);
        randomControls.relative(panel.content).x(6).y(70).w(1F, -12).h(1F, -130);
        randomControls.setVisible(LAST_OFFSET_SECTION == 3);
        tick.setVisible(LAST_OFFSET_SECTION == 0);

        if (LAST_OFFSET_SECTION == 1) {
            panel.setMessage(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_DESCRIPTION_STAGGER);
        } else if (LAST_OFFSET_SECTION == 2) {
            panel.setMessage(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_DESCRIPTION_ALTERNATING);
        } else if (LAST_OFFSET_SECTION == 3) {
            panel.setMessage(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_DESCRIPTION_RANDOM);
        } else {
            panel.setMessage(UIKeys.SCENE_REPLAYS_CONTEXT_OFFSET_TIME_DESCRIPTION_EXPRESSION);
        }

        panel.confirm.w(1F, -10);
        panel.content.add(staggerControls, randomControls, tick);
        panel.icons.add(sectionExpression, sectionStagger, sectionAlternating, sectionRandom);

        UIOverlay.addOverlay(this.resolveUiContext(), panel, 240, 240);
    }

    private void copyReplay() {
        MapType replays = new MapType();
        ListType replayList = new ListType();
        ListType idList = new ListType();

        replays.put("replays", replayList);
        replays.put("ids", idList);

        for (Replay replay : this.getCurrent()) {
            replayList.add(replay.toData());
            idList.addString(replay.getId());
        }

        Window.setInMemoryClipboard(replays, "_CopyReplay");
    }

    private void copyGroup() {
        Replay group = this.getCurrentGroup();

        if (group == null) {
            return;
        }

        MapType data = this.createGroupClipboardData(group);

        if (data != null) {
            Window.setInMemoryClipboard(data, GROUP_CLIPBOARD_KEY);
        }
    }

    private void pasteGroup(MapType data) {
        Replay targetGroup = this.getCurrentGroup();

        if (targetGroup == null || data == null) {
            return;
        }

        this.pasteGroupData(data, targetGroup);
    }

    private void duplicateGroup() {
        Replay group = this.getCurrentGroup();

        if (group == null) {
            return;
        }

        MapType data = this.createGroupClipboardData(group);

        if (data != null) {
            this.pasteGroupData(data, group);
        }
    }

    private void deleteGroup() {
        Replay group = this.getCurrentGroup();

        if (group == null) {
            return;
        }

        Film film = this.panel.getData();
        List<Replay> all = film.replays.getAllTyped();
        String groupPath = this.getFullGroupPath(group);
        List<Replay> toRemove = new ArrayList<>();

        for (Replay replay : all) {
            if (replay == group) {
                toRemove.add(replay);
                continue;
            }

            String parentPath = replay.group.get();

            if (parentPath.equals(groupPath) || parentPath.startsWith(groupPath + "/")) {
                toRemove.add(replay);
            }
        }

        int index = this.getIndex();

        for (Replay replay : toRemove) {
            film.replays.remove(replay);
        }

        this.clearExpandedGroupState(groupPath);

        int size = this.list.size();
        index = MathUtils.clamp(index, 0, size - 1);

        this.buildVisualList();
        size = this.list.size();
        this.panel.replayEditor.setReplay(size == 0 ? null : CollectionUtils.getSafe(this.list, index));
        this.updateFilmEditor();
    }

    private MapType createGroupClipboardData(Replay group) {
        String fullPath = this.getFullGroupPath(group);
        List<Replay> all = this.panel.getData().replays.getAllTyped();
        ListType replayList = new ListType();

        for (Replay replay : all) {
            if (replay == group) {
                replayList.add(replay.toData());
                continue;
            }

            String parentPath = replay.group.get();

            if (parentPath.equals(fullPath) || parentPath.startsWith(fullPath + "/")) {
                replayList.add(replay.toData());
            }
        }

        if (replayList.isEmpty()) {
            return null;
        }

        MapType data = new MapType();
        data.putString("root_uuid", group.uuid.get());
        data.putString("root_full_path", fullPath);
        data.put("replays", replayList);

        return data;
    }

    private void pasteGroupData(MapType data, Replay targetGroup) {
        Film film = this.panel.getData();
        ListType copied = data.getList("replays");

        if (copied.isEmpty()) {
            return;
        }

        String rootUuid = data.getString("root_uuid", "");
        String rootFullPath = data.getString("root_full_path", "");
        String destinationParentPath = targetGroup.group.get();
        Replay insertionAnchor = this.findLastGroupElement(targetGroup);
        List<Replay> created = new ArrayList<>();
        List<String> oldParentPaths = new ArrayList<>();
        List<String> oldUuids = new ArrayList<>();
        Map<String, String> remappedGroupUuids = new HashMap<>();
        Replay newRoot = null;

        for (BaseType replayType : copied) {
            Replay replay = film.replays.addReplay();

            BaseValue.edit(replay, (r) -> r.fromData(replayType));

            String oldUuid = replay.uuid.get();
            String oldParentPath = replay.group.get();

            replay.uuid.set(UUID.randomUUID().toString());

            if (replay.isGroup.get()) {
                remappedGroupUuids.put(oldUuid, replay.uuid.get());
            }

            if (oldUuid.equals(rootUuid)) {
                newRoot = replay;
            }

            created.add(replay);
            oldParentPaths.add(oldParentPath);
            oldUuids.add(oldUuid);
        }

        if (newRoot == null) {
            return;
        }

        String newRootFullPath = destinationParentPath.isEmpty() ? newRoot.uuid.get() : destinationParentPath + "/" + newRoot.uuid.get();

        for (int i = 0; i < created.size(); i++) {
            Replay replay = created.get(i);
            String oldUuid = oldUuids.get(i);
            String oldParentPath = oldParentPaths.get(i);

            if (oldUuid.equals(rootUuid)) {
                replay.group.set(destinationParentPath);
            } else {
                replay.group.set(this.remapGroupParentPath(oldParentPath, rootFullPath, newRootFullPath, remappedGroupUuids));
            }
        }

        List<Replay> list = film.replays.getAllTyped();

        list.removeAll(created);

        int index = insertionAnchor == null ? list.size() - 1 : list.indexOf(insertionAnchor);

        if (index < -1) {
            index = list.size() - 1;
        }

        list.addAll(index + 1, created);
        film.replays.sync();

        this.expandedGroups.put(newRootFullPath, true);
        this.buildVisualList();
        this.setCurrentDirect(newRoot);
        this.panel.replayEditor.setReplay(newRoot);
        this.updateFilmEditor();
    }

    private String remapGroupParentPath(String oldPath, String oldRootFullPath, String newRootFullPath, Map<String, String> remappedGroupUuids) {
        if (oldPath.equals(oldRootFullPath)) {
            return newRootFullPath;
        }

        if (oldPath.startsWith(oldRootFullPath + "/")) {
            String suffix = oldPath.substring(oldRootFullPath.length() + 1);

            if (suffix.isEmpty()) {
                return newRootFullPath;
            }

            String[] parts = suffix.split("/");
            StringBuilder builder = new StringBuilder(newRootFullPath);

            for (String part : parts) {
                builder.append("/").append(remappedGroupUuids.getOrDefault(part, part));
            }

            return builder.toString();
        }

        return oldPath;
    }

    private Replay findLastGroupElement(Replay group) {
        String fullPath = this.getFullGroupPath(group);
        Replay anchor = group;

        for (Replay replay : this.panel.getData().replays.getAllTyped()) {
            String parentPath = replay.group.get();

            if (replay == group || parentPath.equals(fullPath) || parentPath.startsWith(fullPath + "/")) {
                anchor = replay;
            }
        }

        return anchor;
    }

    private Replay getCurrentGroup() {
        if (this.isDeselected()) {
            return null;
        }

        Replay replay = this.getCurrentFirst();

        if (replay == null || !replay.isGroup.get()) {
            return null;
        }

        return replay;
    }

    private String getFullGroupPath(Replay group) {
        String path = group.group.get();

        return path.isEmpty() ? group.uuid.get() : path + "/" + group.uuid.get();
    }

    private void clearExpandedGroupState(String removedGroupPath) {
        List<String> keys = new ArrayList<>(this.expandedGroups.keySet());

        for (String key : keys) {
            if (key.equals(removedGroupPath) || key.startsWith(removedGroupPath + "/")) {
                this.expandedGroups.remove(key);
            }
        }
    }

    private void pasteReplay(MapType data) {
        Film film = this.panel.getData();
        ListType replays = data.getList("replays");
        ListType ids = data.getList("ids");
        Map<String, String> idMap = new HashMap<>();
        List<Replay> created = new ArrayList<>();
        Replay last = null;

        for (int i = 0; i < replays.size(); i++) {
            BaseType replayType = replays.get(i);
            Replay replay = film.replays.addReplay();
            String oldId = ids != null && ids.has(i) ? ids.getString(i) : "";

            BaseValue.edit(replay, (r) -> r.fromData(replayType));
            replay.uuid.set(UUID.randomUUID().toString());

            if (oldId != null && !oldId.isEmpty()) {
                idMap.put(oldId, replay.getId());
            }

            created.add(replay);
            last = replay;
        }

        AttackActionClip.remapTargets(created, idMap);

        if (last != null) {
            this.buildVisualList();
            this.setCurrentDirect(last);
            this.panel.replayEditor.setReplay(last);
            this.updateFilmEditor();
        }
    }

    public void openFormEditor(ValueForm form, boolean editing, Consumer<Form> consumer) {
        UIElement target = this.panel;

        if (this.getRoot() != null) {
            target = this.getParentContainer();
        }

        UIFormPalette palette = UIFormPalette.open(target, editing, form.get(), (f) -> {
            for (Replay replay : this.getCurrent()) {
                replay.form.set(FormUtils.copy(f));
            }

            this.updateFilmEditor();

            if (consumer != null) {
                consumer.accept(f);
            } else {
                this.overlay.pickEdit.setForm(f);
            }
        });

        if (!editing) {
            palette.favorites();

            if (!palette.list.hasFavoriteCategory(LAST_PICK_FAVORITE_CATEGORY_ID))
            {
                LAST_PICK_FAVORITE_CATEGORY_ID = null;
            }

            palette.list.setFavoriteCategoryChangedListener((categoryId) -> LAST_PICK_FAVORITE_CATEGORY_ID = categoryId);
            palette.list.setActiveFavoriteCategoryWithFallback(LAST_PICK_FAVORITE_CATEGORY_ID);
        }
        palette.updatable();
    }

    public void refreshAfterExternalEdit()
    {
        this.buildVisualList();

        int size = this.list.size();
        int index = MathUtils.clamp(this.getIndex(), 0, Math.max(0, size - 1));

        this.panel.replayEditor.setReplay(size == 0 ? null : CollectionUtils.getSafe(this.list, index));
        this.updateFilmEditor();
    }

    public void reloadReplays()
    {
        Film film = this.panel.getData();

        if (film == null)
        {
            return;
        }

        for (Replay replay : film.replays.getList())
        {
            MobCemPoseCapture.syncReplay(replay);
        }

        Replay current = this.getCurrentFirst();

        this.buildVisualList();

        if (current != null)
        {
            this.setCurrentDirect(current);
        }

        this.updateFilmEditor();
        this.update();

        if (this.overlay != null)
        {
            this.overlay.setReplay(current);
        }
    }

    public void addReplay() {
        World world = MinecraftClient.getInstance().world;
        Camera camera = this.panel.getCamera();

        BlockHitResult blockHitResult = RayTracing.rayTrace(world, camera, 64F);
        Vec3d p = blockHitResult.getPos();
        Vector3d position = new Vector3d(p.x, p.y, p.z);

        if (blockHitResult.getType() == HitResult.Type.MISS) {
            position.set(camera.getLookDirection()).mul(5F).add(camera.position);
        }

        this.addReplay(position, camera.rotation.x, camera.rotation.y + MathUtils.PI);
    }

    private void fromCamera(int duration) {
        Position position = new Position();
        Clips camera = this.panel.getData().camera;
        CameraClipContext context = new CameraClipContext();

        Film film = this.panel.getData();
        Replay replay = film.replays.addReplay();

        context.clips = camera;

        for (int i = 0; i < duration; i++) {
            context.clipData.clear();
            context.setup(i, 0F);

            for (Clip clip : context.clips.getClips(i)) {
                context.apply(clip, position);
            }

            context.currentLayer = 0;

            float yaw = position.angle.yaw - 180;

            replay.keyframes.x.insert(i, position.point.x);
            replay.keyframes.y.insert(i, position.point.y);
            replay.keyframes.z.insert(i, position.point.z);
            replay.keyframes.yaw.insert(i, (double) yaw);
            replay.keyframes.headYaw.insert(i, (double) yaw);
            replay.keyframes.bodyYaw.insert(i, (double) yaw);
            replay.keyframes.pitch.insert(i, (double) position.angle.pitch);
        }

        this.buildVisualList();
        this.setCurrentDirect(replay);
        this.panel.replayEditor.setReplay(replay);
        this.updateFilmEditor();

        this.openFormEditor(replay.form, false, null);
    }

    private void fromModelBlock() {
        ArrayList<ModelBlockEntity> modelBlocks = new ArrayList<>(BBSRendering.capturedModelBlocks);
        UISearchList<String> search = new UISearchList<>(new UIStringList(null));
        UIList<String> list = search.list;
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d playerPos = client.player != null ? client.player.getPos() : Vec3d.ZERO;

        list.multi();

        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK_TITLE,
                UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK_DESCRIPTION, (b) -> {
                    if (b) {
                        List<String> selected = list.getCurrent();

                        if (selected.isEmpty()) {
                            int index = list.getIndex();
                            ModelBlockEntity modelBlock = CollectionUtils.getSafe(modelBlocks, index);

                            if (modelBlock != null) {
                                this.fromModelBlock(modelBlock);
                            }
                        } else {
                            for (String name : selected) {
                                int index = list.getList().indexOf(name);
                                ModelBlockEntity modelBlock = CollectionUtils.getSafe(modelBlocks, index);

                                if (modelBlock != null) {
                                    this.fromModelBlock(modelBlock);
                                }
                            }
                        }
                    }
                });

        panel.resizable().minSize(300, 300);

        modelBlocks.sort(Comparator.comparingDouble((ModelBlockEntity block) ->
            playerPos.squaredDistanceTo(Vec3d.ofCenter(block.getPos()))));

        for (ModelBlockEntity modelBlock : modelBlocks) {
            double distance = Math.sqrt(playerPos.squaredDistanceTo(Vec3d.ofCenter(modelBlock.getPos())));
            String distanceLabel = UIKeys.SCENE_REPLAYS_CONTEXT_FROM_MODEL_BLOCK_DISTANCE.format(String.format("%.1f", distance)).get();

            list.add(modelBlock.getName() + " (" + distanceLabel + ")");
        }

        list.background();
        search.relative(panel.confirm).y(-5).w(1F).h(16 * 9 + 20).anchor(0F, 1F);

        panel.confirm.w(1F, -10);
        panel.content.add(search);

        UIOverlay.addOverlay(this.resolveUiContext(), panel, 300, 300);
    }

    private void fromModelBlock(ModelBlockEntity modelBlock) {
        Film film = this.panel.getData();
        Replay replay = film.replays.addReplay();
        BlockPos blockPos = modelBlock.getPos();
        ModelProperties properties = modelBlock.getProperties();
        Transform transform = properties.getTransform().copy();
        double x = blockPos.getX() + transform.translate.x + 0.5D;
        double y = blockPos.getY() + transform.translate.y;
        double z = blockPos.getZ() + transform.translate.z + 0.5D;

        transform.translate.set(0, 0, 0);

        replay.shadow.set(properties.isShadow());
        replay.form.set(FormUtils.copy(properties.getForm()));
        replay.keyframes.x.insert(0, x);
        replay.keyframes.y.insert(0, y);
        replay.keyframes.z.insert(0, z);
        replay.keyframes.mainHand.insert(0, this.copyItem(properties.getItemMainHand()));
        replay.keyframes.offHand.insert(0, this.copyItem(properties.getItemOffHand()));
        replay.keyframes.armorHead.insert(0, this.copyItem(properties.getArmorHead()));
        replay.keyframes.armorChest.insert(0, this.copyItem(properties.getArmorChest()));
        replay.keyframes.armorLegs.insert(0, this.copyItem(properties.getArmorLegs()));
        replay.keyframes.armorFeet.insert(0, this.copyItem(properties.getArmorFeet()));

        if (!transform.isDefault()) {
            if (transform.rotate.x == 0 && transform.rotate.z == 0 &&
                    transform.rotate2.x == 0 && transform.rotate2.y == 0 && transform.rotate2.z == 0 &&
                    transform.scale.x == 1 && transform.scale.y == 1 && transform.scale.z == 1) {
                double yaw = -Math.toDegrees(transform.rotate.y);

                replay.keyframes.yaw.insert(0, yaw);
                replay.keyframes.headYaw.insert(0, yaw);
                replay.keyframes.bodyYaw.insert(0, yaw);
            } else {
                AnchorForm form = new AnchorForm();
                BodyPart part = new BodyPart("");

                part.setForm(replay.form.get());
                form.transform.set(transform);
                form.parts.addBodyPart(part);

                replay.form.set(form);
            }
        }

        this.buildVisualList();
        this.setCurrentDirect(replay);
        this.panel.replayEditor.setReplay(replay);
        this.updateFilmEditor();
    }

    private ItemStack copyItem(ItemStack stack) {
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public void addReplay(Vector3d position, float pitch, float yaw) {
        Film film = this.panel.getData();
        Replay replay = film.replays.addReplay();

        replay.keyframes.x.insert(0, position.x);
        replay.keyframes.y.insert(0, position.y);
        replay.keyframes.z.insert(0, position.z);

        replay.keyframes.pitch.insert(0, (double) pitch);
        replay.keyframes.yaw.insert(0, (double) yaw);
        replay.keyframes.headYaw.insert(0, (double) yaw);
        replay.keyframes.bodyYaw.insert(0, (double) yaw);

        this.buildVisualList();
        this.setCurrentDirect(replay);
        this.panel.replayEditor.setReplay(replay);
        this.updateFilmEditor();

        this.openFormEditor(replay.form, false, null);
    }

    private void updateFilmEditor() {
        this.panel.getController().createEntitiesNow();
        this.panel.replayEditor.updateChannelsList();
    }

    public void importFromModelBlock(ModelBlockEntity entity)
    {
        Film film = this.panel.getData();

        if (film == null || entity == null)
        {
            return;
        }

        Form form = entity.getProperties().getForm();

        if (form == null)
        {
            return;
        }

        Replay replay = film.replays.addReplay();

        replay.form.set(FormUtils.copy(form));
        this.finishImport(replay);
    }

    public void finishImport(Replay replay)
    {
        if (replay == null)
        {
            return;
        }

        this.setCurrentDirect(replay);
        this.panel.replayEditor.setReplay(replay);
        this.updateFilmEditor();
    }

    public void dupeReplay() {
        if (this.isDeselected()) {
            return;
        }

        Film film = this.panel.getData();
        Map<String, String> idMap = new HashMap<>();
        List<Replay> created = new ArrayList<>();
        Replay last = null;

        for (Replay replay : this.getCurrent()) {
            Replay newReplay = film.replays.addReplay();
            String oldId = replay.getId();

            newReplay.copy(replay);
            newReplay.uuid.set(UUID.randomUUID().toString());
            idMap.put(oldId, newReplay.getId());
            created.add(newReplay);
            last = newReplay;
        }

        AttackActionClip.remapTargets(created, idMap);

        if (last != null) {
            this.buildVisualList();
            this.setCurrentDirect(last);
            this.panel.replayEditor.setReplay(last);
            this.updateFilmEditor();
        }
    }

    private void applyRandomSkins() {
        if (this.isDeselected()) {
            return;
        }

        List<Replay> selectedReplays = this.getCurrent();
        String defaultModel = "";

        if (!selectedReplays.isEmpty()) {
            ValueForm formValue = selectedReplays.get(0).form;
            if (formValue != null && formValue.get() instanceof ModelForm) {
                defaultModel = ((ModelForm) formValue.get()).model.get();
            }
        }

        String[] steveModel = new String[] {
                LAST_RANDOM_SKINS_STEVE_MODEL.isEmpty() ? defaultModel : LAST_RANDOM_SKINS_STEVE_MODEL };
        String[] alexModel = new String[] {
                LAST_RANDOM_SKINS_ALEX_MODEL.isEmpty() ? "player/alex" : LAST_RANDOM_SKINS_ALEX_MODEL };
        List<File> selectedFolders = new ArrayList<>();

        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
                UIKeys.SCENE_REPLAYS_CONTEXT_RANDOM_SKINS_TITLE,
                UIKeys.SCENE_REPLAYS_CONTEXT_RANDOM_SKINS_DESCRIPTION,
                (b) -> {
                    if (b) {
                        if (selectedFolders.isEmpty()) {
                            UIOverlay.addOverlay(this.resolveUiContext(),
                                    new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR,
                                            UIKeys.SCENE_REPLAYS_CONTEXT_RANDOM_SKINS_FOLDER_REQUIRED));
                            return;
                        }

                        LAST_RANDOM_SKINS_STEVE_MODEL = steveModel[0] == null ? "" : steveModel[0];
                        LAST_RANDOM_SKINS_ALEX_MODEL = alexModel[0] == null ? "" : alexModel[0];

                        this.processRandomSkins(selectedFolders, LAST_RANDOM_SKINS_STEVE_MODEL,
                                LAST_RANDOM_SKINS_ALEX_MODEL);
                    }
                });

        UILabel folderCount = UI.label(UIKeys.FILM_REPLAY_SELECTED.format(0)).background();
        UIStringList folderList = new UIStringList((l) -> {
        });
        folderList.background().h(60);
        folderList.add("<none>");
        final UIButton[] removeFolder = new UIButton[1];
        removeFolder[0] = new UIButton(UIKeys.GENERAL_REMOVE, (b) -> {
            List<Integer> indices = new ArrayList<>(folderList.getCurrentIndices());

            if (indices.isEmpty()) {
                return;
            }

            Collections.sort(indices);

            for (int i = indices.size() - 1; i >= 0; i--) {
                int index = indices.get(i);

                if (index >= 0 && index < selectedFolders.size()) {
                    selectedFolders.remove(index);
                }
            }

            this.updateRandomSkinsFolderList(folderList, folderCount, removeFolder[0], selectedFolders);
        });
        removeFolder[0].setEnabled(false);
        UIButton pickFolder = new UIButton(UIKeys.SCENE_REPLAYS_CONTEXT_RANDOM_SKINS_PICK_FOLDER, (b) -> {
            UIFolderPickerOverlayPanel picker = new UIFolderPickerOverlayPanel(
                    UIKeys.SCENE_REPLAYS_CONTEXT_RANDOM_SKINS_PICK_FOLDER_TITLE,
                    UIKeys.SCENE_REPLAYS_CONTEXT_RANDOM_SKINS_PICK_FOLDER_DESCRIPTION,
                    (folder) -> {
                        if (folder != null && !selectedFolders.contains(folder)) {
                            selectedFolders.add(folder);
                            this.updateRandomSkinsFolderList(folderList, folderCount, removeFolder[0], selectedFolders);
                        }
                    });

            UIOverlay.addOverlay(this.resolveUiContext(), picker);
        });

        final UIButton[] stevePick = new UIButton[1];
        final UIButton[] alexPick = new UIButton[1];

        stevePick[0] = new UIButton(this.getModelLabel(steveModel[0]), (b) -> {
            this.openModelPicker(steveModel[0], (value) -> {
                steveModel[0] = value;
                stevePick[0].label = this.getModelLabel(value);
            });
        });

        alexPick[0] = new UIButton(this.getModelLabel(alexModel[0]), (b) -> {
            this.openModelPicker(alexModel[0], (value) -> {
                alexModel[0] = value;
                alexPick[0].label = this.getModelLabel(value);
            });
        });

        UIElement controls = UI.column(6,
                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_RANDOM_SKINS_FOLDER),
                folderCount,
                folderList,
                UI.row(4, pickFolder, removeFolder[0]),
                UI.row(4,
                        UI.column(2,
                                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_RANDOM_SKINS_MODEL_STEVE).marginTop(8),
                                stevePick[0]),
                        UI.column(2,
                                UI.label(UIKeys.SCENE_REPLAYS_CONTEXT_RANDOM_SKINS_MODEL_ALEX).marginTop(8),
                                alexPick[0])));

        controls.relative(panel.content).x(6).y(70).w(1F, -12).h(1F, -32);
        panel.content.add(controls);

        panel.content.remove(panel.confirm);
        panel.content.add(panel.confirm);

        panel.confirm.label = UIKeys.GENERAL_CONFIRM;
        panel.confirm.relative(panel.content).x(5).y(1F, -26).w(1F, -10).h(20).anchor(0F, 0F);

        UIOverlay.addOverlay(this.resolveUiContext(), panel, 300, 300);
    }

    private void processRandomSkins(List<File> skinsFolders, String steveModel, String alexModel) {
        if (skinsFolders == null || skinsFolders.isEmpty()) {
            return;
        }

        // Get all PNG files from the folders
        List<File> skinFiles = new ArrayList<>();
        for (File skinsFolder : skinsFolders) {
            if (skinsFolder == null || !skinsFolder.exists() || !skinsFolder.isDirectory()) {
                UIOverlay.addOverlay(this.resolveUiContext(),
                        new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR,
                                UIKeys.FILM_REPLAY_ERROR_NOT_DIRECTORY));
                return;
            }

            File[] files = skinsFolder.listFiles();

            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().toLowerCase().endsWith(".png")) {
                        skinFiles.add(file);
                    }
                }
            }
        }

        if (skinFiles.isEmpty()) {
            UIOverlay.addOverlay(this.resolveUiContext(),
                    new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR,
                            UIKeys.FILM_REPLAY_ERROR_NO_PNG));
            return;
        }

        /* Shuffle the skins for random assignment */
        Collections.shuffle(skinFiles);

        /* Get selected replays */
        List<Replay> selectedReplays = this.getCurrent();

        if (selectedReplays.isEmpty()) {
            return;
        }

        /* Apply skins to replays */
        Map<File, SkinType> skinTypeCache = new HashMap<>();
        int skinIndex = 0;
        int successCount = 0;

        for (Replay replay : selectedReplays) {
            File skinFile = skinFiles.get(skinIndex % skinFiles.size());
            SkinType skinType = skinTypeCache.computeIfAbsent(skinFile, this::getSkinType);
            boolean slim = skinType == SkinType.ALEX;
            String targetModel = slim ? alexModel : steveModel;

            System.out.println("[RandomSkins] file=" + skinFile.getName()
                    + " type=" + skinType
                    + " model=" + (targetModel == null || targetModel.isEmpty() ? "<none>" : targetModel));

            /* Create a Link using the AssetProvider */
            Link skinLink = BBSMod.getProvider().getLink(skinFile);

            if (skinLink == null) {
                /* If the file is not in the assets folder, skip it */
                skinIndex++;
                continue;
            }

            /* Get the form and set the texture */
            ValueForm formValue = replay.form;
            if (formValue != null && formValue.get() != null) {
                Form form = formValue.get();

                if (form instanceof MobForm) {
                    ((MobForm) form).texture.set(skinLink);
                    ((MobForm) form).slim.set(slim);
                    successCount++;
                } else if (form instanceof ModelForm) {
                    ((ModelForm) form).texture.set(skinLink);
                    if (targetModel != null && !targetModel.isEmpty()) {
                        ((ModelForm) form).model.set(targetModel);
                    }
                    successCount++;
                }
            }

            skinIndex++;
        }

        /* Update UI */
        this.update();
        this.updateFilmEditor();

        if (successCount == 0) {
            UIOverlay.addOverlay(this.resolveUiContext(),
                    new UIMessageOverlayPanel(UIKeys.GENERAL_ERROR,
                            UIKeys.FILM_REPLAY_ERROR_SKINS_FOLDER_ASSETS));
        }
    }

    private IKey getModelLabel(String model) {
        if (model == null || model.isEmpty()) {
            return UIKeys.GENERAL_NONE;
        }

        return IKey.constant(model);
    }

    private void updateRandomSkinsFolderList(UIStringList folderList, UILabel folderCount, UIButton removeButton,
            List<File> folders) {
        folderList.clear();

        if (folders == null || folders.isEmpty()) {
            folderCount.label = UIKeys.FILM_REPLAY_SELECTED.format(0);
            folderList.add("<none>");
            removeButton.setEnabled(false);
            return;
        }

        folderCount.label = UIKeys.FILM_REPLAY_SELECTED.format(folders.size());
        removeButton.setEnabled(true);

        for (File folder : folders) {
            String label = folder == null ? "" : folder.getName();
            folderList.add(label);
        }
    }

    private void openModelPicker(String current, Consumer<String> callback) {
        UIListOverlayPanel list = new UIListOverlayPanel(UIKeys.FORMS_EDITOR_MODEL_MODELS, (l) -> {
            callback.accept(l);
        });

        list.addValues(BBSModClient.getModels().getAvailableKeys());
        list.list.list.sort();
        list.setValue(current);

        UIOverlay.addOverlay(this.resolveUiContext(), list);
    }

    private SkinType getSkinType(File skinFile) {
        try (FileInputStream stream = new FileInputStream(skinFile)) {
            Pixels pixels = Pixels.fromPNGStream(stream);
            SkinType skinType = this.getSkinType(pixels);

            if (skinType == SkinType.STEVE) {
                SkinType nameType = this.getSkinTypeFromName(skinFile);

                if (nameType == SkinType.ALEX) {
                    skinType = nameType;
                }
            }
            if (pixels != null) {
                pixels.delete();
            }

            return skinType;
        } catch (Exception e) {
            return this.getSkinTypeFromName(skinFile);
        }
    }

    private SkinType getSkinTypeFromName(File skinFile) {
        if (skinFile == null) {
            return SkinType.STEVE;
        }

        String name = skinFile.getName().toLowerCase();

        if (name.contains("alex") || name.contains("slim")) {
            return SkinType.ALEX;
        }

        return SkinType.STEVE;
    }

    private SkinType getSkinType(Pixels pixels) {
        if (pixels == null || pixels.width < 64 || pixels.height < 64) {
            return SkinType.STEVE;
        }

        if (pixels.width == 64 && pixels.height == 64) {
            return this.isAlexSkin(pixels) ? SkinType.ALEX : SkinType.STEVE;
        }

        if (pixels.width % 64 != 0 || pixels.height % 64 != 0) {
            return SkinType.STEVE;
        }

        int scaleX = pixels.width / 64;
        int scaleY = pixels.height / 64;

        if (scaleX <= 0 || scaleY <= 0) {
            return SkinType.STEVE;
        }

        Boolean armWidthSlim = this.isSlimByArmWidth(pixels, scaleX, scaleY);

        if (armWidthSlim != null) {
            return armWidthSlim.booleanValue() ? SkinType.ALEX : SkinType.STEVE;
        }

        boolean slim = this.isAreaFullyTransparent(pixels, 54 * scaleX, 20 * scaleY, 2 * scaleX, 12 * scaleY)
                && this.isAreaFullyTransparent(pixels, 54 * scaleX, 36 * scaleY, 2 * scaleX, 12 * scaleY)
                && this.isAreaFullyTransparent(pixels, 46 * scaleX, 52 * scaleY, 2 * scaleX, 12 * scaleY)
                && this.isAreaFullyTransparent(pixels, 50 * scaleX, 52 * scaleY, 2 * scaleX, 12 * scaleY);

        return slim ? SkinType.ALEX : SkinType.STEVE;
    }

    private boolean isAlexSkin(Pixels pixels) {
        int x = 54;

        for (int y = 20; y < 32; y++) {
            if (pixels.getColor(x, y).a > 0F) {
                return false;
            }
        }

        return true;
    }

    private enum SkinType {
        STEVE,
        ALEX
    }

    private Boolean isSlimByArmWidth(Pixels pixels, int scaleX, int scaleY) {
        int x = 47 * scaleX;
        int y = 20 * scaleY;
        int h = 12 * scaleY;
        int w = Math.max(scaleX, 1);
        int total = w * h;
        int opaque = this.countOpaquePixels(pixels, x, y, w, h);

        if (opaque <= total * 0.1F) {
            return true;
        }
        if (opaque >= total * 0.9F) {
            return false;
        }

        return null;
    }

    private boolean hasAnyOpaquePixel(Pixels pixels, int x, int y, int w, int h) {
        int maxX = Math.min(x + w, pixels.width);
        int maxY = Math.min(y + h, pixels.height);

        for (int px = x; px < maxX; px++) {
            for (int py = y; py < maxY; py++) {
                if (pixels.getColor(px, py).a > 0F) {
                    return true;
                }
            }
        }

        return false;
    }

    private int countOpaquePixels(Pixels pixels, int x, int y, int w, int h) {
        int maxX = Math.min(x + w, pixels.width);
        int maxY = Math.min(y + h, pixels.height);
        int count = 0;

        for (int px = x; px < maxX; px++) {
            for (int py = y; py < maxY; py++) {
                if (pixels.getColor(px, py).a > 0F) {
                    count++;
                }
            }
        }

        return count;
    }

    private boolean isAreaFullyTransparent(Pixels pixels, int x, int y, int w, int h) {
        int maxX = Math.min(x + w, pixels.width);
        int maxY = Math.min(y + h, pixels.height);

        for (int px = x; px < maxX; px++) {
            for (int py = y; py < maxY; py++) {
                if (pixels.getColor(px, py).a > 0F) {
                    return false;
                }
            }
        }

        return true;
    }

    public void removeReplay() {
        if (this.isDeselected()) {
            return;
        }

        Film film = this.panel.getData();
        int index = this.getIndex();
        List<Replay> selected = new ArrayList<>(this.getCurrent());

        BaseValue.edit(film.replays, (replays) -> {
            List<Replay> allReplays = film.replays.getAllTyped();

            for (Replay replay : selected) {
                if (replay.isGroup.get()) {
                    this.reparentChildren(replay, allReplays);
                }

                allReplays.remove(replay);
            }

            film.replays.sync();
        });

        int size = this.list.size();
        index = MathUtils.clamp(index, 0, size - 1);

        this.buildVisualList();
        size = this.list.size();
        this.panel.replayEditor.setReplay(size == 0 ? null : CollectionUtils.getSafe(this.list, index));
        this.updateFilmEditor();
    }

    private void ungroupReplay() {
        if (this.isDeselected()) {
            return;
        }

        Film film = this.panel.getData();
        int index = this.getIndex();
        List<Replay> selected = new ArrayList<>(this.getCurrent());

        BaseValue.edit(film.replays, (replays) -> {
            List<Replay> allReplays = film.replays.getAllTyped();

            for (Replay replay : selected) {
                if (!replay.isGroup.get()) {
                    continue;
                }

                this.reparentChildren(replay, allReplays);
                allReplays.remove(replay);
            }

            film.replays.sync();
        });

        int size = this.list.size();
        index = MathUtils.clamp(index, 0, size - 1);

        this.buildVisualList();
        size = this.list.size();
        this.panel.replayEditor.setReplay(size == 0 ? null : CollectionUtils.getSafe(this.list, index));
        this.updateFilmEditor();
    }

    private void leaveGroup() {
        if (this.isDeselected()) {
            return;
        }

        Film film = this.panel.getData();
        int index = this.getIndex();
        List<Replay> selected = new ArrayList<>(this.getCurrent());

        BaseValue.edit(film.replays, (replays) -> {
            for (Replay replay : selected) {
                if (replay.isGroup.get()) {
                    continue;
                }

                String currentPath = replay.group.get();

                if (currentPath.isEmpty()) {
                    continue;
                }

                replay.group.set(this.getParentGroupPath(currentPath));
            }

            film.replays.sync();
        });

        int size = this.list.size();
        index = MathUtils.clamp(index, 0, size - 1);

        this.buildVisualList();
        size = this.list.size();
        this.panel.replayEditor.setReplay(size == 0 ? null : CollectionUtils.getSafe(this.list, index));
        this.updateFilmEditor();
    }

    private String getParentGroupPath(String path) {
        int index = path.lastIndexOf("/");

        if (index < 0) {
            return "";
        }

        return path.substring(0, index);
    }

    private void reparentChildren(Replay groupToDelete, List<Replay> allReplays) {
        String targetPath = getReplayPath(groupToDelete);
        String targetID = groupToDelete.uuid.get();
        String childPrefix = targetPath.isEmpty() ? targetID : targetPath + "/" + targetID;
        String newParentPath = targetPath;

        for (Replay r : allReplays) {
            if (r == groupToDelete)
                continue;

            String g = r.group.get();

            if (g.equals(childPrefix) || g.startsWith(childPrefix + "/")) {
                String suffix = g.substring(childPrefix.length());
                String newPath;

                if (newParentPath.isEmpty()) {
                    newPath = suffix.startsWith("/") ? suffix.substring(1) : suffix;
                } else {
                    newPath = newParentPath + suffix;
                }

                r.group.set(newPath);
            }
        }
    }

    @Override
    public void render(UIContext context) {
        if (this.panel != null && this.panel.getData() != null) {
            this.buildVisualList();
        }

        super.render(context);
    }

    @Override
    protected String elementToString(UIContext context, int i, Replay element) {
        String label = element.label.get();
        String name = label;

        if (name == null || name.isEmpty())
        {
            Form form = element.form.get();
            String displayName = form == null ? "" : form.name.get();

            if (displayName == null || displayName.isEmpty())
            {
                name = form == null ? element.getId() : form.getDisplayName();
            }
            else
            {
                name = displayName;
            }
        }

        if (element.isGroup.get())
        {
            int limit = BBSSettings.editorReplayEditorTitleLimit == null ? 0 : BBSSettings.editorReplayEditorTitleLimit.get();

            if (limit > 0 && name.length() > limit)
            {
                name = name.substring(0, limit) + "...";
            }
        }

        return context.batcher.getFont().limitToWidth(name, this.area.w - 20);
    }

    @Override
    protected void renderElementPart(UIContext context, Replay element, int i, int x, int y, boolean hover,
            boolean selected) {
        int depth = getReplayDepth(element);
        int indent = depth * 10;
        int textX = x + indent;

        if (element.isGroup.get()) {
            String path = getReplayPath(element);
            String myPath = path.isEmpty() ? element.uuid.get() : path + "/" + element.uuid.get();
            boolean expanded = this.expandedGroups.getOrDefault(myPath, true);
            Icon icon = expanded ? Icons.ARROW_DOWN : Icons.ARROW_RIGHT;
            int folderX = textX + 8;
            int toggleX = x + this.area.w - 20;

            context.batcher.icon(Icons.FOLDER, folderX, y + 2);
            context.batcher.icon(icon, toggleX, y + 2);
            textX = folderX + 16;
        }

        if (this.isReplayListItemActive(element))
        {
            super.renderElementPart(context, element, i, textX, y, hover, selected);
        }
        else
        {
            context.batcher.textShadow(this.elementToString(context, i, element), textX + 4,
                    y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2,
                    hover ? Colors.mulRGB(Colors.HIGHLIGHT, 0.75F) : Colors.GRAY);
        }

        Form form = element.form.get();

        if (form != null) {
            x += this.area.w - 30;

            context.batcher.clip(x, y, 40, 20, context);

            y -= 10;

            RenderSystem.setupLevelDiffuseLighting(UIReplayList.LIGHT_A, UIReplayList.LIGHT_B);
            FormUtilsClient.renderUI(form, context, x, y, x + 40, y + 40);
            DiffuseLighting.disableGuiDepthLighting();

            context.batcher.unclip(context);

            if (element.fp.get()) {
                context.batcher.outlinedIcon(Icons.ARROW_UP, x, y + 20, 0.5F, 0.5F);
            }
        }
    }

    private boolean isReplayListItemActive(Replay replay)
    {
        UIFilmController controller = this.panel.getController();
        FilmEditorController editor = controller == null ? null : controller.editorController;

        if (editor != null)
        {
            int tick = replay.getTick(this.panel.getCursor());

            return editor.isReplayVisible(replay, tick);
        }

        return replay.enabled.get();
    }

    private void addGroup() {
        Film film = this.panel.getData();
        Replay group = new Replay("replay");

        group.uuid.set(UUID.randomUUID().toString());
        group.isGroup.set(true);
        group.label.set("New Group");

        List<Replay> selected = this.getCurrent();

        if (!selected.isEmpty()) {
            List<Replay> list = film.replays.getAllTyped();
            Replay first = selected.get(0);

            int insertionIndex = list.size();

            for (Replay r : selected) {
                int index = list.indexOf(r);

                if (index != -1 && index < insertionIndex) {
                    insertionIndex = index;
                }
            }

            String parentPath = first.group.get();

            group.group.set(parentPath);

            String newGroupPath = parentPath.isEmpty() ? group.uuid.get() : parentPath + "/" + group.uuid.get();

            list.removeAll(selected);

            for (Replay r : selected) {
                r.group.set(newGroupPath);
            }

            if (insertionIndex > list.size()) {
                insertionIndex = list.size();
            }

            list.add(insertionIndex, group);
            list.addAll(insertionIndex + 1, selected);

            this.expandedGroups.put(newGroupPath, true);
        } else {
            film.replays.add(group);
        }

        film.replays.sync();

        this.buildVisualList();
        this.updateFilmEditor();
    }

    public void buildVisualList() {
        if (this.panel == null || this.panel.getData() == null)
            return;

        List<Replay> selected = new ArrayList<>();

        if (this.list != null && !this.list.isEmpty()) {
            selected = this.getCurrent();
        }

        List<Replay> all = this.panel.getData().replays.getList();

        this.visualList.clear();

        for (Replay r : all) {
            String path = getReplayPath(r);

            if (path.isEmpty() || isPathExpanded(path)) {
                this.visualList.add(r);
            }
        }

        this.setList(this.visualList);
        this.current.clear();

        for (Replay r : selected) {
            int index = this.visualList.indexOf(r);

            if (index != -1) {
                this.current.add(index);
            }
        }
    }

    private boolean isPathExpanded(String path) {
        String[] parts = path.split("/");
        String current = "";

        for (String part : parts) {
            current = current.isEmpty() ? part : current + "/" + part;

            if (!this.expandedGroups.getOrDefault(current, true)) {
                return false;
            }
        }

        return true;
    }

    public void updateGroupPath(String oldFullPath, String newFullPath) {
        Film film = this.panel.getData();
        List<Replay> all = film.replays.getList();
        boolean changed = false;

        if (this.expandedGroups.containsKey(oldFullPath)) {
            this.expandedGroups.put(newFullPath, this.expandedGroups.remove(oldFullPath));
        }

        for (Replay r : all) {
            String group = r.group.get();

            if (group.equals(oldFullPath) || group.startsWith(oldFullPath + "/")) {
                String suffix = group.substring(oldFullPath.length());
                r.group.set(newFullPath + suffix);
                changed = true;
            }
        }

        if (changed) {
            film.replays.sync();
            this.buildVisualList();
            this.updateFilmEditor();
        }
    }

    public String getReplayPath(Replay r) {
        return r.group.get();
    }

    private int getReplayDepth(Replay r) {
        String path = getReplayPath(r);
        return path.isEmpty() ? 0 : path.split("/").length;
    }

    public void ensureVisible(Replay replay) {
        if (replay == null) {
            return;
        }

        String path = getReplayPath(replay);
        boolean changed = false;

        if (!path.isEmpty()) {
            String[] parts = path.split("/");
            String current = "";

            for (String part : parts) {
                current = current.isEmpty() ? part : current + "/" + part;

                if (!this.expandedGroups.getOrDefault(current, true)) {
                    this.expandedGroups.put(current, true);
                    changed = true;
                }
            }
        }

        if (changed) {
            this.buildVisualList();
        }
    }

    @Override
    public boolean subMouseClicked(UIContext context) {
        if (context.mouseButton == 0) {
            int index = this.scroll.getIndex(context.mouseX, context.mouseY);

            if (this.exists(index)) {
                Replay r = this.list.get(index);
                int depth = getReplayDepth(r);
                int indent = depth * 10;
                int folderX = this.area.x + indent + 8;
                int toggleX = this.area.x + this.area.w - 24;
                boolean clickedFolderIcon = context.mouseX >= folderX && context.mouseX < folderX + 12;
                boolean clickedToggleIcon = context.mouseX >= toggleX && context.mouseX < toggleX + 16;

                if (r.isGroup.get() && (clickedFolderIcon || clickedToggleIcon)) {
                    String path = getReplayPath(r);
                    String myPath = path.isEmpty() ? r.uuid.get() : path + "/" + r.uuid.get();

                    boolean expanded = this.expandedGroups.getOrDefault(myPath, true);

                    this.expandedGroups.put(myPath, !expanded);
                    this.buildVisualList();

                    return true;
                }
            }
        }

        return super.subMouseClicked(context);
    }

}
