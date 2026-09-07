package mchorse.bbs_mod.ui.utils.presets;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.replays.UIReplayPresetPreview;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class UIPresetsGridOverlayPanel extends UIOverlayPanel
{
    private static final String FOLDER_TOKEN_PREFIX = "__folder__:";
    private static final int GRID_PRESET_VERY_LARGE = 0;
    private static final int GRID_PRESET_LARGE = 1;
    private static final int GRID_PRESET_MEDIUM = 2;
    private static final int GRID_PRESET_SMALL = 3;
    private static final int MAX_COLUMNS = 6;
    private static final int MAX_ROWS = 6;
    private static final int MAX_PAGE_SIZE = MAX_COLUMNS * MAX_ROWS;

    private final UICopyPasteController controller;
    private final int mouseX;
    private final int mouseY;

    private final UITextbox search;
    private final PresetCell[] cells = new PresetCell[MAX_PAGE_SIZE];

    private final List<PresetEntry> allEntries = new ArrayList<>();
    private final List<PresetEntry> filteredEntries = new ArrayList<>();

    private String currentFolder = "";
    private String selectedPreset = "";
    private int page;

    private final UIIcon up;
    private final UIIcon prev;
    private final UIIcon next;
    private final UIIcon trackersToggle;
    private final UIIcon cellSize;
    private boolean needsInitialRefresh = true;
    private int lastContentWidth = -1;
    private int lastContentHeight = -1;
    private static final int TRACKER_POSE = 1;
    private static final int TRACKER_TRANSFORM = 2;
    private static final int TRACKER_POSE_TO_LIMBS = 4;

    public UIPresetsGridOverlayPanel(UICopyPasteController controller, int mouseX, int mouseY)
    {
        super(UIKeys.PRESETS_TITLE);

        this.controller = controller;
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        this.search = new UITextbox(this::setFilter).placeholder(UIKeys.GENERAL_SEARCH);
        this.search.relative(this.content).xy(6, 6).w(1F, -12).h(20);
        this.content.add(this.search);

        for (int i = 0; i < this.cells.length; i++)
        {
            PresetCell cell = new PresetCell(this::onCellClick, this.createPreviewForCell());
            cell.relative(this.content);
            this.cells[i] = cell;
            this.content.add(cell);
        }

        UIIcon save = new UIIcon(Icons.SAVED, (b) -> this.savePreset());
        save.setEnabled(controller.canCopy());
        save.tooltip(UIKeys.PRESETS_SAVE, Direction.LEFT);

        UIIcon folder = new UIIcon(Icons.FOLDER, (b) -> UIUtils.openFolder(controller.manager.getFolder()));
        folder.tooltip(UIKeys.PRESETS_OPEN, Direction.LEFT);
        this.trackersToggle = new UIIcon(() -> this.areTrackerIndicatorsVisible() ? Icons.VISIBLE : Icons.CROSSED_OUT_EYE, (b) ->
        {
            BBSSettings.presetsGridTrackers.set(!this.areTrackerIndicatorsVisible());
            this.updateToolbarStates();
            this.refreshGrid();
        });
        this.trackersToggle.tooltip(UIKeys.PRESETS_GRID_TRACKERS, Direction.LEFT);
        this.cellSize = new UIIcon(Icons.SCALE, (b) -> this.openCellSizeMenu());
        this.cellSize.tooltip(UIKeys.PRESETS_GRID_CELL_SIZE, Direction.LEFT);
        this.up = new UIIcon(Icons.ARROW_UP, (b) -> this.goUpFolder());
        this.up.tooltip(UIKeys.PRESETS_FOLDER_UP, Direction.LEFT);

        this.next = new UIIcon(Icons.ARROW_RIGHT, (b) ->
        {
            if (this.canNext())
            {
                this.page += 1;
                this.refreshGrid();
            }
        });
        this.next.tooltip(UIKeys.PRESETS_PAGE_NEXT, Direction.LEFT);

        this.prev = new UIIcon(Icons.ARROW_LEFT, (b) ->
        {
            if (this.canPrev())
            {
                this.page -= 1;
                this.refreshGrid();
            }
        });
        this.prev.tooltip(UIKeys.PRESETS_PAGE_PREVIOUS, Direction.LEFT);

        this.icons.add(save, folder, this.trackersToggle, this.cellSize, this.up, this.prev, this.next);

        this.updateToolbarStates();
        this.reloadPresets();
    }

    private UICopyPasteController.IPresetPreview createPreviewForCell()
    {
        UICopyPasteController.IPresetPreview preview = this.controller.getPreview();

        UICopyPasteController.IPresetPreview fork = preview == null ? null : preview.fork();

        if (fork instanceof UIReplayPresetPreview replayPreview)
        {
            replayPreview.setPreviewDistance(13);
        }

        return fork;
    }

    private void savePreset()
    {
        MapType type = this.controller.getSupplier().get();

        if (type == null)
        {
            return;
        }

        UIPromptOverlayPanel pane = new UIPromptOverlayPanel(UIKeys.PRESETS_SAVE_TITLE, UIKeys.PRESETS_SAVE_DESCRIPTION, (t) ->
        {
            this.controller.manager.save(this.joinPath(this.currentFolder, t), type);
            this.reloadPresets();
        });

        pane.text.filename();
        UIOverlay.addOverlay(this.getContext(), pane);
    }

    private void onCellClick(String presetId)
    {
        if (presetId == null || presetId.isEmpty())
        {
            return;
        }

        if (this.isFolderToken(presetId))
        {
            this.enterFolder(this.folderPathFromToken(presetId));

            return;
        }

        if (!presetId.equals(this.selectedPreset))
        {
            this.selectedPreset = presetId;
            this.refreshSelection();

            return;
        }

        MapType load = this.controller.manager.load(presetId);

        if (load == null)
        {
            return;
        }

        this.controller.getConsumer().paste(load, this.mouseX, this.mouseY);
        this.close();
    }

    private void reloadPresets()
    {
        this.allEntries.clear();

        for (String folderName : this.controller.manager.getFolders(this.currentFolder))
        {
            String folderPath = this.joinPath(this.currentFolder, folderName);
            this.allEntries.add(PresetEntry.folder(folderPath, folderName));
        }

        for (String presetId : this.controller.manager.getKeys(this.currentFolder))
        {
            this.allEntries.add(PresetEntry.preset(presetId, this.baseName(presetId)));
        }

        this.updateTitle();
        this.setFilter(this.search.textbox.getText());
    }

    private void setFilter(String query)
    {
        String filter = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        this.filteredEntries.clear();

        if (filter.isEmpty())
        {
            this.filteredEntries.addAll(this.allEntries);
        }
        else
        {
            String prefix = this.currentFolder.isEmpty() ? "" : this.currentFolder + "/";

            for (String presetId : this.controller.manager.getKeys())
            {
                if (!prefix.isEmpty() && !presetId.startsWith(prefix))
                {
                    continue;
                }

                String relativePath = prefix.isEmpty() ? presetId : presetId.substring(prefix.length());
                String basename = this.baseName(relativePath);
                String loweredPath = relativePath.toLowerCase(Locale.ROOT);
                String loweredName = basename.toLowerCase(Locale.ROOT);

                if (loweredPath.contains(filter) || loweredName.contains(filter))
                {
                    this.filteredEntries.add(PresetEntry.preset(presetId, relativePath));
                }
            }
        }

        this.page = 0;
        this.selectedPreset = "";
        this.refreshGrid();
    }

    private void refreshGrid()
    {
        int pageSize = this.getCurrentPageSize();
        int maxPage = this.filteredEntries.isEmpty() ? 0 : (this.filteredEntries.size() - 1) / pageSize;

        this.page = Math.max(0, Math.min(this.page, maxPage));
        this.layoutCells();
        /* Apply updated flex values immediately, otherwise cells stay at stale areas
           until an external resize happens (e.g. moving panel). */
        this.content.resize();

        int start = this.page * pageSize;

        for (int i = 0; i < this.cells.length; i++)
        {
            int index = start + i;
            PresetCell cell = this.cells[i];

            if (i < pageSize && index >= 0 && index < this.filteredEntries.size())
            {
                PresetEntry entry = this.filteredEntries.get(index);

                cell.setVisible(true);

                if (entry.folder)
                {
                    cell.bindFolder(this.toFolderToken(entry.path), entry.displayName);
                    cell.setSelected(false);
                }
                else
                {
                    MapType data = this.controller.manager.load(entry.path);
                    int trackerMask = this.collectTrackerMask(data);

                    cell.bindPreset(entry.path, entry.displayName, data, trackerMask);
                    cell.setSelected(entry.path.equals(this.selectedPreset));
                }
            }
            else
            {
                cell.unbind();
                cell.setVisible(false);
            }
        }

        /* One more pass after final visibility/layout settles to avoid first-page-switch misses. */
        this.content.resize();

        for (int i = 0; i < this.cells.length; i++)
        {
            PresetCell cell = this.cells[i];

            if (cell.isVisible())
            {
                cell.syncIndicators();
            }
        }

        this.prev.setEnabled(this.canPrev());
        this.next.setEnabled(this.canNext());
        this.up.setEnabled(!this.currentFolder.isEmpty());
        this.updateToolbarStates();
    }

    private int collectTrackerMask(MapType data)
    {
        if (data == null)
        {
            return 0;
        }

        int mask = 0;

        for (String key : UIKeyframes.parseKeyframes(data).keySet())
        {
            int colon = key.indexOf(':');
            String propertyPath = colon == -1 ? key : key.substring(0, colon);
            String property = StringUtils.fileName(propertyPath);
            String boneName = colon == -1 ? "" : key.substring(colon + 1);
            String propertyLower = property.toLowerCase(Locale.ROOT);
            String pathLower = propertyPath.toLowerCase(Locale.ROOT);

            boolean isPoseTracker = property.equals("pose")
                || property.startsWith("pose_overlay")
                || propertyLower.contains("pose")
                || pathLower.contains("pose");
            boolean isTransformTracker = property.equals("transform")
                || property.startsWith("transform_overlay")
                || propertyLower.contains("transform")
                || pathLower.contains("transform");

            if (isPoseTracker)
            {
                mask |= TRACKER_POSE;

                if (!boneName.isEmpty())
                {
                    mask |= TRACKER_POSE_TO_LIMBS;
                }
            }

            if (isTransformTracker)
            {
                mask |= TRACKER_TRANSFORM;
            }

            if (mask == (TRACKER_POSE | TRACKER_TRANSFORM | TRACKER_POSE_TO_LIMBS))
            {
                break;
            }
        }

        return mask;
    }

    private void refreshSelection()
    {
        for (PresetCell cell : this.cells)
        {
            cell.setSelected(cell.getPresetId().equals(this.selectedPreset));
        }
    }

    private boolean canPrev()
    {
        return this.page > 0;
    }

    private boolean canNext()
    {
        return (this.page + 1) * this.getCurrentPageSize() < this.filteredEntries.size();
    }

    private void enterFolder(String folderPath)
    {
        this.currentFolder = this.normalizePath(folderPath);
        this.selectedPreset = "";
        this.page = 0;
        this.reloadPresets();
    }

    private void goUpFolder()
    {
        if (this.currentFolder.isEmpty())
        {
            return;
        }

        int slash = this.currentFolder.lastIndexOf('/');

        this.enterFolder(slash >= 0 ? this.currentFolder.substring(0, slash) : "");
    }

    private String normalizePath(String path)
    {
        if (path == null)
        {
            return "";
        }

        String normalized = path.trim().replace('\\', '/');

        while (normalized.startsWith("/"))
        {
            normalized = normalized.substring(1);
        }

        while (normalized.endsWith("/"))
        {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private String joinPath(String folder, String name)
    {
        String base = this.normalizePath(folder);
        String leaf = this.normalizePath(name);

        if (base.isEmpty())
        {
            return leaf;
        }

        if (leaf.isEmpty())
        {
            return base;
        }

        return base + "/" + leaf;
    }

    private String baseName(String path)
    {
        String normalized = this.normalizePath(path);
        int slash = normalized.lastIndexOf('/');

        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private String toFolderToken(String folderPath)
    {
        return FOLDER_TOKEN_PREFIX + this.normalizePath(folderPath);
    }

    private boolean isFolderToken(String id)
    {
        return id.startsWith(FOLDER_TOKEN_PREFIX);
    }

    private String folderPathFromToken(String token)
    {
        return token.substring(FOLDER_TOKEN_PREFIX.length());
    }

    private void updateTitle()
    {
        String base = UIKeys.PRESETS_TITLE.get();

        if (this.currentFolder.isEmpty())
        {
            this.title.label = IKey.raw(base);

            return;
        }

        this.title.label = IKey.raw(base + " > " + this.currentFolder.replace("/", " > "));
    }

    private void layoutCells()
    {
        int padding = 6;
        int spacing = 4;
        int top = 6 + 20 + spacing;
        int columns = this.getColumnsForPreset();
        int rows = this.getRowsForPreset();
        int availableW = this.content.area.w - padding * 2;
        int availableH = this.content.area.h - top - padding;
        int safeW = Math.max(1, availableW);
        int safeH = Math.max(1, availableH);
        int cellW = Math.max(1, (safeW - (columns - 1) * spacing) / columns);
        int stretchedCellH = Math.max(1, (safeH - (rows - 1) * spacing) / rows);
        int cellH = Math.min(stretchedCellH, cellW);

        for (int i = 0; i < this.cells.length; i++)
        {
            int col = i % columns;
            int row = i / columns;
            int x = padding + col * (cellW + spacing);
            int y = top + row * (cellH + spacing);

            this.cells[i].xy(x, y).wh(cellW, cellH);
        }
    }

    private boolean areTrackerIndicatorsVisible()
    {
        return BBSSettings.presetsGridTrackers == null || BBSSettings.presetsGridTrackers.get();
    }

    private void updateToolbarStates()
    {
        if (this.trackersToggle != null)
        {
            this.trackersToggle.active(this.areTrackerIndicatorsVisible());
        }
    }

    private void openCellSizeMenu()
    {
        int currentPreset = this.getGridCellSizePreset();

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.BLOCK, UIKeys.TEXTURES_VIEW_PRESETS_VERY_LARGE, currentPreset == GRID_PRESET_VERY_LARGE, () -> this.setGridCellSizePreset(GRID_PRESET_VERY_LARGE));
            menu.action(Icons.BLOCK, UIKeys.TEXTURES_VIEW_PRESETS_LARGE, currentPreset == GRID_PRESET_LARGE, () -> this.setGridCellSizePreset(GRID_PRESET_LARGE));
            menu.action(Icons.BLOCK, UIKeys.TEXTURES_VIEW_PRESETS_MEDIUM, currentPreset == GRID_PRESET_MEDIUM, () -> this.setGridCellSizePreset(GRID_PRESET_MEDIUM));
            menu.action(Icons.BLOCK, UIKeys.TEXTURES_VIEW_PRESETS_SMALL, currentPreset == GRID_PRESET_SMALL, () -> this.setGridCellSizePreset(GRID_PRESET_SMALL));
        });
    }

    private void setGridCellSizePreset(int preset)
    {
        BBSSettings.presetsGridCellSize.set(Math.max(GRID_PRESET_VERY_LARGE, Math.min(GRID_PRESET_SMALL, preset)));
        this.page = 0;
        this.refreshGrid();
    }

    private int getGridCellSizePreset()
    {
        if (BBSSettings.presetsGridCellSize == null)
        {
            return GRID_PRESET_LARGE;
        }

        return Math.max(GRID_PRESET_VERY_LARGE, Math.min(GRID_PRESET_SMALL, BBSSettings.presetsGridCellSize.get()));
    }

    private int getColumnsForPreset()
    {
        return switch (this.getGridCellSizePreset())
        {
            case GRID_PRESET_VERY_LARGE -> 3;
            case GRID_PRESET_MEDIUM -> 5;
            case GRID_PRESET_SMALL -> 6;
            default -> 4;
        };
    }

    private int getRowsForPreset()
    {
        return switch (this.getGridCellSizePreset())
        {
            case GRID_PRESET_VERY_LARGE -> 3;
            case GRID_PRESET_MEDIUM -> 5;
            case GRID_PRESET_SMALL -> 6;
            default -> 4;
        };
    }

    private int getCurrentPageSize()
    {
        return this.getColumnsForPreset() * this.getRowsForPreset();
    }

    @Override
    public void resize()
    {
        super.resize();

        int width = this.content.area.w;
        int height = this.content.area.h;

        if (width != this.lastContentWidth || height != this.lastContentHeight)
        {
            this.lastContentWidth = width;
            this.lastContentHeight = height;
            this.refreshGrid();
        }
    }

    @Override
    protected void onAdd(UIElement parent)
    {
        super.onAdd(parent);

        this.needsInitialRefresh = true;
    }

    @Override
    public void render(UIContext context)
    {
        if (this.needsInitialRefresh && this.content.area.w > 0 && this.content.area.h > 0)
        {
            this.needsInitialRefresh = false;
            this.refreshGrid();
        }

        super.render(context);
    }

    private class PresetCell extends UIElement
    {
        private final Consumer<String> callback;
        private final UICopyPasteController.IPresetPreview preview;
        private final UIElement previewElement;

        private String presetId = "";
        private String label = "";
        private boolean folder;
        private boolean selected;
        private int trackerMask;
        private final PresetIndicator indicatorTransform;
        private final PresetIndicator indicatorPose;
        private final PresetIndicator indicatorPoseToLimbs;

        public PresetCell(Consumer<String> callback, UICopyPasteController.IPresetPreview preview)
        {
            this.callback = callback;
            this.preview = preview;
            this.previewElement = preview == null ? null : preview.createElement();

            if (this.previewElement != null)
            {
                this.previewElement.relative(this);
                this.add(this.previewElement);
            }

            this.indicatorTransform = new PresetIndicator(Colors.GREEN, Colors.A100 + Colors.GREEN);
            this.indicatorTransform.tooltip(UIKeys.PRESETS_GRID_INDICATOR_TRANSFORM, Direction.TOP);
            this.indicatorTransform.relative(this);
            this.indicatorTransform.setVisible(false);
            this.add(this.indicatorTransform);

            this.indicatorPose = new PresetIndicator(Colors.RED, Colors.A100 + Colors.RED);
            this.indicatorPose.tooltip(UIKeys.PRESETS_GRID_INDICATOR_POSE, Direction.TOP);
            this.indicatorPose.relative(this);
            this.indicatorPose.setVisible(false);
            this.add(this.indicatorPose);

            this.indicatorPoseToLimbs = new PresetIndicator(Colors.RED, Colors.WHITE);
            this.indicatorPoseToLimbs.tooltip(UIKeys.PRESETS_GRID_INDICATOR_POSE_TO_LIMBS, Direction.TOP);
            this.indicatorPoseToLimbs.relative(this);
            this.indicatorPoseToLimbs.setVisible(false);
            this.add(this.indicatorPoseToLimbs);
        }

        public String getPresetId()
        {
            return this.presetId;
        }

        public void setSelected(boolean selected)
        {
            this.selected = selected;
        }

        public void bindPreset(String presetId, String label, MapType data, int trackerMask)
        {
            this.presetId = presetId == null ? "" : presetId;
            this.label = label == null ? "" : label;
            this.folder = false;
            this.trackerMask = trackerMask;
            this.updateIndicatorVisibility();
            this.layoutIndicators();

            if (this.previewElement != null)
            {
                this.previewElement.setVisible(true);
            }

            if (this.preview != null && data != null)
            {
                this.preview.preview(this.presetId, data);
            }
        }

        public void bindFolder(String folderToken, String label)
        {
            this.presetId = folderToken == null ? "" : folderToken;
            this.label = label == null ? "" : label;
            this.folder = true;
            this.trackerMask = 0;
            this.updateIndicatorVisibility();
            this.layoutIndicators();

            if (this.previewElement != null)
            {
                this.previewElement.setVisible(false);
            }

            if (this.preview != null)
            {
                this.preview.reset();
            }
        }

        public void unbind()
        {
            this.presetId = "";
            this.label = "";
            this.folder = false;
            this.trackerMask = 0;
            this.updateIndicatorVisibility();

            if (this.previewElement != null)
            {
                this.previewElement.setVisible(true);
            }

            if (this.preview != null)
            {
                this.preview.reset();
            }
        }

        @Override
        public void resize()
        {
            super.resize();

            if (this.previewElement != null)
            {
                int maxW = Math.max(1, this.area.w - 8);
                int maxH = Math.max(1, this.area.h - 22);
                int size = Math.max(1, Math.min(maxW, maxH));
                int x = (this.area.w - size) / 2;

                this.previewElement.xy(x, 4).wh(size, size);
            }

            this.layoutIndicators();
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context) && !this.presetId.isEmpty())
            {
                this.callback.accept(this.presetId);

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        public void render(UIContext context)
        {
            int color = this.selected
                ? Colors.A50 + BBSSettings.primaryColor.get()
                : Colors.A50 + (this.folder ? Colors.DARKER_GRAY : Colors.DARKEST_GRAY);

            this.renderTrackerIndicators(context);

            this.area.render(context.batcher, color);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), this.selected ? Colors.WHITE : Colors.A25);

            if (this.folder)
            {
                context.batcher.icon(Icons.FOLDER, Colors.WHITE, this.area.mx(), this.area.my() - 4, 0.5F, 0.5F);
            }

            super.render(context);

            if (!this.presetId.isEmpty())
            {
                FontRenderer font = context.batcher.getFont();
                String visibleLabel = this.folder ? "[DIR] " + this.label : this.label;
                String line = font.limitToWidth(visibleLabel, this.area.w - 6);
                int x = this.area.mx(font.getWidth(line));
                int y = this.area.ey() - font.getHeight() - 3;

                context.batcher.text(line, x, y, Colors.WHITE);
            }
        }

        private void renderTrackerIndicators(UIContext context)
        {
            this.syncIndicators();
        }

        public void syncIndicators()
        {
            this.updateIndicatorVisibility();
            this.layoutIndicators();
        }

        private void updateIndicatorVisibility()
        {
            boolean showTrackers = UIPresetsGridOverlayPanel.this.areTrackerIndicatorsVisible();
            boolean hasTransform = (this.trackerMask & TRACKER_TRANSFORM) != 0;
            boolean hasPoseToLimbs = (this.trackerMask & TRACKER_POSE_TO_LIMBS) != 0;
            boolean hasPose = !hasPoseToLimbs && (this.trackerMask & TRACKER_POSE) != 0;

            this.indicatorTransform.setVisible(showTrackers && hasTransform);
            this.indicatorPose.setVisible(showTrackers && hasPose);
            this.indicatorPoseToLimbs.setVisible(showTrackers && hasPoseToLimbs);
        }

        private void layoutIndicators()
        {
            int size = 8;
            int spacing = 3;
            int previewX = 4;
            int previewY = 4;
            int maxW = Math.max(1, this.area.w - 8);
            int maxH = Math.max(1, this.area.h - 22);
            int previewW = Math.max(1, Math.min(maxW, maxH));
            int previewH = previewW;

            previewX = (this.area.w - previewW) / 2;

            int x = previewX + previewW - size - 2;
            int y = previewY + 2;

            if (previewH < size + 2)
            {
                y = previewY;
            }

            if (this.indicatorTransform.isVisible())
            {
                this.indicatorTransform.xy(x, y).wh(size, size);
                x -= size + spacing;
            }

            if (this.indicatorPose.isVisible())
            {
                this.indicatorPose.xy(x, y).wh(size, size);
                x -= size + spacing;
            }

            if (this.indicatorPoseToLimbs.isVisible())
            {
                this.indicatorPoseToLimbs.xy(x, y).wh(size, size);
            }
        }
    }

    private static class PresetEntry
    {
        public final boolean folder;
        public final String path;
        public final String displayName;

        private PresetEntry(boolean folder, String path, String displayName)
        {
            this.folder = folder;
            this.path = path;
            this.displayName = displayName;
        }

        public static PresetEntry folder(String path, String displayName)
        {
            return new PresetEntry(true, path, displayName);
        }

        public static PresetEntry preset(String path, String displayName)
        {
            return new PresetEntry(false, path, displayName);
        }
    }

    private static class PresetIndicator extends UIElement
    {
        private final int fillColor;
        private final int borderColor;

        public PresetIndicator(int rgbColor, int borderColor)
        {
            this.fillColor = Colors.A75 + rgbColor;
            this.borderColor = borderColor;
        }

        @Override
        public void render(UIContext context)
        {
            this.area.render(context.batcher, this.fillColor);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), this.borderColor);

            super.render(context);
        }
    }
}
