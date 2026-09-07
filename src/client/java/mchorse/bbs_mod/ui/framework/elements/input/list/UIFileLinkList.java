package mchorse.bbs_mod.ui.framework.elements.input.list;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.NaturalOrderComparator;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class UIFileLinkList extends UIList<UIFileLinkList.FileLink>
{
    private static final int MIN_ITEM_SIZE = 16;
    public static final int VIEW_LIST = MIN_ITEM_SIZE;
    public static final int VIEW_ICONS_SMALL = 32;
    public static final int VIEW_ICONS_MEDIUM = 64;
    public static final int VIEW_ICONS_LARGE = 96;
    public static final int VIEW_ICONS_VERY_LARGE = 160;
    private static final int MAX_ITEM_SIZE = 220;
    private static final int FREE_ZOOM_STEP = 8;
    private static final int GRID_PADDING = 4;
    private static final int GRID_GAP = 6;
    private static final int GRID_TITLE_HEIGHT = 12;

    private int itemSize = MIN_ITEM_SIZE;

    private final List<Link> backHistory = new ArrayList<>();
    private final List<Link> forwardHistory = new ArrayList<>();
    private boolean recordHistory = true;
    private boolean navigatingHistory;
    private boolean skipNextHistory;
    private int setPathDepth;

    public Consumer<Link> fileCallback;
    public Link path = new Link("", "");
    public Predicate<Link> filter;

    public UIFileLinkList(Consumer<Link> fileCallback)
    {
        super(null);

        this.callback = (list) ->
        {
            FileLink fileLink = list.get(0);

            if (!fileLink.folder)
            {
                if (this.fileCallback != null)
                {
                    this.fileCallback.accept(fileLink.link);
                }
            }
            else
            {
                this.setPath(fileLink.link, !fileLink.up);
            }
        };
        this.fileCallback = fileCallback;
        this.scroll.scrollItemSize = this.itemSize;
        this.scroll.scrollSpeed = this.itemSize;
    }

    public UIFileLinkList filter(Predicate<Link> filter)
    {
        this.filter = filter;

        return this;
    }

    public void toggleLargeView()
    {
        this.setItemSize(this.itemSize <= MIN_ITEM_SIZE ? VIEW_ICONS_MEDIUM : MIN_ITEM_SIZE);
    }

    public void changeItemSize(int delta)
    {
        if (delta == 0)
        {
            return;
        }

        this.setItemSize(this.itemSize + delta);
    }

    public void setItemSize(int size)
    {
        this.itemSize = Math.max(MIN_ITEM_SIZE, Math.min(MAX_ITEM_SIZE, size));
        this.scroll.scrollItemSize = this.isLargeViewEnabled() ? this.getGridRowStride() : this.itemSize;
        this.scroll.scrollSpeed = this.scroll.scrollItemSize;
        this.update();
    }

    public int getItemSize()
    {
        return this.itemSize;
    }

    public boolean isLargeViewEnabled()
    {
        return this.itemSize > MIN_ITEM_SIZE;
    }

    public boolean canDecreaseViewSize()
    {
        return this.itemSize > MIN_ITEM_SIZE;
    }

    public boolean canIncreaseViewSize()
    {
        return this.itemSize < MAX_ITEM_SIZE;
    }

    @Override
    public boolean subMouseScrolled(UIContext context)
    {
        if (this.area.isInside(context.mouseX, context.mouseY) && Window.isCtrlPressed() && context.mouseWheel != 0)
        {
            int direction = context.mouseWheel > 0 ? 1 : -1;

            this.setItemSize(this.itemSize + direction * FREE_ZOOM_STEP);

            return true;
        }

        return super.subMouseScrolled(context);
    }

    @Override
    public void update()
    {
        if (!this.isLargeViewEnabled())
        {
            this.scroll.scrollItemSize = this.itemSize;
            this.scroll.scrollSpeed = this.scroll.scrollItemSize;
            super.update();

            return;
        }

        int columns = this.getGridColumns();
        int rows = (int) Math.ceil(this.getVisibleElementsCount() / (double) columns);

        this.scroll.scrollItemSize = this.getGridRowStride();
        this.scroll.scrollSpeed = this.scroll.scrollItemSize;
        this.scroll.setSize(rows);
        this.scroll.clamp();
    }

    @Override
    public void resize()
    {
        super.resize();

        if (this.isLargeViewEnabled())
        {
            this.update();
        }
    }

    @Override
    public int getHoveredIndex(UIContext context)
    {
        if (!this.isLargeViewEnabled())
        {
            return super.getHoveredIndex(context);
        }

        int visible = this.getGridVisibleIndex(context.mouseX, context.mouseY);

        return this.getListIndexFromVisible(visible);
    }

    @Override
    public void setCurrentScroll(FileLink element)
    {
        this.setCurrent(element);

        if (!this.isLargeViewEnabled() || this.current.isEmpty())
        {
            return;
        }

        int visibleIndex = this.getVisibleIndexFromList(this.current.get(0));

        if (visibleIndex < 0)
        {
            return;
        }

        this.scroll.setScroll((visibleIndex / this.getGridColumns()) * this.scroll.scrollItemSize);
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (!this.isLargeViewEnabled())
        {
            return super.subMouseClicked(context);
        }

        if (this.scroll.mouseClicked(context))
        {
            return true;
        }

        if (this.area.isInside(context) && context.mouseButton == 0)
        {
            int visibleIndex = this.getGridVisibleIndex(context.mouseX, context.mouseY);
            int index = this.getListIndexFromVisible(visibleIndex);

            if (this.exists(index))
            {
                this.setIndex(index);

                if (this.callback != null)
                {
                    this.callback.accept(this.getCurrent());

                    return true;
                }
            }

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void renderList(UIContext context)
    {
        if (!this.isLargeViewEnabled())
        {
            super.renderList(context);

            return;
        }

        int count = this.getVisibleElementsCount();

        if (count == 0)
        {
            return;
        }

        int columns = this.getGridColumns();
        int rowStride = this.getGridRowStride();
        int tileWidth = this.getGridTileWidth();
        int tileHeight = this.getGridTileHeight();
        int scroll = (int) this.scroll.getScroll();

        int firstRow = Math.max(0, scroll / rowStride);
        int lastRow = Math.max(firstRow, Math.min((count - 1) / columns, (scroll + this.area.h) / rowStride + 1));

        for (int row = firstRow; row <= lastRow; row++)
        {
            int y = this.area.y + row * rowStride - scroll;

            for (int col = 0; col < columns; col++)
            {
                int visibleIndex = row * columns + col;

                if (visibleIndex >= count)
                {
                    break;
                }

                FileLink element = this.getVisibleElement(visibleIndex);
                int index = this.getListIndexFromVisible(visibleIndex);

                if (element == null || !this.exists(index))
                {
                    continue;
                }

                int x = this.area.x + GRID_PADDING + col * (tileWidth + GRID_GAP);
                boolean hover = context.mouseX >= x && context.mouseX < x + tileWidth && context.mouseY >= y && context.mouseY < y + tileHeight;
                boolean selected = this.current.contains(index);

                this.renderListElement(context, element, index, x, y, hover, selected);
            }
        }
    }

    @Override
    public void renderListElement(UIContext context, FileLink element, int i, int x, int y, boolean hover, boolean selected)
    {
        if (!this.isLargeViewEnabled())
        {
            super.renderListElement(context, element, i, x, y, hover, selected);

            return;
        }

        int tileWidth = this.getGridTileWidth();
        int tileHeight = this.getGridTileHeight();
        int color = 0x22000000;

        if (selected)
        {
            color = Colors.A50 | BBSSettings.primaryColor.get();
        }
        else if (hover)
        {
            color = 0x33000000;
        }

        context.batcher.box(x, y, x + tileWidth, y + tileHeight, color);
        this.renderElementPart(context, element, i, x, y, hover, selected);
    }

    public void clearNavigationHistory()
    {
        this.backHistory.clear();
        this.forwardHistory.clear();
        this.skipNextHistory = true;
    }

    public boolean navigateBack()
    {
        Link target;

        if (!this.backHistory.isEmpty())
        {
            target = this.backHistory.remove(this.backHistory.size() - 1);
        }
        else
        {
            target = this.getParentFolder();
        }

        if (target == null)
        {
            return false;
        }

        this.forwardHistory.add(this.path);
        this.navigatingHistory = true;

        try
        {
            this.setPath(target, false);
        }
        finally
        {
            this.navigatingHistory = false;
        }

        return true;
    }

    public boolean navigateForward()
    {
        if (this.forwardHistory.isEmpty())
        {
            return false;
        }

        Link target = this.forwardHistory.remove(this.forwardHistory.size() - 1);

        this.backHistory.add(this.path);
        this.navigatingHistory = true;

        try
        {
            this.setPath(target, false);
        }
        finally
        {
            this.navigatingHistory = false;
        }

        return true;
    }

    private Link getParentFolder()
    {
        if (this.path.source.isEmpty() && this.path.path.isEmpty())
        {
            return null;
        }

        if (this.path.path.isEmpty())
        {
            return new Link("", "");
        }

        return new Link(this.path.source, StringUtils.parentPath(this.path.path));
    }

    public void setPath(Link link)
    {
        this.setPath(link, true);
    }

    /**
     * Set current link
     */
    public void setPath(Link link, boolean fastForward)
    {
        boolean record = this.recordHistory && !this.navigatingHistory && !this.skipNextHistory && this.setPathDepth == 0;
        Link previous = record ? this.path : null;

        this.setPathDepth++;

        try
        {
            this.setPathInternal(link, fastForward);
        }
        finally
        {
            this.setPathDepth--;

            if (record && previous != null && !previous.equals(this.path))
            {
                this.backHistory.add(previous);
                this.forwardHistory.clear();
            }

            if (this.skipNextHistory && this.setPathDepth == 0)
            {
                this.skipNextHistory = false;
            }
        }
    }

    private void setPathInternal(Link link, boolean fastForward)
    {
        if (link == null || link.source.isEmpty())
        {
            this.clear();

            for (String source : BBSMod.getProvider().getSourceKeys())
            {
                this.add(new FileLink(source, new Link(source, ""), true));
            }

            this.path = new Link("", "");

            this.sort();
        }
        else
        {
            Collection<Link> links = BBSMod.getProvider().getLinksFromPath(link, false);

            if (fastForward && links.size() == 1)
            {
                Link first = links.iterator().next();

                if (first.path.endsWith("/"))
                {
                    this.setPath(first);

                    return;
                }
            }

            this.path = link;

            FileLink parent = link.path.isEmpty()
                ? new FileLink("...", new Link("", ""), true, true)
                : new FileLink("...", new Link(link.source, StringUtils.parentPath(link.path)), true, true);

            this.clear();
            this.add(parent);

            for (Link l : links)
            {
                if (this.filter == null || this.filter.test(l))
                {
                    this.add(new FileLink(StringUtils.fileName(l.path).replaceAll("/", ""), l, l.path.endsWith("/")));
                }
            }

            this.sort();
        }
    }

    public void setCurrent(Link link)
    {
        this.setCurrent(link, false);
    }

    public void setCurrent(Link link, boolean scroll)
    {
        this.deselect();

        if (link == null)
        {
            return;
        }

        for (FileLink entry : this.list)
        {
            if (entry.link.equals(link))
            {
                if (scroll) this.setCurrentScroll(entry);
                else this.setCurrent(entry);

                return;
            }
        }
    }

    @Override
    protected boolean sortElements()
    {
        this.list.sort((a, b) ->
        {
            if (a.folder != b.folder)
            {
                return a.folder ? -1 : 1;
            }

            return NaturalOrderComparator.compare(true, a.title, b.title);
        });

        return true;
    }

    @Override
    protected void renderElementPart(UIContext context, FileLink element, int i, int x, int y, boolean hover, boolean selected)
    {
        if (!this.isLargeViewEnabled())
        {
            int size = this.itemSize;
            int preview = Math.max(12, size - 4);
            int previewX = x + 2;
            int previewY = y + (size - preview) / 2;

            if (element.folder)
            {
                this.renderFolderPreview(context, previewX, previewY, preview, hover);
            }
            else
            {
                Texture texture = BBSModClient.getTextures().getTexture(element.link);

                this.renderTexturePreview(context, texture, previewX, previewY, preview);
            }

            context.batcher.textShadow(element.title, x + preview + 8, y + (size - context.batcher.getFont().getHeight()) / 2, hover ? Colors.HIGHLIGHT : Colors.WHITE);

            return;
        }

        int tileWidth = this.getGridTileWidth();
        int preview = this.itemSize;
        int previewX = x + (tileWidth - preview) / 2;
        int previewY = y + 3;

        if (element.up)
        {
            this.renderArrowPreview(context, previewX, previewY, preview, hover);
        }
        else if (element.folder)
        {
            this.renderFolderPreview(context, previewX, previewY, preview, hover);
        }
        else if (element.folder)
        {
            this.renderFolderPreview(context, previewX, previewY, preview, hover);
        }
        else
        {
            Texture texture = BBSModClient.getTextures().getTexture(element.link);

            this.renderTexturePreview(context, texture, previewX, previewY, preview);
        }

        FontRenderer font = context.batcher.getFont();
        String title = font.limitToWidth(element.title, Math.max(1, tileWidth - 8));
        int titleWidth = font.getWidth(title);
        int titleX = x + (tileWidth - titleWidth) / 2;
        int titleY = previewY + preview + 3;

        context.batcher.textShadow(title, titleX, titleY, hover ? Colors.HIGHLIGHT : Colors.WHITE);
    }

    private void renderFolderPreview(UIContext context, int x, int y, int size, boolean hover)
    {
        int color = Colors.setA(Colors.WHITE, hover ? 0.95F : 0.82F);
        Texture atlas = BBSModClient.getTextures().getTexture(Icons.FOLDER.texture);

        context.batcher.texturedBox(
            atlas,
            color,
            x,
            y,
            size,
            size,
            Icons.FOLDER.x,
            Icons.FOLDER.y,
            Icons.FOLDER.x + Icons.FOLDER.w,
            Icons.FOLDER.y + Icons.FOLDER.h,
            Icons.FOLDER.textureW,
            Icons.FOLDER.textureH
        );
    }

    private void renderArrowPreview(UIContext context, int x, int y, int size, boolean hover)
    {
        int color = Colors.setA(Colors.WHITE, hover ? 0.95F : 0.82F);
        Texture atlas = BBSModClient.getTextures().getTexture(Icons.ARROW_LEFT.texture);

        context.batcher.texturedBox(
            atlas,
            color,
            x,
            y,
            size,
            size,
            Icons.ARROW_LEFT.x,
            Icons.ARROW_LEFT.y,
            Icons.ARROW_LEFT.x + Icons.ARROW_LEFT.w,
            Icons.ARROW_LEFT.y + Icons.ARROW_LEFT.h,
            Icons.ARROW_LEFT.textureW,
            Icons.ARROW_LEFT.textureH
        );
    }

    private void renderTexturePreview(UIContext context, Texture texture, int x, int y, int boxSize)
    {
        int width = texture.width;
        int height = texture.height;

        if (width <= 0 || height <= 0)
        {
            context.batcher.fullTexturedBox(texture, x, y, boxSize, boxSize);

            return;
        }

        float scale = Math.min(boxSize / (float) width, boxSize / (float) height);
        int drawWidth = Math.max(1, Math.round(width * scale));
        int drawHeight = Math.max(1, Math.round(height * scale));
        int drawX = x + (boxSize - drawWidth) / 2;
        int drawY = y + (boxSize - drawHeight) / 2;

        context.batcher.fullTexturedBox(texture, drawX, drawY, drawWidth, drawHeight);
    }

    private int getGridTileWidth()
    {
        return Math.max(56, this.itemSize + 20);
    }

    private int getGridTileHeight()
    {
        return this.itemSize + GRID_TITLE_HEIGHT + 8;
    }

    private int getGridRowStride()
    {
        return this.getGridTileHeight() + GRID_GAP;
    }

    private int getGridColumns()
    {
        int tileWidth = this.getGridTileWidth();
        int available = Math.max(tileWidth, this.area.w - GRID_PADDING * 2 - this.scroll.getScrollbarWidth());

        return Math.max(1, (available + GRID_GAP) / (tileWidth + GRID_GAP));
    }

    private int getVisibleElementsCount()
    {
        if (!this.isFiltering())
        {
            return this.list.size();
        }

        int count = 0;

        while (this.getVisibleElement(count) != null)
        {
            count += 1;
        }

        return count;
    }

    private int getListIndexFromVisible(int visibleIndex)
    {
        if (visibleIndex < 0)
        {
            return -1;
        }

        if (!this.isFiltering())
        {
            return visibleIndex;
        }

        FileLink element = this.getVisibleElement(visibleIndex);

        return element == null ? -1 : this.list.indexOf(element);
    }

    private int getVisibleIndexFromList(int listIndex)
    {
        if (!this.exists(listIndex))
        {
            return -1;
        }

        if (!this.isFiltering())
        {
            return listIndex;
        }

        FileLink target = this.list.get(listIndex);
        int i = 0;

        while (true)
        {
            FileLink element = this.getVisibleElement(i);

            if (element == null)
            {
                return -1;
            }

            if (element == target)
            {
                return i;
            }

            i += 1;
        }
    }

    private int getGridVisibleIndex(int mouseX, int mouseY)
    {
        if (!this.area.isInside(mouseX, mouseY))
        {
            return -1;
        }

        int tileWidth = this.getGridTileWidth();
        int tileHeight = this.getGridTileHeight();
        int rowStride = this.getGridRowStride();
        int x = mouseX - this.area.x - GRID_PADDING;
        int y = mouseY - this.area.y + (int) this.scroll.getScroll();

        if (x < 0 || y < 0)
        {
            return -1;
        }

        int colStride = tileWidth + GRID_GAP;
        int col = x / colStride;
        int row = y / rowStride;

        if (x % colStride >= tileWidth || y % rowStride >= tileHeight || col >= this.getGridColumns())
        {
            return -1;
        }

        int index = row * this.getGridColumns() + col;

        return index < this.getVisibleElementsCount() ? index : -1;
    }

    public static class FileLink
    {
        public String title;
        public Link link;
        public boolean folder;
        public boolean up;

        public FileLink(String title, Link link, boolean folder)
        {
            this(title, link, folder, false);
        }

        public FileLink(String title, Link link, boolean folder, boolean up)
        {
            this.title = title;
            this.link = link;
            this.folder = folder;
            this.up = up;
        }
    }
}
