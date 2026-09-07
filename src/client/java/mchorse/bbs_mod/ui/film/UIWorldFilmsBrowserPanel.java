package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.BBS;
import mchorse.bbs_mod.client.CrossWorldFilmLoader;
import mchorse.bbs_mod.client.CrossWorldFilmScanner;
import mchorse.bbs_mod.client.FilmLaunchHelper;
import mchorse.bbs_mod.client.WorldLaunchHelper;
import mchorse.bbs_mod.film.CrossWorldFilmEntry;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.list.UIDataPathList;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.home.UIHomePanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.resources.Pixels;

import net.minecraft.client.MinecraftClient;
import net.minecraft.world.level.storage.LevelSummary;

import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class UIWorldFilmsBrowserPanel extends UIDashboardPanel
{
    private static final int BANNER_HEIGHT = UIHomePanel.HOME_BANNER_HEIGHT;

    private final UIElement page;
    private final UIDataPathList filmsList;
    private final UIFilmMosaicGrid filmsMosaic;
    private final UIWorldListGrid worldsList;
    private final UISearchList<DataPath> filmsSearch;
    private final UIButton joinWorld;
    private final Map<String, CrossWorldFilmEntry> crossWorldFilmEntries = new HashMap<>();
    private final Map<String, String> crossWorldWorldLabels = new HashMap<>();
    private final Map<String, Texture> filmThumbnails = new HashMap<>();
    private final Map<String, Texture> worldIcons = new HashMap<>();
    private final Set<String> missingFilmThumbnailIds = new HashSet<>();
    private final Set<String> missingWorldIconIds = new HashSet<>();

    private List<LevelSummary> worldSummaries = Collections.emptyList();
    private CrossWorldFilmEntry pendingJoin;
    private LevelSummary pendingWorld;
    private boolean scanning;

    public UIWorldFilmsBrowserPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.page = new UIElement()
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (UIWorldFilmsBrowserPanel.this.isAtWorldRoot())
                {
                    UIWorldFilmsBrowserPanel.this.worldsList.selectedWorldFolder = null;
                    UIWorldFilmsBrowserPanel.this.pendingWorld = null;
                    UIWorldFilmsBrowserPanel.this.updateJoinButton();
                }
                else
                {
                    UIWorldFilmsBrowserPanel.this.filmsMosaic.selectedId = null;
                    UIWorldFilmsBrowserPanel.this.handleFilmSelection(null);
                }

                return super.subMouseClicked(context);
            }
        };

        this.filmsList = new UIDataPathList((list) ->
        {
            if (!list.isEmpty())
            {
                this.handleFilmSelection(list.get(0).toString());
            }
        });
        this.filmsList.setFileIcon(Icons.FILM);
        this.filmsList.background();
        this.filmsList.setVisible(false);

        this.filmsMosaic = new UIFilmMosaicGrid(
            this.filmsList,
            this::getFilmThumbnail,
            this::getFilmCardLabel,
            (id) -> this.handleFilmSelection(id),
            (id) -> this.openFilm(id)
        );
        this.filmsMosaic.setVisible(false);

        this.worldsList = new UIWorldListGrid(
            this::getWorldIcon,
            this::handleWorldSelection,
            this::enterWorldFilms
        );

        this.filmsSearch = new UISearchList<>(this.filmsList).label(UIKeys.GENERAL_SEARCH);
        this.filmsSearch.search.w(1F);

        Consumer<String> oldCallback = this.filmsSearch.search.callback;
        this.filmsSearch.search.callback = (str) ->
        {
            if (oldCallback != null)
            {
                oldCallback.accept(str);
            }

            if (this.isAtWorldRoot())
            {
                this.worldsList.filter(str);
            }
            else
            {
                this.filmsMosaic.filter(str);
            }
        };

        this.joinWorld = new UIButton(UIKeys.FILM_JOIN_WORLD, (b) -> this.joinSelected());
        this.joinWorld.relative(this).x(1F, -12).y(1F, -28).anchor(1F, 1F).w(120).h(20);
        this.joinWorld.setVisible(false);

        this.page.relative(this).x(0.5F, -250).y(0).w(500).h(1F);
        this.filmsSearch.relative(this.page).x(0).y(BANNER_HEIGHT + 20).w(1F).h(20);
        this.worldsList.relative(this.page).x(0).y(BANNER_HEIGHT + 40).w(1F).h(1F, -(BANNER_HEIGHT + 40 + 10));
        this.filmsMosaic.relative(this.page).x(0).y(BANNER_HEIGHT + 40).w(1F).h(1F, -(BANNER_HEIGHT + 40 + 10));
        this.page.add(new UIRenderable(this::renderBanner), this.filmsSearch, this.worldsList, this.filmsMosaic);

        this.add(this.page, this.joinWorld);
    }

    public static boolean isBrowserPanel(UIDashboardPanel panel)
    {
        return panel instanceof UIWorldFilmsBrowserPanel;
    }

    @Override
    public boolean needsBackground()
    {
        return false;
    }

    @Override
    public void appear()
    {
        super.appear();

        this.refreshWorlds();
    }

    @Override
    public void disappear()
    {
        super.disappear();

        this.pendingJoin = null;
        this.pendingWorld = null;
        this.joinWorld.setVisible(false);
        this.clearWorldIcons();
        this.clearFilmThumbnails();
    }

    private boolean isAtWorldRoot()
    {
        return this.filmsList.getPath().strings.isEmpty();
    }

    private void refreshWorlds()
    {
        if (this.scanning)
        {
            return;
        }

        this.scanning = true;

        CompletableFuture<List<LevelSummary>> worldsFuture = CrossWorldFilmScanner.scanWorldsAsync();
        CompletableFuture<List<CrossWorldFilmEntry>> filmsFuture = CrossWorldFilmScanner.scanAsync();

        worldsFuture.thenCombine(filmsFuture, (worlds, films) ->
        {
            MinecraftClient.getInstance().execute(() -> this.applyScanResults(worlds, films));

            return null;
        }).exceptionally((error) ->
        {
            MinecraftClient.getInstance().execute(() ->
            {
                this.scanning = false;
                error.printStackTrace();
                this.applyScanResults(Collections.emptyList(), Collections.emptyList());
            });

            return null;
        });
    }

    private void applyScanResults(List<LevelSummary> worlds, List<CrossWorldFilmEntry> films)
    {
        this.scanning = false;
        this.crossWorldFilmEntries.clear();
        this.crossWorldWorldLabels.clear();
        this.clearFilmThumbnails();
        this.clearWorldIcons();
        this.missingFilmThumbnailIds.clear();
        this.missingWorldIconIds.clear();

        this.worldSummaries = worlds == null ? Collections.emptyList() : new ArrayList<>(worlds);

        List<String> paths = new ArrayList<>();
        Set<String> worldsWithFilms = new HashSet<>();

        if (films != null)
        {
            for (CrossWorldFilmEntry entry : films)
            {
                String path = entry.worldFolder + "/" + entry.filmId;

                paths.add(path);
                worldsWithFilms.add(entry.worldFolder);
                this.crossWorldFilmEntries.put(path, entry);
                this.crossWorldWorldLabels.put(entry.worldFolder, entry.worldLabel);
            }
        }

        for (LevelSummary summary : this.worldSummaries)
        {
            String folder = summary.getName();

            this.crossWorldWorldLabels.put(folder, summary.getDisplayName());

            if (!worldsWithFilms.contains(folder))
            {
                paths.add(folder + "/");
            }
        }

        this.fillFilmPaths(paths);
        this.worldsList.fill(this.worldSummaries, this.worldsList.selectedWorldFolder);
        this.updateBrowseMode();
    }

    private void fillFilmPaths(List<String> paths)
    {
        DataPath selected = this.filmsList.getCurrentFirst();
        String current = selected != null && !selected.folder ? selected.toString() : null;
        DataPath currentPath = this.filmsList.getPath().copy();

        this.filmsList.fill(paths);

        if (!currentPath.strings.isEmpty())
        {
            this.filmsList.goTo(currentPath);
        }

        this.filmsMosaic.fill(paths, current);
    }

    private void updateBrowseMode()
    {
        boolean atRoot = this.isAtWorldRoot();

        this.worldsList.setVisible(atRoot);
        this.filmsMosaic.setVisible(!atRoot);

        if (atRoot)
        {
            this.worldsList.filter(this.filmsSearch.search.textbox.getText());
            this.pendingJoin = null;
        }
        else
        {
            this.filmsMosaic.filter(this.filmsSearch.search.textbox.getText());
            this.pendingWorld = null;
        }

        this.updateJoinButton();
    }

    private String getFilmCardLabel(DataPath path)
    {
        if (path.getLast().equals(".."))
        {
            return "../";
        }

        return path.getLast();
    }

    private Texture getWorldIcon(LevelSummary summary)
    {
        if (summary == null)
        {
            return null;
        }

        String cacheKey = summary.getName();
        Texture cached = this.worldIcons.get(cacheKey);

        if (cached != null)
        {
            return cached;
        }

        if (this.missingWorldIconIds.contains(cacheKey))
        {
            return null;
        }

        Path iconPath = summary.getIconPath();

        if (iconPath == null || !Files.isRegularFile(iconPath))
        {
            this.missingWorldIconIds.add(cacheKey);

            return null;
        }

        try (FileInputStream stream = new FileInputStream(iconPath.toFile()))
        {
            Pixels pixels = Pixels.fromPNGStream(stream);

            if (pixels != null)
            {
                Texture texture = Texture.textureFromPixels(pixels, GL11.GL_LINEAR);

                this.worldIcons.put(cacheKey, texture);
                this.missingWorldIconIds.remove(cacheKey);

                return texture;
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        this.missingWorldIconIds.add(cacheKey);

        return null;
    }

    private File getFilmThumbnailFile(String listPath)
    {
        CrossWorldFilmEntry entry = this.crossWorldFilmEntries.get(listPath);
        String filmId = entry != null ? entry.filmId : listPath;

        if (filmId.endsWith("/"))
        {
            return null;
        }

        File crossWorldFile = CrossWorldFilmLoader.getFilmFile(
            entry != null ? entry.worldFolder : null,
            filmId
        );

        if (crossWorldFile != null)
        {
            File worldRoot = crossWorldFile.getParentFile().getParentFile().getParentFile();
            File worldThumbnail = new File(worldRoot, "config/bbs/thumbnails/films/" + filmId + ".png");

            if (worldThumbnail.exists())
            {
                return worldThumbnail;
            }
        }

        return new File(BBS.getGameFolder(), "config/bbs/thumbnails/films/" + filmId + ".png");
    }

    private Texture getFilmThumbnail(String listPath)
    {
        if (listPath == null || listPath.isEmpty() || listPath.endsWith("/"))
        {
            return null;
        }

        CrossWorldFilmEntry entry = this.crossWorldFilmEntries.get(listPath);
        String cacheKey = entry != null ? entry.encodeKey() : listPath;
        Texture cached = this.filmThumbnails.get(cacheKey);

        if (cached != null)
        {
            return cached;
        }

        if (this.missingFilmThumbnailIds.contains(cacheKey))
        {
            return null;
        }

        File file = this.getFilmThumbnailFile(listPath);

        if (file == null || !file.exists())
        {
            this.missingFilmThumbnailIds.add(cacheKey);

            return null;
        }

        try (FileInputStream stream = new FileInputStream(file))
        {
            Pixels pixels = Pixels.fromPNGStream(stream);

            if (pixels != null && pixels.width > 0 && pixels.height > 0 && pixels.getBuffer() != null)
            {
                int bpp = Math.max(1, pixels.bits);
                long needed = (long) pixels.width * (long) pixels.height * (long) bpp;

                if (needed > 0L && needed <= Integer.MAX_VALUE && pixels.getBuffer().remaining() >= (int) needed)
                {
                    Texture texture = Texture.textureFromPixels(pixels, GL11.GL_LINEAR);

                    this.filmThumbnails.put(cacheKey, texture);
                    this.missingFilmThumbnailIds.remove(cacheKey);

                    return texture;
                }

                pixels.delete();
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        this.missingFilmThumbnailIds.add(cacheKey);

        return null;
    }

    private void handleWorldSelection(LevelSummary summary)
    {
        this.pendingWorld = summary;
        this.pendingJoin = null;
        this.updateJoinButton();
    }

    private void handleFilmSelection(String selected)
    {
        if (selected == null)
        {
            this.pendingJoin = null;
            this.updateJoinButton();

            return;
        }

        DataPath path = this.filmsMosaic.findPath(selected);

        if (path != null && path.folder)
        {
            if (path.getLast().equals(".."))
            {
                this.filmsList.goTo(this.filmsList.getPath().getParent());
            }
            else
            {
                this.filmsList.goTo(path);
            }

            this.filmsMosaic.filter(this.filmsSearch.search.textbox.getText());
            this.pendingJoin = null;
            this.updateBrowseMode();

            return;
        }

        CrossWorldFilmEntry entry = this.crossWorldFilmEntries.get(selected);

        if (entry != null && !entry.filmId.endsWith("/"))
        {
            this.pendingJoin = entry;
            this.pendingWorld = null;

            MinecraftClient client = MinecraftClient.getInstance();

            if (!WorldLaunchHelper.isInLoadedWorld(client))
            {
                FilmLaunchHelper.openCrossWorldFilm(entry);
            }
            else if (WorldLaunchHelper.isCurrentWorld(client, entry.worldFolder))
            {
                FilmLaunchHelper.openCrossWorldFilm(entry);
            }
        }
        else
        {
            this.pendingJoin = null;
        }

        this.updateJoinButton();
    }

    private void updateJoinButton()
    {
        this.joinWorld.setVisible(this.canShowJoinWorld());
    }

    private boolean canShowJoinWorld()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (this.pendingWorld != null)
        {
            return !WorldLaunchHelper.isCurrentWorld(client, this.pendingWorld.getName());
        }

        if (this.pendingJoin == null || this.pendingJoin.filmId.endsWith("/"))
        {
            return false;
        }

        return !WorldLaunchHelper.isCurrentWorld(client, this.pendingJoin.worldFolder);
    }

    private void joinSelected()
    {
        if (this.pendingWorld != null)
        {
            this.joinWorldSummary(this.pendingWorld);

            return;
        }

        if (this.pendingJoin != null)
        {
            FilmLaunchHelper.launch(this.pendingJoin);
            this.pendingJoin = null;
            this.updateJoinButton();
        }
    }

    private void enterWorldFilms(LevelSummary summary)
    {
        if (summary == null)
        {
            return;
        }

        this.pendingWorld = summary;
        this.filmsList.goTo(new DataPath(summary.getName() + "/"));
        this.filmsSearch.search.setText("");
        this.updateBrowseMode();
    }

    private void joinWorldSummary(LevelSummary summary)
    {
        if (summary == null || !summary.isSelectable())
        {
            return;
        }

        WorldLaunchHelper.loadWorld(summary.getName());
        this.pendingWorld = null;
        this.updateJoinButton();
    }

    private void openFilm(String selected)
    {
        CrossWorldFilmEntry entry = this.crossWorldFilmEntries.get(selected);

        if (entry == null || entry.filmId.endsWith("/"))
        {
            return;
        }

        FilmLaunchHelper.openCrossWorldFilm(entry);
    }

    private void clearWorldIcons()
    {
        for (Texture texture : this.worldIcons.values())
        {
            if (texture != null)
            {
                texture.delete();
            }
        }

        this.worldIcons.clear();
    }

    private void clearFilmThumbnails()
    {
        for (Texture texture : this.filmThumbnails.values())
        {
            if (texture != null)
            {
                texture.delete();
            }
        }

        this.filmThumbnails.clear();
    }

    private void renderBanner(UIContext context)
    {
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), Colors.setA(0x0b0b0b, 1F));

        UIHomePanel home = this.dashboard.getPanel(UIHomePanel.class);

        if (home != null)
        {
            home.renderCardAndBanners(context, this.page, this.page.area.x, UIKeys.FILM_HOME_WORLDS.get(), false);
        }
    }
}
