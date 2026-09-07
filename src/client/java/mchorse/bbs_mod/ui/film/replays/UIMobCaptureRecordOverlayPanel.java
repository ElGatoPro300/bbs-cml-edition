package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.MobCaptureAreaScanner;
import mchorse.bbs_mod.film.MobCaptureRecordingSetup;
import mchorse.bbs_mod.film.RecordingPauseHelper;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIClickable;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class UIMobCaptureRecordOverlayPanel extends UIOverlayPanel
{
    private static final int ICON_COLUMN_WIDTH = 20;
    private static final int TOGGLE_WIDTH = 28;
    private static final int COORD_WIDTH = 70;
    private static final int COLUMN_HEADER_MAX_WIDTH = 52;
    private static final int SCROLLBAR_GUTTER = 12;
    private static final int FOOTER_BUTTON_WIDTH = 100;
    private static final int FOOTER_GAP = 8;
    private static final int FOOTER_SPACE = 36;
    private static final int TAB_HEIGHT = 20;
    private static final int SUMMARY_LINE_HEIGHT = 8;
    /* Shared gap between condition-summary rows and wrapped lines in that block. */
    private static final int SUMMARY_ROW_GAP = 3;
    private static final int TAB_UNDERLINE = 0xFF3C3C3C;
    private static final int ENTITY_ROW_HOVER = 0x66000000;

    private enum CaptureTab
    {
        ENTITIES,
        CONDITIONS
    }

    private static UIMobCaptureRecordOverlayPanel opened;

    private final Consumer<MobCaptureRecordingSetup> callback;
    private final Runnable onCancel;
    private final MobCaptureRecordingSetup setup = new MobCaptureRecordingSetup();
    private boolean recordingPauseHeld;

    private UIText description;
    private UIElement tabBar;
    private OverlayTabButton entitiesTab;
    private OverlayTabButton conditionsTab;
    private UIElement entitiesPage;
    private UIElement conditionsPage;
    private UILabel conditionsHeader;
    private UIScrollView conditionsScroll;
    private UILabel radiusLabel;
    private UITrackpad radius;
    private UIToggle capturePlayers;
    private UIToggle playerNametags;
    private UIToggle playerModelForms;
    private UILabel originLabel;
    private UIElement coordsRow;
    private UIButton originMode;
    private UITrackpad originX;
    private UITrackpad originY;
    private UITrackpad originZ;
    private UIToggle includeHeight;
    private UIElement conditionsSummary;
    private UIElement summaryGrid;
    private UIElement summaryLeft;
    private UIElement summaryRight;
    private UILabel conditionsSummaryHeader;
    private UILabel summaryRadius;
    private UILabel summaryHeight;
    private UILabel summaryOrigin;
    private UILabel summaryCoords;
    private UILabel summaryPlayers;
    private UILabel summaryNametags;
    private UILabel summaryModels;
    private UIElement seeMore;
    private UIElement entitiesHeader;
    private UILabel entitiesTitle;
    private UIIcon refresh;
    private UILabel summary;
    private UIScrollView scroll;
    private UIElement footer;
    private CaptureTab currentTab = CaptureTab.ENTITIES;

    private int addColumnWidth = TOGGLE_WIDTH;
    private int vaColumnWidth = TOGGLE_WIDTH;
    private int columnHeaderHeight = 14;

    private final Map<String, Boolean> expandedTypes = new HashMap<>();
    private Map<String, MobCaptureAreaScanner.TypeBucket> lastBuckets = new HashMap<>();

    public static void openInGame(Consumer<MobCaptureRecordingSetup> callback)
    {
        if (isOpened())
        {
            return;
        }

        UIMobCaptureRecordOverlayPanel panel = new UIMobCaptureRecordOverlayPanel(callback, null);

        panel.onClose((event) -> MinecraftClient.getInstance().setScreen(null));

        UIScreen.open(new UIBaseMenu()
        {
            @Override
            public boolean needsWorldRender()
            {
                return true;
            }

            @Override
            public boolean canHideHUD()
            {
                return false;
            }

            @Override
            public boolean canPause()
            {
                return false;
            }

            @Override
            public void onOpen(UIBaseMenu oldMenu)
            {
                super.onOpen(oldMenu);

                /* instantClose: onClose → setScreen(null) must not finish inside the
                 * overlay close animation render pass (one-frame dark flash). */
                UIOverlay.addOverlay(this.context, panel, 500, 480).noBackground().instantClose();
            }
        });
    }

    public static void openOnContext(UIContext context, Consumer<MobCaptureRecordingSetup> callback)
    {
        openOnContext(context, callback, null);
    }

    public static void openOnContext(UIContext context, Consumer<MobCaptureRecordingSetup> callback, Runnable onCancel)
    {
        if (context == null || isOpened())
        {
            return;
        }

        UIOverlay.addOverlay(context, new UIMobCaptureRecordOverlayPanel(callback, onCancel), 500, 480);
    }

    public static boolean isOpened()
    {
        return opened != null && opened.hasParent();
    }

    public static void closeOpened()
    {
        if (opened != null)
        {
            opened.close();
        }
    }

    public UIMobCaptureRecordOverlayPanel(Consumer<MobCaptureRecordingSetup> callback)
    {
        this(callback, null);
    }

    public UIMobCaptureRecordOverlayPanel(Consumer<MobCaptureRecordingSetup> callback, Runnable onCancel)
    {
        super(UIKeys.FILM_MOB_CAPTURE_TITLE);

        opened = this;
        this.callback = callback;
        this.onCancel = onCancel;
        this.setup.loadFromPreferences();
        this.resizable().minSize(400, 420);

        this.description = new UIText(UIKeys.FILM_MOB_CAPTURE_DESCRIPTION).textAnchorX(0.5F);
        this.description.relative(this.content).x(0.5F).w(0.9F).h(36).anchorX(0.5F);

        this.entitiesTab = new OverlayTabButton(UIKeys.FILM_MOB_CAPTURE_TAB_ENTITIES, Icons.LIST, (b) -> this.setTab(CaptureTab.ENTITIES));
        this.conditionsTab = new OverlayTabButton(UIKeys.FILM_MOB_CAPTURE_TAB_CONDITIONS, Icons.GEAR, (b) -> this.setTab(CaptureTab.CONDITIONS));
        this.tabBar = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                super.render(context);

                int x = UIMobCaptureRecordOverlayPanel.this.area.x;
                int ex = UIMobCaptureRecordOverlayPanel.this.area.ex();

                context.batcher.box(x, this.area.ey() - 1, ex, this.area.ey(), TAB_UNDERLINE);
            }
        };
        this.tabBar.relative(this.content).x(12).w(1F, -24).h(TAB_HEIGHT);
        this.entitiesTab.relative(this.tabBar).x(0).y(0);
        this.conditionsTab.relative(this.entitiesTab).x(1F).y(0);
        this.tabBar.add(this.entitiesTab, this.conditionsTab);

        this.entitiesPage = new UIElement();
        this.entitiesPage.relative(this.content).x(12).w(1F, -24);
        this.conditionsPage = new UIElement();
        this.conditionsPage.relative(this.content).x(12).w(1F, -24);

        this.conditionsHeader = this.createSectionLabel(UIKeys.FILM_MOB_CAPTURE_SECTION_CONDITIONS);
        this.conditionsHeader.relative(this.conditionsPage).x(0).w(1F).h(14);

        this.radiusLabel = UI.label(UIKeys.FILM_MOB_CAPTURE_RADIUS);
        this.radiusLabel.w(1F).h(14);
        this.radius = new UITrackpad((v) ->
        {
            this.setup.areaSize = v.floatValue();
            this.refreshTypes();
        });
        this.radius.limit(16D, 256D, true).increment(4D).values(16D, 4D, 32D);
        this.radius.setValue(this.setup.areaSize);
        this.radius.w(1F).h(20);

        this.includeHeight = new UIToggle(UIKeys.FILM_MOB_CAPTURE_INCLUDE_HEIGHT, this.setup.includeHeight, (b) ->
        {
            this.setup.includeHeight = b.getValue();
            this.updateOriginFieldsEnabled();
            this.refreshTypes();
        });
        this.includeHeight.tooltip(UIKeys.FILM_MOB_CAPTURE_INCLUDE_HEIGHT_TOOLTIP);
        this.includeHeight.w(1F).h(20);

        this.capturePlayers = new UIToggle(UIKeys.FILM_MOB_CAPTURE_PLAYERS, this.setup.capturePlayers, (b) ->
        {
            this.setup.capturePlayers = b.getValue();
            this.refreshTypes();
        });
        this.capturePlayers.tooltip(UIKeys.FILM_MOB_CAPTURE_PLAYERS_TOOLTIP);
        this.capturePlayers.w(1F).h(20);

        this.playerNametags = new UIToggle(UIKeys.FILM_MOB_CAPTURE_PLAYER_NAMETAGS, this.setup.playerNametags, (b) ->
        {
            this.setup.playerNametags = b.getValue();
            this.updateConditionsSummary();
        });
        this.playerNametags.tooltip(UIKeys.FILM_MOB_CAPTURE_PLAYER_NAMETAGS_TOOLTIP);
        this.playerNametags.w(1F).h(20);

        this.playerModelForms = new UIToggle(UIKeys.FILM_MOB_CAPTURE_PLAYER_MODELS, this.setup.playerModelForms, (b) ->
        {
            this.setup.playerModelForms = b.getValue();
            this.refreshTypes();
        });
        this.playerModelForms.tooltip(UIKeys.FILM_MOB_CAPTURE_PLAYER_MODELS_TOOLTIP);
        this.playerModelForms.w(1F).h(20);

        this.originLabel = UI.label(UIKeys.FILM_MOB_CAPTURE_ORIGIN);
        this.originLabel.w(1F).h(14);
        this.originMode = new UIButton(this.getOriginModeLabel(), (b) -> this.toggleOriginMode());
        this.originMode.w(1F).h(20);
        this.originMode.tooltip(UIKeys.FILM_MOB_CAPTURE_ORIGIN_TOOLTIP);

        this.originX = new UITrackpad((v) ->
        {
            this.setup.originX = v;
            this.refreshTypes();
        });
        this.originY = new UITrackpad((v) ->
        {
            this.setup.originY = v;
            this.refreshTypes();
        });
        this.originZ = new UITrackpad((v) ->
        {
            this.setup.originZ = v;
            this.refreshTypes();
        });
        this.originX.tooltip(UIKeys.GENERAL_X);
        this.originY.tooltip(UIKeys.GENERAL_Y);
        this.originZ.tooltip(UIKeys.GENERAL_Z);
        this.originX.w(COORD_WIDTH).h(20);
        this.originY.w(COORD_WIDTH).h(20);
        this.originZ.w(COORD_WIDTH).h(20);
        this.coordsRow = UI.row(6, 0, 20, this.originX, this.originY, this.originZ);
        this.coordsRow.w(1F);

        this.conditionsScroll = UI.scrollView(8, 4,
            this.radiusLabel,
            this.radius,
            this.includeHeight,
            this.originLabel,
            this.originMode,
            this.coordsRow,
            this.capturePlayers,
            this.playerNametags,
            this.playerModelForms
        );
        this.conditionsScroll.relative(this.conditionsPage).x(0).w(1F);

        this.conditionsSummaryHeader = this.createSectionLabel(UIKeys.FILM_MOB_CAPTURE_SECTION_CONDITIONS);
        this.conditionsSummaryHeader.w(1F).h(14);
        this.summaryRadius = this.createSummaryLabel();
        this.summaryHeight = this.createSummaryLabel();
        this.summaryOrigin = this.createSummaryLabel();
        this.summaryCoords = this.createSummaryLabel();
        this.summaryPlayers = this.createSummaryLabel();
        this.summaryNametags = this.createSummaryLabel();
        this.summaryModels = this.createSummaryLabel();
        this.seeMore = this.createSeeMoreLink();
        this.summaryLeft = UI.column(SUMMARY_ROW_GAP, this.summaryRadius, this.summaryHeight, this.summaryOrigin, this.summaryCoords);
        this.summaryRight = UI.column(SUMMARY_ROW_GAP, this.summaryPlayers, this.summaryNametags, this.summaryModels);
        this.summaryGrid = new UIElement();
        this.summaryLeft.relative(this.summaryGrid).x(0).y(0).w(0.5F, -4);
        this.summaryRight.relative(this.summaryGrid).x(0.5F, 4).y(0).w(0.5F, -4);
        this.summaryGrid.add(this.summaryLeft, this.summaryRight);
        this.summaryGrid.w(1F);
        this.conditionsSummary = UI.column(SUMMARY_ROW_GAP,
            this.conditionsSummaryHeader,
            this.summaryGrid,
            this.seeMore
        );
        this.conditionsSummary.relative(this.entitiesPage).x(0).w(1F);

        this.entitiesTitle = this.createSectionLabel(UIKeys.FILM_MOB_CAPTURE_SECTION_ENTITIES);
        this.entitiesTitle.w(1F);
        this.refresh = new UIIcon(Icons.REFRESH, (b) -> this.refreshEntityList());
        this.refresh.tooltip(UIKeys.FILM_MOB_CAPTURE_REFRESH);
        this.entitiesHeader = UI.row(4, 0, 16, this.entitiesTitle, this.refresh);
        this.entitiesHeader.relative(this.entitiesPage).x(0).w(1F).h(16);

        this.summary = UI.label(IKey.EMPTY).color(Colors.LIGHTER_GRAY);
        this.summary.relative(this.entitiesPage).x(0).w(1F).h(14);

        this.scroll = UI.scrollView(4, 4);
        this.scroll.relative(this.entitiesPage).x(0).w(1F);

        this.entitiesPage.add(this.conditionsSummary, this.entitiesHeader, this.summary, this.scroll);
        this.conditionsPage.add(this.conditionsHeader, this.conditionsScroll);

        UIButton start = new UIButton(UIKeys.FILM_MOB_CAPTURE_START, (b) -> this.submit());
        UIButton cancel = new UIButton(UIKeys.CONFIG_CANCEL, (b) -> this.cancel());

        cancel.w(FOOTER_BUTTON_WIDTH).h(20);
        start.w(FOOTER_BUTTON_WIDTH).h(20);
        this.footer = UI.row(FOOTER_GAP, 0, 20, cancel, start);
        this.footer.w(FOOTER_BUTTON_WIDTH * 2 + FOOTER_GAP).h(20);
        this.footer.relative(this.content).x(0.5F).y(1F, -12).anchor(0.5F, 1F);

        this.content.add(
            this.description,
            this.tabBar,
            this.entitiesPage,
            this.conditionsPage,
            this.footer
        );

        this.onClose((event) ->
        {
            this.commitConditionsFromUi();
            this.setup.saveToPreferences();
            this.releaseRecordingPause();

            if (opened == this)
            {
                opened = null;
            }
        });

        this.holdRecordingPause();

        if (this.setup.usePlayerOrigin)
        {
            this.seedOriginFromPlayer();
        }

        this.updateOriginModeUi();
        this.setTab(CaptureTab.ENTITIES);
    }

    private void holdRecordingPause()
    {
        if (!this.recordingPauseHeld)
        {
            RecordingPauseHelper.push();
            this.recordingPauseHeld = true;
        }
    }

    private void releaseRecordingPause()
    {
        if (this.recordingPauseHeld)
        {
            RecordingPauseHelper.pop();
            this.recordingPauseHeld = false;
        }
    }

    private UILabel createSectionLabel(IKey title)
    {
        return new UILabel(title)
        {
            @Override
            public void render(UIContext context)
            {
                FontRenderer font = context.batcher.getFont();
                String text = "\u00A7l" + this.label.get();
                int x = this.area.x(this.anchorX, font.getWidth(text));
                int y = this.area.y(this.anchorY, font.getHeight()) + this.textOffsetY;

                context.batcher.text(text, x, y, this.color, this.textShadow);
            }
        }.color(Colors.WHITE);
    }

    private IKey getOriginModeLabel()
    {
        return this.setup.usePlayerOrigin
            ? UIKeys.FILM_MOB_CAPTURE_ORIGIN_PLAYER
            : UIKeys.FILM_MOB_CAPTURE_ORIGIN_COORDS;
    }

    private void toggleOriginMode()
    {
        this.setup.usePlayerOrigin = !this.setup.usePlayerOrigin;

        if (!this.setup.usePlayerOrigin)
        {
            this.seedOriginFromPlayer();
            this.syncOriginFieldsFromSetup();
        }

        this.updateOriginModeUi();
        this.refreshTypes();
    }

    private void updateOriginModeUi()
    {
        this.originMode.label = this.getOriginModeLabel();
        this.updateOriginFieldsEnabled();

        if (!this.setup.usePlayerOrigin)
        {
            this.syncOriginFieldsFromSetup();
        }
        else
        {
            this.syncOriginFieldsFromPlayer();
        }
    }

    private void updateOriginFieldsEnabled()
    {
        boolean coordsEnabled = !this.setup.usePlayerOrigin;

        this.originX.setEnabled(coordsEnabled);
        this.originY.setEnabled(coordsEnabled && this.setup.includeHeight);
        this.originZ.setEnabled(coordsEnabled);
    }

    private void seedOriginFromPlayer()
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null)
        {
            return;
        }

        this.setup.originX = player.getX();
        this.setup.originY = player.getY();
        this.setup.originZ = player.getZ();
    }

    private void syncOriginFieldsFromSetup()
    {
        this.originX.setValue(this.setup.originX);
        this.originY.setValue(this.setup.originY);
        this.originZ.setValue(this.setup.originZ);
    }

    private void syncOriginFieldsFromPlayer()
    {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        if (player == null)
        {
            return;
        }

        this.originX.setValue(player.getX());
        this.originY.setValue(player.getY());
        this.originZ.setValue(player.getZ());
    }

    private void updateLayout()
    {
        int y = 8;

        this.description.y(y);
        y += 40;

        this.tabBar.y(y);
        y += TAB_HEIGHT + 8;

        this.entitiesPage.y(y).h(1F, -(y + FOOTER_SPACE));
        this.conditionsPage.y(y).h(1F, -(y + FOOTER_SPACE));

        boolean showSummary = BBSSettings.recordingMobCaptureConditionsSummary == null
            || BBSSettings.recordingMobCaptureConditionsSummary.get();

        this.conditionsSummary.setVisible(showSummary);

        int pageY = 0;
        int summaryH = showSummary ? this.applySummaryWrapHeights() : 0;

        if (showSummary)
        {
            this.conditionsSummary.y(pageY).h(summaryH);
            pageY += summaryH + 8;
        }

        this.entitiesHeader.y(pageY);
        pageY += 18;

        this.summary.y(pageY);
        pageY += 16;

        this.scroll.y(pageY).h(1F, -pageY);

        this.conditionsHeader.y(0);
        this.conditionsScroll.y(18).h(1F, -18);
        this.content.resize();

        if (showSummary)
        {
            int resizedSummaryH = this.applySummaryWrapHeights();

            if (resizedSummaryH != summaryH)
            {
                this.conditionsSummary.h(resizedSummaryH);
                pageY = resizedSummaryH + 8;
                this.entitiesHeader.y(pageY);
                pageY += 18;
                this.summary.y(pageY);
                pageY += 16;
                this.scroll.y(pageY).h(1F, -pageY);
                this.content.resize();
            }
        }
    }

    private UILabel createSummaryLabel()
    {
        return new SummaryLabel();
    }

    private int applySummaryWrapHeights()
    {
        int pageW = this.entitiesPage.area.w > 0 ? this.entitiesPage.area.w : 400;
        int columnWidth = Math.max(40, pageW / 2 - 8);
        int leftH = this.measureSummaryColumn(columnWidth, this.summaryRadius, this.summaryHeight, this.summaryOrigin, this.summaryCoords);
        int rightH = this.measureSummaryColumn(columnWidth, this.summaryPlayers, this.summaryNametags, this.summaryModels);
        int gridH = Math.max(leftH, rightH);

        this.summaryLeft.h(leftH);
        this.summaryRight.h(rightH);
        this.summaryGrid.h(gridH);

        /* Header + grid + see more, with the same gap used between summary rows. */
        return 14 + SUMMARY_ROW_GAP + gridH + SUMMARY_ROW_GAP + 14;
    }

    private int measureSummaryColumn(int columnWidth, UILabel... labels)
    {
        int height = 0;

        for (int i = 0; i < labels.length; i++)
        {
            if (i > 0)
            {
                height += SUMMARY_ROW_GAP;
            }

            height += this.applyWrappedHeight(labels[i], columnWidth);
        }

        return height;
    }

    private int applyWrappedHeight(UILabel label, int columnWidth)
    {
        FontRenderer font = Batcher2D.getDefaultTextRenderer();
        String text = label.label == null ? "" : label.label.get();
        List<String> lines = font.wrap(text, Math.max(1, columnWidth));
        int lineCount = Math.max(1, lines.isEmpty() ? 1 : lines.size());
        int wrapAdvance = SUMMARY_LINE_HEIGHT + SUMMARY_ROW_GAP;
        int height = lineCount <= 1
            ? SUMMARY_LINE_HEIGHT
            : (lineCount - 1) * wrapAdvance + SUMMARY_LINE_HEIGHT;

        label.h(height);

        return height;
    }

    private void setTab(CaptureTab tab)
    {
        if (this.currentTab == CaptureTab.CONDITIONS)
        {
            this.commitConditionsFromUi();
        }

        this.currentTab = tab;
        this.entitiesTab.setSelected(tab == CaptureTab.ENTITIES);
        this.conditionsTab.setSelected(tab == CaptureTab.CONDITIONS);
        this.entitiesPage.setVisible(tab == CaptureTab.ENTITIES);
        this.conditionsPage.setVisible(tab == CaptureTab.CONDITIONS);

        if (tab == CaptureTab.ENTITIES)
        {
            this.updateConditionsSummary();
            this.refreshTypes();
        }

        this.updateLayout();
    }

    private IKey yesNo(boolean value)
    {
        return value ? UIKeys.FILM_MOB_CAPTURE_VALUE_YES : UIKeys.FILM_MOB_CAPTURE_VALUE_NO;
    }

    private void updateConditionsSummary()
    {
        this.summaryRadius.label(UIKeys.FILM_MOB_CAPTURE_SUMMARY_RADIUS.format(String.valueOf((int) this.setup.areaSize)));
        this.summaryHeight.label(UIKeys.FILM_MOB_CAPTURE_SUMMARY_HEIGHT.format(this.yesNo(this.setup.includeHeight).get()));
        this.summaryOrigin.label(UIKeys.FILM_MOB_CAPTURE_SUMMARY_ORIGIN.format(this.getOriginModeLabel().get()));
        this.summaryCoords.label(UIKeys.FILM_MOB_CAPTURE_SUMMARY_COORDS.format(
            String.valueOf((int) this.setup.originX),
            String.valueOf((int) this.setup.originY),
            String.valueOf((int) this.setup.originZ)
        ));
        this.summaryPlayers.label(UIKeys.FILM_MOB_CAPTURE_SUMMARY_PLAYERS.format(this.yesNo(this.setup.capturePlayers).get()));
        this.summaryNametags.label(UIKeys.FILM_MOB_CAPTURE_SUMMARY_NAMETAGS.format(this.yesNo(this.setup.playerNametags).get()));
        this.summaryModels.label(UIKeys.FILM_MOB_CAPTURE_SUMMARY_MODELS.format(this.yesNo(this.setup.playerModelForms).get()));
    }

    private UIElement createSeeMoreLink()
    {
        return new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                FontRenderer font = context.batcher.getFont();
                String text = UIKeys.FILM_MOB_CAPTURE_SEE_MORE.get();
                boolean hover = this.area.isInside(context);
                int color = hover ? (0xFF000000 | BBSSettings.primaryColor.get()) : Colors.LIGHTER_GRAY;
                int x = this.area.x;
                int y = this.area.y(0.5F, font.getHeight());
                int width = font.getWidth(text);

                context.batcher.text(text, x, y, color, true);

                if (hover)
                {
                    context.batcher.box(x, y + font.getHeight(), x + width, y + font.getHeight() + 1, color);
                    context.requestCursor(GLFW.GLFW_HAND_CURSOR);
                }

                super.render(context);
            }

            @Override
            protected boolean subMouseClicked(UIContext context)
            {
                if (context.mouseButton == 0 && this.area.isInside(context))
                {
                    UIUtils.playClick();
                    UIMobCaptureRecordOverlayPanel.this.setTab(CaptureTab.CONDITIONS);

                    return true;
                }

                return super.subMouseClicked(context);
            }
        }.h(14);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.setup.usePlayerOrigin)
        {
            this.syncOriginFieldsFromPlayer();

            if (this.currentTab == CaptureTab.ENTITIES)
            {
                this.updateConditionsSummary();
            }
        }

        super.render(context);
    }

    private Vec3d getScanOrigin()
    {
        if (this.setup.usePlayerOrigin)
        {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;

            return player == null ? Vec3d.ZERO : player.getEntityPos();
        }

        return new Vec3d(this.setup.originX, this.setup.originY, this.setup.originZ);
    }

    /**
     * Commit focused trackpads and copy their current values into {@link #setup}
     * before rescanning, so Refresh always uses what the user sees in the fields.
     */
    private void commitConditionsFromUi()
    {
        UIContext context = this.getContext();

        if (context != null && context.isFocused())
        {
            context.unfocus();
        }

        this.setup.areaSize = this.radius.getValue();
        this.setup.includeHeight = this.includeHeight.getValue();
        this.setup.capturePlayers = this.capturePlayers.getValue();
        this.setup.playerNametags = this.playerNametags.getValue();
        this.setup.playerModelForms = this.playerModelForms.getValue();

        if (!this.setup.usePlayerOrigin)
        {
            this.setup.originX = this.originX.getValue();
            this.setup.originY = this.originY.getValue();
            this.setup.originZ = this.originZ.getValue();
        }
    }

    private void refreshEntityList()
    {
        this.commitConditionsFromUi();
        this.refreshTypes();
    }

    private void clearScrollChildren()
    {
        this.scroll.getChildren().clear();
    }

    private void refreshTypes()
    {
        /* Keep setup in sync even when refresh comes from a trackpad callback. */
        if (!this.setup.usePlayerOrigin)
        {
            this.setup.originX = this.originX.getValue();
            this.setup.originY = this.originY.getValue();
            this.setup.originZ = this.originZ.getValue();
        }

        this.setup.areaSize = this.radius.getValue();
        this.setup.includeHeight = this.includeHeight.getValue();
        this.setup.capturePlayers = this.capturePlayers.getValue();
        this.setup.playerNametags = this.playerNametags.getValue();
        this.setup.playerModelForms = this.playerModelForms.getValue();
        this.lastBuckets = MobCaptureAreaScanner.scan(this.setup);
        this.removeDisallowedVanillaSelections();
        this.clearScrollChildren();
        this.updateColumnMetrics();

        int total = 0;

        for (MobCaptureAreaScanner.TypeBucket bucket : this.lastBuckets.values())
        {
            total += bucket.entities.size();
        }

        if (this.lastBuckets.isEmpty())
        {
            this.summary.label = UIKeys.FILM_MOB_CAPTURE_EMPTY;
            this.scroll.resize();

            return;
        }

        this.summary.label = UIKeys.FILM_MOB_CAPTURE_SUMMARY.format(String.valueOf(total), String.valueOf(this.lastBuckets.size()));

        this.addColumnHeaderRow();
        this.addSelectAllRow();

        Vec3d origin = this.getScanOrigin();

        for (MobCaptureAreaScanner.TypeBucket bucket : this.lastBuckets.values())
        {
            String typeId = bucket.typeId;
            boolean typeExpanded = this.expandedTypes.getOrDefault(typeId, false);
            boolean typeSelected = this.isTypeFullySelected(bucket);
            boolean typeVanillaAllowed = this.isTypeVanillaAllowed(bucket);
            String typeLabel = bucket.label + " (" + bucket.entities.size() + ")";
            UIIcon typeIcon = new UIIcon(typeExpanded ? Icons.ARROW_DOWN : Icons.ARROW_RIGHT, (b) ->
            {
                this.expandedTypes.put(typeId, !this.expandedTypes.getOrDefault(typeId, false));
                this.refreshTypes();
            });
            UIElement typeName = UI.label(IKey.raw(typeLabel)).w(1F);
            UIToggle typeSelectToggle = new UIToggle(IKey.EMPTY, typeSelected, (b) ->
            {
                this.setTypeSelected(bucket, b.getValue());
                this.syncTypeSelection(bucket);
                this.refreshTypes();
            });
            UIToggle typeVanillaToggle = new UIToggle(IKey.EMPTY, this.setup.vanillaPlaybackTypeIds.contains(typeId), (b) ->
            {
                this.setTypeVanilla(bucket, b.getValue());
                this.refreshTypes();
            });

            typeSelectToggle.w(this.addColumnWidth);
            typeVanillaToggle.w(this.vaColumnWidth);
            typeVanillaToggle.tooltip(UIKeys.FILM_REPLAY_VANILLA_MOB_PLAYBACK_TOOLTIP);
            typeVanillaToggle.setEnabled(typeSelected && typeVanillaAllowed);

            UIElement typeRow = this.createEntityListRow(20, typeIcon, typeName, typeSelectToggle, typeVanillaToggle, this.createScrollbarGutter(20));

            this.scroll.add(typeRow);
            this.syncTypeSelection(bucket);

            if (typeExpanded)
            {
                int index = 0;

                for (Entity entity : bucket.entities)
                {
                    int entityId = entity.getId();
                    boolean entitySelected = this.setup.selectedEntityIds.contains(entityId);
                    boolean entityVanillaAllowed = this.isVanillaPlaybackAllowed(entity);
                    String entityLabel = MobCaptureAreaScanner.getEntityLabel(
                        entity, index, origin.x, origin.y, origin.z, this.setup.includeHeight
                    );
                    UIElement entityName = UI.label(IKey.raw(entityLabel)).w(1F);
                    UIToggle entitySelectToggle = new UIToggle(IKey.EMPTY, entitySelected, (b) ->
                    {
                        if (b.getValue())
                        {
                            this.setup.selectedEntityIds.add(entityId);
                        }
                        else
                        {
                            this.setup.selectedEntityIds.remove(entityId);
                        }

                        this.syncTypeSelection(bucket);
                        this.refreshTypes();
                    });
                    UIToggle entityVanillaToggle = new UIToggle(IKey.EMPTY, this.setup.vanillaPlaybackEntityIds.contains(entityId), (b) ->
                    {
                        if (b.getValue())
                        {
                            this.setup.vanillaPlaybackEntityIds.add(entityId);
                        }
                        else
                        {
                            this.setup.vanillaPlaybackEntityIds.remove(entityId);
                        }

                        this.syncTypeVanilla(bucket);
                        this.refreshTypes();
                    });

                    entitySelectToggle.w(this.addColumnWidth);
                    entityVanillaToggle.w(this.vaColumnWidth);
                    entityVanillaToggle.tooltip(UIKeys.FILM_REPLAY_VANILLA_MOB_PLAYBACK_TOOLTIP);
                    entityVanillaToggle.setEnabled(entitySelected && entityVanillaAllowed);

                    UIElement entityIndent = new UIElement();

                    entityIndent.w(ICON_COLUMN_WIDTH).h(20);

                    UIElement entityRow = this.createEntityListRow(20, entityIndent, entityName, entitySelectToggle, entityVanillaToggle, this.createScrollbarGutter(20));

                    this.scroll.add(entityRow);
                    index += 1;
                }
            }
        }

        this.scroll.resize();
    }

    private UIElement createEntityListRow(int height, UIElement... children)
    {
        UIElement row = new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                if (this.area.isInside(context))
                {
                    context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), ENTITY_ROW_HOVER);
                }

                super.render(context);
            }
        };

        row.row(4).padding(0).height(height);

        for (UIElement child : children)
        {
            child.h(height);

            if (child instanceof UILabel)
            {
                ((UILabel) child).labelAnchor(0F, 0.5F);
            }
        }

        row.add(children);
        row.w(1F).h(height);

        return row;
    }

    private UIElement createScrollbarGutter(int height)
    {
        return new UIElement().w(SCROLLBAR_GUTTER).h(height);
    }

    private void updateColumnMetrics()
    {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        String addText = UIKeys.FILM_MOB_CAPTURE_COLUMN_ADD.get();
        String vaText = UIKeys.FILM_MOB_CAPTURE_COLUMN_VA.get();

        this.addColumnWidth = this.measureColumnWidth(font, addText);
        this.vaColumnWidth = this.measureColumnWidth(font, vaText);
        this.columnHeaderHeight = Math.max(
            this.measureColumnHeaderHeight(font, addText, this.addColumnWidth),
            this.measureColumnHeaderHeight(font, vaText, this.vaColumnWidth)
        );
    }

    private int measureColumnWidth(TextRenderer font, String text)
    {
        int natural = font.getWidth(text) + 4;

        if (natural <= COLUMN_HEADER_MAX_WIDTH)
        {
            return Math.max(TOGGLE_WIDTH, natural);
        }

        return COLUMN_HEADER_MAX_WIDTH;
    }

    private int measureColumnHeaderHeight(TextRenderer font, String text, int width)
    {
        List<String> lines = FontRenderer.wrap(font, text, Math.max(1, width - 2));

        return Math.max(14, lines.size() * font.fontHeight + 2);
    }

    private UIElement createWrappedColumnHeader(IKey key, int width, int height)
    {
        return new UIElement()
        {
            @Override
            public void render(UIContext context)
            {
                FontRenderer font = context.batcher.getFont();
                List<String> lines = FontRenderer.wrap(font.getRenderer(), key.get(), Math.max(1, this.area.w - 2));
                int lineH = font.getHeight();
                int totalH = lines.size() * lineH;
                int y = this.area.my() - totalH / 2;

                for (String line : lines)
                {
                    int x = this.area.mx() - font.getWidth(line) / 2;

                    context.batcher.text(line, x, y, Colors.LIGHTER_GRAY, true);
                    y += lineH;
                }

                super.render(context);
            }
        }.w(width).h(height);
    }

    private void addColumnHeaderRow()
    {
        UIElement spacer = new UIElement();

        spacer.w(ICON_COLUMN_WIDTH).h(this.columnHeaderHeight);

        UIElement nameSpacer = new UIElement();

        nameSpacer.w(1F).h(this.columnHeaderHeight);

        UIElement addHeader = this.createWrappedColumnHeader(UIKeys.FILM_MOB_CAPTURE_COLUMN_ADD, this.addColumnWidth, this.columnHeaderHeight);
        UIElement vanillaHeader = this.createWrappedColumnHeader(UIKeys.FILM_MOB_CAPTURE_COLUMN_VA, this.vaColumnWidth, this.columnHeaderHeight);

        vanillaHeader.tooltip(UIKeys.FILM_REPLAY_VANILLA_MOB_PLAYBACK_TOOLTIP);

        UIElement headerRow = UI.row(4, 0, this.columnHeaderHeight, spacer, nameSpacer, addHeader, vanillaHeader, this.createScrollbarGutter(this.columnHeaderHeight));

        headerRow.w(1F).h(this.columnHeaderHeight);
        this.scroll.add(headerRow);
    }

    private void addSelectAllRow()
    {
        UIElement spacer = new UIElement();

        spacer.w(ICON_COLUMN_WIDTH).h(20);

        UILabel selectAllLabel = UI.label(UIKeys.FILM_MOB_CAPTURE_SELECT_ALL);

        selectAllLabel.w(1F).h(20);

        boolean allSelected = this.isAllSelected();
        UIToggle selectAllAdd = new UIToggle(IKey.EMPTY, allSelected, (b) ->
        {
            this.setAllSelected(b.getValue());
            this.refreshTypes();
        });
        UIToggle selectAllVanilla = new UIToggle(IKey.EMPTY, this.isAllVanilla(), (b) ->
        {
            this.setAllVanilla(b.getValue());
            this.refreshTypes();
        });

        selectAllAdd.w(this.addColumnWidth);
        selectAllVanilla.w(this.vaColumnWidth);
        selectAllVanilla.tooltip(UIKeys.FILM_REPLAY_VANILLA_MOB_PLAYBACK_TOOLTIP);
        selectAllVanilla.setEnabled(allSelected && this.hasAnyVanillaPlaybackAllowed());

        UIElement selectAllRow = this.createEntityListRow(20, spacer, selectAllLabel, selectAllAdd, selectAllVanilla, this.createScrollbarGutter(20));

        this.scroll.add(selectAllRow);
    }

    private boolean isAllSelected()
    {
        if (this.lastBuckets.isEmpty())
        {
            return false;
        }

        for (MobCaptureAreaScanner.TypeBucket bucket : this.lastBuckets.values())
        {
            for (Entity entity : bucket.entities)
            {
                if (!this.setup.selectedEntityIds.contains(entity.getId()))
                {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean isAllVanilla()
    {
        if (this.lastBuckets.isEmpty())
        {
            return false;
        }

        boolean hasAllowed = false;

        for (MobCaptureAreaScanner.TypeBucket bucket : this.lastBuckets.values())
        {
            for (Entity entity : bucket.entities)
            {
                if (!this.isVanillaPlaybackAllowed(entity))
                {
                    continue;
                }

                hasAllowed = true;

                if (!this.setup.vanillaPlaybackEntityIds.contains(entity.getId()))
                {
                    return false;
                }
            }
        }

        return hasAllowed;
    }

    private void setAllSelected(boolean selected)
    {
        this.setup.selectedEntityIds.clear();
        this.setup.selectedTypeIds.clear();

        if (!selected)
        {
            return;
        }

        for (MobCaptureAreaScanner.TypeBucket bucket : this.lastBuckets.values())
        {
            this.setup.selectedTypeIds.add(bucket.typeId);

            for (Entity entity : bucket.entities)
            {
                this.setup.selectedEntityIds.add(entity.getId());
            }
        }
    }

    private void setAllVanilla(boolean enabled)
    {
        this.setup.vanillaPlaybackEntityIds.clear();
        this.setup.vanillaPlaybackTypeIds.clear();

        if (!enabled)
        {
            return;
        }

        for (MobCaptureAreaScanner.TypeBucket bucket : this.lastBuckets.values())
        {
            if (!this.isTypeVanillaAllowed(bucket))
            {
                continue;
            }

            this.setup.vanillaPlaybackTypeIds.add(bucket.typeId);

            for (Entity entity : bucket.entities)
            {
                if (!this.isVanillaPlaybackAllowed(entity))
                {
                    continue;
                }

                this.setup.vanillaPlaybackEntityIds.add(entity.getId());
            }
        }
    }

    private boolean isTypeFullySelected(MobCaptureAreaScanner.TypeBucket bucket)
    {
        if (bucket.entities.isEmpty())
        {
            return false;
        }

        for (Entity entity : bucket.entities)
        {
            if (!this.setup.selectedEntityIds.contains(entity.getId()))
            {
                return false;
            }
        }

        return true;
    }

    private void setTypeSelected(MobCaptureAreaScanner.TypeBucket bucket, boolean selected)
    {
        for (Entity entity : bucket.entities)
        {
            if (selected)
            {
                this.setup.selectedEntityIds.add(entity.getId());
            }
            else
            {
                this.setup.selectedEntityIds.remove(entity.getId());
            }
        }
    }

    private void syncTypeSelection(MobCaptureAreaScanner.TypeBucket bucket)
    {
        if (this.isTypeFullySelected(bucket))
        {
            this.setup.selectedTypeIds.add(bucket.typeId);
        }
        else
        {
            this.setup.selectedTypeIds.remove(bucket.typeId);
        }
    }

    private void setTypeVanilla(MobCaptureAreaScanner.TypeBucket bucket, boolean enabled)
    {
        if (enabled)
        {
            this.setup.vanillaPlaybackTypeIds.add(bucket.typeId);

            for (Entity entity : bucket.entities)
            {
                if (!this.isVanillaPlaybackAllowed(entity))
                {
                    continue;
                }

                this.setup.vanillaPlaybackEntityIds.add(entity.getId());
            }
        }
        else
        {
            this.setup.vanillaPlaybackTypeIds.remove(bucket.typeId);

            for (Entity entity : bucket.entities)
            {
                this.setup.vanillaPlaybackEntityIds.remove(entity.getId());
            }
        }
    }

    private void syncTypeVanilla(MobCaptureAreaScanner.TypeBucket bucket)
    {
        if (this.isTypeFullyVanilla(bucket))
        {
            this.setup.vanillaPlaybackTypeIds.add(bucket.typeId);
        }
        else
        {
            this.setup.vanillaPlaybackTypeIds.remove(bucket.typeId);
        }
    }

    private boolean isTypeFullyVanilla(MobCaptureAreaScanner.TypeBucket bucket)
    {
        if (bucket.entities.isEmpty())
        {
            return false;
        }

        boolean hasAllowed = false;

        for (Entity entity : bucket.entities)
        {
            if (!this.isVanillaPlaybackAllowed(entity))
            {
                continue;
            }

            hasAllowed = true;

            if (!this.setup.vanillaPlaybackEntityIds.contains(entity.getId()))
            {
                return false;
            }
        }

        return hasAllowed;
    }

    private boolean isTypeVanillaAllowed(MobCaptureAreaScanner.TypeBucket bucket)
    {
        for (Entity entity : bucket.entities)
        {
            if (this.isVanillaPlaybackAllowed(entity))
            {
                return true;
            }
        }

        return false;
    }

    private boolean hasAnyVanillaPlaybackAllowed()
    {
        for (MobCaptureAreaScanner.TypeBucket bucket : this.lastBuckets.values())
        {
            if (this.isTypeVanillaAllowed(bucket))
            {
                return true;
            }
        }

        return false;
    }

    private boolean isVanillaPlaybackAllowed(Entity entity)
    {
        return !(entity instanceof PlayerEntity) || !this.setup.playerModelForms;
    }

    private void removeDisallowedVanillaSelections()
    {
        for (MobCaptureAreaScanner.TypeBucket bucket : this.lastBuckets.values())
        {
            for (Entity entity : bucket.entities)
            {
                if (!this.isVanillaPlaybackAllowed(entity))
                {
                    this.setup.vanillaPlaybackEntityIds.remove(entity.getId());
                }
            }

            if (!this.isTypeFullyVanilla(bucket))
            {
                this.setup.vanillaPlaybackTypeIds.remove(bucket.typeId);
            }
        }
    }

    private void cancel()
    {
        UIContext context = this.getContext();
        Runnable onCancel = this.onCancel;

        /* Release before close/callback so deferred UI animations cannot leave
         * the integrated server frozen after recording starts or the screen swaps. */
        this.releaseRecordingPause();
        this.close();

        if (onCancel != null && context != null)
        {
            onCancel.run();
        }
    }

    private void submit()
    {
        this.setup.captureMobs = true;
        this.commitConditionsFromUi();
        MobCaptureRecordingSetup.pending = this.setup;
        this.releaseRecordingPause();
        this.close();

        if (this.callback != null)
        {
            this.callback.accept(this.setup);
        }
    }

    private static class SummaryLabel extends UILabel
    {
        public SummaryLabel()
        {
            super(IKey.EMPTY, Colors.LIGHTER_GRAY);
            this.h(SUMMARY_LINE_HEIGHT);
            this.w(1F);
        }

        @Override
        public void render(UIContext context)
        {
            FontRenderer font = context.batcher.getFont();
            String text = this.label == null ? "" : this.label.get();
            List<String> lines = text.isEmpty() || this.area.w <= 0
                ? Collections.emptyList()
                : font.wrap(text, Math.max(1, this.area.w));
            int y = this.area.y + this.textOffsetY;
            int wrapAdvance = SUMMARY_LINE_HEIGHT + SUMMARY_ROW_GAP;

            for (String line : lines)
            {
                context.batcher.text(line, this.area.x, y, this.color, this.textShadow);
                y += wrapAdvance;
            }

            if (this.tooltip != null && this.area.isInside(context))
            {
                context.tooltip.set(context, this);
            }
        }
    }

    private static class OverlayTabButton extends UIClickable<OverlayTabButton>
    {
        private final IKey label;
        private final Icon icon;
        private boolean selected;

        public OverlayTabButton(IKey label, Icon icon, Consumer<OverlayTabButton> callback)
        {
            super(callback);

            this.label = label;
            this.icon = icon;
            this.h(TAB_HEIGHT);
            this.w(22 + MinecraftClient.getInstance().textRenderer.getWidth(label.get()) + 8);
        }

        public void setSelected(boolean selected)
        {
            this.selected = selected;
        }

        @Override
        protected OverlayTabButton get()
        {
            return this;
        }

        @Override
        protected void renderSkin(UIContext context)
        {
            int color = this.hover || this.selected ? (0xFF000000 | BBSSettings.primaryColor.get()) : Colors.WHITE;
            int textY = this.area.y + (this.area.h - context.batcher.getFont().getHeight()) / 2;

            if (this.selected)
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xFF1D1D1D);
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + 2, 0xFF000000 | BBSSettings.primaryColor.get());
            }
            else
            {
                context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xDD17171B);
            }

            context.batcher.icon(this.icon, color, this.area.x + 11, this.area.y + this.area.h / 2, 0.5F, 0.5F);
            context.batcher.text(this.label.get(), this.area.x + 22, textY, color);
        }
    }
}
