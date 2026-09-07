package mchorse.bbs_mod.ui.film.replays.overlays;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.Keys;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.keys.KeyAction;
import mchorse.bbs_mod.utils.colors.Colors;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;

import java.util.List;
import java.util.function.Consumer;

public class UIQuickReplayOverlayPanel extends UIOverlayPanel
{
    public final UIList<Replay> list;
    private final Consumer<Replay> callback;
    private boolean centerCursor = true;

    public static void open(UIQuickReplayOverlayPanel panel)
    {
        panel.onClose((event) -> MinecraftClient.getInstance().setScreen(null));

        UIScreen.open(new UIBaseMenu()
        {
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

                UIOverlay.addOverlay(this.context, panel, 240, 0.35F).noBackground();
            }
        });
    }

    public static UIQuickReplayOverlayPanel getOpened()
    {
        Screen currentScreen = MinecraftClient.getInstance().currentScreen;

        if (!(currentScreen instanceof UIScreen uiScreen))
        {
            return null;
        }

        List<UIQuickReplayOverlayPanel> panels = uiScreen.getMenu().getRoot().getChildren(UIQuickReplayOverlayPanel.class);

        return panels.isEmpty() ? null : panels.get(0);
    }

    public static boolean isOpened()
    {
        return getOpened() != null;
    }

    public static void closeOpened()
    {
        UIQuickReplayOverlayPanel opened = getOpened();

        if (opened != null)
        {
            opened.close();
        }
    }

    public static void confirmAndCloseOpened()
    {
        UIQuickReplayOverlayPanel opened = getOpened();

        if (opened != null)
        {
            opened.confirmSelection();
        }
    }

    private void confirmSelection()
    {
        Replay replay = this.list.getCurrentFirst();

        if (replay != null && this.callback != null)
        {
            this.callback.accept(replay);
        }

        this.close();
    }

    private void centerCursor(UIContext context)
    {
        if (context.menu.width <= 0 || context.menu.height <= 0 || this.area.w <= 0 || this.area.h <= 0)
        {
            return;
        }

        net.minecraft.client.util.Window mcWindow = MinecraftClient.getInstance().getWindow();
        double fx = mcWindow.getWidth() / (double) context.menu.width;
        double fy = mcWindow.getHeight() / (double) context.menu.height;
        int x = (int) Math.round(this.area.mx() * fx);
        int y = (int) Math.round(this.area.my() * fy);

        Window.moveCursor(x, y);
        context.mouseX = this.area.mx();
        context.mouseY = this.area.my();
        this.centerCursor = false;
    }

    private boolean handleWheelSelection(UIContext context)
    {
        if (this.list.getList().isEmpty() || !this.list.area.isInside(context) || context.mouseWheel == 0)
        {
            return false;
        }

        int step = context.mouseWheel > 0 ? -1 : 1;
        int index = this.list.getIndex();

        if (index == -1)
        {
            index = step > 0 ? 0 : this.list.getList().size() - 1;
        }
        else
        {
            index = Math.max(0, Math.min(this.list.getList().size() - 1, index + step));
        }

        this.list.setIndex(index);
        this.list.scroll.setScroll(index * this.list.scroll.scrollItemSize);

        return true;
    }

    public UIQuickReplayOverlayPanel(List<Replay> replays, Replay selectedReplay, Consumer<Replay> callback)
    {
        super(UIKeys.FILM_REPLAY_TITLE);
        this.callback = callback;

        this.list = new UIList<>((current) ->
        {
            Replay replay = current.isEmpty() ? null : current.get(0);

            if (replay != null && this.callback != null)
            {
                this.callback.accept(replay);
            }

            this.close();
        })
        {
            @Override
            protected String elementToString(UIContext context, int i, Replay element)
            {
                return element.getName();
            }

            @Override
            public boolean subMouseScrolled(UIContext context)
            {
                if (UIQuickReplayOverlayPanel.this.handleWheelSelection(context))
                {
                    return true;
                }

                return super.subMouseScrolled(context);
            }

            @Override
            protected void renderElementPart(UIContext context, Replay element, int i, int x, int y, boolean hover, boolean selected)
            {
                int iconX = x + 2;
                int iconY = y + 2;
                int iconW = 20;
                int iconH = 20;
                int textY = y + (this.scroll.scrollItemSize - context.batcher.getFont().getHeight()) / 2;

                context.batcher.box(iconX, iconY, iconX + iconW, iconY + iconH, hover ? Colors.A75 : Colors.A50);

                Form form = element.form.get();

                if (form != null)
                {
                    context.batcher.clip(iconX, iconY, iconW, iconH, context);
                    FormUtilsClient.renderUI(form, context, iconX, iconY, iconX + iconW, iconY + iconH);
                    context.batcher.unclip(context);
                }

                context.batcher.textShadow(this.elementToString(context, i, element), x + 26, textY, hover ? Colors.HIGHLIGHT : Colors.WHITE);
            }
        };

        this.list.scroll.scrollItemSize = 24;
        this.list.scroll.scrollSpeed = 48;
        this.list.add(replays);
        this.list.update();

        if (selectedReplay != null)
        {
            this.list.setCurrentScroll(selectedReplay);
        }

        int visibleRows = Math.min(5, Math.max(1, replays.size()));
        int panelHeight = 32 + visibleRows * this.list.scroll.scrollItemSize;

        this.minW(240).maxW(240);
        this.minH(panelHeight).maxH(panelHeight);
        this.list.relative(this.content).x(6).y(6).w(1F, -12).h(1F, -12);
        this.content.add(this.list);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.centerCursor)
        {
            this.centerCursor(context);
        }

        super.render(context);
    }

    @Override
    public boolean subKeyPressed(UIContext context)
    {
        if (context.isPressed(Keys.CLOSE))
        {
            return super.subKeyPressed(context);
        }

        KeyBinding keybind = BBSModClient.getKeyOpenQuickReplays();

        if (keybind != null && context.getKeyAction() == KeyAction.PRESSED && keybind.matchesKey(new KeyInput(context.getKeyCode(), context.getScanCode(), 0)))
        {
            this.confirmSelection();

            return true;
        }

        return super.subKeyPressed(context);
    }
}
