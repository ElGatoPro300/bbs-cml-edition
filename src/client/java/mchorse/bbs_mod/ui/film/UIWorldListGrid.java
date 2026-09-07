package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.world.level.storage.LevelSummary;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Singleplayer-style horizontal world rows for the Worlds browser only.
 */
public class UIWorldListGrid extends UIScrollView
{
    private static final int ENTRY_H = 36;
    private static final int ENTRY_GAP = 4;
    private static final int ICON_SIZE = 32;
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());

    private final Function<LevelSummary, Texture> iconProvider;
    private final Consumer<LevelSummary> selectCallback;
    private final Consumer<LevelSummary> doubleClickCallback;

    private final List<LevelSummary> allWorlds = new ArrayList<>();
    private final List<LevelSummary> visibleWorlds = new ArrayList<>();

    public String selectedWorldFolder;
    private String lastClickedFolder;
    private long lastClickTime;
    private String filterQuery = "";

    public UIWorldListGrid(
        Function<LevelSummary, Texture> iconProvider,
        Consumer<LevelSummary> selectCallback,
        Consumer<LevelSummary> doubleClickCallback
    )
    {
        super();

        this.iconProvider = iconProvider;
        this.selectCallback = selectCallback;
        this.doubleClickCallback = doubleClickCallback;
        this.scroll.scrollSpeed = 20;
    }

    public void fill(List<LevelSummary> worlds, String selectedWorldFolder)
    {
        this.allWorlds.clear();
        this.allWorlds.addAll(worlds);
        this.selectedWorldFolder = selectedWorldFolder;
        this.applyFilter();
    }

    public void filter(String query)
    {
        this.filterQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        this.applyFilter();
    }

    public LevelSummary getSelected()
    {
        if (this.selectedWorldFolder == null)
        {
            return null;
        }

        for (LevelSummary summary : this.visibleWorlds)
        {
            if (this.selectedWorldFolder.equals(summary.getName()))
            {
                return summary;
            }
        }

        return null;
    }

    private void applyFilter()
    {
        this.visibleWorlds.clear();

        for (LevelSummary summary : this.allWorlds)
        {
            if (this.matchesFilter(summary))
            {
                this.visibleWorlds.add(summary);
            }
        }

        this.buildRows();

        if (this.hasParent())
        {
            this.resize();
        }
    }

    private boolean matchesFilter(LevelSummary summary)
    {
        if (this.filterQuery.isEmpty())
        {
            return true;
        }

        String name = summary.getName() == null ? "" : summary.getName().toLowerCase(Locale.ROOT);
        String display = summary.getDisplayName() == null ? "" : summary.getDisplayName().toLowerCase(Locale.ROOT);

        return name.contains(this.filterQuery) || display.contains(this.filterQuery);
    }

    private void buildRows()
    {
        this.removeAll();

        for (int i = 0; i < this.visibleWorlds.size(); i++)
        {
            final LevelSummary summary = this.visibleWorlds.get(i);
            final String folder = summary.getName();
            int y = ENTRY_GAP + i * (ENTRY_H + ENTRY_GAP);

            UIElement row = new UIElement()
            {
                @Override
                public boolean subMouseClicked(UIContext context)
                {
                    if (this.area.isInside(context))
                    {
                        UIWorldListGrid.this.onRowClicked(summary);

                        return true;
                    }

                    return false;
                }

                @Override
                public void render(UIContext context)
                {
                    boolean selected = folder.equals(UIWorldListGrid.this.selectedWorldFolder);
                    boolean hovered = this.area.isInside(context);
                    int bg = selected
                        ? Colors.setA(BBSSettings.primaryColor.get(), 0.22F)
                        : (hovered ? Colors.setA(Colors.WHITE, 0.08F) : Colors.setA(0, 0.28F));
                    int border = selected
                        ? (0xFF000000 | BBSSettings.primaryColor.get())
                        : Colors.setA(Colors.WHITE, 0.12F);

                    context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), bg);
                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);

                    int iconX = this.area.x + 2;
                    int iconY = this.area.y + (ENTRY_H - ICON_SIZE) / 2;
                    Texture icon = UIWorldListGrid.this.iconProvider.apply(summary);

                    if (icon != null)
                    {
                        context.batcher.texturedBox(
                            icon,
                            Colors.WHITE,
                            iconX,
                            iconY,
                            ICON_SIZE,
                            ICON_SIZE,
                            0,
                            0,
                            icon.width,
                            icon.height
                        );
                    }
                    else
                    {
                        context.batcher.box(iconX, iconY, iconX + ICON_SIZE, iconY + ICON_SIZE, Colors.setA(0, 0.45F));
                        context.batcher.icon(Icons.FOLDER, iconX + ICON_SIZE / 2, iconY + ICON_SIZE / 2, 0.5F, 0.5F);
                    }

                    int textX = iconX + ICON_SIZE + 6;
                    int textW = this.area.ex() - textX - 6;
                    String displayName = summary.getDisplayName();
                    String midLine = UIWorldListGrid.this.buildMiddleLine(summary);
                    String details = summary.getDetails() == null ? "" : summary.getDetails().getString();

                    context.batcher.textShadow(
                        context.batcher.getFont().limitToWidth(displayName, textW),
                        textX,
                        this.area.y + 2
                    );
                    context.batcher.text(
                        context.batcher.getFont().limitToWidth(midLine, textW),
                        textX,
                        this.area.y + 13,
                        Colors.setA(Colors.WHITE, 0.55F)
                    );
                    context.batcher.text(
                        context.batcher.getFont().limitToWidth(details, textW),
                        textX,
                        this.area.y + 23,
                        Colors.setA(Colors.WHITE, 0.45F)
                    );

                    super.render(context);
                }
            };

            row.relative(this).x(0).y(y).w(1F).h(ENTRY_H);
            this.add(row);
        }

        this.scroll.scrollSize = ENTRY_GAP + this.visibleWorlds.size() * (ENTRY_H + ENTRY_GAP);
        this.scroll.clamp();
    }

    private String buildMiddleLine(LevelSummary summary)
    {
        String folder = summary.getName();
        long lastPlayed = summary.getLastPlayed();

        if (lastPlayed <= 0L)
        {
            return folder;
        }

        return folder + " (" + DATE_FORMAT.format(new Date(lastPlayed)) + ")";
    }

    private void onRowClicked(LevelSummary summary)
    {
        String folder = summary.getName();
        long now = System.currentTimeMillis();
        boolean sameAsPrev = folder.equals(this.lastClickedFolder);
        boolean doubleClick = sameAsPrev && now - this.lastClickTime <= 300L;

        this.lastClickedFolder = folder;
        this.lastClickTime = now;
        this.selectedWorldFolder = folder;

        if (this.selectCallback != null)
        {
            this.selectCallback.accept(summary);
        }

        if (doubleClick && this.doubleClickCallback != null)
        {
            this.doubleClickCallback.accept(summary);
        }
    }

    @Override
    public void resize()
    {
        if (!this.visibleWorlds.isEmpty())
        {
            this.scroll.scrollSize = ENTRY_GAP + this.visibleWorlds.size() * (ENTRY_H + ENTRY_GAP);
        }

        super.resize();
    }
}
