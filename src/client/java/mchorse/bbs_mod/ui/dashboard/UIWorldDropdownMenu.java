package mchorse.bbs_mod.ui.dashboard;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UIPoseSectionCollapse;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIConfirmOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.interps.Lerps;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

/* GameRules was restructured in 1.21.11; WorldPropertiesHelper.readGamerule now accepts String keys directly */

/**
 * Animated World dropdown under the main-menu World button (Time / Weather / Mobs / Gamma).
 */
public class UIWorldDropdownMenu extends UIContextMenu
{
    private static final long THROTTLE_MS = 10L;
    private static final long ANIM_DURATION_NS = 220_000_000L;
    private static final int MENU_WIDTH = 270;
    private static final int MIN_HEIGHT = 114;
    private static final int TIME_COLOR = 0x3aa0ff;
    private static final int WEATHER_COLOR = 0x2f8f72;
    private static final int MOBS_COLOR = 0xe07a3a;
    private static final int GAMMA_COLOR = 0xd4b23a;
    private static final int KILL_MOBS_SUCCESS_GREEN = 0x1B6620;

    private static int openCount;

    private final UIScrollView scroll;
    private long animStartNs;
    private float animFrom;
    private float animTo = 1F;
    private boolean closing;
    private boolean forceDetach;

    private UIToggle freezeTime;
    private UITrackpad time;
    private UITrackpad sunPathRotation;
    private UIToggle pauseWeather;
    private UIToggle mobSpawning;
    private UIButton killMobs;
    private UIToggle overrideGamma;
    private UITrackpad gamma;
    private UIToggle nightVision;

    private int pendingTime = -1;
    private int lastSentTime = -1;
    private long lastSentAt;
    private int killMobsHighlightColor;
    private int maxHeight = 400;

    public static boolean isOpen()
    {
        return openCount > 0;
    }

