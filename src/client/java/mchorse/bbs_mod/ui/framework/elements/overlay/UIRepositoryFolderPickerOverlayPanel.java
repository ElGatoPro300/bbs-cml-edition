package mchorse.bbs_mod.ui.framework.elements.overlay;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.utils.UIDataUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Folder picker used when choosing the save folder for a new asset. It reuses the look of the
 * "Open" asset browser (toolbar breadcrumb + folder grid) but is folders-only and has no type
 * sidebar — the asset type is fixed, so all you do here is browse into a folder (or stay at the
 * root) and confirm it as the save destination.
 */
public class UIRepositoryFolderPickerOverlayPanel extends UIOverlayPanel
{
    private static final int TOOLBAR_H = 28;
    private static final int CARD_W = 90;
    private static final int CARD_THUMB_H = 60;
    private static final int CARD_LABEL_H = 20;
    private static final int CARD_H = CARD_THUMB_H + CARD_LABEL_H;
    private static final int CARD_GAP = 6;
    private static final int FOOTER_H = 34;

    public final UIElement toolbar;
    public final UIIcon backButton;
    public final UIElement breadcrumb;
    public final UIFolderGrid grid;
    public final UIButton confirm;
    public final UIButton cancel;

    private final Consumer<String> callback;
    private final List<String> allFolders = new ArrayList<>();
    private String currentFolder = "";

