package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class UIFilmMosaicGrid extends UIScrollView
{
    private static final int CARD_SIZE = 100;
    private static final int CARD_GAP = 6;
    private static final int CARD_LABEL_H = 16;

    private final UIDataPathList pathList;
    private final Function<String, Texture> thumbnailProvider;
    private final Function<DataPath, String> labelProvider;
    private final Consumer<String> selectCallback;
    private final Consumer<String> doubleClickCallback;

    private final List<DataPath> filmPaths = new ArrayList<>();
    public String selectedId;
    private String lastClickedId;
    private long lastClickTime;
    private int lastCols = -1;
    private boolean rebuilding = false;

    public UIFilmMosaicGrid(
        UIDataPathList pathList,
        Function<String, Texture> thumbnailProvider,
        Function<DataPath, String> labelProvider,
        Consumer<String> selectCallback,
        Consumer<String> doubleClickCallback
    )
    {
        super();

        this.pathList = pathList;
        this.thumbnailProvider = thumbnailProvider;
        this.labelProvider = labelProvider;
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
        this.filmPaths.clear();

        for (DataPath path : this.pathList.getFilteredList())
        {
            this.filmPaths.add(path);
        }

        this.buildCards();

        if (this.hasParent())
        {
            this.resize();
        }
    }

    public DataPath findPath(String id)
    {
        for (DataPath path : this.filmPaths)
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

        if (this.filmPaths.isEmpty())
        {
            return;
        }

        int effectiveW = this.area.w > 0 ? this.area.w : 500;
        int cols = Math.max(1, (effectiveW - CARD_GAP) / (CARD_SIZE + CARD_GAP));

        for (int i = 0; i < this.filmPaths.size(); i++)
        {
            final DataPath path = this.filmPaths.get(i);
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
                        UIFilmMosaicGrid.this.onCardClicked(id);

                        return true;
                    }

                    return false;
                }

                @Override
                public void render(UIContext context)
                {
                    boolean selected = id.equals(UIFilmMosaicGrid.this.selectedId);
                    int border = selected ? BBSSettings.primaryColor.get() : Colors.setA(Colors.WHITE, 0.1F);
                    int bg = selected ? Colors.setA(BBSSettings.primaryColor.get(), 0.1F) : Colors.setA(0, 0.2F);

                    context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), bg);
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);

                    super.render(context);

                    boolean isFolder = path.folder;
                    Texture thumbnail = isFolder ? null : UIFilmMosaicGrid.this.thumbnailProvider.apply(id);

                    if (thumbnail != null)
                    {
                        int w = CARD_SIZE - 4;
                        int h = (int) (w * (thumbnail.height / (float) thumbnail.width));

                        if (h > CARD_SIZE - 4)
                        {
                            h = CARD_SIZE - 4;
                            w = (int) (h * (thumbnail.width / (float) thumbnail.height));
                        }

                        int x = this.area.x + 2 + (CARD_SIZE - 4 - w) / 2;
                        int y = this.area.y + 2 + (CARD_SIZE - 4 - h) / 2;

                        context.batcher.fullTexturedBox(thumbnail, x, y, w, h);
                    }
                    else
                    {
                        int iconX = this.area.mx();
                        int iconY = this.area.y + CARD_SIZE / 2;
                        Icon icon = isFolder ? Icons.FOLDER : Icons.FILM;

                        context.batcher.getContext().getMatrices().push();
                        context.batcher.getContext().getMatrices().translate(iconX, iconY, 0);
                        context.batcher.getContext().getMatrices().scale(2F, 2F, 1F);
                        context.batcher.getContext().getMatrices().translate(-iconX, -iconY, 0);

                        context.batcher.icon(icon, iconX, iconY, 0.5F, 0.5F);

                        context.batcher.getContext().getMatrices().pop();
                    }

                    String label = UIFilmMosaicGrid.this.labelProvider.apply(path);
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

        int rows = (this.filmPaths.size() + cols - 1) / cols;
        int totalH = CARD_GAP + rows * (CARD_SIZE + CARD_GAP + CARD_LABEL_H);

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

        if (!this.filmPaths.isEmpty() && !this.rebuilding)
        {
            if (cols != this.lastCols)
            {
                this.lastCols = cols;
                this.rebuilding = true;
                this.buildCards();
                this.rebuilding = false;
            }

            int rows = (this.filmPaths.size() + cols - 1) / cols;
            int totalH = CARD_GAP + rows * (CARD_SIZE + CARD_GAP + CARD_LABEL_H);

            this.scroll.scrollSize = totalH;
        }

        super.resize();
    }
}
