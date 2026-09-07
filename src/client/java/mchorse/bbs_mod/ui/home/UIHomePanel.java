package mchorse.bbs_mod.ui.home;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.resources.packs.URLSourcePack;
import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.ui.ContentType;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.UIPanelSwitcher;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.overlay.UIOpenAssetOverlayPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.list.UISearchList;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UICreateAssetOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIPromptOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UISoundOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIRenderable;
import mchorse.bbs_mod.ui.model.UIModelPreviewRenderer;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.DataPath;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.utils.RecentAssetsTracker;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.repos.IRepository;
import mchorse.bbs_mod.utils.resources.Pixels;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.lwjgl.opengl.GL11;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class UIHomePanel extends UIDashboardPanel
{
    public static void onDashboardOpened(UIDashboard dashboard)
    {
    }

    private static final String BANNERS_URL = "https://raw.githubusercontent.com/BBSCommunity/CML-NEWS/main/Banners_Panel/banners.json";
    public static final int HOME_BANNER_HEIGHT = 108;
    private static final int BANNER_DURATION = 200;
    private static final int BANNER_TRANSITION = 60;

    private static final Set<Link> prefetchingBanners = Collections.synchronizedSet(new HashSet<>());
    /* View mode is persisted globally — see BBSSettings.lastViewMosaic. */

    private final List<BannerEntry> homeBanners = new ArrayList<>();
    private final List<Integer> bannerSequence = new ArrayList<>();
    private int bannerIndex = 0;
    private int sequenceIndex = 0;
    private float lastBannerTicks = -1;

    private final UIElement homePage;
    private final UIElement homeActionsPanel;
    private final UIButton homeOpenButton;
    private final UIButton homeCreateFilm;
    private final UIButton homeCreateModel;
    private final UIButton homeCreateParticle;
    private final UIButton homeCreateAudio;
    private final UIButton homeDuplicateCurrent;
    private final UIButton homeRenameCurrent;
    private final UIButton homeDeleteCurrent;

    private final UIRecentMosaicGrid homeMosaic;
    private final UIStringList homeRecentList;
    private final UISearchList<String> homeRecentSearch;
    private final UIIcon homeViewToggle;

    private final UIPanelSwitcher panelSwitcher;

    private String selectedId;
    private ContentType selectedType;
    private String listLastClickedId;
    private long listLastClickTime;

    public UIHomePanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.initBanners();

        this.homePage = new UIElement()
        {
            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                UIHomePanel.this.clearSelection();

                return super.subMouseClicked(context);
            }
        };

        this.homeActionsPanel = new UIElement();

        this.homeOpenButton = this.createHomeButton(UIKeys.RAW_OPEN, Icons.FOLDER, (b) ->
            UIOverlay.addOverlay(this.getContext(), new UIOpenAssetOverlayPanel(UIKeys.RAW_OPEN_ASSET, this.dashboard), 520, 320));

        this.homeCreateFilm = this.createHomeButton(UIKeys.FILM_TITLE, Icons.FILM, (b) -> this.createNewAsset(ContentType.FILMS));
        this.homeCreateModel = this.createHomeButton(UIKeys.MODELS_TITLE, Icons.PLAYER, (b) -> this.createNewAsset(ContentType.MODELS));
        this.homeCreateParticle = this.createHomeButton(UIKeys.PANELS_PARTICLES, Icons.PARTICLE, (b) -> this.createNewAsset(ContentType.PARTICLES));
        this.homeCreateAudio = this.createHomeButton(UIKeys.PANELS_AUDIOS, Icons.SOUND, (b) ->
            UIOverlay.addOverlay(this.getContext(), new UISoundOverlayPanel((link) -> this.openAsset(ContentType.SOUNDS, link.toString()))));

        this.homeDuplicateCurrent = this.createHomeButton(UIKeys.GENERAL_DUPE, Icons.COPY, (b) -> this.duplicateSelected());
        this.homeRenameCurrent = this.createHomeButton(UIKeys.GENERAL_RENAME, Icons.EDIT, (b) -> this.renameSelected());
        this.homeDeleteCurrent = this.createHomeButton(UIKeys.GENERAL_REMOVE, Icons.REMOVE, (b) -> this.deleteSelected());

        this.panelSwitcher = new UIPanelSwitcher(this.dashboard);
        this.updateHomeButtonsState();

        this.homeRecentList = new UIStringList((list) ->
        {
            if (list.isEmpty()) return;

            String key = list.get(0);
            long now = System.currentTimeMillis();
            boolean doubleClick = key.equals(this.listLastClickedId) && now - this.listLastClickTime <= 300L;

            this.listLastClickedId = key;
            this.listLastClickTime = now;

            RecentAssetsTracker.Entry entry = this.decodeRecentKey(key);

            if (doubleClick)
            {
                this.openRecent(entry);
            }
            else
            {
                this.handleRecentSelectOnly(entry);
            }
        })
        {
            @Override
            protected String elementToString(UIContext context, int i, String element)
            {
                return UIHomePanel.this.decodeRecentId(element);
            }

            @Override
            protected void renderElementPart(UIContext context, String element, int i, int x, int y, boolean hover, boolean selected)
            {
                Icon icon = UIHomePanel.this.getIconForKey(element);
                int textColor = hover ? Colors.HIGHLIGHT : Colors.WHITE;
                int textX = x + 4;

                if (icon != null)
                {
                    int iconY = y + (this.scroll.scrollItemSize - icon.h) / 2;

                    context.batcher.icon(icon, Colors.WHITE, x + 4, iconY);

                    textX = x + 4 + icon.w + 4;
                }

                context.batcher.textShadow(this.elementToString(context, i, element), textX, y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2, textColor);
            }
        };

        this.homeRecentList.background();
        this.homeRecentList.context((menu) ->
        {
            if (this.selectedId != null && this.selectedType != null)
            {
                RecentAssetsTracker.Entry e = new RecentAssetsTracker.Entry(this.selectedType, this.selectedId);
                menu.action(Icons.REMOVE, UIKeys.FILM_HOME_REMOVE_RECENT, () -> this.removeFromRecent(e));
            }
        });

        boolean mosaic = BBSSettings.lastViewMosaic.get();

        this.homeMosaic = new UIRecentMosaicGrid(this, this::handleRecentSelectOnly, this::openRecent);
        this.homeMosaic.setVisible(mosaic);
        this.homeRecentList.setVisible(!mosaic);

        this.homeRecentSearch = new UISearchList<>(this.homeRecentList).label(UIKeys.GENERAL_SEARCH);

        Consumer<String> oldCallback = this.homeRecentSearch.search.callback;

        this.homeRecentSearch.search.callback = (str) ->
        {
            if (oldCallback != null) oldCallback.accept(str);

            this.homeMosaic.filter(str);
        };

        this.homeViewToggle = new UIIcon(mosaic ? Icons.LIST : Icons.GALLERY, (b) -> this.toggleMosaicView());
        this.homeViewToggle.tooltip(mosaic ? UIKeys.MODELS_HOME_VIEW_LIST : UIKeys.MODELS_HOME_VIEW_MOSAIC, Direction.LEFT);

        this.layout();
    }

    private void layout()
    {
        UIElement labelNew = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF1A1A22);
                context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF2A2A35, 1);
                
                context.batcher.textShadow(UIKeys.RAW_NEW.get(), this.area.x + 4, this.area.y + 6);
                
                super.render(context);
            }
        };
        labelNew.h(20);

        this.homeActionsPanel.add(
            this.homeOpenButton,
            this.homeDuplicateCurrent,
            this.homeRenameCurrent,
            this.homeDeleteCurrent,
            labelNew,
            this.homeCreateFilm,
            this.homeCreateModel,
            this.homeCreateParticle,
            this.homeCreateAudio
        );

        this.homePage.relative(this).x(0.5F, -250).y(0).w(500).h(1F);
        this.homeActionsPanel.relative(this.homePage).x(0).y(HOME_BANNER_HEIGHT + 20).w(0.35F).h(1F, -(HOME_BANNER_HEIGHT + 20 + 10)).column(0).vertical().stretch();
        this.panelSwitcher.relative(this.homePage).x(0.5F, -87).y(1F, -32).w(175).h(24);
        this.homeRecentSearch.relative(this.homePage).x(0.35F).y(HOME_BANNER_HEIGHT + 20).w(0.65F).h(1F, -(HOME_BANNER_HEIGHT + 20 + 10));
        this.homeRecentSearch.search.w(1F, -25);
        this.homeMosaic.relative(this.homeRecentSearch).x(0).y(20).w(1F).h(1F, -20);
        this.homeViewToggle.relative(this.homeRecentSearch).x(1F, -22).y(0).w(20).h(20);

        this.homePage.add(new UIRenderable(this::renderHomeBanner), this.homeActionsPanel, this.homeRecentSearch, this.homeMosaic, this.homeViewToggle, this.panelSwitcher);

        this.add(this.homePage);
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

        this.refreshRecentList();
    }

    private void openAsset(ContentType type, String id)
    {
        if (this.dashboard.documentTabsBar != null)
        {
            /* The tabs bar identifies audio by null type; translate SOUNDS accordingly */
            ContentType tabsType = (type == ContentType.SOUNDS) ? null : type;
            this.dashboard.documentTabsBar.addOrActivate(tabsType, id);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Recent list / mosaic                                                  */
    /* ------------------------------------------------------------------ */

    private String encodeRecentKey(RecentAssetsTracker.Entry entry)
    {
        return entry.type.getId() + "\0" + entry.id;
    }

    private RecentAssetsTracker.Entry decodeRecentKey(String key)
    {
        int sep = key.indexOf('\0');

        if (sep < 0) return null;

        ContentType type = ContentType.fromId(key.substring(0, sep));
        String id = key.substring(sep + 1);

        return new RecentAssetsTracker.Entry(type, id);
    }

    private String decodeRecentId(String key)
    {
        int sep = key.indexOf('\0');

        return sep < 0 ? key : key.substring(sep + 1);
    }

    public void refreshRecentList()
    {
        List<RecentAssetsTracker.Entry> recent = new ArrayList<>();

        for (RecentAssetsTracker.Entry entry : RecentAssetsTracker.RECENT)
        {
            if (!RecentAssetsTracker.shouldExcludeFromRecent(entry.type, entry.id)
                && RecentAssetsTracker.existsInCurrentWorld(entry.type, entry.id))
            {
                recent.add(entry);
            }
        }

        List<String> keys = new ArrayList<>();

        for (RecentAssetsTracker.Entry entry : recent)
        {
            keys.add(this.encodeRecentKey(entry));
        }

        this.homeRecentList.clear();
        this.homeRecentList.add(keys);
        this.homeMosaic.fill(recent, this.selectedId, this.selectedType);
    }

    private void handleRecentSelectOnly(RecentAssetsTracker.Entry entry)
    {
        if (entry == null) return;

        this.selectedId = entry.id;
        this.selectedType = entry.type;
        this.homeMosaic.setSelected(entry.id, entry.type);
        this.homeRecentList.setCurrentScroll(this.encodeRecentKey(entry));
        this.updateHomeButtonsState();
    }

    private void openRecent(RecentAssetsTracker.Entry entry)
    {
        if (entry != null)
        {
            this.openAsset(entry.type, entry.id);
        }
    }

    void removeFromRecent(RecentAssetsTracker.Entry entry)
    {
        if (entry == null)
        {
            return;
        }

        RecentAssetsTracker.remove(entry.type, entry.id);

        if (this.selectedId != null && this.selectedId.equals(entry.id) && this.selectedType == entry.type)
        {
            this.clearSelection();
        }

        this.refreshRecentList();
    }

    private void clearSelection()
    {
        this.selectedId = null;
        this.selectedType = null;

        if (this.homeMosaic != null) this.homeMosaic.setSelected(null, null);

        this.homeRecentList.deselect();
        this.updateHomeButtonsState();
    }

    private void updateHomeButtonsState()
    {
        boolean hasSelection = this.selectedId != null && this.selectedType != null && this.selectedType != ContentType.SOUNDS;

        this.homeDuplicateCurrent.setEnabled(hasSelection);
        this.homeRenameCurrent.setEnabled(hasSelection);
        this.homeDeleteCurrent.setEnabled(hasSelection);
    }

    @SuppressWarnings("unchecked")
    private void createNewAsset(ContentType type)
    {
        UICreateAssetOverlayPanel panel = new UICreateAssetOverlayPanel(
            type,
            (name) ->
            {
                IRepository<ValueGroup> repository = (IRepository<ValueGroup>) type.getRepository();
                ValueGroup created = repository.create(name);

                if (created != null)
                {
                    repository.save(name, created.toData().asMap());
                }

                this.openAsset(type, name);
            }
        );

        UIOverlay.addOverlay(this.getContext(), panel, 260, 160);
    }

    private void duplicateSelected()
    {
        if (this.selectedId == null || this.selectedType == null) return;

        String current = this.selectedId;
        ContentType type = this.selectedType;
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_DUPE,
            UIKeys.PANELS_MODALS_DUPE,
            (name) ->
            {
                if (name.trim().isEmpty())
                {
                    this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

                    return;
                }

                type.getRepository().load(current, (data) ->
                {
                    if (data != null)
                    {
                        type.getRepository().save(name, ((ValueGroup) data).toData().asMap());
                    }
                });
            }
        );

        panel.text.setText(new DataPath(current).getLast());
        panel.text.filename();
        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void renameSelected()
    {
        if (this.selectedId == null || this.selectedType == null) return;

        String current = this.selectedId;
        ContentType type = this.selectedType;
        UIPromptOverlayPanel panel = new UIPromptOverlayPanel(
            UIKeys.GENERAL_RENAME,
            UIKeys.PANELS_MODALS_RENAME,
            (name) ->
            {
                if (name.trim().isEmpty())
                {
                    this.getContext().notifyError(UIKeys.PANELS_MODALS_EMPTY);

                    return;
                }

                type.getRepository().rename(current, name);
                RecentAssetsTracker.RECENT.removeIf(e -> e.type == type && e.id.equals(current));
                RecentAssetsTracker.add(type, name);
                this.refreshRecentList();
                this.clearSelection();
            }
        );

        panel.text.setText(new DataPath(current).getLast());
        panel.text.filename();
        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void deleteSelected()
    {
        if (this.selectedId == null || this.selectedType == null) return;

        String current = this.selectedId;
        ContentType type = this.selectedType;
        UIConfirmOverlayPanel panel = new UIConfirmOverlayPanel(
            UIKeys.GENERAL_REMOVE,
            UIKeys.PANELS_MODALS_REMOVE,
            (confirm) ->
            {
                if (!confirm) return;

                type.getRepository().delete(current);

                if (type == ContentType.FILMS)
                {
                    UIFilmPanel filmPanel = this.dashboard.getPanel(UIFilmPanel.class);

                    if (filmPanel != null)
                    {
                        filmPanel.deleteThumbnail(current);
                    }
                }

                RecentAssetsTracker.RECENT.removeIf(e -> e.type == type && e.id.equals(current));
                RecentAssetsTracker.save();
                this.refreshRecentList();
                this.clearSelection();
            }
        );

        UIOverlay.addOverlay(this.getContext(), panel);
    }

    private void toggleMosaicView()
    {
        boolean mosaic = !BBSSettings.lastViewMosaic.get();

        BBSSettings.lastViewMosaic.set(mosaic);
        this.homeMosaic.setVisible(mosaic);
        this.homeRecentList.setVisible(!mosaic);
        this.homeViewToggle.both(mosaic ? Icons.LIST : Icons.GALLERY);
        this.homeViewToggle.tooltip(mosaic ? UIKeys.MODELS_HOME_VIEW_LIST : UIKeys.MODELS_HOME_VIEW_MOSAIC, Direction.LEFT);

        if (mosaic)
        {
            this.homeMosaic.resize();
        }
    }

    private Icon getIconForKey(String key)
    {
        RecentAssetsTracker.Entry entry = this.decodeRecentKey(key);

        if (entry == null) return null;

        if (entry.type == ContentType.FILMS) return Icons.FILM;
        if (entry.type == ContentType.MODELS) return Icons.PLAYER;
        if (entry.type == ContentType.PARTICLES) return Icons.PARTICLE;

        return Icons.SOUND;
    }

    private UIButton createHomeButton(IKey label, Icon icon, Consumer<UIButton> callback)
    {
        UIButton button = new UIButton(label, callback)
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                int bg = this.hover && this.isEnabled() ? 0xFF181820 : 0xFF111115;
                context.batcher.box(this.area.x + 2, this.area.y, this.area.ex() - 2, this.area.ey(), bg);

                int color = this.isEnabled() ? (this.hover ? Colors.WHITE : 0xFFCCCCCC) : 0xFF666666;
                int textX = this.area.x + 8;

                if (icon != null)
                {
                    context.batcher.icon(icon, color, this.area.x + 6, this.area.my() - 8);
                    textX = this.area.x + 24;
                }

                int y = this.area.my(context.batcher.getFont().getHeight());
                context.batcher.clip(this.area.x, this.area.y, this.area.w - 6, this.area.h, context);
                context.batcher.textShadow(label.get(), textX, y, color);
                context.batcher.unclip(context);
            }
        };

        button.h(20);

        return button;
    }

    /* ------------------------------------------------------------------ */
    /* Banner system                                                         */
    /* ------------------------------------------------------------------ */

    private void initBanners()
    {
        BannerEntry home = new BannerEntry();

        home.author = "ElGatoPro300";
        home.link = Link.assets("textures/banners/films/Home.png");
        this.homeBanners.add(home);

        this.fetchRemoteBanners();
    }

    private void fetchRemoteBanners()
    {
        CompletableFuture.runAsync(() ->
        {
            try
            {
                HttpClient client = HttpClient.newBuilder().build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(BANNERS_URL)).GET().build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());

                if (resp.statusCode() == 200)
                {
                    List<BannerEntry> remote = new Gson().fromJson(resp.body(), new TypeToken<List<BannerEntry>>(){}.getType());

                    if (remote != null)
                    {
                        for (BannerEntry entry : remote)
                        {
                            entry.link = Link.create(entry.url);
                            this.prefetchBannerImage(entry.link);
                        }

                        MinecraftClient.getInstance().execute(() -> this.homeBanners.addAll(remote));
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        });
    }

    private void prefetchBannerImage(Link link)
    {
        if (link == null || link.source == null || !link.source.startsWith("http")) return;
        if (BBSModClient.getTextures().textures.get(link) != null) return;
        if (!prefetchingBanners.add(link)) return;

        CompletableFuture.runAsync(() ->
        {
            try (InputStream stream = URLSourcePack.downloadImage(link))
            {
                if (stream != null)
                {
                    Pixels pixels = Pixels.fromPNGStream(stream);

                    if (pixels != null)
                    {
                        RenderSystem.recordRenderCall(() ->
                        {
                            Texture texture = Texture.textureFromPixels(pixels, GL11.GL_LINEAR);

                            BBSModClient.getTextures().textures.put(link, texture);
                        });
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            finally
            {
                prefetchingBanners.remove(link);
            }
        });
    }

    private void regenerateBannerSequence()
    {
        this.bannerSequence.clear();

        for (int i = 0; i < this.homeBanners.size(); i++)
        {
            this.bannerSequence.add(i);
        }

        this.shuffleRemoteBanners();
        this.sequenceIndex = 0;
        this.bannerIndex = 0;
    }

    private void shuffleRemoteBanners()
    {
        if (this.bannerSequence.size() > 2)
        {
            List<Integer> remote = this.bannerSequence.subList(1, this.bannerSequence.size());

            Collections.shuffle(remote);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Rendering                                                             */
    /* ------------------------------------------------------------------ */

    private void renderHomeBanner(UIContext context)
    {
        int pageX = this.homePage.area.x;
        int pageY = this.homePage.area.y;
        int pageW = this.homePage.area.w;
        int pageH = this.homePage.area.h;
        int editorX = this.area.x;
        int editorY = this.area.y;
        int editorW = this.area.w;
        int editorH = this.area.h;
        int bannerH = HOME_BANNER_HEIGHT;
        int splitY = pageY + bannerH;
        int dividerX = pageX + (int) (pageW * 0.35F);

        /* Full-screen dark background */
        context.batcher.box(editorX, editorY, editorX + editorW, editorY + editorH, Colors.setA(0x0b0b0b, 1F));

        int primary = BBSSettings.primaryColor.get();
        float tick = context.getTickTransition() * 0.015F;
        int segments = 40;
        float segW = editorW / (float) segments;

        Matrix4f matrix4f = context.batcher.getContext().getMatrices().peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        float[] yBot1 = new float[segments + 1];
        float[] yMid1 = new float[segments + 1];
        int[] cMid1 = new int[segments + 1];
        float[] yBot2 = new float[segments + 1];
        float[] yMid2 = new float[segments + 1];
        int[] cMid2 = new int[segments + 1];

        for (int i = 0; i <= segments; i++)
        {
            float nx = (float) i / segments;

            float w1 = (float) Math.sin(tick * 1.2F + nx * 8F);
            float w2 = (float) Math.sin(tick * 0.7F + nx * 15F);
            float w3 = (float) Math.cos(tick * 0.4F - nx * 12F);
            float comb1 = (w1 + w2 + w3) / 3F;

            float curtainYTop = editorY + editorH * 0.05F;
            float curtainYBot = editorY + editorH * 0.5F + comb1 * (editorH * 0.35F);

            if (curtainYBot < curtainYTop + 10) curtainYBot = curtainYTop + 10;

            float transitionY = curtainYBot - editorH * 0.3F;

            if (transitionY < curtainYTop) transitionY = curtainYTop;

            yBot1[i] = curtainYBot;
            yMid1[i] = transitionY;
            cMid1[i] = Colors.setA(primary, 0.15F + Math.max(0, comb1) * 0.2F);

            float w4 = (float) Math.sin(tick * 1.5F - nx * 10F);
            float w5 = (float) Math.cos(tick * 0.9F + nx * 18F);
            float comb2 = (w4 + w5) / 2F;

            float curtain2YTop = editorY + editorH * 0.15F;
            float curtain2YBot = editorY + editorH * 0.75F + comb2 * (editorH * 0.25F);

            if (curtain2YBot < curtain2YTop + 10) curtain2YBot = curtain2YTop + 10;

            float transition2Y = curtain2YBot - editorH * 0.25F;

            if (transition2Y < curtain2YTop) transition2Y = curtain2YTop;

            yBot2[i] = curtain2YBot;
            yMid2[i] = transition2Y;
            cMid2[i] = Colors.setA(Colors.mulRGB(primary, 0.8F), 0.1F + Math.max(0, comb2) * 0.15F);
        }

        int colTop = Colors.setA(primary, 0.0F);
        int colBot = Colors.setA(primary, 0.0F);
        float yTop1f = editorY + editorH * 0.05F;
        float yTop2f = editorY + editorH * 0.15F;

        for (int i = 0; i < segments; i++)
        {
            float x1 = editorX + i * segW;
            float x2 = editorX + (i + 1) * segW;

            builder.vertex(matrix4f, x1, yTop1f, 0).color(colTop);
            builder.vertex(matrix4f, x1, yMid1[i], 0).color(cMid1[i]);
            builder.vertex(matrix4f, x2, yMid1[i + 1], 0).color(cMid1[i + 1]);
            builder.vertex(matrix4f, x2, yTop1f, 0).color(colTop);

            builder.vertex(matrix4f, x1, yMid1[i], 0).color(cMid1[i]);
            builder.vertex(matrix4f, x1, yBot1[i], 0).color(colBot);
            builder.vertex(matrix4f, x2, yBot1[i + 1], 0).color(colBot);
            builder.vertex(matrix4f, x2, yMid1[i + 1], 0).color(cMid1[i + 1]);

            builder.vertex(matrix4f, x1, yTop2f, 0).color(colTop);
            builder.vertex(matrix4f, x1, yMid2[i], 0).color(cMid2[i]);
            builder.vertex(matrix4f, x2, yMid2[i + 1], 0).color(cMid2[i + 1]);
            builder.vertex(matrix4f, x2, yTop2f, 0).color(colTop);

            builder.vertex(matrix4f, x1, yMid2[i], 0).color(cMid2[i]);
            builder.vertex(matrix4f, x1, yBot2[i], 0).color(colBot);
            builder.vertex(matrix4f, x2, yBot2[i + 1], 0).color(colBot);
            builder.vertex(matrix4f, x2, yMid2[i + 1], 0).color(cMid2[i + 1]);
        }

        BufferRenderer.drawWithGlobalProgram(builder.end());

        this.renderCardAndBanners(context, this.homePage, dividerX, UIKeys.FILM_HOME_LIST.get(), true);
    }

    public void renderCardAndBanners(UIContext context, UIElement customHomePage, int dividerX, String listTitle)
    {
        this.renderCardAndBanners(context, customHomePage, dividerX, listTitle, true);
    }

    public void renderCardAndBanners(UIContext context, UIElement customHomePage, int dividerX, String listTitle, boolean showActionsColumn)
    {
        int pageX = customHomePage.area.x;
        int pageY = customHomePage.area.y;
        int pageW = customHomePage.area.w;
        int pageH = customHomePage.area.h;
        int bannerH = HOME_BANNER_HEIGHT;
        int splitY = pageY + bannerH;

        context.batcher.gradientHBox(pageX - 18, pageY, pageX, pageY + pageH, 0, Colors.setA(0x000000, 0.7F));
        context.batcher.gradientHBox(pageX + pageW, pageY, pageX + pageW + 18, pageY + pageH, Colors.setA(0x000000, 0.7F), 0);
        context.batcher.box(pageX, pageY, pageX + pageW, pageY + pageH, 0xFF141418);
        context.batcher.outline(pageX, pageY, pageX + pageW, pageY + pageH, 0xFF2A2A35, 1);

        float currentTicks = context.getTickTransition();

        if (this.lastBannerTicks < 0) this.lastBannerTicks = currentTicks - BANNER_TRANSITION;

        float elapsed = Math.max(0, currentTicks - this.lastBannerTicks);

        if (elapsed >= BANNER_DURATION)
        {
            if (this.homeBanners.size() > 1)
            {
                if (this.bannerSequence.size() != this.homeBanners.size())
                {
                    this.regenerateBannerSequence();
                }

                this.sequenceIndex++;

                if (this.sequenceIndex >= this.bannerSequence.size())
                {
                    this.sequenceIndex = 0;
                    this.shuffleRemoteBanners();
                }

                this.bannerIndex = this.bannerSequence.get(this.sequenceIndex);
            }

            this.lastBannerTicks = currentTicks;
            elapsed = 0;
        }

        float transition = 0F;
        float textTransitionPrev = 1F;
        float textTransitionCurr = 0F;

        if (elapsed < BANNER_TRANSITION && this.homeBanners.size() > 1)
        {
            transition = (float) Interpolations.CUBIC_INOUT.interpolate(1F, 0F, elapsed / (float) BANNER_TRANSITION);
            transition = Math.max(0F, Math.min(1F, transition));
            textTransitionPrev = transition;

            float textElapsed = Math.max(0, elapsed - 20);

            textTransitionCurr = (float) Interpolations.CUBIC_INOUT.interpolate(0F, 1F, textElapsed / (float) (BANNER_TRANSITION - 20));
        }
        else
        {
            textTransitionCurr = 1F;
        }

        if (this.homeBanners.isEmpty())
        {
            context.batcher.box(pageX, pageY, pageX + pageW, pageY + bannerH, Colors.setA(0, 0.3F));
        }
        else
        {
            int prevIndex = this.bannerSequence.isEmpty() ? 0 : this.bannerSequence.get((this.sequenceIndex + this.bannerSequence.size() - 1) % this.bannerSequence.size());
            BannerEntry current = this.homeBanners.get(this.bannerIndex);
            BannerEntry prev = this.homeBanners.get(prevIndex);

            if (transition > 0.001F)
            {
                this.drawBanner(context, prev, pageX, pageY, pageW, bannerH, transition, textTransitionPrev, true);
                this.drawBanner(context, current, pageX, pageY, pageW, bannerH, 1F - transition, textTransitionCurr, true);
            }
            else
            {
                this.drawBanner(context, current, pageX, pageY, pageW, bannerH, 1F, textTransitionCurr, true);
            }
        }

        // Header Row
        context.batcher.box(pageX, splitY, pageX + pageW, splitY + 20, 0xFF1A1A22);
        context.batcher.outline(pageX, splitY, pageX + pageW, splitY + 20, 0xFF2A2A35, 1);

        if (showActionsColumn)
        {
            context.batcher.box(pageX, splitY + 20, dividerX, pageY + pageH, 0xFF111115);
            context.batcher.box(dividerX - 1, splitY + 20, dividerX, pageY + pageH, 0xFF22222A);
            context.batcher.textShadow(UIKeys.FILM_HOME_ACTIONS.get(), pageX + 4, splitY + 6);
            context.batcher.textShadow(listTitle, dividerX + 4, splitY + 6);
        }
        else
        {
            context.batcher.textShadow(listTitle, pageX + 4, splitY + 6);
        }
    }

    private void drawBanner(UIContext context, BannerEntry entry, int x, int y, int w, int h, float alpha, float textAlpha, boolean drawStripe)
    {
        if (alpha < 0.001F && textAlpha < 0.001F) return;

        Link link = entry.link;
        Texture texture = link.source != null && link.source.startsWith("http")
            ? BBSModClient.getTextures().textures.get(link)
            : BBSModClient.getTextures().getTexture(link);

        if (texture == null) return;

        float scale = Math.min(w / (float) texture.width, h / (float) texture.height);
        int tw = Math.max(1, Math.round(texture.width * scale));
        int th = Math.max(1, Math.round(texture.height * scale));
        int tx = x + (w - tw) / 2;
        int ty = y + (h - th) / 2;

        if (alpha > 0.001F)
        {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.batcher.texturedBox(texture, Colors.setA(Colors.WHITE, alpha), tx, ty, tw, th, 0, 0, texture.width, texture.height);
        }

        if (textAlpha > 0.001F && entry.author != null && !entry.author.isEmpty())
        {
            String label = UIKeys.FILM_HOME_BANNER_AUTHOR.format(entry.author).get();
            int lw = context.batcher.getFont().getWidth(label);
            int stripeH = 16;
            int stripeY = ty + th - stripeH - 6;
            int bx = tx + tw - lw - 6;

            if (drawStripe)
            {
                context.batcher.box(bx - 6, stripeY, tx + tw, ty + th - 6, Colors.setA(0, textAlpha * 0.6F));
            }

            context.batcher.textShadow(label, bx, stripeY + (stripeH - 8) / 2, Colors.setA(Colors.WHITE, textAlpha));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Recent mosaic grid                                                    */
    /* ------------------------------------------------------------------ */

    public class UIRecentMosaicGrid extends UIScrollView
    {
        private static final int CARD_SIZE = 100;
        private static final int CARD_GAP = 6;
        private static final int CARD_LABEL_H = 16;

        private final UIHomePanel home;
        private final Consumer<RecentAssetsTracker.Entry> selectCallback;
        private final Consumer<RecentAssetsTracker.Entry> doubleClickCallback;

        private final List<RecentAssetsTracker.Entry> allEntries = new ArrayList<>();
        private final List<RecentAssetsTracker.Entry> filteredEntries = new ArrayList<>();

        public String selectedId;
        public ContentType selectedType;

        private String lastClickedId;
        private long lastClickTime;
        private int lastCols = -1;
        private boolean rebuilding = false;
        private String filterQuery = "";

        public UIRecentMosaicGrid(UIHomePanel home, Consumer<RecentAssetsTracker.Entry> selectCallback, Consumer<RecentAssetsTracker.Entry> doubleClickCallback)
        {
            this.home = home;
            this.selectCallback = selectCallback;
            this.doubleClickCallback = doubleClickCallback;
            this.scroll.scrollSpeed = 20;
        }

        public void setSelected(String id, ContentType type)
        {
            this.selectedId = id;
            this.selectedType = type;
        }

        public void fill(List<RecentAssetsTracker.Entry> entries, String selectedId, ContentType selectedType)
        {
            this.allEntries.clear();
            this.allEntries.addAll(entries);
            this.selectedId = selectedId;
            this.selectedType = selectedType;
            this.lastCols = -1;
            this.filter(this.filterQuery);
        }

        public void filter(String query)
        {
            this.filterQuery = query == null ? "" : query;
            this.filteredEntries.clear();

            String lowerQuery = this.filterQuery.toLowerCase();

            for (RecentAssetsTracker.Entry e : this.allEntries)
            {
                if (lowerQuery.isEmpty() || e.id.toLowerCase().contains(lowerQuery))
                {
                    this.filteredEntries.add(e);
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

            if (this.filteredEntries.isEmpty()) return;

            int effectiveW = this.area.w > 0 ? this.area.w : 500;
            int cols = Math.max(1, (effectiveW - CARD_GAP) / (CARD_SIZE + CARD_GAP));

            for (int i = 0; i < this.filteredEntries.size(); i++)
            {
                RecentAssetsTracker.Entry entry = this.filteredEntries.get(i);
                int col = i % cols;
                int row = i / cols;
                int cx = CARD_GAP + col * (CARD_SIZE + CARD_GAP);
                int cy = CARD_GAP + row * (CARD_SIZE + CARD_GAP + CARD_LABEL_H);

                this.add(this.createCard(entry, cx, cy));
            }

            int rows = (this.filteredEntries.size() + cols - 1) / cols;
            int totalH = CARD_GAP + rows * (CARD_SIZE + CARD_LABEL_H + CARD_GAP);

            this.scroll.scrollSize = totalH;
            this.scroll.clamp();
        }

        private UIElement createCard(RecentAssetsTracker.Entry entry, int cx, int cy)
        {
            UIElement card = new UIElement()
            {
                @Override
                public boolean subMouseClicked(UIContext context)
                {
                    if (this.area.isInside(context))
                    {
                        if (context.mouseButton == 0)
                        {
                            UIRecentMosaicGrid.this.onCardClicked(entry);
                        }
                        else if (context.mouseButton == 1)
                        {
                            UIRecentMosaicGrid.this.selectedId = entry.id;
                            UIRecentMosaicGrid.this.selectedType = entry.type;

                            if (UIRecentMosaicGrid.this.selectCallback != null)
                            {
                                UIRecentMosaicGrid.this.selectCallback.accept(entry);
                            }

                            this.mouseClickedContextMenu(context);
                        }

                        return true;
                    }

                    return false;
                }

                @Override
                public void render(UIContext context)
                {
                    boolean selected = entry.id.equals(UIRecentMosaicGrid.this.selectedId) && entry.type == UIRecentMosaicGrid.this.selectedType;
                    boolean hover = this.area.isInside(context);
                    int border = selected || hover ? BBSSettings.primaryColor(Colors.A100) : Colors.setA(Colors.WHITE, 0.08F);

                    this.area.render(context.batcher, Colors.setA(0, 0.3F));
                    context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + CARD_SIZE, Colors.setA(0, 0.2F));
                    
                    if (selected || hover)
                    {
                        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + CARD_SIZE, Colors.A25);
                    }
                    
                    context.batcher.box(this.area.x, this.area.y + CARD_SIZE, this.area.ex(), this.area.ey(), Colors.A50);

                    super.render(context);

                    if (entry.type == ContentType.FILMS)
                    {
                        UIFilmPanel filmPanel = UIRecentMosaicGrid.this.home.dashboard.getPanel(UIFilmPanel.class);
                        Texture thumbnail = filmPanel != null ? filmPanel.getThumbnail(entry.id) : null;

                        if (thumbnail != null)
                        {
                            int w = CARD_SIZE - 4;
                            int h = (int) (w * (thumbnail.height / (float) thumbnail.width));

                            if (h > CARD_SIZE - 4)
                            {
                                h = CARD_SIZE - 4;
                                w = (int) (h * (thumbnail.width / (float) thumbnail.height));
                            }

                            int ix = this.area.x + 2 + (CARD_SIZE - 4 - w) / 2;
                            int iy = this.area.y + 2 + (CARD_SIZE - 4 - h) / 2;

                            context.batcher.fullTexturedBox(thumbnail, ix, iy, w, h);
                        }
                        else
                        {
                            this.renderIcon(context, Icons.FILM);
                        }
                    }
                    else if (entry.type == ContentType.PARTICLES)
                    {
                        this.renderIcon(context, Icons.PARTICLE);
                    }
                    else if (entry.type == ContentType.SOUNDS)
                    {
                        this.renderIcon(context, Icons.SOUND);
                    }
                    /* Models render through the embedded UIModelPreviewRenderer child */

                    String label = new DataPath(entry.id).getLast();
                    int maxW = this.area.w - 4;

                    if (context.batcher.getFont().getWidth(label) > maxW)
                    {
                        while (label.length() > 1 && context.batcher.getFont().getWidth(label + "...") > maxW)
                        {
                            label = label.substring(0, label.length() - 1);
                        }

                        label = label + "...";
                    }

                    int ty = this.area.y + CARD_SIZE + (CARD_LABEL_H - context.batcher.getFont().getHeight()) / 2;
                    context.batcher.textShadow(label, this.area.x + 3, ty, Colors.WHITE);

                    context.batcher.outline(this.area.x, this.area.y, this.area.ex(), this.area.ey(), border);
                }

                private void renderIcon(UIContext context, Icon icon)
                {
                    int iconX = this.area.mx();
                    int iconY = this.area.y + CARD_SIZE / 2;

                    context.batcher.getContext().getMatrices().push();
                    context.batcher.getContext().getMatrices().translate(iconX, iconY, 0);
                    context.batcher.getContext().getMatrices().scale(2F, 2F, 1F);
                    context.batcher.getContext().getMatrices().translate(-iconX, -iconY, 0);
                    context.batcher.icon(icon, iconX, iconY, 0.5F, 0.5F);
                    context.batcher.getContext().getMatrices().pop();
                }
            };

            card.context((menu) -> menu.action(Icons.REMOVE, UIKeys.FILM_HOME_REMOVE_RECENT,
                () -> UIRecentMosaicGrid.this.home.removeFromRecent(entry)));
            card.relative(this).x(cx).y(cy).w(CARD_SIZE).h(CARD_SIZE + CARD_LABEL_H);

            if (entry.type == ContentType.MODELS)
            {
                UIModelPreviewRenderer preview = new UIModelPreviewRenderer();

                preview.relative(card).x(2).y(2).w(CARD_SIZE - 4).h(CARD_SIZE - 4);
                preview.setModel(entry.id);
                card.add(preview);
            }

            return card;
        }

        private void onCardClicked(RecentAssetsTracker.Entry entry)
        {
            long now = System.currentTimeMillis();
            boolean doubleClick = entry.id.equals(this.lastClickedId) && entry.type == this.selectedType && now - this.lastClickTime <= 300L;

            this.lastClickedId = entry.id;
            this.lastClickTime = now;
            this.selectedId = entry.id;
            this.selectedType = entry.type;

            if (this.selectCallback != null)
            {
                this.selectCallback.accept(entry);
            }

            if (doubleClick && this.doubleClickCallback != null)
            {
                this.doubleClickCallback.accept(entry);
            }
        }

        @Override
        public void resize()
        {
            int effectiveW = this.area.w > 0 ? this.area.w : 500;
            int cols = Math.max(1, (effectiveW - CARD_GAP) / (CARD_SIZE + CARD_GAP));

            if (!this.filteredEntries.isEmpty() && !this.rebuilding)
            {
                if (cols != this.lastCols)
                {
                    this.lastCols = cols;
                    this.rebuilding = true;
                    this.buildCards();
                    this.rebuilding = false;
                }

                int rows = (this.filteredEntries.size() + cols - 1) / cols;
                int totalH = CARD_GAP + rows * (CARD_SIZE + CARD_LABEL_H + CARD_GAP);

                this.scroll.scrollSize = totalH;
            }

            super.resize();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Banner data                                                           */
    /* ------------------------------------------------------------------ */

    public static class BannerEntry
    {
        public String author;
        public String url;
        public transient Link link;
    }
}