    public UIRepositoryFolderPickerOverlayPanel(ContentType type, String initialFolder, Consumer<String> callback)
    {
        super(UIKeys.CREATE_ASSET_SELECT_FOLDER);
        this.title.color(Colors.WHITE);
        this.resizable();

        this.callback = callback;
        this.currentFolder = initialFolder != null ? initialFolder : "";

        /* Toolbar: back + breadcrumb path (matches the Open UI, minus the type sidebar). */
        this.toolbar = new UIElement();
        this.toolbar.relative(this.content).x(0).y(0).w(1F).h(TOOLBAR_H);

        this.backButton = new UIIcon(Icons.ARROW_LEFT, (b) -> this.navigateUp());
        this.backButton.tooltip(UIKeys.RAW_BACK, Direction.BOTTOM);
        this.backButton.relative(this.toolbar).x(4).y(4).w(20).h(20);

        this.breadcrumb = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                String path = UIRepositoryFolderPickerOverlayPanel.this.currentFolder.isEmpty()
                    ? "/"
                    : "/" + UIRepositoryFolderPickerOverlayPanel.this.currentFolder;
                int ty = this.area.my(context.batcher.getFont().getHeight());

                context.batcher.textShadow(path, this.area.x + 2, ty, Colors.LIGHTER_GRAY);
                super.render(context);
            }
        };
        this.breadcrumb.relative(this.toolbar).x(28).y(0).w(1F, -32).h(TOOLBAR_H);

        this.toolbar.add(this.backButton, this.breadcrumb);

        /* Folder grid (the asset area, folders only) */
        this.grid = new UIFolderGrid();
        this.grid.relative(this.content).x(0).y(TOOLBAR_H).w(1F).h(1F, -TOOLBAR_H - FOOTER_H);

        this.confirm = new UIButton(UIKeys.GENERAL_CONFIRM, (b) -> this.submit());
        this.confirm.relative(this.content).x(1F, -12 - 80 - 6 - 80).y(1F, -30).w(80).h(20);

        this.cancel = new UIButton(UIKeys.ADDON_CANCEL, (b) -> this.close());
        this.cancel.relative(this.content).x(1F, -12 - 80).y(1F, -30).w(80).h(20);

        this.content.add(this.toolbar, this.grid, this.confirm, this.cancel);

        /* Fetch existing folders from the repository. */
        UIDataUtils.requestNames(type, (names) ->
        {
            for (String name : names)
            {
                int lastSlash = name.lastIndexOf('/');

                if (lastSlash >= 0)
                {
                    String path = name.substring(0, lastSlash + 1);

                    if (!this.allFolders.contains(path))
                    {
                        this.allFolders.add(path);
                    }
                }
            }

            this.refresh();
        });
    }

    private void navigateInto(String folder)
    {
        this.currentFolder = folder;
        this.refresh();
    }

    private void navigateUp()
    {
        if (this.currentFolder.isEmpty())
        {
            return;
        }

        String path = this.currentFolder.endsWith("/")
            ? this.currentFolder.substring(0, this.currentFolder.length() - 1)
            : this.currentFolder;
        int slash = path.lastIndexOf('/');

        this.currentFolder = slash < 0 ? "" : path.substring(0, slash + 1);
        this.refresh();
    }

    private void refresh()
    {
        /* Direct subfolders of the current folder, sorted alphabetically. */
        List<String> folders = new ArrayList<>();

        for (String folder : this.allFolders)
        {
            if (folder.isEmpty() || folder.equals(this.currentFolder))
            {
                continue;
            }

            if (folder.startsWith(this.currentFolder))
            {
                String sub = folder.substring(this.currentFolder.length());
                int firstSlash = sub.indexOf('/');

                if (firstSlash >= 0 && firstSlash == sub.length() - 1)
                {
                    String full = this.currentFolder + sub;

                    if (!folders.contains(full))
                    {
                        folders.add(full);
                    }
                }
            }
        }

        folders.sort(String.CASE_INSENSITIVE_ORDER);

        this.backButton.setEnabled(!this.currentFolder.isEmpty());
        this.grid.fill(folders);
    }

    private void submit()
    {
        this.close();

        if (this.callback != null)
        {
            this.callback.accept(this.currentFolder);
        }
    }

    private static String baseName(String path)
    {
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int slash = p.lastIndexOf('/');

        return slash < 0 ? p : p.substring(slash + 1);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        // Main background
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF141418);
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF2A2A35, 1);

        // Header row (with outline stroke, matching the Open UI)
        int headerH = 20;
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + headerH, 0xFF1A1A22);
        context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.y + headerH, 0xFF2A2A35, 1);

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
    /* Inner: folder grid                                                    */
    /* ------------------------------------------------------------------ */

    public class UIFolderGrid extends UIScrollView
    {
        private List<String> folders = new ArrayList<>();
        private int lastW = -1;

        public UIFolderGrid()
        {
            this.scroll.scrollSpeed = 20;
        }

        public void fill(List<String> folders)
        {
            this.folders = new ArrayList<>(folders);
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

            List<String> entries = new ArrayList<>();

            if (!UIRepositoryFolderPickerOverlayPanel.this.currentFolder.isEmpty())
            {
                entries.add("..");
            }

            entries.addAll(this.folders);

            int idx = 0;

            for (String folder : entries)
            {
                int col = idx % cols;
                int row = idx / cols;
                int cx = CARD_GAP + col * (CARD_W + CARD_GAP);
                int cy = CARD_GAP + row * (CARD_H + CARD_GAP);
                UIPickFolderCard card = new UIPickFolderCard(folder);

                card.relative(this).x(cx).y(cy).w(CARD_W).h(CARD_H);
                this.add(card);
                idx++;
            }

            int rows = entries.isEmpty() ? 0 : (entries.size() + cols - 1) / cols;

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

        void clickFolder(String folder)
        {
            if (folder.equals(".."))
            {
                UIRepositoryFolderPickerOverlayPanel.this.navigateUp();
            }
            else
            {
                UIRepositoryFolderPickerOverlayPanel.this.navigateInto(folder);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Inner: folder card                                                    */
    /* ------------------------------------------------------------------ */

    public class UIPickFolderCard extends UIClickable<UIPickFolderCard>
    {
        final String folder;

        public UIPickFolderCard(String folder)
        {
            super(null);

            this.folder = folder;
        }

        @Override
        public boolean subMouseClicked(UIContext context)
        {
            if (context.mouseButton == 0 && this.area.isInside(context))
            {
                UIRepositoryFolderPickerOverlayPanel.this.grid.clickFolder(this.folder);

                return true;
            }

            return super.subMouseClicked(context);
        }

        @Override
        protected UIPickFolderCard get()
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

            /* Folder icon, centered in the thumb area. */
            context.batcher.getContext().getMatrices().push();
            context.batcher.getContext().getMatrices().translate(this.area.mx(), this.area.y + CARD_THUMB_H / 2F, 0);
            context.batcher.getContext().getMatrices().scale(2F, 2F, 1F);
            context.batcher.icon(Icons.FOLDER, Colors.WHITE, -8, -8);
            context.batcher.getContext().getMatrices().pop();

            /* Name strip. */
            int stripY = this.area.y + CARD_THUMB_H;
            context.batcher.box(this.area.x, stripY, this.area.ex(), this.area.ey(), Colors.A50);

            String label = this.folder.equals("..") ? ".." : baseName(this.folder);
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

            int border = this.hover
                ? BBSSettings.primaryColor(Colors.A100)
                : Colors.setA(Colors.WHITE, 0.1F);
            context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);
        }
    }
}
