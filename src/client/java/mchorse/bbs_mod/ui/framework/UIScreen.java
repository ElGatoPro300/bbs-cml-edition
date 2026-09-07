package mchorse.bbs_mod.ui.framework;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.discord.DiscordPresenceManager;
import mchorse.bbs_mod.importers.IImportPathProvider;
import mchorse.bbs_mod.importers.ImporterContext;
import mchorse.bbs_mod.importers.Importers;
import mchorse.bbs_mod.importers.types.IImporter;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.IFileDropListener;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.FFMpegUtils;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UIScreen extends Screen implements IFileDropListener
{
    private UIBaseMenu menu;
    private UIRenderingContext context;

    private int lastGuiScale;
    private boolean appliedGameScale;

    public static void open(UIBaseMenu menu)
    {
        MinecraftClient.getInstance().setScreen(new UIScreen(Text.empty(), menu));
    }

    public static UIBaseMenu getCurrentMenu()
    {
        Screen currentScreen = MinecraftClient.getInstance().currentScreen;

        if (currentScreen instanceof UIScreen uiScreen)
        {
            return uiScreen.menu;
        }

        return null;
    }

    public UIScreen(Text title, UIBaseMenu menu)
    {
        super(title);

        MinecraftClient mc = MinecraftClient.getInstance();

        this.client = mc;

        this.menu = menu;
        this.context = new UIRenderingContext(new DrawContext(mc, mc.getBufferBuilders().getEntityVertexConsumers()));

        this.menu.context.setup(this.context);
    }

    public UIBaseMenu getMenu()
    {
        return this.menu;
    }

    public void update()
    {
        this.menu.update();
    }

    public void renderInWorld(WorldRenderContext context)
    {
        this.menu.renderInWorld(context);
    }

    @Override
    public void filesDragged(List<Path> paths)
    {
        super.filesDragged(paths);

        String[] filePaths = new String[paths.size()];
        int i = 0;

        for (Path path : paths)
        {
            filePaths[i] = path.toAbsolutePath().toString();

            i += 1;
        }

        this.acceptFilePaths(filePaths);
    }

    @Override
    public void removed()
    {
        this.restoreGuiScale();

        super.removed();

        /* Force overlay teardown so deferred close animations cannot skip onClose
         * (e.g. RecordingPauseHelper.pop) when setScreen(null) replaces this UI. */
        if (this.menu != null && this.menu.overlay != null)
        {
            for (UIOverlay overlay : this.menu.overlay.getChildren(UIOverlay.class))
            {
                overlay.forceClose();
            }
        }

        this.menu.onClose(null);
        DiscordPresenceManager.INSTANCE.onBbsUiClosed();

        MinecraftClient.getInstance().options.hudHidden = false;
    }

    @Override
    public void onDisplayed()
    {
        MinecraftClient client = MinecraftClient.getInstance();

        this.lastGuiScale = client.options.getGuiScale().getValue();
        this.reapplyScale();

        super.onDisplayed();

        this.menu.onOpen(null);
        DiscordPresenceManager.INSTANCE.onBbsUiOpened(this.menu);

        client.options.hudHidden = this.menu.canHideHUD();
    }

    /**
     * Apply BBS scale for the current link-to-game setting. Independent mode never
     * writes Minecraft's GUI scale; linked mode always forces a window recalc so a
     * fractional BBS value (1.8 vs game 2) is not skipped by an int early-return.
     */
    public void reapplyScale()
    {
        if (BbsGuiScale.isLinkedToGame())
        {
            if (!this.appliedGameScale)
            {
                this.lastGuiScale = MinecraftClient.getInstance().options.getGuiScale().getValue();
            }

            this.applyGameGuiScale(BBSModClient.getGUIScale());
            this.appliedGameScale = true;
            this.menu.resize(this.client.getWindow().getScaledWidth(), this.client.getWindow().getScaledHeight());
        }
        else
        {
            this.restoreGuiScale();
            BbsGuiScale.resizeMenu(this.menu);
        }
    }

    private void applyGameGuiScale(int scale)
    {
        MinecraftClient client = MinecraftClient.getInstance();

        client.options.getGuiScale().setValue(scale);
        client.onResolutionChanged();
    }

    private void restoreGuiScale()
    {
        if (!this.appliedGameScale)
        {
            return;
        }

        this.appliedGameScale = false;
        BbsGuiScale.restoringGameScale(() -> this.applyGameGuiScale(this.lastGuiScale));
    }

    @Override
    public boolean shouldPause()
    {
        return this.menu.canPause();
    }

    @Override
    protected void init()
    {
        super.init();

        if (BbsGuiScale.isLinkedToGame())
        {
            this.menu.resize(this.width, this.height);
        }
        else
        {
            BbsGuiScale.resizeMenu(this.menu);
        }
    }

    @Override
    public void resize(MinecraftClient client, int width, int height)
    {
        super.resize(client, width, height);

        if (BbsGuiScale.isLinkedToGame())
        {
            this.menu.resize(width, height);
        }
        else
        {
            BbsGuiScale.resizeMenu(this.menu);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        return this.menu.mouseClicked(BbsGuiScale.toBbsMouseX(mouseX), BbsGuiScale.toBbsMouseY(mouseY), button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount)
    {
        return this.menu.mouseScrolled(BbsGuiScale.toBbsMouseX(mouseX), BbsGuiScale.toBbsMouseY(mouseY), horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        return this.menu.mouseReleased(BbsGuiScale.toBbsMouseX(mouseX), BbsGuiScale.toBbsMouseY(mouseY), button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers)
    {
        return this.menu.handleKey(keyCode, scanCode, BBSRendering.lastAction, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers)
    {
        return this.menu.handleKey(keyCode, scanCode, GLFW.GLFW_RELEASE, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers)
    {
        this.menu.handleTextInput(chr);

        return true;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta)
    {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta)
    {
        super.render(context, mouseX, mouseY, delta);

        int bbsMouseX = BbsGuiScale.toBbsMouseX(mouseX);
        int bbsMouseY = BbsGuiScale.toBbsMouseY(mouseY);

        BbsGuiScale.withBbsWindowScale(() ->
        {
            this.menu.context.setTransition(this.client.getRenderTickCounter().getTickDelta(false));
            this.menu.renderMenu(this.context, bbsMouseX, bbsMouseY);
            this.menu.context.render.executeRunnables();
        });
        this.client.options.hudHidden = this.menu.canHideHUD();
    }

    @Override
    public void acceptFilePaths(String[] paths)
    {
        if (this.menu != null)
        {
            if (!FFMpegUtils.checkFFMPEG())
            {
                this.menu.context.notifyError(UIKeys.IMPORTER_FFMPEG_NOTIFICATION);

                return;
            }

            File directory = null;
            boolean open = true;

            for (IImportPathProvider provider : this.menu.getRoot().getChildren(IImportPathProvider.class))
            {
                directory = provider.getImporterPath();

                if (directory != null)
                {
                    open = false;

                    break;
                }
            }

            List<File> files = new ArrayList<>();

            for (String path : paths)
            {
                File file = new File(path);

                if (file.exists())
                {
                    files.add(file);
                }
            }

            ImporterContext context = new ImporterContext(files, directory);

            for (IImporter importer : Importers.getImporters())
            {
                if (importer.canImport(context))
                {
                    importer.importFiles(context);

                    if (open)
                    {
                        UIUtils.openFolder(context.getDestination(importer));
                    }

                    this.menu.context.notifySuccess(UIKeys.IMPORTER_SUCCESS_NOTIFICATION.format(importer.getName()));

                    return;
                }
            }
        }
    }
}