    public UIWorldDropdownMenu()
    {
        super();

        openCount++;
        this.animStartNs = System.nanoTime();
        this.animFrom = 0F;
        this.animTo = 1F;
        this.w(MENU_WIDTH).h(MIN_HEIGHT);

        this.freezeTime = new UIToggle(UIKeys.WORLD_FREEZE_TIME, !WorldPropertiesHelper.readGamerule("doDaylightCycle", true), (b) ->
            WorldPropertiesHelper.setGamerule("doDaylightCycle", !b.getValue()));

        this.time = new UITrackpad((v) -> this.pendingTime = (int) v.doubleValue());
        this.time.limit(0D, 24000D, true).increment(100D).values(100D, 10D, 1000D);

        this.sunPathRotation = new UITrackpad((v) -> WorldPropertiesHelper.setSunPathRotation(v.floatValue()));
        this.sunPathRotation.limit(-180D, 180D).increment(5D).values(5D, 1D, 15D);
        this.sunPathRotation.setValue(WorldPropertiesHelper.getSunPathRotation());

        UIButton day = new UIButton(UIKeys.WORLD_TIME_DAY, (b) -> this.setTime(1000));
        UIButton noon = new UIButton(UIKeys.WORLD_TIME_NOON, (b) -> this.setTime(6000));
        UIButton night = new UIButton(UIKeys.WORLD_TIME_NIGHT, (b) -> this.setTime(13000));
        UIButton midnight = new UIButton(UIKeys.WORLD_TIME_MIDNIGHT, (b) -> this.setTime(18000));

        UIElement timeContent = UI.column(5, 0,
            this.freezeTime,
            UI.label(UIKeys.WORLD_TIME_LABEL).marginTop(4),
            this.time,
            UI.row(4, day, noon, night, midnight),
            UI.label(UIKeys.WORLD_SUN_PATH_ROTATION).marginTop(4),
            this.sunPathRotation
        );

        this.pauseWeather = new UIToggle(UIKeys.WORLD_PAUSE_WEATHER, !WorldPropertiesHelper.readGamerule("doWeatherCycle", true), (b) ->
            WorldPropertiesHelper.setGamerule("doWeatherCycle", !b.getValue()));

        UIButton clear = new UIButton(UIKeys.WORLD_WEATHER_CLEAR, (b) -> WorldPropertiesHelper.setWeatherClear());
        UIButton rain = new UIButton(UIKeys.WORLD_WEATHER_RAIN, (b) -> WorldPropertiesHelper.setWeatherRain());
        UIButton thunder = new UIButton(UIKeys.WORLD_WEATHER_THUNDER, (b) -> WorldPropertiesHelper.setWeatherThunder());

        UIElement weatherContent = UI.column(5, 0,
            this.pauseWeather,
            UI.row(4, clear, rain, thunder)
        );

        this.mobSpawning = new UIToggle(UIKeys.WORLD_MOB_SPAWN, WorldPropertiesHelper.readGamerule("doMobSpawning", true), (b) ->
            WorldPropertiesHelper.setGamerule("doMobSpawning", b.getValue()));

        this.killMobs = new UIButton(UIKeys.WORLD_KILL_ALL_MOBS, this::killAllMobsClicked)
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                boolean shiftHover = Window.isShiftPressed() && this.area.isInside(context);
                boolean greenAfter = UIWorldDropdownMenu.this.killMobsHighlightColor != 0;

                if (shiftHover || greenAfter)
                {
                    int bg = shiftHover ? (Colors.RED | Colors.A100) : (UIWorldDropdownMenu.this.killMobsHighlightColor | Colors.A100);

                    this.area.render(context.batcher, bg);

                    FontRenderer font = context.batcher.getFont();
                    String label = font.limitToWidth(this.label.get(), this.area.w - 6);

                    context.batcher.text(label, this.area.mx(font.getWidth(label)), this.area.my(font.getHeight()), Colors.WHITE);

                    return;
                }

                super.renderSkin(context);
            }
        };

        UIElement mobsContent = UI.column(5, 0, this.mobSpawning, this.killMobs);

        this.overrideGamma = new UIToggle(UIKeys.WORLD_GAMMA_OVERRIDE, WorldPropertiesHelper.isGammaOverrideEnabled(), (b) ->
        {
            WorldPropertiesHelper.setGammaOverrideEnabled(b.getValue());
            this.gamma.setEnabled(b.getValue());
        });

        this.gamma = new UITrackpad((v) ->
        {
            this.overrideGamma.setValue(true);
            this.gamma.setEnabled(true);
            WorldPropertiesHelper.setGammaPercent(v);
        });
        this.gamma.limit(0D, 1500D, true).increment(50D).values(10D, 1D, 100D);
        this.gamma.setValue(WorldPropertiesHelper.getGammaPercent());
        this.gamma.setEnabled(WorldPropertiesHelper.isGammaOverrideEnabled());

        UIButton gammaNormal = new UIButton(UIKeys.WORLD_GAMMA_NORMAL, (b) -> this.setGamma(100D));
        UIButton gammaSemi = new UIButton(UIKeys.WORLD_GAMMA_SEMI, (b) -> this.setGamma(750D));
        UIButton gammaFull = new UIButton(UIKeys.WORLD_GAMMA_FULL, (b) -> this.setGamma(1500D));

        this.nightVision = new UIToggle(UIKeys.WORLD_GAMMA_NIGHT_VISION, WorldPropertiesHelper.hasNightVision(), (b) ->
            WorldPropertiesHelper.setNightVision(b.getValue()));

        UIElement gammaContent = UI.column(5, 0,
            this.overrideGamma,
            UI.label(UIKeys.WORLD_GAMMA_LABEL).marginTop(4),
            this.gamma,
            UI.row(4, gammaNormal, gammaSemi, gammaFull),
            this.nightVision
        );

        UIPoseSectionCollapse timeSection = new UIPoseSectionCollapse(UIKeys.WORLD_SECTION_TIME, TIME_COLOR, timeContent);
        UIPoseSectionCollapse weatherSection = new UIPoseSectionCollapse(UIKeys.WORLD_SECTION_WEATHER, WEATHER_COLOR, weatherContent);
        UIPoseSectionCollapse mobsSection = new UIPoseSectionCollapse(UIKeys.WORLD_SECTION_MOBS, MOBS_COLOR, mobsContent);
        UIPoseSectionCollapse gammaSection = new UIPoseSectionCollapse(UIKeys.WORLD_SECTION_GAMMA, GAMMA_COLOR, gammaContent);

        this.scroll = new UIScrollView();
        this.scroll.full(this);
        this.scroll.column(6).vertical().stretch().scroll().padding(8);
        this.scroll.scroll.noScrollbar();
        this.scroll.add(timeSection, weatherSection, mobsSection, gammaSection);

        this.add(this.scroll);
        this.syncFromWorld();
    }

    public void setMaxHeight(int maxHeight)
    {
        this.maxHeight = Math.max(MIN_HEIGHT, maxHeight);
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        /* Position is applied by UIMainMenuBar under the World button. */
        this.w(MENU_WIDTH).h(MIN_HEIGHT);
    }

    @Override
    public void forceClose()
    {
        this.forceDetach = true;
        this.closing = false;

        if (this.hasParent())
        {
            super.removeFromParent();
        }

        this.clearContextMenuRef();
    }

    @Override
    public void removeFromParent()
    {
        if (this.forceDetach)
        {
            super.removeFromParent();
            this.clearContextMenuRef();

            return;
        }

        if (!this.hasParent())
        {
            return;
        }

        this.beginClose();
    }

    private void beginClose()
    {
        if (this.closing || this.forceDetach)
        {
            return;
        }

        this.closing = true;
        this.animFrom = this.getAnimProgress();
        this.animTo = 0F;
        this.animStartNs = System.nanoTime();
        this.scroll.scroll.noScrollbar();
    }

    private void finishClose()
    {
        this.forceDetach = true;
        this.closing = false;

        if (this.hasParent())
        {
            super.removeFromParent();
        }

        this.clearContextMenuRef();
    }

    private void clearContextMenuRef()
    {
        UIContext context = this.getContext();

        if (context != null && context.contextMenu == this)
        {
            context.contextMenu = null;
        }
    }

    private void syncFromWorld()
    {
        ClientWorld world = MinecraftClient.getInstance().world;
        long timeOfDay = world == null ? 6000L : world.getTimeOfDay() % 24000L;

        if (timeOfDay < 0L)
        {
            timeOfDay += 24000L;
        }

        this.lastSentTime = (int) timeOfDay;
        this.pendingTime = this.lastSentTime;
        this.time.setValue(this.lastSentTime);
        WorldPropertiesHelper.setClientTimeOverride(timeOfDay);

        this.freezeTime.setValue(!WorldPropertiesHelper.readGamerule("doDaylightCycle", true));
        this.pauseWeather.setValue(!WorldPropertiesHelper.readGamerule("doWeatherCycle", true));
        this.mobSpawning.setValue(WorldPropertiesHelper.readGamerule("doMobSpawning", true));
        this.overrideGamma.setValue(WorldPropertiesHelper.isGammaOverrideEnabled());
        this.gamma.setEnabled(WorldPropertiesHelper.isGammaOverrideEnabled());
        this.gamma.setValue(WorldPropertiesHelper.getGammaPercent());
        this.nightVision.setValue(WorldPropertiesHelper.hasNightVision());
        this.sunPathRotation.setValue(WorldPropertiesHelper.getSunPathRotation());
    }

    private void setGamma(double percent)
    {
        this.overrideGamma.setValue(true);
        this.gamma.setEnabled(true);
        this.gamma.setValue(percent);
        WorldPropertiesHelper.setGammaPercent(percent);
    }

    private void killAllMobsClicked(UIButton button)
    {
        UIContext context = this.getContext();

        if (context == null)
        {
            return;
        }

        if (Window.isShiftPressed())
        {
            this.executeKillAllMobs();

            return;
        }

        UIOverlay.addOverlay(context, new UIConfirmOverlayPanel(
            UIKeys.WORLD_KILL_ALL_MOBS,
            UIKeys.WORLD_KILL_ALL_MOBS_CONFIRM,
            (confirmed) ->
            {
                if (confirmed)
                {
                    this.executeKillAllMobs();
                }
            }
        ), 260, 140);
    }

    private void executeKillAllMobs()
    {
        this.killMobsHighlightColor = 0;

        WorldPropertiesHelper.killAllMobs((count) ->
        {
            if (count == 0)
            {
                this.killMobsHighlightColor = KILL_MOBS_SUCCESS_GREEN;
            }
        });
    }

    private void setTime(int value)
    {
        this.time.setValue(value);
        this.pendingTime = value;
        this.flushTime(true);
    }

    private void flushTime(boolean force)
    {
        if (this.pendingTime < 0)
        {
            return;
        }

        WorldPropertiesHelper.setClientTimeOverride(this.pendingTime);

        if (this.pendingTime == this.lastSentTime)
        {
            return;
        }

        long now = System.currentTimeMillis();

        if (!force && now - this.lastSentAt < THROTTLE_MS)
        {
            return;
        }

        this.lastSentAt = now;
        this.lastSentTime = this.pendingTime;
        WorldPropertiesHelper.setTimeOfDay(this.pendingTime);
    }

    private float getAnimProgress()
    {
        float t = (System.nanoTime() - this.animStartNs) / (float) ANIM_DURATION_NS;

        if (t >= 1F)
        {
            return this.animTo;
        }

        float eased = Interpolations.QUAD_OUT.interpolate(0F, 1F, MathUtils.clamp(t, 0F, 1F));

        return Lerps.lerp(this.animFrom, this.animTo, eased);
    }

    private void syncAnimatedHeight()
    {
        float progress = this.getAnimProgress();
        int natural = Math.max(MIN_HEIGHT, this.scroll.scroll.scrollSize);
        int target = Math.min(natural, this.maxHeight);
        int animated = Math.max(1, Math.round(target * progress));

        /* Hide scrollbar while open/close tween — scrollSize is full height while the
         * clip is still short, which would otherwise flash a useless bar. */
        boolean animating = this.closing || progress < 0.999F;

        if (animating)
        {
            this.scroll.scroll.scrollbar = false;
        }
        else
        {
            this.scroll.scroll.scrollbar = natural > this.maxHeight;
        }

        if (this.getFlex().h.offset != animated)
        {
            this.h(animated);
            this.resize();
        }
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        float progress = this.getAnimProgress();
        int bg = Colors.setA(0x141418, 0.94F * progress);
        int edge = Colors.setA(0x2A2A35, progress);
        int accent = Colors.setA(BBSSettings.primaryColor.get(), 0.45F * progress);

        context.batcher.dropShadow(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 8, Colors.setA(accent, 0.25F * progress), accent);
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), bg);
        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + 1, edge);
        context.batcher.box(this.area.x, this.area.y, this.area.x + 2, this.area.ey(), Colors.setA(BBSSettings.primaryColor.get(), 0.8F * progress));
    }

    @Override
    public void render(UIContext context)
    {
        this.flushTime(false);
        this.syncAnimatedHeight();

        if (this.closing && this.getAnimProgress() <= 0.001F)
        {
            this.finishClose();

            return;
        }

        context.batcher.clip(this.area.x, this.area.y, this.area.w, this.area.h, context);
        super.render(context);
        context.batcher.unclip(context);
    }

    @Override
    protected void onRemove(UIElement parent)
    {
        openCount = Math.max(0, openCount - 1);
        WorldPropertiesHelper.clearClientTimeOverride();

        super.onRemove(parent);
    }
}
